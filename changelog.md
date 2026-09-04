# Changelog

## v0.1.0 (pre-release)

First DripMusic release. Fork changes on top of upstream Metrolist v13.6.3:

- Home screen shows only YouTube content; app-internal sections (speed dial,
  quick picks, keep listening, forgotten favorites, daily discover) are disabled
- Home sections are grouped into 19 categories with a new settings screen
  (Settings → Home screen sections): drag-and-drop reordering, hide/restore
  whole categories; the menu on a home section title hides its category too
- Pull-to-refresh now loads the full feed (continuation pages), same as on cold
  start, and the home screen is revealed at once behind a short loading shimmer
  instead of sections popping in one by one
- Removed the toggles for YouTube home sections and endless scrolling
  (both permanently enabled)

## v13.6.3-drip.1

Fork changes on top of upstream Metrolist v13.6.3:

- Performance: widget rendering moved off the main thread; one Room flow per home
  section instead of per-item flows; player expand progress read in the draw phase;
  thumbnail loading resized (player artwork 1080px, ItemThumbnail respects caller sizes)
- Home screen: section toggles decoupled from the YouTube home fetch — the speed dial
  stays populated when YouTube sections are hidden; the master toggle now hides all
  YouTube recommendation sections (home rows, account mixes, similar-to, community
  playlists, moods & genres) including their network requests
- Build: release APKs are signed directly by Gradle when keystore environment
  variables are set
- Repo: rebrand to DripMusic (README, fastlane en-US/de-DE)

Upstream Metrolist history remains available in the git history of this repository.
