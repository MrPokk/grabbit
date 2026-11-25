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

/**
 * Runs the FFmpeg binary that ships inside youtubedl-android.
 *
 * The binary is packaged as `libffmpeg.so` in the native library dir (that is
 * the only way to ship an executable inside an APK), and its shared libraries
 * are unzipped by the ffmpeg module into `noBackupFilesDir`.
 */
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

        /** Whether the bundled FFmpeg executable has been unpacked. */
        fun isAvailable(): Boolean = binary().exists()

        /** Unpacks the bundled FFmpeg payload. Safe to call from anywhere. */
        suspend fun ensureInitialized() {
            if (initialized) return
            initLock.withLock {
                if (initialized) return
                withContext(Dispatchers.IO) { FFmpeg.getInstance().init(context) }
                initialized = true
            }
        }

        /** Stops a conversion started with the same [key]. */
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

        /**
         * Converts [input] into [output], trying stream copy before re-encoding for video.
         * Reports progress as whole percent points through [onProgress].
         */
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
                if (!isAvailable()) {
                    return@withContext Result.failure(IllegalStateException("FFmpeg is not available"))
                }
                val durationMs = durationMs(input)

                if (target.audioOnly) {
                    val args = buildArgs(input, output, audioCodecArgs(target, bitrate))
                    return@withContext execute(args, key, durationMs, onProgress)
                }

                // Video: try a stream copy first (fast and lossless), fall back to a
                // re-encode when the source codecs are not compatible with the target.
                val copy = execute(buildArgs(input, output, listOf("-c", "copy")), key, durationMs, onProgress)
                if (copy.isSuccess) return@withContext copy

                output.delete()
                val encodeArgs =
                    buildArgs(
                        input = input,
                        output = output,
                        codecArgs = audioCodecArgs(ConvertFormat.M4A, bitrate) + videoCodecArgs(target),
                    )
                execute(encodeArgs, key, durationMs, onProgress)
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
            key?.let { active[it] = process }
            val stderr = StringBuilder()
            val stderrReader = readStderr(process, stderr)

            return try {
                consumeProgress(process, durationMs, onProgress)
                val code = process.waitFor()
                joinStderrReader(stderrReader)
                if (code == 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(
                        IllegalStateException(
                            stderr.toString().ifBlank { "ffmpeg exited with code $code" },
                        ),
                    )
                }
            } catch (e: IOException) {
                process.destroy()
                Result.failure(e)
            } catch (e: InterruptedException) {
                process.destroy()
                Result.failure(e)
            } finally {
                key?.let { active.remove(it) }
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
            process.inputStream.bufferedReader().forEachLine { line ->
                val micros = progressMicros(line)
                if (micros != null && micros > 0 && durationMs > 0 && onProgress != null) {
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
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (e: IllegalArgumentException) {
                AppLog.e(TAG, "could not read the media duration", e)
                0L
            } catch (e: SecurityException) {
                AppLog.e(TAG, "could not read the media duration", e)
                0L
            } catch (e: IllegalStateException) {
                AppLog.e(TAG, "could not read the media duration", e)
                0L
            } finally {
                try {
                    retriever.release()
                } catch (e: IllegalStateException) {
                    AppLog.e(TAG, "could not release the media retriever", e)
                }
            }
        }

        private fun binary(): File = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

        private fun libDir(): File =
            File(File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg"), "usr/lib")
    }
