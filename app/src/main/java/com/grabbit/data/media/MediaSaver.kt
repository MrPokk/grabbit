package com.grabbit.data.media

import android.content.ContentResolver
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

@Singleton
class MediaSaver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        data class SavedFile(
            val uri: Uri,
            val displayName: String,
            val mimeType: String,
            val sizeBytes: Long,
        )

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
                    val viaTree = saveToTreeOrNull(source, customFolder, mimeType, safeName)
                    if (viaTree != null) return@withContext viaTree
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(source, mediaType, mimeType, safeName)
                } else {
                    saveToLegacyStorage(source, mediaType, mimeType, safeName)
                }
            }

        private fun saveToTreeOrNull(
            source: File,
            folder: Uri,
            mimeType: String,
            displayName: String,
        ): SavedFile? =
            try {
                saveToTree(source, folder, mimeType, displayName)
            } catch (e: IOException) {
                logTreeError(e)
            } catch (e: SecurityException) {
                logTreeError(e)
            }

        private fun logTreeError(e: Exception): SavedFile? {
            AppLog.e(TAG, "could not save into the selected folder", e)
            return null
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
            val uri = insertEntry(resolver, collection, values)
            try {
                writeEntry(resolver, uri, source)
            } catch (e: IOException) {
                deleteQuietly(resolver, uri)
                throw e
            } catch (e: SecurityException) {
                deleteQuietly(resolver, uri)
                throw e
            }
            return SavedFile(uri, displayName, mimeType, source.length())
        }

        private fun insertEntry(
            resolver: ContentResolver,
            collection: Uri,
            values: ContentValues,
        ): Uri =
            resolver.insert(collection, values)
                ?: throw IOException("Could not create a MediaStore entry")

        private fun writeEntry(
            resolver: ContentResolver,
            uri: Uri,
            source: File,
        ) {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, Storage.COPY_BUFFER_BYTES) }
            } ?: throw IOException("Could not open the MediaStore stream")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }

        private fun deleteQuietly(
            resolver: ContentResolver,
            uri: Uri,
        ) {
            try {
                resolver.delete(uri, null, null)
            } catch (e: SecurityException) {
                AppLog.e(TAG, "could not remove the pending MediaStore entry", e)
            }
        }

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
            return writeTreeFile(tree, source, mimeType, displayName)
        }

        private fun writeTreeFile(
            tree: DocumentFile,
            source: File,
            mimeType: String,
            displayName: String,
        ): SavedFile? {
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
