# Unified Reader Screen & Annotations Architecture

Module: `:feature:reader` (with `:core:engine`, `:core:database`, `:core:model`, and `:core:ui`)

## 1. Architectural Overview

The Reader feature in BooxBook implements a reactive **MVVM / MVI (Model-View-Intent)** architecture built with Jetpack Compose, Kotlin Coroutines Flow, Readium Kotlin Toolkit 3.x, and Material 3 Expressive guidelines. It delivers an immersive, unified reading canvas that seamlessly routes between reflowable EPUB publications, fixed-layout comics (CBZ), and Kindle files (AZW3), while managing interactive annotations, bookmarks, reading preferences, and reading progress.

```
+-----------------------------------------------------------------------------------------+
|                                        UI Layer                                         |
|  ReaderScreen                                                                           |
|   ├── Immersive Canvas Router                                                           |
|   │    ├── Case EPUB/AZW3: EpubReaderContainer (AndroidView -> EpubNavigatorFragment)   |
|   │    └── Case CBZ: CbzReaderComponent (Continuous LazyColumn + Zoom/Pan)              |
|   ├── AnimatedReaderTopBar (SpringPhysics slide/fade, Back button, Bookmark toggle)     |
|   ├── FloatingReaderToolbar (32.dp pill, WavyReaderSlider, TOC, Settings, Bookmarks)    |
|   └── Modal Bottom Sheets:                                                              |
|        ├── TableOfContentsSheet (Hierarchical chapter tree & active item highlight)     |
|        ├── ReaderSettingsSheet (Font size roundToInt(), Font family, 4 Theme presets)   |
|        └── BookmarksSheet (Timestamped bookmarks, fast locator jump, 1-tap delete)      |
+--------------------------------------------▲--------------------------------------------+
                                             | StateFlow<ReaderUiState>
                                             | User Intents & Callbacks
+--------------------------------------------▼--------------------------------------------+
|                                    Presentation Layer                                   |
|  ReaderViewModel                                                                        |
|   ├── _uiState: MutableStateFlow<ReaderUiState>                                         |
|   ├── annotationsJob: Job? (Continuous Room observation for active book)               |
|   ├── progressSaveJob: Job? (500ms debounced persistence to Room)                       |
|   └── Synchronization across EpubReaderEngine & CbzReaderEngine                         |
+--------------------------------------------▲--------------------------------------------+
                                             | suspend openBook() / getPublication()
                                             | BookRepository queries & transactions
+--------------------------------------------▼--------------------------------------------+
|                                   Data & Engine Layer                                   |
|  BookRepository + EpubReaderEngine + CbzReaderEngine                                    |
|   ├── Readium AssetRetriever, PublicationOpener & EpubNavigatorFactory                  |
|   ├── On-demand CbzArchive & ZipFile streaming                                          |
|   └── Room Database (ReadingProgressDao, AnnotationDao, BookDao)                        |
+-----------------------------------------------------------------------------------------+
```

### Unidirectional Data Flow (UDF)

1. **State:** The UI observes a single, immutable `ReaderUiState` exposed as a `StateFlow` from `ReaderViewModel` and collected via `collectAsStateWithLifecycle()` in Compose.
2. **Events / Intents:** User interactions (center canvas taps, page scrolling, seeking via progress slider, toggling bookmarks, changing font size or themes, opening sheets) dispatch intention callbacks directly into `ReaderViewModel`.
3. **Persistence Pipeline:** Reading progress updates are debounced by 500ms before writing to the Room persistence layer, shielding the disk I/O thread during rapid page turns or slider scrubbing.

---

### UI State Modeling

The entire UI contract is encapsulated in `ReaderUiState.kt` alongside sheet routing and theme enumerations:

```kotlin
enum class ActiveReaderSheet {
    TOC,
    SETTINGS,
    BOOKMARKS
}

enum class ReaderThemePreset(val displayName: String) {
    LIGHT("Sáng"),
    SEPIA("Giấy ấm"),
    DARK("Tối"),
    AMOLED("Đen tuyền")
}

data class ReaderUiState(
    val isLoading: Boolean = true,
    val loadingPhase: ReaderLoadingPhase = ReaderLoadingPhase.LOADING_BOOK,
    val book: Book? = null,
    val format: BookFormat = BookFormat.EPUB,
    val tableOfContents: List<TocItem> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val progressPercentage: Float = 0f,
    val currentChapterTitle: String = "",
    val currentLocator: String? = null,
    val isControlsVisible: Boolean = false,
    val preferences: ReaderPreferences = ReaderPreferences(),
    val themePreset: ReaderThemePreset = ReaderThemePreset.DARK,
    val activeSheet: ActiveReaderSheet? = null,
    val annotations: List<Annotation> = emptyList(),
    val isCurrentLocationBookmarked: Boolean = false,
    val errorMessage: String? = null
) {
    val isPreparing: Boolean get() = !isCanvasReady && errorMessage == null
    val isCanvasReady: Boolean get() = loadingPhase == ReaderLoadingPhase.READY
}
```

---

## 2. Unified Canvas Integration

BooxBook dynamically routes rendering based on `BookFormat` inside `ReaderScreen.kt`. The canvas is mounted as soon as the book record is known — deliberately **not** gated on `isLoading` — because Readium only reports readiness once its navigator is actually attached to the view tree; a canvas that waits for the opening screen to disappear would never signal back.

```kotlin
val hasCanvas = uiState.book != null && uiState.errorMessage == null

if (hasCanvas) {
    if (uiState.format == BookFormat.CBZ) {
        val archive = viewModel.cbzReaderEngine.getArchive()
        if (archive != null) {
            CbzReaderComponent(
                archive = archive,
                initialPageIndex = uiState.currentPage,
                onPageChanged = { pageIndex, totalPages ->
                    viewModel.onPageChanged(
                        pageIndex = pageIndex,
                        totalPages = totalPages,
                        chapterTitle = "Trang ${pageIndex + 1}"
                    )
                },
                onTap = { viewModel.toggleControls() },
                onReady = viewModel::onCanvasReady, // trang đầu đã giải nén xong
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // EPUB / AZW3 Readium Navigator
        EpubReaderContainer(
            epubEngine = viewModel.getActiveReadiumEngine(),
            preferences = uiState.preferences,
            initialLocatorJson = uiState.currentLocator,
            onLocatorChanged = { locator ->
                viewModel.onCanvasReady() // locator đầu tiên = trang đã vẽ xong
                val totalProg = locator.locations.totalProgression?.toFloat()
                    ?: (locator.locations.progression?.toFloat() ?: 0f)
                val pageIndex = locator.locations.position ?: 0

                viewModel.onPageChanged(
                    pageIndex = pageIndex,
                    totalPages = uiState.totalPages,
                    locator = locator.toJSON().toString(),
                    chapterTitle = locator.title ?: "",
                    percentage = totalProg
                )
            },
            onTapAction = { action -> /* tap zones */ },
            onNavigatorReady = { nav -> activeEpubNavigator = nav },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Lớp phủ mở sách nằm *trên* canvas đang dựng ngầm và tan dần khi trang đầu hiện ra.
ReaderOpeningOverlay(
    visible = uiState.isPreparing,
    phase = uiState.loadingPhase,
    book = uiState.book,
    backgroundColor = backgroundColor
)
```

---

### Opening Pipeline & Contextual Loading Overlay

Mở một cuốn sách không phải một bước duy nhất, và người đọc cần biết *cuốn nào* đang mở chứ không chỉ một vòng xoay trống. `ReaderViewModel` công bố các mốc công việc qua `ReaderLoadingPhase`:

| Phase | Ý nghĩa | Màn chờ hiển thị |
| --- | --- | --- |
| `LOADING_BOOK` | Đọc bản ghi sách + tiến độ đã lưu từ Room | Khung bìa giữ chỗ, tiêu đề "Đang mở sách" |
| `OPENING_PUBLICATION` | Đã biết sách, đang mở Readium publication / giải nén CBZ | Bìa, tựa đề, tác giả + "Đang mở nội dung sách…" |
| `BUILDING_CANVAS` | Engine sẵn sàng, chờ trang đầu vẽ xong | + "Đang dựng trang đọc…" |
| `READY` | Trang đọc đã hiện, hoặc đã chuyển sang màn lỗi | Lớp phủ tan dần (420 ms) |

**Chỉ báo của màn chờ là loading indicator, không phải thanh determinate.** `ReaderLoadingPhase` là *mốc công việc*, không phải phần trăm thời gian — ba bước không tỉ lệ với nhau và thời lượng thật phụ thuộc định dạng sách lẫn tốc độ thiết bị. Material 3 yêu cầu chỉ báo determinate phải đo đúng tiến trình, nên thanh phần trăm dựng từ các mốc này là thông tin sai; phần "đang ở bước nào" do dòng mô tả (`AnimatedContent`) đảm nhiệm, còn chỉ báo dùng [`ExpressiveLoadingIndicator`](core-ui.md) — đúng component mà spec chỉ định cho khoảng chờ ngắn không đo được tiến trình.

Ba chi tiết quyết định trải nghiệm:

1. **Lớp phủ, không phải màn hình thay thế.** `ReaderOpeningOverlay` được vẽ phủ lên canvas đang dựng ngầm. Nếu đợi màn chờ biến mất mới dựng canvas thì `EpubReaderContainer` không bao giờ được gắn và người đọc thấy khoảng trắng; đổi lại, lớp phủ phải **tiêu thụ mọi sự kiện chạm** — nó nằm trên một `AndroidView` (WebView của Readium) mà view thật trong cây Compose vẫn nhận được chạm nếu không có gì chặn:

   ```kotlin
   Modifier.pointerInput(Unit) {
       awaitPointerEventScope {
           while (true) {
               awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
           }
       }
   }
   ```
2. **Tín hiệu "sẵn sàng" đến từ nội dung thật.** EPUB/AZW3 gọi `onCanvasReady()` ở locator đầu tiên Readium phát ra; CBZ gọi khi trang mở đầu tiên giải nén xong (`CbzReaderComponent(onReady = …)`). `onCanvasReady()` là idempotent nên tín hiệu nào tới trước cũng đủ.
3. **Lưới an toàn.** Sau `CANVAS_READY_FALLBACK_MS` (2500 ms) mà không có tín hiệu nào (sách không có reading order, WebView không phát sự kiện…), `beginCanvasBuild()` tự đóng màn chờ để người đọc không bị kẹt sau một lớp phủ.

Màu chữ và màu chỉ báo của lớp phủ được suy ra từ **độ sáng của nền màn đọc** (`Color.luminance()`), không lấy từ `MaterialTheme.colorScheme`: 4 preset nền (Sáng/Giấy ấm/Tối/Đen tuyền) độc lập với theme của app, nên `colorScheme.primary` của theme tối có thể chìm trên nền Sáng.

---

### EPUB Rendering: `EpubReaderContainer` & Readium Integration

Reflowable and fixed-layout EPUB publications are rendered using Readium's `EpubNavigatorFragment` embedded into Compose via an interoperability container (`EpubReaderContainer.kt`).

#### 1. Fragment Lifecycle and Container Retention
Reflowable EPUB/AZW3 pages are hosted by a real Fragment (Readium's `EpubNavigatorFragment` owns the paginated WebView), so it has to be added to the Activity's `FragmentManager`. Two rules make it actually render:

1. **Commit the transaction only after the host view is attached to the window, and outside of Compose's layout pass.** Committing synchronously from `AndroidView`'s `factory` attaches the WebView to a detached hierarchy; Chromium then keeps rendering frames but they never reach the screen, leaving the canvas showing the Compose `Scaffold` background (`#141218` under the DARK preset) — a blank reader.
2. **The host view id must survive configuration changes** so a restored navigator can be matched back to its container.

```kotlin
val activity = LocalContext.current as? FragmentActivity ?: return
val fragmentManager = activity.supportFragmentManager

// Stable id: a restored fragment keeps pointing at the same container id.
val containerId = rememberSaveable { View.generateViewId() }

val engineState by epubEngine.state.collectAsStateWithLifecycle()

LaunchedEffect(hostView, engineState, epubEngine) {
    if (activeFragment != null) return@LaunchedEffect
    if (epubEngine.getNavigatorFactory() == null) return@LaunchedEffect   // publication not ready

    val host = hostView ?: return@LaunchedEffect
    host.awaitAttached()                                                  // post-attach, post-layout
    if (fragmentManager.isDestroyed || fragmentManager.isStateSaved) return@LaunchedEffect

    val navigator = fragmentManager.findFragmentById(containerId) as? EpubNavigatorFragment
        ?: run {
            val factory = epubEngine.createFragmentFactory(initialLocator, preferences)
                ?: return@LaunchedEffect
            factory.instantiate(
                activity.classLoader,
                EpubNavigatorFragment::class.java.name
            ) as EpubNavigatorFragment
        }
    ...
}
```

The container is a plain `FrameLayout` created by the `AndroidView` factory.

#### 2. Configuration Changes and Restoration
`FragmentManager` restores its fragments during `Activity.onCreate`, i.e. **before** Compose has created the container view. A restored navigator therefore ends up with a detached, zero-sized view (`mView` bounds `0,0-0,0`, container child count `0`) and the reader goes blank after rotation.

BooxBook handles this in two places:

- `MainActivity` installs a `RestorableReadiumFragmentFactory` (backed by `ReadiumFragmentFactoryProvider`) on the Activity's `FragmentManager` **before `super.onCreate()`**. Hilt injects fields only on context-available, hence an `@EntryPoint` is used instead of `@Inject`. This prevents `Fragment$InstantiationException: ... could not find Fragment constructor` during restore.
- `EpubReaderContainer` drops any restored navigator whose view is not attached and rebuilds it once the container is on screen; the reading position is re-applied through `initialLocatorJson`.

If the process was restarted there is no open publication, so `MainActivity` passes `null` as the saved state instead of restoring an unbuildable navigator.

#### 2. Tap Zones & Gesture Partitioning

Readium 3.1.1 does **not** wire tap-to-turn navigation: `EpubNavigatorFragment` never references
`DirectionalNavigationAdapter`, so without app-side handling an edge tap does nothing. Taps are
therefore resolved by the app and turned with `goForward(animated)` / `goBackward(animated)`.

`EpubReaderContainer` registers an `InputListener` whose tap handler delegates to the pure
resolver in `ReaderInteraction.kt`:

```kotlin
val action = ReaderTapZones.resolve(
    x = event.point.x / width,
    y = event.point.y / height,
    mode = ReaderTapZoneMode.fromKey(preferences.tapZoneMode),
    topInsetFraction = statusBarTopPx / height
)
if (action == ReaderTapAction.NONE) return false   // let Readium/WebView handle it (e.g. links)
onTapAction(action)
return true
```

| `ReaderTapZoneMode` | prev | menu | next | top strip |
|---|---|---|---|---|
| `KINDLE` (default) | x < 0.30 | 0.30..0.70 | x > 0.70 | status-bar inset + 6% → menu |
| `EDGES` | x < 0.25 | 0.25..0.75 | x > 0.75 | — |
| `MENU_ONLY` | — | 0.25..0.75 | — | — |

The canvas runs edge-to-edge, so the always-menu strip starts **below the status bar**
(`topInsetFraction`); otherwise most of it would sit behind system UI and be unreachable.
`MENU_ONLY` reproduces the pre-feature behaviour exactly.

Because the resolver is a pure function of normalised coordinates, all boundaries are covered by
`feature/reader/src/test/java/com/booxbook/feature/reader/ReaderTapZonesTest.kt`.

#### 3. Page-Turn Effects

`ReaderPageTurnEffect` controls what happens on a page turn:

| Effect | Behaviour |
|---|---|
| `SLIDE` (default) | `goForward/goBackward(animated = true)` — Readium's own scroller animation |
| `FLIP` | Kindle-like page lift (see below) |
| `NONE` | instant swap (`animated = false`) |

The `FLIP` path snapshots the navigator's `publicationView` into a bitmap *before* the turn,
turns instantly, then peels the snapshot away in `PageTurnFlipOverlay` with a 3D rotation about
the spine edge plus a gradient scrim, revealing the freshly rendered page underneath. If the
snapshot is unavailable (zero-sized view, all-one-colour capture), it falls back to `SLIDE`.

A haptic tick is emitted per turn when `ReaderPreferences.hapticsEnabled` is set, and
`TapZonePreviewOverlay` renders a transient, labelled diagram of the active zones (triggered from
reader settings), auto-dismissing after ~2.2 s.

#### 3. Continuous Locator Tracking
The fragment's `currentLocator` Flow is collected continuously, keeping the `ReaderViewModel` and reading progress in sync:

```kotlin
LaunchedEffect(navigatorFragment) {
    val fragment = navigatorFragment ?: return@LaunchedEffect
    onNavigatorReady(fragment)

    fragment.currentLocator.collectLatest { locator ->
        onLocatorChanged(locator)
    }
}
```

#### 4. Preference Synchronization
Changes in `ReaderPreferences` (font size, font family, theme) are dispatched directly to the active navigator:

```kotlin
LaunchedEffect(preferences, activeFragment) {
    val fragment = activeFragment ?: return@LaunchedEffect
    fragment.submitPreferences(epubEngine.buildEpubPreferences(preferences))
}
```

---

### CBZ Comic Rendering: `CbzReaderComponent`

Comics and manga are rendered natively in Jetpack Compose via `CbzReaderComponent.kt`, delivering a continuous vertical scrolling experience (Webtoon style).

#### 1. Continuous Vertical Scrolling & Tracking
A `LazyColumn` renders sequential comic pages on-demand:

```kotlin
val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)

LaunchedEffect(listState, totalPages) {
    var dismissJob: Job? = null
    snapshotFlow { listState.firstVisibleItemIndex }
        .distinctUntilChanged()
        .collect { index ->
            currentPageIndex.value = index
            onPageChanged(index, totalPages)
            isScrolling = true
            dismissJob?.cancel()
            dismissJob = launch {
                delay(2000)
                isScrolling = false
            }
        }
}
```

#### 2. Dual-Gesture System: Pinch-to-Zoom and Panning
`CbzReaderComponent` features smooth multi-touch transformations:
- **Pinch-to-zoom:** Clamped from `1.0f` to `4.0f`.
- **Double-tap zoom:** Toggles between `1.0f` and `2.5f`.
- **Pan bounding:** Offsets are constrained to viewport bounds:
  $$\text{maxOffsetX} = \frac{\text{maxWidthPx} \times (\text{scale} - 1)}{2}$$
- **Scroll Isolation:** When zoomed in (`scale > 1.05f`), `LazyColumn(userScrollEnabled = false)` disables list scrolling so pan gestures do not accidentally trigger vertical page jumps.

```kotlin
LazyColumn(
    state = listState,
    contentPadding = PaddingValues(0.dp),
    modifier = Modifier.fillMaxSize(),
    userScrollEnabled = (scale <= 1.05f)
) {
    items(
        count = totalPages,
        key = { index -> archive.pages[index].entryName }
    ) { index ->
        CbzPageItem(archive = archive, index = index)
    }
}
```

#### 3. Ephemeral Floating Page Pill
While scrolling, an animated page indicator pill displays `Page X of Y` with a 2-second auto-dismiss timeout.

---

## 3. Material 3 Expressive Controls & Sheets

The reader interface uses Material 3 Expressive motion tokens and high-elevation surface containers (`surfaceContainerHigh`, `surfaceContainerHighest`).

### 1. `AnimatedReaderTopBar`

A floating top bar pinned to the top of the screen with spring physics animations:

- **Motion:** Spring enter/exit transitions (`SpringPhysics.BouncyOffsetSpring` and `SpringPhysics.SmoothSpring`).
- **Insets:** Uses `statusBarsPadding()` to render below system status bars.
- **Controls:**
  - Back navigation button returning to the Library.
  - Title and subtitle (active chapter) with `TextOverflow.Ellipsis`.
  - Bookmark toggle button with stateful iconography (`Icons.Rounded.Bookmark` vs `Icons.Rounded.BookmarkBorder`).

```kotlin
AnimatedVisibility(
    visible = visible,
    enter = slideInVertically(animationSpec = SpringPhysics.BouncyOffsetSpring, initialOffsetY = { -it }) + fadeIn(animationSpec = SpringPhysics.SmoothSpring),
    exit = slideOutVertically(animationSpec = SpringPhysics.BouncyOffsetSpring, targetOffsetY = { -it }) + fadeOut(animationSpec = SpringPhysics.SmoothSpring),
    modifier = modifier.fillMaxWidth()
) {
    Surface(
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) { ... }
}
```

---

### 2. `FloatingReaderToolbar`

A 32.dp pill container anchored to the bottom edge:

- **Shape & Insets:** `RoundedCornerShape(32.dp)`, `navigationBarsPadding()`, and 8.dp shadow elevation.
- **Scrubbing Slider:** `WavyReaderSlider` — Material 3 `Slider` với track sóng (M3 không có
  `WavySlider`). Discrete page changes trigger upon release (`onValueChangeFinished`) to prevent
  intermediate navigation spikes. Track không hoạt động dùng `secondaryContainer` (đổi có chủ ý từ
  `surfaceVariant` để khớp `ReadingProgressBar`; token nằm ở `:core:ui`). Xem [core-ui.md](core-ui.md).
  ```kotlin
  WavyReaderSlider(
      value = sliderPosition,
      valueRange = 0f..(totalPages - 1).toFloat(),
      onValueChange = { sliderPosition = it },
      onValueChangeFinished = { onSeekToPage(sliderPosition.toInt()) },
  )
  ```
- **Action Triggers:**
  - **TOC:** Opens Table of Contents bottom sheet.
  - **Settings:** Opens typography & theme customizer.
  - **Bookmarks:** Opens saved bookmarks and highlights.
  - **TTS:** Triggers text-to-speech reading.

---

### 3. `TableOfContentsSheet`

A hierarchical chapter navigation sheet:

- **Nested Tree Layout:** Recursive `TocItemRow` instances calculate dynamic start padding:
  $$\text{startPadding} = (24 + \text{depth} \times 16).\text{dp}$$
- **Active Chapter Highlighting:** Evaluates whether `currentHref` contains `item.href` and applies `primaryContainer` tonal background and bold typography.
- **1-Tap Jump:** Clicking a chapter dismisses the sheet via coroutines and invokes `nav.go(Link(href = Href(url)), animated = true)` for EPUB or calculates target page indices for CBZ.

---

### 4. `ReaderSettingsSheet`

A display customizer modal bottom sheet:

#### A. Font Size Scaler (`roundToInt()`)
Allows adjusting font scaling from `0.7x` (70%) to `2.5x` (250%) in steps of `0.1` (10%):

```kotlin
Text(
    text = "${(preferences.fontSize * 100).roundToInt()}%",
    style = MaterialTheme.typography.titleMedium
)
```

#### B. Theme Color Presets
Provides 4 color configurations:

| Preset | Background Color | Text Color | Intended Environment |
|---|---|---|---|
| **LIGHT** | `#FEF7FF` | `#1D1B20` | Daytime & bright environments |
| **SEPIA** | `#FBF0D9` | `#5F4B32` | Warm, paper-like reading |
| **DARK** | `#141218` | `#E6E0E9` | Low-light comfortable reading |
| **AMOLED** | `#000000` | `#FFFFFF` | Pitch black for OLED battery efficiency |

#### C. Font Family Picker
Instant switching between:
- **Google Sans** (`null` / system default flex font)
- **Serif** (`"serif"`)
- **Monospace** (`"monospace"`)

---

### 5. `BookmarksSheet`

A management sheet for user bookmarks and annotations:

- **Listing & Formats:** Displays bookmarked locations with chapter title or fallback text (`"Trang X"`), accompanied by formatted timestamps:
  ```kotlin
  val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
  ```
- **Fast Jump:** Tapping an entry parses the stored locator (`Locator.fromJSON()` for Readium, or page index for CBZ) and navigates directly to the bookmarked position.
- **1-Tap Deletion:** A red trash icon initiates deletion via `viewModel.deleteAnnotation(annotation)`.

---

### 6. `TtsFloatingPlayer` & Synchronized Sentence Highlighting

BooxBook features an unobtrusive, high-performance Read-Along Text-to-Speech (TTS) engine with live sentence highlighting:

#### A. Draggable Single Floating Play/Pause Button (Nekori-Style)
Designed specifically so as **not to occlude reading content**:
- **Always Available:** Continuously accessible on the reading canvas without needing to open the reader toolbar or controls chrome.
- **Full Drag & Drop Freedom:** The reader can drag the 52dp circular button freely anywhere across the canvas (left/right margins, top/bottom corners). Smart gesture detection separates quick taps (toggle play/pause) from drag gestures.
- **Immediate Current-Page Playback:** Starting playback always resolves the active spine item and sentence directly from the currently visible page and progression, preventing unexpected jumps.
- **Accurate Chapter Bounds:** Fixes chapter index mapping by resolving URLs against Readium `readingOrder` links rather than global book page numbers, properly handling the book's final chapter and multi-chapter transitions without error.
- **Integrated Progress Ring:** A subtle 2.5dp `CircularProgressIndicator` surrounds the button, tracking the chapter sentence completion without consuming extra screen space.
- **Intuitive Interactions:**
  - **Single Tap:** Toggle Play / Pause instantly with tactile feedback (starts playback from current page if inactive).
  - **Mini Close Badge:** Quick 1-tap dismiss target at the top-right corner to stop TTS session.

#### B. Synchronized Live Ebook Highlighting
As the system TTS speaks each sentence:
- **Readium DecorableNavigator Integration:** Emits native `Decoration` objects into `EpubNavigatorFragment.applyDecorations(decorations, "tts_highlight")` with `Decoration.Style.Highlight`.
- **Dynamic Theme-Aware Tints:**
  - *Light:* Warm highlighter yellow (`#55FFD600`).
  - *Sepia:* Antique amber (`#4BE68C1E`).
  - *Dark / AMOLED:* Glowing golden amber (`#55FFD740`) ensuring high legibility on dark backgrounds without blinding the reader.
- **Auto-Page Turn:** Calls `fragment.go(locator, animated = true)` so that the page automatically turns forward when the reader reaches a sentence located on the next page.
- **Auto-Advance Chapters:** Automatically loads and begins playback for the next chapter when the current chapter concludes.
- **Zero-Flicker Fallback:** Injects smooth, multi-node CSS custom highlights via `evaluateJavascript` for complex DOM structures.

---

### 7. Action Feedback: `ReaderFeedback` → Snackbar

Mọi thao tác trên màn đọc đều trả lời người đọc. `ReaderViewModel` phát ra **sự kiện đã xảy ra**, không phát chuỗi hiển thị:

```kotlin
sealed interface ReaderFeedback {
    data object BookmarkAdded : ReaderFeedback
    data object BookmarkRemoved : ReaderFeedback
    data object TtsStarted : ReaderFeedback
    data object TtsStopped : ReaderFeedback
    data object BookFinished : ReaderFeedback
    data class AnnotationDeleted(val annotation: Annotation) : ReaderFeedback
    data class AnnotationRestored(val annotation: Annotation) : ReaderFeedback
    data class Failure(val message: String) : ReaderFeedback
}

private val _feedback = MutableSharedFlow<ReaderFeedback>(extraBufferCapacity = 8)
val feedback: SharedFlow<ReaderFeedback> = _feedback.asSharedFlow()
```

`ReaderScreen` thu thập luồng này và dịch sang snackbar qua `ReaderFeedback.toSnackbar()` — nơi duy nhất chứa câu chữ, và có unit test riêng:

```kotlin
LaunchedEffect(viewModel) {
    viewModel.feedback.collect { feedback ->
        val snackbar = feedback.toSnackbar()
        val result = snackbarHostState.showSnackbar(
            message = snackbar.message,
            actionLabel = snackbar.actionLabel,
            duration = SnackbarDuration.Short
        )
        val undoAnnotation = snackbar.undoAnnotation
        if (result == SnackbarResult.ActionPerformed && undoAnnotation != null) {
            viewModel.restoreAnnotation(undoAnnotation)
        }
    }
}
```

Quy ước đáng chú ý:

- **Xoá ghi chú/đánh dấu là có thể hoàn tác.** `AnnotationDeleted` mang theo bản ghi gốc; nút "Hoàn tác" gọi `restoreAnnotation(annotation)`, ghi lại **đúng `id` cũ** (id vừa được giải phóng nên ghi đè vô hại) khiến thao tác hoàn tác là idempotent thay vì nhân bản ghi chú.
- **"Đã dừng" khác "đã hết sách".** `stopTts(notify: Boolean = true)` chỉ phát `TtsStopped` khi thực sự có phiên đang chạy; khi chương cuối kết thúc, `advanceTtsToNextChapter()` gọi `stopTts(notify = false)` rồi phát `BookFinished`, tránh hai snackbar trùng nghĩa.
- **Thất bại không im lặng.** Đánh dấu, xoá/khôi phục ghi chú và khởi động TTS đều phát `Failure` kèm thông điệp gốc thay vì `runCatching` rồi bỏ qua.
- **Buffer 8 + `tryEmit`.** Snackbar hiển thị tuần tự (`showSnackbar` là suspend), nên buffer giữ các sự kiện đến trong lúc một snackbar đang hiện thay vì chặn luồng gọi.

### 8. `BookendsOverlay` — lớp thông tin phủ trên trang đọc

Lớp overlay cấu hình được, neo ở sáu vùng quanh trang đọc. Ý tưởng và ngữ nghĩa token lấy từ
[bookends.koplugin](https://github.com/AndyHazz/bookends.koplugin) của AndyHazz (GPL-3.0); phần cài đặt
ở đây là Kotlin/Compose thuần, **không** sao chép mã Lua.

#### Phân tầng

```
ReaderUiState ─┐
TocItem ───────┼─► ReaderViewModel.bookendsContext ─┐
Annotation ────┘                                    │
Room (sessions) ─► BookendsViewModel ◄── BookendsPreferencesManager (SharedPreferences + JSON)
phần cứng ────────┘        │
                            ├─► BookendsSnapshotAssembler  (số học thuần)
                            └─► BookendsFormatter          (cú pháp thuần)
                                        │
                                        └─► List<BookendsChunk> ─► BookendsOverlay
```

`BookendsViewModel` **không** nằm trong `ReaderViewModel`: overlay chỉ *đọc* trạng thái, còn cấu hình
Bookends còn được dùng ở nơi khác không có `ReaderViewModel` nào đang sống. Màn đọc chỉ đẩy vào đúng hai
thứ nó sở hữu — vị trí đang đọc và tiến trình phiên:

```kotlin
LaunchedEffect(viewModel, bookendsViewModel) {
    viewModel.bookendsContext.collect(bookendsViewModel::onReadingContextChanged)
}
LaunchedEffect(viewModel, bookendsViewModel) {
    viewModel.bookendsSessionProgress.collect(bookendsViewModel::onSessionProgressChanged)
}
```

#### Tầng resolve thuần khiết

Toàn bộ cú pháp nằm trong `:core:model` (`com.booxbook.core.model.bookends`) và không phụ thuộc Android
lẫn Compose, nên unit test chạy bằng JUnit thường. Một lần gọi `BookendsFormatter.format` đi qua bốn
bước theo thứ tự **bắt buộc**:

1. Quét chuỗi để nhặt tham số ngoặc nhọn (`%datetime{%d %B}`).
2. Dựng bảng token từ `BookendsSnapshot`.
3. Mở rộng khối `[if:…]…[else]…[/if]` — phải làm **trước** khi tách token, vì nhánh bị loại không được
   phép sinh ra token nào cả.
4. Tách token, định dạng nội dòng `[b]/[i]/[u]`, `%bar`, `%spacer` thành `List<BookendsChunk>`.

Cùng một `(line, snapshot, options)` luôn cho cùng kết quả: không đọc Room, không đọc đồng hồ hệ thống,
không chạm Android. Đồng hồ và múi giờ đi vào qua `nowMillis`/`zoneId` do tầng gọi cung cấp.

#### Vì sao trả `Chunk` chứ không trả `String`

| Chunk | Lý do không thể là chuỗi |
| --- | --- |
| `ProgressBar` | Cần `Modifier.weight(1f)` để co giãn phần bề rộng còn lại |
| `Spacer` | Cùng lý do — đẩy hai đầu một dòng |
| `Icon` | Dùng Material Icons thay cho glyph Nerd Fonts của bản gốc |
| `Text` | Mang theo `maxWidthDp` từ cú pháp `%token{N}` để cắt bằng dấu ba chấm |

Sáu kiểu thanh tiến độ được cài đặt: `SOLID`, `BORDER`, `ROUND`, `METRO`, `WAVE`, `HOLLOW`. `WAVE` dùng chính
ngôn ngữ hình ảnh của Material 3 Expressive mà ứng dụng đã dùng cho slider và chỉ báo tải.

#### Auto-hide, và cái bẫy đi kèm

Dòng mà mọi token đều rỗng tự biến mất (`BookendsRender.isBlank`). Điều này đặt ra một ràng buộc
**bắt buộc**: mọi token đã tài liệu hoá phải **luôn** có mặt trong bảng token, kể cả khi không có dữ liệu.
Tokenizer coi tên lạ là văn bản thường (để người dùng thấy lỗi gõ), nên một token quên đăng ký sẽ in
nguyên `%chap_title` lên trang đọc. `BookendsTokens.registerChapterTokens()` và test
`moi token duoc tai lieu hoa deu duoc dang ky...` khoá bất biến này lại.

#### Số trang hiển thị vs vị trí logic

`ReaderUiState` có **hai** cặp số trang, và chúng không được trộn:

| Trường | Ý nghĩa |
| --- | --- |
| `currentPage` / `totalPages` | Vị trí **logic**: `Locator.locations.position` (đếm từ 1 theo **trang**) cho EPUB/AZW3, hoặc chỉ số trang CBZ 0-based; `totalPages` với EPUB là **số mục trong thứ tự đọc** (số tệp XHTML) |
| `displayPageCount` | Tổng số trang thật, đọc từ `Publication.positions().size` của Readium |

Ba con số này nằm trên **ba thang đo khác nhau**, và đã hai lần ghép nhầm trên máy thật:

| Ghép nhầm | Kết quả hiện ra |
| --- | --- |
| `position` + `totalPages` (thứ tự đọc) | `11 / 93` — một cuốn tiểu thuyết 93 tệp chương |
| `position` toàn sách + `PaginationListener.totalPages` | `11 / 8` — `PaginationListener` báo số trang **trong từng tệp chương** (`positionsByReadingOrder`), không phải toàn sách |
| `position` + `positions().size` | `11 / 297` ✅ |

`Publication.positions()` là **nguồn duy nhất** cùng thang đo với `locations.position`: cả hai đều đếm
trên toàn publication. `ReaderViewModel.loadDisplayPageCount()` gọi nó trong nền trên `ioDispatcher`
(Readium phải dàn trang từng tệp chương để đếm vị trí), và giữ `displayPageCount = 0` cho tới khi xong —
trong lúc chờ, token tự ẩn thay vì hiện một tổng số sai.

`PaginationListener` **cố ý không dùng**: nó đúng thang đo cho số trang *trong chương*, nên nếu sau này muốn
`%chap_pages` chính xác thay vì ước lượng thì đây là nguồn đúng — nhưng phải ghép với `pageIndex` của chính
nó, không phải với `position` toàn sách.

Thanh công cụ dưới màn đọc **vẫn** hiển thị cặp logic cũ; đổi nó cần thiết kế lại ánh xạ tìm kiếm theo
vị trí, nên được tách thành việc riêng thay vì sửa nửa vời.

#### Chương: tiêu đề từ engine, số thứ tự từ chỉ mục mục lục

`TocItem` không mang vị trí, nên `BookendsChapterIndexFactory` suy ra vị trí của mỗi mục bằng cách ánh xạ
`href` sang chỉ số trong `readingOrder` (cùng hệ quy chiếu với `Locator.locations.progression`). Với CBZ
thì quy theo `page://N`.

Hai quy tắc khác nhau, và đó là chủ ý:

- `%chap_title` (không hậu tố) — lấy `Locator.title` do **Readium** giải, tức đúng giá trị thanh công cụ đang
  hiển thị. Overlay và chrome không thể nói hai chuyện khác nhau về cùng một vị trí đọc. Chỉ khi engine không
  báo gì (CBZ, hoặc locator thiếu `title`) mới rơi về chỉ mục mục lục.
- `%chap_title_N` — mục **sâu nhất** có cấp ≤ N phủ vị trí đang đọc, do chỉ mục quyết định. Engine không có
  khái niệm cấp mục lục nên không dùng được ở đây.
- `%chap_num_N` / `%chap_count_N` — chỉ đếm mục **đúng cấp** N. Nếu đếm "cấp ≤ N" thì mục lục hai tầng
  (Phần → Chương) sẽ ra "chương 13/26" cho sách 2 phần 24 chương, vì tính cả tiêu đề phần.

Không hậu tố nghĩa là **cấp sâu nhất**, không phải cấp 1 — nếu không, `%chap_pct` và `%chap_time_left`
trên cùng một dòng sẽ nói về hai phạm vi khác nhau.

**Đứng trước mục lục thì không bịa.** Mục lục thường bắt đầu từ chương 1, còn phần đầu sách (bìa, trang tên,
phần mở đầu) nằm trước đó. `titleAt` trả **chuỗi rỗng** trong trường hợp này để dòng tự ẩn. Bản đầu tiên rơi
về `scoped.first()` và như vậy là bịa: đang ở trang bìa mà `%chap_title` khẳng định đang ở "HIỆN TẠI" — đã
quan sát đúng như vậy trên máy thật.

#### Ước lượng thời gian: từ chối khi dữ liệu không đáng tin

`BookendsSnapshot.avgSecondsPerPage` trả `null` — và do đó `%book_time_left`, `%speed`, `%avg_page_time`
đều rỗng, dòng tự ẩn — khi dữ liệu không đủ tin, theo hai chốt:

| Chốt | Ngưỡng | Vì sao |
| --- | --- | --- |
| Mẫu quá nhỏ | < 5 trang | Chia thời gian tích luỹ của cả phiên cho 2 trang là chia cho nhiễu |
| Tốc độ phi thực tế | ngoài 2–300 giây/trang | Bộ theo dõi phiên đọc tính theo thời gian màn hình bật |

Chốt thứ hai có từ quan sát thật: một cuốn 297 trang đọc 11 trang nhưng tích 4,5 giờ cho 25 phút/trang, và
`%book_time_left` hiện **"118h 35m còn lại"**. Cố ý **không kẹp** về ngưỡng — kẹp chỉ tạo ra một con số sai
khác (5 phút/trang vẫn ra "47h"). Đây là chuyện "chưa đo được", không phải "đo được nhưng cần chỉnh".

Gốc rễ nằm ở bộ theo dõi phiên đọc (`ReaderViewModel.recordUserInteraction` tích thời gian theo màn hình bật),
không phải ở Bookends; sửa nó là việc riêng.

#### Cấu hình

`BookendsPreferencesManager` là nguồn sự thật duy nhất, lưu cả `BookendsSettings` thành **một khối JSON**
(`BookendsSettingsCodec`) trong `booxbook_bookends_prefs`. Mã hoá nằm ở `:core:model` để
`:feature:reader` không phải kéo `kotlinx-serialization` chỉ vì một chuỗi JSON.

Cố ý **không** đi qua `ReaderUiState`: các cài đặt reader hiện có (`tapZoneMode`, `pageTurnEffect`…)
được `SettingsViewModel` ghi vào SharedPreferences nhưng `ReaderViewModel` không bao giờ đọc lại, nên đổi
ở tab Cài đặt không có tác dụng trong màn đọc. Bookends đọc và ghi qua đúng một đối tượng nên màn cấu
hình và overlay luôn khớp.

#### Thứ tự lớp trong cây Compose

```
Scaffold
└── Box (insets đã cắt)
    ├── canvas đọc (CbzReaderComponent | EpubReaderContainer)
    ├── PageTurnFlipOverlay
    ├── BookendsOverlay          ← trên chữ, dưới chrome; không gắn pointerInput
    └── TapZonePreviewOverlay
    AnimatedReaderTopBar / FloatingReaderToolbar / sheets  ← nằm ngoài Box insets
```

Overlay ẩn khi `isControlsVisible` hoặc đang mở sheet: nếu không, chữ overlay và thanh công cụ sẽ tranh
nhau cùng một dải màn hình.

#### Metadata sách: ba tầng, và vì sao phải có tầng thứ hai

Các token `%series`, `%description`, `%lang`, `%tags`, `%rating` cần dữ liệu mà `Book` không có sẵn:

```
OPF của tệp ─► BookStorageManager.parseOpfMetadata ─► BookEntity (5 cột mới)
                                                          │
LibraryViewModel.init ─► backfillMetadata()  ──────────────┘  (chỉ cho bản ghi còn NULL)
                                                          │
BookDetailScreen ─► BookReviewDao (book_reviews) ─────────┘  (do người đọc tạo)
                                                          ▼
                     ReaderViewModel.bookendsContext ─► BookendsSnapshot
```

Đánh giá nằm ở bảng riêng vì nó **do người đọc tạo**, không phải metadata của tệp — quét lại OPF không được phép
ghi đè. Còn `NULL` khác `""` ở năm cột metadata là để biết bản ghi nào cần quét lại; xem `docs/database.md`.

#### Định vị ba tầng, và vì sao không cần smart ellipsis

| Tầng | Ở đâu | Dùng khi nào |
|---|---|---|
| Lề chung | `BookendsPreset.marginTopDp`… | Chừa chỗ cho thanh trạng thái và thanh điều hướng |
| Lề riêng của vùng | `BookendsGroup.extraMargin*Dp` | Một vùng cần chừa thêm chỗ — ví dụ góc trên trái đè lên dòng đầu của trang |
| Nudge theo dòng | `BookendsLine.nudgeXDp/nudgeYDp` | Tinh chỉnh từng pixel; dùng `offset` nên không đẩy các dòng khác |

Ba vùng cùng hàng **chia đều bề rộng** (`weight(1f)`), nên chúng **không thể chồng nhau** về mặt cấu trúc. Đó là
lý do bản này không cần cơ chế tự cắt chữ kèm dấu ba chấm của bản gốc: không có gì để cắt. Đổi lại, một dòng
quá dài sẽ bị cắt ở một phần ba bề rộng kể cả khi hai vùng bên cạnh đang trống — chấp nhận được vì preset dựng
sẵn đều đặt dòng dài ở vùng giữa hoặc dùng `%token{N}` để tự đặt giới hạn.

`truncationGapDp` vì vậy được dùng làm **khoảng cách tối thiểu** giữa hai vùng cùng hàng, thay vì cho việc cắt
chữ.

#### Trình soạn thảo

`BookendsSettingsSheet` mở được từ **hai chỗ**: sheet cài đặt trong màn đọc, và tab Cài đặt (mục "Bookends").
Ở tab Cài đặt không có sách nào đang mở nên `snapshot` là `null` và bản xem trước hiện lời nhắc — nhưng cấu
hình vẫn sửa được đầy đủ.

`BookendsTokenCatalogue` là **tài liệu duy nhất** về ~70 token, và là nguồn cho bảng chọn "Chèn token". Có một
unit test duyệt qua toàn bộ danh mục để chốt rằng nó không quảng cáo token không tồn tại — danh mục là thứ
người dùng nhìn thấy, nên nó không được phép sai.

Ô nhập dùng `TextFieldValue` chứ không dùng `String`, vì bảng chọn token cần biết con trỏ đang ở đâu để chèn
đúng chỗ; với `String` thì chỉ có thể nối vào cuối.

#### Lề trang: ba mức, làm ở tầng Compose

| Điều khiển | Trường |
|---|---|
| Lề trên | `ReaderPreferences.marginTopDp` |
| Lề dưới | `ReaderPreferences.marginBottomDp` |
| Lề trái & phải (chung một mức) | `ReaderPreferences.marginHorizontalDp` |

**Trên và dưới riêng, trái và phải chung** là yêu cầu thật: chữ chừa hai bên không đều trông như lỗi, còn
 trên/dưới thì cần khác nhau vì trên phải né thanh trạng thái và overlay, dưới phải né thanh công cụ.

Lề làm bằng **padding Compose** cho cả ba định dạng, không dùng `pageMargins` của Readium — Readium chỉ có **một**
hệ số cho cả bốn phía (`EpubPreferences` không có type `PageMargins` riêng, không có API per-side; đã kiểm bằng
`javap`). `ReaderPreferences.pageMargins` vì vậy vẫn tồn tại nhưng **không dùng**.

##### Mắt xích bắt buộc: báo Readium biết vùng đọc đã đổi kích thước

Padding làm WebView đổi kích thước, nhưng **Readium không tự biết** — pager giữ nguyên bề rộng trang cũ và chữ
chồng lên nhau, tràn ra ngoài khung. `EpubReaderContainer` bù bằng `Modifier.onSizeChanged` trên host view:

```kotlin
.onSizeChanged { size ->
    if (size == lastReportedSize) return@onSizeChanged
    val firstLayout = lastReportedSize == null
    lastReportedSize = size
    if (firstLayout) return@onSizeChanged   // bố cục đầu đã có preferences đúng từ factory
    activeFragment?.takeIf { it.isAdded }?.submitPreferences(epubEngine.buildEpubPreferences(preferences))
}
```

`submitPreferences` là **API công khai duy nhất** khiến Readium dàn lại trang (không có `invalidatePagination`
hay tương đương trên `EpubNavigatorFragment` hay `R2ViewPager`).

##### Ba lần thử, ghi lại để không lặp lại

| Lần | Cách | Kết quả trên máy |
|---|---|---|
| 1 | Padding Compose, không báo gì | Đổi lề giữa phiên: chữ chồng, tràn phải. Mở sách mới thì đúng → padding đúng, Readium không biết |
| 2 | Padding + dựng lại `EpubNavigatorFragment` | Dàn lại được nhưng **canvas trắng** |
| 3 | `pageMargins` của Readium | Đúng, nhưng chỉ một mức cho cả bốn phía |
| 4 | Padding + `onSizeChanged` → `submitPreferences` | Đúng, và đủ ba mức |

Lần 2 là lúc đáng lẽ phải dừng và đặt câu hỏi về kiến trúc: dựng lại navigator là chống lại framework, trong khi
thiếu đúng một dòng báo cho nó biết vùng đọc đã đổi kích thước.

#### `ReaderThemePalette` — một định nghĩa cho hai phía

Trang do Readium vẽ trong WebView, vùng xung quanh do Compose vẽ. Nếu mỗi bên tự chọn màu thì hai thứ lệch nhau
và người đọc thấy một đường ranh giới mờ quanh trang. `ReaderThemePalette` giữ nền/chữ của cả bốn theme;
`EpubPreferencesFactory` truyền thẳng vào `EpubPreferences`, và giao diện Compose đọc cùng hằng số đó.

#### Bookends neo theo màn đọc, không theo trang

`BookendsOverlay` nằm **ngoài** hộp đã chừa lề, cùng cấp với hộp đó. Nhờ vậy đổi lề không làm lớp thông tin trôi
theo. Đặt nó trong hộp đó sẽ khiến cả overlay chạy theo lề — đã quan sát trên máy.

`ReadingFrameLayer` và `TapZonePreviewOverlay` thì nằm **trong** hộp: viền là viền *của trang*, còn bản xem trước
vùng chạm phải khớp với vùng chạm thật của canvas.

#### Viền khung

`ReadingFrameLayer` vẽ bằng `Canvas` + `Stroke(pathEffect)` chứ không dùng `Modifier.border` — `border` không có
kiểu nét đứt. Ba kiểu nét: liền, đứt, chấm (chấm = gạch dài 0 + `StrokeCap.Round`). Độ dài gạch tính theo bề
dày nét, vì nét dày mà gạch ngắn thì các đoạn dính vào nhau thành một đường liền.

Viền **không chiếm chỗ**: nó chỉ vẽ lên trên vùng đọc. Nhờ vậy hai cài đặt độc lập — muốn chữ không chạm viền thì
tăng lề, còn viền vẫn nằm nguyên chỗ đã đặt. `insetDp` cho phép âm để đẩy viền ra ngoài vùng đọc.

Màu là bảng chọn nhỏ (`AUTO` / `ACCENT` / `WARM`) chứ không phải color picker tự do: trên màn e-ink một màu tuỳ
ý rất dễ ra không đủ tương phản, và người đọc chỉ phát hiện ra khi đã chọn xong.

#### `ReaderPreferencesManager` — sửa lỗi cài đặt không dính

Trước đây `SettingsViewModel` ghi thẳng `booxbook_reader_prefs` còn `ReaderViewModel` **không bao giờ đọc lại**, nên
đổi vùng chạm / hiệu ứng lật trang / rung ở tab Cài đặt không có tác dụng gì khi đang đọc. Giờ cả hai phía đọc/ghi
qua `ReaderPreferencesManager`, và **tên khoá SharedPreferences được giữ nguyên** để cài đặt đang có của người
dùng không bị đặt lại về mặc định.

#### Chưa làm

- **Preset gallery**: bản gốc tải preset từ một repo GitHub. Chưa có nguồn nào để trỏ tới, nên chưa làm — và làm
  một gallery rỗng thì vô nghĩa.
- **Cử chỉ đổi preset / ẩn hiện nhanh**: sẽ phải sửa đường vào của tap-zone trong `EpubReaderContainer`; tách
  thành việc riêng vì nó đụng vào logic lật trang đang chạy tốt.
- **Lề bốn phía cho EPUB/AZW3**: đã làm được ba mức (trên, dưới, trái+phải chung). Tách riêng trái và phải thì
  cần thêm một trường nữa trong `ReaderPreferences` và một thanh trượt nữa — không có rào cản kỹ thuật, chỉ là
  chưa cần.
- **Viền khung không tự bám theo lề**: viền vẽ ở mép vùng đọc, còn chữ nằm sau lề; `insetDp` chỉnh tay được.
- **`RADIAL`**: đã khai báo là không làm, kèm lý do ngay trong `BookendsBarStyle` — vòng tròn không biểu diễn
  được trên một thanh tuyến tính, muốn có thì phải làm một component tròn riêng ở góc màn hình.
- **`%chap_pages`/`%chap_read` vẫn là ước lượng** (`spanLength × pageCount`). Với mục lục phẳng, mỗi chương là
  một tệp nên sai số khoảng ±1 trang; với mục lục hai tầng thì ước lượng đúng theo tổng. Dùng
  `Publication.positionsByReadingOrder()` sẽ chính xác tuyệt đối, nhưng phải thêm một lời gọi API mỗi lần mở
  sách và một đường truyền tham số mới — đổi lại chỉ hơn kém một trang.
- **Token `%opened`, `%quote`, `%quote_source`, `%file_num`, `%file_count`** trả rỗng: dữ liệu tương ứng (mốc mở
  sách gần nhất, trích dẫn ngẫu nhiên, vị trí tệp trong thư mục) chưa được theo dõi. Dòng chứa chúng tự ẩn.
- **Thanh công cụ trong màn đọc** vẫn hiển thị cặp số trang logic cũ.

---

## 4. Lifecycle, Memory & Thread Safety

Reading engines consume significant memory, file descriptors, and background coroutines. BooxBook applies strict lifecycle guarantees across every layer.

### 1. Deterministic Resource Teardown (`closeBookSync()`)

To avoid leaking open `ZipFile` handles, Readium publication contexts, or memory mapped buffers when navigating away, `ReaderViewModel` funnels every teardown path through a single public `release()`:

```kotlin
fun release() {
    // Also cancels the perpetual periodic-flush ticker started by startReadingSession().
    flushReadingSession(isEnding = true)
    progressSaveJob?.cancel()
    annotationsJob?.cancel()
    ttsEngineWrapper.stop()
    epubReaderEngine.closeBookSync()
    azw3ReaderEngine.closeBookSync()
    cbzReaderEngine.closeBookSync()
}

override fun onCleared() {
    super.onCleared()
    release()
}
```

`release()` is idempotent and `onCleared()` merely delegates to it, so teardown can also be driven explicitly instead of through the Android lifecycle — tests depend on that to stop reading-session tracking deterministically. `viewModelScope` is deliberately *not* cancelled inside `release()`, because the final reading session still has to be recorded through it; the framework cancels the scope right after `onCleared()`.

Inside the engines:
- `CbzReaderEngine.closeBookSync()` closes open `CbzArchive` instances and clears the active book reference.
- `EpubReaderEngine.closeBookSync()` closes active Readium `Publication` handles and sets references to `null`.

### 2. Debounced Progress Auto-Saving (500ms)

Every page turn or slider scrub invokes `onPageChanged()`. To prevent disk I/O bottlenecks and SQLite write-lock contention, progress writes are debounced:

```kotlin
progressSaveJob?.cancel()
progressSaveJob = viewModelScope.launch(ioDispatcher) {
    delay(500)
    val currentBook = _uiState.value.book ?: return@launch
    val progress = ReadingProgress(
        bookId = currentBook.id,
        locator = loc,
        percentage = percent,
        currentPage = pageIndex,
        totalPages = totalPages
    )
    bookRepository.saveReadingProgress(progress)
    bookRepository.updateLastRead(currentBook.id)
}
```

Reading-session time tracking is flushed on a separate cadence. `startReadingSession()` launches a **perpetual** ticker on `viewModelScope` that re-arms itself every 60 seconds for as long as the ViewModel lives:

```kotlin
periodicFlushJob = viewModelScope.launch(ioDispatcher) {
    // Perpetual by design: the loop always keeps one `delay()` queued, so it never runs out of
    // work and is only stopped by `release()` or `flushReadingSession(isEnding = true)`.
    while (isActive) {
        delay(PERIODIC_FLUSH_INTERVAL_MS) // 60_000
        flushReadingSession(isEnding = false)
    }
}
```

Because the ticker always leaves one task pending on whichever dispatcher it runs on, it is hostile to virtual-time test schedulers: any `advanceUntilIdle()` (including the implicit drain at the end of `runTest`) will spin forever while the ticker is alive. See [Testing Strategy](#5-testing-strategy) for the required pattern.

### 3. Fragment Container Safety & Listener Cleanup

In `EpubReaderContainer.kt`:
1. `rememberSaveable { View.generateViewId() }` ensures the id assigned to the host `FrameLayout` survives configuration changes, so a restored navigator can be matched back to it.
2. `DisposableEffect(activeFragment)` deregisters the Readium `InputListener` and removes the fragment when the Composable leaves the composition tree:
   ```kotlin
   onDispose {
       onNavigatorReady(null)
       fragment.removeInputListener(inputListener)
       val existing = fragmentManager.findFragmentById(containerId)
       if (existing != null && !activity.isFinishing && !activity.isDestroyed) {
           fragmentManager.beginTransaction().remove(existing).commitAllowingStateLoss()
       }
   }
   ```
3. `MainActivity` must install the Readium `FragmentFactory` before `super.onCreate()`, otherwise restoration crashes with `Fragment$InstantiationException`.

> Reference: `Yuneko-dev/Nekori` avoids this whole class of problems by owning a plain `WebView` from the reader ViewModel and rendering paged content itself (CSS columns + JS), with the Activity only attaching the viewer's `FrameLayout`. BooxBook keeps Readium for EPUB parsing/locators and therefore must host its Fragment carefully, as described above.

### 4. Edge-to-Edge System Bar Insets & Content Safe-Area

The Scaffold remains edge-to-edge (`contentWindowInsets = WindowInsets(0, 0, 0, 0)`) so the theme background seamlessly extends behind system bars and display cutouts.

However, to prevent book text and comic panels from being obscured by status bar icons (clock, battery, camera hole) or the bottom navigation bar gesture pill, the reading canvas, flip transition, and tap-zone preview overlay are wrapped in a safe-area container using Compose WindowInsets:

```kotlin
val readerInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

Box(
    modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(readerInsets)
) {
    // EPUB / AZW3 Readium Navigator or CBZ LazyColumn
    ...
    // Page-turn flip overlay
    ...
    // Tap-zone preview overlay
    ...
}
```

Because the canvas bounds are strictly inset to the readable area:
- Readium paginates text precisely within the safe rectangle (0 text clipped by camera cutouts or system bars).
- Tap zones start right at the edges of the visible canvas; the Kindle-style top menu strip (`y <= 6%`) is 100% accessible to user touch immediately below the status bar.
- Reading chrome overlays (`AnimatedReaderTopBar` and `FloatingReaderToolbar`) float at screen edges using `statusBarsPadding()` and `navigationBarsPadding()`.

---

## 5. Testing Strategy

The Reader test suite is located in `feature/reader/src/test/java/com/booxbook/feature/reader/ReaderViewModelTest.kt`. It uses **Robolectric** and **Kotlin Coroutines Test** to evaluate presentation logic, engine coordination, and persistence.

### 1. Test Setup & Dispatcher Injection

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeRepository: FakeReaderBookRepository
    private lateinit var epubEngine: EpubReaderEngine
    private lateinit var cbzEngine: CbzReaderEngine
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        fakeRepository = FakeReaderBookRepository()

        // Create a valid mock CBZ file on-the-fly
        val sampleCbzFile = tempFolder.newFile("sample.cbz")
        ZipOutputStream(FileOutputStream(sampleCbzFile)).use { zos ->
            zos.putNextEntry(ZipEntry("01.jpg"))
            zos.write("page 1".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("02.jpg"))
            zos.write("page 2".toByteArray())
            zos.closeEntry()
        }

        viewModel = ReaderViewModel(fakeRepository, epubEngine, cbzEngine).apply {
            ioDispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```

### 2. Virtual Time Control — never call `advanceUntilIdle()`

`ReaderViewModel` runs a perpetual 60-second flush ticker (see [section 4.2](#2-debounced-progress-auto-saving-500ms)). While that ticker is alive the coroutine test scheduler is **never** idle, so:

- `advanceUntilIdle()` spins forever, because the ticker always queues one more event.
- `runTest` itself drains the scheduler on the way out (`advanceUntilIdleOr { false }`), so even a test body that never calls `advanceUntilIdle()` hangs forever once it has called `loadBook()`.
- The `runTest` timeout cannot save you: it cancels the *test* scope, but the ticker lives on `viewModelScope`, which is a foreign scope.

Two rules keep the suite terminating, both enforced by helpers in the test class:

```kotlin
/** Runs [body] and always tears the ViewModel down before `runTest` returns. */
private fun readerTest(body: suspend TestScope.() -> Unit) = runTest {
    try {
        body()
    } finally {
        viewModel.release()   // cancels the ticker so runTest's scheduler drain can finish
    }
}

/** Runs everything already due at the current virtual time. Bounded, unlike `advanceUntilIdle()`. */
private fun settle() = testDispatcher.scheduler.runCurrent()

/** Moves virtual time forward by [millis], running every task that becomes due in that window. */
private fun advanceBy(millis: Long) {
    testDispatcher.scheduler.advanceTimeBy(millis)
    testDispatcher.scheduler.runCurrent()
}
```

Every test declares `= readerTest { ... }` instead of `= runTest { ... }`, and uses `settle()` / `advanceBy(n)` for time. `runCurrent()` drains the whole no-delay chain that `loadBook()` kicks off (repository reads, engine open, state updates), so it replaces `advanceUntilIdle()` in every case here.

Note that `advanceTimeBy` deliberately does *not* run tasks scheduled at exactly the target time, which is why the helpers pair it with `runCurrent()`.

### 3. Key Test Scenarios

1. **Missing Book Error Handling:**
   Verifies that loading a non-existent book ID turns off `isLoading` and sets `errorMessage = "Không tìm thấy cuốn sách này"`.
2. **CBZ Book Initialization:**
   Asserts that `loadBook()` opens the CBZ archive, counts total pages, sets format to `BookFormat.CBZ`, and extracts the Table of Contents.
3. **Debounced Reading Progress Auto-Save:**
   Calls `viewModel.onPageChanged(...)`, verifies that repository progress is **not** written after `advanceBy(200)`, then confirms it **is** written after the 500ms debounce elapses via a further `advanceBy(350)`, asserting the correct page, locator and percentage.
4. **Bookmark Toggling & Deletion:**
   Verifies that calling `toggleBookmark()` adds an `AnnotationType.BOOKMARK` entry to the repository, updates `isCurrentLocationBookmarked = true`, and that calling `toggleBookmark()` again removes the bookmark.
5. **Reader Preferences & Clamping:**
   Asserts that `updateFontSize(delta)` clamps within $[0.7, 2.5]$, `updateFontFamily(font)` sets the desired typeface, and `updateThemePreset(preset)` correctly synchronizes the `isDarkMode` flag.
6. **Sheet Open & Dismiss Flow:**
   Validates state transitions between `ActiveReaderSheet.TOC`, `SETTINGS`, `BOOKMARKS`, and `null`.
7. **Opening Phase Walk:**
   Asserts `loadBook()` starts at `LOADING_BOOK` with `book == null`, reaches `BUILDING_CANVAS` with the book populated (so the opening screen can already show the cover) once the engine is open, and only becomes `READY` after `onCanvasReady()`.
8. **Canvas-Ready Fallback:**
   Advances virtual time past `CANVAS_READY_FALLBACK_MS` without any readiness signal and asserts the opening screen still closes itself.
9. **Error Never Hides Behind the Overlay:**
   A failed `loadBook()` leaves `isPreparing == false`, so the error screen is never covered by the opening overlay.
10. **Bookmark Feedback:**
    Collects `viewModel.feedback` and asserts a toggle emits `BookmarkAdded` followed by `BookmarkRemoved`.
11. **Delete → Undo Round Trip:**
    Asserts `deleteAnnotation()` emits `AnnotationDeleted` carrying the deleted record, and that `restoreAnnotation()` puts the same locator/note back and emits `AnnotationRestored`.
12. **Silent Stop:**
    `stopTts()` with no active session emits no snackbar, so closing the mini player twice cannot produce a stale "đã dừng" message.
13. **Snackbar Wording (`ReaderFeedbackTest`):**
    Locks the message, action label and undo payload of every `ReaderFeedback` variant to `toSnackbar()`, including that restored feedback never offers a second undo.
