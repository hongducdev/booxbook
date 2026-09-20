package com.booxbook.core.database.repository

import android.net.Uri
import com.booxbook.core.database.entity.BookWithProgress
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.BookReview
import com.booxbook.core.model.ReadingProgress
import com.booxbook.core.model.ReadingSession
import com.booxbook.core.model.ReadingStatisticsOverview
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    fun getAllBooksWithProgress(): Flow<List<BookWithProgress>>
    fun getRecentBooks(limit: Int = 10): Flow<List<Book>>
    fun getRecentBooksWithProgress(limit: Int = 10): Flow<List<BookWithProgress>>
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

    suspend fun recordReadingSession(bookId: String, startTime: Long, endTime: Long, durationSeconds: Long): Long
    fun getAllReadingSessions(): Flow<List<ReadingSession>>
    fun getReadingStatisticsOverview(): Flow<ReadingStatisticsOverview>
    fun getDailyGoalMinutes(): Flow<Int>
    suspend fun setDailyGoalMinutes(minutes: Int)

    fun getReview(bookId: String): Flow<BookReview?>
    suspend fun getReviewSync(bookId: String): BookReview?
    fun getAllReviews(): Flow<List<BookReview>>
    suspend fun saveReview(review: BookReview)
    suspend fun deleteReview(bookId: String)

    /**
     * Quét lại metadata OPF cho những sách nhập trước khi có tính năng đọc metadata.
     *
     * Chạy một lần cho mỗi bản ghi và **đánh dấu đã quét** kể cả khi tệp không khai báo gì, nếu không thì mỗi
     * lần mở ứng dụng lại mở lại từng tệp EPUB. Trả về số bản ghi đã xử lý.
     */
    suspend fun backfillMetadata(): Int
}
