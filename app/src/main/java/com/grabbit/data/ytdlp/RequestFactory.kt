package com.grabbit.data.ytdlp

import com.grabbit.core.Defaults
import com.grabbit.core.Languages
import com.grabbit.core.Limits
import com.grabbit.core.Links
import com.grabbit.core.Timing
import com.grabbit.core.YtDlpArgs
import com.grabbit.data.download.DownloadOptions
import com.grabbit.data.download.MediaType
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/** Everything yt-dlp needs to run a single download. */
data class DownloadSpec(
    val taskId: String,
    val url: String,
    val playlistIndex: Int?,
    val options: DownloadOptions,
    val template: String,
    val workDir: File,
    val cookiesPath: String?,
    val proxy: String?,
    val markerFile: File,
    /** Per-download SAF folder URI, overriding the global save folder. `null` = use the default. */
    val saveFolderUri: String? = null,
    /** Custom file name without extension, or `null` to use the filename template. */
    val fileName: String? = null,
    /** Fresh `--dump-single-json` output; lets yt-dlp skip extracting the link again. */
    val infoJsonPath: String? = null,
)

/** Builds yt-dlp requests; option order matters for some multi-argument flags. */
object RequestFactory {
    /**
     * Metadata request used by [com.grabbit.data.ytdlp.YtDlpEngine.fetch].
     *
     * YouTube Mix/radio URLs (`list=RD…`): by default they are fetched as
     * the single video (`--no-playlist`). With [loadMixAsPlaylist] they are
     * returned as a bounded playlist (`--playlist-end`, [Limits.MIX_PLAYLIST_END]).
     */
    fun buildFetchRequest(
        url: String,
        loadMixAsPlaylist: Boolean,
    ): YoutubeDLRequest =
        buildFetchRequestWithExtractorArgs(
            url,
            loadMixAsPlaylist,
            "youtube:player_client=${YtDlpArgs.FETCH_PLAYER_CLIENT}",
        )

    /** Variant that allows specifying the full YouTube extractor arguments for fallback logic. */
    fun buildFetchRequestWithExtractorArgs(
        url: String,
        loadMixAsPlaylist: Boolean,
        extractorArgs: String,
    ): YoutubeDLRequest =
        YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--flat-playlist")
            addOption("--playlist-end", Limits.MAX_PLAYLIST_ITEMS)
            addOption("--no-warnings")
            addOption("--ignore-config")
            addOption("--socket-timeout", Timing.FETCH_SOCKET_TIMEOUT_SECONDS)
            addOption("--extractor-retries", YtDlpArgs.FETCH_EXTRACTOR_RETRIES)
            addOption("--extractor-args", extractorArgs)
            if (Links.isRadioPlaylist(url)) {
                if (!loadMixAsPlaylist) {
                    addOption("--no-playlist")
                } else {
                    addOption("--playlist-end", Limits.MIX_PLAYLIST_END)
                }
            }
        }

    /** Builds the download request for [spec], including conversion and cookies options. */
    fun build(spec: DownloadSpec): YoutubeDLRequest {
        val options = spec.options
        val request = YoutubeDLRequest(spec.url)

        request.addOption("--ignore-config")
        request.addOption("--newline")
        request.addOption("--no-colors")
        request.addOption("--no-simulate")
        request.addOption("--continue")
        request.addOption("--no-mtime")
        request.addOption("--retries", YtDlpArgs.DOWNLOAD_RETRIES)
        request.addOption("--concurrent-fragments", YtDlpArgs.CONCURRENT_FRAGMENTS)
        request.addOption("--socket-timeout", Timing.DOWNLOAD_SOCKET_TIMEOUT_SECONDS)

        // Reuse the metadata extraction: the URL argument is ignored by yt-dlp in this mode.
        spec.infoJsonPath?.let { path -> request.addOption("--load-info-json", path) }

        // --print-to-file takes TEMPLATE and FILE. They must be consecutive
        // arguments: two separate addOption calls make yt-dlp read the second
        // option as the FILE and treat the marker path as a URL.
        request.addCommands(
            listOf("--print-to-file", "after_move:filepath", spec.markerFile.absolutePath),
        )

        request.addOption("-P", spec.workDir.absolutePath)
        request.addOption(
            "-o",
            outputTemplate(
                template = spec.template,
                playlistIndex = spec.playlistIndex,
                fileName = spec.fileName,
            ),
        )

        // Items are downloaded from their own URLs. Dynamic mixes change their
        // contents between extraction and download, so playlist positions are
        // only used for file naming, never to select a playlist entry again.
        request.addOption("--no-playlist")

        if (options.mediaType == MediaType.AUDIO) {
            request.addOption("-f", "ba/b")
            request.addOption("-x")
            request.addOption("--audio-format", options.audioFormat)
            // 0 is yt-dlp's "best" quality; explicit bitrates are kept for lower presets.
            request.addOption(
                "--audio-quality",
                if (options.audioBitrate >= Defaults.MAX_AUDIO_BITRATE_KBPS) "0" else "${options.audioBitrate}K",
            )
        } else {
            request.addOption("-f", formatSelector(options))
            request.addOption("--merge-output-format", options.container)
        }

        if (options.embedThumbnail) {
            request.addOption("--embed-thumbnail")
            // YouTube thumbnails are WebP; mp3/m4a need a JPEG to embed reliably.
            if (options.mediaType == MediaType.AUDIO) {
                request.addOption("--convert-thumbnails", "jpg")
            }
        }
        if (options.embedMetadata) request.addOption("--embed-metadata")
        if (options.subtitles) {
            request.addOption("--write-subs")
            request.addOption("--embed-subs")
            request.addOption("--sub-langs", Languages.SUPPORTED.joinToString(",") { "$it.*" })
        }
        if (options.sponsorBlock) {
            request.addOption("--sponsorblock-remove", "sponsor,selfpromo")
        }

        spec.cookiesPath?.let { request.addOption("--cookies", it) }
        spec.proxy?.takeIf { it.isNotBlank() }?.let { request.addOption("--proxy", it) }

        return request
    }

    /** Sanitizes the user template, ensures an extension and prefixes a playlist index if set. */
    fun outputTemplate(
        template: String,
        playlistIndex: Int? = null,
        fileName: String? = null,
    ): String {
        // A custom file name (single video only) takes precedence over the template.
        val base = fileName.takeIf { it?.isNotBlank() == true } ?: template
        val cleaned =
            base
                .replace("/", "-")
                .replace("\\", "-")
                .ifBlank { "%(title)s" }
        val withExt = if (cleaned.contains("%(ext)s")) cleaned else "$cleaned.%(ext)s"
        return if (playlistIndex != null) {
            "${playlistIndex.toString().padStart(YtDlpArgs.PLAYLIST_INDEX_PAD, '0')} - $withExt"
        } else {
            withExt
        }
    }

    /** yt-dlp format selector for [options], preferring matching containers and heights. */
    fun formatSelector(options: DownloadOptions): String {
        if (options.mediaType == MediaType.AUDIO) return "ba/b"
        val limit = if (options.maxHeight > 0) "[height<=${options.maxHeight}]" else ""
        return when {
            limit.isEmpty() && options.container == "mp4" ->
                "bv*[ext=mp4]+ba[ext=m4a]/bv*+ba/b"
            limit.isEmpty() ->
                "bv*+ba/b"
            options.container == "mp4" ->
                "bv*$limit[ext=mp4]+ba[ext=m4a]/bv*$limit+ba/b$limit/b"
            else ->
                "bv*$limit+ba/b$limit/b"
        }
    }
}
