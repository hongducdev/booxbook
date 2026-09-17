# Journal Entry: Phase 3 - Readium EPUB & Compose CBZ Engines Implementation
**Date:** 2026-09-17  
**Session:** Phase 3 Implementation & Verification  
**Module:** `:core:engine`  

## Key Decisions & Architecture
- **Unified Engine Contract & State Machine (`ReaderEngine`):**
  - Designed `ReaderEngine` with reactive `StateFlow<ReaderState>` modeling `Idle`, `Loading`, `Ready`, and `Error` states.
  - Standardized operations: `openBook(book)`, `closeBook()`, and `extractCover(book, destinationFile)`.
  - Defined hierarchical `TocItem` tree for Table of Contents and `ReaderPreferences` defaulting to discrete pagination (`isScrollMode = false`).
- **Readium Kotlin Toolkit 3.1.1 Integration (`EpubReaderEngine`):**
  - Integrated `AssetRetriever`, `DefaultPublicationParser`, and `PublicationOpener` via `ReadiumAssetRetriever`.
  - Configured `EpubPreferences` with discrete pagination (`scroll: false`), font sizes, line heights, margins, custom font families, and Dark Mode theme mapping (`Theme.DARK` / `Theme.LIGHT`).
  - Implemented `createFragmentFactory` exposing `EpubNavigatorFragment` instantiation with configured preferences and listeners.
  - Implemented leak-free cover extraction (`pub.cover()`) ensuring transient publications opened outside an active reading session are deterministically closed in `finally`.
  - Enforced cross-thread visibility with `@Volatile` on active engine references.
- **Pure Jetpack Compose CBZ Comic Engine (`CbzReaderComponent` & `CbzArchive`):**
  - **Memory-Efficient On-Demand Extraction:** `CbzArchive` parses ZIP entries, discards macOS/system metadata, and extracts images on-demand to dedicated cache files (`getPageFile`) to prevent OOM on 100MB+ comic archives.
  - **Atomic Temp File Writing:** Employs `.tmp` write and atomic rename pattern to eliminate concurrency race conditions with image decoders (Coil 3.0).
  - **Natural Order Sorting:** Implemented `NaturalOrderComparator` using tokenized regex `(\d+)|(\D+)`, numerical `Long`/`BigInteger` comparison, and full archive path resolution (`Vol/Ch`) to preserve chapter order and prevent page scrambling.
  - **Continuous Vertical Scrolling (Webtoon):** Built with `LazyColumn`, zero spacing/padding, and `ContentScale.FillWidth`.
  - **Touch & Gesture Handling:** Integrated pinch-to-zoom (1x–4x) and double-tap zoom (2.5x / 1x) with dynamic viewport bounding (`BoxWithConstraints`) and disabled list scroll when zoomed to allow pan gestures.
  - **Floating Page Indicator:** Debounced 2-second auto-dismiss indicator using a single cancellable coroutine `Job`.
- **Hilt Dependency Injection (`EngineModule`):**
  - Multi-binding `Map<BookFormat, ReaderEngine>` allowing feature and UI layers to resolve format-specific engines at runtime.

## Testing & Verification
- Unit test suite implemented in `core/engine/src/test`:
  - `CbzArchiveExtractorTest`: 7 test cases covering natural alphanumeric ordering, multi-chapter folder hierarchy preservation, tie-breaker handling (`page01` vs `page1`), file extension filtering, on-demand extraction, and cover art prioritization.
  - `EpubPreferencesTest`: 2 test cases validating default discrete pagination preferences, state transitions, and recursive TOC structures.
- **Results:**
  - `:core:engine:testDebugUnitTest`: 9/9 passed (100%).
  - Full suite (`:core:engine` + `:core:database`): 22/22 passed (100%), zero regressions.
  - Full project assemble: `./gradlew assembleDebug` passed successfully with desugaring enabled.
