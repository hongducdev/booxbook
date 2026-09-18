package com.booxbook.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ReadingSession(
    val id: Long = 0L,
    val bookId: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val date: String // Format: yyyy-MM-dd
)
