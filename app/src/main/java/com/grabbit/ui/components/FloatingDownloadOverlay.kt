package com.grabbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.grabbit.core.Timing
import com.grabbit.core.UiValues
import com.grabbit.data.download.QueueSummary
import com.grabbit.ui.queue.QueueViewModel
import com.grabbit.ui.theme.Dimens
import kotlinx.coroutines.coroutineScope
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

    var docked by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val horizontalPosition = remember { Animatable(0f) }
    val verticalPosition = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    // true only when the widget sits still at its edge (after layout and after the snap animation finished)
    val revealTarget = docked && !state.expanded && !isDragging
    val revealSpec: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        )
    LaunchedEffect(revealTarget) {
        reveal.animateTo(targetValue = if (revealTarget) 1f else 0f, animationSpec = revealSpec)
    }
    val revealValue by reveal.asState()

    // Fades the goo blob out when the queue panel opens so the solid circle under the scrim stops peeking out.
    val blobPresence by animateFloatAsState(
        targetValue = if (state.expanded) 0f else 1f,
        animationSpec = tween(Timing.QUEUE_PANEL_ANIMATION_DURATION_MS),
        label = "blobPresence",
    )

    val layoutDirection = LocalLayoutDirection.current
    val barPadding = WindowInsets.systemBars.asPaddingValues()
    val topInsetPx = with(density) { barPadding.calculateTopPadding().toPx() }
    val leftInsetPx = with(density) { barPadding.calculateLeftPadding(layoutDirection).toPx() }
    val rightInsetPx = with(density) { barPadding.calculateRightPadding(layoutDirection).toPx() }
    val widgetSizePx = with(density) { Dimens.gooCircleSize.toPx() }
    val edgeGapPx = with(density) { Dimens.gooEdgeGap.toPx() }
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
            edgeGap = edgeGapPx,
        )

    LaunchedEffect(bounds) {
        if (containerSize == IntSize.Zero) return@LaunchedEffect
        horizontalPosition.snapTo(bounds.dockedLeft(state.edge))
        verticalPosition.snapTo(bounds.topFor(state.verticalFraction))
        if (!isDragging) docked = true
    }

    Box(modifier.fillMaxSize().onSizeChanged { containerSize = it }) {
        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            horizontalPosition.value.roundToInt(),
                            verticalPosition.value.roundToInt(),
                        )
                    }.size(Dimens.gooCircleSize),
        ) {
            if (!isDragging && blobPresence > 0f) {
                GooeyEdgeBlob(
                    edge = state.edge,
                    reveal = revealValue,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier =
                        Modifier
                            .requiredSize(Dimens.gooLayerSize)
                            .align(Alignment.Center)
                            .graphicsLayer { alpha = blobPresence },
                )
            }
            DownloadCircleIfCollapsed(
                expanded = state.expanded,
                summary = summary,
                edge = state.edge,
                isDragging = isDragging,
            )
            Box(
                modifier =
                    Modifier
                        .size(Dimens.iconButton)
                        .align(Alignment.Center)
                        .pointerInput(Unit) { detectTapGestures(onTap = { state.expand() }) }
                        .pointerInput(bounds) {
                            detectDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    docked = false
                                },
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
                                    isDragging = false
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
                                    scope.launch {
                                        snapToDock(
                                            bounds,
                                            horizontalPosition,
                                            verticalPosition,
                                            draggedEdge,
                                            draggedFraction,
                                        )
                                        if (!isDragging) docked = true
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    scope.launch {
                                        snapToDock(
                                            bounds,
                                            horizontalPosition,
                                            verticalPosition,
                                            state.edge,
                                            state.verticalFraction,
                                        )
                                        docked = true
                                    }
                                },
                            )
                        },
            )
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

@Composable
private fun DownloadCircleIfCollapsed(
    expanded: Boolean,
    summary: QueueSummary,
    edge: DockEdge,
    isDragging: Boolean,
) {
    if (!expanded) {
        DownloadProgressCircle(
            summary = summary,
            badgeAlignment = if (edge == DockEdge.RIGHT) Alignment.TopStart else Alignment.TopEnd,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            contentAlpha = 1f,
            contentScale = 1f,
            containerVisible = isDragging,
            modifier = Modifier.size(Dimens.gooCircleSize),
        )
    }
}

// Suspends until BOTH axes finished, so goo starts only after the widget really touched the edge.
private suspend fun snapToDock(
    bounds: FloatingBounds,
    horizontalPosition: Animatable<Float, AnimationVector1D>,
    verticalPosition: Animatable<Float, AnimationVector1D>,
    edge: DockEdge,
    fraction: Float,
) {
    coroutineScope {
        launch {
            horizontalPosition.animateTo(
                bounds.dockedLeft(edge),
                animationSpec = tween(Timing.FLOATING_SNAP_DURATION_MS),
            )
        }
        launch {
            verticalPosition.animateTo(
                bounds.topFor(fraction),
                animationSpec = tween(Timing.FLOATING_SNAP_DURATION_MS),
            )
        }
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
    val edgeGap: Float,
) {
    val minTop: Float
        get() = top + gutter

    val maxTop: Float
        get() = (height - gutter - widgetSize).coerceAtLeast(minTop)

    val minLeft: Float
        get() = left + edgeGap

    val maxLeft: Float
        get() = (width - right - widgetSize - edgeGap).coerceAtLeast(minLeft)

    val usableCenter: Float
        get() = (left + width - right) * UiValues.FLOATING_EDGE_SNAP_FRACTION

    fun dockedLeft(edge: DockEdge): Float =
        when (edge) {
            DockEdge.LEFT -> left + edgeGap
            DockEdge.RIGHT -> width - right - widgetSize - edgeGap
        }

    fun topFor(fraction: Float): Float = minTop + (maxTop - minTop) * fraction.coerceIn(0f, 1f)

    fun fractionFor(position: Float): Float {
        val usableHeight = (maxTop - minTop).coerceAtLeast(1f)
        return ((position - minTop) / usableHeight).coerceIn(0f, 1f)
    }
}
