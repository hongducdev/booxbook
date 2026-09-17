package com.booxbook.core.engine

import com.booxbook.core.engine.model.ReaderState
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Base abstraction contract for reader engines (EPUB, CBZ, AZW3).
 */
interface ReaderEngine {
    val supportedFormat: BookFormat
    val state: StateFlow<ReaderState>

    /**
     * Opens and prepares the book for reading.
     */
    suspend fun openBook(book: Book): Result<Unit>

    /**
     * Closes the active publication/archive and frees resources.
     */
    suspend fun closeBook()

    /**
     * Extracts the cover image of the given book to [destinationFile].
     * Returns the output file if successful, or null/failure otherwise.
     */
    suspend fun extractCover(book: Book, destinationFile: File): Result<File?>
}
