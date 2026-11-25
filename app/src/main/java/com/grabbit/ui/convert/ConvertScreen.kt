package com.grabbit.ui.convert

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.R
import com.grabbit.core.MimeTypes
import com.grabbit.data.download.ConvertFormat
import com.grabbit.ui.components.SectionHeader
import com.grabbit.ui.theme.Dimens

/** Screen for picking a media file, choosing a target format and converting it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    onBack: () -> Unit,
    viewModel: ConvertViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(
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
            contentPadding = PaddingValues(start = Dimens.md, end = Dimens.md, top = Dimens.xs, bottom = Dimens.xl),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        ) {
            item {
                Text(
                    text = stringResource(R.string.convert_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { PickedFileCard(state.picked, onPick = { picker.launch(arrayOf("audio/*", "video/*")) }) }

            item { SectionHeader(stringResource(R.string.convert_target)) }
            item {
                TargetChips(
                    targets = targets,
                    selected = state.target,
                    running = state.running,
                    onSelect = viewModel::setTarget,
                )
            }

            if (state.target.audioOnly) {
                item { SectionHeader(stringResource(R.string.label_bitrate)) }
                item {
                    BitrateChips(
                        selected = state.bitrate,
                        running = state.running,
                        onSelect = viewModel::setBitrate,
                    )
                }
            }

            item {
                ConvertActions(
                    running = state.running,
                    progress = state.progress,
                    hasFile = state.picked != null,
                    onConvert = viewModel::convert,
                    onCancel = viewModel::cancel,
                )
            }

            if (state.running) {
                item { ConvertProgress(progress = state.progress) }
            }

            state.resultUri?.let { uri ->
                item {
                    ResultCard(
                        uri = uri,
                        name = state.resultName.orEmpty(),
                        mimeType = state.target.mimeType,
                    )
                }
            }

            state.errorRes?.let { errorRes ->
                item { ConvertErrorCard(errorRes = errorRes, detail = state.errorDetail.orEmpty()) }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    text = stringResource(R.string.settings_legal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
