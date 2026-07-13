# AGENTS.md

## Project

Single-module Android music player app. Package: `com.schwanitz`, namespace: `com.schwanitz`.

```kotlin
// Build config
compileSdk = 37, minSdk = 31, targetSdk = 35
versionName = "1.2", versionCode = 2
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
- API keys in `local.properties`: `discogsKey`, `discogsSecret`, `lastfmKey`, `geniusAccessToken`

## Package layout

```
com.schwanitz/
├── data/
│   ├── discogs/                      # DiscogsApiService, DiscogsModels, DiscogsRateLimiter
│   ├── genius/                       # GeniusApiService, GeniusLyricsProvider, GeniusModels
│   ├── lastfm/                       # LastFmApiService, LastFmArtistProfileProvider, LastFmModels
│   ├── local/
│   │   ├── AppDatabase.kt            # Room DB (@Database, version 1, 13 entities, 11 DAOs)
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
- **Room DB**: version 1, 13 entities, 11 DAOs. Uses `Migrations.kt` for schema migrations — no `fallbackToDestructiveMigration()`. Schema exported to `app/schemas/` via KSP arg.
- **Media player**: foreground service (`MusicPlayerService`) extends `MediaSessionService`. Declared in manifest with `foregroundServiceType="mediaPlayback"`. Requires `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions.
- **Navigation**: Compose `NavHost` with bottom bar (Songs / Playlists / Now Playing). Settings via gear icon in Songs top bar. Detail/settings screens hide bottom bar and use `popBackStack()`.
- **Genius lyrics**: Client Access Token auth (`Authorization: Bearer`), JSoup for HTML parsing (`<div data-lyrics-container="true">`), 2-pass search (original title → title without parenthetical suffixes), top-3-hit title validation (case-insensitive + accent-normalized), cached in `song_lyrics` Room table. Cleanup order on source deletion: lyrics → artwork → songs (no orphaned rows).
- **Discogs**: OAuth 1.0a for API auth, rate-limited (1 req/s via `DiscogsRateLimiter`), artist images cached to `artist_pics` table + local file cache.
- **Last.fm**: API key (`api_key` param), artist biographies cached in `artists` table with 6-month TTL.
- **Language**: `AppCompatDelegate.setApplicationLocales()` for locale switching (API 33+); requires `AppCompatActivity`, `android:localeConfig="@xml/locales_config"` in manifest, `NoActionBar` theme parent. Works on minSdk 31 via AppCompat compat.
- **Strings**: ~180 strings in `res/values/strings.xml` (English) and `res/values-de/strings.xml` (German).
- **ProGuard/R8**: `app/proguard-rules.pro` keeps all `com.schwanitz.**` classes. Additional keep rules go in `app/src/main/keepRules/rules.keep`.
- **Tests**: only auto-generated stubs exist, no real tests.
- **No CI, no lint config, no formatter config** in the repo.
