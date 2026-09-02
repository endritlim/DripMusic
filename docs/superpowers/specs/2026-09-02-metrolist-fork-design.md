# Design: Eigener YT-Music-Fork auf Metrolist-Basis

Datum: 2026-09-02
Status: Vom Nutzer abgenommen (Brainstorming abgeschlossen)

## Kontext

Der Nutzer will einen eigenen Android-YouTube-Music-Client. Zwei Kandidaten wurden lokal analysiert:

- **Metrolist** (lokaler Checkout, inzwischen der Fork **DripMusic** unter `/home/endrit/Dokumente/DripMusic`, Branch `drip`): aktiv gepflegt (~30 Commits/5 Tage), aber offiziell im Maintenance Mode, da ein Kotlin-Multiplatform-Rewrite (KMP) angekündigt ist (changelog.md v13.6.1/v13.6.2, keine feste Timeline).
- **Meld** (`/home/endrit/Dokumente/Meld`, nur Referenz): Fork von Metrolist mit Spotify-Integration als Hauptfeature, dazu Qobuz, SponsorBlock, Musixmatch-Lyrics, New Releases, Wrapped. Hinkt Upstream hunderten Commits hinterher, wird nur dünn gepflegt (Ein-Mann-Projekt, eigene AGENTS.md dokumentiert manuelle Rebase-Schmerzen). Kann nach Abschluss von AP4/AP5 gelöscht werden.

Entscheidung (mit Nutzer abgestimmt): **Fork von Metrolist**, gewünschte Meld-Features nachportieren. Gewünscht: Musixmatch, New Releases, später optional Wrapped. Nicht gewünscht: Spotify, Qobuz, SponsorBlock. KMP-Migration wird offengehalten; Änderungen werden daher klein und portierbar gehalten.

## Scope

**Rein:**
1. Performance-Fixes für Startseite (Home) und Suche
2. Musixmatch-Lyrics-Provider (aus Meld portiert)
3. New-Releases-Screen (aus Meld portiert, nur YouTube-Pfad)
4. Später optional: Wrapped

**Raus (explizit nicht im Scope):** Spotify-Integration, Qobuz, SponsorBlock.

## Setup (Arbeitspaket 1)

- GitHub-Fork von Metrolist erstellen, lokal klonen
- Android Studio + JDK 21; Build-Verifikation: `./gradlew :app:assembleFossDebug`
- Eigene `applicationId` + App-Name, damit der Fork parallel zum Original installierbar ist
- Erst nach erfolgreichem Build + App-Start auf Gerät geht es weiter

## Arbeitspakete (Reihenfolge = aufsteigende Komplexität)

### AP2: Performance Home

Befunde aus der Code-Analyse (Metrolist), nach Verifikation:

- Home feuert ~20+ Requests pro Load ab, ohne Response-Caching; jeder `refresh()` (auch bei Netz-Reconnect, `HomeViewModel.kt:727`) wiederholt alles. Fix: In-Memory-Cache für die Home-Page mit 30-Min-TTL (`TimedCache` + `HomePageCache`), `refresh()` invalidiert. Grenze: hilft nicht beim Kaltstart (Cache stirbt mit dem Prozess) — ein persistenter Home-Cache erfordert `HomePage` serialisierbar zu machen (`:innertube`), das ist ein eigenes Follow-up.
- `HomeViewModel.getQuickPicks()` macht pro Song eine einzelne DB-Query (`:339`, N+1) → Batch via vorhandenem `DatabaseDao.getSongsByIds()` (`DatabaseDao.kt:702`).
- ~~`InnerTube.kt:188` `Cache-Control: no-cache` sabotiert den OkHttp-Cache~~ → verworfen: Die YouTube-API-Calls sind POST-Requests, die OkHttp ohnehin nie cached; die Header-Änderung hätte keinen Effekt.
- ~~`delay(1000)` in `getRandomItem()` entfernen~~ → verworfen: Das ist die bewusste Shuffle-Button-Animation (nutzerinitiiert), kein Ladezeit-Problem.

### AP3: Performance Suche

- `OnlineSearchScreen.kt:121` debounced zwar (300 ms), aber jede Query-Änderung löst einen Netz-Request aus; kein Suggestion-Caching. Fix: Query→Suggestions-Cache mit kurzer TTL im `OnlineSearchSuggestionViewModel`.

### AP4: Musixmatch-Provider portieren

- Quelle: `Meld/app/src/main/kotlin/.../lyrics/MusixmatchLyricsProvider.kt`
- Ziel: neues Objekt im `lyrics/`-Package von Metrolist (Interface `LyricsProvider`, `lyrics/LyricsProvider.kt`), Eintrag in `providerMap` in `LyricsProviderRegistry.kt` (+ ggf. Default-Reihenfolge)
- Lizenz: beide Projekte GPL → Portierung unproblematisch
- Dient als Blaupause für weitere Lyrics-Provider des Nutzers

### AP5: New Releases portieren

- Quelle: Melds `ui/screens/NewReleaseScreen.kt` + `NewReleaseViewModel.kt`
- Nur der YouTube-Pfad wird übernommen; alle Spotify-Anteile entfallen

## Upstream-Strategie

- Eigene Änderungen in wenigen, klar abgegrenzten Commits, möglichst in separaten Dateien/Blöcken
- Regelmäßig `git merge upstream/main` (Metrolist ist aktiv)
- Portierbarkeit für ein späteres KMP-Rewrite im Blick behalten: Features modular, keine Streuänderungen über den Core

## Testen

- Vorher/Nachher-Messung der Ladezeiten (Home, Suche) auf echtem Gerät — kein Performance-Claim ohne Messung
- Unit-Tests nur wo neue Logik entsteht (z. B. Cache-TTL); Rest manuell auf Gerät
- Bestehender Build (foss-Flavor) muss nach jedem AP grün bleiben

## Offene Punkte

- Name: **DripMusic** (festgelegt); Icon: simpel mit Musiknote (Entwurf durch Nutzer, folgt in AP1)
- Exakter Zeitpunkt Wrapped-Portierung (niedrige Priorität)
- KMP-Umstieg: wird entschieden, sobald Metrolist das Rewrite tatsächlich veröffentlicht
