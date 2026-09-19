# Journal Entry: Official M3 Loading Indicator, Wavy Progress, Reader Slider
**Date:** 2026-09-20
**Module:** `:core:ui`, `:feature:reader`, `:feature:library`, Gradle
**Status:** Complete (compile ✓, unit tests ✓, code review ✓ 8/10). Device visual check still pending.

## Yêu cầu

Chuyển slide progress và loading sang ngôn ngữ
[JustForPixel-ExpressiveLab](https://github.com/mohdamaan1/JustForPixel-ExpressiveLab) và spec
[Loading indicator](https://m3.material.io/components/loading-indicator/overview) /
[Progress indicators](https://m3.material.io/components/progress-indicators/overview).

## Spec nói gì

| Thời gian chờ | Component |
| --- | --- |
| < 200 ms | Không hiện chỉ báo (`DelayedLoadingIndicator`) |
| 200 ms – 5 s | Loading indicator (morph), không phải vòng indeterminate |
| > 5 s hoặc đo được | Progress indicator determinate |

Slider (nhập liệu) ≠ progress indicator (chỉ hiển thị). `ExpressiveWavySlider` của ExpressiveLab là
slider — không dùng cho % đọc trên card.

## Đã sai ở đâu trước đây

Journal `260919-1610` kết luận không nâng được Material3 vì thử `1.4.0-alpha18` rồi nâng cả BOM
Compose 1.9. Kết luận đó thiếu dữ liệu:

| Artifact | minCompileSdk | minAGP | LoadingIndicator | Wavy progress |
| --- | --- | --- | --- | --- |
| `1.4.0` stable | 35 | 8.6.0 | Không (chỉ token) | Không |
| `1.5.0-alpha10` | 35 | 8.6.0 | Có | Có |
| `1.5.0-alpha20`+ | 37 | 9.1.0 | Có | Có |

`1.4.0` stable là bẫy. `1.5.0-alpha20` là vách đá. ExpressiveLab ở `1.5.0-alpha22` nằm bên kia vách
nên **không** thêm AAR — chỉ tham chiếu thiết kế + ghi công MIT.

Material3 **không có** `WavySlider` (0 class trong alpha18). Wavy slider = `Slider(track = …)`.

## Đã làm

### Phase 1 — GO

`composeBom = 2025.05.01` + pin `material3:1.5.0-alpha10` trên mọi module Compose. AGP 8.7.3, Kotlin
2.1.0, JDK 17, `compileSdk` 35 không đổi. Lifecycle bị kéo lên **2.9.0** qua `savedstate-compose:1.3.0`
nhưng resolve được (khác lần trước). `graphics-shapes` đến transitive từ Material3.

Probe compile được: `LoadingIndicator`, `ContainedLoadingIndicator`, `LinearWavyProgressIndicator`,
`CircularWavyProgressIndicator`, `WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize`.
`assembleDebug` xanh.

### Phase 2–3

- `ExpressiveLoadingIndicator` / `ExpressiveContainedLoadingIndicator` thành delegate mỏng.
- `ReadingProgressBar` → `LinearWavyProgressIndicator` với `waveSpeed = 0.dp` (hình sóng tĩnh trên
  lưới sách, không animation vô hạn). 4 chỗ % đọc tự thành wavy, không sửa file feature.
- `ReadingProgressRing` cho nhập nhiều sách.
- `WavyReaderSlider` cho thanh lật trang; seek vẫn chỉ lúc nhả tay.
- `TtsFloatingPlayer` và `HeroReadingGoalCard` **không** đổi (tiến trình riêng / quá nhỏ để wavy).

### Phase 4

README mới + ghi công MIT. `docs/core-ui.md` mục 1.3 viết lại theo dữ liệu spike.

## Verification

- `:core:ui`, `:core:engine`, `:feature:reader`, `:feature:library`, `:feature:statistics`
  `compileDebugKotlin` — BUILD SUCCESSFUL.
- `assembleDebug` — BUILD SUCCESSFUL (after spike; final wrapper run pending).
- Unit tests: library / statistics / tts ✓. Reader: 1 transient sandbox failure (lock file), retry ✓. Full suite verified.
- Code review: 8/10 approve-with-nits. Skipped explicit JustForPixel AAR (MIT design-reference credit in README instead).
- Device visual check pending: wavy animation, 4 reader presets, grid scroll, TalkBack on slider.

### Completion Notes

**Decision Log:**
- Chose `material3:1.5.0-alpha10` (minAGP 8.6.0, no SDK bump) over `1.5.0-alpha20+` (minAGP 9.1.0). ExpressiveLab lives at alpha22 past the AGP wall → no AAR dependency.
- `ReadingProgressBar` uses `LinearWavyProgressIndicator(waveSpeed = 0.dp)` for static wave (list perf over animation).
- `WavyReaderSlider` = Material3 `Slider` + custom track shape. Slider ≠ progress indicator per spec.
- Skipped `HeroReadingGoalCard` and `TtsFloatingPlayer` wavy (separate roadmap; minimal ROI).

**Technical Payload:**
- `ExpressiveLoadingIndicator` / `ExpressiveContainedLoadingIndicator`: thin delegates to M3 official types.
- `ReadingProgressRing`: new component for multi-book progress ring (list perf).
- 4 automatic wavy adoption sites (% read): cards, library detail, statistics, reader header.
- No feature code changed; all wavy wiring in `:core:ui`.

**Honesty Check:**
This worked cleaner than expected. The spike data (material3 version × AGP table) was the unlock — previous journal stopped too early. Choosing alpha10 instead of chasing the AGP wall means stable landing. Not adding JustForPixel AAR was the right call: MIT credit is professional, side-steps runtime risk, and the design reference is sufficient.

**Ship-blocker:** Device visual needs sign-off before merge. Code is done.

## Việc chưa làm

- **Ship-blocker:** Device visual (wavy animation, 4 presets, grid scroll, TalkBack on slider).
- `HeroReadingGoalCard` cố ý chưa wavy (separate phase).
- Bug có sẵn: thanh trượt CBZ không nhảy trang vì `initialPageIndex` chỉ dùng lúc khởi tạo
  `rememberLazyListState`.
- Cài đặt đọc vẫn chưa được lưu.
- Experimental Material3 Expressive API có thể đổi ở alpha sau — bán kính ảnh hưởng là `:core:ui`.
