package com.booxbook.core.engine.azw3

import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.model.BookFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class Azw3ConverterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `converter returns error when azw3 file does not exist`() = runTest(testDispatcher) {
        val converter = Azw3Converter().apply {
            ioDispatcher = testDispatcher
        }
        val nonExistentFile = File(tempFolder.root, "does_not_exist.azw3")
        val cacheDir = tempFolder.newFolder("cache")

        val result = converter.convertToEpub(nonExistentFile, cacheDir)
        assertTrue("Non-existent file should yield Error", result is Azw3ConversionResult.Error)
        assertTrue(
            "Error message should mention file not found",
            (result as Azw3ConversionResult.Error).message.contains("không tồn tại", ignoreCase = true)
        )
    }

    @Test
    fun `converter returns cached epub if already generated`() = runTest(testDispatcher) {
        val converter = Azw3Converter().apply {
            ioDispatcher = testDispatcher
        }
        val azw3File = tempFolder.newFile("sample.azw3")
        azw3File.writeText("MOBI dummy content")

        val cacheDir = tempFolder.newFolder("cache")
        val azw3CacheDir = File(cacheDir, "azw3_cache").apply { mkdirs() }

        // Compute expected key
        val input = "${azw3File.absolutePath}_${azw3File.length()}_${azw3File.lastModified()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val cacheKey = digest.joinToString("") { "%02x".format(it) }.take(16)

        val cachedEpub = File(azw3CacheDir, "$cacheKey.epub")
        cachedEpub.writeText("PK fake epub content")

        val result = converter.convertToEpub(azw3File, cacheDir)
        assertTrue("Should return Success from cache", result is Azw3ConversionResult.Success)
        val success = result as Azw3ConversionResult.Success
        assertTrue("Should indicate fromCache = true", success.fromCache)
        assertEquals(cachedEpub.absolutePath, success.epubFile.absolutePath)
    }

    @Test
    fun `azw3 format constants and preferences verification`() {
        assertEquals(BookFormat.AZW3, BookFormat.fromExtension("azw3"))
        assertEquals("azw3", BookFormat.AZW3.extension)

        val prefs = ReaderPreferences()
        assertFalse(prefs.isScrollMode)
    }
}
