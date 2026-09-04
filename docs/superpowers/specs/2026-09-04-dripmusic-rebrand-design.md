# DripMusic Rebrand — Design (2026-09-04)

## Goal
Rebrand the repository from Metrolist to DripMusic (personal fork). App identity
(name `DripMusic`, applicationId `com.dripmusic.app`) is already in place; this
covers the remaining repo-facing branding.

## Scope (approved)

1. **README.md** (new, English): title + "performance-focused fork of Metrolist";
   sections: short description, fork differences (performance fixes, home section
   toggles incl. speed dial, Gradle release signing), build instructions (debug +
   release with env vars), credits & license (GPL-3.0, based on Metrolist by
   MetrolistGroup), disclaimer (not affiliated with YouTube/Google, regional
   restriction note). Remove: upstream badges, Discord/Telegram, maintenance-mode
   warning, Metrolist screenshots, donation links.
2. **fastlane/metadata**: en-US `title.txt` → "DripMusic", new short/full
   descriptions (fork note, features, credit); de-DE short/full descriptions
   translated accordingly. Other 26 locales untouched (follow-up).
3. **changelog.md**: replaced with a DripMusic changelog; first entry
   `v13.6.3-drip.1` summarizing fork changes. Upstream history stays in git.
4. **AGENTS.md**: relax the "no markdown edits" rule for fork-owned docs (README,
   changelog, docs/, fastlane); keep upstream core rules (strings rule, no schema
   change, no version bump).
5. **Workflows**: unchanged — they use `${{ github.repository }}` and already run
   against the fork. fastlane images kept until own icon/screenshots exist.

## Commits
1. `docs: rebrand readme, changelog and fastlane metadata for DripMusic`
2. `docs: allow edits to fork-owned markdown in AGENTS.md`
