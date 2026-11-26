package com.grabbit.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.core.Limits
import com.grabbit.core.Links
import com.grabbit.core.UiValues
import com.grabbit.data.download.MediaType
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.ui.components.EmptyState
import com.grabbit.ui.components.ErrorCard
import com.grabbit.ui.components.QuickDownloadControls
import com.grabbit.ui.components.Thumbnail
import com.grabbit.ui.theme.Dimens

private val CollectionThumbnailWidth = 96.dp

internal fun LazyListScope.homeMediaContent(
    state: HomeViewModel.UiState,
    settings: AppSettings,
    viewModel: HomeViewModel,
) {
    if (state.loading) {
        item(key = "loading") {
            Column(Modifier.fillMaxWidth().padding(top = Dimens.xs)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    text = stringResource(R.string.fetching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    state.error?.let { error ->
        item(key = "error") {
            ErrorCard(
                error = error,
                onRetry = viewModel::fetch,
                onUpdate = viewModel::updateYtDlpAndRetry,
            )
        }
    }

    when (val media = state.media) {
        is FetchedMedia.Single -> {
            item(key = "preview") {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    PreviewCard(
                        media = media,
                        settings = settings,
                        onChooseFormat = viewModel::openSheet,
                        onQuickDownload = viewModel::quickDownload,
                        onMediaTypeChange = viewModel::setQuickMediaType,
                    )
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
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
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
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
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
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    QuickDownloadControls(
                        mediaType = settings.quickMediaType,
                        enabled = state.selectedIndices.isNotEmpty(),
                        onMediaTypeChange = viewModel::setQuickMediaType,
                        onQuickDownload = viewModel::quickDownload,
                    )
                    Button(
                        onClick = viewModel::openSheet,
                        enabled = state.selectedIndices.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(Dimens.xs))
                        Text(
                            stringResource(
                                R.string.playlist_download_selected,
                                state.selectedIndices.size,
                            ),
                        )
                    }
                }
            }
        }

        null -> {
            if (!state.loading && state.error == null) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Movie,
                        title = stringResource(R.string.empty_hint_title),
                        body = stringResource(R.string.empty_hint_body),
                        modifier = Modifier.padding(vertical = Dimens.xl),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PreviewCard(
    media: FetchedMedia.Single,
    settings: AppSettings,
    onChooseFormat: () -> Unit,
    onQuickDownload: () -> Unit,
    onMediaTypeChange: (MediaType) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Thumbnail(
                url = media.thumbnail,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(UiValues.THUMBNAIL_ASPECT_RATIO),
                shape = MaterialTheme.shapes.extraSmall,
            )
            Column(Modifier.padding(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    MetaChip(Formatters.duration(media.info.durationSeconds))
                    MetaChip(
                        pluralStringResource(
                            R.plurals.formats_count,
                            media.info.formats.size,
                            media.info.formats.size,
                        ),
                    )
                }
                QuickDownloadControls(
                    mediaType = settings.quickMediaType,
                    enabled = true,
                    onMediaTypeChange = onMediaTypeChange,
                    onQuickDownload = onQuickDownload,
                )
                OutlinedButton(onClick = onChooseFormat, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(Dimens.xs))
                    Text(stringResource(R.string.action_choose_format))
                }
            }
        }
    }
}

@Composable
internal fun CollectionCard(media: FetchedMedia.Collection) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Dimens.md)) {
            Thumbnail(
                url = media.thumbnail,
                modifier =
                    Modifier
                        .width(CollectionThumbnailWidth)
                        .aspectRatio(UiValues.THUMBNAIL_ASPECT_RATIO),
            )
            Spacer(Modifier.width(Dimens.sm))
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
                Spacer(Modifier.height(Dimens.xxs))
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
internal fun MixToggleCard(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onToggle() }
                    .padding(horizontal = Dimens.md, vertical = Dimens.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = stringResource(R.string.mix_toggle_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.mix_toggle_desc,
                            Limits.MIX_PLAYLIST_END,
                            Limits.MIX_PLAYLIST_END,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Dimens.sm))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}
