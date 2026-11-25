package com.grabbit.data.ytdlp

import android.content.Context
import android.util.Log
import com.grabbit.BuildConfig
import com.grabbit.R
import com.grabbit.core.AppError
import com.grabbit.core.ErrorMapper
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
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

class InfoException(val error: AppError) : Exception(error.technical ?: "info failed")

class DownloadException(val error: AppError) : Exception(error.technical ?: "download failed")

class DownloadCancelledException : Exception("cancelled")

sealed interface UpdateOutcome {
    data object Updated : UpdateOutcome
    data object UpToDate : UpdateOutcome
    data class Failed(val message: String) : UpdateOutcome
}

/**
 * Thin coroutine-friendly wrapper around youtubedl-android. All yt-dlp calls are
 * blocking, so they run on [Dispatchers.IO] and never on the main thread.
 */
@Singleton
class YtDlpEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    companion object {
        private const val TAG = "YtDlpEngine"
        const val FETCH_TIMEOUT_MS = 60_000L
    }

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
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "yt-dlp version: ${runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()}",
                    )
                }
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
        ensureInitialized()
        return withContext(Dispatchers.IO) {
            val request = RequestFactory.buildFetchRequest(url, loadMixAsPlaylist)
            if (BuildConfig.DEBUG) Log.d(TAG, "fetch command: ${request.buildCommand()}")
            val timedOut = AtomicBoolean(false)
            val watchdog = launch {
                delay(FETCH_TIMEOUT_MS)
                if (YoutubeDL.getInstance().destroyProcessById(processId)) {
                    timedOut.set(true)
                }
            }
            try {
                val response = YoutubeDL.getInstance().execute(request, processId)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "fetch exit=${response.exitCode} stdout=${response.out.length} stderr=${response.err}",
                    )
                }
                try {
                    InfoParser.parse(response.out, url)
                } catch (e: Throwable) {
                    Log.e(TAG, "metadata parse failed: ${e.message}", e)
                    throw InfoException(
                        AppError.unknown("Could not read metadata: ${e.message}"),
                    )
                }
            } catch (e: CancellationException) {
                YoutubeDL.getInstance().destroyProcessById(processId)
                throw e
            } catch (e: CanceledException) {
                if (timedOut.get()) {
                    throw InfoException(AppError(R.string.error_fetch_timeout, e.message))
                }
                throw CancellationException("fetch cancelled")
            } catch (e: InfoException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "yt-dlp fetch failed: ${e.message}", e)
                if (timedOut.get()) {
                    throw InfoException(AppError(R.string.error_fetch_timeout, e.message))
                }
                throw InfoException(ErrorMapper.fromYtDlp(e.message, url))
            } finally {
                watchdog.cancel()
            }
        }
    }

    /** Kills a running metadata extraction when the user cancels or refetches. */
    fun cancelFetch(processId: String) {
        YoutubeDL.getInstance().destroyProcessById(processId)
    }

    suspend fun download(spec: DownloadSpec, onProgress: (Float, Long, String) -> Unit): File {
        ensureInitialized()
        return withContext(Dispatchers.IO) {
            val request = RequestFactory.build(spec)
            if (BuildConfig.DEBUG) Log.d(TAG, "download command: ${request.buildCommand()}")
            try {
                val response = YoutubeDL.getInstance().execute(request, spec.taskId) { progress, eta, line ->
                    onProgress(progress, eta, line)
                }
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "download exit=${response.exitCode} stderr=${response.err}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: CanceledException) {
                throw DownloadCancelledException()
            } catch (e: Throwable) {
                Log.e(TAG, "yt-dlp download failed: ${e.message}", e)
                throw DownloadException(ErrorMapper.fromYtDlp(e.message, spec.url))
            }

            val reportedPath = spec.markerFile
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

    suspend fun installedVersion(): String? = withContext(Dispatchers.IO) {
        runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
    }

    suspend fun update(): UpdateOutcome {
        ensureInitialized()
        return withContext(Dispatchers.IO) {
            try {
                val status = YoutubeDL.getInstance()
                    .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                settings.setLastUpdateCheck(System.currentTimeMillis())
                if (status == YoutubeDL.UpdateStatus.DONE) UpdateOutcome.Updated else UpdateOutcome.UpToDate
            } catch (e: Exception) {
                UpdateOutcome.Failed(e.message ?: e::class.java.simpleName)
            }
        }
    }
}
