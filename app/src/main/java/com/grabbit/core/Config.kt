package com.grabbit.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Timeouts, delays and polling intervals shared by the app. */
object Timing {
    val FETCH_TIMEOUT: Duration = 60.seconds
    val PROGRESS_THROTTLE: Duration = 250.milliseconds
    val SERVICE_STOP_GRACE: Duration = 1500.milliseconds
    val UPDATE_CHECK_INTERVAL: Duration = 1.days
    val FLOW_SUBSCRIPTION_GRACE: Duration = 5.seconds
    val SEARCH_DEBOUNCE: Duration = 150.milliseconds

    /** oEmbed must answer fast; anything slower is not worth blocking the instant preview on. */
    const val OEMBED_TIMEOUT_MS = 3_000
    const val NAV_ANIMATION_DURATION_MS = 250
    const val FLOATING_SNAP_DURATION_MS = 220
    const val QUEUE_PANEL_ANIMATION_DURATION_MS = 220

    const val FETCH_SOCKET_TIMEOUT_SECONDS = 20
    const val DOWNLOAD_SOCKET_TIMEOUT_SECONDS = 30
    const val STDERR_JOIN_MS = 500L

    const val PLATFORM_TRANSITION_DURATION_MS = 320
    const val PLATFORM_COLOR_DURATION_MS = 300
    const val SEARCH_HTTP_TIMEOUT_MS = 15_000

    const val SECONDS_PER_MINUTE = 60
    const val MINUTES_PER_HOUR = 60
}

/** Network defaults shared by the plain HTTP helpers. */
object Net {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
}

/** User-selectable defaults. They are shared by settings, downloads and conversion. */
object Defaults {
    const val MIN_AUDIO_BITRATE_KBPS = 128
    const val AUDIO_BITRATE_KBPS = 192
    const val MAX_AUDIO_BITRATE_KBPS = 320
    const val VIDEO_HEIGHT = 1080
    const val VIDEO_HEIGHT_720 = 720
    const val VIDEO_HEIGHT_480 = 480
    const val VIDEO_CONTAINER = "mp4"
    const val AUDIO_FORMAT = "mp3"
    const val CONCURRENT_DOWNLOADS = 2
}

/** Hard bounds used when parsing external data or generating file names. */
object Limits {
    const val MAX_PLAYLIST_ITEMS = 200
    const val MIX_PLAYLIST_END = 50
    const val MAX_SEARCH_RESULTS = 20
    const val MAX_HASHTAG_LENGTH = 50
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

/** Cache sizes and lifetimes for metadata that is reused by the fetch and download flows. */
object Cache {
    /** Number of fetch results kept in memory; reopening a recent link should never hit yt-dlp. */
    const val FETCH_MEMORY_ENTRIES = 20

    /** Parsed fetch results are reused for a few minutes only; formats and view counts go stale. */
    val FETCH_TTL: Duration = 5.minutes

    /** Disk entries are evicted only after several TTLs so a short burst of links still fits. */
    const val FETCH_EVICT_MULTIPLIER = 4

    /** Raw `--dump-single-json` output is reused by downloads, but its format URLs expire. */
    val INFO_JSON_TTL: Duration = 1.hours

    /** Raw info files are kept longer than their usable lifetime so a failed download can inspect them. */
    val INFO_JSON_RETENTION: Duration = 6.hours

    /** Cache-directory name for raw `--dump-single-json` files. */
    const val INFO_DIR = "info-json"
}

/** yt-dlp arguments that are not user settings. */
object YtDlpArgs {
    const val FETCH_EXTRACTOR_RETRIES = 2
    const val DOWNLOAD_RETRIES = 5
    const val CONCURRENT_FRAGMENTS = 4
    const val PLAYLIST_INDEX_PAD = 2

    // Fastest YouTube player client for metadata extraction (android_music is typically fastest)
    // Fallback order: android_music > android > mweb > tv_embedded
    const val FETCH_PLAYER_CLIENT = "android_music"
    const val FETCH_PLAYER_CLIENT_FALLBACK = "android"

    /**
     * Experiment: skip the YouTube webpage/configs/JS requests and use the iOS client.
     * Fewer formats and less stable than the default client, so it stays opt-in until
     * a device measurement shows a clear win. [EXPERIMENTAL_YOUTUBE_CLIENT_ENABLED]
     * controls whether [com.grabbit.data.ytdlp.YtDlpEngine] tries it first.
     */
    const val EXPERIMENTAL_YOUTUBE_EXTRACTOR_ARGS = "youtube:player_skip=webpage,configs,js;player_client=ios"
    const val EXPERIMENTAL_YOUTUBE_CLIENT_ENABLED = false
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

    const val MIDPOINT = 0.5f
    const val NAV_DOWNLOAD_UNSELECTED_SCALE = 0.92f
    const val FLOATING_INITIAL_VERTICAL_FRACTION = 0.35f
    const val FLOATING_EDGE_SNAP_FRACTION = MIDPOINT
    const val FLOATING_SCRIM_ALPHA = 0.32f

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
