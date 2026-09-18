package com.booxbook.feature.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.feature.reader.ReaderTapZoneMode
import com.booxbook.feature.reader.ReaderTapZones
import kotlinx.coroutines.delay

private const val PREVIEW_DURATION_MS = 2200L

private val PrevColor = Color(0xE6FF7A33)
private val NextColor = Color(0xE684E296)
private val MenuColor = Color(0xE66750A4)
private val InactiveColor = Color(0x33FFFFFF)

/**
 * Transient overlay that shows where the tap zones are for the selected [mode].
 *
 * Shown from reader settings so the layout is discoverable; auto-dismisses.
 */
@Composable
fun TapZonePreviewOverlay(
    mode: ReaderTapZoneMode,
    visible: Boolean,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (mode == ReaderTapZoneMode.KINDLE) {
                    ZoneCell(
                        title = "Menu",
                        hint = "Thanh điều khiển",
                        color = MenuColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(ReaderTapZones.TOP_STRIP)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (mode) {
                        ReaderTapZoneMode.KINDLE -> {
                            ZoneCell("Trang trước", "Chạm", PrevColor, Modifier.weight(0.30f).fillMaxHeight())
                            ZoneCell("Menu", "Thanh điều khiển", MenuColor, Modifier.weight(0.40f).fillMaxHeight())
                            ZoneCell("Trang sau", "Chạm", NextColor, Modifier.weight(0.30f).fillMaxHeight())
                        }

                        ReaderTapZoneMode.EDGES -> {
                            ZoneCell("Trang trước", "Chạm", PrevColor, Modifier.weight(0.25f).fillMaxHeight())
                            ZoneCell("Menu", "Thanh điều khiển", MenuColor, Modifier.weight(0.50f).fillMaxHeight())
                            ZoneCell("Trang sau", "Chạm", NextColor, Modifier.weight(0.25f).fillMaxHeight())
                        }

                        ReaderTapZoneMode.MENU_ONLY -> {
                            ZoneCell("Không tác dụng", null, InactiveColor, Modifier.weight(0.25f).fillMaxHeight())
                            ZoneCell("Menu", "Thanh điều khiển", MenuColor, Modifier.weight(0.50f).fillMaxHeight())
                            ZoneCell("Không tác dụng", null, InactiveColor, Modifier.weight(0.25f).fillMaxHeight())
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            ) {
                Text(
                    text = "Vùng chạm: ${mode.displayName}",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = GoogleSansFlex600,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            delay(PREVIEW_DURATION_MS)
            onDismissed()
        }
    }
}

@Composable
private fun ZoneCell(
    title: String,
    hint: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = GoogleSansFlex600,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GoogleSansFlex600,
                        fontSize = 11.sp
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
