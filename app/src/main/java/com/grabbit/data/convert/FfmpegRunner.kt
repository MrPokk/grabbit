package com.grabbit.data.convert

import android.content.Context
import android.media.MediaMetadataRetriever
import com.grabbit.core.AppLog
import com.grabbit.core.Defaults
import com.grabbit.core.FfmpegArgs
import com.grabbit.core.Formatting
import com.grabbit.core.Timing
import com.grabbit.data.download.ConvertFormat
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FfmpegRunner"

@Singleton
class FfmpegRunner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val active = ConcurrentHashMap<String, Process>()
        private val initLock = Mutex()

        @Volatile
        private var initialized = false

        fun isAvailable(): Boolean = binary().exists()

        suspend fun ensureInitialized() {
            if (initialized) return
            initLock.withLock {
                if (initialized) return
                withContext(Dispatchers.IO) { FFmpeg.getInstance().init(context) }
                initialized = true
            }
        }

        fun cancel(key: String) {
            active[key]?.let { process ->
                try {
                    process.destroy()
                } catch (e: SecurityException) {
                    AppLog.e(TAG, "could not destroy ffmpeg process", e)
                }
                active.remove(key)
            }
        }

        suspend fun convert(
            input: File,
            output: File,
            target: ConvertFormat,
            bitrate: Int = Defaults.AUDIO_BITRATE_KBPS,
            key: String? = null,
            onProgress: ((Int) -> Unit)? = null,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                ensureInitialized()
                if (!isAvailable()) return@withContext Result.failure(IllegalStateException("FFmpeg is not available"))
                val durationMs = durationMs(input)
                if (target.audioOnly) {
                    return@withContext execute(
                        buildArgs(input, output, audioCodecArgs(target, bitrate)),
                        key,
                        durationMs,
                        onProgress,
                    )
                }
                val copy = execute(buildArgs(input, output, listOf("-c", "copy")), key, durationMs, onProgress)
                if (copy.isSuccess) return@withContext copy
                output.delete()
                val encodeArgs =
                    buildArgs(
                        input,
                        output,
                        audioCodecArgs(ConvertFormat.M4A, bitrate) + videoCodecArgs(target),
                    )
                execute(encodeArgs, key, durationMs, onProgress)
            }

        /** Merge video + audio by copying streams (no re-encode) into [output]. */
        suspend fun mergeCopy(
            video: File,
            audio: File,
            output: File,
            key: String?,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                ensureInitialized()
                if (!isAvailable()) return@withContext Result.failure(IllegalStateException("FFmpeg is not available"))
                execute(
                    listOf(
                        "-y",
                        "-hide_banner",
                        "-nostdin",
                        "-loglevel",
                        "error",
                        "-i",
                        video.absolutePath,
                        "-i",
                        audio.absolutePath,
                        "-map",
                        "0:v",
                        "-map",
                        "1:a",
                        "-c",
                        "copy",
                        output.absolutePath,
                    ),
                    key,
                    0L,
                    null,
                )
            }

        /** Audio-only conversion that can embed cover art (jpg) + metadata, like yt-dlp's audio path. */
        suspend fun convertAudioEmbed(args: AudioEmbedArgs): Result<Unit> =
            withContext(Dispatchers.IO) {
                val input = args.input
                val output = args.output
                val target = args.target
                val bitrate = args.bitrate
                val coverJpg = args.coverJpg
                val metadataTitle = args.metadataTitle
                val metadataArtist = args.metadataArtist
                val key = args.key
                ensureInitialized()
                if (!isAvailable()) return@withContext Result.failure(IllegalStateException("FFmpeg is not available"))
                val durationMs = durationMs(input)
                execute(
                    buildEmbedArgs(input, output, target, bitrate, coverJpg, metadataTitle, metadataArtist),
                    key,
                    durationMs,
                    null,
                )
            }

        /** Convert any image (e.g. WebP thumbnail) to a baseline JPEG. */
        suspend fun convertThumbnailToJpg(
            input: File,
            output: File,
            key: String?,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                ensureInitialized()
                if (!isAvailable()) return@withContext Result.failure(IllegalStateException("FFmpeg is not available"))
                execute(
                    listOf(
                        "-y",
                        "-hide_banner",
                        "-nostdin",
                        "-loglevel",
                        "error",
                        "-i",
                        input.absolutePath,
                        "-frames:v",
                        "1",
                        output.absolutePath,
                    ),
                    key,
                    0L,
                    null,
                )
            }

        private fun buildEmbedArgs(
            input: File,
            output: File,
            target: ConvertFormat,
            bitrate: Int,
            coverJpg: File?,
            metadataTitle: String?,
            metadataArtist: String?,
        ): List<String> {
            val embedCover = coverJpg != null && target != ConvertFormat.OPUS
            val args =
                mutableListOf(
                    "-y",
                    "-hide_banner",
                    "-nostdin",
                    "-loglevel",
                    "error",
                    "-i",
                    input.absolutePath,
                )
            if (embedCover) args += listOf("-i", coverJpg.absolutePath)
            args += "-map"
            args += "0:a"
            if (embedCover) args += listOf("-map", "1:v", "-c:v", "mjpeg", "-disposition:v", "attached_pic")
            args += audioOnlyCodecArgs(target, bitrate)
            if (target == ConvertFormat.MP3) args += listOf("-id3v2_version", "3")
            metadataTitle?.takeIf { it.isNotBlank() }?.let { args += listOf("-metadata", "title=$it") }
            metadataArtist?.takeIf { it.isNotBlank() }?.let { args += listOf("-metadata", "artist=$it") }
            args += output.absolutePath
            return args
        }

        private fun audioOnlyCodecArgs(
            target: ConvertFormat,
            bitrate: Int,
        ): List<String> =
            when (target) {
                ConvertFormat.MP3 -> listOf("-c:a", "libmp3lame", "-b:a", "${bitrate}k")
                ConvertFormat.M4A -> listOf("-c:a", "aac", "-b:a", "${bitrate}k")
                ConvertFormat.OPUS -> listOf("-c:a", "libopus", "-b:a", "${bitrate}k")
                ConvertFormat.FLAC -> listOf("-c:a", "flac")
                else -> listOf("-c:a", "aac", "-b:a", "${bitrate}k")
            }

        private fun audioCodecArgs(
            target: ConvertFormat,
            bitrate: Int,
        ): List<String> =
            when (target) {
                ConvertFormat.MP3 -> listOf("-vn", "-c:a", "libmp3lame", "-b:a", "${bitrate}k")
                ConvertFormat.M4A -> listOf("-vn", "-c:a", "aac", "-b:a", "${bitrate}k")
                ConvertFormat.OPUS -> listOf("-vn", "-c:a", "libopus", "-b:a", "${bitrate}k")
                ConvertFormat.FLAC -> listOf("-vn", "-c:a", "flac")
                else -> listOf("-vn", "-c:a", "aac", "-b:a", "${bitrate}k")
            }

        private fun videoCodecArgs(target: ConvertFormat): List<String> =
            when (target) {
                ConvertFormat.WEBM ->
                    listOf(
                        "-c:v",
                        "libvpx-vp9",
                        "-crf",
                        FfmpegArgs.VP9_CRF.toString(),
                        "-b:v",
                        "0",
                        "-c:a",
                        "libopus",
                        "-b:a",
                        "${FfmpegArgs.VP9_BITRATE_KBPS}k",
                    )

                else ->
                    listOf(
                        "-c:v",
                        "libx264",
                        "-preset",
                        "veryfast",
                        "-crf",
                        FfmpegArgs.CRF.toString(),
                        "-c:a",
                        "aac",
                        "-b:a",
                        "${FfmpegArgs.FASTSTART_AUDIO_BITRATE_KBPS}k",
                        "-movflags",
                        "+faststart",
                    )
            }

        private fun buildArgs(
            input: File,
            output: File,
            codecArgs: List<String>,
        ): List<String> =
            listOf(
                "-y",
                "-hide_banner",
                "-nostdin",
                "-loglevel",
                "error",
                "-i",
                input.absolutePath,
                "-progress",
                "pipe:1",
                "-nostats",
            ) + codecArgs + listOf(output.absolutePath)

        private fun execute(
            args: List<String>,
            key: String?,
            durationMs: Long,
            onProgress: ((Int) -> Unit)?,
        ): Result<Unit> {
            val process = startProcess(args).getOrElse { return Result.failure(it) }
            if (key != null) active[key] = process
            val stderr = StringBuilder()
            val stderrReader = readStderr(process, stderr)
            return try {
                consumeProgress(process, durationMs, onProgress)
                val code = process.waitFor()
                joinStderrReader(stderrReader)
                if (code == 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException(stderr.toString().ifBlank { "ffmpeg exited with code $code" }))
                }
            } catch (
                e: IOException,
            ) {
                process.destroy()
                Result.failure(e)
            } catch (
                e: InterruptedException,
            ) {
                process.destroy()
                Result.failure(e)
            } finally {
                if (key != null) active.remove(key)
            }
        }

        private fun startProcess(args: List<String>): Result<Process> =
            try {
                Result.success(
                    ProcessBuilder(listOf(binary().absolutePath) + args)
                        .redirectErrorStream(false)
                        .apply {
                            environment()["LD_LIBRARY_PATH"] = libDir().absolutePath
                            environment()["HOME"] = context.filesDir.absolutePath
                            environment()["TMPDIR"] = context.cacheDir.absolutePath
                        }.start(),
                )
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: SecurityException) {
                Result.failure(e)
            }

        private fun readStderr(
            process: Process,
            stderr: StringBuilder,
        ): Thread =
            Thread {
                try {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        if (stderr.length < FfmpegArgs.STDERR_LIMIT_BYTES) stderr.appendLine(line)
                    }
                } catch (e: IOException) {
                    AppLog.e(TAG, "could not read ffmpeg stderr", e)
                }
            }.apply {
                isDaemon = true
                start()
            }

        private fun consumeProgress(
            process: Process,
            durationMs: Long,
            onProgress: ((Int) -> Unit)?,
        ) {
            if (onProgress == null) return
            process.inputStream.bufferedReader().forEachLine { line ->
                val micros = progressMicros(line)
                if (micros != null && micros > 0 && durationMs > 0) {
                    val fraction = micros / Formatting.MICROS_PER_MILLIS / durationMs
                    val percent =
                        (fraction * Formatting.PERCENT_SCALE)
                            .toInt()
                            .coerceIn(0, Formatting.PROGRESS_CONVERT_MAX_PERCENT)
                    onProgress(percent)
                }
            }
        }

        private fun progressMicros(line: String): Long? =
            when {
                line.startsWith("out_time_us=") -> line.removePrefix("out_time_us=").toLongOrNull()
                line.startsWith("out_time_ms=") -> line.removePrefix("out_time_ms=").toLongOrNull()
                else -> null
            }

        private fun joinStderrReader(reader: Thread) {
            try {
                reader.join(Timing.STDERR_JOIN_MS)
            } catch (e: InterruptedException) {
                AppLog.e(TAG, "interrupted while joining ffmpeg stderr reader", e)
            }
        }

        private fun durationMs(file: File): Long {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (
                e: IllegalArgumentException,
            ) {
                durationReadError(e)
            } catch (
                e: SecurityException,
            ) {
                durationReadError(e)
            } catch (
                e: IllegalStateException,
            ) {
                durationReadError(e)
            } finally {
                releaseQuietly(retriever)
            }
        }

        private fun durationReadError(e: Exception): Long {
            AppLog.e(TAG, "could not read the media duration", e)
            return 0L
        }

        private fun releaseQuietly(retriever: MediaMetadataRetriever) {
            try {
                retriever.release()
            } catch (e: IllegalStateException) {
                AppLog.e(TAG, "could not release the media retriever", e)
            }
        }

        private fun binary(): File = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

        private fun libDir(): File =
            File(File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg"), "usr/lib")
    }

/** Arguments for [FfmpegRunner.convertAudioEmbed]. */
data class AudioEmbedArgs(
    val input: File,
    val output: File,
    val target: ConvertFormat,
    val bitrate: Int,
    val coverJpg: File?,
    val metadataTitle: String?,
    val metadataArtist: String?,
    val key: String?,
)
