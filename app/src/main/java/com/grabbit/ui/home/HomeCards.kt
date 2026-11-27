package com.grabbit.ui.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.core.MediaPlatform
import com.grabbit.core.UiValues
import com.grabbit.data.ytdlp.PlaylistItem
import com.grabbit.ui.components.Thumbnail
import com.grabbit.ui.theme.Dimens

private val PlaylistThumbnailWidth = 72.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun UrlCard(
    state: HomeViewModel.UiState,
    onUrlChange: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onPlatformClick: (MediaPlatform) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = state.url
    val loading = state.loading
    val searching = state.searching
    val clipboardHint = state.clipboardHint
    val selectedPlatform = state.selectedPlatform
    val noIndicator = MaterialTheme.colorScheme.onSurface.copy(alpha = 0f)
    val searchMode = selectedPlatform != null
    val platformName = selectedPlatform?.let { stringResource(it.labelRes) }
    val busy = loading || searching
    SharedTransitionLayout(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            PlatformSelectorRow(
                sharedScope = this@SharedTransitionLayout,
                selected = selectedPlatform,
                onPlatformClick = onPlatformClick,
                modifier = Modifier.padding(start = Dimens.xxs),
            )

            TextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = noIndicator,
                        unfocusedIndicatorColor = noIndicator,
                        disabledIndicatorColor = noIndicator,
                        errorIndicatorColor = noIndicator,
                    ),
                label = {
                    Text(
                        text =
                            if (platformName != null) {
                                stringResource(R.string.search_hint, platformName)
                            } else {
                                stringResource(R.string.url_hint)
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = if (searchMode) Icons.Rounded.Search else Icons.Rounded.Link,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    Row(
                        modifier = Modifier.padding(end = Dimens.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (url.isNotEmpty()) {
                            IconButton(onClick = onClear) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear))
                            }
                        }
                        PlatformTrailingIcons(
                            sharedScope = this@SharedTransitionLayout,
                            selected = selectedPlatform,
                            onPlatformClick = onPlatformClick,
                        )
                    }
                },
                supportingText =
                    if (clipboardHint && !searchMode) {
                        { Text(stringResource(R.string.clipboard_hint)) }
                    } else {
                        null
                    },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = if (searchMode) KeyboardType.Text else KeyboardType.Uri,
                        imeAction = if (searchMode) ImeAction.Search else ImeAction.Go,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onGo = { onSubmit() },
                        onSearch = { onSubmit() },
                    ),
            )

            UrlActionRow(
                onPaste = onPaste,
                onSubmit = onSubmit,
                url = url,
                busy = busy,
                searchMode = searchMode,
                loading = loading,
                searching = searching,
            )
        }
    }
}

@Composable
private fun UrlActionRow(
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
    url: String,
    busy: Boolean,
    searchMode: Boolean,
    loading: Boolean,
    searching: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        OutlinedButton(
            onClick = onPaste,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                Icons.Rounded.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSm),
            )
            Spacer(Modifier.width(Dimens.xxs))
            Text(stringResource(R.string.action_paste))
        }
        Button(
            onClick = onSubmit,
            enabled = url.isNotBlank() && !busy,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = if (searchMode) Icons.Rounded.Search else Icons.Rounded.Download,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSm),
            )
            Spacer(Modifier.width(Dimens.xxs))
            Text(
                text =
                    stringResource(
                        when {
                            loading -> R.string.fetching
                            searching -> R.string.searching
                            searchMode -> R.string.action_search
                            else -> R.string.action_fetch
                        },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PlaylistRow(
    item: PlaylistItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
    ) {
        Row(
            modifier = Modifier.padding(Dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Thumbnail(
                url = item.thumbnail,
                modifier =
                    Modifier
                        .width(PlaylistThumbnailWidth)
                        .aspectRatio(UiValues.THUMBNAIL_ASPECT_RATIO),
                shape = MaterialTheme.shapes.small,
            )
            Spacer(Modifier.width(Dimens.xs))
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
internal fun MetaChip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.xs, vertical = Dimens.xxs),
        )
    }
}
