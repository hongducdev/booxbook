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
