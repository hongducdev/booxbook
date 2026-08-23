# Kindle-style reader pagination

Status: Complete

## Scope

- Fragment novel chapters into fixed-height horizontal pages.
- Make existing tap zones turn one complete page at a time without vertical scrolling.
- Continue into the adjacent chapter at document edges.
- Keep the page containing the active TTS paragraph visible automatically.
- Reuse the current WebView, progress bridge, and chapter loaders.

## Implementation

- [x] Trace tap navigation, scroll progress, chapter loading, and TTS highlighting.
- [x] Add CSS multi-column pagination and shared page-boundary helpers to the existing scroll runtime.
- [x] Route tap/volume/page keys, progress seeking, and TTS visibility through those helpers.
- [x] Run focused regression checks and reader formatting/tests.
- [x] Record the reader improvement in the fork changelog.
- [x] Stabilize page dimensions under reader overlays, replace horizontal motion with fade, and hide native scrollbars.

## Success criteria

- Repeated next/previous taps land on distinct horizontal pages without vertical scrolling.
- A tap at a chapter edge invokes the existing next/previous chapter flow.
- TTS moves to the page containing its active paragraph, even when highlighting is disabled.
- Existing progress persistence continues to receive horizontal page events; infinite scroll is disabled while pagination is active.
