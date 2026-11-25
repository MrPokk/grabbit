package com.grabbit.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.grabbit.R
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.MediaType
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.ytdlp.FetchedMedia

private val audioFormats = listOf("mp3", "m4a", "opus", "flac")
private val bitrates = listOf(128, 192, 256, 320)
private val containers = listOf("mp4", "mkv", "webm")
private val fallbackHeights = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormatSheet(
    media: FetchedMedia,
    defaults: AppSettings,
    onDismiss: () -> Unit,
    onConfirm: (DownloadOptions) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val heights = (media as? FetchedMedia.Single)
        ?.info?.videoHeights
        ?.takeIf { it.isNotEmpty() }
        ?: fallbackHeights

    var mediaType by remember { mutableStateOf(MediaType.VIDEO) }
    var height by remember {
        mutableStateOf(heights.firstOrNull { it <= defaults.defaultVideoHeight } ?: heights.first())
    }
    var container by remember {
        mutableStateOf(defaults.defaultVideoContainer.takeIf { it in containers } ?: "mp4")
    }
    var audioFormat by remember {
        mutableStateOf(defaults.defaultAudioFormat.takeIf { it in audioFormats } ?: "mp3")
    }
    var bitrate by remember {
        mutableStateOf(defaults.defaultAudioBitrate.takeIf { it in bitrates } ?: 192)
    }
    var embedThumbnail by remember { mutableStateOf(defaults.embedThumbnail) }
    var embedMetadata by remember { mutableStateOf(defaults.embedMetadata) }
    var subtitles by remember { mutableStateOf(defaults.subtitles) }
    var sponsorBlock by remember { mutableStateOf(defaults.sponsorBlock) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    heights.forEach { option ->
                        FilterChip(
                            selected = height == option,
                            onClick = { height = option },
                            label = { Text(if (option >= 2160) "${option}p" else "${option}p") },
                        )
                    }
                }
                Label(stringResource(R.string.label_container))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    containers.forEach { option ->
                        FilterChip(
                            selected = container == option,
                            onClick = { container = option },
                            label = { Text(option.uppercase()) },
                        )
                    }
                }
            } else {
                Label(stringResource(R.string.label_audio_format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    audioFormats.forEach { option ->
                        FilterChip(
                            selected = audioFormat == option,
                            onClick = { audioFormat = option },
                            label = { Text(option.uppercase()) },
                        )
                    }
                }
                Label(stringResource(R.string.label_bitrate))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    bitrates.forEach { option ->
                        FilterChip(
                            selected = bitrate == option,
                            onClick = { bitrate = option },
                            label = { Text(stringResource(R.string.bitrate_kbps, option)) },
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Label(stringResource(R.string.label_extras))
            ToggleRow(stringResource(R.string.extra_thumbnail), embedThumbnail) { embedThumbnail = it }
            ToggleRow(stringResource(R.string.extra_metadata), embedMetadata) { embedMetadata = it }
            ToggleRow(stringResource(R.string.extra_subtitles), subtitles) { subtitles = it }
            ToggleRow(stringResource(R.string.extra_sponsorblock), sponsorBlock) { sponsorBlock = it }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onConfirm(
                        DownloadOptions(
                            mediaType = mediaType,
                            maxHeight = if (mediaType == MediaType.VIDEO) height else 0,
                            container = container,
                            audioFormat = audioFormat,
                            audioBitrate = bitrate,
                            embedThumbnail = embedThumbnail,
                            embedMetadata = embedMetadata,
                            subtitles = subtitles,
                            sponsorBlock = sponsorBlock,
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
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
