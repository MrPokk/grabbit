package com.grabbit.core

/**
 * Filename template handling shared by yt-dlp (`-o` templates) and engines that name files themselves.
 *
 * yt-dlp performs expansion; [expand] reproduces the supported subset for engines that can't.
 * Templates use `%(name)s`; `%%` is a literal percent. Slashes/backslashes can't create subdirectories.
 */
object FilenameTemplate {
    private const val PLAYLIST_INDEX_PAD = 2

    private val FIELD_REGEX = Regex("""%\((\w+)\)s|%%""")

    /**
     * Build the output-template string (not yet expanded), matching [com.grabbit.data.ytdlp.RequestFactory]:
     * a custom [fileName] wins over [template]; `%(ext)s` is appended once; `%(ext)s` left in place;
     * playlist items are prefixed `NN - `; blanks fall back to `%(title)s`; `/` `\` -> `-`.
     */
    fun outputTemplate(
        template: String,
        playlistIndex: Int? = null,
        fileName: String? = null,
    ): String {
        val base = fileName.takeIf { it?.isNotBlank() == true } ?: template
        val cleaned =
            base
                .replace("/", "-")
                .replace("\\", "-")
                .ifBlank { "%(title)s" }
        val withExt = if (cleaned.contains("%(ext)s")) cleaned else "$cleaned.%(ext)s"
        return if (playlistIndex != null) {
            "${playlistIndex.toString().padStart(PLAYLIST_INDEX_PAD, '0')} - $withExt"
        } else {
            withExt
        }
    }

    /**
     * Expand [template] with [values]; `%(ext)s` uses [ext]; `%%` -> `%`; unknown fields -> empty.
     * Result is sanitized (no path separators, no illegal filename chars) and blank -> [fallback].
     */
    fun expand(
        template: String,
        values: Map<String, String>,
        ext: String,
        fallback: String = FileName.FALLBACK_NAME,
    ): String {
        val fields = values + mapOf("ext" to ext)
        val expanded =
            FIELD_REGEX.replace(template) { match ->
                when {
                    match.value == "%%" -> "%"
                    else -> fields[match.groupValues[1]].orEmpty()
                }
            }
        return FileName.sanitize(expanded).ifBlank { fallback }
    }
}
