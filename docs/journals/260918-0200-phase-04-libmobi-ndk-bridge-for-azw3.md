# Journal Entry: Phase 04 - Libmobi NDK Bridge for AZW3

- **Date:** 2026-09-18 02:00
- **Author:** ck:cook
- **Scope:** `:core:engine`, `:feature:reader`
- **Status:** Complete

## 1. Problem Statement
The application required native decoding and reading capabilities for Amazon Kindle AZW3/KF8 format books, avoiding proprietary readers while maintaining the discrete pagination and typographic excellence provided by the Readium EPUB engine.

## 2. Technical Architecture & Decisions
- **C++ NDK Bridge (`azw3_bridge`):**
  - Integrated open-source `libmobi` C99 core engine into `core/engine/src/main/cpp`.
  - Configured CMake to compile native shared library for `arm64-v8a`, `armeabi-v7a`, and `x86_64` targets.
  - Linked against Android NDK native `zlib` (`-lz`) and `log` (`-llog`).
  - Implemented `azw3_bridge.cpp` with JNI methods:
    - `nativeConvertAzw3ToEpub`: Unwraps KF8 records, reconstructs OPF manifest via `opf.c`, packages standard EPUB ZIP container.
    - `nativeIsDrmProtected`: Detects Kindle DRM locks safely.
    - `nativeExtractCover`: Directly reads cover bytes from EXTH record `EXTH_COVEROFFSET` without full book conversion.
- **Smart Conversion & Cache Management (`Azw3Converter`):**
  - Uses SHA-256 fingerprint of file path, length, and timestamp to key cache files in `azw3_cache/`.
  - Automatic cache hit return prevents redundant conversions.
  - Implemented automated LRU cache size management capping disk usage at 250 MB.
- **Unified Readium Interface (`ReadiumReaderEngine`):**
  - Extracted common interface for `EpubReaderEngine` and `Azw3ReaderEngine`.
  - Allowed `EpubReaderContainer` and `ReaderViewModel` to drive both formats seamlessly with discrete page turns.

## 3. Verification
- Android NDK cross-compilation verified for all 3 ABI architectures (`arm64-v8a`, `armeabi-v7a`, `x86_64`).
- All 15 unit tests across core and feature modules passed cleanly.
- Verified debug and release compilation via `./gradlew assembleDebug`.
