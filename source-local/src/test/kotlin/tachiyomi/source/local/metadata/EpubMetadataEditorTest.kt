package tachiyomi.source.local.metadata

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class EpubMetadataEditorTest {
    @Test
    fun `rewrite updates standard and Boox Book metadata while preserving book content`() {
        val original = epubBytes()
        val output = ByteArrayOutputStream()

        EpubMetadataEditor.rewrite(
            input = ByteArrayInputStream(original),
            output = output,
            packagePath = "OEBPS/content.opf",
            metadata = LocalEpubMetadata(
                title = "Tiêu đề mới",
                alternativeTitles = listOf("Tên khác"),
                description = "Mô tả tiếng Việt",
                tags = listOf("Bí ẩn", "Kinh dị"),
                author = "Tác giả",
                artist = "Họa sĩ",
                status = 2,
            ),
        )

        val entries = readEntries(output.toByteArray())
        entries.keys.toList() shouldContainExactly listOf(
            "mimetype",
            "OEBPS/content.opf",
            "OEBPS/chapter.xhtml",
        )
        entries.getValue("OEBPS/chapter.xhtml").decodeToString() shouldBe "<p>Original chapter</p>"

        val document = Jsoup.parse(
            entries.getValue("OEBPS/content.opf").inputStream(),
            Charsets.UTF_8.name(),
            "",
            Parser.xmlParser(),
        )
        document.getElementsByTag("dc:title").text() shouldBe "Tiêu đề mới"
        document.getElementsByTag("dc:creator").text() shouldBe "Tác giả"
        document.getElementsByTag("dc:description").text() shouldBe "Mô tả tiếng Việt"
        document.getElementsByTag("dc:subject").map { it.text() } shouldBe listOf("Bí ẩn", "Kinh dị")
        document.select("meta[name=booxbook:alternative-title]").eachAttr("content") shouldBe listOf("Tên khác")
        document.selectFirst("meta[name=booxbook:artist]")?.attr("content") shouldBe "Họa sĩ"
        document.selectFirst("meta[name=booxbook:status]")?.attr("content") shouldBe "2"
    }

    @Test
    fun `rewrite keeps mimetype stored and first`() {
        val output = ByteArrayOutputStream()

        EpubMetadataEditor.rewrite(
            ByteArrayInputStream(epubBytes()),
            output,
            "OEBPS/content.opf",
            LocalEpubMetadata("Title", emptyList(), "", emptyList(), "", "", 0),
        )

        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            val first = requireNotNull(zip.nextEntry)
            first.name shouldBe "mimetype"
            first.method shouldBe ZipEntry.STORED
        }
    }

    private fun epubBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val mimetype = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mimetype) }
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    this.crc = crc.value
                },
            )
            zip.write(mimetype)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <metadata>
                        <dc:title>Old title</dc:title>
                        <dc:creator>Old author</dc:creator>
                        <dc:description>Old description</dc:description>
                        <dc:subject>Old tag</dc:subject>
                        <meta name="booxbook:artist" content="Old artist"/>
                      </metadata>
                    </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/chapter.xhtml"))
            zip.write("<p>Original chapter</p>".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun readEntries(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes())
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
