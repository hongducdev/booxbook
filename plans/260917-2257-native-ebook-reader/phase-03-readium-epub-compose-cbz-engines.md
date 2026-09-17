---
phase: 3
title: "Readium EPUB & Compose CBZ Engines"
status: completed
priority: P1
effort: "2d"
dependencies: ["1", "2"]
---

# Phase 3: Readium EPUB & Compose CBZ Engines

## Overview
Tích hợp thư viện **Readium Kotlin Toolkit** để làm engine hiển thị và dàn trang cho sách EPUB. Đồng thời phát triển **Comic Engine thuần Jetpack Compose** cho định dạng CBZ hỗ trợ chế độ cuộn dọc liên tục (Vertical Continuous Scroll) và zoom ảnh.

## Requirements
- **Functional:**
  - **EPUB Engine:**
    - Khởi tạo `Streamer` và `Publication` từ file `.epub`.
    - Tích hợp `EpubNavigatorFragment` hỗ trợ chế độ lật trang từng trang một (Discrete Pagination / Slide transition).
    - Hỗ trợ lấy mục lục (Table of Contents / Spine).
    - Trích xuất ảnh bìa sách từ Publication metadata.
  - **CBZ Engine:**
    - Parse tệp nén ZIP của file `.cbz`, đọc danh sách entry ảnh (JPG, PNG, WebP).
    - Tạo `CbzReaderScreen` sử dụng `LazyColumn` để cuộn dọc liên tục từ trên xuống dưới.
    - Tích hợp cử chỉ Pinch-to-Zoom (zoom 2 ngón) trên từng trang hoặc toàn trang truyện.
- **Non-functional:**
  - Tiết kiệm RAM khi đọc truyện tranh nặng hàng trăm MB (sử dụng Coil Memory Cache & Downsampling).
  - Khởi động sách nhanh (Lazy loading các trang tiếp theo).

## Architecture
```
                     [ReaderEngineContract]
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
   [EpubReaderEngine]                     [CbzReaderEngine]
  (Readium Streamer &                   (ZipArchiveExtractor &
   EpubNavigatorFragment)                Compose LazyColumn + Coil)
```

## Related Code Files
- Create: `core/engine/src/main/java/com/booxbook/core/engine/ReaderEngine.kt`
- Create: `core/engine/src/main/java/com/booxbook/core/engine/epub/EpubReaderEngine.kt`
- Create: `core/engine/src/main/java/com/booxbook/core/engine/epub/ReadiumAssetRetriever.kt`
- Create: `core/engine/src/main/java/com/booxbook/core/engine/cbz/CbzArchiveExtractor.kt`
- Create: `core/engine/src/main/java/com/booxbook/core/engine/cbz/CbzReaderComponent.kt`
- Create: `core/engine/src/main/java/com/booxbook/core/engine/model/ReaderState.kt`

## Implementation Steps
1. Thêm dependencies Readium Kotlin Toolkit (`readium-shared`, `readium-streamer`, `readium-navigator`).
2. Xây dựng `EpubReaderEngine`:
   - Mở và parse Publication từ File path.
   - Cấu hình Preferences cho Navigator: `scroll: false` (bật chế độ lật trang discrete), font size, line spacing, margins.
3. Xây dựng `CbzArchiveExtractor`:
   - Mở file `.cbz` qua `ZipInputStream` hoặc `java.util.zip.ZipFile`.
   - Sắp xếp thứ tự tên file theo số tự nhiên (Natural Sort: `page1`, `page2`, `page10`).
   - Cung cấp `ImageUri` hoặc luồng `InputStream` cho Coil.
4. Xây dựng `CbzReaderComponent` trên Jetpack Compose:
   - Sử dụng `LazyColumn` với `rememberLazyListState`.
   - Bắt sự kiện cuộn để tính `currentPageIndex` và báo về ViewModel.
   - Bọc mỗi trang ảnh bằng `Modifier.zoomable`.

## Success Criteria
- [x] Mở được file EPUB mẫu, hiển thị đúng chữ và lật trang mượt mà từng trang.
- [x] Mở được file CBZ mẫu, cuộn dọc mượt mà từ trên xuống dưới không giật lag.
- [x] Trích xuất thành công bìa sách từ cả file EPUB và CBZ để hiển thị ngoài thư viện.
