package com.booxbook.core.model.bookends

import kotlinx.serialization.Serializable

/**
 * Sáu vùng neo trên màn đọc, theo đúng lưới vị trí của `bookends.koplugin`.
 *
 * Vùng trên và vùng dưới được xếp theo cột dọc (trái · giữa · phải) ở [BookendsOverlay]; mỗi vùng chứa
 * nhiều dòng, và mỗi dòng tự quyết định style, cỡ chữ và trang nào thì hiện.
 */
@Serializable
enum class BookendsPosition(val key: String, val label: String, val isTop: Boolean) {
    TOP_LEFT("top_left", "Trên · trái", true),
    TOP_CENTER("top_center", "Trên · giữa", true),
    TOP_RIGHT("top_right", "Trên · phải", true),
    BOTTOM_LEFT("bottom_left", "Dưới · trái", false),
    BOTTOM_CENTER("bottom_center", "Dưới · giữa", false),
    BOTTOM_RIGHT("bottom_right", "Dưới · phải", false);

    companion object {
        fun fromKey(key: String?): BookendsPosition? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
enum class BookendsTextStyle { REGULAR, BOLD, ITALIC, BOLD_ITALIC }

/**
 * Lọc dòng theo tính chẵn/lẻ của số trang.
 *
 * Parity tính theo số trang **1-based** vì đó là con số người đọc nhìn thấy, không phải chỉ số mảng.
 */
@Serializable
enum class BookendsPageFilter(val label: String) {
    ALL("Mọi trang"),
    ODD("Trang lẻ"),
    EVEN("Trang chẵn");

    fun matches(pageNumOneBased: Int): Boolean = when (this) {
        ALL -> true
        ODD -> pageNumOneBased % 2 == 1
        EVEN -> pageNumOneBased % 2 == 0
    }
}

@Serializable
enum class BookendsBarType(val label: String) {
    CHAPTER("Chương"),
    BOOK("Sách"),
    BOOK_PLUS("Sách · mốc cấp 1"),
    BOOK_PLUS_PLUS("Sách · mốc 2 cấp")
}

/**
 * Kiểu vẽ thanh tiến độ.
 *
 * `WAVE` dùng chính ngôn ngữ hình ảnh của Material 3 Expressive mà ứng dụng đã dùng cho slider và chỉ báo
 * tải, nên nó hoà vào phần còn lại của giao diện.
 *
 * **Không có `RADIAL`.** Bản gốc có kiểu này, nhưng nó vẽ một vòng tròn — không biểu diễn được trên một
 * thanh tuyến tính chạy hết chiều ngang hoặc dọc. Thêm vào rồi vẽ đại một thanh thẳng với cái tên "radial"
 * sẽ là nói dối về thứ người dùng chọn; muốn có nó thì phải làm một component tròn riêng ở góc màn hình.
 */
@Serializable
enum class BookendsBarStyle(val label: String) {
    SOLID("Đặc"),
    BORDER("Viền ngoài"),
    ROUND("Bo tròn"),
    METRO("Metro"),
    WAVE("Lượn sóng"),
    HOLLOW("Rỗng")
}

@Serializable
data class BookendsBarSpec(
    val type: BookendsBarType = BookendsBarType.BOOK,
    val style: BookendsBarStyle = BookendsBarStyle.ROUND
)

/**
 * Một dòng overlay.
 *
 * [bar] chỉ có tác dụng khi [format] chứa token `%bar`; nó mô tả thanh đó đo tiến độ gì và vẽ kiểu nào.
 */
@Serializable
data class BookendsLine(
    val format: String,
    val style: BookendsTextStyle = BookendsTextStyle.REGULAR,
    val uppercase: Boolean = false,
    val fontSizeSp: Float = 11f,
    val pageFilter: BookendsPageFilter = BookendsPageFilter.ALL,
    val bar: BookendsBarSpec = BookendsBarSpec(),
    /** Dịch ngang cả dòng, đơn vị dp. Dùng `offset` nên không đẩy các dòng khác. */
    val nudgeXDp: Float = 0f,
    /** Dịch dọc cả dòng, đơn vị dp. */
    val nudgeYDp: Float = 0f
) {
    val isBold: Boolean
        get() = style == BookendsTextStyle.BOLD || style == BookendsTextStyle.BOLD_ITALIC

    val isItalic: Boolean
        get() = style == BookendsTextStyle.ITALIC || style == BookendsTextStyle.BOLD_ITALIC

    val isBlank: Boolean
        get() = format.isBlank()
}

/**
 * Một vùng neo và các dòng trong đó.
 *
 * Bốn lề riêng là **tầng thứ hai** của hệ định vị ba tầng (lề chung của preset → lề riêng của vùng → nudge
 * theo dòng). Cần tầng này vì nhu cầu chừa chỗ khác nhau ở từng vùng: khối chữ ở góc trên trái đè lên dòng
 * đầu của trang nếu dùng chung lề trên với các vùng khác — đã quan sát đúng như vậy trên máy thật.
 */
@Serializable
data class BookendsGroup(
    val position: BookendsPosition,
    val lines: List<BookendsLine> = emptyList(),
    val extraMarginTopDp: Float = 0f,
    val extraMarginBottomDp: Float = 0f,
    val extraMarginLeftDp: Float = 0f,
    val extraMarginRightDp: Float = 0f
)

@Serializable
enum class BookendsBarAnchor(val label: String) { TOP("Trên"), BOTTOM("Dưới"), LEFT("Trái"), RIGHT("Phải") }

@Serializable
enum class BookendsBarFill(val label: String) {
    LEFT_TO_RIGHT("Trái → phải"),
    RIGHT_TO_LEFT("Phải → trái"),
    TOP_TO_BOTTOM("Trên → dưới"),
    BOTTOM_TO_TOP("Dưới → trên")
}

@Serializable
enum class BookendsChapterTicks(val label: String) {
    OFF("Không"),
    TOP_LEVEL("Cấp 1"),
    TOP_TWO_LEVELS("Hai cấp đầu")
}

/**
 * Thanh tiến độ full-width: một lớp riêng nằm sau chữ, neo vào một cạnh màn đọc.
 *
 * Khác `%bar` nội dòng: thanh này không chiếm chỗ của chữ, và có thể chạy dọc theo cạnh trái/phải.
 */
@Serializable
data class BookendsBarLayer(
    val id: String,
    val anchor: BookendsBarAnchor = BookendsBarAnchor.BOTTOM,
    val fill: BookendsBarFill = BookendsBarFill.LEFT_TO_RIGHT,
    val style: BookendsBarStyle = BookendsBarStyle.SOLID,
    val thicknessDp: Float = 4f,
    val insetDp: Float = 0f,
    val ticks: BookendsChapterTicks = BookendsChapterTicks.OFF
) {
    val isVertical: Boolean
        get() = anchor == BookendsBarAnchor.LEFT || anchor == BookendsBarAnchor.RIGHT
}

/**
 * Một bộ cấu hình overlay hoàn chỉnh.
 *
 * Margin và font scale thuộc về preset (không phải cài đặt toàn cục) để đổi preset là đổi cả bố cục,
 * đúng như bản gốc phân tách "global settings" và "per-preset styling".
 */
@Serializable
data class BookendsPreset(
    val id: String,
    val name: String,
    val fontScale: Float = 1f,
    val marginTopDp: Float = 12f,
    val marginBottomDp: Float = 12f,
    val marginLeftDp: Float = 16f,
    val marginRightDp: Float = 16f,
    /** Khoảng cách tối thiểu giữa hai vùng cùng hàng. */
    val truncationGapDp: Float = 24f,
    val groups: List<BookendsGroup> = emptyList(),
    val barLayers: List<BookendsBarLayer> = emptyList()
) {
    fun linesAt(position: BookendsPosition): List<BookendsLine> =
        groups.firstOrNull { it.position == position }?.lines.orEmpty()

    /** Preset không còn nội dung nào — dùng để chặn trạng thái "đang bật nhưng không thấy gì". */
    val hasContent: Boolean
        get() = groups.any { group -> group.lines.any { !it.isBlank } } || barLayers.isNotEmpty()

    fun withLines(position: BookendsPosition, lines: List<BookendsLine>): BookendsPreset {
        val kept = lines.filter { !it.isBlank }
        val existing = groups.firstOrNull { it.position == position }
        val withoutPosition = groups.filter { it.position != position }
        val nextGroups = if (kept.isEmpty()) {
            withoutPosition
        } else {
            // Giữ lại lề riêng đã đặt cho vùng này; nếu không thì mỗi lần sửa một dòng là mất lề.
            withoutPosition + (existing?.copy(lines = kept) ?: BookendsGroup(position, kept))
        }
        return copy(groups = nextGroups.sortedBy { it.position.ordinal })
    }

    /** Đặt lề riêng cho một vùng. Vùng chưa có dòng nào vẫn được tạo để giữ giá trị người dùng vừa nhập. */
    fun withMargins(
        position: BookendsPosition,
        topDp: Float,
        bottomDp: Float,
        leftDp: Float,
        rightDp: Float
    ): BookendsPreset {
        val existing = groups.firstOrNull { it.position == position } ?: BookendsGroup(position)
        val updated = existing.copy(
            extraMarginTopDp = topDp,
            extraMarginBottomDp = bottomDp,
            extraMarginLeftDp = leftDp,
            extraMarginRightDp = rightDp
        )
        return copy(
            groups = (groups.filter { it.position != position } + updated)
                .sortedBy { it.position.ordinal }
        )
    }

    fun groupAt(position: BookendsPosition): BookendsGroup? =
        groups.firstOrNull { it.position == position }
}

/**
 * Quy tắc chọn preset theo đuôi tệp.
 *
 * [presetId] `null` nghĩa là **ẩn hẳn** overlay cho loại tệp đó — đúng ca dùng chính của bản gốc: truyện
 * tranh CBZ cần sạch khung hình.
 */
@Serializable
data class BookendsAutoRule(
    val extension: String,
    val presetId: String? = null
) {
    val isHidden: Boolean
        get() = presetId == null

    companion object {
        fun normalizeExtension(raw: String): String = raw.trim().lowercase().removePrefix(".")
    }
}

/**
 * Toàn bộ trạng thái cấu hình Bookends, được lưu thành một khối JSON trong SharedPreferences.
 */
@Serializable
data class BookendsSettings(
    val enabled: Boolean = false,
    val activePresetId: String = BookendsDefaults.MINIMAL_PRESET_ID,
    val presets: List<BookendsPreset> = BookendsDefaults.builtIn(),
    val autoRules: List<BookendsAutoRule> = emptyList()
) {
    fun presetById(id: String): BookendsPreset? = presets.firstOrNull { it.id == id }

    /**
     * Preset đang dùng, đã tính cả quy tắc theo đuôi tệp.
     *
     * Trả `null` khi overlay phải ẩn hẳn cho định dạng này, hoặc khi tắt ở cài đặt chung.
     */
    fun resolvePreset(fileExtension: String?): BookendsPreset? {
        if (!enabled) return null
        val rule = fileExtension
            ?.let { BookendsAutoRule.normalizeExtension(it) }
            ?.let { ext -> autoRules.firstOrNull { BookendsAutoRule.normalizeExtension(it.extension) == ext } }
        if (rule != null) {
            return rule.presetId?.let(::presetById)
        }
        return presetById(activePresetId)
    }
}

/**
 * Preset dựng sẵn.
 *
 * `minimal` là mặc định: chỉ một dòng ở đáy, đủ để thấy tính năng mà không đè lên chữ. `standard` là bản
 * đầy đủ hơn cho người muốn nhiều thông tin, tương ứng "Basic bookends" của bản gốc.
 */
object BookendsDefaults {
    const val MINIMAL_PRESET_ID = "bookends.minimal"
    const val STANDARD_PRESET_ID = "bookends.standard"

    fun minimal(): BookendsPreset = BookendsPreset(
        id = MINIMAL_PRESET_ID,
        name = "Tối giản",
        groups = listOf(
            BookendsGroup(
                position = BookendsPosition.BOTTOM_CENTER,
                lines = listOf(BookendsLine(format = "%page_num / %page_count"))
            )
        )
    )

    fun standard(): BookendsPreset = BookendsPreset(
        id = STANDARD_PRESET_ID,
        name = "Đầy đủ",
        groups = listOf(
            BookendsGroup(
                position = BookendsPosition.TOP_LEFT,
                lines = listOf(
                    BookendsLine(
                        format = "%<chap_title>",
                        style = BookendsTextStyle.ITALIC,
                        fontSizeSp = 10f
                    )
                )
            ),
            BookendsGroup(
                position = BookendsPosition.TOP_RIGHT,
                lines = listOf(BookendsLine(format = "%book_pct", fontSizeSp = 10f))
            ),
            BookendsGroup(
                position = BookendsPosition.BOTTOM_LEFT,
                lines = listOf(BookendsLine(format = "%title", fontSizeSp = 9f))
            ),
            BookendsGroup(
                position = BookendsPosition.BOTTOM_CENTER,
                lines = listOf(
                    BookendsLine(
                        format = "%page_num / %page_count%spacer%bar",
                        fontSizeSp = 10f
                    )
                ),
                // Vùng dưới giữa là vùng duy nhất có thanh tiến độ trong preset này; nó cần chừa thêm chỗ
                // để thanh không dính vào lề dưới của trang.
                extraMarginBottomDp = 4f
            ),
            BookendsGroup(
                position = BookendsPosition.BOTTOM_RIGHT,
                lines = listOf(
                    BookendsLine(
                        format = "[if:book_time_left]%<book_time_left> còn lại[/if]",
                        fontSizeSp = 9f
                    )
                )
            )
        )
    )

    fun builtIn(): List<BookendsPreset> = listOf(minimal(), standard())
}
