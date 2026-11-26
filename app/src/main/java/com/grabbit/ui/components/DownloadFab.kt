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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import com.grabbit.R
import com.grabbit.data.download.QueueSummary
import com.grabbit.ui.theme.Dimens

/** Circular download button showing queue progress; tap opens the queue. */
@Composable
fun DownloadFab(
    summary: QueueSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(Dimens.iconLg),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (summary.indeterminate) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { summary.progress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
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
                modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.xxs),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(text = summary.count.toString())
            }
        }
    }
}
