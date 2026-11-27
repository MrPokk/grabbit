package com.grabbit.ui.convert

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.grabbit.R
import com.grabbit.core.AppLog
import com.grabbit.core.Formatters
import com.grabbit.core.UiValues
import com.grabbit.data.download.ConvertFormat
import com.grabbit.ui.theme.Dimens

private const val TAG = "ConvertScreen"
private val ProgressStrokeWidth = 2.dp

@Composable
internal fun PickedFileCard(
    picked: ConvertViewModel.PickedFile?,
    onPick: () -> Unit,
) {
    val resources = LocalResources.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AudioFile, contentDescription = null)
            Spacer(Modifier.width(Dimens.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = picked?.name ?: stringResource(R.string.convert_no_file),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (picked != null) {
                    Text(
                        text = Formatters.bytes(resources, picked.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = onPick) {
                Icon(
                    Icons.Rounded.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconSm),
                )
                Spacer(Modifier.width(Dimens.xxs))
                Text(stringResource(R.string.convert_pick))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TargetChips(
    targets: List<ConvertFormat>,
    selected: ConvertFormat,
    running: Boolean,
    onSelect: (ConvertFormat) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        targets.forEach { target ->
            FilterChip(
                selected = selected == target,
                onClick = { onSelect(target) },
                label = { Text(target.label) },
                enabled = !running,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BitrateChips(
    selected: Int,
    running: Boolean,
    onSelect: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        UiValues.BITRATES.forEach { bitrate ->
            FilterChip(
                selected = selected == bitrate,
                onClick = { onSelect(bitrate) },
                label = { Text(stringResource(R.string.bitrate_kbps, bitrate)) },
                enabled = !running,
            )
        }
    }
}

@Composable
internal fun ConvertActions(
    running: Boolean,
    progress: Int,
    hasFile: Boolean,
    onConvert: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        Button(
            onClick = onConvert,
            enabled = hasFile && !running,
            modifier = Modifier.weight(1f),
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconSm),
                    strokeWidth = ProgressStrokeWidth,
                )
                Spacer(Modifier.width(Dimens.xs))
                Text(stringResource(R.string.convert_progress, progress))
            } else {
                Text(stringResource(R.string.convert_start))
            }
        }
        if (running) {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
internal fun ConvertProgress(progress: Int) {
    LinearProgressIndicator(
        progress = { progress / UiValues.PROGRESS_MAX.toFloat() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ResultCard(
    uri: String,
    name: String,
    mimeType: String,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Text(
                text = stringResource(R.string.convert_done, name),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Dimens.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Button(onClick = { openFile(context, uri, mimeType) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(Dimens.xxs))
                    Text(stringResource(R.string.convert_open))
                }
            }
        }
    }
}

private fun openFile(
    context: Context,
    uri: String,
    mimeType: String,
) {
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri.toUri(), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        AppLog.e(TAG, "No activity can open the converted file", e)
        Toast.makeText(context, R.string.file_missing, Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun ConvertErrorCard(
    errorRes: Int,
    detail: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(Modifier.padding(Dimens.md)) {
            Text(
                text = stringResource(errorRes, detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
