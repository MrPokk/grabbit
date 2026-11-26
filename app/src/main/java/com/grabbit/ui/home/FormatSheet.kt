package com.grabbit.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.grabbit.R
import com.grabbit.core.UiValues
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.MediaType
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.toDownloadOptions
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.availableHeights
import com.grabbit.ui.theme.Dimens

/** Bottom sheet with format, quality and extra options for the current media. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormatSheet(
    media: FetchedMedia,
    defaults: AppSettings,
    onDismiss: () -> Unit,
    onConfirm: (DownloadOptions) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val heights = media.availableHeights()
    val initial = defaults.toDownloadOptions(MediaType.VIDEO, heights)

    var options by remember { mutableStateOf(initial) }
    var mediaType by remember { mutableStateOf(initial.mediaType) }
    var videoHeight by remember { mutableStateOf(initial.maxHeight) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.lg)
                    .padding(bottom = Dimens.iconMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.xs),
        ) {
            Text(
                text = stringResource(R.string.sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                FilterChip(
                    selected = mediaType == MediaType.VIDEO,
                    onClick = {
                        mediaType = MediaType.VIDEO
                    },
                    label = { Text(stringResource(R.string.tab_video)) },
                )
                FilterChip(
                    selected = mediaType == MediaType.AUDIO,
                    onClick = {
                        mediaType = MediaType.AUDIO
                    },
                    label = { Text(stringResource(R.string.tab_audio)) },
                )
            }

            if (mediaType == MediaType.VIDEO) {
                Label(stringResource(R.string.label_resolution))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                ) {
                    heights.forEach { option ->
                        FilterChip(
                            selected = videoHeight == option,
                            onClick = { videoHeight = option },
                            label = { Text("${option}p") },
                        )
                    }
                }
                Label(stringResource(R.string.label_container))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    UiValues.CONTAINERS.forEach { option ->
                        FilterChip(
                            selected = options.container == option,
                            onClick = { options = options.copy(container = option) },
                            label = { Text(option.uppercase()) },
                        )
                    }
                }
            } else {
                Label(stringResource(R.string.label_audio_format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    UiValues.AUDIO_FORMATS.forEach { option ->
                        FilterChip(
                            selected = options.audioFormat == option,
                            onClick = { options = options.copy(audioFormat = option) },
                            label = { Text(option.uppercase()) },
                        )
                    }
                }
                Label(stringResource(R.string.label_bitrate))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    UiValues.BITRATES.forEach { option ->
                        FilterChip(
                            selected = options.audioBitrate == option,
                            onClick = { options = options.copy(audioBitrate = option) },
                            label = { Text(stringResource(R.string.bitrate_kbps, option)) },
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = Dimens.xxs))
            Label(stringResource(R.string.label_extras))
            ToggleRow(
                stringResource(R.string.extra_thumbnail),
                options.embedThumbnail,
            ) { options = options.copy(embedThumbnail = it) }
            ToggleRow(
                stringResource(R.string.extra_metadata),
                options.embedMetadata,
            ) { options = options.copy(embedMetadata = it) }
            ToggleRow(
                stringResource(R.string.extra_subtitles),
                options.subtitles,
            ) { options = options.copy(subtitles = it) }
            ToggleRow(
                stringResource(R.string.extra_sponsorblock),
                options.sponsorBlock,
            ) { options = options.copy(sponsorBlock = it) }

            Spacer(Modifier.height(Dimens.xs))
            Button(
                onClick = {
                    onConfirm(
                        options.copy(
                            mediaType = mediaType,
                            maxHeight = if (mediaType == MediaType.VIDEO) videoHeight else 0,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_start_download))
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.xxs),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = Dimens.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Dimens.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
