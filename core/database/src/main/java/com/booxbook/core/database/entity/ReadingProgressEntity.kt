package com.booxbook.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.booxbook.core.model.ReadingProgress

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"])
    ]
)
data class ReadingProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "locator")
    val locator: String,
    @ColumnInfo(name = "percentage")
    val percentage: Float = 0f,
    @ColumnInfo(name = "current_page")
    val currentPage: Int = 0,
    @ColumnInfo(name = "total_pages")
    val totalPages: Int = 0,
    @ColumnInfo(name = "updated_timestamp")
    val updatedTimestamp: Long = System.currentTimeMillis()
)

fun ReadingProgressEntity.asDomainModel(): ReadingProgress = ReadingProgress(
    bookId = bookId,
    locator = locator,
    percentage = percentage,
    currentPage = currentPage,
    totalPages = totalPages,
    updatedTimestamp = updatedTimestamp
)

fun ReadingProgress.asEntity(): ReadingProgressEntity = ReadingProgressEntity(
    bookId = bookId,
    locator = locator,
    percentage = percentage,
    currentPage = currentPage,
    totalPages = totalPages,
    updatedTimestamp = updatedTimestamp
)
