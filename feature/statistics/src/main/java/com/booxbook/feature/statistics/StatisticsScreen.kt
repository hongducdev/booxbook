package com.booxbook.feature.statistics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.booxbook.core.ui.theme.GoogleSansFlex600
import com.booxbook.core.ui.theme.GoogleSansFlexDisplay
import com.booxbook.core.ui.theme.PillShape
import com.booxbook.feature.statistics.components.HeroReadingGoalCard
import com.booxbook.feature.statistics.components.ReadingActivityHeatmapCard
import com.booxbook.feature.statistics.components.StreakAndTotalBentoCard
import com.booxbook.feature.statistics.components.TopBooksReadingList

@Composable
fun StatisticsScreen(
    onBookClick: (String) -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (msg != null) {
            viewModel.clearUserMessage()
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Screen Title & Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Thống kê đọc sách",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = GoogleSansFlexDisplay,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Thói quen và tiến độ đọc của bạn",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = GoogleSansFlex600
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "${uiState.overview.completedBooksCount} sách xong",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = GoogleSansFlex600,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 1. Hero Goal Card
                item {
                    HeroReadingGoalCard(
                        todayMinutes = uiState.overview.todayMinutes,
                        dailyGoalMinutes = uiState.overview.dailyGoalMinutes,
                        isPickerExpanded = uiState.isGoalPickerExpanded,
                        onTogglePicker = viewModel::toggleGoalPicker,
                        onSelectGoal = viewModel::setDailyGoal
                    )
                }

                // 2. Bento Pair: Streak & Total Hours
                item {
                    StreakAndTotalBentoCard(
                        currentStreak = uiState.overview.currentStreakDays,
                        longestStreak = uiState.overview.longestStreakDays,
                        totalHours = uiState.overview.totalReadingHours,
                        totalSessions = uiState.overview.totalSessionsCount
                    )
                }

                // 3. Focus Activity Heatmap (M3 Activity Heatmap Grid)
                item {
                    ReadingActivityHeatmapCard(
                        heatmapStats = uiState.overview.heatmapStats,
                        currentStreak = uiState.overview.currentStreakDays,
                        geometry = uiState.geometry
                    )
                }

                // 4. Top Books Reading Time Breakdown
                item {
                    TopBooksReadingList(
                        books = uiState.overview.topBooks,
                        onBookClick = onBookClick
                    )
                }
            }

            // Loading Indicator Overlay
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
