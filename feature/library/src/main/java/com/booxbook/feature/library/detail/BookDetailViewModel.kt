package com.booxbook.feature.library.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.engine.BookTocExtractor
import com.booxbook.core.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookTocExtractor: BookTocExtractor
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var tocJob: Job? = null

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val book = withContext(ioDispatcher) {
                bookRepository.getBookByIdSync(bookId)
            }

            if (book == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Không tìm thấy cuốn sách này"
                    )
                }
                return@launch
            }

            val savedProgress = withContext(ioDispatcher) {
                bookRepository.getReadingProgressSync(bookId)
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    book = book,
                    progress = savedProgress,
                    isTocLoading = true
                )
            }

            // Realtime observation of reading progress
            progressJob?.cancel()
            progressJob = viewModelScope.launch {
                bookRepository.getReadingProgress(bookId).collect { progress ->
                    _uiState.update { it.copy(progress = progress) }
                }
            }

            // Extract table of contents in background
            tocJob?.cancel()
            tocJob = viewModelScope.launch(ioDispatcher) {
                val toc = bookTocExtractor.extractToc(book)
                _uiState.update {
                    it.copy(
                        tableOfContents = toc,
                        isTocLoading = false
                    )
                }
            }
        }
    }

    fun resetReadingProgress() {
        val currentBook = _uiState.value.book ?: return
        viewModelScope.launch(ioDispatcher) {
            bookRepository.deleteReadingProgress(currentBook.id)
            _uiState.update { it.copy(progress = null) }
        }
    }

    fun deleteBook(onSuccess: () -> Unit) {
        val currentBook = _uiState.value.book ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            val success = withContext(ioDispatcher) {
                runCatching {
                    bookRepository.deleteBook(currentBook.id)
                }.isSuccess
            }
            if (success) {
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = "Không thể xóa sách khỏi thiết bị"
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        tocJob?.cancel()
    }
}
