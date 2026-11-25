package com.grabbit

import android.app.Application
import android.util.Log
import com.grabbit.data.settings.SettingsRepository
import com.grabbit.data.ytdlp.YtDlpEngine
import com.grabbit.service.DownloadService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        appScope.launch { runCatching { ytDlp.ensureInitialized() } }
        appScope.launch { autoUpdateIfDue() }
    }

    /** Keep crashes visible in logcat (`adb logcat -s GrabbitCrash:YtDlpEngine`). */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler() ?: return
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("GrabbitCrash", "Uncaught exception on ${thread.name}", throwable)
            previous.uncaughtException(thread, throwable)
        }
    }

    private suspend fun autoUpdateIfDue() {
        val current = settings.current()
        if (!current.autoCheckUpdates) return
        val dayMillis = 24L * 60L * 60L * 1000L
        if (System.currentTimeMillis() - current.lastUpdateCheck < dayMillis) return
        runCatching { ytDlp.update() }
    }
}
