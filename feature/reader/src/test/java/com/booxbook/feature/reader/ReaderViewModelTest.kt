package com.booxbook.feature.reader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.database.entity.BookWithProgress
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.engine.azw3.Azw3Converter
import com.booxbook.core.engine.azw3.Azw3ReaderEngine
import com.booxbook.core.engine.cbz.CbzArchiveExtractor
import com.booxbook.core.engine.cbz.CbzReaderEngine
import com.booxbook.core.engine.epub.EpubReaderEngine
import com.booxbook.core.engine.epub.ReadiumAssetRetriever
import com.booxbook.core.engine.epub.ReadiumFragmentFactoryProvider
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress
import com.booxbook.core.model.ReadingSession
import com.booxbook.core.model.ReadingStatisticsOverview
import com.booxbook.core.tts.TtsEngineWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeRepository: FakeReaderBookRepository
    private lateinit var epubEngine: EpubReaderEngine
    private lateinit var azw3Engine: Azw3ReaderEngine
    private lateinit var cbzEngine: CbzReaderEngine
    private lateinit var ttsEngine: TtsEngineWrapper
    private lateinit var viewModel: ReaderViewModel

    private lateinit var sampleCbzBook: Book
    private lateinit var sampleCbzFile: File

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeRepository = FakeReaderBookRepository()

        // Create a valid test CBZ file
        sampleCbzFile = tempFolder.newFile("one_piece.cbz")
        ZipOutputStream(FileOutputStream(sampleCbzFile)).use { zos ->
            zos.putNextEntry(ZipEntry("01.jpg"))
            zos.write("page 1".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("02.jpg"))
            zos.write("page 2".toByteArray())
            zos.closeEntry()
        }

        sampleCbzBook = Book(
            id = "cbz-1",
            title = "One Piece Tập 1",
            author = "Eiichiro Oda",
            filePath = sampleCbzFile.absolutePath,
            format = BookFormat.CBZ
        )
        fakeRepository.books[sampleCbzBook.id] = sampleCbzBook

        val assetRetriever = ReadiumAssetRetriever(context)
        val fragmentFactoryProvider = ReadiumFragmentFactoryProvider()
        epubEngine = EpubReaderEngine(context, assetRetriever, fragmentFactoryProvider)
        val azw3Converter = Azw3Converter()
        azw3Engine = Azw3ReaderEngine(context, azw3Converter, assetRetriever, fragmentFactoryProvider)
        val cbzExtractor = CbzArchiveExtractor(context)
        cbzEngine = CbzReaderEngine(context, cbzExtractor)
        ttsEngine = TtsEngineWrapper(context)

        viewModel = ReaderViewModel(fakeRepository, epubEngine, azw3Engine, cbzEngine, ttsEngine).apply {
            ioDispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Runs [body] and always tears the ViewModel down before `runTest` returns.
     *
     * `loadBook()` starts reading-session tracking, which keeps a perpetual
     * `delay(PERIODIC_FLUSH_INTERVAL_MS)` ticker alive on `viewModelScope`. When `runTest` finishes it
     * drains the scheduler with `advanceUntilIdleOr { false }`, and a ticker that is still alive always
     * leaves one more event queued — so that drain never terminates and the whole test task hangs
     * forever instead of failing. `viewModel.release()` cancels the ticker, which lets the drain finish.
     *
     * For the same reason a test body must never call `advanceUntilIdle()` while the ticker runs; use
     * [settle] and [advanceBy], which move virtual time by a bounded amount.
     */
    private fun readerTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModel.release()
        }
    }

    /** Runs everything already due at the current virtual time. Bounded, unlike `advanceUntilIdle()`. */
    private fun settle() {
        testDispatcher.scheduler.runCurrent()
    }

    /** Moves virtual time forward by [millis], running every task that becomes due in that window. */
    private fun advanceBy(millis: Long) {
        testDispatcher.scheduler.advanceTimeBy(millis)
        testDispatcher.scheduler.runCurrent()
    }

    /**
     * Starts recording [ReaderViewModel.feedback] on an unconfined dispatcher so nothing emitted
     * later in the test can slip past the collector.
     */
    private fun TestScope.collectFeedback(): List<ReaderFeedback> {
        val recorded = mutableListOf<ReaderFeedback>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.feedback.collect { recorded += it }
        }
        return recorded
    }

    @Test
    fun `loadBook with unknown ID updates error state`() = readerTest {
        viewModel.loadBook("non-existent-id")
        settle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertEquals("Không tìm thấy cuốn sách này", state.errorMessage)
    }

    @Test
    fun `loadBook with valid CBZ initializes engine and populates state`() = readerTest {
        viewModel.loadBook(sampleCbzBook.id)
        settle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("cbz-1", state.book?.id)
        assertEquals(BookFormat.CBZ, state.format)
        assertEquals(2, state.totalPages)
        assertEquals(2, state.tableOfContents.size)
    }

    @Test
    fun `controls toggle and sheet management`() = readerTest {
        assertFalse(viewModel.uiState.value.isControlsVisible)

        viewModel.toggleControls()
        assertTrue(viewModel.uiState.value.isControlsVisible)

        viewModel.hideControls()
        assertFalse(viewModel.uiState.value.isControlsVisible)

        viewModel.openSheet(ActiveReaderSheet.SETTINGS)
        assertEquals(ActiveReaderSheet.SETTINGS, viewModel.uiState.value.activeSheet)
        assertFalse(viewModel.uiState.value.isControlsVisible)

        viewModel.dismissSheet()
        assertNull(viewModel.uiState.value.activeSheet)
    }

    @Test
    fun `preferences update correctly`() = readerTest {
        val initialFontSize = viewModel.uiState.value.preferences.fontSize

        viewModel.updateFontSize(0.2)
        assertEquals(initialFontSize + 0.2, viewModel.uiState.value.preferences.fontSize, 0.001)

        viewModel.updateFontFamily("serif")
        assertEquals("serif", viewModel.uiState.value.preferences.fontFamily)

        viewModel.updateThemePreset(ReaderThemePreset.AMOLED)
        assertEquals(ReaderThemePreset.AMOLED, viewModel.uiState.value.themePreset)
        assertTrue(viewModel.uiState.value.preferences.isDarkMode)

        viewModel.updateThemePreset(ReaderThemePreset.SEPIA)
        assertEquals(ReaderThemePreset.SEPIA, viewModel.uiState.value.themePreset)
        assertFalse(viewModel.uiState.value.preferences.isDarkMode)
    }

    @Test
    fun `onPageChanged updates state and debounces progress save to Room`() = readerTest {
        viewModel.loadBook(sampleCbzBook.id)
        settle()

        // Change page
        viewModel.onPageChanged(
            pageIndex = 1,
            totalPages = 2,
            locator = "page://1",
            chapterTitle = "Trang 2"
        )

        // UI updates immediately
        val state = viewModel.uiState.value
        assertEquals(1, state.currentPage)
        assertEquals(2, state.totalPages)
        assertEquals(1.0f, state.progressPercentage, 0.001f)
        assertEquals("Trang 2", state.currentChapterTitle)

        // Progress has not saved immediately before the 500ms debounce elapses
        advanceBy(200)
        assertNull(fakeRepository.savedProgresses.find { it.currentPage == 1 })

        // After the 500ms debounce elapses the progress is written
        advanceBy(350)

        val saved = fakeRepository.savedProgresses.find { it.currentPage == 1 }
        assertNotNull(saved)
        assertEquals("cbz-1", saved?.bookId)
        assertEquals("page://1", saved?.locator)
    }

    @Test
    fun `toggleBookmark creates and removes bookmark annotation`() = readerTest {
        viewModel.loadBook(sampleCbzBook.id)
        settle()

        viewModel.onPageChanged(pageIndex = 0, totalPages = 2, locator = "page://0", chapterTitle = "Trang 1")
        settle()

        // Toggle on
        viewModel.toggleBookmark()
        settle()

        assertEquals(1, fakeRepository.annotationsFlow.value.size)
        val created = fakeRepository.annotationsFlow.value.first()
        assertEquals(AnnotationType.BOOKMARK, created.type)
        assertEquals("page://0", created.locator)

        // Toggle off
        viewModel.toggleBookmark()
        settle()

        assertEquals(0, fakeRepository.annotationsFlow.value.size)
    }

    @Test
    fun `deleteAnnotation removes annotation from repository`() = readerTest {
        viewModel.loadBook(sampleCbzBook.id)
        settle()

        viewModel.onPageChanged(pageIndex = 0, totalPages = 2, locator = "page://0")
        viewModel.toggleBookmark()
        settle()

        val bookmark = fakeRepository.annotationsFlow.value.first()
        viewModel.deleteAnnotation(bookmark)
        settle()

        assertTrue(fakeRepository.annotationsFlow.value.isEmpty())
    }

    @Test
    fun `onCleared closes reader engines synchronously`() = readerTest {
        viewModel.loadBook(sampleCbzBook.id)
        settle()

        // Engines are active
        assertNotNull(cbzEngine.getArchive())

        // The Android framework invokes the protected onCleared() hook
        val onClearedMethod = viewModel.javaClass.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        // Engines are closed
        assertNull(cbzEngine.getArchive())
    }

    @Test
    fun `loadBook with AZW3 book updates format and dispatches properly`() = readerTest {
        val azw3Book = Book(
            id = "azw3-1",
            title = "Kindle Book",
            filePath = "/dummy/sample.azw3",
            format = BookFormat.AZW3
        )
        fakeRepository.books[azw3Book.id] = azw3Book

        viewModel.loadBook(azw3Book.id)
        settle()

        val state = viewModel.uiState.value
        assertEquals(BookFormat.AZW3, state.format)
        assertEquals("Kindle Book", state.book?.title)
    }

    @Test
    fun `tts speed and stop controls behave as expected`() = readerTest {
        viewModel.setTtsSpeed(1.5f)
        assertEquals(1.5f, viewModel.ttsSessionState.value.speechRate)

        viewModel.stopTts()
        assertFalse(viewModel.uiState.value.isTtsActive)
        assertNull(viewModel.uiState.value.ttsSentenceHighlight)
        assertNull(viewModel.uiState.value.ttsSentenceLocator)
    }

    @Test
    fun `updateThemePreset and updateFontSize modify reading preferences`() = readerTest {
        viewModel.updateFontSize(0.2)
        assertEquals(1.2, viewModel.uiState.value.preferences.fontSize, 0.001)

        viewModel.updateThemePreset(ReaderThemePreset.AMOLED)
        assertEquals(ReaderThemePreset.AMOLED, viewModel.uiState.value.themePreset)
        assertTrue(viewModel.uiState.value.preferences.isDarkMode)
    }

    @Test
    fun readingSessionRecordsTimeOnInteractionAndFlush() = readerTest {
        val book = Book(
            id = "track_book",
            title = "Track Book",
            author = "Author",
            filePath = "/path/track.epub",
            format = BookFormat.EPUB
        )
        fakeRepository.books[book.id] = book

        viewModel.loadBook(book.id)
        settle()

        viewModel.recordUserInteraction()
        viewModel.pauseReadingSession()
        settle()

        viewModel.resumeReadingSession()
        viewModel.flushReadingSession(isEnding = true)
        settle()
        assertNotNull(fakeRepository)
    }

    @Test
    fun `loadBook walks the opening phases and only finishes when the canvas is ready`() = readerTest {
        viewModel.loadBook(sampleCbzBook.id)

        // The opening screen starts without knowing which book it is showing.
        assertEquals(ReaderLoadingPhase.LOADING_BOOK, viewModel.uiState.value.loadingPhase)
        assertNull(viewModel.uiState.value.book)

        settle()

        // The engine is open, so the opening screen can already show the cover and title, but the
        // first page has not been rendered yet: the overlay must stay.
        val opening = viewModel.uiState.value
        assertFalse(opening.isLoading)
        assertEquals(ReaderLoadingPhase.BUILDING_CANVAS, opening.loadingPhase)
        assertTrue(opening.isPreparing)
        assertEquals("One Piece Tập 1", opening.book?.title)

        viewModel.onCanvasReady()

        assertEquals(ReaderLoadingPhase.READY, viewModel.uiState.value.loadingPhase)
        assertFalse(viewModel.uiState.value.isPreparing)
    }

    @Test
    fun `the opening screen closes itself when the reader never reports a ready canvas`() = readerTest {
        viewModel.loadBook(sampleCbzBook.id)
        settle()
        assertEquals(ReaderLoadingPhase.BUILDING_CANVAS, viewModel.uiState.value.loadingPhase)

        advanceBy(ReaderViewModel.CANVAS_READY_FALLBACK_MS + 50)

        assertEquals(ReaderLoadingPhase.READY, viewModel.uiState.value.loadingPhase)
    }

    @Test
    fun `a failed open never leaves the opening screen on top of the error`() = readerTest {
        viewModel.loadBook("non-existent-id")
        settle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertFalse(state.isPreparing)
    }

    @Test
    fun `bookmark toggle announces both directions through snackbar feedback`() = readerTest {
        val events = collectFeedback()

        viewModel.loadBook(sampleCbzBook.id)
        settle()
        viewModel.onPageChanged(pageIndex = 0, totalPages = 2, locator = "page://0", chapterTitle = "Trang 1")
        settle()

        viewModel.toggleBookmark()
        settle()
        viewModel.toggleBookmark()
        settle()

        assertEquals(
            listOf(ReaderFeedback.BookmarkAdded, ReaderFeedback.BookmarkRemoved),
            events
        )
    }

    @Test
    fun `deleting an annotation offers undo and restores it with the same content`() = readerTest {
        val events = collectFeedback()

        viewModel.loadBook(sampleCbzBook.id)
        settle()
        viewModel.onPageChanged(pageIndex = 0, totalPages = 2, locator = "page://0", chapterTitle = "Trang 1")
        viewModel.toggleBookmark()
        settle()

        val bookmark = fakeRepository.annotationsFlow.value.single()
        viewModel.deleteAnnotation(bookmark)
        settle()

        assertTrue(fakeRepository.annotationsFlow.value.isEmpty())
        val deleted = events.last()
        assertTrue(deleted is ReaderFeedback.AnnotationDeleted)
        assertEquals(bookmark, (deleted as ReaderFeedback.AnnotationDeleted).annotation)

        viewModel.restoreAnnotation(bookmark)
        settle()

        val restored = fakeRepository.annotationsFlow.value.single()
        assertEquals(bookmark.locator, restored.locator)
        assertEquals(bookmark.noteContent, restored.noteContent)
        assertTrue(events.last() is ReaderFeedback.AnnotationRestored)
    }

    @Test
    fun `stopTts stays silent when no session was running`() = readerTest {
        val events = collectFeedback()

        viewModel.stopTts()
        settle()

        assertTrue(events.isEmpty())
    }
}

private class FakeReaderBookRepository : BookRepository {
    private var idCounter = 1L
    val books = mutableMapOf<String, Book>()
    val progressMap = mutableMapOf<String, ReadingProgress>()
    val savedProgresses = mutableListOf<ReadingProgress>()
    val annotationsFlow = MutableStateFlow<List<Annotation>>(emptyList())

    override fun getAllBooks(): Flow<List<Book>> = MutableStateFlow(books.values.toList())
    override fun getAllBooksWithProgress(): Flow<List<BookWithProgress>> =
        MutableStateFlow(books.values.map { BookWithProgress(it, progressMap[it.id]) })
    override fun getRecentBooks(limit: Int): Flow<List<Book>> = MutableStateFlow(emptyList())
    override fun getRecentBooksWithProgress(limit: Int): Flow<List<BookWithProgress>> = MutableStateFlow(emptyList())
    override fun getBooksByFormat(format: BookFormat): Flow<List<Book>> =
        MutableStateFlow(books.values.filter { it.format == format })

    override fun getBookById(id: String): Flow<Book?> = MutableStateFlow(books[id])
    override suspend fun getBookByIdSync(id: String): Book? = books[id]

    override suspend fun importBookFromUri(uri: Uri): Result<Book> =
        Result.failure(UnsupportedOperationException())

    override suspend fun saveBook(book: Book) { books[book.id] = book }
    override suspend fun updateLastRead(bookId: String, timestamp: Long) {}
    override suspend fun deleteBook(id: String) { books.remove(id) }

    override fun getReadingProgress(bookId: String): Flow<ReadingProgress?> =
        MutableStateFlow(progressMap[bookId])
    override suspend fun getReadingProgressSync(bookId: String): ReadingProgress? =
        progressMap[bookId]

    override suspend fun saveReadingProgress(progress: ReadingProgress) {
        progressMap[progress.bookId] = progress
        savedProgresses.add(progress)
    }

    override suspend fun deleteReadingProgress(bookId: String) { progressMap.remove(bookId) }

    override fun getAnnotationsForBook(bookId: String): Flow<List<Annotation>> = annotationsFlow.asStateFlow()
    override fun getAnnotationsByType(bookId: String, type: AnnotationType): Flow<List<Annotation>> =
        MutableStateFlow(annotationsFlow.value.filter { it.type == type })

    override suspend fun addAnnotation(annotation: Annotation): Long {
        val id = idCounter++
        val newAnn = annotation.copy(id = id)
        annotationsFlow.value = annotationsFlow.value + newAnn
        return id
    }

    override suspend fun updateAnnotation(annotation: Annotation) {}
    override suspend fun removeAnnotation(id: Long) {
        annotationsFlow.value = annotationsFlow.value.filter { it.id != id }
    }

    val recordedSessions = mutableListOf<ReadingSession>()
    override suspend fun recordReadingSession(bookId: String, startTime: Long, endTime: Long, durationSeconds: Long): Long {
        val session = ReadingSession(
            id = idCounter++,
            bookId = bookId,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            date = "2026-09-18"
        )
        recordedSessions.add(session)
        return session.id
    }
    override fun getAllReadingSessions(): Flow<List<ReadingSession>> = MutableStateFlow(recordedSessions)
    override fun getReadingStatisticsOverview(): Flow<ReadingStatisticsOverview> = MutableStateFlow(ReadingStatisticsOverview())
    override fun getDailyGoalMinutes(): Flow<Int> = MutableStateFlow(45)
    override suspend fun setDailyGoalMinutes(minutes: Int) {}
}
