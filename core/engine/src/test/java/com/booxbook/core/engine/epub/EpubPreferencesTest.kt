package com.booxbook.core.engine.epub

import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.ReaderState
import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubPreferencesTest {

    @Test
    fun `reader preferences default to discrete page turn`() {
        val defaultPrefs = ReaderPreferences()
        assertFalse("Default should be discrete pagination (scroll = false)", defaultPrefs.isScrollMode)
        assertEquals(1.0, defaultPrefs.fontSize, 0.001)
        assertEquals(1.2, defaultPrefs.lineHeight, 0.001)
        assertEquals(1.0, defaultPrefs.pageMargins, 0.001)
    }

    @Test
    fun `reader state transitions and toc modeling`() {
        val book = Book(
            id = "test-book",
            title = "Test Book",
            filePath = "/path/to/book.epub",
            format = BookFormat.EPUB
        )

        val idleState: ReaderState = ReaderState.Idle
        assertTrue(idleState is ReaderState.Idle)

        val loadingState: ReaderState = ReaderState.Loading(book)
        assertTrue(loadingState is ReaderState.Loading)
        assertEquals("test-book", (loadingState as ReaderState.Loading).book.id)

        val subChapter = TocItem("1.1 Sub-section", "chap1_1.html")
        val chapter = TocItem("1. Chapter One", "chap1.html", listOf(subChapter))
        val readyState: ReaderState = ReaderState.Ready(
            book = book,
            tableOfContents = listOf(chapter),
            totalPages = 10
        )
        assertTrue(readyState is ReaderState.Ready)
        val ready = readyState as ReaderState.Ready
        assertEquals(1, ready.tableOfContents.size)
        assertEquals(1, ready.tableOfContents[0].children.size)
        assertEquals("1.1 Sub-section", ready.tableOfContents[0].children[0].title)
    }
}
