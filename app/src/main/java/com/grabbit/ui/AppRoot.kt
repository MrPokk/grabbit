package com.grabbit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grabbit.R
import com.grabbit.ui.convert.ConvertScreen
import com.grabbit.ui.history.HistoryScreen
import com.grabbit.ui.home.HomeScreen
import com.grabbit.ui.queue.QueueScreen
import com.grabbit.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val DOWNLOAD = "download"
    const val QUEUE = "queue"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CONVERT = "convert"
}

private data class Destination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

@Composable
fun AppRoot(
    incomingLink: StateFlow<String?>,
    onConsumeLink: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DOWNLOAD
    val link by incomingLink.collectAsStateWithLifecycle()

    val destinations = remember {
        listOf(
            Destination(Routes.DOWNLOAD, R.string.nav_download, Icons.Rounded.Download),
            Destination(Routes.QUEUE, R.string.nav_queue, Icons.Rounded.Downloading),
            Destination(Routes.HISTORY, R.string.nav_history, Icons.Rounded.History),
            Destination(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (destinations.any { it.route == currentRoute }) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = null)
                            },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DOWNLOAD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DOWNLOAD) {
                HomeScreen(
                    incomingLink = link,
                    onLinkConsumed = onConsumeLink,
                    onOpenQueue = {
                        navController.navigate(Routes.QUEUE) {
                            popUpTo(Routes.DOWNLOAD)
                        }
                    },
                )
            }
            composable(Routes.QUEUE) {
                QueueScreen(
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenConvert = { navController.navigate(Routes.CONVERT) },
                )
            }
            composable(Routes.CONVERT) {
                ConvertScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
