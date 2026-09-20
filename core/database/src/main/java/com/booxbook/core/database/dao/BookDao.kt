package com.booxbook.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.booxbook.core.database.entity.BookEntity
import com.booxbook.core.database.entity.BookWithProgressEntity
import com.booxbook.core.model.BookFormat
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY added_timestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Transaction
    @Query("SELECT * FROM books ORDER BY added_timestamp DESC")
    fun getAllBooksWithProgress(): Flow<List<BookWithProgressEntity>>

    @Query("SELECT * FROM books WHERE last_read_timestamp > 0 ORDER BY last_read_timestamp DESC LIMIT :limit")
    fun getRecentBooks(limit: Int = 10): Flow<List<BookEntity>>

    @Transaction
    @Query("SELECT * FROM books WHERE last_read_timestamp > 0 ORDER BY last_read_timestamp DESC LIMIT :limit")
    fun getRecentBooksWithProgress(limit: Int = 10): Flow<List<BookWithProgressEntity>>

    @Query("SELECT * FROM books WHERE format = :format ORDER BY added_timestamp DESC")
    fun getBooksByFormat(format: BookFormat): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun getBookById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookByIdSync(id: String): BookEntity?

    /**
     * Sách chưa từng được quét metadata.
     *
     * Điều kiện là **cả ba** trường đều `NULL` — tức là bản ghi từ trước khi có tính năng đọc metadata. Sau
     * lần quét đầu, chúng được ghi thành `""` (đã quét, sách không khai báo), nên không bao giờ lọt vào đây lại.
     */
    @Query("SELECT * FROM books WHERE series IS NULL AND description IS NULL AND language IS NULL")
    suspend fun getBooksMissingMetadata(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Upsert
    suspend fun upsertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET last_read_timestamp = :timestamp WHERE id = :id")
    suspend fun updateLastReadTimestamp(id: String, timestamp: Long)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: String)
}
