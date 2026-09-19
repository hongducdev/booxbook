package com.booxbook.feature.reader.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.booxbook.core.model.Book
import com.booxbook.core.ui.component.ExpressiveLoadingIndicator
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.feature.reader.ReaderLoadingPhase
import java.io.File

/**
 * Màn chờ có ngữ cảnh cho quá trình mở sách.
 *
 * Thay cho một vòng xoay trống, lớp phủ này cho người đọc biết *cuốn nào* đang được mở và *đang ở bước
 * nào*. Nó được vẽ phủ lên trên canvas đang dựng ngầm, rồi tan dần khi [ReaderLoadingPhase.READY] tới —
 * nhờ vậy trang đọc đầu tiên hiện ra bằng một chuyển tiếp mềm thay vì một cú nháy.
 *
 * Màu chữ được suy ra từ độ sáng của [backgroundColor] chứ không lấy từ `MaterialTheme.colorScheme`:
 * màn đọc có 4 preset nền (Sáng/Giấy ấm/Tối/Đen tuyền) độc lập với theme của app, nên chỉ có cách này
 * mới bảo đảm tương phản đủ ở cả bốn preset.
 */
@Composable
fun ReaderOpeningOverlay(
    visible: Boolean,
    phase: ReaderLoadingPhase,
    book: Book?,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 420)),
        modifier = modifier
    ) {
        val isLightBackground = backgroundColor.luminance() > 0.5f
        val onBackground = if (isLightBackground) Color(0xFF1C1B1F) else Color(0xFFF3EEF7)

        Surface(
            color = backgroundColor,
            modifier = Modifier
                .fillMaxSize()
                // Lớp phủ nằm trên một AndroidView (WebView của Readium). View thật trong cây Compose
                // vẫn nhận được chạm nếu không có gì tiêu thụ sự kiện, nên phải chặn tường minh.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 44.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                OpeningCover(
                    book = book,
                    onBackground = onBackground,
                    cardColor = if (isLightBackground) Color(0x14000000) else Color(0x1AFFFFFF)
                )

                Spacer(modifier = Modifier.height(30.dp))

                Text(
                    text = book?.title ?: "Đang mở sách",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = GoogleSansFlexDisplay,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                val author = book?.author
                if (!author.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = GoogleSansFlex400
                        ),
                        color = onBackground.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                OpeningProgress(
                    phase = phase,
                    // Chỉ báo lấy màu suy từ nền màn đọc thay vì `colorScheme.primary`: 4 preset nền
                    // của reader độc lập với theme app, nên primary của theme có thể chìm trên nền Sáng.
                    indicatorColor = onBackground,
                    labelColor = onBackground.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun OpeningCover(
    book: Book?,
    onBackground: Color,
    cardColor: Color
) {
    val coverPath = book?.coverPath

    Box(
        modifier = Modifier
            .width(126.dp)
            .aspectRatio(0.72f)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor),
        contentAlignment = Alignment.Center
    ) {
        if (!coverPath.isNullOrBlank() && File(coverPath).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(coverPath))
                    .crossfade(true)
                    .build(),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Nhịp thở nhẹ: màn chờ vẫn "sống" trong lúc chưa có bìa để hiện.
            val pulse = rememberInfiniteTransition(label = "reader-opening-pulse")
            val pulseAlpha by pulse.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "reader-opening-pulse-alpha"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = null,
                    tint = onBackground.copy(alpha = pulseAlpha * 0.8f),
                    modifier = Modifier.size(34.dp)
                )
                if (book != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = book.format.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = GoogleSansFlex600,
                            fontWeight = FontWeight.Bold
                        ),
                        color = onBackground.copy(alpha = pulseAlpha * 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OpeningProgress(
    phase: ReaderLoadingPhase,
    indicatorColor: Color,
    labelColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Material 3 "Loading indicator": chờ ngắn, không đo được tiến trình -> dùng chỉ báo morph
        // hình. Không vẽ thanh determinate từ các mốc bước, vì spec yêu cầu determinate phải đo đúng
        // tiến trình; phần "đang ở bước nào" do dòng mô tả bên dưới đảm nhiệm.
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(48.dp),
            color = indicatorColor
        )

        Spacer(modifier = Modifier.height(14.dp))

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(160))
            },
            label = "reader-opening-phase"
        ) { currentPhase ->
            Text(
                text = currentPhase.statusLabel(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansFlex400,
                    fontSize = 13.sp
                ),
                color = labelColor
            )
        }
    }
}

/** Câu mô tả từng bước của quá trình mở sách. */
private fun ReaderLoadingPhase.statusLabel(): String = when (this) {
    ReaderLoadingPhase.LOADING_BOOK -> "Đang đọc thông tin sách…"
    ReaderLoadingPhase.OPENING_PUBLICATION -> "Đang mở nội dung sách…"
    ReaderLoadingPhase.BUILDING_CANVAS -> "Đang dựng trang đọc…"
    ReaderLoadingPhase.READY -> "Sẵn sàng"
}
