package com.booxbook.feature.reader.components

import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.booxbook.core.engine.epub.ReadiumReaderEngine
import com.booxbook.core.engine.model.ReaderPreferences
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubReaderContainer(
    epubEngine: ReadiumReaderEngine,
    preferences: ReaderPreferences,
    initialLocatorJson: String?,
    onLocatorChanged: (Locator) -> Unit,
    onCenterTap: () -> Unit,
    onNavigatorReady: (EpubNavigatorFragment?) -> Unit,
    modifier: Modifier = Modifier
) {
    val activity = LocalContext.current as? FragmentActivity ?: return
    val fragmentManager = activity.supportFragmentManager
    val containerId = rememberSaveable { View.generateViewId() }

    val initialLocator = remember(initialLocatorJson) {
        if (!initialLocatorJson.isNullOrBlank()) {
            runCatching {
                Locator.fromJSON(JSONObject(initialLocatorJson))
            }.getOrNull()
        } else {
            null
        }
    }

    val navigatorFragment = remember {
        val factory = epubEngine.createFragmentFactory(
            initialLocator = initialLocator,
            preferences = preferences
        )
        if (factory != null) {
            fragmentManager.fragmentFactory = factory
            fragmentManager.fragmentFactory.instantiate(
                activity.classLoader,
                EpubNavigatorFragment::class.java.name
            ) as EpubNavigatorFragment
        } else {
            null
        }
    }

    LaunchedEffect(navigatorFragment) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        onNavigatorReady(fragment)

        // Observe continuous locator updates as user flips pages
        fragment.currentLocator.collectLatest { locator ->
            onLocatorChanged(locator)
        }
    }

    LaunchedEffect(preferences, navigatorFragment) {
        val fragment = navigatorFragment ?: return@LaunchedEffect
        fragment.submitPreferences(epubEngine.buildEpubPreferences(preferences))
    }

    DisposableEffect(navigatorFragment) {
        val fragment = navigatorFragment
        val inputListener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val point = event.point
                val view = fragment?.view ?: return false
                val width = view.width.toFloat()
                if (width <= 0f) return false

                // If tapped in the central 50% zone of screen, toggle reading controls
                val relativeX = point.x / width
                if (relativeX in 0.25f..0.75f) {
                    onCenterTap()
                    return true
                }
                return false
            }

            override fun onDrag(event: DragEvent): Boolean = false
            override fun onKey(event: KeyEvent): Boolean = false
        }

        fragment?.addInputListener(inputListener)

        onDispose {
            onNavigatorReady(null)
            fragment?.removeInputListener(inputListener)
            val existing = fragmentManager.findFragmentById(containerId)
            if (existing != null && !activity.isFinishing && !activity.isDestroyed) {
                fragmentManager.beginTransaction().remove(existing).commitAllowingStateLoss()
            }
        }
    }

    AndroidView(
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                val existing = fragmentManager.findFragmentById(containerId)
                if (existing == null && navigatorFragment != null) {
                    fragmentManager.beginTransaction()
                        .replace(id, navigatorFragment)
                        .commitAllowingStateLoss()
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
