package com.booxbook.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.booxbook.core.database.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

data class BookDurationAggregate(
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "total_duration_seconds") val totalDurationSeconds: Long
)

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY start_time ASC")
    fun getSessionsBetweenDates(startDate: String, endDate: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId ORDER BY start_time DESC")
    fun getSessionsForBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT DISTINCT date FROM reading_sessions ORDER BY date DESC")
    fun getDistinctReadingDates(): Flow<List<String>>

    @Query("SELECT DISTINCT date FROM reading_sessions ORDER BY date DESC")
    suspend fun getDistinctReadingDatesSync(): List<String>

    @Query("SELECT COALESCE(SUM(duration_seconds), 0) FROM reading_sessions WHERE date = :date")
    fun getDurationSecondsForDate(date: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(duration_seconds), 0) FROM reading_sessions")
    fun getTotalDurationSeconds(): Flow<Long>

    @Query("SELECT book_id, SUM(duration_seconds) as total_duration_seconds FROM reading_sessions GROUP BY book_id ORDER BY total_duration_seconds DESC")
    fun getReadingDurationByBook(): Flow<List<BookDurationAggregate>>

    @Query("DELETE FROM reading_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)
}
