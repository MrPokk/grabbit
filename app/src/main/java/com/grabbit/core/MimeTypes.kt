package com.grabbit.core

private const val DEFAULT_MIME_TYPE = "application/octet-stream"

private val EXTENSION_TO_MIME: Map<String, String> =
    mapOf(
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "opus" to "audio/ogg",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "wav" to "audio/wav",
        "aac" to "audio/aac",
        "srt" to "application/x-subrip",
        "vtt" to "text/vtt",
    )

object MimeTypes {
    fun forExt(ext: String?): String = EXTENSION_TO_MIME[ext?.lowercase()] ?: DEFAULT_MIME_TYPE

    fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")
}
