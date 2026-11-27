package com.grabbit.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The single spacing and icon-size grid used by every screen. */
object Dimens {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val screenHorizontal: Dp = lg
    val xl: Dp = 32.dp

    val iconSm: Dp = 18.dp
    val iconMd: Dp = 28.dp
    val iconLg: Dp = 56.dp
    val iconButton: Dp = 48.dp

    /** Diameter of the docked progress circle; the goo transition starts exactly on its rim. */
    val gooCircleSize: Dp = 36.dp

    /** Contour softness of the alpha field, before the contrast filter sharpens it. */
    val gooTransitionRadius: Dp = 4.dp

    /** Half-length the screen edge is pulled along, up and down from the circle. */
    val gooEdgeSpread: Dp = 65.dp

    /** Thickness of the pulled edge at its centre. */
    val gooEdgeDepth: Dp = 40.dp

    /** Gap between the circle and the screen edge. */
    val gooEdgeGap: Dp = 0.dp

    /** Icon inside [gooCircleSize]; the progress ring already takes the outer few dp. */
    val gooIconSize: Dp = 16.dp

    /** Thin progress stroke so the small circle stays readable. */
    val gooProgressStroke: Dp = 2.dp

    /** Keeps the progress ring off the blob rim so the screen edge cannot eat it. */
    val gooProgressInset: Dp = 4.dp

    /** Goo layer bounds: the whole edge spread plus the pulled tongue's reach. */
    val gooLayerSize: Dp
        get() = maxOf(gooEdgeSpread, gooCircleSize / 2f + gooEdgeDepth + gooTransitionRadius) * 2f
}
