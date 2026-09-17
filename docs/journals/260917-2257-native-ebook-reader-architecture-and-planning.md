# Journal Entry: Native Ebook Reader Architecture & Implementation Planning
**Date:** 2026-09-17  
**Session:** Architecture Brainstorm & Plan Creation  
**Author:** Solution Brainstormer  

## Key Decisions & Context
- **Target Device:** Native Android (phones and tablets), enabling full exploitation of Jetpack Compose and Material 3 Expressive design (Monet dynamic coloring, spring animations, expressive cards/pill buttons) inspired by `JustForPixel-ExpressiveLab`.
- **Reading Engines:**
  - **EPUB & AZW3:** Discrete pagination (page-by-page turning) via Readium Kotlin Toolkit. AZW3 files are unwrapped on-the-fly via a lightweight C++ NDK wrapper around `libmobi` into an EPUB cache.
  - **CBZ (Comics):** Vertical continuous scrolling (Webtoon-style) using Compose `LazyColumn` + Coil with memory-conscious downsampling and pinch-to-zoom gestures.
- **Feature Set:** Reading progress persistence (CFI & page index), text annotations/highlights, and background Text-To-Speech (TTS) with word/sentence visual sync.

## Outcomes
- Scaffolded comprehensive plan: `plans/260917-2257-native-ebook-reader/plan.md` with 8 detailed phases (`phase-01` to `phase-08`).
- Created brainstorm architecture report: `plans/reports/260917-2257-brainstorm-native-ebook-reader.md`.
- Ready for Phase 1 execution (Project setup and module scaffolding).
