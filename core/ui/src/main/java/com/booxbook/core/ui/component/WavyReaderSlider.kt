package com.booxbook.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Thanh lật trang kiểu Material 3 Expressive: `Slider` chuẩn với track sóng.
 *
 * Material3 không có `WavySlider`. Thanh này giữ gesture, `SliderState` và semantics của
 * `androidx.compose.material3.Slider`, chỉ thay slot `track`.
 *
 * Tham chiếu thị giác: JustForPixel ExpressiveLab `ExpressiveWavySlider`
 * (https://github.com/mohdamaan1/JustForPixel-ExpressiveLab, MIT) — không copy source.
 *
 * [onValueChangeFinished] chỉ nên seek một lần khi nhả tay; [onValueChange] chỉ cập nhật state cục bộ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WavyReaderSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val safeRange = if (valueRange.endInclusive > valueRange.start) {
        valueRange
    } else {
        valueRange.start..(valueRange.start + 0.0001f)
    }
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
        disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledActiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    )

    Slider(
        value = value.coerceIn(safeRange.start, safeRange.endInclusive),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = safeRange,
        enabled = enabled,
        modifier = modifier,
        colors = colors,
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 28.dp)
                    .background(
                        color = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                        shape = RoundedCornerShape(percent = 50),
                    ),
            )
        },
        track = { sliderState ->
            WavySliderTrack(
                sliderState = sliderState,
                enabled = enabled,
                activeColor = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                inactiveColor = if (enabled) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WavySliderTrack(
    sliderState: SliderState,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
) {
    val density = LocalDensity.current
    val amplitudePx = with(density) { if (enabled) 3.dp.toPx() else 0f }
    val wavelengthPx = with(density) { 40.dp.toPx() }
    val strokePx = with(density) { 4.dp.toPx() }
    val thumbGapPx = with(density) { 6.dp.toPx() }
    val fadePx = with(density) { 8.dp.toPx() }

    val transition = rememberInfiniteTransition(label = "wavy-reader-slider")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )

    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val centerY = size.height / 2f
        val fraction = sliderState.coercedValueAsFraction.coerceIn(0f, 1f)
        val activeEnd = (size.width * fraction - thumbGapPx).coerceAtLeast(0f)
        val inactiveStart = (size.width * fraction + thumbGapPx).coerceAtMost(size.width)

        if (activeEnd > 0f && amplitudePx > 0f) {
            drawPath(
                path = wavePath(
                    width = activeEnd,
                    amplitude = amplitudePx,
                    wavelength = wavelengthPx,
                    phase = phase,
                    centerY = centerY,
                    fadePx = fadePx,
                ),
                color = activeColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        } else if (activeEnd > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(activeEnd, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }

        if (inactiveStart < size.width) {
            drawLine(
                color = inactiveColor,
                start = Offset(inactiveStart, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun wavePath(
    width: Float,
    amplitude: Float,
    wavelength: Float,
    phase: Float,
    centerY: Float,
    fadePx: Float,
): Path {
    val path = Path()
    if (width <= 0f) return path
    val step = (wavelength / 8f).coerceAtLeast(1f)
    val steps = (width / step).toInt().coerceAtLeast(2)
    path.moveTo(0f, centerY)
    var previousX = 0f
    var previousY = centerY
    for (i in 1..steps) {
        val x = width * i / steps
        val fade = if (x > width - fadePx && fadePx > 0f) {
            ((width - x) / fadePx).coerceIn(0f, 1f)
        } else {
            1f
        }
        val y = centerY + amplitude * fade * sin((x / wavelength) * 2f * PI.toFloat() + phase)
        val midX = (previousX + x) / 2f
        val midY = (previousY + y) / 2f
        path.quadraticTo(previousX, previousY, midX, midY)
        previousX = x
        previousY = y
    }
    path.lineTo(width, centerY)
    return path
}

@Preview(showBackground = true)
@Composable
private fun WavyReaderSliderPreview() {
    WavyReaderSlider(
        value = 12f,
        valueRange = 0f..40f,
        onValueChange = {},
        onValueChangeFinished = {},
        modifier = Modifier.fillMaxWidth(),
    )
}
