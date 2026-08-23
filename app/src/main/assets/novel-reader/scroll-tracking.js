// Page-side scroll listener for the novel WebView reader.
// Installed once per load via NovelWebViewStyler.injectScrollTracking(), which substitutes the
// __TSUNDOKU_OBJECT_NAME__ / __CHAPTER_DIVIDER_CLASS__ / __CHAPTER_ID_ATTR__ /
// __INFINITE_SCROLL_ENABLED__ / __PAGINATED_ENABLED__ / __LOAD_THRESHOLD__ /
// __DONE_THRESHOLD__ / __PROGRESS_EVENT__ tokens.
//
// Reports to the Android JS interface:
//   onChapterScrollUpdate(chapterId, progress)  visible chapter changed
//   onScrollUpdate(progress)                     live slider position
//   onScrollProgress(progress)                   persist point (scroll settled / 100%)
//   loadNextChapter()
//
// Publishes for snippets/plugins:
//   runtime.progress / runtime.chapterProgress / runtime.currentChapterId  (updated every frame)
//   window event __PROGRESS_EVENT__  { progress, chapterProgress, chapterId, isLast }
//     dispatched JS-side (no Kotlin bridge hop), throttled with the slider bridge.

(function () {
    window.__TSUNDOKU_OBJECT_NAME__ = window.__TSUNDOKU_OBJECT_NAME__ || {};
    window.__TSUNDOKU_OBJECT_NAME__.runtime = window.__TSUNDOKU_OBJECT_NAME__.runtime || {};
    var runtime = window.__TSUNDOKU_OBJECT_NAME__.runtime;

    if (runtime.infiniteScrollInstalled) {
        return;
    }
    runtime.infiniteScrollInstalled = true;

    var infiniteScrollEnabled = __INFINITE_SCROLL_ENABLED__;
    var paginated = __PAGINATED_ENABLED__;
    runtime.paginated = paginated;
    var loadThreshold = __LOAD_THRESHOLD__;
    var lastSliderProgress = -1;
    var lastScrollUpdateTime = 0;
    // Late reflow (images/fonts) triggers scroll anchoring that fires scrollend off a
    // still-settling docHeight; persistCurrent waits this out before saving.
    var lastBodyResizeAt = 0;
    var SETTLE_MS = 400;

    runtime.loadingNext = runtime.loadingNext || false;
    runtime.setLoadingNext = function (v) {
        runtime.loadingNext = !!v;
        // Re-check once the latch clears. A document that still ends inside the viewport - every
        // chapter appended so far is shorter than the screen - produces no scroll event, so this is
        // the only thing that can ask for the chapter after it.
        if (!runtime.loadingNext) onScroll();
    };
    runtime.noMoreChapters = runtime.noMoreChapters || false;
    runtime.setNoMoreChapters = function (v) { runtime.noMoreChapters = !!v; };
    runtime.lastChapterIdxSeen = (typeof runtime.lastChapterIdxSeen === 'number') ? runtime.lastChapterIdxSeen : -1;
    // Forces the next scroll frame to re-emit onChapterScrollUpdate. Called by the Android side when
    // it lifts the scroll-restore guard, so a chapter switch dropped while the guard was up (this
    // callback is edge-triggered and won't otherwise re-fire for the same idx) is re-reported.
    runtime.resetChapterTracking = function () { runtime.lastChapterIdxSeen = -1; };

    window.chapterBoundaries = window.chapterBoundaries || [];
    runtime.knownDividerCount = runtime.knownDividerCount || 0;

    function position() {
        return paginated
            ? (window.scrollX || document.documentElement.scrollLeft || document.body.scrollLeft || 0)
            : (window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0);
    }

    function viewportExtent() {
        return Math.max(
            paginated
                ? (window.innerWidth || document.documentElement.clientWidth || 1)
                : (window.innerHeight || document.documentElement.clientHeight || 1),
            1
        );
    }

    function documentExtent() {
        return paginated
            ? Math.max(document.documentElement.scrollWidth, document.body ? document.body.scrollWidth : 0)
            : Math.max(document.documentElement.scrollHeight, document.body ? document.body.scrollHeight : 0);
    }

    // Overflow columns end at their content edge in Chromium, excluding the container's trailing
    // padding. Extend only the scroll area so the last page keeps the same right margin.
    var pageEndSpacer = null;
    function updatePageEndSpacer() {
        if (!paginated || !document.body) return;
        var chapter = document.getElementById('LNReader-chapter');
        if (!chapter) return;
        if (!pageEndSpacer) {
            pageEndSpacer = document.createElement('div');
            pageEndSpacer.setAttribute('aria-hidden', 'true');
            pageEndSpacer.style.cssText = 'position:absolute;top:0;height:1px;width:var(--reader-margin-right);pointer-events:none;';
            document.body.appendChild(pageEndSpacer);
        }
        var left = chapter.scrollWidth + 'px';
        if (pageEndSpacer.style.left !== left) pageEndSpacer.style.left = left;
    }

    function scrollToPosition(value, behavior) {
        window.scrollTo(paginated
            ? { left: value, top: 0, behavior: behavior }
            : { left: 0, top: value, behavior: behavior });
    }

    function computeState() {
        var scrollPosition = position();
        // window.innerHeight is the visual viewport, documentElement.clientHeight (layout viewport)
        // diverges under useWideViewPort/loadWithOverviewMode so the bottom never reaches 100%.
        var docHeight = documentExtent();
        var viewport = viewportExtent();
        var scrollable = docHeight - viewport;
        var progress = scrollable > 0 ? scrollPosition / scrollable : 1;
        if (scrollable > 0 && scrollPosition >= scrollable - 2) progress = 1.0;
        if (progress >= __DONE_THRESHOLD__) progress = 1.0;
        if (progress < 0) progress = 0;

        var chapterProgress = progress;
        var idx = 0;
        var chapterId = null;
        var isLast = true;
        if (infiniteScrollEnabled && window.chapterBoundaries.length > 1) {
            for (var i = 0; i < window.chapterBoundaries.length; i++) {
                if (scrollPosition >= window.chapterBoundaries[i].startOffset) idx = i; else break;
            }
            // A trailing chapter shorter than the viewport begins at or past the furthest reachable
            // scrollTop, so the loop above can never land on it. Being at the document bottom is
            // the only signal that it is the chapter on screen; without this it stayed invisible to
            // the load-next check below and appending stopped there.
            if (scrollable <= 0 || scrollPosition >= scrollable - 2) {
                idx = window.chapterBoundaries.length - 1;
            }
            var boundary = window.chapterBoundaries[idx];
            chapterId = boundary.chapterId;
            var chapterScrollY = Math.max(scrollPosition - boundary.startOffset, 0);
            // Only the last loaded chapter has an unreachable trailing viewport, middle chapters
            // end at the next divider, so subtract innerHeight only for the last one.
            isLast = idx === window.chapterBoundaries.length - 1;
            if (isLast && boundary.height <= viewport) {
                // A last chapter shorter than the viewport has no scroll room of its own, so
                // chapterScrollY stays 0 and it would never reach the load threshold or 100%.
                // Fall back to whole-document progress (the doc bottom is reachable).
                chapterProgress = progress;
            } else {
                var effectiveHeight = Math.max(boundary.height - (isLast ? viewport : 0), 1);
                chapterProgress = Math.min(chapterScrollY / effectiveHeight, 1.0);
                if (chapterProgress >= __DONE_THRESHOLD__) chapterProgress = 1.0;
            }
        }
        return { progress: progress, chapterProgress: chapterProgress, idx: idx, chapterId: chapterId, isLast: isLast };
    }

    function maxScrollPosition() {
        updatePageEndSpacer();
        return documentExtent() - viewportExtent();
    }

    function changePage(target, fade) {
        if (!paginated || !fade) {
            scrollToPosition(target, 'instant');
            return;
        }
        if (runtime.pageTurnBusy) {
            runtime.pendingPageTarget = target;
            return;
        }
        runtime.pageTurnBusy = true;
        document.documentElement.classList.add('tsundoku-page-fading');
        setTimeout(function () {
            scrollToPosition(target, 'instant');
            requestAnimationFrame(function () {
                document.documentElement.classList.remove('tsundoku-page-fading');
                setTimeout(function () {
                    runtime.pageTurnBusy = false;
                    if (typeof runtime.pendingPageTarget === 'number') {
                        var pendingTarget = runtime.pendingPageTarget;
                        runtime.pendingPageTarget = null;
                        if (Math.abs(pendingTarget - position()) >= 2) changePage(pendingTarget, true);
                    }
                }, 90);
            });
        }, 90);
    }

    // One shared page grid for taps, hardware keys and TTS. Returning false means the caller is
    // already at the document edge and should use the existing chapter-navigation bridge.
    runtime.turnPage = function (direction, fraction) {
        if (runtime.pageTurnBusy) return true;
        var height = viewportExtent();
        var current = paginated ? Math.round(position() / height) * height : position();
        var step = Math.max(Math.min(Number(fraction) || 1, 1), 0.01);
        var target = step === 1
            ? (direction > 0 ? Math.floor(current / height + 1) : Math.ceil(current / height - 1)) * height
            : current + (direction > 0 ? 1 : -1) * height * step;
        target = Math.max(0, Math.min(target, Math.max(maxScrollPosition(), 0)));
        if (Math.abs(target - current) < 2) return false;
        changePage(target, true);
        return true;
    };

    runtime.revealPageAt = function (absolutePosition) {
        var height = viewportExtent();
        var current = position();
        var target = Math.max(0, Math.min(Math.floor(absolutePosition / height) * height, Math.max(maxScrollPosition(), 0)));
        if (Math.abs(target - current) < 2) return true;
        changePage(target, true);
        return true;
    };

    var PROGRESS_EVENT = '__PROGRESS_EVENT__';
    // Dispatched page-side (no Kotlin bridge). Constructing + dispatching a CustomEvent with no
    // listeners is cheap, so gating on subscriber count isn't worth the bookkeeping; the 50ms/0.01
    // slider throttle already bounds how often this fires.
    function dispatchProgress(s) {
        try {
            window.dispatchEvent(new CustomEvent(PROGRESS_EVENT, {
                detail: {
                    progress: s.progress,
                    chapterProgress: s.chapterProgress,
                    chapterId: s.chapterId,
                    isLast: s.isLast,
                },
            }));
        } catch (e) {}
    }

    function publishProgress(s) {
        runtime.progress = s.progress;
        runtime.chapterProgress = s.chapterProgress;
        runtime.currentChapterId = s.chapterId;
    }

    var framePending = false;

    function loadNextChapterIfIdle() {
        if (runtime.loadingNext || !infiniteScrollEnabled || runtime.noMoreChapters) return;
        runtime.loadingNext = true;
        try {
            Android.loadNextChapter();
        } catch (e) {
            runtime.loadingNext = false;
        }
    }

    function onFrame() {
        framePending = false;
        var s = computeState();
        publishProgress(s);

        if (infiniteScrollEnabled && s.idx !== runtime.lastChapterIdxSeen && s.chapterId != null) {
            runtime.lastChapterIdxSeen = s.idx;
            Android.onChapterScrollUpdate(String(s.chapterId), s.chapterProgress);
        }

        // Throttle slider bridge (50ms + 0.01 delta); 100% persist exempt so completion isn't dropped.
        if (Math.abs(s.chapterProgress - lastSliderProgress) > 0.01) {
            var now = Date.now();
            if (now - lastScrollUpdateTime > 50) {
                lastScrollUpdateTime = now;
                lastSliderProgress = s.chapterProgress;
                Android.onScrollUpdate(s.chapterProgress);
                dispatchProgress(s);
            }
        }
        // Only the last chapter flashes to 100% and self-persists; a middle chapter momentarily
        // hitting 1.0 as it crosses a divider is marked read Android-side on the chapter switch,
        // so flashing the slider to 100% here would just flicker it for one frame.
        if (s.isLast && s.chapterProgress >= 1.0 && lastSliderProgress !== 1.0) {
            lastSliderProgress = 1.0;
            Android.onScrollUpdate(1.0);
            Android.onScrollProgress(1.0);
            dispatchProgress(s);
        }

        var shouldLoadNext = window.chapterBoundaries.length > 1
            ? s.idx === window.chapterBoundaries.length - 1 && s.chapterProgress >= loadThreshold
            : s.progress >= loadThreshold;
        if (shouldLoadNext) loadNextChapterIfIdle();
    }

    function onScroll() {
        if (framePending) return;
        framePending = true;
        requestAnimationFrame(onFrame);
    }
    window.addEventListener('scroll', onScroll, { passive: true });

    // computeState() is re-read here so a chapter switch mid-scroll can't persist a stale value.
    function persistCurrent(retriesLeft) {
        if (retriesLeft === undefined) retriesLeft = 3;
        if (retriesLeft > 0 && Date.now() - lastBodyResizeAt < SETTLE_MS) {
            setTimeout(function () { persistCurrent(retriesLeft - 1); }, SETTLE_MS);
            return;
        }
        Android.onScrollProgress(computeState().chapterProgress);
    }
    if ('onscrollend' in window) {
        window.addEventListener('scrollend', function () { persistCurrent(); }, { passive: true });
    } else {
        var settleTimer = null;
        window.addEventListener('scroll', function () {
            clearTimeout(settleTimer);
            settleTimer = setTimeout(persistCurrent, 250);
        }, { passive: true });
    }

    // Marks in-flight reflow so persistCurrent can wait it out before saving.
    if (typeof ResizeObserver === 'function' && document.body) {
        var bodyResizeObserver = new ResizeObserver(function () {
            lastBodyResizeAt = Date.now();
            updatePageEndSpacer();
        });
        bodyResizeObserver.observe(document.body);
    }

    window.addChapterBoundary = function (chapterId, startOffset, height) {
        window.chapterBoundaries.push({
            chapterId: chapterId,
            startOffset: startOffset,
            height: height
        });
    };

    // getBoundingClientRect() + scrollY (not offsetTop) so offsets are correct inside a positioned
    // container.
    window.updateChapterBoundaries = function () {
        var dividers = document.querySelectorAll('.__CHAPTER_DIVIDER_CLASS__');
        var scrollY = window.scrollY || window.pageYOffset || 0;
        var boundaries = [];
        dividers.forEach(function (divider, index) {
            var chapterId = divider.getAttribute('__CHAPTER_ID_ATTR__');
            var rect = divider.getBoundingClientRect();
            var startOffset = rect.top + scrollY;
            var nextDivider = dividers[index + 1];
            var endOffset = nextDivider
                ? nextDivider.getBoundingClientRect().top + scrollY
                : document.body.scrollHeight;
            boundaries.push({
                chapterId: chapterId,
                startOffset: startOffset,
                height: endOffset - startOffset
            });
        });
        window.chapterBoundaries = boundaries;
        runtime.knownDividerCount = dividers.length;
        // Boundaries changed, so the load-next answer may have too, and a document that never
        // scrolls fires no scroll event of its own. Coalesced into the same frame as any scroll.
        onScroll();
    };

    // Rebuild boundaries on DOM change (append/prepend/trim) or reflow (image/font load), coalesced
    // to one rebuild per frame.
    if (infiniteScrollEnabled && document.body) {
        var rebuildPending = false;
        function scheduleBoundaryRebuild() {
            if (rebuildPending) return;
            rebuildPending = true;
            requestAnimationFrame(function () {
                rebuildPending = false;
                if (typeof window.updateChapterBoundaries === 'function') window.updateChapterBoundaries();
            });
        }
        if (typeof ResizeObserver === 'function') {
            runtime.boundaryResizeObserver = new ResizeObserver(scheduleBoundaryRebuild);
            runtime.boundaryResizeObserver.observe(document.body);
        }
        if (typeof MutationObserver === 'function') {
            // Coalesce to one rebuild per frame rather than scanning querySelectorAll on every
            // mutation (a chapter insert fires many); updateChapterBoundaries re-reads dividers anyway.
            runtime.boundaryMutationObserver = new MutationObserver(scheduleBoundaryRebuild);
            runtime.boundaryMutationObserver.observe(document.body, { childList: true, subtree: true });
        }
    }

    requestAnimationFrame(function () {
        if (typeof window.updateChapterBoundaries === 'function') window.updateChapterBoundaries();
        updatePageEndSpacer();
        var s0 = computeState();
        publishProgress(s0);
        dispatchProgress(s0);
    });
})();
