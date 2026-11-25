package com.grabbit.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.core.Links
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.PlaylistItem
import com.grabbit.data.ytdlp.RequestFactory
import com.grabbit.ui.components.EmptyState
import com.grabbit.ui.components.ErrorCard
import com.grabbit.ui.components.Thumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    incomingLink: String?,
    onLinkConsumed: () -> Unit,
    onOpenQueue: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(incomingLink) {
        if (!incomingLink.isNullOrBlank()) {
            viewModel.acceptSharedLink(incomingLink)
            onLinkConsumed()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is HomeViewModel.Event.OpenQueue) onOpenQueue()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.home_title)) },
            scrollBehavior = scrollBehavior,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                item(key = "loading") {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.fetching),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.error?.let { error ->
                item(key = "error") {
                    ErrorCard(
                        error = error,
                        onRetry = viewModel::retry,
                        onUpdate = viewModel::updateYtDlpAndRetry,
                    )
                }
            }

            when (val media = state.media) {
                is FetchedMedia.Single -> {
                    item(key = "preview") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PreviewCard(media = media, onDownload = viewModel::openSheet)
                            if (Links.isRadioPlaylist(state.urlForPlaylistToggle)) {
                                MixToggleCard(
                                    checked = state.loadAsMix,
                                    onToggle = viewModel::toggleLoadAsMix,
                                )
                            }
                        }
                    }
                }

                is FetchedMedia.Collection -> {
                    item(key = "collection") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CollectionCard(media = media)
                            if (Links.isRadioPlaylist(state.urlForPlaylistToggle)) {
                                MixToggleCard(
                                    checked = state.loadAsMix,
                                    onToggle = viewModel::toggleLoadAsMix,
                                )
                            }
                        }
                    }
                    item(key = "select") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = viewModel::selectAll,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.playlist_select_all)) }
                            OutlinedButton(
                                onClick = viewModel::deselectAll,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.playlist_deselect_all)) }
                        }
                    }
                    items(media.items, key = { "${it.index}-${it.id}" }) { item ->
                        PlaylistRow(
                            item = item,
                            selected = item.index in state.selectedIndices,
                            onClick = { viewModel.toggleSelection(item.index) },
                        )
                    }
                    item(key = "download") {
                        Button(
                            onClick = viewModel::openSheet,
                            enabled = state.selectedIndices.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    R.string.playlist_download_selected,
                                    state.selectedIndices.size,
                                ),
                            )
                        }
                    }
                }

                null -> {
                    if (!state.loading && state.error == null) {
                        item(key = "empty") {
                            EmptyHint()
                        }
                    }
                }
            }
        }

        UrlCard(
            url = state.url,
            loading = state.loading,
            clipboardHint = state.clipboardHint,
            onUrlChange = viewModel::onUrlChange,
            onClear = viewModel::clear,
            onPaste = viewModel::pasteFromClipboard,
            onFetch = viewModel::fetch,
            modifier = Modifier.imePadding(),
        )
    }

    if (state.showSheet && state.media != null) {
        FormatSheet(
            media = state.media!!,
            defaults = settings,
            onDismiss = viewModel::dismissSheet,
            onConfirm = viewModel::startDownload,
        )
    }
}

@Composable
private fun UrlCard(
    url: String,
    loading: Boolean,
    clipboardHint: Boolean,
    onUrlChange: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onFetch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.url_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
                supportingText = if (clipboardHint) {
                    { Text(stringResource(R.string.clipboard_hint)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onFetch() }),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPaste,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_paste))
                }
                Button(
                    onClick = onFetch,
                    enabled = url.isNotBlank() && !loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (loading) R.string.fetching else R.string.action_fetch))
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(media: FetchedMedia.Single, onDownload: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Thumbnail(
                url = media.thumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                corner = 0.dp,
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media.uploader ?: stringResource(R.string.unknown_channel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaChip(Formatters.duration(media.info.durationSeconds))
                    MetaChip(
                        pluralStringResource(
                            R.plurals.formats_count,
                            media.info.formats.size,
                            media.info.formats.size,
                        ),
                    )
                }
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_choose_format))
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(media: FetchedMedia.Collection) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Thumbnail(
                url = media.thumbnail,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media.uploader ?: stringResource(R.string.unknown_channel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                MetaChip(pluralStringResource(R.plurals.playlist_items, media.items.size, media.items.size))
                if (media.truncated) {
                    Text(
                        text = pluralStringResource(R.plurals.playlist_limited, media.items.size, media.items.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MixToggleCard(checked: Boolean, onToggle: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = stringResource(R.string.mix_toggle_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = pluralStringResource(
                        R.plurals.mix_toggle_desc,
                        com.grabbit.data.ytdlp.RequestFactory.MIX_PLAYLIST_END,
                        com.grabbit.data.ytdlp.RequestFactory.MIX_PLAYLIST_END,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun PlaylistRow(item: PlaylistItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Thumbnail(
                url = item.thumbnail,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(16f / 9f),
                corner = 8.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.durationSeconds > 0) {
                    Text(
                        text = Formatters.duration(item.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_hint_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_hint_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
