package com.booxbook.core.engine.azw3

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class Azw3ConversionResult {
    data class Success(val epubFile: File, val fromCache: Boolean = false) : Azw3ConversionResult()
    data object DrmProtected : Azw3ConversionResult()
    data class Error(val message: String, val code: Int = -1) : Azw3ConversionResult()
}

@Singleton
class Azw3Converter @Inject constructor() {

    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    companion object {
        private const val MAX_CACHE_SIZE_BYTES = 250L * 1024L * 1024L // 250 MB

        private var isNativeLoaded = false

        init {
            try {
                System.loadLibrary("azw3_bridge")
                isNativeLoaded = true
            } catch (_: UnsatisfiedLinkError) {
                isNativeLoaded = false
            } catch (_: Throwable) {
                isNativeLoaded = false
            }
        }

        fun isLibraryAvailable(): Boolean = isNativeLoaded
    }

    suspend fun convertToEpub(azw3File: File, cacheDir: File): Azw3ConversionResult = withContext(ioDispatcher) {
        if (!azw3File.exists()) {
            return@withContext Azw3ConversionResult.Error("File không tồn tại: ${azw3File.absolutePath}")
        }

        val azw3CacheDir = File(cacheDir, "azw3_cache").apply { if (!exists()) mkdirs() }
        val cacheKey = computeCacheKey(azw3File)
        val targetEpub = File(azw3CacheDir, "$cacheKey.epub")

        // Smart cache check: if already converted and valid
        if (targetEpub.exists() && targetEpub.length() > 0) {
            targetEpub.setLastModified(System.currentTimeMillis())
            return@withContext Azw3ConversionResult.Success(targetEpub, fromCache = true)
        }

        if (!isNativeLoaded) {
            return@withContext Azw3ConversionResult.Error(
                "Thư viện native azw3_bridge chưa được nạp trên thiết bị này"
            )
        }

        val tempOutput = File.createTempFile("azw3_conv_", ".epub.tmp", azw3CacheDir)
        try {
            val resultCode = nativeConvertAzw3ToEpub(
                inputPath = azw3File.absolutePath,
                outputEpubPath = tempOutput.absolutePath
            )

            when (resultCode) {
                0 -> {
                    if (tempOutput.exists() && tempOutput.length() > 0) {
                        if (targetEpub.exists()) targetEpub.delete()
                        if (!tempOutput.renameTo(targetEpub)) {
                            tempOutput.copyTo(targetEpub, overwrite = true)
                            tempOutput.delete()
                        }
                        cleanOldCache(azw3CacheDir, MAX_CACHE_SIZE_BYTES)
                        Azw3ConversionResult.Success(targetEpub, fromCache = false)
                    } else {
                        tempOutput.delete()
                        Azw3ConversionResult.Error("Tệp EPUB sau chuyển đổi rỗng hoặc không tạo được")
                    }
                }
                1 -> {
                    tempOutput.delete()
                    Azw3ConversionResult.DrmProtected
                }
                2 -> {
                    tempOutput.delete()
                    Azw3ConversionResult.Error("Định dạng AZW3 không hợp lệ hoặc tệp bị hỏng", resultCode)
                }
                3 -> {
                    tempOutput.delete()
                    Azw3ConversionResult.Error("Không thể phân tích cấu trúc KF8/MOBI của tệp sách", resultCode)
                }
                4 -> {
                    tempOutput.delete()
                    Azw3ConversionResult.Error("Không thể đóng gói tệp EPUB từ dữ liệu AZW3", resultCode)
                }
                else -> {
                    tempOutput.delete()
                    Azw3ConversionResult.Error("Lỗi không xác định khi chuyển đổi AZW3 ($resultCode)", resultCode)
                }
            }
        } catch (e: Throwable) {
            tempOutput.delete()
            Azw3ConversionResult.Error(e.message ?: "Ngoại lệ khi chuyển đổi AZW3: $e")
        }
    }

    suspend fun isDrmProtected(azw3File: File): Boolean = withContext(ioDispatcher) {
        if (!isNativeLoaded || !azw3File.exists()) return@withContext false
        try {
            nativeIsDrmProtected(azw3File.absolutePath)
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun extractCover(azw3File: File, destinationFile: File): Boolean = withContext(ioDispatcher) {
        if (!isNativeLoaded || !azw3File.exists()) return@withContext false
        try {
            destinationFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            nativeExtractCover(azw3File.absolutePath, destinationFile.absolutePath)
        } catch (_: Throwable) {
            false
        }
    }

    private fun cleanOldCache(cacheDir: File, maxSizeBytes: Long) {
        val files = cacheDir.listFiles { f -> f.extension == "epub" } ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize > maxSizeBytes) {
            val sorted = files.sortedBy { it.lastModified() }
            for (file in sorted) {
                val len = file.length()
                if (file.delete()) {
                    totalSize -= len
                    if (totalSize <= maxSizeBytes * 0.75) {
                        break
                    }
                }
            }
        }
    }

    private fun computeCacheKey(file: File): String {
        val input = "${file.absolutePath}_${file.length()}_${file.lastModified()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private external fun nativeConvertAzw3ToEpub(inputPath: String, outputEpubPath: String): Int
    private external fun nativeIsDrmProtected(inputPath: String): Boolean
    private external fun nativeExtractCover(inputPath: String, outputCoverPath: String): Boolean
}
