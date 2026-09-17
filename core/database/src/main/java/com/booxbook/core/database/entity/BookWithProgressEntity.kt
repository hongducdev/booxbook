package com.booxbook.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.booxbook.core.model.Book
import com.booxbook.core.model.ReadingProgress

data class BookWithProgressEntity(
    @Embedded
    val book: BookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "book_id"
    )
    val progress: ReadingProgressEntity?
)

data class BookWithProgress(
    val book: Book,
    val progress: ReadingProgress?
)

fun BookWithProgressEntity.asDomainModel(): BookWithProgress = BookWithProgress(
    book = book.asDomainModel(),
    progress = progress?.asDomainModel()
)
