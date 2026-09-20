package com.booxbook.feature.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BrightnessLow
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booxbook.core.model.bookends.BookendsBarAnchor
import com.booxbook.core.model.bookends.BookendsBarLayer
import com.booxbook.core.model.bookends.BookendsBarStyle
import com.booxbook.core.model.bookends.BookendsBarType
import com.booxbook.core.model.bookends.BookendsChapterTicks
import com.booxbook.core.model.bookends.BookendsChunk
import com.booxbook.core.model.bookends.BookendsFormatter
import com.booxbook.core.model.bookends.BookendsGroup
import com.booxbook.core.model.bookends.BookendsIcon
import com.booxbook.core.model.bookends.BookendsLine
import com.booxbook.core.model.bookends.BookendsPosition
import com.booxbook.core.model.bookends.BookendsPreset
import com.booxbook.core.model.bookends.BookendsRender
import com.booxbook.core.model.bookends.BookendsRenderOptions
import com.booxbook.core.model.bookends.BookendsSnapshot
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import java.util.Locale

/**
 * Lớp overlay của Bookends: sáu vùng chữ neo quanh trang đọc, cộng các thanh tiến độ full-width.
 *
 * Đặt **trên** canvas đọc và **dưới** thanh công cụ. Overlay không gắn `pointerInput` nào, nên mọi cú chạm
 * vẫn rơi xuống vùng lật trang bên dưới — người đọc không phải chạm hai lần vì lỡ trúng một dòng chữ.
 */
@Composable
fun BookendsOverlay(
    preset: BookendsPreset,
    snapshot: BookendsSnapshot,
    visible: Boolean,
    contentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    // Múi giờ và ngôn ngữ lấy từ máy; giữ lại qua recomposition để không dựng lại bảng token mỗi khung hình.
    val options = remember { BookendsRenderOptions() }

    Box(modifier) {
        preset.barLayers.forEach { layer ->
            BookendsBarLayer(
                layer = layer,
                snapshot = snapshot,
                contentColor = contentColor,
                trackColor = trackColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = preset.marginLeftDp.dp,
                    top = preset.marginTopDp.dp,
                    end = preset.marginRightDp.dp,
                    bottom = preset.marginBottomDp.dp
                )
        ) {
            PositionRow(
                preset = preset,
                snapshot = snapshot,
                options = options,
                positions = TOP_ROW,
                contentColor = contentColor,
                trackColor = trackColor
            )
            Spacer(Modifier.weight(1f))
            PositionRow(
                preset = preset,
                snapshot = snapshot,
                options = options,
                positions = BOTTOM_ROW,
                contentColor = contentColor,
                trackColor = trackColor
            )
        }
    }
}

@Composable
private fun BoxScope.BookendsBarLayer(
    layer: BookendsBarLayer,
    snapshot: BookendsSnapshot,
    contentColor: Color,
    trackColor: Color
) {
    val inset = layer.insetDp.dp
    val (alignment, sizing) = when (layer.anchor) {
        BookendsBarAnchor.TOP ->
            Alignment.TopCenter to Modifier.fillMaxWidth().height(layer.thicknessDp.dp).offset(y = inset)

        BookendsBarAnchor.BOTTOM ->
            Alignment.BottomCenter to Modifier.fillMaxWidth().height(layer.thicknessDp.dp).offset(y = -inset)

        BookendsBarAnchor.LEFT ->
            Alignment.CenterStart to Modifier.fillMaxHeight().width(layer.thicknessDp.dp).offset(x = inset)

        BookendsBarAnchor.RIGHT ->
            Alignment.CenterEnd to Modifier.fillMaxHeight().width(layer.thicknessDp.dp).offset(x = -inset)
    }

    BookendsBarLayerView(
        layer = layer,
        progress = snapshot.bookProgression.toFloat(),
        tickPositions = when (layer.ticks) {
            BookendsChapterTicks.OFF -> emptyList()
            BookendsChapterTicks.TOP_LEVEL -> snapshot.chapters.ticksUpTo(1)
            BookendsChapterTicks.TOP_TWO_LEVELS -> snapshot.chapters.ticksUpTo(2)
        },
        color = contentColor,
        trackColor = trackColor,
        modifier = Modifier.align(alignment).then(sizing)
    )
}

@Composable
private fun PositionRow(
    preset: BookendsPreset,
    snapshot: BookendsSnapshot,
    options: BookendsRenderOptions,
    positions: List<BookendsPosition>,
    contentColor: Color,
    trackColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        // `truncationGapDp` là khoảng cách tối thiểu giữa hai vùng cùng hàng.
        //
        // Bản gốc dùng nó cho cơ chế cắt chữ khi các vùng chồng nhau; ở đây ba vùng chia đều bề rộng nên
        // **không thể chồng nhau** về mặt cấu trúc, và trường này trở thành khoảng đệm người dùng chỉnh được
        // khi ba vùng trông quá sát nhau.
        horizontalArrangement = Arrangement.spacedBy(preset.truncationGapDp.dp)
    ) {
        positions.forEach { position ->
            PositionCell(
                group = preset.groupAt(position),
                lines = preset.linesAt(position),
                snapshot = snapshot,
                options = options,
                fontScale = preset.fontScale,
                horizontalAlignment = when (position) {
                    BookendsPosition.TOP_LEFT, BookendsPosition.BOTTOM_LEFT -> Alignment.Start
                    BookendsPosition.TOP_CENTER, BookendsPosition.BOTTOM_CENTER -> Alignment.CenterHorizontally
                    BookendsPosition.TOP_RIGHT, BookendsPosition.BOTTOM_RIGHT -> Alignment.End
                },
                contentColor = contentColor,
                trackColor = trackColor
            )
        }
    }
}

@Composable
private fun RowScope.PositionCell(
    group: BookendsGroup?,
    lines: List<BookendsLine>,
    snapshot: BookendsSnapshot,
    options: BookendsRenderOptions,
    fontScale: Float,
    horizontalAlignment: Alignment.Horizontal,
    contentColor: Color,
    trackColor: Color
) {
    Column(
        modifier = Modifier
            .weight(1f)
            // Tầng thứ hai của hệ định vị: lề riêng của vùng, cộng thêm vào lề chung của preset.
            .padding(
                start = (group?.extraMarginLeftDp ?: 0f).dp,
                top = (group?.extraMarginTopDp ?: 0f).dp,
                end = (group?.extraMarginRightDp ?: 0f).dp,
                bottom = (group?.extraMarginBottomDp ?: 0f).dp
            ),
        horizontalAlignment = horizontalAlignment
    ) {
        lines.forEach { line ->
            // Lọc theo trang và auto-hide đều dẫn tới "không vẽ gì": dòng chỉ hiện khi có nội dung thật.
            if (!BookendsFormatter.isVisibleOnPage(line, snapshot.pageNum)) return@forEach

            val render = BookendsFormatter.format(line, snapshot, options)
            if (render.isBlank) return@forEach

            BookendsLineView(
                line = line,
                render = render,
                snapshot = snapshot,
                fontScale = fontScale,
                contentColor = contentColor,
                trackColor = trackColor,
                // Tầng thứ ba: nudge theo dòng. `offset` không tham gia bố cục nên dòng này dịch chuyển
                // mà không đẩy các dòng còn lại — đúng nghĩa "tinh chỉnh từng pixel".
                modifier = Modifier.offset(x = line.nudgeXDp.dp, y = line.nudgeYDp.dp)
            )
        }
    }
}

/**
 * Vẽ một dòng: chữ (kèm định dạng nội dòng), icon thiết bị, thanh `%bar` và khoảng co giãn `%spacer`.
 *
 * Các đoạn chữ liền nhau được gộp thành **một** `AnnotatedString` để cả dòng ngắt/xuống hàng như một câu,
 * thay vì vỡ thành nhiều `Text` riêng lẻ. Chỉ đoạn có giới hạn bề rộng `{N}` mới buộc phải tách ra, vì nó
 * cần `widthIn` riêng.
 */
@Composable
private fun BookendsLineView(
    line: BookendsLine,
    render: BookendsRender,
    snapshot: BookendsSnapshot,
    fontScale: Float,
    contentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val fontSizeSp = (line.fontSizeSp * fontScale).coerceAtLeast(MIN_FONT_SIZE_SP)
    val textSize = fontSizeSp.sp
    val glyphSize = (fontSizeSp * 1.15f).dp
    val barThickness = (fontSizeSp * 0.6f).dp
    val elements = remember(render, line) { buildElements(render.chunks) }

    // `%spacer` và `%bar` cùng tranh phần bề rộng còn lại, nhưng một dòng chỉ co giãn được ở một chỗ —
    // giống bản gốc: có thanh thì khoảng co giãn bị bỏ.
    val effective = if (elements.any { it is LineElement.BarElement }) {
        elements.filterNot { it is LineElement.Flex }
    } else {
        elements
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        effective.forEach { element ->
            when (element) {
                is LineElement.TextRun -> Text(
                    text = element.text,
                    color = contentColor,
                    fontFamily = if (line.isBold) GoogleSansFlex600 else GoogleSansFlex400,
                    fontStyle = if (line.isItalic) FontStyle.Italic else FontStyle.Normal,
                    fontSize = textSize,
                    lineHeight = textSize * 1.25f,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = element.maxWidthDp
                        ?.let { Modifier.widthIn(max = it.dp) }
                        ?: Modifier
                )

                is LineElement.IconElement -> Icon(
                    imageVector = element.icon.vector(),
                    contentDescription = element.description.ifEmpty { element.icon.label },
                    tint = contentColor,
                    modifier = Modifier.size(glyphSize)
                )

                is LineElement.BarElement -> BookendsInlineBar(
                    progress = barProgress(element.type, snapshot),
                    style = element.style,
                    color = contentColor,
                    trackColor = trackColor,
                    thickness = barThickness,
                    modifier = element.maxWidthDp
                        ?.let { Modifier.widthIn(max = it.dp) }
                        ?: Modifier.weight(1f)
                )

                LineElement.Flex -> Spacer(Modifier.weight(1f))
            }
        }
    }
}

private sealed interface LineElement {
    data class TextRun(val text: AnnotatedString, val maxWidthDp: Int?) : LineElement
    data class IconElement(val icon: BookendsIcon, val description: String) : LineElement
    data class BarElement(
        val type: BookendsBarType,
        val style: BookendsBarStyle,
        val maxWidthDp: Int?
    ) : LineElement

    data object Flex : LineElement
}

/**
 * Gộp các đoạn chữ liền nhau thành một `AnnotatedString`.
 *
 * Hai đoạn chỉ được gộp khi cùng giới hạn bề rộng — đoạn có `{N}` phải đứng riêng để cắt bằng dấu ba chấm
 * tại đúng chỗ người dùng yêu cầu.
 */
private fun buildElements(chunks: List<BookendsChunk>): List<LineElement> {
    val elements = mutableListOf<LineElement>()
    val pending = mutableListOf<BookendsChunk.Text>()
    var pendingWidth: Int? = null

    fun flushText() {
        if (pending.isEmpty()) return
        val text = buildAnnotatedString {
            pending.forEach { chunk ->
                val value = if (chunk.uppercase) chunk.text.uppercase(Locale.getDefault()) else chunk.text
                withStyle(
                    SpanStyle(
                        fontWeight = if (chunk.bold) FontWeight.Bold else null,
                        fontStyle = if (chunk.italic) FontStyle.Italic else null
                    )
                ) {
                    append(value)
                }
            }
        }
        elements += LineElement.TextRun(text, pendingWidth)
        pending.clear()
    }

    chunks.forEach { chunk ->
        when (chunk) {
            is BookendsChunk.Text -> {
                if (pending.isNotEmpty() && pendingWidth != chunk.maxWidthDp) flushText()
                pendingWidth = chunk.maxWidthDp
                pending += chunk
            }

            is BookendsChunk.Icon -> {
                flushText()
                elements += LineElement.IconElement(chunk.icon, chunk.description)
            }

            is BookendsChunk.ProgressBar -> {
                flushText()
                elements += LineElement.BarElement(chunk.type, chunk.style, chunk.maxWidthDp)
            }

            BookendsChunk.Spacer -> {
                flushText()
                elements += LineElement.Flex
            }
        }
    }
    flushText()

    return elements
}

/** `%bar` đo tiến độ chương hay tiến độ sách, tuỳ cấu hình của dòng. */
private fun barProgress(type: BookendsBarType, snapshot: BookendsSnapshot): Float = when (type) {
    BookendsBarType.CHAPTER -> snapshot.chapter()?.progression?.toFloat() ?: 0f
    BookendsBarType.BOOK, BookendsBarType.BOOK_PLUS, BookendsBarType.BOOK_PLUS_PLUS ->
        snapshot.bookProgression.toFloat()
}.coerceIn(0f, 1f)

private fun BookendsIcon.vector(): ImageVector = when (this) {
    BookendsIcon.BATTERY -> Icons.Rounded.BatteryFull
    BookendsIcon.BATTERY_CHARGING -> Icons.Rounded.BatteryChargingFull
    BookendsIcon.WIFI -> Icons.Rounded.Wifi
    BookendsIcon.WIFI_OFF -> Icons.Rounded.WifiOff
    BookendsIcon.LIGHT -> Icons.Rounded.Lightbulb
    BookendsIcon.LIGHT_OFF -> Icons.Rounded.BrightnessLow
    BookendsIcon.NIGHT_MODE -> Icons.Rounded.DarkMode
    BookendsIcon.INVERT -> Icons.Rounded.SwapVert
}

private val TOP_ROW = listOf(
    BookendsPosition.TOP_LEFT,
    BookendsPosition.TOP_CENTER,
    BookendsPosition.TOP_RIGHT
)

private val BOTTOM_ROW = listOf(
    BookendsPosition.BOTTOM_LEFT,
    BookendsPosition.BOTTOM_CENTER,
    BookendsPosition.BOTTOM_RIGHT
)

private const val MIN_FONT_SIZE_SP = 8f
