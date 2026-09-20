package com.booxbook.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat

/**
 * Dấu phân tách các thẻ (tags) trong một cột.
 *
 * Dùng ký tự điều khiển `Unit Separator` (0x1F) thay vì dấu phẩy: thẻ do nhà xuất bản đặt có thể chứa dấu
 * phẩy ("Fiction, Thriller"), và tách bằng dấu phẩy sẽ biến một thẻ thành hai.
 */
internal const val TAG_SEPARATOR = '\u001F'

internal fun encodeTags(tags: List<String>): String =
    tags.filter { it.isNotBlank() }.joinToString(TAG_SEPARATOR.toString())

internal fun decodeTags(raw: String?): List<String> =
    raw?.split(TAG_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

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
    val lastReadTimestamp: Long = 0L,
    @ColumnInfo(name = "series")
    val series: String? = null,
    @ColumnInfo(name = "series_index")
    val seriesIndex: String? = null,
    @ColumnInfo(name = "tags", defaultValue = "")
    val tags: String = "",
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "language")
    val language: String? = null
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
    lastReadTimestamp = lastReadTimestamp,
    series = series,
    seriesIndex = seriesIndex,
    tags = decodeTags(tags),
    description = description,
    language = language
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
    lastReadTimestamp = lastReadTimestamp,
    series = series,
    seriesIndex = seriesIndex,
    tags = encodeTags(tags),
    description = description,
    language = language
)
