package com.grabbit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.grabbit.ui.components.AppBottomBar
import com.grabbit.ui.components.FloatingDownloadOverlay
import com.grabbit.ui.components.rememberFloatingDownloadState
import com.grabbit.ui.convert.ConvertScreen
import com.grabbit.ui.history.HistoryScreen
import com.grabbit.ui.home.HomeScreen
import com.grabbit.ui.queue.QueueScreen
import com.grabbit.ui.queue.QueueViewModel
import com.grabbit.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.StateFlow

/** Root navigation host with the bottom bar for the main destinations. */
@Composable
fun AppRoot(
    incomingLink: StateFlow<String?>,
    onConsumeLink: () -> Unit,
) {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = hiltViewModel()
    val queueViewModel: QueueViewModel = hiltViewModel()
    val queueSummary by appViewModel.queueSummary.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.DOWNLOAD
    val link by incomingLink.collectAsStateWithLifecycle()
    val floatingState = rememberFloatingDownloadState()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    // Show the floating download circle only on the download and history screens;
    // hide it immediately when opening the queue or settings (panel closed).
    val showWidget =
        (currentRoute == Routes.DOWNLOAD || currentRoute == Routes.HISTORY) &&
            queueSummary.count > 0

    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.DOWNLOAD && currentRoute != Routes.HISTORY) {
            floatingState.collapse()
        }
    }

    LaunchedEffect(queueSummary.count) {
        if (queueSummary.count == 0) {
            floatingState.collapse()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (
                currentRoute == Routes.DOWNLOAD ||
                currentRoute == Routes.HISTORY ||
                currentRoute == Routes.SETTINGS
            ) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateToTopLevel(route) },
                )
            }
        },
    ) { padding ->
        // With the keyboard open the bar sits under it; reserving its height here would lift the URL field twice.
        val contentPadding =
            if (currentRoute == Routes.DOWNLOAD && imeBottom > 0.dp) PaddingValues() else padding
        Box(
            modifier =
                Modifier
                    .padding(contentPadding)
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
                        viewModel = queueViewModel,
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
                visible = showWidget,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
            ) {
                FloatingDownloadOverlay(
                    summary = queueSummary,
                    state = floatingState,
                    queueViewModel = queueViewModel,
                    onOpenSettings = { navController.navigateToTopLevel(Routes.SETTINGS) },
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
