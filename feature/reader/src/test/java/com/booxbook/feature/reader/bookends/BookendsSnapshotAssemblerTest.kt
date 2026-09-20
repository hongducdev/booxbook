package com.booxbook.feature.reader.bookends

import com.booxbook.core.model.ReadingSession
import com.booxbook.core.model.ReadingStatisticsOverview
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Các phép tính gộp của Bookends: chuỗi ngày đọc, thời gian hôm nay, tốc độ đọc và thời gian còn lại.
 *
 * Đều là số học dễ sai (mốc ngày, mẫu số bằng 0, chia cho số ngày chưa đọc) nên phải khoá lại bằng test —
 * overlay hiện một con số sai còn tệ hơn không hiện gì.
 */
class BookendsSnapshotAssemblerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    /** 2026-03-28 21:35 giờ Việt Nam — Thứ Bảy. */
    private val now: Long = Instant.parse("2026-03-28T14:35:00Z").toEpochMilli()

    private val bookId = "book-1"

    private fun context(
        progression: Double = 0.5,
        pageNum: Int = 50,
        pageCount: Int = 200,
        filePath: String = "/data/books/abc_Dune.epub"
    ) = BookendsReadingContext(
        bookId = bookId,
        title = "Dune",
        author = "Frank Herbert",
        format = "EPUB",
        filePath = filePath,
        fileExtension = "epub",
        pageNum = pageNum,
        pageCount = pageCount,
        progression = progression
    )

    private fun session(date: String, seconds: Long, book: String = bookId) = ReadingSession(
        bookId = book,
        startTime = 0L,
        endTime = seconds * 1000L,
        durationSeconds = seconds,
        date = date
    )

    private fun assemble(
        context: BookendsReadingContext = context(),
        session: BookendsSessionProgress = BookendsSessionProgress(),
        sessions: List<ReadingSession> = emptyList(),
        overview: ReadingStatisticsOverview? = null,
        device: BookendsDeviceReading = BookendsDeviceReading()
    ) = BookendsSnapshotAssembler.assemble(
        context = context,
        session = session,
        sessions = sessions,
        overview = overview,
        device = device,
        nowMillis = now,
        zoneId = zone
    )

    @Test
    fun `chi gom phien doc cua cuon sach dang mo`() {
        val snapshot = assemble(
            sessions = listOf(
                session("2026-03-26", 600),
                session("2026-03-27", 900),
                session("2026-03-27", 300, book = "book-2")
            )
        )

        assertEquals(1500L, snapshot.bookReadSeconds)
        assertEquals(2, snapshot.daysReadingBook)
    }

    @Test
    fun `thoi gian doc hom nay gom ca nhung cuon khac`() {
        val snapshot = assemble(
            sessions = listOf(
                session("2026-03-28", 600),
                session("2026-03-28", 300, book = "book-2"),
                session("2026-03-27", 900)
            )
        )

        assertEquals(900L, snapshot.timeTodaySeconds)
    }

    @Test
    fun `chuoi ngay doc tinh nguoc tu hom nay`() {
        val snapshot = assemble(
            sessions = listOf(
                session("2026-03-26", 600),
                session("2026-03-27", 600),
                session("2026-03-28", 600)
            )
        )

        assertEquals(3, snapshot.bookStreakDays)
    }

    @Test
    fun `hom nay chua doc thi chuoi van tinh tiep tu hom qua`() {
        // Chuỗi 2 ngày không được hiện thành 0 chỉ vì người đọc chưa mở sách vào lúc 21:35.
        val snapshot = assemble(
            sessions = listOf(
                session("2026-03-26", 600),
                session("2026-03-27", 600)
            )
        )

        assertEquals(2, snapshot.bookStreakDays)
    }

    @Test
    fun `ngay bi ngat thi chuoi dung lai`() {
        val snapshot = assemble(
            sessions = listOf(
                session("2026-03-24", 600),
                session("2026-03-26", 600),
                session("2026-03-27", 600)
            )
        )

        assertEquals(2, snapshot.bookStreakDays)
    }

    @Test
    fun `khong co phien doc nao thi chuoi bang 0`() {
        assertEquals(0, assemble().bookStreakDays)
    }

    @Test
    fun `toc do va thoi gian con lai suy tu thoi gian doc va vi tri`() {
        // 7200 giây cho 100 trang (vị trí 0.5 của 200 trang) → 72 giây/trang → 50 trang/giờ.
        val snapshot = assemble(sessions = listOf(session("2026-03-27", 7200)))

        assertEquals(100, snapshot.bookPagesRead)
        assertEquals(72.0, snapshot.avgSecondsPerPage!!, 1e-9)
        assertEquals(50.0, snapshot.pagesPerHour!!, 1e-9)
        assertEquals(151, snapshot.pagesLeft)
        assertEquals(151 * 72L, snapshot.bookSecondsLeft)
    }

    @Test
    fun `sach chua co tong so trang thi khong uoc luong duoc gi`() {
        val snapshot = assemble(
            context = context(pageCount = 0, pageNum = 0),
            sessions = listOf(session("2026-03-27", 7200))
        )

        assertEquals(0, snapshot.bookPagesRead)
        assertNull(snapshot.avgSecondsPerPage)
        assertNull(snapshot.bookSecondsLeft)
    }

    @Test
    fun `chua doc phut nao thi khong uoc luong duoc gi`() {
        val snapshot = assemble(context = context())

        assertEquals(0L, snapshot.bookReadSeconds)
        assertNull(snapshot.avgSecondsPerPage)
        assertNull(snapshot.bookSecondsLeft)
    }

    @Test
    fun `mau qua nho thi khong uoc luong toc do`() {
        // Mới đọc 2 trang (vị trí 0.01 của 200 trang) nhưng đã tích 1 giờ: tốc độ suy ra là nhiễu.
        val snapshot = assemble(
            context = context(progression = 0.01),
            sessions = listOf(session("2026-03-27", 3600))
        )

        assertEquals(2, snapshot.bookPagesRead)
        assertNull(snapshot.avgSecondsPerPage)
        assertNull(snapshot.bookSecondsLeft)
    }

    @Test
    fun `toc do phi thuc te thi tu choi uoc luong thay vi kẹp ve nguong`() {
        // Đúng ca đã gặp trên máy thật: 11 trang đọc nhưng bộ theo dõi phiên tích 4,5 giờ, cho 25 phút/trang.
        // Kẹp về 5 phút/trang vẫn ra "47h còn lại" — vẫn vô lý, chỉ khác mức độ.
        val snapshot = assemble(
            context = context(progression = 0.055),
            sessions = listOf(session("2026-03-27", 16200))
        )

        assertEquals(11, snapshot.bookPagesRead)
        assertNull(snapshot.avgSecondsPerPage)
        assertNull(snapshot.pagesPerHour)
        assertNull(snapshot.bookSecondsLeft)
    }

    @Test
    fun `doc nhanh bat thuong van con nam trong khoang chap nhan`() {
        // 100 trang trong 300 giây = 3 giây/trang, nhanh nhưng vẫn là đọc thật.
        val snapshot = assemble(
            context = context(progression = 0.5),
            sessions = listOf(session("2026-03-27", 300))
        )

        assertEquals(3.0, snapshot.avgSecondsPerPage!!, 1e-9)
        assertEquals(1200.0, snapshot.pagesPerHour!!, 1e-9)
    }

    @Test
    fun `thong ke tong hop duoc chuyen vao snapshot`() {
        val snapshot = assemble(
            overview = ReadingStatisticsOverview(
                totalReadingHours = 12.5f,
                currentStreakDays = 9,
                completedBooksCount = 4
            )
        )

        assertEquals(45000L, snapshot.totalReadSeconds)
        assertEquals(9, snapshot.streakDays)
        assertEquals(4, snapshot.booksFinished)
    }

    @Test
    fun `thong ke thiet bi va phien doc duoc chuyen nguyen ven`() {
        val snapshot = assemble(
            session = BookendsSessionProgress(seconds = 1380, pageTurns = 14),
            device = BookendsDeviceReading(
                batteryPercent = 73,
                isCharging = true,
                isWifiOn = false,
                isConnected = false,
                lightPercent = 40
            )
        )

        assertEquals(1380L, snapshot.sessionSeconds)
        assertEquals(14, snapshot.sessionPages)
        assertEquals(73, snapshot.batteryPercent)
        assertTrue(snapshot.isCharging)
        assertEquals(40, snapshot.lightPercent)
    }

    @Test
    fun `ten tep duoc tach khoi duong dan va phan mo rong`() {
        val snapshot = assemble(context = context(filePath = "/data/books/abc_Dune.epub"))

        assertEquals("abc_Dune", snapshot.filename)
        assertEquals("epub", context().fileExtension)
    }

    @Test
    fun `tien do ngoai khoang 0 den 1 duoc keo ve dung khoang`() {
        assertEquals(1.0, assemble(context = context(progression = 1.4)).bookProgression, 1e-9)
        assertEquals(0.0, assemble(context = context(progression = -0.2)).bookProgression, 1e-9)
    }
}
