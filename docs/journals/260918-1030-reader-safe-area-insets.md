# Safe-area insets padding for reading canvas

Date: 2026-09-18
Scope: `feature:reader`, `docs`
Plan: `plans/260918-1015-reader-safe-area-insets/plan.md`

## Problem

Under the edge-to-edge configuration (`MainActivity.enableEdgeToEdge()` and `Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0))`), the reading canvas ran underneath system bars.
Measured before the fix (on 1440x3040 display):
- **1,960 bright text pixels** in rows 0..99 (behind status bar clock, battery, camera cutout)
- **661 bright text pixels** in rows 100..138
- **49 bright text pixels** in rows 2990..3038 (behind navigation gesture bar)
Book text at the top and bottom of each page was obscured by system UI.

## Diagnosis

Readium Kotlin Toolkit 3.1.1 has built-in inset handling (`R2EpubPageFragment.setupPadding/updatePadding` reading WindowInsetsCompat), but because `EpubNavigatorFragment` is hosted inside Compose (`AndroidView -> FrameLayout`), the window insets were swallowed before reaching the fragment's views. As a result, Readium received 0 insets and paginated starting at CSS `y=3.84px`.

## Fix

1. In `ReaderScreen.kt`:
   Calculated `val readerInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)`.
   Wrapped the reading canvas, `PageTurnFlipOverlay`, and `TapZonePreviewOverlay` together inside:
   ```kotlin
   Box(
       modifier = Modifier
           .fillMaxSize()
           .windowInsetsPadding(readerInsets)
   )
   ```
   The Scaffold remains edge-to-edge with matching theme background, preserving seamless aesthetics behind the status and navigation bars.

2. In `EpubReaderContainer.kt`:
   Removed `statusBarTopPx` calculation since the container is already padded to the safe area. `ReaderTapZones.resolve()` now works on clean `0f..1f` canvas coordinates without needing manual inset fraction adjustments.

3. In `TapZonePreviewOverlay.kt`:
   Removed manual status-bar offset math. The preview overlay is placed in the same safe-area container as the canvas, aligning 1:1 with user tap zones.

4. In `ReaderTapZonesTest.kt`:
   Cleaned up dead tests for `topInsetFraction`. All 12 boundary and mode tests pass.

## Verification

Measured on physical device (`SM-S721B`, 1440x3040):
- Rows 0..99 (status bar area): **0 text pixels** (was 1,960)
- Rows 100..138: **0 text pixels** (was 661)
- Rows 2990..3038 (nav bar area): **0 text pixels** (was 49)
- Rows 1600..1700 (content reading area): **2,517 text pixels** (was 2,491)
- Edge tap (page forward & backward): works cleanly.
- Top tap (menu strip): opens reader chrome immediately below status bar.
- Device rotation (portrait -> landscape -> portrait): identical PID, 0 crashes, content preserved.
- All unit tests pass (`:feature:reader:testDebugUnitTest`).
