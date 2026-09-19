---
title: "M3 Expressive loading and wavy progress"
description: "Đưa loading indicator + progress indicator của app về đúng Material 3 Expressive: thử dùng API chính thức (LoadingIndicator, LinearWavyProgressIndicator) qua một spike toolchain có giới hạn, và làm wavy slider cho thanh lật trang trong reader — không thêm AAR ExpressiveLab."
status: completed
priority: P2
effort: "8–16h"
branch: "master"
tags: [material3, expressive, core-ui, loading-indicator, progress-indicator, wavy, reader, library, statistics]
blockedBy: []
blocks: []
created: "2026-09-19T17:23:10.752Z"
createdBy: "ck:plan"
completed: "2026-09-20T01:46:00.000Z"
source: skill
---

# M3 Expressive loading and wavy progress

## Overview

Phiên trước đã đưa app về đúng **bảng chọn chỉ báo theo thời gian chờ** của Material 3 (xem
`docs/journals/260919-1610-m3-loading-and-progress-indicators.md`), nhưng bằng một **bản port nội bộ**
(`core/ui/src/main/java/com/booxbook/core/ui/component/ExpressiveLoadingIndicator.kt`) vì lúc đó kết
luận là không nâng được Material3. Còn hai khoảng trống so với yêu cầu:

1. **Chưa có wavy** — cả `LinearWavyProgressIndicator` và `CircularWavyProgressIndicator`.
2. **Thanh lật trang trong reader vẫn là `Slider` chuẩn** (`FloatingReaderToolbar.kt:97`), chưa phải
   wavy slider kiểu ExpressiveLab.

Plan này đóng hai khoảng trống đó. Điều quan trọng nhất: **kết luận "không nâng được Material3" của
journal cũ là sai** — nó chỉ thử `1.4.0-alpha18` rồi nâng cả BOM lên Compose 1.9 và kéo theo chuỗi
lifecycle/savedstate. Phase 1 đã được kiểm chứng trước bằng AAR metadata thật (xem bảng dưới), và có
một đường đi hẹp nhưng sạch.

### Kết quả kiểm chứng trước (đã tra `aar-metadata.properties` thật trên Google Maven)

| Artifact | `minCompileSdk` | `minAndroidGradlePluginVersion` | Có `LoadingIndicator` | Có `*WavyProgressIndicator` |
| --- | --- | --- | --- | --- |
| `material3:1.3.1` (đang dùng) | 35 | 8.6.0 | Không | Không |
| `material3:1.4.0` (**stable**) | 35 | 8.6.0 | **Không** | **Không** |
| `material3:1.5.0-alpha10` | **35** | **8.6.0** | **Có** | **Có** |
| `material3:1.5.0-alpha18` | 35 | 8.6.0 | Có | Có |
| `material3:1.5.0-alpha20`+ | **37** | **9.1.0** | Có | Có |
| `compose.ui:ui-android:1.8.2 / 1.9.3 / 1.10.2 / 1.11.0-beta02` | 35 | 8.6.0 | — | — |

Ba điều rút ra, và chúng đổi hình dạng của plan:

- **`material3:1.4.0` stable là bẫy.** Nó có `LoadingIndicatorTokens` nhưng **không** có
  `LoadingIndicatorKt`, và **không có một class `Wavy*` nào**. Nâng lên 1.4.0 stable không được gì cả.
- **`material3:1.5.0-alpha10` là mục tiêu.** Có đủ `LoadingIndicatorKt`, `LoadingIndicatorDefaults`,
  `WavyProgressIndicatorKt`, `WavyProgressIndicatorDefaults`; chỉ cần `compileSdk 35` + `AGP 8.6.0`
  (dự án đang 35 / 8.7.3) và `kotlin-stdlib 2.0.21` (dự án 2.1.0, tức **mới hơn**, không có vấn đề
  metadata). Nó khai báo `ui-android:1.8.2` — một bước nhảy nhỏ từ Compose 1.7.6 hiện tại.
- **`1.5.0-alpha20` là vách đá.** Từ đó trở lên cần `compileSdk 37` + `AGP 9.1.0`. ExpressiveLab dùng
  `1.5.0-alpha22`, nên việc **không** thêm AAR của nó là quyết định đúng, không phải né tránh.

### Phát hiện thứ hai: wavy slider không cần nâng gì cả

**Material3 không hề có `WavySlider`** — đã kiểm: 0 class khớp `WavySlider` trong `1.5.0-alpha18`.
Nhưng `material3:1.3.1` **đang dùng** đã có overload:

```
Slider(state: SliderState, modifier, enabled, colors, interactionSource,
       thumb: @Composable (SliderState) -> Unit,
       track: @Composable (SliderState) -> Unit)
```

Nghĩa là wavy slider = `Slider` chuẩn + **chỉ thay slot `track`** bằng một Canvas vẽ sóng. Gesture,
`SliderState`, semantics trợ năng, haptic — lấy free từ Material3. Không cần AAR ExpressiveLab, không
cần `mahozad/wavy-slider`, và **không phụ thuộc kết quả Phase 1**.

### Quyết định kiến trúc (chốt, không mở lại)

| Quyết định | Chốt | Vì sao |
| --- | --- | --- |
| Thêm `com.github.ermohdamaan:expressivelab` | **Không** | Cần AGP 9.4 / Kotlin 2.2 / `compileSdk 37` / material3 1.5.0-alpha22. Dự án ở AGP 8.7.3 / Kotlin 2.1.0 / SDK 35 / JDK 17. |
| Dùng ExpressiveLab làm tham chiếu thị giác + ghi công MIT | **Có** | Cùng cách đã làm ở `docs/journals/260918-1400-m3-activity-heatmap-expressive-lab.md`. |
| `ExpressiveWavySlider` của ExpressiveLab cho % đọc trên card | **Không** | Nó là **slider (nhập liệu)**, không phải progress indicator. Dùng sai vai trò là vi phạm spec. |
| Nâng AGP 9.4 / Kotlin 2.2 / JDK 21 | **Không** | Ngoài phạm vi plan này. |
| Nâng `compileSdk` | **Không cần** | Đã kiểm: `1.5.0-alpha10` chỉ cần 35. |
| Nâng `material3` lên `1.4.0` stable | **Không** | Không có `LoadingIndicator`, không có wavy. Vô nghĩa. |
| Đích của spike | `material3:1.5.0-alpha10` | Bậc cao nhất còn ở `compileSdk 35` + `AGP 8.6.0` mà vẫn có đủ API cần dùng. |
| Thêm pull-to-refresh | **Không** | Dữ liệu là Room `Flow`, tự cập nhật. Không có thao tác "kéo để làm mới" để gắn chỉ báo. |
| Wrapper nằm ở đâu | `:core:ui` | Để `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` không rải khắp feature. |
| Wavy slider | Tự làm trong `:core:ui` | `Slider(track = …)` trên `material3:1.3.1` đã đủ. |

### Nguyên tắc

- **YAGNI** — không làm wavy slider đa năng có `RangeSlider`, không làm indeterminate wavy ở chỗ không
  ai dùng. Đúng những call site đã liệt kê.
- **KISS** — nếu Phase 1 thành công, wrapper là *delegate mỏng* và bản port ~390 dòng bị xoá. Nếu thất
  bại, wavy là một `Modifier.drawBehind` nhỏ, **không** copy hình học private của Material3, **không**
  copy nguyên source ExpressiveLab.
- **DRY** — một tiến trình = một cấu hình. `ReadingProgressBar` là chỗ duy nhất định nghĩa hình dạng
  thanh % đọc; call site không tự đặt `strokeCap`/`height`/`trackColor`.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Toolchain spike for official M3 APIs](./phase-01-toolchain-spike-for-official-m3-apis.md) | ✅ Completed |
| 2 | [Core UI loading and wavy primitives](./phase-02-core-ui-loading-and-wavy-primitives.md) | ✅ Completed |
| 3 | [Apply indicators across feature call sites](./phase-03-apply-indicators-across-feature-call-sites.md) | ✅ Completed |
| 4 | [Docs attribution and verification](./phase-04-docs-attribution-and-verification.md) | ✅ Completed (device check PENDING) |

Phase 1 → 2 → 3 → 4 tuần tự. Phase 2 có hai nhánh (A: API chính thức có; B: giữ port + Canvas) do
Phase 1 quyết định, nên **không** chạy song song Phase 1 và 2.

Ngoại lệ duy nhất có thể song song: phần **wavy slider track** của Phase 2 không phụ thuộc Phase 1
(dùng `Slider(track = …)` của `material3:1.3.1`). Nếu muốn rút ngắn, có thể làm nó ngay.

## Sở hữu file (không hai phase nào sửa cùng file)

| Phase | File được sửa |
| --- | --- |
| 1 | `gradle/libs.versions.toml`, `gradle.properties`, `core/ui/build.gradle.kts` (spike; revert nếu no-go) |
| 2 | `core/ui/src/main/java/com/booxbook/core/ui/component/ExpressiveLoadingIndicator.kt`, `.../ReadingProgressBar.kt`, `.../WavyReaderSlider.kt` (mới), `core/ui/build.gradle.kts` (chỉ dòng `graphics-shapes`) |
| 3 | `feature/library/.../LibraryScreen.kt`, `feature/reader/.../FloatingReaderToolbar.kt`, `feature/statistics/.../HeroReadingGoalCard.kt` |
| 4 | `docs/core-ui.md`, `docs/reader.md`, `docs/library.md`, `docs/journals/260920-*.md`, `README.md` (tạo mới nếu chưa có) |

`core/ui/build.gradle.kts` bị cả Phase 1 và 2 chạm — chấp nhận vì hai phase tuần tự, không song song.
Phase 1 chạm khối `dependencies` (thêm override material3), Phase 2 chạm đúng một dòng (xoá
`graphics-shapes`).

## Acceptance criteria

- [ ] Phase 1 ghi **go/no-go rõ ràng** vào phase file: version đã thử, lỗi nguyên văn nếu fail, và
      trạng thái cuối của `gradle/libs.versions.toml` (đã nâng hay đã revert).
- [ ] Thanh % đọc là **wavy** ở `BentoBookCard`, `ContinueReadingCarousel`, `BookDetailScreen`,
      `TopBooksReadingList` — hoặc, nếu Phase 1 no-go, wavy do `:core:ui` tự vẽ với **cùng token**
      (4dp, `primary`, `secondaryContainer`, stop indicator 4dp).
- [ ] Đổi wavy chỉ sửa **một** file `ReadingProgressBar.kt`; không call site nào phải sửa tham số hình
      dạng. (Bằng chứng DRY.)
- [ ] Thanh lật trang trong reader là **wavy slider**: track hoạt động vẽ sóng, track không hoạt động
      phẳng, thumb dạng vạch đứng kiểu M3 Expressive.
- [ ] `FloatingReaderToolbar` vẫn seek đúng: CBZ gọi `viewModel.onPageChanged`, EPUB gọi `nav.go(link)`
      (`ReaderScreen.kt:305-320` không đổi hành vi).
- [ ] Không regress bảng chờ: `ReaderOpeningOverlay` vẫn là loading indicator **không** phải determinate
      giả; `DelayedLoadingIndicator` vẫn 200ms; `TtsFloatingPlayer` vẫn ring determinate **không** wavy.
- [ ] `HeroReadingGoalCard` vẫn là tiến trình **riêng** (mục tiêu ngày), không gộp cấu hình với
      `ReadingProgressBar`.
- [ ] Ghi công MIT của `JustForPixel-ExpressiveLab` có trong `README.md` **và** `docs/core-ui.md`.
- [ ] Compile sạch: `:core:ui`, `:core:engine`, `:feature:reader`, `:feature:library`,
      `:feature:statistics` — `compileDebugKotlin`.
- [ ] Unit test hiện có xanh: `:feature:reader:testDebugUnitTest`, `:feature:library:testDebugUnitTest`,
      `:feature:statistics:testDebugUnitTest`, `:core:tts:test`.
- [ ] Kiểm tay trên thiết bị: sóng chạy mượt, tương phản đủ trên **4 preset nền reader**, wavy slider
      kéo không giật.

## Rollback

Mỗi phase revert độc lập:

- **Phase 1** — `git checkout gradle/libs.versions.toml gradle.properties core/ui/build.gradle.kts`.
  Không file Kotlin nào bị sửa ở phase này, nên revert là tuyệt đối an toàn.
- **Phase 2** — wrapper giữ **nguyên chữ ký public**. Revert = `git checkout core/ui/`, call site
  không cần sửa theo.
- **Phase 3** — mỗi call site là một hunk độc lập; revert từng file được.
- **Phase 4** — chỉ docs, revert vô hại.

Điểm không thể revert dễ: nếu Phase 2 **xoá** bản port `ExpressiveLoadingIndicator.kt` (nhánh A) thì
quay lại phải lấy từ git. Vì vậy Phase 2 quy định xoá port **chỉ sau khi** Phase 3 compile xanh.

## Test matrix

| Cấp | Cái gì | Cách |
| --- | --- | --- |
| Compile | 5 module bị ảnh hưởng | `./gradlew :core:ui:compileDebugKotlin :core:engine:compileDebugKotlin :feature:reader:compileDebugKotlin :feature:library:compileDebugKotlin :feature:statistics:compileDebugKotlin` |
| Unit | ViewModel test hiện có (không được đỏ) | `./gradlew :feature:reader:testDebugUnitTest :feature:library:testDebugUnitTest :feature:statistics:testDebugUnitTest --rerun-tasks` |
| Unit (mới) | Chỉ nếu xuất hiện logic map giá trị | ví dụ clamp NaN, `page → progress` của wavy slider |
| Thủ công | Sóng, tương phản 4 preset nền reader, kéo slider | thiết bị thật |

**Không** viết Compose UI test cho wrapper. Chúng là code vẽ; assert pixel là test giòn, tốn công bảo
trì, không bắt được lỗi thật. Brutal honesty: phase này về bản chất được nghiệm thu bằng mắt.

## Dependencies

Không có plan nào chặn hoặc bị chặn:

- `plans/260917-2257-native-ebook-reader` — **completed**.
- `plans/260918-1045-reading-statistics-tab` — **completed**.
- `plans/260918-0930-kindle-tap-zones-and-page-turn-effects` — không đụng chỉ báo tải/tiến trình.

Plan này **chồng lấn với các thay đổi chưa commit** trong working tree từ phiên trước (`core/ui/.../
ExpressiveLoadingIndicator.kt`, `.../ReadingProgressBar.kt`, `docs/core-ui.md`, và các call site).
Coi các file đó là **điểm bắt đầu**, không phải việc phải làm lại. Nên commit chúng trước khi bắt đầu
Phase 1 để diff của plan này đọc được.

## Handoff

```
/ck:cook d:\MyProjects\BooxBook\plans\260920-0016-m3-expressive-loading-progress\plan.md
```

---

## Sync-Back Summary (2026-09-20 01:46 UTC)

**Overall Status: ✅ 4 of 4 phases completed. Code-level work is done.**

### Completed Deliverables (All Checkboxes ✅)

**Phase 1 (Toolchain Spike — GO):**
- Proved `composeBom 2025.05.01` + `material3:1.5.0-alpha10` is achievable within constraints (compileSdk 35, AGP 8.7.3, Kotlin 2.1.0, JDK 17)
- lifecycle resolved to 2.9.0 (acceptable, no savedstate-compose breakage)
- All 4 official APIs compile: `LoadingIndicator`, `ContainedLoadingIndicator`, `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`
- Decision record locked: Phase 2 nhánh A (delegate to official APIs)

**Phase 2 (Core UI Primitives — Nhánh A):**
- `ExpressiveLoadingIndicator` refactored to ~60 dòng delegate (xoá 300+ dòng bản port)
- `ReadingProgressBar` thành wavy LinearWavyProgressIndicator
- `ReadingProgressRing` mới (CircularWavyProgressIndicator) cho nhập sách
- `WavyReaderSlider` mới (Slider + wavy track, ~40dp wavelength, 3dp amplitude)
- Animation policy tuân thủ (tắt animation hệ thống → sóng đứng yên)
- Call site không sửa: **bằng chứng DRY**

**Phase 3 (Feature Integration):**
- `FloatingReaderToolbar`: `Slider` → `WavyReaderSlider` ✅
- `LibraryScreen`: `CircularProgressIndicator(tự token)` → `ReadingProgressRing` ✅
- 4 chỗ `ReadingProgressBar` giờ wavy mà file không bị touch: DRY verified
- Seek hành vi không đổi: CBZ/EPUB + onValueChangeFinished chỉ 1x khi nhả tay
- No regress: `ReaderOpeningOverlay` still loading, `TtsFloatingPlayer` still determinate ring, `DelayedLoadingIndicator` vẫn 200ms

**Phase 4 (Docs & Attribution):**
- `README.md` tạo mới: ghi công `JustForPixel-ExpressiveLab` (MIT) làm tham chiếu thiết kế
- `docs/core-ui.md`: update decision record + call site + "intentional differences"
- `docs/reader.md`, `docs/library.md`: updated
- Journal `260920-0035-m3-wavy-progress-and-reader-slider.md`: ghi kết quả thực tế
- Compiler ✅, Unit test (reader 18 + feedback 6, library 7 + detail 7, statistics, tts) ✅

### Open Item (Honest)

- **Device visual check: PENDING** — không giả vờ đã verify sóng chạy mượt trên 4 preset nền reader hay frame rate trên grid 20+ card
  - Điều kiện để đóng: kéo slider, grid scroll, sóng trừng trần, tương phản 4 preset, TalkBack ✓

### No Regressions Verified

| Item | Before | After | Status |
|------|--------|-------|--------|
| Toolchain | AGP 8.7.3, Kotlin 2.1.0, JDK 17, compileSdk 35 | Unchanged | ✅ |
| Module count | 5 compile, `:core:engine` CMake/desugaring | All xanh | ✅ |
| Unit test count | 18+6 (reader/feedback), 7+7 (library), 4 (statistics/tts) | Count not decreased | ✅ |
| Progress bar visual | 4dp thick, primary on secondaryContainer, 4dp stop indicator | Still 4dp/primary/secondaryContainer/stop | ✅ |
| Loading indicator size | 24/28/40/48dp call site variants | Size preserved via delegation | ✅ |
| Slider seek behavior | CBZ `onPageChanged`, EPUB `nav.go()`, one-shot on release | **Unchanged** | ✅ |
| Haptic feedback | Slide seek → TextHandleMove | Still bắn | ✅ |
| TTS ring | 48dp, determinate, 2.5dp stroke, play/pause color | **Kept non-wavy** | ✅ |
| HeroReadingGoalCard | 16dp linear inside PillShape | **Kept separate** (not wavy) | ✅ |
| Delay threshold | `DelayedLoadingIndicator` 200ms | Unchanged | ✅ |

### Why This Plan Matters (And What Not to Do)

1. **Alpha10 is _the_ sweet spot**, not 1.4.0 stable (which has no `LoadingIndicator` / `Wavy*` — confirmed via AAR metadata) and not alpha20+ (which force compileSdk 37 + AGP 9.1)
2. **Material3 has no `WavySlider`** → `Slider(track = …)` is the only way; it works on the baseline 1.3.1
3. **Wavy is thin layer now**: single file `WavyReaderSlider.kt`, token once in `ReadingProgressBar` — not scattered across 4 call site
4. **Not adding ExpressiveLab AAR** was the right call: it sits at alpha22, behind the compileSdk 37 wall

### Why "Device Check Still PENDING" Matters

Docs say so because it's true. Code compiles, tests pass, BUT pixel-level verification (sóng smooth trên grid dây, tương phản 4 background, không tụt frame) can only be done by eye on real device. Honesty > overstatement.

**Next step for user:** Device smoke test (15 min) → close device check ✓ → plan is 100% shipped.

