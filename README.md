# BooxBook

Native Android ebook reader for EPUB, AZW3, and CBZ. Jetpack Compose, Material 3 Expressive, Room, Readium, and libmobi.

## Modules

- `:app` — application shell and navigation
- `:core:ui` — theme, expressive primitives (loading, progress, slider)
- `:core:model` / `:core:database` — domain models and Room
- `:core:engine` — EPUB / AZW3 / CBZ readers
- `:core:tts` — text-to-speech
- `:feature:library` / `:feature:reader` / `:feature:statistics`

## Toolchain

- AGP 8.7.3, Gradle 8.13, Kotlin 2.1.0, JDK 17
- `compileSdk` / `targetSdk` 35, `minSdk` 26
- Compose BOM `2025.05.01` + pinned `androidx.compose.material3:material3:1.5.0-alpha10`

See `docs/` for architecture notes.

## Credits

- **[JustForPixel-ExpressiveLab](https://github.com/mohdamaan1/JustForPixel-ExpressiveLab)** (MIT) —
  used as a **design reference** for BooxBook's Material 3 Expressive language: bento grid, activity
  heatmap, and the wave amplitude/wavelength of the reader page slider. This repository does not
  redistribute ExpressiveLab source and does not depend on its AAR.
- **Material Components / Jetpack Compose Material 3** (Apache License 2.0) — official
  `LoadingIndicator` and wavy progress indicators.
