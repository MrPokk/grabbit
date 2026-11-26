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
import androidx.compose.ui.res.pluralStringResource
import com.grabbit.R
import com.grabbit.data.download.QueueSummary
import com.grabbit.ui.theme.Dimens

@Composable
fun DownloadProgressCircle(
    summary: QueueSummary,
    badgeAlignment: Alignment = Alignment.TopEnd,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = Dimens.xxs,
            shadowElevation = Dimens.xxs,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (summary.indeterminate) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = Dimens.xxs,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { summary.progress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = Dimens.xxs,
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
                    modifier = Modifier.size(Dimens.iconMd),
                )
            }
        }
        if (summary.count > 1) {
            Badge(
                modifier = Modifier.align(badgeAlignment).padding(Dimens.xxs),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(text = summary.count.toString())
            }
        }
    }
}
