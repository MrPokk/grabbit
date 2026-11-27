package com.grabbit.core

private const val TAG = "FetchTrace"
private const val NANOS_PER_MILLI = 1_000_000.0

enum class FetchStage(
    val logName: String,
) {
    URL_NORMALIZE("normalize"),
    CACHE_LOOKUP("cache"),
    INIT("init"),
    PROCESS_START("process"),
    YTDLP_RUN("yt-dlp"),
    JSON_PARSE("parse"),
    STATE_UPDATE("state"),
    PREVIEW_SHOWN("preview"),
}

class FetchTrace(
    private val label: String,
) {
    private val startedAtNanos = System.nanoTime()
    private val stages = LinkedHashMap<FetchStage, Long>()

    fun record(
        stage: FetchStage,
        durationMs: Long,
    ) {
        stages[stage] = durationMs
        AppLog.d(TAG) { "$label ${stage.logName}=${durationMs}ms (total ${totalMs()}ms)" }
    }

    fun <T> measure(
        stage: FetchStage,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        try {
            return block()
        } finally {
            record(stage, nanosToMillis(System.nanoTime() - startedAt))
        }
    }

    fun totalMs(): Long = nanosToMillis(System.nanoTime() - startedAtNanos)

    fun logSummary() {
        AppLog.d(TAG) {
            val detail = stages.entries.joinToString(" ") { (stage, ms) -> "${stage.logName}=${ms}ms" }
            "$label total=${totalMs()}ms $detail"
        }
    }

    private fun nanosToMillis(nanos: Long): Long = (nanos / NANOS_PER_MILLI).toLong()

    companion object {
        fun startedAt(): Long = System.nanoTime()

        fun sinceMs(startedAtNanos: Long): Long = ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI).toLong()
    }
}
