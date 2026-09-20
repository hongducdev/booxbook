package com.booxbook.core.model.bookends

/**
 * Danh mục token cho bảng chọn trong trình soạn thảo.
 *
 * Đây cũng là **tài liệu duy nhất** về token: mỗi mục có tên, mô tả và ví dụ. Trước đây danh sách này chỉ
 * tồn tại dưới dạng một chuỗi hướng dẫn dài trong màn cấu hình — người dùng phải đọc hết mới biết có gì, và
 * không có cách nào chèn token mà không gõ tay.
 *
 * Thứ tự nhóm đi từ cái dùng nhiều nhất (trang, chương) tới cái ít dùng hơn (thiết bị), vì bảng chọn mở ra
 * là thấy ngay phần đầu.
 */
data class BookendsTokenInfo(
    val name: String,
    val description: String,
    val example: String
) {
    /** Cú pháp chèn vào chuỗi định dạng. */
    val syntax: String
        get() = "%$name"
}

data class BookendsTokenGroup(
    val title: String,
    val tokens: List<BookendsTokenInfo>
)

object BookendsTokenCatalogue {

    val groups: List<BookendsTokenGroup> = listOf(
        BookendsTokenGroup(
            title = "Trang & tiến độ",
            tokens = listOf(
                BookendsTokenInfo("page_num", "Số trang đang đọc", "42"),
                BookendsTokenInfo("page_count", "Tổng số trang", "297"),
                BookendsTokenInfo("pages_left", "Số trang còn lại", "176"),
                BookendsTokenInfo("book_pct", "Phần trăm đã đọc", "19%"),
                BookendsTokenInfo("book_pct_left", "Phần trăm còn lại", "81%"),
                BookendsTokenInfo("book_pct_read", "Phần trăm đọc tính theo thống kê", "44%"),
                BookendsTokenInfo("page", "Trang chẵn hay lẻ", "odd"),
                BookendsTokenInfo("bar", "Thanh tiến độ nội dòng", "━━━━━━░"),
                BookendsTokenInfo("spacer", "Khoảng co giãn, đẩy hai đầu ra xa", "—")
            )
        ),
        BookendsTokenGroup(
            title = "Chương",
            tokens = listOf(
                BookendsTokenInfo("chap_title", "Tiêu đề chương đang đọc", "Chương 3: Thung lũng"),
                BookendsTokenInfo("chap_num", "Số thứ tự chương", "3"),
                BookendsTokenInfo("chap_count", "Tổng số chương", "24"),
                BookendsTokenInfo("chap_pct", "Phần trăm của chương", "65%"),
                BookendsTokenInfo("chap_pct_left", "Phần trăm chương còn lại", "35%"),
                BookendsTokenInfo("chap_pages", "Số trang trong chương", "12"),
                BookendsTokenInfo("chap_read", "Số trang đã đọc trong chương", "7"),
                BookendsTokenInfo("chap_pages_left", "Số trang còn lại của chương", "5"),
                BookendsTokenInfo(
                    "chap_title_1",
                    "Tiêu đề ở cấp 1 của mục lục (đổi 1 thành 2…9 cho cấp sâu hơn)",
                    "Phần II"
                )
            )
        ),
        BookendsTokenGroup(
            title = "Thời gian đọc",
            tokens = listOf(
                BookendsTokenInfo("session_time", "Thời gian của phiên đọc này", "23m"),
                BookendsTokenInfo("session_pages", "Số lần lật trang trong phiên", "14"),
                BookendsTokenInfo("book_read_time", "Tổng thời gian đã đọc cuốn này", "2h 30m"),
                BookendsTokenInfo("total_read_time", "Tổng thời gian đọc mọi sách", "42h 10m"),
                BookendsTokenInfo("book_time_left", "Thời gian còn lại của cuốn sách", "3h 45m"),
                BookendsTokenInfo("book_time_left_eta", "Mốc giờ sẽ đọc xong", "18:20"),
                BookendsTokenInfo("book_finish_date", "Ngày dự kiến đọc xong", "9 Th6"),
                BookendsTokenInfo("chap_time_left", "Thời gian còn lại của chương", "0h 12m"),
                BookendsTokenInfo("speed", "Tốc độ đọc, trang/giờ", "42"),
                BookendsTokenInfo("avg_page_time", "Thời gian trung bình mỗi trang", "1m 12s"),
                BookendsTokenInfo("pages_per_day", "Số trang mỗi ngày đọc cuốn này", "14")
            )
        ),
        BookendsTokenGroup(
            title = "Đọc & thói quen",
            tokens = listOf(
                BookendsTokenInfo("pages_today", "Số trang đọc hôm nay", "32"),
                BookendsTokenInfo("time_today", "Thời gian đọc hôm nay", "1h 15m"),
                BookendsTokenInfo("book_pages_read", "Số trang đã đọc của cuốn này", "87"),
                BookendsTokenInfo("days_reading_book", "Số ngày đã đọc cuốn này", "5"),
                BookendsTokenInfo("book_streak", "Số ngày đọc cuốn này liên tiếp", "3"),
                BookendsTokenInfo("streak", "Số ngày đọc liên tiếp", "7"),
                BookendsTokenInfo("books_finished", "Số sách đã đọc xong", "24")
            )
        ),
        BookendsTokenGroup(
            title = "Metadata sách",
            tokens = listOf(
                BookendsTokenInfo("title", "Tựa sách", "The Great Gatsby"),
                BookendsTokenInfo("author", "Tác giả", "F. Scott Fitzgerald"),
                BookendsTokenInfo("series", "Bộ sách kèm số tập", "Dune #1"),
                BookendsTokenInfo("series_name", "Tên bộ, không có số tập", "Dune"),
                BookendsTokenInfo("series_num", "Số tập trong bộ", "1"),
                BookendsTokenInfo("chap_title", "Tiêu đề chương", "Chương 3"),
                BookendsTokenInfo("tags", "Các thẻ của sách", "Khoa học viễn tưởng"),
                BookendsTokenInfo("lang", "Mã ngôn ngữ", "vi"),
                BookendsTokenInfo("format", "Định dạng tệp", "EPUB"),
                BookendsTokenInfo("filename", "Tên tệp, không có phần mở rộng", "Dune"),
                BookendsTokenInfo("size", "Kích thước tệp", "488 KB"),
                BookendsTokenInfo("added", "Ngày thêm vào thư viện", "28/03/2026"),
                BookendsTokenInfo("rating", "Đánh giá của bạn dạng sao", "★★★★☆"),
                BookendsTokenInfo("rating_number", "Đánh giá dạng số", "4"),
                BookendsTokenInfo("status", "Trạng thái đọc, không dịch", "reading"),
                BookendsTokenInfo("status_label", "Trạng thái đọc, đã dịch", "Đang đọc"),
                BookendsTokenInfo("description", "Mô tả sách", "Một hành tinh sa mạc…"),
                BookendsTokenInfo("favourite", "Ngôi sao nếu sách nằm trong yêu thích", "★")
            )
        ),
        BookendsTokenGroup(
            title = "Chú thích",
            tokens = listOf(
                BookendsTokenInfo("highlights", "Số đoạn bôi đậm", "3"),
                BookendsTokenInfo("notes", "Số ghi chú", "1"),
                BookendsTokenInfo("bookmarks", "Số đánh dấu trang", "5"),
                BookendsTokenInfo("annotations", "Tổng số chú thích", "9")
            )
        ),
        BookendsTokenGroup(
            title = "Đồng hồ",
            tokens = listOf(
                BookendsTokenInfo("time", "Giờ hiện tại, 24h", "14:35"),
                BookendsTokenInfo("time_12h", "Giờ hiện tại, 12h", "2:35 CH"),
                BookendsTokenInfo("date", "Ngày ngắn", "28 Th3"),
                BookendsTokenInfo("date_long", "Ngày dài", "28 tháng 3, 2026"),
                BookendsTokenInfo("date_numeric", "Ngày dạng số", "28/03/2026"),
                BookendsTokenInfo("weekday", "Thứ trong tuần", "Thứ Bảy"),
                BookendsTokenInfo("weekday_short", "Thứ viết tắt", "T7"),
                BookendsTokenInfo("day", "Thứ viết tắt tiếng Anh, dùng cho điều kiện", "Sat")
            )
        ),
        BookendsTokenGroup(
            title = "Thiết bị",
            tokens = listOf(
                BookendsTokenInfo("batt", "Mức pin", "73%"),
                BookendsTokenInfo("batt_icon", "Biểu tượng pin, đổi theo mức sạc", "🔋"),
                BookendsTokenInfo("charging", "Đang sạc hay không", "yes"),
                BookendsTokenInfo("wifi", "Biểu tượng Wi-Fi", "📶"),
                BookendsTokenInfo("connected", "Có kết nối Internet hay không", "yes"),
                BookendsTokenInfo("light", "Độ sáng đèn nền", "18"),
                BookendsTokenInfo("light_pct", "Độ sáng đèn nền theo phần trăm", "56%"),
                BookendsTokenInfo("light_icon", "Biểu tượng đèn nền", "💡")
            )
        )
    )

    val all: List<BookendsTokenInfo> = groups.flatMap { it.tokens }.distinctBy { it.name }

    /**
     * Cú pháp điều kiện và định dạng nội dòng, nhóm riêng vì chúng không phải token đơn lẻ.
     *
     * Đặt ở đây thay vì trong tài liệu để người dùng chèn được từ bảng chọn — cú pháp `[if:…]` khó nhớ nhất
     * và cũng dễ gõ sai nhất.
     */
    val snippets: List<BookendsTokenInfo> = listOf(
        BookendsTokenInfo("[if:ĐIỀU_KIỆN]…[else]…[/if]", "Hiện nội dung theo điều kiện", "[if:batt<20]Pin yếu[/if]"),
        BookendsTokenInfo("[b]…[/b]", "In đậm một đoạn", "[b]Trang[/b] %page_num"),
        BookendsTokenInfo("[i]…[/i]", "In nghiêng một đoạn", "[i]%chap_title[/i]"),
        BookendsTokenInfo("[u]…[/u]", "Viết hoa một đoạn", "[u]chương[/u] %chap_num"),
        BookendsTokenInfo("%token{N}", "Giới hạn bề rộng một token, đơn vị dp", "%chap_title{200}"),
        BookendsTokenInfo("%<token>", "Đặt chữ dính liền ngay sau token", "%<book_time_left_h>h"),
        BookendsTokenInfo("từ(s)", "Tự thêm số nhiều theo token số phía trước", "%highlights highlight(s)")
    )
}
