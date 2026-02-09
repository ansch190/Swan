# Swan - Umfassende Projektüberprüfung

## Zusammenfassung

Swan ist eine Android-Musikverwaltungs-App mit MVVM-Architektur, Room-Datenbank, Retrofit-API-Integration und einem Feature-Set rund um Filtern, Suchen, Playlists und Wiedergabe. Das Projekt ist insgesamt solide strukturiert, es gibt aber in mehreren Bereichen konkretes Verbesserungspotenzial.

---

## 1. Kritische Probleme (Hohe Priorität)

### 1.1 `runBlocking` blockiert den UI-Thread

**Datei:** `AddToPlaylistDialogFragment.kt:64`

```kotlin
val playlists = runBlocking {
    AppDatabase.getDatabase(requireContext()).playlistDao().getAllPlaylists().first()
        .sortedBy { it.name.lowercase() }
}
```

**Problem:** `runBlocking` in `onCreateDialog()` blockiert den Main-Thread. Bei vielen Playlists oder langsamer Datenbank friert die UI ein (ANR-Risiko).

**Lösung:** Den Dialog zunächst mit einem Ladezustand anzeigen, Daten asynchron laden und den Dialog anschließend aktualisieren. Alternativ die Playlists über ein `ViewModel` mit `LiveData` bereitstellen, bevor der Dialog geöffnet wird.

---

### 1.2 Memory Leak durch `observeForever` ohne Cleanup

**Datei:** `MainViewModel.kt:96`

```kotlin
workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
    // ...
}
```

**Problem:** `observeForever` registriert einen Observer, der nie entfernt wird. Bei jedem `addLibraryPath()`-Aufruf wird ein neuer Observer registriert, der dauerhaft im Speicher bleibt.

**Lösung:** Den Observer in einer Variable speichern und bei `SUCCEEDED`, `FAILED` oder `CANCELLED` per `removeObserver()` entfernen:

```kotlin
val observer = object : Observer<WorkInfo> {
    override fun onChanged(workInfo: WorkInfo?) {
        // ... bestehende Logik ...
        if (workInfo?.state?.isFinished == true) {
            workManager.getWorkInfoByIdLiveData(workRequest.id).removeObserver(this)
        }
    }
}
workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever(observer)
```

---

### 1.3 Temp-Datei-Handling ist nicht thread-safe

**Datei:** `MetadataExtractor.kt:265-270, 289-293`

```kotlin
} finally {
    val tempFile = File(context.cacheDir, "audio.mp3")
    if (tempFile.exists()) {
        tempFile.delete()
    }
}
```

**Problem:** Im `finally`-Block wird eine Datei mit festem Namen (`audio.mp3`) gelöscht, während `createTempFileFromUri()` Dateien mit `System.nanoTime()` im Namen erstellt. Das `finally` löscht also die falsche Datei. Bei paralleler Ausführung (z.B. Batch-Scan in `MusicRepository`) können sich Threads gegenseitig die Temp-Dateien löschen.

**Lösung:** Die tatsächlich erstellte Temp-Datei im `finally`-Block referenzieren:

```kotlin
fun extractMetadata(uri: Uri): Metadata {
    var tempFile: File? = null
    try {
        tempFile = createTempFileFromUri(uri, ".mp3")
        // ... Logik ...
    } catch (e: Exception) {
        // ...
    } finally {
        tempFile?.delete()
    }
}
```

---

### 1.4 Fehlende Fehlerprotokollierung im Worker

**Datei:** `MusicScanWorker.kt:44-46`

```kotlin
} catch (e: Exception) {
    Result.failure()
}
```

**Problem:** Die Exception wird komplett verschluckt - kein Logging, keine Fehlerinfo. Debugging wird dadurch sehr schwierig.

**Lösung:**

```kotlin
} catch (e: Exception) {
    Log.e("MusicScanWorker", "Scan failed for URI: $uri", e)
    Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
}
```

---

## 2. Performance-Probleme (Mittlere Priorität)

### 2.1 N+1-Abfragen im FilterItemAdapter

**Datei:** `FilterItemAdapter.kt:94-106`

Für jedes Album wird in `onBindViewHolder` synchron die Metadata der ersten Datei geladen (inkl. `getArtworkBytes` mit Temp-Datei-Erstellung und I/O). Bei 100 Alben bedeutet das 100 separate I/O-Operationen beim Scrollen.

**Lösung:**
- Artwork-Bytes beim Scan in die Datenbank speichern (z.B. als BLOB oder Dateipfad im Cache)
- Alternativ einen Artwork-Cache implementieren, der einmal pro Album beim Scan befüllt wird
- `DiffUtil` statt `notifyDataSetChanged()` verwenden für effizientere RecyclerView-Updates

### 2.2 MusicFile-Entity-Mapping ist dupliziert

**Datei:** `MainViewModel.kt:39-56` und `MainViewModel.kt:109-127`

Die identische Mapping-Logik von `MusicFileEntity` zu `MusicFile` existiert zweimal.

**Lösung:** Eine Extension-Funktion erstellen:

```kotlin
fun MusicFileEntity.toDomainModel() = MusicFile(
    uri = Uri.parse(uri),
    name = name,
    // ...
)
```

### 2.3 `setupTabs()` wird zu häufig aufgerufen

**Datei:** `LibraryActivity.kt:150-155`

`setupTabs()` wird sowohl durch den Filter-Flow als auch durch den Music-Files-Observer aufgerufen. Jeder Aufruf erstellt einen neuen `FilterPagerAdapter`, was den ViewPager-State (aktiver Tab, Scroll-Position) zurücksetzt.

**Lösung:** Den Adapter nur einmal erstellen und bei Datenänderungen aktualisieren statt neu zu erstellen. Oder eine Prüfung einbauen, ob sich die Filter tatsächlich geändert haben.

### 2.4 Coroutine-Scope-Leaks in ViewHolder

**Datei:** `FilterItemAdapter.kt:67, 94`

```kotlin
val job = CoroutineScope(Dispatchers.Main).launch { ... }
```

Für jeden ViewHolder wird ein neuer `CoroutineScope` erstellt. Beim schnellen Scrollen können viele nicht-abgebrochene Coroutines gleichzeitig laufen.

**Lösung:** Einen gemeinsamen `CoroutineScope` für den Adapter verwenden und diesen im `onDetachedFromRecyclerView` canceln:

```kotlin
class FilterItemAdapter(...) : RecyclerView.Adapter<...>() {
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        adapterScope.cancel()
    }
}
```

---

## 3. Architektur & Design (Mittlere Priorität)

### 3.1 Context-Abhängigkeit in Repositories und ViewModel

**Dateien:** `MusicRepository.kt:21`, `MainViewModel.kt:26`

Repositories und das ViewModel halten eine direkte Referenz auf `Context`. Das erschwert Unit-Testing und kann zu Memory-Leaks führen (wenn der Activity-Context gespeichert wird).

**Lösung:**
- `Application`-Context statt Activity-Context verwenden (wird teilweise schon getan)
- Dependency Injection (z.B. Hilt) einführen, um Abhängigkeiten sauber zu injizieren
- Repositories sollten DAOs direkt erhalten statt `Context`

### 3.2 ViewModel erstellt direkt Datenbank-Instanzen

**Datei:** `MainViewModelFactory.kt`

Das ViewModel wird mit einem `Context` und einem `MusicRepository` erstellt, greift aber intern auch direkt auf `AppDatabase.getDatabase()` zu.

**Lösung:** Die Datenbank-Instanz oder die benötigten DAOs per Constructor Injection übergeben. Das erleichtert das Testen mit Mock-DAOs.

### 3.3 `FilterPagerAdapter` wird bei jedem `setupTabs()`-Aufruf neu erstellt

**Datei:** `LibraryActivity.kt:308`

**Problem:** Fragmenterstellung ist teuer und der ViewPager-State geht verloren.

**Lösung:** `getItemId()` und `containsItem()` in `FilterPagerAdapter` implementieren, um stabile IDs zu haben, und den Adapter nur einmal erstellen und bei Bedarf aktualisieren.

### 3.4 Kein Dependency Injection

Das gesamte Projekt erstellt Abhängigkeiten manuell. `AppDatabase.getDatabase(context)` wird in vielen verschiedenen Dateien aufgerufen.

**Lösung:** Hilt einführen. Die Dependency ist im Build-File bereits als Plugin gelistet, wird aber nicht genutzt. Mit Hilt würden Repositories, DAOs und ViewModels sauber injiziert.

---

## 4. Sicherheit (Mittlere Priorität)

### 4.1 Web-Scraping ohne Request-Validation

**Datei:** `ArtistImageRepository.kt:48-69`

Der Artistname wird direkt in HTTP-Requests an theaudiodb.com verwendet. Obwohl `URLEncoder.encode()` genutzt wird, ist die Kombination aus HTML-Parsing und Regex-basiertem URL-Matching fehleranfällig.

**Empfehlung:**
- Die offizielle API von TheAudioDB direkt nutzen statt Web-Scraping
- Falls Scraping nötig: Input sanitizen und Response validieren
- Timeouts (10s) sind gesetzt, was gut ist

### 4.2 Keine ProGuard/R8-Minifizierung im Release

**Datei:** `build.gradle.kts:22`

```kotlin
isMinifyEnabled = false
```

**Problem:** Release-APK enthält unobfuskierten Code und ist größer als nötig.

**Lösung:** `isMinifyEnabled = true` setzen und ProGuard-Regeln für Retrofit, Gson, Room und Glide konfigurieren.

### 4.3 Fehlende `.gitignore`-Einträge

**Datei:** `local.properties` ist im Repository und enthält den lokalen SDK-Pfad.

**Lösung:** `local.properties` in `.gitignore` aufnehmen (falls nicht bereits geschehen) und aus der Git-History entfernen.

---

## 5. Datenbank & Migrationen

### 5.1 `exportSchema = false`

**Datei:** `AppDatabase.kt:26`

**Problem:** Ohne Schema-Export kann Room keine automatische Validierung der Migrationen durchführen. Bei komplexen Migrationen (wie `MIGRATION_5_6`) erhöht dies das Risiko von Datenverlusten.

**Lösung:** `exportSchema = true` setzen und den Schema-Output-Ordner in `build.gradle.kts` konfigurieren:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

### 5.2 Migration 5→6 verwendet `uuid()`

**Datei:** `AppDatabase.kt:110`

```sql
INSERT INTO playlist_songs_temp (id, playlistId, songUri, position)
SELECT uuid(), playlistId, songUri, rowid FROM playlist_songs
```

**Problem:** `uuid()` ist keine Standard-SQLite-Funktion. Diese Migration wird auf den meisten Android-Geräten fehlschlagen, da SQLite `uuid()` nicht nativ unterstützt.

**Lösung:** UUIDs in Kotlin generieren und die Migration programmatisch durchführen:

```kotlin
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS playlist_songs_temp (...)")
        val cursor = db.query("SELECT playlistId, songUri, rowid FROM playlist_songs")
        while (cursor.moveToNext()) {
            val id = UUID.randomUUID().toString()
            val playlistId = cursor.getString(0)
            val songUri = cursor.getString(1)
            val position = cursor.getLong(2)
            db.execSQL(
                "INSERT INTO playlist_songs_temp VALUES (?, ?, ?, ?)",
                arrayOf(id, playlistId, songUri, position)
            )
        }
        cursor.close()
        // ... rest der Migration
    }
}
```

### 5.3 Playlist-Song-Reihenfolge wird ineffizient aktualisiert

**Datei:** `MainViewModel.kt:259-271`

```kotlin
suspend fun updatePlaylistSongOrder(playlistId: String, songs: List<PlaylistSongEntity>) {
    val existingSongs = db.playlistDao().getSongsForPlaylist(playlistId)
    existingSongs.forEach { song ->
        db.playlistDao().deletePlaylistSong(song.id)
    }
    db.playlistDao().insertPlaylistSongs(songs)
}
```

**Problem:** Jeder Song wird einzeln gelöscht. Bei 100 Songs sind das 100 DELETE-Statements.

**Lösung:** Einen DAO-Methodenaufruf mit `@Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")` verwenden.

---

## 6. Tests

### 6.1 Nur Placeholder-Test vorhanden

**Datei:** `ExampleInstrumentedTest.kt:22`

```kotlin
assertEquals("com.as.swan", appContext.packageName)
```

**Probleme:**
- Der Test prüft auf den falschen Paketnamen (`com.as.swan` statt `com.schwanitz.swan`) und wird daher immer fehlschlagen
- Es existieren keinerlei Unit- oder Integrationstests

**Empfohlene Tests:**
- **Unit-Tests** für `MetadataExtractor` (Genre-Mapping, Year-Extraction)
- **Unit-Tests** für `MainViewModel` (Playlist-Operationen, Filter-Logik)
- **Datenbank-Tests** für alle Migrationen (Room bietet `MigrationTestHelper`)
- **Repository-Tests** für `ArtistImageRepository` (mit MockWebServer)
- **UI-Tests** für kritische User-Flows (Espresso)

### 6.2 Fehlender Paketname im Test

**Datei:** `ExampleInstrumentedTest.kt:22`

Korrektur: `assertEquals("com.schwanitz.swan", appContext.packageName)`

---

## 7. Code-Qualität

### 7.1 Übermäßiges Debug-Logging

Fast jede Methode enthält `Log.d()`-Aufrufe - auch in Performance-kritischen Pfaden wie dem RecyclerView-Adapter. In Release-Builds sollte Debug-Logging deaktiviert sein.

**Lösung:**
- `if (BuildConfig.DEBUG)` Guards verwenden
- Oder Timber als Logging-Framework einführen, das in Release-Builds automatisch stummgeschaltet wird
- Logging aus dem Adapter und anderen Hot-Paths entfernen

### 7.2 Hardcodierte Strings in Fehler-Toasts

**Dateien:** `AddToPlaylistDialogFragment.kt:96`, `AddToPlaylistDialogFragment.kt:116`

```kotlin
"Fehler beim Hinzufügen zur Playlist"
"Fehler beim Laden der Playlisten"
```

**Lösung:** Alle Strings in `strings.xml` auslagern für konsistente Lokalisierung.

### 7.3 Unused Import

**Datei:** `MusicScanWorker.kt:11`

```kotlin
import kotlinx.coroutines.flow.collect
```

Dieser Import ist unnötig, da `collect` als Extension-Funktion bereits im Scope ist.

### 7.4 Inconsistente TAG-Definitionen

Einige Klassen definieren `TAG` als `companion object`-Konstante, andere als Instanz-Property:
- `companion object { private const val TAG = "..." }` (z.B. `AddToPlaylistDialogFragment`)
- `private val TAG = "..."` (z.B. `MainViewModel`, `FilterItemAdapter`)

**Lösung:** Einheitlich `companion object { private const val TAG = "..." }` verwenden, da `const` effizienter ist.

---

## 8. Abhängigkeiten

### 8.1 Veraltete Versionen

| Abhängigkeit | Aktuell | Empfohlen |
|-------------|---------|-----------|
| Gson | 2.9.0 | 2.11.0+ |
| OkHttp | 4.10.0 | 4.12.0+ |
| Retrofit | 2.9.0 | 2.11.0+ |
| Glide | 4.16.0 | 4.16.0 (aktuell) |
| Room | 2.6.1 | 2.7.0+ |

### 8.2 Fehlende Abhängigkeiten

- **Timber** für strukturiertes Logging
- **Hilt** für Dependency Injection (Plugin vorhanden, aber nicht konfiguriert)
- **LeakCanary** (Debug) für Memory-Leak-Erkennung
- **Coroutines Test** für ViewModel-Tests

### 8.3 `converter-gson` Version

**Datei:** `libs.versions.toml:46`

```toml
converter-gson = { module = "com.squareup.retrofit2:converter-gson", version.ref = "gson" }
```

**Problem:** `converter-gson` nutzt die Gson-Version (`2.9.0`), sollte aber die Retrofit-Version verwenden, da es ein Retrofit-Modul ist. Die Versionen stimmen zufällig überein (beide 2.9.0), aber bei Updates könnte das zu Problemen führen.

**Lösung:** `version.ref = "retrofit"` verwenden.

---

## 9. UX & Funktionalität

### 9.1 Kein Fehler-Feedback beim Scan-Abbruch

Wenn `MusicScanWorker` fehlschlägt, bekommt der User kein Feedback.

**Lösung:** Einen Fehler-Status im ViewModel exponieren und in der UI als Snackbar/Toast anzeigen.

### 9.2 Play-Button ohne Funktion

**Datei:** `LibraryActivity.kt:192-194`

```kotlin
binding.playButton.setOnClickListener {
    // Wiedergabe wird später angepasst
}
```

Nicht-funktionale UI-Elemente sollten entweder implementiert oder ausgeblendet werden.

### 9.3 Service wird bei Activity-Destroy gestoppt

**Datei:** `LibraryActivity.kt:288-290`

```kotlin
Intent(this, MusicPlaybackService::class.java).also { intent ->
    stopService(intent)
}
```

**Problem:** Wenn die Activity beendet wird (z.B. durch Zurück-Taste), wird die Musikwiedergabe gestoppt. Nutzer erwarten typischerweise, dass Musik im Hintergrund weiterläuft.

**Lösung:** Den Service nur bei explizitem Stoppen beenden, nicht bei Activity-Destroy.

---

## 10. Priorisierte Empfehlungen

### Sofort umsetzen (Bugs/Risiken):
1. **Temp-Datei-Handling fixen** - Thread-Safety-Bug in `MetadataExtractor`
2. **`runBlocking` entfernen** - ANR-Risiko in `AddToPlaylistDialogFragment`
3. **`observeForever` Memory-Leak fixen** - in `MainViewModel.addLibraryPath()`
4. **Migration 5→6 fixen** - `uuid()` ist keine SQLite-Funktion
5. **Fehlenden Error-Log im Worker hinzufügen**

### Kurzfristig (Qualität):
6. Falschen Paketnamen im Test korrigieren
7. Entity-zu-Domain-Mapping konsolidieren
8. Hardcodierte Strings in `strings.xml` auslagern
9. `converter-gson` Version-Referenz korrigieren
10. ProGuard aktivieren für Release-Builds

### Mittelfristig (Architektur):
11. Hilt für Dependency Injection einführen
12. Artwork-Caching implementieren
13. Unit-Tests für Kernlogik schreiben
14. Timber als Logging-Framework einführen
15. `DiffUtil` in RecyclerView-Adaptern verwenden

### Langfristig (Features):
16. Hintergrund-Wiedergabe auch bei Activity-Destroy
17. Crash-Reporting (Firebase Crashlytics o.ä.)
18. Room Schema-Export aktivieren
19. Datenbank-Migrations-Tests
20. Web-Scraping durch direkte API-Nutzung ersetzen
