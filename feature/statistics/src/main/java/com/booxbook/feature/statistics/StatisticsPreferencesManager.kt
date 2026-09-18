package com.booxbook.feature.statistics

import android.content.Context
import com.booxbook.feature.statistics.components.HeatmapTileGeometry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsPreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("booxbook_statistics_prefs", Context.MODE_PRIVATE)

    private val _heatmapGeometry = MutableStateFlow(loadGeometry())
    val heatmapGeometry: StateFlow<HeatmapTileGeometry> = _heatmapGeometry.asStateFlow()

    private fun loadGeometry(): HeatmapTileGeometry {
        val key = prefs.getString("heatmap_geometry", HeatmapTileGeometry.PEBBLE.name)
        return runCatching {
            HeatmapTileGeometry.valueOf(key ?: HeatmapTileGeometry.PEBBLE.name)
        }.getOrDefault(HeatmapTileGeometry.PEBBLE)
    }

    fun setGeometry(geometry: HeatmapTileGeometry) {
        prefs.edit().putString("heatmap_geometry", geometry.name).apply()
        _heatmapGeometry.value = geometry
    }
}
