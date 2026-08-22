package eu.kanade.presentation.more.settings.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.history.repository.ReadingSessionRepository
import tachiyomi.i18n.novel.TDMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsReadingStatisticsScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = TDMR.strings.pref_novel_read_tracking_group

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val readerPreferences = remember { Injekt.get<ReaderPreferences>() }
        val repository = remember { Injekt.get<ReadingSessionRepository>() }
        var showDisableDialog by remember { mutableStateOf(false) }

        if (showDisableDialog) {
            AlertDialog(
                onDismissRequest = { showDisableDialog = false },
                title = { Text(stringResource(TDMR.strings.pref_novel_read_tracking_dialog_title)) },
                text = { Text(stringResource(TDMR.strings.pref_novel_read_tracking_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            readerPreferences.novelReadTracking.set(false)
                            showDisableDialog = false
                        },
                    ) {
                        Text(stringResource(TDMR.strings.pref_novel_read_tracking_keep))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                val deleted = repository.deleteAll()
                                withUIContext {
                                    if (deleted) {
                                        readerPreferences.novelReadTracking.set(false)
                                        showDisableDialog = false
                                    } else {
                                        context.toast(TDMR.strings.pref_novel_read_tracking_delete_error)
                                    }
                                }
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(TDMR.strings.pref_novel_read_tracking_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(TDMR.strings.pref_novel_read_tracking_group),
                preferenceItems = listOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = readerPreferences.useModernStats,
                        title = stringResource(TDMR.strings.pref_stats_use_modern),
                        subtitle = stringResource(TDMR.strings.pref_stats_use_modern_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = readerPreferences.novelReadTracking,
                        title = stringResource(TDMR.strings.pref_novel_read_tracking),
                        subtitle = stringResource(TDMR.strings.pref_novel_read_tracking_summary),
                        onValueChanged = { enabled ->
                            if (!enabled) showDisableDialog = true
                            enabled
                        },
                    ),
                ),
            ),
        )
    }
}
