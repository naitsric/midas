package com.midas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.midas.ui.theme.LocalMidasColors

@Composable
fun MidasMonogram(size: Dp = 56.dp, cornerRadius: Dp = 14.dp) {
    val colors = LocalMidasColors.current
    val accent = colors.primaryAccent
    val bg = if (colors.isDark) accent.copy(alpha = 0.08f) else accent.copy(alpha = 0.10f)
    val borderColor = accent.copy(alpha = 0.27f)
    Box(
        modifier = Modifier
            .size(size)
            .background(bg, RoundedCornerShape(cornerRadius))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.57f)) {
            val w = this.size.width
            val h = this.size.height
            fun px(x: Float) = x / 32f * w
            fun py(y: Float) = y / 32f * h
            val path = Path().apply {
                moveTo(px(4f), py(26f))
                lineTo(px(4f), py(6f))
                lineTo(px(12f), py(18f))
                lineTo(px(16f), py(12f))
                lineTo(px(20f), py(18f))
                lineTo(px(28f), py(6f))
                lineTo(px(28f), py(26f))
            }
            drawPath(
                path = path,
                brush = SolidColor(accent),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(color = accent, radius = 2.2f, center = Offset(px(16f), py(12f)))
        }
    }
}

/** Decorative dark backdrop: radial green glow on top, faint 32dp grid that fades out. */
@Composable
fun MidasBackdrop() {
    val colors = LocalMidasColors.current
    val density = LocalDensity.current
    val accent = colors.primaryAccent
    val glowAlpha = if (colors.isDark) 0.10f else 0.06f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .background(
                Brush.radialGradient(
                    0f to accent.copy(alpha = glowAlpha),
                    1f to Color.Transparent,
                ),
            ),
    )

    val lineColor = colors.cardBorder.copy(alpha = 0.10f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = with(density) { 32.dp.toPx() }
        val cutoff = size.height * 0.55f
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, cutoff),
                strokeWidth = 1f,
            )
            x += step
        }
        var y = 0f
        while (y < cutoff) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
    }
}

@Composable
fun ArrowForwardGlyph(tint: Color, size: Dp = 16.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun px(x: Float) = x / 24f * w
        fun py(y: Float) = y / 24f * h
        drawLine(
            color = tint,
            start = Offset(px(5f), py(12f)),
            end = Offset(px(19f), py(12f)),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round,
        )
        val head = Path().apply {
            moveTo(px(13f), py(6f))
            lineTo(px(19f), py(12f))
            lineTo(px(13f), py(18f))
        }
        drawPath(
            head,
            color = tint,
            style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun ArrowBackGlyph(tint: Color, size: Dp = 16.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun px(x: Float) = x / 24f * w
        fun py(y: Float) = y / 24f * h
        drawLine(
            color = tint,
            start = Offset(px(19f), py(12f)),
            end = Offset(px(5f), py(12f)),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round,
        )
        val head = Path().apply {
            moveTo(px(11f), py(6f))
            lineTo(px(5f), py(12f))
            lineTo(px(11f), py(18f))
        }
        drawPath(
            head,
            color = tint,
            style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
