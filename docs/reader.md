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
|   ├── FloatingReaderToolbar (32.dp pill, Progress Slider, TOC, Settings, Bookmarks)    |
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
)
```

---

## 2. Unified Canvas Integration

BooxBook dynamically routes rendering based on `BookFormat` inside `ReaderScreen.kt`:

```kotlin
when {
    uiState.format == BookFormat.CBZ -> {
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
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    else -> {
        // EPUB / AZW3 Readium Navigator
        EpubReaderContainer(
            epubEngine = viewModel.epubReaderEngine,
            preferences = uiState.preferences,
            initialLocatorJson = uiState.currentLocator,
            onLocatorChanged = { locator ->
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
            onCenterTap = { viewModel.toggleControls() },
            onNavigatorReady = { nav -> activeEpubNavigator = nav },
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

---

### EPUB Rendering: `EpubReaderContainer` & Readium Integration

Reflowable and fixed-layout EPUB publications are rendered using Readium's `EpubNavigatorFragment` embedded into Compose via an interoperability container (`EpubReaderContainer.kt`).

#### 1. Fragment Lifecycle and Container Retention
To ensure crash-free execution during orientation changes and recompositions, the container dynamically generates and remembers a stable view ID:

```kotlin
val activity = LocalContext.current as? FragmentActivity ?: return
val fragmentManager = activity.supportFragmentManager
val containerId = rememberSaveable { View.generateViewId() }
```

The navigator fragment is instantiated via `epubEngine.createFragmentFactory()` with saved locator restoration:

```kotlin
val navigatorFragment = remember {
    val factory = epubEngine.createFragmentFactory(
        initialLocator = initialLocator,
        preferences = preferences
    )
    if (factory != null) {
        fragmentManager.fragmentFactory = factory
        fragmentManager.fragmentFactory.instantiate(
            activity.classLoader,
            EpubNavigatorFragment::class.java.name
        ) as EpubNavigatorFragment
    } else {
        null
    }
}
```

The underlying `FragmentContainerView` mounts the fragment through `fragmentManager.beginTransaction().replace(id, navigatorFragment).commitAllowingStateLoss()`.

#### 2. Central Tap Detection & Gesture Partitioning
To prevent conflict between page-flipping taps and reading chrome toggling, `EpubReaderContainer` registers an `InputListener` with the Readium fragment:

```kotlin
val inputListener = object : InputListener {
    override fun onTap(event: TapEvent): Boolean {
        val point = event.point
        val view = fragment?.view ?: return false
        val width = view.width.toFloat()
        if (width <= 0f) return false

        // Central 50% zone triggers reading chrome; edge 25% zones flip pages
        val relativeX = point.x / width
        if (relativeX in 0.25f..0.75f) {
            onCenterTap()
            return true
        }
        return false
    }

    override fun onDrag(event: DragEvent): Boolean = false
    override fun onKey(event: KeyEvent): Boolean = false
}
```

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
LaunchedEffect(preferences, navigatorFragment) {
    val fragment = navigatorFragment ?: return@LaunchedEffect
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
- **Scrubbing Slider:** Allows seeking across total pages. Discrete page changes trigger upon release (`onValueChangeFinished`) to prevent intermediate navigation spikes:
  ```kotlin
  Slider(
      value = sliderPosition,
      onValueChange = { sliderPosition = it },
      onValueChangeFinished = { onSeekToPage(sliderPosition.toInt()) },
      valueRange = 0f..(totalPages - 1).toFloat()
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

## 4. Lifecycle, Memory & Thread Safety

Reading engines consume significant memory, file descriptors, and background coroutines. BooxBook applies strict lifecycle guarantees across every layer.

### 1. Deterministic Resource Teardown (`closeBookSync()`)

To avoid leaking open `ZipFile` handles, Readium publication contexts, or memory mapped buffers when navigating away, `ReaderViewModel` invokes synchronous teardown in `onCleared()`:

```kotlin
override fun onCleared() {
    super.onCleared()
    progressSaveJob?.cancel()
    annotationsJob?.cancel()
    epubReaderEngine.closeBookSync()
    cbzReaderEngine.closeBookSync()
}
```

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

### 3. Fragment Container Safety & Listener Cleanup

In `EpubReaderContainer.kt`:
1. `rememberSaveable { View.generateViewId() }` ensures the view ID assigned to `FragmentContainerView` survives configuration changes.
2. `DisposableEffect` deregisters the Readium `InputListener` and cleans up the fragment transaction when the Composable leaves the composition tree:
   ```kotlin
   onDispose {
       onNavigatorReady(null)
       fragment?.removeInputListener(inputListener)
       val existing = fragmentManager.findFragmentById(containerId)
       if (existing != null && !activity.isFinishing && !activity.isDestroyed) {
           fragmentManager.beginTransaction().remove(existing).commitAllowingStateLoss()
       }
   }
   ```

### 4. True Immersive Edge-to-Edge Mode

To provide an authentic reading experience, the Scaffold applies zero content insets:

```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize().background(backgroundColor),
    containerColor = backgroundColor,
    contentWindowInsets = WindowInsets(0, 0, 0, 0)
)
```

The underlying reading canvas (EPUB or CBZ) extends edge-to-edge behind translucent system status and navigation bars. Overlays (`AnimatedReaderTopBar` and `FloatingReaderToolbar`) apply explicit `statusBarsPadding()` and `navigationBarsPadding()` respectively, preventing UI overlap with system cutouts or gesture pills.

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

### 2. Key Test Scenarios

1. **Missing Book Error Handling:**
   Verifies that loading a non-existent book ID turns off `isLoading` and sets `errorMessage = "Không tìm thấy cuốn sách này"`.
2. **CBZ Book Initialization:**
   Asserts that `loadBook()` opens the CBZ archive, counts total pages, sets format to `BookFormat.CBZ`, and extracts the Table of Contents.
3. **Debounced Reading Progress Auto-Save:**
   Calls `viewModel.onPageChanged(...)`, verifies that repository progress is **not** immediately written before 500ms, advances virtual time via `testDispatcher.scheduler.advanceTimeBy(600)`, and asserts that `saveReadingProgress` is called with correct page and percentage values.
4. **Bookmark Toggling & Deletion:**
   Verifies that calling `toggleBookmark()` adds an `AnnotationType.BOOKMARK` entry to the repository, updates `isCurrentLocationBookmarked = true`, and that calling `toggleBookmark()` again removes the bookmark.
5. **Reader Preferences & Clamping:**
   Asserts that `updateFontSize(delta)` clamps within $[0.7, 2.5]$, `updateFontFamily(font)` sets the desired typeface, and `updateThemePreset(preset)` correctly synchronizes the `isDarkMode` flag.
6. **Sheet Open & Dismiss Flow:**
   Validates state transitions between `ActiveReaderSheet.TOC`, `SETTINGS`, `BOOKMARKS`, and `null`.
