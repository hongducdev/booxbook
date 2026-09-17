---
phase: 4
title: "Libmobi NDK Bridge for AZW3"
status: pending
priority: P2
effort: "2d"
dependencies: ["1", "3"]
---

# Phase 4: Libmobi NDK Bridge for AZW3

## Overview
Xây dựng module NDK C++ tích hợp thư viện mã nguồn mở **`libmobi`** để giải mã định dạng Amazon Kindle KF8/AZW3 (DRM-Free). Cung cấp cầu nối JNI (Java Native Interface) để chuyển đổi file `.azw3` thành định dạng chuẩn `.epub` trong thư mục cache tạm, cho phép tái sử dụng toàn bộ sức mạnh dàn trang của Readium EPUB Engine.

## Requirements
- **Functional:**
  - Biên dịch `libmobi` (C99) với CMake trong Android NDK cho các kiến trúc: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
  - Viết hàm JNI `nativeConvertAzw3ToEpub(inputPath: String, outputEpubPath: String): Boolean`.
  - Xử lý cache thông minh: Nếu file AZW3 đã được convert trước đó trong cache, bỏ qua bước convert và mở trực tiếp.
  - Tự động dọn dẹp file cache cũ khi bộ nhớ vượt ngưỡng.
- **Non-functional:**
  - Tốc độ chuyển đổi nhanh (< 1-2 giây cho sách thông thường dưới 10MB).
  - Bắt lỗi an toàn, không làm crash app nếu gặp file AZW3 lỗi định dạng hoặc dính DRM.

## Architecture
```
[User imports .azw3] ──► [Azw3Converter JNI]
                                │
                                ▼ (C++ NDK - libmobi)
                      [Unwrap KF8 to EPUB 3]
                                │
                                ▼
                       [Cached .epub File]
                                │
                                ▼
                      [Readium Epub Engine] ──► [EpubNavigatorFragment]
```

## Related Code Files
- Create: `core/engine/src/main/cpp/CMakeLists.txt`
- Create: `core/engine/src/main/cpp/libmobi/` (Source files của libmobi)
- Create: `core/engine/src/main/cpp/azw3_bridge.cpp`
- Create: `core/engine/src/main/java/com/booxbook/core/engine/azw3/Azw3Converter.kt`
- Create: `core/engine/src/main/java/com/booxbook/core/engine/azw3/Azw3ReaderEngine.kt`

## Implementation Steps
1. Tải source code `libmobi` (chỉ lấy các file core C cần thiết, bỏ các CLI tool).
2. Tạo `CMakeLists.txt` liên kết `libmobi` và `zlib` có sẵn của Android NDK.
3. Viết `azw3_bridge.cpp`:
   - Mở file đầu vào bằng `mobi_init()`, `mobi_load_filename()`.
   - Kiểm tra xem sách có dính DRM không (`mobi_is_drm()`). Nếu có DRM, trả về mã lỗi thông báo người dùng sách có bảo vệ bản quyền.
   - Giải nén phần thân sách và xuất cấu trúc EPUB qua API của `libmobi`.
4. Viết lớp Kotlin `Azw3Converter.kt`:
   - Load native library: `System.loadLibrary("azw3_bridge")`.
   - Cung cấp hàm suspend: `suspend fun convert(azw3File: File): Result<File>`.
5. Tạo `Azw3ReaderEngine` kế thừa logic của `EpubReaderEngine`, tự động convert ngầm và nạp file đã convert vào Readium.

## Success Criteria
- [ ] Module C++ biên dịch thành công cho cả thiết bị thật (ARM64) và máy ảo Android (x86_64).
- [ ] Chuyển đổi thành công file `.azw3` mẫu sang file `.epub` có thể đọc bình thường trên Readium.
- [ ] Xử lý khéo léo thông báo lỗi khi người dùng mở file AZW3 có DRM của Amazon.
