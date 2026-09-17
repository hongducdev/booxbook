package com.booxbook.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.booxbook.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    fun getProgressForBook(bookId: String): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    suspend fun getProgressForBookSync(bookId: String): ReadingProgressEntity?

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE book_id = :bookId")
    suspend fun deleteProgressForBook(bookId: String)
}
