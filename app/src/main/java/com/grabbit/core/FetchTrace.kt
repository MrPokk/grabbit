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
    ENGINE("engine"),
    STATE_UPDATE("state"),
    PREVIEW_SHOWN("preview"),
}

class FetchTrace(
    private val label: String,
) {
    private val startedAtNanos = System.nanoTime()
    private val stages = LinkedHashMap<FetchStage, Long>()

    @Volatile
    private var _engine: String? = null

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

    /** Record which extraction engine produced the result (for debug details). */
    fun setEngine(name: String) {
        _engine = name
        AppLog.d(TAG) { "$label engine=$name" }
    }

    /** Last recorded engine name, if any. */
    val engine: String?
        get() = _engine

    fun totalMs(): Long = nanosToMillis(System.nanoTime() - startedAtNanos)

    fun logSummary() {
        AppLog.d(TAG) {
            val detail = stages.entries.joinToString(" ") { (stage, ms) -> "${stage.logName}=${ms}ms" }
            val enginePart = engine?.let { " engine=$it" } ?: ""
            "$label total=${totalMs()}ms$enginePart $detail"
        }
    }

    private fun nanosToMillis(nanos: Long): Long = (nanos / NANOS_PER_MILLI).toLong()

    companion object {
        fun startedAt(): Long = System.nanoTime()

        fun sinceMs(startedAtNanos: Long): Long = ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI).toLong()
    }
}
