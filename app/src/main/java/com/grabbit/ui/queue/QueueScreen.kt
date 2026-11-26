package com.grabbit.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.ui.components.EmptyState
import com.grabbit.ui.theme.Dimens

/** Queue screen with active downloads, task actions and quick access to settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val activeCount = tasks.count { it.status.isActive }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.queue_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            actions = {
                if (tasks.any { it.status.isFinished }) {
                    IconButton(onClick = viewModel::clearFinished) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.action_clear_finished),
                        )
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        start = Dimens.md,
                        end = Dimens.md,
                        top = Dimens.xxs,
                        bottom = Dimens.xl,
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs),
            ) {
                if (activeCount > 0) {
                    item(key = "hint") {
                        Text(
                            text =
                                pluralStringResource(
                                    R.plurals.queue_concurrency_hint,
                                    settings.concurrentDownloads,
                                    settings.concurrentDownloads,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Dimens.xxs),
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
