package com.booxbook.feature.reader.bookends

import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.bookends.BookendsChapterIndex
import com.booxbook.core.model.bookends.BookendsTocNode

/**
 * Dựng chỉ mục chương từ mục lục của engine.
 *
 * `TocItem` chỉ có `title`/`href`, không kèm vị trí, nên phải **suy ra** vị trí bắt đầu của mỗi mục. Cách
 * suy được chọn sao cho cùng hệ quy chiếu với tiến độ đọc mà Readium báo (`locations.progression`, tính
 * theo thứ tự đọc): ánh xạ `href` của mục sang chỉ số trong `readingOrder` rồi chia cho tổng số mục.
 *
 * Nếu chỉ đánh số mục lục 1..n thì `%chap_pct` sẽ so một thang đo khác với `%book_pct`, và hai con số trên
 * cùng một dòng sẽ mâu thuẫn nhau — đó là lý do không dùng cách đơn giản hơn.
 */
object BookendsChapterIndexFactory {

    /** Từ chối dựng chỉ mục khi mục lục quá lớn: CBZ 800 trang không phải là "chương". */
    private const val MAX_NODES = 2000

    fun build(
        tableOfContents: List<TocItem>,
        readingOrderHrefs: List<String> = emptyList(),
        cbzPageCount: Int = 0
    ): BookendsChapterIndex {
        if (tableOfContents.isEmpty()) return BookendsChapterIndex()

        val flattened = flatten(tableOfContents)
        if (flattened.isEmpty() || flattened.size > MAX_NODES) return BookendsChapterIndex()

        val resolver = ProgressionResolver(flattened.map { it.href }, readingOrderHrefs, cbzPageCount)

        // Sắp theo vị trí để tra "chương đang đọc" bằng tìm kiếm nhị phân-tuyến tính đúng nghĩa tài liệu.
        val nodes = flattened
            .map { BookendsTocNode(title = it.title, depth = it.depth, progression = resolver.resolve(it.href)) }
            .sortedWith(compareBy({ it.progression }, { it.depth }))

        return BookendsChapterIndex(nodes)
    }

    private fun flatten(items: List<TocItem>, depth: Int = 1): List<FlatTocEntry> =
        items.flatMap { item ->
            listOf(FlatTocEntry(item.title, item.href, depth)) + flatten(item.children, depth + 1)
        }

    private data class FlatTocEntry(val title: String, val href: String, val depth: Int)

    /**
     * Quy `href` thành vị trí 0..1 trong sách.
     *
     * Ba chế độ, theo thứ tự ưu tiên: trang CBZ (`page://N`), thứ tự đọc của Readium, và cuối cùng là vị trí
     * của mục trong chính mục lục — chế độ chót chỉ để `%chap_num`/`%chap_title` còn dùng được khi không
     * có cách nào tốt hơn.
     */
    private class ProgressionResolver(
        private val tocHrefs: List<String>,
        private val readingOrderHrefs: List<String>,
        private val cbzPageCount: Int
    ) {
        private val readingOrderIndex: Map<String, Int> = buildMap {
            readingOrderHrefs.forEachIndexed { index, href ->
                putIfAbsent(normalize(href), index)
            }
        }

        private val tocIndex: Map<String, Int> = buildMap {
            tocHrefs.forEachIndexed { index, href -> putIfAbsent(normalize(href), index) }
        }

        fun resolve(href: String): Double {
            extractPageIndex(href)?.let { pageIndex ->
                if (cbzPageCount > 0) return (pageIndex.toDouble() / cbzPageCount).coerceIn(0.0, 1.0)
            }

            readingOrderIndex[normalize(href)]?.let { index ->
                if (readingOrderHrefs.isNotEmpty()) {
                    return (index.toDouble() / readingOrderHrefs.size).coerceIn(0.0, 1.0)
                }
            }

            val fallbackIndex = tocIndex[normalize(href)] ?: 0
            return if (tocHrefs.isEmpty()) 0.0 else (fallbackIndex.toDouble() / tocHrefs.size).coerceIn(0.0, 1.0)
        }

        /** Bỏ fragment: mục lục hay trỏ `chapter1.xhtml#phan-2` còn `readingOrder` chỉ có `chapter1.xhtml`. */
        private fun normalize(href: String): String = href.substringBefore('#').trim()

        private fun extractPageIndex(href: String): Int? =
            href.substringAfter("page://", "").substringBefore('#').toIntOrNull()
    }
}
