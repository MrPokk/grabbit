package com.grabbit.ui.home

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
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.core.UiValues
import com.grabbit.data.ytdlp.PlaylistItem
import com.grabbit.ui.components.Thumbnail
import com.grabbit.ui.theme.Dimens

private val PlaylistThumbnailWidth = 72.dp

@Composable
internal fun UrlCard(
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
            modifier = Modifier.padding(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
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
                supportingText =
                    if (clipboardHint) {
                        { Text(stringResource(R.string.clipboard_hint)) }
                    } else {
                        null
                    },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                keyboardActions = KeyboardActions(onGo = { onFetch() }),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                OutlinedButton(
                    onClick = onPaste,
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
                    onClick = onFetch,
                    enabled = url.isNotBlank() && !loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSm),
                    )
                    Spacer(Modifier.width(Dimens.xxs))
                    Text(stringResource(if (loading) R.string.fetching else R.string.action_fetch))
                }
            }
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
