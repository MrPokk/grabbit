package com.grabbit.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.SystemClock
import androidx.core.net.toUri
import com.grabbit.core.AppError
import com.grabbit.core.ErrorMapper
import com.grabbit.core.MimeTypes
import com.grabbit.data.convert.FfmpegRunner
import com.grabbit.data.db.HistoryDao
import com.grabbit.data.db.HistoryEntity
import com.grabbit.data.media.MediaSaver
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.ytdlp.DownloadCancelledException
import com.grabbit.data.ytdlp.DownloadException
import com.grabbit.data.ytdlp.DownloadSpec
import com.grabbit.data.ytdlp.InfoException
import com.grabbit.data.ytdlp.YtDlpEngine
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The download queue. It lives in the application scope (not in the Activity or
 * the Service), so downloads keep running when the UI goes away, and the
 * foreground service only exists to keep the process alive and show progress.
 */
@Singleton
class DownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ytDlp: YtDlpEngine,
    private val settings: SettingsRepository,
    private val saver: MediaSaver,
    private val ffmpeg: FfmpegRunner,
    private val historyDao: HistoryDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val lastProgressAt = ConcurrentHashMap<String, Long>()
    private val pumpSignal = Channel<Unit>(Channel.CONFLATED)
    @Volatile private var concurrentLimit = 2

    init {
        // Cache the limit so pump() never has to suspend on DataStore.
        scope.launch { settings.settings.collect { concurrentLimit = it.concurrentDownloads } }
        scope.launch {
            for (signal in pumpSignal) {
                safePump()
            }
        }
        safePump()
    }

    /** A single failed pump must not kill the queue consumer forever. */
    private fun safePump() {
        Log.d(TAG, "safePump enter")
        try {
            pump()
        } catch (e: Throwable) {
            Log.e(TAG, "download queue pump failed", e)
        }
    }

    fun enqueue(requests: List<DownloadRequest>): List<String> {
        Log.d(TAG, "enqueue size=${requests.size}")
        if (requests.isEmpty()) return emptyList()
        val created = requests.map { request ->
            DownloadTask(id = UUID.randomUUID().toString(), request = request)
        }
        _tasks.update { it + created }
        pumpSignal.trySend(Unit)
        safePump()
        return created.map { it.id }
    }

    fun pause(id: String) {
        val task = find(id) ?: return
        if (!task.status.isActive) return
        update(id) { it.copy(status = TaskStatus.PAUSED, speed = null, etaSeconds = null) }
        // Kill the native process; the coroutine then unwinds with CanceledException.
        runCatching { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(id) }
        ffmpeg.cancel(id)
    }

    fun resume(id: String) {
        val task = find(id) ?: return
        if (task.status.isActive || task.status == TaskStatus.COMPLETED) return
        update(id) { it.copy(status = TaskStatus.QUEUED, progress = 0f, error = null, speed = null, etaSeconds = null) }
        pumpSignal.trySend(Unit)
    }

    fun cancel(id: String) {
        val task = find(id) ?: return
        if (task.status.isFinished) {
            remove(id)
            return
        }
        update(id) { it.copy(status = TaskStatus.CANCELLED, speed = null, etaSeconds = null) }
        runCatching { com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(id) }
        ffmpeg.cancel(id)
    }

    fun remove(id: String) {
        val task = find(id) ?: return
        if (!task.status.isFinished) return
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    fun clearFinished() {
        _tasks.update { list -> list.filterNot { it.status.isFinished } }
    }

    fun retry(id: String) = resume(id)

    private fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    private fun update(id: String, block: (DownloadTask) -> DownloadTask) {
        _tasks.update { list ->
            list.map { if (it.id == id) block(it) else it }
        }
    }

    @Synchronized
    private fun pump() {
        val limit = concurrentLimit.coerceIn(1, 5)
        val snapshot = _tasks.value
        // QUEUED counts as "active" for the UI, but it must not consume a
        // worker slot — otherwise the queue can never start.
        val active = snapshot.count { it.status != TaskStatus.QUEUED && it.status.isActive }
        var freeSlots = limit - active
        if (freeSlots <= 0) return
        val queued = snapshot.filter { it.status == TaskStatus.QUEUED }
        Log.d(TAG, "pump: limit=$limit active=$active queued=${queued.size}")
        for (task in queued) {
            if (freeSlots <= 0) break
            if (find(task.id)?.status != TaskStatus.QUEUED) continue
            start(task.id)
            freeSlots--
        }
    }

    private fun start(id: String) {
        update(id) { it.copy(status = TaskStatus.PREPARING, progress = 0f, error = null) }
        val job = scope.launch {
            try {
                runTask(id)
            } catch (e: CancellationException) {
                // The queue was cancelled; status is already set.
            } catch (e: Throwable) {
                val current = find(id)
                if (current == null || current.status == TaskStatus.PAUSED || current.status == TaskStatus.CANCELLED) {
                    return@launch
                }
                fail(id, (e as? DownloadException)?.error
                    ?: (e as? InfoException)?.error
                    ?: ErrorMapper.fromThrowable(e))
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
            val spec = DownloadSpec(
                taskId = id,
                url = task.request.url,
                playlistIndex = task.request.playlistIndex,
                options = options,
                template = appSettings.filenameTemplate,
                workDir = workDir,
                cookiesPath = appSettings.cookiesPath?.takeIf { File(it).exists() },
                proxy = appSettings.proxy.takeIf { it.isNotBlank() },
                markerFile = marker,
            )

            update(id) { it.copy(status = TaskStatus.DOWNLOADING) }
            var output = ytDlp.download(spec) { progress, eta, line ->
                onProgress(id, progress, eta, line)
            }

            val convertTo = options.convertTo
            if (convertTo != null && !convertTo.ext.equals(output.extension, ignoreCase = true)) {
                update(id) { it.copy(status = TaskStatus.CONVERTING, progress = 0f, speed = null, etaSeconds = null) }
                val converted = File(workDir, "${output.nameWithoutExtension}.${convertTo.ext}")
                val result = ffmpeg.convert(
                    input = output,
                    output = converted,
                    target = convertTo,
                    bitrate = options.audioBitrate,
                    key = id,
                ) { percent ->
                    update(id) { it.copy(progress = percent / 100f) }
                }
                if (result.isSuccess) {
                    output.delete()
                    output = converted
                } else {
                    throw DownloadException(
                        AppError(
                            com.grabbit.R.string.error_ffmpeg,
                            result.exceptionOrNull()?.message,
                        ),
                    )
                }
            }

            update(id) { it.copy(status = TaskStatus.SAVING, progress = 1f, speed = null, etaSeconds = null) }
            val mimeType = MimeTypes.forExt(output.extension)
            val saved = saver.save(
                source = output,
                mediaType = options.mediaType,
                mimeType = mimeType,
                displayName = output.name,
                customFolder = appSettings.customFolderUri?.toUri(),
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

    private fun onProgress(id: String, progress: Float, etaSeconds: Long, line: String) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastProgressAt[id] ?: 0L
        val fraction = if (progress >= 0f) (progress / 100f).coerceIn(0f, 1f) else null
        if (fraction != null && now - previous < 250L && fraction < 1f) return
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

    private fun fail(id: String, error: AppError) {
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
        const val TAG = "DownloadEngine"
        val speedRegex = Regex("""\d+(?:\.\d+)?\s*[KMGT]?i?B/s""")
    }
}
