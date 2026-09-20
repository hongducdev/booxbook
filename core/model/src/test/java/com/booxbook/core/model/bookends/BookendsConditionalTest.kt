package com.booxbook.core.model.bookends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm chứng ngữ nghĩa của khối `[if:…]` — phần dễ sai nhất của cú pháp, vì nó vừa là bộ phân tích biểu
 * thức vừa là bộ thay thế chuỗi lồng nhau.
 */
class BookendsConditionalTest {

    private val values = mapOf(
        "batt" to BookendsValue.Num(15.0),
        "charging" to BookendsValue.Bool(false),
        "wifi" to BookendsValue.Str("off"),
        "day" to BookendsValue.Str("Sun"),
        "series" to BookendsValue.Empty,
        "title" to BookendsValue.Str("Dune"),
        "book_pct" to BookendsValue.Num(95.0),
        "chap_title_2" to BookendsValue.Empty,
        "author" to BookendsValue.Str("J.R.R. Tolkien")
    )

    // ── Đánh giá biểu thức ──────────────────────────────────────────────────────────────────────

    @Test
    fun `so sanh so dung thu tu so hoc chu khong phai thu tu chuoi`() {
        // "9" < "20" theo từ điển, nhưng 9 < 20 theo số học — đây là lý do phải giữ kiểu Num.
        assertTrue(BookendsConditional.evaluate("batt<20", values))
        assertFalse(BookendsConditional.evaluate("batt>20", values))
    }

    @Test
    fun `so sanh chuoi khop chinh xac`() {
        assertTrue(BookendsConditional.evaluate("day=Sun", values))
        assertFalse(BookendsConditional.evaluate("day=Sat", values))
        assertTrue(BookendsConditional.evaluate("day!=Sat", values))
    }

    @Test
    fun `chuoi rong la sai nen not series nghia la sach khong thuoc bo`() {
        assertFalse(BookendsConditional.evaluate("series", values))
        assertTrue(BookendsConditional.evaluate("not series", values))
        assertTrue(BookendsConditional.evaluate("title", values))
        assertFalse(BookendsConditional.evaluate("not title", values))
    }

    @Test
    fun `toan tu and or co thu tu uu tien dung`() {
        // Nếu `and` không chặt hơn `or`, biểu thức này sẽ sai: (false and false) or true.
        assertTrue(BookendsConditional.evaluate("charging and not wifi=on or book_pct>90", values))
        assertFalse(BookendsConditional.evaluate("charging and book_pct>90", values))
    }

    @Test
    fun `ngoac ep duoc thu tu khac`() {
        val withCharging = values + ("charging" to BookendsValue.Bool(true))
        // Không ngoặc: `and` chặt hơn `or` → true or (…and…) = true.
        assertTrue(BookendsConditional.evaluate("charging or title=Dune and series", withCharging))
        // Có ngoặc: (true or …) and series → phụ thuộc `series` đang rỗng → sai.
        assertFalse(BookendsConditional.evaluate("(charging or title=Dune) and series", withCharging))
    }

    @Test
    fun `tham chieu trang thai bang a cung tra ve gia tri`() {
        assertTrue(BookendsConditional.evaluate("@title=Dune", values))
        assertTrue(BookendsConditional.evaluate("@title!=@author", values))
    }

    @Test
    fun `gia tri trong ngoac kep giu duoc dau cach`() {
        assertTrue(BookendsConditional.evaluate("author=\"J.R.R. Tolkien\"", values))
        assertFalse(BookendsConditional.evaluate("author=Tolkien", values))
    }

    @Test
    fun `bien the sai cu phap coi nhu sai chu khong nem loi`() {
        assertFalse(BookendsConditional.evaluate("batt << 20", values))
        assertFalse(BookendsConditional.evaluate("day=Sat Sun", values))
        assertFalse(BookendsConditional.evaluate("(batt<20", values))
        assertFalse(BookendsConditional.evaluate("", values))
    }

    // ── Mở rộng khối ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `khoi dung thi tra ve nhanh then`() {
        assertEquals("Pin yếu", BookendsConditional.expand("[if:batt<20]Pin yếu[/if]", values))
        assertEquals("", BookendsConditional.expand("[if:batt>20]Pin yếu[/if]", values))
    }

    @Test
    fun `else duoc chon khi dieu kien sai`() {
        assertEquals(
            "Còn pin",
            BookendsConditional.expand("[if:batt>20]Pin yếu[else]Còn pin[/if]", values)
        )
    }

    @Test
    fun `khoi long nhau danh gia tu ngoai vao`() {
        val input = "[if:batt<20][if:wifi=on]Yếu và có mạng[else]Yếu và offline[/if][/if]"
        assertEquals("Yếu và offline", BookendsConditional.expand(input, values))
    }

    @Test
    fun `nhanh bi loai bien mat hoan toan khoi ket qua`() {
        // Dấu cách nằm trong khối biến mất cùng khối; đó là lý do phải viết `[if:x]… [/if]` với dấu cách
        // bên trong, nếu không sẽ thấy hai dấu cách liền nhau.
        assertEquals("Pin", BookendsConditional.expand("[if:charging]Sạc [/if]Pin", values))
        // Ở đây dấu cách nằm ngoài khối nên còn lại hai dấu cách — đúng như hành vi được mô tả.
        assertEquals("Pin  yếu", BookendsConditional.expand("Pin [if:charging]sạc [/if] yếu", values))
    }

    @Test
    fun `bo mo rong khoi khong dung toi token`() {
        // Thay khối là việc của lớp này; `%batt` do lớp token giải quyết sau đó.
        assertEquals("%batt", BookendsConditional.expand("[if:charging]Sạc [/if]%batt", values))
        assertEquals("[if:batt<20]Pin yếu", BookendsConditional.expand("[if:batt<20]Pin yếu", values))
    }

    @Test
    fun `else cua khoi long khong bi nham voi khoi ngoai`() {
        val input = "[if:charging][if:series]a[else]b[/if][else]c[/if]"
        assertEquals("c", BookendsConditional.expand(input, values))
    }

    @Test
    fun `khoi thieu the dong duoc giu nguyen van`() {
        val input = "[if:batt<20]Pin yếu"
        assertEquals(input, BookendsConditional.expand(input, values))
    }

    @Test
    fun `van ban quanh khoi duoc giu nguyen`() {
        val input = "Trước [if:batt<20]giữa [/if]sau"
        assertEquals("Trước giữa sau", BookendsConditional.expand(input, values))
    }
}
