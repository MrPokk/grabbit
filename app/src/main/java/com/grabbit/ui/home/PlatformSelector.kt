package com.grabbit.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grabbit.R
import com.grabbit.core.MediaPlatform
import com.grabbit.ui.theme.Dimens
import com.grabbit.ui.theme.Motion

/** Brand assets and labels of the selectable search platforms. */
@get:StringRes
internal val MediaPlatform.labelRes: Int
    get() =
        when (this) {
            MediaPlatform.YOUTUBE -> R.string.platform_youtube
            MediaPlatform.TIKTOK -> R.string.platform_tiktok
            MediaPlatform.INSTAGRAM -> R.string.platform_instagram
        }

@get:DrawableRes
internal val MediaPlatform.iconRes: Int
    get() =
        when (this) {
            MediaPlatform.YOUTUBE -> R.drawable.ic_platform_youtube
            MediaPlatform.TIKTOK -> R.drawable.ic_platform_tiktok
            MediaPlatform.INSTAGRAM -> R.drawable.ic_platform_instagram
        }

/** Size of a platform selection chip. */
private val PlatformChipSize: Dp = 40.dp

/** Size of the monochrome icon inside a platform chip. */
private val PlatformIconSize: Dp = 24.dp

/** Icons above the input; the active platform leaves this row for the input's trailing slot. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PlatformSelectorRow(
    sharedScope: SharedTransitionScope,
    selected: MediaPlatform?,
    onPlatformClick: (MediaPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaPlatform.entries.forEach { platform ->
            AnimatedVisibility(
                visible = selected != platform,
                enter = fadeIn(animationSpec = Motion.PlatformTransitionSpec),
                exit = fadeOut(animationSpec = Motion.PlatformTransitionSpec),
            ) {
                PlatformChip(
                    sharedScope = sharedScope,
                    animatedVisibilityScope = this@AnimatedVisibility,
                    platform = platform,
                    isSelected = false,
                    contentDescription =
                        stringResource(
                            R.string.cd_platform_activate,
                            stringResource(platform.labelRes),
                        ),
                    onClick = { onPlatformClick(platform) },
                )
            }
        }
    }
}

/** Active platform shown at the trailing edge of the input; tapping it deselects the platform. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PlatformTrailingIcons(
    sharedScope: SharedTransitionScope,
    selected: MediaPlatform?,
    onPlatformClick: (MediaPlatform) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MediaPlatform.entries.forEach { platform ->
            AnimatedVisibility(
                visible = selected == platform,
                enter = fadeIn(animationSpec = Motion.PlatformTransitionSpec),
                exit = fadeOut(animationSpec = Motion.PlatformTransitionSpec),
            ) {
                PlatformChip(
                    sharedScope = sharedScope,
                    animatedVisibilityScope = this@AnimatedVisibility,
                    platform = platform,
                    isSelected = true,
                    contentDescription =
                        stringResource(
                            R.string.cd_platform_deactivate,
                            stringResource(platform.labelRes),
                        ),
                    onClick = { onPlatformClick(platform) },
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlatformChip(
    sharedScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    platform: MediaPlatform,
    isSelected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    /** Each chip animates its own state from its own [isSelected], independent of the indicator. */
    val selected by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = Motion.PlatformChangeSpec,
        label = "platformSelected${platform.name}",
    )
    val alpha =
        Motion.PlatformInactiveAlpha + (Motion.PlatformActiveAlpha - Motion.PlatformInactiveAlpha) * selected
    val scale =
        Motion.PlatformInactiveScale + (Motion.PlatformActiveScale - Motion.PlatformInactiveScale) * selected
    val surfaceColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val tintColor by animateColorAsState(
        targetValue =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = Motion.PlatformColorSpec,
        label = "platformTint${platform.name}",
    )
    Surface(
        onClick = onClick,
        modifier =
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(platform.name),
                    animatedVisibilityScope = animatedVisibilityScope,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            }.size(PlatformChipSize),
        shape = CircleShape,
        color = surfaceColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PlatformIcon(
                platform = platform,
                iconAlpha = alpha,
                iconScale = scale,
                tintColor = tintColor,
                contentDescription = contentDescription,
                modifier = Modifier.size(PlatformIconSize),
            )
        }
    }
}

@Composable
private fun PlatformIcon(
    platform: MediaPlatform,
    iconAlpha: Float,
    iconScale: Float,
    tintColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    /** Monochrome vector drawable; tinted via tintColor (inactive: onSurfaceVariant, active: primary). */
    val iconModifier =
        modifier
            .graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
                alpha = iconAlpha
            }
    Image(
        painter = painterResource(platform.iconRes),
        contentDescription = contentDescription,
        modifier = iconModifier,
        colorFilter = ColorFilter.tint(tintColor),
    )
}
