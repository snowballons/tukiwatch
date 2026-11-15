package com.snowballons.tukiwatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service providing persistent background playback for live streams.
 *
 * Keeps CPU and audio active when the user switches apps or turns off the screen,
 * and publishes a system media notification with play/pause and stop controls.
 */
class PlaybackService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "tukiwatch_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.snowballons.tukiwatch.action.START_PLAYBACK"
        const val ACTION_PAUSE = "com.snowballons.tukiwatch.action.PAUSE_PLAYBACK"
        const val ACTION_PLAY = "com.snowballons.tukiwatch.action.PLAY_PLAYBACK"
        const val ACTION_STOP = "com.snowballons.tukiwatch.action.STOP_PLAYBACK"

        const val EXTRA_STREAMER_NAME = "extra_streamer_name"
        const val EXTRA_STREAM_TITLE = "extra_stream_title"

        fun start(context: Context, streamerName: String?, streamTitle: String?) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STREAMER_NAME, streamerName)
                putExtra(EXTRA_STREAM_TITLE, streamTitle)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TukiWatch:PlaybackWakeLock"
        )?.apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? TukiWatchApp
        when (intent?.action) {
            ACTION_START -> {
                val streamerName = intent.getStringExtra(EXTRA_STREAMER_NAME)
                    ?: app?.activeStreamerName
                    ?: "Live Stream"
                val streamTitle = intent.getStringExtra(EXTRA_STREAM_TITLE)
                    ?: app?.activeStreamTitle
                    ?: "TukiWatch"

                app?.activeStreamerName = streamerName
                app?.activeStreamTitle = streamTitle

                wakeLock?.acquire(6 * 60 * 60 * 1000L) // 6 hours safety timeout
                val notification = buildNotification(streamerName, streamTitle, isPlaying = true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            ACTION_PAUSE -> {
                app?.activePlayer?.let { player ->
                    player.playWhenReady = false
                }
                updateNotification(isPlaying = false)
            }
            ACTION_PLAY -> {
                app?.activePlayer?.let { player ->
                    player.playWhenReady = true
                }
                updateNotification(isPlaying = true)
            }
            ACTION_STOP -> {
                app?.activePlayer?.let { player ->
                    player.stop()
                }
                stopForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(isPlaying: Boolean) {
        val app = application as? TukiWatchApp
        val streamerName = app?.activeStreamerName ?: "Live Stream"
        val streamTitle = app?.activeStreamTitle ?: "TukiWatch"
        val notification = buildNotification(streamerName, streamTitle, isPlaying)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        streamerName: String,
        streamTitle: String,
        isPlaying: Boolean,
    ): android.app.Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(
                this,
                1,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            ).build()
        } else {
            val playIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY }
            val playPendingIntent = PendingIntent.getService(
                this,
                2,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                playPendingIntent
            ).build()
        }

        val stopIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(streamerName)
            .setContentText(streamTitle)
            .setSmallIcon(R.drawable.tukiwatch_logo)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stream Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for background stream playback"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}
