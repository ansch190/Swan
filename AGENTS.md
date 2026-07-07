# AGENTS.md

## Project

Single-module Android music player app. Package: `com.schwanitz`, namespace: `com.schwanitz`.

```kotlin
// Build config
compileSdk = 35, minSdk = 35, targetSdk = 35
versionName = "1.0", versionCode = 1
Java 17, Kotlin 2.0.21, AGP 8.13.2
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

## Package layout

```
com.schwanitz/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt              # Room DB (@Database)
│   │   ├── dao/                        # SongDao, PlaylistDao, SourceConfigDao, SongArtworkDao
│   │   ├── entity/                     # SongEntity, PlaylistEntity, SourceConfigEntity, PlaylistWithSongs, etc.
│   │   └── converter/                  # Mappers, SourceMappers (entity ↔ domain)
│   ├── repository/                     # MusicRepositoryImpl, PlaylistRepositoryImpl, SourceManagerImpl, MusicSourceRegistry
│   └── source/                         # LocalFolderMusicSource, WebDavMusicSource, MetadataExtractor, AuthHttpDataSourceFactory, ArtworkCache, ContentUriDataSource
├── domain/
│   ├── model/                          # Song, Playlist, SongArtwork
│   ├── repository/                     # MusicRepository, PlaylistRepository, SourceManager (interfaces)
│   └── source/                         # MusicSource (interface), SourceConfig, SourceType, LoadSongsResult
├── di/                                 # AppModule, DatabaseModule, PlayerModule, RepositoryModule (Hilt)
├── player/                             # MusicPlayerManager (@Singleton), MusicPlayerService (MediaSessionService)
└── ui/
    ├── navigation/                     # NavGraph, MainScreen, BottomNavItem
    ├── theme/                          # Color, Theme, Type (Material 3)
    ├── components/                     # PlayerControlBar, SongListItem, MarqueeText
    └── screens/
        ├── home/                       # HomeScreen + HomeViewModel (song list, search, favorites)
        ├── nowplaying/                 # NowPlayingScreen + NowPlayingViewModel (queue, playback controls)
        ├── playlist/                   # PlaylistList, PlaylistDetail, SelectSongs (each with VM)
        ├── settings/                   # SettingsDashboard, SettingsScreen (sources), AddSource (wizard), AboutScreen, WebDavProvider
        └── songinfo/                   # SongInfoScreen + SongInfoViewModel (metadata + technical tabs)
```

Entrypoints: `MyApplication` (`@HiltAndroidApp`), `MainActivity` (`@AndroidEntryPoint`).

## Key quirks

- **DI**: Hilt with **KSP** (not kapt). New annotation processors go in `ksp { }` block in `app/build.gradle.kts`.
- **Room DB**: uses `fallbackToDestructiveMigration()` in `DatabaseModule.kt:27` — schema changes destroy all data.
- **Media player**: foreground service (`MusicPlayerService`) extends `MediaSessionService`. Declared in manifest with `foregroundServiceType="mediaPlayback"`. Requires `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions.
- **Navigation**: Compose `NavHost` with bottom bar (Songs / Playlists / Now Playing). Settings via gear icon in Songs top bar. Detail/settings screens hide bottom bar and use `popBackStack()`.
- **ProGuard/R8**: `app/proguard-rules.pro` keeps all `com.schwanitz.**` classes. Additional keep rules go in `app/src/main/keepRules/rules.keep`.
- **Tests**: only auto-generated stubs exist, no real tests.
- **No CI, no lint config, no formatter config** in the repo.
