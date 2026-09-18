package com.booxbook.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.booxbook.core.database.repository.BookRepository
import com.booxbook.feature.statistics.components.HeatmapTileGeometry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val preferencesManager: StatisticsPreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState(geometry = preferencesManager.heatmapGeometry.value))
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.heatmapGeometry.collect { geom ->
                _uiState.update { it.copy(geometry = geom) }
            }
        }
        loadStatistics()
    }

    fun loadStatistics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            bookRepository.getReadingStatisticsOverview()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userMessage = "Không thể tải thống kê: ${e.message}"
                        )
                    }
                }
                .collect { overview ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            overview = overview
                        )
                    }
                }
        }
    }

    fun setHeatmapGeometry(geometry: HeatmapTileGeometry) {
        preferencesManager.setGeometry(geometry)
    }

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch {
            bookRepository.setDailyGoalMinutes(minutes)
            _uiState.update { it.copy(isGoalPickerExpanded = false) }
        }
    }

    fun toggleGoalPicker(expanded: Boolean) {
        _uiState.update { it.copy(isGoalPickerExpanded = expanded) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
