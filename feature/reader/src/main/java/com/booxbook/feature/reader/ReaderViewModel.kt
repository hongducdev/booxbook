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
import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.ReaderState
import com.booxbook.core.engine.model.ReadingFrame
import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.BookReview
import com.booxbook.core.model.ReadingProgress
import com.booxbook.core.model.bookends.BookendsAnnotationCounts
import com.booxbook.core.model.bookends.BookendsChapterIndex
import com.booxbook.feature.reader.bookends.BookendsChapterIndexFactory
import com.booxbook.feature.reader.bookends.BookendsReadingContext
import com.booxbook.feature.reader.bookends.BookendsSessionProgress
import com.booxbook.feature.reader.preferences.ReaderPreferencesManager
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
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.mediatype.MediaType
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val preferencesManager: ReaderPreferencesManager,
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

    /**
     * Nguồn thời gian của bộ theo dõi phiên đọc.
     *
     * Có khe cắm riêng vì test điều khiển được thời gian **ảo** của coroutine scheduler nhưng không điều khiển
     * được `System.currentTimeMillis()`. Muốn khoá hành vi "thời gian nhàn rỗi không phải thời gian đọc" thì
     * phải đẩy được đồng hồ, nếu không test chỉ kiểm tra được rằng hàm chạy mà không sập.
     */
    internal var clock: () -> Long = System::currentTimeMillis

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
    private var reviewJob: Job? = null
    private var canvasReadyFallbackJob: Job? = null
    private var initialLocator: String? = null
    private var loadedBookId: String? = null
    private var appContext: Context? = null
    private var currentTtsSpineIndex: Int = 0

    // ── Bookends ────────────────────────────────────────────────────────────────────────────────
    private val _bookendsContext = MutableStateFlow(BookendsReadingContext())

    /**
     * Vị trí đọc hiện tại, dưới dạng mà lớp overlay cần.
     *
     * Chỉ màn đọc biết được sự kết hợp này: mục lục nằm trong engine, số trang hiển thị nằm ở `uiState`,
     * còn số chú thích nằm trong luồng annotations. Gom lại ở đây để `BookendsViewModel` không phải móc
     * vào ba nguồn khác nhau.
     */
    val bookendsContext: StateFlow<BookendsReadingContext> = _bookendsContext.asStateFlow()

    private val _bookendsSessionProgress = MutableStateFlow(BookendsSessionProgress())

    /** Tiến trình của phiên đọc đang diễn ra, cho `%session_time` và `%session_pages`. */
    val bookendsSessionProgress: StateFlow<BookendsSessionProgress> = _bookendsSessionProgress.asStateFlow()

    private var bookendsChapterIndex: BookendsChapterIndex = BookendsChapterIndex()
    private var bookendsChapterIndexSource: List<TocItem>? = null
    private var bookendsSessionPageTurns: Int = 0
    private var displayPageCountJob: Job? = null

    /**
     * Ảnh chụp cài đặt **ảnh hưởng tới số trang**.
     *
     * Cỡ chữ làm Readium dàn lại trang nên `displayPageCount` có thể đổi. Lề thì **không** nằm ở đây: nó là
     * padding Compose, và `EpubReaderContainer` báo Readium dàn lại khi vùng đọc đổi kích thước.
     */
    private data class TypographyKey(
        val fontSize: Double,
        val fontFamily: String?
    )

    private var lastTypographyKey: TypographyKey? = null

    /** Chỉ tính lại số trang sau khi đã mở sách — trước đó chưa có publication để hỏi. */
    private var hasOpenedBook: Boolean = false

    /**
     * Đánh giá của cuốn đang mở, giữ lại để dựng snapshot cho token `%rating`.
     *
     * Lưu thành trường thay vì đọc lại trong `refreshBookendsContext` vì hàm đó chạy mỗi lần lật trang, còn
     * đánh giá chỉ đổi khi người đọc chấm điểm.
     */
    private var loadedReview: BookReview? = null

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

        /**
         * Độ trễ trước khi tính lại tổng số trang sau khi cài đặt dàn trang đổi.
         *
         * Đủ dài để gộp một loạt thay đổi từ thanh trượt, đủ ngắn để người dùng không kịp thấy thiếu số.
         */
        const val PAGE_COUNT_DEBOUNCE_MS = 700L
    }

    init {
        // Cài đặt hiển thị đến từ `ReaderPreferencesManager` — nguồn duy nhất mà cả màn đọc lẫn tab Cài đặt
        // cùng đọc/ghi. Trước đây `ReaderViewModel` giữ bản sao riêng trong `uiState` nên thay đổi từ tab Cài
        // đặt không bao giờ tới được màn đọc.
        viewModelScope.launch {
            preferencesManager.preferences.collect { preferences ->
                val typography = TypographyKey(
                    fontSize = preferences.fontSize,
                    fontFamily = preferences.fontFamily
                )

                // Lần phát đầu tiên là giá trị nạp từ đĩa lúc khởi tạo, không phải người dùng vừa đổi.
                val changed = lastTypographyKey != null && typography != lastTypographyKey
                lastTypographyKey = typography

                _uiState.update {
                    it.copy(
                        preferences = preferences,
                        themePreset = preferences.toThemePreset()
                    )
                }

                if (changed && hasOpenedBook) scheduleDisplayPageCountRefresh()
            }
        }

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
                    refreshBookendsContext()
                }
            }

            reviewJob?.cancel()
            reviewJob = viewModelScope.launch {
                bookRepository.getReview(bookId).collect { review ->
                    loadedReview = review
                    refreshBookendsContext()
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
        refreshBookendsContext()
        hasOpenedBook = true
        scheduleDisplayPageCountRefresh()
        canvasReadyFallbackJob?.cancel()
        canvasReadyFallbackJob = viewModelScope.launch {
            delay(CANVAS_READY_FALLBACK_MS)
            onCanvasReady()
        }
    }

    private fun emitFeedback(feedback: ReaderFeedback) {
        _feedback.tryEmit(feedback)
    }

    // ── Bookends ────────────────────────────────────────────────────────────────────────────────

    /**
     * Hẹn tính lại tổng số trang sau khi cài đặt dàn trang đổi.
     *
     * Có độ trễ vì người dùng thường kéo thanh trượt qua nhiều giá trị liên tiếp: tính `positions()` cho mỗi
     * bước kéo sẽ mở lại từng tệp chương hàng chục lần.
     *
     * **Không** đặt `displayPageCount` về 0 trong lúc chờ: Readium ước lượng vị trí theo từng tệp chương nên
     * con số này thường không đổi khi lề hay cỡ chữ thay đổi, và đặt về 0 chỉ làm token nháy mỗi lần kéo.
     */
    private fun scheduleDisplayPageCountRefresh() {
        displayPageCountJob?.cancel()
        displayPageCountJob = viewModelScope.launch {
            delay(PAGE_COUNT_DEBOUNCE_MS)
            readDisplayPageCount()
        }
    }

    /**
     * Đọc tổng số trang thật của cuốn sách từ `Publication.positions()`.
     *
     * Chạy nền và không chặn màn đọc: Readium phải dàn trang từng tệp chương để đếm vị trí, việc này tốn thời
     * gian với sách dài. Trong lúc chờ, `displayPageCount` bằng 0 nên token tự ẩn — chưa có số vẫn tốt hơn
     * hiện một tổng số sai (đã từng hiện `11 / 8` khi lấy nhầm `PaginationListener`).
     *
     * Tự bỏ qua với CBZ: `getActiveReadiumEngine()` trả `null` nên không cần nhánh riêng ở nơi gọi.
     */
    private suspend fun readDisplayPageCount() {
        val publication = getActiveReadiumEngine()?.getPublication() ?: return
        val count = withContext(ioDispatcher) {
            runCatching { publication.positions().size }.getOrNull()
        } ?: return

        if (count <= 0) return
        _uiState.update { it.copy(displayPageCount = count) }
        refreshBookendsContext()
    }

    private fun refreshBookendsContext() {
        val state = _uiState.value
        val book = state.book
        if (book == null) {
            _bookendsContext.value = BookendsReadingContext()
            return
        }

        _bookendsContext.value = BookendsReadingContext(
            bookId = book.id,
            title = book.title,
            author = book.author,
            format = book.format.displayName,
            filePath = book.filePath,
            fileExtension = book.format.extension,
            series = book.series.orEmpty(),
            seriesLabel = book.seriesLabel,
            seriesIndex = book.seriesIndex.orEmpty(),
            tags = book.tags,
            description = book.description.orEmpty(),
            language = book.language.orEmpty(),
            rating = loadedReview?.rating ?: 0,
            addedMillis = book.addedTimestamp,
            fileSizeBytes = book.fileSize,
            pageNum = displayedPageNumber(state),
            pageCount = displayedPageCount(state),
            progression = state.progressPercentage.toDouble(),
            chapterTitle = state.currentChapterTitle,
            chapters = chapterIndexFor(state.tableOfContents, book.format),
            annotations = BookendsAnnotationCounts(
                highlights = state.annotations.count { it.type == AnnotationType.HIGHLIGHT },
                notes = state.annotations.count { it.type == AnnotationType.NOTE },
                bookmarks = state.annotations.count { it.type == AnnotationType.BOOKMARK }
            )
        )
    }

    /**
     * Số trang người đọc nhìn thấy.
     *
     * CBZ đếm từ 0 trong state nên phải cộng 1. EPUB/AZW3 đã có sẵn số đúng trong [ReaderUiState.currentPage]
     * — đó chính là `Locator.locations.position`, chỉ số trang đếm từ 1 trên toàn publication, cùng thang đo
     * với [ReaderUiState.displayPageCount].
     */
    private fun displayedPageNumber(state: ReaderUiState): Int = when (state.format) {
        BookFormat.CBZ -> state.currentPage + 1
        else -> state.currentPage
    }

    private fun displayedPageCount(state: ReaderUiState): Int = when (state.format) {
        BookFormat.CBZ -> state.totalPages
        else -> state.displayPageCount
    }

    /**
     * Chỉ mục chương, dựng lại chỉ khi danh sách mục lục đổi.
     *
     * Dựng lại là việc phải tránh: ánh xạ từng `href` sang thứ tự đọc là phép tìm tuyến tính, và hàm này
     * được gọi mỗi lần lật trang.
     */
    private fun chapterIndexFor(tableOfContents: List<TocItem>, format: BookFormat): BookendsChapterIndex {
        if (bookendsChapterIndexSource === tableOfContents) return bookendsChapterIndex

        val readingOrderHrefs = if (format == BookFormat.CBZ) {
            emptyList()
        } else {
            getActiveReadiumEngine()?.getPublication()?.readingOrder?.map { it.href.toString() }.orEmpty()
        }

        bookendsChapterIndex = BookendsChapterIndexFactory.build(
            tableOfContents = tableOfContents,
            readingOrderHrefs = readingOrderHrefs,
            cbzPageCount = if (format == BookFormat.CBZ) _uiState.value.totalPages else 0
        )
        bookendsChapterIndexSource = tableOfContents
        return bookendsChapterIndex
    }

    private fun publishBookendsSessionProgress() {
        _bookendsSessionProgress.value = BookendsSessionProgress(
            seconds = accumulatedActiveDurationMs / 1000L,
            pageTurns = bookendsSessionPageTurns
        )
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
        val previousPage = _uiState.value.currentPage

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

        // `onPageChanged` còn được gọi khi chỉ đổi định dạng chữ (cùng trang), nên số lần lật trang chỉ tăng
        // khi vị trí thật sự khác đi — nếu không, `%session_pages` sẽ nhảy vọt mỗi lần chỉnh cỡ chữ.
        if (pageIndex != previousPage) {
            bookendsSessionPageTurns++
            publishBookendsSessionProgress()
        }
        refreshBookendsContext()

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

    fun updateFontSize(delta: Double) = preferencesManager.setFontSizeDelta(delta)

    fun updateFontFamily(font: String?) = preferencesManager.setFontFamily(font)

    fun updateThemePreset(preset: ReaderThemePreset) = preferencesManager.setThemePreset(
        presetName = preset.name,
        isDark = preset == ReaderThemePreset.DARK || preset == ReaderThemePreset.AMOLED
    )

    fun updateTapZoneMode(mode: ReaderTapZoneMode) = preferencesManager.setTapZoneMode(mode.name)

    fun updatePageTurnEffect(effect: ReaderPageTurnEffect) = preferencesManager.setPageTurnEffect(effect.name)

    fun updateHapticsEnabled(enabled: Boolean) = preferencesManager.setHapticsEnabled(enabled)

    fun updateMargin(side: ReaderPreferencesManager.MarginSide, valueDp: Float) =
        preferencesManager.setMargin(side, valueDp)

    fun resetMargins() = preferencesManager.resetMargins()

    fun updateFrame(transform: (ReadingFrame) -> ReadingFrame) =
        preferencesManager.updateFrame(transform)

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
        val now = clock()
        sessionStartTimeMs = now
        lastActiveTimeMs = now
        accumulatedActiveDurationMs = 0L
        isSessionPaused = false
        bookendsSessionPageTurns = 0
        publishBookendsSessionProgress()

        periodicFlushJob?.cancel()
        periodicFlushJob = viewModelScope.launch(ioDispatcher) {
            // Perpetual by design: the loop always keeps one `delay()` queued, so it never runs out of
            // work and is only stopped by `release()` or `flushReadingSession(isEnding = true)`.
            //
            // The tick only *persists* what has already been accumulated. It must not accumulate on its own:
            // it fires on the wall clock, not on anything the reader did.
            while (isActive) {
                delay(PERIODIC_FLUSH_INTERVAL_MS)
                flushReadingSession(isEnding = false)
            }
        }
    }

    /**
     * Ghi nhận **một tương tác thật** của người đọc.
     *
     * Đây là nơi duy nhất được phép đẩy [lastActiveTimeMs] tiến lên. [flushReadingSession] cố ý không làm
     * việc đó: nó chạy theo đồng hồ treo tường (mỗi 60 giây), còn đây là hành động của người đọc.
     */
    fun recordUserInteraction() {
        accumulateActiveTime()
        lastActiveTimeMs = clock()
        isSessionPaused = false
        publishBookendsSessionProgress()
    }

    /**
     * Cộng dồn thời gian đã trôi qua kể từ mốc hoạt động gần nhất.
     *
     * Khoảng trống dài hơn [INACTIVITY_TIMEOUT_MS] chỉ được tính đúng [INACTIVITY_TIMEOUT_MS]: người đọc gấp
     * máy rồi mở lại sau ba tiếng thì ba tiếng đó không phải thời gian đọc. Trừ khi TTS đang chạy — lúc đó máy
     * vẫn đang đọc thành tiếng cho người dùng nghe, nên thời gian đó là thời gian đọc thật.
     */
    private fun accumulateActiveTime() {
        if (isSessionPaused || lastActiveTimeMs <= 0L) return
        val elapsed = clock() - lastActiveTimeMs
        val isTtsActive = ttsEngineWrapper.state.value.isPlaying
        accumulatedActiveDurationMs += if (elapsed <= INACTIVITY_TIMEOUT_MS || isTtsActive) {
            elapsed
        } else {
            INACTIVITY_TIMEOUT_MS
        }
    }

    fun pauseReadingSession() {
        if (isSessionPaused) return
        accumulateActiveTime()
        isSessionPaused = true
        flushReadingSession(isEnding = false)
    }

    fun resumeReadingSession() {
        lastActiveTimeMs = clock()
        isSessionPaused = false
    }

    /**
     * Ghi phần thời gian đã cộng dồn xuống Room.
     *
     * **Không** cộng thêm thời gian trôi qua kể từ tương tác cuối. Bản trước có cộng, và vì hàm này được gọi
     * mỗi 60 giây bởi [periodicFlushJob], để màn đọc mở không tương tác vẫn sinh ra 60 giây "thời gian đọc"
     * mỗi phút — quan sát được trên máy thật: 11 trang nhưng tích 4,5 giờ.
     */
    fun flushReadingSession(isEnding: Boolean = false) {
        val bookId = loadedBookId ?: return
        val now = clock()

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
        publishBookendsSessionProgress()
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
        reviewJob?.cancel()
        canvasReadyFallbackJob?.cancel()
        canvasReadyFallbackJob = null
        displayPageCountJob?.cancel()
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
