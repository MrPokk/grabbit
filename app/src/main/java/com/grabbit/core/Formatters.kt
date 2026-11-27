package com.grabbit.core

import android.content.res.Resources
import com.grabbit.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

object Formatters {
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

    fun bytes(
        resources: Resources,
        bytes: Long,
    ): String {
        val numberFormat = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
        val units =
            arrayOf(
                R.string.unit_b,
                R.string.unit_kb,
                R.string.unit_mb,
                R.string.unit_gb,
                R.string.unit_tb,
            )
        var value = bytes.coerceAtLeast(0).toDouble()
        var index = 0
        while (value >= Formatting.BYTES_BASE && index < units.lastIndex) {
            value /= Formatting.BYTES_BASE
            index++
        }
        val display =
            if (index == 0) {
                numberFormat.format(value.roundToLong())
            } else {
                numberFormat.minimumFractionDigits = 1
                val rounded = (value * Formatting.BYTES_PRECISION).roundToLong() / Formatting.BYTES_PRECISION.toDouble()
                numberFormat.format(rounded)
            }
        return resources.getString(R.string.unit_size, display, resources.getString(units[index]))
    }

    fun speed(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * Formatting.PERCENT_SCALE_F).roundToLong().toInt()

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
