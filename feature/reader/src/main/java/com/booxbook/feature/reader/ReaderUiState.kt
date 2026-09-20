package com.booxbook.feature.reader

import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat

enum class ActiveReaderSheet {
    TOC,
    SETTINGS,
    BOOKMARKS,
    BOOKENDS
}

enum class ReaderThemePreset(val displayName: String) {
    LIGHT("Sáng"),
    SEPIA("Giấy ấm"),
    DARK("Tối"),
    AMOLED("Đen tuyền")
}

/**
 * Suy theme từ tên đã lưu trong cài đặt.
 *
 * Tên lạ (bản cũ để lại, hoặc người dùng sửa tay) rơi về [ReaderThemePreset.DARK] thay vì ném lỗi — mất một
 * lựa chọn hiển thị là thiệt hại nhỏ hơn hẳn một màn đọc không mở được.
 */
fun ReaderPreferences.toThemePreset(): ReaderThemePreset =
    ReaderThemePreset.entries.firstOrNull { it.name == themePreset } ?: ReaderThemePreset.DARK

/**
 * Các bước của quá trình mở một cuốn sách.
 *
 * Đây là *mốc công việc*, không phải phần trăm thời gian: các bước không tỉ lệ với nhau và thời lượng
 * thật phụ thuộc định dạng sách lẫn tốc độ thiết bị. Vì vậy màn chờ không vẽ thanh determinate từ
 * những giá trị này — Material 3 yêu cầu chỉ báo determinate phải đo đúng tiến trình, còn chờ không đo
 * được thì dùng loading indicator kèm mô tả từng bước.
 */
enum class ReaderLoadingPhase {
    /** Đang đọc bản ghi sách và tiến độ đọc đã lưu từ Room. */
    LOADING_BOOK,

    /** Đã biết sách, đang mở publication/giải nén kho truyện. */
    OPENING_PUBLICATION,

    /** Engine đã sẵn sàng, đang chờ trang đọc đầu tiên vẽ xong. */
    BUILDING_CANVAS,

    /** Quá trình mở đã kết thúc: trang đọc đã hiện, hoặc đã chuyển sang màn lỗi. */
    READY
}

/**
 * State representing the active reading session.
 */
data class ReaderUiState(
    /** Engine đã mở xong và state sách đã được nạp. Không dùng cờ này để quyết định hiển thị canvas. */
    val isLoading: Boolean = true,
    /** Bước hiện tại của quá trình mở sách, điều khiển màn chờ có ngữ cảnh. */
    val loadingPhase: ReaderLoadingPhase = ReaderLoadingPhase.LOADING_BOOK,
    val book: Book? = null,
    val format: BookFormat = BookFormat.EPUB,
    val tableOfContents: List<TocItem> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    /**
     * Tổng số trang **thật** của cuốn sách, đọc từ `Publication.positions()` của Readium.
     *
     * Tách khỏi [totalPages] vì hai đại lượng đo hai thứ khác nhau với EPUB: [totalPages] là **số mục trong
     * thứ tự đọc** (số tệp XHTML), còn [currentPage] là `Locator.locations.position` — chỉ số **trang** đếm
     * từ 1 trên toàn publication. Ghép chúng lại sẽ ra `11 / 93` cho một cuốn tiểu thuyết 93 tệp chương.
     *
     * Bằng 0 khi chưa đọc xong `positions()`, để token tự ẩn thay vì hiện một tổng số sai.
     *
     * **Không** dùng `EpubNavigatorFragment.PaginationListener` cho việc này: nó báo số trang **trong từng
     * tệp chương** (`positionsByReadingOrder`), nên ghép `position` toàn sách với `totalPages` theo chương
     * sẽ ra `11 / 8` — đã quan sát đúng như vậy trên máy thật.
     */
    val displayPageCount: Int = 0,
    val progressPercentage: Float = 0f,
    val currentChapterTitle: String = "",
    val currentLocator: String? = null,
    val isControlsVisible: Boolean = false,
    val preferences: ReaderPreferences = ReaderPreferences(),
    val themePreset: ReaderThemePreset = ReaderThemePreset.DARK,
    val activeSheet: ActiveReaderSheet? = null,
    val annotations: List<Annotation> = emptyList(),
    val isCurrentLocationBookmarked: Boolean = false,
    val isTtsActive: Boolean = false,
    val ttsSentenceHighlight: String? = null,
    val ttsSentenceLocator: String? = null,
    val errorMessage: String? = null
) {
    /**
     * Đang ở một bước nào đó của quá trình mở sách.
     *
     * Trong lúc mở, trang đọc vẫn được dựng ngầm *bên dưới* màn chờ — nếu chỉ dựng canvas sau khi màn
     * chờ biến mất thì Readium không bao giờ báo sẵn sàng và người đọc sẽ thấy một khoảng trắng. Vì
     * vậy màn chờ là một lớp phủ, còn tiến trình mở sách được theo dõi bằng [loadingPhase].
     */
    val isPreparing: Boolean
        get() = !isCanvasReady && errorMessage == null

    /** Quá trình mở sách đã kết thúc: hoặc trang đọc đã hiện, hoặc đã chuyển sang màn lỗi. */
    val isCanvasReady: Boolean
        get() = loadingPhase == ReaderLoadingPhase.READY
}
