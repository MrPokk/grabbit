package com.grabbit.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.grabbit.core.AppLog
import com.grabbit.core.FileName
import com.grabbit.core.Formatting
import com.grabbit.core.Storage
import com.grabbit.data.download.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MediaSaver"

/**
 * Copies a finished download into scoped storage:
 *  - MediaStore (Movies/Grabbit for video, Music/Grabbit for audio) by default,
 *  - a user-picked SAF tree when one is configured.
 *
 * Downloads themselves always run in the app cache, because yt-dlp cannot write
 * to MediaStore paths directly.
 */
@Singleton
class MediaSaver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** A file placed in MediaStore or in the user's SAF tree. */
        data class SavedFile(
            val uri: Uri,
            val displayName: String,
            val mimeType: String,
            val sizeBytes: Long,
        )

        /**
         * Copies [source] into the configured destination, falling back from a SAF tree
         * to MediaStore (or public storage on API < 29) when the tree cannot be written.
         */
        suspend fun save(
            source: File,
            mediaType: MediaType,
            mimeType: String,
            displayName: String,
            customFolder: Uri?,
        ): SavedFile =
            withContext(Dispatchers.IO) {
                val safeName = FileName.sanitize(displayName)

                if (customFolder != null) {
                    val viaTree =
                        try {
                            saveToTree(source, customFolder, mimeType, safeName)
                        } catch (e: IOException) {
                            AppLog.e(TAG, "could not save into the selected folder", e)
                            null
                        } catch (e: SecurityException) {
                            AppLog.e(TAG, "could not save into the selected folder", e)
                            null
                        }
                    if (viaTree != null) return@withContext viaTree
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(source, mediaType, mimeType, safeName)
                } else {
                    saveToLegacyStorage(source, mediaType, mimeType, safeName)
                }
            }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun saveToMediaStore(
            source: File,
            mediaType: MediaType,
            mimeType: String,
            displayName: String,
        ): SavedFile {
            val resolver = context.contentResolver
            val collection = collectionFor(mediaType)
            val relativePath =
                if (mediaType == MediaType.AUDIO) {
                    "${Environment.DIRECTORY_MUSIC}/Grabbit"
                } else {
                    "${Environment.DIRECTORY_MOVIES}/Grabbit"
                }

            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

            val uri =
                resolver.insert(collection, values)
                    ?: throw IOException("Could not create a MediaStore entry")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output, Storage.COPY_BUFFER_BYTES) }
                } ?: throw IOException("Could not open the MediaStore stream")

                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            } catch (e: IOException) {
                try {
                    resolver.delete(uri, null, null)
                } catch (e: SecurityException) {
                    // The original failure is the useful one; keep it.
                    AppLog.e(TAG, "could not remove the pending MediaStore entry", e)
                }
                throw e
            } catch (e: SecurityException) {
                try {
                    resolver.delete(uri, null, null)
                } catch (e: SecurityException) {
                    // The original failure is the useful one; keep it.
                    AppLog.e(TAG, "could not remove the pending MediaStore entry", e)
                }
                throw e
            }

            return SavedFile(uri, displayName, mimeType, source.length())
        }

        /**
         * API < 29 has no scoped storage: public directories and MediaStore.DATA
         * are still needed there, hence the deprecation suppression.
         */
        @Suppress("DEPRECATION")
        private fun saveToLegacyStorage(
            source: File,
            mediaType: MediaType,
            mimeType: String,
            displayName: String,
        ): SavedFile {
            val base =
                if (mediaType == MediaType.AUDIO) {
                    Environment.DIRECTORY_MUSIC
                } else {
                    Environment.DIRECTORY_MOVIES
                }
            val dir = File(Environment.getExternalStoragePublicDirectory(base), "Grabbit")
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Could not create ${dir.absolutePath}")
            }
            val target = uniqueFile(dir, displayName)
            source.copyTo(target, overwrite = false)

            val values =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, target.absolutePath)
                    put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / Formatting.MILLIS_PER_SECOND)
                }
            val uri =
                context.contentResolver.insert(collectionFor(mediaType), values)
                    ?: Uri.fromFile(target)
            return SavedFile(uri, target.name, mimeType, target.length())
        }

        private fun saveToTree(
            source: File,
            folder: Uri,
            mimeType: String,
            displayName: String,
        ): SavedFile? {
            val tree = DocumentFile.fromTreeUri(context, folder) ?: return null
            if (!tree.canWrite()) return null
            val file = tree.createFile(mimeType, displayName) ?: return null
            context.contentResolver.openOutputStream(file.uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, Storage.COPY_BUFFER_BYTES) }
            } ?: return null
            return SavedFile(file.uri, file.name ?: displayName, mimeType, source.length())
        }

        private fun collectionFor(mediaType: MediaType): Uri =
            if (mediaType == MediaType.AUDIO) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

        private fun uniqueFile(
            dir: File,
            displayName: String,
        ): File {
            val existing =
                dir
                    .listFiles()
                    ?.map { it.name }
                    ?.toSet()
                    .orEmpty()
            val name = FileName.unique(displayName, existing)
            return File(dir, name)
        }
    }
