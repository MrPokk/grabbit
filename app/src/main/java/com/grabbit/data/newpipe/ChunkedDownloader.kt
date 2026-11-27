package com.grabbit.data.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/** Named chunked-download tuning. */
object ChunkedDownloadConfig {
    /** Parallel ranged-chunk concurrency (YouTube throttles a single long request). */
    const val CHUNK_CONCURRENCY = 4
    const val CHUNK_SIZE_BYTES = 4L * 1024L * 1024L
    const val CHUNK_MAX_RETRIES = 3
    const val PROGRESS_EMIT_INTERVAL_MS = 250L
    const val SPEED_WINDOW_MS = 1000L
    const val BYTES_PER_KB = 1024.0
    const val BYTES_PER_MB = 1024.0 * 1024.0
    const val MILLIS_PER_SECOND = 1000.0
    const val PERCENT_SCALE = 100f
}

class ChunkedDownloadException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Chunked/ranged HTTP downloader: parallel chunks, retries, resume, progress + speed.
 *
 * Resume: a `<target>.parts` sidecar lists completed chunk indices; a partial [target] plus sidecar
 * lets a later call skip finished chunks. Unknown total size or no range support -> single stream.
 */
class ChunkedDownloader(
    private val client: OkHttpClient,
    private val concurrency: Int = ChunkedDownloadConfig.CHUNK_CONCURRENCY,
    private val chunkSizeBytes: Long = ChunkedDownloadConfig.CHUNK_SIZE_BYTES,
    private val maxRetries: Int = ChunkedDownloadConfig.CHUNK_MAX_RETRIES,
) {
    suspend fun download(
        url: String,
        target: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: (progressPercent: Float, etaSeconds: Long, line: String) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val totalSize = probeTotalSize(url, headers)
        val rangeSupported = probeRangeSupport(url, headers)

        if (totalSize == null || totalSize <= 0L || !rangeSupported) {
            downloadSingleStream(url, target, headers, totalSize, onProgress, isCancelled)
            return@withContext
        }

        val planned = ChunkPlanner.plan(totalSize, chunkSizeBytes)
        val partsFile = File(target.parentFile, "${target.name}.parts")
        val completed = readCompleted(partsFile)
        val remaining = ChunkPlanner.remaining(planned, completed)

        // Trust an existing partial target only when we have resume metadata.
        val canResume =
            target.exists() && completed.isNotEmpty() && target.length() >= maxWrittenOffset(planned, completed)
        if (!canResume) {
            completed.clear()
            prepareTarget(target)
        }

        val bytesDone = AtomicLong(bytesWritten(planned, completed))
        val startedAt = System.nanoTime()
        val semaphore = Semaphore(concurrency.coerceAtLeast(1))

        val targetFile = RandomAccessFile(target, "rw")
        try {
            if (!canResume) targetFile.setLength(totalSize)
            coroutineScope {
                remaining
                    .map { range ->
                        async {
                            semaphore.withPermit {
                                if (isCancelled()) throw ChunkedDownloadException("cancelled")
                                downloadChunk(url, range, headers, targetFile, isCancelled) { writtenInChunk ->
                                    val done = bytesDone.get()
                                    reportProgress(done + writtenInChunk, totalSize, startedAt, onProgress)
                                }
                                markCompleted(partsFile, range.index)
                                val done = bytesDone.addAndGet(range.length)
                                reportProgress(done, totalSize, startedAt, onProgress)
                            }
                        }
                    }.awaitAll()
            }
        } finally {
            targetFile.close()
        }

        reportProgress(totalSize, totalSize, startedAt, onProgress)
        partsFile.delete()
    }

    private fun downloadChunk(
        url: String,
        range: ByteRange,
        headers: Map<String, String>,
        targetFile: RandomAccessFile,
        isCancelled: () -> Boolean,
        onChunkProgress: (writtenInChunk: Long) -> Unit,
    ) {
        var lastError: Throwable? = null
        repeat(maxRetries.coerceAtLeast(1)) {
            if (isCancelled()) throw ChunkedDownloadException("cancelled")
            try {
                val writtenInChunk = AtomicLong(0L)
                val from = range.startInclusive + writtenInChunk.get()
                val request = baseRequest(url, headers).header("Range", "bytes=$from-${range.endInclusive}").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != HTTP_PARTIAL_CONTENT) {
                        throw ChunkedDownloadException("HTTP ${response.code} for chunk ${range.index}")
                    }
                    val body = response.body ?: throw ChunkedDownloadException("empty body for chunk ${range.index}")
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (isCancelled()) throw ChunkedDownloadException("cancelled")
                            synchronized(targetFile) {
                                targetFile.seek(range.startInclusive + writtenInChunk.get())
                                targetFile.write(buffer, 0, read)
                            }
                            writtenInChunk.addAndGet(read.toLong())
                            onChunkProgress(writtenInChunk.get())
                        }
                    }
                }
                if (writtenInChunk.get() != range.length) {
                    throw ChunkedDownloadException(
                        "short read on chunk ${range.index}: ${writtenInChunk.get()}/${range.length}",
                    )
                }
                return
            } catch (e: ChunkedDownloadException) {
                if (e.message == "cancelled") throw e
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw ChunkedDownloadException("chunk ${range.index} failed after $maxRetries attempts", lastError)
    }

    private fun downloadSingleStream(
        url: String,
        target: File,
        headers: Map<String, String>,
        totalSize: Long?,
        onProgress: (Float, Long, String) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val request = baseRequest(url, headers).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ChunkedDownloadException("HTTP ${response.code}")
            val body = response.body ?: throw ChunkedDownloadException("empty body")
            val total = totalSize ?: body.contentLength().takeIf { it > 0 }
            copyStream(body, target, total, onProgress, isCancelled)
        }
    }

    private fun streamTo(
        input: java.io.InputStream,
        out: java.io.OutputStream,
        total: Long?,
        onProgress: (Float, Long, String) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var read: Int
        var done = 0L
        val startedAt = System.nanoTime()
        while (input.read(buffer).also { read = it } != -1) {
            if (isCancelled()) throw ChunkedDownloadException("cancelled")
            out.write(buffer, 0, read)
            done += read
            if (total != null) reportProgress(done, total, startedAt, onProgress)
        }
    }

    private fun copyStream(
        body: okhttp3.ResponseBody,
        target: File,
        total: Long?,
        onProgress: (Float, Long, String) -> Unit,
        isCancelled: () -> Boolean,
    ) {
        target.outputStream().use { out ->
            body.byteStream().use { input -> streamTo(input, out, total, onProgress, isCancelled) }
        }
    }

    @Suppress("SwallowedException")
    private fun probeTotalSize(
        url: String,
        headers: Map<String, String>,
    ): Long? =
        try {
            client.newCall(baseRequest(url, headers).head().build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
            }
        } catch (e: IOException) {
            null
        }

    @Suppress("SwallowedException")
    private fun probeRangeSupport(
        url: String,
        headers: Map<String, String>,
    ): Boolean =
        try {
            client.newCall(baseRequest(url, headers).header("Range", "bytes=0-0").build()).execute().use { response ->
                if (response.code == HTTP_PARTIAL_CONTENT) return true
                val acceptRanges = response.header("Accept-Ranges")?.lowercase().orEmpty()
                acceptRanges.contains("bytes") || response.code == HTTP_PARTIAL_CONTENT
            }
        } catch (e: IOException) {
            false
        }

    private fun baseRequest(
        url: String,
        headers: Map<String, String>,
    ): Request.Builder {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun prepareTarget(target: File) {
        if (!target.exists()) target.createNewFile()
    }

    @Suppress("SwallowedException")
    private fun readCompleted(partsFile: File): MutableSet<Int> {
        if (!partsFile.exists()) return mutableSetOf()
        return try {
            partsFile.readLines().mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
        } catch (e: IOException) {
            mutableSetOf()
        }
    }

    private fun markCompleted(
        partsFile: File,
        index: Int,
    ) {
        synchronized(partsFile) {
            partsFile.appendText("$index\n")
        }
    }

    private fun bytesWritten(
        planned: List<ByteRange>,
        completed: Set<Int>,
    ): Long = planned.filter { it.index in completed }.sumOf { it.length }

    private fun maxWrittenOffset(
        planned: List<ByteRange>,
        completed: Set<Int>,
    ): Long = planned.filter { it.index in completed }.maxOfOrNull { it.endInclusive + 1 } ?: 0L

    private fun reportProgress(
        done: Long,
        total: Long,
        startedAtNanos: Long,
        onProgress: (Float, Long, String) -> Unit,
    ) {
        val percent = if (total > 0) (done.toFloat() / total * ChunkedDownloadConfig.PERCENT_SCALE) else 0f
        val elapsedSeconds = (System.nanoTime() - startedAtNanos) / NANOS_PER_SECOND
        val speedBytesPerSecond = if (elapsedSeconds > 0) done / elapsedSeconds else 0.0
        val eta = if (speedBytesPerSecond > 0) ((total - done) / speedBytesPerSecond).toLong() else 0L
        onProgress(percent.coerceIn(0f, ChunkedDownloadConfig.PERCENT_SCALE), eta, progressLine(speedBytesPerSecond))
    }

    private fun progressLine(speedBytesPerSecond: Double): String = "at ${formatSpeed(speedBytesPerSecond)}"

    private fun formatSpeed(bytesPerSecond: Double): String =
        when {
            bytesPerSecond >= ChunkedDownloadConfig.BYTES_PER_MB ->
                "${"%.2f".format(bytesPerSecond / ChunkedDownloadConfig.BYTES_PER_MB)}MiB/s"
            bytesPerSecond >= ChunkedDownloadConfig.BYTES_PER_KB ->
                "${"%.2f".format(bytesPerSecond / ChunkedDownloadConfig.BYTES_PER_KB)}KiB/s"
            else -> "${bytesPerSecond.toLong()}B/s"
        }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val HTTP_PARTIAL_CONTENT = 206
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
