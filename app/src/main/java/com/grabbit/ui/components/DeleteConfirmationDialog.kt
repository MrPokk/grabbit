package com.grabbit.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.grabbit.R

@Composable
internal fun DeleteConfirmationDialog(
    mode: DeleteMode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fromDevice = mode == DeleteMode.DELETE_FILE
    val titleRes = if (fromDevice) R.string.delete_device_confirm_title else R.string.delete_confirm_title
    val bodyRes = if (fromDevice) R.string.delete_device_confirm_body else R.string.delete_confirm_body
    val confirmRes = if (fromDevice) R.string.action_delete_from_device else R.string.action_remove_from_list
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmRes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
