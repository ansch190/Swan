package com.schwanitz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.schwanitz.data.source.SourceScanCoordinator
import com.schwanitz.data.backup.BackupJobCoordinator
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.ui.navigation.LocalMusicPlayerManager
import com.schwanitz.ui.navigation.MainScreen
import com.schwanitz.ui.theme.MusicPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var backupOpenRequestToken by mutableLongStateOf(0L)

    private var notificationPermissionRequestPending = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationPermissionRequestPending = false
        // Playback and scans remain available when notifications are denied.
    }

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    @Inject
    lateinit var sourceScanCoordinator: SourceScanCoordinator

    @Inject
    lateinit var backupJobCoordinator: BackupJobCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeBackupIntent(intent)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    musicPlayerManager.playbackStarted.collect { requestNotificationPermission() }
                }
                launch {
                    sourceScanCoordinator.scanRequested.collect { requestNotificationPermission() }
                }
                launch {
                    backupJobCoordinator.jobRequested.collect { requestNotificationPermission() }
                }
            }
        }
        setContent {
            MusicPlayerTheme {
                CompositionLocalProvider(LocalMusicPlayerManager provides musicPlayerManager) {
                    MainScreen(backupJobCoordinator, backupOpenRequestToken)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeBackupIntent(intent)
    }

    private fun consumeBackupIntent(intent: android.content.Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_BACKUP, false) == true) {
            backupOpenRequestToken++
            intent.removeExtra(EXTRA_OPEN_BACKUP)
        }
    }

    private fun requestNotificationPermission() {
        if (!notificationPermissionRequestPending &&
            Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequestPending = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_BACKUP = "com.schwanitz.OPEN_BACKUP"
    }
}
