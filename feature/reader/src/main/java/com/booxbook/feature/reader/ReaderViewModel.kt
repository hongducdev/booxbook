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
import com.booxbook.core.tts.model.TtsSentence
import com.booxbook.core.tts.model.TtsSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.mediatype.MediaType
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

    /**
     * Phản hồi ngắn hạn cho các thao tác của người đọc, hiển thị bằng snackbar ở [ReaderScreen].
     *
     * Buffer nhỏ nhưng khác `0` để [emitFeedback] không bao giờ chặn luồng gọi: `tryEmit` chỉ thất bại
     * khi buffer đầy *và* không có ai thu thập, tình huống không xảy ra khi màn đọc còn sống.
     */
    private val _feedback = MutableSharedFlow<ReaderFeedback>(extraBufferCapacity = 8)
    val feedback: SharedFlow<ReaderFeedback> = _feedback.asSharedFlow()

    val ttsSessionState: StateFlow<TtsSessionState> = ttsEngineWrapper.state

    private var progressSaveJob: Job? = null
    private var annotationsJob: Job? = null
    private var canvasReadyFallbackJob: Job? = null
    private var initialLocator: String? = null
    private var loadedBookId: String? = null
    private var appContext: Context? = null
    private var currentTtsSpineIndex: Int = 0

    // Reading Time Tracking State
    private var sessionStartTimeMs: Long = 0L
    private var lastActiveTimeMs: Long = 0L
    private var accumulatedActiveDurationMs: Long = 0L
    private var isSessionPaused: Boolean = false
    private var periodicFlushJob: Job? = null

    companion object {
        const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L // 5 phút không tương tác
        const val PERIODIC_FLUSH_INTERVAL_MS = 60 * 1000L // 60 giây định kỳ

        /**
         * Thời gian tối đa còn chờ trang đọc đầu tiên trước khi tự đóng màn chờ.
         *
         * Trang đọc báo sẵn sàng qua [onCanvasReady]; hạn này chỉ là lưới an toàn cho những trường hợp
         * không bao giờ có tín hiệu (sách không có reading order, WebView không phát sự kiện…), để
         * người đọc không bị kẹt vĩnh viễn sau một lớp phủ.
         */
        const val CANVAS_READY_FALLBACK_MS = 2500L
    }

    init {
        viewModelScope.launch {
            ttsEngineWrapper.state.collect { ttsState ->
                _uiState.update {
                    it.copy(
                        isTtsActive = ttsState.isActive,
                        ttsSentenceHighlight = if (ttsState.isPlaying) ttsState.currentSentence else null,
                        ttsSentenceLocator = if (ttsState.isPlaying) ttsState.currentLocator else null
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
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    loadingPhase = ReaderLoadingPhase.LOADING_BOOK
                )
            }

            val book = withContext(ioDispatcher) {
                bookRepository.getBookByIdSync(bookId)
            }

            if (book == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingPhase = ReaderLoadingPhase.READY,
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

            // Đã biết sách: màn chờ có thể hiện bìa, tựa đề và chuyển sang bước mở publication.
            _uiState.update {
                it.copy(
                    book = book,
                    format = book.format,
                    loadingPhase = ReaderLoadingPhase.OPENING_PUBLICATION,
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

            // Start tracking reading time
            startReadingSession(bookId)
        }
    }

    fun getActiveReadiumEngine(): ReadiumReaderEngine? = when (_uiState.value.format) {
        BookFormat.AZW3 -> azw3ReaderEngine
        BookFormat.EPUB -> epubReaderEngine
        else -> null
    }

    /**
     * Báo rằng trang đọc đầu tiên đã thật sự hiện ra, cho phép màn chờ tan đi.
     *
     * Idempotent: EPUB gọi từ lần đầu nhận locator, CBZ gọi khi trang đầu giải nén xong, và hạn
     * [CANVAS_READY_FALLBACK_MS] gọi như một lưới an toàn — tín hiệu nào tới trước cũng đủ.
     */
    fun onCanvasReady() {
        canvasReadyFallbackJob?.cancel()
        canvasReadyFallbackJob = null
        _uiState.update {
            if (it.loadingPhase == ReaderLoadingPhase.READY) {
                it
            } else {
                it.copy(loadingPhase = ReaderLoadingPhase.READY)
            }
        }
    }

    /**
     * Chuyển màn chờ sang bước "đang dựng trang đọc" và hẹn giờ tự đóng nếu canvas im lặng.
     */
    private fun beginCanvasBuild() {
        _uiState.update { it.copy(loadingPhase = ReaderLoadingPhase.BUILDING_CANVAS, isLoading = false) }
        canvasReadyFallbackJob?.cancel()
        canvasReadyFallbackJob = viewModelScope.launch {
            delay(CANVAS_READY_FALLBACK_MS)
            onCanvasReady()
        }
    }

    private fun emitFeedback(feedback: ReaderFeedback) {
        _feedback.tryEmit(feedback)
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
            beginCanvasBuild()
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadingPhase = ReaderLoadingPhase.READY,
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
            beginCanvasBuild()
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadingPhase = ReaderLoadingPhase.READY,
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
            beginCanvasBuild()
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadingPhase = ReaderLoadingPhase.READY,
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
            val result = runCatching {
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

            result.onSuccess {
                emitFeedback(
                    if (isCurrentlyBookmarked) ReaderFeedback.BookmarkRemoved
                    else ReaderFeedback.BookmarkAdded
                )
            }.onFailure { error ->
                emitFeedback(
                    ReaderFeedback.Failure(
                        "Không thể cập nhật đánh dấu: ${error.message ?: "lỗi không xác định"}"
                    )
                )
            }
        }
    }

    fun startTts(context: Context) {
        val book = _uiState.value.book ?: return
        if (book.format == BookFormat.CBZ) return
        appContext = context.applicationContext

        ttsEngineWrapper.onChapterFinishedListener = {
            viewModelScope.launch(Dispatchers.Main) {
                advanceTtsToNextChapter()
            }
        }

        loadAndStartTtsForCurrentChapter()
    }

    private fun loadAndStartTtsForCurrentChapter(
        spineIndex: Int? = null,
        initialSentenceIndex: Int? = null
    ) {
        val book = _uiState.value.book ?: return
        if (book.format == BookFormat.CBZ) return

        viewModelScope.launch(ioDispatcher) {
            val publication = getActiveReadiumEngine()?.getPublication()
            if (publication == null) {
                emitFeedback(ReaderFeedback.Failure("Sách chưa sẵn sàng để đọc bằng giọng nói"))
                return@launch
            }
            val readingOrder = publication.readingOrder
            if (readingOrder.isEmpty()) {
                emitFeedback(ReaderFeedback.Failure("Sách này không có nội dung để đọc"))
                return@launch
            }

            // Xác định đúng chương hiện tại dựa trên currentLocator hoặc spineIndex truyền vào
            // Tuyệt đối không dùng currentPage làm index của readingOrder vì currentPage là số trang
            val resolvedSpineIndex = spineIndex ?: run {
                val curLocJson = _uiState.value.currentLocator
                if (!curLocJson.isNullOrBlank()) {
                    val parsedLocator = runCatching { Locator.fromJSON(JSONObject(curLocJson)) }.getOrNull()
                    if (parsedLocator != null) {
                        val locHrefStr = parsedLocator.href.toString().substringBefore("#").trimEnd('/')
                        val foundIndex = readingOrder.indexOfFirst { link ->
                            val linkHrefStr = link.href.toString().substringBefore("#").trimEnd('/')
                            val linkUrlStr = link.url().toString().substringBefore("#").trimEnd('/')
                            linkHrefStr == locHrefStr || linkUrlStr == locHrefStr ||
                                locHrefStr.endsWith("/$linkHrefStr") || linkHrefStr.endsWith("/$locHrefStr") ||
                                locHrefStr.endsWith(linkHrefStr) || linkHrefStr.endsWith(locHrefStr)
                        }
                        if (foundIndex >= 0) foundIndex else 0
                    } else 0
                } else {
                    0
                }
            }

            currentTtsSpineIndex = resolvedSpineIndex.coerceIn(0, readingOrder.size - 1)
            val currentLink = readingOrder.getOrNull(currentTtsSpineIndex) ?: readingOrder.first()

            var chapterText = ""
            val sentencesWithLocators = mutableListOf<TtsSentence>()
            try {
                val resource = publication.get(currentLink)
                val rawBytes = resource?.read()?.getOrNull()
                if (rawBytes != null) {
                    val html = rawBytes.toString(Charsets.UTF_8)
                    val cleanedHtml = html
                        .replace(Regex("(?s)<style.*?>.*?</style>"), "")
                        .replace(Regex("(?s)<script.*?>.*?</script>"), "")
                        .replace(Regex("(?s)<head.*?>.*?</head>"), "")
                    val plainText = android.text.Html.fromHtml(cleanedHtml, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    chapterText = plainText.trim()

                    val tokenized = ttsEngineWrapper.tokenizeSentences(chapterText)
                    val linkUrl = currentLink.url()

                    tokenized.forEachIndexed { idx, sentence ->
                        val sIdx = chapterText.indexOf(sentence)
                        val beforeText = if (sIdx > 0) {
                            val start = (sIdx - 30).coerceAtLeast(0)
                            chapterText.substring(start, sIdx)
                        } else null
                        val afterText = if (sIdx >= 0 && sIdx + sentence.length < chapterText.length) {
                            val end = (sIdx + sentence.length + 30).coerceAtMost(chapterText.length)
                            chapterText.substring(sIdx + sentence.length, end)
                        } else null

                        val locator = Locator(
                            href = linkUrl,
                            mediaType = currentLink.mediaType ?: MediaType.HTML,
                            title = currentLink.title ?: _uiState.value.currentChapterTitle.ifBlank { book.title },
                            text = Locator.Text(
                                before = beforeText,
                                highlight = sentence,
                                after = afterText
                            )
                        )

                        sentencesWithLocators.add(
                            TtsSentence(
                                index = idx,
                                text = sentence,
                                locator = locator.toJSON().toString()
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}

            if (chapterText.isBlank()) {
                chapterText = "${book.title}. ${_uiState.value.currentChapterTitle}"
            }

            // Tìm vị trí câu bắt đầu thông minh từ vị trí trang hiện tại
            var startIndex = initialSentenceIndex ?: 0
            if (initialSentenceIndex == null && sentencesWithLocators.isNotEmpty()) {
                val curLocJson = _uiState.value.currentLocator
                if (!curLocJson.isNullOrBlank()) {
                    try {
                        val curLoc = Locator.fromJSON(JSONObject(curLocJson))
                        val curHighlight = curLoc?.text?.highlight
                        var matchedIdx = -1
                        if (!curHighlight.isNullOrBlank()) {
                            matchedIdx = sentencesWithLocators.indexOfFirst {
                                it.text.contains(curHighlight, ignoreCase = true) ||
                                    curHighlight.contains(it.text, ignoreCase = true)
                            }
                        }
                        if (matchedIdx >= 0) {
                            startIndex = matchedIdx
                        } else {
                            val currentProgression = curLoc?.locations?.progression
                            if (currentProgression != null && currentProgression > 0.0) {
                                val targetIdx = (currentProgression * sentencesWithLocators.size).toInt()
                                    .coerceIn(0, sentencesWithLocators.size - 1)
                                startIndex = targetIdx
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }

            withContext(Dispatchers.Main) {
                val chapterTitle = currentLink.title ?: "Chương ${currentTtsSpineIndex + 1}"
                ttsEngineWrapper.loadContent(
                    bookId = book.id,
                    bookTitle = book.title,
                    chapterTitle = chapterTitle,
                    rawText = chapterText,
                    startIndex = startIndex,
                    preParsedSentences = sentencesWithLocators
                )
                ttsEngineWrapper.play()

                val ctx = appContext
                if (ctx != null) {
                    val intent = Intent(ctx, TtsService::class.java).apply {
                        action = TtsService.ACTION_PLAY
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ctx.startForegroundService(intent)
                        } else {
                            ctx.startService(intent)
                        }
                    } catch (_: Throwable) {}
                }

                _uiState.update {
                    it.copy(
                        isTtsActive = true,
                        currentChapterTitle = chapterTitle
                    )
                }

                emitFeedback(ReaderFeedback.TtsStarted)
            }
        }
    }

    private fun advanceTtsToNextChapter() {
        val publication = getActiveReadiumEngine()?.getPublication() ?: return
        val readingOrder = publication.readingOrder
        val nextSpineIndex = currentTtsSpineIndex + 1
        if (nextSpineIndex < readingOrder.size) {
            val nextLink = readingOrder[nextSpineIndex]
            currentTtsSpineIndex = nextSpineIndex
            val title = nextLink.title ?: "Chương ${nextSpineIndex + 1}"
            _uiState.update {
                it.copy(
                    currentChapterTitle = title
                )
            }
            loadAndStartTtsForCurrentChapter(spineIndex = nextSpineIndex, initialSentenceIndex = 0)
        } else {
            // Đã đến hết cuốn sách: tắt phiên đọc nhưng báo đúng lý do thay vì "đã dừng".
            stopTts(notify = false)
            emitFeedback(ReaderFeedback.BookFinished)
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

    /**
     * Dừng phiên đọc giọng nói.
     *
     * [notify] chỉ chi phối việc có phát snackbar "đã dừng" hay không: khi phiên kết thúc vì hết sách,
     * [advanceTtsToNextChapter] tự báo lý do thật nên không muốn thêm một thông báo trùng nghĩa.
     */
    fun stopTts(notify: Boolean = true) {
        val wasActive = ttsEngineWrapper.state.value.isActive
        ttsEngineWrapper.stop()
        _uiState.update { it.copy(isTtsActive = false, ttsSentenceHighlight = null, ttsSentenceLocator = null) }
        if (notify && wasActive) {
            emitFeedback(ReaderFeedback.TtsStopped)
        }
    }

    fun deleteAnnotation(annotation: Annotation) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { bookRepository.removeAnnotation(annotation.id) }
                .onSuccess { emitFeedback(ReaderFeedback.AnnotationDeleted(annotation)) }
                .onFailure { error ->
                    emitFeedback(
                        ReaderFeedback.Failure(
                            "Không thể xoá ghi chú: ${error.message ?: "lỗi không xác định"}"
                        )
                    )
                }
        }
    }

    /**
     * Khôi phục một ghi chú/đánh dấu vừa bị xoá — đích đến của nút "Hoàn tác" trên snackbar.
     *
     * Bản ghi được ghi lại với đúng `id` cũ: id đó vừa được giải phóng nên ghi đè là vô hại, và nhờ vậy
     * thao tác hoàn tác là idempotent thay vì nhân bản ghi chú.
     */
    fun restoreAnnotation(annotation: Annotation) {
        viewModelScope.launch(ioDispatcher) {
            runCatching { bookRepository.addAnnotation(annotation) }
                .onSuccess { emitFeedback(ReaderFeedback.AnnotationRestored(annotation)) }
                .onFailure { error ->
                    emitFeedback(
                        ReaderFeedback.Failure(
                            "Không thể khôi phục ghi chú: ${error.message ?: "lỗi không xác định"}"
                        )
                    )
                }
        }
    }

    fun startReadingSession(bookId: String) {
        val now = System.currentTimeMillis()
        sessionStartTimeMs = now
        lastActiveTimeMs = now
        accumulatedActiveDurationMs = 0L
        isSessionPaused = false

        periodicFlushJob?.cancel()
        periodicFlushJob = viewModelScope.launch(ioDispatcher) {
            // Perpetual by design: the loop always keeps one `delay()` queued, so it never runs out of
            // work and is only stopped by `release()` or `flushReadingSession(isEnding = true)`.
            while (isActive) {
                delay(PERIODIC_FLUSH_INTERVAL_MS)
                flushReadingSession(isEnding = false)
            }
        }
    }

    fun recordUserInteraction() {
        val now = System.currentTimeMillis()
        if (!isSessionPaused && lastActiveTimeMs > 0) {
            val elapsed = now - lastActiveTimeMs
            val isTtsActive = ttsEngineWrapper.state.value.isPlaying
            if (elapsed <= INACTIVITY_TIMEOUT_MS || isTtsActive) {
                accumulatedActiveDurationMs += elapsed
            } else {
                accumulatedActiveDurationMs += INACTIVITY_TIMEOUT_MS
            }
        }
        lastActiveTimeMs = now
        isSessionPaused = false
    }

    fun pauseReadingSession() {
        if (isSessionPaused) return
        val now = System.currentTimeMillis()
        val isTtsActive = ttsEngineWrapper.state.value.isPlaying
        if (lastActiveTimeMs > 0) {
            val elapsed = now - lastActiveTimeMs
            if (elapsed <= INACTIVITY_TIMEOUT_MS || isTtsActive) {
                accumulatedActiveDurationMs += elapsed
            } else {
                accumulatedActiveDurationMs += INACTIVITY_TIMEOUT_MS
            }
        }
        isSessionPaused = true
        flushReadingSession(isEnding = false)
    }

    fun resumeReadingSession() {
        lastActiveTimeMs = System.currentTimeMillis()
        isSessionPaused = false
    }

    fun flushReadingSession(isEnding: Boolean = false) {
        val bookId = loadedBookId ?: return
        val now = System.currentTimeMillis()

        if (!isSessionPaused && lastActiveTimeMs > 0) {
            val elapsed = now - lastActiveTimeMs
            val isTtsActive = ttsEngineWrapper.state.value.isPlaying
            if (elapsed <= INACTIVITY_TIMEOUT_MS || isTtsActive) {
                accumulatedActiveDurationMs += elapsed
            } else {
                accumulatedActiveDurationMs += INACTIVITY_TIMEOUT_MS
            }
            lastActiveTimeMs = now
        }

        val secondsToRecord = accumulatedActiveDurationMs / 1000L
        if (secondsToRecord >= 1L) {
            val startTime = sessionStartTimeMs
            accumulatedActiveDurationMs = 0L
            sessionStartTimeMs = now
            viewModelScope.launch(ioDispatcher) {
                bookRepository.recordReadingSession(
                    bookId = bookId,
                    startTime = startTime,
                    endTime = now,
                    durationSeconds = secondsToRecord
                )
            }
        }

        if (isEnding) {
            periodicFlushJob?.cancel()
            sessionStartTimeMs = 0L
            lastActiveTimeMs = 0L
            accumulatedActiveDurationMs = 0L
        }
    }

    private fun isBookmarked(annotations: List<Annotation>, locator: String?, page: Int): Boolean {
        if (locator == null) return false
        return annotations.any {
            it.type == AnnotationType.BOOKMARK && (it.locator == locator || it.locator == "page://$page")
        }
    }

    /**
     * Stops reading-session tracking and closes every reader resource.
     *
     * Idempotent, and safe to call from any thread. [onCleared] delegates here so teardown can also be
     * driven explicitly instead of through the Android lifecycle — notably from tests, which must stop
     * the perpetual periodic-flush ticker before the coroutine test scheduler is drained.
     *
     * Note that [viewModelScope] itself is intentionally *not* cancelled here: the final reading session
     * still has to be recorded through it. The Android framework cancels the scope after [onCleared].
     */
    fun release() {
        // Cancels the periodic flush ticker.
        flushReadingSession(isEnding = true)
        progressSaveJob?.cancel()
        annotationsJob?.cancel()
        canvasReadyFallbackJob?.cancel()
        canvasReadyFallbackJob = null
        ttsEngineWrapper.stop()
        epubReaderEngine.closeBookSync()
        azw3ReaderEngine.closeBookSync()
        cbzReaderEngine.closeBookSync()
    }

    override fun onCleared() {
        super.onCleared()
        // Synchronously and deterministically close resources to prevent memory & file descriptor leaks
        release()
    }
}
