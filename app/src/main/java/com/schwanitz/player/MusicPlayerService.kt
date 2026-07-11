package com.schwanitz.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.schwanitz.MainActivity
import com.schwanitz.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class MusicPlayerService : Service() {

    companion object {
        const val ACTION_PLAY = "com.schwanitz.action.PLAY"
        const val ACTION_PLAY_PAUSE = "com.schwanitz.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.schwanitz.action.SKIP_NEXT"
        const val ACTION_SKIP_PREVIOUS = "com.schwanitz.action.SKIP_PREVIOUS"
        const val ACTION_SHUFFLE = "com.schwanitz.action.SHUFFLE"
        const val ACTION_REPEAT = "com.schwanitz.action.REPEAT"
        const val ACTION_STOP = "com.schwanitz.action.STOP"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "media_playback"

        private const val REQ_CONTENT = 0
        private const val REQ_PREV = 1
        private const val REQ_PLAY_PAUSE = 2
        private const val REQ_NEXT = 3
        private const val REQ_SHUFFLE = 4
        private const val REQ_REPEAT = 5
    }

    @Inject
    lateinit var player: ExoPlayer

    private var mediaSession: MediaSession? = null

    private var isInForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        player.addListener(playerListener)

        val sessionActivity = PendingIntent.getActivity(
            this,
            REQ_CONTENT,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                if (!isInForeground) {
                    startForeground(NOTIFICATION_ID, buildNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                    isInForeground = true
                }
            }
            ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                updateNotification()
            }
            ACTION_SKIP_NEXT -> {
                player.seekToNextMediaItem()
            }
            ACTION_SKIP_PREVIOUS -> {
                player.seekToPreviousMediaItem()
            }
            ACTION_SHUFFLE -> {
                if (player.repeatMode != Player.REPEAT_MODE_ONE) {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    player.repeatMode = if (player.shuffleModeEnabled) {
                        Player.REPEAT_MODE_ALL
                    } else {
                        Player.REPEAT_MODE_OFF
                    }
                }
                updateNotification()
            }
            ACTION_REPEAT -> {
                player.repeatMode = when {
                    player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    player.shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_ALL -> {
                        player.shuffleModeEnabled = false
                        Player.REPEAT_MODE_OFF
                    }
                    player.repeatMode == Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    player.repeatMode == Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                updateNotification()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isInForeground = false
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        player.removeListener(playerListener)
        super.onDestroy()
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateNotification()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateNotification()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateNotification()
        }
    }

    private fun updateNotification() {
        if (isInForeground) {
            startForeground(NOTIFICATION_ID, buildNotification(), FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }
    }

    private fun buildNotification(): Notification {
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString() ?: getString(R.string.app_name)
        val artist = metadata?.artist?.toString() ?: ""

        val style = Notification.MediaStyle()
        mediaSession?.platformToken?.let { style.setMediaSession(it) }
        style.setShowActionsInCompactView(0, 1, 2, 3, 4)

        val pendingFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val shuffleIntent = PendingIntent.getService(
            this, REQ_SHUFFLE,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_SHUFFLE },
            pendingFlags
        )
        val prevIntent = PendingIntent.getService(
            this, REQ_PREV,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_SKIP_PREVIOUS },
            pendingFlags
        )
        val playPauseIntent = PendingIntent.getService(
            this, REQ_PLAY_PAUSE,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_PLAY_PAUSE },
            pendingFlags
        )
        val nextIntent = PendingIntent.getService(
            this, REQ_NEXT,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_SKIP_NEXT },
            pendingFlags
        )
        val repeatIntent = PendingIntent.getService(
            this, REQ_REPEAT,
            Intent(this, MusicPlayerService::class.java).apply { action = ACTION_REPEAT },
            pendingFlags
        )

        val playPauseIcon = if (player.isPlaying)
            R.drawable.ic_notification_pause else R.drawable.ic_notification_play

        val repeatIcon = if (player.repeatMode == Player.REPEAT_MODE_ONE)
            R.drawable.ic_notification_repeat_one else R.drawable.ic_notification_repeat

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this, REQ_CONTENT, openAppIntent, pendingFlags
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setStyle(style)
            .addAction(R.drawable.ic_notification_shuffle, "Shuffle", shuffleIntent)
            .addAction(R.drawable.ic_notification_skip_prev, "Previous", prevIntent)
            .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
            .addAction(R.drawable.ic_notification_skip_next, "Next", nextIntent)
            .addAction(repeatIcon, "Repeat", repeatIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Media Playback", NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
