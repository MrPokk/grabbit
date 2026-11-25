package com.grabbit.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.core.UiValues
import com.grabbit.data.download.DownloadTask
import com.grabbit.data.download.TaskStatus
import com.grabbit.ui.components.Thumbnail
import com.grabbit.ui.theme.Dimens

private val TaskThumbnailWidth = 88.dp

@Composable
internal fun TaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val resources = LocalResources.current
    val statusText = statusText(task)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (task.status == TaskStatus.FAILED) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
    ) {
        Column(Modifier.padding(Dimens.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(
                    url = task.thumbnail,
                    modifier =
                        Modifier
                            .width(TaskThumbnailWidth)
                            .aspectRatio(UiValues.THUMBNAIL_ASPECT_RATIO),
                )
                Spacer(Modifier.width(Dimens.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Dimens.xxs))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (task.status == TaskStatus.FAILED) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                TaskActions(
                    task = task,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onRemove = onRemove,
                )
            }

            if (task.status.isActive) {
                Spacer(Modifier.height(Dimens.xs))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Dimens.xxs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text =
                            stringResource(
                                R.string.queue_progress_percent,
                                Formatters.percent(task.progress),
                            ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    task.speed?.let { speed ->
                        Text(text = speed, style = MaterialTheme.typography.labelMedium)
                    }
                    task.etaSeconds?.let { eta ->
                        Text(
                            text = stringResource(R.string.eta_label, Formatters.duration(eta)),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (task.status == TaskStatus.COMPLETED) {
                task.fileName?.let { name ->
                    Spacer(Modifier.height(Dimens.xxs))
                    Text(
                        text =
                            stringResource(
                                R.string.queue_file_info,
                                name,
                                Formatters.bytes(resources, task.sizeBytes),
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (task.status == TaskStatus.FAILED && task.error != null) {
                Spacer(Modifier.height(Dimens.xxs))
                Text(
                    text = stringResource(task.error.messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
internal fun TaskActions(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    Row {
        when (task.status) {
            TaskStatus.QUEUED, TaskStatus.PREPARING, TaskStatus.DOWNLOADING,
            TaskStatus.CONVERTING, TaskStatus.SAVING,
            -> {
                IconButton(onClick = onPause) {
                    Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.action_pause))
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }

            TaskStatus.PAUSED -> {
                IconButton(onClick = onResume) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_resume))
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                }
            }

            TaskStatus.FAILED -> {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove))
                }
            }

            TaskStatus.COMPLETED, TaskStatus.CANCELLED -> {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove))
                }
            }
        }
    }
}

@Composable
internal fun statusText(task: DownloadTask): String =
    when (task.status) {
        TaskStatus.QUEUED -> stringResource(R.string.status_queued)
        TaskStatus.PREPARING -> stringResource(R.string.status_preparing)
        TaskStatus.DOWNLOADING -> {
            val eta = task.etaSeconds?.let { stringResource(R.string.eta_label, Formatters.duration(it)) }
            val details = listOfNotNull(task.speed, eta)
            details.fold(stringResource(R.string.status_downloading)) { status, detail ->
                stringResource(R.string.status_downloading_details, status, detail)
            }
        }

        TaskStatus.CONVERTING -> stringResource(R.string.status_converting)
        TaskStatus.SAVING -> stringResource(R.string.status_saving)
        TaskStatus.COMPLETED -> stringResource(R.string.status_completed)
        TaskStatus.PAUSED -> stringResource(R.string.status_paused)
        TaskStatus.CANCELLED -> stringResource(R.string.status_cancelled)
        TaskStatus.FAILED -> stringResource(R.string.status_failed)
    }
