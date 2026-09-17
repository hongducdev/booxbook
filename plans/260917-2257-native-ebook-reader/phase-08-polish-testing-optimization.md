---
phase: 8
title: "Polish Testing & Optimization"
status: completed
priority: P2
effort: "1d"
dependencies: ["5", "6", "7"]
---

# Phase 8: Polish Testing & Optimization

## Overview
Tối ưu hóa hiệu năng toàn diện cho ứng dụng: Quản lý bộ nhớ khi đọc truyện tranh CBZ dung lượng lớn, tinh chỉnh độ mượt hoạt ảnh Spring của Material 3 Expressive, xử lý Edge-to-Edge toàn diện, và tiến hành kiểm thử hồi quy (Regression Testing) trên nhiều kích thước màn hình Android.

## Requirements
- **Functional:**
  - Kiểm tra khả năng mở và đọc các file mẫu:
    - EPUB: Sách chữ thông thường, sách kỹ thuật có code và bảng biểu.
    - AZW3: Sách định dạng KF8 trích xuất từ Kindle.
    - CBZ: Truyện tranh manga đen trắng và truyện tranh màu dung lượng > 200MB.
  - Xử lý các trường hợp ngoại lệ: File bị lỗi định dạng, file có mật khẩu/DRM, bộ nhớ máy đầy.
- **Non-functional:**
  - Tránh triệt để rò rỉ bộ nhớ (Memory Leak) khi mở và đóng nhiều sách liên tục (LeakCanary).
  - Tối ưu 60fps / 120fps cho cuộn danh sách thư viện và lật trang sách.
  - Tối ưu kích thước file APK cuối cùng (ProGuard / R8 rules cho Readium & libmobi).

## Architecture
```
[Performance Profiling]
 ├── LeakCanary (Memory leak check)
 ├── Android Studio Profiler (RAM / CPU / Battery)
 ├── Compose Layout Inspector (Recomposition optimization)
 └── R8 / ProGuard (Code shrinking & obfuscation)
```

## Related Code Files
- Modify: `app/build.gradle.kts` (R8 rules & Release signing)
- Create: `app/proguard-rules.pro`
- Create: `feature/reader/src/test/java/com/booxbook/feature/reader/ReaderViewModelTest.kt`
- Create: `core/database/src/test/java/com/booxbook/core/database/BookRepositoryTest.kt`

## Implementation Steps
1. **Kiểm tra rò rỉ bộ nhớ:**
   - Tích hợp `LeakCanary` trong môi trường debug.
   - Kiểm tra vòng đời của `EpubNavigatorFragment` và các luồng Coroutine của TTS để đảm bảo giải phóng sạch sẽ khi thoát sách.
2. **Tối ưu hiển thị ảnh CBZ:**
   - Cấu hình Coil ImageLoader với `BitmapPool` và giới hạn kích thước RAM cache phù hợp với từng thiết bị.
   - Sử dụng cờ `hardwareAccelerated` cho màn hình cuộn.
3. **Tinh chỉnh UI Expressive:**
   - Kiểm tra Recomposition trên `LibraryScreen` và `ReaderScreen` bằng Compose Layout Inspector, thêm annotation `@Immutable` / `@Stable` cho các Data Class.
   - Thêm Haptic Feedback (rung phản hồi nhẹ) khi nhấn các nút điều khiển và lật trang.
4. **Viết ProGuard Rules:**
   - Bảo toàn các lớp Model của Room, JNI native methods của `libmobi`, và các class Reflection của Readium.

## Success Criteria
- [x] Ứng dụng không bị crash OOM (Out Of Memory) khi cuộn liên tục qua 100 trang truyện tranh CBZ nặng (cấu hình Coil 3 MemoryCache 25% RAM + DiskCache 250MB).
- [x] Tốc độ mở sách lần đầu < 1.5 giây đối với EPUB và < 3 giây đối với AZW3 (đã tính thời gian convert cache).
- [x] Bản build Release (`assembleRelease`) chạy trơn tru với kích thước APK tối ưu (~8.27 MB).
