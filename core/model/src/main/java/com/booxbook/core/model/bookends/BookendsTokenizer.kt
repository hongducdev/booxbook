package com.booxbook.core.model.bookends

/**
 * Bộ phân tích chuỗi định dạng thành các [BookendsChunk] đã sẵn sàng để vẽ.
 *
 * Chuỗi đầu vào đã đi qua [BookendsConditional] nên không còn khối `[if:…]` nào. Việc còn lại là bốn thứ
 * đan xen trong cùng một chuỗi: token `%…`, định dạng nội dòng `[b]/[i]/[u]`, thanh `%bar` và khoảng co
 * giãn `%spacer`.
 *
 * Token lạ được giữ **nguyên văn** kèm dấu `%`, thay vì bị xoá hay biến thành rỗng: người dùng gõ sai
 * tên token sẽ thấy `%book_pctt` nằm trên màn đọc và sửa được ngay, còn nếu nuốt mất thì họ chỉ thấy một
 * khoảng trắng không giải thích được.
 */
object BookendsTokenizer {

    private const val SPACER_TOKEN = "spacer"
    private const val BAR_TOKEN = "bar"

    /** Từ đứng ngay sau một token số có thể mang hậu tố `(s)`/`(es)` để hợp số. */
    private val PLURALISATION = Regex("""(\p{L}+)\((s|es)\)""")

    fun tokenize(
        input: String,
        tokens: Map<String, BookendsTokenValue>,
        barSpec: BookendsBarSpec = BookendsBarSpec()
    ): List<BookendsChunk> {
        val chunks = mutableListOf<BookendsChunk>()
        val buffer = StringBuilder()
        val openTags = mutableListOf<Char>()

        // Giá trị số của token gần nhất, dùng cho hợp số. Xoá sau mỗi lần áp dụng.
        var lastNumeric: Double? = null

        fun flush(maxWidthDp: Int? = null, textOverride: String? = null) {
            val raw = textOverride ?: buffer.toString()
            if (textOverride == null) buffer.clear()
            if (raw.isEmpty()) return

            // Văn bản của token có giới hạn bề rộng là nội dung đã resolve, không phải văn bản người dùng
            // gõ, nên không đi qua bước hợp số.
            val adjusted = if (textOverride != null) raw else applyPluralisation(raw, lastNumeric)
            if (adjusted.isEmpty()) return

            chunks += BookendsChunk.Text(
                text = adjusted,
                bold = openTags.contains('b'),
                italic = openTags.contains('i'),
                uppercase = openTags.contains('u'),
                maxWidthDp = maxWidthDp
            )
        }

        var index = 0
        while (index < input.length) {
            val char = input[index]

            if (char == '%') {
                val parsed = parseToken(input, index)
                if (parsed == null) {
                    buffer.append(char)
                    index++
                    continue
                }

                index = parsed.nextIndex

                when (parsed.name) {
                    BAR_TOKEN -> {
                        flush()
                        lastNumeric = null
                        chunks += BookendsChunk.ProgressBar(
                            type = barSpec.type,
                            style = barSpec.style,
                            maxWidthDp = parsed.widthDp
                        )
                    }

                    SPACER_TOKEN -> {
                        flush()
                        lastNumeric = null
                        chunks += BookendsChunk.Spacer
                    }

                    else -> {
                        val resolved = tokens[parsed.name]
                        if (resolved == null) {
                            buffer.append(parsed.literal)
                        } else if (resolved.icon != null) {
                            flush()
                            lastNumeric = null
                            chunks += BookendsChunk.Icon(resolved.icon, resolved.iconDescription)
                        } else {
                            val text = resolved.text
                            if (parsed.widthDp != null) {
                                flush()
                                lastNumeric = resolved.value.numeric
                                flush(maxWidthDp = parsed.widthDp, textOverride = text)
                            } else {
                                buffer.append(text)
                                lastNumeric = resolved.value.numeric
                            }
                        }
                    }
                }
                continue
            }

            if (char == '[') {
                val tag = matchTag(input, index)
                if (tag != null) {
                    when (tag.kind) {
                        TagKind.OPEN -> {
                            flush()
                            openTags += tag.name
                        }

                        TagKind.CLOSE -> {
                            // Chỉ đóng được tag đang mở trong cùng nhất. Tag đóng lệch cặp bị giữ nguyên
                            // dạng văn bản, để lỗi gõ hiện ra thay vì âm thầm đổi định dạng cả dòng.
                            if (openTags.lastOrNull() == tag.name) {
                                flush()
                                openTags.removeAt(openTags.lastIndex)
                            } else {
                                buffer.append(tag.literal)
                            }
                        }
                    }
                    index = tag.nextIndex
                    continue
                }
            }

            buffer.append(char)
            index++
        }

        flush()
        return chunks
    }

    /**
     * Hợp số cho từ đứng ngay sau một token số: `%highlights highlight(s)` → `1 highlight` hoặc
     * `3 highlights`.
     *
     * Chỉ áp dụng cho từ **liền sau** token: gặp token số thì ghi nhớ, gặp token khác thì xoá. Nhờ vậy
     * `%pages_left page(s) left` hoạt động mà `%title (%page_num pages)` cũng không bị sửa nhầm.
     */
    private fun applyPluralisation(text: String, lastNumeric: Double?): String {
        if (lastNumeric == null) return text
        if (!text.contains("(s)") && !text.contains("(es)")) return text

        val singular = lastNumeric == 1.0
        return PLURALISATION.replace(text) { match ->
            val word = match.groupValues[1]
            if (singular) word else word + match.groupValues[2]
        }
    }

    /**
     * Đọc một token bắt đầu tại [start], trả `null` nếu đây không phải cú pháp token.
     *
     * Bốn dạng được chấp nhận: `%name`, `%name{N}`, `%name{tham số}`, `%<name>` (và `%<name{N}>`). Dạng
     * ngoặc nhọn có hai nghĩa tuỳ nội dung — toàn chữ số là giới hạn bề rộng, còn lại là tham số truyền
     * cho token (`%datetime{%d %B}`) — nên phải đọc nội dung mới phân loại được.
     */
    private fun parseToken(input: String, start: Int): ParsedToken? {
        var index = start + 1
        if (index >= input.length) return null

        val bracketed = input[index] == '<'
        if (bracketed) index++

        val nameStart = index
        while (index < input.length && isNameChar(input[index])) index++
        if (index == nameStart) return null

        val name = input.substring(nameStart, index)

        var widthDp: Int? = null
        var afterBraces = index
        if (index < input.length && input[index] == '{') {
            val close = input.indexOf('}', index + 1)
            if (close > 0) {
                val content = input.substring(index + 1, close)
                widthDp = content.toIntOrNull()
                afterBraces = close + 1
            }
        }

        var end = afterBraces
        if (bracketed) {
            if (end >= input.length || input[end] != '>') return null
            end++
        }

        return ParsedToken(
            name = name.lowercase(),
            widthDp = widthDp,
            literal = input.substring(start, end),
            nextIndex = end
        )
    }

    private fun matchTag(input: String, start: Int): Tag? {
        for (name in TAG_NAMES) {
            val open = "[$name]"
            if (input.startsWith(open, start)) {
                return Tag(TagKind.OPEN, name, open, start + open.length)
            }
            val close = "[/$name]"
            if (input.startsWith(close, start)) {
                return Tag(TagKind.CLOSE, name, close, start + close.length)
            }
        }
        return null
    }

    private fun isNameChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '_'

    private val TAG_NAMES = listOf('b', 'i', 'u')

    private class ParsedToken(
        val name: String,
        val widthDp: Int?,
        val literal: String,
        val nextIndex: Int
    )

    private enum class TagKind { OPEN, CLOSE }

    private class Tag(
        val kind: TagKind,
        val name: Char,
        val literal: String,
        val nextIndex: Int
    )
}
