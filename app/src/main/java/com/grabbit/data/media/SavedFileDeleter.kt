package com.grabbit.data.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.grabbit.core.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SavedFileDeleter"

@Singleton
class SavedFileDeleter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun delete(uriString: String): Boolean = withContext(Dispatchers.IO) { deleteUri(uriString.toUri()) }

        private fun deleteUri(uri: Uri): Boolean =
            try {
                when {
                    DocumentsContract.isDocumentUri(context, uri) ->
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    uri.scheme == ContentResolver.SCHEME_FILE -> deleteFileUri(uri)
                    else -> context.contentResolver.delete(uri, null, null) > 0
                }
            } catch (e: SecurityException) {
                handleDeleteError(e, "no permission to delete a saved file")
            } catch (e: IOException) {
                handleDeleteError(e, "could not delete a saved file")
            } catch (e: IllegalArgumentException) {
                handleDeleteError(e, "invalid saved file uri")
            }

        private fun deleteFileUri(uri: Uri): Boolean {
            val path = uri.path ?: return false
            val file = File(path)
            return !file.exists() || file.delete()
        }

        private fun handleDeleteError(
            e: Exception,
            message: String,
        ): Boolean {
            AppLog.e(TAG, message, e)
            return false
        }
    }
