package com.booxbook.core.engine.model

import com.booxbook.core.model.Book

/**
 * Table of Contents item for navigation.
 */
data class TocItem(
    val title: String,
    val href: String,
    val children: List<TocItem> = emptyList()
)

/**
 * Reader user preferences for text rendering and page transitions.
 */
data class ReaderPreferences(
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.2,
    val pageMargins: Double = 1.0,
    val isScrollMode: Boolean = false, // false = Discrete pagination (lật từng trang)
    val fontFamily: String? = null,
    val isDarkMode: Boolean = false,
    val themePreset: String = "DARK",
    /** Tap-zone layout key, resolved by `ReaderTapZoneMode` in :feature:reader. */
    val tapZoneMode: String = "KINDLE",
    /** Page-turn transition key, resolved by `ReaderPageTurnEffect` in :feature:reader. */
    val pageTurnEffect: String = "SLIDE",
    val hapticsEnabled: Boolean = true
)

/**
 * State representing reader engine lifecycle and reading session.
 */
sealed interface ReaderState {
    data object Idle : ReaderState

    data class Loading(val book: Book) : ReaderState

    data class Ready(
        val book: Book,
        val tableOfContents: List<TocItem> = emptyList(),
        val totalPages: Int = 0,
        val currentLocator: String? = null,
        val progress: Float = 0f
    ) : ReaderState

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : ReaderState
}
