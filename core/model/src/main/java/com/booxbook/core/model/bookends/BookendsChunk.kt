package com.booxbook.core.model.bookends

/**
 * Một mảnh đầu ra của [BookendsFormatter], đã resolve xong và sẵn sàng để vẽ.
 *
 * Vì sao không trả về `String`: `%bar` và `%spacer` cần layout của Compose (`weight(1f)`) mới biểu diễn
 * được, còn icon thì nên dùng Material Icons thay vì glyph của Nerd Fonts như bản gốc. Ba thứ đó không
 * nhét vừa trong một chuỗi, nên tầng resolve trả về cấu trúc và tầng vẽ quyết định hình thức.
 */
sealed interface BookendsChunk {

    /** Đoạn chữ thuần, kèm định dạng nội dòng đang hiệu lực tại vị trí đó. */
    data class Text(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val uppercase: Boolean = false,
        /** Giới hạn bề rộng từ cú pháp `%token{N}`, đơn vị dp. `null` là không giới hạn. */
        val maxWidthDp: Int? = null
    ) : BookendsChunk

    /** Icon động theo trạng thái thiết bị (pin, Wi-Fi, đèn nền…). */
    data class Icon(val icon: BookendsIcon, val description: String = "") : BookendsChunk

    /** Thanh tiến độ nội dòng sinh bởi token `%bar`; co giãn hết bề rộng còn lại của dòng. */
    data class ProgressBar(
        val type: BookendsBarType,
        val style: BookendsBarStyle,
        val maxWidthDp: Int? = null
    ) : BookendsChunk

    /** `%spacer`: hút hết bề rộng trống để đẩy phần chữ phía sau ra sát mép đối diện. */
    data object Spacer : BookendsChunk
}

/** Tập icon tối thiểu thay cho bảng glyph Nerd Fonts của bản gốc. */
enum class BookendsIcon(val label: String) {
    BATTERY("Pin"),
    BATTERY_CHARGING("Đang sạc"),
    WIFI("Wi-Fi"),
    WIFI_OFF("Mất Wi-Fi"),
    LIGHT("Đèn nền"),
    LIGHT_OFF("Đèn nền tắt"),
    NIGHT_MODE("Chế độ tối"),
    INVERT("Đảo hướng lật trang")
}

/**
 * Kết quả cuối cùng cho một dòng overlay.
 *
 * [isBlank] là cơ chế auto-hide: dòng mà mọi token đều rỗng/không (ví dụ `[if:…]` không thoả, hoặc
 * `%book_time_left` khi chưa đủ dữ liệu thống kê) sẽ tự biến mất thay vì để lại khoảng trắng lơ lửng.
 * Chỉ những dòng thật sự có nội dung hiển thị mới được vẽ.
 */
data class BookendsRender(
    val chunks: List<BookendsChunk> = emptyList(),
    val isBlank: Boolean = true
) {
    companion object {
        val Empty = BookendsRender()
    }
}
