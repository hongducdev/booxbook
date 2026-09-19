# Core UI: Shared Compose Primitives

Module: `:core:ui` (namespace `com.booxbook.core.ui`)

`:core:ui` chứa các primitive dùng chung cho mọi feature: theme (màu, chữ, shape), animation spring, và
các component Expressive. Tài liệu này tập trung vào **chỉ báo tải và tiến trình**, vì đó là phần có quy
tắc thiết kế chặt chẽ nhất và dễ bị làm sai nhất.

---

## 1. Loading & Progress Indicators

### 1.1 Bảng chọn chỉ báo theo thời gian chờ

Material 3 quy định thẳng việc chọn component theo thời gian chờ dự kiến
([loading indicator](https://m3.material.io/components/loading-indicator/guidelines),
[progress indicators](https://m3.material.io/components/progress-indicators/guidelines)):

| Thời gian chờ | Khuyến nghị | Component trong app |
| --- | --- | --- |
| Tức thời (dưới 200 ms) | **Không** hiện chỉ báo, hiện nội dung ngay | `DelayedLoadingIndicator` (chưa vẽ gì trong 200 ms đầu) |
| Ngắn (200 ms – 5 s) | **Loading indicator** (thay cho indeterminate circular) | `ExpressiveLoadingIndicator` / `ExpressiveContainedLoadingIndicator` |
| Dài (trên 5 s) | **Progress indicator** (determinate nếu đo được) | `ReadingProgressBar`, `ReadingProgressRing` |

Hai luật kèm theo, cũng từ spec:

- **Không chuyển từ loading indicator sang determinate.** Muốn chuyển thì phải bắt đầu bằng
  indeterminate *progress* indicator rồi chuyển sang determinate.
- **Một tiến trình, một cấu hình.** Cùng một quá trình thì dùng cùng variant (linear/circular) và cùng
  tham số ở mọi màn hình.

### 1.2 `ExpressiveLoadingIndicator`

```kotlin
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
)

@Composable
fun ExpressiveContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerShape: Shape = CircleShape,
)
```

- Delegate sang `androidx.compose.material3.LoadingIndicator` / `ContainedLoadingIndicator`
  (`material3:1.5.0-alpha10`). Morph hình và vòng quay do Material3 bảo trì.
- Container mặc định 48dp; muốn nhỏ hơn, truyền `Modifier.size(...)`.
- Bản có container dùng khi chỉ báo đặt **trên nội dung khác**; lúc đó active indicator đổi sang
  `onPrimaryContainer` trên nền `primaryContainer` để đủ tương phản.
- `@OptIn(ExperimentalMaterial3ExpressiveApi)` chỉ nằm trong `:core:ui`, không rải ra feature.

### 1.3 Vì sao giờ dùng được component chính thức

Journal `260919-1610` kết luận không nâng được Material3 vì đã thử `1.4.0-alpha18` rồi nâng cả BOM
Compose 1.9 và kéo lifecycle 2.9 / `savedstate-compose` đến mức không resolve. Kết luận đó **lạc
hậu**:

| Artifact | `minCompileSdk` | `minAGP` | `LoadingIndicator` | `*WavyProgressIndicator` |
| --- | --- | --- | --- | --- |
| `material3:1.3.1` (cũ) | 35 | 8.6.0 | Không | Không |
| `material3:1.4.0` **stable** | 35 | 8.6.0 | **Không** (chỉ có token) | **Không** |
| `material3:1.5.0-alpha10` (đang dùng) | **35** | **8.6.0** | **Có** | **Có** |
| `material3:1.5.0-alpha20`+ | 37 | 9.1.0 | Có | Có |

Đường đi đã chạy thật: `composeBom = 2025.05.01` (ui 1.8.2) + pin `material3:1.5.0-alpha10`. AGP vẫn
8.7.3, Kotlin vẫn 2.1.0, JDK vẫn 17, `compileSdk` vẫn 35. Lifecycle **có** bị kéo lên 2.9.0 qua
`savedstate-compose:1.3.0` — nhưng lần này resolve được (khác journal cũ). Bản port ~390 dòng và
khai báo tường minh `graphics-shapes` đã xoá; `graphics-shapes` đến transitive từ Material3.

`1.4.0` stable là bẫy: có `LoadingIndicatorTokens` nhưng không có `LoadingIndicatorKt` và không có
class `Wavy*`. `1.5.0-alpha20` là vách đá (`compileSdk 37` + `AGP 9.1`). Đó cũng là lý do **không**
thêm AAR [JustForPixel-ExpressiveLab](https://github.com/mohdamaan1/JustForPixel-ExpressiveLab)
(MIT) — thư viện đó ngồi ở `material3:1.5.0-alpha22`, bên kia vách. ExpressiveLab chỉ là **tham chiếu
thiết kế** (bento, heatmap, biên độ/bước sóng của slider).

### 1.4 `DelayedLoadingIndicator`

```kotlin
@Composable
fun DelayedLoadingIndicator(
    modifier: Modifier = Modifier,
    delayMillis: Long = InstantWaitThresholdMillis, // 200
    color: Color = MaterialTheme.colorScheme.primary,
)
```

Đặt vào nhánh "đang tải": nếu dữ liệu về trước ngưỡng thì composable bị rời khỏi composition và chưa
từng vẽ gì, đúng luật "dưới 200 ms thì hiện nội dung ngay, không chỉ báo".

### 1.5 `ReadingProgressBar`

```kotlin
@Composable
fun ReadingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
)
```

Determinate **linear wavy** (`LinearWavyProgressIndicator`) với cấu hình Material 3 Expressive: stroke
~4dp, container ~10dp, active `primary`, track `secondaryContainer`, **stop indicator 4dp** (spec: bắt
buộc khi track không đủ tương phản 3:1 với nền). `waveSpeed = 0.dp` — giữ hình sóng, không chạy sóng
trên lưới nhiều card. NaN được quy về 0 để không vỡ hình.

`ReadingProgressRing` là vòng determinate wavy (`CircularWavyProgressIndicator`) cho tiến trình đo
được dạng vòng (nhập nhiều sách). Token nằm ở `:core:ui`, không đặt `strokeWidth`/`trackColor` tại
call site.

Chỉ dùng cho giá trị phần trăm **có thật**. Bốn chỗ % đọc (`BentoBookCard`, `ContinueReadingCarousel`,
`BookDetailScreen`, `TopBooksReadingList`) đều đi qua `ReadingProgressBar` — đổi sóng chỉ sửa một file.

### 1.6 Nơi đang dùng (kiểm tra khi sửa)

| Màn hình / component | Chờ gì | Chỉ báo |
| --- | --- | --- |
| `ReaderOpeningOverlay` | Mở sách (3 mốc công việc) | `ExpressiveLoadingIndicator` 48dp + mô tả bước; màu suy từ độ sáng nền reader |
| `LibraryScreen` (nhập sách, > 1 tệp) | `current/total` đo được | `ReadingProgressRing` |
| `FloatingReaderToolbar` | Lật trang (seek) | `WavyReaderSlider` — slider, không phải progress indicator |
| `LibraryScreen` (nhập sách, 1 tệp) | Không đo được | `ExpressiveLoadingIndicator` 28dp trong card |
| `BookDetailScreen` (nạp sách) | Room | `DelayedLoadingIndicator` |
| `BookDetailScreen` (mục lục) | Trích TOC | `ExpressiveLoadingIndicator` 24dp (cỡ nhỏ nhất spec cho phép) |
| `BookDetailScreen`, `BentoBookCard`, `ContinueReadingCarousel`, `TopBooksReadingList` | % tiến độ đọc | `ReadingProgressBar` |
| `StatisticsScreen` | Room | `DelayedLoadingIndicator` |
| `CbzReaderComponent` (trang chưa giải nén) | Giải nén 1 trang | `ExpressiveContainedLoadingIndicator` 40dp (đặt trên khung trang truyện nên cần container) |
| `TtsFloatingPlayer` | Tiến độ câu trong chương | `CircularProgressIndicator(progress = …)` determinate — xem 1.7 |
| `HeroReadingGoalCard` (mục tiêu ngày) | % mục tiêu | `LinearProgressIndicator` dày 16dp — xem 1.7 |

### 1.7 Chủ ý giữ khác spec

- **Vòng tiến độ quanh nút TTS** giữ dạng annular determinate, **không wavy**. Nó đo đúng giá trị (câu
  hiện tại / tổng số câu). Spec về "progress indicator trong button" yêu cầu đổi màu active cho trùng
  màu icon và bỏ track khi nút quá nhỏ — sóng ở bán kính 48dp / stroke 2.5dp là nhiễu thị giác.
- **`HeroReadingGoalCard` dày 16dp** là cấu hình expressive có chủ ý cho một *tiến trình khác* (mục
  tiêu đọc trong ngày, không phải tiến độ một cuốn sách). Không dùng `ReadingProgressBar`; không wavy
  vì sóng sẽ tràn khỏi khung pill có border.
- **`WavyReaderSlider` là tự dựng.** Material3 **không có** `WavySlider` ở bất kỳ bản nào đã kiểm
  (kể cả 1.5.0-alpha18). Thanh lật trang = `Slider` chuẩn + slot `track` vẽ sin. Tham chiếu thị giác:
  ExpressiveLab `ExpressiveWavySlider` (MIT). Gesture / semantics lấy từ Material3. `inactiveTrackColor`
  đổi từ `surfaceVariant` sang `secondaryContainer` để khớp `ReadingProgressBar`.
- **Không dùng pull-to-refresh.** Spec gắn loading indicator với pull-to-refresh, nhưng thư viện ở đây
  là Room `Flow` — dữ liệu tự cập nhật, không có thao tác "kéo để làm mới" để gắn chỉ báo.

---

## 2. Thành phần Expressive khác

| File | Vai trò |
| --- | --- |
| `component/ExpressivePillButton.kt` | Nút pill có spring scale khi nhấn |
| `component/ExpressiveCard.kt` | Card bo tròn theo trục roundness của M3 Expressive |
| `component/ExpressiveFilterChip.kt` | Chip lọc có chuyển màu/spring |
| `component/ExpressiveLoadingIndicator.kt` | Loading indicator chính thức + delay 200ms (mục 1.2 – 1.4) |
| `component/ReadingProgressBar.kt` | Progress bar / ring wavy dùng chung (mục 1.5) |
| `component/WavyReaderSlider.kt` | Slider lật trang với track sóng |
| `animation/SpringPhysics.kt` | Hằng số spring dùng cho chuyển động của toolbar/sheet |
| `theme/Color.kt`, `theme/Type.kt`, `theme/Shape.kt`, `theme/Theme.kt` | Palette, Google Sans Flex, shape scale |

---

## 3. Kiểm thử

Chỉ báo là phần vẽ thuần Compose: không có unit test (không có logic trạng thái để test ngoài tham số
đầu vào). Xác minh bằng:

- `./gradlew :core:ui:compileDebugKotlin` — wrapper delegate biên dịch với Compose BOM 2025.05.01 /
  Material3 1.5.0-alpha10.
- `./gradlew test` — không feature nào phụ thuộc vào chỉ báo trong test đơn vị (các test đều chạy trên
  ViewModel/state, không dựng UI).
- Kiểm tra tay trên thiết bị: morph hình chính thức, sóng trên thanh % đọc + slider, tương phản trên 4
  preset nền reader, `DelayedLoadingIndicator` không nháy khi Room về nhanh, cuộn grid thư viện không
  tụt frame.

---

## 4. Ghi công

- **Material Components / Jetpack Compose Material 3** (Apache License 2.0) — `LoadingIndicator`,
  `ContainedLoadingIndicator`, `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`.
- **[JustForPixel-ExpressiveLab](https://github.com/mohdamaan1/JustForPixel-ExpressiveLab)** (MIT,
  Er. Mohd Amaan) — tham chiếu thiết kế cho ngôn ngữ Expressive (bento, heatmap, biên độ/bước sóng
  của `WavyReaderSlider`). Không phân phối lại code; không phụ thuộc AAR.
