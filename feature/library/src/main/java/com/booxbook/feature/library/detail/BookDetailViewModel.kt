package com.booxbook.feature.library.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.engine.BookTocExtractor
import com.booxbook.core.model.Book
import com.booxbook.core.model.ReadingProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Phản hồi ngắn hạn cho các thao tác trên màn chi tiết sách, hiển thị bằng snackbar.
 *
 * Trước đây lỗi xoá sách chỉ được ghi vào `errorMessage`, nhưng màn hình chỉ hiện `errorMessage` khi
 * *chưa* có sách — nên lỗi xoá/xoá tiến độ thất bại là vô hình với người đọc. Snackbar là kênh đúng
 * cho những thông báo không được phép thay thế nội dung đang xem.
 */
sealed interface BookDetailFeedback {
    /** Tiến độ đã bị đặt lại; [canUndo] cho biết có bản ghi cũ để khôi phục hay không. */
    data class ProgressReset(val canUndo: Boolean) : BookDetailFeedback

    data object ProgressRestored : BookDetailFeedback
    data class Failure(val message: String) : BookDetailFeedback
}

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookTocExtractor: BookTocExtractor
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private val _feedback = MutableSharedFlow<BookDetailFeedback>(extraBufferCapacity = 4)
    val feedback: SharedFlow<BookDetailFeedback> = _feedback.asSharedFlow()

    /** Tiến độ bị thay thế bởi lần đặt lại gần nhất, giữ lại để nút "Hoàn tác" khôi phục được. */
    private var progressBeforeReset: ReadingProgress? = null

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
        val previousProgress = _uiState.value.progress
        viewModelScope.launch(ioDispatcher) {
            runCatching { bookRepository.deleteReadingProgress(currentBook.id) }
                .onSuccess {
                    progressBeforeReset = previousProgress
                    _uiState.update { it.copy(progress = null) }
                    _feedback.tryEmit(BookDetailFeedback.ProgressReset(canUndo = previousProgress != null))
                }
                .onFailure { error ->
                    _feedback.tryEmit(
                        BookDetailFeedback.Failure(
                            "Không thể đặt lại tiến độ: ${error.message ?: "lỗi không xác định"}"
                        )
                    )
                }
        }
    }

    /**
     * Khôi phục tiến độ đã bị [resetReadingProgress] xoá — đích đến của nút "Hoàn tác".
     */
    fun undoResetProgress() {
        val previousProgress = progressBeforeReset ?: return
        progressBeforeReset = null
        viewModelScope.launch(ioDispatcher) {
            runCatching { bookRepository.saveReadingProgress(previousProgress) }
                .onSuccess {
                    _uiState.update { it.copy(progress = previousProgress) }
                    _feedback.tryEmit(BookDetailFeedback.ProgressRestored)
                }
                .onFailure { error ->
                    _feedback.tryEmit(
                        BookDetailFeedback.Failure(
                            "Không thể khôi phục tiến độ: ${error.message ?: "lỗi không xác định"}"
                        )
                    )
                }
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
                _uiState.update { it.copy(isDeleting = false) }
                _feedback.tryEmit(BookDetailFeedback.Failure("Không thể xóa sách khỏi thiết bị"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        tocJob?.cancel()
    }
}
