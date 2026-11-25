package com.grabbit.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.data.download.DownloadTask
import com.grabbit.data.download.TaskStatus
import com.grabbit.ui.components.EmptyState
import com.grabbit.ui.components.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onOpenSettings: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val activeCount = tasks.count { it.status.isActive }

    Column(Modifier.fillMaxSize()) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.queue_title)) },
            scrollBehavior = scrollBehavior,
            actions = {
                if (tasks.any { it.status.isFinished }) {
                    IconButton(onClick = viewModel::clearFinished) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = stringResource(R.string.action_clear_finished))
                    }
                }
            },
        )

        if (tasks.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.DownloadDone,
                title = stringResource(R.string.queue_empty_title),
                body = stringResource(R.string.queue_empty_body),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (activeCount > 0) {
                    item(key = "hint") {
                        Text(
                            text = pluralStringResource(
                                R.plurals.queue_concurrency_hint,
                                settings.concurrentDownloads,
                                settings.concurrentDownloads,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onCancel = { viewModel.cancel(task.id) },
                        onRetry = { viewModel.retry(task.id) },
                        onRemove = { viewModel.remove(task.id) },
                    )
                }
                item(key = "settings") {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val statusText = statusText(task)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.status == TaskStatus.FAILED) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(
                    url = task.thumbnail,
                    modifier = Modifier
                        .width(88.dp)
                        .aspectRatio(16f / 9f),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (task.status == TaskStatus.FAILED) {
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
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${Formatters.percent(task.progress)}%",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    task.speed?.let { speed ->
                        Text(text = speed, style = MaterialTheme.typography.labelMedium)
                    }
                    task.etaSeconds?.let { eta ->
                        Text(
                            text = stringResource(R.string.eta_label, Formatters.eta(eta)),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (task.status == TaskStatus.COMPLETED) {
                task.fileName?.let { name ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "$name · ${Formatters.bytes(task.sizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (task.status == TaskStatus.FAILED && task.error != null) {
                Spacer(Modifier.height(6.dp))
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
private fun TaskActions(
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
private fun statusText(task: DownloadTask): String = when (task.status) {
    TaskStatus.QUEUED -> stringResource(R.string.status_queued)
    TaskStatus.PREPARING -> stringResource(R.string.status_preparing)
    TaskStatus.DOWNLOADING -> {
        val parts = mutableListOf(stringResource(R.string.status_downloading))
        task.speed?.let { parts += it }
        task.etaSeconds?.let { parts += stringResource(R.string.eta_label, Formatters.eta(it)) }
        parts.joinToString(" · ")
    }

    TaskStatus.CONVERTING -> stringResource(R.string.status_converting)
    TaskStatus.SAVING -> stringResource(R.string.status_saving)
    TaskStatus.COMPLETED -> stringResource(R.string.status_completed)
    TaskStatus.PAUSED -> stringResource(R.string.status_paused)
    TaskStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    TaskStatus.FAILED -> stringResource(R.string.status_failed)
}
