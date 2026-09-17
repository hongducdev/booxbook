package com.booxbook.core.engine.cbz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CbzArchiveExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `natural order comparator correctly sorts alphanumeric page names`() {
        val filenames = listOf(
            "page10.jpg",
            "page1.jpg",
            "page2.jpg",
            "page20.jpg",
            "page3.jpg",
            "page11.jpg",
            "page100.jpg"
        )

        val sorted = filenames.sortedWith(NaturalOrderComparator)

        val expected = listOf(
            "page1.jpg",
            "page2.jpg",
            "page3.jpg",
            "page10.jpg",
            "page11.jpg",
            "page20.jpg",
            "page100.jpg"
        )
        assertEquals(expected, sorted)
    }

    @Test
    fun `natural order comparator handles mixed prefixes and case correctly`() {
        val filenames = listOf(
            "Ch1_p02.png",
            "ch1_p01.png",
            "Ch1_p10.png",
            "Ch1_p09.png"
        )

        val sorted = filenames.sortedWith(NaturalOrderComparator)

        assertEquals("ch1_p01.png", sorted[0])
        assertEquals("Ch1_p02.png", sorted[1])
        assertEquals("Ch1_p09.png", sorted[2])
        assertEquals("Ch1_p10.png", sorted[3])
    }

    @Test
    fun `cbz archive filters non-image entries and ignores macos metadata`() {
        val validEntry = ZipEntry("chapter1/page01.jpg")
        val textEntry = ZipEntry("info.txt")
        val macEntry = ZipEntry("__MACOSX/._page01.jpg")
        val hiddenEntry = ZipEntry(".DS_Store")
        val dirEntry = ZipEntry("chapter1/")

        assertTrue(CbzArchive.isValidImageEntry(validEntry))
        assertFalse(CbzArchive.isValidImageEntry(textEntry))
        assertFalse(CbzArchive.isValidImageEntry(macEntry))
        assertFalse(CbzArchive.isValidImageEntry(hiddenEntry))
        assertFalse(CbzArchive.isValidImageEntry(dirEntry))
    }

    @Test
    fun `cbz archive extracts pages and cover successfully`() {
        val cbzFile = tempFolder.newFile("comic.cbz")
        val cacheDir = tempFolder.newFolder("cache")

        // Create sample CBZ archive
        ZipOutputStream(FileOutputStream(cbzFile)).use { zos ->
            // Page 2
            zos.putNextEntry(ZipEntry("page_2.jpg"))
            zos.write("page 2 content".toByteArray())
            zos.closeEntry()

            // Page 10
            zos.putNextEntry(ZipEntry("page_10.jpg"))
            zos.write("page 10 content".toByteArray())
            zos.closeEntry()

            // Page 1
            zos.putNextEntry(ZipEntry("page_1.jpg"))
            zos.write("page 1 content".toByteArray())
            zos.closeEntry()

            // Ignored entry
            zos.putNextEntry(ZipEntry("readme.txt"))
            zos.write("some text".toByteArray())
            zos.closeEntry()
        }

        val archive = CbzArchive(cbzFile, cacheDir)

        assertEquals(3, archive.pageCount)
        assertEquals("page_1.jpg", archive.pages[0].fileName)
        assertEquals("page_2.jpg", archive.pages[1].fileName)
        assertEquals("page_10.jpg", archive.pages[2].fileName)

        // Test on-demand page extraction
        val page0 = archive.getPageFile(0)
        assertTrue(page0.exists())
        assertEquals("page 1 content", page0.readText())

        // Test cover extraction
        val coverFile = tempFolder.newFile("extracted_cover.jpg")
        val extractedCover = archive.extractCover(coverFile)
        assertNotNull(extractedCover)
        assertTrue(coverFile.exists())
        assertEquals("page 1 content", coverFile.readText())

        archive.close()
    }

    @Test
    fun `cbz archive preserves multi-folder chapter hierarchy`() {
        val cbzFile = tempFolder.newFile("multi_chapter.cbz")
        val cacheDir = tempFolder.newFolder("cache_multi")

        ZipOutputStream(FileOutputStream(cbzFile)).use { zos ->
            // Insert in scrambled order
            zos.putNextEntry(ZipEntry("Ch02/02.jpg"))
            zos.write("ch2 p2".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("Ch01/02.jpg"))
            zos.write("ch1 p2".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("Ch02/01.jpg"))
            zos.write("ch2 p1".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("Ch01/01.jpg"))
            zos.write("ch1 p1".toByteArray())
            zos.closeEntry()
        }

        val archive = CbzArchive(cbzFile, cacheDir)

        assertEquals(4, archive.pageCount)
        assertEquals("Ch01/01.jpg", archive.pages[0].entryName)
        assertEquals("Ch01/02.jpg", archive.pages[1].entryName)
        assertEquals("Ch02/01.jpg", archive.pages[2].entryName)
        assertEquals("Ch02/02.jpg", archive.pages[3].entryName)

        archive.close()
    }

    @Test
    fun `natural order comparator handles page01 and page1 with tie-breaker`() {
        val filenames = listOf("page1.jpg", "page01.jpg")
        val sorted = filenames.sortedWith(NaturalOrderComparator)
        // Both exist, comparator distinguishes them consistently
        assertEquals(2, sorted.size)
        assertEquals(listOf("page01.jpg", "page1.jpg"), sorted)
        assertTrue(NaturalOrderComparator.compare("page01.jpg", "page1.jpg") < 0)
    }

    @Test
    fun `cbz archive prefers cover image entry if available`() {
        val cbzFile = tempFolder.newFile("manga_with_cover.cbz")
        val cacheDir = tempFolder.newFolder("cache2")

        ZipOutputStream(FileOutputStream(cbzFile)).use { zos ->
            zos.putNextEntry(ZipEntry("01.jpg"))
            zos.write("content 01".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("cover.jpg"))
            zos.write("comic cover art".toByteArray())
            zos.closeEntry()
        }

        val archive = CbzArchive(cbzFile, cacheDir)
        val coverDest = tempFolder.newFile("cover_out.jpg")
        archive.extractCover(coverDest)

        assertEquals("comic cover art", coverDest.readText())
        archive.close()
    }
}
