package com.grabbit.data.extract

/** User-facing "Extraction engine" setting. */
enum class ExtractionEnginePreference(
    val key: String,
) {
    /** Hybrid: YouTube -> NewPipeEngine, everything else -> yt-dlp, with automatic yt-dlp fallback. */
    AUTO("auto"),

    /** Always yt-dlp. Default until measurements justify hybrid. */
    YTDLP_ONLY("yt-dlp-only"),
    ;

    companion object {
        val DEFAULT = AUTO

        fun fromKey(key: String?): ExtractionEnginePreference = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
