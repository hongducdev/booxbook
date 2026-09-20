package com.booxbook.core.model.bookends

import java.time.ZoneId
import java.util.Locale

/**
 * Một token đã resolve.
 *
 * [display] tách khỏi [value] vì có token mang **số để so sánh** nhưng **chuỗi có đơn vị để hiển thị**:
 * `%book_pct` phải là `19` khi đem so `[if:book_pct>90]`, nhưng in ra phải là `19%`. Nếu chỉ có một biểu
 * diễn thì hoặc điều kiện sai, hoặc người đọc thấy `19` trơ trọi.
 */
data class BookendsTokenValue(
    val value: BookendsValue,
    val display: String? = null,
    val icon: BookendsIcon? = null,
    /** Mô tả cho `contentDescription` khi token là icon. */
    val iconDescription: String = ""
) {
    val text: String
        get() = display ?: value.display
}

/** Tuỳ chọn chỉ ảnh hưởng tới phần trình bày, không ảnh hưởng tới dữ liệu. */
data class BookendsRenderOptions(
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault()
)

/**
 * Bảng token.
 *
 * Dựng **toàn bộ** bảng một lần cho mỗi lần render, thay vì tra lười: mỗi token chỉ là vài phép tính trên
 * dữ liệu đã có trong [BookendsSnapshot], nên chi phí không đáng kể, còn bù lại khối `[if:…]` không phải
 * biết trước nó cần những khoá nào.
 *
 * Token không có dữ liệu trả [BookendsValue.Empty] chứ không trả `0`, để dòng tự ẩn theo cơ chế
 * auto-hide thay vì hiện một con số vô nghĩa.
 */
object BookendsTokens {

    fun resolveAll(
        snapshot: BookendsSnapshot,
        options: BookendsRenderOptions = BookendsRenderOptions(),
        /**
         * Tham số trong ngoặc nhọn của token, ví dụ `%datetime{%d %B}` → `datetime` → `%d %B`.
         *
         * Formatter tự quét chuỗi định dạng để dựng bảng này, nên tầng token không phải biết cú pháp
         * ngoặc nhọn — nó chỉ hỏi "token này có tham số gì".
         */
        argumentOf: (String) -> String? = { null }
    ): Map<String, BookendsTokenValue> {
        val tokens = LinkedHashMap<String, BookendsTokenValue>(TOKEN_COUNT)

        putMetadata(tokens, snapshot, options)
        putPosition(tokens, snapshot)
        putChapter(tokens, snapshot)
        putStatistics(tokens, snapshot)
        putEstimates(tokens, snapshot, options, argumentOf)
        putBookEstimates(tokens, snapshot, options, argumentOf)
        putClock(tokens, snapshot, options, argumentOf)
        putDevice(tokens, snapshot)

        return tokens
    }

    /** Tên những cặp `toán hạng` mà `[if:…]` dùng được, tức là toàn bộ token trừ icon. */
    fun conditionValues(tokens: Map<String, BookendsTokenValue>): Map<String, BookendsValue> =
        tokens.mapValues { (_, token) -> token.value }

    // ── Metadata ────────────────────────────────────────────────────────────────────────────────

    private fun putMetadata(
        tokens: MutableMap<String, BookendsTokenValue>,
        snapshot: BookendsSnapshot,
        options: BookendsRenderOptions
    ) {
        tokens["title"] = text(snapshot.title)
        tokens["author"] = text(snapshot.author)
        tokens["authors"] = text(snapshot.authors.joinToString(", "))
        tokens["authors_short"] = text(shortAuthorList(snapshot.authors))
        tokens["author_2"] = text(snapshot.authors.getOrNull(1).orEmpty())
        tokens["author_count"] = number(snapshot.authors.size.toDouble())
        tokens["series"] = text(snapshot.seriesLabel)
        tokens["series_name"] = text(snapshot.series)
        tokens["series_num"] = text(snapshot.seriesNumber)
        tokens["description"] = text(snapshot.description)
        tokens["lang"] = text(snapshot.language)
        tokens["tags"] = text(snapshot.tags.joinToString(", "))
        tokens["format"] = text(snapshot.format)
        tokens["filename"] = text(snapshot.filename)
        tokens["favourite"] = text(if (snapshot.isFavourite) "★" else "")

        // `%status` giữ nguyên khoá không dịch (`unread`/`reading`/`finished`) để câu điều kiện
        // `[if:status=finished]` chạy giống nhau trên mọi ngôn ngữ; `%status_label` mới là bản hiển thị.
        tokens["status"] = text(snapshot.status.key)
        tokens["status_label"] = text(snapshot.status.label)

        tokens["added"] = snapshot.addedMillis
            ?.let { BookendsTokenValue(BookendsValue.Str(BookendsDuration.dateNumeric(it, options.zoneId))) }
            ?: empty()

        tokens["size"] = snapshot.fileSizeBytes
            ?.takeIf { it > 0L }
            ?.let { BookendsTokenValue(BookendsValue.Num(it.toDouble()), humanFileSize(it)) }
            ?: empty()

        val stars = starRating(snapshot.rating)
        tokens["rating"] = BookendsTokenValue(
            value = if (stars.isEmpty()) BookendsValue.Empty else BookendsValue.Str(stars),
            display = stars
        )
        tokens["rating_number"] = if (snapshot.rating > 0) {
            number(snapshot.rating.toDouble())
        } else {
            empty()
        }

        val counts = snapshot.annotations
        tokens["highlights"] = number(counts.highlights.toDouble())
        tokens["notes"] = number(counts.notes.toDouble())
        tokens["bookmarks"] = number(counts.bookmarks.toDouble())
        tokens["annotations"] = number(counts.total.toDouble())

        // `%opened` và `%quote` cần dữ liệu chưa được theo dõi (mốc mở sách gần nhất, trích dẫn ngẫu nhiên).
        // Trả rỗng để dòng tự ẩn thay vì hiện chữ thô.
        tokens["opened"] = empty()
        tokens["quote"] = empty()
        tokens["quote_source"] = empty()
        tokens["file_num"] = empty()
        tokens["file_count"] = empty()
    }

    /** Kích thước tệp dạng người đọc được, ví dụ `488 KB` — khớp `%size` của bản gốc. */
    private fun humanFileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "${oneDecimal(bytes, 1024L * 1024L * 1024L)} GB"
        bytes >= 1024L * 1024L -> "${oneDecimal(bytes, 1024L * 1024L)} MB"
        bytes >= 1024L -> "${oneDecimal(bytes, 1024L)} KB"
        else -> "$bytes B"
    }

    private fun oneDecimal(value: Long, unit: Long): String {
        val scaled = Math.round(value * 10.0 / unit) / 10.0
        return formatNumber(scaled)
    }

    // ── Vị trí đọc ──────────────────────────────────────────────────────────────────────────────

    private fun putPosition(tokens: MutableMap<String, BookendsTokenValue>, snapshot: BookendsSnapshot) {
        tokens["page_num"] = number(snapshot.pageNum.toDouble())
        tokens["page_count"] = number(snapshot.pageCount.toDouble())
        tokens["pages_left"] = number(snapshot.pagesLeft.toDouble())

        val percent = toPercent(snapshot.bookProgression)
        tokens["book_pct"] = percentage(percent)
        tokens["book_pct_left"] = percentage(100 - percent)

        // `%page` là chẵn/lẻ của trang đang đọc, khớp với lọc trang của dòng.
        tokens["page"] = text(if (snapshot.pageNum % 2 == 0) "even" else "odd")

        val statsProgression = snapshot.bookProgressionFromStats
        tokens["book_pct_read"] = if (statsProgression == null) empty() else percentage(toPercent(statsProgression))
    }

    // ── Chương ──────────────────────────────────────────────────────────────────────────────────

    private fun putChapter(tokens: MutableMap<String, BookendsTokenValue>, snapshot: BookendsSnapshot) {
        // Đăng ký trước toàn bộ tên token của họ này ở dạng rỗng. Tokenizer coi tên không có trong bảng là
        // **văn bản thường**, nên một token chưa đăng ký sẽ in nguyên tên `%chap_title` lên trang đọc thay
        // vì để dòng tự ẩn — đúng thứ mà cơ chế auto-hide sinh ra để tránh.
        registerChapterTokens(tokens)

        val chapters = snapshot.chapters
        if (chapters.isEmpty) return

        // Không hậu tố = **cấp sâu nhất** của mục lục, không phải cấp 1. Với sách có mục lục hai cấp, gộp
        // hai thứ đó làm một sẽ khiến `%chap_pct` đo theo chương nhỏ còn `%chap_time_left` đo theo cả phần
        // — hai con số trên cùng một dòng sẽ nói về hai phạm vi khác nhau.
        putChapterLevel(tokens, snapshot, chapters.maxDepth, "")

        // `_1`…`_9` là cấp chỉ định tường minh; cấp sâu hơn mục lục thì thu về cấp sâu nhất.
        for (level in 1..MAX_CHAPTER_LEVEL) {
            putChapterLevel(tokens, snapshot, level, "_$level")
        }
    }

    /** Đặt mọi tên token theo cấp về [BookendsTokenValue] rỗng nếu chưa có giá trị thật. */
    private fun registerChapterTokens(tokens: MutableMap<String, BookendsTokenValue>) {
        for (level in 0..MAX_CHAPTER_LEVEL) {
            val suffix = if (level == 0) "" else "_$level"
            CHAPTER_PROGRESS_TOKENS.forEach { base -> tokens.getOrPut("$base$suffix") { empty() } }
            CHAPTER_TIME_TOKENS.forEach { base -> tokens.getOrPut("$base$suffix") { empty() } }
            tokens.getOrPut("chap_time_left${suffix}_eta") { empty() }
        }
    }

    private fun putChapterLevel(
        tokens: MutableMap<String, BookendsTokenValue>,
        snapshot: BookendsSnapshot,
        level: Int,
        suffix: String
    ) {
        val chapters = snapshot.chapters
        if (chapters.isEmpty) return

        val scopedLevel = level.coerceAtMost(chapters.maxDepth).coerceAtLeast(1)
        val span = chapters.span(snapshot.bookProgression, scopedLevel)

        // Không hậu tố thì ưu tiên tiêu đề do **engine** báo (`Locator.title` của Readium): đó cũng chính là
        // giá trị thanh công cụ đang hiển thị, nên overlay và chrome không thể nói hai chuyện khác nhau.
        // Chỉ số mục lục vẫn là nguồn cho các biến thể `_N` — engine không có khái niệm cấp mục lục.
        val title = if (suffix.isEmpty()) {
            snapshot.chapterTitle.ifBlank { chapters.titleAt(snapshot.bookProgression, scopedLevel) }
        } else {
            chapters.titleAt(snapshot.bookProgression, scopedLevel)
        }

        tokens["chap_title$suffix"] = text(title)
        tokens["chap_count$suffix"] = number(chapters.countAt(scopedLevel).toDouble())

        if (span == null) return

        tokens["chap_num$suffix"] = number(span.index.toDouble())

        // `%chap_pages` là số **trang** trong chương, suy từ độ dài chương theo vị trí đọc — không phải
        // số mục mục lục. Sách không có tổng số trang thì không ước lượng được, và trả rỗng để dòng tự ẩn.
        val spanLength = span.endProgression - span.startProgression
        val chapterPages = Math.round(spanLength * snapshot.pageCount).toInt()
        if (chapterPages > 0) {
            val pagesRead = Math.round(span.progression * chapterPages).toInt().coerceIn(0, chapterPages)
            tokens["chap_pages$suffix"] = number(chapterPages.toDouble())
            tokens["chap_read$suffix"] = number(pagesRead.toDouble())
            tokens["chap_pages_left$suffix"] = number((chapterPages - pagesRead).toDouble())
        } else {
            tokens["chap_pages$suffix"] = empty()
            tokens["chap_read$suffix"] = empty()
            tokens["chap_pages_left$suffix"] = empty()
        }

        val percent = toPercent(span.progression)
        tokens["chap_pct$suffix"] = percentage(percent)
        tokens["chap_pct_left$suffix"] = percentage(100 - percent)
    }

    // ── Thống kê phiên đọc ──────────────────────────────────────────────────────────────────────

    private fun putStatistics(tokens: MutableMap<String, BookendsTokenValue>, snapshot: BookendsSnapshot) {
        tokens["book_read_time"] = duration(snapshot.bookReadSeconds)
        tokens["session_time"] = duration(snapshot.sessionSeconds)
        tokens["total_read_time"] = duration(snapshot.totalReadSeconds)
        tokens["session_pages"] = number(snapshot.sessionPages.toDouble())
        tokens["pages_today"] = number(snapshot.pagesToday.toDouble())
        tokens["time_today"] = duration(snapshot.timeTodaySeconds)
        tokens["book_pages_read"] = number(snapshot.bookPagesRead.toDouble())
        tokens["days_reading_book"] = number(snapshot.daysReadingBook.toDouble())
        tokens["streak"] = number(snapshot.streakDays.toDouble())
        tokens["book_streak"] = number(snapshot.bookStreakDays.toDouble())
        tokens["books_finished"] = number(snapshot.booksFinished.toDouble())

        tokens["pages_per_day"] = if (snapshot.daysReadingBook > 0) {
            number(snapshot.bookPagesRead.toDouble() / snapshot.daysReadingBook)
        } else {
            empty()
        }

        tokens["speed"] = snapshot.pagesPerHour
            ?.let { number(Math.round(it).toDouble()) }
            ?: empty()

        tokens["avg_page_time"] = snapshot.avgSecondsPerPage
            ?.let { BookendsTokenValue(BookendsValue.Num(it), BookendsDuration.precise(it.toLong())) }
            ?: empty()

        // Bí danh `session` của bản gốc, dùng trong điều kiện `[if:session>30]`.
        tokens["session"] = number((snapshot.sessionSeconds / 60L).toDouble())
    }

    // ── Ước lượng thời gian còn lại ─────────────────────────────────────────────────────────────

    private fun putEstimates(
        tokens: MutableMap<String, BookendsTokenValue>,
        snapshot: BookendsSnapshot,
        options: BookendsRenderOptions,
        argumentOf: (String) -> String?
    ) {
        registerChapterTokens(tokens)
        if (snapshot.chapters.isEmpty) return

        // Cùng quy ước với họ token tiến độ chương: không hậu tố là cấp sâu nhất, `_N` là cấp chỉ định.
        putEstimateLevel(tokens, snapshot, options, argumentOf, snapshot.chapters.maxDepth, "")
        for (level in 1..MAX_CHAPTER_LEVEL) {
            putEstimateLevel(tokens, snapshot, options, argumentOf, level, "_$level")
        }
    }

    private fun putEstimateLevel(
        tokens: MutableMap<String, BookendsTokenValue>,
        snapshot: BookendsSnapshot,
        options: BookendsRenderOptions,
        argumentOf: (String) -> String?,
        level: Int,
        suffix: String
    ) {
        val etaName = "chap_time_left${suffix}_eta"
        val seconds = snapshot.chapterSecondsLeft(level)
        if (seconds == null) {
            // Chưa đủ dữ liệu thống kê (hoặc không suy được độ dài chương): trả rỗng để dòng tự ẩn.
            tokens["chap_time_left$suffix"] = empty()
            tokens["chap_time_left_h$suffix"] = empty()
            tokens["chap_time_left_m$suffix"] = empty()
            tokens[etaName] = empty()
            return
        }

        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L

        tokens["chap_time_left$suffix"] = duration(seconds)
        tokens["chap_time_left_h$suffix"] = number(hours.toDouble())
        tokens["chap_time_left_m$suffix"] = number(minutes.toDouble())
        tokens[etaName] = eta(snapshot.nowMillis + seconds * 1000L, etaName, options, argumentOf)
    }

    /** Ước lượng thời gian còn lại của **cả cuốn sách**. */
    private fun putBookEstimates(
        tokens: MutableMap<String, BookendsTokenValue>,
        snapshot: BookendsSnapshot,
        options: BookendsRenderOptions,
        argumentOf: (String) -> String?
    ) {
        // Ước lượng theo chương cần mục lục, nhưng ước lượng **cả sách** thì không — nên hai phần tách
        // hẳn nhau, và sách không có mục lục vẫn có `%book_time_left`.
        val bookSecondsLeft = snapshot.bookSecondsLeft
        if (bookSecondsLeft != null) {
            val hours = bookSecondsLeft / 3600L
            val minutes = (bookSecondsLeft % 3600L) / 60L
            tokens["book_time_left"] = duration(bookSecondsLeft)
            tokens["book_time_left_h"] = number(hours.toDouble())
            tokens["book_time_left_m"] = number(minutes.toDouble())
            tokens["book_time_left_eta"] = eta(
                snapshot.nowMillis + bookSecondsLeft * 1000L,
                "book_time_left_eta",
                options,
                argumentOf
            )
            tokens["book_finish_date"] = BookendsTokenValue(
                value = BookendsValue.Str(
                    BookendsDuration.dateShort(
                        snapshot.nowMillis + bookSecondsLeft * 1000L,
                        options.zoneId,
                        options.locale
                    )
                )
            )
        } else {
            tokens["book_time_left"] = empty()
            tokens["book_time_left_h"] = empty()
            tokens["book_time_left_m"] = empty()
            tokens["book_time_left_eta"] = empty()
            tokens["book_finish_date"] = empty()
        }
    }

    /** Mốc giờ sẽ tới đích. Có tham số `{spec}` thì theo `strftime`, không thì lấy giờ 24h. */
    private fun eta(
        etaMillis: Long,
        tokenName: String,
        options: BookendsRenderOptions,
        argumentOf: (String) -> String?
    ): BookendsTokenValue {
        val spec = argumentOf(tokenName)
        val rendered = if (spec.isNullOrEmpty()) {
            BookendsDuration.clock24(etaMillis, options.zoneId)
        } else {
            BookendsDuration.strftime(spec, etaMillis, options.zoneId, options.locale)
        }
        return BookendsTokenValue(BookendsValue.Str(rendered))
    }

    // ── Đồng hồ ─────────────────────────────────────────────────────────────────────────────────

    private fun putClock(
        tokens: MutableMap<String, BookendsTokenValue>,
        snapshot: BookendsSnapshot,
        options: BookendsRenderOptions,
        argumentOf: (String) -> String?
    ) {
        val zone = options.zoneId
        val locale = options.locale
        val now = snapshot.nowMillis

        val clock24 = BookendsDuration.clock24(now, zone)
        tokens["time"] = text(clock24)
        tokens["time_24h"] = text(clock24)
        tokens["time_12h"] = text(BookendsDuration.clock12(now, zone, locale))
        tokens["date"] = text(BookendsDuration.dateShort(now, zone, locale))
        tokens["date_long"] = text(BookendsDuration.dateLong(now, zone, locale))
        tokens["date_numeric"] = text(BookendsDuration.dateNumeric(now, zone))
        tokens["weekday"] = text(BookendsDuration.weekday(now, zone, locale))
        tokens["weekday_short"] = text(BookendsDuration.weekdayShort(now, zone, locale))

        // `day` cố ý dùng tên viết tắt tiếng Anh bất kể locale: nó là **toán hạng điều kiện**, nên
        // `[if:day=Sat]` phải chạy giống nhau trên mọi máy. `%weekday_short` mới là bản địa hoá.
        tokens["day"] = text(
            ENGLISH_WEEKDAY_SHORT[BookendsDuration.weekdayIndex(now, zone)]
        )

        val spec = argumentOf("datetime")
        tokens["datetime"] = if (spec.isNullOrEmpty()) {
            empty()
        } else {
            text(BookendsDuration.strftime(spec, now, zone, locale))
        }
    }

    // ── Thiết bị ────────────────────────────────────────────────────────────────────────────────

    private fun putDevice(tokens: MutableMap<String, BookendsTokenValue>, snapshot: BookendsSnapshot) {
        tokens["batt"] = BookendsTokenValue(
            value = BookendsValue.Num(snapshot.batteryPercent.toDouble()),
            display = "${snapshot.batteryPercent}%"
        )
        tokens["batt_icon"] = BookendsTokenValue(
            value = BookendsValue.Bool(snapshot.isCharging),
            icon = if (snapshot.isCharging) BookendsIcon.BATTERY_CHARGING else BookendsIcon.BATTERY,
            iconDescription = if (snapshot.isCharging) "Đang sạc" else "Pin ${snapshot.batteryPercent}%"
        )
        tokens["charging"] = BookendsTokenValue(BookendsValue.of(snapshot.isCharging))
        tokens["wifi"] = BookendsTokenValue(
            value = BookendsValue.Bool(snapshot.isWifiOn),
            icon = if (snapshot.isWifiOn) BookendsIcon.WIFI else BookendsIcon.WIFI_OFF,
            iconDescription = if (snapshot.isWifiOn) "Wi-Fi đang bật" else "Wi-Fi đang tắt"
        )
        tokens["wifi_icon"] = tokens.getValue("wifi")
        tokens["connected"] = BookendsTokenValue(BookendsValue.of(snapshot.isConnected))

        val lightPercent = snapshot.lightPercent
        tokens["light"] = when {
            lightPercent == null -> empty()
            lightPercent == 0 -> text("OFF")
            else -> number(lightPercent.toDouble())
        }
        tokens["light_pct"] = if (lightPercent == null) empty() else percentage(lightPercent.toDouble())
        tokens["light_icon"] = if (lightPercent == null) {
            empty()
        } else {
            BookendsTokenValue(
                value = BookendsValue.Bool(lightPercent > 0),
                icon = if (lightPercent > 0) BookendsIcon.LIGHT else BookendsIcon.LIGHT_OFF,
                iconDescription = if (lightPercent > 0) "Đèn nền $lightPercent%" else "Đèn nền tắt"
            )
        }
        tokens["nightmode"] = BookendsTokenValue(
            value = BookendsValue.Bool(true),
            icon = BookendsIcon.NIGHT_MODE,
            iconDescription = "Chế độ tối"
        )
        tokens["invert"] = BookendsTokenValue(
            value = BookendsValue.of(snapshot.isInverted),
            icon = BookendsIcon.INVERT,
            iconDescription = "Đảo hướng lật trang"
        )
    }

    // ── Tiện ích ────────────────────────────────────────────────────────────────────────────────

    private fun text(value: String): BookendsTokenValue = BookendsTokenValue(BookendsValue.Str(value))

    private fun number(value: Double): BookendsTokenValue =
        BookendsTokenValue(BookendsValue.Num(value))

    private fun percentage(value: Double): BookendsTokenValue = BookendsTokenValue(
        value = BookendsValue.Num(value),
        display = "${formatNumber(value)}%"
    )

    private fun duration(seconds: Long): BookendsTokenValue =
        BookendsTokenValue(BookendsValue.Num(seconds / 60.0), BookendsDuration.letters(seconds))

    private fun empty(): BookendsTokenValue = BookendsTokenValue(BookendsValue.Empty)

    private fun toPercent(progression: Double): Double =
        Math.round(progression.coerceIn(0.0, 1.0) * 100.0).toDouble()

    private fun starRating(rating: Int): String {
        if (rating <= 0) return ""
        val filled = rating.coerceAtMost(5)
        return "★".repeat(filled) + "☆".repeat(5 - filled)
    }

    private fun shortAuthorList(authors: List<String>): String = when {
        authors.isEmpty() -> ""
        authors.size <= 2 -> authors.joinToString(", ")
        else -> authors.take(2).joinToString(", ") + ", et al."
    }

    /** Cấp mục lục tối đa mà token hỗ trợ, khớp `%chap_title_1`…`%chap_title_9` của bản gốc. */
    private const val MAX_CHAPTER_LEVEL = 9

    private val ENGLISH_WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** Họ token tiến độ chương, dùng để đăng ký trước ở dạng rỗng. */
    private val CHAPTER_PROGRESS_TOKENS = listOf(
        "chap_title",
        "chap_count",
        "chap_num",
        "chap_read",
        "chap_pages",
        "chap_pages_left",
        "chap_pct",
        "chap_pct_left"
    )

    private val CHAPTER_TIME_TOKENS = listOf(
        "chap_time_left",
        "chap_time_left_h",
        "chap_time_left_m"
    )

    private const val TOKEN_COUNT = 96
}
