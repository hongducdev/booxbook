package eu.kanade.tachiyomi.security

import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.jsplugin.JsPluginManager

class SensitiveContentPolicy(
    private val getIncognitoState: GetIncognitoState,
    private val securityPreferences: SecurityPreferences,
    private val pluginManager: JsPluginManager,
) {
    enum class Action {
        READING_PROGRESS,
        READING_HISTORY,
    }

    fun isBlocked(action: Action, sourceId: Long?): Boolean {
        if (getIncognitoState.await(sourceId)) return true

        return when (pluginManager.contentWarningForSource(sourceId)) {
            CONTENT_WARNING_MIXED -> when (action) {
                Action.READING_PROGRESS -> securityPreferences.mixedBlockReadingProgress.get()
                Action.READING_HISTORY -> securityPreferences.mixedBlockReadingHistory.get()
            }
            CONTENT_WARNING_NSFW -> when (action) {
                Action.READING_PROGRESS -> securityPreferences.nsfwBlockReadingProgress.get()
                Action.READING_HISTORY -> securityPreferences.nsfwBlockReadingHistory.get()
            }
            else -> false
        }
    }

    private companion object {
        const val CONTENT_WARNING_MIXED = 2
        const val CONTENT_WARNING_NSFW = 3
    }
}
