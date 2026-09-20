package com.booxbook.core.model.bookends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cấu hình Bookends phải sống sót qua vòng ghi/đọc, và tệp hỏng phải không làm sập màn đọc.
 */
class BookendsSettingsCodecTest {

    @Test
    fun `ghi roi doc lai giu nguyen cau hinh`() {
        val original = BookendsSettings(
            enabled = true,
            activePresetId = "preset-cua-toi",
            presets = listOf(
                BookendsPreset(
                    id = "preset-cua-toi",
                    name = "Của tôi",
                    fontScale = 1.4f,
                    marginTopDp = 22f,
                    groups = listOf(
                        BookendsGroup(
                            position = BookendsPosition.TOP_CENTER,
                            lines = listOf(
                                BookendsLine(
                                    format = "[b]%chap_title[/b] %bar",
                                    style = BookendsTextStyle.BOLD,
                                    pageFilter = BookendsPageFilter.ODD,
                                    bar = BookendsBarSpec(
                                        type = BookendsBarType.CHAPTER,
                                        style = BookendsBarStyle.METRO
                                    )
                                )
                            )
                        )
                    ),
                    barLayers = listOf(
                        BookendsBarLayer(
                            id = "layer-1",
                            anchor = BookendsBarAnchor.LEFT,
                            fill = BookendsBarFill.BOTTOM_TO_TOP,
                            style = BookendsBarStyle.HOLLOW,
                            thicknessDp = 7f,
                            ticks = BookendsChapterTicks.TOP_TWO_LEVELS
                        )
                    )
                )
            ),
            autoRules = listOf(BookendsAutoRule("cbz", presetId = null))
        )

        assertEquals(original, BookendsSettingsCodec.decode(BookendsSettingsCodec.encode(original)))
    }

    @Test
    fun `chuoi rong hoac null tra ve mac dinh`() {
        assertEquals(BookendsSettings(), BookendsSettingsCodec.decode(null))
        assertEquals(BookendsSettings(), BookendsSettingsCodec.decode(""))
        assertEquals(BookendsSettings(), BookendsSettingsCodec.decode("   "))
    }

    @Test
    fun `json hong khong nem loi ma quay ve mac dinh`() {
        // Ghi dở do bị kill, hoặc người dùng sửa tay sai: đều phải chịu được.
        assertEquals(BookendsSettings(), BookendsSettingsCodec.decode("{ khong phai json"))
        assertEquals(BookendsSettings(), BookendsSettingsCodec.decode("""{"enabled": "khong-phai-bool"}"""))
        assertEquals(BookendsSettings(), BookendsSettingsCodec.decode("[1,2,3]"))
    }

    @Test
    fun `khoa la trong json cu bi bo qua thay vi lam hong ca cau hinh`() {
        val withUnknownKey = """
            {
              "enabled": true,
              "activePresetId": "${BookendsDefaults.MINIMAL_PRESET_ID}",
              "presets": [],
              "truongKhongConTonTai": 42
            }
        """.trimIndent()

        val decoded = BookendsSettingsCodec.decode(withUnknownKey)
        assertTrue(decoded.enabled)
        assertEquals(BookendsDefaults.MINIMAL_PRESET_ID, decoded.activePresetId)
    }

    @Test
    fun `danh sach preset rong duoc bu lai bang preset dung san`() {
        // Không có preset nào thì overlay không thể bật, và người dùng mất luôn đường quay lại.
        val decoded = BookendsSettingsCodec.decode("""{"enabled":true,"presets":[]}""")
        assertFalse(decoded.presets.isEmpty())
        assertEquals(BookendsDefaults.builtIn().map { it.id }, decoded.presets.map { it.id })
    }

    @Test
    fun `preset dung san giu duoc thu tu va id qua vong ghi doc`() {
        val decoded = BookendsSettingsCodec.decode(
            BookendsSettingsCodec.encode(BookendsSettings(presets = BookendsDefaults.builtIn()))
        )
        assertEquals(BookendsDefaults.builtIn(), decoded.presets)
    }
}
