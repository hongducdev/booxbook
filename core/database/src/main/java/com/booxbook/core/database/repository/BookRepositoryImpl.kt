package com.booxbook.core.database.repository

import android.net.Uri
import com.booxbook.core.database.dao.AnnotationDao
import com.booxbook.core.database.dao.BookDao
import com.booxbook.core.database.dao.ReadingProgressDao
import com.booxbook.core.database.entity.asDomainModel
import com.booxbook.core.database.entity.asEntity
import com.booxbook.core.database.storage.BookStorageManager
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    private val annotationDao: AnnotationDao,
    private val storageManager: BookStorageManager
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> {
        return bookDao.getAllBooks().map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getRecentBooks(limit: Int): Flow<List<Book>> {
        return bookDao.getRecentBooks(limit).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getBooksByFormat(format: BookFormat): Flow<List<Book>> {
        return bookDao.getBooksByFormat(format).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getBookById(id: String): Flow<Book?> {
        return bookDao.getBookById(id).map { it?.asDomainModel() }
    }

    override suspend fun getBookByIdSync(id: String): Book? {
        return bookDao.getBookByIdSync(id)?.asDomainModel()
    }

    override suspend fun importBookFromUri(uri: Uri): Result<Book> {
        return storageManager.importBookFromUri(uri).mapCatching { info ->
            val book = Book(
                id = info.id,
                title = info.title,
                author = info.author,
                filePath = info.filePath,
                coverPath = info.coverPath,
                format = info.format,
                fileSize = info.fileSize,
                addedTimestamp = System.currentTimeMillis()
            )
            bookDao.insertBook(book.asEntity())
            book
        }
    }

    override suspend fun saveBook(book: Book) {
        bookDao.upsertBook(book.asEntity())
    }

    override suspend fun updateLastRead(bookId: String, timestamp: Long) {
        bookDao.updateLastReadTimestamp(bookId, timestamp)
    }

    override suspend fun deleteBook(id: String) {
        val existing = bookDao.getBookByIdSync(id)
        if (existing != null) {
            storageManager.deleteBookFiles(existing.filePath, existing.coverPath)
            bookDao.deleteBookById(id)
        }
    }

    override fun getReadingProgress(bookId: String): Flow<ReadingProgress?> {
        return readingProgressDao.getProgressForBook(bookId).map { it?.asDomainModel() }
    }

    override suspend fun getReadingProgressSync(bookId: String): ReadingProgress? {
        return readingProgressDao.getProgressForBookSync(bookId)?.asDomainModel()
    }

    override suspend fun saveReadingProgress(progress: ReadingProgress) {
        readingProgressDao.upsertProgress(progress.asEntity())
        bookDao.updateLastReadTimestamp(progress.bookId, progress.updatedTimestamp)
    }

    override suspend fun deleteReadingProgress(bookId: String) {
        readingProgressDao.deleteProgressForBook(bookId)
    }

    override fun getAnnotationsForBook(bookId: String): Flow<List<Annotation>> {
        return annotationDao.getAnnotationsForBook(bookId).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override fun getAnnotationsByType(bookId: String, type: AnnotationType): Flow<List<Annotation>> {
        return annotationDao.getAnnotationsByType(bookId, type).map { entities ->
            entities.map { it.asDomainModel() }
        }
    }

    override suspend fun addAnnotation(annotation: Annotation): Long {
        return annotationDao.insertAnnotation(annotation.asEntity())
    }

    override suspend fun updateAnnotation(annotation: Annotation) {
        annotationDao.updateAnnotation(annotation.asEntity())
    }

    override suspend fun removeAnnotation(id: Long) {
        annotationDao.deleteAnnotationById(id)
    }
}
