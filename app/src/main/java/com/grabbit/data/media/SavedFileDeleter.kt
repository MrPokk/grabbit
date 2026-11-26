package com.grabbit.data.media

import android.content.ContentResolver
import android.content.Context
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

/** Deletes a finished download from MediaStore, a SAF tree or legacy public storage. */
@Singleton
class SavedFileDeleter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun delete(uriString: String): Boolean =
            withContext(Dispatchers.IO) {
                val uri = uriString.toUri()
                try {
                    when {
                        DocumentsContract.isDocumentUri(context, uri) ->
                            DocumentsContract.deleteDocument(context.contentResolver, uri)
                        uri.scheme == ContentResolver.SCHEME_FILE -> {
                            val file = File(uri.path ?: return@withContext false)
                            !file.exists() || file.delete()
                        }
                        else -> context.contentResolver.delete(uri, null, null) > 0
                    }
                } catch (e: SecurityException) {
                    AppLog.e(TAG, "no permission to delete a saved file", e)
                    false
                } catch (e: IOException) {
                    AppLog.e(TAG, "could not delete a saved file", e)
                    false
                } catch (e: IllegalArgumentException) {
                    AppLog.e(TAG, "invalid saved file uri", e)
                    false
                }
            }
    }
