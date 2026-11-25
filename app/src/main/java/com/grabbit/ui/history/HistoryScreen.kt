package com.grabbit.ui.history

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.data.db.HistoryEntity
import com.grabbit.ui.components.EmptyState
import com.grabbit.ui.components.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<HistoryEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.history_title)) },
            scrollBehavior = scrollBehavior,
        )

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.history_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
        )

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History,
                title = stringResource(
                    if (query.isBlank()) R.string.history_empty_title else R.string.history_empty_search,
                ),
                body = stringResource(R.string.history_empty_body),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { entity ->
                    HistoryRow(
                        entity = entity,
                        onOpen = { openFile(context, entity) },
                        onShare = { shareFile(context, entity) },
                        onRedownload = { viewModel.redownload(entity) },
                        onDelete = { pendingDelete = entity },
                    )
                }
            }
        }
    }

    pendingDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(entity)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.action_remove_record)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun HistoryRow(
    entity: HistoryEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val relativeDate = remember(entity.createdAt) {
        DateUtils.getRelativeTimeSpanString(
            entity.createdAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(
            url = entity.thumbnail,
            modifier = Modifier
                .width(88.dp)
                .aspectRatio(16f / 9f),
            corner = 10.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entity.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.history_meta,
                    entity.formatLabel,
                    Formatters.bytes(entity.sizeBytes),
                    relativeDate,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_open)) },
                leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onOpen()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_redownload)) },
                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRedownload()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

private fun openFile(context: Context, entity: HistoryEntity) {
    val uri = entity.uri.toUri()
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, entity.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.file_missing, Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, entity: HistoryEntity) {
    val uri: Uri = entity.uri.toUri()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = entity.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null))
    }
}
