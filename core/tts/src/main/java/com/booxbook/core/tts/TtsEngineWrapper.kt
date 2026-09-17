package com.booxbook.core.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.booxbook.core.tts.model.TtsPlaybackState
import com.booxbook.core.tts.model.TtsSentence
import com.booxbook.core.tts.model.TtsSessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.BreakIterator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsEngineWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var tts: TextToSpeech? = null
    private val sentences = mutableListOf<TtsSentence>()

    private val _state = MutableStateFlow(TtsSessionState())
    val state: StateFlow<TtsSessionState> = _state.asStateFlow()

    var onChapterFinishedListener: (() -> Unit)? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            tts = TextToSpeech(context, this)
        } catch (_: Throwable) {
            _state.update { it.copy(isInitialized = false) }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsInstance = tts ?: return

            // Prefer Vietnamese if available, fallback to default or US
            val viLocale = Locale("vi", "VN")
            val available = ttsInstance.isLanguageAvailable(viLocale)
            if (available >= TextToSpeech.LANG_AVAILABLE) {
                ttsInstance.language = viLocale
            } else {
                ttsInstance.language = Locale.getDefault()
            }

            ttsInstance.setSpeechRate(_state.value.speechRate)
            ttsInstance.setPitch(_state.value.pitch)

            setupProgressListener(ttsInstance)
            _state.update { it.copy(isInitialized = true) }
        } else {
            _state.update {
                it.copy(
                    isInitialized = false,
                    playbackState = TtsPlaybackState.Error("Không thể khởi tạo engine TTS của hệ thống")
                )
            }
        }
    }

    private fun setupProgressListener(ttsInstance: TextToSpeech) {
        ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                scope.launch {
                    _state.update { it.copy(playbackState = TtsPlaybackState.Playing) }
                }
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    val currentIndex = _state.value.currentSentenceIndex
                    if (currentIndex + 1 < sentences.size) {
                        speakSentence(currentIndex + 1)
                    } else {
                        _state.update {
                            it.copy(
                                playbackState = TtsPlaybackState.Idle,
                                currentSentenceIndex = sentences.size - 1
                            )
                        }
                        onChapterFinishedListener?.invoke()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                scope.launch {
                    _state.update {
                        it.copy(playbackState = TtsPlaybackState.Error("Lỗi phát âm câu $utteranceId"))
                    }
                }
            }
        })
    }

    fun loadContent(
        bookId: String,
        bookTitle: String,
        chapterTitle: String,
        rawText: String,
        startIndex: Int = 0
    ) {
        val parsedSentences = tokenizeSentences(rawText)
        sentences.clear()
        sentences.addAll(parsedSentences.mapIndexed { idx, txt ->
            TtsSentence(index = idx, text = txt)
        })

        val initialIndex = startIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        val initialSentence = sentences.getOrNull(initialIndex)?.text ?: ""

        // Detect language from text heuristics
        detectAndSetLanguage(rawText)

        _state.update {
            it.copy(
                bookId = bookId,
                bookTitle = bookTitle,
                chapterTitle = chapterTitle,
                currentSentenceIndex = initialIndex,
                totalSentences = sentences.size,
                currentSentence = initialSentence,
                playbackState = TtsPlaybackState.Paused
            )
        }
    }

    fun play() {
        val state = _state.value
        if (!state.isInitialized || sentences.isEmpty()) return
        speakSentence(state.currentSentenceIndex)
    }

    fun pause() {
        tts?.stop()
        _state.update { it.copy(playbackState = TtsPlaybackState.Paused) }
    }

    fun resume() {
        play()
    }

    fun stop() {
        tts?.stop()
        _state.update {
            it.copy(
                playbackState = TtsPlaybackState.Idle,
                currentSentence = "",
                currentSentenceIndex = 0
            )
        }
    }

    fun next() {
        val nextIndex = _state.value.currentSentenceIndex + 1
        if (nextIndex < sentences.size) {
            seekTo(nextIndex)
        }
    }

    fun previous() {
        val prevIndex = _state.value.currentSentenceIndex - 1
        if (prevIndex >= 0) {
            seekTo(prevIndex)
        }
    }

    fun seekTo(index: Int) {
        if (index in 0 until sentences.size) {
            val wasPlaying = _state.value.isPlaying
            _state.update {
                it.copy(
                    currentSentenceIndex = index,
                    currentSentence = sentences[index].text
                )
            }
            if (wasPlaying) {
                speakSentence(index)
            }
        }
    }

    fun setSpeed(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.5f)
        tts?.setSpeechRate(clamped)
        _state.update { it.copy(speechRate = clamped) }
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(clamped)
        _state.update { it.copy(pitch = clamped) }
    }

    private fun speakSentence(index: Int) {
        val sentence = sentences.getOrNull(index) ?: return
        val ttsInstance = tts ?: return

        _state.update {
            it.copy(
                currentSentenceIndex = index,
                currentSentence = sentence.text,
                playbackState = TtsPlaybackState.Playing
            )
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sentence_$index")
        }
        ttsInstance.speak(sentence.text, TextToSpeech.QUEUE_FLUSH, params, "sentence_$index")
    }

    fun detectAndSetLanguage(text: String) {
        val ttsInstance = tts ?: return
        // Check for Vietnamese diacritics
        val vietnameseChars = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"
        val hasVietnamese = text.take(500).any { it.lowercaseChar() in vietnameseChars }

        val targetLocale = if (hasVietnamese) {
            Locale("vi", "VN")
        } else {
            Locale.US
        }

        if (ttsInstance.isLanguageAvailable(targetLocale) >= TextToSpeech.LANG_AVAILABLE) {
            ttsInstance.language = targetLocale
        }
    }

    fun tokenizeSentences(text: String): List<String> {
        val clean = text.replace("\r", "").trim()
        if (clean.isBlank()) return emptyList()

        val results = mutableListOf<String>()
        val iterator = BreakIterator.getSentenceInstance(Locale.getDefault())
        iterator.setText(clean)

        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val sentence = clean.substring(start, end).trim()
            if (sentence.length > 1) {
                // Further split very long paragraphs with multiple newlines
                if (sentence.contains("\n\n")) {
                    sentence.split(Regex("\n+")).forEach { part ->
                        val sub = part.trim()
                        if (sub.length > 1) results.add(sub)
                    }
                } else {
                    results.add(sentence)
                }
            }
            start = end
            end = iterator.next()
        }

        return if (results.isEmpty()) listOf(clean) else results
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {}
        tts = null
        sentences.clear()
        _state.update { TtsSessionState() }
    }
}
