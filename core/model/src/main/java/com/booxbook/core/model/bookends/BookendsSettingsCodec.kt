package com.booxbook.core.model.bookends

import kotlinx.serialization.json.Json

/**
 * Mã hoá cấu hình Bookends thành một chuỗi JSON và đọc ngược lại.
 *
 * Nằm ở `core:model` chứ không ở tầng lưu trữ vì đây là **định dạng dữ liệu**, không phải cơ chế lưu: ai
 * lưu cũng được (SharedPreferences, Room, tệp chia sẻ preset), miễn là đi qua đúng một bộ mã hoá. Nhờ vậy
 * `:feature:reader` không cần phụ thuộc `kotlinx-serialization` chỉ để ghi một chuỗi.
 */
object BookendsSettingsCodec {

    private val json = Json {
        // Bản cũ còn khoá lạ sau khi nâng cấp không được làm hỏng cả cấu hình: bỏ qua chúng.
        ignoreUnknownKeys = true
        // Ghi cả giá trị mặc định để tệp preset đọc được bằng mắt và sửa tay được.
        encodeDefaults = true
    }

    fun encode(settings: BookendsSettings): String =
        json.encodeToString(BookendsSettings.serializer(), settings)

    /**
     * Đọc cấu hình, rơi về mặc định khi chuỗi rỗng hoặc hỏng.
     *
     * JSON hỏng là chuyện có thật: ghi dở khi bị kill, người dùng sửa tay, hoặc một bản cũ để lại khoá mang
     * kiểu khác. Không trường hợp nào được phép làm sập màn đọc — mất cấu hình là thiệt hại nhỏ hơn nhiều.
     */
    fun decode(raw: String?): BookendsSettings {
        if (raw.isNullOrBlank()) return BookendsSettings()
        val decoded = runCatching { json.decodeFromString<BookendsSettings>(raw) }.getOrNull()
            ?: return BookendsSettings()
        return if (decoded.presets.isEmpty()) {
            decoded.copy(presets = BookendsDefaults.builtIn())
        } else {
            decoded
        }
    }
}
