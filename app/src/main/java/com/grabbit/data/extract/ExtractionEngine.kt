package com.grabbit.data.extract

import com.grabbit.core.FetchTrace
import com.grabbit.data.ytdlp.DownloadSpec
import com.grabbit.data.ytdlp.FetchedMedia
import java.io.File
import java.util.UUID

/** Which backend produced a result; surfaced in FetchTrace debug output. */
enum class EngineKind(
    val logName: String,
) {
    YTDLP("yt-dlp"),
    NEWPIPE("newpipe"),
}

/**
 * A media extraction backend: fetch metadata (incl. formats) for a URL and download it.
 *
 * Implementations must keep [com.grabbit.data.ytdlp.InfoException] /
 * [com.grabbit.data.ytdlp.DownloadException] /
 * [com.grabbit.data.ytdlp.DownloadCancelledException] semantics so callers don't depend on the engine.
 */
interface ExtractionEngine {
    val kind: EngineKind

    /**
     * Fetch metadata for [url]. Implementations normalize URLs, keep mix/radio handling, caches,
     * and honour cancellation via [processId] + [cancelFetch].
     */
    suspend fun fetch(
        url: String,
        loadMixAsPlaylist: Boolean,
        processId: String = UUID.randomUUID().toString(),
        bypassCache: Boolean = false,
        trace: FetchTrace? = null,
    ): FetchedMedia

    /** Cancel an in-flight [fetch] identified by [processId]. */
    fun cancelFetch(processId: String)

    /**
     * Download [spec] to a file in [DownloadSpec.workDir].
     * [onProgress] receives (progressPercent 0..100, etaSeconds, rawLine); the raw line carries speed text.
     */
    suspend fun download(
        spec: DownloadSpec,
        onProgress: (progress: Float, etaSeconds: Long, line: String) -> Unit,
    ): File

    /** Cancel an in-flight [download] identified by [DownloadSpec.taskId]. */
    fun cancelDownload(taskId: String)
}
