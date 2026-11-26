package com.grabbit.ui.settings

import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grabbit.BuildConfig
import com.grabbit.R
import com.grabbit.data.settings.AppSettings
import com.grabbit.ui.theme.Dimens

private val ProgressStrokeWidth = 2.dp

@Composable
internal fun NetworkSection(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    onProxy: () -> Unit,
    cookiePicker: ActivityResultLauncher<Array<String>>,
) {
    SettingsCard {
        ValueRow(
            title = stringResource(R.string.settings_proxy),
            value = settings.proxy.ifBlank { stringResource(R.string.settings_proxy_not_set) },
            subtitle = stringResource(R.string.settings_proxy_desc),
            onClick = onProxy,
        )
        HorizontalDivider()
        Row(
            modifier = Modifier.padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Key, contentDescription = null)
            Spacer(Modifier.width(Dimens.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_cookies),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text =
                        settings.cookiesName?.let {
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

@Composable
internal fun UpdatesSection(
    version: String?,
    updateState: SettingsViewModel.UpdateState,
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    SettingsCard {
        Column(Modifier.padding(Dimens.md)) {
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
                            modifier = Modifier.size(Dimens.iconSm),
                            strokeWidth = ProgressStrokeWidth,
                        )
                        Spacer(Modifier.width(Dimens.xs))
                        Text(stringResource(R.string.settings_updating))
                    } else {
                        Text(stringResource(R.string.settings_update_now))
                    }
                }
            }
            Spacer(Modifier.height(Dimens.xs))
            Text(
                text = stringResource(R.string.settings_update_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                updateState.updated -> ResultText(stringResource(R.string.settings_update_done))
                updateState.upToDate -> ResultText(stringResource(R.string.settings_update_latest))
                updateState.error != null ->
                    ResultText(
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

@Composable
internal fun ToolsSection(onOpenConvert: () -> Unit) {
    SettingsCard {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onOpenConvert)
                    .padding(Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
            Spacer(Modifier.width(Dimens.sm))
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

@Composable
internal fun AboutSection() {
    SettingsCard {
        Row(Modifier.padding(Dimens.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Palette, contentDescription = null)
            Spacer(Modifier.width(Dimens.sm))
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
            text = stringResource(R.string.settings_legal, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Dimens.md),
        )
    }
}
