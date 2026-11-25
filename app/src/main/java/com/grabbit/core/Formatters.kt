package com.grabbit.core

import android.content.res.Resources
import com.grabbit.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** Formatting helpers for durations, sizes, speeds and progress values. */
object Formatters {
    /** 75 -> "1:15", 3675 -> "1:01:15". */
    fun duration(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "—"
        val seconds = totalSeconds % Timing.SECONDS_PER_MINUTE
        val minutes = (totalSeconds / Timing.SECONDS_PER_MINUTE) % Timing.MINUTES_PER_HOUR
        val hours = totalSeconds / (Timing.SECONDS_PER_MINUTE * Timing.MINUTES_PER_HOUR)
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /** Formats a byte count with localized units and one decimal above kilobytes. */
    fun bytes(
        resources: Resources,
        bytes: Long,
    ): String {
        val numberFormat =
            NumberFormat.getNumberInstance().apply {
                maximumFractionDigits = 1
            }
        if (bytes <= 0) {
            return resources.getString(
                R.string.unit_size,
                numberFormat.format(0),
                resources.getString(R.string.unit_b),
            )
        }
        val units =
            arrayOf(
                R.string.unit_b,
                R.string.unit_kb,
                R.string.unit_mb,
                R.string.unit_gb,
                R.string.unit_tb,
            )
        var value = bytes.toDouble()
        var index = 0
        while (value >= Formatting.BYTES_BASE && index < units.lastIndex) {
            value /= Formatting.BYTES_BASE
            index++
        }
        if (index == 0) {
            return resources.getString(
                R.string.unit_size,
                numberFormat.format(value.roundToLong()),
                resources.getString(units[index]),
            )
        }
        val rounded = (value * Formatting.BYTES_PRECISION).roundToLong() / Formatting.BYTES_PRECISION.toDouble()
        numberFormat.minimumFractionDigits = 1
        return resources.getString(
            R.string.unit_size,
            numberFormat.format(rounded),
            resources.getString(units[index]),
        )
    }

    /** "12.4MiB/s" -> "12.4MiB/s"; keeps yt-dlp's own formatting when present. */
    fun speed(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    /** Converts a 0..1 fraction into a rounded percentage. */
    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * Formatting.PERCENT_SCALE_F).roundToLong().toInt()

    /** Universal resolution labels: "1080p", "4K", "8K" and an optional fps suffix. */
    fun resolution(
        height: Int,
        fps: Int,
    ): String {
        if (height <= 0) return "—"
        val label =
            when {
                height >= Formatting.RESOLUTION_8K_HEIGHT -> "8K"
                height >= Formatting.RESOLUTION_4K_HEIGHT -> "4K"
                height >= Formatting.RESOLUTION_1440_HEIGHT -> "1440p"
                else -> "${height}p"
            }
        return if (fps >= Formatting.FPS_THRESHOLD) "$label$fps" else label
    }
}
