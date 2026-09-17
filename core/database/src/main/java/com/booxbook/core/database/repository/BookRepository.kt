package com.booxbook.core.database.repository

import android.net.Uri
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun getRecentBooks(limit: Int = 10): Flow<List<Book>>
    fun getBooksByFormat(format: BookFormat): Flow<List<Book>>
    fun getBookById(id: String): Flow<Book?>
    suspend fun getBookByIdSync(id: String): Book?
    suspend fun importBookFromUri(uri: Uri): Result<Book>
    suspend fun saveBook(book: Book)
    suspend fun updateLastRead(bookId: String, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteBook(id: String)

    fun getReadingProgress(bookId: String): Flow<ReadingProgress?>
    suspend fun getReadingProgressSync(bookId: String): ReadingProgress?
    suspend fun saveReadingProgress(progress: ReadingProgress)
    suspend fun deleteReadingProgress(bookId: String)

    fun getAnnotationsForBook(bookId: String): Flow<List<Annotation>>
    fun getAnnotationsByType(bookId: String, type: AnnotationType): Flow<List<Annotation>>
    suspend fun addAnnotation(annotation: Annotation): Long
    suspend fun updateAnnotation(annotation: Annotation)
    suspend fun removeAnnotation(id: Long)
}
