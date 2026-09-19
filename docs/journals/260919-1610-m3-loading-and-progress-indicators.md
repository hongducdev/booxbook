# Journal Entry: Applying M3 Loading Indicator & Progress Indicators
**Date:** 2026-09-19  
**Module:** `:core:ui`, `:core:engine`, `:feature:reader`, `:feature:library`, `:feature:statistics`  
**Status:** Implemented & Verified (compile + unit tests)  

## Yêu cầu

Áp dụng hai trang spec của Material 3 vào các thành phần thích hợp của app:

- <https://m3.material.io/components/loading-indicator/overview>
- <https://m3.material.io/components/progress-indicators/overview>

Đọc kèm trang guidelines của cả hai (bảng chọn theo thời gian chờ nằm ở đó).

## Spec nói gì (phần đã áp dụng)

| Thời gian chờ | Khuyến nghị |
| --- | --- |
| Tức thời (< 200 ms) | Không hiện chỉ báo, hiện nội dung ngay |
| Ngắn (200 ms – 5 s) | **Loading indicator** (spec: "nên thay thế hầu hết chỗ dùng indeterminate circular") |
| Dài (> 5 s) | **Progress indicator**, determinate nếu đo được |

Cộng thêm: một tiến trình phải dùng **một** cấu hình (variant + tham số) trong toàn app; determinate
phải đo **đúng** tiến trình; linear đặt ở cạnh container, circular đặt giữa; stop indicator 4dp bắt buộc
khi track không đủ 3:1; không chuyển từ loading indicator sang determinate (phải bắt đầu bằng
indeterminate progress).

## Đã sai ở đâu trước đây

1. **Màn chờ mở sách** vẽ thanh determinate từ các mốc bước (0.15/0.55/0.85). Đó là *mốc công việc*,
   không phải phần trăm thời gian — spec yêu cầu determinate phải đo đúng, nên thanh này là thông tin sai.
2. **Cùng "tiến độ đọc" mà mỗi nơi một kiểu**: `BentoBookCard` 4dp, `TopBooksReadingList` 5dp,
   `BookDetailScreen` 8dp, track `surfaceContainerHighest`, có nơi `.clip(CircleShape)`/`.clip(PillShape)`.
   Vi phạm cả luật "một tiến trình, một cấu hình" lẫn màu track của spec (`secondaryContainer`).
3. **Nhập nhiều sách** biết `current/total` nhưng vẫn dùng vòng xoay indeterminate — vứt đi tiến trình
   đã có trong state.
4. **Mọi chỗ chờ ngắn** đều dùng `CircularProgressIndicator` indeterminate, gồm cả những chỗ dữ liệu
   Room về trong vài chục ms (nháy một khung hình — đúng ca "tức thời" mà spec nói không nên hiện gì).

## Đã làm

### 1. Port Material 3 Loading Indicator vào `:core:ui`

`ExpressiveLoadingIndicator` / `ExpressiveContainedLoadingIndicator` /
`DelayedLoadingIndicator` + `ReadingProgressBar` (xem [docs/core-ui.md](../core-ui.md)).

Component chính thức (`androidx.compose.material3.LoadingIndicator`) chỉ có từ Material3 1.4, và đường
nâng cấp đã được thử rồi phải lùi lại:

- `material3:1.4.0-alpha18` cần `compose-ui:1.8+` -> phải nâng BOM lên Compose 1.9.0.
- Compose 1.9.0 + Material3 1.4-alpha kéo `lifecycle-viewmodel-compose:2.9.0`,
  `lifecycle-runtime-compose-android:2.9.2`, `savedstate-compose-android:1.3.1` — chuỗi này không
  resolve được và làm hỏng build.
- Bản mới nhất (`compose 1.12.0-rc01` / `material3 1.5.0-alpha25`) yêu cầu `compileSdk 37` + `AGP 9.1`,
  trong khi dự án đang ở `compileSdk 35` + `AGP 8.7.3`.

Nên phần lõi được port nguyên token (48dp container / 38dp hình, `primary`), nguyên nhịp chuyển động
(morph mỗi 650 ms bằng spring `dampingRatio = 0.6`, `stiffness = 200`, `visibilityThreshold = 0.1`; cả
khối quay một vòng trong 4666 ms) và nguyên thứ tự 7 hình, dùng `androidx.graphics:graphics-shapes` —
chính thư viện Material3 dùng nội bộ. Dãy hình dựng lại bằng API công khai (`circle`, `star`,
`rectangle`, `RoundedPolygon(numVertices = …)`) nên không copy dữ liệu hình học của Material3.

### 2. Áp vào từng chỗ

| Chỗ | Trước | Sau |
| --- | --- | --- |
| `ReaderOpeningOverlay` | Thanh determinate 6dp từ mốc bước | `ExpressiveLoadingIndicator` 48dp + mô tả bước (màu suy từ độ sáng nền reader) |
| `LibraryScreen` — nhập > 1 tệp | Vòng xoay indeterminate | `ReadingProgressRing` (wavy, determinate) |
| `LibraryScreen` — nhập 1 tệp | Vòng xoay indeterminate | `ExpressiveLoadingIndicator` 28dp |
| `BookDetailScreen` — nạp sách | Vòng xoay indeterminate | `DelayedLoadingIndicator` (200 ms) |
| `BookDetailScreen` — mục lục | Vòng xoay 14dp | `ExpressiveLoadingIndicator` 24dp (cỡ nhỏ nhất spec cho phép) |
| `BookDetailScreen` / `BentoBookCard` / `ContinueReadingCarousel` / `TopBooksReadingList` | 4–8dp, track `surfaceContainerHighest`, clip thủ công | `ReadingProgressBar` (4dp, `primary`/`secondaryContainer`, stop indicator) |
| `StatisticsScreen` | Vòng xoay indeterminate | `DelayedLoadingIndicator` |
| `CbzReaderComponent` — trang chưa giải nén | Vòng xoay trắng 50% | `ExpressiveContainedLoadingIndicator` 40dp (có container vì nằm trên khung trang truyện) |

### 3. Chủ ý giữ khác spec

- Vòng tiến độ quanh nút TTS: determinate và đo đúng (câu/tổng câu), màu active đổi theo trạng thái
  play/pause — đúng phần "progress indicator trong button" của spec.
- `HeroReadingGoalCard` dày 16dp: cấu hình expressive có chủ ý cho một *tiến trình khác* (mục tiêu
  ngày), không trộn với cấu hình tiến độ đọc sách.
- Không thêm pull-to-refresh: dữ liệu là Room `Flow` tự cập nhật, không có thao tác "kéo để làm mới".

### 4. Dependency

Thêm `androidx.graphics:graphics-shapes:1.0.1` cho `:core:ui` (minCompileSdk 34, khớp dự án), và
`:core:engine` giờ phụ thuộc `:core:ui` vì `CbzReaderComponent` là UI Compose và dùng chung primitive
hiển thị. BOM Compose **không** đổi (vẫn 2024.12.01 / Material3 1.3.1).

## Verification

- `:core:ui`, `:core:engine`, `:feature:reader`, `:feature:library`, `:feature:statistics`
  `compileDebugKotlin` — BUILD SUCCESSFUL.
- `:feature:reader:testDebugUnitTest`, `:feature:library:testDebugUnitTest`,
  `:feature:statistics:testDebugUnitTest` chạy lại bằng `--rerun-tasks` — 0 failure
  (reader 18 + feedback 6 + tap-zones 12, library 7 + detail 7).
- Chưa chạy trên thiết bị: cần xem tay nhịp morph, độ tương phản trên 4 preset nền reader, và việc
  `DelayedLoadingIndicator` không nháy khi dữ liệu Room về nhanh.

## Việc chưa làm

- Nâng Compose/Material3 để dùng component chính thức và xoá phần port (xem điều kiện ở mục 1).
- Cài đặt đọc vẫn chưa được lưu (cỡ chữ, font, theme, vùng chạm, hiệu ứng lật, rung).
- Thanh trượt tiến độ ở CBZ vẫn không nhảy trang (`initialPageIndex` chỉ dùng lúc khởi tạo
  `rememberLazyListState`).
