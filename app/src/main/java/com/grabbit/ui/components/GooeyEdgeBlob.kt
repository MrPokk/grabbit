package com.grabbit.ui.components

import android.graphics.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grabbit.core.UiValues
import com.grabbit.ui.theme.Dimens

private const val GOO_ALPHA_GAIN = 39f
private const val GOO_ALPHA_OFFSET_BYTE = -5000f

private const val GOO_EDGE_FIELD_MIDPOINT = 0.5f

private const val GOO_WINDOW_FACTOR = 2f

private const val GOO_STRIP_STEP_DP = 1f
private const val GOO_FULL_ALPHA = 1f
private const val GOO_CLEAR_ALPHA = 0f
private const val GOO_HALF = 0.5f
private const val GOO_COLOR_CHANNEL_MASK = 0x00FFFFFF
private const val GOO_ALPHA_SHIFT = 24
private const val GOO_BYTE_MAX = 255

// AGSL port of the same fields used by the strip fallback: circle, flat edge and pulled edge,
// summed with a + b * (1 - a) and pushed through the alpha contrast in one pass.
private const val GOO_AGSL = """
uniform float2 uCenter;
uniform float uRadius;
uniform float uHalo;
uniform float uFlatY;
uniform float uFlatHalo;
uniform float uEdgeX;
uniform float uEdgeDir;
uniform float uSpread;
uniform float uDepth;
uniform half4 uColor;

half4 main(float2 p) {
float r = distance(p, uCenter);
float aCircle = 0.0;
if (uHalo > 0.0) {
    aCircle = clamp((uRadius + uHalo * 0.5 - r) / uHalo, 0.0, 1.0);
    } else if (r <= uRadius) {
        aCircle = 1.0;
        }
        float aFlat = 0.0;
        if (uFlatHalo > 0.0) {
            aFlat = clamp(0.5 + (p.y - uFlatY) / uFlatHalo, 0.0, 1.0);
            }
            float aEdge = 0.0;
            if (uSpread > 0.0) {
                float dist = (p.x - uEdgeX) * uEdgeDir;
                float dy = (p.y - uCenter.y) / uSpread;
                if (abs(dy) < 1.0) {
                    float bell = 1.0 - dy * dy;
                    float t = uDepth * bell * bell;
                    float w = max(2.0 * t, uHalo);
                    aEdge = clamp(0.5 + (t - dist) / w, 0.0, 1.0);
                    }
                    }
                    float a = aCircle + aFlat * (1.0 - aCircle);
                    a = a + aEdge * (1.0 - a);
                    float k = clamp(39.0 * a - 19.6, 0.0, 1.0);
                    return half4(uColor.rgb * k, k);
                    }
                    """

private data class GooField(
    val center: Offset,
    val radius: Float,
    val halo: Float,
    val flatY: Float = 0f,
    val flatHalo: Float = 0f,
    val edgeX: Float = 0f,
    val edgeDirection: Float = 0f,
    val spread: Float = 0f,
    val depth: Float = 0f,
)

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

    GooCanvas(color, stripStep, modifier) { center ->
        val edgeDistance = radius + gap
        GooField(
            center = center,
            radius = radius,
            halo = halo * strength,
            edgeX = if (edge == DockEdge.LEFT) center.x - edgeDistance else center.x + edgeDistance,
            edgeDirection = if (edge == DockEdge.LEFT) 1f else -1f,
            spread = spread * strength,
            depth = depth * strength,
        )
    }
}

@Composable
internal fun GooeyCradleBlob(
    circleRadius: Dp,
    flatY: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radius = with(density) { circleRadius.toPx() }
    val flatYPx = with(density) { flatY.toPx() }
    val halo = with(density) { Dimens.gooTransitionRadius.toPx() }
    val stripStep = with(density) { GOO_STRIP_STEP_DP.dp.toPx() }

    GooCanvas(color, stripStep, modifier) { center ->
        GooField(
            center = center,
            radius = radius,
            halo = halo,
            flatY = flatYPx,
            flatHalo = halo,
        )
    }
}

@Composable
private fun GooCanvas(
    color: Color,
    stripStep: Float,
    modifier: Modifier,
    center: (Size) -> Offset = { size ->
        Offset(size.width * UiValues.MIDPOINT, size.height * UiValues.MIDPOINT)
    },
    field: (Offset) -> GooField,
) {
    val agslShader =
        remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) createGooShader() else null
        }
    val agslPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Canvas(modifier) {
        val gooField = field(center(size))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslShader != null) {
            drawShader(agslShader, agslPaint, gooField, color)
        } else {
            drawStrips(gooField, stripStep, color.toArgb())
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
    field.applyTo(shader)

    // Plain half4, not a layout(color) uniform: setColorUniform rejects it, components go as floats.
    shader.setFloatUniform("uColor", color.red, color.green, color.blue, color.alpha)

    paint.shader = shader
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun GooField.applyTo(shader: RuntimeShader) {
    shader.setFloatUniform("uCenter", center.x, center.y)
    shader.setFloatUniform("uRadius", radius)
    shader.setFloatUniform("uHalo", halo)
    shader.setFloatUniform("uFlatY", flatY)
    shader.setFloatUniform("uFlatHalo", flatHalo)
    shader.setFloatUniform("uEdgeX", edgeX)
    shader.setFloatUniform("uEdgeDir", edgeDirection)
    shader.setFloatUniform("uSpread", spread)
    shader.setFloatUniform("uDepth", depth)
}

// Legacy path: the same summed field, quantised into horizontal strips (T and w are constant across a strip)
// and thresholded once for the whole layer by the alpha contrast matrix.
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

        drawFlatField(native, field, colorArgb)
        drawCircleField(native, field.center, field.radius, field.halo, colorArgb)
        drawEdgeStrips(native, field, step, colorArgb)

        native.restoreToCount(layer)
    }
}

private fun DrawScope.drawFlatField(
    native: Canvas,
    field: GooField,
    colorArgb: Int,
) {
    if (field.flatHalo <= GOO_CLEAR_ALPHA) return

    val top = field.flatY - field.flatHalo * GOO_HALF
    val opaqueAt = field.flatY + field.flatHalo * GOO_HALF
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
                LinearGradient(
                    0f,
                    top,
                    0f,
                    opaqueAt,
                    intArrayOf(
                        withAlpha(colorArgb, GOO_CLEAR_ALPHA),
                        withAlpha(colorArgb, GOO_FULL_ALPHA),
                    ),
                    null,
                    Shader.TileMode.CLAMP,
                )
        }

    native.drawRect(0f, top, size.width, opaqueAt, paint)

    paint.apply {
        shader = null
        color = colorArgb
    }
    native.drawRect(0f, opaqueAt, size.width, size.height, paint)
}

private fun drawCircleField(
    native: Canvas,
    center: Offset,
    radius: Float,
    halo: Float,
    colorArgb: Int,
) {
    val half = halo * GOO_HALF
    val outer = radius + half
    val core = (radius - half).coerceAtLeast(GOO_CLEAR_ALPHA)

    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (outer <= core) {
                color = colorArgb
            } else {
                shader =
                    RadialGradient(
                        center.x,
                        center.y,
                        outer,
                        intArrayOf(colorArgb, colorArgb, withAlpha(colorArgb, GOO_CLEAR_ALPHA)),
                        floatArrayOf(GOO_CLEAR_ALPHA, core / outer, GOO_FULL_ALPHA),
                        Shader.TileMode.CLAMP,
                    )
            }
        }
    native.drawCircle(center.x, center.y, outer, paint)
}

private fun drawEdgeStrips(
    native: Canvas,
    field: GooField,
    step: Float,
    colorArgb: Int,
) {
    if (field.spread <= 0f || step <= 0f) return

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    generateSequence(field.center.y - field.spread) { it + step }
        .takeWhile { it < field.center.y + field.spread }
        .forEach { drawEdgeStrip(native, field, step, colorArgb, paint, it) }
}

private fun drawEdgeStrip(
    native: Canvas,
    field: GooField,
    step: Float,
    colorArgb: Int,
    paint: Paint,
    stripTop: Float,
) {
    val stripCenter = stripTop + step * GOO_HALF
    val unit = (stripCenter - field.center.y) / field.spread
    val bell = GOO_FULL_ALPHA - unit * unit

    if (bell <= GOO_CLEAR_ALPHA) return

    val thickness = field.depth * bell * bell
    val window = maxOf(GOO_WINDOW_FACTOR * thickness, field.halo)

    if (thickness <= GOO_CLEAR_ALPHA || window <= GOO_CLEAR_ALPHA) return

    val reach = thickness + window * GOO_HALF
    val head =
        (GOO_EDGE_FIELD_MIDPOINT + thickness / window).coerceIn(
            GOO_CLEAR_ALPHA,
            GOO_FULL_ALPHA,
        )
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
