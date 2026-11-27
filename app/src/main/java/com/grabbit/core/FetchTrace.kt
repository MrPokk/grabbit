package com.grabbit.core

private const val TAG = "FetchTrace"
private const val NANOS_PER_MILLI = 1_000_000.0

/** Stages of the link → preview flow, in the order they are usually reported. */
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

/**
 * Records how long each stage of a fetch takes and logs one summary line.
 * Stages are measured by the caller so a stage never absorbs the debounce wait
 * or another stage's work.
 */
class FetchTrace(
    private val label: String,
) {
    private val startedAtNanos = System.nanoTime()
    private val stages = LinkedHashMap<FetchStage, Long>()

    /** Records [durationMs] for [stage] and logs it immediately. */
    fun record(
        stage: FetchStage,
        durationMs: Long,
    ) {
        stages[stage] = durationMs
        AppLog.d(TAG) { "$label ${stage.logName}=${durationMs}ms (total ${totalMs()}ms)" }
    }

    /** Measures [block] and records its duration for [stage]. */
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

    /** Milliseconds since the link was entered, including debounce and every stage. */
    fun totalMs(): Long = nanosToMillis(System.nanoTime() - startedAtNanos)

    /** One line with every recorded stage; call once when the preview is on screen. */
    fun logSummary() {
        AppLog.d(TAG) {
            val detail = stages.entries.joinToString(" ") { (stage, ms) -> "${stage.logName}=${ms}ms" }
            "$label total=${totalMs()}ms $detail"
        }
    }

    private fun nanosToMillis(nanos: Long): Long = (nanos / NANOS_PER_MILLI).toLong()

    companion object {
        /** Nanosecond timestamp for stages measured outside the trace object. */
        fun startedAt(): Long = System.nanoTime()

        /** Milliseconds elapsed since a [startedAt] timestamp. */
        fun sinceMs(startedAtNanos: Long): Long = ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI).toLong()
    }
}
