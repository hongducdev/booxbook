# Journal Entry: M3 Activity Heatmap (JustForPixel-ExpressiveLab)
**Date:** 2026-09-18  
**Module:** `:feature:statistics`, `:core:model`, `:core:database`  
**Status:** Completed & Verified on Device  

## Architecture & Visual Implementation
- **M3 Activity Heatmap Component (`ReadingActivityHeatmapCard`):**
  - Replaced the weekly column bar chart with a full 16-week **M3 Activity Heatmap (Contribution & Streak Grid)** faithfully replicating the `JustForPixel-ExpressiveLab` reference design.
  - **Dark Forest Slate Bento Container (`#141A16`):** 28.dp rounded corners (`BentoCardShape`), high-contrast dark background for vivid mint/emerald accents.
  - **4 Selectable Tile Geometries:**
    - `Squircle`: 5.dp continuous smooth rounded rectangles.
    - `Pebble`: Pure circular pebbles (`CircleShape`).
    - `Diamond`: 45-degree rotated squares with rounded vertices.
    - `Glow`: Radiant halo glow effects with inner core highlights on active reading days.
  - **16-Week Grid Matrix (112 Days):**
    - 7 row labels on the left (`M`, `T`, `W`, `T`, `F`, `S`, `S`).
    - 16 weekly columns scrollable horizontally, auto-scrolled to the current week on initial render.
    - Current day highlight with subtle mint border ring.
    - Interactive tap feedback: spring-scaling to 1.25x with tactile tooltip banner detailing specific date and reading minutes.
  - **Material 3 5-Tier Intensity Color Palette:**
    - Level 0: `#1C2620` (unattended / rest day)
    - Level 1: `#38533F` (1 - 15 minutes)
    - Level 2: `#638C6B` (16 - 30 minutes)
    - Level 3: `#98C99F` (31 - 60 minutes)
    - Level 4: `#C7F3CE` (peak reading session with radiant glow core)
  - **Legend & Specs Card:**
    - Integrated bottom right 5-tier dot legend (`Less` ... `More`).
    - Added `Expressive Heatmap Specs` documentation card detailing geometry, glow effects, color tiers, and responsive layout.

## Verification on Hardware
- Built via `./gradlew.bat assembleDebug` and installed directly onto Samsung Galaxy S24 FE (`R3CM60952ED`).
- Validated smooth interactive switching between Squircle, Pebble, Diamond, and Glow modes.
- Verified interactive tile tap tooltips and spring scaling animation.
