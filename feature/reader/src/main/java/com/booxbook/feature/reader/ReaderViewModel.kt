package com.booxbook.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.engine.cbz.CbzReaderEngine
import com.booxbook.core.engine.epub.EpubReaderEngine
import com.booxbook.core.engine.model.ReaderState
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    val epubReaderEngine: EpubReaderEngine,
    val cbzReaderEngine: CbzReaderEngine
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        set(value) {
            field = value
            epubReaderEngine.ioDispatcher = value
            cbzReaderEngine.ioDispatcher = value
        }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var progressSaveJob: Job? = null
    private var annotationsJob: Job? = null
    private var initialLocator: String? = null

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val book = withContext(ioDispatcher) {
                bookRepository.getBookByIdSync(bookId)
            }

            if (book == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không tìm thấy cuốn sách này"
                    )
                }
                return@launch
            }

            // Load saved reading progress & annotations
            val savedProgress = withContext(ioDispatcher) {
                bookRepository.getReadingProgressSync(bookId)
            }
            initialLocator = savedProgress?.locator

            // Set book immediately upon retrieval from repository
            _uiState.update {
                it.copy(
                    book = book,
                    format = book.format,
                    currentLocator = savedProgress?.locator,
                    progressPercentage = savedProgress?.percentage ?: 0f,
                    currentPage = savedProgress?.currentPage ?: 0
                )
            }

            // Collect annotations with tracked cancellable job
            annotationsJob?.cancel()
            annotationsJob = viewModelScope.launch {
                bookRepository.getAnnotationsForBook(bookId).collect { annotations ->
                    _uiState.update { state ->
                        state.copy(
                            annotations = annotations,
                            isCurrentLocationBookmarked = isBookmarked(annotations, state.currentLocator, state.currentPage)
                        )
                    }
                }
            }

            // Initialize respective engine
            when (book.format) {
                BookFormat.EPUB -> initEpub(book, savedProgress)
                BookFormat.CBZ -> initCbz(book, savedProgress)
                BookFormat.AZW3 -> {
                    // Fallback to EPUB reader engine for AZW3
                    initEpub(book, savedProgress)
                }
            }
        }
    }

    private suspend fun initEpub(book: Book, savedProgress: ReadingProgress?) {
        val result = epubReaderEngine.openBook(book)
        if (result.isSuccess) {
            val engineState = epubReaderEngine.state.value
            val toc = if (engineState is ReaderState.Ready) engineState.tableOfContents else emptyList()
            val totalSpine = if (engineState is ReaderState.Ready) engineState.totalPages else 0

            _uiState.update {
                it.copy(
                    isLoading = false,
                    book = book,
                    format = book.format,
                    tableOfContents = toc,
                    totalPages = totalSpine,
                    currentLocator = savedProgress?.locator,
                    progressPercentage = savedProgress?.percentage ?: 0f,
                    currentPage = savedProgress?.currentPage ?: 0
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Không thể mở sách EPUB"
                )
            }
        }
    }

    private suspend fun initCbz(book: Book, savedProgress: ReadingProgress?) {
        val result = cbzReaderEngine.openBook(book)
        if (result.isSuccess) {
            val archive = cbzReaderEngine.getArchive()
            val totalPages = archive?.pageCount ?: 0
            val initialPage = savedProgress?.currentPage ?: 0
            val initialPercent = if (totalPages > 0) (initialPage + 1).toFloat() / totalPages else 0f
            val engineState = cbzReaderEngine.state.value
            val toc = if (engineState is ReaderState.Ready) engineState.tableOfContents else emptyList()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    book = book,
                    format = BookFormat.CBZ,
                    totalPages = totalPages,
                    tableOfContents = toc,
                    currentPage = initialPage,
                    progressPercentage = initialPercent,
                    currentLocator = "page://$initialPage"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Không thể mở truyện CBZ"
                )
            }
        }
    }

    fun onPageChanged(
        pageIndex: Int,
        totalPages: Int,
        locator: String? = null,
        chapterTitle: String = "",
        percentage: Float? = null
    ) {
        val percent = percentage ?: if (totalPages > 0) (pageIndex + 1).toFloat() / totalPages else 0f
        val loc = locator ?: "page://$pageIndex"

        _uiState.update { state ->
            state.copy(
                currentPage = pageIndex,
                totalPages = totalPages,
                progressPercentage = percent,
                currentLocator = loc,
                currentChapterTitle = if (chapterTitle.isNotBlank()) chapterTitle else state.currentChapterTitle,
                isCurrentLocationBookmarked = isBookmarked(state.annotations, loc, pageIndex)
            )
        }

        // Debounced save progress to Room
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch(ioDispatcher) {
            delay(500)
            val currentBook = _uiState.value.book ?: return@launch
            val progress = ReadingProgress(
                bookId = currentBook.id,
                locator = loc,
                percentage = percent,
                currentPage = pageIndex,
                totalPages = totalPages
            )
            bookRepository.saveReadingProgress(progress)
            bookRepository.updateLastRead(currentBook.id)
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun hideControls() {
        _uiState.update { it.copy(isControlsVisible = false) }
    }

    fun openSheet(sheet: ActiveReaderSheet) {
        _uiState.update { it.copy(activeSheet = sheet, isControlsVisible = false) }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(activeSheet = null) }
    }

    fun updateFontSize(delta: Double) {
        _uiState.update { state ->
            val newSize = (state.preferences.fontSize + delta).coerceIn(0.7, 2.5)
            state.copy(preferences = state.preferences.copy(fontSize = newSize))
        }
    }

    fun updateFontFamily(font: String?) {
        _uiState.update { state ->
            state.copy(preferences = state.preferences.copy(fontFamily = font))
        }
    }

    fun updateThemePreset(preset: ReaderThemePreset) {
        _uiState.update { state ->
            val isDark = preset == ReaderThemePreset.DARK || preset == ReaderThemePreset.AMOLED
            state.copy(
                themePreset = preset,
                preferences = state.preferences.copy(
                    isDarkMode = isDark,
                    themePreset = preset.name
                )
            )
        }
    }

    fun toggleBookmark() {
        val currentBook = _uiState.value.book ?: return
        val currentLoc = _uiState.value.currentLocator ?: "page://${_uiState.value.currentPage}"
        val isCurrentlyBookmarked = _uiState.value.isCurrentLocationBookmarked

        viewModelScope.launch(ioDispatcher) {
            if (isCurrentlyBookmarked) {
                val existing = _uiState.value.annotations.firstOrNull {
                    it.type == AnnotationType.BOOKMARK && it.locator == currentLoc
                }
                if (existing != null) {
                    bookRepository.removeAnnotation(existing.id)
                }
            } else {
                val title = _uiState.value.currentChapterTitle.ifBlank { "Trang ${_uiState.value.currentPage + 1}" }
                val bookmark = Annotation(
                    bookId = currentBook.id,
                    type = AnnotationType.BOOKMARK,
                    locator = currentLoc,
                    noteContent = title
                )
                bookRepository.addAnnotation(bookmark)
            }
        }
    }

    fun deleteAnnotation(annotation: Annotation) {
        viewModelScope.launch(ioDispatcher) {
            bookRepository.removeAnnotation(annotation.id)
        }
    }

    private fun isBookmarked(annotations: List<Annotation>, locator: String?, page: Int): Boolean {
        if (locator == null) return false
        return annotations.any {
            it.type == AnnotationType.BOOKMARK && (it.locator == locator || it.locator == "page://$page")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Synchronously and deterministically close resources to prevent memory & file descriptor leaks
        progressSaveJob?.cancel()
        annotationsJob?.cancel()
        epubReaderEngine.closeBookSync()
        cbzReaderEngine.closeBookSync()
    }
}
