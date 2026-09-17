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

## Storage Management (`BookStorageManager`)
- Sao chép an toàn từ SAF `Uri` (`content://` hoặc `file://`) vào thư mục `context.filesDir/books/`.
- Tự động trích xuất metadata và ảnh bìa:
  - **EPUB**: Parse `META-INF/container.xml` và OPF manifest (`dc:title`, `dc:creator`, cover image).
  - **CBZ**: Trích xuất ảnh đầu tiên trong file Zip làm bìa.
- Tự động dọn dẹp file sách và file bìa khi sách bị xóa (`deleteBookFiles`).

## Repository & Hilt DI
- `BookRepository` cung cấp Kotlin Flow phản ứng thời gian thực cho UI.
- Hilt `DatabaseModule` cung cấp các phụ thuộc Singleton cho toàn bộ ứng dụng.
