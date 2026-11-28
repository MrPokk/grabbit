package com.grabbit.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

private const val ELLIPSIS = "…"
private const val MIDPOINT_DIVISOR = 2
private const val WORD_JOINER = " "
private val WORD_SEPARATOR = Regex("\\s+")

/** Text that truncates with an ellipsis at a word boundary instead of mid-word. */
@Composable
fun WordTruncatedText(
    text: String,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val maxWidthPx = constraints.maxWidth
        val truncated =
            remember(text, maxWidthPx, style, maxLines, measurer) {
                truncateToWordBoundary(
                    measurer = measurer,
                    text = text,
                    style = style,
                    maxWidthPx = maxWidthPx,
                    maxLines = maxLines,
                )
            }
        Text(
            text = truncated,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun truncateToWordBoundary(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    maxLines: Int,
): String {
    if (fitsWithinLines(measurer, text, style, maxWidthPx, maxLines)) return text
    val words = text.split(WORD_SEPARATOR).filter { it.isNotEmpty() }
    var low = 0
    var high = words.size - 1
    var best: String? = null
    while (low <= high) {
        val mid = (low + high) / MIDPOINT_DIVISOR
        val prefix = words.take(mid).joinToString(WORD_JOINER)
        val candidate = if (prefix.isEmpty()) ELLIPSIS else prefix + WORD_JOINER + ELLIPSIS
        if (fitsWithinLines(measurer, candidate, style, maxWidthPx, maxLines)) {
            best = candidate
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return best ?: ELLIPSIS
}

private fun fitsWithinLines(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    maxLines: Int,
): Boolean {
    val layout =
        measurer.measure(
            text = text,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = maxWidthPx),
        )
    return !layout.hasVisualOverflow
}
