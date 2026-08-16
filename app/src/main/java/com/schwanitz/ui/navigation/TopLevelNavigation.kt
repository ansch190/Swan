package com.schwanitz.ui.navigation

import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

internal fun NavHostController.isInTopLevel(item: BottomNavItem): Boolean =
    currentBackStackEntry?.destination?.hierarchy?.any { it.route == item.route } == true

internal fun NavHostController.navigateToTopLevel(item: BottomNavItem) {
    if (isInTopLevel(item)) {
        returnToRoot(item)
        return
    }
    navigate(item.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun NavHostController.navigateToTopLevelDestination(
    item: BottomNavItem,
    destination: String
) {
    if (!isInTopLevel(item)) {
        navigate(item.route) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    navigate(destination) { launchSingleTop = true }
}

internal fun NavHostController.returnToRoot(item: BottomNavItem) {
    if (currentDestination?.route == item.startDestination) return
    if (!popBackStack(item.startDestination, inclusive = false)) {
        navigate(item.startDestination) {
            popUpTo(item.route) { inclusive = false }
            launchSingleTop = true
        }
    }
}
