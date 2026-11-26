package com.grabbit.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.grabbit.R
import com.grabbit.core.MediaPlatform
import com.grabbit.core.Timing
import com.grabbit.ui.theme.Dimens

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

private val MediaPlatform.sharedKey: String get() = "platform-$name"

private val PlatformFade =
    tween<Float>(
        durationMillis = Timing.PLATFORM_TRANSITION_DURATION_MS,
        easing = FastOutSlowInEasing,
    )

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
                enter = fadeIn(PlatformFade),
                exit = fadeOut(PlatformFade),
            ) {
                PlatformChip(
                    platform = platform,
                    active = false,
                    contentDescription =
                        stringResource(
                            R.string.cd_platform_activate,
                            stringResource(platform.labelRes),
                        ),
                    onClick = { onPlatformClick(platform) },
                    modifier =
                        with(sharedScope) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(platform.sharedKey),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                        },
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
                enter = fadeIn(PlatformFade),
                exit = fadeOut(PlatformFade),
            ) {
                PlatformChip(
                    platform = platform,
                    active = true,
                    contentDescription =
                        stringResource(
                            R.string.cd_platform_deactivate,
                            stringResource(platform.labelRes),
                        ),
                    onClick = { onPlatformClick(platform) },
                    modifier =
                        with(sharedScope) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(platform.sharedKey),
                                animatedVisibilityScope = this@AnimatedVisibility,
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun PlatformChip(
    platform: MediaPlatform,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(Dimens.iconButton),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(contentAlignment = Alignment.Center) {
            PlatformIcon(
                platform = platform,
                active = active,
                contentDescription = contentDescription,
                modifier = Modifier.size(Dimens.iconMd),
            )
        }
    }
}

@Composable
private fun PlatformIcon(
    platform: MediaPlatform,
    active: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var colored by remember { mutableStateOf(false) }
    LaunchedEffect(active) { colored = active }
    val saturation by animateFloatAsState(
        targetValue = if (colored) 1f else 0f,
        animationSpec = tween(durationMillis = Timing.PLATFORM_COLOR_DURATION_MS),
        label = "platformSaturation",
    )
    val colorMatrix =
        remember(saturation) {
            ColorMatrix().apply { setToSaturation(saturation) }
        }
    Image(
        painter = painterResource(platform.iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.colorMatrix(colorMatrix),
    )
}
