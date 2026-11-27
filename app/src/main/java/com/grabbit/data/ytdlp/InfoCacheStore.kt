package com.grabbit.data.ytdlp

import com.grabbit.core.AppLog
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

private const val TAG = "InfoCache"
private const val HEX_RADIX = 16
private const val BYTE_MASK = 0xff
private const val HASH_ALGORITHM = "SHA-1"

/** A cached `--dump-single-json` file and the moment it was written. */
data class CachedInfoFile(
    val path: String,
    val fetchedAtMs: Long,
)

/**
 * Stores raw yt-dlp info JSON in the cache dir so a download can start with
 * `--load-info-json` instead of extracting the same link again. Entries older
 * than [ttlMs] are not usable because YouTube/TikTok format URLs expire.
 */
class InfoCacheStore(
    private val root: File,
    private val ttlMs: Long,
    private val retentionMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Writes [rawJson] for [key]; returns `null` when the file could not be written. */
    fun save(
        key: String,
        rawJson: String,
    ): CachedInfoFile? {
        val dir = root
        if (!dir.exists() && !dir.mkdirs()) {
            AppLog.e(TAG, "could not create the info cache directory: ${dir.absolutePath}")
            return null
        }
        val target = File(dir, "${hash(key)}.json")
        val temp = File(dir, "${target.name}.tmp")
        return try {
            temp.writeText(rawJson)
            // The previous file is only a cache entry; deleting it first keeps the rename atomic enough.
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                AppLog.e(TAG, "could not move the info file into place: ${target.absolutePath}")
                return null
            }
            val savedAt = clock()
            target.setLastModified(savedAt)
            evictExpired()
            CachedInfoFile(target.absolutePath, savedAt)
        } catch (e: IOException) {
            AppLog.e(TAG, "could not write the info file", e)
            temp.delete()
            null
        } catch (e: SecurityException) {
            AppLog.e(TAG, "no permission to write the info file", e)
            null
        }
    }

    /** Whether [path] exists and was written less than [ttlMs] ago. */
    fun isFresh(
        path: String?,
        fetchedAtMs: Long,
    ): Boolean {
        if (path.isNullOrBlank() || fetchedAtMs <= 0L) return false
        if (!File(path).exists()) return false
        return clock() - fetchedAtMs <= ttlMs
    }

    /** Deletes one cached file, e.g. after its format URLs expired. */
    fun discard(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (file.exists() && !file.delete()) {
            AppLog.d(TAG) { "could not delete the stale info file: ${file.absolutePath}" }
        }
    }

    /** Drops files that are far past their usable lifetime. */
    private fun evictExpired() {
        val now = clock()
        root
            .listFiles()
            ?.filter { it.isFile && now - it.lastModified() > retentionMs }
            ?.forEach { it.delete() }
    }

    private fun hash(key: String): String =
        try {
            MessageDigest
                .getInstance(HASH_ALGORITHM)
                .digest(key.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
        } catch (e: NoSuchAlgorithmException) {
            AppLog.e(TAG, "$HASH_ALGORITHM is unavailable", e)
            // Fall back to a stable JVM hash; collisions only cost a re-extraction.
            key.hashCode().toString(HEX_RADIX)
        }
}
