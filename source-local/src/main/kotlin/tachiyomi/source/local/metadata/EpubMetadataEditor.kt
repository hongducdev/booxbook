package tachiyomi.source.local.metadata

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class LocalEpubMetadata(
    val title: String,
    val alternativeTitles: List<String>,
    val description: String,
    val tags: List<String>,
    val author: String,
    val artist: String,
    val status: Int,
)

internal object EpubMetadataEditor {
    private const val MAX_PACKAGE_BYTES = 4 * 1024 * 1024
    private const val MIMETYPE_PATH = "mimetype"

    fun rewrite(
        input: InputStream,
        output: OutputStream,
        packagePath: String,
        metadata: LocalEpubMetadata,
    ) {
        require(metadata.status in 0..6) { "Unsupported publication status" }
        val normalizedPackagePath = packagePath.trimStart('/')
        var packageUpdated = false

        ZipInputStream(input.buffered()).use { zipInput ->
            ZipOutputStream(output.buffered()).use { zipOutput ->
                var sourceEntry = zipInput.nextEntry
                while (sourceEntry != null) {
                    val entryName = sourceEntry.name.trimStart('/')
                    val replacement = if (entryName == normalizedPackagePath) {
                        packageUpdated = true
                        rewritePackageDocument(zipInput.readBytesLimited(MAX_PACKAGE_BYTES), metadata)
                    } else {
                        null
                    }
                    if (entryName == MIMETYPE_PATH) {
                        val bytes = replacement ?: zipInput.readBytesLimited(128)
                        zipOutput.putNextEntry(storedEntry(entryName, bytes, sourceEntry.time))
                        zipOutput.write(bytes)
                    } else {
                        zipOutput.putNextEntry(deflatedEntry(entryName, sourceEntry.time, sourceEntry.isDirectory))
                        if (replacement != null) {
                            zipOutput.write(replacement)
                        } else if (!sourceEntry.isDirectory) {
                            zipInput.copyTo(zipOutput)
                        }
                    }
                    zipOutput.closeEntry()
                    zipInput.closeEntry()
                    sourceEntry = zipInput.nextEntry
                }
            }
        }

        check(packageUpdated) { "EPUB package document was not found" }
    }

    internal fun rewritePackageDocument(
        packageBytes: ByteArray,
        value: LocalEpubMetadata,
    ): ByteArray {
        val document = Jsoup.parse(packageBytes.inputStream(), Charsets.UTF_8.name(), "", Parser.xmlParser())
        val metadata = document.getElementsByTag("metadata").firstOrNull()
            ?: error("EPUB package has no metadata element")

        metadata.replaceTextElement("title", "dc:title", value.title)
        metadata.replaceTextElement("creator", "dc:creator", value.author)
        metadata.replaceTextElement("description", "dc:description", value.description)
        metadata.replaceTextElements("subject", "dc:subject", value.tags)
        metadata.replaceCustomMeta("alternative-title", value.alternativeTitles)
        metadata.replaceCustomMeta("artist", listOf(value.artist))
        metadata.replaceCustomMeta("status", listOf(value.status.toString()))

        document.outputSettings()
            .syntax(Document.OutputSettings.Syntax.xml)
            .prettyPrint(false)
            .charset(Charsets.UTF_8)
        return document.outerHtml().toByteArray(Charsets.UTF_8)
    }

    private fun Element.replaceTextElement(localName: String, qualifiedName: String, value: String) {
        directChildren(localName).forEach(Element::remove)
        value.trim().takeIf(String::isNotEmpty)?.let { appendElement(qualifiedName).text(it) }
    }

    private fun Element.replaceTextElements(localName: String, qualifiedName: String, values: List<String>) {
        directChildren(localName).forEach(Element::remove)
        values.normalized().forEach { appendElement(qualifiedName).text(it) }
    }

    private fun Element.replaceCustomMeta(name: String, values: List<String>) {
        children()
            .filter { it.normalName() == "meta" && it.attr("name") == "booxbook:$name" }
            .forEach(Element::remove)
        values.normalized().forEach {
            appendElement("meta")
                .attr("name", "booxbook:$name")
                .attr("content", it)
        }
    }

    private fun Element.directChildren(localName: String): List<Element> =
        children().filter { it.tagName().substringAfter(':') == localName }

    private fun List<String>.normalized(): List<String> =
        map(String::trim).filter(String::isNotEmpty).distinctBy(String::lowercase)

    private fun storedEntry(name: String, bytes: ByteArray, time: Long): ZipEntry {
        val crc = CRC32().apply { update(bytes) }
        return ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
            if (time >= 0) this.time = time
        }
    }

    private fun deflatedEntry(name: String, time: Long, directory: Boolean): ZipEntry =
        ZipEntry(if (directory && !name.endsWith('/')) "$name/" else name).apply {
            method = ZipEntry.DEFLATED
            if (time >= 0) this.time = time
        }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "EPUB package document is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
