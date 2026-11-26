package com.grabbit.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.FileUriExposedException
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
import java.io.File

private const val TAG = "DownloadActions"

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

private const val PRIMARY_VOLUME = "primary"

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
    val fileUri = uri.toUri()
    // Prefer showing the parent folder so the user sees the file next to its siblings.
    val folderUri = savedFileFolderUri(context, fileUri)
    if (folderUri != null && openFolderPickerAt(context, folderUri)) return
    // Fall back to opening the file itself when no app can show its folder.
    if (openUriViaChooser(context, fileUri, null, R.string.action_open)) {
        return
    }
    AppLog.e(TAG, "No activity can show the file location or open the file")
    copyPathToClipboard(context, fileUri)
    Toast.makeText(context, R.string.error_cannot_open_folder, Toast.LENGTH_LONG).show()
}

private fun openFolderPickerAt(
    context: Context,
    folderUri: Uri,
): Boolean {
    val intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderUri)
        }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        AppLog.e(TAG, "No file manager can show $folderUri", e)
        false
    } catch (e: SecurityException) {
        AppLog.e(TAG, "Cannot show folder $folderUri", e)
        false
    }
}

/** Opens [uri] with ACTION_VIEW in a chooser so the user picks the app. Returns true on success. */
private fun openUriViaChooser(
    context: Context,
    uri: Uri,
    mimeType: String?,
    titleRes: Int,
): Boolean {
    val intent =
        Intent(Intent.ACTION_VIEW).apply {
            if (mimeType != null) {
                setDataAndType(uri, mimeType)
            } else {
                setData(uri)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    return try {
        context.startActivity(Intent.createChooser(intent, context.getString(titleRes)))
        true
    } catch (e: ActivityNotFoundException) {
        AppLog.e(TAG, "No activity can handle $uri", e)
        false
    } catch (e: SecurityException) {
        AppLog.e(TAG, "Cannot access $uri", e)
        false
    } catch (e: FileUriExposedException) {
        AppLog.e(TAG, "File URI exposure for $uri", e)
        false
    }
}

private fun copyPathToClipboard(
    context: Context,
    uri: Uri,
) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("file_path", uri.toString()))
    Toast.makeText(context, R.string.path_copied_to_clipboard, Toast.LENGTH_SHORT).show()
}

private fun savedFileFolderUri(
    context: Context,
    uri: Uri,
): Uri? {
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return uri.path?.let(::folderUriForAbsolutePath)
    }
    return when (uri.authority) {
        MediaStore.AUTHORITY -> folderUriFromMediaStore(context, uri)
        EXTERNAL_STORAGE_AUTHORITY -> folderUriFromDocument(uri)
        else -> null
    }
}

@Suppress("DEPRECATION")
private fun folderUriFromMediaStore(
    context: Context,
    uri: Uri,
): Uri? {
    val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATA)
    val cursor = context.contentResolver.query(uri, projection, null, null, null) ?: return null
    return cursor.use { c ->
        if (!c.moveToFirst()) return@use null
        val relativeIndex = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        val relative = if (relativeIndex >= 0) c.getString(relativeIndex) else null
        if (!relative.isNullOrBlank()) {
            folderUriForRelativePath(relative)
        } else {
            val dataIndex = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            val data = if (dataIndex >= 0) c.getString(dataIndex) else null
            if (data.isNullOrBlank()) null else folderUriForAbsolutePath(data)
        }
    }
}

private fun folderUriFromDocument(uri: Uri): Uri? {
    val documentId = DocumentsContract.getDocumentId(uri)
    if (!documentId.contains(':')) return null
    val volume = documentId.substringBefore(':')
    val path = documentId.substringAfter(':')
    val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
    val parentDocumentId = if (parentPath.isEmpty()) volume else "$volume:$parentPath"
    return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, parentDocumentId)
}

@Suppress("DEPRECATION")
private fun folderUriForAbsolutePath(path: String): Uri? {
    val parent = File(path).parent ?: return null
    val root = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
    return folderUriForRelativePath(parent.removePrefix("$root/").trim('/'))
}

private fun folderUriForRelativePath(relative: String): Uri {
    val trimmed = relative.trim('/')
    val documentId = if (trimmed.isEmpty()) PRIMARY_VOLUME else "$PRIMARY_VOLUME:$trimmed"
    return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
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
