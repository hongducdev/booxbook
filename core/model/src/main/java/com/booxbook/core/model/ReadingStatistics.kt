package com.booxbook.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DailyReadingStat(
    val date: String, // yyyy-MM-dd
    val dayOfWeek: String, // e.g. "T2", "T3", ..., "CN"
    val durationMinutes: Int,
    val isToday: Boolean = false
)

@Serializable
data class HeatmapDayStat(
    val date: String, // yyyy-MM-dd
    val durationMinutes: Int,
    val level: Int, // 0..4
    val dayOfWeek: Int, // 1 (Mon) .. 7 (Sun)
    val isToday: Boolean = false
)

@Serializable
data class BookReadingStat(
    val book: Book,
    val totalDurationSeconds: Long,
    val progressPercentage: Float = 0f
)

@Serializable
data class ReadingStatisticsOverview(
    val todayMinutes: Int = 0,
    val dailyGoalMinutes: Int = 45,
    val totalReadingHours: Float = 0f,
    val totalSessionsCount: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val completedBooksCount: Int = 0,
    val weeklyStats: List<DailyReadingStat> = emptyList(),
    val heatmapStats: List<HeatmapDayStat> = emptyList(),
    val topBooks: List<BookReadingStat> = emptyList()
)
