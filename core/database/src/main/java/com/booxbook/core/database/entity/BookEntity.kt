package com.booxbook.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["last_read_timestamp"]),
        Index(value = ["format"])
    ]
)
data class BookEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "author")
    val author: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null,
    @ColumnInfo(name = "format")
    val format: BookFormat,
    @ColumnInfo(name = "total_pages")
    val totalPages: Int = 0,
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,
    @ColumnInfo(name = "added_timestamp")
    val addedTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_read_timestamp")
    val lastReadTimestamp: Long = 0L
)

fun BookEntity.asDomainModel(): Book = Book(
    id = id,
    title = title,
    author = author,
    filePath = filePath,
    coverPath = coverPath,
    format = format,
    totalPages = totalPages,
    fileSize = fileSize,
    addedTimestamp = addedTimestamp,
    lastReadTimestamp = lastReadTimestamp
)

fun Book.asEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    filePath = filePath,
    coverPath = coverPath,
    format = format,
    totalPages = totalPages,
    fileSize = fileSize,
    addedTimestamp = addedTimestamp,
    lastReadTimestamp = lastReadTimestamp
)
