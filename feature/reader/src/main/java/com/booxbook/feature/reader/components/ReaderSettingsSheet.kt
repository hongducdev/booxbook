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
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.core.ui.theme.PillShape
import com.booxbook.feature.reader.ReaderPageTurnEffect
import com.booxbook.feature.reader.ReaderTapZoneMode
import com.booxbook.feature.reader.ReaderThemePreset

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

            // 4. Touch interaction & page-turn effects
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
        }
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
