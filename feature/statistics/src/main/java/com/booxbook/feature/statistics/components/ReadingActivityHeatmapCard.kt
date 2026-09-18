package com.booxbook.feature.statistics.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booxbook.core.model.HeatmapDayStat
import com.booxbook.core.ui.animation.SpringPhysics
import com.booxbook.core.ui.theme.BentoCardShape
import com.booxbook.core.ui.theme.GoogleSansFlex400
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.core.ui.theme.PillShape
import java.time.LocalDate

enum class HeatmapTileGeometry(val label: String, val description: String) {
    PEBBLE("Viên sỏi", "Hình tròn mềm mại tối giản"),
    SQUIRCLE("Bo tròn", "Hình vuông bo cong nhẹ 5dp"),
    DIAMOND("Hình thoi", "Hình thoi xoay góc 45°"),
    GLOW("Phát sáng", "Hiệu ứng viền sáng rạng rỡ")
}

private val VIETNAMESE_DAY_LABELS = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

@Composable
fun ReadingActivityHeatmapCard(
    heatmapStats: List<HeatmapDayStat>,
    currentStreak: Int,
    geometry: HeatmapTileGeometry = HeatmapTileGeometry.PEBBLE,
    modifier: Modifier = Modifier
) {
    var selectedDay by remember { mutableStateOf<HeatmapDayStat?>(null) }
    val isDark = isSystemInDarkTheme()

    val totalMinutes = heatmapStats.sumOf { it.durationMinutes }
    val activeDays = heatmapStats.count { it.durationMinutes > 0 }

    // Scroll state: Auto-scroll to the latest week on the far right
    val scrollState = rememberScrollState()
    LaunchedEffect(heatmapStats.size) {
        if (heatmapStats.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    // Split into weekly columns of 7 days
    val weeks = remember(heatmapStats) {
        if (heatmapStats.isEmpty()) emptyList()
        else heatmapStats.chunked(7)
    }

    Card(
        shape = BentoCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Tiêu đề, số phút đóng góp, badge chuỗi ngày
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hoạt động đọc sách",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = GoogleSansFlexDisplay,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (totalMinutes > 0) "$totalMinutes phút đọc • $activeDays ngày hoạt động" else "Chưa có hoạt động ghi nhận",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = GoogleSansFlex400
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Badge chuỗi ngày đọc (Streak)
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFFF6D00),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Chuỗi $currentStreak ngày",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = GoogleSansFlex600,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Banner hiển thị thông tin ngày khi người dùng chạm vào ô
            AnimatedVisibility(
                visible = selectedDay != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedDay?.let { day ->
                    val dayLabel = formatVietnameseDate(day.date)
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GoogleSansFlex600
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (day.durationMinutes > 0) "${day.durationMinutes} phút đọc" else "Nghỉ đọc",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GoogleSansFlex600,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (day.durationMinutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Heatmap Grid: Nhãn ngày bên trái + Lưới các cột tuần
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nhãn thứ (T2, T3, T4, T5, T6, T7, CN)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    VIETNAMESE_DAY_LABELS.forEach { label ->
                        Box(
                            modifier = Modifier.size(17.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GoogleSansFlex600,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Lưới 16 tuần cuộn ngang mượt mà
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    weeks.forEach { weekDays ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            weekDays.forEach { stat ->
                                HeatmapTile(
                                    stat = stat,
                                    geometry = geometry,
                                    isDark = isDark,
                                    isSelected = selectedDay?.date == stat.date,
                                    onClick = {
                                        selectedDay = if (selectedDay?.date == stat.date) null else stat
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chú giải cấp độ (Legend): Ít [o o o o o] Nhiều
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ít",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GoogleSansFlex400,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))

                val legendShape: Shape = when (geometry) {
                    HeatmapTileGeometry.SQUIRCLE -> RoundedCornerShape(3.dp)
                    HeatmapTileGeometry.PEBBLE, HeatmapTileGeometry.GLOW -> CircleShape
                    HeatmapTileGeometry.DIAMOND -> RoundedCornerShape(1.5.dp)
                }

                (0..4).forEach { level ->
                    val color = getHeatmapColor(level, isDark)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (geometry == HeatmapTileGeometry.DIAMOND) 8.dp else 10.dp)
                                .then(
                                    if (geometry == HeatmapTileGeometry.DIAMOND) Modifier.rotate(45f) else Modifier
                                )
                                .clip(legendShape)
                                .background(color)
                                .then(
                                    if ((level >= 4) || (geometry == HeatmapTileGeometry.GLOW && level > 0)) {
                                        Modifier.border(0.8.dp, MaterialTheme.colorScheme.primaryContainer, legendShape)
                                    } else Modifier
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Nhiều",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GoogleSansFlex400,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeatmapTile(
    stat: HeatmapDayStat,
    geometry: HeatmapTileGeometry,
    isDark: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val baseColor = getHeatmapColor(stat.level, isDark)

    val shape: Shape = when (geometry) {
        HeatmapTileGeometry.SQUIRCLE -> RoundedCornerShape(5.dp)
        HeatmapTileGeometry.PEBBLE, HeatmapTileGeometry.GLOW -> CircleShape
        HeatmapTileGeometry.DIAMOND -> RoundedCornerShape(3.dp)
    }

    val hasGlowBorder = (stat.level >= 4) || (geometry == HeatmapTileGeometry.GLOW && stat.level > 0)
    val glowBorderColor = MaterialTheme.colorScheme.primaryContainer
    val glowCoreColor = MaterialTheme.colorScheme.onPrimary

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.25f else 1.0f,
        animationSpec = SpringPhysics.BouncySpring,
        label = "HeatmapTileScale"
    )

    Box(
        modifier = Modifier.size(17.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (geometry == HeatmapTileGeometry.DIAMOND) 12.dp else 17.dp)
                .scale(scale)
                .then(
                    if (geometry == HeatmapTileGeometry.DIAMOND) Modifier.rotate(45f) else Modifier
                )
                .clip(shape)
                .background(baseColor)
                .then(
                    when {
                        isSelected -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                        hasGlowBorder -> Modifier.border(1.2.dp, glowBorderColor, shape)
                        stat.isToday -> Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary, shape)
                        else -> Modifier
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Inner radiant core for peak days / glow mode
            if (hasGlowBorder && stat.level > 0) {
                Box(
                    modifier = Modifier
                        .size(if (geometry == HeatmapTileGeometry.DIAMOND) 5.dp else 7.dp)
                        .clip(CircleShape)
                        .background(glowCoreColor)
                )
            }
        }
    }
}

@Composable
private fun getHeatmapColor(level: Int, isDark: Boolean): Color {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceContainerHighest = MaterialTheme.colorScheme.surfaceContainerHighest
    return when (level) {
        0 -> if (isDark) surfaceContainerHighest.copy(alpha = 0.40f) else surfaceContainerHighest.copy(alpha = 0.55f)
        1 -> primary.copy(alpha = if (isDark) 0.28f else 0.22f)
        2 -> primary.copy(alpha = if (isDark) 0.52f else 0.45f)
        3 -> primary.copy(alpha = if (isDark) 0.78f else 0.72f)
        4 -> primary
        else -> surfaceContainerHighest
    }
}

private fun formatVietnameseDate(isoDate: String): String {
    return runCatching {
        val parsed = LocalDate.parse(isoDate)
        val dayOfWeekStr = when (parsed.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "Thứ 2"
            java.time.DayOfWeek.TUESDAY -> "Thứ 3"
            java.time.DayOfWeek.WEDNESDAY -> "Thứ 4"
            java.time.DayOfWeek.THURSDAY -> "Thứ 5"
            java.time.DayOfWeek.FRIDAY -> "Thứ 6"
            java.time.DayOfWeek.SATURDAY -> "Thứ 7"
            java.time.DayOfWeek.SUNDAY -> "Chủ nhật"
        }
        "$dayOfWeekStr, ${parsed.dayOfMonth}/${parsed.monthValue}/${parsed.year}"
    }.getOrDefault("Ngày $isoDate")
}
