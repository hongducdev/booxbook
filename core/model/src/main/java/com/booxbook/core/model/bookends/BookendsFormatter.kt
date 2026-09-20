package com.booxbook.core.model.bookends

/**
 * Điểm vào duy nhất của tầng resolve.
 *
 * Một lần gọi [format] đi qua đúng bốn bước, theo thứ tự **bắt buộc**:
 *
 * 1. Quét chuỗi định dạng để nhặt tham số trong ngoặc nhọn (`%datetime{%d %B}`).
 * 2. Dựng bảng token từ [BookendsSnapshot].
 * 3. Mở rộng khối `[if:…]` — phải làm **trước** khi tách token, vì nhánh bị loại không được phép sinh ra
 *    token nào cả.
 * 4. Tách token, định dạng nội dòng, `%bar`, `%spacer` thành [BookendsChunk].
 *
 * Toàn bộ quá trình thuần khiết: cùng một `(line, snapshot, options)` luôn cho cùng kết quả, không đọc
 * Room, không đọc đồng hồ hệ thống. Nhờ vậy unit test chỉ cần dựng snapshot là kiểm chứng được mọi ngóc
 * ngách của cú pháp.
 */
object BookendsFormatter {

    /** Resolve một dòng overlay. */
    fun format(
        line: BookendsLine,
        snapshot: BookendsSnapshot,
        options: BookendsRenderOptions = BookendsRenderOptions()
    ): BookendsRender = format(line.format, line.bar, snapshot, options)

    /**
     * Resolve một chuỗi định dạng trần, không gắn với [BookendsLine] nào.
     *
     * Dùng cho bản xem trước trong màn cấu hình: người dùng đang gõ dở một dòng chưa lưu, vẫn phải thấy
     * kết quả ngay.
     */
    fun format(
        format: String,
        barSpec: BookendsBarSpec = BookendsBarSpec(),
        snapshot: BookendsSnapshot,
        options: BookendsRenderOptions = BookendsRenderOptions()
    ): BookendsRender {
        if (format.isEmpty()) return BookendsRender.Empty

        val arguments = parseArguments(format)
        val tokens = BookendsTokens.resolveAll(snapshot, options) { name -> arguments[name] }
        val expanded = BookendsConditional.expand(format, BookendsTokens.conditionValues(tokens))
        val chunks = BookendsTokenizer.tokenize(expanded, tokens, barSpec)

        return BookendsRender(chunks = chunks, isBlank = !hasVisibleContent(chunks))
    }

    /** Dòng này có được vẽ ở trang [pageNumOneBased] hay không. */
    fun isVisibleOnPage(line: BookendsLine, pageNumOneBased: Int): Boolean =
        line.pageFilter.matches(pageNumOneBased)

    /**
     * Dòng có nội dung thật để hiện hay không.
     *
     * `%spacer` một mình không tính là nội dung — nó chỉ chia chỗ, nên một dòng chỉ có spacer phải biến
     * mất thay vì để lại một khoảng trống đẩy chữ vô hình.
     */
    private fun hasVisibleContent(chunks: List<BookendsChunk>): Boolean = chunks.any { chunk ->
        when (chunk) {
            is BookendsChunk.Text -> chunk.text.isNotEmpty()
            is BookendsChunk.Icon -> true
            is BookendsChunk.ProgressBar -> true
            BookendsChunk.Spacer -> false
        }
    }

    /**
     * Nhặt tham số `{…}` của mọi token trong chuỗi định dạng.
     *
     * Ngoặc nhọn mang hai nghĩa: toàn chữ số là giới hạn bề rộng (`%title{200}`), còn lại là tham số của
     * token (`%datetime{%d %B}`). Ở đây chỉ quan tâm nghĩa thứ hai, vì giới hạn bề rộng do tokenizer xử lý
     * ngay tại chỗ nó gặp token.
     */
    private fun parseArguments(format: String): Map<String, String> {
        val arguments = mutableMapOf<String, String>()
        var index = 0

        while (index < format.length) {
            val percent = format.indexOf('%', index)
            if (percent < 0) break

            var cursor = percent + 1
            if (cursor >= format.length) break

            val bracketed = format[cursor] == '<'
            if (bracketed) cursor++

            val nameStart = cursor
            while (cursor < format.length && isNameChar(format[cursor])) cursor++
            if (cursor == nameStart) {
                index = percent + 1
                continue
            }

            val name = format.substring(nameStart, cursor).lowercase()

            if (cursor < format.length && format[cursor] == '{') {
                val close = format.indexOf('}', cursor + 1)
                if (close > 0) {
                    val content = format.substring(cursor + 1, close)
                    if (content.isNotEmpty() && content.toIntOrNull() == null) {
                        arguments[name] = content
                    }
                    cursor = close + 1
                }
            }

            if (bracketed && cursor < format.length && format[cursor] == '>') cursor++
            index = cursor
        }

        return arguments
    }

    private fun isNameChar(char: Char): Boolean = char.isLetterOrDigit() || char == '_'
}
