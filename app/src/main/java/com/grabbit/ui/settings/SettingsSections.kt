package com.grabbit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.grabbit.R
import com.grabbit.core.Languages
import com.grabbit.core.UiValues
import com.grabbit.data.extract.ExtractionEnginePreference
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.ThemeMode
import com.grabbit.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        Column(Modifier.padding(Dimens.md)) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(Dimens.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(themeLabel(mode)) },
                    )
                }
            }
        }
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.settings_dynamic_color),
            subtitle = stringResource(R.string.settings_dynamic_color_desc),
            checked = settings.dynamicColor,
            onCheckedChange = viewModel::setDynamicColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadsSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onFilename: () -> Unit,
    onPickFolder: () -> Unit,
) {
    SettingsCard {
        ChipSetting(
            title = stringResource(R.string.settings_default_quality),
            options = UiValues.VIDEO_HEIGHTS,
            selected = settings.defaultVideoHeight,
            label = { if (it == 0) stringResource(R.string.option_best) else "${it}p" },
            onSelect = viewModel::setVideoHeight,
        )
        HorizontalDivider()
        ChipSetting(
            title = stringResource(R.string.settings_default_container),
            options = UiValues.CONTAINERS,
            selected = settings.defaultVideoContainer,
            label = { it.uppercase() },
            onSelect = viewModel::setVideoContainer,
        )
        HorizontalDivider()
        ChipSetting(
            title = stringResource(R.string.settings_default_audio),
            options = UiValues.AUDIO_FORMATS,
            selected = settings.defaultAudioFormat,
            label = { it.uppercase() },
            onSelect = viewModel::setAudioFormat,
        )
        HorizontalDivider()
        ChipSetting(
            title = stringResource(R.string.settings_default_bitrate),
            options = UiValues.BITRATES,
            selected = settings.defaultAudioBitrate,
            label = { stringResource(R.string.bitrate_kbps, it) },
            onSelect = viewModel::setAudioBitrate,
        )
        HorizontalDivider()
        ConcurrencySetting(
            value = settings.concurrentDownloads,
            onChange = viewModel::setConcurrency,
        )
        HorizontalDivider()
        ValueRow(
            title = stringResource(R.string.settings_filename),
            value = settings.filenameTemplate,
            subtitle = stringResource(R.string.settings_filename_desc),
            onClick = onFilename,
        )
        HorizontalDivider()
        ChipSetting(
            title = stringResource(R.string.settings_extraction_engine),
            options = ExtractionEnginePreference.entries,
            selected = settings.extractionEngine,
            label = { extractionEngineLabel(it) },
            onSelect = viewModel::setExtractionEngine,
        )
        HorizontalDivider()
        FolderRow(
            settings = settings,
            onPick = onPickFolder,
            onClear = viewModel::clearCustomFolder,
        )
    }
}

@Composable
private fun extractionEngineLabel(preference: ExtractionEnginePreference): String =
    stringResource(
        when (preference) {
            ExtractionEnginePreference.AUTO -> R.string.extraction_engine_auto
            ExtractionEnginePreference.YTDLP_ONLY -> R.string.extraction_engine_ytdlp_only
        },
    )

@Composable
internal fun ExtrasSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        SwitchRow(
            title = stringResource(R.string.settings_embed_thumbnail),
            checked = settings.embedThumbnail,
            onCheckedChange = viewModel::setEmbedThumbnail,
        )
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.settings_embed_metadata),
            checked = settings.embedMetadata,
            onCheckedChange = viewModel::setEmbedMetadata,
        )
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.settings_subtitles),
            checked = settings.subtitles,
            onCheckedChange = viewModel::setSubtitles,
        )
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.settings_sponsorblock),
            checked = settings.sponsorBlock,
            onCheckedChange = viewModel::setSponsorBlock,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LanguageSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        Column(Modifier.padding(Dimens.md)) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(Dimens.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                FilterChip(
                    selected = settings.language == null,
                    onClick = { viewModel.setLanguage(null) },
                    label = { Text(stringResource(R.string.settings_language_system)) },
                )
                Languages.SUPPORTED.forEach { tag ->
                    FilterChip(
                        selected = settings.language == tag,
                        onClick = { viewModel.setLanguage(tag) },
                        label = { Text(Languages.displayName(tag)) },
                    )
                }
            }
        }
    }
}
