package com.booxbook.core.engine.epub

import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.ReaderThemePalette
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

/**
 * Dựng [EpubPreferences] từ [ReaderPreferences].
 *
 * Trước đây `EpubReaderEngine` và `Azw3ReaderEngine` mỗi bên có một bản sao của hàm này. Hai bản sao của cùng
 * một phép ánh xạ cài đặt là cách chắc chắn nhất để một bên được sửa còn bên kia thì không — và triệu chứng
 * sẽ là "EPUB đúng mà AZW3 sai" với cùng một cài đặt.
 *
 * Bốn điểm ánh xạ đáng lưu ý:
 *
 * - **`pageMargins` là lề thật cho EPUB/AZW3.** Readium chỉ nhận **một hệ số** cho cả bốn phía, không có API
 *   per-side. Đây vẫn là đường đúng: Readium tự dàn lại trang khi giá trị này đổi, còn padding ở tầng Compose
 *   làm WebView hẹp lại mà pager giữ nguyên bề rộng trang cũ — gây chữ chồng và tràn (đã quan sát trên máy).
 * - **Màu nền và màu chữ được ghi đè** bằng bảng màu của ứng dụng. Không có hai dòng đó thì Readium dùng nền
 *   riêng của `Theme.DARK` còn Compose dùng nền của app, và người đọc thấy một vệt lệch màu quanh trang.
 * - **`Theme` vẫn được đặt** dù đã có màu tường minh: Readium dùng nó cho các phần khác (màu liên kết, ảnh).
 * - `scroll = false` là lật từng trang; chế độ cuộn chưa được giao diện bật.
 */
object EpubPreferencesFactory {

    fun build(prefs: ReaderPreferences): EpubPreferences {
        val resolvedTheme = when (prefs.themePreset) {
            "SEPIA" -> Theme.SEPIA
            "LIGHT" -> Theme.LIGHT
            "DARK", "AMOLED" -> Theme.DARK
            else -> if (prefs.isDarkMode) Theme.DARK else Theme.LIGHT
        }

        return EpubPreferences(
            scroll = prefs.isScrollMode,
            fontSize = prefs.fontSize,
            lineHeight = prefs.lineHeight,
            pageMargins = prefs.pageMargins,
            fontFamily = prefs.fontFamily?.let { FontFamily(it) },
            theme = resolvedTheme,
            backgroundColor = ReadiumColor(ReaderThemePalette.backgroundArgb(prefs.themePreset).toInt()),
            textColor = ReadiumColor(ReaderThemePalette.textArgb(prefs.themePreset).toInt())
        )
    }
}
