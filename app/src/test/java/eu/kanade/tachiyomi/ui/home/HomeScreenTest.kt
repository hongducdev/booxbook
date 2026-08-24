package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeScreenTest {

    @Test
    fun `phone shows requested navigation outside settings`() {
        shouldShowBottomNavigation(
            isTabletUi = false,
            requestedVisible = true,
            settingsOpen = false,
        ) shouldBe true
    }

    @Test
    fun `settings hide phone navigation overlay`() {
        shouldShowBottomNavigation(
            isTabletUi = false,
            requestedVisible = true,
            settingsOpen = true,
        ) shouldBe false
    }

    @Test
    fun `hidden navigation request remains hidden`() {
        shouldShowBottomNavigation(
            isTabletUi = false,
            requestedVisible = false,
            settingsOpen = false,
        ) shouldBe false
    }

    @Test
    fun `tablet uses navigation rail instead`() {
        shouldShowBottomNavigation(
            isTabletUi = true,
            requestedVisible = true,
            settingsOpen = false,
        ) shouldBe false
    }
}
