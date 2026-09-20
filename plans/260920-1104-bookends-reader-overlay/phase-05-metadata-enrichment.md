# Phase 05 — Metadata enrichment (series, tags, mô tả, đánh giá)

**Module:** `:core:model`, `:core:database`, `:core:engine` · **Phụ thuộc:** Phase 01 · **Trạng thái:** ⏳

## Vấn đề

`Book` hiện chỉ có `title`, `author`, `coverPath`, `format`, `totalPages`, `fileSize`. Thiếu
`series`, `seriesIndex`, `tags`, `description`, `language`, `wordCount` — nên các token `%series`,
`%description`, `%lang` không có dữ liệu để hiển thị.

`BookStorageManager.parseOpfMetadata()` cũng chỉ đọc `dc:title`, `dc:creator` và ảnh bìa; nó bỏ qua
`meta[name="calibre:series"]`, `meta[name="calibre:series_index"]`, `dc:description`, `dc:subject`,
`dc:language`.

## ⚠️ Ràng buộc bắt buộc

`DatabaseModule` đang dùng `.fallbackToDestructiveMigration()` với `DATABASE_VERSION = 2`. Thêm cột
mà chỉ bump version sẽ **xoá toàn bộ thư viện, tiến độ đọc và thống kê của người dùng**.

→ Phase này **phải** viết `Migration(2, 3)` thật (ALTER TABLE cho từng cột mới) và bỏ
`fallbackToDestructiveMigration()`; hoặc chấp nhận giữ destructive nhưng chỉ khi chưa có bản phát
hành công khai. Quyết định này phải chốt trước khi viết dòng code đầu tiên.

## Kế hoạch

1. Thêm cột vào `BookEntity` + `Book` domain: `series`, `series_index`, `tags` (CSV), `description`,
   `language`. `Migration(2, 3)` với `ALTER TABLE books ADD COLUMN …`.
2. Mở rộng `parseOpfMetadata()` đọc `dc:description`, `dc:subject` (nhiều), `dc:language`,
   `meta[name="calibre:series"]`, `meta[name="calibre:series_index"]`, `meta[name="calibre:series_index"]`.
3. Re-scan sách cũ: tác vụ nền một lần, chạy lại `parseOpfMetadata` cho các bản ghi còn trống, có
   tiến trình và có thể chạy lại an toàn.
4. Nối token `%series`, `%series_name`, `%series_num`, `%description`, `%lang`, `%tags`.
5. Bảng đánh giá + review: thêm bảng Room `book_reviews` (`book_id` PK, `rating` 1..5, `review`,
   `finished_at`), `Migration` tương ứng; token `%rating`, `%rating_number`; UI nhập ở màn chi tiết
   sách (đã có `BookDetailScreen`).
6. Token `%highlights`, `%notes`, `%bookmarks`, `%annotations` — chỉ cần đếm `AnnotationEntity` theo
   `type`, có thể làm sớm hơn vì không đụng schema.

## Ghi chú

Mục 6 có thể tách ra làm trước Phase 5 để có thêm token hữu ích mà không cần migration.

---

## Kết quả — Phase 5 đã xong

### Đã làm

| Việc | Ở đâu |
|---|---|
| 5 cột metadata trên `Book`/`BookEntity` | `core:model/Book.kt`, `core:database/entity/BookEntity.kt` |
| `MIGRATION_2_3` viết tay | `core:database/Migrations.kt` |
| Bảng `book_reviews` + DAO + repository | `BookReviewEntity.kt`, `BookReviewDao.kt`, `BookRepositoryImpl` |
| Parse OPF mở rộng (Calibre + EPUB 3) | `BookStorageManager.parseOpfMetadata` |
| Quét lại sách cũ | `BookRepository.backfillMetadata()` ← `LibraryViewModel.init` |
| Token mới | `%series`, `%series_name`, `%series_num`, `%tags`, `%lang`, `%description`, `%rating`, `%rating_number`, `%status`, `%status_label`, `%added`, `%size` |
| UI nhật ký đọc | `BookDetailScreen.ReadingJournalSection` |

### Ba quyết định đáng ghi lại

1. **Bỏ `fallbackToDestructiveMigration()`.** Nó biến mọi lần tăng version schema thành một lần xoá sạch thư
   viện, tiến độ đọc và thống kê của người dùng. Đã kiểm trên DB v2 thật: `DB version upgrading from 2 to 3`,
   sách + tiến độ + phiên đọc + chú thích còn nguyên.
2. **`NULL` khác `""`.** `NULL` = chưa quét, `""` = đã quét và sách không khai báo. Gộp lại thì hoặc bỏ sót sách
   cũ, hoặc mở lại từng tệp EPUB ở mọi lần mở ứng dụng.
3. **Đánh giá ở bảng riêng.** Nó do người đọc tạo, không phải metadata của tệp — quét lại OPF không được phép
   ghi đè.

### Đã kiểm trên máy

`%lang` → `en`, `%size` → `488.7 KB`, `%status` → `reading`, `%rating` → `★★★★☆` sau khi chạm 4 sao trong màn
chi tiết sách. Dòng overlay thật: `en|reading|488.7 KB|★★★★☆`.

### Chưa làm

1. **Preset gallery** — bản gốc tải preset từ một repo GitHub; chưa có nguồn nào để trỏ tới.
2. **Cử chỉ đổi preset / ẩn hiện nhanh** — phải sửa đường vào của tap-zone trong `EpubReaderContainer`.
3. **`RADIAL`** — đã khai báo là không làm kèm lý do trong `BookendsBarStyle`: vòng tròn không biểu diễn được trên
   một thanh tuyến tính.
4. **`%chap_pages`/`%chap_read` vẫn là ước lượng** — sai số ±1 trang với mục lục phẳng.
5. **Token `%opened`, `%quote`, `%file_num`, `%file_count`** trả rỗng: dữ liệu chưa được theo dõi.
6. **Chưa kiểm CBZ/AZW3 trên máy** — máy chỉ có EPUB.
