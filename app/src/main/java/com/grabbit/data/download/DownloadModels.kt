package com.grabbit.data.download

import com.grabbit.core.AppError
import com.grabbit.core.Defaults
import kotlinx.serialization.Serializable

/** Whether a download produces a video or an audio file. */
@Serializable
enum class MediaType { VIDEO, AUDIO }

/** Container/codec preset offered by the convert screen and used for downloads. */
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
        /** Audio-only conversion targets. */
        val audioTargets: List<ConvertFormat> = listOf(MP3, M4A, OPUS, FLAC)

        /** Video conversion targets. */
        val videoTargets: List<ConvertFormat> = listOf(MP4, MKV, WEBM)

        /** Looks up a preset by file extension, ignoring case. */
        fun fromExt(ext: String?): ConvertFormat? = entries.firstOrNull { it.ext.equals(ext, ignoreCase = true) }

        /** Targets that match [mediaType]. */
        fun targetsFor(mediaType: MediaType): List<ConvertFormat> =
            if (mediaType == MediaType.AUDIO) audioTargets else videoTargets
    }
}

/** Options stored with each download and reused when re-downloading from history. */
@Serializable
data class DownloadOptions(
    val mediaType: MediaType = MediaType.VIDEO,
    /** 0 means "best available". */
    val maxHeight: Int = 0,
    val container: String = Defaults.VIDEO_CONTAINER,
    val audioFormat: String = Defaults.AUDIO_FORMAT,
    val audioBitrate: Int = Defaults.AUDIO_BITRATE_KBPS,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val subtitles: Boolean = false,
    val sponsorBlock: Boolean = false,
) {
    /** Short human-readable summary shown in history and conversion results. */
    val formatLabel: String
        get() =
            when (mediaType) {
                MediaType.AUDIO -> "${audioFormat.uppercase()} · $audioBitrate kbps"
                MediaType.VIDEO ->
                    if (maxHeight > 0) {
                        "${maxHeight}p · ${container.uppercase()}"
                    } else {
                        "Best · ${container.uppercase()}"
                    }
            }
}

/** A user's download intent, before it becomes a queue task. */
data class DownloadRequest(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSeconds: Long,
    val site: String?,
    val options: DownloadOptions,
    /** Position in the source playlist, used only for file naming. */
    val playlistIndex: Int? = null,
)

/** Lifecycle of a queued download. */
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

    /** Whether the task still occupies a slot in the queue. */
    val isActive: Boolean
        get() =
            this == QUEUED ||
                this == PREPARING ||
                this == DOWNLOADING ||
                this == SAVING

    /** Whether the task can be removed from the queue. */
    val isFinished: Boolean
        get() = this == COMPLETED || this == CANCELLED || this == FAILED
}

/** One item in the download queue, including its current progress or error. */
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
