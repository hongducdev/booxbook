package com.booxbook.feature.library

import android.net.Uri
import com.booxbook.core.database.entity.BookWithProgress
import com.booxbook.core.database.repository.BookRepository
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeBookRepository
    private lateinit var viewModel: LibraryViewModel

    private val sampleBook1 = Book(
        id = "1",
        title = "Đắc Nhân Tâm",
        author = "Dale Carnegie",
        filePath = "/path/dac_nhan_tam.epub",
        format = BookFormat.EPUB
    )

    private val sampleBook2 = Book(
        id = "2",
        title = "One Piece Tập 1",
        author = "Eiichiro Oda",
        filePath = "/path/one_piece.cbz",
        format = BookFormat.CBZ
    )

    private val sampleBook3 = Book(
        id = "3",
        title = "Nhà Giả Kim",
        author = "Paulo Coelho",
        filePath = "/path/nha_gia_kim.azw3",
        format = BookFormat.AZW3
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeBookRepository()
        fakeRepository.booksFlow.value = listOf(sampleBook1, sampleBook2, sampleBook3)
        fakeRepository.recentBooksFlow.value = listOf(sampleBook1)
        fakeRepository.saveProgressSync(ReadingProgress(bookId = "1", locator = "cfi", percentage = 0.45f))

        viewModel = LibraryViewModel(fakeRepository).apply {
            ioDispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState emits all books with reading progress correctly`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertEquals(3, state.books.size)
        assertEquals(1, state.recentBooks.size)
        assertEquals("1", state.recentBooks.first().book.id)
        assertNotNull(state.books.first { it.book.id == "1" }.progress)
        assertEquals(0.45f, state.books.first { it.book.id == "1" }.progress?.percentage)
    }

    @Test
    fun `search query filters books by title and author`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("One Piece")
        advanceUntilIdle()
        val filteredByTitle = viewModel.uiState.value.filteredBooks
        assertEquals(1, filteredByTitle.size)
        assertEquals("One Piece Tập 1", filteredByTitle.first().book.title)

        viewModel.onSearchQueryChanged("Paulo")
        advanceUntilIdle()
        val filteredByAuthor = viewModel.uiState.value.filteredBooks
        assertEquals(1, filteredByAuthor.size)
        assertEquals("Nhà Giả Kim", filteredByAuthor.first().book.title)

        viewModel.onSearchQueryChanged("Không tồn tại")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.filteredBooks.isEmpty())
        assertTrue(viewModel.uiState.value.isSearchEmpty)
    }

    @Test
    fun `format filter correctly filters books by format`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onFormatSelected(BookFormat.CBZ)
        advanceUntilIdle()
        val cbzBooks = viewModel.uiState.value.filteredBooks
        assertEquals(1, cbzBooks.size)
        assertEquals(BookFormat.CBZ, cbzBooks.first().book.format)

        viewModel.onFormatSelected(BookFormat.EPUB)
        advanceUntilIdle()
        val epubBooks = viewModel.uiState.value.filteredBooks
        assertEquals(1, epubBooks.size)
        assertEquals(BookFormat.EPUB, epubBooks.first().book.format)

        viewModel.onFormatSelected(null)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.filteredBooks.size)
    }

    @Test
    fun `select and dismiss book for detail sheet`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        val bookItem = viewModel.uiState.value.books.first()

        viewModel.onBookSelectedForDetail(bookItem)
        assertEquals(bookItem, viewModel.selectedBookForDetail.value)

        viewModel.dismissDetail()
        assertNull(viewModel.selectedBookForDetail.value)
    }

    @Test
    fun `delete book calls repository and updates user message`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        val bookToDelete = sampleBook1
        val bookItem = viewModel.uiState.value.books.first { it.book.id == bookToDelete.id }

        viewModel.onBookSelectedForDetail(bookItem)
        viewModel.deleteBook(bookToDelete)
        advanceUntilIdle()

        assertNull(viewModel.selectedBookForDetail.value)
        assertEquals(2, fakeRepository.booksFlow.value.size)
        assertEquals("Đã xóa \"${bookToDelete.title}\"", viewModel.uiState.value.userMessage)

        viewModel.clearUserMessage()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun `importBooksFromUris handles success and clear state`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val uri = Uri.parse("content://media/book.epub")
        viewModel.importBooksFromUris(listOf(uri))
        advanceUntilIdle()

        val importState = viewModel.uiState.value.importState
        assertTrue(importState is ImportState.Success)
        assertEquals(1, (importState as ImportState.Success).count)

        viewModel.clearImportState()
        advanceUntilIdle()
        assertEquals(ImportState.Idle, viewModel.uiState.value.importState)
    }

    @Test
    fun `importBooksFromUris handles batch result with errors`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        fakeRepository.failNextImport = true
        val uriFail = Uri.parse("content://media/invalid.txt")
        viewModel.importBooksFromUris(listOf(uriFail))
        advanceUntilIdle()

        val errorState = viewModel.uiState.value.importState
        assertTrue(errorState is ImportState.Error)

        viewModel.clearImportState()
        advanceUntilIdle()
        assertEquals(ImportState.Idle, viewModel.uiState.value.importState)
    }
}

private class FakeBookRepository : BookRepository {
    val booksFlow = MutableStateFlow<List<Book>>(emptyList())
    val recentBooksFlow = MutableStateFlow<List<Book>>(emptyList())
    private val progressMap = mutableMapOf<String, ReadingProgress>()
    var failNextImport = false

    fun saveProgressSync(progress: ReadingProgress) {
        progressMap[progress.bookId] = progress
    }

    override fun getAllBooks(): Flow<List<Book>> = booksFlow.asStateFlow()

    override fun getAllBooksWithProgress(): Flow<List<BookWithProgress>> = booksFlow.map { books ->
        books.map { BookWithProgress(it, progressMap[it.id]) }
    }

    override fun getRecentBooks(limit: Int): Flow<List<Book>> = recentBooksFlow.asStateFlow()

    override fun getRecentBooksWithProgress(limit: Int): Flow<List<BookWithProgress>> = recentBooksFlow.map { books ->
        books.map { BookWithProgress(it, progressMap[it.id]) }
    }

    override fun getBooksByFormat(format: BookFormat): Flow<List<Book>> =
        MutableStateFlow(booksFlow.value.filter { it.format == format })

    override fun getBookById(id: String): Flow<Book?> =
        MutableStateFlow(booksFlow.value.firstOrNull { it.id == id })

    override suspend fun getBookByIdSync(id: String): Book? =
        booksFlow.value.firstOrNull { it.id == id }

    override suspend fun importBookFromUri(uri: Uri): Result<Book> {
        if (failNextImport) {
            return Result.failure(IllegalArgumentException("Định dạng không hợp lệ"))
        }
        val newBook = Book(
            id = "imported-${System.currentTimeMillis()}",
            title = "Imported Book",
            filePath = uri.toString(),
            format = BookFormat.EPUB
        )
        booksFlow.value = booksFlow.value + newBook
        return Result.success(newBook)
    }

    override suspend fun saveBook(book: Book) {
        booksFlow.value = booksFlow.value.filter { it.id != book.id } + book
    }

    override suspend fun updateLastRead(bookId: String, timestamp: Long) {}

    override suspend fun deleteBook(id: String) {
        booksFlow.value = booksFlow.value.filter { it.id != id }
        recentBooksFlow.value = recentBooksFlow.value.filter { it.id != id }
    }

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
