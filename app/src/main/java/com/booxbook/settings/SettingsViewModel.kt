package com.booxbook.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.feature.statistics.StatisticsPreferencesManager
import com.booxbook.feature.statistics.components.HeatmapTileGeometry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val readerPrefs = context.getSharedPreferences("booxbook_reader_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            heatmapGeometry = statisticsPreferencesManager.heatmapGeometry.value,
            dailyGoalMinutes = 45,
            tapZoneMode = readerPrefs.getString("tap_zone_mode", "KINDLE") ?: "KINDLE",
            pageTurnEffect = readerPrefs.getString("page_turn_effect", "SLIDE") ?: "SLIDE",
            hapticsEnabled = readerPrefs.getBoolean("haptics_enabled", true)
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
    }

    fun setHeatmapGeometry(geometry: HeatmapTileGeometry) {
        statisticsPreferencesManager.setGeometry(geometry)
    }

    fun setDailyGoalMinutes(minutes: Int) {
        viewModelScope.launch {
            bookRepository.setDailyGoalMinutes(minutes)
        }
    }

    fun setTapZoneMode(mode: String) {
        readerPrefs.edit().putString("tap_zone_mode", mode).apply()
        _uiState.update { it.copy(tapZoneMode = mode) }
    }

    fun setPageTurnEffect(effect: String) {
        readerPrefs.edit().putString("page_turn_effect", effect).apply()
        _uiState.update { it.copy(pageTurnEffect = effect) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        readerPrefs.edit().putBoolean("haptics_enabled", enabled).apply()
        _uiState.update { it.copy(hapticsEnabled = enabled) }
    }
}
