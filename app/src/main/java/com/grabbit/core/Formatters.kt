package com.grabbit.core

import java.util.Locale
import kotlin.math.roundToLong

object Formatters {

    /** 75 -> "1:15", 3675 -> "1:01:15" */
    fun duration(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "—"
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** 3675 -> "1:01:15" for ETA values. */
    fun eta(totalSeconds: Long): String = duration(totalSeconds)

    fun bytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024
            index++
        }
        val rounded = (value * 10).roundToLong() / 10.0
        return if (index == 0) {
            "${rounded.toLong()} ${units[index]}"
        } else {
            String.format(Locale.US, "%.1f %s", rounded, units[index])
        }
    }

    /** "12.4MiB/s" -> "12.4MiB/s"; keeps yt-dlp's own formatting when present. */
    fun speed(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100f).roundToLong().toInt()

    fun resolution(height: Int, fps: Int): String {
        if (height <= 0) return "—"
        val label = when {
            height >= 4320 -> "8K"
            height >= 2160 -> "4K"
            height >= 1440 -> "1440p"
            else -> "${height}p"
        }
        return if (fps >= 50) "$label$fps" else label
    }
}
