# Database & Storage Layer Architecture

Module: `:core:database`

## Overview
Tầng dữ liệu cục bộ của BooxBook được xây dựng trên nền tảng **Android Room Database** kết hợp cùng **Storage Access Framework (SAF)** và bộ quản lý tệp nội bộ `BookStorageManager`.

## Room Entities

### 1. `BookEntity` (`books`)
| Column | Type | Description |
|---|---|---|
| `id` | `TEXT` (PK) | UUID định danh sách |
| `title` | `TEXT` | Tiêu đề sách |
| `author` | `TEXT` | Tác giả |
| `file_path` | `TEXT` | Đường dẫn tệp sách trong bộ nhớ nội bộ (`files/books/`) |
| `cover_path` | `TEXT?` | Đường dẫn ảnh bìa trích xuất (`files/covers/`) |
| `format` | `TEXT` | Enum `BookFormat` (`EPUB`, `AZW3`, `CBZ`) |
| `total_pages` | `INTEGER` | Tổng số trang |
| `file_size` | `INTEGER` | Kích thước tệp (bytes) |
| `added_timestamp` | `INTEGER` | Thời gian nhập vào thư viện |
| `last_read_timestamp` | `INTEGER` | Thời gian đọc gần nhất (chỉ mục để lọc & sắp xếp) |
| `series` | `TEXT?` | Tên bộ sách, đọc từ OPF |
| `series_index` | `TEXT` | Số tập trong bộ (lưu dạng `TEXT` vì OPF cho phép `1.5`, `II`…) |
| `tags` | `TEXT NOT NULL DEFAULT ''` | Các thẻ, nối bằng ký tự `Unit Separator` (0x1F) |
| `description` | `TEXT?` | Mô tả sách, đã bỏ thẻ HTML |
| `language` | `TEXT?` | Mã ngôn ngữ (`vi`, `en`…) |

#### `NULL` khác `""` — và vì sao phải phân biệt

| Giá trị | Nghĩa |
|---|---|
| `NULL` | **Chưa quét** metadata (bản ghi nhập trước khi có tính năng này) |
| `""` | **Đã quét**, sách không khai báo trường đó |

Gộp hai thứ này thành một thì `BookRepository.backfillMetadata()` không biết bản ghi nào cần quét lại — hoặc là
bỏ sót, hoặc là mở lại từng tệp EPUB ở **mọi** lần mở ứng dụng. Truy vấn chọn lọc vì vậy đòi hỏi cả ba cột đều
`NULL`; sau lần quét đầu, cả ba được ghi thành `""` kể cả khi không đọc được gì.

Thẻ dùng ký tự `Unit Separator` chứ không dùng dấu phẩy: thẻ do nhà xuất bản đặt có thể chứa dấu phẩy
("Fiction, Thriller"), và tách bằng dấu phẩy sẽ biến một thẻ thành hai.

### 2. `ReadingProgressEntity` (`reading_progress`)
| Column | Type | Description |
|---|---|---|
| `book_id` | `TEXT` (PK, FK) | Khóa ngoại trỏ tới `books(id)` với `CASCADE` delete |
| `locator` | `TEXT` | Readium CFI string (EPUB/AZW3) hoặc page index (CBZ) |
| `percentage` | `REAL` | Tỷ lệ % tiến độ đọc (0.0 - 1.0) |
| `current_page` | `INTEGER` | Trang hiện tại |
| `total_pages` | `INTEGER` | Tổng số trang |
| `updated_timestamp` | `INTEGER` | Thời gian cập nhật |

### 3. `AnnotationEntity` (`annotations`)
| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER` (PK auto) | ID tự tăng |
| `book_id` | `TEXT` (FK) | Khóa ngoại trỏ tới `books(id)` với `CASCADE` delete |
| `type` | `TEXT` | Enum `AnnotationType` (`HIGHLIGHT`, `NOTE`, `BOOKMARK`) |
| `locator` | `TEXT` | Vị trí đánh dấu (CFI hoặc page index) |
| `selected_text` | `TEXT?` | Đoạn văn bản được trích dẫn / highlight |
| `note_content` | `TEXT?` | Nội dung ghi chú của người dùng |
| `color_hex` | `TEXT` | Mã màu highlight |
| `created_timestamp` | `INTEGER` | Thời gian tạo |

### 4. `ReadingSessionEntity` (`reading_sessions`)
| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER` (PK auto) | ID tự tăng |
| `book_id` | `TEXT` (FK) | Khóa ngoại trỏ tới `books(id)` với `CASCADE` delete |
| `start_time` / `end_time` | `INTEGER` | Mốc bắt đầu và kết thúc phiên |
| `duration_seconds` | `INTEGER` | Thời gian đọc **thực** của phiên |
| `date` | `TEXT` | Ngày dạng `yyyy-MM-dd`, chỉ mục để gộp theo ngày |

`duration_seconds` chỉ được cộng khi có **tương tác thật** của người đọc. Nhịp ghi định kỳ mỗi 60 giây chỉ ghi
xuống đĩa phần đã cộng dồn, không tự cộng thêm thời gian trôi qua — nếu không thì chỉ cần để màn đọc mở là mỗi
phút sinh ra một phút "đọc". Xem `ReaderViewModel.recordUserInteraction()`.

### 5. `BookReviewEntity` (`book_reviews`)
| Column | Type | Description |
|---|---|---|
| `book_id` | `TEXT` (PK, FK) | Khoá chính luôn là khoá ngoại — mỗi sách chỉ có một đánh giá |
| `rating` | `INTEGER` | 0 = chưa chấm, 1..5 = số sao |
| `review` | `TEXT` | Cảm nhận của người đọc |
| `updated_timestamp` | `INTEGER` | Thời điểm ghi, do tầng lưu trữ đặt |
| `finished_at` | `INTEGER?` | Mốc người đọc xác nhận đã đọc xong; `NULL` khi chưa |

Tách khỏi `books` vì đây là dữ liệu **do người đọc tạo**, không phải metadata của tệp: quét lại OPF không được
phép ghi đè nó. `finished_at` độc lập với `reading_progress.percentage` — tiến độ do màn đọc cập nhật, còn đây
là xác nhận chủ động của người đọc.

## Migration

`DatabaseConstants.DATABASE_VERSION` hiện là **3**. `DatabaseModule` cố ý **không** dùng
`fallbackToDestructiveMigration()`: nó biến mọi lần tăng version schema thành một lần **xoá sạch thư viện, tiến
độ đọc và thống kê** của người dùng. Mỗi lần tăng version phải đi kèm một `Migration` viết tay trong
`ALL_MIGRATIONS`.

`MIGRATION_2_3` thêm năm cột metadata và bảng `book_reviews`. Ba điểm dễ viết sai, ghi lại để lần sau không
lặp lại:

1. **SQLite không cho thêm cột `NOT NULL` mà không có `DEFAULT`** → `tags` phải có `DEFAULT ''`, và entity phải
   khai `@ColumnInfo(defaultValue = "")`. Room so khớp **giá trị mặc định** giữa schema mong đợi và schema thật.
2. **`book_reviews` không được có `DEFAULT`** — entity dùng giá trị mặc định của Kotlin, không phải của SQL.
3. **`book_id` là khoá chính nên đã được đánh chỉ mục** — khai thêm `Index` sẽ khiến Room báo thừa.

## Storage Management (`BookStorageManager`)
- Sao chép an toàn từ SAF `Uri` (`content://` hoặc `file://`) vào thư mục `context.filesDir/books/`.
- Tự động trích xuất metadata và ảnh bìa:
  - **EPUB**: Parse `META-INF/container.xml` và OPF manifest — `dc:title`, `dc:creator`, `dc:description`,
    `dc:subject` (nhiều), `dc:language`, `meta[name="calibre:series"]`, `meta[name="calibre:series_index"]`,
    và dạng EPUB 3 `meta[property="belongs-to-collection"]` + `meta[property="group-position"]`. Mô tả được
    bỏ thẻ HTML và gộp khoảng trắng trước khi lưu.
  - **CBZ**: Trích xuất ảnh đầu tiên trong file Zip làm bìa.
- `reExtractMetadata(book)` đọc lại metadata từ tệp đã nhập mà không đụng tới tệp hay ảnh bìa — dùng cho
  `backfillMetadata()`.
- Tự động dọn dẹp file sách và file bìa khi sách bị xóa (`deleteBookFiles`).

## Repository & Hilt DI
- `BookRepository` cung cấp Kotlin Flow phản ứng thời gian thực cho UI.
- `backfillMetadata()` quét lại OPF cho sách nhập trước khi có tính năng đọc metadata; gọi một lần từ
  `LibraryViewModel.init`.
- Đánh giá và cảm nhận đi qua `getReview`/`saveReview`/`deleteReview`; `saveReview` tự đặt `updatedTimestamp`.
- Hilt `DatabaseModule` cung cấp các phụ thuộc Singleton cho toàn bộ ứng dụng.
