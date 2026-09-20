package com.booxbook.feature.reader.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.engine.model.ReadingFrameColor
import com.booxbook.core.engine.model.ReadingFrameStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cài đặt hiển thị của màn đọc phải **dính** qua lần mở sách sau, và phải đọc được cài đặt mà bản cũ để lại.
 *
 * Bài học từ chính dự án này: `SettingsViewModel` từng ghi `booxbook_reader_prefs` còn `ReaderViewModel`
 * không bao giờ đọc lại, nên đổi vùng chạm ở tab Cài đặt không có tác dụng gì khi đang đọc. Test dựng lại
 * manager từ cùng một `Context` để chứng minh giá trị đã thật sự xuống đĩa, chứ không chỉ nằm trong bộ nhớ.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderPreferencesManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun newManager() = ReaderPreferencesManager(context)

    @Test
    fun `chua co gi tren dia thi dung mac dinh`() {
        val preferences = newManager().preferences.value

        assertEquals(1.0, preferences.fontSize, 1e-9)
        assertEquals("KINDLE", preferences.tapZoneMode)
        assertEquals("SLIDE", preferences.pageTurnEffect)
        assertTrue(preferences.hapticsEnabled)
        assertFalse("Viền khung phải mặc định tắt", preferences.frame.enabled)
        assertFalse(preferences.hasMargins)
    }

    @Test
    fun `le tren duoi rieng va trai phai chung`() {
        val manager = newManager()
        manager.setMargin(ReaderPreferencesManager.MarginSide.TOP, 24f)
        manager.setMargin(ReaderPreferencesManager.MarginSide.BOTTOM, 12f)
        manager.setMargin(ReaderPreferencesManager.MarginSide.HORIZONTAL, 32f)

        val reloaded = newManager().preferences.value
        assertEquals(24f, reloaded.marginTopDp, 1e-6f)
        assertEquals(12f, reloaded.marginBottomDp, 1e-6f)
        assertEquals(32f, reloaded.marginHorizontalDp, 1e-6f)
        assertTrue(reloaded.hasMargins)
    }

    @Test
    fun `le cu bon phia duoc doc lai thanh muc trai phai chung`() {
        // Bản trước lưu `margin_left_dp` và `margin_right_dp` riêng. Đổi sang "trái phải chung" mà không đọc lại
        // thì cài đặt đang có của người dùng âm thầm về 0.
        prefs().edit()
            .putFloat("margin_left_dp", 33f)
            .putFloat("margin_right_dp", 30f)
            .putFloat("margin_top_dp", 50f)
            .commit()

        val preferences = newManager().preferences.value

        assertEquals(50f, preferences.marginTopDp, 1e-6f)
        assertEquals(33f, preferences.marginHorizontalDp, 1e-6f)
    }

    @Test
    fun `le bi kep trong khoang hop le`() {
        val manager = newManager()
        manager.setMargin(ReaderPreferencesManager.MarginSide.TOP, -50f)
        manager.setMargin(ReaderPreferencesManager.MarginSide.BOTTOM, 9999f)

        assertEquals(0f, manager.preferences.value.marginTopDp, 1e-6f)
        assertEquals(
            com.booxbook.core.engine.model.ReaderPreferences.MAX_MARGIN_DP,
            manager.preferences.value.marginBottomDp,
            1e-6f
        )
    }

    @Test
    fun `dat lai le dua ca ba muc ve 0`() {
        val manager = newManager()
        manager.setMargin(ReaderPreferencesManager.MarginSide.TOP, 20f)
        manager.setMargin(ReaderPreferencesManager.MarginSide.HORIZONTAL, 20f)

        manager.resetMargins()

        assertFalse(newManager().preferences.value.hasMargins)
    }

    @Test
    fun `vien khung duoc luu ca sau truong`() {
        val manager = newManager()
        manager.updateFrame {
            it.copy(
                enabled = true,
                thicknessDp = 4f,
                cornerRadiusDp = 18f,
                insetDp = -4f,
                style = ReadingFrameStyle.DOTTED,
                color = ReadingFrameColor.WARM
            )
        }

        val frame = newManager().preferences.value.frame
        assertTrue(frame.enabled)
        assertEquals(4f, frame.thicknessDp, 1e-6f)
        assertEquals(18f, frame.cornerRadiusDp, 1e-6f)
        assertEquals(-4f, frame.insetDp, 1e-6f)
        assertEquals(ReadingFrameStyle.DOTTED, frame.style)
        assertEquals(ReadingFrameColor.WARM, frame.color)
    }

    @Test
    fun `cai dat cu cua nguoi dung khong bi mat khi doi nguon quan ly`() {
        // Bản cũ ghi thẳng ba khoá này bằng `SettingsViewModel`. Đổi sang manager mà đổi luôn tên khoá thì
        // người dùng đang dùng bị âm thầm đặt lại hết về mặc định.
        prefs().edit()
            .putString("tap_zone_mode", "EDGES")
            .putString("page_turn_effect", "FLIP")
            .putBoolean("haptics_enabled", false)
            .commit()

        val preferences = newManager().preferences.value

        assertEquals("EDGES", preferences.tapZoneMode)
        assertEquals("FLIP", preferences.pageTurnEffect)
        assertFalse(preferences.hapticsEnabled)
    }

    @Test
    fun `ten enum la khong con ton tai thi roi ve mac dinh chu khong nem loi`() {
        // Enum đổi tên khi tính năng tiến hoá; đọc thẳng bằng `valueOf` sẽ làm sập màn đọc vì một giá trị
        // cài đặt cũ.
        prefs().edit()
            .putString("frame_style", "KIEU_DA_BI_XOA")
            .putString("frame_color", "MAU_KHONG_CON")
            .commit()

        val frame = newManager().preferences.value.frame

        assertEquals(ReadingFrameStyle.SOLID, frame.style)
        assertEquals(ReadingFrameColor.AUTO, frame.color)
    }

    @Test
    fun `cac truong khac khong bi ghi de khi chi doi mot truong`() {
        val manager = newManager()
        manager.setTapZoneMode("MENU_ONLY")
        manager.setMargin(ReaderPreferencesManager.MarginSide.TOP, 16f)
        manager.updateFrame { it.copy(enabled = true) }

        // Đổi cỡ chữ không được kéo theo việc mất lề hay viền.
        manager.setFontSizeDelta(0.2)

        val preferences = newManager().preferences.value
        assertEquals("MENU_ONLY", preferences.tapZoneMode)
        assertEquals(16f, preferences.marginTopDp, 1e-6f)
        assertTrue(preferences.frame.enabled)
        assertEquals(1.2, preferences.fontSize, 1e-6)
    }
    @Test
    fun `co gian co chu bi kep trong khoang hop le`() {
        val manager = newManager()
        repeat(20) { manager.setFontSizeDelta(0.5) }
        assertEquals(2.5, manager.preferences.value.fontSize, 1e-9)

        repeat(40) { manager.setFontSizeDelta(-0.5) }
        assertEquals(0.7, manager.preferences.value.fontSize, 1e-9)
    }

    @Test
    fun `font family null nghia la dung font mac dinh cua app`() {
        val manager = newManager()
        manager.setFontFamily("serif")
        assertEquals("serif", newManager().preferences.value.fontFamily)

        manager.setFontFamily(null)
        assertNull(newManager().preferences.value.fontFamily)
    }

    private companion object {
        const val PREFS_NAME = "booxbook_reader_prefs"
    }
}
