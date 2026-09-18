package com.booxbook.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure geometry tests for the tap-zone resolver.
 *
 * These are the contract that `EpubReaderContainer` relies on, so boundaries are asserted
 * exactly: a one-pixel shift at 30% / 70% must not teleport a tap into the wrong zone.
 */
class ReaderTapZonesTest {

    @Test
    fun `kindle mode turns pages only in the outer thirds`() {
        assertEquals(ReaderTapAction.PREV, resolve(0.10f, 0.50f, ReaderTapZoneMode.KINDLE))
        assertEquals(ReaderTapAction.NEXT, resolve(0.90f, 0.50f, ReaderTapZoneMode.KINDLE))
        assertEquals(ReaderTapAction.MENU, resolve(0.50f, 0.50f, ReaderTapZoneMode.KINDLE))
    }

    @Test
    fun `kindle mode boundary between prev and menu is exclusive at 30 percent`() {
        assertEquals(ReaderTapAction.PREV, resolve(0.299f, 0.50f, ReaderTapZoneMode.KINDLE))
        assertEquals(ReaderTapAction.MENU, resolve(0.300f, 0.50f, ReaderTapZoneMode.KINDLE))
    }

    @Test
    fun `kindle mode boundary between menu and next is exclusive at 70 percent`() {
        assertEquals(ReaderTapAction.MENU, resolve(0.700f, 0.50f, ReaderTapZoneMode.KINDLE))
        assertEquals(ReaderTapAction.NEXT, resolve(0.701f, 0.50f, ReaderTapZoneMode.KINDLE))
    }

    @Test
    fun `kindle mode top strip opens the menu wherever it is tapped`() {
        assertEquals(ReaderTapAction.MENU, resolve(0.50f, 0.03f, ReaderTapZoneMode.KINDLE))
        // The strip wins over the previous-page zone.
        assertEquals(ReaderTapAction.MENU, resolve(0.05f, 0.03f, ReaderTapZoneMode.KINDLE))
        // ...and over the next-page zone.
        assertEquals(ReaderTapAction.MENU, resolve(0.95f, 0.03f, ReaderTapZoneMode.KINDLE))
    }

    @Test
    fun `kindle mode top strip boundary is inclusive and stops below it`() {
        assertEquals(ReaderTapAction.MENU, resolve(0.05f, ReaderTapZones.TOP_STRIP, ReaderTapZoneMode.KINDLE))
        assertEquals(ReaderTapAction.PREV, resolve(0.05f, 0.07f, ReaderTapZoneMode.KINDLE))
        assertEquals(ReaderTapAction.NEXT, resolve(0.95f, 0.07f, ReaderTapZoneMode.KINDLE))
    }

    @Test
    fun `edges mode ignores the top strip`() {
        assertEquals(ReaderTapAction.PREV, resolve(0.05f, 0.01f, ReaderTapZoneMode.EDGES))
        assertEquals(ReaderTapAction.NEXT, resolve(0.95f, 0.01f, ReaderTapZoneMode.EDGES))
    }

    @Test
    fun `edges mode uses a quarter of the width on each side`() {
        assertEquals(ReaderTapAction.PREV, resolve(0.24f, 0.50f, ReaderTapZoneMode.EDGES))
        assertEquals(ReaderTapAction.MENU, resolve(0.25f, 0.50f, ReaderTapZoneMode.EDGES))
        assertEquals(ReaderTapAction.MENU, resolve(0.75f, 0.50f, ReaderTapZoneMode.EDGES))
        assertEquals(ReaderTapAction.NEXT, resolve(0.76f, 0.50f, ReaderTapZoneMode.EDGES))
    }

    @Test
    fun `menu only mode never turns pages`() {
        assertEquals(ReaderTapAction.NONE, resolve(0.05f, 0.50f, ReaderTapZoneMode.MENU_ONLY))
        assertEquals(ReaderTapAction.NONE, resolve(0.95f, 0.50f, ReaderTapZoneMode.MENU_ONLY))
        assertEquals(ReaderTapAction.NONE, resolve(0.10f, 0.02f, ReaderTapZoneMode.MENU_ONLY))
    }

    @Test
    fun `menu only mode keeps the previous middle band and its boundaries`() {
        assertEquals(ReaderTapAction.NONE, resolve(0.249f, 0.50f, ReaderTapZoneMode.MENU_ONLY))
        assertEquals(ReaderTapAction.MENU, resolve(0.250f, 0.50f, ReaderTapZoneMode.MENU_ONLY))
        assertEquals(ReaderTapAction.MENU, resolve(0.500f, 0.50f, ReaderTapZoneMode.MENU_ONLY))
        assertEquals(ReaderTapAction.MENU, resolve(0.750f, 0.50f, ReaderTapZoneMode.MENU_ONLY))
        assertEquals(ReaderTapAction.NONE, resolve(0.751f, 0.50f, ReaderTapZoneMode.MENU_ONLY))
    }

    @Test
    fun `coordinates outside the canvas are clamped`() {
        assertEquals(ReaderTapAction.NEXT, resolve(1.40f, 0.50f, ReaderTapZoneMode.KINDLE))
        assertEquals(ReaderTapAction.PREV, resolve(-0.40f, 0.50f, ReaderTapZoneMode.KINDLE))
        assertEquals(ReaderTapAction.NEXT, resolve(1.40f, 0.50f, ReaderTapZoneMode.EDGES))
        assertEquals(ReaderTapAction.NONE, resolve(1.40f, 0.50f, ReaderTapZoneMode.MENU_ONLY))
    }

    @Test
    fun `mode keys round trip and fall back to kindle`() {
        ReaderTapZoneMode.entries.forEach { mode ->
            assertEquals(mode, ReaderTapZoneMode.fromKey(mode.name))
        }
        assertEquals(ReaderTapZoneMode.KINDLE, ReaderTapZoneMode.fromKey(null))
        assertEquals(ReaderTapZoneMode.KINDLE, ReaderTapZoneMode.fromKey("not-a-mode"))
    }

    @Test
    fun `effect keys round trip and fall back to slide`() {
        ReaderPageTurnEffect.entries.forEach { effect ->
            assertEquals(effect, ReaderPageTurnEffect.fromKey(effect.name))
        }
        assertEquals(ReaderPageTurnEffect.SLIDE, ReaderPageTurnEffect.fromKey(null))
        assertEquals(ReaderPageTurnEffect.SLIDE, ReaderPageTurnEffect.fromKey("not-an-effect"))
    }

    @Test
    fun `status bar inset pushes the menu strip below the status bar`() {
        val inset = 0.046f
        val stripBottom = inset + ReaderTapZones.TOP_STRIP

        // On the strip boundary -> menu.
        assertEquals(
            ReaderTapAction.MENU,
            ReaderTapZones.resolve(0.05f, stripBottom, ReaderTapZoneMode.KINDLE, inset)
        )
        // Just below it the normal zones apply again.
        assertEquals(
            ReaderTapAction.PREV,
            ReaderTapZones.resolve(0.05f, stripBottom + 0.005f, ReaderTapZoneMode.KINDLE, inset)
        )
        // A tap physically behind the status bar also resolves to menu (it never reaches the app).
        assertEquals(
            ReaderTapAction.MENU,
            ReaderTapZones.resolve(0.05f, 0.01f, ReaderTapZoneMode.KINDLE, inset)
        )
    }

    @Test
    fun `absurd insets are clamped so zones stay reachable`() {
        assertEquals(
            ReaderTapAction.MENU,
            ReaderTapZones.resolve(0.05f, 0.50f, ReaderTapZoneMode.KINDLE, 5f)
        )
        assertEquals(
            ReaderTapAction.MENU,
            ReaderTapZones.resolve(0.05f, ReaderTapZones.TOP_STRIP, ReaderTapZoneMode.KINDLE, -1f)
        )
    }

    private fun resolve(x: Float, y: Float, mode: ReaderTapZoneMode) = ReaderTapZones.resolve(x, y, mode)
}
