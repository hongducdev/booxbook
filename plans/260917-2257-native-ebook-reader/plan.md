---
title: BooxBook Native Ebook Reader
description: >-
  Native Android Ebook Reader supporting EPUB, AZW3, and CBZ with Material 3
  Expressive UI (JustForPixel style), discrete pagination, webtoon comic
  scrolling, annotations, and TTS.
status: pending
priority: P1
branch: feature/native-ebook-reader
tags:
  - android
  - jetpack-compose
  - readium
  - libmobi
  - m3-expressive
  - epub
  - azw3
  - cbz
  - tts
blockedBy: []
blocks: []
created: '2026-09-17T15:57:27.470Z'
createdBy: 'ck:plan'
source: skill
---

# BooxBook Native Ebook Reader

## Overview
BooxBook là ứng dụng đọc sách native hiện đại trên nền tảng Android, kế thừa ngôn ngữ thiết kế **Material 3 Expressive (MD3 Expressive / Pixel UI)** từ repo `JustForPixel-ExpressiveLab`. Ứng dụng hỗ trợ đa dạng định dạng: **EPUB, AZW3 (Amazon Kindle KF8), và CBZ (Comic/Manga)** với hai cơ chế hiển thị chuyên biệt:
- **Sách chữ (EPUB, AZW3):** Lật trang từng trang một (Discrete Pagination / Slide transition), hiển thị chuẩn máy đọc sách.
- **Truyện tranh (CBZ):** Cuộn mượt mà từ trên xuống dưới (Vertical Continuous Scroll) chuẩn Webtoon/Manga, hỗ trợ phóng to thu nhỏ.
- **Tính năng mở rộng:** Lưu tiến độ đọc chính xác, Ghi chú / Highlight, và Trình đọc giọng nói Text-To-Speech (TTS) đồng bộ với giao diện.

## Architecture Highlights
- **UI:** 100% Jetpack Compose với Material 3 Expressive (`androidx.compose.material3`).
- **DI & Concurrency:** Hilt / Kotlin Coroutines & Flow.
- **Database:** Room Database (Lưu Book metadata, Reading Progress dạng CFI/PageIndex, Annotations).
- **Core Engine:**
  - EPUB: Readium Kotlin Toolkit (`readium-navigator`).
  - AZW3: `libmobi` (C++ qua NDK/JNI) giải mã và chuyển đổi sang EPUB cache cho Readium.
  - CBZ: Compose `LazyColumn` + Coil 3.0 với quản lý bộ nhớ ảnh độ phân giải cao.
  - TTS: Android `android.speech.tts.TextToSpeech` kết hợp câu lệnh đồng bộ `UtteranceProgressListener`.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Architecture & Project Scaffolding](./phase-01-architecture-project-scaffolding.md) | Completed |
| 2 | [Core Database & Storage Layer](./phase-02-core-database-storage-layer.md) | Completed |
| 3 | [Readium EPUB & Compose CBZ Engines](./phase-03-readium-epub-compose-cbz-engines.md) | Completed |
| 4 | [Libmobi NDK Bridge for AZW3](./phase-04-libmobi-ndk-bridge-for-azw3.md) | Completed |
| 5 | [Expressive UI & Library Feature](./phase-05-expressive-ui-library-feature.md) | Completed |
| 6 | [Reader Screen & Annotations](./phase-06-reader-screen-annotations.md) | Completed |
| 7 | [TTS Engine & Mini Player](./phase-07-tts-engine-mini-player.md) | Completed |
| 8 | [Polish Testing & Optimization](./phase-08-polish-testing-optimization.md) | Pending |

## Dependencies
- Android NDK r25+ (cho module `libmobi`).
- Readium Kotlin Toolkit (JitPack / MavenCentral).
- Android SDK 34 (UpsideDownCake) hoặc SDK 35 (VanillaIceCream).
