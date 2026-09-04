# DripMusic Plan 3: Jank-Fixes (Widget, Home-Flows, Player) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Scroll-Ruckeln auf der Startseite und die choppy Player-Expand-Animation beheben; dabei das Home-Page-Caching aus Plan 1 wieder entfernen (Nutzerwunsch).

**Architecture:** Größter Hebel zuerst: Widget-Rendering vom Main-Thread holen (Portierung des Meld-Ansatzes: Early-Out ohne installierte Widgets, `Dispatchers.Default`, 1s-Intervall, Mutex). Dann: Per-Item-DB-Flows in Quick Picks / Forgotten Favorites durch je einen gebündelten Flow ersetzen, `state.progress`-Read im Player in die Draw-Phase verlagern, Thumbnail-Resizes korrigieren.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Coroutines.

**Regeln:** Commit-Format `type(scope): desc`; Strings nur `app/src/main/res/values/metrolist_strings.xml`; KEINE DB-Schema-Änderungen (neue DAO-Queries sind erlaubt); keine Version-Bumps; Build `./gradlew :app:assembleFossDebug` aus `/home/endrit/Dokumente/DripMusic`; Branch `drip`.

**Referenz für den Widget-Fix:** Melds Implementierung unter `/home/endrit/Dokumente/Meld/app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt:4437-4521` und `widget/MetrolistWidgetManager.kt:48-99` (Meld ist nur Lese-Referenz, niemals ändern).

**Bewusst NICHT in diesem Plan:**
- Blur-Hintergrund-Umbau im Player (groß; erst nach Messung, ob Tasks 2-4 reichen)
- Badge-Lookups in `Items.kt` (`YouTubeListItem`/`YouTubeGridItem`: 2 Queries + Download-Flow pro Item — genutzt von vielen Screens, eigener Plan)
- Pre-Composition des Player-Contents in `BottomSheet.kt` (Architektur-Trade-off, erst evaluieren)

---

### Task 1: Home-Page-Caching entfernen (Revert)

**Files:**
- Delete: `app/src/main/kotlin/com/metrolist/music/viewmodels/HomePageCache.kt`
- Delete: `app/src/main/kotlin/com/metrolist/music/utils/TimedCache.kt` und `app/src/test/kotlin/com/metrolist/music/utils/TimedCacheTest.kt` (TimedCache wird nach dem Revert nicht mehr genutzt — der Suggestion-Cache nutzt eine eigene LinkedHashMap)
- Modify: `app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt`

- [ ] **Step 1: HomeViewModel zurückbauen**

Referenz-Commits zum Gegenlesen: `git show 2cb5f0eb8` (Cache-Einführung) und `git show 1c97c7e96` (Account-Invalidate).

In `HomeViewModel.kt`:
1. In `load()`: den gecachten Pfad zurück auf den direkten Fetch — aus
   ```kotlin
   val cachedPage = HomePageCache.get()
   val result = cachedPage?.let { Result.success(it) } ?: YouTube.home()
   result.onSuccess { page ->
       if (cachedPage == null) HomePageCache.put(page)
   ```
   wird wieder `YouTube.home().onSuccess { page ->` (der Filter-Block darunter bleibt unverändert). Achtung: der Launch ist seit Plan 2 mit `if (showYouTubeHomeSections)` gegatet — das Gating BLEIBT.
2. In `refresh()`: die Zeile `HomePageCache.invalidate()` entfernen (else-Zweig ruft nur noch `load()`).
3. Im Cookie/Account-Collector im `init`: die Zeile `HomePageCache.invalidate()` samt ihrem Kommentar entfernen.

- [ ] **Step 2: Dateien löschen**

```bash
cd /home/endrit/Dokumente/DripMusic
git rm app/src/main/kotlin/com/metrolist/music/viewmodels/HomePageCache.kt \
       app/src/main/kotlin/com/metrolist/music/utils/TimedCache.kt \
       app/src/test/kotlin/com/metrolist/music/utils/TimedCacheTest.kt
```

- [ ] **Step 3: Prüfen, dass nichts mehr referenziert + Build + Tests**

```bash
grep -rn "HomePageCache\|TimedCache" app/src/ || echo "keine Referenzen"
./gradlew :app:assembleFossDebug
./gradlew :app:testFossDebugUnitTest
```

Expected: keine Referenzen, Build + gesamte Test-Suite grün.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "revert(home): remove home page caching (didn't want it)"
```

---

### Task 2: Widget-Rendering vom Main-Thread holen (Meld-Port)

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt` (`updateWidgetUI` ~Zeile 4545-4583, `startWidgetUpdates` ~Zeile 4587-4598)
- Modify: `app/src/main/kotlin/com/metrolist/music/widget/MetrolistWidgetManager.kt` (`updateWidgets` ~Zeile 52-110)

- [ ] **Step 1: Intervall 200ms → 1000ms**

In `MusicService.startWidgetUpdates()`: `delay(200)` → `delay(1_000)`. Kommentar aus Meld sinngemäß übernehmen: das Widget zeigt einen groben Fortschrittsbalken, 1s ist ununterscheidbar von 200ms — spart fünf AppWidgetManager-Binder-Roundtrips pro Sekunde.

- [ ] **Step 2: Rendering auf Hintergrund-Thread**

In `MusicService.updateWidgetUI(...)`: das `scope.launch { ... }` → `scope.launch(Dispatchers.Default) { ... }`. Der bestehende Pending/Coalescing-Mechanismus (`widgetUpdateInFlight`/`pendingWidgetUpdate`) bleibt — er macht das, was Melds Channel.CONFLATED macht, und ist damit die kleinere Änderung. HINWEIS: `widgetUpdateInFlight`/`pendingWidgetUpdate` werden damit potenziell von mehreren Threads angefasst — Implementer prüft, ob die Flags Atomics/volatile brauchen (Meld umgeht das mit dem Channel; bei uns reicht wahrscheinlich `@Volatile`, da nur grob koalesziert wird — mit einem Satz Kommentar dokumentieren).

- [ ] **Step 3: Early-Out + Mutex im Widget-Manager**

In `MetrolistWidgetManager.updateWidgets(...)`:
1. Die Widget-ID-Abfragen (beide `getAppWidgetIds`, aktuell Zeilen 80-81 und 100-101) an den ANFANG der Funktion ziehen — vor das Album-Art-Laden. Danach:
   ```kotlin
   // Nothing on the home screen — skip artwork decoding and binder traffic.
   // The refresh loop runs for the whole playback session, so this is the common case.
   if (widgetIds.isEmpty() && turntableWidgetIds.isEmpty()) return
   ```
2. Da `updateWidgets` jetzt off-main aus mehreren Quellen (Loop + Player-Events) laufen kann und die Bitmap-Caches (`cachedArtworkUri` etc.) mutiert: `private val renderMutex = Mutex()` ergänzen und den Funktionsrumpf in `renderMutex.withLock { ... }` wrappen (Muster: Melds `MetrolistWidgetManager.kt:53,77`). Import `kotlinx.coroutines.sync.Mutex` / `withLock`.

- [ ] **Step 4: Build + Tests**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug && ./gradlew :app:testFossDebugUnitTest
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt app/src/main/kotlin/com/metrolist/music/widget/MetrolistWidgetManager.kt
git commit -m "perf(widget): render widgets off main thread, 1s interval, early-out without widgets"
```

---

### Task 3: Per-Item-DB-Flows in Home bündeln (Quick Picks + Forgotten Favorites)

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt` (neue Flow-Query, KEIN Schema-Change)
- Modify: `app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt` (~Zeilen 1845-1852 QuickPicks, ~2170-2178 ForgottenFavorites — per Suche nach `database.song(` finden)

- [ ] **Step 1: DAO-Flow-Query**

In `DatabaseDao.kt` direkt nach `getSongsByIds` (~Zeile 702-704):

```kotlin
    @Transaction
    @Query("SELECT * FROM song WHERE id IN (:songIds)")
    fun songsByIdsFlow(songIds: List<String>): Flow<List<Song>>
```

- [ ] **Step 2: Live-Listen im ViewModel**

In `HomeViewModel.kt` nach den bisherigen `quickPicks`-/`forgottenFavorites`-Deklarationen:

```kotlin
    // Live variants that stay in sync with the song table (likes etc.) with one
    // Room flow per section instead of one flow per visible item.
    @OptIn(ExperimentalCoroutinesApi::class)
    val quickPicksLive: StateFlow<List<Song>?> =
        quickPicks.flatMapLatest { list ->
            if (list == null) {
                flowOf(null)
            } else {
                database.songsByIdsFlow(list.map { it.id }).map { songs ->
                    val byId = songs.associateBy { it.id }
                    list.mapNotNull { byId[it.id] }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val forgottenFavoritesLive: StateFlow<List<Song>?> =
        forgottenFavorites.flatMapLatest { list ->
            if (list == null) {
                flowOf(null)
            } else {
                database.songsByIdsFlow(list.map { it.id }).map { songs ->
                    val byId = songs.associateBy { it.id }
                    list.mapNotNull { byId[it.id] }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)
```

Imports: `kotlinx.coroutines.flow.flatMapLatest`, `flowOf`, `ExperimentalCoroutinesApi` (prüfen, teils vorhanden).

- [ ] **Step 3: HomeScreen umstellen**

- `viewModel.quickPicks` → `viewModel.quickPicksLive` bzw. `viewModel.forgottenFavorites` → `viewModel.forgottenFavoritesLive` bei den `collectAsStateWithLifecycle`-Reads (nur die beiden; die anderen Stellen, die quickPicks/forgottenFavorites nutzen — z. B. distinctBy-Remembers — auf die Live-Varianten umbiegen, damit die angezeigten Daten konsistent bleiben).
- Im Item-Rendering beider Sections: den Per-Item-Flow
  ```kotlin
  val song by database
      .song(originalSong.id)
      .collectAsStateWithLifecycle(initialValue = originalSong)
  ```
  entfernen und direkt das Item nutzen (`val song = originalSong`), die `!!`-Aufrufe entsprechend bereinigen (die Live-Liste liefert non-null `Song`).

- [ ] **Step 4: Build + Tests + Commit**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug && ./gradlew :app:testFossDebugUnitTest
git add app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt app/src/main/kotlin/com/metrolist/music/ui/screens/HomeScreen.kt
git commit -m "perf(home): replace per-item song flows with one flow per section"
```

---

### Task 4: Player backgroundAlpha in die Draw-Phase

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt` (~Zeile 830, ~Zeile 852)

- [ ] **Step 1: Read aus der Composition entfernen**

Aktuell: `val backgroundAlpha = state.progress.coerceIn(0f, 1f)` (Zeile 830) wird in der Composition gelesen → jeder Animationsframe recomposed den ganzen Player. `Modifier.alpha(backgroundAlpha)` (Zeile 852) ersetzen durch einen Draw-Phase-Read:

```kotlin
Box(modifier = Modifier.graphicsLayer { alpha = state.progress.coerceIn(0f, 1f) }) {
```

Vorher mit Grep prüfen, ob `backgroundAlpha` noch an weiteren Stellen in Player.kt genutzt wird — alle solche Stellen auf `graphicsLayer`-Lambdas umstellen; falls `backgroundAlpha` danach ungenutzt ist, die Zeile 830 entfernen.

- [ ] **Step 2: Build + Commit**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug
git add app/src/main/kotlin/com/metrolist/music/ui/player/Player.kt
git commit -m "perf(player): read expand progress in draw phase to avoid per-frame recomposition"
```

---

### Task 5: Thumbnail-Resizes korrigieren

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/player/Thumbnail.kt` (~Zeile 631-637)
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/component/Items.kt` (`ItemThumbnail`, ~Zeile 1492)
- Modify: `app/src/main/kotlin/com/metrolist/music/ui/utils/YouTubeUtils.kt` (Helper)

- [ ] **Step 1: Player-Cover resizen**

In `Thumbnail.kt` beim großen Player-Cover (AsyncImage mit `.data(artworkUri)` ohne resize, ~Zeile 633):

```kotlin
.data(artworkUri?.let { if (it is String) it.resize(1080, 1080) else it })
```

(Typ von `artworkUri` vorher prüfen; wenn bereits String: einfach `artworkUri?.resize(1080, 1080)`. `resize` fällt bei Nicht-YouTube-URLs auf den Original-String zurück — sicher.)

- [ ] **Step 2: `resize()` respektiert bereits resizte URLs**

Problem: `String.resize()` (YouTubeUtils.kt:16-44) überschreibt vorhandene Größenparameter — Caller-Resizes (z. B. 200px aus SongListItem) werden von `ItemThumbnail` wieder auf 544px hochgestuft.

In `YouTubeUtils.kt` ergänzen:

```kotlin
fun String.hasThumbnailSize(): Boolean =
    GOOGLEUSERCONTENT_SIZE_PATTERN.matches(this) || GGPHT_SIZE_PATTERN.matches(this)
```

(Die Patterns matchen nur URLs, die bereits Größenparameter tragen.)

In `Items.kt`, `ItemThumbnail` (~Zeile 1492): `thumbnailUrl?.resize(544, 544)` → `thumbnailUrl?.let { if (it.hasThumbnailSize()) it else it.resize(544, 544) }` — Caller, die bereits resizen, werden respektiert; alle anderen bekommen wie bisher 544px.

- [ ] **Step 3: Build + Commit**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug
git add app/src/main/kotlin/com/metrolist/music/ui/player/Thumbnail.kt app/src/main/kotlin/com/metrolist/music/ui/component/Items.kt app/src/main/kotlin/com/metrolist/music/ui/utils/YouTubeUtils.kt
git commit -m "perf(ui): resize player artwork, respect caller thumbnail sizes in ItemThumbnail"
```

---

### Task 6: Verifikation auf Gerät + Push

**Files:**
- Create: `docs/benchmarks/2026-09-02-jank-fixes.md`

- [ ] **Step 1: Install + manueller Test**

```bash
cd /home/endrit/Dokumente/DripMusic
adb install -r app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Checkliste am Gerät (mit User):
1. Musik abspielen, Home scrollen → spürbar flüssiger? (Vorher: Widget-Loop alle 200ms auf Main)
2. Mini-Player antippen → Expand-Animation flüssiger?
3. Player-Cover sieht normal aus (kein Qualitätsverlust)?
4. Like-Status in Quick Picks / Vergessene Favoriten aktualisiert sich noch (Song liken → Herz erscheint)?
5. Home lädt normal (Proof, dass der Cache-Revert sauber ist)?

- [ ] **Step 2: Kurzbericht + Commit + Push**

```bash
git add docs/benchmarks/2026-09-02-jank-fixes.md
git commit -m "docs: jank fix verification results"
git push origin drip
```
