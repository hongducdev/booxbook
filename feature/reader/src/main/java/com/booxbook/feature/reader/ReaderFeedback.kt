package com.booxbook.feature.reader

import com.booxbook.core.model.Annotation
import com.booxbook.core.model.AnnotationType

/**
 * Phản hồi ngắn hạn mà [ReaderScreen] hiển thị qua snackbar.
 *
 * `ReaderViewModel` chỉ phát ra *sự kiện đã xảy ra* (đánh dấu thành công, xoá ghi chú, TTS dừng…),
 * không phát chuỗi hiển thị. Nhờ vậy tầng state không phụ thuộc vào câu chữ, và mọi thay đổi về
 * wording đều nằm gọn trong [toSnackbar] — nơi có unit test riêng.
 */
sealed interface ReaderFeedback {
    data object BookmarkAdded : ReaderFeedback
    data object BookmarkRemoved : ReaderFeedback
    data object TtsStarted : ReaderFeedback
    data object TtsStopped : ReaderFeedback
    data object BookFinished : ReaderFeedback

    /** Ghi chú/đánh dấu vừa bị xoá; mang theo bản ghi gốc để có thể hoàn tác. */
    data class AnnotationDeleted(val annotation: Annotation) : ReaderFeedback

    /** Ghi chú/đánh dấu vừa được khôi phục từ thao tác hoàn tác. */
    data class AnnotationRestored(val annotation: Annotation) : ReaderFeedback

    /** Một thao tác thất bại và người đọc cần biết lý do. */
    data class Failure(val message: String) : ReaderFeedback
}

/** Nhãn nút hành động dùng chung cho mọi snackbar có thể hoàn tác. */
const val READER_UNDO_LABEL = "Hoàn tác"

/**
 * Một snackbar đã sẵn sàng để hiển thị.
 *
 * [undoAnnotation] khác `null` nghĩa là bấm [actionLabel] sẽ khôi phục đúng bản ghi đó.
 */
data class ReaderSnackbar(
    val message: String,
    val actionLabel: String? = null,
    val undoAnnotation: Annotation? = null
)

fun ReaderFeedback.toSnackbar(): ReaderSnackbar = when (this) {
    ReaderFeedback.BookmarkAdded -> ReaderSnackbar("Đã đánh dấu trang")
    ReaderFeedback.BookmarkRemoved -> ReaderSnackbar("Đã bỏ đánh dấu trang")
    ReaderFeedback.TtsStarted -> ReaderSnackbar("Đang đọc bằng giọng nói")
    ReaderFeedback.TtsStopped -> ReaderSnackbar("Đã dừng đọc bằng giọng nói")
    ReaderFeedback.BookFinished -> ReaderSnackbar("Đã đọc hết cuốn sách")

    is ReaderFeedback.AnnotationDeleted -> ReaderSnackbar(
        message = if (annotation.type == AnnotationType.BOOKMARK) {
            "Đã xoá đánh dấu trang"
        } else {
            "Đã xoá ghi chú"
        },
        actionLabel = READER_UNDO_LABEL,
        undoAnnotation = annotation
    )

    is ReaderFeedback.AnnotationRestored -> ReaderSnackbar(
        if (annotation.type == AnnotationType.BOOKMARK) {
            "Đã khôi phục đánh dấu trang"
        } else {
            "Đã khôi phục ghi chú"
        }
    )

    is ReaderFeedback.Failure -> ReaderSnackbar(message)
}
