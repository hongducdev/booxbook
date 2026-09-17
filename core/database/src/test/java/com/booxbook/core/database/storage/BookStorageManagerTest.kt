package com.booxbook.core.database.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.booxbook.core.model.BookFormat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BookStorageManagerTest {

    private lateinit var context: Context
    private lateinit var storageManager: BookStorageManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storageManager = BookStorageManager(context)
    }

    @Test
    fun importCbzFileExtractsFirstImageAsCover() = runTest {
        // Create a temporary CBZ (zip) file
        val tempCbz = File(context.cacheDir, "manga_vol1.cbz")
        ZipOutputStream(FileOutputStream(tempCbz)).use { zip ->
            // Page 2
            zip.putNextEntry(ZipEntry("page_02.jpg"))
            zip.write("page2_data".toByteArray())
            zip.closeEntry()

            // Page 1 (alphabetically first image)
            zip.putNextEntry(ZipEntry("page_01.jpg"))
            zip.write("page1_cover_data".toByteArray())
            zip.closeEntry()
        }

        val uri = Uri.fromFile(tempCbz)
        val result = storageManager.importBookFromUri(uri)

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(BookFormat.CBZ, info.format)
        assertEquals("manga_vol1", info.title)
        assertNotNull(info.coverPath)

        val coverFile = File(info.coverPath!!)
        assertTrue(coverFile.exists())
        assertEquals("page1_cover_data", coverFile.readText())

        // Test deleteBookFiles
        val deleted = storageManager.deleteBookFiles(info.filePath, info.coverPath)
        assertTrue(deleted)
        assertTrue(!File(info.filePath).exists())
        assertTrue(!coverFile.exists())
    }

    @Test
    fun importEpubFileExtractsTitleAuthorAndCover() = runTest {
        // Create a temporary EPUB file with container.xml and content.opf
        val tempEpub = File(context.cacheDir, "sample.epub")
        ZipOutputStream(FileOutputStream(tempEpub)).use { zip ->
            // container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent()
            zip.write(containerXml.toByteArray())
            zip.closeEntry()

            // content.opf
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opfXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Nhà Giả Kim</dc:title>
                        <dc:creator>Paulo Coelho</dc:creator>
                    </metadata>
                    <manifest>
                        <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                    </manifest>
                </package>
            """.trimIndent()
            zip.write(opfXml.toByteArray())
            zip.closeEntry()

            // cover image
            zip.putNextEntry(ZipEntry("OEBPS/images/cover.jpg"))
            zip.write("epub_cover_bytes".toByteArray())
            zip.closeEntry()
        }

        val uri = Uri.fromFile(tempEpub)
        val result = storageManager.importBookFromUri(uri)

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(BookFormat.EPUB, info.format)
        assertEquals("Nhà Giả Kim", info.title)
        assertEquals("Paulo Coelho", info.author)
        assertNotNull(info.coverPath)

        val coverFile = File(info.coverPath!!)
        assertTrue(coverFile.exists())
        assertEquals("epub_cover_bytes", coverFile.readText())

        // Clean up
        storageManager.deleteBookFiles(info.filePath, info.coverPath)
    }
}
