package com.grabbit.ui.components

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
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

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
private const val MEDIA_AUTHORITY = "media"
private const val PRIMARY_VOLUME_ID = "primary"
private const val EXTERNAL_VOLUME_SEGMENT = "external"

// Every menu entry is an independent callback; grouping them into a holder would obscure the wiring.
@Suppress("LongParameterList")
@Composable
fun DownloadActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShowInFolder: () -> Unit,
    onShare: () -> Unit,
    onRedownload: () -> Unit,
    onRemove: () -> Unit,
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
            text = { Text(stringResource(R.string.action_show_in_folder)) },
            leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
            onClick = {
                onDismiss()
                onShowInFolder()
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
            text = { Text(stringResource(R.string.action_remove_from_list)) },
            leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
            onClick = {
                onDismiss()
                onRemove()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete_from_device)) },
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

fun showSavedFileInFolder(
    context: Context,
    uri: String,
) {
    val folderUri = savedFileFolderUri(context, uri.toUri())
    if (folderUri == null) {
        Toast.makeText(context, R.string.error_file_location, Toast.LENGTH_SHORT).show()
        return
    }
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        AppLog.e(TAG, "No activity can show the file location", e)
        Toast.makeText(context, R.string.error_file_location, Toast.LENGTH_SHORT).show()
    }
}

private fun savedFileFolderUri(
    context: Context,
    uri: Uri,
): Uri? {
    val documentId =
        when {
            uri.scheme == ContentResolver.SCHEME_FILE -> folderDocumentIdFromPath(uri.path)
            uri.authority == EXTERNAL_STORAGE_AUTHORITY -> folderDocumentIdFromExternalStorage(uri)
            uri.authority == MEDIA_AUTHORITY -> folderDocumentIdFromMediaStore(context, uri)
            else -> null
        } ?: return null
    return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
}

private fun folderDocumentIdFromExternalStorage(uri: Uri): String? {
    val documentId =
        if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.getTreeDocumentId(uri)
        } else {
            DocumentsContract.getDocumentId(uri)
        }
    if (!documentId.contains(':')) return null
    val volume = documentId.substringBefore(':')
    val path = documentId.substringAfter(':')
    return "$volume:" + path.substringBeforeLast('/', missingDelimiterValue = "")
}

private fun folderDocumentIdFromMediaStore(
    context: Context,
    uri: Uri,
): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val volume =
            uri.pathSegments.firstOrNull()?.let {
                if (it == EXTERNAL_VOLUME_SEGMENT) PRIMARY_VOLUME_ID else it
            } ?: return null
        val relativePath = queryColumn(context, uri, MediaStore.MediaColumns.RELATIVE_PATH) ?: return null
        return "$volume:" + relativePath.trimEnd('/')
    }
    @Suppress("DEPRECATION")
    val data = queryColumn(context, uri, MediaStore.MediaColumns.DATA) ?: return null
    return folderDocumentIdFromPath(data)
}

@Suppress("DEPRECATION") // Only legacy file:// / MediaStore DATA paths from API < 29 reach here.
private fun folderDocumentIdFromPath(path: String?): String? {
    val absolute = path ?: return null
    val root = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/') + "/"
    if (!absolute.startsWith(root)) return null
    val relative = absolute.removePrefix(root).substringBeforeLast('/', missingDelimiterValue = "")
    return "$PRIMARY_VOLUME_ID:$relative"
}

private fun queryColumn(
    context: Context,
    uri: Uri,
    column: String,
): String? =
    try {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: IllegalArgumentException) {
        AppLog.e(TAG, "Could not read $column for $uri", e)
        null
    } catch (e: SecurityException) {
        AppLog.e(TAG, "Could not read $column for $uri", e)
        null
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
