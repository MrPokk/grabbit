package com.grabbit.data.ytdlp

import android.content.Context
import com.grabbit.R
import com.grabbit.core.AppError
import com.grabbit.core.AppLog
import com.grabbit.core.Cache
import com.grabbit.core.ErrorMapper
import com.grabbit.core.FetchStage
import com.grabbit.core.FetchTrace
import com.grabbit.core.Links
import com.grabbit.core.Timing
import com.grabbit.core.YtDlpArgs
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.extract.EngineKind
import com.grabbit.data.extract.ExtractionEngine
import com.grabbit.data.settings.SettingsRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.CanceledException
import com.yausername.youtubedl_android.YoutubeDLRequest
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YtDlpEngine"

class InfoException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.technical ?: "info failed", cause)

open class DownloadException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.technical ?: "download failed", cause)

class DownloadCancelledException(
    cause: Throwable? = null,
) : Exception("cancelled", cause)

sealed interface UpdateOutcome {
    data object Updated : UpdateOutcome

    data object UpToDate : UpdateOutcome

    data class Failed(
        val message: String,
    ) : UpdateOutcome
}

@Singleton
class YtDlpEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SettingsRepository,
        private val memoryCache: FetchMemoryCache,
        private val cacheDao: FetchCacheDao,
        private val infoCache: InfoCacheStore,
    ) : ExtractionEngine {
        override val kind: EngineKind = EngineKind.YTDLP

        private val initLock = Mutex()

        @Volatile
        private var initialized = false

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

        override suspend fun fetch(
            url: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            bypassCache: Boolean,
            trace: FetchTrace?,
        ): FetchedMedia {
            val normalizedUrl = Links.normalize(url)
            val cacheKey = "$normalizedUrl|$loadMixAsPlaylist"

            if (!bypassCache) {
                val cacheStartedAt = FetchTrace.startedAt()
                trace?.record(FetchStage.CACHE_LOOKUP, FetchTrace.sinceMs(cacheStartedAt))
                val cached = cachedFetch(cacheKey)
                if (cached != null) {
                    trace?.setEngine(kind.logName)
                    return cached
                }
            }

            val initStartedAt = FetchTrace.startedAt()
            ensureInitialized()
            trace?.record(FetchStage.INIT, FetchTrace.sinceMs(initStartedAt))

            val media = fetchWithFallback(normalizedUrl, loadMixAsPlaylist, processId, trace)
            trace?.setEngine(kind.logName)

            memoryCache.put(cacheKey, media)
            cacheDao.put(FetchCacheEntity(cacheKey, FetchCacheSerializer.toJson(media)))
            val evictionBefore =
                System.currentTimeMillis() -
                    Cache.FETCH_TTL.inWholeMilliseconds * Cache.FETCH_EVICT_MULTIPLIER
            cacheDao.evictOlderThan(evictionBefore)

            return media
        }

        private suspend fun cachedFetch(cacheKey: String): FetchedMedia? {
            val memoryHit = memoryCache.get(cacheKey)
            if (memoryHit != null) {
                AppLog.d(TAG) { "fetch cache hit (memory): $cacheKey" }
                return memoryHit
            }
            val freshBefore = System.currentTimeMillis() - Cache.FETCH_TTL.inWholeMilliseconds
            val cachedEntity = cacheDao.getFresh(cacheKey, freshBefore)
            return cachedEntity?.let { entity ->
                FetchCacheSerializer.fromJson(entity.json)?.let { cached ->
                    AppLog.d(TAG) { "fetch cache hit (disk): $cacheKey" }
                    memoryCache.put(cacheKey, cached)
                    cached
                }
            }
        }

        override fun cancelFetch(processId: String) {
            YoutubeDL.getInstance().destroyProcessById(processId)
        }

        override fun cancelDownload(taskId: String) {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        }

        override suspend fun download(
            spec: DownloadSpec,
            onProgress: (Float, Long, String) -> Unit,
        ): File {
            ensureInitialized()
            return withContext(Dispatchers.IO) {
                val startedAt = FetchTrace.startedAt()
                val request = RequestFactory.build(spec)
                AppLog.d(TAG) { "download command: ${request.buildCommand()}" }
                val response =
                    try {
                        YoutubeDL.getInstance().execute(request, spec.taskId) { progress, eta, line ->
                            onProgress(progress, eta, line)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: CanceledException) {
                        AppLog.e(TAG, "yt-dlp download was cancelled", e)
                        throw DownloadCancelledException(e)
                    } catch (e: Exception) {
                        AppLog.e(TAG, "yt-dlp download failed: ${e.message}", e)
                        throw DownloadException(ErrorMapper.fromYtDlp(e.message, spec.url), e)
                    }
                AppLog.d(TAG) {
                    "download exit=${response.exitCode} run=${FetchTrace.sinceMs(startedAt)}ms " +
                        "loadInfoJson=${spec.infoJsonPath != null} stderr=${response.err}"
                }
                outputFile(spec)
            }
        }

        private fun outputFile(spec: DownloadSpec): File {
            val reportedPath =
                spec.markerFile
                    .takeIf { it.exists() }
                    ?.readLines()
                    ?.lastOrNull { it.isNotBlank() }
                    ?.trim()
            if (reportedPath.isNullOrBlank()) {
                throw DownloadException(AppError.unknown("yt-dlp did not report an output file"))
            }
            return File(reportedPath).takeIf { it.exists() }
                ?: throw DownloadException(AppError.unknown("Output file is missing: $reportedPath"))
        }

        suspend fun installedVersion(): String? =
            withContext(Dispatchers.IO) {
                suspendRunCatching { YoutubeDL.getInstance().version(context) }
                    .onFailure { e -> AppLog.e(TAG, "could not read the yt-dlp version", e) }
                    .getOrNull()
            }

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

        private suspend fun fetchWithFallback(
            normalizedUrl: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            trace: FetchTrace?,
        ): FetchedMedia {
            val experimental = tryExperimentalClient(normalizedUrl, loadMixAsPlaylist, processId, trace)
            if (experimental != null) return experimental

            val primaryClient = "youtube:player_client=${YtDlpArgs.FETCH_PLAYER_CLIENT}"
            return try {
                withContext(Dispatchers.IO) {
                    fetchWithClient(normalizedUrl, loadMixAsPlaylist, processId, primaryClient, trace)
                }
            } catch (e: InfoException) {
                if (!e.error.suggestUpdate) throw e
                AppLog.d(TAG) {
                    "Primary player client failed, trying fallback: ${YtDlpArgs.FETCH_PLAYER_CLIENT_FALLBACK}"
                }
                val fallbackClient = "youtube:player_client=${YtDlpArgs.FETCH_PLAYER_CLIENT_FALLBACK}"
                withContext(Dispatchers.IO) {
                    fetchWithClient(normalizedUrl, loadMixAsPlaylist, processId, fallbackClient, trace)
                }
            }
        }

        private suspend fun tryExperimentalClient(
            normalizedUrl: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            trace: FetchTrace?,
        ): FetchedMedia? {
            if (!YtDlpArgs.EXPERIMENTAL_YOUTUBE_CLIENT_ENABLED || !Links.isYouTube(normalizedUrl)) {
                return null
            }
            return try {
                withContext(Dispatchers.IO) {
                    fetchWithClient(
                        normalizedUrl,
                        loadMixAsPlaylist,
                        processId,
                        YtDlpArgs.EXPERIMENTAL_YOUTUBE_EXTRACTOR_ARGS,
                        trace,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: InfoException) {
                AppLog.d(TAG) { "experimental YouTube client failed, using the default: ${e.message}" }
                null
            }
        }

        private suspend fun fetchWithClient(
            normalizedUrl: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            extractorArgs: String,
            trace: FetchTrace?,
        ): FetchedMedia =
            withContext(Dispatchers.IO) {
                val processStartedAt = FetchTrace.startedAt()
                val request =
                    RequestFactory.buildFetchRequestWithExtractorArgs(
                        normalizedUrl,
                        loadMixAsPlaylist,
                        extractorArgs,
                    )
                AppLog.d(TAG) { "fetch command: ${request.buildCommand()}" }
                val timedOut = AtomicBoolean(false)
                val watchdog =
                    launch {
                        delay(Timing.FETCH_TIMEOUT.inWholeMilliseconds)
                        if (YoutubeDL.getInstance().destroyProcessById(processId)) timedOut.set(true)
                    }
                try {
                    runFetch(request, normalizedUrl, processId, trace, processStartedAt)
                } catch (e: CancellationException) {
                    YoutubeDL.getInstance().destroyProcessById(processId)
                    throw e
                } catch (e: CanceledException) {
                    handleFetchTimeout(e, timedOut)
                } catch (e: InfoException) {
                    throw e
                } catch (e: Exception) {
                    handleFetchError(e, timedOut, normalizedUrl)
                } finally {
                    watchdog.cancel()
                }
            }

        private suspend fun runFetch(
            request: YoutubeDLRequest,
            normalizedUrl: String,
            processId: String,
            trace: FetchTrace?,
            processStartedAt: Long,
        ): FetchedMedia {
            val runStartedAt = FetchTrace.startedAt()
            trace?.record(FetchStage.PROCESS_START, FetchTrace.sinceMs(processStartedAt))
            val response = YoutubeDL.getInstance().execute(request, processId)
            trace?.record(FetchStage.YTDLP_RUN, FetchTrace.sinceMs(runStartedAt))
            AppLog.d(TAG) {
                "fetch exit=${response.exitCode} stdout=${response.out.length} stderr=${response.err}"
            }
            val parsedStartedAt = FetchTrace.startedAt()
            val parsed = parseResponse(response.out, normalizedUrl)
            trace?.record(FetchStage.JSON_PARSE, FetchTrace.sinceMs(parsedStartedAt))
            return withCachedInfoFile(parsed, normalizedUrl, response.out)
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

        private fun handleFetchTimeout(
            e: CanceledException,
            timedOut: AtomicBoolean,
        ): Nothing {
            AppLog.e(TAG, "yt-dlp fetch was cancelled", e)
            if (timedOut.get()) {
                throw InfoException(AppError(R.string.error_fetch_timeout, e.message), e)
            }
            throw CancellationException("fetch cancelled", e)
        }

        private fun handleFetchError(
            e: Exception,
            timedOut: AtomicBoolean,
            normalizedUrl: String,
        ): Nothing {
            AppLog.e(TAG, "yt-dlp fetch failed: ${e.message}", e)
            if (timedOut.get()) {
                throw InfoException(AppError(R.string.error_fetch_timeout, e.message), e)
            }
            throw InfoException(ErrorMapper.fromYtDlp(e.message, normalizedUrl), e)
        }

        private fun withCachedInfoFile(
            media: FetchedMedia,
            normalizedUrl: String,
            rawJson: String,
        ): FetchedMedia {
            val single = media as? FetchedMedia.Single ?: return media
            val cached = infoCache.save(normalizedUrl, rawJson) ?: return media
            return single.copy(infoJsonPath = cached.path, infoFetchedAtMs = cached.fetchedAtMs)
        }
    }
