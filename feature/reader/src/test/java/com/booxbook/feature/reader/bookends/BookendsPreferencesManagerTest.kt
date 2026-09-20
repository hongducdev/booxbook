package com.booxbook.feature.reader.bookends

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.model.bookends.BookendsDefaults
import com.booxbook.core.model.bookends.BookendsGroup
import com.booxbook.core.model.bookends.BookendsLine
import com.booxbook.core.model.bookends.BookendsPosition
import com.booxbook.core.model.bookends.BookendsPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cấu hình Bookends phải là *nguồn sự thật duy nhất*: màn cấu hình ghi vào, màn đọc đọc ra, và cả hai phải
 * thấy cùng một thứ. Test dựng lại manager từ cùng một `Context` để chứng minh giá trị đã thật sự xuống đĩa
 * chứ không chỉ nằm trong bộ nhớ của một đối tượng.
 */
@RunWith(RobolectricTestRunner::class)
class BookendsPreferencesManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Mỗi test bắt đầu từ cấu hình trắng.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun newManager() = BookendsPreferencesManager(context)

    @Test
    fun `chua co gi tren dia thi dung mac dinh`() {
        val settings = newManager().settings.value

        assertFalse(settings.enabled)
        assertEquals(BookendsDefaults.MINIMAL_PRESET_ID, settings.activePresetId)
        assertEquals(BookendsDefaults.builtIn().map { it.id }, settings.presets.map { it.id })
    }

    @Test
    fun `bat overlay duoc ghi xuong dia`() {
        newManager().setEnabled(true)

        assertTrue(newManager().settings.value.enabled)
    }

    @Test
    fun `preset moi duoc luu va dat lam dang dung`() {
        val manager = newManager()
        val custom = BookendsPreset(
            id = "cua-toi",
            name = "Của tôi",
            groups = listOf(
                BookendsGroup(
                    position = BookendsPosition.TOP_RIGHT,
                    lines = listOf(BookendsLine(format = "%book_pct"))
                )
            )
        )

        manager.upsertPreset(custom, makeActive = true)

        val reloaded = newManager().settings.value
        assertEquals("cua-toi", reloaded.activePresetId)
        assertEquals(1, reloaded.presetById("cua-toi")!!.linesAt(BookendsPosition.TOP_RIGHT).size)
    }

    @Test
    fun `sua preset giu nguyen vi tri trong danh sach`() {
        val manager = newManager()
        val standard = BookendsDefaults.standard()
        manager.upsertPreset(standard.copy(fontScale = 1.5f))

        val reloaded = newManager().settings.value
        assertEquals(1, reloaded.presets.indexOfFirst { it.id == BookendsDefaults.STANDARD_PRESET_ID })
        assertEquals(1.5f, reloaded.presetById(BookendsDefaults.STANDARD_PRESET_ID)!!.fontScale, 1e-6f)
    }

    @Test
    fun `preset dung san khong xoa duoc va khong the de lai danh sach rong`() {
        val manager = newManager()

        manager.deletePreset(BookendsDefaults.MINIMAL_PRESET_ID)

        val settings = newManager().settings.value
        assertEquals(BookendsDefaults.builtIn().map { it.id }, settings.presets.map { it.id })
        assertNotNull(settings.presetById(BookendsDefaults.MINIMAL_PRESET_ID))
    }

    @Test
    fun `xoa preset dang dung thi chuyen sang preset khac chu khong de treo`() {
        val manager = newManager()
        manager.upsertPreset(
            BookendsPreset(id = "tam", name = "Tạm", groups = emptyList()),
            makeActive = true
        )

        manager.deletePreset("tam")

        val settings = newManager().settings.value
        assertNull(settings.presetById("tam"))
        assertNotNull(settings.presetById(settings.activePresetId))
    }

    @Test
    fun `khoi phuc goc tra preset dung san ve nguyen ban`() {
        val manager = newManager()
        manager.upsertPreset(BookendsDefaults.minimal().copy(name = "Đã đổi tên", fontScale = 2f))

        manager.resetPreset(BookendsDefaults.MINIMAL_PRESET_ID)

        val restored = newManager().settings.value.presetById(BookendsDefaults.MINIMAL_PRESET_ID)!!
        assertEquals(BookendsDefaults.minimal(), restored)
    }

    @Test
    fun `quy tac theo duoi tep duoc chuan hoa va ghi de lan nhau`() {
        val manager = newManager()
        manager.setEnabled(true)

        manager.setAutoRule(".CBZ", presetId = null)
        manager.setAutoRule("epub", presetId = BookendsDefaults.STANDARD_PRESET_ID)
        // Cùng một đuôi viết khác đi phải thay thế quy tắc cũ, không tạo thêm dòng trùng.
        manager.setAutoRule("EPUB", presetId = BookendsDefaults.MINIMAL_PRESET_ID)

        val settings = newManager().settings.value
        assertEquals(2, settings.autoRules.size)
        assertEquals(BookendsDefaults.MINIMAL_PRESET_ID, settings.resolvePreset("epub")!!.id)
        // Quy tắc trỏ `null` nghĩa là ẩn hẳn overlay cho đuôi đó.
        assertNull(settings.resolvePreset("cbz"))
    }

    @Test
    fun `duoi tep rong bi tu choi`() {
        val manager = newManager()
        manager.setAutoRule("   ", presetId = BookendsDefaults.MINIMAL_PRESET_ID)

        assertTrue(newManager().settings.value.autoRules.isEmpty())
    }

    @Test
    fun `go quy tac tra ve trang thai khong co quy tac`() {
        val manager = newManager()
        manager.setEnabled(true)
        manager.setAutoRule("cbz", presetId = null)
        manager.removeAutoRule(".CBZ")

        val settings = newManager().settings.value
        assertTrue(settings.autoRules.isEmpty())
        assertEquals(BookendsDefaults.MINIMAL_PRESET_ID, settings.resolvePreset("cbz")!!.id)
    }

    @Test
    fun `cau hinh hong duoi dia khong lam sap man doc`() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("bookends_settings", "{ hong")
            .commit()

        val settings = newManager().settings.value
        assertEquals(BookendsDefaults.MINIMAL_PRESET_ID, settings.activePresetId)
        assertFalse(settings.enabled)
    }

    private companion object {
        const val PREFS_NAME = "booxbook_bookends_prefs"
    }
}
