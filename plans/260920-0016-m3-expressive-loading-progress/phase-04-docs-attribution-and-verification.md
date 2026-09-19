---
phase: 4
title: "Docs attribution and verification"
status: completed
priority: P2
effort: "1–2h"
dependencies: [3]
completed_date: "2026-09-20"
notes: "Device visual check PENDING — do not overstate completion"
---

# Phase 4: Docs attribution and verification

## Overview

Đóng plan: cập nhật tài liệu cho khớp code đã đổi, **ghi công MIT** cho
`JustForPixel-ExpressiveLab`, chạy đủ bộ verification, và viết journal.

Hai việc trong phase này là **bắt buộc về pháp lý/đạo đức**, không phải "nice to have":

1. **Ghi công MIT.** ExpressiveLab được dùng làm tham chiếu thị giác/chuyển động cho wavy slider và
   phong cách Expressive. Giấy phép MIT yêu cầu giữ thông báo bản quyền. Repo **hiện chưa có
   `README.md`** (đã kiểm: không tồn tại) — nên phase này phải **tạo mới** nó, không phải "cập nhật".
2. **Ghi rõ điều gì cố ý khác spec, và vì sao.** `docs/core-ui.md` mục 1.7 đã có khuôn này. Không có nó
   thì lần sau sẽ có người "sửa" lại đúng những chỗ đang cố ý khác.

## Requirements

### Ghi công (không thoả hiệp)

| Chỗ | Nội dung |
| --- | --- |
| `README.md` (**tạo mới**) | Mục `## Credits` / `## Acknowledgements`: nêu `JustForPixel-ExpressiveLab` (https://github.com/mohdamaan1/JustForPixel-ExpressiveLab), giấy phép **MIT**, và nói rõ dùng làm **tham chiếu thiết kế**, **không** phân phối lại code |
| `docs/core-ui.md` | Một mục ghi công + nói rõ ranh giới: cái gì là port từ Material3 (Apache 2.0), cái gì lấy cảm hứng từ ExpressiveLab (MIT), cái gì tự viết |
| `docs/journals/260920-*.md` | Journal mới ghi nguồn tham chiếu |

Phải trung thực về mức độ: nếu wavy slider chỉ **nhìn** ExpressiveLab để khớp biên độ/bước sóng thì viết
đúng như vậy. Nếu có đoạn nào thật sự bắt nguồn từ code của họ thì phải nói, kèm bản quyền MIT. Ghi công
mơ hồ tệ hơn không ghi.

Song song, giữ phần ghi công **Apache 2.0** đang có: `ExpressiveLoadingIndicator.kt:309-310` ghi "port
từ `androidx.compose.material3.internal` (ShapeUtil.kt), Apache License 2.0". Nếu Phase 2 đi nhánh A và
xoá bản port, dòng đó biến mất **đúng** — nhưng phải kiểm là không còn code port nào sót lại mà mất ghi
công.

### Tài liệu phải khớp code (không phải khớp dự định)

| File | Cập nhật gì |
| --- | --- |
| `docs/core-ui.md:19-23` | Bảng thời gian chờ — nếu vẫn đúng thì **đừng sửa**, chỉ xác nhận |
| `docs/core-ui.md:60-82` (mục 1.3) | Mục "Vì sao là bản cài đặt nội bộ": **phải viết lại**. Lý lẽ hiện tại (`:67-69`: "1.5.0-alpha25 cần compileSdk 37 + AGP 9.1") đúng cho alpha25 nhưng **sai với alpha10** — alpha10 chỉ cần compileSdk 35 / AGP 8.6.0. Thay bằng dữ liệu thật của Phase 1. |
| `docs/core-ui.md:99-117` (mục 1.5) | `ReadingProgressBar` giờ là wavy; ghi token + lý do |
| `docs/core-ui.md:119-131` (mục 1.6) | Bảng "Nơi đang dùng": thêm `WavyReaderSlider`, đổi dòng `LibraryScreen` sang `ReadingProgressRing` |
| `docs/core-ui.md:133-143` (mục 1.7) | "Chủ ý giữ khác spec": thêm (a) TTS ring không wavy vì quá nhỏ, (b) `HeroReadingGoalCard` là tiến trình riêng, (c) M3 **không có** `WavySlider` nên slider là tự dựng bằng slot `track` |
| `docs/core-ui.md:147-157` (mục 2) | Bảng file: thêm `component/WavyReaderSlider.kt` |
| `docs/core-ui.md:160-169` (mục 3) | Cập nhật lệnh verify + version Compose thật |
| `docs/reader.md` | Thanh lật trang giờ là wavy slider; ghi việc đổi `inactiveTrackColor` `surfaceVariant` → `secondaryContainer` |
| `docs/library.md` | Chỉ báo nhập sách dùng `ReadingProgressRing` |
| `docs/journals/260920-<HHMM>-m3-wavy-progress-and-reader-slider.md` | Journal mới |

### Journal phải ghi những điều này

Journal là nơi lần sau người ta tra. Bắt buộc có:

- **Kết quả Phase 1 dưới dạng bảng version + `minCompileSdk` + `minAGP`.** Đây là thứ journal
  `260919-1610` thiếu, và vì thiếu nên kết luận "không nâng được" đã đứng vững sai gần một phiên.
- **`material3:1.4.0` stable là bẫy**: có `LoadingIndicatorTokens` nhưng không có `LoadingIndicatorKt`,
  không có class `Wavy*` nào. Ghi lại để không ai nâng lên 1.4.0 rồi tưởng sẽ có wavy.
- **`1.5.0-alpha20` là vách đá** (`compileSdk 37` + `AGP 9.1.0`), và ExpressiveLab ở `alpha22` nên nằm
  bên kia vách — đó là bằng chứng số cho quyết định không thêm AAR.
- **Material3 không có `WavySlider`**; wavy slider là `Slider(track = …)` tự vẽ, và cách đó chạy được
  ngay trên `material3:1.3.1`.
- Nếu Phase 1 NO-GO: **lỗi nguyên văn từng bậc**.

## Architecture

Phase này không có kiến trúc code. Nó có kiến trúc **thông tin**, và luật là: mỗi sự thật sống ở **một**
chỗ.

```
README.md         -> ghi công + giấy phép (cửa vào của repo)
docs/core-ui.md   -> hợp đồng primitive: API, token, bảng chờ, chủ ý khác spec
docs/reader.md    -> hành vi đặc thù reader (slider, seek)
docs/library.md   -> hành vi đặc thù library (chỉ báo nhập sách)
docs/journals/    -> lịch sử: đã thử gì, hỏng ra sao, quyết định vì sao
plans/…/phase-01  -> Decision record: dữ liệu thô của spike
```

Không nhân bản bảng thời gian chờ sang `reader.md` / `library.md`. Chúng **trỏ** về `docs/core-ui.md`.

## Related Code Files

| File | Thao tác |
| --- | --- |
| `README.md` | **Tạo mới** (chưa tồn tại) — ghi công MIT + Apache 2.0 |
| `docs/core-ui.md:60-82` | Viết lại mục 1.3 theo dữ liệu Phase 1 |
| `docs/core-ui.md:99-117, 119-131, 133-143, 147-157, 160-169` | Cập nhật mục 1.5 – 3 |
| `docs/reader.md` | Mục thanh lật trang |
| `docs/library.md` | Mục chỉ báo nhập sách |
| `docs/journals/260920-<HHMM>-m3-wavy-progress-and-reader-slider.md` | Journal mới, theo khuôn `260919-1610-…md` |
| `plans/260920-0016-m3-expressive-loading-progress/phase-01-…md` | Nguồn: Decision record |
| `core/ui/src/main/java/com/booxbook/core/ui/component/ExpressiveLoadingIndicator.kt:309-310` | Kiểm ghi công Apache 2.0 còn đúng sau Phase 2 |
| `core/ui/src/main/java/com/booxbook/core/ui/component/WavyReaderSlider.kt` | Thêm KDoc nêu nguồn tham chiếu |

## Implementation Steps

1. **Chạy đủ bộ verification trước khi viết docs.** Viết docs dựa trên dự định rồi phát hiện build đỏ là
   lãng phí.
   ```powershell
   ./gradlew :core:ui:compileDebugKotlin :core:engine:compileDebugKotlin `
             :feature:reader:compileDebugKotlin :feature:library:compileDebugKotlin `
             :feature:statistics:compileDebugKotlin
   ./gradlew :feature:reader:testDebugUnitTest :feature:library:testDebugUnitTest `
             :feature:statistics:testDebugUnitTest :core:tts:testDebugUnitTest --rerun-tasks
   ./gradlew assembleDebug
   ```
   `:core:tts` nằm trong danh sách vì working tree có thay đổi chưa commit ở `TtsEngineWrapper.kt`,
   `TtsState.kt`, `TtsEngineWrapperTest.kt` — đừng để plan này bị quy tội cho test đỏ có sẵn. Nếu
   `:core:tts` đỏ **trước** khi plan bắt đầu, ghi lại là đỏ từ trước.

2. **Tạo `README.md`.** Tối thiểu: tên app, các module, toolchain (AGP/Kotlin/Compose/JDK thật sau Phase 1),
   và mục ghi công:

   ```markdown
   ## Credits

   - **[JustForPixel-ExpressiveLab](https://github.com/mohdamaan1/JustForPixel-ExpressiveLab)** (MIT) —
     dùng làm **tham chiếu thiết kế** cho ngôn ngữ Material 3 Expressive của app: bento grid, activity
     heatmap, và biên độ/bước sóng của thanh trượt wavy trong reader. Không phân phối lại code của repo
     này; không phụ thuộc AAR của nó.
   - **Material Components / Jetpack Compose Material 3** (Apache License 2.0) — token và chuyển động
     của loading/progress indicator.
   ```

   Không phóng đại: nếu chỉ tham chiếu thị giác thì viết "tham chiếu thiết kế", đừng viết "based on".

3. **Viết lại `docs/core-ui.md` mục 1.3.** Đây là mục quan trọng nhất vì lý lẽ hiện tại đã lạc hậu:
   - Nếu Phase 1 **GO**: đổi tiêu đề mục thành đại ý "vì sao giờ dùng được component chính thức", ghi
     bảng version + `minCompileSdk`/`minAGP`, ghi rằng bản port đã bị xoá và `graphics-shapes` đã bỏ.
   - Nếu Phase 1 **NO-GO**: giữ mục nhưng **thay lý lẽ** — bỏ lập luận "1.5.0-alpha25 cần compileSdk 37"
     (đúng nhưng không liên quan) và thay bằng lỗi thật của bậc 1/2/3. Kèm **điều kiện xoá port**.

4. **Cập nhật mục 1.5 – 1.7 + bảng file + mục kiểm thử** theo bảng ở Requirements. Mục 1.7 phải nêu đủ
   ba chủ ý khác spec (TTS ring, `HeroReadingGoalCard`, `Slider` tự vẽ vì M3 không có `WavySlider`).

5. **Cập nhật `docs/reader.md` và `docs/library.md`** — ngắn, trỏ về `docs/core-ui.md` cho token. Nêu rõ
   việc đổi `inactiveTrackColor` để người review sau không tưởng là hồi quy.

6. **Viết journal** `docs/journals/260920-<HHMM>-m3-wavy-progress-and-reader-slider.md` theo khuôn
   `260919-1610-…md`: Yêu cầu → Spec nói gì → Đã sai ở đâu trước đây → Đã làm → Verification → Việc chưa
   làm. Bắt buộc có 5 điểm ở mục Requirements.

7. **Kiểm tay trên thiết bị và ghi kết quả vào journal** (không được viết "đã verify" mà chưa chạy):
   - sóng chạy mượt ở thanh % đọc và slider;
   - tương phản đủ trên **4 preset nền reader**;
   - `DelayedLoadingIndicator` **không nháy** khi Room về nhanh;
   - cuộn grid thư viện nhiều card: không tụt frame;
   - TalkBack đọc được slider.

8. **Ghi mục "Việc chưa làm"** — trung thực, gồm:
   - Nếu NO-GO: điều kiện để nâng Material3 và xoá bản port.
   - Nếu nhánh B: circular wavy bị bỏ (chỉ một call site, chi phí không hợp).
   - `HeroReadingGoalCard` chưa wavy (tiến trình riêng, có chủ ý).
   - Bug có sẵn: thanh trượt CBZ không nhảy trang vì `initialPageIndex` chỉ dùng lúc khởi tạo
     `rememberLazyListState` (journal `260919-1610:103-105`) — **không** sửa trong plan này.
   - Cài đặt đọc vẫn chưa được lưu (journal `260919-1610:102`) — không liên quan, nhưng vẫn mở.

9. **Cập nhật status.** Đặt `status: completed` trong frontmatter của `plan.md` và 4 phase file. Nếu có
   phase bị bỏ dở, đặt `cancelled` + một dòng lý do — **đừng** đánh completed cho việc chưa làm.

## Success Criteria

- [x] `README.md` tồn tại, có mục ghi công nêu `JustForPixel-ExpressiveLab` + **MIT** + link repo, và nói
      rõ là tham chiếu thiết kế chứ không phân phối lại code.
- [x] Ghi công Apache 2.0 cho phần port Material3 vẫn còn **nếu** còn code port; đã xoá **nếu** không còn.
- [x] `docs/core-ui.md` mục 1.3 phản ánh dữ liệu **thật** của Phase 1, không còn lý lẽ lạc hậu về
      `1.5.0-alpha25`.
- [x] `docs/core-ui.md` mục 1.6 liệt kê đúng call site hiện tại, gồm `WavyReaderSlider` và
      `ReadingProgressRing`.
- [x] `docs/core-ui.md` mục 1.7 nêu đủ 3 chủ ý khác spec (TTS ring, `HeroReadingGoalCard`, không có
      `WavySlider` chính thức).
- [x] `docs/reader.md` + `docs/library.md` cập nhật, **không** nhân bản bảng thời gian chờ.
- [x] Journal `docs/journals/260920-*.md` tồn tại, có bảng version + `minCompileSdk`/`minAGP`, có ghi
      "`1.4.0` stable không có wavy", có "`alpha20` là vách đá", có "M3 không có `WavySlider`".
- [x] 5 module compile xanh; `assembleDebug` xanh; unit test **không giảm** số pass.
- [ ] Kết quả kiểm tay trên thiết bị đã ghi vào journal (5 mục ở bước 7). **PENDING**
- [x] Mục "Việc chưa làm" liệt kê đủ follow-up, gồm bug seek CBZ.
- [x] `status` trong `plan.md` + 4 phase file đã cập nhật đúng thực tế.

## Risk Assessment

| Rủi ro | Khả năng | Tác động | Giảm thiểu |
| --- | --- | --- | --- |
| Viết "đã verify" mà chưa chạy trên thiết bị | Trung bình | **Cao** — phá giá trị của toàn bộ docs | Bước 1 chạy trước khi viết; bước 7 ghi kết quả thật. Nếu chưa có thiết bị thì ghi "chưa kiểm trên thiết bị" như journal `260919-1610:97-99` đã làm — đó là cách ghi đúng. |
| Ghi công quá nhẹ (chỉ nhắc tên, không nêu MIT) | Trung bình | Cao — vấn đề giấy phép | Checklist yêu cầu tường minh: tên + link + "MIT" + phạm vi sử dụng. |
| Ghi công quá mạnh ("based on" khi chỉ là tham chiếu thị giác) | Trung bình | Trung bình | Diễn đạt khớp thực tế. Nếu thật sự có đoạn bắt nguồn từ code họ, nói rõ chỗ nào. |
| `docs/core-ui.md` mục 1.3 giữ lý lẽ cũ → lần sau lại kết luận sai | **Cao** | Cao | Bước 3 nêu tường minh đây là mục **phải** viết lại. Đây chính là lỗi mà plan này đang dọn. |
| `:core:tts` đỏ vì thay đổi chưa commit của phiên khác → bị quy cho plan này | Trung bình | Thấp | Bước 1 chạy `:core:tts` và ghi trạng thái trước/sau. |
| Docs nói một đằng, code một nẻo sau khi Phase 3 đổi quyết định `HeroReadingGoalCard` | Trung bình | Trung bình | Phase 3 bước 5 yêu cầu ghi quyết định vào commit message; bước 4 ở đây đọc lại nó. |
| Đánh `status: completed` cho phase chưa xong | Trung bình | Trung bình | Bước 9 quy định `cancelled` + lý do cho việc bỏ dở. |

### Backwards compatibility

Chỉ có docs và `README.md`. Không có bề mặt tương thích ngược. Một lưu ý nhỏ: tạo `README.md` mới thì
đừng để nó thành tài liệu thứ hai mâu thuẫn với `docs/`. `README.md` giữ ngắn — giới thiệu, module,
toolchain, ghi công — và **trỏ** vào `docs/` cho chi tiết.

### Rollback

```powershell
git checkout docs/
git rm README.md   # nếu cần bỏ
```

Vô hại. Nhưng nếu revert docs mà giữ code thì repo còn tệ hơn lúc đầu: code wavy + docs nói không wavy.
Revert docs **chỉ khi** revert luôn Phase 2/3.
