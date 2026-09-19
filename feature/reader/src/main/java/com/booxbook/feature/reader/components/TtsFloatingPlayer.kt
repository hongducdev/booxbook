package com.booxbook.feature.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.booxbook.core.tts.model.TtsSessionState
import kotlin.math.roundToInt

/**
 * Single Draggable Floating Play/Pause Button for TTS (inspired by Nekori novel reader).
 *
 * - Always present on the reading canvas without needing toolbar menus.
 * - Freely draggable across the screen to stay out of the reader's line of sight.
 * - Reliable Tap detection: Tapping triggers instant Play/Pause.
 * - Integrated subtle circular progress indicator around the button.
 * - Mini 1-tap Close button when TTS is active.
 */
@Composable
fun TtsFloatingPlayer(
    visible: Boolean,
    state: TtsSessionState,
    onTogglePlayPause: () -> Unit,
    onNextSentence: () -> Unit,
    onPreviousSentence: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val touchSlopPx = with(density) { 6.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = scaleOut(
            targetScale = 0.6f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) + fadeOut(),
        modifier = modifier
    ) {
        val buttonScale by animateFloatAsState(
            targetValue = if (isPressed) 0.88f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "ttsFabScale"
        )

        val progress = if (state.totalSentences > 0) {
            (state.currentSentenceIndex + 1).toFloat() / state.totalSentences
        } else 0f

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Main Circular Play/Pause Floating Action Button with combined Tap & Drag Gestures
            Surface(
                shape = CircleShape,
                color = if (state.isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shadowElevation = 8.dp,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .size(52.dp)
                    .scale(buttonScale)
                    .pointerInput(onTogglePlayPause) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true
                            var isDrag = false
                            var totalDistance = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    // Pointer released (Up)!
                                    if (!isDrag) {
                                        // User tapped the button!
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTogglePlayPause()
                                    }
                                    change.consume()
                                    break
                                }

                                val delta = change.position - change.previousPosition
                                totalDistance += delta.getDistance()

                                if (!isDrag && totalDistance > touchSlopPx) {
                                    isDrag = true
                                }

                                if (isDrag) {
                                    offsetX += delta.x
                                    offsetY += delta.y
                                    change.consume()
                                }
                            }
                            isPressed = false
                        }
                    }
            ) {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Subtle chapter progress ring surrounding the button
                    if (state.isActive && state.totalSentences > 0) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 2.5.dp,
                            color = if (state.isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            trackColor = if (state.isPlaying) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            }
                        )
                    }

                    // Center Play / Pause Icon
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Tạm dừng" else "Tiếp tục phát",
                        tint = if (state.isPlaying) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Discreet mini close badge at the top-right corner (visible when TTS is active)
            if (state.isActive) {
                val closeInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(20.dp)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            interactionSource = closeInteraction,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClose()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tắt TTS",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
