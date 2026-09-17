---
phase: 7
title: "TTS Engine & Mini Player"
status: pending
priority: P2
effort: "2d"
dependencies: ["6"]
---

# Phase 7: TTS Engine & Mini Player

## Overview
Tích hợp trình đọc văn bản thành giọng nói (Text-To-Speech) cho sách chữ (EPUB & AZW3). Xây dựng thanh điều khiển phát âm thanh nổi (Floating Mini-Player) lấy cảm hứng từ trình phát nhạc Pixel Expressive (Google Podcasts / Pixel Media Player) với tính năng đồng bộ câu đang đọc lên trang sách.

## Requirements
- **Functional:**
  - **TTS Engine:**
    - Khởi tạo `android.speech.tts.TextToSpeech`.
    - Trích xuất nội dung văn bản theo từng đoạn/câu từ chương hiện tại của sách.
    - Hỗ trợ ngôn ngữ tiếng Việt (vi-VN) và tiếng Anh (en-US), tự động nhận diện ngôn ngữ của sách.
    - Điều chỉnh tốc độ đọc (0.75x, 1.0x, 1.25x, 1.5x, 2.0x) và cao độ (pitch).
  - **Đồng bộ hóa (Visual Synchronization):**
    - Lắng nghe `UtteranceProgressListener.onRangeStart` hoặc `onStart` để xác định câu đang đọc.
    - Tự động highlight câu đang đọc trên trang sách và tự động lật sang trang tiếp theo khi đọc hết trang.
  - **Expressive Mini-Player:**
    - Thanh điều khiển nổi ở đáy màn hình: Nút Play/Pause dạng Squircle (Expressive shape), nút Tua tới/lui 10s, Nút chọn tốc độ đọc, và Thanh tiến độ câu.
    - Hỗ trợ Foreground Service có Notification điều khiển khi tắt màn hình (Background Audio).
- **Non-functional:**
  - Chuyển chương mượt mà khi đọc hết chương cũ (tải trước câu tiếp theo).
  - Tạm dừng khi có cuộc gọi đến hoặc rút tai nghe (`AudioManager.AUDIOBECOMING_NOISY`).

## Architecture
```
[User clicks TTS] ──► [TtsPlaybackService (Foreground)]
                                │
                                ├──► [Android TextToSpeech API]
                                │          │ (Utterance Events)
                                │          ▼
                                └──► [SyncHighlightState] ──► [ReaderScreen Highlight & Auto-flip]
                                           ▲
                                           │
                                [FloatingMiniPlayer (UI)]
```

## Related Code Files
- Create: `core/tts/src/main/java/com/booxbook/core/tts/TtsService.kt`
- Create: `core/tts/src/main/java/com/booxbook/core/tts/TtsEngineWrapper.kt`
- Create: `core/tts/src/main/java/com/booxbook/core/tts/model/TtsState.kt`
- Create: `feature/reader/src/main/java/com/booxbook/feature/reader/components/TtsFloatingPlayer.kt`
- Create: `feature/reader/src/main/java/com/booxbook/feature/reader/components/TtsSpeedSelector.kt`

## Implementation Steps
1. Xây dựng `TtsEngineWrapper`:
   - Quản lý vòng đời `TextToSpeech` và thiết lập `UtteranceProgressListener`.
   - Viết hàm tách văn bản thành danh sách câu hoàn chỉnh (Sentence Tokenizer).
2. Xây dựng `TtsService` (Android Foreground Service):
   - Tạo Media Notification hỗ trợ điều khiển Play / Pause / Next / Prev từ màn hình khóa.
   - Xử lý Audio Focus để tạm dừng khi có âm thanh khác phát đè lên.
3. Thiết kế `TtsFloatingPlayer` trên Compose:
   - Floating Card với bo góc mềm mại, đổ bóng nhẹ.
   - Nút Play/Pause hoạt ảnh lò xo (Spring bounce animation khi nhấn).
   - Menu trượt để chọn tốc độ phát.
4. Kết nối `TtsService` với `ReaderViewModel`:
   - Khi TTS phát câu nào, gửi index câu đó về ViewModel để cập nhật visual highlight trên `EpubNavigator`.

## Success Criteria
- [ ] TTS phát giọng đọc rõ ràng bằng tiếng Việt hoặc tiếng Anh.
- [ ] Câu đang đọc được bôi vàng nổi bật trên màn hình và tự lật trang khi đọc sang trang mới.
- [ ] Mini-player điều khiển mượt mà, tắt màn hình vẫn tiếp tục đọc được qua Foreground Service.
