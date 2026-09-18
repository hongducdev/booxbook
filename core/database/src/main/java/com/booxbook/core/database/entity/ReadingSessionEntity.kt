package com.booxbook.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.booxbook.core.model.ReadingSession

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["date"])
    ]
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "start_time")
    val startTime: Long,
    @ColumnInfo(name = "end_time")
    val endTime: Long,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long,
    @ColumnInfo(name = "date")
    val date: String // yyyy-MM-dd
)

fun ReadingSessionEntity.asDomainModel(): ReadingSession = ReadingSession(
    id = id,
    bookId = bookId,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    date = date
)

fun ReadingSession.asEntity(): ReadingSessionEntity = ReadingSessionEntity(
    id = id,
    bookId = bookId,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    date = date
)
