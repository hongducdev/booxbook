package com.booxbook.feature.library

import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import com.booxbook.core.model.ReadingProgress

/**
 * UI representation of a book with its associated reading progress.
 */
data class BookItemUiModel(
    val book: Book,
    val progress: ReadingProgress? = null
)

/**
 * State representing asynchronous book import operations.
 */
sealed interface ImportState {
    data object Idle : ImportState
    data class Importing(val current: Int = 0, val total: Int = 0, val fileName: String? = null) : ImportState
    data class Success(val count: Int, val lastBook: Book) : ImportState
    data class Error(val message: String) : ImportState
    data class BatchResult(val successCount: Int, val errors: List<String>) : ImportState
}

/**
 * Comprehensive UI State for the Library screen.
 */
data class LibraryUiState(
    val isLoading: Boolean = false,
    val books: List<BookItemUiModel> = emptyList(),
    val recentBooks: List<BookItemUiModel> = emptyList(),
    val filteredBooks: List<BookItemUiModel> = emptyList(),
    val searchQuery: String = "",
    val selectedFormat: BookFormat? = null,
    val importState: ImportState = ImportState.Idle,
    val selectedBookForDetail: BookItemUiModel? = null,
    val userMessage: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && books.isEmpty()
    val isSearchEmpty: Boolean get() = !isLoading && books.isNotEmpty() && filteredBooks.isEmpty()
}
