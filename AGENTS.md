# AGENTS.md

## Project

Single-module Android music player app. Package: `com.schwanitz`, namespace: `com.schwanitz`.

```kotlin
// Build config
compileSdk = 37, minSdk = 31, targetSdk = 36
versionName = "2.3", versionCode = 7
Java 17, Kotlin 2.1.0, AGP 9.2.1
```

## Build

```powershell
./gradlew assembleDebug                              # debug APK
./gradlew installDebug                               # install on device/emulator
./gradlew testDebugUnitTest                          # unit tests (JVM)
./gradlew connectedDebugAndroidTest                  # instrumented tests (device)
./gradlew testDebugUnitTest --tests "com.schwanitz.SomeTest"  # single test
```

- Always use the Gradle wrapper (`./gradlew`), not system Gradle.
- Version catalog: `gradle/libs.versions.toml`
- API credentials are user-provided in Settings and stored with AES-GCM under an Android Keystore key. Do not add API secrets to BuildConfig.

## Package layout

```
com.schwanitz/
├── data/
│   ├── discogs/                      # DiscogsApiService, DiscogsModels, DiscogsRateLimiter
│   ├── genius/                       # GeniusApiService, GeniusLyricsProvider, GeniusModels
│   ├── lastfm/                       # LastFmApiService, LastFmArtistProfileProvider, LastFmModels
│   ├── local/
│   │   ├── AppDatabase.kt            # Room DB (@Database, version 7, including scan staging)
│   │   ├── LanguagePreferences.kt    # DataStore wrapper for language code
│   │   ├── Migrations.kt            # Room migration definitions
│   │   ├── dao/                      # SongDao, PlaylistDao, SourceConfigDao, AlbumDao,
│   │   │                             # AlbumArtworkDao, ArtistDao, ArtistPicDao, SongLyricsDao,
│   │   │                             # AlbumSeriesDao, AlbumSongDao, SongTechnicalInfoDao
│   │   ├── entity/                   # SongEntity, PlaylistEntity, PlaylistSongMapping,
│   │   │                             # SourceConfigEntity, ArtistEntity, ArtistPicEntity,
│   │   │                             # SongLyricsEntity, SongTechnicalInfoEntity, AlbumEntity,
│   │   │                             # AlbumArtworkEntity, AlbumSongMappingEntity,
│   │   │                             # AlbumSeriesEntity, AlbumSeriesMappingEntity
│   │   └── converter/                # Mappers, SourceMappers (entity ↔ domain)
│   ├── repository/                   # MusicRepositoryImpl, PlaylistRepositoryImpl,
│   │                                 # SourceManagerImpl, MusicSourceRegistry,
│   │                                 # ArtistRepositoryImpl
│   ├── backup/                       # streaming v2 backup + legacy v1 importer
│   └── source/                       # LocalFolderMusicSource, WebDavMusicSource,
│                                     # MetadataExtractor, AuthHttpDataSourceFactory,
│                                     # ArtworkCache, ArtistImageCache, ContentUriDataSource,
│                                     # SeriesDetector, ImageScaler
├── domain/
│   ├── model/                        # Song, Playlist, Album, AlbumArtwork,
│   │                                 # Artist, AlbumSeries
│   ├── repository/                   # MusicRepository, PlaylistRepository, SourceManager,
│   │                                 # ArtistRepository (interfaces)
│   └── source/                       # MusicSource (interface), SourceConfig, SourceType,
│                                     # LoadSongsResult
├── di/                               # AppModule, DatabaseModule, PlayerModule,
│                                     # RepositoryModule (Hilt)
├── player/                           # MusicPlayerManager (@Singleton),
│                                     # MusicPlayerService (MediaSessionService)
└── ui/
    ├── navigation/                   # NavGraph, MainScreen, BottomNavItem
    ├── theme/                        # Color, Theme, Type (Material 3)
    ├── components/                   # PlayerControlBar, SongListItem, MarqueeText,
    │                                 # AlbumListItem, SelectableSongItem,
    │                                 # PlaylistPickerDialog, SelectionDelegate
    └── screens/
        ├── home/                     # HomeScreen + HomeViewModel (song list, search, favorites)
        ├── nowplaying/               # NowPlayingScreen + NowPlayingViewModel
        │                             # (queue, playback controls, lyrics dialog)
        ├── playlist/                 # PlaylistList, PlaylistDetail, SelectSongs (each with VM)
        ├── settings/                 # SettingsDashboard, SettingsScreen, SettingsViewModel,
        │                             # AddSource (wizard), GeneralSettingsScreen (language),
        │                             # LanguageSelectionViewModel, AboutScreen, WebDavProvider
        ├── songinfo/                 # SongInfoScreen + SongInfoViewModel
        │                             # (metadata, technical tabs + lyrics dialog)
        ├── albumlist/                # AlbumListScreen + AlbumListViewModel
        ├── albumdetail/              # AlbumDetailScreen + AlbumDetailViewModel (multi-CD)
        ├── artistlist/               # ArtistListScreen + ArtistListViewModel
        ├── artistdetail/             # ArtistDetailScreen + ArtistDetailViewModel
        │                             # (photo, bio, songs/albums tabs)
        ├── genrelist/                # GenreListScreen + GenreListViewModel
        ├── genredetail/              # GenreDetailScreen + GenreDetailViewModel
        │                             # (songs/artists/albums tabs)
        ├── yearlist/                 # YearListScreen + YearListViewModel
        ├── yeardetail/               # YearDetailScreen + YearDetailViewModel
        │                             # (songs/albums tabs)
        ├── serieslist/               # SeriesListScreen + SeriesListViewModel
        └── seriesdetail/             # SeriesDetailScreen + SeriesDetailViewModel
                                      # (albums in series)
```

Entrypoints: `MyApplication` (`@HiltAndroidApp`), `MainActivity` (`@AndroidEntryPoint`, extends `AppCompatActivity`).

## Key quirks

- **DI**: Hilt with **KSP** (not kapt). New annotation processors go in `ksp { }` block in `app/build.gradle.kts`.
- **Room DB**: version 7 with persistent scan staging. Uses `Migrations.kt` — no `fallbackToDestructiveMigration()`. Schema is exported to `app/schemas/` and migration tests use `MigrationTestHelper`.
- **Refresh semantics**: scans emit `ScanEvent.Discovered` and `ScanEvent.Parsed` in batches up to 100. Only a completed enumeration commits; fatal errors/cancellation discard staging and retain live data. Per-file failures retain old rows. Scan commits are serialized by a central mutex.
- **Album identity**: `(name, albumArtist, year)` via typed `AlbumKey`; migration 6→7 consolidates duplicates and creates a unique index. Artist equality is exact and case-insensitive, never `LIKE`.
- **Media player**: foreground service (`MusicPlayerService`) extends `MediaSessionService`. Declared in manifest with `foregroundServiceType="mediaPlayback"`. Requires `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions.
- **Navigation**: Compose `NavHost` with bottom bar (Songs / Playlists / Now Playing). Settings via gear icon in Songs top bar. Detail/settings screens hide bottom bar and use `popBackStack()`.
- **Genius lyrics**: Client Access Token auth (`Authorization: Bearer`), JSoup for HTML parsing (`<div data-lyrics-container="true">`), 2-pass search (original title → title without parenthetical suffixes), top-3-hit title validation (case-insensitive + accent-normalized), cached in `song_lyrics` Room table. Cleanup order on source deletion: lyrics → artwork → songs (no orphaned rows).
- **Discogs**: OAuth 1.0a for API auth, rate-limited (1 req/s via `DiscogsRateLimiter`), artist images cached to `artist_pics` table + local file cache.
- **Last.fm**: API key (`api_key` param), artist biographies cached in `artists` table with 6-month TTL.
- **Language**: `AppCompatDelegate.setApplicationLocales()` for locale switching (API 33+); requires `AppCompatActivity`, `android:localeConfig="@xml/locales_config"` in manifest, `NoActionBar` theme parent. Works on minSdk 31 via AppCompat compat.
- **Strings**: ~252 strings in `res/values/strings.xml` (English) and `res/values-de/strings.xml` (German).
- **ProGuard/R8**: `app/proguard-rules.pro` keeps all `com.schwanitz.**` classes. Additional keep rules go in `app/src/main/keepRules/rules.keep`.
- **Backup & Recovery**: v2 is a streaming encrypted ZIP-like container with `SWANBAK2`, PBKDF2-HMAC-SHA256 (600k) and AES-256-GCM. Settings/credentials are always included; library records and image caches are optional. Playlists/favorites are excluded. Restore replaces app data. v1 remains readable with its legacy pepper only in the v1 importer. Local SAF sources require reauthorization after restore.
- **Tests**: JVM tests cover converters, repositories, refresh results, playback modes and backup models. Instrumented Room tests validate 1→7 and 6→7 migrations.
- **CI/Releases**: CI runs unit tests, lint, debug/release builds and API 35 instrumentation. Tagged releases use a secret-provided keystore and publish a minified release APK plus SHA-256 only.
