package com.booxbook.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.booxbook.core.model.BookReview

/**
 * Đánh giá và cảm nhận của người đọc về một cuốn sách.
 *
 * Dùng `book_id` làm khoá chính luôn: mỗi cuốn chỉ có một đánh giá, nên không cần khoá tự tăng rồi phải
 * thêm ràng buộc duy nhất — và khoá chính đã được đánh chỉ mục nên tra theo sách là tra thẳng.
 */
@Entity(
    tableName = "book_reviews",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookReviewEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "rating")
    val rating: Int = 0,
    @ColumnInfo(name = "review")
    val review: String = "",
    @ColumnInfo(name = "updated_timestamp")
    val updatedTimestamp: Long = 0L,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long? = null
)

fun BookReviewEntity.asDomainModel(): BookReview = BookReview(
    bookId = bookId,
    rating = rating,
    review = review,
    updatedTimestamp = updatedTimestamp,
    finishedAt = finishedAt
)

fun BookReview.asEntity(): BookReviewEntity = BookReviewEntity(
    bookId = bookId,
    rating = rating,
    review = review,
    updatedTimestamp = updatedTimestamp,
    finishedAt = finishedAt
)
