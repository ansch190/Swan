package com.schwanitz.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.schwanitz.player.MusicPlayerManager
import com.schwanitz.data.backup.BackupJobCoordinator
import com.schwanitz.data.backup.BackupJobProgress
import com.schwanitz.data.backup.BackupJobStage
import com.schwanitz.data.backup.BackupOperation
import com.schwanitz.ui.screens.settings.BackupProgressDetails

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

val LocalMusicPlayerManager = staticCompositionLocalOf<MusicPlayerManager> {
    error("No MusicPlayerManager provided")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    backupJobCoordinator: BackupJobCoordinator,
    backupOpenRequestToken: Long = 0L,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val playerManager = LocalMusicPlayerManager.current
    var playerUiResetToken by rememberSaveable { mutableLongStateOf(0L) }
    val backupWorkState by backupJobCoordinator.workState.collectAsStateWithLifecycle(initialValue = null)
    val restoreProgress = backupWorkState?.takeIf {
        it.operation == BackupOperation.RESTORE && it.isRunning
    }?.progress ?: backupWorkState?.takeIf {
        it.operation == BackupOperation.RESTORE && it.isRunning
    }?.let { BackupJobProgress(BackupOperation.RESTORE, BackupJobStage.PREPARING) }
    val view = LocalView.current

    DisposableEffect(view, restoreProgress) {
        val previous = view.keepScreenOn
        if (restoreProgress != null) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    if (restoreProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(com.schwanitz.R.string.backup_restore_progress_title)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    BackupProgressDetails(restoreProgress)
                    Text(
                        stringResource(com.schwanitz.R.string.backup_restore_background_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )
    }

    LaunchedEffect(Unit) {
        playerManager.navigateToPlayer.collect {
            if (navController.navigateToPlayerForExternalPlayback()) {
                playerUiResetToken++
            }
        }
    }

    LaunchedEffect(backupOpenRequestToken) {
        if (backupOpenRequestToken > 0) {
            navController.navigateToTopLevel(BottomNavItem.Songs)
            navController.navigateWithinTopLevel(Routes.BACKUP)
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                BottomNavItem.items.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    item(
                        icon = { Icon(item.icon, contentDescription = stringResource(item.titleRes)) },
                        label = { Text(stringResource(item.titleRes)) },
                        selected = selected,
                        onClick = {
                            if (selected) {
                                navController.returnToRoot(item)
                                if (item == BottomNavItem.NowPlaying) playerUiResetToken++
                            } else {
                                navController.navigateToTopLevel(item)
                            }
                        },
                    )
                }
            },
        ) {
            Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
                Surface(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(navController = navController, playerUiResetToken = playerUiResetToken)
                }
            }
        }
    }
}
