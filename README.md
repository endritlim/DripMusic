<div align="center">

<img src="assets/logo.png" alt="DripMusic logo" width="160" />

# DripMusic

### Material 3 YouTube Music client for Android — a performance-focused fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist)

</div>

DripMusic is a personal fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist),
a third-party YouTube Music client. It keeps everything that makes Metrolist great and
puts extra work into the things that matter day-to-day: smooth scrolling, fast loading
and a home screen you can actually shape to your taste.

## What's different from Metrolist

- **Performance fixes** — home screen jank fixes: widget rendering moved off the main
  thread, one Room flow per home section instead of one per item, player expand
  animation reads its progress in the draw phase, thumbnail loading resized.
- **Configurable home screen** — toggles for every home section, including a
  dedicated **speed dial** grid, plus a master toggle that hides all YouTube
  recommendation sections (home rows, mixes, similar-to, moods & genres) including
  their network requests.
- **Easy release builds** — release APKs are signed directly by Gradle when keystore
  environment variables are set (see below), no manual signing step.

## Build

Debug build (for development):

```bash
./gradlew :app:assembleFossDebug
```

Release build (optimized, installable next to the debug build):

```bash
STORE_PASSWORD=<store password> KEY_ALIAS=<key alias> KEY_PASSWORD=<key password> \
  ./gradlew :app:assembleFossRelease
```

The release signing config expects a keystore at `app/keystore/release.keystore`
(`*.keystore` is gitignored). Without the environment variables, the build produces
an unsigned APK as usual.

## Credits & License

- Based on [Metrolist](https://github.com/MetrolistGroup/Metrolist) by
  [MetrolistGroup](https://github.com/MetrolistGroup) — all credit for the app goes
  to them and the upstream contributors.
- Licensed under [GPL-3.0](LICENSE), same as upstream.

## Disclaimer

This project is not affiliated with YouTube or Google. If YouTube Music is
unavailable in your region, the app will not work without a VPN or proxy connecting
to a supported region.
