package com.grabbit.ui.convert

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.core.Formatters
import com.grabbit.core.MimeTypes
import com.grabbit.data.download.ConvertFormat
import com.grabbit.ui.components.SectionHeader

private val bitrateOptions = listOf(128, 192, 256, 320)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConvertScreen(
    onBack: () -> Unit,
    viewModel: ConvertViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::pick) }

    val isAudio = state.picked?.let { MimeTypes.isAudio(it.mimeType) } ?: true
    val targets = if (isAudio) ConvertFormat.audioTargets else ConvertFormat.videoTargets

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.convert_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.convert_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.picked?.name ?: stringResource(R.string.convert_no_file),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.picked?.let { picked ->
                                Text(
                                    text = Formatters.bytes(picked.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.convert_pick))
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.convert_target)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    targets.forEach { target ->
                        FilterChip(
                            selected = state.target == target,
                            onClick = { viewModel.setTarget(target) },
                            label = { Text(target.label) },
                            enabled = !state.running,
                        )
                    }
                }
            }

            if (state.target.audioOnly) {
                item { SectionHeader(stringResource(R.string.label_bitrate)) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bitrateOptions.forEach { bitrate ->
                            FilterChip(
                                selected = state.bitrate == bitrate,
                                onClick = { viewModel.setBitrate(bitrate) },
                                label = { Text(stringResource(R.string.bitrate_kbps, bitrate)) },
                                enabled = !state.running,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::convert,
                        enabled = state.picked != null && !state.running,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.convert_progress, state.progress))
                        } else {
                            Text(stringResource(R.string.convert_start))
                        }
                    }
                    if (state.running) {
                        OutlinedButton(onClick = viewModel::cancel) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }

            if (state.running) {
                item {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.resultUri?.let { uri ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(
                                    R.string.convert_done,
                                    state.resultName.orEmpty(),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri.toUri(), state.target.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: ActivityNotFoundException) {
                                            Toast.makeText(context, R.string.file_missing, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.convert_open))
                                }
                            }
                        }
                    }
                }
            }

            state.errorRes?.let { errorRes ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(errorRes, state.errorDetail.orEmpty()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_legal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
