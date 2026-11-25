package com.grabbit.data.ytdlp

/** What a link resolved to: a single media item or a playlist / channel. */
sealed interface FetchedMedia {
    val title: String
    val uploader: String?
    val thumbnail: String?
    val sourceUrl: String

    /** A single video or audio item. */
    data class Single(
        val info: MediaInfo,
    ) : FetchedMedia {
        override val title: String get() = info.title
        override val uploader: String? get() = info.uploader
        override val thumbnail: String? get() = info.thumbnail
        override val sourceUrl: String get() = info.url
    }

    /** A playlist, channel or mix; [truncated] means yt-dlp reported more items than returned. */
    data class Collection(
        override val title: String,
        override val uploader: String?,
        override val thumbnail: String?,
        override val sourceUrl: String,
        val items: List<PlaylistItem>,
        val truncated: Boolean,
    ) : FetchedMedia
}

/** Metadata of one media item and the formats yt-dlp reported for it. */
data class MediaInfo(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long,
    val thumbnail: String?,
    val site: String?,
    val formats: List<FormatInfo>,
) {
    /** Distinct video heights, highest first. */
    val videoHeights: List<Int>
        get() =
            formats
                .asSequence()
                .filter { it.isVideo && it.height > 0 }
                .map { it.height }
                .distinct()
                .sortedDescending()
                .toList()

    val maxHeight: Int get() = videoHeights.maxOrNull() ?: 0
}

/** One downloadable stream from yt-dlp's `formats` list. */
data class FormatInfo(
    val id: String,
    val ext: String,
    val height: Int,
    val fps: Int,
    val vcodec: String?,
    val acodec: String?,
    val abr: Double,
    val tbr: Double,
    val fileSize: Long,
    val note: String?,
) {
    val isVideo: Boolean get() = !vcodec.isNullOrBlank() && vcodec != "none"
    val isAudio: Boolean get() = !acodec.isNullOrBlank() && acodec != "none" && !isVideo
}

/** One entry of a playlist or mix. */
data class PlaylistItem(
    val index: Int,
    val id: String,
    val title: String,
    val url: String,
    val durationSeconds: Long,
    val thumbnail: String?,
)
