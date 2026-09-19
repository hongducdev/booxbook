package com.booxbook.core.tts

import com.booxbook.core.tts.model.TtsPlaybackState
import com.booxbook.core.tts.model.TtsSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.BreakIterator
import java.util.Locale

class TtsEngineWrapperTest {

    private fun tokenizeSentences(text: String): List<String> {
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

    private fun detectLanguageIsVietnamese(text: String): Boolean {
        val vietnameseChars = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"
        return text.take(500).any { it.lowercaseChar() in vietnameseChars }
    }

    @Test
    fun `sentence tokenizer splits multi-sentence paragraph correctly`() {
        val text = "Xin chào các bạn. Đây là ứng dụng BooxBook đọc sách trên máy đọc sách! Bạn có thích không? Chúc đọc vui vẻ."
        val sentences = tokenizeSentences(text)

        assertEquals(4, sentences.size)
        assertTrue(sentences[0].contains("Xin chào các bạn"))
        assertTrue(sentences[1].contains("BooxBook"))
        assertTrue(sentences[2].contains("Bạn có thích không"))
        assertTrue(sentences[3].contains("Chúc đọc vui vẻ"))
    }

    @Test
    fun `sentence tokenizer handles blank and single sentence input`() {
        assertTrue(tokenizeSentences("").isEmpty())
        assertTrue(tokenizeSentences("    ").isEmpty())

        val single = tokenizeSentences("Chỉ có một câu duy nhất")
        assertEquals(1, single.size)
        assertEquals("Chỉ có một câu duy nhất", single[0])
    }

    @Test
    fun `language detection distinguishes Vietnamese and English`() {
        val viText = "BooxBook hỗ trợ đọc sách điện tử tuyệt vời với ngôn ngữ tiếng Việt."
        val enText = "BooxBook is a modern ebook reader with Material 3 Expressive UI."

        assertTrue("Should detect Vietnamese text", detectLanguageIsVietnamese(viText))
        assertFalse("Should detect English text", detectLanguageIsVietnamese(enText))
    }

    @Test
    fun `tts session state properties behavior`() {
        val idleState = TtsSessionState(playbackState = TtsPlaybackState.Idle)
        assertFalse(idleState.isPlaying)
        assertFalse(idleState.isActive)

        val playingState = TtsSessionState(
            playbackState = TtsPlaybackState.Playing,
            bookTitle = "Dế Mèn Phiêu Lưu Ký",
            currentSentence = "Tôi sống độc lập từ thuở bé.",
            currentLocator = "{\"href\":\"chapter1.xhtml\",\"text\":{\"highlight\":\"Tôi sống độc lập từ thuở bé.\"}}",
            currentSentenceIndex = 0,
            totalSentences = 10
        )
        assertTrue(playingState.isPlaying)
        assertTrue(playingState.isActive)
        assertEquals(10, playingState.totalSentences)
        assertEquals("{\"href\":\"chapter1.xhtml\",\"text\":{\"highlight\":\"Tôi sống độc lập từ thuở bé.\"}}", playingState.currentLocator)

        val pausedState = TtsSessionState(playbackState = TtsPlaybackState.Paused)
        assertFalse(pausedState.isPlaying)
        assertTrue(pausedState.isActive)
    }
}
