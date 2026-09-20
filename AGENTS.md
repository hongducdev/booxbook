# BooxBook — quy ước làm việc

Những quy ước dưới đây đều đã trả giá bằng **lỗi thật** trong dự án này. Ghi lại kèm lý do để không lặp lại.

## Commit

- **Commit ngay khi một việc xong.** Không dồn rồi cắt hunk: thay đổi trong dự án này đan xen nhiều file
  (`ReaderViewModel.kt` từng chứa bốn phần khác nhau), nên tách sau là phải cắt hunk và **không kiểm được commit
  trung gian có biên dịch được hay không**. Một commit hỏng tệ hơn một commit gộp.
- Conventional commits: `type(scope): mô tả` — mệnh lệnh, hiện tại, dưới 72 ký tự, không dấu chấm cuối.
- Không ghi công cụ AI trong commit message.
- Tách `docs/` và `plans/` khỏi code: nhóm đó luôn tách sạch được.

## Trước khi nói "xong"

- `./gradlew testDebugUnitTest :app:assembleDebug` phải xanh, và báo **con số** test cụ thể.
- **Thay đổi giao diện màn đọc thì phải kiểm trên máy thật.** Unit test đã bỏ sót: chữ chồng khi đổi lề, canvas
  trắng sau khi dựng lại navigator, vệt lệch màu nền, Bookends trôi theo lề, `%page_count` sai thang đo. Cả năm
  lỗi đó đều lọt qua hơn 160 unit test xanh.
- Đừng tuyên bố đã sửa xong nếu chưa chạy lại đúng lệnh đã dùng để phát hiện lỗi.

## Database

- **Không bump `DATABASE_VERSION` mà không viết `Migration` tay** trong `ALL_MIGRATIONS`. `DatabaseModule` đã bỏ
  `fallbackToDestructiveMigration()`; thêm lại nó là biến mỗi lần tăng version thành một lần **xoá sạch thư viện,
  tiến độ đọc và toàn bộ thống kê** của người dùng.
- `NULL` khác `""` ở các cột metadata: `NULL` = chưa quét, `""` = đã quét và sách không khai báo. Truy vấn chọn
  sách cần quét lại dựa vào sự khác biệt này.
- `MigrationTest` dựng DB version cũ bằng DDL thô rồi mở bằng Room (Room tự kiểm schema sau migration). Sau khi
  sửa migration, **kiểm lại rằng test đó có thể đỏ** — test không thể đỏ là test vô giá trị.

## Readium

- **Kiểm API bằng `javap` trên AAR trong Gradle cache trước khi dùng.** Đã hai lần đoán sai ngữ nghĩa API và phải
  làm lại: `PaginationListener` báo số trang *trong từng tệp chương* chứ không phải toàn sách, và `EpubPreferences`
  chỉ có *một* `pageMargins` cho cả bốn phía.
- Đổi kích thước vùng đọc thì **phải** gọi `submitPreferences`, nếu không pager giữ nguyên bề rộng trang cũ và chữ
  chồng lên nhau.
- **Không dựng lại `EpubNavigatorFragment`** để ép dàn lại — nó để lại canvas trắng.

## Cài đặt

- `ReaderPreferencesManager` là nguồn duy nhất cho cài đặt hiển thị của màn đọc. Đừng ghi thẳng SharedPreferences
  từ ViewModel khác: đó chính là lỗi làm cài đặt ở tab Cài đặt không có tác dụng khi đang đọc.
- **Đổi tên khoá SharedPreferences là đặt lại cài đặt của người dùng về mặc định.** Khi buộc phải đổi tên, phải
  đọc lại khoá cũ làm giá trị dự phòng.
- Đọc enum từ giá trị đã lưu phải rơi về mặc định khi tên không còn tồn tại, không được ném lỗi.

## Ghi chú

- Tài liệu kiến trúc nằm ở `docs/`; kế hoạch và nhật ký ở `plans/` và `docs/journals/`. Cập nhật chúng cùng lúc với
  code, không để sau.
