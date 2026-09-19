@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.booxbook.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.delay

/**
 * Chỉ báo tải theo Material 3 "Loading indicator" (M3 Expressive).
 *
 * Spec: <https://m3.material.io/components/loading-indicator/overview>
 *
 * Delegate mỏng sang `androidx.compose.material3.LoadingIndicator` (Material3 1.5.0-alpha10).
 * Call site không cần `@OptIn(ExperimentalMaterial3ExpressiveApi)`.
 *
 * Vì sao dùng component này thay cho vòng xoay tròn cũ:
 * - Spec chỉ định rõ: chờ **ngắn (200 ms – 5 s)** và **không đo được tiến trình** thì dùng loading
 *   indicator; nó "được khuyến nghị thay thế hầu hết chỗ dùng indeterminate circular progress".
 * - Chờ **dài hơn 5 s** thì phải dùng progress indicator (xem [ReadingProgressBar]), và không được
 *   chuyển từ loading indicator sang determinate — muốn chuyển thì bắt đầu từ indeterminate progress.
 * - Container chỉ dùng khi chỉ báo nằm **trên nội dung khác** ([ExpressiveContainedLoadingIndicator]);
 *   nằm trực tiếp trên một surface thì dùng bản không container.
 *
 * Kích thước mặc định 48dp (spec: 24–240dp). Muốn nhỏ hơn, truyền `Modifier.size(...)`.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(
        modifier = modifier,
        color = color,
    )
}

/**
 * Bản có container của [ExpressiveLoadingIndicator].
 *
 * Dùng khi chỉ báo đặt **trên nội dung khác** để nó nổi bật hơn; spec quy định lúc đó active indicator
 * đổi từ `primary` sang `onPrimaryContainer` trên nền `primaryContainer`.
 */
@Composable
fun ExpressiveContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerShape: Shape = CircleShape,
) {
    ContainedLoadingIndicator(
        modifier = modifier,
        containerColor = containerColor,
        indicatorColor = indicatorColor,
        containerShape = containerShape,
    )
}

/**
 * [ExpressiveLoadingIndicator] chỉ xuất hiện sau [delayMillis].
 *
 * Spec có bảng chọn chỉ báo theo thời gian chờ: dưới 200 ms thì **không hiện chỉ báo gì** (hiện nội
 * dung ngay), 200 ms – 5 s thì dùng loading indicator. Với dữ liệu đọc từ Room (thường xong trong vài
 * chục ms), hiện chỉ báo ngay sẽ chỉ tạo ra một cái nháy một khung hình; composable này hoãn việc hiện
 * chỉ báo cho tới khi thời gian chờ đã thật sự đáng để báo.
 *
 * Chỉ cần đặt nó vào nhánh "đang tải": nếu việc tải xong trước [delayMillis], composable bị rời khỏi
 * composition và chưa từng vẽ gì.
 */
@Composable
fun DelayedLoadingIndicator(
    modifier: Modifier = Modifier,
    delayMillis: Long = InstantWaitThresholdMillis,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 150)),
        exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        modifier = modifier,
    ) {
        ExpressiveLoadingIndicator(color = color)
    }
}

/** Ngưỡng "chờ tức thời" của Material 3: dưới ngưỡng này không nên hiện chỉ báo nào. */
const val InstantWaitThresholdMillis = 200L
