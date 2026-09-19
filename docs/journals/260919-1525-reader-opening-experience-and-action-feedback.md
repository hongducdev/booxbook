# Journal Entry: Reader Opening Experience & Action Feedback
**Date:** 2026-09-19  
**Module:** `:feature:reader`, `:feature:library`, `:core:engine`  
**Status:** Implemented & Verified (compile + unit tests)  

## Vấn đề

Hai khoảng trống trải nghiệm đối lập nhau nhưng cùng nằm ở chỗ "người đọc không biết chuyện gì đang xảy ra":

1. **Mở sách** chỉ có một vòng xoay trống với dòng chữ chung chung *"Đang mở sách..."*. Với EPUB/AZW3, sau khi vòng xoay biến mất còn một khoảng canvas trắng trong lúc Readium gắn navigator và WebView vẽ trang đầu — người đọc không biết đang chờ gì hay app có treo không.
2. **Các thao tác không trả lời.** Đánh dấu trang, xoá ghi chú, bật/tắt TTS, đặt lại tiến độ đều im lặng. Đáng chú ý nhất là lỗi xoá sách ở màn chi tiết: `BookDetailViewModel` ghi lỗi vào `errorMessage`, nhưng `BookDetailScreen` chỉ hiển thị `errorMessage` khi `book == null` — nên lỗi **không bao giờ hiện ra**.

## 1. Opening Pipeline & `ReaderOpeningOverlay`

`ReaderLoadingPhase` công bố tiến trình thật của việc mở sách thay vì một cờ `isLoading` nhị phân:

| Phase | Bước thật |
| --- | --- |
| `LOADING_BOOK` (0.15) | Đọc bản ghi sách + tiến độ đã lưu từ Room |
| `OPENING_PUBLICATION` (0.55) | Đã biết sách, mở Readium publication / giải nén CBZ |
| `BUILDING_CANVAS` (0.85) | Engine xong, chờ trang đầu vẽ |
| `READY` (1.0) | Trang đã hiện **hoặc** đã chuyển sang màn lỗi |

`ReaderOpeningOverlay` phủ lên trên: bìa sách (Coil), tựa đề, tác giả, thanh tiến độ + nhãn bước có crossfade. Nhãn tiếng Việt: *Đang đọc thông tin sách… → Đang mở nội dung sách… → Đang dựng trang đọc…*.

### Ba quyết định kỹ thuật

1. **Lớp phủ, không phải màn hình thay thế.** Canvas được dựng ngay khi biết sách (kể cả khi `isLoading` vẫn `true`), vì Readium chỉ phát tín hiệu sẵn sàng *sau khi* navigator được gắn vào cây view. Nếu đợi màn chờ tan mới dựng canvas thì tín hiệu không bao giờ tới, và người đọc thấy đúng khoảng trắng mà tính năng này định xoá.
2. **Lớp phủ phải tiêu thụ chạm.** Nó nằm trên một `AndroidView` (WebView của Readium); view thật trong cây Compose vẫn nhận được chạm nếu không có gì chặn, nên lớp phủ tiêu thụ mọi `PointerEvent` ở `PointerEventPass.Initial`.
3. **"Sẵn sàng" đo bằng nội dung thật, không bằng đồng hồ.** EPUB/AZW3 gọi `onCanvasReady()` ở locator đầu tiên Readium phát ra; CBZ gọi khi trang mở đầu tiên giải nén xong (`CbzReaderComponent(onReady = …)`). `onCanvasReady()` idempotent nên tín hiệu nào tới trước cũng đủ, và `CANVAS_READY_FALLBACK_MS = 2500` là lưới an toàn cho trường hợp không có tín hiệu nào.

Màu chữ lớp phủ suy từ `Color.luminance()` của nền màn đọc, không lấy từ `MaterialTheme.colorScheme`: 4 preset nền Sáng/Giấy ấm/Tối/Đen tuyền độc lập với theme app, nên chỉ cách này mới tương phản đủ ở cả bốn.

## 2. `ReaderFeedback` → Snackbar

`ReaderViewModel` phát **sự kiện**, không phát chuỗi: `BookmarkAdded/Removed`, `TtsStarted/Stopped`, `BookFinished`, `AnnotationDeleted(annotation)`, `AnnotationRestored(annotation)`, `Failure(message)`. `ReaderScreen` dịch qua `ReaderFeedback.toSnackbar()` — nơi duy nhất giữ câu chữ, có test riêng khoá lại wording và nhãn nút.

- **Xoá ghi chú có thể hoàn tác.** `AnnotationDeleted` mang bản ghi gốc; "Hoàn tác" gọi `restoreAnnotation()` ghi lại **đúng `id` cũ** → hoàn tác là idempotent thay vì nhân bản ghi chú.
- **"Đã dừng" ≠ "đã hết sách".** `stopTts(notify: Boolean = true)` chỉ phát `TtsStopped` khi có phiên đang chạy; hết chương cuối thì `stopTts(notify = false)` + `BookFinished`.
- **Thất bại không im lặng.** Đánh dấu, xoá/khôi phục ghi chú, khởi động TTS đều phát `Failure` kèm thông điệp gốc.
- Buffer `extraBufferCapacity = 8` + `tryEmit` để sự kiện không bị mất trong lúc `showSnackbar` (suspend) đang hiển thị.

## 3. Phản hồi ở `BookDetailScreen`

Thêm `SnackbarHost` + `BookDetailFeedback` (`ProgressReset(canUndo)`, `ProgressRestored`, `Failure`):

- **Đặt lại tiến độ có thể hoàn tác:** bản ghi `ReadingProgress` cũ được giữ lại trước khi xoá; "Hoàn tác" ghi lại đúng bản ghi đó và hồi sinh state.
- **Hoàn tác chỉ một lần:** con trỏ `progressBeforeReset` bị xoá sau khi khôi phục → bấm lần hai là no-op, không ghi đè tiến độ mới hơn.
- **Lỗi xoá sách đi qua snackbar** thay vì `errorMessage` vô hình.

## Verification

- `:feature:reader:compileDebugKotlin`, `:feature:library:compileDebugKotlin` — BUILD SUCCESSFUL.
- `:feature:reader:testDebugUnitTest`, `:feature:library:testDebugUnitTest` — pass:
  - `ReaderViewModelTest`: 18 test (thêm 6: walk phase, fallback, lỗi không bị phủ, bookmark feedback, delete→undo, stopTts im lặng).
  - `ReaderFeedbackTest`: 6 test mới khoá wording/nhãn/hành động hoàn tác.
  - `BookDetailViewModelTest`: 7 test (thêm 3: reset→undo, undo hai lần, lỗi xoá sách).
- Ràng buộc test cũ được giữ nguyên: không `advanceUntilIdle()` trong `ReaderViewModelTest` (ticker 60s vẫn chạy vô hạn), và `release()` giờ huỷ thêm `canvasReadyFallbackJob` để scheduler drain kết thúc được.

## Chưa làm (ghi lại để không quên)

- **Cài đặt đọc không được lưu.** Cỡ chữ, font, theme, vùng chạm, hiệu ứng lật trang, rung chỉ nằm trong `ReaderUiState` — thoát reader là mất. Cần một `ReaderPreferencesManager` (SharedPreferences/DataStore) như `StatisticsPreferencesManager`.
- **Thanh trượt tiến độ ở CBZ không nhảy trang.** `CbzReaderComponent` nhận `initialPageIndex` nhưng chỉ dùng lúc khởi tạo `rememberLazyListState`; chưa có `LaunchedEffect` gọi `scrollToItem` khi state đổi, nên `onSeekToPage` ở chế độ CBZ không có tác dụng thị giác.
