package com.booxbook

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.booxbook.feature.library.LibraryScreen
import com.booxbook.feature.library.LibraryViewModel
import com.booxbook.feature.library.SUPPORTED_BOOK_MIME_TYPES
import com.booxbook.feature.statistics.StatisticsScreen
import com.booxbook.feature.statistics.StatisticsViewModel
import com.booxbook.navigation.BooxBookFloatingToolbar
import com.booxbook.navigation.FloatingNavigationItem
import com.booxbook.settings.SettingsScreen
import com.booxbook.settings.SettingsViewModel
import kotlinx.coroutines.launch

enum class MainTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LIBRARY(
        title = "Tủ sách",
        selectedIcon = Icons.Rounded.AutoStories,
        unselectedIcon = Icons.Outlined.AutoStories
    ),
    STATISTICS(
        title = "Thống kê",
        selectedIcon = Icons.Rounded.Insights,
        unselectedIcon = Icons.Outlined.Insights
    ),
    SETTINGS(
        title = "Cài đặt",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

@Composable
fun MainScreen(
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Eagerly instantiate all tab ViewModels at MainScreen creation to prevent jank on first tab click
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val statisticsViewModel: StatisticsViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var currentTabOrdinal by rememberSaveable { mutableIntStateOf(MainTab.LIBRARY.ordinal) }
    val currentTab = MainTab.entries[currentTabOrdinal]

    val pagerState = rememberPagerState(initialPage = currentTabOrdinal) { MainTab.entries.size }

    // SAF Document Picker for adding books from storage
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            libraryViewModel.importBooksFromUris(uris)
        }
    }

    val navItems = MainTab.entries.mapIndexed { index, tab ->
        FloatingNavigationItem(
            title = tab.title,
            selectedIcon = tab.selectedIcon,
            unselectedIcon = tab.unselectedIcon,
            onClick = {
                if (currentTabOrdinal != index) {
                    currentTabOrdinal = index
                    coroutineScope.launch {
                        pagerState.scrollToPage(index)
                    }
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Tab Content with pre-composed pages (beyondViewportPageCount = 2) to eliminate first-time tab switch jank
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            beyondViewportPageCount = 2,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (MainTab.entries[page]) {
                MainTab.LIBRARY -> LibraryScreen(
                    onBookClick = onBookClick,
                    viewModel = libraryViewModel
                )
                MainTab.STATISTICS -> StatisticsScreen(
                    onBookClick = onBookClick,
                    viewModel = statisticsViewModel
                )
                MainTab.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel
                )
            }
        }

        // Essentials-style Floating Navigation Toolbar with Side Action Button
        BooxBookFloatingToolbar(
            items = navItems,
            selectedIndex = currentTabOrdinal,
            actionButton = if (currentTab == MainTab.LIBRARY) {
                {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .size(60.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    documentPicker.launch(SUPPORTED_BOOK_MIME_TYPES)
                                }
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Thêm sách",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            } else null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
        )
    }
}
