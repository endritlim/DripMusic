# Changelog

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
