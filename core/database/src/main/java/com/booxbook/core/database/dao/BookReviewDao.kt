package com.booxbook.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.booxbook.core.database.entity.BookReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookReviewDao {
    @Query("SELECT * FROM book_reviews WHERE book_id = :bookId")
    fun getReview(bookId: String): Flow<BookReviewEntity?>

    @Query("SELECT * FROM book_reviews WHERE book_id = :bookId")
    suspend fun getReviewSync(bookId: String): BookReviewEntity?

    @Query("SELECT * FROM book_reviews")
    fun getAllReviews(): Flow<List<BookReviewEntity>>

    @Upsert
    suspend fun upsertReview(review: BookReviewEntity)

    @Query("DELETE FROM book_reviews WHERE book_id = :bookId")
    suspend fun deleteReview(bookId: String)
}
