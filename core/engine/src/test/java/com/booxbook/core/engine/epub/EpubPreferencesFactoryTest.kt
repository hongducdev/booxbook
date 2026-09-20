package com.booxbook.core.engine.epub

import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.ReaderThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ánh xạ cài đặt đọc sang `EpubPreferences`.
 *
 * Hai thứ ở đây từng gây lỗi thật trên máy, nên được khoá lại:
 *
 * 1. **Màu nền.** Readium vẽ nền trang theo `Theme` của chính nó; Compose vẽ nền xung quanh theo bảng màu của
 *    ứng dụng. Không ghi đè `backgroundColor` thì hai nền lệch nhau và người đọc thấy một vệt ranh giới quanh
 *    trang.
 * 2. **Lề.** Readium chỉ nhận **một** hệ số lề cho cả bốn phía. Đây là đường đúng để chừa lề cho EPUB/AZW3 vì
 *    Readium tự dàn lại trang khi giá trị đổi.
 *
 * Chạy bằng Robolectric: enum `Theme` của Readium chạm lớp Android khi khởi tạo, nên JVM thuần không dựng nổi
 * `EpubPreferences`.
 */
@RunWith(RobolectricTestRunner::class)
class EpubPreferencesFactoryTest {

    @Test
    fun `le trang duoc truyen thang vao readium`() {
        val preferences = EpubPreferencesFactory.build(ReaderPreferences(pageMargins = 1.8))

        assertEquals(1.8, preferences.pageMargins!!, 1e-9)
    }

    @Test
    fun `mau nen va mau chu khop bang mau cua ung dung`() {
        listOf("LIGHT", "SEPIA", "DARK", "AMOLED").forEach { preset ->
            val preferences = EpubPreferencesFactory.build(ReaderPreferences(themePreset = preset))

            assertEquals(
                "Nền trang của theme $preset không khớp bảng màu của ứng dụng",
                ReaderThemePalette.backgroundArgb(preset).toInt(),
                preferences.backgroundColor!!.int
            )
            assertEquals(
                "Màu chữ của theme $preset không khớp bảng màu của ứng dụng",
                ReaderThemePalette.textArgb(preset).toInt(),
                preferences.textColor!!.int
            )
        }
    }

    @Test
    fun `bon theme co mau nen khac nhau`() {
        val backgrounds = listOf("LIGHT", "SEPIA", "DARK", "AMOLED")
            .map { ReaderThemePalette.backgroundArgb(it) }

        assertEquals(backgrounds.size, backgrounds.distinct().size)
    }

    @Test
    fun `ten theme la khong lam mat mau nen`() {
        // Tên lạ (bản cũ để lại, sửa tay) rơi về nền tối. Điều không được phép xảy ra là `backgroundColor`
        // thành null — Readium sẽ quay lại dùng nền của `Theme` và vệt lệch màu xuất hiện trở lại.
        val preferences = EpubPreferencesFactory.build(ReaderPreferences(themePreset = "KHONG_TON_TAI"))

        assertEquals(ReaderThemePalette.DARK_BACKGROUND.toInt(), preferences.backgroundColor!!.int)
        assertEquals(ReaderThemePalette.DARK_TEXT.toInt(), preferences.textColor!!.int)
    }

    @Test
    fun `nen toi duoc nhan dien dung de chon do tuong phan cho overlay`() {
        assertTrue(ReaderThemePalette.isDark("DARK"))
        assertTrue(ReaderThemePalette.isDark("AMOLED"))
        assertTrue("Thiếu tên theme thì mặc định phải là nền tối", ReaderThemePalette.isDark(null))
        assertEquals(false, ReaderThemePalette.isDark("LIGHT"))
        assertEquals(false, ReaderThemePalette.isDark("SEPIA"))
    }

    @Test
    fun `mau chu tuong phan voi mau nen o ca bon theme`() {
        listOf("LIGHT", "SEPIA", "DARK", "AMOLED").forEach { preset ->
            val background = ReaderThemePalette.backgroundArgb(preset)
            val text = ReaderThemePalette.textArgb(preset)
            assertNotEquals("Theme $preset có nền và chữ cùng màu", background, text)

            // Chênh lệch tổng ba kênh màu — dưới ngưỡng này thì chữ khó đọc trên màn e-ink.
            val distance = channelDistance(background, text)
            assertTrue(
                "Theme $preset có độ tương phản quá thấp ($distance)",
                distance >= 120
            )
        }
    }

    private fun channelDistance(a: Long, b: Long): Int {
        val dr = kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)).toInt()
        val dg = kotlin.math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)).toInt()
        val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF)).toInt()
        return dr + dg + db
    }
}
