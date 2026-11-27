package com.grabbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import com.grabbit.ui.theme.Motion
import kotlin.math.roundToInt

/**
 * Standard enter / exit transition for a single list item: a soft fade paired with a small
 * vertical slide. Adding and removing items is visually obvious instead of instantaneous.
 *
 * Use it instead of a bare `items(...)` lambda in [LazyColumn]. (Placement animation for
 * reordering is intentionally omitted: [androidx.compose.foundation.layout.animateItemPlacement]
 * is not available in the Compose version the app targets.)
 */
@Composable
fun ListItemTransition(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val slidePx = (Motion.ListItemSlideDp.value * density.density).roundToInt()
    AnimatedVisibility(
        visible = true,
        enter =
            fadeIn(animationSpec = Motion.ListItemAppearSpec) +
                slideInVertically(animationSpec = Motion.ListItemSlideSpec) { _ -> slidePx },
        exit =
            fadeOut(animationSpec = Motion.ListItemDisappearSpec) +
                slideOutVertically(animationSpec = Motion.ListItemDisappearSlideSpec) { _ -> slidePx },
    ) {
        content()
    }
}
