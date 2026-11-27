package com.grabbit.data.download

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.SystemClock
import androidx.core.net.toUri
import com.grabbit.core.AppError
import com.grabbit.core.AppLog
import com.grabbit.core.Defaults
import com.grabbit.core.ErrorMapper
import com.grabbit.core.ErrorSignals
import com.grabbit.core.Formatting
import com.grabbit.core.Limits
import com.grabbit.core.MimeTypes
import com.grabbit.core.Timing
import com.grabbit.data.convert.FfmpegRunner
import com.grabbit.data.db.HistoryDao
import com.grabbit.data.db.HistoryEntity
import com.grabbit.data.media.MediaSaver
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.ytdlp.DownloadCancelledException
import com.grabbit.data.ytdlp.DownloadException
import com.grabbit.data.ytdlp.DownloadSpec
import com.grabbit.data.ytdlp.FetchedMedia
import com.grabbit.data.ytdlp.InfoCacheStore
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadEngine"

/**
 * The download queue. It lives in the application scope (not in the Activity or
 * the Service), so downloads keep running when the UI goes away, and the
 * foreground service only exists to keep the process alive and show progress.
 */
@Singleton
@Suppress("LongParameterList") // Constructor injection; a parameter holder would only hide the wiring.
class DownloadEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ytDlp: YtDlpEngine,
        private val settings: SettingsRepository,
        private val saver: MediaSaver,
        private val ffmpeg: FfmpegRunner,
        private val historyDao: HistoryDao,
        private val infoCache: InfoCacheStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val json = Json { ignoreUnknownKeys = true }

        private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())

        /** Current queue contents. */
        val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

        private val jobs = ConcurrentHashMap<String, Job>()
        private val lastProgressAt = ConcurrentHashMap<String, Long>()
        private val pumpSignal = Channel<Unit>(Channel.CONFLATED)

        @Volatile private var concurrentLimit = Defaults.CONCURRENT_DOWNLOADS

        init {
            // Cache the limit so pump() never has to suspend on DataStore.
            scope.launch { settings.settings.collect { concurrentLimit = it.concurrentDownloads } }
            scope.launch {
                pumpSignal.consumeAsFlow().collect { safePump() }
            }
            safePump()
        }

        /** Adds requests to the queue and returns their ids. */
        fun enqueue(requests: List<DownloadRequest>): List<String> {
            AppLog.d(TAG) { "enqueue size=${requests.size}" }
            if (requests.isEmpty()) return emptyList()
            val created =
                requests.map { request ->
                    DownloadTask(id = UUID.randomUUID().toString(), request = request)
                }
            _tasks.update { it + created }
            pumpSignal.trySend(Unit)
            safePump()
            return created.map { it.id }
        }

        /** Pauses a task and kills its native process. */
        fun pause(id: String) {
            val task = find(id) ?: return
            if (!task.status.isActive) return
            update(id) { it.copy(status = TaskStatus.PAUSED, speed = null, etaSeconds = null) }
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (e: SecurityException) {
                AppLog.e(TAG, "could not destroy the download process", e)
            }
            ffmpeg.cancel(id)
        }

        /** Re-queues a paused or failed task. */
        fun resume(id: String) {
            val task = find(id) ?: return
            if (task.status.isActive || task.status == TaskStatus.COMPLETED) return
            update(
                id,
            ) { it.copy(status = TaskStatus.QUEUED, progress = 0f, error = null, speed = null, etaSeconds = null) }
            pumpSignal.trySend(Unit)
        }

        /** Cancels a task and kills its native process. */
        fun cancel(id: String) {
            val task = find(id) ?: return
            if (task.status.isFinished) {
                remove(id)
                return
            }
            update(id) { it.copy(status = TaskStatus.CANCELLED, speed = null, etaSeconds = null) }
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (e: SecurityException) {
                AppLog.e(TAG, "could not destroy the download process", e)
            }
            ffmpeg.cancel(id)
        }

        /** Removes a finished task from the displayed queue. */
        fun remove(id: String) {
            val task = find(id) ?: return
            if (!task.status.isFinished) return
            _tasks.update { list -> list.filterNot { it.id == id } }
        }

        /** Removes every finished task from the displayed queue. */
        fun clearFinished() {
            _tasks.update { list -> list.filterNot { it.status.isFinished } }
        }

        /** A single failed pump must not kill the queue consumer forever. */
        private fun safePump() {
            AppLog.d(TAG) { "safePump enter" }
            try {
                pump()
            } catch (e: IllegalStateException) {
                AppLog.e(TAG, "download queue pump failed", e)
            }
        }

        private fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

        private fun update(
            id: String,
            block: (DownloadTask) -> DownloadTask,
        ) {
            _tasks.update { list ->
                list.map { if (it.id == id) block(it) else it }
            }
        }

        @Synchronized
        private fun pump() {
            val limit =
                concurrentLimit.coerceIn(
                    Limits.MIN_CONCURRENT_DOWNLOADS,
                    Limits.MAX_CONCURRENT_DOWNLOADS,
                )
            val snapshot = _tasks.value
            // QUEUED counts as "active" for the UI, but it must not consume a
            // worker slot — otherwise the queue can never start.
            val active = snapshot.count { it.status != TaskStatus.QUEUED && it.status.isActive }
            var freeSlots = limit - active
            if (freeSlots <= 0) return
            val queued = snapshot.filter { it.status == TaskStatus.QUEUED }
            AppLog.d(TAG) { "pump: limit=$limit active=$active queued=${queued.size}" }
            for (task in queued) {
                if (freeSlots <= 0) break
                if (find(task.id)?.status != TaskStatus.QUEUED) continue
                start(task.id)
                freeSlots--
            }
        }

        private fun start(id: String) {
            update(id) { it.copy(status = TaskStatus.PREPARING, progress = 0f, error = null) }
            val job =
                scope.launch {
                    try {
                        runTask(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: DownloadException) {
                        handleTaskFailure(id, e, e.error)
                    } catch (e: InfoException) {
                        handleTaskFailure(id, e, e.error)
                    } catch (e: DownloadCancelledException) {
                        handleTaskFailure(id, e)
                    } catch (e: IOException) {
                        handleTaskFailure(id, e)
                    } catch (e: SecurityException) {
                        handleTaskFailure(id, e)
                    } catch (e: SQLiteException) {
                        handleTaskFailure(id, e)
                    } catch (e: SerializationException) {
                        handleTaskFailure(id, e)
                    } catch (e: IllegalStateException) {
                        handleTaskFailure(id, e)
                    } finally {
                        jobs.remove(id)
                        pumpSignal.trySend(Unit)
                    }
                }
            jobs[id] = job
        }

        private suspend fun runTask(id: String) {
            val task = find(id) ?: return
            val options = task.options
            val workDir = File(context.cacheDir, "downloads/$id").apply { mkdirs() }
            val marker = File(workDir, "output.path")
            marker.delete()

            try {
                val appSettings = settings.current()
                val infoJsonPath =
                    task.request.infoJsonPath?.takeIf {
                        infoCache.isFresh(it, task.request.infoFetchedAtMs)
                    }
                val spec =
                    DownloadSpec(
                        taskId = id,
                        url = task.request.url,
                        playlistIndex = task.request.playlistIndex,
                        options = options,
                        template = appSettings.filenameTemplate,
                        workDir = workDir,
                        cookiesPath = appSettings.cookiesPath?.takeIf { File(it).exists() },
                        proxy = appSettings.proxy.takeIf { it.isNotBlank() },
                        markerFile = marker,
                        saveFolderUri =
                            options.saveFolderUri.takeIf { it?.isNotBlank() == true }
                                ?: appSettings.customFolderUri,
                        fileName = options.fileName,
                        infoJsonPath = infoJsonPath,
                    )

                update(id) { it.copy(status = TaskStatus.DOWNLOADING) }
                val output = downloadWithInfoRetry(id, spec)

                update(id) { it.copy(status = TaskStatus.SAVING, progress = 1f, speed = null, etaSeconds = null) }
                val mimeType = MimeTypes.forExt(output.extension)
                val displayName =
                    options.fileName
                        .takeIf { it?.isNotBlank() == true }
                        ?.let { "$it.${output.extension}" }
                        ?: output.name
                val saved =
                    saver.save(
                        source = output,
                        mediaType = options.mediaType,
                        mimeType = mimeType,
                        displayName = displayName,
                        customFolder = spec.saveFolderUri?.toUri(),
                    )
                output.delete()

                historyDao.insert(
                    HistoryEntity(
                        url = task.request.url,
                        title = task.title,
                        uploader = task.uploader,
                        thumbnail = task.thumbnail,
                        uri = saved.uri.toString(),
                        fileName = saved.displayName,
                        mimeType = saved.mimeType,
                        mediaType = options.mediaType.name,
                        formatLabel = options.formatLabel,
                        sizeBytes = saved.sizeBytes,
                        durationSeconds = task.request.durationSeconds,
                        createdAt = System.currentTimeMillis(),
                        sourceSite = task.request.site,
                        optionsJson = json.encodeToString(options),
                    ),
                )

                update(id) {
                    it.copy(
                        status = TaskStatus.COMPLETED,
                        progress = 1f,
                        speed = null,
                        etaSeconds = null,
                        savedUri = saved.uri.toString(),
                        fileName = saved.displayName,
                        sizeBytes = saved.sizeBytes,
                    )
                }
            } finally {
                val status = find(id)?.status
                if (status != TaskStatus.PAUSED) {
                    workDir.deleteRecursively()
                }
                marker.delete()
            }
        }

        /**
         * Runs the download, reusing the metadata JSON. If the cached format URLs
         * expired, re-extracts once and retries; other failures are reported as-is.
         */
        private suspend fun downloadWithInfoRetry(
            id: String,
            spec: DownloadSpec,
        ): File {
            try {
                return ytDlp.download(spec) { progress, eta, line ->
                    onProgress(id, progress, eta, line)
                }
            } catch (e: DownloadException) {
                val staleInfo = spec.infoJsonPath
                if (staleInfo == null || !ErrorSignals.isExpiredFormatUrl(e.error.technical)) throw e
                AppLog.d(TAG) { "cached info expired, re-fetching once: ${spec.url}" }
                infoCache.discard(staleInfo)
                val refreshed = ytDlp.fetch(spec.url, loadMixAsPlaylist = false, bypassCache = true)
                val single = refreshed as? FetchedMedia.Single
                val freshInfo =
                    single?.infoJsonPath?.takeIf {
                        infoCache.isFresh(it, single.infoFetchedAtMs)
                    }
                return ytDlp.download(spec.copy(infoJsonPath = freshInfo)) { progress, eta, line ->
                    onProgress(id, progress, eta, line)
                }
            }
        }

        private fun handleTaskFailure(
            id: String,
            e: Exception,
            error: AppError = ErrorMapper.fromThrowable(e),
        ) {
            AppLog.e(TAG, "download task $id failed", e)
            val current = find(id) ?: return
            if (current.status == TaskStatus.PAUSED || current.status == TaskStatus.CANCELLED) return
            fail(id, error)
        }

        private fun onProgress(
            id: String,
            progress: Float,
            etaSeconds: Long,
            line: String,
        ) {
            val now = SystemClock.elapsedRealtime()
            val previous = lastProgressAt[id] ?: 0L
            val fraction =
                if (progress >= 0f) {
                    (progress / Formatting.PERCENT_SCALE_F).coerceIn(0f, 1f)
                } else {
                    null
                }
            if (fraction != null &&
                now - previous < Timing.PROGRESS_THROTTLE.inWholeMilliseconds &&
                fraction < 1f
            ) {
                return
            }
            lastProgressAt[id] = now

            val speed = speedRegex.find(line)?.value
            update(id) { task ->
                task.copy(
                    status = TaskStatus.DOWNLOADING,
                    progress = fraction ?: task.progress,
                    speed = speed ?: task.speed,
                    etaSeconds = etaSeconds.takeIf { it > 0 } ?: task.etaSeconds,
                )
            }
        }

        private fun fail(
            id: String,
            error: AppError,
        ) {
            update(id) {
                it.copy(
                    status = TaskStatus.FAILED,
                    error = error,
                    speed = null,
                    etaSeconds = null,
                )
            }
        }

        private companion object {
            val speedRegex = Regex("""\d+(?:\.\d+)?\s*[KMGT]?i?B/s""")
        }
    }
