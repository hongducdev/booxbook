package com.booxbook.core.engine.cbz

import android.content.Context
import com.booxbook.core.engine.ReaderEngine
import com.booxbook.core.engine.model.ReaderState
import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Native Comic/Manga CBZ reader engine.
 * Powered by on-demand ZIP extraction and Jetpack Compose LazyColumn rendering.
 */
@Singleton
class CbzReaderEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cbzArchiveExtractor: CbzArchiveExtractor
) : ReaderEngine {

    override val supportedFormat: BookFormat = BookFormat.CBZ

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Idle)
    override val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var activeArchive: CbzArchive? = null
    private var activeBook: Book? = null

    /**
     * Gets the active CBZ archive instance.
     */
    fun getArchive(): CbzArchive? = activeArchive

    override suspend fun openBook(book: Book): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            closeBook()
            _state.value = ReaderState.Loading(book)

            val file = File(book.filePath)
            if (!file.exists()) {
                val err = "Tệp truyện CBZ không tồn tại: ${book.filePath}"
                _state.value = ReaderState.Error(err)
                throw IllegalArgumentException(err)
            }

            val archive = cbzArchiveExtractor.openArchive(file, book.id)
            activeArchive = archive
            activeBook = book

            val pageCount = archive.pageCount
            val toc = archive.pages.map { page ->
                TocItem(
                    title = "Trang ${page.index + 1}",
                    href = "page://${page.index}"
                )
            }

            _state.value = ReaderState.Ready(
                book = book,
                tableOfContents = toc,
                totalPages = pageCount
            )
        }.onFailure { throwable ->
            _state.value = ReaderState.Error(
                message = throwable.message ?: "Không thể mở tệp CBZ",
                throwable = throwable
            )
        }
    }

    override suspend fun closeBook() {
        withContext(Dispatchers.IO) {
            try {
                activeArchive?.close()
            } catch (_: Throwable) {}
            activeArchive = null
            activeBook = null
            _state.value = ReaderState.Idle
        }
    }

    override suspend fun extractCover(book: Book, destinationFile: File): Result<File?> = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        if (!file.exists()) return@withContext Result.success(null)
        cbzArchiveExtractor.extractCover(file, book.id, destinationFile)
    }
}
