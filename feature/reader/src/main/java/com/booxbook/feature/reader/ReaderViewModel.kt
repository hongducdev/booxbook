package com.booxbook.feature.reader

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.engine.azw3.Azw3ReaderEngine
import com.booxbook.core.engine.cbz.CbzReaderEngine
import com.booxbook.core.engine.epub.EpubReaderEngine
import com.booxbook.core.engine.epub.ReadiumReaderEngine
import com.booxbook.core.engine.model.ReaderState
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress
import com.booxbook.core.tts.TtsEngineWrapper
import com.booxbook.core.tts.TtsService
import com.booxbook.core.tts.model.TtsSessionState
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
    val azw3ReaderEngine: Azw3ReaderEngine,
    val cbzReaderEngine: CbzReaderEngine,
    val ttsEngineWrapper: TtsEngineWrapper
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        set(value) {
            field = value
            epubReaderEngine.ioDispatcher = value
            azw3ReaderEngine.ioDispatcher = value
            cbzReaderEngine.ioDispatcher = value
        }

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val ttsSessionState: StateFlow<TtsSessionState> = ttsEngineWrapper.state

    private var progressSaveJob: Job? = null
    private var annotationsJob: Job? = null
    private var initialLocator: String? = null
    private var loadedBookId: String? = null

    init {
        viewModelScope.launch {
            ttsEngineWrapper.state.collect { ttsState ->
                _uiState.update {
                    it.copy(
                        isTtsActive = ttsState.isActive,
                        ttsSentenceHighlight = if (ttsState.isPlaying) ttsState.currentSentence else null
                    )
                }
            }
        }
    }

    fun loadBook(bookId: String, targetLocator: String? = null) {
        // Configuration changes recompose the reader and restart its LaunchedEffect. Re-opening
        // the book would close the live Publication out from under the restored navigator
        // (its WebView still streams resources), so a session is only opened once per ViewModel.
        val current = _uiState.value
        if (loadedBookId == bookId && current.book != null && current.errorMessage == null) {
            return
        }
        loadedBookId = bookId

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
            val locatorToUse = targetLocator ?: savedProgress?.locator
            initialLocator = locatorToUse

            // Set book immediately upon retrieval from repository
            _uiState.update {
                it.copy(
                    book = book,
                    format = book.format,
                    currentLocator = locatorToUse,
                    progressPercentage = if (targetLocator != null) it.progressPercentage else (savedProgress?.percentage ?: 0f),
                    currentPage = if (targetLocator != null) it.currentPage else (savedProgress?.currentPage ?: 0)
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
                BookFormat.EPUB -> initEpub(book, savedProgress, locatorToUse)
                BookFormat.CBZ -> initCbz(book, savedProgress, locatorToUse)
                BookFormat.AZW3 -> initAzw3(book, savedProgress, locatorToUse)
            }
        }
    }

    fun getActiveReadiumEngine(): ReadiumReaderEngine? = when (_uiState.value.format) {
        BookFormat.AZW3 -> azw3ReaderEngine
        BookFormat.EPUB -> epubReaderEngine
        else -> null
    }

    private suspend fun initEpub(book: Book, savedProgress: ReadingProgress?, targetLocator: String? = null) {
        val result = epubReaderEngine.openBook(book)
        if (result.isSuccess) {
            val engineState = epubReaderEngine.state.value
            val toc = if (engineState is ReaderState.Ready) engineState.tableOfContents else emptyList()
            val totalSpine = if (engineState is ReaderState.Ready) engineState.totalPages else 0
            val locatorToUse = targetLocator ?: savedProgress?.locator

            _uiState.update {
                it.copy(
                    isLoading = false,
                    book = book,
                    format = book.format,
                    tableOfContents = toc,
                    totalPages = totalSpine,
                    currentLocator = locatorToUse,
                    progressPercentage = if (targetLocator != null) it.progressPercentage else (savedProgress?.percentage ?: 0f),
                    currentPage = if (targetLocator != null) it.currentPage else (savedProgress?.currentPage ?: 0)
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

    private suspend fun initAzw3(book: Book, savedProgress: ReadingProgress?, targetLocator: String? = null) {
        val result = azw3ReaderEngine.openBook(book)
        if (result.isSuccess) {
            val engineState = azw3ReaderEngine.state.value
            val toc = if (engineState is ReaderState.Ready) engineState.tableOfContents else emptyList()
            val totalSpine = if (engineState is ReaderState.Ready) engineState.totalPages else 0
            val locatorToUse = targetLocator ?: savedProgress?.locator

            _uiState.update {
                it.copy(
                    isLoading = false,
                    book = book,
                    format = BookFormat.AZW3,
                    tableOfContents = toc,
                    totalPages = totalSpine,
                    currentLocator = locatorToUse,
                    progressPercentage = if (targetLocator != null) it.progressPercentage else (savedProgress?.percentage ?: 0f),
                    currentPage = if (targetLocator != null) it.currentPage else (savedProgress?.currentPage ?: 0)
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Không thể mở sách AZW3"
                )
            }
        }
    }

    private suspend fun initCbz(book: Book, savedProgress: ReadingProgress?, targetLocator: String? = null) {
        val result = cbzReaderEngine.openBook(book)
        if (result.isSuccess) {
            val archive = cbzReaderEngine.getArchive()
            val totalPages = archive?.pageCount ?: 0
            val targetPage = targetLocator?.substringAfter("page://", "")?.toIntOrNull()
            val initialPage = targetPage ?: (savedProgress?.currentPage ?: 0)
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

    fun updateTapZoneMode(mode: ReaderTapZoneMode) {
        _uiState.update { state ->
            state.copy(preferences = state.preferences.copy(tapZoneMode = mode.name))
        }
    }

    fun updatePageTurnEffect(effect: ReaderPageTurnEffect) {
        _uiState.update { state ->
            state.copy(preferences = state.preferences.copy(pageTurnEffect = effect.name))
        }
    }

    fun updateHapticsEnabled(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(preferences = state.preferences.copy(hapticsEnabled = enabled))
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

    fun startTts(context: Context) {
        val book = _uiState.value.book ?: return
        if (book.format == BookFormat.CBZ) return

        viewModelScope.launch(ioDispatcher) {
            val publication = getActiveReadiumEngine()?.getPublication()
            val readingOrder = publication?.readingOrder ?: emptyList()
            val currentPosition = _uiState.value.currentPage.coerceIn(0, (readingOrder.size - 1).coerceAtLeast(0))
            val currentLink = readingOrder.getOrNull(currentPosition) ?: readingOrder.firstOrNull()

            var chapterText = ""
            if (currentLink != null && publication != null) {
                try {
                    val resource = publication.get(currentLink)
                    val rawBytes = resource?.read()?.getOrNull()
                    if (rawBytes != null) {
                        val html = rawBytes.toString(Charsets.UTF_8)
                        chapterText = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    }
                } catch (_: Throwable) {}
            }

            if (chapterText.isBlank()) {
                chapterText = "${book.title}. ${_uiState.value.currentChapterTitle}"
            }

            withContext(Dispatchers.Main) {
                ttsEngineWrapper.loadContent(
                    bookId = book.id,
                    bookTitle = book.title,
                    chapterTitle = _uiState.value.currentChapterTitle,
                    rawText = chapterText
                )
                ttsEngineWrapper.play()

                val intent = Intent(context, TtsService::class.java).apply {
                    action = TtsService.ACTION_PLAY
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (_: Throwable) {}

                _uiState.update { it.copy(isTtsActive = true) }
            }
        }
    }

    fun toggleTtsPlayPause() {
        if (ttsEngineWrapper.state.value.isPlaying) {
            ttsEngineWrapper.pause()
        } else {
            ttsEngineWrapper.resume()
        }
    }

    fun nextTtsSentence() {
        ttsEngineWrapper.next()
    }

    fun previousTtsSentence() {
        ttsEngineWrapper.previous()
    }

    fun seekTtsSentence(index: Int) {
        ttsEngineWrapper.seekTo(index)
    }

    fun setTtsSpeed(speed: Float) {
        ttsEngineWrapper.setSpeed(speed)
    }

    fun stopTts() {
        ttsEngineWrapper.stop()
        _uiState.update { it.copy(isTtsActive = false, ttsSentenceHighlight = null) }
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
        ttsEngineWrapper.stop()
        epubReaderEngine.closeBookSync()
        azw3ReaderEngine.closeBookSync()
        cbzReaderEngine.closeBookSync()
    }
}
