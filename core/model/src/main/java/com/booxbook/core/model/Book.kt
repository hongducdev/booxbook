package com.booxbook.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String = "Tác giả chưa rõ",
    val filePath: String,
    val coverPath: String? = null,
    val format: BookFormat,
    val totalPages: Int = 0,
    val fileSize: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastReadTimestamp: Long = 0L
)
