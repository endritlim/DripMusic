# DripMusic Plan 1: Fork-Setup + Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** DripMusic als eigenständig installierbaren Fork von Metrolist aufsetzen und die Ladezeiten von Startseite und Suche messbar verbessern.

**Architecture:** Fork von Metrolist (Kotlin/Jetpack Compose, Module `:app` + `:innertube`). Performance über drei gezielte Eingriffe: In-Memory-Cache für die Home-Page (30 Min TTL), Batch-DB-Query statt N+1 in Quick Picks, LRU-Suggestion-Cache (5 Min TTL) in der Suche. Persistenter Home-Cache (über Prozessneustarts hinweg) ist bewusst NICHT Teil dieses Plans — `HomePage` ist nicht serialisierbar, das wäre ein invasiver Eingriff ins `:innertube`-Modul (Follow-up-Plan).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Ktor/OkHttp, JUnit4 (Tests unter `app/src/test`).

**Regeln aus `AGENTS.md` (gelten für alle Tasks):**
- Commit-Format: `type(scope): short description` (z. B. `perf(home): ...`)
- KEINE Änderungen am Datenbank-Schema (neue DAO-Queries sind ok, keine Entity-/Migrations-Änderungen)
- Strings nur in `app/src/main/res/values/metrolist_strings.xml` ändern
- KEINE Upstream-Markdown-Dateien anlegen/ändern (README, changelog etc.); ausgenommen ist unser eigenes `docs/`-Verzeichnis im Fork
- Keine Version-Bumps (`versionCode`/`versionName` unangetastet lassen)
- Build-Verifikation nach jeder Code-Änderung: `./gradlew :app:assembleFossDebug` (aus dem Repo-Root)
- APK für manuelle Tests: `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk`

**Verzeichnisse:**
- Repo-Root (= der Fork „DripMusic"): `/home/endrit/Dokumente/DripMusic` — Code UND eigene Docs (`docs/`) leben hier, Branch `drip`
- Meld (Referenz für spätere Portierungen): `/home/endrit/Dokumente/Meld`

---

### Task 1: GitHub-Fork & Remote-Setup

**Files:** keine (nur Git)

- [ ] **Step 1: Upstream-URL prüfen**

```bash
git -C /home/endrit/Dokumente/DripMusic remote -v
```

Expected: zeigt die origin-URL von Metrolist (z. B. `https://github.com/MetrolistGroup/Metrolist.git`)

- [ ] **Step 2: Fork auf GitHub erstellen**

Mit gh CLI (falls eingeloggt via `gh auth status`):

```bash
gh repo fork MetrolistGroup/Metrolist --fork-name DripMusic --clone=false
```

Fallback: Browser → Metrolist-Repo → „Fork" → Name `DripMusic`.

- [ ] **Step 3: Remotes umbiegen — upstream bleibt Metrolist, origin wird der eigene Fork**

```bash
cd /home/endrit/Dokumente/DripMusic
git remote rename origin upstream
git remote add origin https://github.com/<DEIN-GITHUB-USER>/DripMusic.git
git remote -v
```

Expected: `origin` zeigt auf den eigenen Fork, `upstream` auf Metrolist.

- [ ] **Step 4: Arbeitsbranch anlegen und pushen**

```bash
cd /home/endrit/Dokumente/DripMusic
git checkout -b drip
git push -u origin drip
```

Expected: Branch `drip` existiert auf dem eigenen Fork. `main` bleibt unangetastet als Upstream-Spiegel.

- [ ] **Step 5: Baseline-Build (unveränderter Code muss bauen)**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug
```

Expected: `BUILD SUCCESSFUL`. Der erste Build dauert lange (Dependency-Downloads), das ist normal. APK liegt danach unter `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk`.

---

### Task 2: Rebrand zu DripMusic

**Files:**
- Modify: `app/build.gradle.kts:10` (`baseApplicationId`)
- Modify: `app/build.gradle.kts:48` (`app_name`)
- Modify: `app/build.gradle.kts:136` (Debug-App-Name)

- [ ] **Step 1: applicationId ändern**

In `app/build.gradle.kts` Zeile 10:

```kotlin
val baseApplicationId = "com.dripmusic.app"
```

(vorher: `"com.metrolist.music"`). Debug-Builds bekommen automatisch den Suffix `.debug` (Zeile 131-133), dadurch koexistieren Original, DripMusic und DripMusic-Debug parallel auf einem Gerät.

- [ ] **Step 2: App-Namen ändern**

Zeile 48:

```kotlin
        resValue("string", "app_name", appNameOverride ?: "DripMusic")
```

Zeile 136:

```kotlin
                resValue("string", "app_name", "DripMusic Debug")
```

- [ ] **Step 3: Bauen, installieren, verifizieren**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug
adb install -r app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk
```

Expected: Build erfolgreich; im App-Drawer erscheint „DripMusic Debug" zusätzlich zum installierten Metrolist; App startet und zeigt die Startseite.

- [ ] **Step 4: Commit**

```bash
cd /home/endrit/Dokumente/DripMusic
git add app/build.gradle.kts
git commit -m "chore: rebrand to DripMusic (applicationId + app name)"
```

---

### Task 3: Baseline-Messung (Vorher)

**Files:**
- Create: `docs/benchmarks/2026-09-02-baseline.md` (im Fork-Repo unter `docs/`)

- [ ] **Step 1: Messprotokoll ausführen**

Auf dem Gerät mit installiertem „DripMusic Debug" aus Task 2, jeweils: App komplett schließen (`adb shell am force-stop com.dripmusic.app.debug`), Kaltstart vom Launcher, Stoppuhr vom Tap bis sichtbarer Home-Inhalt. 3 Durchläufe für die Startseite, 3 Durchläufe „Suche öffnen und Begriff tippen bis Vorschläge erscheinen".

- [ ] **Step 2: Werte dokumentieren und committen**

`docs/benchmarks/2026-09-02-baseline.md` im DripMusic-Repo:

```markdown
# Baseline 2026-09-02 (unveränderter Metrolist-Code, fossDebug)

Gerät: <Modell>, Netz: <WLAN/LTE>

## Home-Kaltstart (Tap → Inhalt sichtbar)
- Lauf 1: X,X s
- Lauf 2: X,X s
- Lauf 3: X,X s
- Median: X,X s

## Suche (Tippen → Vorschläge sichtbar)
- Lauf 1: X,X s
- Lauf 2: X,X s
- Lauf 3: X,X s
- Median: X,X s
```

```bash
cd /home/endrit/Dokumente/DripMusic
git add docs/benchmarks/2026-09-02-baseline.md
git commit -m "docs: performance baseline before optimizations"
```

---

### Task 4: TimedCache-Utility (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/metrolist/music/utils/TimedCache.kt`
- Test: `app/src/test/kotlin/com/metrolist/music/utils/TimedCacheTest.kt`

- [ ] **Step 1: Failing Test schreiben**

`app/src/test/kotlin/com/metrolist/music/utils/TimedCacheTest.kt`:

```kotlin
package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedCacheTest {
    private var currentTime = 0L
    private val cache = TimedCache<String>(ttlMillis = 1000L, now = { currentTime })

    @Test
    fun `get returns null when empty`() {
        assertNull(cache.get())
    }

    @Test
    fun `get returns value within ttl`() {
        cache.put("a")
        currentTime = 999L
        assertEquals("a", cache.get())
    }

    @Test
    fun `get returns null after ttl expired`() {
        cache.put("a")
        currentTime = 1000L
        assertNull(cache.get())
    }

    @Test
    fun `invalidate clears the entry`() {
        cache.put("a")
        cache.invalidate()
        assertNull(cache.get())
    }

    @Test
    fun `put overwrites previous entry and refreshes timestamp`() {
        cache.put("a")
        currentTime = 900L
        cache.put("b")
        currentTime = 1800L
        assertEquals("b", cache.get())
    }
}
```

- [ ] **Step 2: Test laufen lassen, muss fehlschlagen**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:testFossDebugUnitTest --tests "com.metrolist.music.utils.TimedCacheTest"
```

Expected: FAIL — Klasse `TimedCache` existiert nicht (Compilation error).

- [ ] **Step 3: Implementierung**

`app/src/main/kotlin/com/metrolist/music/utils/TimedCache.kt`:

```kotlin
package com.metrolist.music.utils

/**
 * In-memory cache holding a single value with a time-to-live.
 * Synchronized because callers use Dispatchers.IO.
 */
class TimedCache<T>(
    private val ttlMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var entry: Pair<Long, T>? = null

    @Synchronized
    fun get(): T? = entry?.takeIf { now() - it.first < ttlMillis }?.second

    @Synchronized
    fun put(value: T) {
        entry = now() to value
    }

    @Synchronized
    fun invalidate() {
        entry = null
    }
}
```

- [ ] **Step 4: Test laufen lassen, muss grün sein**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:testFossDebugUnitTest --tests "com.metrolist.music.utils.TimedCacheTest"
```

Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
cd /home/endrit/Dokumente/DripMusic
git add app/src/main/kotlin/com/metrolist/music/utils/TimedCache.kt app/src/test/kotlin/com/metrolist/music/utils/TimedCacheTest.kt
git commit -m "feat(utils): add TimedCache with TTL"
```

---

### Task 5: Home-Page-Cache (In-Memory, 30 Min TTL)

**Files:**
- Create: `app/src/main/kotlin/com/metrolist/music/viewmodels/HomePageCache.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt:468-481` (YouTube.home()-Block in `load()`)
- Modify: `app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt:704-706` (else-Zweig in `refresh()`)

- [ ] **Step 1: HomePageCache anlegen**

`app/src/main/kotlin/com/metrolist/music/viewmodels/HomePageCache.kt`:

```kotlin
package com.metrolist.music.viewmodels

import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.utils.TimedCache

// ponytail: in-memory only — cache dies with the process, so cold starts still hit
// the network (upgrade path: persist HomePage, requires making it @Serializable in :innertube)
object HomePageCache {
    private const val TTL_MILLIS = 30L * 60L * 1000L
    private val cache = TimedCache<HomePage>(TTL_MILLIS)

    fun get(): HomePage? = cache.get()

    fun put(page: HomePage) = cache.put(page)

    fun invalidate() = cache.invalidate()
}
```

- [ ] **Step 2: `load()` im HomeViewModel auf den Cache umstellen**

Den bisherigen Block (Zeilen 468-481):

```kotlin
            launch(Dispatchers.IO) {
                YouTube.home().onSuccess { page ->
                    homePage.value = page.copy(
```

ersetzen durch (Cache speichert die UNGEFILTERTE Seite, Filter laufen wie bisher beim Lesen — so bleiben die Hide-Settings wirksam):

```kotlin
            launch(Dispatchers.IO) {
                val cachedPage = HomePageCache.get()
                val result = cachedPage?.let { Result.success(it) } ?: YouTube.home()
                result.onSuccess { page ->
                    if (cachedPage == null) HomePageCache.put(page)
                    homePage.value = page.copy(
```

Der Rest des Blocks (`sections = page.sections.mapNotNull { ... }`, `.onFailure { reportException(it) }`) bleibt unverändert. Import ergänzen: keiner nötig (gleiches Package).

- [ ] **Step 3: `refresh()` invalidiert den Cache**

In `refresh()` (Zeilen 704-706), else-Zweig:

```kotlin
            } else {
                HomePageCache.invalidate()
                load()
            }
```

Damit bleibt „runterziehen zum Aktualisieren" ein echter Netz-Refresh; der automatische Refresh bei Netz-Reconnect läuft über denselben Pfad und holt ebenfalls frisch.

- [ ] **Step 4: Bauen und manuell verifizieren**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug
adb install -r app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk
```

Manuell: App öffnen (Home lädt), App in den Hintergrund schicken und ViewModel-Recreation auslösen (z. B. „Don't keep activities" oder einfach App wegwerfen und innerhalb von 30 Min neu öffnen) → Home sollte ohne sichtbare Ladezeit aus dem Cache kommen. Pull-to-refresh → lädt sichtbar neu.

- [ ] **Step 5: Commit**

```bash
cd /home/endrit/Dokumente/DripMusic
git add app/src/main/kotlin/com/metrolist/music/viewmodels/HomePageCache.kt app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt
git commit -m "perf(home): cache home page in memory with 30 min TTL"
```

---

### Task 6: N+1-Fix in Quick Picks

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt:336-345` (in `getQuickPicks()`)

- [ ] **Step 1: Schleife durch Batch-Query ersetzen**

Bisher (einzelner Query pro Song):

```kotlin
                        YouTube.related(endpoint).onSuccess { page ->
                            // Convert YouTube songs to local Song format if they exist in database
                            page.songs.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
```

Neu (ein Batch-Query via bereits vorhandenem `DatabaseDao.getSongsByIds`, `DatabaseDao.kt:702-704`):

```kotlin
                        YouTube.related(endpoint).onSuccess { page ->
                            // Convert YouTube songs to local Song format if they exist in database
                            val candidates = page.songs.take(10)
                            val localById = database.getSongsByIds(candidates.map { it.id }).associateBy { it.id }
                            candidates.forEach { ytSong ->
                                localById[ytSong.id]?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
```

- [ ] **Step 2: Bauen**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd /home/endrit/Dokumente/DripMusic
git add app/src/main/kotlin/com/metrolist/music/viewmodels/HomeViewModel.kt
git commit -m "perf(home): batch quick picks song lookup (N+1 -> 1 query)"
```

---

### Task 7: Suggestion-Cache in der Suche (5 Min TTL)

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/viewmodels/OnlineSearchSuggestionViewModel.kt:78` (Aufruf) und Klassenkörper (Cache + Helper)

- [ ] **Step 1: Cache und Helper-Methode hinzufügen**

In `OnlineSearchSuggestionViewModel`, direkt unter `val viewState = _viewState.asStateFlow()` (Zeile 47):

```kotlin
        // ponytail: in-memory LRU, cleared on process death; confined to viewModelScope (main dispatcher)
        private val suggestionCache =
            object : LinkedHashMap<String, Pair<Long, SearchSuggestions>>(32, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, Pair<Long, SearchSuggestions>>?,
                ): Boolean = size > 32
            }

        private fun getSuggestionsCached(query: String): SearchSuggestions? {
            val now = System.currentTimeMillis()
            suggestionCache[query]?.let { (timestamp, cached) ->
                if (now - timestamp < SUGGESTION_CACHE_TTL_MILLIS) return cached
                suggestionCache.remove(query)
            }
            return null
        }

        private fun putSuggestions(query: String, result: SearchSuggestions) {
            suggestionCache[query] = System.currentTimeMillis() to result
        }
```

Und am Ende der Klasse (vor der schließenden Klammer, nach `fetchParsedUrlItem`):

```kotlin
        private companion object {
            const val SUGGESTION_CACHE_TTL_MILLIS = 5L * 60L * 1000L
        }
```

Import ergänzen: `import com.metrolist.innertube.models.SearchSuggestions`.

- [ ] **Step 2: Aufruf umstellen**

Zeile 78, bisher:

```kotlin
                                val result = YouTube.searchSuggestions(query).getOrNull()
```

Neu:

```kotlin
                                val result =
                                    getSuggestionsCached(query)
                                        ?: YouTube.searchSuggestions(query).getOrNull()?.also { putSuggestions(query, it) }
```

- [ ] **Step 3: Bauen und manuell verifizieren**

```bash
cd /home/endrit/Dokumente/DripMusic
./gradlew :app:assembleFossDebug
adb install -r app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk
```

Manuell: Suche öffnen, Begriff tippen, löschen, denselben Begriff nochmal tippen → Vorschläge erscheinen beim zweiten Mal sofort ohne Ladeverzögerung.

- [ ] **Step 4: Commit**

```bash
cd /home/endrit/Dokumente/DripMusic
git add app/src/main/kotlin/com/metrolist/music/viewmodels/OnlineSearchSuggestionViewModel.kt
git commit -m "perf(search): cache search suggestions in memory (5 min TTL)"
```

---

### Task 8: Nachher-Messung & Abschluss

**Files:**
- Create: `docs/benchmarks/2026-09-02-after.md`

- [ ] **Step 1: Messprotokoll aus Task 3 wiederholen**

Gleiches Gerät, gleiches Netz, gleiches Protokoll (3 Kaltstarts Home, 3 Suchen), mit der Build aus allen Perf-Tasks. Hinweis: Der Home-Cache ist in-memory → der KALTSTART wird sich kaum ändern; erwartete Verbesserung zeigt sich bei wiederholtem Öffnen innerhalb von 30 Min und in der Suche. Das Protokoll daher um 3 Läufe „App wegwerfen und innerhalb von 30 Min erneut öffnen" ergänzen.

- [ ] **Step 2: Werte dokumentieren, committen, pushen**

`docs/benchmarks/2026-09-02-after.md` im gleichen Format wie die Baseline, plus kurzer Vergleich (Median vorher/nachher).

```bash
cd /home/endrit/Dokumente/DripMusic
git add docs/benchmarks/2026-09-02-after.md
git commit -m "docs: performance numbers after optimizations"
cd /home/endrit/Dokumente/DripMusic
git push origin drip
```

- [ ] **Step 3: Fazit**

Wenn die Zahlen zeigen, dass der Kaltstart weiterhin dominiert, ist der nächste Hebel der persistente Home-Cache (`HomePage` serialisierbar machen) — das wird ein eigener Plan, zusammen mit AP4 (Musixmatch) und AP5 (New Releases) aus der Spec.
