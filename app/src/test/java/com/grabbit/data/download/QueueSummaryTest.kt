package com.grabbit.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSummaryTest {
    @Test
    fun `count excludes completed and cancelled tasks`() {
        val tasks =
            listOf(
                task("active", TaskStatus.DOWNLOADING, 0.5f),
                task("paused", TaskStatus.PAUSED, 0.25f),
                task("failed", TaskStatus.FAILED, 0.1f),
                task("completed", TaskStatus.COMPLETED, 1f),
                task("cancelled", TaskStatus.CANCELLED),
            )

        assertEquals(3, QueueSummary.from(tasks).count)
    }

    @Test
    fun `indeterminate until an active task reports progress`() {
        val preparing = QueueSummary.from(listOf(task("a", TaskStatus.PREPARING), task("b", TaskStatus.QUEUED)))
        assertEquals(2, preparing.count)
        assertEquals(true, preparing.indeterminate)

        val pausedOnly = QueueSummary.from(listOf(task("a", TaskStatus.PAUSED, 0.5f)))
        assertEquals(true, pausedOnly.indeterminate)
    }

    @Test
    fun `progress averages active tasks only`() {
        val summary =
            QueueSummary.from(
                listOf(
                    task("a", TaskStatus.DOWNLOADING, 0.5f),
                    task("b", TaskStatus.CONVERTING, 1f),
                    task("paused", TaskStatus.PAUSED, 1f),
                ),
            )

        assertEquals(3, summary.count)
        assertEquals(false, summary.indeterminate)
        assertEquals(0.75f, summary.progress, 0.0001f)
    }

    private fun task(
        id: String,
        status: TaskStatus,
        progress: Float = 0f,
    ): DownloadTask =
        DownloadTask(
            id = id,
            request =
                DownloadRequest(
                    url = "https://example.com/$id",
                    title = id,
                    uploader = null,
                    thumbnail = null,
                    durationSeconds = 0,
                    site = null,
                    options = DownloadOptions(),
                ),
            status = status,
            progress = progress,
        )
}
