package com.schwanitz.swan

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class MusicPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicPlaybackBinder()

    inner class MusicPlaybackBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun play(uri: Uri) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, uri)
            mediaPlayer?.start()
            Log.d("MusicPlaybackService", "Playing: $uri")
        } catch (e: Exception) {
            Log.e("MusicPlaybackService", "Error playing: $uri", e)
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        Log.d("MusicPlaybackService", "Paused")
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        Log.d("MusicPlaybackService", "Stopped")
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}