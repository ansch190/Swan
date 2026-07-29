package com.schwanitz

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.ui.navigation.LocalMusicPlayerManager
import com.schwanitz.ui.navigation.MainScreen
import com.schwanitz.ui.theme.MusicPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(LocalMusicPlayerManager provides musicPlayerManager) {
                    MainScreen()
                }
            }
        }
    }
}
