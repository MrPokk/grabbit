package com.grabbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.grabbit.R
import com.grabbit.core.Timing
import com.grabbit.core.UiValues
import com.grabbit.ui.Routes
import com.grabbit.ui.theme.Dimens

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

    Box(modifier.fillMaxWidth()) {
        NavigationBar {
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
        FloatingActionButton(
            onClick = { if (!downloadSelected) onNavigate(Routes.DOWNLOAD) },
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -Dimens.sm)
                    .size(Dimens.iconLg)
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
