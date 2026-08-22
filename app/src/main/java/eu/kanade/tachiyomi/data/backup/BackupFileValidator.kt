package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import kotlinx.serialization.protobuf.ProtoBuf
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupFileValidator(
    private val context: Context,

    private val sourceManager: SourceManager = Injekt.get(),
    private val parser: ProtoBuf = Injekt.get(),
) {

    /**
     * Checks for critical backup file data.
     *
     * @return Missing novel sources.
     */
    suspend fun validate(uri: Uri): Results {
        val backupSources = mutableListOf<BackupSource>()
        val novelSourceIds = mutableSetOf<Long>()
        val reader = BackupProtoReader(context)
        try {
            reader.read(uri) { fieldNumber, data ->
                when (fieldNumber) {
                    1 -> {
                        val migrated = BackupProtoMigration.migrateManga(data)
                        val manga = parser.decodeFromByteArray(BackupManga.serializer(), migrated)
                        if (manga.isNovel) {
                            novelSourceIds += manga.source
                        }
                    }
                    101 -> {
                        val source = parser.decodeFromByteArray(BackupSource.serializer(), data)
                        backupSources.add(source)
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

        val sources = backupSources
            .filter { it.sourceId in novelSourceIds }
            .associate { it.sourceId to it.name }
        val missingSources = sources
            .filter { sourceManager.get(it.key) == null }
            .values.map {
                val id = it.toLongOrNull()
                if (id == null) {
                    it
                } else {
                    sourceManager.getOrStub(id).toString()
                }
            }
            .distinct()
            .sorted()

        return Results(missingSources)
    }

    data class Results(
        val missingSources: List<String>,
    )
}
