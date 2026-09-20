package com.booxbook.feature.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booxbook.core.model.bookends.BookendsBarStyle
import com.booxbook.core.model.bookends.BookendsBarType
import com.booxbook.core.model.bookends.BookendsGroup
import com.booxbook.core.model.bookends.BookendsLine
import com.booxbook.core.model.bookends.BookendsPageFilter
import com.booxbook.core.model.bookends.BookendsPosition
import com.booxbook.core.model.bookends.BookendsPreset
import com.booxbook.core.model.bookends.BookendsSettings
import com.booxbook.core.model.bookends.BookendsSnapshot
import com.booxbook.core.model.bookends.BookendsTextStyle
import com.booxbook.core.model.bookends.BookendsTokenCatalogue
import com.booxbook.core.model.bookends.BookendsTokenInfo
import com.booxbook.core.ui.component.ExpressiveFilterChip
import com.booxbook.core.ui.theme.AmoledBackground
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import kotlin.math.roundToInt

/**
 * Màn cấu hình Bookends.
 *
 * Bản xem trước ở đầu sheet chạy trên **chính cuốn sách đang đọc**, không phải dữ liệu giả: người dùng
 * thấy ngay `%chap_title` của mình dài bao nhiêu và thanh `%bar` chiếm chỗ thế nào, thay vì phải lưu, thoát
 * sheet rồi nhìn lại trang đọc để đoán.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookendsSettingsSheet(
    settings: BookendsSettings,
    snapshot: BookendsSnapshot?,
    onCreatePreset: (String) -> Unit,
    onPresetUpdated: (BookendsPreset) -> Unit,
    onPresetSelected: (String) -> Unit,
    onPresetReset: (String) -> Unit,
    onPresetDelete: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onAutoRuleSet: (String, String?) -> Unit,
    onAutoRuleRemoved: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activePreset = settings.presetById(settings.activePresetId) ?: settings.presets.firstOrNull()

    var lineEditorTarget by remember { mutableStateOf<LineEditorTarget?>(null) }
    var marginEditorTarget by remember { mutableStateOf<BookendsPosition?>(null) }
    var ruleEditorVisible by remember { mutableStateOf(false) }
    var newPresetDialogVisible by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            SheetHeader()
            Spacer(Modifier.height(12.dp))

            PreviewCard(preset = activePreset, snapshot = snapshot)
            Spacer(Modifier.height(16.dp))

            SwitchRow(
                title = "Bật overlay",
                subtitle = "Hiện thông tin phủ trên trang đọc",
                checked = settings.enabled,
                onCheckedChange = onEnabledChange
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            if (activePreset != null) {
                SliderRow(
                    label = "Tỉ lệ cỡ chữ",
                    value = activePreset.fontScale,
                    valueRange = 0.5f..2.5f,
                    display = "${(activePreset.fontScale * 100).roundToInt()}%",
                    onValueChange = { onPresetUpdated(activePreset.copy(fontScale = it)) }
                )
                MarginSliders(
                    preset = activePreset,
                    onPresetUpdated = onPresetUpdated
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Preset")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.presets.forEach { preset ->
                    ExpressiveFilterChip(
                        selected = preset.id == settings.activePresetId,
                        onClick = { onPresetSelected(preset.id) },
                        label = preset.name
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { newPresetDialogVisible = true }) { Text("Tạo preset mới") }
                activePreset?.let { preset ->
                    TextButton(onClick = { onPresetReset(preset.id) }) {
                        Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Khôi phục gốc")
                    }
                    TextButton(onClick = { onPresetDelete(preset.id) }) { Text("Xoá") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Dòng theo vị trí")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Mỗi vùng neo được nhiều dòng. Mở trình soạn thảo để chèn token từ bảng chọn, đổi " +
                    "kiểu chữ, cỡ chữ, lọc trang chẵn/lẻ và tinh chỉnh vị trí.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (activePreset == null) {
                Text("Chưa có preset nào để sửa.", style = MaterialTheme.typography.bodyMedium)
            } else {
                BookendsPosition.entries.forEach { position ->
                    PositionSection(
                        position = position,
                        group = activePreset.groupAt(position),
                        lines = activePreset.linesAt(position),
                        onLinesChanged = { updated ->
                            onPresetUpdated(activePreset.withLines(position, updated))
                        },
                        onEditLine = { index ->
                            lineEditorTarget = LineEditorTarget(position, index, activePreset.linesAt(position)[index])
                        },
                        onAddLine = {
                            // Mở thẳng trình soạn thảo thay vì chèn một dòng rỗng: dòng rỗng bị chính
                            // `withLines` lọc bỏ (đó là cách xoá dòng), nên "Thêm dòng" sẽ trông như nút hỏng.
                            lineEditorTarget = LineEditorTarget(position, NEW_LINE_INDEX, BookendsLine(format = ""))
                        },
                        onEditMargins = { marginEditorTarget = position }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Tự chọn preset theo định dạng")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Truyện tranh CBZ thường cần khung hình sạch; thêm quy tắc để ẩn hẳn overlay cho " +
                    "định dạng đó.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            settings.autoRules.forEach { rule ->
                val presetName = rule.presetId?.let { id -> settings.presetById(id)?.name } ?: "Ẩn overlay"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ".${rule.extension}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = GoogleSansFlex600,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.width(72.dp)
                    )
                    Text(
                        text = presetName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansFlex400),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onAutoRuleRemoved(rule.extension) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Xoá quy tắc")
                    }
                }
            }

            TextButton(onClick = { ruleEditorVisible = true }) { Text("Thêm quy tắc") }
        }
    }

    lineEditorTarget?.let { target ->
        LineEditorDialog(
            line = target.line,
            onConfirm = { updated ->
                val preset = activePreset
                if (preset != null) {
                    val lines = preset.linesAt(target.position).toMutableList()
                    if (target.index in lines.indices) {
                        lines[target.index] = updated
                    } else {
                        lines += updated
                    }
                    onPresetUpdated(preset.withLines(target.position, lines))
                }
                lineEditorTarget = null
            },
            onDismiss = { lineEditorTarget = null }
        )
    }

    marginEditorTarget?.let { position ->
        val group = activePreset?.groupAt(position)
        PositionMarginsDialog(
            position = position,
            topDp = group?.extraMarginTopDp ?: 0f,
            bottomDp = group?.extraMarginBottomDp ?: 0f,
            leftDp = group?.extraMarginLeftDp ?: 0f,
            rightDp = group?.extraMarginRightDp ?: 0f,
            onConfirm = { top, bottom, left, right ->
                activePreset?.let {
                    onPresetUpdated(it.withMargins(position, top, bottom, left, right))
                }
                marginEditorTarget = null
            },
            onDismiss = { marginEditorTarget = null }
        )
    }

    if (ruleEditorVisible) {
        AutoRuleDialog(
            presets = settings.presets,
            onConfirm = { extension, presetId ->
                onAutoRuleSet(extension, presetId)
                ruleEditorVisible = false
            },
            onDismiss = { ruleEditorVisible = false }
        )
    }

    if (newPresetDialogVisible) {
        NewPresetDialog(
            onConfirm = { name ->
                onCreatePreset(name)
                newPresetDialogVisible = false
            },
            onDismiss = { newPresetDialogVisible = false }
        )
    }
}

@Composable
private fun SheetHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Bookends",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = GoogleSansFlexDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Lớp thông tin phủ trên trang đọc, lấy cảm hứng từ bookends.koplugin của AndyHazz.",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PreviewCard(preset: BookendsPreset?, snapshot: BookendsSnapshot?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AmoledBackground)
    ) {
        if (preset == null || snapshot == null) {
            Text(
                text = "Mở một cuốn sách để xem trước trên chính nội dung của nó.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                color = Color(0xFF9E9A9F),
                modifier = Modifier.align(Alignment.Center).padding(16.dp)
            )
        } else {
            BookendsOverlay(
                preset = preset,
                snapshot = snapshot,
                visible = true,
                contentColor = Color(0xFFE6E0E9),
                trackColor = Color(0x33E6E0E9),
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontFamily = GoogleSansFlex600,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansFlex400)
            )
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansFlex600)
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

/**
 * Bốn thanh trượt lề, mỗi thanh một phía.
 *
 * Tách riêng từng phía thay vì một thanh "lề" chung vì trang đọc cần chừa chỗ khác nhau ở trên (thanh trạng
 * thái) và dưới (thanh điều hướng) — gộp lại sẽ không đặt được bố cục nào vừa mắt.
 */
@Composable
private fun MarginSliders(preset: BookendsPreset, onPresetUpdated: (BookendsPreset) -> Unit) {
    SliderRow(
        label = "Lề trên",
        value = preset.marginTopDp,
        valueRange = 0f..64f,
        display = "${preset.marginTopDp.roundToInt()} dp",
        onValueChange = { onPresetUpdated(preset.copy(marginTopDp = it)) }
    )
    SliderRow(
        label = "Lề dưới",
        value = preset.marginBottomDp,
        valueRange = 0f..64f,
        display = "${preset.marginBottomDp.roundToInt()} dp",
        onValueChange = { onPresetUpdated(preset.copy(marginBottomDp = it)) }
    )
    SliderRow(
        label = "Lề trái",
        value = preset.marginLeftDp,
        valueRange = 0f..64f,
        display = "${preset.marginLeftDp.roundToInt()} dp",
        onValueChange = { onPresetUpdated(preset.copy(marginLeftDp = it)) }
    )
    SliderRow(
        label = "Lề phải",
        value = preset.marginRightDp,
        valueRange = 0f..64f,
        display = "${preset.marginRightDp.roundToInt()} dp",
        onValueChange = { onPresetUpdated(preset.copy(marginRightDp = it)) }
    )
}

@Composable
private fun PositionSection(
    position: BookendsPosition,
    group: BookendsGroup?,
    lines: List<BookendsLine>,
    onLinesChanged: (List<BookendsLine>) -> Unit,
    onEditLine: (Int) -> Unit,
    onAddLine: () -> Unit,
    onEditMargins: () -> Unit
) {
    val hasExtraMargins = group != null && (
        group.extraMarginTopDp != 0f || group.extraMarginBottomDp != 0f ||
            group.extraMarginLeftDp != 0f || group.extraMarginRightDp != 0f
        )

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = position.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEditMargins) {
                Icon(
                    imageVector = Icons.Rounded.Straighten,
                    contentDescription = "Lề riêng cho vùng ${position.label}",
                    tint = if (hasExtraMargins) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        lines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = line.format,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                )

                // Thứ tự dòng quyết định thứ tự vẽ, nên phải đổi được ngay tại chỗ thay vì xoá rồi thêm lại.
                IconButton(
                    enabled = index > 0,
                    onClick = { onLinesChanged(lines.swapped(index, index - 1)) }
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Đưa dòng lên", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    enabled = index < lines.lastIndex,
                    onClick = { onLinesChanged(lines.swapped(index, index + 1)) }
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Đưa dòng xuống", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onEditLine(index) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Sửa dòng", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onLinesChanged(lines.filterIndexed { i, _ -> i != index }) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Xoá dòng", modifier = Modifier.size(18.dp))
                }
            }
        }

        TextButton(onClick = onAddLine) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Thêm dòng")
        }
    }
}

/** Đổi chỗ hai dòng. Sao chép danh sách để trạng thái cũ không bị sửa tại chỗ. */
private fun List<BookendsLine>.swapped(from: Int, to: Int): List<BookendsLine> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply {
        val moved = removeAt(from)
        add(to, moved)
    }
}

@Composable
private fun LineEditorDialog(
    line: BookendsLine,
    onConfirm: (BookendsLine) -> Unit,
    onDismiss: () -> Unit
) {
    // Dùng `TextFieldValue` chứ không dùng `String`: bảng chọn token cần biết con trỏ đang ở đâu để chèn
    // đúng chỗ. Với `String` thì chỉ có thể nối vào cuối, và người dùng phải tự cắt dán lại.
    var formatField by remember { mutableStateOf(TextFieldValue(line.format)) }
    var style by remember { mutableStateOf(line.style) }
    var uppercase by remember { mutableStateOf(line.uppercase) }
    var fontSize by remember { mutableStateOf(line.fontSizeSp) }
    var pageFilter by remember { mutableStateOf(line.pageFilter) }
    var barType by remember { mutableStateOf(line.bar.type) }
    var barStyle by remember { mutableStateOf(line.bar.style) }
    var nudgeX by remember { mutableStateOf(line.nudgeXDp) }
    var nudgeY by remember { mutableStateOf(line.nudgeYDp) }
    var tokenPickerVisible by remember { mutableStateOf(false) }

    val format = formatField.text

    fun insertAtCursor(snippet: String) {
        val start = formatField.selection.min.coerceIn(0, format.length)
        val end = formatField.selection.max.coerceIn(0, format.length)
        val next = format.replaceRange(start, end, snippet)
        formatField = TextFieldValue(next, TextRange(start + snippet.length))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sửa dòng") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = formatField,
                    onValueChange = { formatField = it },
                    label = { Text("Chuỗi định dạng") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { tokenPickerVisible = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chèn token")
                }
                Spacer(Modifier.height(8.dp))

                SectionTitle("Kiểu chữ")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BookendsTextStyle.entries.forEach { candidate ->
                        ExpressiveFilterChip(
                            selected = style == candidate,
                            onClick = { style = candidate },
                            label = candidate.displayLabel()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                SectionTitle("Trang hiển thị")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BookendsPageFilter.entries.forEach { candidate ->
                        ExpressiveFilterChip(
                            selected = pageFilter == candidate,
                            onClick = { pageFilter = candidate },
                            label = candidate.label
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                SliderRow(
                    label = "Cỡ chữ",
                    value = fontSize,
                    valueRange = 8f..28f,
                    display = "${fontSize.roundToInt()} sp",
                    onValueChange = { fontSize = it }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Viết hoa toàn bộ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansFlex400),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = uppercase, onCheckedChange = { uppercase = it })
                }

                SectionTitle("Tinh chỉnh vị trí")
                SliderRow(
                    label = "Dịch ngang",
                    value = nudgeX,
                    valueRange = -32f..32f,
                    display = "${nudgeX.roundToInt()} dp",
                    onValueChange = { nudgeX = it }
                )
                SliderRow(
                    label = "Dịch dọc",
                    value = nudgeY,
                    valueRange = -32f..32f,
                    display = "${nudgeY.roundToInt()} dp",
                    onValueChange = { nudgeY = it }
                )

                if (format.contains("%bar")) {
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("Thanh tiến độ")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BookendsBarType.entries.forEach { candidate ->
                            ExpressiveFilterChip(
                                selected = barType == candidate,
                                onClick = { barType = candidate },
                                label = candidate.label
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BookendsBarStyle.entries.forEach { candidate ->
                            ExpressiveFilterChip(
                                selected = barStyle == candidate,
                                onClick = { barStyle = candidate },
                                label = candidate.label
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = format.isNotBlank(),
                onClick = {
                    onConfirm(
                        line.copy(
                            format = format,
                            style = style,
                            uppercase = uppercase,
                            fontSizeSp = fontSize,
                            pageFilter = pageFilter,
                            bar = line.bar.copy(type = barType, style = barStyle),
                            nudgeXDp = nudgeX,
                            nudgeYDp = nudgeY
                        )
                    )
                }
            ) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )

    if (tokenPickerVisible) {
        TokenPickerDialog(
            onPick = { snippet ->
                insertAtCursor(snippet)
                tokenPickerVisible = false
            },
            onDismiss = { tokenPickerVisible = false }
        )
    }
}

/**
 * Bảng chọn token: nhóm theo chủ đề, kèm mô tả và ví dụ.
 *
 * Cú pháp điều kiện và định dạng nội dòng được đặt lên đầu vì chúng khó nhớ nhất và cũng dễ gõ sai nhất.
 */
@Composable
private fun TokenPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chèn token") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                item { SectionTitle("Cú pháp") }
                items(BookendsTokenCatalogue.snippets) { info ->
                    TokenPickerRow(info = info, onClick = { onPick(info.name) })
                }

                BookendsTokenCatalogue.groups.forEach { group ->
                    item { Spacer(Modifier.height(10.dp)); SectionTitle(group.title) }
                    items(group.tokens) { info ->
                        TokenPickerRow(info = info, onClick = { onPick(info.syntax) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } }
    )
}

@Composable
private fun TokenPickerRow(info: BookendsTokenInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = info.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = info.example,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = info.description,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Lề riêng cho một vùng neo, cộng thêm vào lề chung của preset.
 *
 * Cần tầng này vì nhu cầu chừa chỗ khác nhau ở từng vùng — khối chữ ở góc trên trái đè lên dòng đầu của
 * trang nếu dùng chung lề trên với các vùng khác.
 */
@Composable
private fun PositionMarginsDialog(
    position: BookendsPosition,
    topDp: Float,
    bottomDp: Float,
    leftDp: Float,
    rightDp: Float,
    onConfirm: (Float, Float, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var top by remember { mutableStateOf(topDp) }
    var bottom by remember { mutableStateOf(bottomDp) }
    var left by remember { mutableStateOf(leftDp) }
    var right by remember { mutableStateOf(rightDp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lề riêng · ${position.label}") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = "Cộng thêm vào lề chung của preset, chỉ áp cho vùng này.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SliderRow("Trên", top, -24f..120f, "${top.roundToInt()} dp") { top = it }
                SliderRow("Dưới", bottom, -24f..120f, "${bottom.roundToInt()} dp") { bottom = it }
                SliderRow("Trái", left, -24f..120f, "${left.roundToInt()} dp") { left = it }
                SliderRow("Phải", right, -24f..120f, "${right.roundToInt()} dp") { right = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(top, bottom, left, right) }) { Text("Lưu") } },
        dismissButton = {
            Row {
                TextButton(onClick = { top = 0f; bottom = 0f; left = 0f; right = 0f }) { Text("Đặt lại") }
                TextButton(onClick = onDismiss) { Text("Huỷ") }
            }
        }
    )
}

@Composable
private fun AutoRuleDialog(
    presets: List<BookendsPreset>,
    onConfirm: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var extension by remember { mutableStateOf("") }
    var presetId by remember { mutableStateOf<String?>(HIDDEN_RULE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quy tắc theo định dạng") },
        text = {
            Column {
                OutlinedTextField(
                    value = extension,
                    onValueChange = { extension = it },
                    label = { Text("Đuôi tệp (epub, cbz…)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpressiveFilterChip(
                        selected = presetId == HIDDEN_RULE,
                        onClick = { presetId = HIDDEN_RULE },
                        label = "Ẩn overlay"
                    )
                    presets.forEach { preset ->
                        ExpressiveFilterChip(
                            selected = presetId == preset.id,
                            onClick = { presetId = preset.id },
                            label = preset.name
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(extension, presetId?.takeIf { it != HIDDEN_RULE }) }) {
                Text("Lưu")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}

@Composable
private fun NewPresetDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preset mới") },
        text = {
            Column {
                Text(
                    text = "Preset mới sao chép bố cục và các dòng của preset đang dùng.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên preset") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.ifBlank { "Preset của tôi" }) }) { Text("Tạo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ") } }
    )
}

private data class LineEditorTarget(
    val position: BookendsPosition,
    val index: Int,
    val line: BookendsLine
)

/** Chỉ số của dòng chưa tồn tại; trình soạn thảo hiểu đây là "thêm mới" chứ không phải "thay thế". */
private const val NEW_LINE_INDEX = -1

private fun BookendsTextStyle.displayLabel(): String = when (this) {
    BookendsTextStyle.REGULAR -> "Thường"
    BookendsTextStyle.BOLD -> "Đậm"
    BookendsTextStyle.ITALIC -> "Nghiêng"
    BookendsTextStyle.BOLD_ITALIC -> "Đậm nghiêng"
}

/** Giá trị gửi đi cho lựa chọn "ẩn overlay" trong hộp thoại quy tắc. */
private const val HIDDEN_RULE = "__hidden__"
