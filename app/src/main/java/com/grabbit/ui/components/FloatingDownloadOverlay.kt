package com.grabbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.grabbit.R
import com.grabbit.core.Timing
import com.grabbit.core.UiValues
import com.grabbit.data.download.QueueSummary
import com.grabbit.ui.queue.QueueViewModel
import com.grabbit.ui.theme.Dimens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingDownloadOverlay(
    summary: QueueSummary,
    state: FloatingDownloadState,
    queueViewModel: QueueViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    val horizontalPosition = remember { Animatable(0f) }
    val verticalPosition = remember { Animatable(0f) }

    val layoutDirection = LocalLayoutDirection.current
    val barPadding = WindowInsets.systemBars.asPaddingValues()
    val topInsetPx = with(density) { barPadding.calculateTopPadding().toPx() }
    val leftInsetPx = with(density) { barPadding.calculateLeftPadding(layoutDirection).toPx() }
    val rightInsetPx = with(density) { barPadding.calculateRightPadding(layoutDirection).toPx() }
    val widgetSizePx = with(density) { Dimens.iconLg.toPx() }
    val gutterPx = with(density) { Dimens.md.toPx() }
    val bounds =
        FloatingBounds(
            width = containerSize.width,
            height = containerSize.height,
            top = topInsetPx,
            left = leftInsetPx,
            right = rightInsetPx,
            gutter = gutterPx,
            widgetSize = widgetSizePx,
            overlap = widgetSizePx * UiValues.FLOATING_DOCK_OVERLAP_FRACTION,
        )

    fun animateTo(
        edge: DockEdge,
        fraction: Float,
    ) {
        scope.launch {
            horizontalPosition.animateTo(
                bounds.dockedLeft(edge),
                animationSpec = tween(Timing.FLOATING_SNAP_DURATION_MS),
            )
        }
        scope.launch {
            verticalPosition.animateTo(
                bounds.topFor(fraction),
                animationSpec = tween(Timing.FLOATING_SNAP_DURATION_MS),
            )
        }
    }

    LaunchedEffect(bounds) {
        if (containerSize == IntSize.Zero) return@LaunchedEffect
        horizontalPosition.snapTo(bounds.dockedLeft(state.edge))
        verticalPosition.snapTo(bounds.topFor(state.verticalFraction))
    }

    Box(modifier.fillMaxSize().onSizeChanged { containerSize = it }) {
        val widgetOffset =
            Modifier.offset {
                IntOffset(horizontalPosition.value.roundToInt(), verticalPosition.value.roundToInt())
            }
        val widgetAnimationSpec = tween<Float>(Timing.FAB_ANIMATION_DURATION_MS)

        AnimatedVisibility(
            visible = !state.expanded,
            modifier = widgetOffset,
            enter = fadeIn(animationSpec = widgetAnimationSpec) + scaleIn(animationSpec = widgetAnimationSpec),
            exit = fadeOut(animationSpec = widgetAnimationSpec) + scaleOut(animationSpec = widgetAnimationSpec),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Dimens.iconLg)
                        .pointerInput(Unit) { detectTapGestures(onTap = { state.expand() }) }
                        .pointerInput(bounds) {
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDrag = { change, delta ->
                                    change.consume()
                                    scope.launch {
                                        horizontalPosition.snapTo(
                                            (horizontalPosition.value + delta.x).coerceIn(
                                                bounds.minLeft,
                                                bounds.maxLeft,
                                            ),
                                        )
                                        verticalPosition.snapTo(
                                            (verticalPosition.value + delta.y).coerceIn(
                                                bounds.minTop,
                                                bounds.maxTop,
                                            ),
                                        )
                                    }
                                },
                                onDragEnd = {
                                    val draggedEdge =
                                        if (
                                            horizontalPosition.value + widgetSizePx * UiValues.MIDPOINT <
                                            bounds.usableCenter
                                        ) {
                                            DockEdge.LEFT
                                        } else {
                                            DockEdge.RIGHT
                                        }
                                    val draggedFraction = bounds.fractionFor(verticalPosition.value)
                                    state.dock(draggedEdge, draggedFraction)
                                    isDragging = false
                                    animateTo(draggedEdge, draggedFraction)
                                },
                                onDragCancel = {
                                    isDragging = false
                                    animateTo(state.edge, state.verticalFraction)
                                },
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                DownloadProgressCircle(
                    summary = summary,
                    badgeAlignment =
                        if (state.edge == DockEdge.RIGHT) Alignment.TopStart else Alignment.TopEnd,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val arrowSizePx = with(density) { Dimens.iconButton.toPx() }
        val arrowGapPx = with(density) { Dimens.xxs.toPx() }
        val arrowX =
            if (state.edge == DockEdge.RIGHT) {
                horizontalPosition.value - arrowSizePx - arrowGapPx
            } else {
                horizontalPosition.value + widgetSizePx + arrowGapPx
            }
        val arrowY = verticalPosition.value + (widgetSizePx - arrowSizePx) * UiValues.MIDPOINT
        AnimatedVisibility(
            visible = !state.expanded && !isDragging,
            modifier = Modifier.offset { IntOffset(arrowX.roundToInt(), arrowY.roundToInt()) },
            enter = fadeIn(animationSpec = widgetAnimationSpec) + scaleIn(animationSpec = widgetAnimationSpec),
            exit = fadeOut(animationSpec = widgetAnimationSpec) + scaleOut(animationSpec = widgetAnimationSpec),
        ) {
            Surface(
                onClick = state::expand,
                modifier = Modifier.size(Dimens.iconButton),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = Dimens.xxs,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector =
                            if (state.edge == DockEdge.RIGHT) {
                                Icons.Rounded.ChevronLeft
                            } else {
                                Icons.Rounded.ChevronRight
                            },
                        contentDescription = stringResource(R.string.cd_expand_downloads),
                        modifier = Modifier.size(Dimens.iconSm),
                    )
                }
            }
        }

        DownloadQueuePanel(
            visible = state.expanded,
            edge = state.edge,
            viewModel = queueViewModel,
            onCollapse = state::collapse,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private data class FloatingBounds(
    val width: Int,
    val height: Int,
    val top: Float,
    val left: Float,
    val right: Float,
    val gutter: Float,
    val widgetSize: Float,
    val overlap: Float,
) {
    val minTop: Float
        get() = top + gutter

    val maxTop: Float
        get() = (height - gutter - widgetSize).coerceAtLeast(minTop)

    val minLeft: Float
        get() = left

    val maxLeft: Float
        get() = (width - right - widgetSize).coerceAtLeast(minLeft)

    val usableCenter: Float
        get() = (left + width - right) * UiValues.FLOATING_EDGE_SNAP_FRACTION

    fun dockedLeft(edge: DockEdge): Float =
        when (edge) {
            DockEdge.LEFT -> left - overlap
            DockEdge.RIGHT -> width - right - widgetSize + overlap
        }

    fun topFor(fraction: Float): Float = minTop + (maxTop - minTop) * fraction.coerceIn(0f, 1f)

    fun fractionFor(position: Float): Float {
        val usableHeight = (maxTop - minTop).coerceAtLeast(1f)
        return ((position - minTop) / usableHeight).coerceIn(0f, 1f)
    }
}
