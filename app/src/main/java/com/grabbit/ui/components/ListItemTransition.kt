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
