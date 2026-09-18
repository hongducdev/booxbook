package com.booxbook.feature.library.detail

import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Book
import com.booxbook.core.model.ReadingProgress

/**
 * UI State for the Book Detail screen.
 */
data class BookDetailUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val progress: ReadingProgress? = null,
    val tableOfContents: List<TocItem> = emptyList(),
    val isTocLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null
) {
    val percentage: Float
        get() = progress?.percentage ?: 0f

    val percentageInt: Int
        get() = (percentage * 100).toInt().coerceIn(0, 100)

    val isFinished: Boolean
        get() = percentage >= 0.99f

    val hasStartedReading: Boolean
        get() = percentage > 0.01f || (progress?.currentPage ?: 0) > 0

    val readingStatusText: String
        get() = when {
            isFinished -> "Đã đọc xong"
            hasStartedReading -> "Đang đọc dở ($percentageInt%)"
            else -> "Chưa bắt đầu đọc"
        }
}
