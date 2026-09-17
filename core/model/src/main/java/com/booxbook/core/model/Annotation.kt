package com.booxbook.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AnnotationType {
    HIGHLIGHT,
    NOTE,
    BOOKMARK
}

@Serializable
data class Annotation(
    val id: Long = 0L,
    val bookId: String,
    val type: AnnotationType,
    val locator: String,
    val selectedText: String? = null,
    val noteContent: String? = null,
    val colorHex: String = "#FFEB3B",
    val createdTimestamp: Long = System.currentTimeMillis()
)
