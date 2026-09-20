package com.booxbook.feature.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.booxbook.core.engine.model.ReadingFrame
import com.booxbook.core.engine.model.ReadingFrameColor
import com.booxbook.core.engine.model.ReadingFrameStyle

/**
 * Đường viền bao quanh vùng đọc.
 *
 * **Viền không chiếm chỗ.** Nó được vẽ *lên trên* vùng đọc và không tham gia bố cục, nên hai cài đặt lề và
 * viền độc lập với nhau: muốn chữ không chạm viền thì tăng lề, còn viền vẫn nằm nguyên chỗ đã đặt. Nếu cho
 * viền chiếm chỗ thì mỗi lần đổi độ dày viền, Readium lại dàn trang lại và tổng số trang đổi theo — một hiệu
 * ứng phụ mà người dùng không hề yêu cầu.
 *
 * Vẽ bằng `Canvas` + `Stroke(pathEffect)` chứ không dùng `Modifier.border`: `border` không có kiểu nét đứt.
 */
@Composable
fun ReadingFrameLayer(
    frame: ReadingFrame,
    isDarkReader: Boolean,
    modifier: Modifier = Modifier
) {
    if (!frame.enabled) return

    val color = frameColor(frame.color, isDarkReader)

    Canvas(modifier) {
        drawFrameBorder(frame = frame, color = color)
    }
}

/**
 * Màu viền theo bảng chọn.
 *
 * `AUTO` dùng alpha thay vì màu đặc: một đường kẻ đặc quanh trang đọc trông như khung ảnh, còn alpha vừa
 * đủ để mắt nhận ra ranh giới mà không tranh chấp với chữ.
 */
@Composable
fun frameColor(choice: ReadingFrameColor, isDarkReader: Boolean): Color = when (choice) {
    ReadingFrameColor.AUTO -> if (isDarkReader) Color(0x99E6E0E9) else Color(0x881D1B20)
    ReadingFrameColor.ACCENT -> MaterialTheme.colorScheme.primary
    ReadingFrameColor.WARM -> Color(0xFF8D6E63)
}

private fun DrawScope.drawFrameBorder(frame: ReadingFrame, color: Color) {
    val stroke = frame.thicknessDp.dp.toPx().coerceAtLeast(1f)
    val half = stroke / 2f
    val inset = frame.insetDp.dp.toPx()

    // Viền được vẽ *quanh* mép vùng đọc, lệch vào trong theo `insetDp`. Kẹp lại để inset lớn hơn nửa bề
    // rộng vùng đọc không tạo ra hình chữ nhật lộn ngược.
    val left = inset + half
    val top = inset + half
    val right = (size.width - inset - half).coerceAtLeast(left)
    val bottom = (size.height - inset - half).coerceAtLeast(top)

    if (right <= left || bottom <= top) return

    val cornerPx = frame.cornerRadiusDp.dp.toPx().coerceAtMost((right - left) / 2f)

    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(
            width = stroke,
            cap = if (frame.style == ReadingFrameStyle.DOTTED) StrokeCap.Round else StrokeCap.Butt,
            pathEffect = pathEffectFor(frame.style, stroke)
        )
    )
}

/**
 * Kiểu nét.
 *
 * Độ dài đoạn gạch tính theo bề dày nét: nét dày mà gạch ngắn thì các đoạn dính vào nhau thành một đường
 * liền, còn nét mảnh mà gạch dài thì trông như nét liền bị đứt quãng.
 */
private fun pathEffectFor(style: ReadingFrameStyle, strokeWidth: Float): PathEffect? = when (style) {
    ReadingFrameStyle.SOLID -> null
    ReadingFrameStyle.DASHED -> PathEffect.dashPathEffect(
        floatArrayOf(strokeWidth * 4f, strokeWidth * 2.5f)
    )

    ReadingFrameStyle.DOTTED -> PathEffect.dashPathEffect(
        // Gạch dài bằng 0 + cap tròn = chấm tròn đều nhau.
        floatArrayOf(0.01f, strokeWidth * 2.2f)
    )
}
