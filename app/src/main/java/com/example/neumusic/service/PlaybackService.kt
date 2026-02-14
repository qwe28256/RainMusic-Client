package com.example.neumusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.neumusic.MainActivity
import com.example.neumusic.R
import com.example.neumusic.utils.SleepTimerManager

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var notificationManager: NotificationManager

    // 【新增】用于限制通知更新频率的时间戳
    private var lastNotificationUpdateTime = 0L

    companion object {
        const val NOTIFICATION_ID = 8888
        const val CHANNEL_ID = "neumusic_classic_v1"

        const val ACTION_PLAY_PAUSE = "com.example.neumusic.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.neumusic.ACTION_NEXT"
        const val ACTION_PREV = "com.example.neumusic.ACTION_PREV"
        const val ACTION_CLOSE = "com.example.neumusic.ACTION_CLOSE"
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    if (player.isPlaying) player.pause() else player.play()
                }
                ACTION_NEXT -> {
                    if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                }
                ACTION_PREV -> {
                    if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                }
                ACTION_CLOSE -> {
                    player.pause()
                    stopForeground(true)
                    stopSelf()
                }
            }
            // 用户主动操作，立即强制更新
            forceUpdateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1. 配置 AudioAttributes
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // 2. 初始化 Player
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREV)
            addAction(ACTION_CLOSE)
        }

        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(notificationReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 播放状态改变非常重要，立即更新
                forceUpdateNotification()
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // 切歌也立即更新
                forceUpdateNotification()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                // 其他状态改变（如缓冲、拖动进度）进行节流，防止卡顿
                updateNotificationDebounced()
            }
        })

        SleepTimerManager.setOnTimerFinishedListener { player.pause() }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Neumusic Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music player controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 【新增】带节流的更新 (Debounce)
    private fun updateNotificationDebounced() {
        if (player.playbackState != Player.STATE_IDLE) {
            val now = System.currentTimeMillis()
            // 500ms 内只更新一次，大幅减少 NotificationService 压力
            if (now - lastNotificationUpdateTime > 500) {
                notificationManager.notify(NOTIFICATION_ID, createNotification())
                lastNotificationUpdateTime = now
            }
        }
    }

    // 【新增】强制更新 (用于点击按钮、切歌等必须立即响应的场景)
    private fun forceUpdateNotification() {
        if (player.playbackState != Player.STATE_IDLE) {
            notificationManager.notify(NOTIFICATION_ID, createNotification())
            lastNotificationUpdateTime = System.currentTimeMillis()
        }
    }

    private fun createNotification(): Notification {
        val collapsedView = RemoteViews(packageName, R.layout.notification_collapsed)

        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title ?: getString(R.string.app_name)
        val artist = metadata?.artist ?: "Unknown Artist"
        val coverUri = metadata?.artworkUri

        collapsedView.setTextViewText(R.id.notification_title, title)
        collapsedView.setTextViewText(R.id.notification_artist, artist)

        var bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        if (coverUri != null && coverUri.scheme == "file") {
            try {
                val fileBitmap = BitmapFactory.decodeFile(coverUri.path)
                if (fileBitmap != null) {
                    bitmap = fileBitmap
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        collapsedView.setImageViewBitmap(R.id.notification_cover, bitmap)

        val playIcon = if (player.isPlaying) R.drawable.ic_music_pause else R.drawable.ic_music_play
        collapsedView.setImageViewResource(R.id.notification_btn_play, playIcon)

        collapsedView.setOnClickPendingIntent(R.id.notification_btn_prev, getPendingIntent(ACTION_PREV))
        collapsedView.setOnClickPendingIntent(R.id.notification_btn_play, getPendingIntent(ACTION_PLAY_PAUSE))
        collapsedView.setOnClickPendingIntent(R.id.notification_btn_next, getPendingIntent(ACTION_NEXT))
        collapsedView.setOnClickPendingIntent(R.id.notification_btn_close, getPendingIntent(ACTION_CLOSE))

        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(collapsedView)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        }

        return builder.build()
    }

    private fun getPendingIntent(action: String): PendingIntent {
        val intent = Intent(action)
        intent.setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        try {
            unregisterReceiver(notificationReceiver)
        } catch (e: Exception) {}
        super.onDestroy()
    }
}
