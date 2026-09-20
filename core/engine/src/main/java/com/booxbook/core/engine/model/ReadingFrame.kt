package com.booxbook.core.engine.model

/**
 * Đường viền bao quanh vùng đọc.
 *
 * Tách khỏi [ReaderPreferences] thành kiểu riêng vì nó là một **nhóm** cấu hình luôn đi cùng nhau: bật/tắt,
 * độ dày, bo góc, kiểu nét, màu. Nhét sáu trường rời vào `ReaderPreferences` sẽ làm mọi nơi truyền cài đặt
 * phải nhớ sáu cái tên thay vì một.
 *
 * Đơn vị là dp chứ không phải px: cùng một giá trị phải cho cùng một độ dày trên màn 1080p và 1440p.
 */
data class ReadingFrame(
    val enabled: Boolean = false,
    val thicknessDp: Float = 2f,
    val cornerRadiusDp: Float = 12f,
    /**
     * Khoảng cách từ viền vào mép vùng đọc.
     *
     * Cho phép âm để đẩy viền ra ngoài vùng đọc — hữu ích khi người đọc muốn viền sát mép màn hình hơn là
     * sát chữ.
     */
    val insetDp: Float = 6f,
    val style: ReadingFrameStyle = ReadingFrameStyle.SOLID,
    val color: ReadingFrameColor = ReadingFrameColor.AUTO
) {
    companion object {
        const val MIN_THICKNESS_DP = 1f
        const val MAX_THICKNESS_DP = 8f
        const val MAX_CORNER_RADIUS_DP = 40f
        const val MIN_INSET_DP = -12f
        const val MAX_INSET_DP = 32f
    }
}

enum class ReadingFrameStyle(val label: String) {
    SOLID("Liền"),
    DASHED("Nét đứt"),
    DOTTED("Chấm")
}

/**
 * Màu viền.
 *
 * Cố ý là **bảng chọn nhỏ** chứ không phải color picker tự do: trên màn e-ink một màu tuỳ ý rất dễ ra không
 * đủ tương phản với nền giấy, và người đọc chỉ phát hiện ra khi đã chọn xong.
 */
enum class ReadingFrameColor(val label: String) {
    /** Tương phản với nền đọc hiện tại — mặc định an toàn cho cả bốn theme. */
    AUTO("Tự động"),

    /** Màu nhấn của ứng dụng. */
    ACCENT("Màu nhấn"),

    /** Nâu giấy, hợp với nền sepia. */
    WARM("Giấy ấm")
}
