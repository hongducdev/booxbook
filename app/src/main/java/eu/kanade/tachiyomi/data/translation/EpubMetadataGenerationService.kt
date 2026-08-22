package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import tachiyomi.domain.translation.model.AiExecutionConfig
import tachiyomi.domain.translation.model.LanguageCodes
import tachiyomi.domain.translation.model.LlmGenerationRequest
import tachiyomi.domain.translation.model.LlmOutputFormat
import tachiyomi.domain.translation.model.LlmResult
import tachiyomi.domain.translation.service.TranslationPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Serializable
data class EpubMetadataDraft(
    val title: String,
    val alternativeTitles: List<String>,
    val description: String,
    val tags: List<String>,
    val author: String,
    val artist: String,
    val status: Long,
) {
    companion object {
        val EMPTY = EpubMetadataDraft("", emptyList(), "", emptyList(), "", "", 0)
    }
}

@Serializable
data class EpubContentExcerpt(
    val chapter: String,
    val text: String,
)

sealed interface EpubMetadataGenerationResult {
    data class Success(val metadata: EpubMetadataDraft) : EpubMetadataGenerationResult
    data class Failure(val message: String) : EpubMetadataGenerationResult
}

class EpubMetadataGenerationService(
    private val generator: LlmGenerator = Injekt.get(),
    private val aiSettings: AiSettingsStore = Injekt.get(),
    private val preferences: TranslationPreferences = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    fun isConfigured(): Boolean = resolveConfig().isComplete

    suspend fun generate(
        current: EpubMetadataDraft,
        excerpts: List<EpubContentExcerpt>,
    ): EpubMetadataGenerationResult {
        val config = resolveConfig()
        val request = LlmGenerationRequest(
            systemPrompt = buildPrompt(
                targetLanguage = LanguageCodes.getDisplayName(preferences.targetLanguage().get()),
                guidelines = config.guidelines.orEmpty(),
            ),
            input = json.encodeToString(GenerationInput(current, excerpts)),
            outputFormat = LlmOutputFormat.JsonSchema("epub_metadata", OUTPUT_SCHEMA),
        )
        return when (
            val result = AiRetryPolicy.execute(
                retries = preferences.requestRetryCount().get(),
                failureCode = { (it as? LlmResult.Failure)?.code },
            ) { generator.generate(config, request) }
        ) {
            is LlmResult.Failure -> EpubMetadataGenerationResult.Failure(result.message)
            is LlmResult.Success -> parseResponse(result.text, current)
        }
    }

    private fun parseResponse(
        value: String,
        current: EpubMetadataDraft,
    ): EpubMetadataGenerationResult {
        val payload = runCatching {
            val root = json.parseToJsonElement(value.removeMarkdownFence()).jsonObject
            val metadataObject = if (OUTPUT_FIELDS.any(root::containsKey)) {
                root
            } else {
                root.values.filterIsInstance<JsonObject>().singleOrNull() ?: root
            }
            json.decodeFromJsonElement<GeneratedMetadataPayload>(metadataObject)
        }.getOrElse {
            return EpubMetadataGenerationResult.Failure("Invalid AI metadata response: ${it.message}")
        }
        if (payload.isEmpty) {
            return EpubMetadataGenerationResult.Failure("Invalid AI metadata response: no metadata fields")
        }
        return EpubMetadataGenerationResult.Success(
            EpubMetadataDraft(
                title = payload.title?.trim()?.takeIf(String::isNotEmpty) ?: current.title,
                alternativeTitles = payload.alternativeTitles?.normalizeValues() ?: current.alternativeTitles,
                description = payload.description?.trim() ?: current.description,
                tags = payload.tags?.normalizeValues() ?: current.tags,
                author = payload.author?.trim() ?: current.author,
                artist = payload.artist?.trim() ?: current.artist,
                status = payload.status?.takeIf { it in VALID_STATUSES } ?: current.status,
            ),
        )
    }

    private fun resolveConfig(): AiExecutionConfig = aiSettings.resolveConfig(
        preferences.epubMetadataProviderId().get(),
        preferences.epubMetadataGuidelinesId().get(),
    )

    private fun buildPrompt(targetLanguage: String, guidelines: String): String {
        val custom = guidelines.trim().ifEmpty { "No specific guidelines." }
        return """
            You generate editable metadata for a local EPUB novel from its existing metadata and representative excerpts.

            Rules:
            - Write title, alternative titles, description, tags, author and artist in $targetLanguage when a localized form is appropriate.
            - Use only facts supported by the supplied metadata or excerpts. Never invent names, credits or publication state.
            - Keep a reliable existing value when the excerpts do not justify replacing it.
            - Use an empty string or empty array when a value is genuinely unknown.
            - Description must be a concise spoiler-light synopsis, not a review or chapter-by-chapter summary.
            - Tags must be short genre or theme labels without duplicates.
            - status must be one of: 0 unknown, 1 ongoing, 2 completed, 3 licensed, 4 publishing finished, 5 cancelled, 6 on hiatus.
            - Return only the JSON object required by the schema.

            ---
            [User guidelines]:
            $custom
        """.trimIndent()
    }

    @Serializable
    private data class GenerationInput(
        val currentMetadata: EpubMetadataDraft,
        val epubExcerpts: List<EpubContentExcerpt>,
    )

    @Serializable
    private data class GeneratedMetadataPayload(
        val title: String? = null,
        val alternativeTitles: List<String>? = null,
        val description: String? = null,
        val tags: List<String>? = null,
        val author: String? = null,
        val artist: String? = null,
        val status: Long? = null,
    ) {
        val isEmpty: Boolean
            get() = title == null &&
                alternativeTitles == null &&
                description == null &&
                tags == null &&
                author == null &&
                artist == null &&
                status == null
    }

    companion object {
        private val VALID_STATUSES = 0L..6L
        private val OUTPUT_FIELDS = setOf(
            "title",
            "alternativeTitles",
            "description",
            "tags",
            "author",
            "artist",
            "status",
        )

        private val OUTPUT_SCHEMA = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                stringProperty("title")
                arrayProperty("alternativeTitles")
                stringProperty("description")
                arrayProperty("tags")
                stringProperty("author")
                stringProperty("artist")
                putJsonObject("status") {
                    put("type", "integer")
                    put("enum", buildJsonArray { (0..6).forEach { add(it) } })
                }
            }
            putJsonArray("required") {
                listOf("title", "alternativeTitles", "description", "tags", "author", "artist", "status")
                    .forEach { add(it) }
            }
            put("additionalProperties", false)
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.stringProperty(name: String) {
            putJsonObject(name) { put("type", "string") }
        }

        private fun kotlinx.serialization.json.JsonObjectBuilder.arrayProperty(name: String) {
            putJsonObject(name) {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
        }
    }
}

private fun String.removeMarkdownFence(): String {
    val trimmed = trim()
    if (!trimmed.startsWith("```")) return trimmed
    return trimmed.substringAfter('\n').substringBeforeLast("```").trim()
}

private fun List<String>.normalizeValues(): List<String> =
    map(String::trim).filter(String::isNotEmpty).distinctBy(String::lowercase)
