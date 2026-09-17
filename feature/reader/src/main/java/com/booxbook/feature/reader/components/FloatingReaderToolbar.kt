package com.booxbook.feature.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.booxbook.core.ui.animation.SpringPhysics
import com.booxbook.core.ui.theme.GoogleSansFlex600

@Composable
fun FloatingReaderToolbar(
    visible: Boolean,
    currentPage: Int,
    totalPages: Int,
    progressPercentage: Float,
    onSeekToPage: (Int) -> Unit,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onTtsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember(currentPage, progressPercentage) {
        mutableFloatStateOf(if (totalPages > 0) currentPage.toFloat() else progressPercentage * 100f)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = SpringPhysics.BouncyOffsetSpring, initialOffsetY = { it }) + fadeIn(animationSpec = SpringPhysics.SmoothSpring),
        exit = slideOutVertically(animationSpec = SpringPhysics.BouncyOffsetSpring, targetOffsetY = { it }) + fadeOut(animationSpec = SpringPhysics.SmoothSpring),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slider + Page progress text
                if (totalPages > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentPage + 1}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = GoogleSansFlex600,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                onSeekToPage(sliderPosition.toInt())
                            },
                            valueRange = 0f..(totalPages - 1).toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Text(
                            text = "$totalPages",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = GoogleSansFlex600,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenToc) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.List,
                            contentDescription = "Mục lục",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.FormatSize,
                            contentDescription = "Cài đặt hiển thị",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = onOpenBookmarks) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmarks,
                            contentDescription = "Ghi chú & Đánh dấu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = onTtsClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Đọc bằng giọng nói",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
