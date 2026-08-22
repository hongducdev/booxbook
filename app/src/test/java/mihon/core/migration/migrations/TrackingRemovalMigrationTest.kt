package mihon.core.migration.migrations

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference

class TrackingRemovalMigrationTest {
    @Test
    fun `tracking credentials and settings are removed without touching reading statistics`() {
        val store = InMemoryPreferenceStore().apply {
            getString(Preference.privateKey("pref_mangasync_password_2"), "").set("secret")
            getString(Preference.privateKey("track_token_100"), "").set("token")
            getBoolean("novelupdates_sync_reading_list", false).set(true)
            getString("pref_auto_update_manga_on_mark_read", "").set("ALWAYS")
            getBoolean("pref_novel_read_tracking", false).set(true)
        }

        removeTrackingPreferences(store)

        store.getString(Preference.privateKey("pref_mangasync_password_2"), "").get() shouldBe ""
        store.getString(Preference.privateKey("track_token_100"), "").get() shouldBe ""
        store.getBoolean("novelupdates_sync_reading_list", false).get() shouldBe false
        store.getString("pref_auto_update_manga_on_mark_read", "").get() shouldBe ""
        store.getBoolean("pref_novel_read_tracking", false).get() shouldBe true
    }
}
