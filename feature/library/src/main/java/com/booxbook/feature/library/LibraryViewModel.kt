package com.booxbook.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedFormat = MutableStateFlow<BookFormat?>(null)
    val selectedFormat: StateFlow<BookFormat?> = _selectedFormat

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState

    private val _selectedBookForDetail = MutableStateFlow<BookItemUiModel?>(null)
    val selectedBookForDetail: StateFlow<BookItemUiModel?> = _selectedBookForDetail

    private val _userMessage = MutableStateFlow<String?>(null)

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    init {
        // Sách nhập trước khi có tính năng đọc metadata (series, mô tả, ngôn ngữ, thẻ) nằm lại với các cột
        // `NULL`. Quét lại một lần lúc mở thư viện để token `%series`/`%description`/`%lang` có dữ liệu.
        // Repository tự đánh dấu đã quét nên lần mở sau không mở lại từng tệp nữa.
        viewModelScope.launch(ioDispatcher) {
            runCatching { bookRepository.backfillMetadata() }
        }
    }

    // Room emits on its background query dispatcher; mapping is purely in-memory
    private val booksDataFlow = combine(
        bookRepository.getAllBooksWithProgress(),
        bookRepository.getRecentBooksWithProgress(10)
    ) { allBooks, recentBooks ->
        val bookItems = allBooks.map { BookItemUiModel(it.book, it.progress) }
        val recentItems = recentBooks.map { BookItemUiModel(it.book, it.progress) }
        Pair(bookItems, recentItems)
    }

    private val feedbackFlow = combine(_importState, _userMessage) { importStatus, message ->
        Pair(importStatus, message)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksDataFlow,
        _searchQuery,
        _selectedFormat,
        _selectedBookForDetail,
        feedbackFlow
    ) { (allBooks, recentBooks), query, format, selectedDetail, (importStatus, message) ->
        val filtered = allBooks.filter { item ->
            val matchesFormat = format == null || item.book.format == format
            val matchesQuery = query.isBlank() ||
                    item.book.title.contains(query, ignoreCase = true) ||
                    item.book.author.contains(query, ignoreCase = true)
            matchesFormat && matchesQuery
        }

        LibraryUiState(
            isLoading = false,
            books = allBooks,
            recentBooks = recentBooks,
            filteredBooks = filtered,
            searchQuery = query,
            selectedFormat = format,
            importState = importStatus,
            selectedBookForDetail = selectedDetail,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFormatSelected(format: BookFormat?) {
        _selectedFormat.value = format
    }

    fun onBookSelectedForDetail(item: BookItemUiModel) {
        _selectedBookForDetail.value = item
    }

    fun dismissDetail() {
        _selectedBookForDetail.value = null
    }

    fun importBooksFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val total = uris.size
            _importState.value = ImportState.Importing(current = 0, total = total)
            val successfulBooks = mutableListOf<Book>()
            val errors = mutableListOf<String>()

            withContext(ioDispatcher) {
                uris.forEachIndexed { index, uri ->
                    _importState.value = ImportState.Importing(current = index + 1, total = total)
                    val result = bookRepository.importBookFromUri(uri)
                    result.onSuccess { book ->
                        successfulBooks.add(book)
                    }.onFailure { error ->
                        errors.add(error.message ?: "Không thể nhập tệp: $uri")
                    }
                }
            }

            when {
                errors.isNotEmpty() && successfulBooks.isNotEmpty() -> {
                    _importState.value = ImportState.BatchResult(successfulBooks.size, errors)
                }
                errors.isNotEmpty() && successfulBooks.isEmpty() -> {
                    _importState.value = ImportState.Error(errors.joinToString("\n"))
                }
                successfulBooks.isNotEmpty() -> {
                    _importState.value = ImportState.Success(successfulBooks.size, successfulBooks.last())
                }
                else -> {
                    _importState.value = ImportState.Idle
                }
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching { bookRepository.deleteBook(book.id) }
            }
            if (_selectedBookForDetail.value?.book?.id == book.id) {
                _selectedBookForDetail.value = null
            }
            result.onSuccess {
                _userMessage.value = "Đã xóa \"${book.title}\""
            }.onFailure { e ->
                _userMessage.value = "Không thể xóa sách: ${e.message}"
            }
        }
    }

    fun clearImportState() {
        _importState.value = ImportState.Idle
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
