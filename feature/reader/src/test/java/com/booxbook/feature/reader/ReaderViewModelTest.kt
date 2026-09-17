package com.booxbook.feature.reader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.database.entity.BookWithProgress
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.engine.cbz.CbzArchiveExtractor
import com.booxbook.core.engine.cbz.CbzReaderEngine
import com.booxbook.core.engine.epub.EpubReaderEngine
import com.booxbook.core.engine.epub.ReadiumAssetRetriever
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
    private lateinit var cbzEngine: CbzReaderEngine
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
        epubEngine = EpubReaderEngine(context, assetRetriever)
        val cbzExtractor = CbzArchiveExtractor(context)
        cbzEngine = CbzReaderEngine(context, cbzExtractor)

        viewModel = ReaderViewModel(fakeRepository, epubEngine, cbzEngine).apply {
            ioDispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadBook with unknown ID updates error state`() = runTest {
        viewModel.loadBook("non-existent-id")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertEquals("Không tìm thấy cuốn sách này", state.errorMessage)
    }

    @Test
    fun `loadBook with valid CBZ initializes engine and populates state`() = runTest {
        viewModel.loadBook(sampleCbzBook.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        println("DEBUG: isLoading=${state.isLoading}, error=${state.errorMessage}, totalPages=${state.totalPages}, tocSize=${state.tableOfContents.size}")
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("cbz-1", state.book?.id)
        assertEquals(BookFormat.CBZ, state.format)
        assertEquals(2, state.totalPages)
        assertEquals(2, state.tableOfContents.size)
    }

    @Test
    fun `controls toggle and sheet management`() = runTest {
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
    fun `preferences update correctly`() = runTest {
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
    fun `onPageChanged updates state and debounces progress save to Room`() = runTest {
        viewModel.loadBook(sampleCbzBook.id)
        advanceUntilIdle()

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

        // Progress has not saved immediately before 500ms debounce
        advanceTimeBy(200)
        assertNull(fakeRepository.savedProgresses.find { it.currentPage == 1 })

        // After 500ms debounce completes
        advanceTimeBy(350)
        advanceUntilIdle()

        val saved = fakeRepository.savedProgresses.find { it.currentPage == 1 }
        assertNotNull(saved)
        assertEquals("cbz-1", saved?.bookId)
        assertEquals("page://1", saved?.locator)
    }

    @Test
    fun `toggleBookmark creates and removes bookmark annotation`() = runTest {
        viewModel.loadBook(sampleCbzBook.id)
        advanceUntilIdle()

        viewModel.onPageChanged(pageIndex = 0, totalPages = 2, locator = "page://0", chapterTitle = "Trang 1")
        advanceUntilIdle()

        // Toggle on
        viewModel.toggleBookmark()
        advanceUntilIdle()

        assertEquals(1, fakeRepository.annotationsFlow.value.size)
        val created = fakeRepository.annotationsFlow.value.first()
        assertEquals(AnnotationType.BOOKMARK, created.type)
        assertEquals("page://0", created.locator)

        // Toggle off
        viewModel.toggleBookmark()
        advanceUntilIdle()

        assertEquals(0, fakeRepository.annotationsFlow.value.size)
    }

    @Test
    fun `deleteAnnotation removes annotation from repository`() = runTest {
        viewModel.loadBook(sampleCbzBook.id)
        advanceUntilIdle()

        viewModel.onPageChanged(pageIndex = 0, totalPages = 2, locator = "page://0")
        viewModel.toggleBookmark()
        advanceUntilIdle()

        val bookmark = fakeRepository.annotationsFlow.value.first()
        viewModel.deleteAnnotation(bookmark)
        advanceUntilIdle()

        assertTrue(fakeRepository.annotationsFlow.value.isEmpty())
    }

    @Test
    fun `onCleared closes reader engines synchronously`() = runTest {
        viewModel.loadBook(sampleCbzBook.id)
        advanceUntilIdle()

        // Engines are active
        assertNotNull(cbzEngine.getArchive())

        // Clear viewmodel directly
        val onClearedMethod = viewModel.javaClass.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        // Engines are closed
        assertNull(cbzEngine.getArchive())
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
}
