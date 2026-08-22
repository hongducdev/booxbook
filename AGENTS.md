# Repository Guidelines

## Project Overview

Boox Book is an Android 8.0+ novel reader and a personal Tsundoku fork. It loads LNReader JavaScript plugins through a headless React Native/Hermes runtime; Kotlin/Tachiyomi extensions are intentionally unsupported. The native UI is Jetpack Compose with Voyager navigation, while novel reading uses a native WebView.

Preserve inherited compatibility names such as `Manga`, `SManga`, and `MangasPage`. They remain part of source and binary contracts even though the product is novel-focused.

## Architecture & Data Flow

- `:app` is the composition root: application/activity entry points, Compose screens, Voyager navigation, view models, jobs, networking, downloads, tracking, reader logic, and plugin integration.
- `:domain` owns most models, repository contracts, preferences, services, and interactors. App-specific inherited domain code also exists under `app/src/main/java/eu/kanade/domain/`; follow the nearest feature's placement rather than creating a second convention.
- `:data` implements domain repositories using the generated SQLDelight `tachiyomi.data.Database` and mapper functions.
- `:source-api` defines stable source/plugin contracts. Do not remove compatibility defaults or deprecated Rx bridges without proving published-source binary compatibility.
- `:source-local` handles local EPUB, TXT, and HTML content.
- `:js-runtime` encapsulates React Native/Hermes. `JsRuntime` is the only intended app-facing runtime boundary.
- `core/*`, `presentation-core`, and `presentation-widget` provide shared coroutine, archive, view-model, Compose, and widget primitives.

Primary flows:

1. Startup: `AndroidManifest.xml` -> `App.kt` -> Injekt modules -> migrations -> `MainActivity.kt` -> Voyager `HomeScreen`.
2. Library: Compose screen -> `LibraryViewModel` -> domain interactor -> repository interface -> SQLDelight implementation -> `Flow` -> immutable UI state.
3. Plugin browse: plugin metadata -> `JsPluginManager` -> `AndroidSourceManager` -> `JsSource` -> `JsRuntime.call()` -> Hermes plugin fetch/parse -> source models.
4. Reader: `ReaderViewModel` -> `ChapterLoader` -> downloaded/local/HTTP/JS/stub loader -> one-page novel chapter -> `fetchPageText` -> reader state.

Composition roots are `app/src/main/java/eu/kanade/tachiyomi/App.kt`, `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt`, and `app/src/main/java/eu/kanade/domain/DomainModule.kt`. DI uses Injekt, not Hilt or Koin: infrastructure/repositories are normally singleton factories; stateless interactors are factories.

## Key Directories

- `app/src/main/java/eu/kanade/tachiyomi/ui/`: feature navigation, activities, and view-model orchestration.
- `app/src/main/java/eu/kanade/presentation/`: reusable or stateless Compose surfaces.
- `app/src/main/java/eu/kanade/tachiyomi/jsplugin/`: LNReader plugin management and source adaptation.
- `domain/src/main/java/tachiyomi/domain/`: domain models, interactors, and repository interfaces.
- `data/src/main/java/tachiyomi/data/`: SQLDelight repository implementations and mapping.
- `source-api/src/main/kotlin/`: source-facing compatibility API.
- `source-local/src/main/kotlin/`: local novel implementations.
- `js-runtime/`: nested pnpm workspace and Android library for React Native/Hermes.
- `core/`, `presentation-core/`, `presentation-widget/`: shared platform and UI primitives.
- `i18n/`, `i18n-novel/`: Kotlin Multiplatform string resources; base English resources are under `i18n/src/commonMain/moko-resources/base/`.
- `baseline-profile/`: baseline-profile generation and startup Macrobenchmarks.
- `gradle/build-logic/`: shared `mihonx` convention plugins; reuse these instead of duplicating module setup.
- `app/tools/`: generators for committed JS assets and shipped-library metadata.

## Development Commands

Install JS dependencies before any Gradle command in a fresh checkout; `settings.gradle.kts` resolves the React Native Gradle plugin through `js-runtime/node_modules`.

```bash
cd js-runtime
pnpm install --frozen-lockfile
cd ..
./gradlew clean
./gradlew assembleRelease
./gradlew testDebugUnitTest
./gradlew verifySqlDelightMigration
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

Common checks:

```bash
./gradlew spotlessCheck          # Kotlin, KTS, and XML formatting
./gradlew spotlessApply          # apply formatting
cd js-runtime && pnpm run check  # TypeScript type-check, ESLint, Prettier check
```

Other authoritative tasks:

```bash
./gradlew assembleNightly -Penable-updater
./gradlew :app:generateBaselineProfile
./gradlew :baseline-profile:connectedBenchmarkReleaseAndroidTest
```

Run Macrobenchmarks on a physical device. Android Studio plus an emulator or developer-enabled device is the documented run environment; the repository does not define a custom CLI launch command.

## Code Conventions & Common Patterns

- Formatting: UTF-8, spaces, final newline, and no trailing whitespace. Kotlin/KTS use four-space indentation, a 120-character line limit, IntelliJ ktlint style, and trailing commas. See `.editorconfig`.
- Follow adjacent package/module conventions. The inherited module/package layering is intentionally imperfect; avoid broad relocation or cosmetic manga-to-novel renames.
- View models commonly extend `StateViewModel<S>`, keep `MutableStateFlow` private, expose `StateFlow`, and update immutable data-class state with `update { copy(...) }`.
- Screens collect state and pass values plus callback method references to presentation composables. Use sealed interfaces for dialogs/events and Voyager `Screen` for navigation.
- Interactors generally expose `await(...)` for one-shot suspend work and `subscribe(...)` for observable `Flow` work.
- Use `viewModelScope`/`lifecycleScope` for UI work and existing `launchIO`/`withIOContext` helpers for blocking I/O. Manager-owned work commonly uses `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- Derive state with `combine`, `flatMapLatest`, `distinctUntilChanged`, `collectLatest`, `stateIn`, or `launchIn`; do not add manual observer plumbing beside Flow-based code.
- Protect shared plugin/download/translation state with the existing `Mutex`, atomic, bounded-dispatcher, or concurrent-collection pattern. Preserve plugin timeouts, deduplication locks, and bounded caches.
- Log recoverable failures with `logcat` and useful context. Rethrow `CancellationException` before broad exception handling; restore loading/progress flags in `finally`.
- Use constructor defaults such as `Injekt.get()` where established. Android components often use `by injectLazy()`; runtime view-model arguments use AndroidX `viewModelFactory`/`CreationExtras`.
- Prefer targeted SQL queries and bounded caches for large libraries and chapter bodies; avoid unnecessary full-library materialization.

## Important Files

- `app/src/main/AndroidManifest.xml`: application and launcher declarations.
- `app/src/main/java/eu/kanade/tachiyomi/App.kt`: startup, DI import order, process services.
- `app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`: migration barrier and Compose/Voyager host.
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt`: reader loader selection order.
- `app/src/main/java/eu/kanade/tachiyomi/jsplugin/source/JsSource.kt`: LNReader adapter, runtime calls, caches, and timeouts.
- `js-runtime/src/main/kotlin/eu/kanade/tachiyomi/jsruntime/JsRuntime.kt`: sole app-facing Hermes boundary.
- `settings.gradle.kts`: module graph and nested React Native plugin resolution.
- `app/build.gradle.kts`: variants, signing inputs, ABI splits, React Native bundling, app dependencies.
- `gradle/libs.versions.toml`: dependency and plugin versions.
- `gradle/mihon.versions.toml`: SDK, NDK, Java compatibility, and convention-plugin aliases.
- `gradle.properties`: Gradle performance, Hermes/new-architecture, and ABI settings.
- `.github/workflows/build.yml`: canonical CI build and QA sequence.
- `BOOXBOOK_CHANGELOG.md`: Boox Book-specific changes. Keep `CHANGELOG.md` byte-identical to upstream Tsundoku.

## Runtime/Tooling Preferences

- Use the Gradle wrapper 9.6.1; do not use a system Gradle.
- CI runtime: Temurin JDK 21. Java/Kotlin bytecode compatibility: 17.
- Android: `minSdk=26`, `compileSdk=37`, `targetSdk=36`, NDK `29.0.14206865`.
- JavaScript: Node 24 and pnpm 10.33.0, pinned under `js-runtime/`. The repository root is not the npm workspace root.
- React Native 0.86.0, React 19.2.8, Hermes enabled, new architecture enabled. Keep `hermes-compiler` exactly aligned with React Native's bundled Hermes version.
- Keep `reactNativeArchitectures` synchronized with app ABI splits. Do not enable Gradle configure-on-demand; it is disabled for React Native/AGP compatibility.
- Keep `local.properties`, `keystore.properties`, signing keys, and service credentials untracked. Optional build switches are presence-based: `-Penable-updater`, `-Pinclude-telemetry`, and `-Pinclude-dependency-info`.
- Generated assets are committed. Change generator sources and regenerate; do not hand-edit `app/src/main/assets/js/vendor/cheerio.bundle.js`, `app/src/main/assets/novel-reader/videojs.min.js`, or `hls.min.js`.

## Testing & QA

- JVM tests live in each owning module's `src/test/java` or `src/test/kotlin`; test classes use `*Test.kt`. Frameworks: JUnit Platform/Jupiter, Kotest assertions, MockK, and `kotlinx-coroutines-test` where needed.
- Prefer behavior-focused backtick names, inline fixtures, narrow private fakes, `@TempDir` for filesystem behavior, and `runTest`/virtual time for coroutine behavior. Clean up static mocks, singleton/global state, sockets, and engines.
- Packaged Hermes/React Native integration tests belong in `app/src/androidTest`, because only the app test APK contains `assets/index.android.bundle`. Instrumentation uses AndroidJUnit4.
- Baseline/profile tests use AndroidJUnit4, Macrobenchmark, and UI Automator. They depend on English visible labels and can fail after locale/navigation changes.
- Required CI-equivalent QA: `./gradlew testDebugUnitTest` plus `./gradlew verifySqlDelightMigration`. Run the latter whenever SQLDelight schema or migration behavior changes.
- Run `./gradlew spotlessCheck` for Kotlin/Gradle/XML changes and `pnpm run check` inside `js-runtime/` for JS/TS changes; the main build workflow does not cover these checks.
- No numeric coverage threshold is configured. Validate affected behavior, then run the repository-wide unit-test gate before completion.
- For UI changes, check all base themes and tablet mode and provide visual evidence. No conventional Compose UI assertion suite is present.
