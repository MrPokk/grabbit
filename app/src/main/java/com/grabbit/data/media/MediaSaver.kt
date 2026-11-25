package com.grabbit.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import com.grabbit.data.download.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a finished download into scoped storage:
 *  - MediaStore (Movies/Grabbit for video, Music/Grabbit for audio) by default,
 *  - a user-picked SAF tree when one is configured.
 *
 * Downloads themselves always run in the app cache, because yt-dlp cannot write
 * to MediaStore paths directly.
 */
@Singleton
class MediaSaver @Inject constructor(
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
    ): SavedFile = withContext(Dispatchers.IO) {
        val safeName = sanitize(displayName)

        if (customFolder != null) {
            val viaTree = runCatching {
                saveToTree(source, customFolder, mimeType, safeName)
            }.getOrNull()
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
        val relativePath = if (mediaType == MediaType.AUDIO) {
            "${Environment.DIRECTORY_MUSIC}/Grabbit"
        } else {
            "${Environment.DIRECTORY_MOVIES}/Grabbit"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create a MediaStore entry")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER) }
            } ?: throw IOException("Could not open the MediaStore stream")

            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }

        return SavedFile(uri, displayName, mimeType, source.length())
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyStorage(
        source: File,
        mediaType: MediaType,
        mimeType: String,
        displayName: String,
    ): SavedFile {
        val base = if (mediaType == MediaType.AUDIO) {
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

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, target.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
        }
        val uri = context.contentResolver.insert(collectionFor(mediaType), values)
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
            source.inputStream().use { input -> input.copyTo(output, DEFAULT_BUFFER) }
        } ?: return null
        return SavedFile(file.uri, file.name ?: displayName, mimeType, source.length())
    }

    private fun collectionFor(mediaType: MediaType): Uri =
        if (mediaType == MediaType.AUDIO) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private fun uniqueFile(dir: File, displayName: String): File {
        var candidate = File(dir, displayName)
        if (!candidate.exists()) return candidate
        val base = displayName.substringBeforeLast('.', displayName)
        val ext = displayName.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists() && index < 1000) {
            val name = if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext"
            candidate = File(dir, name)
            index++
        }
        return candidate
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "download" }
    }

    private companion object {
        const val DEFAULT_BUFFER = 64 * 1024
    }
}
