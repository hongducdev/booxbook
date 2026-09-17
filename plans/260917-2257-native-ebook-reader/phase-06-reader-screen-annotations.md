---
phase: 6
title: "Reader Screen & Annotations"
status: pending
priority: P1
effort: "3d"
dependencies: ["3", "4", "5"]
---

# Phase 6: Reader Screen & Annotations

## Overview
Xây dựng Màn hình Đọc sách hợp nhất (Unified Reader Screen), hỗ trợ chuyển đổi linh hoạt giữa chế độ lật trang (EPUB/AZW3 qua Readium) và chế độ cuộn dọc (CBZ qua Compose). Tích hợp thanh công cụ nổi (Floating Pill Toolbar), Menu cài đặt hiển thị (Font, Cỡ chữ, Màu nền), Mục lục (Table of Contents), và hệ thống Đánh dấu / Ghi chú (Annotations).

## Requirements
- **Functional:**
  - **Màn hình đọc toàn cảnh (Immersive Mode):** Tự động ẩn Status bar và Navigation bar khi đọc sách.
  - **Floating Pill Toolbar:** Chạm vào vùng giữa màn hình để hiện/ẩn thanh điều khiển nổi phía dưới với hiệu ứng Spring.
  - **Cài đặt hiển thị (Reader Settings):**
    - Cỡ chữ, Giãn dòng, Căn lề, Font chữ tùy chọn (Roboto Flex, Lexend, Bookerly).
    - Theme màu đọc sách: Sáng (Light), Tối (AMOLED Dark), Giấy ấm (Sepia), E-paper (High contrast).
  - **Mục lục (Table of Contents):** Hiển thị danh sách chương trong Bottom Sheet, cho phép nhảy nhanh đến chương bất kỳ.
  - **Tiến độ đọc:** Tự động lưu vị trí (CFI / PageIndex) mỗi khi người dùng chuyển trang hoặc cuộn.
  - **Highlight & Ghi chú (Annotations):**
    - Bôi đen đoạn văn trong EPUB -> Hiện Action Popup (Màu vàng, xanh, đỏ, Ghi chú, Tra từ).
    - Lưu vào Room và hiển thị vệt highlight trên trang sách.
    - Quản lý danh sách Highlights / Notes theo cuốn sách.
- **Non-functional:**
  - Lật trang mượt mà không chớp nháy (Zero flickering).
  - Khôi phục chính xác 100% vị trí đọc khi người dùng mở lại sách.

## Architecture
```
[ReaderScreen (Compose)]
 ├── [ReaderEngineContainer]
 │    ├── Case EPUB/AZW3: AndroidView(EpubNavigatorFragment)
 │    └── Case CBZ: CbzReaderComponent (LazyColumn)
 ├── [AnimatedTopBar]          (Tên sách, Chương hiện tại, Nút Back)
 ├── [FloatingBottomToolbar]   (Mục lục, Cài đặt font, Bookmark, Nút bật TTS)
 ├── [SettingsBottomSheet]     (Chỉnh kiểu chữ, theme nền)
 └── [TocBottomSheet]          (Danh sách chương)
```

## Related Code Files
- Create: `feature/reader/src/main/java/com/booxbook/feature/reader/ReaderScreen.kt`
- Create: `feature/reader/src/main/java/com/booxbook/feature/reader/ReaderViewModel.kt`
- Create: `feature/reader/src/main/java/com/booxbook/feature/reader/components/FloatingReaderToolbar.kt`
- Create: `feature/reader/src/main/java/com/booxbook/feature/reader/components/ReaderSettingsSheet.kt`
- Create: `feature/reader/src/main/java/com/booxbook/feature/reader/components/TableOfContentsSheet.kt`
- Create: `feature/reader/src/main/java/com/booxbook/feature/reader/components/AnnotationActionPopup.kt`

## Implementation Steps
1. Xây dựng `ReaderViewModel`:
   - Nạp thông tin sách và vị trí đọc gần nhất từ `BookRepository`.
   - Kết nối với Engine tương ứng dựa trên `book.format`.
   - Lưu tiến độ đọc định kỳ (Debounced 500ms) vào Database.
2. Xây dựng `FloatingReaderToolbar`:
   - Thiết kế dạng viên thuốc nổi bo góc 32.dp với nền Blur / M3 Surface Tonal.
   - Hoạt ảnh xuất hiện / biến mất dạng Spring trượt từ dưới lên.
3. Tích hợp `EpubNavigatorFragment` qua `AndroidViewBinding` hoặc Compose `AndroidView`:
   - Gắn listener lắng nghe sự kiện lật trang (`navigator.currentLocator`).
   - Cấu hình style CSS cho trang sách (Font, Theme, Spacing).
4. Xây dựng tính năng Highlight:
   - Tích hợp API Decoration / Selection của Readium để bắt vị trí text bôi đen.
   - Lưu vào bảng `annotations`.
5. Tạo `TableOfContentsSheet` và `ReaderSettingsSheet` với Material 3 Expressive BottomSheet.

## Success Criteria
- [ ] Lật trang EPUB và cuộn CBZ hoạt động trơn tru.
- [ ] Đổi cỡ chữ, màu nền phản hồi tức thì mà không làm mất vị trí đọc hiện tại.
- [ ] Bôi đen text và tạo highlight thành công, highlight vẫn hiển thị khi mở lại sách.
- [ ] Nhảy chương từ mục lục chính xác.
