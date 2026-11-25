package com.grabbit.data.convert

import android.content.Context
import android.media.MediaMetadataRetriever
import com.grabbit.data.download.ConvertFormat
import com.yausername.ffmpeg.FFmpeg
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the FFmpeg binary that ships inside youtubedl-android.
 *
 * The binary is packaged as `libffmpeg.so` in the native library dir (that is
 * the only way to ship an executable inside an APK), and its shared libraries
 * are unzipped by the ffmpeg module into `noBackupFilesDir`.
 */
@Singleton
class FfmpegRunner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val active = ConcurrentHashMap<String, Process>()
    private val initLock = Mutex()

    @Volatile
    private var initialized = false

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

    fun cancel(key: String) {
        active[key]?.let { process ->
            runCatching { process.destroy() }
            active.remove(key)
        }
    }

    suspend fun convert(
        input: File,
        output: File,
        target: ConvertFormat,
        bitrate: Int = 192,
        key: String? = null,
        onProgress: ((Int) -> Unit)? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
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
        val encodeArgs = buildArgs(
            input = input,
            output = output,
            codecArgs = audioCodecArgs(ConvertFormat.M4A, bitrate) + videoCodecArgs(target),
        )
        execute(encodeArgs, key, durationMs, onProgress)
    }

    private fun audioCodecArgs(target: ConvertFormat, bitrate: Int): List<String> = when (target) {
        ConvertFormat.MP3 -> listOf("-vn", "-c:a", "libmp3lame", "-b:a", "${bitrate}k")
        ConvertFormat.M4A -> listOf("-vn", "-c:a", "aac", "-b:a", "${bitrate}k")
        ConvertFormat.OPUS -> listOf("-vn", "-c:a", "libopus", "-b:a", "${bitrate}k")
        ConvertFormat.FLAC -> listOf("-vn", "-c:a", "flac")
        else -> listOf("-vn", "-c:a", "aac", "-b:a", "${bitrate}k")
    }

    private fun videoCodecArgs(target: ConvertFormat): List<String> = when (target) {
        ConvertFormat.WEBM -> listOf(
            "-c:v", "libvpx-vp9", "-crf", "34", "-b:v", "0",
            "-c:a", "libopus", "-b:a", "128k",
        )

        else -> listOf(
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "22",
            "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart",
        )
    }

    private fun buildArgs(input: File, output: File, codecArgs: List<String>): List<String> =
        listOf(
            "-y", "-hide_banner", "-nostdin", "-loglevel", "error",
            "-i", input.absolutePath,
            "-progress", "pipe:1", "-nostats",
        ) + codecArgs + listOf(output.absolutePath)

    private fun execute(
        args: List<String>,
        key: String?,
        durationMs: Long,
        onProgress: ((Int) -> Unit)?,
    ): Result<Unit> {
        val process = try {
            ProcessBuilder(listOf(binary().absolutePath) + args)
                .redirectErrorStream(false)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = libDir().absolutePath
                    environment()["HOME"] = context.filesDir.absolutePath
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                }
                .start()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        key?.let { active[it] = process }
        val stderr = StringBuilder()
        val stderrReader = Thread {
            runCatching {
                process.errorStream.bufferedReader().forEachLine { line ->
                    if (stderr.length < 8000) stderr.appendLine(line)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        return try {
            process.inputStream.bufferedReader().forEachLine { line ->
                val micros = when {
                    line.startsWith("out_time_us=") -> line.removePrefix("out_time_us=").toLongOrNull()
                    line.startsWith("out_time_ms=") -> line.removePrefix("out_time_ms=").toLongOrNull()
                    else -> null
                }
                if (micros != null && micros > 0 && durationMs > 0 && onProgress != null) {
                    val percent = ((micros / 1000.0) / durationMs * 100.0).toInt().coerceIn(0, 99)
                    onProgress(percent)
                }
            }
            val code = process.waitFor()
            runCatching { stderrReader.join(500) }
            if (code == 0) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(
                        stderr.toString().ifBlank { "ffmpeg exited with code $code" },
                    ),
                )
            }
        } catch (e: Exception) {
            process.destroy()
            Result.failure(e)
        } finally {
            key?.let { active.remove(it) }
        }
    }

    private fun durationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun binary(): File = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

    private fun libDir(): File =
        File(File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg"), "usr/lib")
}
