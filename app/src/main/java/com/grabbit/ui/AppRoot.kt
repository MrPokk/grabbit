package com.grabbit.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
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
import com.grabbit.ui.theme.Motion
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

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

        val contentPadding =
            if (currentRoute == Routes.DOWNLOAD && imeBottom > 0.dp) PaddingValues() else padding
        Box(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
        ) {
            val density = LocalDensity.current
            val navSlidePx = (Motion.NavSlideDp.value * density.density).roundToInt()
            val navEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                fadeIn(animationSpec = Motion.NavSpec) +
                    slideInHorizontally(animationSpec = Motion.NavSlideSpec) { _ -> navSlidePx }
            }
            val navExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                fadeOut(animationSpec = Motion.NavSpec) +
                    slideOutHorizontally(animationSpec = Motion.NavSlideSpec) { _ -> -navSlidePx }
            }
            val navPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                fadeIn(animationSpec = Motion.NavSpec) +
                    slideInHorizontally(animationSpec = Motion.NavSlideSpec) { _ -> -navSlidePx }
            }
            val navPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                fadeOut(animationSpec = Motion.NavSpec) +
                    slideOutHorizontally(animationSpec = Motion.NavSlideSpec) { _ -> navSlidePx }
            }

            NavHost(
                navController = navController,
                startDestination = Routes.DOWNLOAD,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(
                    route = Routes.DOWNLOAD,
                    enterTransition = navEnter,
                    exitTransition = navExit,
                    popEnterTransition = navPopEnter,
                    popExitTransition = navPopExit,
                ) {
                    HomeScreen(
                        incomingLink = link,
                        onLinkConsumed = onConsumeLink,
                    )
                }
                composable(
                    route = Routes.QUEUE,
                    enterTransition = navEnter,
                    exitTransition = navExit,
                    popEnterTransition = navPopEnter,
                    popExitTransition = navPopExit,
                ) {
                    QueueScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = { navController.navigateToTopLevel(Routes.SETTINGS) },
                        viewModel = queueViewModel,
                    )
                }
                composable(
                    route = Routes.HISTORY,
                    enterTransition = navEnter,
                    exitTransition = navExit,
                    popEnterTransition = navPopEnter,
                    popExitTransition = navPopExit,
                ) {
                    HistoryScreen()
                }
                composable(
                    route = Routes.SETTINGS,
                    enterTransition = navEnter,
                    exitTransition = navExit,
                    popEnterTransition = navPopEnter,
                    popExitTransition = navPopExit,
                ) {
                    SettingsScreen(
                        onOpenConvert = { navController.navigate(Routes.CONVERT) },
                    )
                }
                composable(
                    route = Routes.CONVERT,
                    enterTransition = navEnter,
                    exitTransition = navExit,
                    popEnterTransition = navPopEnter,
                    popExitTransition = navPopExit,
                ) {
                    ConvertScreen(onBack = { navController.popBackStack() })
                }
            }
            AnimatedVisibility(
                visible = showWidget,
                enter = fadeIn(animationSpec = Motion.FabSpec),
                exit = fadeOut(animationSpec = Motion.FabSpec),
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
        // Saved state is keyed to the start destination in a flat graph; restoring it would re-show the tab being left.
        popUpTo(graph.findStartDestination().id)
        launchSingleTop = true
    }
}
