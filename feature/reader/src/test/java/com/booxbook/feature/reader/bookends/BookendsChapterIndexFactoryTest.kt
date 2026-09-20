package com.booxbook.feature.reader.bookends

import com.booxbook.core.engine.model.TocItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `TocItem` không mang vị trí, nên vị trí chương phải **suy ra**. Ba chế độ suy (thứ tự đọc của Readium,
 * trang CBZ, và vị trí trong chính mục lục) được kiểm ở đây vì sai ở đây sẽ làm `%chap_pct` lệch hẳn khỏi
 * `%book_pct` trên cùng một dòng.
 */
class BookendsChapterIndexFactoryTest {

    private val readingOrder = listOf(
        "/OEBPS/title.xhtml",
        "/OEBPS/chapter1.xhtml",
        "/OEBPS/chapter2.xhtml",
        "/OEBPS/chapter3.xhtml"
    )

    @Test
    fun `vi tri chuong lay theo thu tu doc cua readium`() {
        val toc = listOf(
            TocItem("Chương 1", "/OEBPS/chapter1.xhtml"),
            TocItem("Chương 2", "/OEBPS/chapter2.xhtml"),
            TocItem("Chương 3", "/OEBPS/chapter3.xhtml")
        )

        val index = BookendsChapterIndexFactory.build(toc, readingOrder)

        // readingOrder có 4 mục, nên ba chương nằm ở 0.25 · 0.5 · 0.75.
        assertEquals(3, index.countAt(1))
        assertEquals(0.25, index.span(0.3, 1)!!.startProgression, 1e-9)
        assertEquals(1, index.span(0.3, 1)!!.index)
        assertEquals("Chương 1", index.titleAt(0.3, 1))
        assertEquals(2, index.span(0.6, 1)!!.index)
        assertEquals("Chương 2", index.titleAt(0.6, 1))
    }

    @Test
    fun `muc luc long nhau duoc lam phang kem cap do`() {
        val toc = listOf(
            TocItem(
                title = "Phần I",
                href = "/OEBPS/chapter1.xhtml",
                children = listOf(
                    TocItem("Chương 1", "/OEBPS/chapter2.xhtml"),
                    TocItem("Chương 2", "/OEBPS/chapter3.xhtml")
                )
            )
        )

        val index = BookendsChapterIndexFactory.build(toc, readingOrder)

        assertEquals(2, index.maxDepth)
        // Cấp 1 chỉ có "Phần I"; cấp 2 mới là hai chương thật.
        assertEquals(1, index.countAt(1))
        assertEquals(2, index.countAt(2))
        assertEquals("Chương 1", index.titleAt(0.5, 2))
        assertEquals("Phần I", index.titleAt(0.5, 1))
        // Đếm theo cấp nên chương 1 của phần I là chương số 1, không phải số 2 (đã tính cả tiêu đề phần).
        assertEquals(1, index.span(0.5, 2)!!.index)
        assertEquals(1, index.span(0.5, 1)!!.index)
    }

    @Test
    fun `fragment trong href khong lam mat anh xa`() {
        val toc = listOf(
            TocItem("Chương 1", "/OEBPS/chapter1.xhtml#phan-2"),
            TocItem("Chương 2", "/OEBPS/chapter2.xhtml")
        )

        val index = BookendsChapterIndexFactory.build(toc, readingOrder)

        // Không khớp được thì mọi mục sẽ dồn về vị trí 0 và `%chap_num` luôn bằng 1.
        assertTrue(index.span(0.3, 1)!!.startProgression > 0.0)
    }

    @Test
    fun `trang CBZ duoc quy theo so trang chu khong theo thu tu doc`() {
        val toc = (0 until 10).map { TocItem("Trang ${it + 1}", "page://$it") }

        val index = BookendsChapterIndexFactory.build(
            tableOfContents = toc,
            readingOrderHrefs = emptyList(),
            cbzPageCount = 10
        )

        assertEquals(10, index.countAt(1))
        assertEquals(0.5, index.span(0.55, 1)!!.startProgression, 1e-9)
        assertEquals("Trang 6", index.titleAt(0.55, 1))
    }

    @Test
    fun `khong co muc luc thi tra ve chi muc rong`() {
        assertTrue(BookendsChapterIndexFactory.build(emptyList(), readingOrder).isEmpty)
        assertNull(BookendsChapterIndexFactory.build(emptyList(), readingOrder).span(0.5, 1))
    }

    @Test
    fun `thieu thu tu doc thi van danh so duoc chuong`() {
        // AZW3 chuyển đổi có thể chưa có readingOrder khớp href; vẫn phải còn `%chap_num`/`%chap_title`.
        val toc = listOf(
            TocItem("Một", "/a.xhtml"),
            TocItem("Hai", "/b.xhtml"),
            TocItem("Ba", "/c.xhtml")
        )

        val index = BookendsChapterIndexFactory.build(toc, readingOrderHrefs = emptyList())

        assertEquals(3, index.countAt(1))
        assertEquals("Ba", index.titleAt(1.0, 1))
    }

    @Test
    fun `muc luc qua lon bi tu choi de khong keo sap hieu nang`() {
        val hugeToc = (0 until 2500).map { TocItem("Chương $it", "/x$it.xhtml") }

        assertTrue(BookendsChapterIndexFactory.build(hugeToc, readingOrder).isEmpty)
    }
}
