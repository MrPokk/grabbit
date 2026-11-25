package com.grabbit.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grabbit.BuildConfig
import com.grabbit.R
import com.grabbit.core.Languages
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.ThemeMode
import com.grabbit.ui.components.SectionHeader
import kotlin.math.roundToInt

private val qualityOptions = listOf(0, 2160, 1440, 1080, 720, 480)
private val containerOptions = listOf("mp4", "mkv", "webm")
private val audioFormatOptions = listOf("mp3", "m4a", "opus", "flac")
private val bitrateOptions = listOf(128, 192, 256, 320)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenConvert: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val version by viewModel.ytDlpVersion.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    var showFilenameDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }

    val cookiePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importCookies) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::setCustomFolder) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            scrollBehavior = scrollBehavior,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_theme),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            item {
                SettingsCard {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            item { SectionHeader(stringResource(R.string.settings_section_downloads)) }
            item {
                SettingsCard {
                    ChipSetting(
                        title = stringResource(R.string.settings_default_quality),
                        options = qualityOptions,
                        selected = settings.defaultVideoHeight,
                        label = { if (it == 0) stringResource(R.string.option_best) else "${it}p" },
                        onSelect = viewModel::setVideoHeight,
                    )
                    HorizontalDivider()
                    ChipSetting(
                        title = stringResource(R.string.settings_default_container),
                        options = containerOptions,
                        selected = settings.defaultVideoContainer,
                        label = { it.uppercase() },
                        onSelect = viewModel::setVideoContainer,
                    )
                    HorizontalDivider()
                    ChipSetting(
                        title = stringResource(R.string.settings_default_audio),
                        options = audioFormatOptions,
                        selected = settings.defaultAudioFormat,
                        label = { it.uppercase() },
                        onSelect = viewModel::setAudioFormat,
                    )
                    HorizontalDivider()
                    ChipSetting(
                        title = stringResource(R.string.settings_default_bitrate),
                        options = bitrateOptions,
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
                        onClick = { showFilenameDialog = true },
                    )
                    HorizontalDivider()
                    FolderRow(
                        settings = settings,
                        onPick = { folderPicker.launch(null) },
                        onClear = viewModel::clearCustomFolder,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_extras)) }
            item {
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

            item { SectionHeader(stringResource(R.string.settings_section_network)) }
            item {
                SettingsCard {
                    ValueRow(
                        title = stringResource(R.string.settings_proxy),
                        value = settings.proxy.ifBlank { stringResource(R.string.settings_proxy_not_set) },
                        subtitle = stringResource(R.string.settings_proxy_desc),
                        onClick = { showProxyDialog = true },
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_cookies),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = settings.cookiesName?.let {
                                    stringResource(R.string.settings_cookies_loaded, it)
                                } ?: stringResource(R.string.settings_cookies_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { cookiePicker.launch(arrayOf("*/*")) }) {
                            Text(stringResource(R.string.action_import))
                        }
                        if (settings.cookiesPath != null) {
                            TextButton(onClick = viewModel::clearCookies) {
                                Text(stringResource(R.string.action_remove))
                            }
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_updates)) }
            item {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_ytdlp_version),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = version ?: stringResource(R.string.settings_ytdlp_version_unknown),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = viewModel::updateYtDlp,
                                enabled = !updateState.running,
                            ) {
                                if (updateState.running) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_updating))
                                } else {
                                    Text(stringResource(R.string.settings_update_now))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_update_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            updateState.updated -> ResultText(stringResource(R.string.settings_update_done))
                            updateState.upToDate -> ResultText(stringResource(R.string.settings_update_latest))
                            updateState.error != null -> ResultText(
                                stringResource(R.string.settings_update_failed, updateState.error.orEmpty()),
                            )
                        }
                    }
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_check_updates),
                        checked = settings.autoCheckUpdates,
                        onCheckedChange = viewModel::setAutoCheckUpdates,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_tools)) }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenConvert)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_convert),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.settings_convert_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                SettingsCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_version),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.settings_legal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (showFilenameDialog) {
        TextInputDialog(
            title = stringResource(R.string.settings_filename),
            initial = settings.filenameTemplate,
            onDismiss = { showFilenameDialog = false },
            onConfirm = {
                viewModel.setFilenameTemplate(it)
                showFilenameDialog = false
            },
        )
    }

    if (showProxyDialog) {
        TextInputDialog(
            title = stringResource(R.string.settings_proxy),
            initial = settings.proxy,
            placeholder = stringResource(R.string.settings_proxy_desc),
            onDismiss = { showProxyDialog = false },
            onConfirm = {
                viewModel.setProxy(it)
                showProxyDialog = false
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun ConcurrencySetting(value: Int, onChange: (Int) -> Unit) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_concurrent),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.settings_concurrent_value, sliderValue.roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = 1f..5f,
            steps = 3,
            onValueChangeFinished = { onChange(sliderValue.roundToInt()) },
        )
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderRow(
    settings: AppSettings,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_save_folder),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (settings.hasCustomFolder) {
                    stringResource(R.string.settings_save_folder_custom)
                } else {
                    stringResource(R.string.settings_save_folder_system)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onPick) {
            Text(stringResource(R.string.settings_pick_folder))
        }
        if (settings.hasCustomFolder) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.action_remove))
            }
        }
    }
}

@Composable
private fun ResultText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = placeholder?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
