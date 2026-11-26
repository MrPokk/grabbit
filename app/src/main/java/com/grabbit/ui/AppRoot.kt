package com.grabbit.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grabbit.R
import com.grabbit.core.Timing
import com.grabbit.ui.components.DownloadFab
import com.grabbit.ui.convert.ConvertScreen
import com.grabbit.ui.history.HistoryScreen
import com.grabbit.ui.home.HomeScreen
import com.grabbit.ui.queue.QueueScreen
import com.grabbit.ui.settings.SettingsScreen
import com.grabbit.ui.theme.Dimens
import kotlinx.coroutines.flow.StateFlow

private data class Destination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

/** Root navigation host with the bottom bar for the main destinations. */
@Composable
fun AppRoot(
    incomingLink: StateFlow<String?>,
    onConsumeLink: () -> Unit,
) {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = hiltViewModel()
    val queueSummary by appViewModel.queueSummary.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DOWNLOAD
    val link by incomingLink.collectAsStateWithLifecycle()
    val showFab =
        (currentRoute == Routes.DOWNLOAD || currentRoute == Routes.HISTORY) && queueSummary.count > 0
    val fabAnimationSpec = tween<Float>(Timing.FAB_ANIMATION_DURATION_MS)

    val destinations =
        remember {
            listOf(
                Destination(Routes.DOWNLOAD, R.string.nav_download, Icons.Rounded.Download),
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
                                    navController.navigateToTopLevel(destination.route)
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
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.DOWNLOAD,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.DOWNLOAD) {
                    HomeScreen(
                        incomingLink = link,
                        onLinkConsumed = onConsumeLink,
                    )
                }
                composable(Routes.QUEUE) {
                    QueueScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigateToTopLevel(Routes.SETTINGS) },
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
            AnimatedVisibility(
                visible = showFab,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = Dimens.md, end = Dimens.md),
                enter = fadeIn(animationSpec = fabAnimationSpec) + scaleIn(animationSpec = fabAnimationSpec),
                exit = fadeOut(animationSpec = fabAnimationSpec) + scaleOut(animationSpec = fabAnimationSpec),
            ) {
                DownloadFab(
                    summary = queueSummary,
                    onClick = {
                        navController.navigate(Routes.QUEUE) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}

private fun NavController.navigateToTopLevel(route: String) {
    navigate(route) {
        // Saved state is keyed to the start destination in a flat graph, so restoring it would
        // put the tab being left back on top instead of the requested one.
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
