---
phase: 5
title: "Expressive UI & Library Feature"
status: completed
priority: P1
effort: "2d"
dependencies: ["1", "2"]
---

# Phase 5: Expressive UI & Library Feature

## Overview
Xây dựng giao diện Thư viện sách (Library Screen) áp dụng phong cách thiết kế **Material 3 Expressive (Pixel UI)** tham khảo từ repo `JustForPixel-ExpressiveLab`: Sử dụng Bento Grid, Carousel trượt thẻ sách đang đọc dở, Filter Chips phân loại định dạng, và Floating Action Pill để thêm sách mới.

## Requirements
- **Functional:**
  - Hiển thị danh sách sách trong máy theo dạng Bento Grid hoặc Expressive Carousel.
  - Phân loại sách nhanh bằng Expressive Filter Chips: "Tất cả", "EPUB", "AZW3", "Truyện tranh (CBZ)".
  - Tìm kiếm sách theo tiêu đề / tác giả với Expressive Search Bar có hiệu ứng mở rộng.
  - Nút thêm sách nổi (Expressive Morphing FAB) mở SAF Picker để nhập file.
  - Nhấn vào sách để điều hướng sang Màn hình Đọc sách (Reader Screen).
  - Nhấn giữ sách để mở Context Menu dạng Bottom Sheet (Xem thông tin, Xóa sách, Xuất ghi chú).
- **Non-functional:**
  - Chuyển động vật lý lò xo (Spring Physics Animations) trên các thẻ sách khi cuộn và chạm.
  - Dynamic Color Monet: Tự động đổi tone màu chủ đạo theo ảnh bìa sách đang đọc gần nhất.

## Architecture
```
[LibraryScreen (Compose)]
 ├── [ExpressiveSearchBar]
 ├── [FormatFilterChips]
 ├── [ContinueReadingCarousel] ──► Hiển thị bìa sách lớn + Progress pill
 ├── [AllBooksBentoGrid]       ──► Thẻ sách bo góc 28.dp với hiệu ứng chạm nảy
 └── [AddBookFloatingPill]     ──► SAF Document Picker
```

## Related Code Files
- Create: `core/ui/src/main/java/com/booxbook/core/ui/component/ExpressiveCard.kt`
- Create: `core/ui/src/main/java/com/booxbook/core/ui/component/ExpressivePillButton.kt`
- Create: `core/ui/src/main/java/com/booxbook/core/ui/animation/SpringPhysics.kt`
- Create: `feature/library/src/main/java/com/booxbook/feature/library/LibraryScreen.kt`
- Create: `feature/library/src/main/java/com/booxbook/feature/library/LibraryViewModel.kt`
- Create: `feature/library/src/main/java/com/booxbook/feature/library/components/BentoBookCard.kt`
- Create: `feature/library/src/main/java/com/booxbook/feature/library/components/BookDetailBottomSheet.kt`

## Implementation Steps
1. Xây dựng bộ component Expressive trong `:core:ui`:
   - `ExpressiveCard`: Thẻ bo góc lớn có Spring click scale effect.
   - `ExpressiveFilterChip`: Chip dạng viên thuốc với hoạt ảnh đổi màu mượt mà.
2. Xây dựng `LibraryViewModel`:
   - Lắng nghe StateFlow từ `BookRepository`.
   - Quản lý trạng thái tìm kiếm (Search Query) và bộ lọc định dạng (Format Filter).
   - Xử lý intent nhập sách từ SAF picker qua `BookStorageManager`.
3. Ghép nối `LibraryScreen`:
   - Section 1: Top App Bar + Search Bar nổi.
   - Section 2: "Đang đọc dở" (Continue Reading) dạng ngang lướt mượt mà.
   - Section 3: "Tất cả sách" dạng Bento Grid 2 cột tự co giãn.
4. Thêm Bottom Sheet chi tiết sách với hiệu ứng trượt Spring bottom sheet.

## Success Criteria
- [x] Giao diện thư viện hiển thị sắc nét, chuẩn phong cách Pixel Material 3 Expressive.
- [x] Tìm kiếm và lọc định dạng phản hồi tức thì (< 50ms).
- [x] Thêm file EPUB/AZW3/CBZ từ bộ nhớ máy thành công và xuất hiện ngay lập tức trên kệ sách.
