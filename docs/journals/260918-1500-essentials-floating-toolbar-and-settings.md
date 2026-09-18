# Journal Entry: Essentials Floating Navigation & Dedicated Settings Module
**Date:** 2026-09-18  
**Module:** `:app`, `:feature:statistics`, `:feature:library`, `:core:database`  
**Status:** Completed & Verified on Device  

## Architecture & Visual Implementation

### 1. Essentials Floating Navigation Toolbar (`sameerasw/essentials` style)
- **Floating Pill Surface (`BooxBookFloatingToolbar`):**
  - Raised tactile navigation bar floating above bottom insets (`windowInsetsPadding(WindowInsets.navigationBars)`).
  - High tonal and shadow elevation (`shadowElevation = 8.dp`, `PillShape`).
  - Active tab expansion with spring bouncy physics (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessLow`).
  - Pop-out contrasting pill background for the selected tab (`MaterialTheme.colorScheme.surface`), with compact circular icons for inactive tabs.
- **Companion Floating Action Button (FAB):**
  - Placed alongside the right edge of the floating toolbar (size exactly 60.dp x 60.dp, matching the height of the floating toolbar, with 22.dp rounded corners, `MaterialTheme.colorScheme.primaryContainer`).
  - **Context-Aware Visibility:** Exclusively rendered when `currentTab == MainTab.LIBRARY`. When switching to Statistics or Settings, the FAB smoothly slides and fades away with spring physics, and the navigation toolbar re-centers itself automatically.
  - Dedicated direct trigger for Android Storage Access Framework (`OpenMultipleDocuments`) to import books from anywhere in device storage.
  - Removed legacy `ExtendedFloatingActionButton` and empty placeholder pill buttons in `:feature:library`, creating an ultra-clean, decluttered library canvas.

### 2. Dedicated Settings Screen (`SettingsScreen` & `SettingsViewModel`)
- Fully integrated 3rd tab in the primary navigation structure:
  - **Nhóm 1: Thống kê & Thói quen đọc sách (Statistics Settings):**
    - Shape selector for Activity Heatmap tiles (Viên sỏi, Bo tròn, Hình thoi, Phát sáng) with live preview tiles.
    - Daily reading goal selector (15m, 30m, 45m, 60m).
    - Backed by singleton `StatisticsPreferencesManager` with reactive `StateFlow` synchronization between screens.
  - **Nhóm 2: Trải nghiệm đọc sách (Reader Preferences):**
    - Tap zones (Kindle, Hai bên viền, Chỉ menu).
    - Page turn transitions (Trượt trang, Lật trang, Chuyển ngay).
    - Haptic vibration feedback toggle.
  - **Nhóm 3: Thông tin ứng dụng:**
    - Version info, E-ink & AMOLED design manifesto.

### 3. Dynamic Heatmap Palette & Adaptive Legend
- Replaced hardcoded green hues with the app's native Material 3 violet/primary tonal palette:
  - Level 0: Neutral subtle surface container tone.
  - Levels 1..3: Primary violet alpha gradient (28%, 52%, 78%).
  - Level 4: Pure vibrant primary with ambient glow border and core highlight.
- **Adaptive Legend Matching:** The bottom `Ít ... Nhiều` legend indicators dynamically morph their geometry (Squircle, Pebble, Diamond, Glow) in lockstep with the selected tile geometry.
- 100% Vietnamese localization across titles, date tooltips, streak badges, and day headers (`T2`, `T3`, `T4`, `T5`, `T6`, `T7`, `CN`).
- Removed redundant settings button from the statistics header badge row, consolidating all preferences inside the dedicated Settings tab.

## Verification on Hardware
- Built and streamed debug APK to connected device (`R3CM60952ED`).
- Verified zero layout overlap with 140dp content bottom padding.
- Verified live sync between Settings and Statistics heatmap tiles/legend.
- Verified SAF document picker invocation from the companion FAB.
