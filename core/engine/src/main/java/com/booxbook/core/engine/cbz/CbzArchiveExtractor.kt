package com.booxbook.core.engine.cbz

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Information about an image page in a CBZ archive.
 */
data class CbzPageEntry(
    val index: Int,
    val entryName: String,
    val fileName: String,
    val size: Long
)

/**
 * Natural order comparator for string/filenames ensuring human/numeric sorting
 * (e.g. "page1", "page2", ..., "page9", "page10" instead of lexicographical order).
 */
object NaturalOrderComparator : Comparator<String> {
    private val regex = Regex("(\\d+)|(\\D+)")

    override fun compare(s1: String, s2: String): Int {
        val tokens1 = regex.findAll(s1).map { it.value }.toList()
        val tokens2 = regex.findAll(s2).map { it.value }.toList()
        val minSize = minOf(tokens1.size, tokens2.size)

        for (i in 0 until minSize) {
            val t1 = tokens1[i]
            val t2 = tokens2[i]
            val isDigit1 = t1.all { it.isDigit() }
            val isDigit2 = t2.all { it.isDigit() }

            val cmp = if (isDigit1 && isDigit2) {
                val n1 = t1.toLongOrNull()
                val n2 = t2.toLongOrNull()
                if (n1 != null && n2 != null) {
                    n1.compareTo(n2)
                } else {
                    val b1 = t1.toBigIntegerOrNull() ?: BigInteger.ZERO
                    val b2 = t2.toBigIntegerOrNull() ?: BigInteger.ZERO
                    b1.compareTo(b2)
                }
            } else {
                t1.compareTo(t2, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        val lengthCmp = tokens1.size.compareTo(tokens2.size)
        return if (lengthCmp != 0) lengthCmp else s1.compareTo(s2, ignoreCase = true)
    }
}

/**
 * Parser and extractor for CBZ (Comic Book Zip) archives.
 * Implements natural sorting of pages, on-demand extraction for memory efficiency,
 * and cover extraction.
 */
class CbzArchive(
    val file: File,
    private val cacheDir: File
) : AutoCloseable {

    private val zipFile = ZipFile(file)
    private val _pages: List<CbzPageEntry>

    val pages: List<CbzPageEntry> get() = _pages
    val pageCount: Int get() = _pages.size

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val entries = mutableListOf<ZipEntry>()
        val enumeration = zipFile.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (isValidImageEntry(entry)) {
                entries.add(entry)
            }
        }

        // Sort entries by natural order using the full entry path
        // to preserve chapter/folder hierarchy (e.g. Vol1/Ch1 before Vol1/Ch2)
        entries.sortWith { a, b ->
            NaturalOrderComparator.compare(a.name, b.name)
        }

        _pages = entries.mapIndexed { index, entry ->
            CbzPageEntry(
                index = index,
                entryName = entry.name,
                fileName = entry.name.substringAfterLast('/'),
                size = entry.size
            )
        }
    }

    /**
     * Extracts a page by index on-demand into cache, or returns existing cached file.
     */
    fun getPageFile(index: Int): File {
        if (index !in _pages.indices) {
            throw IndexOutOfBoundsException("Page index $index out of bounds (0..${_pages.size - 1})")
        }
        val page = _pages[index]
        val extension = page.fileName.substringAfterLast('.', "jpg").lowercase()
        val cachedFile = File(cacheDir, "page_${index}.$extension")

        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile
        }

        val entry = zipFile.getEntry(page.entryName)
            ?: throw IllegalStateException("Không tìm thấy entry: ${page.entryName}")

        // Write to temporary file first and atomically rename to avoid partial reads by Coil
        val tempFile = File.createTempFile("cbz_tmp_${index}_", ".tmp", cacheDir)
        try {
            zipFile.getInputStream(entry).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (!tempFile.renameTo(cachedFile)) {
                tempFile.copyTo(cachedFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }

        return cachedFile
    }

    /**
     * Provides an [InputStream] for the given page index directly from the ZIP archive.
     */
    fun getPageInputStream(index: Int): InputStream {
        if (index !in _pages.indices) {
            throw IndexOutOfBoundsException("Page index $index out of bounds")
        }
        val page = _pages[index]
        val entry = zipFile.getEntry(page.entryName)
            ?: throw IllegalStateException("Không tìm thấy entry: ${page.entryName}")
        return zipFile.getInputStream(entry)
    }

    /**
     * Extracts the comic cover to [destinationFile].
     * Prefers any entry containing 'cover' in its name, otherwise takes page 0.
     */
    fun extractCover(destinationFile: File): File? {
        if (_pages.isEmpty()) return null

        val coverPage = _pages.firstOrNull { it.fileName.contains("cover", ignoreCase = true) }
            ?: _pages.first()

        val entry = zipFile.getEntry(coverPage.entryName) ?: return null
        destinationFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val tempFile = File.createTempFile("cbz_cover_tmp_", ".tmp", destinationFile.parentFile)
        try {
            zipFile.getInputStream(entry).use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }

        return if (destinationFile.exists() && destinationFile.length() > 0) destinationFile else null
    }

    /**
     * Clears all cached extracted page files.
     */
    fun clearCache() {
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }

    override fun close() {
        try {
            zipFile.close()
        } catch (_: Throwable) {}
    }

    companion object {
        val SUPPORTED_IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "avif"
        )

        fun isValidImageEntry(entry: ZipEntry): Boolean {
            if (entry.isDirectory) return false
            val name = entry.name
            // Exclude macOS and system files
            if (name.startsWith("__MACOSX") || name.contains("/.") || name.startsWith(".")) {
                return false
            }
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in SUPPORTED_IMAGE_EXTENSIONS
        }
    }
}

/**
 * Service to open and manage CBZ archives.
 */
@Singleton
class CbzArchiveExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val baseCacheDir: File by lazy {
        File(context.cacheDir, "cbz_cache").apply { if (!exists()) mkdirs() }
    }

    /**
     * Opens a CBZ file with dedicated cache directory.
     */
    fun openArchive(cbzFile: File, bookId: String): CbzArchive {
        val bookCacheDir = File(baseCacheDir, bookId)
        return CbzArchive(cbzFile, bookCacheDir)
    }

    /**
     * Extracts the cover image from a CBZ file directly.
     */
    suspend fun extractCover(cbzFile: File, bookId: String, destinationFile: File): Result<File?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempCacheDir = File(baseCacheDir, "temp_cover_$bookId")
                try {
                    CbzArchive(cbzFile, tempCacheDir).use { archive ->
                        archive.extractCover(destinationFile)
                    }
                } finally {
                    tempCacheDir.deleteRecursively()
                }
            }
        }
}
