---
phase: 1
title: Architecture & Project Scaffolding
status: completed
priority: P1
effort: 1d
dependencies: []
---

# Phase 1: Architecture & Project Scaffolding

## Overview
Khởi tạo cấu trúc dự án Android đa module (Gradle Multi-module), cấu hình Version Catalog (`libs.versions.toml`), tích hợp Hilt Dependency Injection, và thiết lập nền tảng Jetpack Compose với Material 3 Expressive.

## Requirements
- **Functional:**
  - Thiết lập dự án Android với Kotlin 2.0+ và target SDK 34+.
  - Chia tách các module rõ ràng theo Clean Architecture: `:app`, `:core:ui`, `:core:database`, `:core:model`, `:core:engine`, `:feature:library`, `:feature:reader`.
- **Non-functional:**
  - Build cache và Kotlin DSL ổn định.
  - Hỗ trợ Edge-to-Edge display mặc định theo chuẩn Android 14/15.

## Architecture
```
BooxBook/
├── gradle/libs.versions.toml
├── app/
├── core/
│   ├── ui/          (Theme, Typography, Shapes, Dynamic Color Monet)
│   ├── model/       (Data models: Book, Chapter, Highlight, Progress)
│   ├── database/    (Room DB entities, DAOs, Migrations)
│   └── engine/      (Abstractions for Reader engines)
└── feature/
    ├── library/     (Shelf, Bento Grid, Import, Search)
    └── reader/      (Unified Reader Screen, Controls, Settings)
```

## Related Code Files
- Create: `gradle/libs.versions.toml`
- Create: `build.gradle.kts`, `settings.gradle.kts`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `core/ui/build.gradle.kts`, `core/ui/src/main/java/com/booxbook/core/ui/theme/`
- Create: `core/model/build.gradle.kts`

## Implementation Steps
1. Khởi tạo Gradle wrapper và tệp `settings.gradle.kts` cấu hình các module con.
2. Thiết lập `libs.versions.toml` bao gồm:
   - Compose BOM, Material3 Expressive, Navigation Compose.
   - Hilt Android & KSP.
   - Room KMP / Android, Coroutines, Serialization.
3. Cấu hình module `:core:ui` với hệ thống theme Material 3 (Color Tokens, Dynamic Monet, Typography Lexend/Bookerly, Expressive Shapes bo cong 28.dp).
4. Thiết lập Single Activity trong `:app` kích hoạt `enableEdgeToEdge()` và cấu hình Navigation Host cơ bản.

## Success Criteria
- [x] Dự án build thành công trên Gradle (`./gradlew assembleDebug`).
- [x] Ứng dụng chạy được trên thiết bị/máy ảo hiển thị màn hình Splash với theme Material 3 Expressive.
- [x] Hilt DI được inject thành công vào MainActivity.
