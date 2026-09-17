package com.booxbook.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ReadingProgress(
    val bookId: String,
    val locator: String, // CFI progression string for EPUB/AZW3 or page index for CBZ
    val percentage: Float = 0f,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val updatedTimestamp: Long = System.currentTimeMillis()
)
