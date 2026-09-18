package com.booxbook.core.engine

import android.content.Context
import com.booxbook.core.engine.azw3.Azw3ConversionResult
import com.booxbook.core.engine.azw3.Azw3Converter
import com.booxbook.core.engine.cbz.CbzArchiveExtractor
import com.booxbook.core.engine.epub.ReadiumAssetRetriever
import com.booxbook.core.engine.model.TocItem
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to extract Table of Contents (mục lục) from ebook files
 * without rendering the full reader UI.
 */
@Singleton
class BookTocExtractor @Inject constructor(
    private val readiumAssetRetriever: ReadiumAssetRetriever,
    private val cbzArchiveExtractor: CbzArchiveExtractor,
    private val azw3Converter: Azw3Converter,
    @ApplicationContext private val context: Context
) {
    suspend fun extractToc(book: Book): List<TocItem> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(book.filePath)
            if (!file.exists()) return@runCatching emptyList()

            when (book.format) {
                BookFormat.EPUB -> {
                    val pubResult = readiumAssetRetriever.openPublication(file)
                    val publication = pubResult.getOrNull() ?: return@runCatching emptyList()
                    try {
                        mapLinksToToc(publication.tableOfContents)
                    } finally {
                        try { publication.close() } catch (_: Throwable) {}
                    }
                }
                BookFormat.CBZ -> {
                    val archive = cbzArchiveExtractor.openArchive(file, book.id)
                    try {
                        archive.pages.map { page ->
                            TocItem(
                                title = "Trang ${page.index + 1}",
                                href = "page://${page.index}"
                            )
                        }
                    } finally {
                        try { archive.close() } catch (_: Throwable) {}
                    }
                }
                BookFormat.AZW3 -> {
                    val conversion = azw3Converter.convertToEpub(file, context.cacheDir)
                    if (conversion is Azw3ConversionResult.Success) {
                        val pub = readiumAssetRetriever.openPublication(conversion.epubFile).getOrNull()
                        try {
                            pub?.let { mapLinksToToc(it.tableOfContents) } ?: emptyList()
                        } finally {
                            try { pub?.close() } catch (_: Throwable) {}
                        }
                    } else {
                        emptyList()
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun mapLinksToToc(links: List<Link>): List<TocItem> {
        return links.map { link ->
            TocItem(
                title = link.title ?: link.href.toString(),
                href = link.href.toString(),
                children = mapLinksToToc(link.children)
            )
        }
    }
}
