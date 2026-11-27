package com.grabbit

import android.app.Application
import com.grabbit.core.AppLog
import com.grabbit.core.FetchTrace
import com.grabbit.core.Timing
import com.grabbit.core.suspendRunCatching
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.ytdlp.YtDlpEngine
import com.grabbit.service.DownloadService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GrabbitApp"
private const val CRASH_TAG = "GrabbitCrash"

/** Application entry point: installs crash logging and warms up yt-dlp off the main thread. */
@HiltAndroidApp
class GrabbitApp : Application() {
    @Inject
    lateinit var ytDlp: YtDlpEngine

    @Inject
    lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        DownloadService.createChannels(this)

        // Unpack the bundled python / yt-dlp / ffmpeg binaries off the main thread
        // so the first download does not have to wait for it.
        appScope.launch {
            val startedAt = FetchTrace.startedAt()
            suspendRunCatching { ytDlp.ensureInitialized() }
                .onFailure { e -> AppLog.e(TAG, "yt-dlp initialization failed", e) }
            AppLog.d(TAG) { "yt-dlp/ffmpeg warm-up finished in ${FetchTrace.sinceMs(startedAt)}ms" }
        }
        appScope.launch { autoUpdateIfDue() }
    }

    /** Keeps crashes visible in logcat (`adb logcat -s GrabbitCrash`). */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler() ?: return
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLog.e(CRASH_TAG, "Uncaught exception on ${thread.name}", throwable)
            previous.uncaughtException(thread, throwable)
        }
    }

    private suspend fun autoUpdateIfDue() {
        val current = settings.current()
        if (!current.autoCheckUpdates) return
        val sinceLastCheck = System.currentTimeMillis() - current.lastUpdateCheck
        if (sinceLastCheck < Timing.UPDATE_CHECK_INTERVAL.inWholeMilliseconds) return
        suspendRunCatching { ytDlp.update() }
            .onFailure { e -> AppLog.e(TAG, "yt-dlp auto-update failed", e) }
    }
}
