package com.booxbook.core.model.bookends

import com.booxbook.core.model.ReadingStatus

/**
 * Một mục lục đã được làm phẳng theo thứ tự tài liệu, kèm độ sâu và vị trí bắt đầu.
 *
 * `core:model` không được biết tới `TocItem` của `core:engine` (engine phụ thuộc model, không phải
 * ngược lại), nên đây là biểu diễn trung lập mà tầng reader tự ánh xạ sang.
 *
 * @param progression vị trí bắt đầu của mục, 0..1 theo toàn sách.
 */
data class BookendsTocNode(
    val title: String,
    val depth: Int,
    val progression: Double
)

private const val PROGRESSION_EPSILON = 1e-6

/**
 * Một chương đã được định vị theo vị trí đọc hiện tại.
 *
 * [progression] được tính sẵn lúc dựng thay vì suy ra từ vị trí hiện tại, để kiểu dữ liệu giữ được tính
 * bất biến — mọi toán hạng của [BookendsFormatter] phải bất biến thì kết quả mới tái lập được.
 */
data class BookendsChapterSpan(
    val title: String,
    val index: Int,
    val count: Int,
    val startProgression: Double,
    val endProgression: Double,
    /** Tiến độ đọc trong chương, 0..1. Chương dài 0 được coi là đã đọc xong. */
    val progression: Double
) {
    companion object {
        fun of(
            title: String,
            index: Int,
            count: Int,
            startProgression: Double,
            endProgression: Double,
            currentProgression: Double
        ): BookendsChapterSpan {
            val length = endProgression - startProgression
            val fraction = if (length <= PROGRESSION_EPSILON) {
                1.0
            } else {
                ((currentProgression - startProgression) / length).coerceIn(0.0, 1.0)
            }
            return BookendsChapterSpan(title, index, count, startProgression, endProgression, fraction)
        }
    }
}

/**
 * Chỉ mục mục lục: trả lời "đang ở chương nào" cho một vị trí đọc bất kỳ.
 *
 * Mục lục EPUB lồng nhiều cấp và có sách đánh dấu mục ở từng đoạn văn, nên mọi truy vấn đều nhận thêm
 * một `maxDepth` để người dùng chọn đo theo cấp nào (tương ứng `%chap_pct_1` của bản gốc).
 */
data class BookendsChapterIndex(val nodes: List<BookendsTocNode> = emptyList()) {

    /** Cấp sâu nhất có trong mục lục; 0 khi sách không có mục lục. */
    val maxDepth: Int = nodes.maxOfOrNull { it.depth } ?: 0

    val isEmpty: Boolean
        get() = nodes.isEmpty()

    /**
     * Các mục **đúng cấp** [level], theo thứ tự tài liệu.
     *
     * Đây là định nghĩa "chương" mà `%chap_num`/`%chap_count` đếm. Không dùng "cấp ≤ N" vì với mục lục
     * hai tầng (Phần → Chương) cách đó sẽ đếm cả tiêu đề nhóm lẫn chương: sách 2 phần 24 chương sẽ báo
     * "chương 13/26" thay vì "13/24".
     *
     * Đổi lại, mục lục trộn cấp ở cùng một tầng (vừa có mục lá vừa có mục có mục con) có thể bị đếm thiếu
     * — chấp nhận được vì tên chương vẫn lấy đúng theo [titleAt].
     */
    fun chaptersAt(level: Int): List<BookendsTocNode> =
        nodes.filter { it.depth == level.coerceIn(1, maxDepth.coerceAtLeast(1)) }

    /**
     * Chương bao quanh [progression], đo theo các mục đúng cấp [level].
     *
     * Trả `null` khi sách không có mục lục.
     */
    fun span(progression: Double, level: Int = maxDepth): BookendsChapterSpan? {
        if (nodes.isEmpty()) return null

        val scopedLevel = level.coerceIn(1, maxDepth.coerceAtLeast(1))
        val levelNodes = chaptersAt(scopedLevel)
        if (levelNodes.isEmpty()) return null

        // Chương đang đọc là mục cùng cấp gần nhất đã bắt đầu; 0 nghĩa là đang đứng trước chương đầu.
        val index = levelNodes.indexOfLast { it.progression <= progression + PROGRESSION_EPSILON } + 1
        val startProgression = levelNodes.getOrNull(index - 1)?.progression ?: 0.0
        val endProgression = levelNodes.getOrNull(index)?.progression ?: 1.0

        return BookendsChapterSpan.of(
            title = titleAt(progression, scopedLevel),
            index = index,
            count = levelNodes.size,
            startProgression = startProgression,
            endProgression = endProgression.coerceAtLeast(startProgression),
            currentProgression = progression
        )
    }

    /**
     * Tiêu đề mục đang đọc, xét các mục có cấp ≤ [level].
     *
     * Trả **chuỗi rỗng** khi vị trí đang đứng trước mục đầu tiên của mục lục — thường gặp ở phần đầu sách
     * (bìa, trang tên, lời tựa) vì mục lục hay bắt đầu từ chương 1. Trước đây chỗ này rơi về `scoped.first()`
     * và như vậy là **bịa**: đang ở trang bìa mà `%chap_title` lại khẳng định đang ở chương đầu. Bỏ trống để
     * dòng tự ẩn vẫn trung thực hơn.
     */
    fun titleAt(progression: Double, level: Int = maxDepth): String {
        if (nodes.isEmpty()) return ""
        val scoped = nodes.filter { it.depth <= level.coerceAtLeast(1) }
        return scoped.lastOrNull { it.progression <= progression + PROGRESSION_EPSILON }?.title.orEmpty()
    }

    /** Số chương ở cấp [level]. Khớp `%chap_count`. */
    fun countAt(level: Int = maxDepth): Int = chaptersAt(level).size

    /**
     * Vị trí bắt đầu của mọi mục ở cấp ≤ [level], dùng để vẽ mốc chương trên thanh tiến độ.
     *
     * Trả về cặp (vị trí, cấp) để renderer giảm độ dày dần theo cấp, giống bản gốc.
     */
    fun ticksUpTo(level: Int): List<Pair<Double, Int>> =
        nodes.filter { it.depth <= level.coerceAtLeast(1) }.map { it.progression to it.depth }
}

/** Số lượng chú thích của cuốn sách đang đọc. */
data class BookendsAnnotationCounts(
    val highlights: Int = 0,
    val notes: Int = 0,
    val bookmarks: Int = 0
) {
    val total: Int
        get() = highlights + notes + bookmarks
}

/**
 * Ảnh chụp mọi giá trị mà token có thể cần, tại một thời điểm.
 *
 * Đây là **đầu vào duy nhất** của [BookendsFormatter]. Nhờ vậy tầng resolve hoàn toàn thuần khiết:
 * không đọc Room, không đọc đồng hồ hệ thống, không chạm Android — nên test được bằng JUnit thường và
 * kết quả không phụ thuộc thời điểm chạy.
 *
 * Giá trị nào không có dữ liệu thì để mặc định rỗng/0; token tương ứng sẽ trả chuỗi rỗng để dòng tự ẩn
 * theo cơ chế auto-hide, thay vì hiện "0" hay "null".
 */
data class BookendsSnapshot(
    // ── Metadata ────────────────────────────────────────────────────────────────────────────────
    val title: String = "",
    val author: String = "",
    val authors: List<String> = emptyList(),
    val series: String = "",
    /** Tên bộ kèm số tập (`Dune #1`). Nguồn duy nhất là [com.booxbook.core.model.Book.seriesLabel]. */
    val seriesLabel: String = "",
    val seriesNumber: String = "",
    val description: String = "",
    val language: String = "",
    val tags: List<String> = emptyList(),
    val format: String = "",
    val filename: String = "",
    val rating: Int = 0,
    val isFavourite: Boolean = false,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val addedMillis: Long? = null,
    val fileSizeBytes: Long? = null,

    // ── Vị trí đọc ──────────────────────────────────────────────────────────────────────────────
    val pageNum: Int = 0,
    val pageCount: Int = 0,
    val bookProgression: Double = 0.0,
    /** Tỉ lệ đọc tính theo thống kê (bỏ qua các trang lướt nhanh). `null` khi chưa đủ dữ liệu. */
    val bookProgressionFromStats: Double? = null,
    val chapterTitle: String = "",
    val chapters: BookendsChapterIndex = BookendsChapterIndex(),
    val annotations: BookendsAnnotationCounts = BookendsAnnotationCounts(),

    // ── Phiên đọc & thống kê ────────────────────────────────────────────────────────────────────
    val sessionSeconds: Long = 0L,
    val sessionPages: Int = 0,
    val bookReadSeconds: Long = 0L,
    val totalReadSeconds: Long = 0L,
    val bookPagesRead: Int = 0,
    val pagesToday: Int = 0,
    val timeTodaySeconds: Long = 0L,
    val streakDays: Int = 0,
    val bookStreakDays: Int = 0,
    val daysReadingBook: Int = 0,
    val booksFinished: Int = 0,

    // ── Thiết bị ────────────────────────────────────────────────────────────────────────────────
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val isWifiOn: Boolean = false,
    val isConnected: Boolean = true,
    val lightPercent: Int? = null,

    // ── Đồng hồ & tuỳ chọn ──────────────────────────────────────────────────────────────────────
    val nowMillis: Long = 0L,
    val isInverted: Boolean = false,
    val includeCurrentPageInPagesLeft: Boolean = false
) {
    /** Số trang còn lại. Bản gốc mặc định **không** tính trang đang đọc. */
    val pagesLeft: Int
        get() {
            if (pageCount <= 0) return 0
            val consumed = if (includeCurrentPageInPagesLeft) pageNum else pageNum - 1
            return (pageCount - consumed).coerceAtLeast(0)
        }

    /**
     * Số giây trung bình cho một trang, suy từ thống kê. `null` khi dữ liệu **không đủ tin** để ước lượng.
     *
     * Có hai chốt chặn, cả hai đều do quan sát trên máy thật mà có:
     *
     * 1. **Mẫu quá nhỏ.** Đọc 2 trang rồi chia cho thời gian tích luỹ của cả phiên sẽ ra một tốc độ vô nghĩa.
     * 2. **Tốc độ phi thực tế.** Bộ theo dõi phiên đọc của ứng dụng tính thời gian theo thời gian màn hình bật,
     *    nên một cuốn sách mới đọc 11 trang nhưng tích 4,5 giờ sẽ cho 25 phút/trang — và `%book_time_left`
     *    hiện "118h 35m còn lại" cho một cuốn tiểu thuyết 297 trang. Trả `null` để token tự ẩn: **không có số**
     *    vẫn tốt hơn một con số khiến người đọc mất tin vào cả lớp overlay.
     *
     * Cố ý **không** kẹp giá trị về ngưỡng: kẹp chỉ tạo ra một con số sai khác. Đây là chuyện "chưa đo được",
     * không phải "đo được nhưng cần chỉnh".
     */
    val avgSecondsPerPage: Double?
        get() {
            if (bookPagesRead < MIN_PAGES_FOR_ESTIMATE || bookReadSeconds <= 0L) return null
            val perPage = bookReadSeconds.toDouble() / bookPagesRead
            return perPage.takeIf { it in MIN_SECONDS_PER_PAGE..MAX_SECONDS_PER_PAGE }
        }

    /** Tốc độ đọc, trang/giờ. `null` khi chưa ước lượng được. */
    val pagesPerHour: Double?
        get() = avgSecondsPerPage?.takeIf { it > 0.0 }?.let { 3600.0 / it }

    /** Ước lượng số giây còn lại của cuốn sách, dựa trên tốc độ đọc thực tế. */
    val bookSecondsLeft: Long?
        get() = avgSecondsPerPage?.let { perPage ->
            if (pagesLeft <= 0) null else (perPage * pagesLeft).toLong()
        }

    /** Ước lượng số giây còn lại của chương hiện tại. */
    fun chapterSecondsLeft(level: Int = chapters.maxDepth): Long? {
        val perPage = avgSecondsPerPage ?: return null
        val bookSpan = chapters.span(bookProgression, level) ?: return null
        val spanLength = bookSpan.endProgression - bookSpan.startProgression
        if (spanLength <= 0.0 || pageCount <= 0) return null
        val remainingFraction = (1.0 - bookSpan.progression).coerceIn(0.0, 1.0)
        val pagesInChapter = spanLength * pageCount
        return (pagesInChapter * remainingFraction * perPage).toLong()
    }

    /** Chương hiện tại tại một cấp mục lục cho trước. */
    fun chapter(level: Int = chapters.maxDepth): BookendsChapterSpan? =
        chapters.span(bookProgression, level)

    companion object {
        /** Dưới ngưỡng này thì tốc độ đọc suy ra được là nhiễu, không phải đo lường. */
        const val MIN_PAGES_FOR_ESTIMATE = 5

        /** Nhanh hơn 0,5 trang/giây thì không phải đang đọc. */
        const val MIN_SECONDS_PER_PAGE = 2.0

        /** Chậm hơn 5 phút/trang thì thời gian tích luỹ không còn là thời gian đọc. */
        const val MAX_SECONDS_PER_PAGE = 300.0
    }
}
