package com.grabbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grabbit.R
import com.grabbit.core.Timing
import com.grabbit.core.UiValues
import com.grabbit.ui.Routes
import com.grabbit.ui.theme.Dimens

private val FabOffset = -Dimens.sm
private val CradleLift = Dimens.xxs
private val FabSize = Dimens.iconLg + Dimens.sm

@Composable
fun AppBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadSelected = currentRoute == Routes.DOWNLOAD
    val historySelected = currentRoute == Routes.HISTORY
    val settingsSelected = currentRoute == Routes.SETTINGS
    val colorAnimationSpec = tween<Color>(Timing.NAV_ANIMATION_DURATION_MS)
    val scaleAnimationSpec = tween<Float>(Timing.NAV_ANIMATION_DURATION_MS)
    val downloadContainer by
        animateColorAsState(
            targetValue =
                if (downloadSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            animationSpec = colorAnimationSpec,
            label = "downloadContainerColor",
        )
    val downloadContent by
        animateColorAsState(
            targetValue =
                if (downloadSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            animationSpec = colorAnimationSpec,
            label = "downloadContentColor",
        )
    val downloadScale by
        animateFloatAsState(
            targetValue =
                if (downloadSelected) 1f else UiValues.NAV_DOWNLOAD_UNSELECTED_SCALE,
            animationSpec = scaleAnimationSpec,
            label = "downloadScale",
        )
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val ringOffset by
        animateDpAsState(
            targetValue = FabOffset - CradleLift - if (downloadSelected) Dimens.xxs else 0.dp,
            animationSpec = tween(Timing.NAV_ANIMATION_DURATION_MS),
            label = "ringOffset",
        )
    val ringSize by
        animateDpAsState(
            targetValue = if (downloadSelected) FabSize + Dimens.sm * 2 else FabSize + Dimens.sm,
            animationSpec = tween(Timing.NAV_ANIMATION_DURATION_MS),
            label = "ringSize",
        )

    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) { drawRect(color = barColor) }
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NavigationBarItem(
                selected = historySelected,
                onClick = { if (!historySelected) onNavigate(Routes.HISTORY) },
                icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_history)) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.weight(1f))
            NavigationBarItem(
                selected = settingsSelected,
                onClick = { if (!settingsSelected) onNavigate(Routes.SETTINGS) },
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings)) },
                modifier = Modifier.weight(1f),
            )
        }
        // Ring of the bar color behind the download button, centered on it.
        Surface(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = ringOffset)
                    .size(ringSize),
            shape = CircleShape,
            color = barColor,
        ) {
        }
        FloatingActionButton(
            onClick = { if (!downloadSelected) onNavigate(Routes.DOWNLOAD) },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = FabOffset)
                    .size(FabSize)
                    .graphicsLayer {
                        scaleX = downloadScale
                        scaleY = downloadScale
                    },
            shape = CircleShape,
            containerColor = downloadContainer,
            contentColor = downloadContent,
            elevation = FloatingActionButtonDefaults.elevation(),
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.nav_download),
                modifier = Modifier.size(Dimens.iconMd),
            )
        }
    }
}
