package com.schwanitz.swan.ui.activity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import com.schwanitz.swan.service.MusicPlaybackService
abstract class BaseMusicActivity : AppCompatActivity() {

    protected var musicService: MusicPlaybackService? = null
    protected var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicPlaybackBinder
            musicService = binder.getService()
            isBound = true
            onMusicServiceConnected()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            onMusicServiceDisconnected()
            musicService = null
        }
    }

    protected open fun onMusicServiceConnected() {}
    protected open fun onMusicServiceDisconnected() {}

    protected fun bindMusicService() {
        Intent(this, MusicPlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    protected fun unbindMusicService() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onDestroy() {
        unbindMusicService()
        super.onDestroy()
    }
}
