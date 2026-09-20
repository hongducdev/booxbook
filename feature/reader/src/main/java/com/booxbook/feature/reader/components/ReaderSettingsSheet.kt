package com.booxbook.feature.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.ReadingFrame
import com.booxbook.core.engine.model.ReadingFrameColor
import com.booxbook.core.engine.model.ReadingFrameStyle
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.core.ui.theme.PillShape
import com.booxbook.feature.reader.ReaderPageTurnEffect
import com.booxbook.feature.reader.ReaderTapZoneMode
import com.booxbook.feature.reader.ReaderThemePreset
import com.booxbook.feature.reader.preferences.ReaderPreferencesManager

import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    preferences: ReaderPreferences,
    themePreset: ReaderThemePreset,
    tapZoneMode: ReaderTapZoneMode,
    pageTurnEffect: ReaderPageTurnEffect,
    hapticsEnabled: Boolean,
    onFontSizeDelta: (Double) -> Unit,
    onFontFamilySelected: (String?) -> Unit,
    onThemePresetSelected: (ReaderThemePreset) -> Unit,
    onTapZoneModeSelected: (ReaderTapZoneMode) -> Unit,
    onPageTurnEffectSelected: (ReaderPageTurnEffect) -> Unit,
    onHapticsToggled: (Boolean) -> Unit,
    onPreviewTapZones: () -> Unit,
    onOpenBookends: () -> Unit,
    onMarginChange: (ReaderPreferencesManager.MarginSide, Float) -> Unit,
    onResetMargins: () -> Unit,
    onFrameChanged: (ReadingFrame) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Cài đặt hiển thị",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansFlexDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Font Size Control
            Text(
                text = "Cỡ chữ",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledIconButton(
                    onClick = { onFontSizeDelta(-0.1) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Giảm cỡ chữ")
                }

                Text(
                    text = "${(preferences.fontSize * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansFlex600,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                FilledIconButton(
                    onClick = { onFontSizeDelta(0.1) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Tăng cỡ chữ")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Reading Theme Colors
            Text(
                text = "Màu nền đọc sách",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeColorPill(
                    name = "Sáng",
                    bgColor = Color(0xFFFEF7FF),
                    textColor = Color(0xFF1D1B20),
                    selected = themePreset == ReaderThemePreset.LIGHT,
                    onClick = { onThemePresetSelected(ReaderThemePreset.LIGHT) },
                    modifier = Modifier.weight(1f)
                )

                ThemeColorPill(
                    name = "Giấy ấm",
                    bgColor = Color(0xFFFBF0D9),
                    textColor = Color(0xFF5F4B32),
                    selected = themePreset == ReaderThemePreset.SEPIA,
                    onClick = { onThemePresetSelected(ReaderThemePreset.SEPIA) },
                    modifier = Modifier.weight(1f)
                )

                ThemeColorPill(
                    name = "Tối",
                    bgColor = Color(0xFF141218),
                    textColor = Color(0xFFE6E0E9),
                    selected = themePreset == ReaderThemePreset.DARK,
                    onClick = { onThemePresetSelected(ReaderThemePreset.DARK) },
                    modifier = Modifier.weight(1f)
                )

                ThemeColorPill(
                    name = "AMOLED",
                    bgColor = Color.Black,
                    textColor = Color.White,
                    selected = themePreset == ReaderThemePreset.AMOLED,
                    onClick = { onThemePresetSelected(ReaderThemePreset.AMOLED) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Font Family Selection
            Text(
                text = "Kiểu chữ",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OptionPill(
                    title = "Google Sans",
                    selected = preferences.fontFamily == null,
                    onClick = { onFontFamilySelected(null) },
                    modifier = Modifier.weight(1f)
                )
                OptionPill(
                    title = "Serif",
                    selected = preferences.fontFamily == "serif",
                    onClick = { onFontFamilySelected("serif") },
                    modifier = Modifier.weight(1f)
                )
                OptionPill(
                    title = "Monospace",
                    selected = preferences.fontFamily == "monospace",
                    onClick = { onFontFamilySelected("monospace") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // 4. Lề vùng đọc & viền khung
            ReadingMarginsAndFrameSection(
                preferences = preferences,
                onMarginChange = onMarginChange,
                onResetMargins = onResetMargins,
                onFrameChanged = onFrameChanged
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // 5. Touch interaction & page-turn effects
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chạm & lật trang",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = GoogleSansFlex600,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            SectionLabel("Vùng chạm")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReaderTapZoneMode.entries.forEach { mode ->
                    OptionPill(
                        title = mode.displayName,
                        selected = tapZoneMode == mode,
                        onClick = { onTapZoneModeSelected(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            SectionLabel("Hiệu ứng lật trang")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReaderPageTurnEffect.entries.forEach { effect ->
                    OptionPill(
                        title = effect.displayName,
                        selected = pageTurnEffect == effect,
                        onClick = { onPageTurnEffectSelected(effect) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Rung nhẹ khi lật trang",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansFlex400),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = hapticsEnabled,
                    onCheckedChange = onHapticsToggled
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onPreviewTapZones,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Xem vùng chạm",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = GoogleSansFlex600,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Bookends nằm trong cùng sheet này thay vì một mục riêng ở tab Cài đặt: nó là cài đặt *của màn
            // đọc*, và người đọc chỉ nhận ra mình muốn đổi nó khi đang đọc dở một cuốn.
            TextButton(
                onClick = onOpenBookends,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bookends · lớp thông tin trên trang đọc",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = GoogleSansFlex600,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Lề trang và viền khung.
 *
 * **Trên và dưới riêng, trái và phải chung một mức.** Chữ chừa hai bên không đều trông như lỗi, còn trên/dưới
 * thì thật sự cần khác nhau: trên phải né thanh trạng thái và overlay, dưới phải né thanh công cụ.
 *
 * Lề làm bằng padding Compose chứ không dùng `pageMargins` của Readium — Readium chỉ có **một** hệ số cho cả
 * bốn phía nên không đủ. Đổi lại, `EpubReaderContainer` phải báo Readium dàn lại mỗi khi vùng đọc đổi kích
 * thước, nếu không pager sẽ giữ bề rộng trang cũ.
 *
 * Viền khung **không chiếm chỗ** — nó chỉ vẽ lên trên vùng đọc, nên hai nhóm cài đặt độc lập.
 */
@Composable
private fun ReadingMarginsAndFrameSection(
    preferences: ReaderPreferences,
    onMarginChange: (ReaderPreferencesManager.MarginSide, Float) -> Unit,
    onResetMargins: () -> Unit,
    onFrameChanged: (ReadingFrame) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.CropFree,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Lề & viền trang",
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = GoogleSansFlex600,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onResetMargins) { Text("Đặt lại") }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Lề chừa quanh trang sách, áp cho cả EPUB và truyện tranh CBZ. Trái và phải dùng chung một mức.",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    MarginSlider("Trên", preferences.marginTopDp) { onMarginChange(ReaderPreferencesManager.MarginSide.TOP, it) }
    MarginSlider("Dưới", preferences.marginBottomDp) { onMarginChange(ReaderPreferencesManager.MarginSide.BOTTOM, it) }
    MarginSlider("Trái & phải", preferences.marginHorizontalDp) {
        onMarginChange(ReaderPreferencesManager.MarginSide.HORIZONTAL, it)
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Viền khung",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Vẽ một đường viền quanh vùng đọc, không chiếm chỗ của chữ",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = preferences.frame.enabled,
            onCheckedChange = { enabled -> onFrameChanged(preferences.frame.copy(enabled = enabled)) }
        )
    }

    if (preferences.frame.enabled) {
        val frame = preferences.frame
        Spacer(modifier = Modifier.height(8.dp))

        SectionLabel("Kiểu nét")
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReadingFrameStyle.entries.forEach { candidate ->
                OptionPill(
                    title = candidate.label,
                    selected = frame.style == candidate,
                    onClick = { onFrameChanged(frame.copy(style = candidate)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        SectionLabel("Màu viền")
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReadingFrameColor.entries.forEach { candidate ->
                OptionPill(
                    title = candidate.label,
                    selected = frame.color == candidate,
                    onClick = { onFrameChanged(frame.copy(color = candidate)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        FrameSlider(
            label = "Độ dày",
            value = frame.thicknessDp,
            range = ReadingFrame.MIN_THICKNESS_DP..ReadingFrame.MAX_THICKNESS_DP
        ) { onFrameChanged(frame.copy(thicknessDp = it)) }
        FrameSlider(
            label = "Bo góc",
            value = frame.cornerRadiusDp,
            range = 0f..ReadingFrame.MAX_CORNER_RADIUS_DP
        ) { onFrameChanged(frame.copy(cornerRadiusDp = it)) }
        FrameSlider(
            label = "Khoảng cách vào trong",
            value = frame.insetDp,
            range = ReadingFrame.MIN_INSET_DP..ReadingFrame.MAX_INSET_DP
        ) { onFrameChanged(frame.copy(insetDp = it)) }
    }
}

@Composable
private fun MarginSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Lề $label",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${value.roundToInt()} dp",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex600),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..ReaderPreferences.MAX_MARGIN_DP
        )
    }
}

@Composable
private fun FrameSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex400),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${value.roundToInt()} dp",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex600),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun SectionLabel(text: String) {
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
private fun ThemeColorPill(
    name: String,
    bgColor: Color,
    textColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textColor
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun OptionPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = PillShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .height(42.dp)
            .clip(PillShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
