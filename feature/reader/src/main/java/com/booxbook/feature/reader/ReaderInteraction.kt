package com.booxbook.feature.reader

/**
 * Tap-zone layout used to turn pages by tapping the reading canvas.
 *
 * Geometry is expressed on normalised coordinates (`0f..1f`) so it is independent of the
 * device size and of the WebView's pixel density.
 */
enum class ReaderTapZoneMode(val displayName: String) {
    /** Kindle-like: previous | menu | next thirds, plus a thin always-menu strip at the top. */
    KINDLE("Kiểu Kindle"),

    /** Only the outer edges turn pages; everything else opens the menu. */
    EDGES("Hai mép"),

    /** No tap-based page turning; only the middle band toggles the reading chrome. */
    MENU_ONLY("Chỉ menu");

    companion object {
        fun fromKey(key: String?): ReaderTapZoneMode =
            entries.firstOrNull { it.name == key } ?: KINDLE
    }
}

/** Transition played when a page turn is triggered. */
enum class ReaderPageTurnEffect(val displayName: String) {
    /** Horizontal slide, provided by Readium's own pager animation. */
    SLIDE("Trượt"),

    /** Kindle-like page lift: the outgoing page peels away over the new one. */
    FLIP("Lật trang"),

    /** Instant swap. */
    NONE("Không");

    companion object {
        fun fromKey(key: String?): ReaderPageTurnEffect =
            entries.firstOrNull { it.name == key } ?: SLIDE
    }
}

/** What a tap on the reading canvas resolved to. */
enum class ReaderTapAction { PREV, NEXT, MENU, NONE }

/**
 * Pure tap-zone resolver.
 *
 * Kept free of Android types so it can be unit-tested directly.
 */
object ReaderTapZones {

    /** Height of the always-menu strip at the top of the canvas. */
    const val TOP_STRIP = 0.06f

    /** Edge width for [ReaderTapZoneMode.KINDLE]. */
    const val KINDLE_EDGE = 0.30f

    /** Edge width for [ReaderTapZoneMode.EDGES]. */
    const val EDGES_EDGE = 0.25f

    /** Middle band that toggles the chrome in [ReaderTapZoneMode.MENU_ONLY]. */
    const val MENU_ONLY_START = 0.25f
    const val MENU_ONLY_END = 0.75f

    /**
     * Resolves a tap at normalised ([x], [y]) into an action.
     *
     * [topInsetFraction] is the fraction of the canvas hidden behind the status bar; the
     * always-menu strip starts *below* it, otherwise it would be unreachable on edge-to-edge
     * screens. Inputs are clamped, so a tap slightly outside the canvas still resolves predictably.
     */
    fun resolve(
        x: Float,
        y: Float,
        mode: ReaderTapZoneMode,
        topInsetFraction: Float = 0f
    ): ReaderTapAction {
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
        val stripBottom = topInsetFraction.coerceIn(0f, 0.5f) + TOP_STRIP

        return when (mode) {
            ReaderTapZoneMode.KINDLE -> when {
                py <= stripBottom -> ReaderTapAction.MENU
                px < KINDLE_EDGE -> ReaderTapAction.PREV
                px > 1f - KINDLE_EDGE -> ReaderTapAction.NEXT
                else -> ReaderTapAction.MENU
            }

            ReaderTapZoneMode.EDGES -> when {
                px < EDGES_EDGE -> ReaderTapAction.PREV
                px > 1f - EDGES_EDGE -> ReaderTapAction.NEXT
                else -> ReaderTapAction.MENU
            }

            ReaderTapZoneMode.MENU_ONLY ->
                if (px in MENU_ONLY_START..MENU_ONLY_END) ReaderTapAction.MENU else ReaderTapAction.NONE
        }
    }
}
