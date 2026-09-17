package com.booxbook.feature.reader

import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Annotation
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat

enum class ActiveReaderSheet {
    TOC,
    SETTINGS,
    BOOKMARKS
}

enum class ReaderThemePreset(val displayName: String) {
    LIGHT("Sáng"),
    SEPIA("Giấy ấm"),
    DARK("Tối"),
    AMOLED("Đen tuyền")
}

/**
 * State representing the active reading session.
 */
data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: Book? = null,
    val format: BookFormat = BookFormat.EPUB,
    val tableOfContents: List<TocItem> = emptyList(),
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val progressPercentage: Float = 0f,
    val currentChapterTitle: String = "",
    val currentLocator: String? = null,
    val isControlsVisible: Boolean = false,
    val preferences: ReaderPreferences = ReaderPreferences(),
    val themePreset: ReaderThemePreset = ReaderThemePreset.DARK,
    val activeSheet: ActiveReaderSheet? = null,
    val annotations: List<Annotation> = emptyList(),
    val isCurrentLocationBookmarked: Boolean = false,
    val errorMessage: String? = null
)
