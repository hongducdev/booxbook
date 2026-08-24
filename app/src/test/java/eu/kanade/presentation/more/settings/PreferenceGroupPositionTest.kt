package eu.kanade.presentation.more.settings

import eu.kanade.presentation.more.settings.widget.PreferenceItemPosition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PreferenceGroupPositionTest {

    @Test
    fun `standard rows are grouped by default while custom content stays outside`() {
        val custom = Preference.PreferenceItem.CustomPreference("Theme") {}
        val first = Preference.PreferenceItem.TextPreference("Pure black")
        val last = Preference.PreferenceItem.TextPreference("Primary color")
        val group = Preference.PreferenceGroup(
            title = "Theme",
            preferenceItems = listOf(custom, first, last),
        )

        group.positionOf(custom) shouldBe null
        group.positionOf(first) shouldBe PreferenceItemPosition.First
        group.positionOf(last) shouldBe PreferenceItemPosition.Last
    }

    @Test
    fun `custom list row participates in its group`() {
        val first = Preference.PreferenceItem.TextPreference("Font size")
        val customListRow = Preference.PreferenceItem.CustomPreference("Font family", isListItem = true) {}
        val last = Preference.PreferenceItem.TextPreference("Font manager")
        val group = Preference.PreferenceGroup(
            title = "Text",
            preferenceItems = listOf(first, customListRow, last),
        )

        group.positionOf(customListRow) shouldBe PreferenceItemPosition.Middle
    }
}
