package com.grabbit.core

/** Maps container extensions to MIME types used by MediaStore and SAF. */
object MimeTypes {
    /** MIME type for a file extension; unknown extensions get a generic binary type. */
    fun forExt(ext: String?): String =
        when (ext?.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            else -> "application/octet-stream"
        }

    /** Whether [mimeType] belongs to an audio track. */
    fun isAudio(mimeType: String): Boolean = mimeType.startsWith("audio/")
}
