package com.grabbit.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.grabbit.core.Timing

object Motion {
    val StandardEasing: Easing = FastOutSlowInEasing

    val EmphasisEasing: Easing = LinearOutSlowInEasing

    // Values shared with core are referenced, not redefined.

    val NavDurationMs: Int = Timing.NAV_ANIMATION_DURATION_MS

    val FloatingSnapDurationMs: Int = Timing.FLOATING_SNAP_DURATION_MS

    val QueuePanelDurationMs: Int = Timing.QUEUE_PANEL_ANIMATION_DURATION_MS

    val ListItemAppearMs: Int = 220

    val ListItemDisappearMs: Int = 160

    val ProgressChangeMs: Int = 120

    val StatusChangeMs: Int = 200

    val PressMs: Int = 100

    val TerminalFeedbackHoldMs: Int = 150

    val DialogMs: Int = 220

    val BottomSheetMs: Int = 220

    val FabMs: Int = 200

    val EmptyStateMs: Int = 250

    val NavSpec =
        tween<Float>(durationMillis = NavDurationMs, easing = StandardEasing)

    val NavSlideSpec =
        tween<IntOffset>(durationMillis = NavDurationMs, easing = StandardEasing)

    val ListItemAppearSpec =
        tween<Float>(durationMillis = ListItemAppearMs, easing = EmphasisEasing)

    val ListItemDisappearSpec =
        tween<Float>(durationMillis = ListItemDisappearMs, easing = StandardEasing)

    val ListItemSlideSpec =
        tween<IntOffset>(durationMillis = ListItemAppearMs, easing = EmphasisEasing)

    val ListItemDisappearSlideSpec =
        tween<IntOffset>(durationMillis = ListItemDisappearMs, easing = StandardEasing)

    val ProgressChangeSpec =
        tween<Float>(durationMillis = ProgressChangeMs, easing = StandardEasing)

    val StatusChangeSpec =
        tween<Float>(durationMillis = StatusChangeMs, easing = StandardEasing)

    val StatusColorSpec =
        tween<Color>(durationMillis = StatusChangeMs, easing = StandardEasing)

    val PressSpec = tween<Float>(durationMillis = PressMs, easing = StandardEasing)

    val DialogSpec = tween<Float>(durationMillis = DialogMs, easing = EmphasisEasing)

    val BottomSheetSpec = tween<Float>(durationMillis = BottomSheetMs, easing = EmphasisEasing)

    val FabSpec = tween<Float>(durationMillis = FabMs, easing = EmphasisEasing)

    val EmptyStateSpec =
        tween<Float>(durationMillis = EmptyStateMs, easing = EmphasisEasing)

    val EmptyStateSlideSpec =
        tween<IntOffset>(durationMillis = EmptyStateMs, easing = EmphasisEasing)

    val PressedScale: Float = 0.96f

    val TerminalPopScale: Float = 1.04f

    val NavSlideDp: Dp = 16.dp

    val ListItemSlideDp: Dp = 8.dp

    val EmptyStateSlideDp: Dp = 12.dp

    val PlatformChangeMs: Int = Timing.PLATFORM_COLOR_DURATION_MS

    val PlatformTransitionMs: Int = Timing.PLATFORM_TRANSITION_DURATION_MS

    val PlatformInactiveAlpha: Float = 0.45f
    val PlatformActiveAlpha: Float = 1.0f

    val PlatformInactiveScale: Float = 0.92f
    val PlatformActiveScale: Float = 1.0f

    val PlatformChangeSpec =
        tween<Float>(durationMillis = PlatformChangeMs, easing = StandardEasing)

    val PlatformColorSpec =
        tween<Color>(durationMillis = PlatformChangeMs, easing = StandardEasing)

    val PlatformTransitionSpec =
        tween<Float>(durationMillis = PlatformTransitionMs, easing = StandardEasing)

    val PlatformResizeSpec =
        tween<IntSize>(durationMillis = PlatformTransitionMs, easing = StandardEasing)
}
