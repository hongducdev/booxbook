package com.booxbook.core.model

import kotlinx.serialization.Serializable

/**
 * Một cuốn sách trong thư viện.
 *
 * Các trường metadata (`series`…`language`) là **nullable** chứ không mặc định chuỗi rỗng, vì hai trạng thái
 * này khác nhau và cần phân biệt:
 *
 * - `null` — chưa trích xuất được, có thể là sách nhập từ trước khi có tính năng đọc metadata (cần quét lại).
 * - `""` — đã đọc tệp và biết chắc sách không khai báo trường đó.
 *
 * Gộp hai thứ này lại thì không biết bản ghi nào cần quét lại, và `%series` sẽ hiện rỗng cho cả hai trường hợp.
 */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String = "Tác giả chưa rõ",
    val filePath: String,
    val coverPath: String? = null,
    val format: BookFormat,
    val totalPages: Int = 0,
    val fileSize: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastReadTimestamp: Long = 0L,
    val series: String? = null,
    val seriesIndex: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val language: String? = null
) {
    /**
     * Tên bộ kèm số tập, dạng `Dune #1` — khớp `%series` của bản gốc.
     *
     * Chỉ có tên mà không có số tập thì trả đúng tên, không thêm dấu `#` trống.
     */
    val seriesLabel: String
        get() {
            val name = series.orEmpty()
            if (name.isEmpty()) return ""
            val index = seriesIndex.orEmpty()
            return if (index.isEmpty()) name else "$name #$index"
        }

    /** Metadata đã được trích xuất hay chưa. Dùng để quyết định có cần quét lại tệp không. */
    val hasExtractedMetadata: Boolean
        get() = series != null || description != null || language != null || tags.isNotEmpty()
}

/**
 * Đánh giá và cảm nhận sau khi đọc xong một cuốn.
 *
 * Tách khỏi [Book] vì đây là dữ liệu **do người đọc tạo**, không phải metadata của tệp: quét lại OPF không
 * được phép ghi đè nó, và xoá sách thì nó phải biến mất theo (khoá ngoại CASCADE ở tầng Room).
 */
@Serializable
data class BookReview(
    val bookId: String,
    /** 0 = chưa chấm, 1..5 = số sao. */
    val rating: Int = 0,
    val review: String = "",
    val updatedTimestamp: Long = 0L,
    /** Mốc đánh dấu đã đọc xong; `null` khi chưa đánh dấu. */
    val finishedAt: Long? = null
) {
    val hasRating: Boolean
        get() = rating > 0

    val isFinished: Boolean
        get() = finishedAt != null
}

/** Trạng thái đọc suy ra từ tiến độ, dùng cho token `%status`. Không lưu xuống đĩa. */
enum class ReadingStatus(val key: String, val label: String) {
    UNREAD("unread", "Chưa đọc"),
    READING("reading", "Đang đọc"),
    FINISHED("finished", "Đã đọc xong");

    companion object {
        /** Ngưỡng coi như đã đọc xong, khớp cách màn Thống kê đếm sách hoàn thành. */
        const val FINISHED_THRESHOLD = 0.99f

        fun fromProgression(progression: Float): ReadingStatus = when {
            progression <= 0f -> UNREAD
            progression >= FINISHED_THRESHOLD -> FINISHED
            else -> READING
        }
    }
}
