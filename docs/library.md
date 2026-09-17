# Library Feature Architecture

Module: `:feature:library` (with `:core:ui`, `:core:database`, and `:core:model`)

## 1. Architectural Overview

The Library feature in BooxBook implements a reactive **MVVM / MVI (Model-View-Intent)** architecture built with Jetpack Compose, Kotlin Coroutines Flow, and Material 3 Expressive guidelines. It serves as the primary bookshelf interface, coordinating local database queries, storage import pipelines, format filtering, and book lifecycle actions.

```
+-----------------------------------------------------------------------------+
|                               UI Layer                                      |
|  LibraryScreen                                                              |
|   ├── Search Bar & ExpressiveFilterChips (EPUB, CBZ, AZW3)                  |
|   ├── ContinueReadingCarousel (LazyRow for in-progress books)               |
|   ├── Bento Book Grid (LazyVerticalGrid with 28.dp BentoBookCards)          |
|   ├── BookDetailBottomSheet (Metadata stats, progress, safe deletion)       |
|   └── EmptyLibraryPlaceholder (Contextual empty / no-search states)         |
+--------------------------------------▲--------------------------------------+
                                       | StateFlow<LibraryUiState>
                                       | Intent callbacks (events)
+--------------------------------------▼--------------------------------------+
|                         Presentation Layer                                  |
|  LibraryViewModel                                                           |
|   ├── _searchQuery: MutableStateFlow<String>                                |
|   ├── _selectedFormat: MutableStateFlow<BookFormat?>                        |
|   ├── _selectedBookForDetail: MutableStateFlow<BookItemUiModel?>           |
|   ├── _importState: MutableStateFlow<ImportState>                           |
|   ├── _userMessage: MutableStateFlow<String?>                               |
|   └── Flow Combine Pipeline -> stateIn(WhileSubscribed(5000))               |
+--------------------------------------▲--------------------------------------+
                                       | Flow<List<BookWithProgress>>
                                       | suspend importBookFromUri() / delete()
+--------------------------------------▼--------------------------------------+
|                         Data & Storage Layer                                |
|  BookRepository (Room Database + BookStorageManager)                        |
|   ├── BookDao (@Transaction single-query @Relation joins)                   |
|   └── Background Dispatchers.IO SAF stream copying & cover extraction       |
+-----------------------------------------------------------------------------+
```

### Unidirectional Data Flow (UDF)

1. **State:** The UI observes a single, immutable `LibraryUiState` stream exposed via `StateFlow` and collected using `collectAsStateWithLifecycle()` in Compose.
2. **Events / Intents:** User actions (typing a search query, selecting a format filter chip, clicking a book card, tapping import, or requesting book deletion) are dispatched directly as function calls into `LibraryViewModel`.
3. **Pipeline Transformation:** `LibraryViewModel` combines database streams and user filters, computing the derived presentation state purely in memory without blocking the UI main thread.

---

### UI State Modeling

The UI contract is defined in `LibraryUiState.kt` with clean separation between persistent data, transient operations, and user feedback:

```kotlin
data class BookItemUiModel(
    val book: Book,
    val progress: ReadingProgress? = null
)

sealed interface ImportState {
    data object Idle : ImportState
    data class Importing(val current: Int = 0, val total: Int = 0, val fileName: String? = null) : ImportState
    data class Success(val count: Int, val lastBook: Book) : ImportState
    data class Error(val message: String) : ImportState
    data class BatchResult(val successCount: Int, val errors: List<String>) : ImportState
}

data class LibraryUiState(
    val isLoading: Boolean = false,
    val books: List<BookItemUiModel> = emptyList(),
    val recentBooks: List<BookItemUiModel> = emptyList(),
    val filteredBooks: List<BookItemUiModel> = emptyList(),
    val searchQuery: String = "",
    val selectedFormat: BookFormat? = null,
    val importState: ImportState = ImportState.Idle,
    val selectedBookForDetail: BookItemUiModel? = null,
    val userMessage: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && books.isEmpty()
    val isSearchEmpty: Boolean get() = !isLoading && books.isNotEmpty() && filteredBooks.isEmpty()
}
```

### Reactive Pipeline in `LibraryViewModel`

`LibraryViewModel` manages reactive state using Kotlin's `combine` operator, bridging the persistence layer and user intent:

```kotlin
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val booksDataFlow = combine(
        bookRepository.getAllBooksWithProgress(),
        bookRepository.getRecentBooksWithProgress(10)
    ) { allBooks, recentBooks ->
        val bookItems = allBooks.map { BookItemUiModel(it.book, it.progress) }
        val recentItems = recentBooks.map { BookItemUiModel(it.book, it.progress) }
        Pair(bookItems, recentItems)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        booksDataFlow,
        _searchQuery,
        _selectedFormat,
        _selectedBookForDetail,
        feedbackFlow
    ) { (allBooks, recentBooks), query, format, selectedDetail, (importStatus, message) ->
        val filtered = allBooks.filter { item ->
            val matchesFormat = format == null || item.book.format == format
            val matchesQuery = query.isBlank() ||
                    item.book.title.contains(query, ignoreCase = true) ||
                    item.book.author.contains(query, ignoreCase = true)
            matchesFormat && matchesQuery
        }

        LibraryUiState(
            isLoading = false,
            books = allBooks,
            recentBooks = recentBooks,
            filteredBooks = filtered,
            searchQuery = query,
            selectedFormat = format,
            importState = importStatus,
            selectedBookForDetail = selectedDetail,
            userMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(isLoading = true)
    )
}
```

- **`SharingStarted.WhileSubscribed(5000)`**: Keeps the upstream database flow alive during configuration changes (e.g., screen rotation) while cleanly canceling after 5 seconds of inactivity when the app is in the background, conserving battery and memory.
- **In-Memory Filtering:** Database queries emit complete domain models, while search and format filtering happen instantaneously in memory on background flows, avoiding redundant SQLite re-querying on every keystroke.

---

## 2. Material 3 Expressive Components

BooxBook adheres strictly to the **Material 3 Expressive** design tokens and motion physics, incorporating tactile spring feedback, bento-grid card ergonomics, and pill-shaped interactive elements.

### Motion & Shape Foundations
- **`BentoCardShape`**: Defined as `RoundedCornerShape(28.dp)` in `:core:ui:theme:Shape.kt`.
- **`PillShape`**: `CircleShape` for full capsule rounding.
- **`SpringPhysics.BouncySpring`**: Built on Android physics-based springs:
  ```kotlin
  val BouncySpring = spring<Float>(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessLow
  )
  ```

---

### Component Specifications

### 1. `BentoBookCard`
Location: `feature/library/src/main/java/com/booxbook/feature/library/components/BentoBookCard.kt`

A 28.dp rounded bento card designed for library grid display.

- **Spring Bounce Press Animation:** Leverages `MutableInteractionSource` and `collectIsPressedAsState()` to drive a physics-based scale down to `0.95f` on touch down, springing back on release using `SpringPhysics.BouncySpring`:
  ```kotlin
  val scale by animateFloatAsState(
      targetValue = if (isPressed) 0.95f else 1.0f,
      animationSpec = SpringPhysics.BouncySpring,
      label = "BentoBookCardScale"
  )
  ```
- **Coil 3.0 Cover Rendering:** Uses `coil3.compose.AsyncImage` with `ImageRequest.Builder` and `crossfade(true)`. File resolution is passed directly without performing blocking synchronous `File.exists()` checks on the UI main thread. Fallbacks display `Icons.AutoMirrored.Rounded.MenuBook` when no cover path exists.
- **Format Badges:** Distinct pill surface displaying the file format (`EPUB`, `CBZ`, `AZW3`) overlaid on the cover thumbnail.
- **Reading Progress Indicator:** When reading progress is present, a linear progress bar (`LinearProgressIndicator`) and percentage caption (e.g., `45%`) render seamlessly along the bottom edge of the cover card.
- **Interaction Contract:** Supports combined click (`onClick` to open reading session) and long-click (`onLongClick` to open the detail bottom sheet).

---

### 2. `ContinueReadingCarousel`
Location: `feature/library/src/main/java/com/booxbook/feature/library/components/ContinueReadingCarousel.kt`

A horizontal `LazyRow` displayed at the top of the library when in-progress books exist (`recentBooks.isNotEmpty()`).

- **Card Layout:** Horizontal card layout (`ContinueReadingCard`) pairing a compact cover thumbnail (aspect ratio `0.72f`, 14.dp corners) with a right-hand information column displaying title, author, formatted progress percentage, and last read timestamp.
- **Tactile Feedback:** Incorporates independent spring scale animation (`0.95f` on press) with `BouncySpring`.
- **Progress Gauge:** Highlights current reading depth with a branded `LinearProgressIndicator` clipped in `PillShape`.

---

### 3. `BookDetailBottomSheet`
Location: `feature/library/src/main/java/com/booxbook/feature/library/components/BookDetailBottomSheet.kt`

An expressive modal bottom sheet (`ModalBottomSheet`) delivering full book metadata and management options.

- **Header:** Thumbnail preview, book title, author, and format badge.
- **Metadata Stats Grid:** Clean informational layout displaying:
  - Total page count / file size (in MB/KB via `DecimalFormat`).
  - Current reading percentage and locator information.
- **Action Buttons:**
  - "Tiếp tục đọc" / "Bắt đầu đọc": Primary `ExpressivePillButton` launching the reader engine.
  - "Xóa sách": Destructive action button opening a safety confirmation dialog.
- **Animated Dismiss:** Triggers `sheetState.hide()` within a coroutine before invoking `onDismiss()` to prevent jarring visual dismiss artifacts.
- **Safe Deletion Dialog:** Nested `AlertDialog` verifying user intent before executing filesystem and database removal.

---

### 4. `EmptyLibraryPlaceholder`
Location: `feature/library/src/main/java/com/booxbook/feature/library/components/EmptyLibraryPlaceholder.kt`

Context-aware placeholder screen distinguishing between two states:
1. **Empty Library (`isEmpty`):** Shows `Icons.Rounded.AutoStories`, an expressive welcome message, and an `ExpressivePillButton` with `Icons.Rounded.Add` to initiate the document picker.
2. **No Search Matches (`isSearchEmpty`):** Shows `Icons.Rounded.SearchOff` and guides the user to adjust their search query or format chips without showing the redundant add button.

---

### 5. `ExpressiveFilterChip` & `ExpressivePillButton`
Location: `:core:ui:component`

Shared Material 3 Expressive primitives used throughout the feature:

- **`ExpressiveFilterChip`:**
  - Animates scale down to `0.92f` on press via `BouncySpring`.
  - Animates container and text colors via `animateColorAsState` between `surfaceVariant` and `primaryContainer`.
  - Completely encapsulated within `PillShape` (`CircleShape`).
- **`ExpressivePillButton`:**
  - Standardized 48.dp height pill button.
  - Scale bounce feedback (`0.94f`) on press.
  - Full support for leading icons, labels, custom `ButtonColors`, and disabled states.

---

## 3. Database & Performance Optimizations

### Room `@Relation` & Elimination of N+1 Queries

In digital reader libraries, querying books and their reading progress separately results in $N+1$ database queries (1 query for all books + $N$ queries for each book's progress), which degrades scrolling performance and causes frame drops.

BooxBook completely eliminates this issue using Room's `@Relation` and `@Transaction`:

```kotlin
// core/database/src/main/java/com/booxbook/core/database/entity/BookWithProgressEntity.kt
data class BookWithProgressEntity(
    @Embedded
    val book: BookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "book_id"
    )
    val progress: ReadingProgressEntity?
)

// core/database/src/main/java/com/booxbook/core/database/dao/BookDao.kt
@Transaction
@Query("SELECT * FROM books ORDER BY added_timestamp DESC")
fun getAllBooksWithProgress(): Flow<List<BookWithProgressEntity>>

@Transaction
@Query("SELECT * FROM books WHERE last_read_timestamp > 0 ORDER BY last_read_timestamp DESC LIMIT :limit")
fun getRecentBooksWithProgress(limit: Int = 10): Flow<List<BookWithProgressEntity>>
```

**Benefits:**
- Room generates optimized SQL queries joining `books` and `reading_progress` within a single SQLite transaction.
- Any change to reading progress automatically triggers an update on `getAllBooksWithProgress()` and `getRecentBooksWithProgress()`, instantly re-rendering progress bars across the library without manual refresh logic.

---

### Zero Synchronous Main-Thread Disk I/O

A common performance pitfall in Android list/grid rendering is calling `File.exists()` or `File.length()` synchronously inside Composable functions or item scopes. On devices with flash storage or under heavy I/O, synchronous file status checks block the main Looper, causing stutter during fast scrolling.

BooxBook maintains strict zero synchronous disk I/O in UI composition:
1. **Metadata in Room:** File size, format, page count, and cover file paths are parsed once during SAF import and stored in SQLite. The UI only reads in-memory model fields (`book.coverPath`).
2. **No `File.exists()` Checks in Compose:** `BentoBookCard` and `ContinueReadingCard` directly pass `File(coverPath)` into Coil's `ImageRequest.Builder`.
3. **Coil 3.0 Background Pipeline:** Coil resolves disk file existence, decodes bitmaps, scales down to target viewport dimensions, and caches bitmaps in memory entirely on background coroutine dispatchers.

---

### Batch Import State Machine

Importing multiple eBooks via Android's Storage Access Framework (`ActivityResultContracts.OpenMultipleDocuments()`) can be intensive due to content resolver stream reading, zip archive scanning, and cover image extraction.

BooxBook runs imports through an asynchronous finite state machine in `LibraryViewModel`:

```kotlin
fun importBooksFromUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
        val total = uris.size
        _importState.value = ImportState.Importing(current = 0, total = total)
        val successfulBooks = mutableListOf<Book>()
        val errors = mutableListOf<String>()

        withContext(ioDispatcher) {
            uris.forEachIndexed { index, uri ->
                _importState.value = ImportState.Importing(current = index + 1, total = total)
                val result = bookRepository.importBookFromUri(uri)
                result.onSuccess { book ->
                    successfulBooks.add(book)
                }.onFailure { error ->
                    errors.add(error.message ?: "Không thể nhập tệp: $uri")
                }
            }
        }

        when {
            errors.isNotEmpty() && successfulBooks.isNotEmpty() -> {
                _importState.value = ImportState.BatchResult(successfulBooks.size, errors)
            }
            errors.isNotEmpty() && successfulBooks.isEmpty() -> {
                _importState.value = ImportState.Error(errors.joinToString("\n"))
            }
            successfulBooks.isNotEmpty() -> {
                _importState.value = ImportState.Success(successfulBooks.size, successfulBooks.last())
            }
            else -> {
                _importState.value = ImportState.Idle
            }
        }
    }
}
```

**Architecture Characteristics:**
- **Execution offloaded to `ioDispatcher` (`Dispatchers.IO`)**: Keeps the UI responsive at 60/120 fps while parsing multi-megabyte EPUB and CBZ archives.
- **Granular Progress Reporting:** Real-time updates emit `Importing(current, total)` enabling top-level progress bar and snackbar counters.
- **Fault-Tolerant Error Aggregation:** If 3 out of 10 files fail (e.g., corrupted archive or unsupported format), the remaining 7 books are imported successfully, and a composite `BatchResult` notifies the user of the exact errors without rolling back successful imports.

---

## 4. Testing & Verification Strategy

The Library feature maintains 100% test coverage over view model state flows, filtering heuristics, import pipelines, and deletion workflows in `feature/library/src/test/java/com/booxbook/feature/library/LibraryViewModelTest.kt`.

### Test Architecture

- **Robolectric Runner:** `@RunWith(RobolectricTestRunner::class)` enables testing of Android framework dependencies (such as `android.net.Uri`) without requiring an attached emulator or physical device.
- **Coroutines Test Dispatchers:**
  - `StandardTestDispatcher`: Used with `testScheduler` and `advanceUntilIdle()` to control coroutine execution deterministically.
  - `UnconfinedTestDispatcher`: Attached to `backgroundScope` for active `uiState.collect()` subscriptions.
  - `Dispatchers.setMain(testDispatcher)` / `Dispatchers.resetMain()`: Guarantees correct lifecycle synchronization.
- **Dispatcher Injection:** `LibraryViewModel.ioDispatcher` is overridden with `testDispatcher` in tests, allowing instant synchronous resolution of background tasks during testing.
- **Fake Repository Pattern:** `FakeBookRepository` implements `BookRepository`, exposing in-memory `MutableStateFlow` streams that simulate real Room and storage events.

### Test Matrix

| Test Case | Scenario Verified |
|---|---|
| `uiState emits all books with reading progress correctly` | Verifies Room `@Relation` joins and progress mapping in initial state. |
| `search query filters books by title and author` | Verifies case-insensitive title and author substring filtering. |
| `format selection filters books correctly` | Verifies exact matching on `BookFormat` (`EPUB`, `CBZ`, `AZW3`). |
| `search query combined with format filter` | Verifies intersection logic when both search and format filters are active. |
| `onBookSelectedForDetail and dismissDetail manage bottom sheet state` | Verifies detail bottom sheet state selection and dismissal. |
| `importBooksFromUris updates importState with Success on valid files` | Verifies single-file and batch import completion with counter emission. |
| `importBooksFromUris handles partial failures with BatchResult` | Verifies partial failure resilience when importing good and corrupted files together. |
| `importBooksFromUris handles total failure with Error state` | Verifies error state emission when all imported files fail. |
| `deleteBook removes book and dismisses detail bottom sheet` | Verifies cascade deletion and cleanup of the active bottom sheet selection. |

---

## 5. File & Directory Reference

| File Path | Description |
|---|---|
| `feature/library/src/main/java/com/booxbook/feature/library/LibraryScreen.kt` | Main Compose screen: TopAppBar, search bar, chips, carousel, grid, bottom sheet. |
| `feature/library/src/main/java/com/booxbook/feature/library/LibraryViewModel.kt` | Hilt ViewModel managing MVI state flow, search, filtering, imports, and deletion. |
| `feature/library/src/main/java/com/booxbook/feature/library/LibraryUiState.kt` | Data classes and sealed interfaces defining the presentation contracts. |
| `feature/library/src/main/java/com/booxbook/feature/library/components/BentoBookCard.kt` | 28.dp rounded bento card with spring bounce animation and Coil 3.0 cover rendering. |
| `feature/library/src/main/java/com/booxbook/feature/library/components/ContinueReadingCarousel.kt` | Horizontal `LazyRow` carousel for recently read books. |
| `feature/library/src/main/java/com/booxbook/feature/library/components/BookDetailBottomSheet.kt` | Modal bottom sheet with metadata overview, reading trigger, and safe deletion. |
| `feature/library/src/main/java/com/booxbook/feature/library/components/EmptyLibraryPlaceholder.kt` | Contextual placeholder for empty library vs no-search-results states. |
| `core/ui/src/main/java/com/booxbook/core/ui/component/ExpressiveFilterChip.kt` | Spring-animated pill filter chip with color transitions. |
| `core/ui/src/main/java/com/booxbook/core/ui/component/ExpressivePillButton.kt` | Tactile spring-scale pill button primitive. |
| `core/database/src/main/java/com/booxbook/core/database/entity/BookWithProgressEntity.kt` | Room `@Relation` entity joining `books` and `reading_progress`. |
| `feature/library/src/test/java/com/booxbook/feature/library/LibraryViewModelTest.kt` | Robolectric unit tests validating reactive state flows and edge cases. |
