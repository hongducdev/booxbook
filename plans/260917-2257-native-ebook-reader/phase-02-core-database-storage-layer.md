---
phase: 2
title: "Core Database & Storage Layer"
status: pending
priority: P1
effort: "1d"
dependencies: ["1"]
---

# Phase 2: Core Database & Storage Layer

## Overview
Xây dựng tầng lưu trữ cục bộ sử dụng Room Database để quản lý danh mục sách (Books), tiến độ đọc (Reading Progress), và hệ thống ghi chú (Annotations & Highlights). Thiết lập bộ quản lý tệp tin (File Storage Manager) hỗ trợ Storage Access Framework (SAF) để nhập sách.

## Requirements
- **Functional:**
  - Định nghĩa thực thể `BookEntity` hỗ trợ các định dạng `EPUB`, `AZW3`, `CBZ`.
  - Định nghĩa thực thể `ReadingProgressEntity` lưu tiến độ theo chuẩn CFI của Readium hoặc `pageIndex` của CBZ.
  - Định nghĩa thực thể `AnnotationEntity` (Highlight, Note, Bookmark).
  - Viết `BookStorageManager` để sao chép file từ SAF vào App Internal Storage và trích xuất bìa sách (Cover image).
- **Non-functional:**
  - Truy vấn bất đồng bộ hoàn toàn qua Kotlin Flow / Coroutines.
  - Tối ưu hóa Index trên `bookId` và `lastReadTimestamp`.

## Architecture
```
[Storage Access Framework] ──► [BookStorageManager] ──► [Internal Files Dir]
                                        │
                                        ▼
                                  [Room Database]
                                ├── BookDao
                                ├── ReadingProgressDao
                                └── AnnotationDao
```

## Related Code Files
- Create: `core/database/src/main/java/com/booxbook/core/database/entity/BookEntity.kt`
- Create: `core/database/src/main/java/com/booxbook/core/database/entity/ReadingProgressEntity.kt`
- Create: `core/database/src/main/java/com/booxbook/core/database/entity/AnnotationEntity.kt`
- Create: `core/database/src/main/java/com/booxbook/core/database/dao/BookDao.kt`
- Create: `core/database/src/main/java/com/booxbook/core/database/BooxBookDatabase.kt`
- Create: `core/database/src/main/java/com/booxbook/core/database/repository/BookRepositoryImpl.kt`
- Create: `core/database/src/main/java/com/booxbook/core/database/storage/BookStorageManager.kt`

## Implementation Steps
1. Xây dựng các Entity và quan hệ (Foreign Keys với `CASCADE` delete).
2. Xây dựng các DAO với các thao tác CRUD cơ bản và Flow observers cho danh sách sách gần đây, danh sách theo định dạng.
3. Cấu hình TypeConverters cho Enum `BookFormat` và `AnnotationType`.
4. Viết `BookStorageManager` xử lý:
   - Phân tích URI từ SAF picker.
   - Nhận diện MIME type / phần mở rộng (`.epub`, `.azw3`, `.cbz`).
   - Copy file an toàn vào `context.filesDir/books/`.
5. Viết Unit Test cho DAO và Repository sử dụng In-Memory Database.

## Success Criteria
- [ ] Lưu và đọc được thông tin sách vào Room Database.
- [ ] Cập nhật tiến độ đọc (CFI / PageIndex) chính xác khi chuyển trang.
- [ ] Unit test Room Database pass 100%.
