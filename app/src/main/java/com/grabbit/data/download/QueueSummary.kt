package com.grabbit.data.download

data class QueueSummary(
    val count: Int = 0,
    val progress: Float = 0f,
    val indeterminate: Boolean = true,
) {
    companion object {
        fun from(tasks: List<DownloadTask>): QueueSummary {
            val inQueue =
                tasks.filter {
                    it.status.isActive || it.status == TaskStatus.PAUSED || it.status == TaskStatus.FAILED
                }
            if (inQueue.isEmpty()) return QueueSummary()

            val active = inQueue.filter { it.status.isActive }
            if (active.none { it.progress > 0f }) return QueueSummary(count = inQueue.size)

            val progress = active.map { it.progress.coerceIn(0f, 1f) }.average().toFloat()
            return QueueSummary(count = inQueue.size, progress = progress, indeterminate = false)
        }
    }
}
