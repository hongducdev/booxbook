package com.booxbook.core.model.bookends

/**
 * Giá trị của một token, đủ để vừa hiển thị vừa đem đi so sánh trong `[if:…]`.
 *
 * Giữ ba kiểu thay vì quy hết về chuỗi: `[if:batt<20]` phải so sánh **số**, còn `[if:series]` chỉ cần
 * biết có rỗng hay không. Nếu mọi thứ là chuỗi thì `"9" < "20"` sẽ sai theo thứ tự từ điển.
 */
sealed interface BookendsValue {

    /** Dùng trong `[if:…]`: rỗng và 0 là sai, còn lại là đúng. */
    val isTruthy: Boolean

    /** Chuỗi để hiển thị. Số được bỏ phần thập phân thừa, ví dụ `42` chứ không phải `42.0`. */
    val display: String

    /** Giá trị quy về số khi so sánh. `null` nghĩa là vế này không so sánh số được. */
    val numeric: Double?

    data class Num(val value: Double) : BookendsValue {
        override val isTruthy: Boolean get() = value != 0.0
        override val display: String get() = formatNumber(value)
        override val numeric: Double get() = value
    }

    data class Str(val value: String) : BookendsValue {
        override val isTruthy: Boolean get() = value.isNotEmpty()
        override val display: String get() = value
        override val numeric: Double? get() = value.trim().toDoubleOrNull()
    }

    data class Bool(val value: Boolean) : BookendsValue {
        override val isTruthy: Boolean get() = value
        override val display: String get() = if (value) "yes" else "no"
        override val numeric: Double? get() = if (value) 1.0 else 0.0
    }

    companion object {
        val Empty: BookendsValue = Str("")
        val True: BookendsValue = Bool(true)
        val False: BookendsValue = Bool(false)

        fun of(value: Double): BookendsValue = Num(value)
        fun of(value: Int): BookendsValue = Num(value.toDouble())
        fun of(value: Long): BookendsValue = Num(value.toDouble())
        fun of(value: Boolean): BookendsValue = Bool(value)
        fun of(value: String?): BookendsValue = Str(value.orEmpty())
    }
}

/**
 * In số không kèm `.0`.
 *
 * `String.format` theo locale sẽ cho `42,5` ở Tiếng Việt, nhưng token đi vào cả câu so sánh lẫn câu
 * hiển thị, nên phần thập phân luôn dùng dấu chấm để `[if:book_pct>90]` không phụ thuộc ngôn ngữ máy.
 */
internal fun formatNumber(value: Double): String {
    if (value.isNaN()) return ""
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
