package com.booxbook.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booxbook.core.ui.component.ExpressivePillButton
import com.booxbook.core.ui.theme.BentoCardShape
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.core.ui.theme.PillShape
import com.booxbook.feature.reader.bookends.BookendsViewModel
import com.booxbook.feature.reader.components.BookendsSettingsSheet
import com.booxbook.feature.statistics.components.HeatmapTileGeometry

private val DAILY_GOAL_OPTIONS = listOf(15, 30, 45, 60)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    bookendsViewModel: BookendsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookendsState by bookendsViewModel.uiState.collectAsStateWithLifecycle()
    var showBookends by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = "Cài đặt",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = GoogleSansFlexDisplay,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Cấu hình ứng dụng và tùy chỉnh trải nghiệm đọc",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = GoogleSansFlex600
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- SECTION 1: CÀI ĐẶT THỐNG KÊ ---
            item {
                SectionHeader(
                    icon = Icons.Rounded.Insights,
                    title = "Thống kê & Thói quen đọc sách"
                )
            }

            // Card 1: Hình dạng ô Heatmap
            item {
                Card(
                    shape = BentoCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Hình dạng ô Heatmap",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = GoogleSansFlex600,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HeatmapTileGeometry.entries.forEach { geom ->
                                val isSelected = geom == uiState.heatmapGeometry
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setHeatmapGeometry(geom) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            val previewShape: Shape = when (geom) {
                                                HeatmapTileGeometry.SQUIRCLE -> RoundedCornerShape(4.dp)
                                                HeatmapTileGeometry.PEBBLE, HeatmapTileGeometry.GLOW -> CircleShape
                                                HeatmapTileGeometry.DIAMOND -> RoundedCornerShape(2.dp)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .then(
                                                        if (geom == HeatmapTileGeometry.DIAMOND) Modifier.rotate(45f) else Modifier
                                                    )
                                                    .clip(previewShape)
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                            )

                                            Column {
                                                Text(
                                                    text = geom.label,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontFamily = GoogleSansFlex600,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    ),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = geom.description,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = GoogleSansFlex400,
                                                        fontSize = 12.sp
                                                    ),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { viewModel.setHeatmapGeometry(geom) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 2: Mục tiêu đọc sách hàng ngày
            item {
                Card(
                    shape = BentoCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Mục tiêu đọc mỗi ngày",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = GoogleSansFlex600,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DAILY_GOAL_OPTIONS.forEach { goal ->
                                val isSelected = goal == uiState.dailyGoalMinutes
                                Surface(
                                    shape = PillShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setDailyGoalMinutes(goal) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = "${goal}p",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = GoogleSansFlex600,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- SECTION 2: TRẢI NGHIỆM ĐỌC SÁCH ---
            item {
                SectionHeader(
                    icon = Icons.Rounded.AutoStories,
                    title = "Trải nghiệm đọc sách"
                )
            }

            // Card 3: Vùng chạm & Hiệu ứng lật trang
            item {
                Card(
                    shape = BentoCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // Vùng chạm
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Vùng chạm lật trang",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = GoogleSansFlex600,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modes = listOf(
                                "KINDLE" to "Kindle",
                                "EDGES" to "Hai viền",
                                "MENU_ONLY" to "Chỉ menu"
                            )
                            modes.forEach { (key, label) ->
                                val isSelected = key == uiState.tapZoneMode
                                Surface(
                                    shape = PillShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setTapZoneMode(key) }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = GoogleSansFlex600,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Hiệu ứng lật trang
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Swipe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Hiệu ứng lật trang",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = GoogleSansFlex600,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val effects = listOf(
                                "SLIDE" to "Trượt trang",
                                "FLIP" to "Lật trang",
                                "NONE" to "Chuyển ngay"
                            )
                            effects.forEach { (key, label) ->
                                val isSelected = key == uiState.pageTurnEffect
                                Surface(
                                    shape = PillShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setPageTurnEffect(key) }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = GoogleSansFlex600,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Phản hồi rung
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Vibration,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Rung khi lật trang",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = GoogleSansFlex600,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Switch(
                                checked = uiState.hapticsEnabled,
                                onCheckedChange = viewModel::setHapticsEnabled
                            )
                        }
                    }
                }
            }

            // --- SECTION 3: THÔNG TIN ỨNG DỤNG ---
            item {
                SectionHeader(
                    icon = Icons.Rounded.Info,
                    title = "Thông tin ứng dụng"
                )
            }

            item {
                Card(
                    shape = BentoCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "BooxBook v1.0.0",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GoogleSansFlexDisplay,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Trình đọc sách điện tử Android Native tối ưu cho màn hình E-ink & AMOLED, thiết kế theo phong cách Material 3 Expressive (lấy cảm hứng từ JustForPixel-ExpressiveLab).",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = GoogleSansFlex400,
                                lineHeight = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- SECTION 4: BOOKENDS ---
            item {
                SectionHeader(
                    icon = Icons.Rounded.Layers,
                    title = "Bookends — lớp thông tin trên trang đọc"
                )
            }

            item {
                Card(
                    shape = BentoCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = if (bookendsState.settings.enabled) "Đang bật" else "Đang tắt",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GoogleSansFlexDisplay,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (bookendsState.settings.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Preset, dòng chữ, thanh tiến độ và quy tắc theo định dạng. Mở một cuốn sách " +
                                "để xem trước trên chính nội dung của nó; ở đây vẫn sửa được cấu hình.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = GoogleSansFlex400,
                                lineHeight = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ExpressivePillButton(onClick = { showBookends = true }) {
                            Text("Mở cấu hình Bookends")
                        }
                    }
                }
            }
        }
    }

    if (showBookends) {
        BookendsSettingsSheet(
            settings = bookendsState.settings,
            // Không có sách nào đang mở ở tab Cài đặt; sheet tự hiện lời nhắc thay vì bản xem trước.
            snapshot = bookendsState.snapshot,
            onCreatePreset = bookendsViewModel::createPresetFromActive,
            onPresetUpdated = { bookendsViewModel.upsertPreset(it) },
            onPresetSelected = bookendsViewModel::setActivePreset,
            onPresetReset = bookendsViewModel::resetPreset,
            onPresetDelete = bookendsViewModel::deletePreset,
            onEnabledChange = bookendsViewModel::setEnabled,
            onAutoRuleSet = bookendsViewModel::setAutoRule,
            onAutoRuleRemoved = bookendsViewModel::removeAutoRule,
            onDismiss = { showBookends = false }
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = GoogleSansFlex600,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
