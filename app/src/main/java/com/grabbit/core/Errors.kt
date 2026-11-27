package com.grabbit.core

import androidx.annotation.StringRes
import com.grabbit.R

private const val MAX_CAUSE_DEPTH = 3

/**
 * A user-facing error. [messageRes] is what the UI shows, [technical] keeps the
 * raw yt-dlp / ffmpeg text for logging and for the expandable details block.
 */
data class AppError(
    @StringRes val messageRes: Int,
    val technical: String? = null,
    val suggestUpdate: Boolean = false,
) {
    companion object {
        /** Fallback error for failures that do not match any known yt-dlp / ffmpeg pattern. */
        fun unknown(technical: String? = null) = AppError(R.string.error_unknown, technical)
    }
}

/** Recognizes failures that mean a cached info JSON's format URLs are no longer valid. */
object ErrorSignals {
    private val expiredNeedles =
        listOf(
            "http error 403",
            "403: forbidden",
            "403 forbidden",
            "forbidden",
            "has expired",
            "url signature",
            "signature expired",
        )

    /** True when yt-dlp output points at an expired/blocked format URL rather than a broken link. */
    fun isExpiredFormatUrl(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val text = raw.lowercase()
        return expiredNeedles.any { text.contains(it) }
    }
}

/** Maps raw yt-dlp / ffmpeg output to user-facing [AppError]s. */
object ErrorMapper {
    private data class Rule(
        val needles: List<String>,
        val error: AppError,
    )

    private val rules =
        listOf(
            Rule(
                listOf(
                    "sign in to confirm your age",
                    "age-restricted",
                    "age restricted",
                    "inappropriate for some users",
                ),
                AppError(R.string.error_age, suggestUpdate = false),
            ),
            Rule(
                listOf(
                    "private video",
                    "this video is private",
                    "login required",
                    "sign in to confirm you're not a bot",
                    "cookies",
                ),
                AppError(R.string.error_private),
            ),
            Rule(
                listOf(
                    "not available in your country",
                    "geo restricted",
                    "geo-restricted",
                    "blocked in your country",
                    "geoblock",
                ),
                AppError(R.string.error_geo),
            ),
            Rule(
                listOf(
                    "video unavailable",
                    "this video is not available",
                    "content isn't available",
                    "video is unavailable",
                ),
                AppError(R.string.error_unavailable),
            ),
            Rule(
                listOf("has been removed", "does not exist", "no longer available", "404 not found", "not found"),
                AppError(R.string.error_removed),
            ),
            Rule(
                listOf("live event will begin", "premieres in", "is live", "live stream"),
                AppError(R.string.error_live),
            ),
            Rule(
                listOf("requested format is not available", "requested format not available", "no video formats found"),
                AppError(R.string.error_format),
            ),
            Rule(
                listOf("drm", "protected by drm", "widevine"),
                AppError(R.string.error_drm),
            ),
            Rule(
                listOf("no space left", "enospc", "insufficient storage"),
                AppError(R.string.error_no_space),
            ),
            Rule(
                listOf(
                    "unable to download webpage",
                    "timed out",
                    "timeout",
                    "connection",
                    "network is unreachable",
                    "temporary failure in name resolution",
                    "failed to resolve",
                    "ssl",
                    "http error 5",
                ),
                AppError(R.string.error_network),
            ),
            Rule(
                listOf(
                    "unable to extract",
                    "signature",
                    "nsig",
                    "extractor error",
                    "failed to parse",
                    "player response",
                    "http error 403",
                    "http error 429",
                ),
                AppError(R.string.error_outdated, suggestUpdate = true),
            ),
            Rule(
                listOf("ffmpeg", "postprocessing", "post-processing", "conversion failed"),
                AppError(R.string.error_ffmpeg),
            ),
            Rule(
                listOf("permission denied", "read-only file system", "operation not permitted"),
                AppError(R.string.error_storage),
            ),
        )

    /** Maps yt-dlp output to an error; [url] disambiguates "unsupported url" for the submitted link. */
    fun fromYtDlp(
        raw: String?,
        url: String? = null,
    ): AppError {
        if (raw.isNullOrBlank()) return AppError.unknown()
        val text = raw.lowercase()
        val rule = rules.firstOrNull { r -> r.needles.any { text.contains(it) } }
        // "unsupported url" is only meaningful when yt-dlp is complaining about
        // the link the user submitted. A bad marker argument or a local path
        // must not be disguised as "site not supported" — keep the raw text.
        val unsupportedForSubmittedUrl =
            url != null &&
                text.contains(url.lowercase()) &&
                (
                    text.contains("unsupported url") ||
                        text.contains("is not a valid url") ||
                        text.contains("no suitable extractor")
                )
        val error =
            when {
                rule != null -> rule.error
                unsupportedForSubmittedUrl -> AppError(R.string.error_unsupported)
                else -> AppError.unknown(raw.takeLast(Limits.ERROR_TECHNICAL_DETAIL_CHARS))
            }
        return error.copy(technical = raw.takeLast(Limits.ERROR_TECHNICAL_TAIL_CHARS))
    }

    /** Uses the exception chain as the technical text so nested causes stay visible. */
    fun fromThrowable(t: Throwable): AppError {
        val message =
            buildString {
                append(t.message ?: t::class.java.simpleName)
                var cause = t.cause
                var depth = 0
                while (cause != null && depth < MAX_CAUSE_DEPTH) {
                    append("\n").append(cause.message)
                    cause = cause.cause
                    depth++
                }
            }
        return fromYtDlp(message)
    }
}
