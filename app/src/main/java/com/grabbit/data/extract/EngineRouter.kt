package com.grabbit.data.extract

import com.grabbit.core.AppLog
import com.grabbit.core.ErrorSignals
import com.grabbit.core.FetchTrace
import com.grabbit.core.Links
import com.grabbit.data.newpipe.NewPipeEngine
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.ytdlp.DownloadCancelledException
import com.grabbit.data.ytdlp.DownloadException
import com.grabbit.data.ytdlp.DownloadSpec
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EngineRouter"
private const val MUSIC_YOUTUBE_HOST = "music.youtube.com"

/** Pure routing + fallback policy, unit-tested without Android. */
object EngineRouting {
    /** YouTube/youtu.be/music.youtube.com hosts. */
    fun isYouTube(url: String): Boolean = Links.isYouTube(url) || Links.host(url) == MUSIC_YOUTUBE_HOST

    /**
     * Engine kind for [url] under [preference]. Non-YouTube always yt-dlp; YouTube -> NewPipe only in Auto.
     */
    fun kindFor(
        url: String,
        preference: ExtractionEnginePreference,
    ): EngineKind =
        when {
            preference != ExtractionEnginePreference.AUTO -> EngineKind.YTDLP
            isYouTube(url) -> EngineKind.NEWPIPE
            else -> EngineKind.YTDLP
        }

    /** A fetch result is usable if a single has any playable format; collections are always usable. */
    fun hasUsableFormats(media: FetchedMedia): Boolean =
        when (media) {
            is FetchedMedia.Single -> media.info.formats.any { it.isVideo || it.isAudio }
            is FetchedMedia.Collection -> true
        }
}

/**
 * Routes extraction to an engine.
 *
 * - YouTube/youtu.be/music.youtube.com -> [NewPipeEngine]; everything else -> [YtDlpEngine] (Auto mode).
 * - If [NewPipeEngine] throws, returns no usable formats, or a download fails with an expired/403 URL,
 *   retry once with [YtDlpEngine]; whichever engine produced the result is logged + recorded on the trace.
 * - [ExtractionEnginePreference.YTDLP_ONLY] always uses [YtDlpEngine].
 * - Mix/radio handling, URL normalization, caches and fetch-cancel are delegated to engines.
 */
@Singleton
class EngineRouter
    internal constructor(
        private val ytDlp: ExtractionEngine,
        private val newPipe: ExtractionEngine,
        private val preferenceProvider: suspend () -> ExtractionEnginePreference,
    ) {
        @Inject
        constructor(
            ytDlp: YtDlpEngine,
            newPipe: NewPipeEngine,
            settings: SettingsRepository,
        ) : this(ytDlp, newPipe, { settings.current().extractionEngine })

        suspend fun engineFor(url: String): ExtractionEngine =
            if (kindFor(url) == EngineKind.NEWPIPE) newPipe else ytDlp

        suspend fun kindFor(url: String): EngineKind = EngineRouting.kindFor(url, preferenceProvider())

        /** Pure routing for tests. */
        fun routeKind(
            url: String,
            preference: ExtractionEnginePreference,
        ): EngineKind = EngineRouting.kindFor(url, preference)

        suspend fun fetch(
            url: String,
            loadMixAsPlaylist: Boolean,
            processId: String = UUID.randomUUID().toString(),
            bypassCache: Boolean = false,
            trace: FetchTrace? = null,
        ): FetchedMedia {
            val normalizedUrl = Links.normalize(url)
            val kind = kindFor(normalizedUrl)
            if (kind != EngineKind.NEWPIPE) {
                return ytDlp.fetch(normalizedUrl, loadMixAsPlaylist, processId, bypassCache, trace)
            }

            return try {
                val media = newPipe.fetch(normalizedUrl, loadMixAsPlaylist, processId, bypassCache, trace)
                if (!EngineRouting.hasUsableFormats(media)) {
                    AppLog.d(TAG) { "newpipe returned no usable formats, falling back to yt-dlp: $normalizedUrl" }
                    fallbackFetch(normalizedUrl, loadMixAsPlaylist, processId, bypassCache, trace)
                } else {
                    logEngine(EngineKind.NEWPIPE, normalizedUrl, trace)
                    media
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InfoException) {
                AppLog.e(TAG, "newpipe fetch failed, falling back to yt-dlp: ${e.message}", e)
                fallbackFetch(normalizedUrl, loadMixAsPlaylist, processId, bypassCache, trace)
            } catch (e: Exception) {
                AppLog.e(TAG, "newpipe fetch failed, falling back to yt-dlp: ${e.message}", e)
                fallbackFetch(normalizedUrl, loadMixAsPlaylist, processId, bypassCache, trace)
            }
        }

        fun cancelFetch(processId: String) {
            ytDlp.cancelFetch(processId)
            newPipe.cancelFetch(processId)
        }

        suspend fun download(
            spec: DownloadSpec,
            onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
        ): File {
            val kind = kindFor(spec.url)
            // Subtitles + SponsorBlock are only implemented by the yt-dlp path; don't silently drop them.
            if (kind != EngineKind.NEWPIPE || spec.options.subtitles || spec.options.sponsorBlock) {
                return ytDlp.download(spec, onProgress)
            }

            return try {
                val file = newPipe.download(spec, onProgress)
                AppLog.d(TAG) { "download engine=${EngineKind.NEWPIPE.logName} url=${spec.url}" }
                file
            } catch (e: DownloadCancelledException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadException) {
                AppLog.e(TAG, "newpipe download failed, falling back to yt-dlp: ${e.message}", e)
                fallbackDownload(spec, onProgress, e)
            } catch (e: Exception) {
                AppLog.e(TAG, "newpipe download failed, falling back to yt-dlp: ${e.message}", e)
                fallbackDownload(spec, onProgress, e)
            }
        }

        fun cancelDownload(taskId: String) {
            ytDlp.cancelDownload(taskId)
            newPipe.cancelDownload(taskId)
        }

        private suspend fun fallbackFetch(
            normalizedUrl: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            bypassCache: Boolean,
            trace: FetchTrace?,
        ): FetchedMedia {
            val media = ytDlp.fetch(normalizedUrl, loadMixAsPlaylist, processId, bypassCache, trace)
            logEngine(EngineKind.YTDLP, normalizedUrl, trace, fallback = true)
            return media
        }

        private suspend fun fallbackDownload(
            spec: DownloadSpec,
            onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
            cause: Throwable,
        ): File {
            val expired = ErrorSignals.isExpiredFormatUrl(cause.message)
            AppLog.d(TAG) { "fallback to yt-dlp (expired=$expired) url=${spec.url}" }
            val file = ytDlp.download(spec, onProgress)
            AppLog.d(TAG) { "download engine=${EngineKind.YTDLP.logName} url=${spec.url}" }
            return file
        }

        private fun logEngine(
            kind: EngineKind,
            url: String,
            trace: FetchTrace?,
            fallback: Boolean = false,
        ) {
            trace?.setEngine(if (fallback) "${kind.logName}(fallback)" else kind.logName)
            AppLog.d(TAG) { "fetch engine=${kind.logName} fallback=$fallback url=$url" }
        }
    }
