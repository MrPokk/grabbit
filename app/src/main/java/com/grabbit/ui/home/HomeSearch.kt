package com.grabbit.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.core.UiValues
import com.grabbit.data.search.MediaSearchResult
import com.grabbit.ui.components.EmptyState
import com.grabbit.ui.components.ErrorCard
import com.grabbit.ui.components.Thumbnail
import com.grabbit.ui.theme.Dimens

/** Search status, prompt and results for the selected platform. */
internal fun LazyListScope.homeSearchContent(
    state: HomeViewModel.UiState,
    onOpen: (MediaSearchResult) -> Unit,
    onRetry: () -> Unit,
) {
    if (state.searching) {
        item(key = "search-loading") {
            Column(Modifier.fillMaxWidth().padding(top = Dimens.xs)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.searching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.xs),
                )
            }
        }
    }

    state.searchError?.let { error ->
        item(key = "search-error") {
            ErrorCard(error = error, onRetry = onRetry)
        }
    }

    val platform = state.selectedPlatform
    val showPrompt =
        platform != null &&
            !state.searching &&
            state.searchError == null &&
            state.searchResults.isEmpty() &&
            state.media == null &&
            !state.loading &&
            state.error == null
    if (platform != null && showPrompt) {
        item(key = "search-prompt") {
            val platformName = stringResource(platform.labelRes)
            EmptyState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.search_prompt_title, platformName),
                body = stringResource(R.string.search_prompt_body, platformName),
                modifier = Modifier.padding(vertical = Dimens.xl),
            )
        }
    }

    if (state.searchResults.isNotEmpty()) {
        item(key = "search-header") {
            Text(
                text = stringResource(R.string.search_results_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Dimens.xs),
            )
        }
        items(state.searchResults, key = { "search-${it.platform.name}-${it.id}" }) { result ->
            SearchResultRow(result = result, onClick = { onOpen(result) })
        }
    }
}

@Composable
private fun SearchResultRow(
    result: MediaSearchResult,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(Dimens.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(
                url = result.thumbnail,
                modifier =
                    Modifier
                        .width(ListThumbnailWidth)
                        .aspectRatio(UiValues.THUMBNAIL_ASPECT_RATIO),
                shape = MaterialTheme.shapes.small,
            )
            Spacer(Modifier.width(Dimens.xs))
            Column(Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val duration =
                    if (result.durationSeconds > 0) {
                        Formatters.duration(result.durationSeconds)
                    } else {
                        null
                    }
                val meta =
                    when {
                        result.uploader != null && duration != null ->
                            stringResource(R.string.search_result_meta, result.uploader, duration)
                        result.uploader != null -> result.uploader
                        else -> duration
                    }
                if (meta != null) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.cd_open_result, result.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
