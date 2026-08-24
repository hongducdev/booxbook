package eu.kanade.presentation.more.settings

import eu.kanade.presentation.more.settings.widget.PreferenceItemPosition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PreferenceGroupPositionTest {

    @Test
    fun `grouped style leaves custom content outside the connected standard rows`() {
        val custom = Preference.PreferenceItem.CustomPreference("Theme") {}
        val first = Preference.PreferenceItem.TextPreference("Pure black")
        val last = Preference.PreferenceItem.TextPreference("Primary color")
        val group = Preference.PreferenceGroup(
            title = "Theme",
            groupedStyle = true,
            preferenceItems = listOf(custom, first, last),
        )

        group.positionOf(custom) shouldBe null
        group.positionOf(first) shouldBe PreferenceItemPosition.First
        group.positionOf(last) shouldBe PreferenceItemPosition.Last
    }
}
