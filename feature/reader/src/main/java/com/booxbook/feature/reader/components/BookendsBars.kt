package com.booxbook.feature.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.booxbook.core.model.bookends.BookendsBarAnchor
import com.booxbook.core.model.bookends.BookendsBarFill
import com.booxbook.core.model.bookends.BookendsBarLayer
import com.booxbook.core.model.bookends.BookendsBarStyle
import kotlin.math.PI
import kotlin.math.sin

/**
 * Vẽ thanh tiến độ theo phong cách của Bookends.
 *
 * Dùng `Canvas` tự vẽ thay vì `LinearProgressIndicator` của Material 3 vì Bookends cần năm kiểu dáng khác
 * hẳn nhau (kể cả Metro chia ô và Hollow rỗng) trên cùng một thang đo, còn component của Material chỉ có
 * một hình dáng với hai trạng thái determinate/indeterminate.
 */
@Composable
fun BookendsInlineBar(
    progress: Float,
    style: BookendsBarStyle,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 6.dp
) {
    Canvas(modifier.fillMaxWidth().height(thickness)) {
        drawStyledBar(
            style = style,
            fraction = progress,
            color = color,
            trackColor = trackColor,
            vertical = false,
            reversed = false
        )
    }
}

/**
 * Một thanh tiến độ full-width neo vào cạnh màn đọc.
 *
 * Khác `%bar` nội dòng: thanh này không chiếm chỗ của chữ mà nằm *sau* chúng, và chạy được cả theo chiều
 * dọc ở cạnh trái/phải.
 *
 * @param ticks vị trí bắt đầu của các mục lục kèm cấp, để vẽ mốc chương. Rỗng nghĩa là không vẽ mốc.
 */
@Composable
fun BookendsBarLayerView(
    layer: BookendsBarLayer,
    progress: Float,
    tickPositions: List<Pair<Double, Int>> = emptyList(),
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val vertical = layer.isVertical
    val reversed = when (layer.fill) {
        BookendsBarFill.RIGHT_TO_LEFT,
        BookendsBarFill.BOTTOM_TO_TOP -> true

        else -> false
    }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawStyledBar(
                style = layer.style,
                fraction = progress,
                color = color,
                trackColor = trackColor,
                vertical = vertical,
                reversed = reversed
            )

            tickPositions.forEach { (position, depth) ->
                drawTick(
                    style = layer.style,
                    position = position,
                    depth = depth,
                    color = color,
                    vertical = vertical
                )
            }
        }
    }
}

/**
 * Vẽ một thanh đã điền [fraction].
 *
 * Gộp cả hai hướng vào một hàm: mọi hình dáng đều suy ra từ (chiều dài, bề dày) nên chỉ cần biết trục nào
 * là trục dài, rồi hoán vị toạ độ. Viết hai bản riêng cho ngang và dọc sẽ nhân đôi số nhánh cần kiểm thử.
 */
private fun DrawScope.drawStyledBar(
    style: BookendsBarStyle,
    fraction: Float,
    color: Color,
    trackColor: Color,
    vertical: Boolean,
    reversed: Boolean
) {
    val totalLength = if (vertical) size.height else size.width
    val breadth = if (vertical) size.width else size.height
    if (totalLength <= 0f || breadth <= 0f) return

    val filled = totalLength * fraction.coerceIn(0f, 1f)
    val start = if (reversed) totalLength - filled else 0f
    val radius = CornerRadius(breadth / 2f, breadth / 2f)

    fun place(offsetLength: Float, length: Float): Pair<Offset, Size> = if (vertical) {
        Offset(0f, offsetLength) to Size(breadth, length)
    } else {
        Offset(offsetLength, 0f) to Size(length, breadth)
    }

    fun track() {
        val (topLeft, boxSize) = place(0f, totalLength)
        drawRect(trackColor, topLeft, boxSize)
    }

    when (style) {
        BookendsBarStyle.SOLID -> {
            track()
            if (filled > 0f) {
                val (topLeft, boxSize) = place(start, filled)
                drawRect(color, topLeft, boxSize)
            }
        }

        BookendsBarStyle.BORDER -> {
            track()
            val stroke = (breadth * 0.14f).coerceAtLeast(1f)
            val inner = (filled - stroke * 2f).coerceAtLeast(0f)
            if (inner > 0f) {
                val (topLeft, boxSize) = place(start + stroke, inner)
                drawRect(color, topLeft, boxSize)
            }
            drawRect(color.copy(alpha = 0.55f), Offset.Zero, size, style = Stroke(stroke))
        }

        BookendsBarStyle.ROUND -> {
            val (trackTopLeft, trackSize) = place(0f, totalLength)
            drawRoundRect(trackColor, trackTopLeft, trackSize, radius)
            if (filled > 0f) {
                val (topLeft, boxSize) = place(start, filled)
                drawRoundRect(color, topLeft, boxSize, radius)
            }
        }

        BookendsBarStyle.HOLLOW -> {
            val stroke = (breadth * 0.16f).coerceAtLeast(1f)
            val (trackTopLeft, trackSize) = place(0f, totalLength)
            drawRoundRect(
                color = color.copy(alpha = 0.35f),
                topLeft = trackTopLeft,
                size = trackSize,
                cornerRadius = radius,
                style = Stroke(stroke)
            )
            if (filled > 0f) {
                val coreBreadth = (breadth * 0.28f).coerceAtLeast(1f)
                val coreOffset = (breadth - coreBreadth) / 2f
                val coreRadius = CornerRadius(coreBreadth / 2f, coreBreadth / 2f)
                val (rawTopLeft, rawSize) = place(start, filled)
                val topLeft = if (vertical) {
                    Offset(rawTopLeft.x + coreOffset, rawTopLeft.y)
                } else {
                    Offset(rawTopLeft.x, rawTopLeft.y + coreOffset)
                }
                val boxSize = if (vertical) Size(coreBreadth, rawSize.height) else Size(rawSize.width, coreBreadth)
                drawRoundRect(color, topLeft, boxSize, coreRadius)
            }
        }

        BookendsBarStyle.WAVE -> {
            drawWaveBar(
                fraction = fraction,
                color = color,
                trackColor = trackColor,
                vertical = vertical,
                reversed = reversed,
                totalLength = totalLength,
                breadth = breadth,
                start = start,
                filled = filled
            )
        }

        BookendsBarStyle.METRO -> {
            val gap = (totalLength * SEGMENT_GAP_RATIO).coerceAtLeast(1f)
            val segmentLength = (totalLength - gap * (METRO_SEGMENTS - 1)) / METRO_SEGMENTS
            if (segmentLength <= 0f) {
                track()
                return
            }

            val filledSegments = Math.round(fraction.coerceIn(0f, 1f) * METRO_SEGMENTS)
            repeat(METRO_SEGMENTS) { index ->
                val (topLeft, boxSize) = place(index * (segmentLength + gap), segmentLength)
                // Thanh chạy ngược thì phần đã đọc nằm ở cuối trục, không phải ở đầu trục.
                val isFilled = if (reversed) {
                    index >= METRO_SEGMENTS - filledSegments
                } else {
                    index < filledSegments
                }
                drawRect(if (isFilled) color else trackColor, topLeft, boxSize)
            }
        }
    }
}

/**
 * Thanh lượn sóng: biên độ bằng nửa bề dày, bước sóng gấp ba bề dày.
 *
 * Vẽ bằng `Path` cộng `Stroke` chứ không tô kín: một dải tô kín theo hình sin sẽ dày gấp đôi ở đỉnh sóng và
 * mỏng ở đáy, trông như lỗi vẽ chứ không như thiết kế.
 */
private fun DrawScope.drawWaveBar(
    fraction: Float,
    color: Color,
    trackColor: Color,
    vertical: Boolean,
    reversed: Boolean,
    totalLength: Float,
    breadth: Float,
    start: Float,
    filled: Float
) {
    val strokeWidth = (breadth * 0.28f).coerceAtLeast(1f)
    val amplitude = (breadth / 2f - strokeWidth).coerceAtLeast(0.5f)
    val wavelength = (breadth * 3f).coerceAtLeast(8f)
    val centreAcross = breadth / 2f

    fun wavePath(offsetLength: Float, length: Float): Path {
        val path = Path()
        var travelled = 0f
        while (travelled <= length) {
            val offset = sin(travelled / wavelength * 2f * PI.toFloat()) * amplitude
            val along = offsetLength + travelled
            val x = if (vertical) centreAcross + offset else along
            val y = if (vertical) along else centreAcross + offset
            if (travelled == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            travelled += 1f
        }
        return path
    }

    drawPath(
        path = wavePath(0f, totalLength),
        color = trackColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    if (filled > 0f) {
        drawPath(
            path = wavePath(start, filled),
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

/** Vạch mốc chương: càng sâu càng mảnh, để mắt phân biệt được cấp mà không cần màu khác. */
private fun DrawScope.drawTick(
    style: BookendsBarStyle,
    position: Double,
    depth: Int,
    color: Color,
    vertical: Boolean
) {
    val totalLength = if (vertical) size.height else size.width
    val breadth = if (vertical) size.width else size.height
    if (totalLength <= 0f || breadth <= 0f) return

    val tickLength = (breadth * (0.65f / (1f + (depth - 1) * 0.5f))).coerceAtLeast(1f)
    val offset = (position.coerceIn(0.0, 1.0) * totalLength).toFloat()
    val start = if (vertical) Offset(0f, offset) else Offset(offset, 0f)
    val end = if (vertical) Offset(tickLength, offset) else Offset(offset, tickLength)

    // Metro đã có khe giữa các ô, còn Wave thì vạch thẳng sẽ cắt ngang đỉnh sóng — cả hai đều rối hơn là rõ.
    if (style == BookendsBarStyle.METRO || style == BookendsBarStyle.WAVE) return
    drawLine(
        color = color.copy(alpha = 0.9f),
        start = start,
        end = end,
        strokeWidth = 1.5f
    )
}

private const val METRO_SEGMENTS = 24
private const val SEGMENT_GAP_RATIO = 0.004f
