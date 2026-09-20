package com.booxbook.core.engine.model

import com.booxbook.core.model.Book

/**
 * Table of Contents item for navigation.
 */
data class TocItem(
    val title: String,
    val href: String,
    val children: List<TocItem> = emptyList()
)

/**
 * Reader user preferences for text rendering and page transitions.
 */
data class ReaderPreferences(
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.2,
    /**
     * Lề trang của Readium, áp đều bốn phía và **chỉ cho EPUB/AZW3**.
     *
     * Giữ ở giá trị mặc định và **không dùng** cho tính năng lề của ứng dụng: Readium chỉ nhận một giá trị cho
     * cả bốn phía, còn lề thật nằm ở ba trường `margin*Dp` bên dưới, làm ở tầng Compose nên áp được cho cả ba
     * định dạng và cho phép trên/dưới khác nhau.
     */
    val pageMargins: Double = 1.0,
    val isScrollMode: Boolean = false, // false = Discrete pagination (lật từng trang)
    val fontFamily: String? = null,
    val isDarkMode: Boolean = false,
    val themePreset: String = "DARK",
    /** Tap-zone layout key, resolved by `ReaderTapZoneMode` in :feature:reader. */
    val tapZoneMode: String = "KINDLE",
    /** Page-turn transition key, resolved by `ReaderPageTurnEffect` in :feature:reader. */
    val pageTurnEffect: String = "SLIDE",
    val hapticsEnabled: Boolean = true,

    /**
     * Lề vùng đọc, đơn vị dp, áp cho **cả ba định dạng**.
     *
     * Đặt ở tầng Compose thay vì dùng `pageMargins` của Readium vì Readium chỉ có **một** giá trị cho cả bốn
     * phía — không đủ cho nhu cầu thật là chừa chỗ khác nhau ở trên (thanh trạng thái, overlay) và ở dưới
     * (thanh công cụ).
     *
     * Trái và phải dùng **chung một giá trị**: chữ chừa hai bên không đều trông như lỗi, còn trên/dưới thì thật
     * sự cần khác nhau. Đổi lại, việc Readium dàn lại trang khi vùng đọc đổi kích thước được xử lý ở
     * `EpubReaderContainer` bằng cách gọi `submitPreferences` sau mỗi lần đổi kích thước.
     */
    val marginTopDp: Float = 0f,
    val marginBottomDp: Float = 0f,
    val marginHorizontalDp: Float = 0f,

    /** Đường viền bao quanh vùng đọc. */
    val frame: ReadingFrame = ReadingFrame()
) {
    /** Có chừa lề nào không. */
    val hasMargins: Boolean
        get() = marginTopDp != 0f || marginBottomDp != 0f || marginHorizontalDp != 0f

    companion object {
        const val MAX_MARGIN_DP = 64f
    }
}

/**
 * State representing reader engine lifecycle and reading session.
 */
sealed interface ReaderState {
    data object Idle : ReaderState

    data class Loading(val book: Book) : ReaderState

    data class Ready(
        val book: Book,
        val tableOfContents: List<TocItem> = emptyList(),
        val totalPages: Int = 0,
        val currentLocator: String? = null,
        val progress: Float = 0f
    ) : ReaderState

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : ReaderState
}
