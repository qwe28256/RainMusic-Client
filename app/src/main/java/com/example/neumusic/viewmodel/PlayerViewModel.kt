package com.example.neumusic.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.neumusic.data.AudioFile
import com.example.neumusic.data.AudioRepository
import com.example.neumusic.data.database.MusicDatabase
import com.example.neumusic.service.PlaybackService
import com.example.neumusic.utils.LyricsHelper
import com.example.neumusic.utils.LyricsLine
import com.example.neumusic.utils.SleepTimerManager
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayMode {
    LOOP_ALL, LOOP_ONE, SHUFFLE
}

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var playerController: MediaController? = null
    private val musicDao = MusicDatabase.getDatabase(application).musicDao()

    private val _playlist = MutableStateFlow<List<AudioFile>>(emptyList())
    val playlist = _playlist.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // 核心：在 IO 线程进行搜索过滤
    val displaySongs: StateFlow<List<AudioFile>> = _playlist
        .combine(_searchQuery) { songs, query ->
            if (query.isEmpty()) {
                songs
            } else {
                songs.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true)
                }
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentSong = MutableStateFlow<AudioFile?>(null)
    val currentSong = _currentSong.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricsLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex = _currentLyricIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    val progress = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _playMode = MutableStateFlow(PlayMode.LOOP_ALL)
    val playMode = _playMode.asStateFlow()

    val sleepTimerText = SleepTimerManager.timeLeft

    fun setSleepTimer(minutes: Int) {
        SleepTimerManager.startTimer(minutes)
    }

    fun cancelSleepTimer() {
        SleepTimerManager.stopTimer()
    }

    fun initializeController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                playerController = controllerFuture.get()
                setupPlayerListener()
                observeDatabase()
                syncPlayModeState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val repository = AudioRepository(context)
            repository.allMusic.collect { files ->
                _playlist.value = files
                withContext(Dispatchers.Main) {
                    if (playerController != null) {
                        if (files.isNotEmpty() && (playerController?.mediaItemCount == 0 || playerController?.mediaItemCount != files.size)) {
                            updatePlayerMediaItems(files)
                        } else if (files.isEmpty()) {
                            playerController?.clearMediaItems()
                        } else {
                            syncPlayerState()
                        }
                    }
                }
            }
        }
    }

    private fun updatePlayerMediaItems(files: List<AudioFile>) {
        val mediaItems = files.map { file ->
            val metadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(file.title)
                .setArtist(file.artist)
                .setArtworkUri(file.albumArtUri)
                .build()

            MediaItem.Builder()
                .setUri(file.uri)
                .setMediaId(file.id.toString())
                .setMediaMetadata(metadata)
                .build()
        }

        val currentIdx = playerController?.currentMediaItemIndex ?: 0
        val currentPos = playerController?.currentPosition ?: 0L
        val wasPlaying = playerController?.isPlaying == true

        playerController?.setMediaItems(mediaItems)

        if (currentIdx < mediaItems.size) {
            playerController?.seekTo(currentIdx, currentPos)
        }

        playerController?.prepare()
        if (wasPlaying) playerController?.play()
    }

    private fun syncPlayerState() {
        playerController?.let { controller ->
            _isPlaying.value = controller.isPlaying
            val index = controller.currentMediaItemIndex
            if (index >= 0 && index < _playlist.value.size) {
                val song = _playlist.value[index]
                if (_currentSong.value?.id != song.id) {
                    _currentSong.value = song
                    _duration.value = song.duration
                    loadLyrics(song.path)
                }
            }
            if (controller.isPlaying) {
                startProgressUpdate()
            } else {
                _progress.value = controller.currentPosition
                updateLyricIndex(controller.currentPosition)
            }
            syncPlayModeState()
        }
    }

    private fun syncPlayModeState() {
        playerController?.let {
            if (it.shuffleModeEnabled) {
                _playMode.value = PlayMode.SHUFFLE
            } else if (it.repeatMode == Player.REPEAT_MODE_ONE) {
                _playMode.value = PlayMode.LOOP_ONE
            } else {
                _playMode.value = PlayMode.LOOP_ALL
            }
        }
    }

    private fun setupPlayerListener() {
        playerController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) startProgressUpdate()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentSongInfo()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    updateCurrentSongInfo()
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                syncPlayModeState()
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                syncPlayModeState()
            }
        })
    }

    fun togglePlayMode() {
        playerController?.let { controller ->
            when (_playMode.value) {
                PlayMode.LOOP_ALL -> {
                    controller.shuffleModeEnabled = false
                    controller.repeatMode = Player.REPEAT_MODE_ONE
                    _playMode.value = PlayMode.LOOP_ONE
                }
                PlayMode.LOOP_ONE -> {
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    controller.shuffleModeEnabled = true
                    _playMode.value = PlayMode.SHUFFLE
                }
                PlayMode.SHUFFLE -> {
                    controller.shuffleModeEnabled = false
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    _playMode.value = PlayMode.LOOP_ALL
                }
            }
        }
    }

    private fun updateCurrentSongInfo() {
        val index = playerController?.currentMediaItemIndex ?: 0
        if (_playlist.value.isNotEmpty() && index < _playlist.value.size && index >= 0) {
            val song = _playlist.value[index]
            if (_currentSong.value?.id != song.id) {
                _currentSong.value = song
                _duration.value = song.duration
                loadLyrics(song.path)
            }
        }
    }

    private fun loadLyrics(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedLyrics = LyricsHelper.getLyrics(path)
            _lyrics.value = loadedLyrics
        }
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (_isPlaying.value) {
                playerController?.let {
                    val currentPos = it.currentPosition
                    _progress.value = currentPos
                    updateLyricIndex(currentPos)
                }
                delay(200)
            }
        }
    }

    private fun updateLyricIndex(currentPos: Long) {
        if (_lyrics.value.isNotEmpty()) {
            val index = _lyrics.value.indexOfLast { line -> line.startTime <= currentPos }
            if (index != _currentLyricIndex.value) {
                _currentLyricIndex.value = index
            }
        }
    }

    fun togglePlayPause() {
        if (playerController?.isPlaying == true) playerController?.pause() else playerController?.play()
    }

    fun playNext() = playerController?.seekToNextMediaItem()
    fun playPrevious() = playerController?.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) {
        playerController?.seekTo(positionMs)
        _progress.value = positionMs
        updateLyricIndex(positionMs)
    }

    fun playFromPlaylist(index: Int) {
        playerController?.seekTo(index, 0)
        playerController?.play()
    }

    fun playAudioFile(song: AudioFile) {
        val index = _playlist.value.indexOfFirst { it.id == song.id }
        if (index != -1) {
            playFromPlaylist(index)
        }
    }

    fun removeFromPlaylist(audioFile: AudioFile) {
        viewModelScope.launch(Dispatchers.IO) {
            musicDao.deleteMusicById(audioFile.id)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteAudioFile(audioFile: AudioFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 删除音频文件
                val file = java.io.File(audioFile.path)
                if (file.exists()) {
                    file.delete()
                }

                // 2. 【新增】删除封面图片
                // 注意：AudioFile 中我们把 string 路径转成了 Uri，这里需要转回去或者判断
                // AudioFile.albumArtUri 是 file://...
                audioFile.albumArtUri?.path?.let { coverPath ->
                    val coverFile = java.io.File(coverPath)
                    if (coverFile.exists()) {
                        coverFile.delete()
                    }
                }

                // 3. 删除数据库记录
                musicDao.deleteMusicById(audioFile.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    override fun onCleared() {
        playerController?.release()
        super.onCleared()
    }
}
