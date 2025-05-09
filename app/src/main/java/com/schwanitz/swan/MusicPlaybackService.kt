package com.schwanitz.swan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle

class MusicPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicPlaybackBinder()
    private var currentUri: Uri? = null
    private var isPlaying: Boolean = false
    private val metadataExtractor by lazy { MetadataExtractor(this) }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "MusicPlaybackChannel"
        private const val CHANNEL_NAME = "Music Playback"
    }

    inner class MusicPlaybackBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d("MusicPlaybackService", "Service bound")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("MusicPlaybackService", "Service created, notification channel initialized")
    }

    fun play(uri: Uri) {
        try {
            // Nur neuen MediaPlayer erstellen, wenn uri sich ändert oder kein MediaPlayer existiert
            if (uri != currentUri || mediaPlayer == null) {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer.create(this, uri)
                if (mediaPlayer == null) {
                    Log.e("MusicPlaybackService", "Failed to create MediaPlayer for uri: $uri")
                    return
                }
                currentUri = uri
            }
            mediaPlayer?.start()
            isPlaying = true
            Log.d("MusicPlaybackService", "Playing: $uri")
            startForeground(NOTIFICATION_ID, buildNotification(uri, isPlaying = true).build())
            Log.d("MusicPlaybackService", "Foreground notification started for uri: $uri")
        } catch (e: Exception) {
            Log.e("MusicPlaybackService", "Error playing: $uri", e)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying = false
        Log.d("MusicPlaybackService", "Paused")
        currentUri?.let { updateNotification(isPlaying = false) }
    }

    fun resume() {
        if (mediaPlayer != null && !isPlaying) {
            mediaPlayer?.start()
            isPlaying = true
            Log.d("MusicPlaybackService", "Resumed")
            currentUri?.let { updateNotification(isPlaying = true) }
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        currentUri = null
        isPlaying = false
        Log.d("MusicPlaybackService", "Stopped")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentUri = null
        isPlaying = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.d("MusicPlaybackService", "Service destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for music playback notifications"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d("MusicPlaybackService", "Notification channel created: $CHANNEL_ID")
        }
    }

    private fun buildNotification(uri: Uri, isPlaying: Boolean): NotificationCompat.Builder {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pausePlayIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = if (isPlaying) "ACTION_PAUSE" else "ACTION_PLAY"
        }
        val pausePlayPendingIntent = PendingIntent.getService(
            this, 0, pausePlayIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = "ACTION_STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Metadaten abrufen
        val metadata = try {
            metadataExtractor.extractMetadata(uri)
        } catch (e: Exception) {
            Log.e("MusicPlaybackService", "Failed to extract metadata for $uri", e)
            Metadata("", "", "", "", "", "", "", "", 0, 0, "", 0, 0L, "")
        }
        val title = metadata.title.takeIf { it.isNotEmpty() } ?: uri.lastPathSegment ?: "Unknown Track"
        val artist = metadata.artist.takeIf { it.isNotEmpty() } ?: "Unknown Artist"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(pendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) getString(R.string.notification_pause) else getString(R.string.notification_play),
                pausePlayPendingIntent
            )
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.notification_stop),
                stopPendingIntent
            )
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun updateNotification(isPlaying: Boolean) {
        currentUri?.let { uri ->
            val notification = buildNotification(uri, isPlaying).build()
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
            Log.d("MusicPlaybackService", "Notification updated, isPlaying: $isPlaying")
        } ?: Log.w("MusicPlaybackService", "No currentUri, cannot update notification")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicPlaybackService", "onStartCommand, action: ${intent?.action}")
        when (intent?.action) {
            "ACTION_PAUSE" -> pause()
            "ACTION_PLAY" -> resume()
            "ACTION_STOP" -> stop()
        }
        return START_NOT_STICKY
    }
}