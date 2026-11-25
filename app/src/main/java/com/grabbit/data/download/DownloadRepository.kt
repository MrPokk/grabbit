package com.grabbit.data.download

import android.content.Context
import com.grabbit.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing facade: mutates the queue and keeps the foreground service alive. */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: DownloadEngine,
) {
    val tasks: StateFlow<List<DownloadTask>> = engine.tasks

    fun enqueue(requests: List<DownloadRequest>): List<String> {
        val ids = engine.enqueue(requests)
        if (ids.isNotEmpty()) DownloadService.start(context)
        return ids
    }

    fun pause(id: String) = engine.pause(id)

    fun resume(id: String) {
        engine.resume(id)
        DownloadService.start(context)
    }

    fun cancel(id: String) = engine.cancel(id)

    fun remove(id: String) = engine.remove(id)

    fun clearFinished() = engine.clearFinished()

    fun retry(id: String) = resume(id)
}
