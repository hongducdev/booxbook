package com.booxbook.core.engine.epub

import androidx.fragment.app.FragmentFactory
import com.booxbook.core.engine.ReaderEngine
import com.booxbook.core.engine.model.ReaderPreferences
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/**
 * Common interface for reader engines that use Readium's EPUB rendering toolkit
 * (EPUB and unpacked KF8/AZW3).
 */
@OptIn(ExperimentalReadiumApi::class)
interface ReadiumReaderEngine : ReaderEngine {
    fun getPublication(): Publication?
    fun getNavigatorFactory(): EpubNavigatorFactory?
    fun buildEpubPreferences(prefs: ReaderPreferences = ReaderPreferences()): EpubPreferences
    fun createFragmentFactory(
        initialLocator: Locator? = null,
        preferences: ReaderPreferences = ReaderPreferences(),
        listener: EpubNavigatorFragment.Listener? = null,
        paginationListener: EpubNavigatorFragment.PaginationListener? = null
    ): FragmentFactory?
}
