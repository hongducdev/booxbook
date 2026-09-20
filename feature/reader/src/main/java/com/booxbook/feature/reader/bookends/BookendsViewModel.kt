package com.booxbook.feature.reader.bookends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.core.model.ReadingSession
import com.booxbook.core.model.ReadingStatisticsOverview
import com.booxbook.core.model.bookends.BookendsDefaults
import com.booxbook.core.model.bookends.BookendsPreset
import com.booxbook.core.model.bookends.BookendsSettings
import com.booxbook.core.model.bookends.BookendsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Trạng thái overlay mà màn đọc cần để vẽ.
 *
 * [preset] đã tính sẵn cả công tắc chung lẫn quy tắc theo đuôi tệp, nên `null` nghĩa là **không vẽ gì** —
 * tầng UI không phải tự quyết định lại.
 */
data class BookendsUiState(
    val settings: BookendsSettings = BookendsSettings(),
    val preset: BookendsPreset? = null,
    val snapshot: BookendsSnapshot? = null
)

/**
 * Chủ sở hữu trạng thái Bookends trong màn đọc.
 *
 * Tách khỏi [com.booxbook.feature.reader.ReaderViewModel] vì hai lý do: `ReaderViewModel` đã gánh điều
 * phối engine, phiên đọc và TTS, còn overlay chỉ cần *đọc* trạng thái; và cấu hình Bookends còn được dùng
 * ở màn Cài đặt, nơi không có `ReaderViewModel` nào đang sống.
 *
 * Màn đọc chỉ đẩy vào một thứ duy nhất: [onReadingContextChanged]. Mọi nguồn khác — lịch sử đọc, thống kê
 * tổng hợp, phần cứng, đồng hồ — được gom trong này.
 */
@HiltViewModel
class BookendsViewModel @Inject constructor(
    private val preferencesManager: BookendsPreferencesManager,
    private val deviceState: BookendsDeviceState,
    private val bookRepository: BookRepository
) : ViewModel() {

    private val readingContext = MutableStateFlow(BookendsReadingContext())
    private val sessionProgress = MutableStateFlow(BookendsSessionProgress())

    /**
     * Nhịp làm mới cho token đồng hồ và token phiên đọc.
     *
     * 60 giây, khớp với bản gốc: `%time`, `%session_time` và `%batt` đều đổi ở mức phút hoặc thô hơn, còn
     * vẽ lại overlay dày hơn thế chỉ tốn pin trên màn e-ink mà mắt không nhận ra.
     */
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(REFRESH_INTERVAL_MS)
        }
    }

    private val library: Flow<LibraryReadingData> = combine(
        bookRepository.getAllReadingSessions(),
        bookRepository.getReadingStatisticsOverview()
    ) { sessions, overview -> LibraryReadingData(sessions, overview) }

    val uiState: StateFlow<BookendsUiState> = combine(
        preferencesManager.settings,
        readingContext,
        sessionProgress,
        library,
        clock
    ) { settings, context, session, libraryData, nowMillis ->
        val preset = settings.resolvePreset(context.fileExtension)
        BookendsUiState(
            settings = settings,
            preset = preset,
            // Dựng snapshot ngay cả khi overlay đang **tắt**: màn cấu hình cần nó cho bản xem trước, và nếu
            // chỉ dựng khi đã bật thì người dùng phải bật trước rồi mới thấy mình vừa bật cái gì — đúng thứ
            // tự ngược lại với nhu cầu "xem thử rồi mới quyết định".
            snapshot = if (context.bookId.isEmpty()) {
                null
            } else {
                BookendsSnapshotAssembler.assemble(
                    context = context,
                    session = session,
                    sessions = libraryData.sessions,
                    overview = libraryData.overview,
                    device = deviceState.read(),
                    nowMillis = nowMillis
                )
            }
        )
    }
        // Đọc pin/độ sáng là lời gọi hệ thống; không nên chạy trên luồng chính mỗi phút một lần.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = BookendsUiState()
        )

    /** Màn đọc báo vị trí đang đọc. `MutableStateFlow` tự bỏ qua giá trị trùng nên gọi mỗi recomposition là an toàn. */
    fun onReadingContextChanged(context: BookendsReadingContext) {
        readingContext.value = context
    }

    /** Bộ theo dõi phiên đọc báo tiến trình của phiên hiện tại. */
    fun onSessionProgressChanged(progress: BookendsSessionProgress) {
        sessionProgress.value = progress
    }

    // ── Cấu hình ────────────────────────────────────────────────────────────────────────────────

    fun setEnabled(enabled: Boolean) = preferencesManager.setEnabled(enabled)

    fun setActivePreset(presetId: String) = preferencesManager.setActivePreset(presetId)

    fun upsertPreset(preset: BookendsPreset, makeActive: Boolean = false) =
        preferencesManager.upsertPreset(preset, makeActive)

    /**
     * Tạo preset mới bằng cách sao chép preset đang dùng.
     *
     * Sao chép thay vì tạo rỗng: người dùng gần như luôn muốn "bản này nhưng khác một chút", còn một preset
     * trống sẽ làm overlay biến mất ngay sau khi bấm tạo — trông như tính năng bị hỏng.
     */
    fun createPresetFromActive(name: String) {
        val settings = uiState.value.settings
        val source = settings.presetById(settings.activePresetId) ?: settings.presets.firstOrNull()
        val preset = (source ?: BookendsDefaults.minimal()).copy(
            id = java.util.UUID.randomUUID().toString(),
            name = name
        )
        preferencesManager.upsertPreset(preset, makeActive = true)
    }

    fun deletePreset(presetId: String) = preferencesManager.deletePreset(presetId)

    fun resetPreset(presetId: String) = preferencesManager.resetPreset(presetId)

    fun setAutoRule(extension: String, presetId: String?) = preferencesManager.setAutoRule(extension, presetId)

    fun removeAutoRule(extension: String) = preferencesManager.removeAutoRule(extension)

    /** Tuỳ chọn hiển thị dùng chung: múi giờ và ngôn ngữ của máy. */
    private data class LibraryReadingData(
        val sessions: List<ReadingSession>,
        val overview: ReadingStatisticsOverview?
    )

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
