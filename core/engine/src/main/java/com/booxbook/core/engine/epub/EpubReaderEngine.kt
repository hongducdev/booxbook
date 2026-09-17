package com.booxbook.core.engine.epub

import android.content.Context
import android.graphics.Bitmap
import androidx.fragment.app.FragmentFactory
import com.booxbook.core.engine.ReaderEngine
import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.ReaderState
import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native EPUB reader engine powered by Readium Kotlin Toolkit.
 * Configured for discrete pagination (lật từng trang) by default.
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class EpubReaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readiumAssetRetriever: ReadiumAssetRetriever
) : ReaderEngine {

    override val supportedFormat: BookFormat = BookFormat.EPUB

    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Idle)
    override val state: StateFlow<ReaderState> = _state.asStateFlow()

    @Volatile
    private var activePublication: Publication? = null
    @Volatile
    private var activeBook: Book? = null
    @Volatile
    private var navigatorFactory: EpubNavigatorFactory? = null

    /**
     * Gets the currently active Readium Publication.
     */
    fun getPublication(): Publication? = activePublication

    /**
     * Gets the active EpubNavigatorFactory for Fragment instantiation.
     */
    fun getNavigatorFactory(): EpubNavigatorFactory? = navigatorFactory

    override suspend fun openBook(book: Book): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            closeBook()
            _state.value = ReaderState.Loading(book)

            val file = File(book.filePath)
            if (!file.exists()) {
                val err = "Tệp sách không tồn tại: ${book.filePath}"
                _state.value = ReaderState.Error(err)
                throw IllegalArgumentException(err)
            }

            val publication = readiumAssetRetriever.openPublication(file).getOrThrow()
            activePublication = publication
            activeBook = book

            // Create Navigator Factory
            val factory = EpubNavigatorFactory(publication)
            navigatorFactory = factory

            // Extract Table of Contents
            val tocItems = mapLinksToToc(publication.tableOfContents)
            val totalSpineItems = publication.readingOrder.size

            _state.value = ReaderState.Ready(
                book = book,
                tableOfContents = tocItems,
                totalPages = totalSpineItems
            )
        }.onFailure { throwable ->
            _state.value = ReaderState.Error(
                message = throwable.message ?: "Không thể mở sách EPUB",
                throwable = throwable
            )
        }
    }

    override suspend fun closeBook() {
        withContext(ioDispatcher) {
            closeBookSync()
        }
    }

    /**
     * Synchronously closes resources, safe to call during lifecycle teardown or onCleared().
     */
    fun closeBookSync() {
        try {
            activePublication?.close()
        } catch (_: Throwable) {}
        activePublication = null
        activeBook = null
        navigatorFactory = null
        _state.value = ReaderState.Idle
    }

    /**
     * Builds [EpubPreferences] according to [ReaderPreferences].
     * Defaults to discrete pagination (scroll = false).
     */
    fun buildEpubPreferences(prefs: ReaderPreferences = ReaderPreferences()): EpubPreferences {
        val resolvedTheme = when (prefs.themePreset) {
            "SEPIA" -> Theme.SEPIA
            "LIGHT" -> Theme.LIGHT
            "DARK", "AMOLED" -> Theme.DARK
            else -> if (prefs.isDarkMode) Theme.DARK else Theme.LIGHT
        }
        return EpubPreferences(
            scroll = prefs.isScrollMode, // false = discrete page-turn
            fontSize = prefs.fontSize,
            lineHeight = prefs.lineHeight,
            pageMargins = prefs.pageMargins,
            fontFamily = prefs.fontFamily?.let { FontFamily(it) },
            theme = resolvedTheme
        )
    }

    /**
     * Creates a FragmentFactory for [EpubNavigatorFragment] with discrete pagination preferences.
     */
    fun createFragmentFactory(
        initialLocator: Locator? = null,
        preferences: ReaderPreferences = ReaderPreferences(),
        listener: EpubNavigatorFragment.Listener? = null,
        paginationListener: EpubNavigatorFragment.PaginationListener? = null
    ): FragmentFactory? {
        val factory = navigatorFactory ?: return null
        val epubPrefs = buildEpubPreferences(preferences)
        return factory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = epubPrefs,
            listener = listener,
            paginationListener = paginationListener
        )
    }

    override suspend fun extractCover(book: Book, destinationFile: File): Result<File?> = withContext(ioDispatcher) {
        runCatching {
            val isTransient = activeBook?.id != book.id || activePublication == null
            val pubToUse = if (!isTransient) {
                activePublication
            } else {
                val file = File(book.filePath)
                if (!file.exists()) return@runCatching null
                readiumAssetRetriever.openPublication(file).getOrNull()
            } ?: return@runCatching null

            try {
                val bitmap: Bitmap? = pubToUse.cover()
                if (bitmap != null) {
                    destinationFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                    val tempFile = File.createTempFile("cover_tmp_", ".tmp", destinationFile.parentFile)
                    try {
                        FileOutputStream(tempFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        if (!tempFile.renameTo(destinationFile)) {
                            tempFile.copyTo(destinationFile, overwrite = true)
                            tempFile.delete()
                        }
                        destinationFile
                    } catch (e: Throwable) {
                        tempFile.delete()
                        throw e
                    }
                } else {
                    null
                }
            } finally {
                if (isTransient) {
                    try {
                        pubToUse.close()
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun mapLinksToToc(links: List<Link>): List<TocItem> {
        return links.map { link ->
            TocItem(
                title = link.title ?: link.href.toString(),
                href = link.href.toString(),
                children = mapLinksToToc(link.children)
            )
        }
    }
}
