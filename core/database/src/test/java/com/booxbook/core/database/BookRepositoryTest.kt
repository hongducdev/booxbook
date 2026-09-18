package com.booxbook.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.database.repository.BookRepositoryImpl
import com.booxbook.core.database.storage.BookStorageManager
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookRepositoryTest {

    private lateinit var db: BooxBookDatabase
    private lateinit var repository: BookRepository
    private lateinit var storageManager: BookStorageManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BooxBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        storageManager = BookStorageManager(context)
        repository = BookRepositoryImpl(
            bookDao = db.bookDao(),
            readingProgressDao = db.readingProgressDao(),
            annotationDao = db.annotationDao(),
            readingSessionDao = db.readingSessionDao(),
            storageManager = storageManager
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveAndRetrieveBook() = runTest {
        val book = Book(
            id = "repo_book_1",
            title = "Truyện Kiều",
            author = "Nguyễn Du",
            filePath = "/storage/emulated/0/Books/truyen_kieu.epub",
            format = BookFormat.EPUB
        )

        repository.saveBook(book)

        val retrieved = repository.getBookById("repo_book_1").first()
        assertNotNull(retrieved)
        assertEquals("Truyện Kiều", retrieved?.title)
        assertEquals("Nguyễn Du", retrieved?.author)

        val syncRetrieved = repository.getBookByIdSync("repo_book_1")
        assertEquals("Truyện Kiều", syncRetrieved?.title)
    }

    @Test
    fun saveReadingProgressUpdatesBookLastRead() = runTest {
        val book = Book(
            id = "repo_book_2",
            title = "Sherlock Holmes",
            author = "Arthur Conan Doyle",
            filePath = "/path/to/sherlock.epub",
            format = BookFormat.EPUB,
            lastReadTimestamp = 0L
        )
        repository.saveBook(book)

        val progressTime = 999999L
        val progress = ReadingProgress(
            bookId = "repo_book_2",
            locator = "cfi_pos_1",
            percentage = 0.35f,
            currentPage = 35,
            totalPages = 100,
            updatedTimestamp = progressTime
        )
        repository.saveReadingProgress(progress)

        val savedProgress = repository.getReadingProgress("repo_book_2").first()
        assertNotNull(savedProgress)
        assertEquals(0.35f, savedProgress?.percentage ?: 0f, 0.001f)

        // Book's lastReadTimestamp should be updated automatically
        val updatedBook = repository.getBookByIdSync("repo_book_2")
        assertEquals(progressTime, updatedBook?.lastReadTimestamp)
    }

    @Test
    fun annotationLifecycleThroughRepository() = runTest {
        val book = Book(
            id = "repo_book_3",
            title = "Book 3",
            author = "Author 3",
            filePath = "/path/to/book3.cbz",
            format = BookFormat.CBZ
        )
        repository.saveBook(book)

        val annotation = Annotation(
            bookId = "repo_book_3",
            type = AnnotationType.NOTE,
            locator = "page:15",
            noteContent = "Trang truyện này vẽ rất chi tiết"
        )
        val id = repository.addAnnotation(annotation)
        val allAnnotations = repository.getAnnotationsForBook("repo_book_3").first()
        assertEquals(1, allAnnotations.size)
        assertEquals("Trang truyện này vẽ rất chi tiết", allAnnotations[0].noteContent)

        repository.removeAnnotation(id)
        val emptyAnnotations = repository.getAnnotationsForBook("repo_book_3").first()
        assertEquals(0, emptyAnnotations.size)
    }

    @Test
    fun deleteBookRemovesAllRelatedData() = runTest {
        val book = Book(
            id = "repo_book_4",
            title = "Book 4",
            author = "Author 4",
            filePath = "/path/to/book4.azw3",
            format = BookFormat.AZW3
        )
        repository.saveBook(book)
        repository.saveReadingProgress(ReadingProgress("repo_book_4", "cfi", 0.1f))
        repository.addAnnotation(Annotation(bookId = "repo_book_4", type = AnnotationType.BOOKMARK, locator = "loc"))

        repository.deleteBook("repo_book_4")

        assertNull(repository.getBookByIdSync("repo_book_4"))
        assertNull(repository.getReadingProgressSync("repo_book_4"))
        assertEquals(0, repository.getAnnotationsForBook("repo_book_4").first().size)
    }

    @Test
    fun recordReadingSessionAndVerifyStatistics() = runTest {
        val book = Book(
            id = "stats_book_1",
            title = "Dế Mèn",
            author = "Tô Hoài",
            filePath = "/path/to/demen.epub",
            format = BookFormat.EPUB
        )
        repository.saveBook(book)

        // Record a 30-minute session (1800s)
        val startTime = System.currentTimeMillis() - 1800_000
        val endTime = System.currentTimeMillis()
        repository.recordReadingSession("stats_book_1", startTime, endTime, 1800L)

        val stats = repository.getReadingStatisticsOverview().first()
        assertEquals(30, stats.todayMinutes)
        assertEquals(1, stats.totalSessionsCount)
        assertEquals(1, stats.currentStreakDays)
        assertEquals(1, stats.topBooks.size)
        assertEquals("Dế Mèn", stats.topBooks[0].book.title)
        assertEquals(1800L, stats.topBooks[0].totalDurationSeconds)

        // Verify weekly stats includes today with 30 minutes
        val todayStat = stats.weeklyStats.firstOrNull { it.isToday }
        assertNotNull(todayStat)
        assertEquals(30, todayStat?.durationMinutes)

        // Verify daily goal change
        repository.setDailyGoalMinutes(60)
        val updatedStats = repository.getReadingStatisticsOverview().first()
        assertEquals(60, updatedStats.dailyGoalMinutes)
    }
}
