package com.grabbit.data.newpipe

import com.grabbit.core.AppError
import com.grabbit.core.AppLog
import com.grabbit.core.Cache
import com.grabbit.core.FetchStage
import com.grabbit.core.FetchTrace
import com.grabbit.core.FileName
import com.grabbit.core.FilenameTemplate
import com.grabbit.core.Limits
import com.grabbit.core.Links
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.convert.AudioEmbedArgs
import com.grabbit.data.convert.FfmpegRunner
import com.grabbit.data.download.ConvertFormat
import com.grabbit.data.download.MediaType
import com.grabbit.data.extract.EngineKind
import com.grabbit.data.extract.ExtractionEngine
import com.grabbit.data.ytdlp.DownloadCancelledException
import com.grabbit.data.ytdlp.DownloadException
import com.grabbit.data.ytdlp.DownloadSpec
import com.grabbit.data.ytdlp.FetchCacheDao
import com.grabbit.data.ytdlp.FetchCacheEntity
import com.grabbit.data.ytdlp.FetchCacheSerializer
import com.grabbit.data.ytdlp.FetchMemoryCache
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.FormatInfo
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.MediaInfo
import com.grabbit.data.ytdlp.PlaylistItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Extractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NewPipeEngine"

/** Engine-related failure inside [NewPipeEngine.download]; the router falls back to yt-dlp. */
class NewPipeDownloadException(
    message: String,
    cause: Throwable? = null,
) : DownloadException(AppError.unknown(message), cause)

/** Per-engine cache namespace so NewPipe results don't collide with yt-dlp cache entries. */
private const val NEWPIPE_CACHE_PREFIX = "newpipe|"

/** Video + separate-audio progress split, then merge/convert tail. */
private const val VIDEO_PROGRESS_SCALE = 55f
private const val AUDIO_PROGRESS_SCALE = 40f
private const val AUDIO_DOWNLOAD_SCALE = 85f
private const val MERGE_PROGRESS_PERCENT = 96f
private const val CONVERT_PROGRESS_PERCENT = 92f
private const val FULL_PROGRESS_PERCENT = 100f
private const val MAX_TITLE_FALLBACK = 120
private const val CANCELLED_MESSAGE = "cancelled"

/** Params dropped when reducing a radio/mix URL to its single video. */
private val RADIO_PARAMS = setOf("list", "start_radio", "radio", "index", "t")

/**
 * NewPipeExtractor-backed engine for YouTube. Only this package imports `org.schabi.newpipe`.
 *
 * Fetch maps NewPipe output to app models; download re-extracts streams, downloads them chunked +
 * parallel, merges/converts with bundled FFmpeg, and embeds cover + metadata like the yt-dlp path.
 */
@Singleton
@Suppress("TooManyFunctions")
class NewPipeEngine
    @Inject
    constructor(
        private val memoryCache: FetchMemoryCache,
        private val cacheDao: FetchCacheDao,
        private val ffmpeg: FfmpegRunner,
    ) : ExtractionEngine {
        override val kind: EngineKind = EngineKind.NEWPIPE

        private val client = newPipeOkHttpClient()
        private val chunkedDownloader = ChunkedDownloader(client)
        private val initLock = Mutex()

        @Volatile
        private var initialized = false

        private val fetchCancelled = ConcurrentHashMap<String, AtomicBoolean>()
        private val downloadCancelled = ConcurrentHashMap<String, AtomicBoolean>()

        suspend fun ensureInitialized() {
            if (initialized) return
            initLock.withLock {
                if (initialized) return
                withContext(Dispatchers.IO) { NewPipe.init(OkHttpDownloader(client)) }
                initialized = true
            }
        }

        // ---- Fetch ----

        override suspend fun fetch(
            url: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            bypassCache: Boolean,
            trace: FetchTrace?,
        ): FetchedMedia =
            withContext(Dispatchers.IO) {
                val normalizedUrl = Links.normalize(url)
                val cacheKey = "$NEWPIPE_CACHE_PREFIX$normalizedUrl|$loadMixAsPlaylist"

                if (!bypassCache) {
                    val cacheStartedAt = FetchTrace.startedAt()
                    trace?.record(FetchStage.CACHE_LOOKUP, FetchTrace.sinceMs(cacheStartedAt))
                    cachedFetch(cacheKey)?.let { cached ->
                        trace?.setEngine(kind.logName)
                        return@withContext cached
                    }
                }

                val initStartedAt = FetchTrace.startedAt()
                ensureInitialized()
                trace?.record(FetchStage.INIT, FetchTrace.sinceMs(initStartedAt))

                fetchCancelled[processId] = AtomicBoolean(false)
                try {
                    val media = fetchInternal(normalizedUrl, loadMixAsPlaylist, processId, trace)
                    throwIfFetchCancelled(processId)

                    memoryCache.put(cacheKey, media)
                    cacheDao.put(FetchCacheEntity(cacheKey, FetchCacheSerializer.toJson(media)))
                    cacheDao.evictOlderThan(
                        System.currentTimeMillis() - Cache.FETCH_TTL.inWholeMilliseconds * Cache.FETCH_EVICT_MULTIPLIER,
                    )
                    trace?.setEngine(kind.logName)
                    media
                } catch (e: CancellationException) {
                    throw e
                } catch (e: InfoException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e(TAG, "newpipe fetch failed: ${e.message}", e)
                    throw InfoException(AppError.unknown("NewPipe: ${e.message}"), e)
                } finally {
                    fetchCancelled.remove(processId)
                }
            }

        override fun cancelFetch(processId: String) {
            fetchCancelled[processId]?.set(true)
        }

        private suspend fun cachedFetch(cacheKey: String): FetchedMedia? {
            memoryCache.get(cacheKey)?.let { return it }
            val freshBefore = System.currentTimeMillis() - Cache.FETCH_TTL.inWholeMilliseconds
            val entity = cacheDao.getFresh(cacheKey, freshBefore) ?: return null
            return FetchCacheSerializer.fromJson(entity.json)?.also { memoryCache.put(cacheKey, it) }
        }

        private suspend fun fetchInternal(
            normalizedUrl: String,
            loadMixAsPlaylist: Boolean,
            processId: String,
            trace: FetchTrace?,
        ): FetchedMedia {
            val service = service()
            val radio = Links.isRadioPlaylist(normalizedUrl)

            // Radio/mix: default is the single video; only load as a (capped) playlist when asked.
            val effectiveUrl = if (radio && !loadMixAsPlaylist) stripToSingleVideo(normalizedUrl) else normalizedUrl

            val wantPlaylist = !(radio && !loadMixAsPlaylist) && isPlaylist(service, effectiveUrl)
            throwIfFetchCancelled(processId)

            return if (wantPlaylist) {
                val cap =
                    if (radio && loadMixAsPlaylist) Limits.MIX_PLAYLIST_END else Limits.MAX_PLAYLIST_ITEMS
                fetchPlaylist(service, effectiveUrl, cap, processId, trace)
            } else {
                fetchSingle(service, effectiveUrl, processId, trace)
            }
        }

        private suspend fun fetchSingle(
            service: StreamingService,
            url: String,
            processId: String,
            trace: FetchTrace?,
        ): FetchedMedia.Single {
            val extractor = service.getStreamExtractor(url)
            fetchPage(extractor)
            throwIfFetchCancelled(processId)

            val parsedStartedAt = FetchTrace.startedAt()
            val info =
                MediaInfo(
                    id = safeString { extractor.id }.orEmpty().ifBlank { url.hashCode().toString() },
                    url = safeString { extractor.originalUrl }.orEmpty().ifBlank { url },
                    title = safeString { extractor.name }.orEmpty().ifBlank { url },
                    uploader = safeString { extractor.uploaderName },
                    durationSeconds = safeLong { extractor.length },
                    thumbnail = NewPipeMapper.bestThumbnail(safeList { extractor.thumbnails }),
                    site = service.serviceInfo.name,
                    formats = collectFormats(extractor),
                )
            trace?.record(FetchStage.JSON_PARSE, FetchTrace.sinceMs(parsedStartedAt))
            return FetchedMedia.Single(info)
        }

        private suspend fun fetchPlaylist(
            service: StreamingService,
            url: String,
            cap: Int,
            processId: String,
            trace: FetchTrace?,
        ): FetchedMedia.Collection {
            val extractor = service.getPlaylistExtractor(url)
            fetchPage(extractor)
            throwIfFetchCancelled(processId)

            val items = mutableListOf<PlaylistItem>()
            var page = extractor.initialPage
            var truncated = false
            val parsedStartedAt = FetchTrace.startedAt()

            while (true) {
                page.items.orEmpty().forEach { item ->
                    if (items.size >= cap) {
                        truncated = true
                    } else {
                        items.add(NewPipeMapper.toPlaylistItem(item, items.size + 1, url))
                    }
                }
                if (items.size >= cap) {
                    truncated = truncated || page.hasNextPage()
                    break
                }
                if (!page.hasNextPage()) break
                throwIfFetchCancelled(processId)
                page = extractor.getPage(page.nextPage)
            }

            trace?.record(FetchStage.JSON_PARSE, FetchTrace.sinceMs(parsedStartedAt))
            return FetchedMedia.Collection(
                title = safeString { extractor.name }.orEmpty().ifBlank { url },
                uploader = safeString { extractor.uploaderName },
                thumbnail = NewPipeMapper.bestThumbnail(safeList { extractor.thumbnails }),
                sourceUrl = url,
                items = items,
                truncated = truncated,
            )
        }

        private fun collectFormats(extractor: StreamExtractor): List<FormatInfo> {
            val videoOnly = safeList { extractor.videoOnlyStreams }.map(NewPipeMapper::toVideoFormatInfo)
            val combined = safeList { extractor.videoStreams }.map(NewPipeMapper::toVideoFormatInfo)
            val audio = safeList { extractor.audioStreams }.map(NewPipeMapper::toAudioFormatInfo)
            return (videoOnly + combined + audio).filter { it.isVideo || it.isAudio }
        }

        // ---- Download ----

        override suspend fun download(
            spec: DownloadSpec,
            onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
        ): File =
            withContext(Dispatchers.IO) {
                ensureInitialized()
                val taskId = spec.taskId
                downloadCancelled[taskId] = AtomicBoolean(false)
                val workDir = File(spec.workDir, "newpipe-$taskId").apply { mkdirs() }

                try {
                    throwIfDownloadCancelled(taskId)
                    val extractor = service().getStreamExtractor(spec.url)
                    fetchPage(extractor)
                    throwIfDownloadCancelled(taskId)

                    val title = safeString { extractor.name }.orEmpty()
                    val uploader = safeString { extractor.uploaderName }

                    val result =
                        if (spec.options.mediaType == MediaType.AUDIO) {
                            downloadAudio(extractor, spec, workDir, title, uploader, taskId, onProgress)
                        } else {
                            downloadVideo(extractor, spec, workDir, title, uploader, taskId, onProgress)
                        }
                    throwIfDownloadCancelled(taskId)
                    result
                } catch (e: DownloadCancelledException) {
                    throw e
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NewPipeDownloadException) {
                    throw e
                } catch (e: DownloadException) {
                    throw e
                } catch (e: Exception) {
                    if (isDownloadCancelled(taskId)) throw DownloadCancelledException(e)
                    AppLog.e(TAG, "newpipe download failed: ${e.message}", e)
                    throw NewPipeDownloadException("newpipe download failed: ${e.message}", e)
                } finally {
                    downloadCancelled.remove(taskId)
                }
            }

        override fun cancelDownload(taskId: String) {
            downloadCancelled[taskId]?.set(true)
            ffmpeg.cancel(taskId)
        }

        private suspend fun downloadVideo(
            extractor: StreamExtractor,
            spec: DownloadSpec,
            workDir: File,
            title: String,
            uploader: String?,
            taskId: String,
            onProgress: (Float, Long, String) -> Unit,
        ): File {
            val container =
                spec.options.container
                    .lowercase()
                    .ifBlank { "mp4" }
            val maxHeight = spec.options.maxHeight

            val videos =
                safeList { extractor.videoOnlyStreams }.map(NewPipeMapper::toVideoStreamInfo) +
                    safeList { extractor.videoStreams }.map(NewPipeMapper::toVideoStreamInfo)
            val audios = safeList { extractor.audioStreams }.map(NewPipeMapper::toAudioStreamInfo)

            val video =
                StreamSelection.pickVideo(videos, maxHeight, container)
                    ?: throw NewPipeDownloadException("no usable video stream")
            val separateAudio =
                if (StreamSelection.isCombined(video)) {
                    null
                } else {
                    StreamSelection.pickAudio(audios, container)
                        ?: throw NewPipeDownloadException("no usable audio stream")
                }

            val ext = StreamSelection.outputExt(container)
            val output = File(spec.workDir, outputFileName(spec, title, uploader, ext))

            val videoFile = File(workDir, "video.${video.container ?: "mp4"}")
            downloadTracked(video.url, videoFile, taskId, onProgress, VIDEO_PROGRESS_SCALE, progressOffset = 0f)

            return if (separateAudio != null) {
                val audioFile = File(workDir, "audio.${separateAudio.container ?: "m4a"}")
                downloadTracked(
                    separateAudio.url,
                    audioFile,
                    taskId,
                    onProgress,
                    AUDIO_PROGRESS_SCALE,
                    progressOffset = VIDEO_PROGRESS_SCALE,
                )
                val merged = File(workDir, "merged.$ext")
                onProgress(MERGE_PROGRESS_PERCENT, 0L, "merging")
                ffmpeg
                    .mergeCopy(videoFile, audioFile, merged, taskId)
                    .exceptionOrNull()
                    ?.let { e -> throw NewPipeDownloadException("ffmpeg merge failed: ${e.message}", e) }
                moveIntoPlace(merged, output)
                onProgress(FULL_PROGRESS_PERCENT, 0L, "done")
                output
            } else {
                moveIntoPlace(videoFile, output)
                onProgress(FULL_PROGRESS_PERCENT, 0L, "done")
                output
            }
        }

        private suspend fun downloadAudio(
            extractor: StreamExtractor,
            spec: DownloadSpec,
            workDir: File,
            title: String,
            uploader: String?,
            taskId: String,
            onProgress: (Float, Long, String) -> Unit,
        ): File {
            val audios = safeList { extractor.audioStreams }.map(NewPipeMapper::toAudioStreamInfo)
            val audio =
                StreamSelection.pickAudio(audios, null)
                    ?: StreamSelection.pickAudio(
                        safeList { extractor.videoStreams }.map { v ->
                            AudioStreamInfo(v.url.orEmpty(), null, null, 0, v.id.orEmpty())
                        },
                        null,
                    )
                    ?: throw NewPipeDownloadException("no usable audio stream")

            val target = ConvertFormat.fromExt(spec.options.audioFormat) ?: ConvertFormat.MP3
            val sourceFile = File(workDir, "audio-source.${audio.container ?: "m4a"}")
            downloadTracked(audio.url, sourceFile, taskId, onProgress, AUDIO_DOWNLOAD_SCALE, progressOffset = 0f)

            val output = File(spec.workDir, outputFileName(spec, title, uploader, target.ext))

            // Cover art: convert thumbnail to jpg first (like yt-dlp --convert-thumbnails jpg).
            val coverJpg = if (spec.options.embedThumbnail) buildCoverJpg(extractor, workDir, taskId) else null

            onProgress(CONVERT_PROGRESS_PERCENT, 0L, "converting")
            ffmpeg
                .convertAudioEmbed(
                    AudioEmbedArgs(
                        input = sourceFile,
                        output = output,
                        target = target,
                        bitrate = spec.options.audioBitrate,
                        coverJpg = coverJpg,
                        metadataTitle = if (spec.options.embedMetadata) title else null,
                        metadataArtist = if (spec.options.embedMetadata) uploader else null,
                        key = taskId,
                    ),
                ).exceptionOrNull()
                ?.let { e -> throw NewPipeDownloadException("ffmpeg audio failed: ${e.message}", e) }

            onProgress(FULL_PROGRESS_PERCENT, 0L, "done")
            return output
        }

        private suspend fun buildCoverJpg(
            extractor: StreamExtractor,
            workDir: File,
            taskId: String,
        ): File? {
            val thumbnailUrl = NewPipeMapper.bestThumbnail(safeList { extractor.thumbnails }) ?: return null
            return suspendRunCatching {
                val rawThumb = File(workDir, "thumb-source")
                downloadFile(thumbnailUrl, rawThumb)
                val cover = File(workDir, "cover.jpg")
                if (ffmpeg.convertThumbnailToJpg(rawThumb, cover, taskId).isSuccess) cover else null
            }.onFailure { e -> AppLog.e(TAG, "cover art failed", e) }.getOrNull()
        }

        private suspend fun downloadTracked(
            url: String,
            target: File,
            taskId: String,
            onProgress: (Float, Long, String) -> Unit,
            progressScale: Float,
            progressOffset: Float,
        ) {
            val coroutine = currentCoroutineContext()
            try {
                chunkedDownloader.download(
                    url = url,
                    target = target,
                    onProgress = { p, eta, line ->
                        onProgress(
                            (progressOffset + p * progressScale / FULL_PROGRESS_PERCENT).coerceIn(
                                0f,
                                FULL_PROGRESS_PERCENT,
                            ),
                            eta,
                            line,
                        )
                    },
                    isCancelled = { isDownloadCancelled(taskId) || !coroutine.isActive },
                )
            } catch (e: ChunkedDownloadException) {
                if (isDownloadCancelled(taskId) || e.message == CANCELLED_MESSAGE) throw DownloadCancelledException(e)
                throw e
            }
            if (isDownloadCancelled(taskId)) throw DownloadCancelledException()
        }

        private fun downloadFile(
            url: String,
            target: File,
        ) {
            client
                .newCall(
                    okhttp3.Request
                        .Builder()
                        .url(url)
                        .build(),
                ).execute()
                .use { response ->
                    if (!response.isSuccessful) throw NewPipeDownloadException("HTTP ${response.code}")
                    response.body?.byteStream()?.use { input -> target.outputStream().use { out -> input.copyTo(out) } }
                        ?: throw NewPipeDownloadException("empty body")
                }
        }

        private fun moveIntoPlace(
            source: File,
            output: File,
        ) {
            if (output.exists()) output.delete()
            if (!source.renameTo(output)) {
                source.copyTo(output, overwrite = true)
                source.delete()
            }
        }

        private fun outputFileName(
            spec: DownloadSpec,
            title: String,
            uploader: String?,
            ext: String,
        ): String {
            val template =
                FilenameTemplate.outputTemplate(
                    template = spec.template,
                    playlistIndex = spec.playlistIndex,
                    fileName = spec.fileName,
                )
            val values =
                mapOf(
                    "title" to title.ifBlank { titleFromUrl(spec.url) },
                    "uploader" to uploader.orEmpty(),
                    "id" to "",
                    "ext" to ext,
                )
            return FilenameTemplate.expand(template, values, ext).ifBlank { FileName.FALLBACK_NAME }
        }

        private fun titleFromUrl(url: String): String =
            url.substringAfterLast('=').substringAfterLast('/').take(MAX_TITLE_FALLBACK)

        // ---- helpers ----

        private fun service(): StreamingService = ServiceList.YouTube

        private suspend fun fetchPage(extractor: Extractor) {
            runInterruptible(Dispatchers.IO) { extractor.fetchPage() }
        }

        private fun isPlaylist(
            service: StreamingService,
            url: String,
        ): Boolean =
            try {
                service.getLinkTypeByUrl(url) == StreamingService.LinkType.PLAYLIST
            } catch (e: Exception) {
                AppLog.d(TAG) { "could not determine link type: ${e.message}" }
                false
            }

        private fun stripToSingleVideo(url: String): String {
            val base = url.substringBefore('#')
            val query = base.substringAfter('?', "")
            val path = base.substringBefore('?')
            // youtu.be short links carry the id in the path.
            if (query.isBlank() && Links.host(url) == "youtu.be") return path
            val kept =
                query.split('&').filter { param ->
                    val name = param.substringBefore('=').lowercase()
                    name == "v" || (name.isNotEmpty() && name !in RADIO_PARAMS)
                }
            return if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
        }

        private fun throwIfFetchCancelled(processId: String) {
            if (fetchCancelled[processId]?.get() == true) throw CancellationException("fetch cancelled")
        }

        private fun throwIfDownloadCancelled(taskId: String) {
            if (isDownloadCancelled(taskId)) throw DownloadCancelledException()
        }

        private fun isDownloadCancelled(taskId: String): Boolean = downloadCancelled[taskId]?.get() == true

        @Suppress("SwallowedException")
        private inline fun safeString(block: () -> String?): String? =
            try {
                block()
            } catch (e: RuntimeException) {
                null
            }

        @Suppress("SwallowedException")
        private inline fun safeLong(block: () -> Long): Long =
            try {
                block()
            } catch (e: RuntimeException) {
                0L
            }

        @Suppress("SwallowedException")
        private inline fun <T> safeList(block: () -> List<T>?): List<T> =
            try {
                block().orEmpty()
            } catch (e: RuntimeException) {
                emptyList()
            }
    }
