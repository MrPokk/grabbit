package com.grabbit.data.download

import android.content.Context
import com.grabbit.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing facade: mutates the queue and keeps the foreground service alive. */
@Singleton
class DownloadRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val engine: DownloadEngine,
    ) {
        /** Current queue contents. */
        val tasks: StateFlow<List<DownloadTask>> = engine.tasks

        val queueSummary: Flow<QueueSummary> = tasks.map(QueueSummary::from)

        /** Adds requests to the queue and starts the foreground service for them. */
        fun enqueue(requests: List<DownloadRequest>): List<String> {
            val ids = engine.enqueue(requests)
            if (ids.isNotEmpty()) DownloadService.start(context)
            return ids
        }

        /** Re-queues a task and restarts the foreground service. */
        fun resume(id: String) {
            engine.resume(id)
            DownloadService.start(context)
        }
    }
