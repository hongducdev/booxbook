package com.booxbook.core.database.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.booxbook.core.model.Book
import com.booxbook.core.model.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedBookInfo(
    val id: String,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String?,
    val format: BookFormat,
    val fileSize: Long,
    val series: String? = null,
    val seriesIndex: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val language: String? = null
)

@Singleton
class BookStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val booksDir: File by lazy {
        File(context.filesDir, "books").apply { if (!exists()) mkdirs() }
    }

    private val coversDir: File by lazy {
        File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
    }

    suspend fun importBookFromUri(uri: Uri): Result<ImportedBookInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val bookId = UUID.randomUUID().toString()
            val (originalName, querySize) = resolveFileNameAndSize(uri)
            val extension = originalName.substringAfterLast('.', "").lowercase()
            val format = detectBookFormat(uri, extension)
                ?: throw IllegalArgumentException("Định dạng tệp không được hỗ trợ: $originalName")

            val safeFileName = "${bookId}_${sanitizeFileName(originalName)}"
            val destinationFile = File(booksDir, safeFileName)

            val copiedSize = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Không thể đọc tệp từ URI: $uri")

            val finalSize = if (copiedSize > 0) copiedSize else querySize

            // Trích xuất metadata và ảnh bìa
            val extracted = extractMetadataAndCover(destinationFile, format, bookId, originalName)

            ImportedBookInfo(
                id = bookId,
                title = extracted.title,
                author = extracted.author,
                filePath = destinationFile.absolutePath,
                coverPath = extracted.coverPath,
                format = format,
                fileSize = finalSize,
                series = extracted.series,
                seriesIndex = extracted.seriesIndex,
                tags = extracted.tags,
                description = extracted.description,
                language = extracted.language
            )
        }
    }

    /**
     * Đọc lại metadata từ tệp đã nhập, không đụng tới tệp hay ảnh bìa.
     *
     * Dùng cho sách nhập trước khi có tính năng đọc metadata: chúng cần được quét lại để `%series`,
     * `%description`, `%lang` có dữ liệu. Trả `null` khi không đọc được gì thêm, để nơi gọi không ghi một bản
     * ghi rỗng đè lên bản ghi đang có.
     */
    internal suspend fun reExtractMetadata(book: Book): ExtractedData? = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        if (!file.exists()) return@withContext null

        when (book.format) {
            BookFormat.EPUB -> runCatching {
                extractEpubMetadata(file, book.id, book.title, book.author)
            }.getOrNull()

            // CBZ và AZW3 không mang metadata văn bản; quét lại không cho thêm gì.
            BookFormat.CBZ, BookFormat.AZW3 -> null
        }
    }

    fun deleteBookFiles(filePath: String, coverPath: String?): Boolean {
        var success = true
        val bookFile = File(filePath)
        if (bookFile.exists()) {
            success = success && bookFile.delete()
        }
        if (coverPath != null) {
            val coverFile = File(coverPath)
            if (coverFile.exists()) {
                success = success && coverFile.delete()
            }
        }
        return success
    }

    private fun resolveFileNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "book_${System.currentTimeMillis()}"
        var size = 0L

        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)?.let { name = it }
                        }
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { path ->
                val f = File(path)
                name = f.name
                if (f.exists()) size = f.length()
            }
        }

        // Đảm bảo chỉ lấy tên tệp, loại bỏ toàn bộ đường dẫn phân cách bởi '/' hoặc '\'
        val cleanName = name.substringAfterLast('/').substringAfterLast('\\')
        return Pair(cleanName, size)
    }

    private fun detectBookFormat(uri: Uri, extension: String): BookFormat? {
        BookFormat.fromExtension(extension)?.let { return it }

        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("epub", ignoreCase = true) == true -> BookFormat.EPUB
            mimeType?.contains("comic", ignoreCase = true) == true -> BookFormat.CBZ
            mimeType?.contains("mobipocket", ignoreCase = true) == true -> BookFormat.AZW3
            else -> null
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    internal data class ExtractedData(
        val title: String,
        val author: String,
        val coverPath: String?,
        val series: String? = null,
        val seriesIndex: String? = null,
        val tags: List<String> = emptyList(),
        val description: String? = null,
        val language: String? = null
    )

    private fun extractMetadataAndCover(
        bookFile: File,
        format: BookFormat,
        bookId: String,
        originalName: String
    ): ExtractedData {
        val defaultTitle = originalName.substringBeforeLast('.')
        val defaultAuthor = "Tác giả chưa rõ"

        return when (format) {
            BookFormat.EPUB -> extractEpubMetadata(bookFile, bookId, defaultTitle, defaultAuthor)
            BookFormat.CBZ -> extractCbzCover(bookFile, bookId, defaultTitle, defaultAuthor)
            BookFormat.AZW3 -> ExtractedData(
                title = defaultTitle,
                author = defaultAuthor,
                coverPath = null
            )
        }
    }

    private fun extractEpubMetadata(
        bookFile: File,
        bookId: String,
        defaultTitle: String,
        defaultAuthor: String
    ): ExtractedData {
        var title = defaultTitle
        var author = defaultAuthor
        var coverPath: String? = null
        var series: String? = null
        var seriesIndex: String? = null
        var tags: List<String> = emptyList()
        var description: String? = null
        var language: String? = null

        runCatching {
            ZipFile(bookFile).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return@use
                val opfPath = parseOpfPathFromContainer(zip.getInputStream(containerEntry)) ?: return@use
                val opfEntry = zip.getEntry(opfPath) ?: return@use

                val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                val parsed = parseOpfMetadata(opfContent)

                if (parsed.title.isNotBlank()) title = parsed.title
                if (parsed.author.isNotBlank()) author = parsed.author
                series = parsed.series
                seriesIndex = parsed.seriesIndex
                tags = parsed.tags
                description = parsed.description
                language = parsed.language

                if (parsed.coverHref != null) {
                    val resolvedCoverPath = resolveZipPath(opfPath, parsed.coverHref)
                    val coverEntry = zip.getEntry(resolvedCoverPath)
                    if (coverEntry != null) {
                        val extension = resolvedCoverPath.substringAfterLast('.', "jpg")
                        val coverFile = File(coversDir, "${bookId}_cover.$extension")
                        zip.getInputStream(coverEntry).use { input ->
                            FileOutputStream(coverFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        coverPath = coverFile.absolutePath
                    }
                }
            }
        }

        return ExtractedData(
            title = title,
            author = author,
            coverPath = coverPath,
            series = series,
            seriesIndex = seriesIndex,
            tags = tags,
            description = description,
            language = language
        )
    }

    private fun parseOpfPathFromContainer(inputStream: InputStream): String? {
        return runCatching {
            val parser = Xml.newPullParser().apply {
                setInput(inputStream, "UTF-8")
            }
            var eventType = parser.eventType
            var opfPath: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                    val mediaType = parser.getAttributeValue(null, "media-type")
                    if (mediaType == null || mediaType.equals("application/oebps-package+xml", ignoreCase = true)) {
                        opfPath = parser.getAttributeValue(null, "full-path")
                        if (!opfPath.isNullOrBlank()) break
                    }
                }
                eventType = parser.next()
            }
            opfPath
        }.getOrNull()
    }

    private data class OpfParsedResult(
        val title: String,
        val author: String,
        val coverHref: String?,
        val series: String?,
        val seriesIndex: String?,
        val tags: List<String>,
        val description: String?,
        val language: String?
    )

    /**
     * Đọc metadata từ OPF.
     *
     * Hỗ trợ cả hai cách khai báo bộ sách đang tồn tại ngoài thực tế:
     *
     * - **Calibre** — `meta[name="calibre:series"]` + `meta[name="calibre:series_index"]`. Đây là dạng phổ
     *   biến nhất vì phần lớn thư viện EPUB được quản lý bằng Calibre.
     * - **EPUB 3 chuẩn** — `meta[property="belongs-to-collection"]` + `meta[property="group-position"]`.
     *
     * `dc:subject` có thể xuất hiện nhiều lần, mỗi lần một thẻ; gom hết thay vì chỉ lấy cái đầu.
     *
     * Mọi lần đọc văn bản phần tử đều đi qua [safeText]: một thẻ rỗng hay sai cấu trúc không được phép làm mất
     * **toàn bộ** metadata còn lại, mà `nextText()` ném lỗi thì cả khối `runCatching` ngoài sẽ bỏ hết.
     */
    private fun parseOpfMetadata(opfXml: String): OpfParsedResult {
        var title = ""
        var author = ""
        var coverHref: String? = null
        var coverMetaItemId: String? = null
        var series: String? = null
        var seriesIndex: String? = null
        var description: String? = null
        var language: String? = null
        val subjects = mutableListOf<String>()
        val items = mutableMapOf<String, String>() // id -> href

        runCatching {
            val parser = Xml.newPullParser().apply {
                setInput(StringReader(opfXml))
            }
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name.lowercase()
                    when {
                        tagName == "title" || tagName.endsWith(":title") -> {
                            val text = parser.safeText()
                            if (!text.isNullOrBlank() && title.isBlank()) title = text
                        }
                        tagName == "creator" || tagName.endsWith(":creator") -> {
                            val text = parser.safeText()
                            if (!text.isNullOrBlank() && author.isBlank()) author = text
                        }
                        tagName == "description" || tagName.endsWith(":description") -> {
                            val text = parser.safeText()
                            if (!text.isNullOrBlank() && description == null) description = cleanDescription(text)
                        }
                        tagName == "subject" || tagName.endsWith(":subject") -> {
                            val text = parser.safeText()
                            if (!text.isNullOrBlank()) subjects += text
                        }
                        tagName == "language" || tagName.endsWith(":language") -> {
                            val text = parser.safeText()
                            if (!text.isNullOrBlank() && language == null) language = text
                        }
                        tagName == "meta" -> {
                            val name = parser.getAttributeValue(null, "name")
                            val property = parser.getAttributeValue(null, "property")
                            val content = parser.getAttributeValue(null, "content")

                            when {
                                name.equals("cover", ignoreCase = true) && !content.isNullOrBlank() -> {
                                    coverMetaItemId = content
                                }

                                name.equals("calibre:series", ignoreCase = true) -> {
                                    if (!content.isNullOrBlank() && series == null) series = content.trim()
                                }

                                name.equals("calibre:series_index", ignoreCase = true) -> {
                                    if (!content.isNullOrBlank() && seriesIndex == null) {
                                        seriesIndex = content.trim()
                                    }
                                }

                                property.equals("belongs-to-collection", ignoreCase = true) -> {
                                    val text = parser.safeText()
                                    if (!text.isNullOrBlank() && series == null) series = text
                                }

                                property.equals("group-position", ignoreCase = true) -> {
                                    val text = parser.safeText()
                                    if (!text.isNullOrBlank() && seriesIndex == null) seriesIndex = text
                                }
                            }
                        }
                        tagName == "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val properties = parser.getAttributeValue(null, "properties")

                            if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                                items[id] = href
                                if (properties?.contains("cover-image", ignoreCase = true) == true) {
                                    coverHref = href
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (coverHref == null && coverMetaItemId != null) {
                coverHref = items[coverMetaItemId]
            }
        }

        return OpfParsedResult(
            title = title,
            author = author,
            coverHref = coverHref,
            series = series,
            seriesIndex = seriesIndex,
            tags = subjects.distinct(),
            description = description,
            language = language
        )
    }

    /** `nextText()` ném lỗi khi phần tử rỗng hoặc ở cuối tài liệu; ở đây coi đó là "không có gì". */
    private fun XmlPullParser.safeText(): String? = runCatching { nextText()?.trim() }.getOrNull()

    /**
     * Mô tả sách trong OPF thường là HTML (`<p>`, `<br/>`, thực thể).
     *
     * Giữ nguyên thì `%description` trên overlay sẽ hiện cả thẻ — vô nghĩa với một dòng chữ. Bỏ thẻ và gộp
     * khoảng trắng để phần mô tả còn đọc được.
     */
    private fun cleanDescription(raw: String): String = raw
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun resolveZipPath(baseFilePath: String, relativeHref: String): String {
        val baseDir = baseFilePath.substringBeforeLast('/', "")
        return if (baseDir.isEmpty()) {
            relativeHref
        } else {
            "$baseDir/$relativeHref"
        }
    }

    private fun extractCbzCover(
        bookFile: File,
        bookId: String,
        defaultTitle: String,
        defaultAuthor: String
    ): ExtractedData {
        var coverPath: String? = null

        runCatching {
            ZipFile(bookFile).use { zip ->
                val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")
                val imageEntries = mutableListOf<ZipEntry>()

                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && !entry.name.startsWith("__MACOSX")) {
                        val ext = entry.name.substringAfterLast('.', "").lowercase()
                        if (ext in imageExtensions) {
                            imageEntries.add(entry)
                        }
                    }
                }

                val firstImage = imageEntries.minByOrNull { it.name }
                if (firstImage != null) {
                    val ext = firstImage.name.substringAfterLast('.', "jpg")
                    val coverFile = File(coversDir, "${bookId}_cover.$ext")
                    zip.getInputStream(firstImage).use { input ->
                        FileOutputStream(coverFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    coverPath = coverFile.absolutePath
                }
            }
        }

        return ExtractedData(
            title = defaultTitle,
            author = defaultAuthor,
            coverPath = coverPath
        )
    }
}
