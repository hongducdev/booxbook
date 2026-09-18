# Reader canvas rendered nothing — root cause and fix

Date: 2026-09-18
Scope: `feature:reader`, `core:engine`, `app`
Reference studied: `Yuneko-dev/Nekori` (novel reader)

## Symptom

Opening any EPUB showed the reader chrome (top bar, progress slider, title, chapter) but the
canvas between them was empty. Under the default DARK preset the whole content area was a flat
`#141218`, i.e. exactly `ReaderThemePreset.DARK`'s background colour.

## Diagnosis

Evidence collected from a live device (`SM-S721B`, `com.booxbook`):

1. `dumpsys activity` proved the whole chain was attached and sized:
   `AndroidComposeView -> AndroidViewsHandler -> ViewFactoryHolder -> FragmentContainerView ->
   ConstraintLayout -> R2ViewPager -> CoordinatorLayout -> R2WebView 0,0-1440,3040`,
   and `EpubNavigatorFragment` was `mAdded=true, mHidden=false, mDetached=false`.
2. CDP (WebView debugging) proved the DOM was healthy: 411x868 CSS viewport, night theme,
   Vietnamese text laid out at `x=20,y=3.8`, `elementFromPoint(20,200)` returned a `<p>`,
   fonts loaded. Repainting `html`/`body` red/green changed nothing on screen.
3. `requestAnimationFrame` fired ~29 frames / 500 ms -> Chromium's compositor was running and
   producing frames.
4. Pixel sampling of the screenshot was the decisive test: the content area was `#141218`
   (Compose `Scaffold` background), **not** `#000000` (the WebView's own night background).
   The interop view was therefore never drawn to the window.

Root cause: the navigator Fragment was committed **synchronously** from `AndroidView`'s `factory`
(`commitNowAllowingStateLoss()` during Compose's measure/layout pass), which attaches the WebView
to a container that is not yet part of the window. Chromium keeps rendering, but nothing reaches
the screen.

A second, independent defect surfaced once rotation was exercised:
`Fragment$InstantiationException: Unable to instantiate EpubNavigatorFragment: could not find
Fragment constructor` - the Readium `FragmentFactory` was being installed from Compose, i.e.
after `FragmentManager` had already tried to restore its fragments during `Activity.onCreate`.

A third defect: `loadBook()` re-ran on every configuration change and called
`openBook()` -> `closeBook()`, closing the live `Publication` while the restored navigator's
WebView was still streaming resources (`IllegalStateException: zip file closed`).

## What Nekori does differently

Nekori never puts the reader inside Compose interop. `NovelWebViewViewer` owns a plain
`FrameLayout` + `WebView`, is held by the reader ViewModel (so it survives configuration
changes), implements paging itself with CSS columns and injected JS, and the Activity only
attaches the viewer's container. That removes the FragmentManager/Compose-interop interaction
that caused every one of the failures above.

BooxBook keeps Readium (EPUB parsing, spine, TOC, locators), so the Fragment stays - but the
lesson was applied in full: the container is a plain view, the Fragment is attached only after
that container is on screen, and nothing depends on `FragmentManager` being able to rebuild the
navigator into a Compose-created view.

## Fix

`feature/reader/.../components/EpubReaderContainer.kt`
- Host view is a plain `FrameLayout`; the navigator is committed from a `LaunchedEffect` after
  the host is attached (`awaitAttached()`), never from the `AndroidView` factory.
- The effect is keyed on `epubEngine.state`, waits for `getNavigatorFactory() != null`, and is
  guarded by `activeFragment != null`, so an early composition retries instead of silently
  giving up.
- A navigator restored by `FragmentManager` keeps a detached, zero-sized view (container child
  count `0`), because Compose creates the container after restoration. Such an orphan is dropped
  and rebuilt once the container is on screen; the reading position is re-applied via
  `initialLocatorJson`.

`core/engine/.../epub/ReadiumFragmentFactory.kt` (new)
- `ReadiumFragmentFactoryProvider` parks the latest Readium `FragmentFactory`.
- `RestorableReadiumFragmentFactory` delegates to it, so restored navigator fragments can be
  re-instantiated. `canRestoreNavigator` reports whether a publication is currently open.

`core/engine` engines
- `EpubReaderEngine` / `Azw3ReaderEngine` register the factory they create and clear it in
  `closeBookSync()` (a stale factory would point at a closed publication).

`app/.../MainActivity.kt`
- Installs the factory on the `FragmentManager` **before** `super.onCreate()` through an
  `@EntryPoint` (Hilt injects fields on context-available, which is too late).
- Drops the saved state when no publication is open, so a process restart cannot restore an
  unbuildable navigator.

`feature/reader/.../ReaderViewModel.kt`
- `loadBook()` is idempotent per ViewModel instance, so configuration changes no longer close
  and reopen the live `Publication` underneath the WebView.

## Verification (device, debug build)

| Check | Result |
| --- | --- |
| Fresh open | cover page and chapter text render (`694 KB` screenshot vs `34 KB` blank before) |
| Pixel probe of content area | `#000000` / text - WebView pixels, no longer `#141218` |
| Page turn forward / backward | renders, returns to the same page |
| Controls overlay / TOC sheet | render above the live canvas |
| Rotate portrait -> landscape -> portrait | same PID, no crash, text renders, position kept |
| `logcat -b crash` | clean |
| `:feature:reader:testDebugUnitTest` | BUILD SUCCESSFUL |

## Follow-up worth considering

The remaining structural risk is hosting a Fragment inside Compose at all. A future upgrade
along the Nekori line would be to serve each spine resource to a plain `WebView` owned outside
the composition (publication resource -> HTML + injected CSS/JS, CSS-column pagination,
locator extraction from JS) and drop `EpubNavigatorFragment` entirely.
