package com.booxbook.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.booxbook.feature.library.LibraryScreen
import com.booxbook.feature.reader.ReaderScreen

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
}

@Composable
fun BooxBookNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = modifier
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                onBookClick = { bookId ->
                    navController.navigate(Screen.Reader.createRoute(bookId))
                }
            )
        }
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            ReaderScreen(
                bookId = bookId,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
