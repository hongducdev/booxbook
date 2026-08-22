package eu.kanade.tachiyomi.ui.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EpubMetadataPageUrlTest {
    @Test
    fun `direct epub entry removes the duplicated file segment`() {
        resolveEpubMetadataPageUrl(
            mangaUrl = "book.epub",
            chapterUrl = "book.epub/book.epub#OEBPS/chapter.xhtml",
        ) shouldBe "book.epub#OEBPS/chapter.xhtml"
    }

    @Test
    fun `epub inside a novel directory keeps its resolvable chapter url`() {
        resolveEpubMetadataPageUrl(
            mangaUrl = "book-directory",
            chapterUrl = "book-directory/volume.epub#OEBPS/chapter.xhtml",
        ) shouldBe "book-directory/volume.epub#OEBPS/chapter.xhtml"
    }
}
