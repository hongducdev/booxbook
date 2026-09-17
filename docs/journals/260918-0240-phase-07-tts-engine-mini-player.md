# Journal Entry: Phase 07 - TTS Engine & Expressive Mini Player

- **Date:** 2026-09-18 02:40
- **Author:** ck:cook
- **Scope:** `:core:tts`, `:feature:reader`, `:app`
- **Status:** Complete

## 1. Problem Statement
Readers needed an audio reading option (Text-To-Speech) for EPUB and AZW3 text books with continuous background audio, responsive visual sentence synchronization, lock screen/notification controls, and an expressive, tactile floating player on Android.

## 2. Technical Architecture & Decisions
- **`:core:tts` Module:**
  - `TtsEngineWrapper`: Wraps Android `TextToSpeech`, handles initialization, dynamic Vietnamese and English locale detection, sentence boundary segmentation (`BreakIterator`), speech rate/pitch clamping, and sentence progress reporting via Kotlin `StateFlow`.
  - `TtsService`: Android `Service` running in the foreground with `mediaPlayback` type. Dispatches Media Notification with Play/Pause, Next Sentence, Previous Sentence, and Stop actions via PendingIntents.
  - Handles `AudioManager.ACTION_AUDIO_BECOMING_NOISY` to pause playback gracefully when headphones or Bluetooth audio disconnects.
- **Expressive UI Mini-Player (`feature/reader`):**
  - `TtsFloatingPlayer`: Material 3 Expressive floating card positioned above bottom navigation controls. Features a squircle bouncy spring Play/Pause button, sentence progress bar ("Câu X / Y"), sentence preview, and a speed chip.
  - `TtsSpeedSelectorSheet`: Bottom sheet allowing selection of reading speeds from 0.75x to 2.0x.
  - Top Bar integration: Added headphone toggle button to `AnimatedReaderTopBar` when reading text books (EPUB / AZW3).

## 3. Verification
- All 18 unit tests passed across all modules including new sentence tokenization and locale detection tests.
- Verified `./gradlew assembleDebug` successfully packaged the APK with `:core:tts` and the media playback foreground service.
