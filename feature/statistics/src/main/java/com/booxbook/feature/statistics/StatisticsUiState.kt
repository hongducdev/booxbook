package com.booxbook.feature.statistics

import com.booxbook.core.model.ReadingStatisticsOverview
import com.booxbook.feature.statistics.components.HeatmapTileGeometry

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val overview: ReadingStatisticsOverview = ReadingStatisticsOverview(),
    val geometry: HeatmapTileGeometry = HeatmapTileGeometry.PEBBLE,
    val isSettingsOpen: Boolean = false,
    val isGoalPickerExpanded: Boolean = false,
    val userMessage: String? = null
)
