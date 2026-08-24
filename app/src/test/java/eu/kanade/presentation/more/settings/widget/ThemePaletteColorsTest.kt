package eu.kanade.presentation.more.settings.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import eu.kanade.domain.ui.model.AppTheme
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ThemePaletteColorsTest {

    @Test
    fun `Catppuccin swatch uses base mantle and crust`() {
        val base = Color(0xFF1E1E2E)
        val mantle = Color(0xFF181825)
        val crust = Color(0xFF11111B)
        val scheme = darkColorScheme(
            surfaceContainerHigh = base,
            background = mantle,
            scrim = crust,
        )

        themePaletteColors(AppTheme.CATPPUCCIN, scheme) shouldBe Triple(base, mantle, crust)
    }
}
