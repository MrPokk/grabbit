package com.grabbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.grabbit.R
import com.grabbit.core.Timing
import com.grabbit.core.UiValues
import com.grabbit.ui.queue.QueueScreen
import com.grabbit.ui.queue.QueueViewModel
import com.grabbit.ui.theme.Dimens

@Composable
fun DownloadQueuePanel(
    visible: Boolean,
    edge: DockEdge,
    viewModel: QueueViewModel,
    onCollapse: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fadeSpec = tween<Float>(Timing.QUEUE_PANEL_ANIMATION_DURATION_MS)
    val slideSpec = tween<IntOffset>(Timing.QUEUE_PANEL_ANIMATION_DURATION_MS)
    val collapseLabel = stringResource(R.string.action_collapse)
    Box(modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = fadeSpec),
            exit = fadeOut(animationSpec = fadeSpec),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = UiValues.FLOATING_SCRIM_ALPHA),
                    ).clickable(onClick = onCollapse)
                    .semantics {
                        contentDescription = collapseLabel
                        role = Role.Button
                    },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter =
                slideInHorizontally(
                    initialOffsetX = { if (edge == DockEdge.LEFT) -it else it },
                    animationSpec = slideSpec,
                ) + fadeIn(animationSpec = fadeSpec),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { if (edge == DockEdge.LEFT) -it else it },
                    animationSpec = slideSpec,
                ) + fadeOut(animationSpec = fadeSpec),
        ) {
            Box(Modifier.fillMaxSize()) {
                Surface(
                    modifier =
                        Modifier
                            .align(if (edge == DockEdge.LEFT) Alignment.CenterStart else Alignment.CenterEnd)
                            .fillMaxSize()
                            .padding(
                                start = Dimens.md,
                                top = Dimens.queuePanelTopPadding,
                                end = Dimens.md,
                                bottom = Dimens.queuePanelBottomPadding,
                            ),
                    shape = MaterialTheme.shapes.large,
                    // Card-window: doesn't reach the bottom edge so the bar stays visible underneath.
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = Dimens.xxs,
                    shadowElevation = Dimens.sm,
                ) {
                    QueueScreen(
                        onBack = onCollapse,
                        onCollapse = onCollapse,
                        onOpenSettings = onOpenSettings,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
