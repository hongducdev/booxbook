package com.booxbook.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.feature.reader.preferences.ReaderPreferencesManager
import com.booxbook.feature.statistics.StatisticsPreferencesManager
import com.booxbook.feature.statistics.components.HeatmapTileGeometry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val heatmapGeometry: HeatmapTileGeometry = HeatmapTileGeometry.PEBBLE,
    val dailyGoalMinutes: Int = 45,
    val tapZoneMode: String = "KINDLE",
    val pageTurnEffect: String = "SLIDE",
    val hapticsEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val statisticsPreferencesManager: StatisticsPreferencesManager,
    private val readerPreferencesManager: ReaderPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            heatmapGeometry = statisticsPreferencesManager.heatmapGeometry.value,
            dailyGoalMinutes = 45,
            tapZoneMode = readerPreferencesManager.preferences.value.tapZoneMode,
            pageTurnEffect = readerPreferencesManager.preferences.value.pageTurnEffect,
            hapticsEnabled = readerPreferencesManager.preferences.value.hapticsEnabled
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            statisticsPreferencesManager.heatmapGeometry.collect { geom ->
                _uiState.update { it.copy(heatmapGeometry = geom) }
            }
        }
        viewModelScope.launch {
            bookRepository.getDailyGoalMinutes().collect { goal ->
                _uiState.update { it.copy(dailyGoalMinutes = goal) }
            }
        }
        // Cài đặt hiển thị của màn đọc đến từ cùng một nguồn mà `ReaderViewModel` dùng, nên đổi ở đây là có
        // tác dụng ngay khi đang đọc. Trước đây lớp này ghi thẳng vào SharedPreferences còn màn đọc không bao
        // giờ đọc lại — cài đặt chỉ có tác dụng sau khi khởi động lại ứng dụng, tức là gần như không bao giờ.
        viewModelScope.launch {
            readerPreferencesManager.preferences.collect { preferences ->
                _uiState.update {
                    it.copy(
                        tapZoneMode = preferences.tapZoneMode,
                        pageTurnEffect = preferences.pageTurnEffect,
                        hapticsEnabled = preferences.hapticsEnabled
                    )
                }
            }
        }
    }

    fun setHeatmapGeometry(geometry: HeatmapTileGeometry) {
        statisticsPreferencesManager.setGeometry(geometry)
    }

    fun setDailyGoalMinutes(minutes: Int) {
        viewModelScope.launch {
            bookRepository.setDailyGoalMinutes(minutes)
        }
    }

    fun setTapZoneMode(mode: String) = readerPreferencesManager.setTapZoneMode(mode)

    fun setPageTurnEffect(effect: String) = readerPreferencesManager.setPageTurnEffect(effect)

    fun setHapticsEnabled(enabled: Boolean) = readerPreferencesManager.setHapticsEnabled(enabled)
}
