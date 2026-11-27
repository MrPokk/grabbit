package com.grabbit.data.newpipe

/** Engine-neutral video stream; produced by [NewPipeMapper], consumed by pure selection + tests. */
data class VideoStreamInfo(
    val url: String,
    val container: String?,
    val codec: String?,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateKbps: Int,
    val videoOnly: Boolean,
    val id: String = "",
)

/** Engine-neutral audio stream. */
data class AudioStreamInfo(
    val url: String,
    val container: String?,
    val codec: String?,
    val bitrateKbps: Int,
    val id: String = "",
)

/**
 * Pure stream-selection logic (no I/O, no NewPipe types), unit-tested.
 *
 * Quality ordering mirrors yt-dlp's `bv*+ba/b`: prefer video-only streams + a separate best audio track
 * (merged losslessly), cap height at `maxHeight`, prefer the output container, then highest resolution/bitrate.
 */
object StreamSelection {
    fun pickVideo(
        streams: List<VideoStreamInfo>,
        maxHeight: Int,
        preferContainer: String?,
    ): VideoStreamInfo? {
        val usable = streams.filter { it.url.isNotBlank() && it.height > 0 }
        if (usable.isEmpty()) return null
        val capped = if (maxHeight > 0) usable.filter { it.height <= maxHeight } else usable
        val pool = capped.ifEmpty { usable }

        val preferredContainer = preferContainer?.lowercase().orEmpty()
        return pool
            .sortedWith(
                compareByDescending<VideoStreamInfo> { containerMatches(it.container, preferredContainer) }
                    .thenByDescending { it.height }
                    .thenByDescending { it.fps }
                    .thenByDescending { it.bitrateKbps }
                    .thenByDescending { it.videoOnly },
            ).firstOrNull()
    }

    /** Best separate audio track; prefer container-compatible audio, then highest bitrate. */
    fun pickAudio(
        streams: List<AudioStreamInfo>,
        preferContainer: String?,
    ): AudioStreamInfo? {
        val usable = streams.filter { it.url.isNotBlank() }
        if (usable.isEmpty()) return null
        val preferredContainer = preferContainer?.lowercase().orEmpty()
        return usable
            .sortedWith(
                compareByDescending<AudioStreamInfo> { audioContainerMatches(it.container, preferredContainer) }
                    .thenByDescending { it.bitrateKbps },
            ).firstOrNull()
    }

    /** Whether a chosen video is already muxed (has its own audio) so no separate audio track is needed. */
    fun isCombined(video: VideoStreamInfo): Boolean = !video.videoOnly

    /** Output container extension for the merged result. */
    fun outputExt(targetContainer: String): String {
        val target = targetContainer.lowercase().ifBlank { "mp4" }
        return when (target) {
            "mkv" -> "mkv"
            "webm" -> "webm"
            else -> "mp4"
        }
    }

    private fun containerMatches(
        streamContainer: String?,
        preferred: String,
    ): Boolean {
        if (preferred.isBlank()) return false
        val c = streamContainer?.lowercase().orEmpty()
        return when (preferred) {
            "mp4" -> c == "mp4" || c == "m4a"
            "webm" -> c == "webm"
            "mkv" -> true
            else -> c == preferred
        }
    }

    private fun audioContainerMatches(
        streamContainer: String?,
        preferred: String,
    ): Boolean {
        val c = streamContainer?.lowercase().orEmpty()
        return when (preferred) {
            "mp4" -> c == "m4a" || c == "mp4" || c == "aac"
            "webm" -> c == "webm" || c == "opus"
            "mkv" -> true
            else -> c == preferred
        }
    }
}
