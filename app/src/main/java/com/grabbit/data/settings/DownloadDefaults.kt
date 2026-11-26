package com.grabbit.data.settings

import com.grabbit.core.Defaults
import com.grabbit.core.UiValues
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.MediaType

/** Builds the options a download would get from the current settings and available heights. */
fun AppSettings.toDownloadOptions(
    mediaType: MediaType,
    availableHeights: List<Int>,
): DownloadOptions {
    val height =
        availableHeights.firstOrNull { it <= defaultVideoHeight }
            ?: availableHeights.maxOrNull()
            ?: Defaults.VIDEO_HEIGHT
    return DownloadOptions(
        mediaType = mediaType,
        maxHeight = if (mediaType == MediaType.VIDEO) height else 0,
        container = defaultVideoContainer.takeIf { it in UiValues.CONTAINERS } ?: Defaults.VIDEO_CONTAINER,
        audioFormat = defaultAudioFormat.takeIf { it in UiValues.AUDIO_FORMATS } ?: Defaults.AUDIO_FORMAT,
        audioBitrate = defaultAudioBitrate.takeIf { it in UiValues.BITRATES } ?: Defaults.AUDIO_BITRATE_KBPS,
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        subtitles = subtitles,
        sponsorBlock = sponsorBlock,
    )
}
