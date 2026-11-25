package com.grabbit.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Timeouts, delays and polling intervals shared by the app. */
object Timing {
    val FETCH_TIMEOUT: Duration = 60.seconds
    val PROGRESS_THROTTLE: Duration = 250.milliseconds
    val SERVICE_STOP_GRACE: Duration = 1500.milliseconds
    val UPDATE_CHECK_INTERVAL: Duration = 1.days
    val FLOW_SUBSCRIPTION_GRACE: Duration = 5.seconds
    val SEARCH_DEBOUNCE: Duration = 150.milliseconds

    const val FETCH_SOCKET_TIMEOUT_SECONDS = 20
    const val DOWNLOAD_SOCKET_TIMEOUT_SECONDS = 30
    const val STDERR_JOIN_MS = 500L

    const val SECONDS_PER_MINUTE = 60
    const val MINUTES_PER_HOUR = 60
}

/** User-selectable defaults. They are shared by settings, downloads and conversion. */
object Defaults {
    const val MIN_AUDIO_BITRATE_KBPS = 128
    const val AUDIO_BITRATE_KBPS = 192
    const val MAX_AUDIO_BITRATE_KBPS = 320
    const val VIDEO_HEIGHT = 1080
    const val VIDEO_HEIGHT_720 = 720
    const val VIDEO_HEIGHT_480 = 480
    const val CONCURRENT_DOWNLOADS = 2
}

/** Hard bounds used when parsing external data or generating file names. */
object Limits {
    const val MAX_PLAYLIST_ITEMS = 200
    const val MIX_PLAYLIST_END = 50
    const val ERROR_TECHNICAL_TAIL_CHARS = 1200
    const val ERROR_TECHNICAL_DETAIL_CHARS = 600

    const val MIN_CONCURRENT_DOWNLOADS = 1
    const val MAX_CONCURRENT_DOWNLOADS = 5
    const val COOKIE_HEADER_LINES = 4
    const val MIN_HOST_LENGTH = 3
}

/** File-copy and file-name limits. */
object Storage {
    const val COPY_BUFFER_BYTES = 64 * 1024
    const val MAX_UNIQUE_FILE_INDEX = 1000
    const val COOKIES_FILE_NAME = "cookies.txt"
}

/** Number formats and thresholds used by [Formatters] and progress calculations. */
object Formatting {
    const val BYTES_BASE = 1024
    const val BYTES_PRECISION = 10
    const val PERCENT_SCALE = 100
    const val PERCENT_SCALE_F = 100f
    const val PROGRESS_MAX_PERCENT = 100
    const val PROGRESS_CONVERT_MAX_PERCENT = 99
    const val MICROS_PER_MILLIS = 1000.0
    const val MILLIS_PER_SECOND = 1000

    const val RESOLUTION_8K_HEIGHT = 4320
    const val RESOLUTION_4K_HEIGHT = 2160
    const val RESOLUTION_1440_HEIGHT = 1440
    const val FPS_THRESHOLD = 50
}

/** yt-dlp arguments that are not user settings. */
object YtDlpArgs {
    const val FETCH_EXTRACTOR_RETRIES = 2
    const val DOWNLOAD_RETRIES = 5
    const val CONCURRENT_FRAGMENTS = 4
    const val PLAYLIST_INDEX_PAD = 2
}

/** FFmpeg arguments and resource limits. */
object FfmpegArgs {
    const val CRF = 22
    const val VP9_CRF = 34
    const val VP9_BITRATE_KBPS = Defaults.MIN_AUDIO_BITRATE_KBPS
    const val STDERR_LIMIT_BYTES = 8000
    const val FASTSTART_AUDIO_BITRATE_KBPS = Defaults.AUDIO_BITRATE_KBPS
}

/** Fixed values exposed to the UI: notification id, sliders and preset lists. */
object UiValues {
    const val FOREGROUND_NOTIFICATION_ID = 1000
    const val PROGRESS_MAX = Formatting.PERCENT_SCALE
    const val MIN_DOWNLOADS = Limits.MIN_CONCURRENT_DOWNLOADS
    const val MAX_DOWNLOADS = Limits.MAX_CONCURRENT_DOWNLOADS
    const val CONCURRENCY_SLIDER_STEPS = 3
    const val THUMBNAIL_ASPECT_RATIO = 16f / 9f
    const val DETAILS_MAX_LINES = 12

    val BITRATES =
        listOf(
            Defaults.MIN_AUDIO_BITRATE_KBPS,
            Defaults.AUDIO_BITRATE_KBPS,
            256,
            Defaults.MAX_AUDIO_BITRATE_KBPS,
        )
    val VIDEO_HEIGHTS =
        listOf(
            0,
            Formatting.RESOLUTION_4K_HEIGHT,
            Formatting.RESOLUTION_1440_HEIGHT,
            Defaults.VIDEO_HEIGHT,
            Defaults.VIDEO_HEIGHT_720,
            Defaults.VIDEO_HEIGHT_480,
        )
    val FALLBACK_HEIGHTS =
        listOf(
            Formatting.RESOLUTION_4K_HEIGHT,
            Formatting.RESOLUTION_1440_HEIGHT,
            Defaults.VIDEO_HEIGHT,
            Defaults.VIDEO_HEIGHT_720,
            Defaults.VIDEO_HEIGHT_480,
            360,
            240,
            144,
        )
    val CONTAINERS = listOf("mp4", "mkv", "webm")
    val AUDIO_FORMATS = listOf("mp3", "m4a", "opus", "flac")
}
