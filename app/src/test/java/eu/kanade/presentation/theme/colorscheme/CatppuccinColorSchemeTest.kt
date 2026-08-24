package eu.kanade.presentation.theme.colorscheme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import eu.kanade.domain.ui.CatppuccinColor
import eu.kanade.domain.ui.UiPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class CatppuccinColorSchemeTest {

    @Test
    fun `Mauve remains the default primary color`() {
        val preferences = UiPreferences(InMemoryPreferenceStore())

        assertEquals(CatppuccinColor.MAUVE, preferences.catppuccinPrimaryColor.get())
    }

    @Test
    fun `each accent uses Latte in light mode and Mocha in dark mode`() {
        CatppuccinColor.entries.forEach { color ->
            val light = CatppuccinColorScheme.getColorScheme(false, false, color)
            val dark = CatppuccinColorScheme.getColorScheme(true, false, color)

            assertEquals(Color(color.latte), light.primary)
            assertEquals(Color(color.mocha), dark.primary)
            assertMinimumContrast(light.primary, light.onPrimary, 4.5f)
            assertMinimumContrast(dark.primary, dark.onPrimary, 4.5f)
            assertMinimumContrast(light.primaryContainer, light.onPrimaryContainer, 4.5f)
            assertMinimumContrast(dark.primaryContainer, dark.onPrimaryContainer, 4.5f)
            assertMinimumContrast(light.secondaryContainer, light.onSecondaryContainer, 4.5f)
            assertMinimumContrast(dark.secondaryContainer, dark.onSecondaryContainer, 4.5f)
            assertMinimumContrast(light.surface, light.outline, 3f)
            assertMinimumContrast(dark.surface, dark.outline, 3f)
        }
    }

    @Test
    fun `outline and inversePrimary follow the accent when it stays readable`() {
        val light = CatppuccinColorScheme.getColorScheme(false, false, CatppuccinColor.RED)
        val dark = CatppuccinColorScheme.getColorScheme(true, false, CatppuccinColor.RED)

        assertEquals(Color(CatppuccinColor.RED.latte), light.outline)
        assertEquals(Color(CatppuccinColor.RED.mocha), dark.outline)
        assertEquals(Color(CatppuccinColor.RED.mocha), light.inversePrimary)
        assertEquals(Color(CatppuccinColor.RED.latte), dark.inversePrimary)
    }

    @Test
    fun `pale accents fall back to the built-in outline and inversePrimary roles`() {
        val light = CatppuccinColorScheme.getColorScheme(false, false, CatppuccinColor.ROSEWATER)
        val dark = CatppuccinColorScheme.getColorScheme(true, false, CatppuccinColor.ROSEWATER)

        assertEquals(Color(0xFF8839EF), light.outline)
        assertEquals(Color(CatppuccinColor.ROSEWATER.mocha), dark.outline)
        assertEquals(Color(CatppuccinColor.ROSEWATER.mocha), light.inversePrimary)
        assertEquals(Color(0xFF8839EF), dark.inversePrimary)
    }

    @Test
    fun `only the fourteen accent colors are selectable`() {
        assertEquals(
            listOf(
                "ROSEWATER", "FLAMINGO", "PINK", "MAUVE", "RED", "MAROON", "PEACH",
                "YELLOW", "GREEN", "TEAL", "SKY", "SAPPHIRE", "BLUE", "LAVENDER",
            ),
            CatppuccinColor.entries.map(CatppuccinColor::name),
        )
    }

    private fun assertMinimumContrast(background: Color, foreground: Color, minimum: Float) {
        val lighter = maxOf(background.luminance(), foreground.luminance())
        val darker = minOf(background.luminance(), foreground.luminance())
        val contrast = (lighter + 0.05f) / (darker + 0.05f)

        org.junit.jupiter.api.Assertions.assertTrue(contrast >= minimum, "Contrast was $contrast")
    }
}
