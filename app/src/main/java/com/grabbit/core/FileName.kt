package com.grabbit.core

import java.util.Locale

/** Illegal characters in a file name, replaced with `_` during sanitization. */
private val FILE_NAME_ILLEGAL_CHARS = Regex("[/\\\\:*?\"<>|]")

/** Fallback name when a user-provided file name is blank or only illegal characters. */
private const val FILE_NAME_FALLBACK = "download"

/** Suffix separator used when resolving name collisions, e.g. "video (1)". */
private const val COLLISION_SUFFIX = " (%d)"

/**
 * Pure helpers for user-provided file names: sanitizing illegal characters and
 * resolving collisions by appending " (1)", " (2)", … before the extension.
 */
object FileName {
    /**
     * Sanitizes [name] by replacing illegal characters with `_`, trimming, and
     * falling back to "[FILE_NAME_FALLBACK]" when the result is blank.
     */
    fun sanitize(name: String): String =
        name.replace(FILE_NAME_ILLEGAL_CHARS, "_").trim().ifBlank { FILE_NAME_FALLBACK }

    /**
     * Returns [candidate] if it is not already taken, otherwise appends
     * " (1)", " (2)", … before the extension until a free name is found.
     *
     * @param existing names that are already in use (case-sensitive).
     */
    fun unique(
        candidate: String,
        existing: Set<String>,
    ): String {
        if (candidate !in existing) return candidate
        // Split on the first dot so compound extensions (".tar.gz") stay
        // intact and the collision suffix lands before them: "archive (1).tar.gz".
        // Names without a dot get no extension: "video (1)".
        val dotIndex = candidate.indexOf('.')
        val base = if (dotIndex >= 0) candidate.substring(0, dotIndex) else candidate
        val ext = if (dotIndex >= 0) candidate.substring(dotIndex + 1) else ""
        var index = 1
        while (true) {
            val name =
                if (ext.isBlank()) {
                    String.format(Locale.US, "$base$COLLISION_SUFFIX", index)
                } else {
                    String.format(Locale.US, "$base$COLLISION_SUFFIX.$ext", index)
                }
            if (name !in existing) return name
            index++
        }
    }
}
