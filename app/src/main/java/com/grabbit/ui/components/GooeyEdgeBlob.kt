package com.grabbit.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.grabbit.core.UiValues
import com.grabbit.ui.theme.Dimens

/** Alpha contrast: keeps only the top half of the summed field and makes it fully opaque. */
private const val GOO_ALPHA_GAIN = 39f
private const val GOO_ALPHA_OFFSET_BYTE = -5000f

/** Field value that lands exactly on the threshold, i.e. the untouched contour. */
private const val GOO_EDGE_FIELD_MIDPOINT = 0.5f

/** The edge tongue's fade window is twice its thickness at the centre. */
private const val GOO_WINDOW_FACTOR = 2f

/** Legacy strip height: fine enough that the contour step stays sub-pixel on the wings. */
private const val GOO_STRIP_STEP_DP = 1f
private const val GOO_FULL_ALPHA = 1f
private const val GOO_CLEAR_ALPHA = 0f
private const val GOO_HALF = 0.5f
private const val GOO_COLOR_CHANNEL_MASK = 0x00FFFFFF
private const val GOO_ALPHA_SHIFT = 24
private const val GOO_BYTE_MAX = 255

// AGSL port of the same fields used by the strip fallback: circle + pulled edge, summed with
// a + e * (1 - a) and pushed through the alpha contrast in one pass.
private const val GOO_AGSL = """
uniform float2 uCenter;
uniform float uRadius;
uniform float uHalo;
uniform float uEdgeX;
uniform float uEdgeDir;
uniform float uSpread;
uniform float uDepth;
uniform half4 uColor;

half4 main(float2 p) {
    float r = distance(p, uCenter);
    float aCircle = clamp((uRadius + uHalo * 0.5 - r) / uHalo, 0.0, 1.0);
    float dist = (p.x - uEdgeX) * uEdgeDir;
    float dy = (p.y - uCenter.y) / uSpread;
    float aEdge = 0.0;
    if (abs(dy) < 1.0) {
        float bell = 1.0 - dy * dy;
        float t = uDepth * bell * bell;
        float w = max(2.0 * t, uHalo);
        aEdge = clamp(0.5 + (t - dist) / w, 0.0, 1.0);
    }
    float a = aCircle + aEdge * (1.0 - aCircle);
    float k = clamp(39.0 * a - 19.6, 0.0, 1.0);
    return half4(uColor.rgb * k, k);
}
"""

/** Everything the two fields need: circle, and the edge line the tongue is pulled from. */
private data class GooField(
    val center: Offset,
    val radius: Float,
    val halo: Float,
    val edgeX: Float,
    val edgeDirection: Float,
    val spread: Float,
    val depth: Float,
)

/**
 * Docked blob: a soft circle field plus an edge field that is pulled toward the circle and
 * spreads along the screen edge. The two fields are summed and passed through an alpha contrast,
 * so the visible contour starts exactly on the circle's rim and the edge fades out tangentially
 * at both ends. API 33+ evaluates the fields in one AGSL shader; older versions draw the circle
 * gradient and one horizontal gradient strip per dp inside a single contrast-filtered layer.
 * Icon/progress must be drawn by the caller on top.
 */
@Composable
internal fun GooeyEdgeBlob(
    edge: DockEdge,
    reveal: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radius = with(density) { Dimens.gooCircleSize.toPx() * UiValues.MIDPOINT }
    val halo = with(density) { Dimens.gooTransitionRadius.toPx() }
    val spread = with(density) { Dimens.gooEdgeSpread.toPx() }
    val depth = with(density) { Dimens.gooEdgeDepth.toPx() }
    val gap = with(density) { Dimens.gooEdgeGap.toPx() }
    val stripStep = with(density) { GOO_STRIP_STEP_DP.dp.toPx() }
    val strength = reveal.coerceIn(0f, 1f)
    val colorArgb = color.toArgb()

    val agslShader =
        remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createGooShader() else null
        }
    val agslPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Canvas(modifier) {
        val center = Offset(size.width * UiValues.MIDPOINT, size.height * UiValues.MIDPOINT)
        if (strength <= 0f) {
            drawCircle(color = color, radius = radius, center = center)
            return@Canvas
        }
        // The screen edge sits one circle radius (plus the gap) from the circle's centre.
        val edgeDistance = radius + gap
        val field =
            GooField(
                center = center,
                radius = radius,
                halo = halo * strength,
                edgeX = if (edge == DockEdge.LEFT) center.x - edgeDistance else center.x + edgeDistance,
                edgeDirection = if (edge == DockEdge.LEFT) 1f else -1f,
                spread = spread * strength,
                depth = depth * strength,
            )
        if (agslShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawShader(agslShader, agslPaint, field, color)
        } else {
            drawStrips(field, stripStep, colorArgb)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createGooShader(): RuntimeShader = RuntimeShader(GOO_AGSL)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawShader(
    shader: RuntimeShader,
    paint: Paint,
    field: GooField,
    color: Color,
) {
    shader.setFloatUniform("uCenter", field.center.x, field.center.y)
    shader.setFloatUniform("uRadius", field.radius)
    shader.setFloatUniform("uHalo", field.halo)
    shader.setFloatUniform("uEdgeX", field.edgeX)
    shader.setFloatUniform("uEdgeDir", field.edgeDirection)
    shader.setFloatUniform("uSpread", field.spread)
    shader.setFloatUniform("uDepth", field.depth)
    // Plain half4, not a layout(color) uniform: setColorUniform rejects it, components go as floats.
    shader.setFloatUniform("uColor", color.red, color.green, color.blue, color.alpha)
    paint.shader = shader
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}

// Legacy path: the same summed field, quantised into horizontal strips (T and w are constant
// across a strip) and thresholded once for the whole layer by the alpha contrast matrix.
private fun DrawScope.drawStrips(
    field: GooField,
    step: Float,
    colorArgb: Int,
) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val layerPaint =
            Paint().apply {
                colorFilter = ColorMatrixColorFilter(gooContrastMatrix())
            }
        val layer = native.saveLayer(0f, 0f, size.width, size.height, layerPaint)
        drawCircleField(native, field, colorArgb)
        drawEdgeStrips(native, field, step, colorArgb)
        native.restoreToCount(layer)
    }
}

private fun drawCircleField(
    native: android.graphics.Canvas,
    field: GooField,
    colorArgb: Int,
) {
    val half = field.halo * GOO_HALF
    val outer = field.radius + half
    val core = (field.radius - half).coerceAtLeast(GOO_CLEAR_ALPHA)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (outer <= core) {
        paint.color = colorArgb
    } else {
        paint.shader =
            RadialGradient(
                field.center.x,
                field.center.y,
                outer,
                intArrayOf(colorArgb, colorArgb, withAlpha(colorArgb, GOO_CLEAR_ALPHA)),
                floatArrayOf(GOO_CLEAR_ALPHA, core / outer, GOO_FULL_ALPHA),
                Shader.TileMode.CLAMP,
            )
    }
    native.drawCircle(field.center.x, field.center.y, outer, paint)
}

private fun drawEdgeStrips(
    native: android.graphics.Canvas,
    field: GooField,
    step: Float,
    colorArgb: Int,
) {
    if (field.spread <= 0f || step <= 0f) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var stripTop = field.center.y - field.spread
    while (stripTop < field.center.y + field.spread) {
        val stripCenter = stripTop + step * GOO_HALF
        val unit = (stripCenter - field.center.y) / field.spread
        val bell = GOO_FULL_ALPHA - unit * unit
        if (bell > GOO_CLEAR_ALPHA) {
            val thickness = field.depth * bell * bell
            val window = maxOf(GOO_WINDOW_FACTOR * thickness, field.halo)
            val reach = thickness + window * GOO_HALF
            if (thickness > GOO_CLEAR_ALPHA && window > GOO_CLEAR_ALPHA && reach > GOO_CLEAR_ALPHA) {
                val head =
                    (GOO_EDGE_FIELD_MIDPOINT + thickness / window)
                        .coerceIn(GOO_CLEAR_ALPHA, GOO_FULL_ALPHA)
                val tailX = field.edgeX + field.edgeDirection * reach
                paint.shader =
                    LinearGradient(
                        field.edgeX,
                        stripCenter,
                        tailX,
                        stripCenter,
                        intArrayOf(withAlpha(colorArgb, head), withAlpha(colorArgb, GOO_CLEAR_ALPHA)),
                        null,
                        Shader.TileMode.CLAMP,
                    )
                native.drawRect(
                    minOf(field.edgeX, tailX),
                    stripTop,
                    maxOf(field.edgeX, tailX),
                    stripTop + step,
                    paint,
                )
            }
        }
        stripTop += step
    }
}

private fun gooContrastMatrix(): ColorMatrix =
    ColorMatrix(
        floatArrayOf(
            1f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
            0f,
            0f,
            0f,
            0f,
            GOO_ALPHA_GAIN,
            GOO_ALPHA_OFFSET_BYTE,
        ),
    )

private fun withAlpha(
    colorArgb: Int,
    alpha: Float,
): Int {
    val byte = (alpha.coerceIn(GOO_CLEAR_ALPHA, GOO_FULL_ALPHA) * GOO_BYTE_MAX).toInt()
    return (colorArgb and GOO_COLOR_CHANNEL_MASK) or (byte shl GOO_ALPHA_SHIFT)
}
