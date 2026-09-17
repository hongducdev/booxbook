package com.booxbook.core.tts.model

data class TtsSentence(
    val index: Int,
    val text: String,
    val locator: String? = null
)

sealed interface TtsPlaybackState {
    data object Idle : TtsPlaybackState
    data object Loading : TtsPlaybackState
    data object Playing : TtsPlaybackState
    data object Paused : TtsPlaybackState
    data class Error(val message: String) : TtsPlaybackState
}

data class TtsSessionState(
    val playbackState: TtsPlaybackState = TtsPlaybackState.Idle,
    val bookId: String = "",
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentence: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val isInitialized: Boolean = false
) {
    val isPlaying: Boolean
        get() = playbackState is TtsPlaybackState.Playing

    val isActive: Boolean
        get() = playbackState !is TtsPlaybackState.Idle
}
