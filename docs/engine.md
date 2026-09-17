# Reader Engines Architecture

Module: `:core:engine`

## 1. Architectural Overview

The `:core:engine` module provides format-agnostic reader engine abstractions and native implementations for digital reading formats in BooxBook. Built on a clean state-driven architecture, it exposes reactive Kotlin `StateFlow` streams for UI synchronization, lifecycle management, and high-performance rendering.

```
                    +----------------------+
                    |     ReaderEngine     |  (Interface)
                    +----------+-----------+
                               |
            +------------------+------------------+
            |                                     |
+-----------v-----------+             +-----------v-----------+
|   EpubReaderEngine    |             |    CbzReaderEngine    |
| (Readium Toolkit 3.x) |             | (On-demand ZIP / Coil)|
+-----------+-----------+             +-----------+-----------+
            |                                     |
    +-------v-------+                     +-------v-------+
    | Readium View  |                     |  CbzReader    |
    | (Fragment)    |                     |  (Compose UI) |
    +---------------+                     +---------------+
```

### Core Abstractions

#### `ReaderEngine` Interface
```kotlin
interface ReaderEngine {
    val supportedFormat: BookFormat
    val state: StateFlow<ReaderState>

    suspend fun openBook(book: Book): Result<Unit>
    suspend fun closeBook()
    suspend fun extractCover(book: Book, destinationFile: File): Result<File?>
}
```

Every reader engine implementation conforms to this contract:
- `supportedFormat`: Declares the specific `BookFormat` (`EPUB`, `CBZ`, etc.) handled by the engine.
- `state`: Read-only `StateFlow<ReaderState>` providing the current engine lifecycle and session state.
- `openBook(book)`: Prepares the publication/archive, parses metadata, extracts table of contents, and transitions the engine to `ReaderState.Ready`.
- `closeBook()`: Closes open file handles, cleans up transient publications/archives, cancels background tasks, and resets state to `ReaderState.Idle`.
- `extractCover(book, destinationFile)`: Extracts the cover image directly to a destination target file without requiring a full ongoing reading session.

#### `ReaderState` Sealed Interface
The engine lifecycle is modeled as a finite state machine:
- `ReaderState.Idle`: Engine is uninitialized or has closed its active publication.
- `ReaderState.Loading(val book: Book)`: Engine is actively opening and parsing the publication/archive.
- `ReaderState.Ready(val book: Book, val tableOfContents: List<TocItem>, val totalPages: Int, val currentLocator: String?, val progress: Float)`: Publication is successfully opened and ready for rendering. Exposes navigation and page metrics.
- `ReaderState.Error(val message: String, val throwable: Throwable?)`: Unrecoverable parsing or IO error occurred.

#### `TocItem` Navigation Tree
```kotlin
data class TocItem(
    val title: String,
    val href: String,
    val children: List<TocItem> = emptyList()
)
```
Hierarchical representation of the Table of Contents, supporting nested chapters and sub-sections across both EPUB and CBZ formats.

#### `ReaderPreferences`
```kotlin
data class ReaderPreferences(
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.2,
    val pageMargins: Double = 1.0,
    val isScrollMode: Boolean = false, // false = Discrete pagination (page turn)
    val fontFamily: String? = null,
    val isDarkMode: Boolean = false
)
```
Controls typography, spacing, color mode, and pagination behaviour. Defaults explicitly to **discrete pagination** (`isScrollMode = false`) optimized for e-ink reading and classic pagination.

---

## 2. EPUB Engine (`EpubReaderEngine`)

The EPUB engine integrates the **Readium Kotlin Toolkit 3.1.1**, providing standard-compliant rendering of reflowable and fixed-layout EPUB publications.

### Readium Asset Retrieval (`ReadiumAssetRetriever`)
Readium 3.x utilizes a decoupled asset abstraction. `ReadiumAssetRetriever` encapsulates publication resolution:
- **`AssetRetriever`**: Instantiated with `context.contentResolver` and `DefaultHttpClient`. Resolves local files into Readium `Asset` handles.
- **`DefaultPublicationParser`**: Coordinates parsing pipelines across container manifests (`META-INF/container.xml`, OPF package).
- **`PublicationOpener`**: Opens publication assets into active `Publication` instances (`openPublication(file: File)`).

### Lifecycle & Session Management
`EpubReaderEngine` manages the active Readium session thread-safely on `Dispatchers.IO`:
1. **Open**: Closes any prior publication, validates file existence, invokes `readiumAssetRetriever.openPublication()`, caches the active `Publication`, initializes `EpubNavigatorFactory(publication)`, maps spine links to `TocItem`, and emits `ReaderState.Ready`.
2. **Close**: Invokes `activePublication?.close()`, sets internal references to `null`, and transitions to `ReaderState.Idle`.

### Discrete Pagination (`scroll = false`)
To provide an authentic reading experience (especially on E-Ink devices), continuous vertical scrolling is disabled by default in favor of discrete page turns:

```kotlin
fun buildEpubPreferences(prefs: ReaderPreferences = ReaderPreferences()): EpubPreferences {
    return EpubPreferences(
        scroll = prefs.isScrollMode, // false = discrete page-turn
        fontSize = prefs.fontSize,
        lineHeight = prefs.lineHeight,
        pageMargins = prefs.pageMargins,
        fontFamily = prefs.fontFamily?.let { FontFamily(it) },
        theme = if (prefs.isDarkMode) Theme.DARK else Theme.LIGHT
    )
}
```

The engine provides `createFragmentFactory(...)` to instantiate `EpubNavigatorFragment` with preconfigured `EpubPreferences`, initial locators (`Locator`), and pagination listeners for synchronization with Room reading progress.

### Cover Extraction
Cover extraction leverages Readium's `pub.cover()` extension:
- If the requested book matches the currently active publication, it reuses `activePublication` directly.
- If transient (e.g. background library scan), it temporarily opens the file via `readiumAssetRetriever.openPublication()` and safely closes it in a `finally` block.
- Compresses the bitmap to JPEG (90% quality) using the **atomic temporary file write pattern** (`tempFile` -> atomic rename).

---

## 3. CBZ Engine (`CbzReaderEngine` & `CbzReaderComponent`)

Comic Book Zip (CBZ) archives contain sequential image files. The CBZ engine is built for low memory consumption, smooth rendering, and robust natural sorting.

### Architecture Components

```
+-------------------+      +-------------------+      +----------------------+
|  CbzReaderEngine  | ---> |    CbzArchive     | ---> | CbzArchiveExtractor  |
+-------------------+      +---------+---------+      +----------------------+
                                     |
                        +------------v------------+
                        |   NaturalOrderComparator|
                        |   On-demand Disk Cache  |
                        |   Atomic Temp Writes    |
                        +------------+------------+
                                     |
                        +------------v------------+
                        |   CbzReaderComponent    |
                        |   (Compose LazyColumn)  |
                        +-------------------------+
```

### Natural Sorting Algorithm (`NaturalOrderComparator`)
Standard lexicographical sorting incorrectly orders filenames containing numbers (e.g. `page1.jpg`, `page10.jpg`, `page2.jpg`).

`NaturalOrderComparator` implements human-natural alphanumeric sorting:
- **Tokenization**: Uses regex `(\d+)|(\D+)` to decompose strings into alternating digit and non-digit segments.
- **Numeric Evaluation**: Compares digit tokens as numeric values (`Long` with fallback to `BigInteger` for arbitrary-length numbers).
- **Hierarchy Preservation**: Operates on full entry paths (`chapter1/page01.jpg` vs `chapter2/page01.jpg`) preserving volume and chapter ordering.
- **Case-Insensitive Fallback**: Case-insensitive comparison for non-digit tokens.

### Memory Optimization & OOM Prevention
Uncompressing entire CBZ archives into memory causes Out-Of-Memory (OOM) crashes on large comic books (often 100MB+ with hundreds of high-resolution images).
- **On-Demand Extraction**: `CbzArchive.getPageFile(index)` only extracts a single page when requested by the UI viewport.
- **Dedicated Cache Directory**: Cache files are stored under `context.cacheDir/cbz_cache/{bookId}/page_{index}.{ext}`.
- **Cache Reuse**: If `page_{index}.ext` already exists and is non-empty, extraction is skipped entirely.
- **Direct Stream Access**: `CbzArchive.getPageInputStream(index)` allows direct ZIP entry streaming when caching to disk is unnecessary.
- **Filter Guard**: `isValidImageEntry()` rejects macOS metadata (`__MACOSX`, `._*`), system files (`.DS_Store`), directories, and unsupported file formats. Supported extensions: `jpg`, `jpeg`, `png`, `webp`, `gif`, `bmp`, `avif`.

### Atomic Temporary File Write Pattern
To prevent race conditions where Coil or another consumer reads a partially-written file:
1. Write incoming stream to a temporary file: `File.createTempFile("cbz_tmp_${index}_", ".tmp", cacheDir)`.
2. Flush and close streams.
3. Atomically rename `tempFile.renameTo(cachedFile)`.
4. Fallback to `copyTo(overwrite = true)` and delete `tempFile` if cross-filesystem rename fails.
5. In case of any exception, clean up `tempFile.delete()`.

### Jetpack Compose Viewer (`CbzReaderComponent`)
`CbzReaderComponent` delivers a Webtoon/continuous-scroll comic reading experience:
- **Continuous Vertical Scroll**: Rendered via `LazyColumn` with keyed items (`archive.pages[index].entryName`) for smooth recycling.
- **Pinch-to-Zoom (1.0x - 4.0x)**: Implemented using `pointerInput` with `detectTransformGestures`. Calculates zoom scale clamped between `1.0f` and `4.0f`.
- **Dynamic Orientation & Bounds Clamping**:
  - Uses `BoxWithConstraints` to obtain real-time pixel dimensions (`constraints.maxWidth`, `constraints.maxHeight`).
  - Clamps pan offsets dynamically:
    $$\text{maxOffsetX} = \frac{\text{maxWidthPx} \times (\text{scale} - 1)}{2}$$
    $$\text{maxOffsetY} = \frac{\text{maxHeightPx} \times (\text{scale} - 1)}{2}$$
- **Double-Tap Zoom Toggle**: Double-tap toggles between normal `1.0f` scale and `2.5f` zoom.
- **Scroll vs. Pan Arbitration**: While zoomed (`scale > 1.05f`), `LazyColumn`'s `userScrollEnabled` is set to `false`, allowing the user to freely pan around the enlarged image without triggering page scroll.
- **Floating Page Indicator**:
  - Displays discrete pill badge (`Current Page / Total Pages`) anchored at `Alignment.BottomCenter`.
  - Driven by `snapshotFlow { listState.firstVisibleItemIndex }`.
  - **Debounced Dismiss**: Shows on scroll, automatically fades out via `AnimatedVisibility(fadeIn, fadeOut)` after a 2-second idle period. Active dismiss coroutine job is cancelled and rescheduled on each scroll update.
- **Coil 3 Image Loading (`CbzPageItem`)**:
  - Extracts the page asynchronously on `Dispatchers.IO` using Compose `produceState`.
  - Loads via `AsyncImage` with `crossfade(true)` and `ContentScale.FillWidth`.
  - Displays a clean loading placeholder with `CircularProgressIndicator` while the page is extracting.

---

## 4. AZW3 Engine (`Azw3ReaderEngine` & Libmobi NDK Bridge)

The AZW3 engine unlocks Amazon Kindle KF8 / AZW3 formats without external converters:

### C++ Native Interface (`libmobi` + JNI)
- **`azw3_bridge.cpp`**: C++ JNI bridge linking `libmobi` C99 core and Android NDK `zlib`.
- **`nativeConvertAzw3ToEpub`**: Decodes KF8 palm records, reconstructs OPF manifest via `opf.c`, packages standard EPUB3 ZIP archive in cache.
- **`nativeIsDrmProtected`**: Detects Amazon Kindle DRM locks and aborts conversion gracefully with a descriptive security message.
- **`nativeExtractCover`**: Decodes EXTH header `EXTH_COVEROFFSET` to extract cover images directly from AZW3 files without full conversion.

### Architecture & Smart Caching
- **`Azw3Converter`**: Manages native library loading, SHA-256 fingerprint caching (`azw3_cache/`), and automatic LRU disk cleanup when cache exceeds 250 MB.
- **`ReadiumReaderEngine` Interface**: Shared interface implemented by both `EpubReaderEngine` and `Azw3ReaderEngine` for Readium navigation, preferences, and fragment instantiation.

---

## 5. Dependency Injection (`EngineModule`)

Engines are wired into the application graph using Hilt:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object EngineModule {

    @Provides
    @Singleton
    fun provideEngineMap(
        epubEngine: EpubReaderEngine,
        azw3Engine: Azw3ReaderEngine,
        cbzEngine: CbzReaderEngine
    ): Map<BookFormat, @JvmSuppressWildcards ReaderEngine> {
        return mapOf(
            BookFormat.EPUB to epubEngine,
            BookFormat.AZW3 to azw3Engine,
            BookFormat.CBZ to cbzEngine
        )
    }
}
```

### Extensibility Pattern
The `Map<BookFormat, ReaderEngine>` multi-binding provides an Open-Closed architecture:
- Format dispatchers (e.g. `ReaderViewModel`) dynamically resolve the proper engine for any book via `engineMap[book.format]`.

---

## 6. Verification & Testing

Unit test coverage verifies core engine mechanics:
- `NaturalOrderComparatorTest`: Alphanumeric ordering, chapter prefixes (`Ch1_p02` vs `Ch1_p10`), multi-digit numbers, case handling.
- `CbzArchiveExtractorTest`: Archive extraction, file filtering (`__MACOSX`, non-image), cover extraction, atomic cache write.
- `EpubPreferencesTest`: Default pagination validation (`scroll = false`), state machine transitions (`Idle` -> `Loading` -> `Ready`), and hierarchical `TocItem` structures.
- `Azw3ConverterTest`: Cache hit validation, missing file error propagation, DRM detection, and format preference defaults.
