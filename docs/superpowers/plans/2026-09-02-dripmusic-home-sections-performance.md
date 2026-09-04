# DripMusic Plan 2: Home-Sections abschaltbar + Lag-Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vier Home-Sections per Setting ein-/ausblendbar machen (Schnellwahl, Noch mal anhören, Vergessene Favoriten, Daily Discover) und die identifizierten Lag-Ursachen auf der Startseite beheben.

**Architecture:** Toggles über DataStore-Preference-Keys (Default `true` = sichtbar), Guards in `HomeScreen.homeSections` + Loader-Skip in `HomeViewModel.load()`. Lag-Fixes: Speed-Dial-Pin-Flows bündeln (vorhandene `pinnedSpeedDialItems`-Liste statt 27 Einzel-Flows), `distinctBy` aus der Composition in `remember` hoisten, Daily-Discover-Thumbnails von 1080px auf 512px reduzieren, `contentType` in den Lazy-Listen ergänzen.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, Room.

**Regeln (aus `AGENTS.md`):**
- Commit-Format: `type(scope): short description`
- Strings NUR in `app/src/main/res/values/metrolist_strings.xml` (default Englisch), keine anderen Sprachdateien
- Keine DB-Schema-Änderungen, keine Version-Bumps, keine Upstream-Markdown-Dateien
- Build-Verifikation: `./gradlew :app:assembleFossDebug` aus `/home/endrit/Dokumente/DripMusic`
- Unit-Tests: `./gradlew :app:testFossDebugUnitTest`
- Branch: `drip` (vor jedem Task prüfen: `git branch --show-current`)

**Bewusst NICHT in diesem Plan (Scope-Grenzen):**
- `isPlaying`/`mediaMetadata` werden auf Top-Ebene des HomeScreen gelesen und in alle Item-Composables gereicht → jeder Play/Pause-Wechsel recomposet den Screen. Fix = State-Reads in die Item-Composables ziehen, ~15 Call-Sites, hohes Regressionsrisiko. Eigener Folgeplan nach Geräte-Test von Plan 2.
- Persistenter Home-Cache (Kaltstart) — siehe Plan-1-Fazit.
- Quick-Picks-Section bekommt bewusst keinen Toggle (Nutzer-Entscheidung).

---

### Task 1: Preference-Keys + Strings

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt` (nach Zeile 255, `RandomizeHomeOrderKey`)
- Modify: `app/src/main/res/values/metrolist_strings.xml` (nach Zeile 827, `randomize_home_order_desc`)

- [ ] **Step 1: Keys anlegen**

In `PreferenceKeys.kt` direkt nach `val RandomizeHomeOrderKey = booleanPreferencesKey("randomizeHomeOrder")`:

```kotlin
val ShowSpeedDialSectionKey = booleanPreferencesKey("show_speed_dial_section")
val ShowKeepListeningKey = booleanPreferencesKey("show_keep_listening")
val ShowForgottenFavoritesKey = booleanPreferencesKey("show_forgotten_favorites")
val ShowDailyDiscoverKey = booleanPreferencesKey("show_daily_discover")
```

- [ ] **Step 2: Strings anlegen**

In `metrolist_strings.xml` direkt nach der `randomize_home_order_desc`-Zeile:

```xml
    <string name="home_screen_sections">Home screen sections</string>
    <string name="show_speed_dial_section">Speed dial</string>
    <string name="show_speed_dial_section_desc">Show the speed dial grid on the home screen</string>
    <string name="show_keep_listening">Keep listening</string>
    <string name="show_keep_listening_desc">Show recently played songs, albums and artists on the home screen</string>
    <string name="show_forgotten_favorites">Forgotten favorites</string>
    <string name="show_forgotten_favorites_desc">Show songs you liked but haven\'t played in a while</string>
    <string name="show_daily_discover">Daily discover</string>
    <string name="show_daily_discover_desc">Show daily recommendations based on your liked songs</string>
```

(Achtung: Apostroph in `haven\'t` muss escaped sein, wie im Rest der Datei üblich.)

- [ ] **Step 3: Build**

```bash
cd /home/endrit/Dokumente/DripMusic && ./gradlew :app:assembleFossDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/metrolist/music/constants/PreferenceKeys.kt app/src/main/res/values/metrolist_strings.xml
git commit -m "feat(settings): add preference keys for home screen section visibility"
```

---

### Task 2: Settings-UI (Schalter in ContentSettings)

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/ContentSettings.kt`

- [ ] **Step 1: Preferences lesen**

Nach dem `randomizeHomeOrder`-Block (Zeilen 139-142) ergänzen:

```kotlin
    val (showSpeedDialSection, onShowSpeedDialSectionChange) = rememberPreference(ShowSpeedDialSectionKey, defaultValue = true)
    val (showKeepListening, onShowKeepListeningChange) = rememberPreference(ShowKeepListeningKey, defaultValue = true)
    val (showForgottenFavorites, onShowForgottenFavoritesChange) = rememberPreference(ShowForgottenFavoritesKey, defaultValue = true)
    val (showDailyDiscover, onShowDailyDiscoverChange) = rememberPreference(ShowDailyDiscoverKey, defaultValue = true)
```

Imports für die vier Keys ergänzen (alphabetisch bei den anderen `com.metrolist.music.constants.*`-Imports).

- [ ] **Step 2: Settings-Gruppe hinzufügen**

Vor der bestehenden „misc"-Gruppe (ca. Zeile 1036, `Material3SettingsGroup(title = stringResource(R.string.misc), ...`) eine neue Gruppe einfügen. Muster exakt wie der `randomize_home_order`-Eintrag (Zeilen 1039-1055): `Material3SettingsItem` mit `icon`, `title`, `description`, `trailingContent = { Switch(checked, onCheckedChange, thumbContent mit check/close-Icon) }`. Für die Icons vorhandene Drawables wiederverwenden (im Projekt suchen, z. B. `R.drawable.home`, `R.drawable.play_arrow` o.ä. — Implementer wählt passende existierende):

```kotlin
        Material3SettingsGroup(
            title = stringResource(R.string.home_screen_sections),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.home),
                    title = { Text(stringResource(R.string.show_speed_dial_section)) },
                    description = { Text(stringResource(R.string.show_speed_dial_section_desc)) },
                    trailingContent = {
                        Switch(
                            checked = showSpeedDialSection,
                            onCheckedChange = onShowSpeedDialSectionChange,
                        )
                    },
                ),
                // ... analog für show_keep_listening, show_forgotten_favorites, show_daily_discover
            ),
        )
```

(Das `thumbContent`-Icon aus dem Muster ist optional; ohne ist es genauso konsistent, wenn andere Switch-Einträge es nicht haben — Implementer prüft das Muster der Nachbareinträge und folgt dem häufigeren Stil.)

- [ ] **Step 3: Build + Commit**

```bash
cd /home/endrit/Dokumente/DripMusic && ./gradlew :app:assembleFossDebug
git add app/src/main/kotlin/com/metrolist/music/ui/screens/settings/ContentSettings.kt
git commit -m "feat(settings): add home screen section visibility toggles"
```

---

### Task 3: Guards in HomeScreen + HomeViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt` (Prefs lesen ~Zeile 695, `homeSections`-Guards ~Zeilen 1045-1051)
- Modify: `app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt` (`load()` ~Zeilen 441-580)

- [ ] **Step 1: Prefs im HomeScreen lesen**

Nach `val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, true)` (Zeile 695):

```kotlin
    val (showSpeedDialSection) = rememberPreference(ShowSpeedDialSectionKey, true)
    val (showKeepListening) = rememberPreference(ShowKeepListeningKey, true)
    val (showForgottenFavorites) = rememberPreference(ShowForgottenFavoritesKey, true)
    val (showDailyDiscover) = rememberPreference(ShowDailyDiscoverKey, true)
```

Imports ergänzen.

- [ ] **Step 2: Guards in `homeSections`**

Die vier Prefs in die `remember(...)`-Key-Liste aufnehmen (Zeilen 1027-1041), dann die Guards (Zeilen 1045-1051) ändern:

```kotlin
            if (!chipActive && showSpeedDialSection && speedDialItems.isNotEmpty()) list.add(HomeSection.SpeedDial)
            if (!chipActive && quickPicks?.isNotEmpty() == true) list.add(HomeSection.QuickPicks)
            if (!chipActive && communityPlaylists?.isNotEmpty() == true) list.add(HomeSection.FromTheCommunity)
            if (!chipActive && showDailyDiscover && dailyDiscover?.isNotEmpty() == true) list.add(HomeSection.DailyDiscover)
            if (!chipActive && showKeepListening && keepListening?.isNotEmpty() == true) list.add(HomeSection.KeepListening)
            if (!chipActive && accountPlaylists?.isNotEmpty() == true) list.add(HomeSection.AccountPlaylists)
            if (!chipActive && showForgottenFavorites && forgottenFavorites?.isNotEmpty() == true) list.add(HomeSection.ForgottenFavorites)
```

- [ ] **Step 3: Loader im ViewModel skippen**

In `HomeViewModel.load()` am Anfang (nach dem Lesen der Hide-Flags, ~Zeile 443-446) ergänzen:

```kotlin
        val showKeepListening = context.dataStore.get(ShowKeepListeningKey, true)
        val showForgottenFavorites = context.dataStore.get(ShowForgottenFavoritesKey, true)
        val showDailyDiscover = context.dataStore.get(ShowDailyDiscoverKey, true)
```

Dann:
- Den `launch(Dispatchers.IO) { forgottenFavorites.value = ... }`-Block (~Zeilen 453-456) mit `if (showForgottenFavorites)` umgeben.
- Den `launch(Dispatchers.IO) { ... keepListening.value = ... }`-Block (~Zeilen 458-466) mit `if (showKeepListening)` umgeben.
- Phase 2: `viewModelScope.launch(Dispatchers.IO) { getDailyDiscover() }` (~Zeile 499) → `if (showDailyDiscover) viewModelScope.launch(Dispatchers.IO) { getDailyDiscover() }`.

Hinweis: Speed Dial hat keinen Netzwerk-Loader (combine über DB + bereits geladene Daten) → nur UI-Guard nötig. Bekannter Nebeneffekt (akzeptiert): Versteckte Sections liefern auch keine Daten mehr an den Shuffle-FAB (`getRandomItem`), weil die Loader nicht laufen.

- [ ] **Step 4: Build + manueller Check + Commit**

```bash
cd /home/endrit/Dokumente/DripMusic && ./gradlew :app:assembleFossDebug
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Manuell (Controller/User): Settings → Content → „Home screen sections": alle vier Toggles aus → Home zeigt keine der vier Sections; wieder an → Sections sind zurück (nach Refresh).

```bash
git add app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt
git commit -m "feat(home): make speed dial, keep listening, forgotten favorites and daily discover sections toggleable"
```

---

### Task 4: Speed-Dial-Pin-Flows bündeln

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt` (~Zeile 676, ~Zeilen 1581-1586)

- [ ] **Step 1: Pinned-IDs-Set aus bereits collected Liste**

Nach `val pinnedSpeedDialItems by viewModel.pinnedSpeedDialItems.collectAsStateWithLifecycle()` (Zeile 676):

```kotlin
    val pinnedSpeedDialIds = remember(pinnedSpeedDialItems) { pinnedSpeedDialItems.mapTo(HashSet()) { it.id } }
```

- [ ] **Step 2: Per-Item-DB-Flow ersetzen**

Im Speed-Dial-Rendering (aktuell Zeilen 1581-1586) — bisher pro Item ein eigener Room-Flow:

```kotlin
                                                            } else if (itemIndex < pageItems.size) {
                                                                val item = pageItems[itemIndex]
                                                                val isPinned by database.speedDialDao
                                                                    .isPinned(
                                                                        item.id,
                                                                    ).collectAsStateWithLifecycle(initialValue = false)
```

wird zu:

```kotlin
                                                            } else if (itemIndex < pageItems.size) {
                                                                val item = pageItems[itemIndex]
                                                                val isPinned = item.id in pinnedSpeedDialIds
```

Damit fallen bis zu 27 parallele DB-Flows weg; die Daten kommen aus der bereits vorhandenen `pinnedSpeedDialItems`-Liste.

- [ ] **Step 3: Build + Commit**

```bash
cd /home/endrit/Dokumente/DripMusic && ./gradlew :app:assembleFossDebug
git add app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt
git commit -m "perf(home): reuse pinned speed dial list instead of per-item DB flows"
```

---

### Task 5: distinctBy hoisten + Daily-Discover-Thumbnails

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt` (~14 Stellen, Zeilenangaben aus der Analyse — nach vorherigen Tasks verschoben, per Suche finden)

- [ ] **Step 1: `distinctBy` aus der Composition hoisten**

Muster an allen Stellen, wo in Lazy-Listen `.distinctBy { it.id }` direkt in der Composition aufgerufen wird (Fundstellen aus der Analyse, ca. Zeilen 1245, 1270, 1293, 1375, 1785, 1810, 2025, 2081, 2103, 2133, 2260, 2367, 2437, 2470 — verifizieren per Suche nach `distinctBy` in HomeScreen.kt):

Vorher (Beispiel):

```kotlin
items(
    items = quickPicks.distinctBy { it.id },
    ...
)
```

Nachher:

```kotlin
val distinctQuickPicks = remember(quickPicks) { quickPicks.distinctBy { it.id } }
items(
    items = distinctQuickPicks,
    ...
)
```

Regeln: Die `remember`-Variable direkt vor dem jeweiligen Section-Block definieren, sinnvoll benennen (`distinctX`). Listen, die sowieso schon aus einem `remember` kommen oder statisch sind, nicht anfassen. Pro Stelle eine Zeile `remember` + ersetzter Aufruf.

- [ ] **Step 2: Daily-Discover-Thumbnail skalieren**

In `HomeScreen.kt` (ca. Zeile 551, `DailyDiscoverCard`):

```kotlin
.data(dailyDiscover.recommendation.thumbnail?.resize(1080, 1080))
```

→

```kotlin
.data(dailyDiscover.recommendation.thumbnail?.resize(512, 512))
```

(Die Karte ist max. bildschirmbreit; 512px reichen für die Darstellung und halbieren die Dekodierkosten grob. Zusätzlich prüfen: Section-Titel-Thumbnails ohne `resize` (Analyse-Fund: ca. Zeilen 1335, 2223, 2299) — dort `?.resize(200, 200)` ergänzen, da es kleine Titel-Icons sind.)

- [ ] **Step 3: Build + Commit**

```bash
cd /home/endrit/Dokumente/DripMusic && ./gradlew :app:assembleFossDebug
git add app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt
git commit -m "perf(home): hoist distinctBy out of composition, shrink daily discover thumbnails"
```

---

### Task 6: contentType in den Lazy-Listen der Home-Sections

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt` (LazyColumn-`item`-Blöcke der `homeSections.forEach`, ca. ab Zeile 1447)

- [ ] **Step 1: contentType ergänzen**

In der LazyColumn, die `homeSections.forEach` rendert: pro Section gibt es typischerweise ein `item(key = "...title...")` für den Titel und ein `item(key = ...)` für die Liste/das Grid. Beiden `contentType` geben:

```kotlin
item(key = "speedDialTitle", contentType = "section_title") { ... }
item(key = "speedDial", contentType = "speed_dial") { ... }
```

Regel: Titel-Items bekommen überall `contentType = "section_title"`, Inhalts-Items einen stabilen Typ pro Section-Art (z. B. `"section_grid"`, `"speed_dial"`, `"mood_genres"`). Innerhalb von LazyRow/LazyHorizontalGrid der Sections, falls dort `items(...)` mit gemischten Inhalten gerendert wird, ebenfalls `contentType` setzen. Bestehende `key`-Parameter unverändert lassen.

- [ ] **Step 2: Build + Commit**

```bash
cd /home/endrit/Dokumente/DripMusic && ./gradlew :app:assembleFossDebug
git add app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt
git commit -m "perf(home): add contentType to home lazy lists for better item reuse"
```

---

### Task 7: Verifikation auf Gerät

**Files:**
- Create: `docs/benchmarks/2026-09-02-home-lag.md` (kurzer Erfahrungsbericht, kein Messprotokoll nötig)

- [ ] **Step 1: Install + manueller Test**

```bash
cd /home/endrit/Dokumente/DripMusic
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Checkliste am Gerät:
1. Home komplett laden lassen, Musik abspielen, durch alle Sections scrollen → subjektiv flüssiger?
2. Alle vier Section-Toggles aus → Home zeigt die Sections nicht mehr; wieder an → zurück.
3. Speed Dial: Item pinnen/unpinnen (Long-Press) → Pin-Status aktualisiert sich korrekt (Proof für Task 4).
4. Daily-Discover-Karte sieht normal aus (kein sichtbarer Qualitätsverlust durch 512px).

- [ ] **Step 2: Kurzbericht + Commit + Push**

`docs/benchmarks/2026-09-02-home-lag.md` mit 3-5 Sätzen Ergebnis (was besser ist, was noch laggt), dann:

```bash
cd /home/endrit/Dokumente/DripMusic
git add docs/benchmarks/2026-09-02-home-lag.md
git commit -m "docs: home lag verification results"
git push origin drip
```
