# Phase 02 — Overlay rendering, preset store & wiring

**Module:** `:feature:reader` (+ `ReaderScreen`) · **Phụ thuộc:** Phase 01 · **Trạng thái:** ✅

## Mục tiêu

Biến đầu ra của formatter thành overlay thật trên trang đọc, có chỗ lưu cấu hình, và có màn bật/tắt
để người dùng tự kiểm chứng.

## Việc đã làm

1. `BookendsPreferencesManager` (`@Singleton`) — SharedPreferences `booxbook_bookends_prefs`, lưu
   `BookendsSettings` dạng JSON qua `kotlinx.serialization`, phơi `StateFlow<BookendsSettings>`.
   Preset tự tạo/hư hỏng thì rơi về preset mặc định thay vì làm sập màn đọc.
2. `BookendsHostState` — gom `BookendsSnapshot` từ `ReaderUiState` + thống kê Room + trạng thái thiết
   bị (pin/điện/mạng/đèn) + đồng hồ, tính `avgSecondsPerPage` để suy ra thời gian còn lại.
3. `BookendsOverlay` — 6 vùng neo, hàng trên/dưới, dòng rỗng tự ẩn.
4. `BookendsLineView` — `Row` gồm text (`AnnotatedString` cho bold/italic/uppercase), `Icon` Material,
   `ProgressBar` nội dòng co giãn, `Spacer` đẩy hai đầu.
5. `BookendsProgressBar` — vẽ `SOLID/BORDER/ROUND/METRO/HOLLOW`, màu theo theme đọc.
6. `BookendsSettingsSheet` — bật/tắt, chọn preset, font scale, margin, **live preview** ngay trong
   sheet, và nút khôi phục preset gốc.
7. Wiring: `ReaderScreen` đặt `BookendsOverlay` phía trên canvas và dưới chrome (cùng tầng với
   `PageTurnFlipOverlay`); `ReaderSettingsSheet` có mục mở màn Bookends.

## Quyết định thiết kế

- **Cấu hình đi qua `BookendsPreferencesManager`, không qua `ReaderUiState`.** Các cài đặt reader
  hiện có (`tapZoneMode`, `pageTurnEffect`…) được ghi vào SharedPreferences bởi `SettingsViewModel`
  nhưng `ReaderViewModel` không bao giờ đọc lại — nên đổi ở tab Cài đặt không có tác dụng trong
  reader. Bookends dùng một nguồn sự thật duy nhất cho cả hai phía.
- **Overlay nằm trong vùng đã cắt insets**, cùng cấp với `PageTurnFlipOverlay`, nên không đè lên
  status bar / nav bar.
- **Overlay không chặn chạm**: chỉ vẽ, `pointerInput` không được gắn, để tap-zone lật trang vẫn ăn.

## Kiểm thử

`feature/reader/src/test/java/com/booxbook/feature/reader/BookendsPreferencesManagerTest.kt` — lưu/đọc
lại preset, JSON hỏng → mặc định, bật/tắt, active preset không tồn tại → rơi về preset đầu.
