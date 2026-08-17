package com.schwanitz.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.schwanitz.player.MusicPlayerManager

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided")
}

val LocalMusicPlayerManager = staticCompositionLocalOf<MusicPlayerManager> {
    error("No MusicPlayerManager provided")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val playerManager = LocalMusicPlayerManager.current
    var playerUiResetToken by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        playerManager.navigateToPlayer.collect {
            if (navController.navigateToPlayerForExternalPlayback()) {
                playerUiResetToken++
            }
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    BottomNavItem.items.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = stringResource(item.titleRes)) },
                            label = { Text(stringResource(item.titleRes)) },
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    navController.returnToRoot(item)
                                    if (item == BottomNavItem.NowPlaying) {
                                        playerUiResetToken++
                                    }
                                } else {
                                    navController.navigateToTopLevel(item)
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                    NavGraph(
                        navController = navController,
                        playerUiResetToken = playerUiResetToken
                    )
                }
            }
        }
    }
}
