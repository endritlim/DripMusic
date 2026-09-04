# Home Jank Fixes — Verification Results (2026-09-04)

## Changes verified

All changes were verified on a physical device (Samsung, Android targetSdk 36) by manual testing.

### Plan 3 fixes

| Change | Commit | Result |
|---|---|---|
| Revert home page caching | `b34e484fc` | Home loads normally, no stale content |
| Widget rendering off main thread (1 s interval, early-out, mutex) | `a7ace7f6` | No more main-thread widget work during scrolling |
| Per-item DB flows → one Room flow per section (Quick Picks, Forgotten Favorites) | `61361fc2b` | Like status still updates live in both sections |
| Player expand progress read in draw phase (`graphicsLayer`) | `39334b7ca` | Mini-player expand animation smooth |
| Player artwork resized to 1080px; `ItemThumbnail` respects caller sizes | `08806f338` | No visible cover quality loss |

### Build findings

- **Root cause of perceived home scroll jank: debug builds.** The reference app Meld
  (`com.meld.app` v0.8.8) is a release build (`flags=0x0`); all local test builds were
  `fossDebug` (`isDebuggable = true`, no R8). Code diff of the home scroll path
  (HomeScreen, Items, image loading, dependency versions — ours are all newer) showed
  no structural difference that explains the jank. A/B test on the same device:
  the signed release build scrolls noticeably smoother than the debug build.
- Gradle now signs release APKs when `STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env
  vars are set (`682028429`), producing `app-foss-release.apk` directly:
  `STORE_PASSWORD=android KEY_ALIAS=androiddebugkey KEY_PASSWORD=android ./gradlew :app:assembleFossRelease`
  (unsigned output preserved when env vars are absent, e.g. upstream CI).

### Home section toggle decoupling

- `a38d8f1fe`: The speed dial is built from home page items, so hiding YouTube home
  sections also emptied the speed dial. `YouTube.home()` is now fetched when either
  the YouTube sections **or** the speed dial need it. Chips row is hidden with the
  toggle; a stale chip selection can no longer blank the page.
- `59ee02d38`: The toggle now consistently hides **all** YouTube recommendation
  content (home rows, account mixes, similar-to, community playlists, moods & genres)
  and skips the corresponding network fetches. Setting description updated.

### Expected empty sections

Daily Discover requires at least one liked song; Forgotten Favorites requires play
history spanning ~30 days (heavily played before, rarely now). Both are empty on a
fresh install by design.
