package com.booxbook.feature.library

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.database.entity.BookWithProgress
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.engine.BookTocExtractor
import com.booxbook.core.engine.azw3.Azw3Converter
import com.booxbook.core.engine.cbz.CbzArchiveExtractor
import com.booxbook.core.engine.epub.ReadiumAssetRetriever
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress
import com.booxbook.feature.library.detail.BookDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BookDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDetailBookRepository
    private lateinit var bookTocExtractor: BookTocExtractor
    private lateinit var viewModel: BookDetailViewModel

    private val sampleBook = Book(
        id = "book-123",
        title = "Đắc Nhân Tâm",
        author = "Dale Carnegie",
        filePath = "/dummy/dac_nhan_tam.epub",
        format = BookFormat.EPUB,
        fileSize = 1024L * 1024L * 2
    )

    private val sampleProgress = ReadingProgress(
        bookId = "book-123",
        locator = "locator-xyz",
        percentage = 0.45f,
        currentPage = 45,
        totalPages = 100
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        fakeRepository = FakeDetailBookRepository()
        fakeRepository.books[sampleBook.id] = sampleBook
        fakeRepository.progressMap[sampleBook.id] = sampleProgress

        val assetRetriever = ReadiumAssetRetriever(context)
        val cbzExtractor = CbzArchiveExtractor(context)
        val azw3Converter = Azw3Converter()
        bookTocExtractor = BookTocExtractor(assetRetriever, cbzExtractor, azw3Converter, context)

        viewModel = BookDetailViewModel(fakeRepository, bookTocExtractor).apply {
            ioDispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadBook with unknown ID updates error state`() = runTest {
        viewModel.loadBook("unknown-id")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertEquals("Không tìm thấy cuốn sách này", state.errorMessage)
    }

    @Test
    fun `loadBook with valid ID populates book and progress`() = runTest {
        viewModel.loadBook(sampleBook.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals("book-123", state.book?.id)
        assertEquals("Đắc Nhân Tâm", state.book?.title)
        assertEquals(0.45f, state.percentage, 0.001f)
        assertEquals(45, state.percentageInt)
        assertTrue(state.hasStartedReading)
        assertFalse(state.isFinished)
    }

    @Test
    fun `resetReadingProgress clears progress state`() = runTest {
        viewModel.loadBook(sampleBook.id)
        advanceUntilIdle()

        assertEquals(0.45f, viewModel.uiState.value.percentage, 0.001f)

        viewModel.resetReadingProgress()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.progress)
        assertEquals(0f, viewModel.uiState.value.percentage, 0.001f)
        assertFalse(viewModel.uiState.value.hasStartedReading)
    }

    @Test
    fun `deleteBook invokes callback upon success`() = runTest {
        viewModel.loadBook(sampleBook.id)
        advanceUntilIdle()

        var deletedCalled = false
        viewModel.deleteBook {
            deletedCalled = true
        }
        advanceUntilIdle()

        assertTrue(deletedCalled)
        assertNull(fakeRepository.books[sampleBook.id])
    }
}

private class FakeDetailBookRepository : BookRepository {
    val books = mutableMapOf<String, Book>()
    val progressMap = mutableMapOf<String, ReadingProgress>()

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
    }

    override suspend fun deleteReadingProgress(bookId: String) {
        progressMap.remove(bookId)
    }

    override fun getAnnotationsForBook(bookId: String): Flow<List<Annotation>> =
        MutableStateFlow(emptyList())
    override fun getAnnotationsByType(bookId: String, type: AnnotationType): Flow<List<Annotation>> =
        MutableStateFlow(emptyList())
    override suspend fun addAnnotation(annotation: Annotation): Long = 1L
    override suspend fun updateAnnotation(annotation: Annotation) {}
    override suspend fun removeAnnotation(id: Long) {}
}
