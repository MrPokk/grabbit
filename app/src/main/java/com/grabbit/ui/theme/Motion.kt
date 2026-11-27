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

/**
 * Single source of truth for all motion specs: durations, easing, scales and offsets.
 *
 * Values already defined in [com.grabbit.core.Timing] are referenced here, not duplicated, so
 * core and UI motion stay in sync. Every other value lives only in this object; screens and
 * components must not repeat magic numbers for animation timing, scale or offset.
 */
object Motion {
    // --- Easing ---------------------------------------------------------------

    /** Default easing for most UI motion. */
    val StandardEasing: Easing = FastOutSlowInEasing

    /** Slightly emphasized easing for entrances and dismissals. */
    val EmphasisEasing: Easing = LinearOutSlowInEasing

    // --- Durations (ms) -------------------------------------------------------
    // Values shared with core are referenced, not redefined.

    /** Navigation route transitions (fade + slide/scale). */
    val NavDurationMs: Int = Timing.NAV_ANIMATION_DURATION_MS

    /** Floating widget snap-to-dock. */
    val FloatingSnapDurationMs: Int = Timing.FLOATING_SNAP_DURATION_MS

    /** Floating queue panel show/hide. */
    val QueuePanelDurationMs: Int = Timing.QUEUE_PANEL_ANIMATION_DURATION_MS

    /** New list item appearing. */
    val ListItemAppearMs: Int = 220

    /** List item being removed / swiped away. */
    val ListItemDisappearMs: Int = 160

    /** Progress bar catching up to a new value. */
    val ProgressChangeMs: Int = 120

    /** Status change (icon, text, color crossfade). */
    val StatusChangeMs: Int = 200

    /** Button / card press scale-down. */
    val PressMs: Int = 100

    /** Completion / error pop: how long the scaled-up state is held. */
    val TerminalFeedbackHoldMs: Int = 150

    /** Dialog show / dismiss. */
    val DialogMs: Int = 220

    /** Bottom sheet show / dismiss. */
    val BottomSheetMs: Int = 220

    /** FAB appear / disappear. */
    val FabMs: Int = 200

    /** Empty state appear. */
    val EmptyStateMs: Int = 250

    // --- Animation specs ------------------------------------------------------

    val NavSpec =
        tween<Float>(durationMillis = NavDurationMs, easing = StandardEasing)

    /** Slide offset for nav route transitions. */
    val NavSlideSpec =
        tween<IntOffset>(durationMillis = NavDurationMs, easing = StandardEasing)

    val ListItemAppearSpec =
        tween<Float>(durationMillis = ListItemAppearMs, easing = EmphasisEasing)

    val ListItemDisappearSpec =
        tween<Float>(durationMillis = ListItemDisappearMs, easing = StandardEasing)

    /** Slide offset for list item enter / exit. */
    val ListItemSlideSpec =
        tween<IntOffset>(durationMillis = ListItemAppearMs, easing = EmphasisEasing)

    val ListItemDisappearSlideSpec =
        tween<IntOffset>(durationMillis = ListItemDisappearMs, easing = StandardEasing)

    val ProgressChangeSpec =
        tween<Float>(durationMillis = ProgressChangeMs, easing = StandardEasing)

    val StatusChangeSpec =
        tween<Float>(durationMillis = StatusChangeMs, easing = StandardEasing)

    /** Color crossfade for task status text / card background. */
    val StatusColorSpec =
        tween<Color>(durationMillis = StatusChangeMs, easing = StandardEasing)

    val PressSpec = tween<Float>(durationMillis = PressMs, easing = StandardEasing)

    val DialogSpec = tween<Float>(durationMillis = DialogMs, easing = EmphasisEasing)

    val BottomSheetSpec = tween<Float>(durationMillis = BottomSheetMs, easing = EmphasisEasing)

    val FabSpec = tween<Float>(durationMillis = FabMs, easing = EmphasisEasing)

    val EmptyStateSpec =
        tween<Float>(durationMillis = EmptyStateMs, easing = EmphasisEasing)

    /** Slide offset for empty state enter / exit. */
    val EmptyStateSlideSpec =
        tween<IntOffset>(durationMillis = EmptyStateMs, easing = EmphasisEasing)

    // --- Scales ---------------------------------------------------------------

    /** Pressed state scale for buttons and cards. */
    val PressedScale: Float = 0.96f

    /** Brief scale-up when a task reaches a terminal state (completed / failed). */
    val TerminalPopScale: Float = 1.04f

    // --- Offsets --------------------------------------------------------------

    /** Navigation route slide offset. */
    val NavSlideDp: Dp = 16.dp

    /** List item enter / exit slide offset. */
    val ListItemSlideDp: Dp = 8.dp

    /** Empty state enter slide offset. */
    val EmptyStateSlideDp: Dp = 12.dp

    /** Platform selector chip selected-state change (color / alpha / scale). */
    val PlatformChangeMs: Int = Timing.PLATFORM_COLOR_DURATION_MS

    /** Platform indicator travel between the row above the input and the trailing slot. */
    val PlatformTransitionMs: Int = Timing.PLATFORM_TRANSITION_DURATION_MS

    /** Chip alpha when inactive / active. */
    val PlatformInactiveAlpha: Float = 0.45f
    val PlatformActiveAlpha: Float = 1.0f

    /** Chip scale when inactive / active. */
    val PlatformInactiveScale: Float = 0.92f
    val PlatformActiveScale: Float = 1.0f

    val PlatformChangeSpec =
        tween<Float>(durationMillis = PlatformChangeMs, easing = StandardEasing)

    val PlatformColorSpec =
        tween<Color>(durationMillis = PlatformChangeMs, easing = StandardEasing)

    val PlatformTransitionSpec =
        tween<Float>(durationMillis = PlatformTransitionMs, easing = StandardEasing)

    /** Width change of a platform chip leaving or joining the selector row, so the row reflows smoothly. */
    val PlatformResizeSpec =
        tween<IntSize>(durationMillis = PlatformTransitionMs, easing = StandardEasing)
}
