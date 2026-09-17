# Báo cáo Thiết kế Kỹ thuật: Native Android Ebook Reader (BooxBook)
**Ngày:** 2026-09-17  
**Trạng thái:** Approved  
**Phong cách UI:** Material 3 Expressive (`JustForPixel-ExpressiveLab`)  
**Định dạng hỗ trợ:** EPUB, AZW3, CBZ  

---

## 1. Mục tiêu & Yêu cầu cốt lõi
- **Thiết bị:** Android Native (Phone & Tablet), tận dụng Jetpack Compose, Material You Monet, Spring animations và các component Expressive.
- **Trải nghiệm đọc:**
  - **Sách chữ (EPUB, AZW3):** Dàn trang theo trang rời (Discrete Pagination / Slide transition), lật trang từng trang chuẩn e-reader.
  - **Truyện tranh (CBZ):** Cuộn liên tục dọc (Vertical Continuous Scroll) chuẩn Webtoon/Manga kết hợp zoom 2 ngón (Pinch-to-Zoom).
- **Tính năng MVP:**
  - Quản lý thư viện cục bộ (Local Storage).
  - Đọc và lưu tiến độ đọc (Reading Progress).
  - Đánh dấu & Ghi chú (Highlight / Annotations / Bookmarks).
  - Trình đọc giọng nói Text-To-Speech (TTS) đồng bộ với hiển thị câu đang đọc.

---

## 2. Các giải pháp kỹ thuật đã đánh giá

| Tiêu chí | Tiếp cận đã chọn (Option A - Modern Hybrid) | Tiếp cận loại bỏ (Option B - C++ Core) | Tiếp cận loại bỏ (Option C - Tự viết từ đầu) |
| :--- | :--- | :--- | :--- |
| **Engine** | Readium Kotlin + Libmobi NDK + Compose Comic | KOReader / crengine bọc ngoài | Tự parse Zip/XML/HTML và dàn trang Compose |
| **Ưu điểm** | UI Compose 100% linh hoạt, Readium hỗ trợ EPUB/CFI chuẩn quốc tế, CBZ nhẹ nhàng với Coil | Hỗ trợ AZW3 native không cần cache | Không phụ thuộc thư viện ngoài |
| **Nhược điểm** | Cần bridge NDK `libmobi` để unwrap AZW3 | UI đọc sách bị bó hẹp trong Canvas C++, khó tích hợp M3 Expressive | Chi phí phát triển cực lớn, thuật toán dàn trang dễ lỗi |
| **Lý do chọn/loại** | **CHỌN:** Cân bằng hoàn hảo giữa sức mạnh UI Expressive và độ ổn định của engine đọc | **LOẠI:** Khó đạt độ mượt mà và animation của JustForPixel | **LOẠI:** Tốn hàng tháng vô ích cho việc dàn trang |

---

## 3. Kiến trúc hệ thống được phê duyệt

### Kiến trúc phân tầng (Clean Architecture & Modularization)
```
:app
 ├── :core:ui              (M3 Expressive Theme, Typography, Shapes, Dynamic Color Monet)
 ├── :core:database        (Room Database: Books, ReadingProgress, Annotations)
 ├── :core:engine:epub     (Readium Kotlin Toolkit Navigator)
 ├── :core:engine:azw3     (libmobi C++ NDK Wrapper -> EPUB Cache Converter)
 ├── :core:engine:cbz      (Compose LazyColumn + Subsampling/Zoomable Coil Viewer)
 ├── :core:tts             (Android TextToSpeech Service & Utterance Sync)
 ├── :feature:library      (Bento Grid, Expressive Carousel, Search & Filter)
 └── :feature:reader       (Unified Reader Screen, Floating Bottom Toolbar, TTS Player)
```

### Thiết kế Dữ liệu (Room Database)
- **`books`**: Lưu thông tin sách, định dạng (EPUB, AZW3, CBZ), đường dẫn file, ảnh bìa, thời gian đọc gần nhất.
- **`reading_progress`**: Lưu vị trí đọc (CFI của Readium hoặc `pageIndex` của CBZ) và tỷ lệ % hoàn thành.
- **`annotations`**: Lưu vị trí bôi đen, màu sắc, nội dung ghi chú (Note/Highlight/Bookmark).

---

## 4. Quản lý Rủi ro
1. **Biên dịch NDK libmobi:** Đóng gói CMake script tối giản cho các kiến trúc `arm64-v8a`, `armeabi-v7a`, `x86_64`.
2. **Quản lý bộ nhớ với CBZ:** Sử dụng Coil với decode stream và Bitmap RGB_565, chỉ giữ các trang lân cận trong RAM.
3. **Đồng bộ TTS:** Bắt sự kiện `UtteranceProgressListener.onRangeStart` để highlight và lật trang tự động.
