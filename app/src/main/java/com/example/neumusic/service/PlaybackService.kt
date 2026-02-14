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
            // 收到指令后立即更新 UI，防止延迟
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
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
                updateNotification()
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateNotification()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateNotification()
            }
        })

        SleepTimerManager.setOnTimerFinishedListener { player.pause() }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    // 禁用系统默认通知
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

    private fun updateNotification() {
        if (player.playbackState != Player.STATE_IDLE) {
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun createNotification(): Notification {
        // 【核心修改】只加载 collapsed 布局
        val collapsedView = RemoteViews(packageName, R.layout.notification_collapsed)

        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title ?: getString(R.string.app_name)
        val artist = metadata?.artist ?: "Unknown Artist"
        val coverUri = metadata?.artworkUri

        // 1. 设置文本
        collapsedView.setTextViewText(R.id.notification_title, title)
        collapsedView.setTextViewText(R.id.notification_artist, artist)

        // 2. 设置封面
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

        // 3. 设置按钮图标
        val playIcon = if (player.isPlaying) R.drawable.ic_music_pause else R.drawable.ic_music_play
        collapsedView.setImageViewResource(R.id.notification_btn_play, playIcon)

        // 4. 绑定点击事件 (包括上一首、下一首、关闭)
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
            // 【核心修改】不设置 BigContentView，强制只显示小视图
            //.setCustomBigContentView(expandedView)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        // Android 12+ 装饰风格适配 (保留装饰框，内容显示为我们的 XML)
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
