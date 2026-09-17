package com.booxbook.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.database.dao.AnnotationDao
import com.booxbook.core.database.dao.BookDao
import com.booxbook.core.database.dao.ReadingProgressDao
import com.booxbook.core.database.entity.AnnotationEntity
import com.booxbook.core.database.entity.BookEntity
import com.booxbook.core.database.entity.ReadingProgressEntity
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.BookFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BooxBookDaoTest {

    private lateinit var db: BooxBookDatabase
    private lateinit var bookDao: BookDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var annotationDao: AnnotationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BooxBookDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookDao = db.bookDao()
        readingProgressDao = db.readingProgressDao()
        annotationDao = db.annotationDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetBook() = runTest {
        val book = BookEntity(
            id = "book_1",
            title = "Dế Mèn Phiêu Lưu Ký",
            author = "Tô Hoài",
            filePath = "/data/books/de_men.epub",
            format = BookFormat.EPUB,
            addedTimestamp = 1000L
        )
        bookDao.insertBook(book)

        val retrieved = bookDao.getBookByIdSync("book_1")
        assertNotNull(retrieved)
        assertEquals("Dế Mèn Phiêu Lưu Ký", retrieved?.title)
        assertEquals("Tô Hoài", retrieved?.author)
        assertEquals(BookFormat.EPUB, retrieved?.format)

        val flowList = bookDao.getAllBooks().first()
        assertEquals(1, flowList.size)
        assertEquals("book_1", flowList[0].id)
    }

    @Test
    fun getRecentBooksSortedAndFiltered() = runTest {
        val book1 = BookEntity(
            id = "1",
            title = "Book 1",
            author = "Author 1",
            filePath = "/path/1",
            format = BookFormat.EPUB,
            lastReadTimestamp = 100L
        )
        val book2 = BookEntity(
            id = "2",
            title = "Book 2",
            author = "Author 2",
            filePath = "/path/2",
            format = BookFormat.AZW3,
            lastReadTimestamp = 300L
        )
        val book3 = BookEntity(
            id = "3",
            title = "Book 3",
            author = "Author 3",
            filePath = "/path/3",
            format = BookFormat.CBZ,
            lastReadTimestamp = 200L
        )
        val unreadBook = BookEntity(
            id = "4",
            title = "Book 4",
            author = "Author 4",
            filePath = "/path/4",
            format = BookFormat.EPUB,
            lastReadTimestamp = 0L
        )

        bookDao.insertBook(book1)
        bookDao.insertBook(book2)
        bookDao.insertBook(book3)
        bookDao.insertBook(unreadBook)

        val recent = bookDao.getRecentBooks(limit = 2).first()
        assertEquals(2, recent.size)
        assertEquals("2", recent[0].id) // lastRead 300L
        assertEquals("3", recent[1].id) // lastRead 200L
    }

    @Test
    fun getBooksByFormat() = runTest {
        bookDao.insertBook(BookEntity("1", "Epub 1", "A", "/p/1", null, BookFormat.EPUB))
        bookDao.insertBook(BookEntity("2", "CBZ 1", "B", "/p/2", null, BookFormat.CBZ))
        bookDao.insertBook(BookEntity("3", "Epub 2", "C", "/p/3", null, BookFormat.EPUB))

        val epubs = bookDao.getBooksByFormat(BookFormat.EPUB).first()
        assertEquals(2, epubs.size)
        assertTrue(epubs.all { it.format == BookFormat.EPUB })

        val comics = bookDao.getBooksByFormat(BookFormat.CBZ).first()
        assertEquals(1, comics.size)
        assertEquals("2", comics[0].id)
    }

    @Test
    fun updateLastReadTimestamp() = runTest {
        val book = BookEntity("1", "Book 1", "A", "/p/1", null, BookFormat.EPUB, lastReadTimestamp = 0L)
        bookDao.insertBook(book)

        bookDao.updateLastReadTimestamp("1", 5000L)
        val updated = bookDao.getBookByIdSync("1")
        assertEquals(5000L, updated?.lastReadTimestamp)
    }

    @Test
    fun readingProgressUpsertAndQuery() = runTest {
        val book = BookEntity("book_1", "Title", "Author", "/p/1", null, BookFormat.EPUB)
        bookDao.insertBook(book)

        val progress1 = ReadingProgressEntity(
            bookId = "book_1",
            locator = "epubcfi(/6/4[chapter1]!/4/2/1:0)",
            percentage = 0.25f,
            currentPage = 25,
            totalPages = 100,
            updatedTimestamp = 1000L
        )
        readingProgressDao.upsertProgress(progress1)

        val saved = readingProgressDao.getProgressForBookSync("book_1")
        assertNotNull(saved)
        assertEquals("epubcfi(/6/4[chapter1]!/4/2/1:0)", saved?.locator)
        assertEquals(0.25f, saved?.percentage ?: 0f, 0.001f)

        // Upsert to new progress
        val progress2 = progress1.copy(
            locator = "epubcfi(/6/8[chapter2]!/4/2/1:0)",
            percentage = 0.50f,
            currentPage = 50,
            updatedTimestamp = 2000L
        )
        readingProgressDao.upsertProgress(progress2)

        val updated = readingProgressDao.getProgressForBook("book_1").first()
        assertNotNull(updated)
        assertEquals("epubcfi(/6/8[chapter2]!/4/2/1:0)", updated?.locator)
        assertEquals(0.50f, updated?.percentage ?: 0f, 0.001f)
    }

    @Test
    fun annotationCrudOperations() = runTest {
        val book = BookEntity("book_1", "Title", "Author", "/p/1", null, BookFormat.EPUB)
        bookDao.insertBook(book)

        val highlight = AnnotationEntity(
            bookId = "book_1",
            type = AnnotationType.HIGHLIGHT,
            locator = "epubcfi(/6/4!/4/2)",
            selectedText = "Một đoạn trích hay",
            colorHex = "#FFE082"
        )
        val note = AnnotationEntity(
            bookId = "book_1",
            type = AnnotationType.NOTE,
            locator = "epubcfi(/6/4!/4/4)",
            selectedText = "Cần ghi nhớ ý này",
            noteContent = "Ý kiến cá nhân về chương 1",
            colorHex = "#80D8FF"
        )

        val hId = annotationDao.insertAnnotation(highlight)
        val nId = annotationDao.insertAnnotation(note)

        val allAnnotations = annotationDao.getAnnotationsForBook("book_1").first()
        assertEquals(2, allAnnotations.size)

        val highlightsOnly = annotationDao.getAnnotationsByType("book_1", AnnotationType.HIGHLIGHT).first()
        assertEquals(1, highlightsOnly.size)
        assertEquals("Một đoạn trích hay", highlightsOnly[0].selectedText)

        // Delete one annotation
        annotationDao.deleteAnnotationById(hId)
        val remaining = annotationDao.getAnnotationsForBook("book_1").first()
        assertEquals(1, remaining.size)
        assertEquals(nId, remaining[0].id)
    }

    @Test
    fun cascadeDeleteBookDeletesProgressAndAnnotations() = runTest {
        val book = BookEntity("book_1", "Title", "Author", "/p/1", null, BookFormat.EPUB)
        bookDao.insertBook(book)

        val progress = ReadingProgressEntity("book_1", "cfi", 0.1f, 1, 10)
        readingProgressDao.upsertProgress(progress)

        val annotation = AnnotationEntity(
            bookId = "book_1",
            type = AnnotationType.BOOKMARK,
            locator = "cfi_bookmark"
        )
        annotationDao.insertAnnotation(annotation)

        // Verify inserted
        assertNotNull(readingProgressDao.getProgressForBookSync("book_1"))
        assertEquals(1, annotationDao.getAnnotationsForBook("book_1").first().size)

        // Delete Book
        bookDao.deleteBookById("book_1")

        // Verify Cascade deletion
        assertNull(bookDao.getBookByIdSync("book_1"))
        assertNull(readingProgressDao.getProgressForBookSync("book_1"))
        assertEquals(0, annotationDao.getAnnotationsForBook("book_1").first().size)
    }
}
