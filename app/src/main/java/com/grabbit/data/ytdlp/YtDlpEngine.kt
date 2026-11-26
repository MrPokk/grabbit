package com.grabbit.data.ytdlp

import android.content.Context
import com.grabbit.R
import com.grabbit.core.AppError
import com.grabbit.core.AppLog
import com.grabbit.core.ErrorMapper
import com.grabbit.core.Links
import com.grabbit.core.Timing
import com.grabbit.core.YtDlpArgs
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.settings.SettingsRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.CanceledException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YtDlpEngine"
// Cache TTL: 5 minutes
private const val FETCH_CACHE_TTL_MS = 5 * 60 * 1000L
// Evict entries older than 4x TTL (20 minutes) to allow some grace period
private const val FETCH_CACHE_EVICT_MULTIPLIER = 4

/** yt-dlp metadata parsing failed; [error] is safe to show in the UI. */
class InfoException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.technical ?: "info failed", cause)

/** yt-dlp download failed; [error] is safe to show in the UI. */
class DownloadException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.technical ?: "download failed", cause)

/** The native download process was cancelled by the user. */
class DownloadCancelledException(
    cause: Throwable? = null,
) : Exception("cancelled", cause)

/** Result of a background yt-dlp self-update check. */
sealed interface UpdateOutcome {
    data object Updated : UpdateOutcome

    data object UpToDate : UpdateOutcome

    data class Failed(
        val message: String,
    ) : UpdateOutcome
}

/**
 * Thin coroutine-friendly wrapper around youtubedl-android. All yt-dlp calls are
 * blocking, so they run on [Dispatchers.IO] and never on the main thread.
 */
@Singleton
class YtDlpEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SettingsRepository,
        private val memoryCache: FetchMemoryCache,
        private val cacheDao: FetchCacheDao,
    ) {
        private val initLock = Mutex()

        @Volatile
        private var initialized = false

        /**
         * Extracts the bundled Python + yt-dlp + ffmpeg payloads on first use.
         * This takes a couple of seconds on a cold install, hence the background
         * warm-up in [com.grabbit.GrabbitApp].
         */
        suspend fun ensureInitialized() {
            if (initialized) return
            initLock.withLock {
                if (initialized) return
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                    val version =
                        suspendRunCatching { YoutubeDL.getInstance().version(context) }
                            .onFailure { e -> AppLog.e(TAG, "could not read the yt-dlp version", e) }
                            .getOrNull()
                    AppLog.d(TAG) { "yt-dlp version: $version" }
                }
                initialized = true
            }
        }

        /**
         * Extracts metadata. [loadMixAsPlaylist] turns a YouTube Mix/radio URL
         * into a bounded playlist (`--playlist-end`); otherwise such URLs resolve
         * to the single video.
         */
        suspend fun fetch(
            url: String,
            loadMixAsPlaylist: Boolean,
            processId: String = UUID.randomUUID().toString(),
        ): FetchedMedia {
            val normalizedUrl = Links.normalize(url)
            val cacheKey = "$normalizedUrl|$loadMixAsPlaylist"

            // Check in-memory cache first
            memoryCache.get(cacheKey)?.let { cached ->
                AppLog.d(TAG) { "fetch cache hit (memory): $cacheKey" }
                return cached
            }

            // Check persistent cache
            val cachedEntity = cacheDao.getFresh(cacheKey, System.currentTimeMillis() - FETCH_CACHE_TTL_MS)
            cachedEntity?.let { entity ->
                FetchCacheSerializer.fromJson(entity.json)?.let { cached ->
                    AppLog.d(TAG) { "fetch cache hit (disk): $cacheKey" }
                    memoryCache.put(cacheKey, cached)
                    return cached
                }
            }

            ensureInitialized()
            // Try primary player client, then fallback
            val media = try {
                withContext(Dispatchers.IO) {
                    fetchWithClient(normalizedUrl, loadMixAsPlaylist, processId, YtDlpArgs.FETCH_PLAYER_CLIENT)
                }
            } catch (e: InfoException) {
                // If primary client fails with extractor error, try fallback
                if (e.error.suggestUpdate) {
                    AppLog.d(TAG) {
                        "Primary player client failed, trying fallback: " +
                            YtDlpArgs.FETCH_PLAYER_CLIENT_FALLBACK
                    }
                    withContext(Dispatchers.IO) {
                        fetchWithClient(
                            normalizedUrl, loadMixAsPlaylist, processId,
                            YtDlpArgs.FETCH_PLAYER_CLIENT_FALLBACK
                        )
                    }
                } else {
                    throw e
                }
            }

            // Cache the result
            memoryCache.put(cacheKey, media)
            cacheDao.put(FetchCacheEntity(cacheKey, FetchCacheSerializer.toJson(media)))
            // Evict old entries periodically
            cacheDao.evictOlderThan(System.currentTimeMillis() - FETCH_CACHE_TTL_MS * FETCH_CACHE_EVICT_MULTIPLIER)

            return media
        }

        /** Kills a running metadata extraction when the user cancels or refetches. */
        fun cancelFetch(processId: String) {
            YoutubeDL.getInstance().destroyProcessById(processId)
        }

        /** Downloads [spec], reporting yt-dlp progress as a fraction and an ETA in seconds. */
        suspend fun download(
            spec: DownloadSpec,
            onProgress: (Float, Long, String) -> Unit,
        ): File {
            ensureInitialized()
            return withContext(Dispatchers.IO) {
                val request = RequestFactory.build(spec)
                AppLog.d(TAG) { "download command: ${request.buildCommand()}" }
                try {
                    val response =
                        YoutubeDL.getInstance().execute(request, spec.taskId) { progress, eta, line ->
                            onProgress(progress, eta, line)
                        }
                    AppLog.d(TAG) { "download exit=${response.exitCode} stderr=${response.err}" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: CanceledException) {
                    AppLog.e(TAG, "yt-dlp download was cancelled", e)
                    throw DownloadCancelledException(e)
                } catch (e: Exception) {
                    AppLog.e(TAG, "yt-dlp download failed: ${e.message}", e)
                    throw DownloadException(ErrorMapper.fromYtDlp(e.message, spec.url), e)
                }

                val reportedPath =
                    spec.markerFile
                        .takeIf { it.exists() }
                        ?.readLines()
                        ?.lastOrNull { it.isNotBlank() }
                        ?.trim()

                if (reportedPath.isNullOrBlank()) {
                    throw DownloadException(
                        AppError.unknown("yt-dlp did not report an output file"),
                    )
                }
                val file = File(reportedPath)
                if (!file.exists()) {
                    throw DownloadException(AppError.unknown("Output file is missing: $reportedPath"))
                }
                file
            }
        }

        /** Installed yt-dlp version string, or `null` when it cannot be read. */
        suspend fun installedVersion(): String? =
            withContext(Dispatchers.IO) {
                suspendRunCatching { YoutubeDL.getInstance().version(context) }
                    .onFailure { e -> AppLog.e(TAG, "could not read the yt-dlp version", e) }
                    .getOrNull()
            }

        /** Updates yt-dlp and records the check time; failures are reported to the caller. */
        suspend fun update(): UpdateOutcome {
            ensureInitialized()
            return withContext(Dispatchers.IO) {
                try {
                    val status =
                        YoutubeDL
                            .getInstance()
                            .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                    settings.setLastUpdateCheck(System.currentTimeMillis())
                    if (status == YoutubeDL.UpdateStatus.DONE) UpdateOutcome.Updated else UpdateOutcome.UpToDate
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e(TAG, "yt-dlp update failed: ${e.message}", e)
                    UpdateOutcome.Failed(e.message ?: e::class.java.simpleName)
                }
            }
        }

        private suspend fun fetchWithClient(
            normalizedUrl: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            playerClient: String,
        ): FetchedMedia = withContext(Dispatchers.IO) {
            val request = RequestFactory.buildFetchRequestWithClient(normalizedUrl, loadMixAsPlaylist, playerClient)
            AppLog.d(TAG) { "fetch command: ${request.buildCommand()}" }
            val timedOut = AtomicBoolean(false)
            val watchdog =
                launch {
                    delay(Timing.FETCH_TIMEOUT.inWholeMilliseconds)
                    if (YoutubeDL.getInstance().destroyProcessById(processId)) {
                        timedOut.set(true)
                    }
                }
            try {
                val response = YoutubeDL.getInstance().execute(request, processId)
                AppLog.d(TAG) {
                    "fetch exit=${response.exitCode} stdout=${response.out.length} stderr=${response.err}"
                }
                parseResponse(response.out, normalizedUrl)
            } catch (e: CancellationException) {
                YoutubeDL.getInstance().destroyProcessById(processId)
                throw e
            } catch (e: CanceledException) {
                AppLog.e(TAG, "yt-dlp fetch was cancelled", e)
                if (timedOut.get()) {
                    throw InfoException(AppError(R.string.error_fetch_timeout, e.message), e)
                }
                throw CancellationException("fetch cancelled", e)
            } catch (e: InfoException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "yt-dlp fetch failed: ${e.message}", e)
                if (timedOut.get()) {
                    throw InfoException(AppError(R.string.error_fetch_timeout, e.message), e)
                }
                throw InfoException(ErrorMapper.fromYtDlp(e.message, normalizedUrl), e)
            } finally {
                watchdog.cancel()
            }
        }

        private fun parseResponse(
            output: String,
            url: String,
        ): FetchedMedia =
            try {
                InfoParser.parse(output, url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SerializationException) {
                throw metadataParseFailure(e)
            } catch (e: IllegalArgumentException) {
                throw metadataParseFailure(e)
            } catch (e: IllegalStateException) {
                throw metadataParseFailure(e)
            }

        private fun metadataParseFailure(e: Exception): InfoException {
            AppLog.e(TAG, "metadata parse failed: ${e.message}", e)
            return InfoException(AppError.unknown("Could not read metadata: ${e.message}"))
        }
    }
