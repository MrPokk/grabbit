package com.grabbit.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

    val platformTrailingOffset: Dp = 2.dp

    val gooCircleSize: Dp = 36.dp

    val gooTransitionRadius: Dp = 4.dp

    val gooEdgeSpread: Dp = 65.dp

    val gooEdgeDepth: Dp = 40.dp

    val gooEdgeGap: Dp = 0.dp

    val gooIconSize: Dp = 16.dp

    val gooProgressStroke: Dp = 2.dp

    val gooProgressInset: Dp = 4.dp

    val gooLayerSize: Dp
        get() = maxOf(gooEdgeSpread, gooCircleSize / 2f + gooEdgeDepth + gooTransitionRadius) * 2f
}
