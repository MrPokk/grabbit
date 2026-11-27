package com.grabbit.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.grabbit.R
import com.grabbit.core.AppLog
import com.grabbit.core.UiValues
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.MediaType
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.toDownloadOptions
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.availableHeights
import com.grabbit.ui.theme.Dimens

private const val TAG = "FormatSheet"

private const val PRIMARY_STORAGE_VOLUME = "primary"

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
    val isPlaylist = media is FetchedMedia.Collection
    val context = LocalContext.current

    var options by remember { mutableStateOf(initial) }
    var mediaType by remember { mutableStateOf(initial.mediaType) }
    var videoHeight by remember { mutableStateOf(initial.maxHeight) }
    var saveFolderUri by remember { mutableStateOf<String?>(defaults.customFolderUri) }
    var saveFolderPath by remember {
        mutableStateOf(defaults.customFolderUri?.let { folderFullPath(Uri.parse(it)) })
    }
    var fileName by remember { mutableStateOf(if (!isPlaylist) media.title else "") }

    val folderPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let { pickedUri ->
                saveFolderUri = pickedUri.toString()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            pickedUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    } catch (e: SecurityException) {
                        AppLog.e(TAG, "could not persist the folder permission", e)
                    }
                }
                saveFolderPath = folderFullPath(pickedUri)
            }
        }

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
            ExtrasSection(options = options, onOptionsChange = { options = it })

            HorizontalDivider(Modifier.padding(vertical = Dimens.xxs))
            SaveLocationSection(
                saveFolderPath = saveFolderPath,
                hasCustomFolder = saveFolderUri != null,
                onPickFolder = { folderPicker.launch(null) },
                onClearFolder = {
                    saveFolderUri = null
                    saveFolderPath = null
                },
            )

            if (!isPlaylist) {
                HorizontalDivider(Modifier.padding(vertical = Dimens.xxs))
                FileNameSection(fileName = fileName, onFileNameChange = { fileName = it })
            }

            Spacer(Modifier.height(Dimens.xs))
            Button(
                onClick = {
                    onConfirm(
                        options.copy(
                            mediaType = mediaType,
                            maxHeight = if (mediaType == MediaType.VIDEO) videoHeight else 0,
                            saveFolderUri = saveFolderUri.takeIf { it?.isNotBlank() == true },
                            fileName = fileName.takeIf { it.isNotBlank() },
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

private fun folderFullPath(uri: Uri?): String? {
    val documentId =
        uri?.let {
            try {
                DocumentsContract.getTreeDocumentId(it)
            } catch (e: IllegalArgumentException) {
                AppLog.e(TAG, "not a tree URI: $it", e)
                null
            }
        } ?: return null
    val volume = documentId.substringBefore(':')
    val relative = documentId.substringAfter(':').trim('/')
    if (relative.isEmpty()) return null
    val root =
        if (volume == PRIMARY_STORAGE_VOLUME) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volume"
        }
    return "$root/$relative"
}

@Composable
private fun SaveLocationSection(
    saveFolderPath: String?,
    hasCustomFolder: Boolean,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
) {
    Label(stringResource(R.string.save_location_title))
    val folderDescription =
        saveFolderPath
            ?: if (hasCustomFolder) {
                stringResource(R.string.save_location_custom)
            } else {
                stringResource(R.string.save_location_system)
            }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = folderDescription,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
            TextButton(onClick = onPickFolder) {
                Text(stringResource(R.string.save_location_pick))
            }
            if (hasCustomFolder) {
                TextButton(onClick = onClearFolder) {
                    Text(stringResource(R.string.save_location_clear))
                }
            }
        }
    }
}

@Composable
private fun FileNameSection(
    fileName: String,
    onFileNameChange: (String) -> Unit,
) {
    Label(stringResource(R.string.file_name_label))
    OutlinedTextField(
        value = fileName,
        onValueChange = onFileNameChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.file_name_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.file_name_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.xxs),
    )
}

@Composable
private fun ExtrasSection(
    options: DownloadOptions,
    onOptionsChange: (DownloadOptions) -> Unit,
) {
    Label(stringResource(R.string.label_extras))
    ToggleRow(
        stringResource(R.string.extra_thumbnail),
        options.embedThumbnail,
    ) { onOptionsChange(options.copy(embedThumbnail = it)) }
    ToggleRow(
        stringResource(R.string.extra_metadata),
        options.embedMetadata,
    ) { onOptionsChange(options.copy(embedMetadata = it)) }
    ToggleRow(
        stringResource(R.string.extra_subtitles),
        options.subtitles,
    ) { onOptionsChange(options.copy(subtitles = it)) }
    ToggleRow(
        stringResource(R.string.extra_sponsorblock),
        options.sponsorBlock,
    ) { onOptionsChange(options.copy(sponsorBlock = it)) }
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
                .clip(MaterialTheme.shapes.medium)
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = Dimens.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Dimens.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
