package com.booxbook.feature.reader.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity

/**
 * One in-flight page-lift transition: a snapshot of the page that is being left behind,
 * plus the direction of the turn.
 */
data class PageTurnFlip(
    val image: ImageBitmap,
    val forward: Boolean,
    val id: Long
)

/**
 * Drives the Kindle-like page-lift transition.
 *
 * The outgoing page is snapshotted *before* the navigator turns, the turn itself is instant,
 * and the snapshot is then peeled away over the freshly rendered page.
 */
@Stable
class PageTurnFlipController {

    var current: PageTurnFlip? by mutableStateOf(null)
        private set

    private var nextId = 0L

    /**
     * Captures [view] and starts a lift.
     *
     * Returns `false` when nothing usable could be captured, so the caller can fall back to the
     * navigator's own animated transition.
     */
    fun play(view: View, forward: Boolean): Boolean {
        val snapshot = captureView(view) ?: return false
        if (isProbablyBlank(snapshot)) return false

        current = PageTurnFlip(
            image = snapshot.asImageBitmap(),
            forward = forward,
            id = nextId++
        )
        return true
    }

    internal fun finished(id: Long) {
        if (current?.id == id) current = null
    }
}

@Composable
fun rememberPageTurnFlipController(): PageTurnFlipController = remember { PageTurnFlipController() }

/**
 * Draws the page-lift transition on top of the reading canvas.
 *
 * Place it directly after the canvas and before the reading chrome.
 */
@Composable
fun PageTurnFlipOverlay(
    controller: PageTurnFlipController,
    modifier: Modifier = Modifier
) {
    val flip = controller.current ?: return
    val progress = remember(flip.id) { Animatable(0f) }
    val density = LocalDensity.current.density

    LaunchedEffect(flip.id) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = PAGE_TURN_DURATION_MS, easing = FastOutSlowInEasing)
        )
        controller.finished(flip.id)
    }

    val fraction = progress.value
    val direction = if (flip.forward) -1f else 1f
    // The page pivots around the spine: right edge when advancing, left edge when going back.
    val spineX = if (flip.forward) 1f else 0f

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = flip.image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(spineX, 0.5f)
                    cameraDistance = 14f * density
                    rotationY = direction * PAGE_TURN_MAX_ROTATION_DEG * fraction
                    alpha = 1f - 0.25f * fraction * fraction
                }
        )

        // Shadow that sweeps in behind the lifting page, so the new page reads as uncovered.
        val shadow = Color.Black.copy(alpha = 0.45f * fraction)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (flip.forward) {
                        Brush.horizontalGradient(colors = listOf(Color.Transparent, shadow))
                    } else {
                        Brush.horizontalGradient(colors = listOf(shadow, Color.Transparent))
                    }
                )
        )
    }
}

private const val PAGE_TURN_DURATION_MS = 280
private const val PAGE_TURN_MAX_ROTATION_DEG = 84f

/**
 * Snapshots a live view hierarchy (the Readium navigator's publication view) into a bitmap.
 * Must be called on the main thread.
 */
private fun captureView(view: View): Bitmap? {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) return null
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap
    } catch (_: Throwable) {
        null
    }
}

/**
 * Cheap sanity check so a failed capture (all one colour) falls back to the navigator transition
 * instead of flashing a blank overlay.
 */
private fun isProbablyBlank(bitmap: Bitmap): Boolean {
    val stepX = (bitmap.width / 8).coerceAtLeast(1)
    val stepY = (bitmap.height / 8).coerceAtLeast(1)
    var reference: Int? = null
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val first = reference
            if (first == null) {
                reference = pixel
            } else if (pixel != first) {
                return false
            }
            x += stepX
        }
        y += stepY
    }
    return true
}
