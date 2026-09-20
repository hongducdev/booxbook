package com.booxbook.feature.reader.bookends

import com.booxbook.core.model.ReadingSession
import com.booxbook.core.model.ReadingStatisticsOverview
import com.booxbook.core.model.ReadingStatus
import com.booxbook.core.model.bookends.BookendsAnnotationCounts
import com.booxbook.core.model.bookends.BookendsChapterIndex
import com.booxbook.core.model.bookends.BookendsSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Những gì đang được đọc, tại thời điểm này.
 *
 * Đây là phần **duy nhất** của snapshot mà `ReaderUiState` cung cấp được; mọi thứ còn lại đến từ lịch sử
 * đọc hoặc từ thiết bị.
 */
data class BookendsReadingContext(
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val format: String = "",
    val filePath: String = "",
    /** Đuôi tệp đã chuẩn hoá, dùng cho quy tắc auto-preset. */
    val fileExtension: String = "",
    val series: String = "",
    /** Tên bộ kèm số tập, lấy thẳng từ [com.booxbook.core.model.Book.seriesLabel] để chỉ có một quy tắc định dạng. */
    val seriesLabel: String = "",
    val seriesIndex: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val language: String = "",
    val rating: Int = 0,
    val addedMillis: Long? = null,
    val fileSizeBytes: Long? = null,
    val pageNum: Int = 0,
    val pageCount: Int = 0,
    val progression: Double = 0.0,
    val chapterTitle: String = "",
    val chapters: BookendsChapterIndex = BookendsChapterIndex(),
    val annotations: BookendsAnnotationCounts = BookendsAnnotationCounts()
) {
    val filename: String
        get() = filePath.substringAfterLast('/').substringBeforeLast('.')
}

/** Tiến trình của **phiên đọc đang diễn ra**, do bộ theo dõi thời gian của màn đọc cung cấp. */
data class BookendsSessionProgress(
    val seconds: Long = 0L,
    val pageTurns: Int = 0
)

/**
 * Trạng thái thiết bị cho các token `%batt`, `%wifi`, `%light`.
 *
 * `lightPercent` là `null` khi thiết bị không báo được độ sáng — token sẽ rỗng và dòng tự ẩn, thay vì
 * hiện "0%".
 */
data class BookendsDeviceReading(
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val isWifiOn: Boolean = false,
    val isConnected: Boolean = true,
    val lightPercent: Int? = null
)

/**
 * Ghép các nguồn dữ liệu rời thành [BookendsSnapshot].
 *
 * Hàm thuần khiết: mọi thứ phụ thuộc thời gian đều đi vào qua [nowMillis] và [zoneId]. Nhờ vậy các phép
 * tính dễ sai — chuỗi ngày liên tiếp, tổng thời gian hôm nay, số trang đã đọc — kiểm chứng được bằng
 * JUnit mà không cần Robolectric, không cần Room, không cần chờ đồng hồ.
 */
object BookendsSnapshotAssembler {

    fun assemble(
        context: BookendsReadingContext,
        session: BookendsSessionProgress = BookendsSessionProgress(),
        sessions: List<ReadingSession> = emptyList(),
        overview: ReadingStatisticsOverview? = null,
        device: BookendsDeviceReading = BookendsDeviceReading(),
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BookendsSnapshot {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val bookSessions = sessions.filter { it.bookId == context.bookId }
        val readingDates = bookSessions.map { it.date }.distinct()

        return BookendsSnapshot(
            title = context.title,
            author = context.author,
            authors = context.author.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
            series = context.series,
            seriesLabel = context.seriesLabel,
            seriesNumber = context.seriesIndex,
            description = context.description,
            language = context.language,
            tags = context.tags,
            format = context.format,
            filename = context.filename,
            rating = context.rating,
            status = ReadingStatus.fromProgression(context.progression.toFloat()),
            addedMillis = context.addedMillis,
            fileSizeBytes = context.fileSizeBytes,

            pageNum = context.pageNum,
            pageCount = context.pageCount,
            bookProgression = context.progression.coerceIn(0.0, 1.0),
            chapterTitle = context.chapterTitle,
            chapters = context.chapters,
            annotations = context.annotations,

            sessionSeconds = session.seconds,
            sessionPages = session.pageTurns,
            bookReadSeconds = bookSessions.sumOf { it.durationSeconds },
            totalReadSeconds = ((overview?.totalReadingHours ?: 0f) * 3600f).toLong(),
            bookPagesRead = pagesReached(context),
            timeTodaySeconds = sessions.filter { it.date == today.toString() }.sumOf { it.durationSeconds },
            streakDays = overview?.currentStreakDays ?: 0,
            bookStreakDays = consecutiveDaysEndingAt(readingDates, today),
            daysReadingBook = readingDates.size,
            booksFinished = overview?.completedBooksCount ?: 0,

            batteryPercent = device.batteryPercent,
            isCharging = device.isCharging,
            isWifiOn = device.isWifiOn,
            isConnected = device.isConnected,
            lightPercent = device.lightPercent,

            nowMillis = nowMillis
        )
    }

    /**
     * Số trang đã đọc của cuốn sách.
     *
     * Ước lượng bằng vị trí đọc hiện tại nhân tổng số trang: ứng dụng chỉ lưu **vị trí**, không lưu số
     * trang đã đi qua trong từng phiên. Hệ quả là nếu người đọc nhảy tới cuối sách ngay từ đầu thì
     * `%speed` sẽ cao bất thường — chấp nhận được cho một chỉ số mang tính tham khảo, và sẽ chính xác hơn
     * khi bộ theo dõi phiên ghi được số trang mỗi phiên.
     */
    private fun pagesReached(context: BookendsReadingContext): Int {
        if (context.pageCount <= 0) return 0
        return Math.round(context.progression.coerceIn(0.0, 1.0) * context.pageCount).toInt()
    }

    /**
     * Số ngày đọc liên tiếp tính ngược từ hôm nay.
     *
     * Nếu hôm nay chưa đọc thì bắt đầu đếm từ hôm qua: chuỗi 7 ngày không nên hiện thành 0 chỉ vì người
     * đọc chưa mở sách vào lúc 8 giờ sáng.
     */
    private fun consecutiveDaysEndingAt(readingDates: List<String>, today: LocalDate): Int {
        if (readingDates.isEmpty()) return 0
        val dates = readingDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        if (dates.isEmpty()) return 0

        var cursor = if (today in dates) today else today.minusDays(1)
        var streak = 0
        while (cursor in dates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
