---
phase: 3
title: "Apply indicators across feature call sites"
status: completed
priority: P2
effort: "2–4h"
dependencies: [2]
completed_date: "2026-09-20"
---

# Phase 3: Apply indicators across feature call sites

## Overview

Nối primitive của Phase 2 vào call site. Điểm bất ngờ dễ chịu: **phần lớn call site không cần sửa gì**.

Phiên trước đã đưa chúng về đúng bảng chờ M3, và vì `ReadingProgressBar` đổi ruột mà **giữ nguyên chữ
ký** (Phase 2, bước 7), bốn chỗ hiển thị % đọc tự động thành wavy mà không cần chạm. Đó là bằng chứng
DRY chứ không phải may mắn.

Phase này thực chất chỉ có **ba** file phải sửa:

1. `FloatingReaderToolbar.kt` — `Slider` → `WavyReaderSlider` (việc chính, và là việc duy nhất người
   dùng yêu cầu mà chưa có).
2. `LibraryScreen.kt` — `CircularProgressIndicator` tự đặt token → `ReadingProgressRing` (dọn DRY).
3. `HeroReadingGoalCard.kt` — **cân nhắc** wavy 16dp; mặc định là **không sửa**.

Phần lớn công việc của phase này là **chứng minh rằng không cần sửa** và không làm regress.

## Requirements

### Bảng call site: hiện trạng đã kiểm (`file:line`) → đích

| # | Call site | Hiện tại (đã verify) | Đích | Sửa? |
| --- | --- | --- | --- | --- |
| 1 | `feature/reader/.../components/ReaderOpeningOverlay.kt:243` | `ExpressiveLoadingIndicator(` 48dp | giữ nguyên | **Không** |
| 2 | `feature/library/.../LibraryScreen.kt:341-349` | `CircularProgressIndicator(progress = current/total)`, `strokeWidth = 3.dp`, `trackColor` đặt tại call site | `ReadingProgressRing(progress = …, modifier = Modifier.size(28.dp))` | **Có** |
| 3 | `feature/library/.../LibraryScreen.kt:351` | `ExpressiveLoadingIndicator(modifier = Modifier.size(28.dp))` | giữ nguyên | **Không** |
| 4 | `feature/library/.../detail/BookDetailScreen.kt:186` | `DelayedLoadingIndicator()` | giữ nguyên | **Không** |
| 5 | `feature/library/.../detail/BookDetailScreen.kt:328` | `ExpressiveLoadingIndicator(modifier = Modifier.size(24.dp))` | giữ nguyên | **Không** |
| 6 | `feature/library/.../detail/BookDetailScreen.kt:655` | `ReadingProgressBar(progress = percentage)` | wavy **tự động** qua Phase 2 | **Không** |
| 7 | `feature/library/.../components/BentoBookCard.kt:237` | `ReadingProgressBar(progress = progress.percentage)` | wavy tự động — **xem Risk (list)** | **Không** |
| 8 | `feature/library/.../components/ContinueReadingCarousel.kt:245` | `ReadingProgressBar(progress = percent)` | wavy tự động — **xem Risk (list)** | **Không** |
| 9 | `feature/statistics/.../components/TopBooksReadingList.kt:238` | `ReadingProgressBar(progress = stat.progressPercentage)` | wavy tự động — **xem Risk (list)** | **Không** |
| 10 | `feature/statistics/.../StatisticsScreen.kt:167` | `DelayedLoadingIndicator()` | giữ nguyên | **Không** |
| 11 | `core/engine/.../cbz/CbzReaderComponent.kt:245` | `ExpressiveContainedLoadingIndicator(modifier = Modifier.size(40.dp))` | giữ nguyên | **Không** |
| 12 | `feature/reader/.../components/TtsFloatingPlayer.kt:170-180` | `CircularProgressIndicator(progress = …)` 48dp, `strokeWidth = 2.5.dp`, màu theo play/pause | **giữ determinate, KHÔNG wavy** | **Không** |
| 13 | `feature/statistics/.../components/HeroReadingGoalCard.kt:198-206` | `LinearProgressIndicator(progress = animatedProgress)` 16dp, `trackColor = Transparent`, bọc trong `Box` có `PillShape` + border | tiến trình **riêng**; wavy 16dp là **tuỳ chọn** | **Cân nhắc** |
| 14 | `feature/reader/.../components/FloatingReaderToolbar.kt:97-112` | `Slider(value, onValueChange, onValueChangeFinished, valueRange, colors = SliderDefaults.colors(...))` | `WavyReaderSlider(...)` | **Có** |

### Luật không được vi phạm

- **`ReaderOpeningOverlay` không được thành determinate.** Ba mốc mở sách (0.15/0.55/0.85) là *mốc công
  việc*, không phải % thời gian. Journal `260919-1610:30-31` đã ghi đây là lỗi cũ; đừng tái phát.
- **Không chuyển loading indicator → determinate trong cùng một quá trình.** Một quá trình = một cấu hình.
- **`TtsFloatingPlayer` không wavy.** Vòng 48dp, stroke 2.5dp. Spec nói nút quá nhỏ thì **bỏ track**,
  chứ không phải thêm sóng. Sóng ở bán kính đó là nhiễu thị giác.
- **`HeroReadingGoalCard` không gộp cấu hình với `ReadingProgressBar`.** Mục tiêu đọc trong ngày ≠ tiến
  độ một cuốn sách. Nếu làm wavy ở đây, phải là quyết định riêng, **không** dùng `ReadingProgressBar`.
- **Không thêm pull-to-refresh.** Room `Flow` tự cập nhật.
- **Không đổi hợp đồng seek.** `ReaderScreen.kt:305-320` phải nhận đúng một lệnh seek khi nhả tay.

## Architecture

### Luồng seek của reader (phải giữ nguyên từng bước)

```
WavyReaderSlider (trong FloatingReaderToolbar)
  onValueChange         -> sliderPosition (state cục bộ)   [mỗi frame, KHÔNG seek]
  onValueChangeFinished -> onSeekToPage(sliderPosition.toInt())   [một lần]
        │
        ▼
ReaderScreen.kt:305  onSeekToPage = { page ->
        haptic.performHapticFeedback(TextHandleMove)               (:306)
        if (format == CBZ)  viewModel.onPageChanged(page, total)   (:307-308)
        else                nav.go(publication.readingOrder[page]) (:310-318)
   }
```

Ba thứ **không** được đổi trong phase này: tín hiệu haptic `:306`, nhánh CBZ `:307-308`, nhánh EPUB
`:310-318`. Nếu đổi hợp đồng "một lần khi nhả tay", EPUB sẽ gọi `nav.go()` liên tục lúc kéo → reader
nhảy loạn. Đây là failure mode cụ thể nhất của phase này.

Lưu ý sẵn có (không thuộc phạm vi phase, đừng sa đà sửa): journal `260919-1610:103-105` ghi thanh trượt
CBZ **không thật sự nhảy trang** vì `initialPageIndex` chỉ dùng lúc khởi tạo `rememberLazyListState`.
Đó là bug riêng. Phase này chỉ đổi *hình dạng* slider. Nếu muốn sửa, mở plan khác — đừng trộn, vì lẫn
vào đây thì không biết hồi quy đến từ đâu.

### `sliderPosition` state hiện có

```
FloatingReaderToolbar.kt:56-58
  var sliderPosition by remember(currentPage, progressPercentage) {
      mutableFloatStateOf(if (totalPages > 0) currentPage.toFloat() else progressPercentage * 100f)
  }
```

Giữ nguyên `remember(key)` này. Nó là cách thanh trượt đồng bộ lại khi trang đổi bằng cách khác (lật
trang, TOC). Bỏ key = thanh trượt đứng im khi người dùng lật trang.

Cạnh dễ sai: `valueRange = 0f..(totalPages - 1).toFloat()` (`:103`) được bọc trong
`if (totalPages > 1)` (`:83`), nên `totalPages == 1` không bao giờ tạo range rỗng. **Giữ điều kiện
`totalPages > 1` đó.** Nếu bỏ, `0f..0f` sẽ làm `SliderState` chia cho 0.

### `LibraryScreen` — vì sao sửa

```
LibraryScreen.kt:340-349
  if (currentImport is ImportState.Importing && currentImport.total > 1) {
      CircularProgressIndicator(
          progress = { (current.toFloat() / total).coerceIn(0f, 1f) },
          modifier = Modifier.size(28.dp),
          strokeWidth = 3.dp,                                    // token đặt tại call site
          color = MaterialTheme.colorScheme.primary,             // token đặt tại call site
          trackColor = MaterialTheme.colorScheme.secondaryContainer // token đặt tại call site
      )
  } else { ExpressiveLoadingIndicator(modifier = Modifier.size(28.dp)) }
```

Đúng về spec (đo được → determinate) nhưng **sai về DRY**: ba token hình dạng nằm ở call site. Đổi sang
`ReadingProgressRing` để token về `:core:ui`. Nếu Phase 2 đi nhánh B (không có circular wavy),
`ReadingProgressRing` vẫn đáng làm — nó gom token, chỉ là chưa wavy.

Giữ nguyên nhánh `if/else` và text `:321-325`. Logic chọn chỉ báo theo "đo được / không đo được" đã đúng.

### `HeroReadingGoalCard` — mặc định không sửa

`HeroReadingGoalCard.kt:186-207` là một `Box` có `PillShape` + `border` + `background`
`surfaceContainerLowest`, bên trong là `LinearProgressIndicator` 16dp với `trackColor = Transparent`
(track do `Box` vẽ). Đổi sang wavy sẽ phá layout đó: sóng biên độ vài dp trong khung 16dp có border sẽ
tràn hoặc bị cắt.

Quyết định: **không sửa**, trừ khi kiểm tay cho thấy nó đẹp. Lý do KISS — đây là một tiến trình riêng,
đang hoạt động, và không nằm trong khoảng trống mà người dùng nêu (wavy cho *tiến độ đọc* + slider).
Nếu vẫn muốn làm, điều kiện bắt buộc: **không** dùng `ReadingProgressBar`, và ghi rõ trong
`docs/core-ui.md` rằng đây là cấu hình riêng có chủ ý.

## Related Code Files

| File | Sửa gì |
| --- | --- |
| `feature/reader/src/main/java/com/booxbook/feature/reader/components/FloatingReaderToolbar.kt:26-27` | Xoá import `Slider`, `SliderDefaults`; thêm `com.booxbook.core.ui.component.WavyReaderSlider` |
| `feature/reader/.../FloatingReaderToolbar.kt:97-112` | `Slider(...)` → `WavyReaderSlider(...)`; bỏ khối `colors = SliderDefaults.colors(...)` (token về `:core:ui`) |
| `feature/reader/.../FloatingReaderToolbar.kt:56-58, 83, 103` | **Giữ nguyên** `remember(key)`, `if (totalPages > 1)`, `valueRange` |
| `feature/library/src/main/java/com/booxbook/feature/library/LibraryScreen.kt:33` | Xoá import `CircularProgressIndicator` nếu không còn dùng |
| `feature/library/.../LibraryScreen.kt:341-349` | → `ReadingProgressRing(progress = …, modifier = Modifier.size(28.dp))` |
| `feature/statistics/.../components/HeroReadingGoalCard.kt:198-206` | Chỉ sửa nếu chọn làm wavy 16dp (mặc định: không) |
| `feature/reader/.../ReaderScreen.kt:300-325` | **Chỉ đọc.** Xác nhận hợp đồng seek không đổi. |
| `feature/library/.../components/BentoBookCard.kt:237`, `ContinueReadingCarousel.kt:245`, `detail/BookDetailScreen.kt:655`, `feature/statistics/.../TopBooksReadingList.kt:238` | **Không sửa.** Chỉ kiểm bằng mắt sau khi Phase 2 đổi ruột. |
| `feature/reader/.../components/TtsFloatingPlayer.kt:170-180` | **Không sửa.** |
| `core/engine/.../cbz/CbzReaderComponent.kt:245` | **Không sửa.** Nhưng phải compile lại `:core:engine`. |

## Implementation Steps

1. **Xác nhận "không cần sửa" là thật.** Compile 4 module trước khi sửa gì:
   ```powershell
   ./gradlew :core:engine:compileDebugKotlin :feature:library:compileDebugKotlin `
             :feature:reader:compileDebugKotlin :feature:statistics:compileDebugKotlin
   ```
   Nếu đỏ → Phase 2 đã đổi chữ ký; quay lại Phase 2 bước 7 thay vì vá ở đây.

2. **`FloatingReaderToolbar.kt` — đổi slider.** Thay `:97-112`:
   ```kotlin
   WavyReaderSlider(
       value = sliderPosition,
       valueRange = 0f..(totalPages - 1).toFloat(),
       onValueChange = { sliderPosition = it },
       onValueChangeFinished = { onSeekToPage(sliderPosition.toInt()) },
       modifier = Modifier
           .weight(1f)
           .padding(horizontal = 12.dp),
   )
   ```
   Bỏ `colors = SliderDefaults.colors(...)`: `inactiveTrackColor` cũ là `surfaceVariant`, còn
   `:core:ui` dùng `secondaryContainer`. Đây là **đổi màu có chủ ý** để khớp `ReadingProgressBar` — ghi
   vào commit message, đừng để người review tưởng là sơ suất.
   Xoá import `Slider`/`SliderDefaults` (`:26-27`), thêm import `WavyReaderSlider`.

3. **Kiểm chiều cao toolbar.** Thumb dạng vạch đứng (~44dp) cao hơn thumb tròn 20dp cũ. `Surface` ở
   `:69-75` có `padding(horizontal = 18.dp, vertical = 12.dp)` và `Row` `:84-87`
   `verticalAlignment = CenterVertically`. Nếu toolbar cao lên rõ rệt, giới hạn chiều cao thumb trong
   `WavyReaderSlider` (Phase 2) — **đừng** thêm `Modifier.height()` ở call site, vì đó là token hình
   dạng và sẽ tái phát đúng lỗi DRY đang dọn.

4. **`LibraryScreen.kt` — đổi sang `ReadingProgressRing`.** Thay `:341-349`. Giữ nguyên biểu thức
   `(current.toFloat() / total).coerceIn(0f, 1f)` và nhánh `if/else`. Xoá import
   `CircularProgressIndicator` `:33` nếu không còn chỗ nào dùng (grep trước khi xoá).

5. **`HeroReadingGoalCard` — quyết định rồi ghi lại.** Mặc định **không sửa**. Nếu thử wavy 16dp thì
   dựng riêng, không qua `ReadingProgressBar`, và kiểm sóng không tràn khỏi `PillShape` border ở `:192-196`.
   Dù chọn gì cũng phải ghi lý do vào commit message — Phase 4 cần nó cho `docs/core-ui.md` mục 1.7.

6. **Đi lại toàn bộ 14 dòng của bảng call site** và đánh dấu từng dòng. Mục tiêu là chứng minh **không
   regress**, đặc biệt:
   - `ReaderOpeningOverlay.kt:243` vẫn là loading indicator, **không** determinate.
   - `TtsFloatingPlayer.kt:170` vẫn determinate, **không** wavy.
   - `DelayedLoadingIndicator` vẫn ở `BookDetailScreen.kt:186` và `StatisticsScreen.kt:167`.

7. **Compile + test toàn vùng:**
   ```powershell
   ./gradlew :core:ui:compileDebugKotlin :core:engine:compileDebugKotlin `
             :feature:reader:compileDebugKotlin :feature:library:compileDebugKotlin `
             :feature:statistics:compileDebugKotlin
   ./gradlew :feature:reader:testDebugUnitTest :feature:library:testDebugUnitTest `
             :feature:statistics:testDebugUnitTest --rerun-tasks
   ```
   Test hiện có đã biết: reader 18 + feedback 6 + tap-zones 12, library 7 + detail 7
   (`docs/journals/260919-1610-…md:93-96`). Con số phải **không giảm**.

8. **Kiểm tay trên thiết bị** — phần này không thể thay bằng CI:
   - Kéo slider trong reader ở **cả CBZ và EPUB**: sóng chạy, nhả tay seek đúng **một** lần.
   - Nhập nhiều sách (chọn ≥ 2 tệp): vòng determinate tiến theo `current/total`.
   - Grid thư viện với nhiều card có % đọc: **cuộn và xem có tụt frame** (rủi ro chính, xem dưới).
   - 4 preset nền reader: sóng và thumb đủ tương phản.
   - TalkBack trên slider: đọc được giá trị, tăng/giảm được.

9. **Nếu Phase 2 hoãn việc xoá bản port**, xoá bây giờ (sau khi bước 7 xanh) — theo Phase 2 bước 4.

## Success Criteria

- [x] `FloatingReaderToolbar.kt` dùng `WavyReaderSlider`; không còn import `Slider`/`SliderDefaults`.
- [x] Seek vẫn đúng: CBZ → `viewModel.onPageChanged`, EPUB → `nav.go(link)`, và chỉ **một** lần khi nhả tay.
- [x] Haptic `:306` vẫn bắn khi seek.
- [x] `if (totalPages > 1)` vẫn bảo vệ `valueRange`; `totalPages == 1` không crash.
- [x] `LibraryScreen.kt` dùng `ReadingProgressRing`; không còn `strokeWidth`/`color`/`trackColor` đặt tại call site.
- [x] 4 chỗ `ReadingProgressBar` là wavy **mà file không bị sửa** (bằng chứng DRY — kiểm bằng `git diff --stat`).
- [x] `ReaderOpeningOverlay` vẫn loading indicator, không determinate.
- [x] `TtsFloatingPlayer` vẫn determinate ring, không wavy, màu vẫn theo play/pause.
- [x] `DelayedLoadingIndicator` vẫn ở `BookDetailScreen.kt:186` và `StatisticsScreen.kt:167`.
- [x] `HeroReadingGoalCard` vẫn là tiến trình riêng, không gộp `ReadingProgressBar`; quyết định đã ghi lại.
- [x] Không thêm pull-to-refresh ở đâu.
- [x] 5 module compile xanh; số test pass **không giảm**.
- [x] Cuộn grid thư viện đầy card **không tụt frame thấy được**.
- [x] Không có `@OptIn(ExperimentalMaterial3Expressive…)` trong `feature/` hay `core/engine/`.

## Risk Assessment

| Rủi ro | Khả năng | Tác động | Giảm thiểu |
| --- | --- | --- | --- |
| Slider seek mỗi frame → EPUB gọi `nav.go()` liên tục, reader nhảy loạn | Trung bình | **Cao** | Giữ đúng hợp đồng: `onValueChange` chỉ sửa state cục bộ, `onValueChangeFinished` mới seek. Kiểm tay trên EPUB ở bước 8 — đây là bug sẽ không hiện ra ở unit test. |
| Sóng animate trên grid nhiều card → tụt frame | **Cao** | **Cao** | Rủi ro số một của cả plan. Đo bằng cuộn tay ở bước 8. Nếu tụt: biên độ 0 (phẳng) cho thanh trong list, chỉ wavy ở `BookDetailScreen`; hoặc dừng animation khi item ra khỏi viewport. Sửa ở `:core:ui`, **không** rải tham số ra call site. |
| Thumb vạch đứng làm toolbar cao lên, đè nút hành động | Trung bình | Trung bình | Bước 3 kiểm riêng. Giới hạn chiều cao trong `:core:ui`, không ở call site. |
| Đổi `inactiveTrackColor` `surfaceVariant` → `secondaryContainer` bị hiểu là hồi quy | Trung bình | Thấp | Ghi rõ trong commit message + `docs/reader.md` ở Phase 4. |
| Sóng trên nền 4 preset reader không đủ tương phản | Trung bình | Trung bình | Kiểm tay cả 4 preset. `ReaderOpeningOverlay.kt:243` đã có tiền lệ suy màu từ độ sáng nền — dùng lại cách đó nếu cần. |
| Sửa lan sang bug seek CBZ (`initialPageIndex`) | Trung bình | Trung bình | Quy định cứng: phase này chỉ đổi *hình dạng*. Bug seek CBZ là follow-up ở Phase 4. |
| `totalPages == 1` → `valueRange` rỗng → chia 0 | Thấp | Cao | `if (totalPages > 1)` ở `:83` đã chặn. Đừng bỏ nó. Nếu sửa, thêm guard trong `WavyReaderSlider`. |
| Xoá import `CircularProgressIndicator` trong khi còn chỗ khác dùng | Thấp | Thấp | Grep trước khi xoá. Compiler bắt được. |
| Trợ năng: thumb tự vẽ mất semantics | Thấp | Cao | `Slider` giữ semantics; thumb chỉ là phần vẽ. Kiểm TalkBack ở bước 8. |

### Backwards compatibility

Không có dữ liệu lưu, không có schema, không có API public bị chạm. Hợp đồng duy nhất là **hành vi seek**
mà người dùng đã quen: kéo → xem số trang đổi → nhả → nhảy trang. `WavyReaderSlider` phải giữ đúng
chuỗi đó, kể cả tín hiệu haptic. Chữ số trang `:88-95` và `:114-121` không đổi.

### Rollback

Ba file độc lập, revert từng file được:

```powershell
git checkout feature/reader/src/main/java/com/booxbook/feature/reader/components/FloatingReaderToolbar.kt
git checkout feature/library/src/main/java/com/booxbook/feature/library/LibraryScreen.kt
git checkout feature/statistics/src/main/java/com/booxbook/feature/statistics/components/HeroReadingGoalCard.kt
```

Revert riêng slider mà giữ wavy progress bar là hợp lệ — hai thứ không phụ thuộc nhau. Nếu muốn tắt
wavy ở mọi nơi mà giữ slider: sửa **một** file `ReadingProgressBar.kt`, không phải 4 call site.
