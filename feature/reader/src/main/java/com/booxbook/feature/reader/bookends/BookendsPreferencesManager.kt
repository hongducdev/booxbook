package com.booxbook.feature.reader.bookends

import android.content.Context
import com.booxbook.core.model.bookends.BookendsAutoRule
import com.booxbook.core.model.bookends.BookendsDefaults
import com.booxbook.core.model.bookends.BookendsPreset
import com.booxbook.core.model.bookends.BookendsSettings
import com.booxbook.core.model.bookends.BookendsSettingsCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nguồn sự thật duy nhất cho cấu hình Bookends.
 *
 * Cố ý **không** đi qua `ReaderUiState`: cài đặt reader hiện có (`tapZoneMode`, `pageTurnEffect`…) được
 * ghi vào SharedPreferences bởi `SettingsViewModel` nhưng `ReaderViewModel` không bao giờ đọc lại, nên
 * đổi ở tab Cài đặt không có tác dụng gì trong màn đọc. Bookends đọc và ghi qua đúng một đối tượng này,
 * nên màn cấu hình và overlay luôn khớp nhau.
 *
 * Toàn bộ [BookendsSettings] được lưu thành **một khối JSON** thay vì rải khoá-phẳng: preset là dữ liệu
 * có cấu trúc lồng nhau, rải phẳng ra sẽ phải tự đặt tên khoá cho từng dòng và không thể tiến hoá schema.
 */
@Singleton
class BookendsPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<BookendsSettings> = _settings.asStateFlow()

    /** Sửa cấu hình rồi ghi ngay xuống đĩa. Mọi thao tác chỉnh sửa đều đi qua đây. */
    fun update(transform: (BookendsSettings) -> BookendsSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        persist(next)
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setActivePreset(presetId: String) = update { it.copy(activePresetId = presetId) }

    /** Thêm mới hoặc thay thế preset theo `id`, rồi đặt nó làm preset đang dùng. */
    fun upsertPreset(preset: BookendsPreset, makeActive: Boolean = false) = update { settings ->
        val existingIndex = settings.presets.indexOfFirst { it.id == preset.id }
        val presets = if (existingIndex >= 0) {
            settings.presets.toMutableList().apply { this[existingIndex] = preset }
        } else {
            settings.presets + preset
        }
        settings.copy(
            presets = presets,
            activePresetId = if (makeActive) preset.id else settings.activePresetId
        )
    }

    /**
     * Xoá một preset do người dùng tạo.
     *
     * Preset dựng sẵn không xoá được — chúng là đường thoát khi người dùng làm hỏng cấu hình của mình, và
     * xoá chúng chỉ khiến màn cài đặt trống trơn. Có [BookendsPreferencesManager.resetPreset] để trả về
     * nguyên bản.
     */
    fun deletePreset(presetId: String) = update { settings ->
        if (BookendsDefaults.builtIn().any { it.id == presetId }) {
            settings
        } else {
            val presets = settings.presets.filterNot { it.id == presetId }
            val activeId = if (settings.activePresetId == presetId) {
                presets.firstOrNull()?.id ?: BookendsDefaults.MINIMAL_PRESET_ID
            } else {
                settings.activePresetId
            }
            settings.copy(presets = presets.ifEmpty { BookendsDefaults.builtIn() }, activePresetId = activeId)
        }
    }

    /** Trả một preset dựng sẵn về đúng nội dung gốc. */
    fun resetPreset(presetId: String) {
        val builtIn = BookendsDefaults.builtIn().firstOrNull { it.id == presetId } ?: return
        upsertPreset(builtIn)
    }

    /** Đặt quy tắc cho một đuôi tệp. `presetId` `null` nghĩa là ẩn hẳn overlay cho đuôi đó. */
    fun setAutoRule(extension: String, presetId: String?) = update { settings ->
        val normalized = BookendsAutoRule.normalizeExtension(extension)
        if (normalized.isEmpty()) {
            settings
        } else {
            val rules = settings.autoRules.filterNot {
                BookendsAutoRule.normalizeExtension(it.extension) == normalized
            } + BookendsAutoRule(normalized, presetId)
            settings.copy(autoRules = rules)
        }
    }

    fun removeAutoRule(extension: String) = update { settings ->
        val normalized = BookendsAutoRule.normalizeExtension(extension)
        settings.copy(
            autoRules = settings.autoRules.filterNot {
                BookendsAutoRule.normalizeExtension(it.extension) == normalized
            }
        )
    }

    private fun load(): BookendsSettings =
        BookendsSettingsCodec.decode(prefs.getString(KEY_SETTINGS, null))

    private fun persist(settings: BookendsSettings) {
        prefs.edit()
            .putString(KEY_SETTINGS, BookendsSettingsCodec.encode(settings))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "booxbook_bookends_prefs"
        const val KEY_SETTINGS = "bookends_settings"
    }
}
