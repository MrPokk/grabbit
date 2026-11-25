package com.grabbit.data.ytdlp

/** What a link resolved to: a single media item or a playlist / channel. */
sealed interface FetchedMedia {
    val title: String
    val uploader: String?
    val thumbnail: String?
    val sourceUrl: String

    data class Single(val info: MediaInfo) : FetchedMedia {
        override val title: String get() = info.title
        override val uploader: String? get() = info.uploader
        override val thumbnail: String? get() = info.thumbnail
        override val sourceUrl: String get() = info.url
    }

    data class Collection(
        override val title: String,
        override val uploader: String?,
        override val thumbnail: String?,
        override val sourceUrl: String,
        val items: List<PlaylistItem>,
        val truncated: Boolean,
    ) : FetchedMedia
}

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
    val videoHeights: List<Int>
        get() = formats.asSequence()
            .filter { it.isVideo && it.height > 0 }
            .map { it.height }
            .distinct()
            .sortedDescending()
            .toList()

    val maxHeight: Int get() = videoHeights.maxOrNull() ?: 0
}

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

data class PlaylistItem(
    val index: Int,
    val id: String,
    val title: String,
    val url: String,
    val durationSeconds: Long,
    val thumbnail: String?,
)
