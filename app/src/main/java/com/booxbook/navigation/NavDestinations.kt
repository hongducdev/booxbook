package com.booxbook.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.booxbook.feature.library.LibraryScreen
import com.booxbook.feature.library.detail.BookDetailScreen
import com.booxbook.feature.reader.ReaderScreen
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Library : Screen("library")

    data object BookDetail : Screen("book_detail/{bookId}") {
        fun createRoute(bookId: String) = "book_detail/$bookId"
    }

    data object Reader : Screen("reader/{bookId}?locator={locator}") {
        fun createRoute(bookId: String, locator: String? = null): String {
            return if (locator.isNullOrBlank()) {
                "reader/$bookId"
            } else {
                val encoded = URLEncoder.encode(locator, "UTF-8")
                "reader/$bookId?locator=$encoded"
            }
        }
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
                    navController.navigate(Screen.BookDetail.createRoute(bookId))
                }
            )
        }
        composable(
            route = Screen.BookDetail.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            BookDetailScreen(
                bookId = bookId,
                onBackClick = {
                    navController.popBackStack()
                },
                onReadClick = { bId, locator ->
                    navController.navigate(Screen.Reader.createRoute(bId, locator))
                },
                onBookDeleted = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("locator") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            val rawLocator = backStackEntry.arguments?.getString("locator")
            val decodedLocator = rawLocator?.let {
                runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() ?: it
            }
            ReaderScreen(
                bookId = bookId,
                initialLocator = decodedLocator,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
