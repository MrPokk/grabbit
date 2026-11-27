package com.grabbit.data.download

import com.grabbit.core.AppError
import com.grabbit.core.Defaults
import kotlinx.serialization.Serializable

@Serializable
enum class MediaType { VIDEO, AUDIO }

@Serializable
enum class ConvertFormat(
    val ext: String,
    val mimeType: String,
    val audioOnly: Boolean,
    val label: String,
) {
    MP4("mp4", "video/mp4", false, "MP4"),
    MKV("mkv", "video/x-matroska", false, "MKV"),
    WEBM("webm", "video/webm", false, "WebM"),
    MP3("mp3", "audio/mpeg", true, "MP3"),
    M4A("m4a", "audio/mp4", true, "M4A"),
    OPUS("opus", "audio/ogg", true, "Opus"),
    FLAC("flac", "audio/flac", true, "FLAC"),
    ;

    companion object {
        val audioTargets: List<ConvertFormat> = listOf(MP3, M4A, OPUS, FLAC)

        val videoTargets: List<ConvertFormat> = listOf(MP4, MKV, WEBM)

        fun fromExt(ext: String?): ConvertFormat? = entries.firstOrNull { it.ext.equals(ext, ignoreCase = true) }

        fun targetsFor(mediaType: MediaType): List<ConvertFormat> =
            when (mediaType) {
                MediaType.AUDIO -> audioTargets
                else -> videoTargets
            }
    }
}

@Serializable
data class DownloadOptions(
    val mediaType: MediaType = MediaType.VIDEO,
    val maxHeight: Int = 0,
    val container: String = Defaults.VIDEO_CONTAINER,
    val audioFormat: String = Defaults.AUDIO_FORMAT,
    val audioBitrate: Int = Defaults.AUDIO_BITRATE_KBPS,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val subtitles: Boolean = false,
    val sponsorBlock: Boolean = false,
    val saveFolderUri: String? = null,
    val fileName: String? = null,
) {
    val formatLabel: String
        get() =
            when (mediaType) {
                MediaType.AUDIO -> "${audioFormat.uppercase()} · $audioBitrate kbps"
                MediaType.VIDEO -> {
                    val heightLabel = if (maxHeight > 0) "$maxHeight p" else "Best"
                    "$heightLabel · ${container.uppercase()}"
                }
            }
}

data class DownloadRequest(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSeconds: Long,
    val site: String?,
    val options: DownloadOptions,
    val playlistIndex: Int? = null,
    val infoJsonPath: String? = null,
    val infoFetchedAtMs: Long = 0L,
)

enum class TaskStatus {
    QUEUED,
    PREPARING,
    DOWNLOADING,
    SAVING,
    COMPLETED,
    PAUSED,
    CANCELLED,
    FAILED,
    ;

    val isActive: Boolean
        get() =
            this == QUEUED ||
                this == PREPARING ||
                this == DOWNLOADING ||
                this == SAVING

    val isFinished: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == FAILED
}

data class DownloadTask(
    val id: String,
    val request: DownloadRequest,
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f,
    val speed: String? = null,
    val etaSeconds: Long? = null,
    val error: AppError? = null,
    val savedUri: String? = null,
    val fileName: String? = null,
    val sizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val title: String get() = request.title
    val uploader: String? get() = request.uploader
    val thumbnail: String? get() = request.thumbnail
    val options: DownloadOptions get() = request.options
}
