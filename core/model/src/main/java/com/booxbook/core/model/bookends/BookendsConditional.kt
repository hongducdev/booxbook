package com.booxbook.core.model.bookends

/**
 * Bộ mở rộng khối điều kiện `[if:…]…[else]…[/if]`.
 *
 * Đây là bản cài đặt lại ngữ nghĩa của `bookends.koplugin`: điều kiện được **đánh giá rồi thay bằng
 * đúng nhánh được chọn**, phần bị loại bỏ biến mất hoàn toàn chứ không để lại khoảng trắng. Vì vậy dấu
 * cách phân tách phải nằm *trong* khối — `[if:x]… [/if]%y` chứ không phải `[if:x]…[/if] %y`.
 *
 * Khối không có `[/if]` đóng lại được giữ nguyên như văn bản thường, thay vì bị nuốt: người dùng đang gõ
 * dở sẽ thấy mình gõ dở, chứ không thấy chữ biến mất không rõ lý do.
 */
object BookendsConditional {

    private const val IF_OPEN = "[if:"
    private const val IF_CLOSE = "[/if]"
    private const val ELSE = "[else]"

    /**
     * Mức lồng tối đa.
     *
     * Không phải giới hạn kỹ thuật mà là chốt an toàn: mục lục và điều kiện đều do người dùng nhập, nên
     * một chuỗi lồng vô hạn không được phép làm treo màn đọc.
     */
    private const val MAX_DEPTH = 32

    /** Thay mọi khối `[if:…]` trong [input] bằng nhánh tương ứng với [values]. */
    fun expand(input: String, values: Map<String, BookendsValue>): String {
        if (!input.contains(IF_OPEN)) return input
        return expandInternal(input, values, depth = 0)
    }

    private fun expandInternal(input: String, values: Map<String, BookendsValue>, depth: Int): String {
        if (depth >= MAX_DEPTH) return input

        var result = input
        var searchFrom = 0

        while (true) {
            val openIndex = result.indexOf(IF_OPEN, searchFrom)
            if (openIndex < 0) return result

            val closeIndex = findMatchingClose(result, openIndex)
            if (closeIndex < 0) {
                // Chưa đóng khối: nhảy qua khối này để không lặp vô hạn, và giữ nguyên nó trong kết quả.
                searchFrom = openIndex + IF_OPEN.length
                continue
            }

            val body = result.substring(openIndex + IF_OPEN.length, closeIndex)
            val conditionEnd = body.indexOf(']')
            if (conditionEnd < 0) {
                searchFrom = openIndex + IF_OPEN.length
                continue
            }

            val condition = body.substring(0, conditionEnd)
            val (thenPart, elsePart) = splitElse(body.substring(conditionEnd + 1))
            val branch = if (evaluate(condition, values)) thenPart else elsePart
            val expandedBranch = expandInternal(branch, values, depth + 1)

            result = result.substring(0, openIndex) + expandedBranch + result.substring(closeIndex + IF_CLOSE.length)
            searchFrom = openIndex + expandedBranch.length
        }
    }

    /**
     * Vị trí `[/if]` khớp với `[if:` tại [openIndex], tính cả `[if:` lồng bên trong.
     *
     * Trả `-1` khi khối chưa được đóng.
     */
    private fun findMatchingClose(text: String, openIndex: Int): Int {
        var index = openIndex
        var depth = 0

        while (index < text.length) {
            val nextOpen = text.indexOf(IF_OPEN, index)
            val nextClose = text.indexOf(IF_CLOSE, index)
            if (nextClose < 0) return -1

            if (nextOpen in 0 until nextClose) {
                depth++
                index = nextOpen + IF_OPEN.length
            } else {
                depth--
                if (depth == 0) return nextClose
                index = nextClose + IF_CLOSE.length
            }
        }

        return -1
    }

    /**
     * Tách phần thân khối thành nhánh "đúng" và "sai".
     *
     * `[else]` chỉ được tính khi nó ở cùng cấp với khối đang xét — `[else]` của khối lồng bên trong
     * thuộc về khối đó, không phải khối này.
     */
    private fun splitElse(rest: String): Pair<String, String> {
        var index = 0
        var depth = 0

        while (index < rest.length) {
            val nextOpen = rest.indexOf(IF_OPEN, index)
            val nextClose = rest.indexOf(IF_CLOSE, index)
            val nextElse = rest.indexOf(ELSE, index)

            var best = -1
            var kind = TokenKind.NONE
            var length = 0

            if (nextOpen >= 0) {
                best = nextOpen; kind = TokenKind.OPEN; length = IF_OPEN.length
            }
            if (nextClose >= 0 && (best < 0 || nextClose < best)) {
                best = nextClose; kind = TokenKind.CLOSE; length = IF_CLOSE.length
            }
            if (nextElse >= 0 && (best < 0 || nextElse < best)) {
                best = nextElse; kind = TokenKind.ELSE; length = ELSE.length
            }

            if (best < 0) break

            when (kind) {
                TokenKind.OPEN -> {
                    depth++
                    index = best + length
                }

                TokenKind.CLOSE -> {
                    depth--
                    index = best + length
                }

                TokenKind.ELSE -> {
                    if (depth == 0) return rest.substring(0, best) to rest.substring(best + length)
                    index = best + length
                }

                TokenKind.NONE -> break
            }
        }

        return rest to ""
    }

    /** Đánh giá biểu thức điều kiện. Biểu thức sai cú pháp coi như sai, không ném lỗi ra màn đọc. */
    fun evaluate(expression: String, values: Map<String, BookendsValue>): Boolean {
        if (expression.isBlank()) return false
        val parser = ExpressionParser(expression, values)
        return runCatching { parser.parse().isTruthy }.getOrDefault(false)
    }

    private enum class TokenKind { NONE, OPEN, CLOSE, ELSE }
}

// ────────────────────────────────────────────────────────────────────────────────────────────────
// Biểu thức điều kiện
// ────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Bộ phân tích biểu thức điều kiện bằng phương pháp đệ quy xuống.
 *
 * Thứ tự ưu tiên từ thấp đến cao: `or` → `and` → `not` → so sánh → giá trị. Nhờ vậy
 * `[if:day=Sat or day=Sun]` nhóm đúng, và ngoặc `()` dùng được để ép thứ tự khác.
 */
private class ExpressionParser(
    input: String,
    private val values: Map<String, BookendsValue>
) {
    private val tokens: List<ExpressionToken> = ExpressionLexer(input).tokenize()
    private var position = 0

    fun parse(): BookendsValue {
        val value = parseOr()
        // Còn token thừa nghĩa là biểu thức sai cú pháp (ví dụ `day=Sat Sun`); từ chối thay vì đoán.
        check(position == tokens.size) { "Biểu thức điều kiện còn token thừa: $tokens" }
        return value
    }

    private fun parseOr(): BookendsValue {
        var left = parseAnd()
        while (matchKeyword("or")) {
            val right = parseAnd()
            left = BookendsValue.of(left.isTruthy || right.isTruthy)
        }
        return left
    }

    private fun parseAnd(): BookendsValue {
        var left = parseUnary()
        while (matchKeyword("and")) {
            val right = parseUnary()
            left = BookendsValue.of(left.isTruthy && right.isTruthy)
        }
        return left
    }

    private fun parseUnary(): BookendsValue {
        if (matchKeyword("not")) return BookendsValue.of(!parseUnary().isTruthy)
        return parseComparison()
    }

    private fun parseComparison(): BookendsValue {
        val left = parsePrimary()
        val operator = peekOperator() ?: return left
        position++
        val right = parsePrimary()
        return compare(left, operator, right)
    }

    private fun parsePrimary(): BookendsValue {
        if (match(ExpressionTokenType.LPAREN)) {
            val value = parseOr()
            check(match(ExpressionTokenType.RPAREN)) { "Thiếu dấu ) trong biểu thức điều kiện" }
            return value
        }

        val token = peek()
        check(token != null) { "Biểu thức điều kiện kết thúc sớm" }
        position++

        return when (token.type) {
            ExpressionTokenType.NUMBER -> BookendsValue.Num(token.number ?: 0.0)
            ExpressionTokenType.STRING -> BookendsValue.Str(token.text)
            ExpressionTokenType.IDENT -> resolveIdentifier(token.text)
            else -> error("Token không hợp lệ trong biểu thức điều kiện: ${token.text}")
        }
    }

    /**
     * Biến thành giá trị.
     *
     * `@name` là tham chiếu trạng thái tường minh. Tên trần được tra trong [values] trước, không có thì
     * coi là **chuỗi hằng** — đó là cách `[if:day=Sat]` hay `[if:status=finished]` hoạt động, và cũng là
     * lý do không được ném lỗi khi gặp tên lạ.
     */
    private fun resolveIdentifier(name: String): BookendsValue {
        if (name.startsWith('@')) {
            return values[name.substring(1)] ?: BookendsValue.Empty
        }
        return values[name] ?: BookendsValue.Str(name)
    }

    private fun compare(left: BookendsValue, operator: String, right: BookendsValue): BookendsValue {
        val leftNumber = left.numeric
        val rightNumber = right.numeric

        val result = if (leftNumber != null && rightNumber != null) {
            when (operator) {
                "=" -> leftNumber == rightNumber
                "!=" -> leftNumber != rightNumber
                "<" -> leftNumber < rightNumber
                ">" -> leftNumber > rightNumber
                "<=" -> leftNumber <= rightNumber
                else -> leftNumber >= rightNumber
            }
        } else {
            val a = left.display
            val b = right.display
            when (operator) {
                "=" -> a == b
                "!=" -> a != b
                "<" -> a < b
                ">" -> a > b
                "<=" -> a <= b
                else -> a >= b
            }
        }

        return BookendsValue.of(result)
    }

    private fun matchKeyword(keyword: String): Boolean {
        val token = peek() ?: return false
        if (token.type != ExpressionTokenType.IDENT) return false
        if (token.text.lowercase() != keyword) return false
        position++
        return true
    }

    private fun peekOperator(): String? {
        val token = peek() ?: return null
        return when (token.type) {
            ExpressionTokenType.OPERATOR -> token.text
            // `and`/`or` cũng nằm ở tầng trên, nên ở đây không được nhận chúng như toán tử so sánh.
            else -> null
        }
    }

    private fun peek(): ExpressionToken? = tokens.getOrNull(position)

    private fun match(type: ExpressionTokenType): Boolean {
        val token = peek() ?: return false
        if (token.type != type) return false
        position++
        return true
    }
}

private enum class ExpressionTokenType { IDENT, NUMBER, STRING, OPERATOR, LPAREN, RPAREN }

private data class ExpressionToken(
    val type: ExpressionTokenType,
    val text: String,
    val number: Double? = null
)

private class ExpressionLexer(private val input: String) {

    fun tokenize(): List<ExpressionToken> {
        val tokens = mutableListOf<ExpressionToken>()
        var index = 0

        while (index < input.length) {
            val char = input[index]

            when {
                char.isWhitespace() -> index++

                char == '(' -> {
                    tokens += ExpressionToken(ExpressionTokenType.LPAREN, "(")
                    index++
                }

                char == ')' -> {
                    tokens += ExpressionToken(ExpressionTokenType.RPAREN, ")")
                    index++
                }

                char == '"' || char == '\'' -> {
                    val quote = char
                    val end = input.indexOf(quote, index + 1)
                    val text = if (end < 0) input.substring(index + 1) else input.substring(index + 1, end)
                    tokens += ExpressionToken(ExpressionTokenType.STRING, text)
                    index = if (end < 0) input.length else end + 1
                }

                char == '@' -> {
                    var end = index + 1
                    while (end < input.length && isIdentifierPart(input[end])) end++
                    tokens += ExpressionToken(ExpressionTokenType.IDENT, input.substring(index, end))
                    index = end
                }

                char.isDigit() -> {
                    var end = index
                    while (end < input.length && (input[end].isDigit() || input[end] == '.')) end++
                    val text = input.substring(index, end)
                    tokens += ExpressionToken(ExpressionTokenType.NUMBER, text, text.toDoubleOrNull())
                    index = end
                }

                isOperatorStart(char) -> {
                    // `!=`, `<=`, `>=` phải được đọc liền một mạch, nếu không `!` và `=` sẽ tách rời.
                    val twoChar = input.substring(index, minOf(index + 2, input.length))
                    if (twoChar == "!=" || twoChar == "<=" || twoChar == ">=") {
                        tokens += ExpressionToken(ExpressionTokenType.OPERATOR, twoChar)
                        index += 2
                    } else {
                        tokens += ExpressionToken(ExpressionTokenType.OPERATOR, char.toString())
                        index++
                    }
                }

                isIdentifierPart(char) -> {
                    var end = index
                    while (end < input.length && isIdentifierPart(input[end])) end++
                    tokens += ExpressionToken(ExpressionTokenType.IDENT, input.substring(index, end))
                    index = end
                }

                else -> index++
            }
        }

        return tokens
    }

    private fun isOperatorStart(char: Char): Boolean =
        char == '=' || char == '!' || char == '<' || char == '>'

    private fun isIdentifierPart(char: Char): Boolean =
        char.isLetterOrDigit() || char == '_' || char == '.'
}
