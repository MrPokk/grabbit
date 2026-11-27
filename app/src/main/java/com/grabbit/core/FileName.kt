package com.grabbit.core

import java.util.Locale

private val FILE_NAME_ILLEGAL_CHARS = Regex("[/\\\\:*?\"<>|]")

private const val FILE_NAME_FALLBACK = "download"

private const val COLLISION_SUFFIX = " (%d)"

object FileName {
    fun sanitize(name: String): String =
        name.replace(FILE_NAME_ILLEGAL_CHARS, "_").trim().ifBlank { FILE_NAME_FALLBACK }

    fun unique(
        candidate: String,
        existing: Set<String>,
    ): String {
        if (candidate !in existing) return candidate

        // Split on the first dot so compound extensions (".tar.gz") stay intact; suffix lands before them.
        val dotIndex = candidate.indexOf('.')
        val base = if (dotIndex >= 0) candidate.substring(0, dotIndex) else candidate
        val ext = if (dotIndex >= 0) candidate.substring(dotIndex + 1) else ""
        val suffix = if (ext.isBlank()) "" else ".$ext"
        return generateSequence(1) { it + 1 }
            .map { String.format(Locale.US, "$base$COLLISION_SUFFIX$suffix", it) }
            .first { it !in existing }
    }
}
