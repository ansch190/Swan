# Swan

A modern Android music player built with Jetpack Compose.

Play music from your device and from network sources (WebDAV). Manage playlists, browse your library, view rich artist/album/genre/year detail screens, fetch lyrics automatically, and enjoy a clean Material 3 interface — all with background playback.

## Features

- **Local playback** — Add folders from your device via the Storage Access Framework
- **Network sources** — WebDAV support with presets for pCloud, Koofr, GMX MediaCenter, WEB.DE, Mailbox.org (SMB coming soon)
- **Playlists** — Create, rename, reorder via drag-and-drop, add/remove songs
- **Queue** — View and jump between upcoming tracks
- **Search** — Real-time filtering by title, artist, or album
- **Favorites** — Mark songs and filter to show only favorites
- **Shuffle & Repeat** — Toggle shuffle; cycle through Off → One → All repeat modes
- **Song info** — Rich metadata (title, artist, album, track, disc, year, genre) and technical details (codec, sample rate, bitrate, file size, tag version)
- **Lyrics** — Automatic lyrics fetching from Genius, cached in local database; viewable from Song Info and Now Playing screens
- **Artist images** — Artist photos fetched from Discogs API, cached to local storage
- **Artist biographies** — Artist bios fetched from Last.fm API, cached with 6-month TTL
- **Album detail** — Multi-CD support with songs grouped by disc, artwork pager
- **Artist detail** — Photo, biography popup, and Songs/Albums tabs
- **Genre detail** — Three tabs: Songs / Artists / Albums
- **Year detail** — Two tabs: Songs / Albums
- **Multiple album artworks** — Swipeable artwork carousel with dot indicators
- **Multi-language UI** — Switch between System / Deutsch / English in Settings
- **Background playback** — Foreground service with media notification & lock screen controls
- **Material 3 design** — Modern Compose UI with dynamic theming

## Screenshots

*(Add screenshots here)*

## Tech Stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose + Material 3 |
| Navigation | Compose Navigation (NavHost) |
| DI | Hilt + KSP |
| Database | Room |
| Player | Media3 ExoPlayer + MediaSession |
| Images | Coil |
| HTML Parsing | JSoup |
| Serialization | kotlinx-serialization |
| Preferences | DataStore Preferences |
| HTTP | OkHttp (with digest auth for WebDAV) |
| Drag-and-drop | reorderable |
| APIs | Discogs (OAuth 1.0a), Last.fm (API key), Genius (Client Token) |

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedDebugAndroidTest
```

- Requires Java 17
- Use the Gradle wrapper (`./gradlew`), not a system installation
- API keys must be set in `local.properties`: `discogsKey`, `discogsSecret`, `lastfmKey`, `geniusAccessToken`

## Architecture

```
app/src/main/java/com/schwanitz/
├── data/            # Room DB, DAOs, entities, repository implementations, music sources,
│                    # external API clients (Discogs, Last.fm, Genius)
├── domain/          # Models (Song, Playlist, SongArtwork, Album, ArtistImage, ArtistProfile),
│                    # repository interfaces, MusicSource interface
├── di/              # Hilt modules (AppModule, DatabaseModule, PlayerModule, RepositoryModule)
├── player/          # MusicPlayerManager (singleton), MusicPlayerService (foreground)
└── ui/              # Compose screens (Songs, Playlists, Now Playing, Settings, About,
                     # Song Info, Album Detail, Artist Detail, Genre Detail, Year Detail)
```

- **ViewModels** use `@HiltViewModel` and expose state via `StateFlow`
- **Sources** implement a common `MusicSource` interface registered via `MusicSourceRegistry`
- **Player** is a `@Singleton` wrapping Media3 ExoPlayer with authenticated HTTP support
- **External APIs** are wrapped in `@Singleton` services with caching (Room + local files) and rate limiting

## Music Sources

- **Local Folder** — pick any directory via SAF; the app retains persistent access
- **WebDAV** — connect to any WebDAV server; presets available for popular providers
- **SMB** — placeholder for future support

Sources can be enabled or disabled individually, and all sources can be rescanned with a single "Reload All" button.

## License

PolyForm Noncommercial License 1.0.0 — see [LICENSE](LICENSE).

Copyright 2026 Andreas Schwanitz
