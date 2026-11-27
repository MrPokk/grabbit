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
import com.grabbit.data.db.HistoryEntity
import com.grabbit.data.media.MediaSaver
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.ytdlp.DownloadCancelledException
import com.grabbit.data.ytdlp.DownloadException
import com.grabbit.data.ytdlp.DownloadSpec
import com.grabbit.data.ytdlp.FetchedMedia
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
private val SPEED_REGEX = Regex("""\d+(?:\.\d+)?\s*[KMGT]?i?B/s""")

private fun DownloadTask.idle() = copy(speed = null, etaSeconds = null)

@Singleton
class DownloadEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ytDlp: YtDlpEngine,
        private val settings: SettingsRepository,
        private val saver: MediaSaver,
        private val ffmpeg: FfmpegRunner,
        private val stores: DownloadStores,
    ) {
        private val historyDao get() = stores.historyDao
        private val infoCache get() = stores.infoCache
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val json = Json { ignoreUnknownKeys = true }

        private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
        val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

        private val jobs = ConcurrentHashMap<String, Job>()
        private val lastProgressAt = ConcurrentHashMap<String, Long>()
        private val pumpSignal = Channel<Unit>(Channel.CONFLATED)

        @Volatile private var concurrentLimit = Defaults.CONCURRENT_DOWNLOADS

        init {
            scope.launch { settings.settings.collect { concurrentLimit = it.concurrentDownloads } }
            scope.launch { pumpSignal.consumeAsFlow().collect { safePump() } }
            pumpSignal.trySend(Unit)
        }

        fun enqueue(requests: List<DownloadRequest>): List<String> {
            AppLog.d(TAG) { "enqueue size=${requests.size}" }
            val created = requests.map { DownloadTask(id = UUID.randomUUID().toString(), request = it) }
            if (created.isEmpty()) return emptyList()
            _tasks.update { it + created }
            pumpSignal.trySend(Unit)
            return created.map { it.id }
        }

        fun pause(id: String) {
            if (find(id)?.status?.isActive != true) return
            halt(id, TaskStatus.PAUSED)
        }

        fun resume(id: String) {
            val task = find(id) ?: return
            if (task.status.isActive || task.status == TaskStatus.COMPLETED) return
            update(id) { it.idle().copy(status = TaskStatus.QUEUED, progress = 0f, error = null) }
            pumpSignal.trySend(Unit)
        }

        fun cancel(id: String) {
            val task = find(id) ?: return
            if (task.status.isFinished) remove(id) else halt(id, TaskStatus.CANCELLED)
        }

        fun remove(id: String) {
            if (find(id)?.status?.isFinished != true) return
            _tasks.update { list -> list.filterNot { it.id == id } }
        }

        fun clearFinished() = _tasks.update { list -> list.filterNot { it.status.isFinished } }

        private fun find(id: String) = _tasks.value.firstOrNull { it.id == id }

        private fun update(
            id: String,
            block: (DownloadTask) -> DownloadTask,
        ) = _tasks.update { list -> list.map { if (it.id == id) block(it) else it } }

        private fun halt(
            id: String,
            status: TaskStatus,
        ) {
            update(id) { it.idle().copy(status = status) }
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (e: SecurityException) {
                AppLog.e(TAG, "could not destroy the download process", e)
            }
            ffmpeg.cancel(id)
        }

        private fun safePump() {
            try {
                pump()
            } catch (e: IllegalStateException) {
                AppLog.e(TAG, "download queue pump failed", e)
            }
        }

        @Synchronized
        private fun pump() {
            val limit = concurrentLimit.coerceIn(Limits.MIN_CONCURRENT_DOWNLOADS, Limits.MAX_CONCURRENT_DOWNLOADS)
            val snapshot = _tasks.value
            val active = snapshot.count { it.status != TaskStatus.QUEUED && it.status.isActive }
            val queued = snapshot.filter { it.status == TaskStatus.QUEUED }
            AppLog.d(TAG) { "pump: limit=$limit active=$active queued=${queued.size}" }
            queued
                .filter { find(it.id)?.status == TaskStatus.QUEUED }
                .take(limit - active)
                .forEach { start(it.id) }
        }

        private fun start(id: String) {
            update(id) { it.copy(status = TaskStatus.PREPARING, progress = 0f, error = null) }
            jobs[id] =
                scope.launch {
                    try {
                        runTask(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: DownloadException) {
                        onFailure(id, e, e.error)
                    } catch (e: InfoException) {
                        onFailure(id, e, e.error)
                    } catch (
                        e: Exception,
                    ) {
                        if (e.isExpected()) onFailure(id, e) else throw e
                    } finally {
                        jobs.remove(id)
                        lastProgressAt.remove(id)
                        pumpSignal.trySend(Unit)
                    }
                }
        }

        private fun Exception.isExpected() =
            this is DownloadCancelledException ||
                this is IOException ||
                this is SecurityException ||
                this is SQLiteException ||
                this is SerializationException ||
                this is IllegalStateException

        private fun onFailure(
            id: String,
            e: Exception,
            error: AppError = ErrorMapper.fromThrowable(e),
        ) {
            AppLog.e(TAG, "download task $id failed", e)
            val status = find(id)?.status ?: return
            if (status == TaskStatus.PAUSED || status == TaskStatus.CANCELLED) return
            update(id) { it.idle().copy(status = TaskStatus.FAILED, error = error) }
        }

        private suspend fun runTask(id: String) {
            val task = find(id) ?: return
            val options = task.options
            val workDir = File(context.cacheDir, "downloads/$id").apply { mkdirs() }
            val marker = File(workDir, "output.path").apply { delete() }

            try {
                val spec = buildSpec(task, workDir, marker)

                update(id) { it.copy(status = TaskStatus.DOWNLOADING) }
                val output = downloadWithInfoRetry(id, spec)

                update(id) { it.idle().copy(status = TaskStatus.SAVING, progress = 1f) }
                val saved =
                    saver.save(
                        source = output,
                        mediaType = options.mediaType,
                        mimeType = MimeTypes.forExt(output.extension),
                        displayName =
                            options.fileName
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "$it.${output.extension}" }
                                ?: output.name,
                        customFolder = spec.saveFolderUri?.toUri(),
                    )
                output.delete()

                historyDao.insert(task.toHistory(saved))
                update(id) {
                    it.idle().copy(
                        status = TaskStatus.COMPLETED,
                        progress = 1f,
                        savedUri = saved.uri.toString(),
                        fileName = saved.displayName,
                        sizeBytes = saved.sizeBytes,
                    )
                }
            } finally {
                if (find(id)?.status != TaskStatus.PAUSED) workDir.deleteRecursively()
                marker.delete()
            }
        }

        private suspend fun buildSpec(
            task: DownloadTask,
            workDir: File,
            marker: File,
        ): DownloadSpec {
            val app = settings.current()
            val options = task.options
            return DownloadSpec(
                taskId = task.id,
                url = task.request.url,
                playlistIndex = task.request.playlistIndex,
                options = options,
                template = app.filenameTemplate,
                workDir = workDir,
                cookiesPath = app.cookiesPath?.takeIf { File(it).exists() },
                proxy = app.proxy.takeIf { it.isNotBlank() },
                markerFile = marker,
                saveFolderUri =
                    options.saveFolderUri?.takeIf {
                        it.isNotBlank()
                    } ?: app.customFolderUri,
                fileName = options.fileName,
                infoJsonPath =
                    task.request.infoJsonPath?.takeIf {
                        infoCache.isFresh(it, task.request.infoFetchedAtMs)
                    },
            )
        }

        private fun DownloadTask.toHistory(saved: MediaSaver.SavedFile) =
            HistoryEntity(
                url = request.url,
                title = title,
                uploader = uploader,
                thumbnail = thumbnail,
                uri = saved.uri.toString(),
                fileName = saved.displayName,
                mimeType = saved.mimeType,
                mediaType = options.mediaType.name,
                formatLabel = options.formatLabel,
                sizeBytes = saved.sizeBytes,
                durationSeconds = request.durationSeconds,
                createdAt = System.currentTimeMillis(),
                sourceSite = request.site,
                optionsJson = json.encodeToString(options),
            )

        private suspend fun downloadWithInfoRetry(
            id: String,
            spec: DownloadSpec,
            retried: Boolean = false,
        ): File {
            val progress = { p: Float, eta: Long, line: String -> onProgress(id, p, eta, line) }
            return try {
                ytDlp.download(spec, progress)
            } catch (e: DownloadException) {
                if (retried) throw e
                val refreshed = refreshedSpecAfterFailure(spec, e)
                if (refreshed == null && !ErrorSignals.isTransientDownloadFailure(e.error.technical)) throw e
                AppLog.d(TAG) { "transient download failure, retrying once: ${spec.url}" }
                downloadWithInfoRetry(id, refreshed ?: spec, retried = true)
            }
        }

        private suspend fun refreshedSpecAfterFailure(
            spec: DownloadSpec,
            e: DownloadException,
        ): DownloadSpec? {
            val stale = spec.infoJsonPath ?: return null
            if (!ErrorSignals.isExpiredFormatUrl(e.error.technical)) return null
            AppLog.d(TAG) { "cached info expired, re-fetching once: ${spec.url}" }
            infoCache.discard(stale)
            val fresh =
                (ytDlp.fetch(spec.url, loadMixAsPlaylist = false, bypassCache = true) as? FetchedMedia.Single)
                    ?.takeIf { infoCache.isFresh(it.infoJsonPath, it.infoFetchedAtMs) }
                    ?.infoJsonPath
            return spec.copy(infoJsonPath = fresh)
        }

        private fun onProgress(
            id: String,
            progress: Float,
            etaSeconds: Long,
            line: String,
        ) {
            val now = SystemClock.elapsedRealtime()
            val fraction = progress.takeIf { it >= 0f }?.let { (it / Formatting.PERCENT_SCALE_F).coerceIn(0f, 1f) }
            val throttled =
                fraction != null &&
                    fraction < 1f &&
                    now - (lastProgressAt[id] ?: 0L) < Timing.PROGRESS_THROTTLE.inWholeMilliseconds
            if (throttled) return
            lastProgressAt[id] = now
            val speed = SPEED_REGEX.find(line)?.value
            update(id) {
                it.copy(
                    status = TaskStatus.DOWNLOADING,
                    progress = fraction ?: it.progress,
                    speed = speed ?: it.speed,
                    etaSeconds = etaSeconds.takeIf { eta -> eta > 0 } ?: it.etaSeconds,
                )
            }
        }
    }
