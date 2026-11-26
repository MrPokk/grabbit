package com.grabbit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.Dp
import com.grabbit.R
import com.grabbit.data.download.QueueSummary
import com.grabbit.ui.theme.Dimens

@Composable
fun DownloadProgressCircle(
    summary: QueueSummary,
    badgeAlignment: Alignment = Alignment.TopEnd,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    contentAlpha: Float = 1f,
    contentScale: Float = 1f,
    shadowElevation: Dp = Dimens.xxs,
    containerVisible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val content =
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }

    Box(modifier) {
        if (containerVisible) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = Dimens.xxs,
                shadowElevation = shadowElevation,
            ) {
                DownloadProgressContent(summary, contentColor, content, contentScale)
            }
        } else {
            DownloadProgressContent(summary, contentColor, content, contentScale)
        }
        if (summary.count > 1) {
            Badge(
                modifier =
                    Modifier
                        .align(badgeAlignment)
                        .padding(Dimens.xxs)
                        .graphicsLayer { alpha = contentAlpha },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(text = summary.count.toString())
            }
        }
    }
}

@Composable
private fun DownloadProgressContent(
    summary: QueueSummary,
    contentColor: Color,
    modifier: Modifier = Modifier,
    contentScale: Float = 1f,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val ring =
            Modifier
                .fillMaxSize()
                .padding(Dimens.gooProgressInset)
        if (summary.indeterminate) {
            CircularProgressIndicator(
                modifier = ring,
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = Dimens.gooProgressStroke,
            )
        } else {
            CircularProgressIndicator(
                progress = { summary.progress },
                modifier = ring,
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = Dimens.gooProgressStroke,
            )
        }
        Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription =
                pluralStringResource(
                    R.plurals.cd_queue,
                    summary.count,
                    summary.count,
                ),
            tint = contentColor,
            modifier = Modifier
                .size(Dimens.gooIconSize)
                .graphicsLayer { scaleX = contentScale; scaleY = contentScale },
        )
    }
}
