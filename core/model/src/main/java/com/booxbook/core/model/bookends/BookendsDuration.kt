package com.booxbook.core.model.bookends

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Định dạng thời lượng, giờ và ngày cho token.
 *
 * Tách khỏi [BookendsFormatter] vì đây là chỗ duy nhất phải chạm tới `java.time` và `Locale`; mọi thứ
 * khác trong tầng resolve chỉ làm việc trên số và chuỗi thuần.
 *
 * Bản gốc để KOReader quyết định kiểu thời lượng (`classic` → `7:36`, `letters` → `7h 36m`). BooxBook
 * chốt kiểu `letters` vì nó đọc rõ nhất trên màn e-ink và không cần thêm một lựa chọn nữa trong cài đặt.
 */
object BookendsDuration {

    /** Thời lượng kiểu `2h 30m`. Trả chuỗi rỗng khi không có gì để hiển thị, để dòng tự ẩn. */
    fun letters(seconds: Long): String {
        if (seconds <= 0L) return ""
        if (seconds < 60L) return "<1m"

        val totalMinutes = seconds / 60L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /** Thời lượng chính xác hơn cho `%avg_page_time`, ví dụ `1m 12s`. */
    fun precise(seconds: Long): String {
        if (seconds <= 0L) return ""
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainder = seconds % 60L

        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> if (remainder > 0L) "${minutes}m ${remainder}s" else "${minutes}m"
            else -> "${remainder}s"
        }
    }

    /** Chỉ số thứ trong tuần, 0 = Thứ Hai … 6 = Chủ Nhật (theo ISO-8601, không theo locale). */
    fun weekdayIndex(timeMillis: Long, zone: ZoneId): Int =
        instant(timeMillis).atZone(zone).dayOfWeek.value - 1

    /** Giờ dạng 24h, `HH:mm`. */
    fun clock24(timeMillis: Long, zone: ZoneId): String =
        instant(timeMillis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))

    /** Giờ dạng 12h kèm AM/PM. */
    fun clock12(timeMillis: Long, zone: ZoneId, locale: Locale): String =
        instant(timeMillis).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", locale))

    /** `28/03/2026` — dạng số, không phụ thuộc tên tháng. */
    fun dateNumeric(timeMillis: Long, zone: ZoneId): String =
        instant(timeMillis).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))

    /** `28 Th3` — ngắn, dùng tên tháng viết tắt theo ngôn ngữ hệ thống. */
    fun dateShort(timeMillis: Long, zone: ZoneId, locale: Locale): String =
        instant(timeMillis).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM", locale))

    /** `28 tháng 3, 2026`. */
    fun dateLong(timeMillis: Long, zone: ZoneId, locale: Locale): String =
        instant(timeMillis).atZone(zone).format(DateTimeFormatter.ofPattern("d MMMM, yyyy", locale))

    fun weekday(timeMillis: Long, zone: ZoneId, locale: Locale): String =
        instant(timeMillis).atZone(zone).dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)

    fun weekdayShort(timeMillis: Long, zone: ZoneId, locale: Locale): String =
        instant(timeMillis).atZone(zone).dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)

    /**
     * Tập con của `strftime` cho token `%datetime{spec}`.
     *
     * Không cài đặt đủ bảng `strftime`: chỉ những chỉ thị có tương ứng một-một với `DateTimeFormatter`
     * mới được dịch. Chỉ thị lạ **giữ nguyên tại chỗ** thay vì bị nuốt, để người dùng thấy ngay là mình
     * gõ sai thay vì nhận một chuỗi rỗng khó hiểu.
     */
    fun strftime(spec: String, timeMillis: Long, zone: ZoneId, locale: Locale): String {
        val zoned = instant(timeMillis).atZone(zone)
        val builder = StringBuilder()
        var index = 0

        while (index < spec.length) {
            val char = spec[index]
            if (char != '%' || index == spec.length - 1) {
                builder.append(char)
                index++
                continue
            }

            val directive = spec[index + 1]
            val pattern = STRFTIME_PATTERNS[directive]
            if (pattern == null) {
                builder.append(char).append(directive)
            } else {
                builder.append(zoned.format(DateTimeFormatter.ofPattern(pattern, locale)))
            }
            index += 2
        }

        return builder.toString()
    }

    private fun instant(timeMillis: Long): Instant = Instant.ofEpochMilli(timeMillis)

    private val STRFTIME_PATTERNS: Map<Char, String> = mapOf(
        'Y' to "yyyy",
        'y' to "yy",
        'm' to "MM",
        'd' to "dd",
        'e' to "d",
        'H' to "HH",
        'I' to "hh",
        'M' to "mm",
        'S' to "ss",
        'p' to "a",
        'A' to "EEEE",
        'a' to "EEE",
        'B' to "MMMM",
        'b' to "MMM",
        'j' to "DDD"
    )
}
