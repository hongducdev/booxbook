package eu.kanade.tachiyomi.data.translation

import androidx.core.os.LocaleListCompat
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.translation.model.AIProvider
import tachiyomi.domain.translation.model.AIProviderType
import tachiyomi.domain.translation.model.LlmGenerationRequest
import tachiyomi.domain.translation.model.LlmOutputFormat
import tachiyomi.domain.translation.model.LlmResult
import tachiyomi.domain.translation.model.UserGuidelines
import tachiyomi.domain.translation.service.TranslationPreferences
import java.util.Locale

class EpubMetadataGenerationServiceTest {
    private val preferences = TranslationPreferences(InMemoryPreferenceStore())
    private val json = Json { ignoreUnknownKeys = true }
    private val aiSettings = AiSettingsStore(preferences, json)
    private val generator = mockk<LlmGenerator>()
    private val service = EpubMetadataGenerationService(generator, aiSettings, preferences, json) { "English" }

    @Test
    fun `an unconfigured provider means metadata cannot be generated`() {
        service.isConfigured() shouldBe false

        aiSettings.saveProvider(provider, "secret")

        service.isConfigured() shouldBe true
    }

    @Test
    fun `generation uses the app language and sends existing metadata as structured input`() = runTest {
        aiSettings.saveProvider(provider, "secret")
        aiSettings.saveGuidelines(UserGuidelines("metadata", "Metadata", "Prefer canonical English titles"))
        preferences.epubMetadataGuidelinesId().set("metadata")
        preferences.targetLanguage().set("ja")
        val vietnameseService = EpubMetadataGenerationService(generator, aiSettings, preferences, json) {
            "Vietnamese"
        }
        var captured: LlmGenerationRequest? = null
        coEvery { generator.generate(any(), any()) } answers {
            captured = secondArg()
            LlmResult.Success(validResponse)
        }

        vietnameseService.generate(
            current = EpubMetadataDraft(
                title = "Current title",
                alternativeTitles = listOf("Old title"),
                description = "Current description",
                tags = listOf("Fantasy"),
                author = "Known author",
                artist = "Known artist",
                status = 1,
            ),
            excerpts = listOf(EpubContentExcerpt("Chapter 1", "The story begins.")),
        )

        val request = requireNotNull(captured)
        request.input shouldContain "Current title"
        request.input shouldContain "Chapter 1"
        request.systemPrompt shouldContain "must be written in Vietnamese"
        request.systemPrompt shouldContain "Translate the title"
        request.input shouldContain "The story begins."
        request.systemPrompt shouldContain "Vietnamese"
        request.systemPrompt shouldContain "Prefer canonical English titles"
        (request.outputFormat is LlmOutputFormat.JsonSchema) shouldBe true
    }

    @Test
    fun `explicit app locale takes precedence over system locale`() {
        currentAppLanguageName(
            appLocales = LocaleListCompat.forLanguageTags("vi"),
            systemLocale = Locale.ENGLISH,
        ) shouldBe "Vietnamese"
    }

    @Test
    fun `valid provider JSON becomes an editable metadata draft`() = runTest {
        aiSettings.saveProvider(provider, "secret")
        coEvery { generator.generate(any(), any()) } returns LlmResult.Success(validResponse)

        val result = service.generate(EpubMetadataDraft.EMPTY, emptyList())

        result shouldBe EpubMetadataGenerationResult.Success(
            EpubMetadataDraft(
                title = "Generated title",
                alternativeTitles = listOf("Alternative"),
                description = "Generated description",
                tags = listOf("Fantasy", "Adventure"),
                author = "Author",
                artist = "Illustrator",
                status = 1,
            ),
        )
    }

    @Test
    fun `nested partial JSON preserves metadata fields omitted by the provider`() = runTest {
        aiSettings.saveProvider(provider, "secret")
        coEvery { generator.generate(any(), any()) } returns LlmResult.Success(
            """{"epub_metadata":{"description":"New description","tags":["Mystery"]}}""",
        )
        val current = EpubMetadataDraft(
            title = "Existing title",
            alternativeTitles = listOf("Existing alternative"),
            description = "Existing description",
            tags = listOf("Thriller"),
            author = "Existing author",
            artist = "Existing artist",
            status = 2,
        )

        val result = service.generate(current, emptyList())

        result shouldBe EpubMetadataGenerationResult.Success(
            current.copy(
                description = "New description",
                tags = listOf("Mystery"),
            ),
        )
    }

    @Test
    fun `malformed provider JSON returns a failure without hiding the response problem`() = runTest {
        aiSettings.saveProvider(provider, "secret")
        coEvery { generator.generate(any(), any()) } returns LlmResult.Success("not-json")

        val result = service.generate(EpubMetadataDraft.EMPTY, emptyList())

        (result as EpubMetadataGenerationResult.Failure).message shouldContain "metadata"
        coVerify(exactly = 1) { generator.generate(any(), any()) }
    }

    private val validResponse = """
        {
          "title": "Generated title",
          "alternativeTitles": ["Alternative"],
          "description": "Generated description",
          "tags": ["Fantasy", "Adventure"],
          "author": "Author",
          "artist": "Illustrator",
          "status": 1
        }
    """.trimIndent()

    private val provider = AIProvider(
        id = "p1",
        alias = "Provider",
        type = AIProviderType.OPENAI,
        endpoint = "https://example.com/v1",
        model = "model",
    )
}
