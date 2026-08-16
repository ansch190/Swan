package com.schwanitz.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
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

    LaunchedEffect(Unit) {
        playerManager.navigateToPlayer.collect {
            navController.navigateToTopLevel(BottomNavItem.NowPlaying)
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
                    NavGraph(navController = navController)
                }
            }
        }
    }
}

private fun NavHostController.navigateToTopLevel(item: BottomNavItem) {
    val alreadySelected = currentBackStackEntry?.destination?.hierarchy
        ?.any { it.route == item.route } == true
    if (alreadySelected) {
        returnToRoot(item)
        return
    }
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.returnToRoot(item: BottomNavItem) {
    if (currentDestination?.route == item.startDestination) return
    if (!popBackStack(item.startDestination, inclusive = false)) {
        navigate(item.startDestination) {
            popUpTo(item.route) { inclusive = false }
            launchSingleTop = true
        }
    }
}
