@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.booxbook.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Thanh tiến trình đọc theo Material 3 Expressive "Progress indicators" (determinate linear wavy).
 *
 * Spec: <https://m3.material.io/components/progress-indicators/overview>
 *
 * Spec yêu cầu hai điều mà bản cũ trong app vi phạm:
 * 1. **Determinate thì phải đo đúng tiến trình.** Thanh này chỉ dùng cho giá trị phần trăm có thật
 *    (tiến độ đọc của sách). Chờ mà không biết tiến trình thì dùng
 *    [ExpressiveLoadingIndicator], không dùng thanh này.
 * 2. **Một tiến trình phải dùng đúng một cấu hình trong toàn app.** Tất cả chỗ % đọc đi qua
 *    composable này: stroke ~4dp, container ~10dp, active `primary`, track `secondaryContainer`,
 *    stop indicator 4dp, dạng sóng của `LinearWavyProgressIndicator`.
 *    `waveSpeed = 0.dp` để giữ hình sóng nhưng **không** chạy animation vô hạn trên lưới sách.
 */
@Composable
fun ReadingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    LinearWavyProgressIndicator(
        progress = { if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = color,
        trackColor = trackColor,
        stopSize = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
        // Static wave: same shape on every card, no per-item infinite animation.
        waveSpeed = 0.dp,
    )
}

/**
 * Vòng tiến trình determinate dùng chung (nhập nhiều sách). Token hình dạng nằm ở đây, không ở
 * call site — cùng luật "một tiến trình, một cấu hình" với [ReadingProgressBar].
 */
@Composable
fun ReadingProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
) {
    CircularWavyProgressIndicator(
        progress = { if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f) },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@Preview(showBackground = true)
@Composable
private fun ReadingProgressBarPreview() {
    ReadingProgressBar(progress = 0.42f)
}
