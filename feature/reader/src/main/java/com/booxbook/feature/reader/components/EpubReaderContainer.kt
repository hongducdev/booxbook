package com.booxbook.feature.reader.components

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booxbook.core.engine.epub.ReadiumReaderEngine
import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.feature.reader.ReaderTapAction
import com.booxbook.feature.reader.ReaderTapZoneMode
import com.booxbook.feature.reader.ReaderTapZones
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import com.booxbook.feature.reader.ReaderThemePreset
import kotlin.coroutines.resume

/**
 * Hosts Readium's [EpubNavigatorFragment] inside the Compose reader canvas.
 *
 * The navigator is a real Fragment (it owns the paginated WebView), so it has to be
 * added to the Activity's [androidx.fragment.app.FragmentManager]. Two rules matter for
 * this to actually render:
 *
 * 1. The fragment must be committed **after** the host view is attached to the window and
 *    **outside** of Compose's measure/layout pass. Committing synchronously from
 *    [AndroidView]'s `factory` leaves the WebView attached to a detached hierarchy, so
 *    Chromium never presents a frame and the canvas stays blank.
 * 2. The host view id must survive configuration changes ([rememberSaveable]) so that a
 *    restored navigator fragment can be re-attached to its container.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubReaderContainer(
    epubEngine: ReadiumReaderEngine,
    preferences: ReaderPreferences,
    initialLocatorJson: String?,
    ttsSentenceHighlight: String? = null,
    ttsSentenceLocatorJson: String? = null,
    isTtsPlaying: Boolean = false,
    themePreset: ReaderThemePreset = ReaderThemePreset.DARK,
    onLocatorChanged: (Locator) -> Unit,
    onTapAction: (ReaderTapAction) -> Unit,
    onNavigatorReady: (EpubNavigatorFragment?) -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? FragmentActivity ?: return
    val fragmentManager = activity.supportFragmentManager

    // Stable id: restored fragments keep pointing at the same container id.
    val containerId = rememberSaveable { View.generateViewId() }

    val initialLocator = remember(initialLocatorJson) {
        if (initialLocatorJson.isNullOrBlank()) {
            null
        } else {
            runCatching {
                Locator.fromJSON(JSONObject(initialLocatorJson))
            }.getOrNull() ?: runCatching {
                Url(initialLocatorJson)?.let { url ->
                    Locator(href = url, mediaType = MediaType.HTML)
                }
            }.getOrNull()
        }
    }

    var hostView by remember { mutableStateOf<FrameLayout?>(null) }
    var activeFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    /** Kích thước đã báo cho Readium lần gần nhất — tránh gọi `submitPreferences` lặp lại vô ích. */
    var lastReportedSize by remember { mutableStateOf<IntSize?>(null) }

    // Re-key the attach effect on the engine state so it is retried once the publication is
    // ready, instead of silently giving up if the first composition runs too early.
    val engineState by epubEngine.state.collectAsStateWithLifecycle()

    // Create/attach the navigator once the container is attached to the window.
    //
    // Không dựng lại navigator khi cài đặt dàn trang đổi: Readium tự dàn lại qua `submitPreferences`, còn
    // dựng lại fragment để lại canvas trắng (đã thử và quan sát trên máy).
    LaunchedEffect(hostView, engineState, epubEngine) {
        if (activeFragment != null) return@LaunchedEffect
        if (epubEngine.getNavigatorFactory() == null) return@LaunchedEffect

        val host = hostView ?: return@LaunchedEffect
        host.awaitAttached()

        if (fragmentManager.isDestroyed || fragmentManager.isStateSaved) return@LaunchedEffect

        val existing = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
        val navigator = if (existing != null && existing.view?.isAttachedToWindow == true) {
            // Already live inside this container (recomposition after the first attach).
            existing
        } else {
            // A navigator restored by FragmentManager cannot be attached: Compose creates the
            // container *after* restoration, so the fragment would keep a detached, zero-sized
            // view (blank canvas after a configuration change). Replace it, synchronously, so a
            // later lookup cannot observe a fragment that is still pending removal. Reading position
            // is re-applied through [initialLocator].
            if (existing != null) {
                runCatching {
                    fragmentManager.beginTransaction()
                        .remove(existing)
                        .commitNowAllowingStateLoss()
                }
            }

            val factory = epubEngine.createFragmentFactory(
                initialLocator = initialLocator,
                preferences = preferences
            ) ?: return@LaunchedEffect

            val created = factory.instantiate(
                activity.classLoader,
                EpubNavigatorFragment::class.java.name
            ) as? EpubNavigatorFragment ?: return@LaunchedEffect

            fragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(containerId, created)
                .commitNowAllowingStateLoss()

            created
        }

        activeFragment = navigator
    }

    // Continuous locator tracking for progress/bookmark sync.
    LaunchedEffect(activeFragment) {
        val fragment = activeFragment ?: return@LaunchedEffect
        onNavigatorReady(fragment)
        if (fragment.isAdded) {
            fragment.currentLocator.collectLatest { locator ->
                onLocatorChanged(locator)
            }
        }
    }

    // Typography / theme changes are pushed straight into the live navigator.
    LaunchedEffect(preferences, activeFragment) {
        val fragment = activeFragment ?: return@LaunchedEffect
        if (fragment.isAdded && !fragment.isDetached) {
            runCatching {
                fragment.submitPreferences(epubEngine.buildEpubPreferences(preferences))
            }
        }
    }

    // TTS Sentence Highlighting & Auto-Page-Turn Synchronization
    LaunchedEffect(activeFragment, ttsSentenceHighlight, ttsSentenceLocatorJson, isTtsPlaying, themePreset) {
        val fragment = activeFragment ?: return@LaunchedEffect
        if (!fragment.isAdded || fragment.isDetached) return@LaunchedEffect

        if (!isTtsPlaying || ttsSentenceHighlight.isNullOrBlank()) {
            runCatching {
                fragment.applyDecorations(emptyList(), "tts_highlight")
            }
            runCatching {
                fragment.evaluateJavascript(
                    """
                    (function() {
                        var marks = document.querySelectorAll('.boox-tts-highlight');
                        for (var i = 0; i < marks.length; i++) {
                            var el = marks[i];
                            var parent = el.parentNode;
                            if (parent) {
                                while (el.firstChild) parent.insertBefore(el.firstChild, el);
                                parent.removeChild(el);
                            }
                        }
                    })();
                    """.trimIndent()
                )
            }
            return@LaunchedEffect
        }

        // Harmonious tint color depending on reader theme preset
        val highlightArgb = when (themePreset) {
            ReaderThemePreset.LIGHT -> android.graphics.Color.argb(85, 255, 214, 0) // Warm yellow
            ReaderThemePreset.SEPIA -> android.graphics.Color.argb(75, 230, 140, 30) // Warm amber
            ReaderThemePreset.DARK -> android.graphics.Color.argb(90, 255, 215, 64)  // Golden glow on dark
            ReaderThemePreset.AMOLED -> android.graphics.Color.argb(100, 255, 215, 64) // Amber on black
        }

        val parsedLocator = if (!ttsSentenceLocatorJson.isNullOrBlank()) {
            runCatching {
                Locator.fromJSON(JSONObject(ttsSentenceLocatorJson))
            }.getOrNull()
        } else null

        val locatorToUse = parsedLocator ?: runCatching {
            fragment.currentLocator.value.let { cur ->
                Locator(
                    href = cur.href,
                    mediaType = cur.mediaType,
                    title = cur.title,
                    text = Locator.Text(highlight = ttsSentenceHighlight)
                )
            }
        }.getOrNull()

        if (locatorToUse != null) {
            val decoration = Decoration(
                id = "tts_active_highlight",
                locator = locatorToUse,
                style = Decoration.Style.Highlight(tint = highlightArgb, isActive = true)
            )

            // Apply Readium native decoration overlay
            runCatching {
                fragment.applyDecorations(listOf(decoration), "tts_highlight")
            }

            // Auto-turn page if the sentence is on a different page
            runCatching {
                fragment.go(locatorToUse, animated = true)
            }
        }

        // Secondary / Fallback Webview JS highlight to guarantee immediate visual feedback
        val cssColor = when (themePreset) {
            ReaderThemePreset.LIGHT -> "rgba(255, 214, 0, 0.35)"
            ReaderThemePreset.SEPIA -> "rgba(230, 140, 30, 0.30)"
            ReaderThemePreset.DARK -> "rgba(255, 215, 64, 0.35)"
            ReaderThemePreset.AMOLED -> "rgba(255, 215, 64, 0.40)"
        }
        val safeText = JSONObject.quote(ttsSentenceHighlight)
        runCatching {
            fragment.evaluateJavascript(
                """
                (function() {
                    try {
                        var marks = document.querySelectorAll('.boox-tts-highlight');
                        for (var i = 0; i < marks.length; i++) {
                            var el = marks[i];
                            var parent = el.parentNode;
                            if (parent) {
                                while (el.firstChild) parent.insertBefore(el.firstChild, el);
                                parent.removeChild(el);
                            }
                        }

                        var styleEl = document.getElementById('boox-tts-custom-style');
                        if (!styleEl) {
                            styleEl = document.createElement('style');
                            styleEl.id = 'boox-tts-custom-style';
                            document.head.appendChild(styleEl);
                        }
                        styleEl.textContent = '.boox-tts-highlight { background-color: ' + '$cssColor' + ' !important; border-radius: 3px; padding: 1px 0; }';

                        var text = $safeText;
                        if (!text || text.length < 2) return;

                        var cleanSearch = text.trim().replace(/\s+/g, ' ');
                        var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                        var node;
                        while (node = walker.nextNode()) {
                            var nodeText = node.textContent;
                            var idx = nodeText.indexOf(cleanSearch);
                            if (idx === -1 && cleanSearch.length > 25) {
                                idx = nodeText.indexOf(cleanSearch.substring(0, 25));
                            }
                            if (idx !== -1) {
                                var range = document.createRange();
                                range.setStart(node, idx);
                                var matchLen = Math.min(cleanSearch.length, nodeText.length - idx);
                                range.setEnd(node, idx + matchLen);
                                var mark = document.createElement('mark');
                                mark.className = 'boox-tts-highlight';
                                try {
                                    range.surroundContents(mark);
                                } catch(e) {}
                                break;
                            }
                        }
                    } catch(e) {}
                })();
                """.trimIndent()
            )
        }
    }

    DisposableEffect(activeFragment, preferences.tapZoneMode) {
        val fragment = activeFragment ?: return@DisposableEffect onDispose {}
        val tapZoneMode = ReaderTapZoneMode.fromKey(preferences.tapZoneMode)

        val inputListener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val view = fragment.view ?: return false
                val width = view.width.toFloat()
                val height = view.height.toFloat()
                if (width <= 0f || height <= 0f) return false

                val action = ReaderTapZones.resolve(
                    x = event.point.x / width,
                    y = event.point.y / height,
                    mode = tapZoneMode
                )
                if (action == ReaderTapAction.NONE) return false

                onTapAction(action)
                return true
            }

            override fun onDrag(event: DragEvent): Boolean = false
            override fun onKey(event: KeyEvent): Boolean = false
        }

        runCatching { fragment.addInputListener(inputListener) }

        onDispose {
            onNavigatorReady(null)
            runCatching { fragment.removeInputListener(inputListener) }
            val existing = fragmentManager.findFragmentById(containerId)
            if (existing != null && !activity.isFinishing && !activity.isDestroyed) {
                runCatching {
                    fragmentManager.beginTransaction().remove(existing).commitAllowingStateLoss()
                }
            }
        }
    }

    AndroidView(
        factory = { context -> FrameLayout(context).apply { id = containerId } },
        modifier = modifier
            .fillMaxSize()
            // Khi lề thay đổi, hộp Compose hẹp lại và WebView đổi kích thước — nhưng Readium **không tự biết**,
            // nên pager giữ nguyên bề rộng trang cũ và chữ chồng lên nhau, tràn ra ngoài khung. Đã quan sát
            // đúng như vậy trên máy. `submitPreferences` là API công khai duy nhất khiến Readium dàn lại, nên
            // gọi nó sau mỗi lần kích thước đổi.
            .onSizeChanged { size ->
                if (size.width == 0 || size.height == 0) return@onSizeChanged
                if (size == lastReportedSize) return@onSizeChanged
                val firstLayout = lastReportedSize == null
                lastReportedSize = size

                // Bố cục đầu tiên đã có preferences đúng ngay từ `createFragmentFactory`, không cần gọi lại.
                if (firstLayout) return@onSizeChanged

                val fragment = activeFragment ?: return@onSizeChanged
                if (!fragment.isAdded) return@onSizeChanged
                runCatching { fragment.submitPreferences(epubEngine.buildEpubPreferences(preferences)) }
            },
        update = { view -> hostView = view }
    )
}

/**
 * Suspends until [this] view is attached to a window (or immediately if it already is).
 */
private suspend fun View.awaitAttached() = suspendCancellableCoroutine { continuation ->
    if (isAttachedToWindow) {
        if (continuation.isActive) continuation.resume(Unit)
        return@suspendCancellableCoroutine
    }
    val listener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            v.removeOnAttachStateChangeListener(this)
            if (continuation.isActive) continuation.resume(Unit)
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    }
    addOnAttachStateChangeListener(listener)
    continuation.invokeOnCancellation { removeOnAttachStateChangeListener(listener) }
}
