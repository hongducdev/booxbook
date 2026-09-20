package com.booxbook.core.engine.model

/**
 * Màu nền và màu chữ của trang đọc, theo từng theme.
 *
 * **Một định nghĩa cho cả hai phía.** Trang sách do Readium vẽ trong WebView, còn vùng xung quanh do Compose
 * vẽ; nếu mỗi bên tự chọn màu thì hai thứ sẽ lệch nhau một chút và người đọc thấy một đường ranh giới mờ
 * quanh trang — đã quan sát đúng như vậy trên máy thật (Readium `Theme.DARK` dùng nền riêng của nó, còn app
 * dùng `0xFF141218`).
 *
 * Vì vậy màu nền được truyền thẳng vào `EpubPreferences.backgroundColor`, và giao diện Compose đọc cùng
 * hằng số đó. Sửa một chỗ là cả hai bên đổi theo.
 */
object ReaderThemePalette {

    /** Theme mặc định khi tên đã lưu không còn tồn tại. */
    const val DEFAULT_PRESET = "DARK"

    const val LIGHT_BACKGROUND = 0xFFFEF7FF
    const val LIGHT_TEXT = 0xFF1D1B20

    const val SEPIA_BACKGROUND = 0xFFFBF0D9
    const val SEPIA_TEXT = 0xFF5F4B32

    const val DARK_BACKGROUND = 0xFF141218
    const val DARK_TEXT = 0xFFE6E0E9

    const val AMOLED_BACKGROUND = 0xFF000000
    const val AMOLED_TEXT = 0xFFEDEDED

    /** Nền trang đọc, dạng ARGB. */
    fun backgroundArgb(themePreset: String?): Long = when (themePreset) {
        "LIGHT" -> LIGHT_BACKGROUND
        "SEPIA" -> SEPIA_BACKGROUND
        "AMOLED" -> AMOLED_BACKGROUND
        "DARK" -> DARK_BACKGROUND
        else -> DARK_BACKGROUND
    }

    /** Màu chữ trên trang đọc, dạng ARGB. */
    fun textArgb(themePreset: String?): Long = when (themePreset) {
        "LIGHT" -> LIGHT_TEXT
        "SEPIA" -> SEPIA_TEXT
        "AMOLED" -> AMOLED_TEXT
        "DARK" -> DARK_TEXT
        else -> DARK_TEXT
    }

    /** Theme nền tối hay không — quyết định độ tương phản của overlay và viền khung. */
    fun isDark(themePreset: String?): Boolean =
        themePreset == "DARK" || themePreset == "AMOLED" || themePreset == null
}
