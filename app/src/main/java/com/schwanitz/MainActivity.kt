package com.schwanitz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.schwanitz.ui.navigation.MainScreen
import com.schwanitz.ui.theme.MusicPlayerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.e("MainActivity", "onCreate called ${BuildConfig.VERSION_NAME}")
        enableEdgeToEdge()
        setContent {
            MusicPlayerTheme {
                MainScreen()
            }
        }
    }
}
