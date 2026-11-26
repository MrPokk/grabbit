package com.grabbit.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.grabbit.R
import com.grabbit.core.AppLog

private const val TAG = "DownloadActions"

@Composable
fun DownloadActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_open)) },
            leadingIcon = { Icon(Icons.Rounded.OpenInNew, contentDescription = null) },
            onClick = {
                onDismiss()
                onOpen()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_share)) },
            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
            onClick = {
                onDismiss()
                onShare()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_redownload)) },
            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            onClick = {
                onDismiss()
                onRedownload()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            onClick = {
                onDismiss()
                onDelete()
            },
        )
    }
}

fun openSavedFile(
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
        AppLog.e(TAG, "No activity can open a saved file", e)
        Toast.makeText(context, R.string.file_missing, Toast.LENGTH_SHORT).show()
    }
}

fun shareSavedFile(
    context: Context,
    uri: String,
    fileName: String?,
    mimeType: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    try {
        context.startActivity(Intent.createChooser(intent, fileName))
    } catch (e: ActivityNotFoundException) {
        AppLog.e(TAG, "No activity can share a saved file", e)
        Toast.makeText(context, R.string.file_missing, Toast.LENGTH_SHORT).show()
    }
}
