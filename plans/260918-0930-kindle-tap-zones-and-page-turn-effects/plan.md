# Plan: Kindle-like touch interaction & page-turn effects

Timestamp: 260918-0930
Module: `feature:reader` (+ `core:engine` model, `docs`)
Status: **implemented** (auto mode) — verified on device; see
`docs/journals/260918-0945-kindle-tap-zones-and-page-turn-effects.md`

## Goal

Add Kindle-style reading ergonomics to the EPUB/AZW3 reader:

1. **Tap zones that actually turn pages** — today tapping the left/right edge does nothing
   (verified on device: screenshots before/after an edge tap are byte-identical). Readium 3.1.1
   does not wire `DirectionalNavigationAdapter` at all (`EpubNavigatorFragment` never references
   it), so the app must drive page turns itself.
2. **Page-turn effects** — selectable, Kindle-flavoured: slide (default), page flip ("curl"), none.
3. **Light haptic tick** on every page turn.
4. **Zone preview overlay** so the user can see where to tap (Nekori does the same).

## Evidence / constraints (already verified)

| Fact | Source |
| --- | --- |
| Edge taps do not turn pages | device test, `e0.png` == `e_right.png` == `e_left.png` (694884 bytes) |
| Center tap toggles chrome (works) | device test, `e_top.png` differs |
| Readium exposes `goForward(animated)`, `goBackward(animated)`, `addInputListener` | `javap` on `EpubNavigatorFragment` |
| Readium does **not** register tap-edge navigation | `DirectionalNavigationAdapter`/`TapEdge` string count = 0 in `EpubNavigatorFragment.class` |
| `TapEvent` carries only a `PointF` (view-space px) | `javap` on `org.readium.r2.navigator.input.TapEvent` |
| Nekori's real page-curl = ~1070 lines / 5 files, mesh renderer, Tsundoku-coupled | `curl/*.kt` line counts |
| Nekori's tap zones = normalised rects + always-on top 5% menu strip | `viewer/ViewerNavigation.kt` |
| BooxBook `ReaderPreferences` is in-memory only (no DataStore) | grep |

## Design decisions

**Tap-zone layouts** (`ReaderTapZoneMode`):

| Mode | prev | menu | next | top strip |
| --- | --- | --- | --- | --- |
| `KINDLE` (default) | x < 0.30 | 0.30..0.70 | x > 0.70 | y < 0.06 → menu |
| `EDGES` | x < 0.25 | 0.25..0.75 | x > 0.75 | — |
| `MENU_ONLY` | — | x in 0.25..0.75 | — | — |

`MENU_ONLY` reproduces today's behaviour exactly, so the change is safe to default away from.

**Page-turn effects** (`ReaderPageTurnEffect`):

| Effect | Implementation |
| --- | --- |
| `SLIDE` (default) | Readium's own scroller animation — `goForward/goBackward(animated = true)` |
| `FLIP` ("lật trang") | Snapshot the navigator view to a bitmap, turn instantly (`animated = false`), then peel the snapshot away with a Compose `graphicsLayer` 3D rotation + shadow scrim. Falls back to `SLIDE` if the snapshot is empty. |
| `NONE` | `animated = false` |

A true Kindle mesh curl (Nekori's `NovelPageCurlGeometry`/`Renderer`) is **deliberately not
ported**: it is ~1000 lines coupled to Tsundoku's `ReaderActivity`/`WebView`, and its geometry is
tuned for their own pager. The `FLIP` overlay gives the same read (a page lifting off the book)
at a fraction of the risk.

## Phases

### phase-01 — model & preferences
- `core/engine/.../model/ReaderState.kt`: extend `ReaderPreferences` with
  `tapZoneMode: String = "KINDLE"`, `pageTurnEffect: String = "SLIDE"`, `hapticsEnabled: Boolean = true`.
- `feature/reader/.../ReaderInteraction.kt` (new): `ReaderTapZoneMode`, `ReaderPageTurnEffect`,
  and the pure resolver `fun resolveTapZone(x: Float, y: Float, mode: ReaderTapZoneMode): ReaderTapAction`
  where `ReaderTapAction = PREV | NEXT | MENU | NONE`. Pure = unit-testable.
- `ReaderViewModel`: `updateTapZoneMode`, `updatePageTurnEffect`, `updateHaptics`.

### phase-02 — gesture plumbing
- `EpubReaderContainer`: replace the hardcoded central-band `InputListener` with the resolver,
  drive `goForward/goBackward`, and report turns through a new `onPageTurned: (forward: Boolean) -> Unit`
  callback. Keep consuming only resolved zones so link taps still reach the WebView.
- `ReaderScreen`: haptic on `onPageTurned` when `hapticsEnabled`; pass mode/effect down.

### phase-03 — flip overlay
- `PageTurnFlipOverlay.kt` (new): holds the captured `ImageBitmap`, animates progress 0→1 with a
  `graphicsLayer` rotation about the spine edge plus an edge scrim, then clears.
- Snapshot helper on the Android side (`fragment.publicationView.draw(bitmapCanvas)`), executed on
  the main thread before the instant turn.

### phase-04 — settings UI
- `ReaderSettingsSheet`: new section "Chạm & lật trang" — tap-zone pills, page-turn-effect pills,
  haptics switch, and a "Xem vùng chạm" button.

### phase-05 — zone preview overlay
- `TapZonePreviewOverlay.kt` (new): translucent labelled rectangles over the canvas
  (Trang trước / Menu / Trang sau), auto-dismiss after ~2.2 s.
- Shown from the settings button.

### phase-06 — tests & device verification
- `feature/reader/src/test/.../ReaderTapZonesTest.kt` (new): table-driven resolver tests
  (boundaries 0.25/0.30/0.70/0.75, top strip, `MENU_ONLY` no-ops).
- Device: each mode × tap left/right/center/top; each effect; verify page actually changes and the
  slider follows; rotation still renders; `logcat -b crash` clean.

## Acceptance criteria

- [ ] Tapping the right/left zone changes the page in every non-`MENU_ONLY` mode (screenshot differs).
- [ ] Top strip opens the chrome in `KINDLE`.
- [ ] `MENU_ONLY` reproduces the pre-change behaviour.
- [ ] `SLIDE` animates; `NONE` cuts; `FLIP` shows the page-lift overlay.
- [ ] Tapping a link inside the book still follows the link (does not turn the page).
- [ ] Zone preview overlay renders the configured layout and auto-dismisses.
- [ ] Resolver unit tests pass; `:feature:reader:testDebugUnitTest` + `:app:assembleDebug` green.
- [ ] No regression to the rotation/restore fix from `260918-0900-reader-canvas-blank-render-fix`.

## Explicitly out of scope

- Persisting reader preferences across app restarts (needs new DataStore plumbing; separate task).
- True mesh page-curl, double-page spreads, tap-zone inversion for left-handed/RTL e-ink use.
- CBZ page-turn effects (CBZ is a Compose `LazyColumn`; different mechanism).

## Risks

| Risk | Mitigation |
| --- | --- |
| WebView software snapshot blank/slow for `FLIP` | validate bitmap; fall back to `SLIDE`; snapshot before the turn on the main thread |
| Consuming taps that should reach links | resolver returns `NONE` outside zones → listener returns `false`; verify link tap on device |
| `TapEvent.point` units | it is view-space px; divide by `fragment.view.width/height`, guard `<= 0` |
| Effect changes mid-session | effects are read per turn, no navigator rebuild needed |
