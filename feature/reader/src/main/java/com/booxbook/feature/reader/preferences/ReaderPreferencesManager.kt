package com.booxbook.feature.reader.preferences

import android.content.Context
import com.booxbook.core.engine.model.ReaderPreferences
import com.booxbook.core.engine.model.ReadingFrame
import com.booxbook.core.engine.model.ReadingFrameColor
import com.booxbook.core.engine.model.ReadingFrameStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nguồn sự thật duy nhất cho cài đặt hiển thị của màn đọc.
 *
 * Trước đây `SettingsViewModel` ghi thẳng vào `booxbook_reader_prefs` còn `ReaderViewModel` **không bao giờ
 * đọc lại**, nên đổi vùng chạm / hiệu ứng lật trang / rung ở tab Cài đặt không có tác dụng gì khi đang đọc.
 * Đó là lỗi có sẵn, và lề trang cũng sẽ mắc đúng lỗi đó nếu đi theo đường cũ — nên gom về một chỗ.
 *
 * **Tên khoá SharedPreferences được giữ nguyên** như bản cũ (`tap_zone_mode`, `page_turn_effect`,
 * `haptics_enabled`): người dùng đã có cài đặt trên máy, đổi tên khoá là âm thầm đặt lại hết về mặc định.
 *
 * Ghi cả bộ khoá mỗi lần cập nhật thay vì chỉ khoá vừa đổi: mười bốn khoá `putX` rẻ hơn hẳn một tầng logic
 * theo dõi khoá nào đã đổi, và không có nhánh nào để viết sai.
 */
@Singleton
class ReaderPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(load())
    val preferences: StateFlow<ReaderPreferences> = _preferences.asStateFlow()

    fun update(transform: (ReaderPreferences) -> ReaderPreferences) {
        val next = transform(_preferences.value)
        _preferences.value = next
        persist(next)
    }

    fun setFontSizeDelta(delta: Double) = update {
        it.copy(fontSize = (it.fontSize + delta).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))
    }

    fun setFontFamily(fontFamily: String?) = update { it.copy(fontFamily = fontFamily) }

    fun setThemePreset(presetName: String, isDark: Boolean) = update {
        it.copy(themePreset = presetName, isDarkMode = isDark)
    }

    fun setTapZoneMode(mode: String) = update { it.copy(tapZoneMode = mode) }

    fun setPageTurnEffect(effect: String) = update { it.copy(pageTurnEffect = effect) }

    fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    fun setMargin(side: MarginSide, valueDp: Float) {
        val clamped = valueDp.coerceIn(0f, ReaderPreferences.MAX_MARGIN_DP)
        update {
            when (side) {
                MarginSide.TOP -> it.copy(marginTopDp = clamped)
                MarginSide.BOTTOM -> it.copy(marginBottomDp = clamped)
                MarginSide.HORIZONTAL -> it.copy(marginHorizontalDp = clamped)
            }
        }
    }

    /**
     * Hệ số lề trang của Readium.
     *
     * **Không dùng cho tính năng lề của ứng dụng** và không có giao diện nào đặt nó: Readium chỉ có một hệ số
     * cho cả bốn phía, còn lề thật là ba trường `margin*Dp` (xem [ReaderPreferences]). Giữ lại vì
     * `EpubPreferences` cần một giá trị.
     */
    fun setPageMargins(value: Double) = update {
        it.copy(pageMargins = value.coerceIn(MIN_PAGE_MARGINS, MAX_PAGE_MARGINS))
    }

    fun resetMargins() = update {
        it.copy(marginTopDp = 0f, marginBottomDp = 0f, marginHorizontalDp = 0f)
    }

    fun updateFrame(transform: (ReadingFrame) -> ReadingFrame) = update {
        it.copy(frame = transform(it.frame))
    }

    private fun load(): ReaderPreferences = ReaderPreferences(
        fontSize = prefs.getFloat(KEY_FONT_SIZE, 1.0f).toDouble(),
        fontFamily = prefs.getString(KEY_FONT_FAMILY, null),
        themePreset = prefs.getString(KEY_THEME_PRESET, "DARK") ?: "DARK",
        isDarkMode = prefs.getBoolean(KEY_IS_DARK, true),
        tapZoneMode = prefs.getString(KEY_TAP_ZONE, "KINDLE") ?: "KINDLE",
        pageTurnEffect = prefs.getString(KEY_PAGE_TURN, "SLIDE") ?: "SLIDE",
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
        marginTopDp = prefs.getFloat(KEY_MARGIN_TOP, 0f),
        marginBottomDp = prefs.getFloat(KEY_MARGIN_BOTTOM, 0f),
        // Bản trước lưu lề trái và phải riêng. Đọc lại giá trị trái làm mức chung để cài đặt đang có của người
        // dùng không bị đặt lại về 0 khi tính năng đổi từ bốn phía sang "trái phải chung".
        marginHorizontalDp = prefs.getFloat(
            KEY_MARGIN_HORIZONTAL,
            prefs.getFloat(KEY_MARGIN_LEFT_LEGACY, prefs.getFloat(KEY_MARGIN_RIGHT_LEGACY, 0f))
        ),
        frame = ReadingFrame(
            enabled = prefs.getBoolean(KEY_FRAME_ENABLED, false),
            thicknessDp = prefs.getFloat(KEY_FRAME_THICKNESS, ReadingFrame().thicknessDp),
            cornerRadiusDp = prefs.getFloat(KEY_FRAME_CORNER, ReadingFrame().cornerRadiusDp),
            insetDp = prefs.getFloat(KEY_FRAME_INSET, ReadingFrame().insetDp),
            style = decodeEnum(prefs.getString(KEY_FRAME_STYLE, null), ReadingFrameStyle.entries, ReadingFrameStyle.SOLID),
            color = decodeEnum(prefs.getString(KEY_FRAME_COLOR, null), ReadingFrameColor.entries, ReadingFrameColor.AUTO)
        )
    )

    private fun persist(value: ReaderPreferences) {
        prefs.edit()
            .putFloat(KEY_FONT_SIZE, value.fontSize.toFloat())
            .putString(KEY_FONT_FAMILY, value.fontFamily)
            .putString(KEY_THEME_PRESET, value.themePreset)
            .putBoolean(KEY_IS_DARK, value.isDarkMode)
            .putString(KEY_TAP_ZONE, value.tapZoneMode)
            .putString(KEY_PAGE_TURN, value.pageTurnEffect)
            .putBoolean(KEY_HAPTICS, value.hapticsEnabled)
            .putFloat(KEY_MARGIN_TOP, value.marginTopDp)
            .putFloat(KEY_MARGIN_BOTTOM, value.marginBottomDp)
            .putFloat(KEY_MARGIN_HORIZONTAL, value.marginHorizontalDp)
            .putBoolean(KEY_FRAME_ENABLED, value.frame.enabled)
            .putFloat(KEY_FRAME_THICKNESS, value.frame.thicknessDp)
            .putFloat(KEY_FRAME_CORNER, value.frame.cornerRadiusDp)
            .putFloat(KEY_FRAME_INSET, value.frame.insetDp)
            .putString(KEY_FRAME_STYLE, value.frame.style.name)
            .putString(KEY_FRAME_COLOR, value.frame.color.name)
            // Dọn khoá của bản trước: `page_margins` không còn được đọc (lề giờ là ba trường `margin*Dp`), để lại
            // trong tệp chỉ gây nhầm lẫn khi đọc bằng mắt.
            .remove(KEY_PAGE_MARGINS_LEGACY)
            .apply()
    }

    /**
     * Đọc enum từ tên đã lưu, rơi về mặc định khi tên không còn tồn tại.
     *
     * Enum đổi tên hoặc bị bỏ là chuyện sẽ xảy ra khi tính năng tiến hoá; đọc thẳng bằng `valueOf` sẽ ném lỗi
     * và làm sập màn đọc vì một giá trị cài đặt cũ.
     */
    private fun <T : Enum<T>> decodeEnum(raw: String?, values: List<T>, fallback: T): T =
        values.firstOrNull { it.name == raw } ?: fallback

    /** Ba phía của lề. Trái và phải dùng chung một giá trị. */
    enum class MarginSide { TOP, BOTTOM, HORIZONTAL }

    private companion object {
        const val PREFS_NAME = "booxbook_reader_prefs"

        const val KEY_FONT_SIZE = "font_size"
        const val KEY_FONT_FAMILY = "font_family"
        const val KEY_THEME_PRESET = "theme_preset"
        const val KEY_IS_DARK = "is_dark_mode"
        const val KEY_TAP_ZONE = "tap_zone_mode"
        const val KEY_PAGE_TURN = "page_turn_effect"
        const val KEY_HAPTICS = "haptics_enabled"

        const val KEY_MARGIN_TOP = "margin_top_dp"
        const val KEY_MARGIN_BOTTOM = "margin_bottom_dp"
        const val KEY_MARGIN_HORIZONTAL = "margin_horizontal_dp"

        /** Khoá của bản trước, chỉ còn để đọc lại hoặc dọn đi. */
        const val KEY_MARGIN_LEFT_LEGACY = "margin_left_dp"
        const val KEY_MARGIN_RIGHT_LEGACY = "margin_right_dp"
        const val KEY_PAGE_MARGINS_LEGACY = "page_margins"

        const val KEY_FRAME_ENABLED = "frame_enabled"
        const val KEY_FRAME_THICKNESS = "frame_thickness_dp"
        const val KEY_FRAME_CORNER = "frame_corner_radius_dp"
        const val KEY_FRAME_INSET = "frame_inset_dp"
        const val KEY_FRAME_STYLE = "frame_style"
        const val KEY_FRAME_COLOR = "frame_color"

        const val MIN_FONT_SIZE = 0.7
        const val MAX_FONT_SIZE = 2.5

        /** Readium nhận hệ số lề; 1.0 là mức mặc định của nó. */
        const val MIN_PAGE_MARGINS = 0.0
        const val MAX_PAGE_MARGINS = 3.0
    }
}
