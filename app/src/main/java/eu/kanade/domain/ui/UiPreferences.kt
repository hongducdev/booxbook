package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    preferenceStore: PreferenceStore,
) {

    val themeMode: Preference<ThemeMode> = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    val appTheme: Preference<AppTheme> = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    val themeDarkAmoled: Preference<Boolean> = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    val catppuccinPrimaryColor: Preference<CatppuccinColor> = preferenceStore.getEnum(
        "pref_catppuccin_primary_color",
        CatppuccinColor.MAUVE,
    )

    val relativeTime: Preference<Boolean> = preferenceStore.getBoolean("relative_time_v2", true)

    val dateFormat: Preference<String> = preferenceStore.getString("app_date_format", "")

    val tabletUiMode: Preference<TabletUiMode> = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    val imagesInDescription: Preference<Boolean> = preferenceStore.getBoolean("pref_render_images_description", true)

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}

enum class CatppuccinColor(
    val latte: Long,
    val mocha: Long,
) {
    ROSEWATER(0xFFDC8A78L, 0xFFF5E0DCL),
    FLAMINGO(0xFFDD7878L, 0xFFF2CDCDL),
    PINK(0xFFEA76CBL, 0xFFF5C2E7L),
    MAUVE(0xFF8839EFL, 0xFFCBA6F7L),
    RED(0xFFD20F39L, 0xFFF38BA8L),
    MAROON(0xFFE64553L, 0xFFEBA0ACL),
    PEACH(0xFFFE640BL, 0xFFFAB387L),
    YELLOW(0xFFDF8E1DL, 0xFFF9E2AFL),
    GREEN(0xFF40A02BL, 0xFFA6E3A1L),
    TEAL(0xFF179299L, 0xFF94E2D5L),
    SKY(0xFF04A5E5L, 0xFF89DCEBL),
    SAPPHIRE(0xFF209FB5L, 0xFF74C7ECL),
    BLUE(0xFF1E66F5L, 0xFF89B4FAL),
    LAVENDER(0xFF7287FDL, 0xFFB4BEFEL),
}
