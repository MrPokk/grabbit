package com.grabbit.ui.history

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.core.UiValues
import com.grabbit.data.db.HistoryEntity
import com.grabbit.ui.components.DownloadActionsMenu
import com.grabbit.ui.components.EmptyState
import com.grabbit.ui.components.Thumbnail
import com.grabbit.ui.components.openSavedFile
import com.grabbit.ui.components.shareSavedFile
import com.grabbit.ui.theme.Dimens

private val HistoryThumbnailWidth = 88.dp

/** History screen with search, saved-file actions and deletion confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<HistoryEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.history_title)) },
            scrollBehavior = scrollBehavior,
        )

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.md),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = viewModel::setQuery,
                onSearch = { _ -> Unit },
                expanded = false,
                onExpandedChange = { _ -> Unit },
                placeholder = { Text(stringResource(R.string.history_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(Dimens.xs))

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History,
                title =
                    stringResource(
                        if (query.isBlank()) R.string.history_empty_title else R.string.history_empty_search,
                    ),
                body = stringResource(R.string.history_empty_body),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = Dimens.md, end = Dimens.md, bottom = Dimens.xl),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs),
            ) {
                items(items, key = { it.id }) { entity ->
                    HistoryRow(
                        entity = entity,
                        onOpen = { openSavedFile(context, entity.uri, entity.mimeType) },
                        onShare = {
                            shareSavedFile(context, entity.uri, entity.fileName, entity.mimeType)
                        },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    entity: HistoryEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val relativeDate =
        remember(entity.createdAt) {
            DateUtils
                .getRelativeTimeSpanString(
                    entity.createdAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
        }

    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = {
                            menuOpen = false
                            onShare()
                        },
                    ).padding(vertical = Dimens.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(
                url = entity.thumbnail,
                modifier =
                    Modifier
                        .width(HistoryThumbnailWidth)
                        .aspectRatio(UiValues.THUMBNAIL_ASPECT_RATIO),
            )
            Spacer(Modifier.width(Dimens.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entity.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            R.string.history_meta,
                            entity.formatLabel,
                            Formatters.bytes(context.resources, entity.sizeBytes),
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
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = Dimens.xxs)
                    .size(Dimens.iconButton),
        ) {
            DownloadActionsMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                onOpen = onOpen,
                onShare = onShare,
                onRedownload = onRedownload,
                onDelete = onDelete,
            )
        }
    }
}
