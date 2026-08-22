package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.util.system.workManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class TrackingRemovalMigration : Migration {
    override val version: Float = 24f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        migrationContext.get<Application>()?.let { application ->
            application.workManager.cancelUniqueWork(DELAYED_TRACKING_WORK)
            application.deleteSharedPreferences(DELAYED_TRACKING_STORE)
        }
        removeTrackingPreferences(preferenceStore)
        return true
    }

    private companion object {
        const val DELAYED_TRACKING_WORK = "DelayedTrackingUpdate"
        const val DELAYED_TRACKING_STORE = "tracking_queue"
    }
}

internal fun removeTrackingPreferences(preferenceStore: PreferenceStore) {
    val trackerIds = (1L..14L) + listOf(100L, 101L, 102L)
    trackerIds.forEach { trackerId ->
        listOf(
            "pref_mangasync_username_$trackerId",
            "pref_mangasync_displayname_$trackerId",
            "pref_mangasync_password_$trackerId",
            "pref_tracker_auth_expired_$trackerId",
            "track_token_$trackerId",
        ).forEach { key ->
            preferenceStore.getString(Preference.privateKey(key), "").delete()
        }
    }

    listOf(
        "anilist_score_type",
        "mangabaka_score_type",
        "novelupdates_custom_list_mapping",
        "novelupdates_cached_lists",
        "min_chapters_before_tracking_manga",
        "min_chapters_before_tracking_novel",
    ).forEach { preferenceStore.getString(it, "").delete() }

    listOf(
        "pref_auto_update_manga_sync_key",
        "novelupdates_mark_chapters_read",
        "novelupdates_sync_reading_list",
        "novelupdates_use_custom_list_mapping",
        "novellist_mark_chapters_read",
        "novellist_sync_reading_list",
        "source_tracker_run_on_migration",
        "ranobedb_mark_chapters_read",
        "ranobedb_sync_reading_list",
        "mangabaka_mark_chapters_read",
        "mangabaka_sync_reading_list",
    ).forEach { preferenceStore.getBoolean(it, false).delete() }

    preferenceStore.getString("pref_auto_update_manga_on_mark_read", "").delete()
}
