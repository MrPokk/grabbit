package com.grabbit.ui.queue

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.core.MimeTypes
import com.grabbit.data.download.DownloadTask
import com.grabbit.data.download.MediaType
import com.grabbit.ui.components.DeleteConfirmationDialog
import com.grabbit.ui.components.DeleteMode
import com.grabbit.ui.components.EmptyState
import com.grabbit.ui.components.ListItemTransition
import com.grabbit.ui.components.openSavedFile
import com.grabbit.ui.components.shareSavedFile
import com.grabbit.ui.components.showSavedFileInFolder
import com.grabbit.ui.theme.Dimens

/** Queue screen with active downloads, task actions and quick access to settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val activeCount = tasks.count { it.status.isActive }
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Pair<DownloadTask, DeleteMode>?>(null) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    Column(modifier.fillMaxSize()) {
        BackHandler(enabled = onCollapse != null) {
            onCollapse?.invoke()
        }
        TopAppBar(
            title = { Text(stringResource(R.string.queue_title)) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            navigationIcon = {
                if (onCollapse == null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            actions = {
                if (onCollapse != null) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
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
                    ListItemTransition {
                        TaskCard(
                            task = task,
                            callbacks =
                                TaskCallbacks(
                                    onPause = { viewModel.pause(task.id) },
                                    onResume = { viewModel.resume(task.id) },
                                    onCancel = { viewModel.cancel(task.id) },
                                    onRetry = { viewModel.retry(task.id) },
                                    onRemove = { viewModel.remove(task.id) },
                                    onOpen = {
                                        task.savedUri?.let { uri -> openSavedFile(context, uri, task.mimeType()) }
                                    },
                                    onShowInFolder = {
                                        task.savedUri?.let { uri -> showSavedFileInFolder(context, uri) }
                                    },
                                    onShare = {
                                        task.savedUri?.let { uri ->
                                            shareSavedFile(context, uri, task.fileName, task.mimeType())
                                        }
                                    },
                                    onRedownload = { viewModel.downloadAgain(task) },
                                    onDelete = { pendingDelete = task to DeleteMode.DELETE_FILE },
                                ),
                        )
                    }
                }
                item(key = "settings") {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                }
            }
        }
    }

    pendingDelete?.let { (task, mode) ->
        DeleteConfirmationDialog(
            mode = mode,
            onConfirm = {
                when (mode) {
                    DeleteMode.REMOVE -> viewModel.remove(task.id)
                    DeleteMode.DELETE_FILE -> viewModel.deleteFromDevice(task)
                }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun DownloadTask.mimeType(): String {
    val extension =
        fileName
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotEmpty() }
            ?: if (options.mediaType == MediaType.AUDIO) options.audioFormat else options.container
    return MimeTypes.forExt(extension)
}
