package com.booxbook.core.engine

import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat

/**
 * Base abstraction contract for reader engines (EPUB, AZW3, CBZ).
 * Full implementations are developed in Phase 3 and Phase 4.
 */
interface ReaderEngine {
    val supportedFormat: BookFormat
    suspend fun openBook(book: Book): Result<Unit>
    suspend fun closeBook()
}
