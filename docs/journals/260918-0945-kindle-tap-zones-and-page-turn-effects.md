# Kindle-like tap zones and page-turn effects

Date: 2026-09-18
Scope: `feature:reader`, `core:engine` (model), `docs`
Plan: `plans/260918-0930-kindle-tap-zones-and-page-turn-effects/plan.md`
Reference studied: `Yuneko-dev/Nekori` (`ui/reader/viewer/text/webview/**`)

## What was wrong

Tapping the left or right edge of the reading canvas did nothing. Verified on device: the
screenshot before and after an edge tap were byte-identical (`694884` bytes each).

Cause: Readium 3.1.1 does not install tap-to-turn navigation. `EpubNavigatorFragment.class`
contains zero references to `DirectionalNavigationAdapter`/`TapEdge`, so nothing was handling
edge taps — the only ways to turn a page were swiping or the progress slider. The app's own
`InputListener` only consoled the middle 50% band to toggle the chrome.

## What Nekori does

`NovelWebViewViewer` resolves taps against normalised `RectF` regions
(`ViewerNavigation.NavigationRegion` = `MENU | PREV | NEXT | LEFT | RIGHT`) and always reserves a
5%-tall menu strip at the very top. Page effects are an enum
(`NovelPageEffect { NONE, HORIZONTAL, SLIDE, CURL }`); the real curl is ~1070 lines across five
files (`NovelPageCurlController/View/Geometry/Renderer/DoublePageCurlRenderer`) and is coupled to
Tsundoku's `ReaderActivity` and webview.

Decision: port the *model* (normalised zones, always-menu strip, named effects), not the mesh
curl. `FLIP` reproduces the read of a Kindle page lift with a snapshot + 3D rotation instead of a
geometry mesh.

## Implementation

New:
- `feature/reader/.../ReaderInteraction.kt` — `ReaderTapZoneMode` (`KINDLE`/`EDGES`/`MENU_ONLY`),
  `ReaderPageTurnEffect` (`SLIDE`/`FLIP`/`NONE`), `ReaderTapAction`, and the pure
  `ReaderTapZones.resolve(x, y, mode, topInsetFraction)`.
- `feature/reader/.../components/PageTurnFlipOverlay.kt` — `PageTurnFlipController` snapshots the
  navigator's `publicationView`, plus the peel animation (280 ms, 84° about the spine edge,
  sweeping shadow). Falls back when the capture is unusable.
- `feature/reader/.../components/TapZonePreviewOverlay.kt` — labelled zone diagram, auto-dismiss.
- `feature/reader/src/test/.../ReaderTapZonesTest.kt` — 14 geometry/boundary tests.

Changed:
- `ReaderPreferences`: `tapZoneMode`, `pageTurnEffect`, `hapticsEnabled`.
- `EpubReaderContainer`: tap handler now resolves zones and emits `ReaderTapAction`; unknown
  zones return `false` so future WebView/link handling is untouched.
- `ReaderScreen`: performs turns with the selected effect, fires haptics, hosts both overlays.
- `ReaderSettingsSheet`: new "Chạm & lật trang" section (zone pills, effect pills, haptics switch,
  preview button). The sheet is now `verticalScroll` — the added controls were otherwise clipped
  off-screen on a 1440x3040 device.
- `ReaderViewModel`: `updateTapZoneMode`, `updatePageTurnEffect`, `updateHapticsEnabled`.

Two design fixes found while testing:
1. The always-menu strip originally spanned the top 6% (182 px) of an edge-to-edge canvas, but the
   status bar is 139 px — only 43 px were reachable. The strip now starts *below* the inset
   (`topInsetFraction`), and the preview overlay draws it at the same offset.
2. A race introduced while touching `EpubReaderContainer`: removing a restored navigator with
   `commitAllowingStateLoss()` is asynchronous, so the following `findFragmentById` still returned
   the fragment pending removal and the detached-view orphan was reused — blank canvas after
   rotation. The replace path is now synchronous and explicit
   (`existing.view?.isAttachedToWindow == true` → reuse, otherwise remove-now + create).

## Verification (device, debug build)

| Check | Result |
| --- | --- |
| Tap right zone (`KINDLE`) | `694884` → `800730`, next page renders |
| Tap left zone | back to `694884` |
| Tap centre | chrome toggles (`703467`) |
| Tap top strip (below status bar) | chrome opens (`704446`) |
| `FLIP` effect | mid-transition frame captured (`j_mid2` = 1.61 MB vs 0.80 MB) showing the outgoing page rotated about the spine over the new page |
| Zone preview overlay | renders (`599130`), auto-dismisses |
| Settings sheet | scrolls, all new controls reachable |
| Rotate ×2 | same PID, no crash, content renders (`809991` / `810070`) |
| `logcat -b crash` | clean |
| `:feature:reader:testDebugUnitTest` | 14 + 11 tests, 0 failures |

## Not verified / out of scope

- Tapping an inline `<a>` link: reasoned only. Taps outside all zones return `false` to Readium,
  and Readium's `TapEvent` is raised from JS for non-link taps, but this was not exercised against
  a book containing links.
- Reader preferences are still in-memory (no DataStore): tap-zone/effect/haptics choices reset
  when the ViewModel is recreated. Separate task.
- No mesh page-curl, no double-page spread, no RTL zone inversion, no CBZ effects.
