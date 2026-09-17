package com.booxbook.core.engine.azw3

import android.content.Context
import android.graphics.Bitmap
import androidx.fragment.app.FragmentFactory
import com.booxbook.core.engine.ReaderEngine
import com.booxbook.core.engine.epub.ReadiumReaderEngine
import com.booxbook.core.engine.epub.ReadiumAssetRetriever
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
 * Native AZW3 (Amazon Kindle KF8) reader engine.
 * Unpacks AZW3 to a cached EPUB using libmobi C++ JNI bridge and renders
 * through Readium EPUB Navigator with discrete pagination.
 */
@OptIn(ExperimentalReadiumApi::class)
@Singleton
class Azw3ReaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val azw3Converter: Azw3Converter,
    private val readiumAssetRetriever: ReadiumAssetRetriever
) : ReadiumReaderEngine {

    override val supportedFormat: BookFormat = BookFormat.AZW3

    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        set(value) {
            field = value
            azw3Converter.ioDispatcher = value
        }

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Idle)
    override val state: StateFlow<ReaderState> = _state.asStateFlow()

    @Volatile
    private var activePublication: Publication? = null
    @Volatile
    private var activeBook: Book? = null
    @Volatile
    private var navigatorFactory: EpubNavigatorFactory? = null

    override fun getPublication(): Publication? = activePublication
    override fun getNavigatorFactory(): EpubNavigatorFactory? = navigatorFactory

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

            // Convert AZW3 to EPUB cache
            val conversionResult = azw3Converter.convertToEpub(file, context.cacheDir)
            val cachedEpubFile = when (conversionResult) {
                is Azw3ConversionResult.Success -> conversionResult.epubFile
                is Azw3ConversionResult.DrmProtected -> {
                    val err = "Sách này được bảo vệ bản quyền DRM của Amazon Kindle và không thể giải mã"
                    _state.value = ReaderState.Error(err)
                    throw SecurityException(err)
                }
                is Azw3ConversionResult.Error -> {
                    val err = "Lỗi xử lý file AZW3: ${conversionResult.message}"
                    _state.value = ReaderState.Error(err)
                    throw IllegalStateException(err)
                }
            }

            // Open converted publication in Readium
            val publication = readiumAssetRetriever.openPublication(cachedEpubFile).getOrThrow()
            activePublication = publication
            activeBook = book

            val factory = EpubNavigatorFactory(publication)
            navigatorFactory = factory

            val tocItems = mapLinksToToc(publication.tableOfContents)
            val totalSpineItems = publication.readingOrder.size

            _state.value = ReaderState.Ready(
                book = book,
                tableOfContents = tocItems,
                totalPages = totalSpineItems
            )
        }.onFailure { throwable ->
            _state.value = ReaderState.Error(
                message = throwable.message ?: "Không thể mở sách AZW3",
                throwable = throwable
            )
        }
    }

    override suspend fun closeBook() {
        withContext(ioDispatcher) {
            closeBookSync()
        }
    }

    fun closeBookSync() {
        try {
            activePublication?.close()
        } catch (_: Throwable) {}
        activePublication = null
        activeBook = null
        navigatorFactory = null
        _state.value = ReaderState.Idle
    }

    override fun buildEpubPreferences(prefs: ReaderPreferences): EpubPreferences {
        val resolvedTheme = when (prefs.themePreset) {
            "SEPIA" -> Theme.SEPIA
            "LIGHT" -> Theme.LIGHT
            "DARK", "AMOLED" -> Theme.DARK
            else -> if (prefs.isDarkMode) Theme.DARK else Theme.LIGHT
        }
        return EpubPreferences(
            scroll = prefs.isScrollMode,
            fontSize = prefs.fontSize,
            lineHeight = prefs.lineHeight,
            pageMargins = prefs.pageMargins,
            fontFamily = prefs.fontFamily?.let { FontFamily(it) },
            theme = resolvedTheme
        )
    }

    override fun createFragmentFactory(
        initialLocator: Locator?,
        preferences: ReaderPreferences,
        listener: EpubNavigatorFragment.Listener?,
        paginationListener: EpubNavigatorFragment.PaginationListener?
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
            val bookFile = File(book.filePath)
            if (!bookFile.exists()) return@runCatching null

            // 1. Direct native extraction from AZW3 EXTH headers
            val extracted = azw3Converter.extractCover(bookFile, destinationFile)
            if (extracted && destinationFile.exists() && destinationFile.length() > 0) {
                return@runCatching destinationFile
            }

            // 2. Fallback: Convert/open publication and extract cover via Readium
            val isTransient = activeBook?.id != book.id || activePublication == null
            val pubToUse = if (!isTransient) {
                activePublication
            } else {
                val conversion = azw3Converter.convertToEpub(bookFile, context.cacheDir)
                if (conversion is Azw3ConversionResult.Success) {
                    readiumAssetRetriever.openPublication(conversion.epubFile).getOrNull()
                } else null
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
