package com.grabbit.data.newpipe

import com.grabbit.data.ytdlp.FormatInfo
import com.grabbit.data.ytdlp.PlaylistItem
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

/** Maps NewPipeExtractor types to engine-neutral + app models. No other package touches NewPipe types. */
object NewPipeMapper {
    fun toVideoStreamInfo(stream: VideoStream): VideoStreamInfo =
        VideoStreamInfo(
            url = stream.url.orEmpty(),
            container = containerOf(stream),
            codec = stream.codec,
            width = safeInt { stream.width },
            height = safeInt { stream.height },
            fps = safeInt { stream.fps },
            bitrateKbps = safeInt { stream.bitrate },
            videoOnly = safeBool { stream.isVideoOnly },
            id = stream.id.orEmpty(),
        )

    fun toAudioStreamInfo(stream: AudioStream): AudioStreamInfo =
        AudioStreamInfo(
            url = stream.url.orEmpty(),
            container = containerOf(stream),
            codec = stream.codec,
            bitrateKbps = safeInt { stream.averageBitrate },
            id = stream.id.orEmpty(),
        )

    fun toVideoFormatInfo(stream: VideoStream): FormatInfo {
        val info = toVideoStreamInfo(stream)
        return FormatInfo(
            id = info.id.ifBlank { info.url },
            ext = info.container.orEmpty().ifBlank { "mp4" },
            height = info.height,
            fps = info.fps,
            vcodec = info.codec,
            acodec = if (info.videoOnly) null else "unknown",
            abr = 0.0,
            tbr = info.bitrateKbps.toDouble(),
            fileSize = 0L,
            note = null,
            url = info.url.ifBlank { null },
        )
    }

    fun toAudioFormatInfo(stream: AudioStream): FormatInfo {
        val info = toAudioStreamInfo(stream)
        return FormatInfo(
            id = info.id.ifBlank { info.url },
            ext = info.container.orEmpty().ifBlank { "m4a" },
            height = 0,
            fps = 0,
            vcodec = null,
            acodec = info.codec,
            abr = info.bitrateKbps.toDouble(),
            tbr = info.bitrateKbps.toDouble(),
            fileSize = 0L,
            note = null,
            url = info.url.ifBlank { null },
        )
    }

    fun toPlaylistItem(
        item: StreamInfoItem,
        index: Int,
        fallbackUrl: String,
    ): PlaylistItem =
        PlaylistItem(
            index = index,
            id =
                item.url
                    .orEmpty()
                    .substringAfterLast('=')
                    .ifBlank { item.name ?: index.toString() },
            title = item.name ?: item.url.orEmpty().ifBlank { "item $index" },
            url = item.url.orEmpty().ifBlank { fallbackUrl },
            durationSeconds = safeLong { item.duration },
            thumbnail = bestThumbnail(item.thumbnails),
        )

    fun bestThumbnail(images: List<Image>?): String? {
        val urls = images.orEmpty().mapNotNull { it.url?.takeIf(String::isNotBlank) }
        return urls.maxByOrNull { it.length } ?: urls.lastOrNull()
    }

    private fun containerOf(stream: org.schabi.newpipe.extractor.stream.Stream): String? =
        stream.format
            ?.suffix
            ?.lowercase()
            ?.ifBlank { null }

    @Suppress("SwallowedException")
    private inline fun safeInt(block: () -> Int): Int =
        try {
            block()
        } catch (e: RuntimeException) {
            0
        }

    @Suppress("SwallowedException")
    private inline fun safeBool(block: () -> Boolean): Boolean =
        try {
            block()
        } catch (e: RuntimeException) {
            false
        }

    @Suppress("SwallowedException")
    private inline fun safeLong(block: () -> Long): Long =
        try {
            block()
        } catch (e: RuntimeException) {
            0L
        }
}
