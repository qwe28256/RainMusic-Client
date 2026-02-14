package com.example.neumusic.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.neumusic.data.AudioFile
import com.example.neumusic.data.AudioRepository
import com.example.neumusic.service.PlaybackService
import com.example.neumusic.utils.LyricsHelper
import com.example.neumusic.utils.LyricsLine
import com.example.neumusic.utils.SleepTimerManager // 导入
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var playerController: MediaController? = null

    // UI State
    private val _playlist = MutableStateFlow<List<AudioFile>>(emptyList())
    val playlist = _playlist.asStateFlow()

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

    // === 修改：直接对接 SleepTimerManager ===
    // 这里的 sleepTimerText 直接透传 Manager 的状态
    val sleepTimerText = SleepTimerManager.timeLeft

    // 设置定时器 (UI 调用此方法)
    fun setSleepTimer(minutes: Int) {
        SleepTimerManager.startTimer(minutes)
    }

    // 取消定时器
    fun cancelSleepTimer() {
        SleepTimerManager.stopTimer()
    }

    // ... 以下所有代码保持不变 ...

    fun initializeController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                playerController = controllerFuture.get()
                setupPlayerListener()
                observeDatabase()
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
                    if (playerController != null && files.isNotEmpty()) {
                        if (playerController?.mediaItemCount == 0) {
                            if (playerController?.mediaItemCount == 0) {
                                val mediaItems = files.map { file ->
                                    // 【核心修复】：构建完整的 MediaMetadata，供通知栏读取
                                    val metadata = androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(file.title)
                                        .setArtist(file.artist)
                                        // 传入封面 URI，通知栏适配器会读取这个 URI 来显示图片
                                        .setArtworkUri(file.albumArtUri)
                                        .build()

                                    MediaItem.Builder()
                                        .setUri(file.uri)
                                        .setMediaId(file.id.toString())
                                        .setMediaMetadata(metadata)
                                        .build()
                                }
                                playerController?.setMediaItems(mediaItems)
                                playerController?.prepare()
                            }
                        } else {
                            syncPlayerState()
                        }
                    }
                }
            }
        }
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
        })
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

    override fun onCleared() {
        playerController?.release()
        // 这里不要 cancelSleepTimer()，否则退出界面定时器就挂了
        // 让 SleepTimerManager 自己管理生命周期
        super.onCleared()
    }
}
