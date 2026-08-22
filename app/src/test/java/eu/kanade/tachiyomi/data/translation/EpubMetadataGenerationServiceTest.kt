package eu.kanade.tachiyomi.data.translation

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

class EpubMetadataGenerationServiceTest {
    private val preferences = TranslationPreferences(InMemoryPreferenceStore())
    private val json = Json { ignoreUnknownKeys = true }
    private val aiSettings = AiSettingsStore(preferences, json)
    private val generator = mockk<LlmGenerator>()
    private val service = EpubMetadataGenerationService(generator, aiSettings, preferences, json)

    @Test
    fun `an unconfigured provider means metadata cannot be generated`() {
        service.isConfigured() shouldBe false

        aiSettings.saveProvider(provider, "secret")

        service.isConfigured() shouldBe true
    }

    @Test
    fun `generation sends existing metadata and epub excerpts as structured input`() = runTest {
        aiSettings.saveProvider(provider, "secret")
        aiSettings.saveGuidelines(UserGuidelines("metadata", "Metadata", "Prefer canonical English titles"))
        preferences.epubMetadataGuidelinesId().set("metadata")
        preferences.targetLanguage().set("vi")
        var captured: LlmGenerationRequest? = null
        coEvery { generator.generate(any(), any()) } answers {
            captured = secondArg()
            LlmResult.Success(validResponse)
        }

        service.generate(
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
        request.input shouldContain "The story begins."
        request.systemPrompt shouldContain "Vietnamese"
        request.systemPrompt shouldContain "Prefer canonical English titles"
        (request.outputFormat is LlmOutputFormat.JsonSchema) shouldBe true
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
