package com.booxbook.core.model.bookends

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm chứng đầu-cuối của tầng resolve: token, `%bar`, `%spacer`, định dạng nội dòng, hợp số, giới hạn bề
 * rộng và auto-hide.
 *
 * Mọi giá trị trong snapshot đều cố định (kể cả đồng hồ và múi giờ), nên không assertion nào phụ thuộc
 * thời điểm chạy hay ngôn ngữ của máy.
 */
class BookendsFormatterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val options = BookendsRenderOptions(zone, Locale.forLanguageTag("vi-VN"))

    // 2026-03-28 14:35 UTC = 21:35 giờ Việt Nam, và là ngày Thứ Bảy.
    private val fixedNow: Long = Instant.parse("2026-03-28T14:35:00Z").toEpochMilli()

    private fun snapshot(
        bookProgression: Double = 0.1926,
        annotations: BookendsAnnotationCounts = BookendsAnnotationCounts(3, 1, 5),
        title: String = "The Great Gatsby",
        series: String = ""
    ) = BookendsSnapshot(
        title = title,
        author = "F. Scott Fitzgerald",
        authors = listOf("F. Scott Fitzgerald"),
        series = series,
        // Bộ không có số tập thì nhãn bằng đúng tên bộ.
        seriesLabel = series,
        format = "EPUB",
        pageNum = 42,
        pageCount = 218,
        bookProgression = bookProgression,
        chapters = BookendsChapterIndex(
            listOf(
                BookendsTocNode("Chapter 1", 1, 0.0),
                BookendsTocNode("Chapter 2", 1, 0.5)
            )
        ),
        annotations = annotations,
        sessionSeconds = 1380,
        bookReadSeconds = 7200,
        bookPagesRead = 72,
        pagesToday = 32,
        timeTodaySeconds = 4500,
        streakDays = 7,
        batteryPercent = 73,
        isWifiOn = true,
        nowMillis = fixedNow
    )

    private fun render(format: String, snapshot: BookendsSnapshot = snapshot()): BookendsRender =
        BookendsFormatter.format(format, BookendsBarSpec(), snapshot, options)

    private fun text(render: BookendsRender): String =
        render.chunks.filterIsInstance<BookendsChunk.Text>().joinToString("") { it.text }

    // ── Token cơ bản ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `token trang va tien do cho ra so nguoi doc thay`() {
        assertEquals("42 / 218", text(render("%page_num / %page_count")))
        assertEquals("19%", text(render("%book_pct")))
        assertEquals("81%", text(render("%book_pct_left")))
        assertEquals("177", text(render("%pages_left")))
    }

    @Test
    fun `pages_left khong tinh trang dang doc theo mac dinh`() {
        val inclusive = snapshot().copy(includeCurrentPageInPagesLeft = true)
        assertEquals("176", text(render("%pages_left", inclusive)))
    }

    @Test
    fun `tien do chuong do theo cap sau nhat cua muc luc`() {
        assertEquals("Chapter 1", text(render("%chap_title")))
        assertEquals("1", text(render("%chap_num")))
        assertEquals("2", text(render("%chap_count")))
        assertEquals("39%", text(render("%chap_pct")))
        assertEquals("109", text(render("%chap_pages")))
        assertEquals("42", text(render("%chap_read")))
        assertEquals("67", text(render("%chap_pages_left")))
    }

    @Test
    fun `khong hau to la cap sau nhat con hau to so la cap chi dinh`() {
        // Mục lục hai cấp: "Phần I" (cấp 1) gồm "Chương 1" và "Chương 2" (cấp 2).
        val twoLevels = snapshot().copy(
            chapters = BookendsChapterIndex(
                listOf(
                    BookendsTocNode("Phần I", 1, 0.0),
                    BookendsTocNode("Chương 1", 2, 0.0),
                    BookendsTocNode("Chương 2", 2, 0.5)
                )
            )
        )

        // Không hậu tố phải bám chương nhỏ nhất đang đọc, không phải phần bao ngoài.
        assertEquals("Chương 1", text(render("%chap_title", twoLevels)))
        assertEquals("1", text(render("%chap_num", twoLevels)))
        assertEquals("2", text(render("%chap_count", twoLevels)))

        // `_1` chỉ định tường minh cấp 1, tức là cả phần.
        assertEquals("Phần I", text(render("%chap_title_1", twoLevels)))
        assertEquals("1", text(render("%chap_count_1", twoLevels)))

        // Cấp sâu hơn mục lục thì thu về cấp sâu nhất, không biến thành token không tồn tại.
        assertEquals("Chương 1", text(render("%chap_title_7", twoLevels)))
    }

    @Test
    fun `uoc luong thoi gian chuong theo cung quy uoc cap do voi tien do`() {
        val twoLevels = snapshot().copy(
            chapters = BookendsChapterIndex(
                listOf(
                    BookendsTocNode("Phần I", 1, 0.0),
                    BookendsTocNode("Chương 1", 2, 0.0),
                    BookendsTocNode("Chương 2", 2, 0.5)
                )
            )
        )

        // Cả phần (cấp 1, dài trọn sách ở đây) còn 218 trang × 0.8074 × 100 s ≈ 4h53m.
        assertEquals("4h 53m", text(render("%chap_time_left_1", twoLevels)))
        // Chương nhỏ đang đọc (cấp 2, dài nửa sách) còn 109 × 0.6148 × 100 s ≈ 1h51m.
        assertEquals("1h 51m", text(render("%chap_time_left", twoLevels)))
    }

    @Test
    fun `sach khong co muc luc van uoc luong duoc thoi gian con lai cua ca cuon`() {
        val noToc = snapshot().copy(chapters = BookendsChapterIndex())

        assertEquals("4h 55m", text(render("%book_time_left", noToc)))
        assertTrue(render("%chap_time_left", noToc).isBlank)
    }

    @Test
    fun `token thong ke phien doc`() {
        assertEquals("23m", text(render("%session_time")))
        assertEquals("2h 0m", text(render("%book_read_time")))
        assertTrue(text(render("%time_today")).startsWith("1h"))
        assertEquals("32", text(render("%pages_today")))
        assertEquals("7", text(render("%streak")))
    }

    @Test
    fun `toc do va thoi gian con lai suy tu thong ke`() {
        // 7200 giây cho 72 trang → 100 giây/trang → 36 trang/giờ.
        assertEquals("36", text(render("%speed")))
        assertEquals("1m 40s", text(render("%avg_page_time")))
        assertEquals("4h 55m", text(render("%book_time_left")))

        val chapterLeft = text(render("%chap_time_left"))
        assertTrue("Thiếu ước lượng thời gian chương: '$chapterLeft'", chapterLeft.isNotEmpty())
    }

    @Test
    fun `token thoi gian dung mui gio truyen vao`() {
        assertEquals("21:35", text(render("%time")))
        assertEquals("28/03/2026", text(render("%date_numeric")))
        assertEquals("Sat", text(render("%day")))
    }

    @Test
    fun `datetime nhan tham so trong ngoac nhon`() {
        assertEquals("28 03", text(render("%datetime{%d %m}")))
    }

    @Test
    fun `token thiet bi`() {
        assertEquals("73%", text(render("%batt")))
        val icons = render("%batt_icon").chunks.filterIsInstance<BookendsChunk.Icon>()
        assertEquals(BookendsIcon.BATTERY, icons.single().icon)
    }

    // ── Cú pháp ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dau ngoac nhon chen truc tiep vao van ban`() {
        // `%<name>` cho phép chữ dính liền ngay sau token mà không bị đọc thành phần của tên.
        assertEquals("4h55m", text(render("%<book_time_left_h>h%<book_time_left_m>m")))
    }

    @Test
    fun `hop so theo gia tri cua token so lien truoc`() {
        assertEquals(
            "3 highlights",
            text(render("%highlights highlight(s)", snapshot(annotations = BookendsAnnotationCounts(3, 0, 0))))
        )
        assertEquals(
            "1 highlight",
            text(render("%highlights highlight(s)", snapshot(annotations = BookendsAnnotationCounts(1, 0, 0))))
        )
    }

    @Test
    fun `gioi han be rong tach thanh chunk rieng`() {
        val chunks = render("%chap_title{120} · %page_num").chunks
        val limited = chunks.filterIsInstance<BookendsChunk.Text>().first { it.maxWidthDp != null }
        assertEquals("Chapter 1", limited.text)
        assertEquals(120, limited.maxWidthDp)
        assertEquals("Chapter 1 · 42", text(render("%chap_title{120} · %page_num")))
    }

    @Test
    fun `dinh dang noi dong doi duoc dang chu cua mot doan`() {
        val chunks = render("[b]Page[/b] %page_num").chunks.filterIsInstance<BookendsChunk.Text>()
        assertTrue(chunks.first().bold)
        assertFalse(chunks.last().bold)
        assertEquals("Page 42", text(render("[b]Page[/b] %page_num")))
    }

    @Test
    fun `tag dong lech cap bi giu nguyen van thay vi am tham bo qua`() {
        val rendered = text(render("[i]a[/b]b"))
        assertEquals("a[/b]b", rendered)
    }

    @Test
    fun `token la duoc giu nguyen van de nguoi dung thay loi go`() {
        assertEquals("%book_pctt", text(render("%book_pctt")))
    }

    @Test
    fun `spacer tach thanh chunk rieng de day hai dau`() {
        val chunks = render("%title%spacer%book_pct").chunks
        assertTrue(chunks[1] is BookendsChunk.Spacer)
        assertEquals("The Great Gatsby19%", text(render("%title%spacer%book_pct")))
    }

    @Test
    fun `bar noi dong mang theo kieu cau hinh cua dong`() {
        val chunks = BookendsFormatter.format(
            "%book_pct %bar",
            BookendsBarSpec(type = BookendsBarType.CHAPTER, style = BookendsBarStyle.METRO),
            snapshot(),
            options
        ).chunks

        val bar = chunks.filterIsInstance<BookendsChunk.ProgressBar>().single()
        assertEquals(BookendsBarType.CHAPTER, bar.type)
        assertEquals(BookendsBarStyle.METRO, bar.style)
        assertEquals("19% ", text(BookendsFormatter.format(
            "%book_pct %bar",
            BookendsBarSpec(type = BookendsBarType.CHAPTER, style = BookendsBarStyle.METRO),
            snapshot(),
            options
        )))
    }

    // ── Điều kiện & auto-hide ───────────────────────────────────────────────────────────────────

    @Test
    fun `dieu kien dung so de so sanh nen phan tram 19 khong lot qua nguong 90`() {
        assertTrue(render("[if:book_pct>90]Gần xong[/if]").isBlank)
        assertFalse(render("[if:book_pct<50]Mới bắt đầu[/if]").isBlank)
        assertEquals("Mới bắt đầu", text(render("[if:book_pct<50]Mới bắt đầu[/if]")))
    }

    @Test
    fun `dong khong co du lieu thi tu an thay vi hien so 0`() {
        val empty = BookendsSnapshot(pageNum = 0, pageCount = 0)
        assertTrue(render("%book_time_left", empty).isBlank)
        assertTrue(render("[if:series]%series[/if]").isBlank)
        assertTrue(render("%spacer").isBlank)
        assertTrue(render("%spacer%spacer").isBlank)
    }

    @Test
    fun `series rong lam dieu kien not series dung`() {
        assertEquals("Đứng riêng", text(render("[if:not series]Đứng riêng[/if]")))
        assertEquals("Dune", text(render("[if:not series]Đứng riêng[else]%series[/if]", snapshot(series = "Dune"))))
    }

    @Test
    fun `dong co noi dung thi khong bi an`() {
        assertFalse(render("%title").isBlank)
        assertFalse(render("%bar").isBlank)
        assertFalse(render("%batt_icon").isBlank)
    }

    // ── Lọc theo trang ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `loc trang tinh theo so trang nguoi doc nhin thay`() {
        val oddLine = BookendsLine(format = "%page_num", pageFilter = BookendsPageFilter.ODD)
        val evenLine = BookendsLine(format = "%page_num", pageFilter = BookendsPageFilter.EVEN)

        assertTrue(BookendsFormatter.isVisibleOnPage(oddLine, 41))
        assertFalse(BookendsFormatter.isVisibleOnPage(oddLine, 42))
        assertTrue(BookendsFormatter.isVisibleOnPage(evenLine, 42))
        assertFalse(BookendsFormatter.isVisibleOnPage(evenLine, 41))
        assertTrue(BookendsFormatter.isVisibleOnPage(BookendsLine("%page_num"), 42))
    }

    @Test
    fun `moi token duoc tai lieu hoa deu duoc dang ky nen khong bao gio hien nguyen ten`() {
        // Tokenizer coi tên lạ là văn bản thường (§ để người dùng thấy lỗi gõ). Hệ quả là bất kỳ token nào
        // **quên đăng ký** cũng sẽ in nguyên tên lên trang đọc khi không có dữ liệu — tệ hơn hẳn việc để
        // dòng tự ẩn. Test này chốt lại danh sách token đã hứa.
        val documented = listOf(
            "title", "author", "authors", "authors_short", "author_2", "author_count",
            "series", "series_name", "series_num", "description", "lang", "format", "filename",
            "favourite", "rating", "rating_number", "tags",
            "highlights", "notes", "bookmarks", "annotations",
            "status", "status_label", "added", "opened", "size", "quote", "quote_source",
            "file_num", "file_count",
            "page_num", "page_count", "pages_left", "book_pct", "book_pct_left", "page",
            "book_pct_read",
            "chap_title", "chap_count", "chap_num", "chap_read", "chap_pages", "chap_pages_left",
            "chap_pct", "chap_pct_left", "chap_title_1", "chap_pct_2", "chap_num_9",
            "chap_time_left", "chap_time_left_h", "chap_time_left_m", "chap_time_left_eta",
            "chap_time_left_1", "chap_time_left_2_eta", "chap_time_left_9",
            "book_read_time", "session_time", "total_read_time", "session_pages", "pages_today",
            "time_today", "book_pages_read", "days_reading_book", "streak", "book_streak",
            "books_finished", "pages_per_day", "speed", "avg_page_time", "session",
            "book_time_left", "book_time_left_h", "book_time_left_m", "book_time_left_eta",
            "book_finish_date",
            "time", "time_24h", "time_12h", "date", "date_long", "date_numeric", "weekday",
            "weekday_short", "day", "datetime",
            "batt", "batt_icon", "charging", "wifi", "wifi_icon", "connected", "light",
            "light_pct", "light_icon", "nightmode", "invert"
        )

        // Snapshot rỗng hoàn toàn: mọi token đều không có dữ liệu, tức là ca dễ hở nhất.
        val emptySnapshot = BookendsSnapshot()

        documented.forEach { name ->
            val rendered = text(render("%$name", emptySnapshot))
            assertFalse(
                "Token %$name chưa được đăng ký nên bị in nguyên văn: '$rendered'",
                rendered == "%$name"
            )
        }
    }

    @Test
    fun `truoc muc luc thi khong bia ra chuong dau tien`() {
        val index = BookendsChapterIndex(
            listOf(
                BookendsTocNode("Chương 1", 1, 0.2),
                BookendsTocNode("Chương 2", 1, 0.6)
            )
        )

        // Đang ở phần đầu sách (bìa, trang tên) — trước mục lục. Khẳng định "đang ở Chương 1" là sai.
        assertEquals("", index.titleAt(0.05, 1))
        assertEquals("Chương 1", index.titleAt(0.2, 1))
        assertEquals("Chương 2", index.titleAt(0.9, 1))
    }

    @Test
    fun `tieu de chuong khong hau to uu tien gia tri cua engine`() {
        // Readium tự giải mục lục và cho `Locator.title`; dùng nó để overlay và thanh công cụ không thể nói
        // hai chuyện khác nhau về cùng một vị trí đọc.
        val withEngineTitle = snapshot().copy(
            chapterTitle = "01.",
            chapters = BookendsChapterIndex(listOf(BookendsTocNode("HIỆN TẠI", 1, 0.0)))
        )
        assertEquals("01.", text(render("%chap_title", withEngineTitle)))

        // Engine không báo gì (CBZ, hoặc locator không có title) thì rơi về chỉ mục mục lục.
        val withoutEngineTitle = snapshot().copy(chapterTitle = "")
        assertEquals("Chapter 1", text(render("%chap_title", withoutEngineTitle)))

        // Biến thể theo cấp vẫn do chỉ mục mục lục quyết định — engine không có khái niệm cấp, nên `_1` bỏ qua
        // `Locator.title` và lấy đúng mục cấp 1 mà chỉ mục tìm được.
        assertEquals("HIỆN TẠI", text(render("%chap_title_1", withEngineTitle)))
    }

    @Test
    fun `token metadata sach co du lieu that`() {
        val rich = snapshot().copy(
            series = "Dune",
            seriesLabel = "Dune #1",
            seriesNumber = "1",
            description = "Một hành tinh sa mạc.",
            language = "vi",
            tags = listOf("Khoa học viễn tưởng", "Kinh điển"),
            rating = 4,
            status = com.booxbook.core.model.ReadingStatus.READING,
            addedMillis = fixedNow,
            fileSizeBytes = 500_000L
        )

        assertEquals("Dune #1", text(render("%series", rich)))
        assertEquals("Dune", text(render("%series_name", rich)))
        assertEquals("1", text(render("%series_num", rich)))
        assertEquals("Một hành tinh sa mạc.", text(render("%description", rich)))
        assertEquals("vi", text(render("%lang", rich)))
        assertEquals("Khoa học viễn tưởng, Kinh điển", text(render("%tags", rich)))
        assertEquals("★★★★☆", text(render("%rating", rich)))
        assertEquals("4", text(render("%rating_number", rich)))
        assertEquals("28/03/2026", text(render("%added", rich)))
        assertEquals("488.3 KB", text(render("%size", rich)))
    }

    @Test
    fun `status giu khoa khong dich de dieu kien chay giong nhau moi ngon ngu`() {
        val reading = snapshot().copy(status = com.booxbook.core.model.ReadingStatus.READING)
        val finished = snapshot().copy(status = com.booxbook.core.model.ReadingStatus.FINISHED)

        // `%status` là **toán hạng điều kiện**, nên nó phải ổn định; `%status_label` mới để hiển thị.
        assertEquals("reading", text(render("%status", reading)))
        assertEquals("Đang đọc", text(render("%status_label", reading)))
        assertEquals("Đã đọc xong", text(render("%status_label", finished)))
        assertEquals("Xong rồi", text(render("[if:status=finished]Xong rồi[/if]", finished)))
        assertTrue(render("[if:status=finished]Xong rồi[/if]", reading).isBlank)
    }

    @Test
    fun `series rong thi khong hien dau thang`() {
        val standalone = snapshot().copy(series = "", seriesLabel = "", seriesNumber = "")
        assertTrue(render("%series", standalone).isBlank)

        val noIndex = snapshot().copy(series = "Dune", seriesLabel = "Dune", seriesNumber = "")
        assertEquals("Dune", text(render("%series", noIndex)))
    }

    @Test
    fun `moi token trong danh muc deu that su ton tai`() {
        // Danh mục là thứ người dùng nhìn thấy trong bảng chọn, nên nó không được phép quảng cáo một token
        // không tồn tại — và cũng không được bỏ sót token nào đã cài đặt.
        val emptySnapshot = BookendsSnapshot()

        BookendsTokenCatalogue.all.forEach { info ->
            val rendered = text(render(info.syntax, emptySnapshot))
            assertFalse(
                "Danh mục có token %${info.name} nhưng tokenizer không biết tên này",
                rendered == info.syntax
            )
        }
    }

    @Test
    fun `danh muc khong trung ten va co mo ta cho moi muc`() {
        val names = BookendsTokenCatalogue.all.map { it.name }
        assertEquals(names.size, names.distinct().size)

        BookendsTokenCatalogue.all.forEach { info ->
            assertTrue("Token %${info.name} thiếu mô tả", info.description.isNotBlank())
            assertTrue("Token %${info.name} thiếu ví dụ", info.example.isNotBlank())
        }
    }

    // ── Preset dựng sẵn ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `preset dung san deu resolve ra noi dung o snapshot mac dinh`() {
        for (preset in BookendsDefaults.builtIn()) {
            assertTrue("Preset '${preset.name}' không có nội dung", preset.hasContent)
            for (group in preset.groups) {
                for (line in group.lines) {
                    val rendered = BookendsFormatter.format(line, snapshot(), options)
                    assertFalse(
                        "Dòng '${line.format}' của preset '${preset.name}' không resolve ra gì",
                        rendered.isBlank
                    )
                }
            }
        }
    }

    @Test
    fun `sua dong khong lam mat le rieng cua vung`() {
        // Lề riêng là tầng cấu hình thứ hai; nếu `withLines` tạo lại group từ đầu thì mỗi lần sửa một dòng
        // là lề người dùng vừa đặt biến mất, và họ không có cách nào biết tại sao.
        val preset = BookendsDefaults.minimal()
            .withMargins(BookendsPosition.BOTTOM_CENTER, topDp = 0f, bottomDp = 40f, leftDp = 0f, rightDp = 0f)
            .withLines(BookendsPosition.BOTTOM_CENTER, listOf(BookendsLine("%page_num")))

        val group = preset.groupAt(BookendsPosition.BOTTOM_CENTER)!!
        assertEquals(40f, group.extraMarginBottomDp, 1e-6f)
        assertEquals(1, group.lines.size)
    }

    @Test
    fun `le rieng dat cho vung chua co dong van duoc giu`() {
        // Người dùng có thể chỉnh lề trước khi thêm dòng; đặt lại về 0 vì "chưa có dòng" là mất dữ liệu.
        val preset = BookendsDefaults.minimal()
            .withMargins(BookendsPosition.TOP_RIGHT, 12f, 0f, 0f, 0f)

        assertEquals(12f, preset.groupAt(BookendsPosition.TOP_RIGHT)!!.extraMarginTopDp, 1e-6f)
        assertTrue(preset.groupAt(BookendsPosition.TOP_RIGHT)!!.lines.isEmpty())
    }

    @Test
    fun `nudge theo dong nam trong preset nen duoc luu cung dong`() {
        val preset = BookendsDefaults.minimal().withLines(
            BookendsPosition.BOTTOM_CENTER,
            listOf(BookendsLine(format = "%page_num", nudgeXDp = 4f, nudgeYDp = -6f))
        )

        val line = preset.linesAt(BookendsPosition.BOTTOM_CENTER).single()
        assertEquals(4f, line.nudgeXDp, 1e-6f)
        assertEquals(-6f, line.nudgeYDp, 1e-6f)
        assertEquals(preset, BookendsSettingsCodec.decode(BookendsSettingsCodec.encode(
            BookendsSettings(presets = listOf(preset))
        )).presets.single())
    }

    @Test
    fun `moi kieu thanh tien do deu co nhan hien thi`() {
        // Enum này đi thẳng vào bảng chọn trong trình soạn thảo, nên thiếu nhãn là ô trống trên màn hình.
        BookendsBarStyle.entries.forEach { assertTrue(it.name, it.label.isNotBlank()) }
        BookendsBarType.entries.forEach { assertTrue(it.name, it.label.isNotBlank()) }
        BookendsPageFilter.entries.forEach { assertTrue(it.name, it.label.isNotBlank()) }
        BookendsPosition.entries.forEach { assertTrue(it.name, it.label.isNotBlank()) }
    }

    @Test
    fun `quy tac theo duoi tep chon dung preset hoac an han`() {
        val settings = BookendsSettings(
            enabled = true,
            activePresetId = BookendsDefaults.MINIMAL_PRESET_ID,
            presets = BookendsDefaults.builtIn(),
            autoRules = listOf(
                BookendsAutoRule("cbz", presetId = null),
                BookendsAutoRule(".EPUB", presetId = BookendsDefaults.STANDARD_PRESET_ID)
            )
        )

        // Không khớp quy tắc nào thì dùng preset người dùng đã chọn.
        assertEquals(BookendsDefaults.MINIMAL_PRESET_ID, settings.resolvePreset("azw3")?.id)
        // Đuôi tệp được chuẩn hoá trước khi so, nên ".EPUB" khớp với "epub".
        assertEquals(BookendsDefaults.STANDARD_PRESET_ID, settings.resolvePreset("epub")?.id)
        // Quy tắc trỏ tới `null` nghĩa là ẩn hẳn overlay cho loại tệp đó.
        assertNull(settings.resolvePreset("cbz"))
    }

    @Test
    fun `tat overlay thi khong preset nao duoc chon`() {
        val settings = BookendsSettings(enabled = false, presets = BookendsDefaults.builtIn())
        assertNull(settings.resolvePreset("epub"))
    }
}
