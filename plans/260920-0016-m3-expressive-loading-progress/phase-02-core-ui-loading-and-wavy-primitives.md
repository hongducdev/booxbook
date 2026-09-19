---
phase: 2
title: "Core UI loading and wavy primitives"
status: completed
priority: P2
effort: "3–6h"
dependencies: [1]
completed_date: "2026-09-20"
---

# Phase 2: Core UI loading and wavy primitives

## Overview

Dựng bộ primitive trong `:core:ui` để call site **không bao giờ** phải viết
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` hay tự đặt token hình dạng. Sau phase này, toàn bộ
kiến thức về "chỉ báo trông như thế nào" nằm trong đúng ba file.

Phase này có **hai nhánh**, do Decision record của Phase 1 quyết định:

- **Nhánh A (Phase 1 = GO)** — wrapper thành *delegate mỏng* sang API chính thức. Bản port 392 dòng
  trong `ExpressiveLoadingIndicator.kt` bị **xoá**. Đây là kết cục mong muốn: ít code hơn, đúng spec
  hơn, do Google bảo trì.
- **Nhánh B (Phase 1 = NO-GO)** — giữ bản port, tự vẽ wavy bằng Compose Canvas. Nhiều code hơn, phải
  tự bảo trì.

Phần **wavy slider** (`WavyReaderSlider`) **không thuộc nhánh nào** — nó chạy được trên
`material3:1.3.1` đang dùng, bất kể Phase 1 ra sao. Lý do ở mục Architecture.

### Brutal honesty

Nhánh B là code vẽ tự viết, không có test tự động thật sự, và sẽ lệch khỏi spec khi Google chỉnh
token. Nếu Phase 1 GO thì đừng vì "đã viết rồi" mà giữ Canvas — xoá. Nếu Phase 1 NO-GO thì nhánh B là
**nợ kỹ thuật có ý thức**, phải ghi rõ trong `docs/core-ui.md` ở Phase 4 rằng đây là bản tạm và điều
kiện để xoá là gì.

## Requirements

### API public sau phase này (chữ ký **không đổi** giữa nhánh A và B)

Đây là ràng buộc cứng. Call site ở Phase 3 phải compile được với cả hai nhánh, nếu không thì Phase 1
NO-GO sẽ lan thành viết lại Phase 3.

```kotlin
// ExpressiveLoadingIndicator.kt — giữ nguyên chữ ký hiện có, không đổi
@Composable fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier, color: Color = …)
@Composable fun ExpressiveContainedLoadingIndicator(
    modifier: Modifier = Modifier, containerColor: Color = …, indicatorColor: Color = …, containerShape: Shape = CircleShape)
@Composable fun DelayedLoadingIndicator(
    modifier: Modifier = Modifier, delayMillis: Long = InstantWaitThresholdMillis, color: Color = …)
const val InstantWaitThresholdMillis = 200L

// ReadingProgressBar.kt — giữ nguyên chữ ký, đổi ruột
@Composable fun ReadingProgressBar(
    progress: Float, modifier: Modifier = Modifier, color: Color = …, trackColor: Color = …)

// ReadingProgressBar.kt — mới, cho ca đo được nhưng dạng vòng (nhập nhiều sách)
@Composable fun ReadingProgressRing(
    progress: Float, modifier: Modifier = Modifier, color: Color = …, trackColor: Color = …)

// WavyReaderSlider.kt — mới
@Composable fun WavyReaderSlider(
    value: Float, valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true)
```

`ReadingProgressRing` là primitive mới duy nhất được thêm, và chỉ vì `LibraryScreen.kt:341` đang tự
đặt `strokeWidth`/`trackColor` tại call site — vi phạm "một tiến trình, một cấu hình". Không thêm
`ReadingProgressRing` nếu Phase 3 quyết định giữ nguyên chỗ đó (xem Phase 3).

### Ràng buộc token (cả hai nhánh phải giống nhau về mắt)

| Thứ | Giá trị | Nguồn |
| --- | --- | --- |
| Độ dày thanh % đọc | 4dp | Mặc định `LinearProgressIndicator` M3 |
| Màu hoạt động | `primary` | `ProgressIndicatorDefaults.linearColor` |
| Màu track | `secondaryContainer` | `ProgressIndicatorDefaults.linearTrackColor` |
| Stop indicator | 4dp | Spec: bắt buộc khi track không đủ 3:1 với nền |
| Loading indicator container | 48dp, hình 38dp | `LoadingIndicatorTokens` |
| Ngưỡng chờ tức thời | 200ms | Spec |
| Bước sóng wavy (nếu nhánh B) | lấy từ `WavyProgressIndicatorDefaults` nếu có, nếu không thì bước sóng 40dp / biên độ 3dp | Xem Risk |

### Không làm (YAGNI)

- Không `RangeSlider` wavy — không call site nào cần.
- Không wavy indeterminate cho linear — chỗ không đo được đã dùng loading indicator.
- Không `ExpressiveWavySlider` đa năng. Chỉ đúng một slider: thanh lật trang reader.
- Không đổi `TtsFloatingPlayer` sang wavy (spec: quá nhỏ thì bỏ track, chứ không thêm sóng).
- Không tạo `:core:ui` API mới cho `HeroReadingGoalCard` — nó là tiến trình khác, giữ riêng.

## Architecture

### Wavy slider: vì sao không cần Phase 1, và không cần thư viện

Đã kiểm bằng `javap` trên `material3-android:1.3.1` (**phiên bản dự án đang dùng**):

```
SliderKt.Slider(SliderState, Modifier, boolean, SliderColors, MutableInteractionSource,
                Function3<SliderState,…> thumb,
                Function3<SliderState,…> track)
SliderDefaults.Track(SliderState, Modifier, SliderColors, boolean)
SliderDefaults.Track-4EFweAY(SliderState, Modifier, boolean, SliderColors,
                drawStopIndicator, drawTick, thumbTrackGapSize, trackInsideCornerSize)
SliderDefaults.getTrackStopIndicatorSize-D9Ej5fM()
```

Nên wavy slider = `Slider` chuẩn, **chỉ thay slot `track`**:

```
WavyReaderSlider
  └─ Slider(state = rememberSliderState(...),
            thumb = { M3 thumb dạng vạch đứng },
            track = { state -> Canvas vẽ: sóng ở phần hoạt động, đường phẳng ở phần còn lại })
```

Lấy free từ Material3: xử lý gesture kéo/tap, `SliderState` + `onValueChangeFinished`, semantics trợ
năng (`ProgressBarRangeInfo`, hành động tăng/giảm), hỗ trợ RTL, vùng chạm tối thiểu. Đó là phần khó và
dễ làm sai nhất của một slider — và cũng là lý do **không** tự dựng slider từ `pointerInput`.

Đã kiểm thêm: **Material3 không có `WavySlider`** (0 class khớp `WavySlider` trong `1.5.0-alpha18`).
Nên đây không phải "tạm bợ chờ API chính thức" — đây là cách duy nhất, kể cả sau khi nâng.

### Hình học sóng (dùng cho track slider, và cho nhánh B của progress bar)

Một hàm private duy nhất, dùng chung, không nhân bản:

```
wavePath(width, height, amplitude, wavelength, phase) : Path
  - sin rời rạc hoá bằng quadraticBezierTo, bước ~wavelength/8
  - phase chạy bằng rememberInfiniteTransition (tween LinearEasing, ~2000ms/chu kỳ)
  - amplitude giảm dần về 0 ở ~8dp cuối phần hoạt động để nối phẳng với track, không cắt gãy
```

Ba điều bắt buộc:

1. **Không copy hình học private của Material3.** Không port `WavyProgressIndicatorKt` /
   `LinearWavyProgressModifiers`. Hàm trên là sin đơn giản tự viết.
2. **Không copy nguyên source ExpressiveLab.** ExpressiveLab là **tham chiếu thị giác** (nhìn để khớp
   biên độ/bước sóng cho giống), ghi công MIT ở Phase 4. Không dán code.
3. **Tôn trọng `InfiniteAnimationPolicy`.** `ExpressiveLoadingIndicator.kt:203-213` đã làm đúng việc
   này; animation sóng phải theo cùng khuôn, nếu không thì bật "tắt animation" trong trợ năng sẽ để
   sóng chạy mãi ở nền.

### Nhánh A: ruột của từng wrapper

```
ExpressiveLoadingIndicator      -> androidx.compose.material3.LoadingIndicator
ExpressiveContainedLoadingIndicator -> androidx.compose.material3.ContainedLoadingIndicator
DelayedLoadingIndicator         -> giữ nguyên (logic delay 200ms là của app, không phải của M3)
ReadingProgressBar              -> LinearWavyProgressIndicator(progress = { … })
ReadingProgressRing             -> CircularWavyProgressIndicator(progress = { … })
WavyReaderSlider                -> Slider(track = wavy) — KHÔNG đổi, M3 không có WavySlider
```

Kèm theo: xoá toàn bộ vùng `// Tokens & hình dạng` → hết file
(`ExpressiveLoadingIndicator.kt:249-392`), xoá các import `androidx.graphics.shapes.*`
(`:44-49`), và xoá `implementation(libs.androidx.graphics.shapes)` ở `core/ui/build.gradle.kts:39`
**nếu** Decision record ghi là material3 đã kéo `graphics-shapes` transitive.

`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` đặt ở **file level** của hai file wrapper. Không
đặt ở call site. Đó là toàn bộ lý do tồn tại của phase này.

### Nhánh B: ruột của từng wrapper

```
ExpressiveLoadingIndicator      -> giữ bản port hiện tại, không sửa
ReadingProgressBar              -> LinearProgressIndicator(progress) + Modifier.drawWithContent vẽ sóng
                                   thay cho phần track hoạt động; giữ stop indicator 4dp
ReadingProgressRing             -> CircularProgressIndicator(progress) thường (KHÔNG wavy)
WavyReaderSlider                -> giống nhánh A
```

Quyết định trong nhánh B: **circular wavy bị bỏ**. Vẽ sóng trên cung tròn với biên độ đúng spec là
phần khó nhất của cả wavy family, và nó chỉ phục vụ **một** call site (`LibraryScreen.kt:341`, nhập
nhiều sách) mà người dùng thấy trong vài giây. Chi phí/lợi ích không hợp. Ghi thành follow-up ở Phase 4.

### Data flow

```
progress: Float (0f..1f, có thể NaN)
   │  coerce: NaN -> 0f, clamp 0f..1f      (đã có: ReadingProgressBar.kt:33)
   ▼
ReadingProgressBar
   ├─ nhánh A: LinearWavyProgressIndicator(progress = { p })
   └─ nhánh B: LinearProgressIndicator(progress = { p }) + drawWithContent(wavePath)
   ▼
Vẽ: 4dp, primary trên secondaryContainer, stop indicator 4dp

value: Float (chỉ số trang), valueRange 0f..(totalPages-1)
   ▼
WavyReaderSlider -> SliderState -> Slider(track = wavy)
   ├─ onValueChange        : cập nhật state cục bộ (kéo mượt, KHÔNG seek mỗi frame)
   └─ onValueChangeFinished : gọi callback -> Phase 3 nối vào onSeekToPage
```

Điểm dễ sai: **không** gọi seek trong `onValueChange`. `ReaderScreen.kt:305-320` xử lý seek bằng
`nav.go(link)` cho EPUB — gọi mỗi frame sẽ làm reader nhảy liên tục. Hành vi hiện tại
(`FloatingReaderToolbar.kt:99-102`) đã đúng; giữ nguyên hợp đồng đó.

## Related Code Files

| File | Thao tác |
| --- | --- |
| `core/ui/src/main/java/com/booxbook/core/ui/component/ExpressiveLoadingIndicator.kt` | Nhánh A: thu về ~60 dòng delegate, xoá `:249-392` và import `graphics.shapes` `:44-49`. Nhánh B: không sửa. |
| `core/ui/src/main/java/com/booxbook/core/ui/component/ReadingProgressBar.kt` | Đổi ruột sang wavy; **giữ nguyên** chữ ký `:25-30` và xử lý NaN `:33`. Thêm `ReadingProgressRing` nếu Phase 3 cần. |
| `core/ui/src/main/java/com/booxbook/core/ui/component/WavyReaderSlider.kt` | **Tạo mới.** `Slider(track = wavy)` + hàm `wavePath` private. |
| `core/ui/build.gradle.kts:39` | Nhánh A: xoá `implementation(libs.androidx.graphics.shapes)` nếu transitive đã có. Nhánh B: giữ. |
| `gradle/libs.versions.toml:23,35` | `graphicsShapes = "1.0.1"` + alias — nhánh A xoá nếu không module nào còn dùng (grep trước). |
| `core/ui/src/main/java/com/booxbook/core/ui/animation/SpringPhysics.kt` | Nguồn hằng số spring; dùng lại cho animation sóng thay vì đặt số mới. |
| `plans/260920-0016-m3-expressive-loading-progress/phase-01-…md` | Decision record — **đọc trước khi viết dòng code nào.** |

## Implementation Steps

1. **Đọc Decision record của Phase 1.** Nếu trống → dừng, Phase 2 bị chặn. Chọn nhánh A hoặc B và ghi
   lựa chọn vào commit message.

2. **Làm `WavyReaderSlider.kt` trước** (không phụ thuộc nhánh, nên làm được ngay và giảm rủi ro tiến độ):
   - Tạo `SliderState` bằng `remember`, nối `value`/`valueRange`/`onValueChangeFinished`.
   - `track = { state -> }` vẽ bằng `Canvas`:
     - phần **hoạt động** (`0 → state.value`): `wavePath` với biên độ 3dp, bước sóng 40dp, `primary`,
       `stroke` 4dp, `StrokeCap.Round`;
     - phần **còn lại**: đường phẳng 4dp `secondaryContainer`;
     - chừa khoảng hở ~6dp hai bên thumb (`thumbTrackGapSize` của M3 Expressive);
     - biên độ tắt dần về 0 ở ~8dp trước thumb để không cắt gãy.
   - `thumb = { }` dạng vạch đứng bo tròn (~4dp × 44dp), `primary` — đúng kiểu M3 Expressive slider.
   - `phase` chạy bằng `rememberInfiniteTransition`, `tween(2000, easing = LinearEasing)`, và **bọc
     trong `InfiniteAnimationPolicy`** theo đúng khuôn `ExpressiveLoadingIndicator.kt:203-213`.
   - Nếu `enabled == false` hoặc `valueRange` rỗng → biên độ 0 (sóng phẳng), không animate.

3. **Đổi ruột `ReadingProgressBar`** theo nhánh:
   - **A**: thay `LinearProgressIndicator` bằng `LinearWavyProgressIndicator`, giữ nguyên
     `progress`/`color`/`trackColor` và xử lý NaN ở `:33`. Dùng chữ ký **thật** từ Decision record.
   - **B**: giữ `LinearProgressIndicator`, phủ `Modifier.drawWithContent` dùng lại `wavePath` từ bước 2.
     Giữ stop indicator 4dp — nếu vẽ đè làm mất nó thì vẽ lại bằng tay, đừng bỏ (spec bắt buộc).
   - Cả hai nhánh: chữ ký public **không đổi**. Kiểm bằng cách compile `:feature:library` mà không sửa
     file feature nào.

4. **Nhánh A — dọn bản port** (chỉ sau khi bước 3 compile xanh):
   - Xoá `ExpressiveLoadingIndicator.kt:249-392` (tokens, `IndeterminateIndicatorPolygons`, `morphSequence`,
     `calculateScaleFactor`, `processPath`, `Morph.toPath`) và `LoadingIndicatorImpl` `:148-247`.
   - Xoá import `androidx.graphics.shapes.*` (`:44-49`) và các import animation không còn dùng.
   - **Giữ nguyên** `DelayedLoadingIndicator` `:123-142` và `InstantWaitThresholdMillis` `:145` — đây là
     logic của app (bảng chờ M3), không phải component M3. Không có gì chính thức thay được.
   - Giữ nguyên KDoc giải thích *vì sao* mỗi wrapper tồn tại; chỉ đổi đoạn "vì sao là bản port".
   - `grep` `graphics.shapes` toàn repo. Nếu không còn chỗ nào, xoá dòng `core/ui/build.gradle.kts:39`
     và alias ở `libs.versions.toml:23,35`.

5. **Nhánh A — kiểm kích thước thật.** API chính thức có token size riêng; `Modifier.size(24.dp)` /
   `28.dp` / `40.dp` / `48.dp` ở các call site phải vẫn ra kích thước cũ. Nếu `LoadingIndicator` chính
   thức không nhận size qua `modifier` như bản port (`ExpressiveLoadingIndicator.kt:220-226` áp
   `modifier` **trước** `.size()` nên modifier thắng), thì wrapper phải tự áp `.size()` — nếu không,
   4 call site sẽ đột nhiên đổi kích thước. Kiểm trực quan, không suy đoán.

6. **Compile riêng `:core:ui`** trước khi chạm feature:
   ```powershell
   ./gradlew :core:ui:compileDebugKotlin
   ```

7. **Chứng minh DRY.** Compile `:feature:library` + `:feature:statistics` **mà không sửa file nào trong
   chúng**. Nếu phải sửa → chữ ký đã bị đổi, quay lại bước 3.
   ```powershell
   ./gradlew :feature:library:compileDebugKotlin :feature:statistics:compileDebugKotlin
   ```

8. **Thêm `@Preview`** cho `WavyReaderSlider` và `ReadingProgressBar` trong `debug` source set (dùng
   `debugImplementation(libs.compose.ui.tooling)` đã có ở `core/ui/build.gradle.kts:41`). Đây là cách
   duy nhất kiểm hình dạng mà không cần build cả app — đáng cho code vẽ.

9. **Unit test: chỉ nếu có logic map.** Nếu `WavyReaderSlider` phải chuyển `page ↔ value` hoặc clamp
   `valueRange` rỗng (`totalPages == 1` → `0f..0f`), tách thành hàm pure và test nó. Ngược lại **không
   viết test** — assert pixel là test giòn.

## Success Criteria

- [x] Nhánh (A hoặc B) đã chọn theo Decision record của Phase 1 và ghi trong commit message.
- [x] Chữ ký public của `ExpressiveLoadingIndicator`, `ExpressiveContainedLoadingIndicator`,
      `DelayedLoadingIndicator`, `InstantWaitThresholdMillis`, `ReadingProgressBar` **không đổi**.
- [x] `:feature:library` và `:feature:statistics` compile xanh **mà không sửa file nào trong chúng**.
- [x] `ReadingProgressBar` vẽ wavy, vẫn 4dp / `primary` / `secondaryContainer` / stop indicator 4dp.
- [x] `WavyReaderSlider` tồn tại, kéo được, `onValueChangeFinished` chỉ bắn **một lần** khi nhả tay
      (không bắn mỗi frame).
- [x] Animation sóng bọc trong `InfiniteAnimationPolicy` — tắt animation hệ thống thì sóng đứng yên.
- [x] `progress` NaN vẫn ra 0, không vỡ hình.
- [x] Không có `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` nào ngoài `:core:ui`
      (`grep -r ExperimentalMaterial3Expressive feature/ core/engine/` → rỗng).
- [x] Nhánh A: `ExpressiveLoadingIndicator.kt` **ngắn hơn 100 dòng**; không còn import
      `androidx.graphics.shapes`; `graphics-shapes` đã xoá khỏi `core/ui/build.gradle.kts` nếu transitive.
- [x] Không copy source từ `WavyProgressIndicatorKt` của Material3 hay từ ExpressiveLab.
- [x] `@Preview` cho `WavyReaderSlider` + `ReadingProgressBar` render được trong Android Studio.

## Risk Assessment

| Rủi ro | Khả năng | Tác động | Giảm thiểu |
| --- | --- | --- | --- |
| Đổi chữ ký public → Phase 3 phình ra thành viết lại | Trung bình | Cao | Bước 7 là cổng kiểm cứng: compile feature mà không sửa feature. |
| `LinearWavyProgressIndicator` chính thức không nhận `trackColor`/`strokeCap` như `LinearProgressIndicator` → lệch hình so với `HeroReadingGoalCard` | Cao | Trung bình | Dùng chữ ký **thật** từ Decision record, không dùng doc alpha28. Nếu thiếu tham số, đặt qua `WavyProgressIndicatorDefaults` chứ đừng hard-code. |
| Nhánh A làm mất stop indicator 4dp (wavy có mặc định khác) | Trung bình | Trung bình | So sánh trực quan với ảnh trước/sau. Spec bắt buộc có stop indicator; thiếu thì thêm lại tường minh. |
| Kích thước loading indicator đổi sau khi delegate (24/28/40/48dp) | Trung bình | Trung bình | Bước 5 kiểm riêng. Wrapper tự áp `.size()` nếu API chính thức không cho modifier thắng. |
| Sóng animate liên tục trên danh sách dài (`BentoBookCard` × N) → tụt frame | **Cao** | **Cao** | Đây là rủi ro lớn nhất của phase. Một `rememberInfiniteTransition` **mỗi thanh** trên grid 20+ card là rất tệ. Giảm thiểu: vẽ trong `drawWithCache`/`drawBehind` (không recompose), và **cân nhắc biên độ 0 (phẳng) cho thanh trong list**, chỉ wavy ở `BookDetailScreen`. Đo bằng Layout Inspector / `--profile` trước khi chốt. |
| Wavy 4dp quá nhỏ để thấy sóng → công cốc | Trung bình | Trung bình | Kiểm tay sớm (bước 8, `@Preview`). Nếu 4dp không đọc được sóng, giữ 4dp (spec, DRY) và ghi nhận wavy chỉ có tác dụng ở thanh dày — đừng lặng lẽ nâng lên 8dp ở một chỗ rồi phá luật "một tiến trình một cấu hình". |
| Thumb tự vẽ mất vùng chạm 48dp → khó kéo, hỏng trợ năng | Trung bình | Cao | `Slider` giữ `minimumInteractiveComponentSize`; thumb chỉ là phần **vẽ**. Không tự làm gesture. Kiểm bằng TalkBack. |
| Nhánh B: tự vẽ rồi lệch dần khỏi spec | Chắc chắn (nếu B) | Trung bình | Ghi rõ trong `docs/core-ui.md` (Phase 4) rằng đây là bản tạm + điều kiện xoá. Chỉ một file chứa hình học. |
| Xoá bản port rồi Phase 3 phát hiện vấn đề, không lùi được | Thấp | Cao | Quy định: **xoá port chỉ sau khi Phase 3 compile xanh**. Nếu chưa chắc, để việc xoá sang cuối Phase 3. |

### Backwards compatibility

Không có bề mặt tương thích ngược thật: `:core:ui` là module nội bộ, không phát hành, không có dữ liệu
lưu, không có schema. Hợp đồng duy nhất cần giữ là **chữ ký Kotlin** cho 4 module gọi nó — và đó chính
là điều kiện nghiệm thu ở bước 7.

Một lưu ý: `:core:engine` cũng phụ thuộc `:core:ui` (`core/engine/build.gradle.kts:54`) và
`CbzReaderComponent.kt:245` dùng `ExpressiveContainedLoadingIndicator`. Đừng quên module này khi kiểm
compile — nó có CMake nên build lâu và hay bị bỏ sót.

### Rollback

`git checkout core/ui/` và xoá `WavyReaderSlider.kt`. Vì chữ ký public không đổi, call site không cần
lùi theo. Nếu đã xoá bản port thì lấy lại từ git history của
`core/ui/src/main/java/com/booxbook/core/ui/component/ExpressiveLoadingIndicator.kt`.
