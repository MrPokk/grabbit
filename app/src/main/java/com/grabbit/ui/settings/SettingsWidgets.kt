package com.grabbit.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.grabbit.R
import com.grabbit.core.UiValues
import com.grabbit.data.settings.AppSettings
import com.grabbit.data.settings.ThemeMode
import com.grabbit.ui.theme.Dimens
import kotlin.math.roundToInt

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.md),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column { content() }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = Dimens.md, vertical = Dimens.xs),
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
        Spacer(Modifier.width(Dimens.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChipSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = Dimens.md, vertical = Dimens.sm)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Dimens.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
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
internal fun ConcurrencySetting(
    value: Int,
    onChange: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = Dimens.md, vertical = Dimens.sm)) {
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
            valueRange = UiValues.MIN_DOWNLOADS.toFloat()..UiValues.MAX_DOWNLOADS.toFloat(),
            steps = UiValues.CONCURRENCY_SLIDER_STEPS,
            onValueChangeFinished = { onChange(sliderValue.roundToInt()) },
        )
    }
}

@Composable
internal fun ValueRow(
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Dimens.md),
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
internal fun FolderRow(
    settings: AppSettings,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(Dimens.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null)
        Spacer(Modifier.width(Dimens.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_save_folder),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text =
                    if (settings.hasCustomFolder) {
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
internal fun ResultText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Dimens.xs),
    )
}

@Composable
internal fun themeLabel(mode: ThemeMode): String =
    when (mode) {
        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }

@Composable
internal fun TextInputDialog(
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
