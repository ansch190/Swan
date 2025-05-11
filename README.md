Swan - Music Library and Playback App

Overview
Swan is an Android music library and playback application built with Kotlin, designed to manage and play music files stored on a user's device. It allows users to import music from selected folders, browse and filter their music collection, view detailed metadata, and play tracks with a foreground service. The app features a modern UI with navigation drawers, tabs, and full-screen image viewing capabilities.
Features

Library Management:

Import music files from user-selected folders using SAF (Storage Access Framework).
Asynchronous scanning with progress indicators using WorkManager.
Support for audio formats like MP3, FLAC, AAC, and WAV.


Filtering and Browsing:

Configurable filters (e.g., Title, Artist, Album, Genre) with reactive search functionality.
Tab-based navigation for filtering music by different criteria.
Long-press filter configuration for customization.


Metadata and Artwork:

Extract and display metadata (e.g., Title, Artist, Album, Year) and technical details (e.g., Codec, Bitrate) using jaudiotagger.
Display album artwork and artist images, with full-screen viewing support.
Fetch artist images dynamically from TheAudioDB API with local caching.


Playback:

Music playback via a foreground MusicPlaybackService with notification controls (Play, Pause, Stop).
Seamless integration with the UI for playback controls.


UI/UX:

Navigation drawer for accessing Library and Settings.
Tabbed views for discs in albums (configurable in Settings).
Responsive layouts with Glide for efficient image loading.



Project Structure
The app follows a clean architecture approach with the following key components:

Activities:

LibraryActivity: Main entry point with filter tabs and playback controls.
SongsActivity: Displays songs for a selected album or artist with tab or list views.
FullScreenImageActivity: Full-screen viewer for album artwork or artist images.


Fragments:

FilterFragment: Displays filterable music lists.
DiscFragment: Shows songs for a specific disc in an album.
MetadataFragment: Displays metadata and technical details with tabs.
SettingsFragment, LibraryPathsFragment, FilterSettingsFragment: Manage app settings and library paths.
ImageViewerDialogFragment: Dialog for swipable image viewing.


Service:

MusicPlaybackService: Handles music playback with MediaPlayer and notifications.


Database:

Room database (AppDatabase) with tables for:
library_paths: Stores folder URIs and display names.
music_files: Stores music file metadata and technical details.
filters: Stores user-configured filter criteria.
artists: Caches artist names and image URLs.




Repository:

MusicRepository: Manages music file scanning and storage.
ArtistImageRepository: Handles artist image fetching and caching.


ViewModel:

MainViewModel: Manages app state, including music files, scan progress, and filters.



Tech Stack

Language: Kotlin 1.9.21
Build Tool: Gradle
Android SDK: Minimum API 27, Target API 34
Key Libraries:
Jetpack: Room, WorkManager, ViewModel, LiveData, Navigation
Glide: Image loading and caching
Retrofit: API calls to TheAudioDB
JAudiotagger: Metadata extraction
Coroutines: Asynchronous programming
OkHttp: Network operations



Setup
Prerequisites

Android Studio (latest stable version)
JDK 21
Android device/emulator with API 27 or higher

Installation

Clone the repository:git clone https://github.com/ansch190/Swan.git


Open the project in Android Studio.
Sync the project with Gradle to download dependencies.
Build and run the app on a device or emulator.

Permissions
The app requires the following permissions:

READ_MEDIA_AUDIO (API 33+): Access audio files.
READ_EXTERNAL_STORAGE (API < 33): Legacy storage access.
POST_NOTIFICATIONS: Display playback notifications.
INTERNET and ACCESS_NETWORK_STATE: Fetch artist images.

Usage

Launch the app and grant necessary permissions.
Add a music folder via Settings > Library Paths.
Wait for the scan to complete (progress shown).
Browse music using filters (e.g., Title, Artist) in the Library.
Tap a song to play, or long-press for metadata.
View album artwork or artist images in full-screen mode.

Development Guidelines

Code Style: Follow Kotlin coding conventions.
Commits: Use descriptive commit messages (e.g., "Fix artist image loading issue").
Testing:
Unit tests: Located in app/src/test (e.g., ExampleUnitTest).
Instrumented tests: Located in app/src/androidTest (e.g., ExampleInstrumentedTest).


Logging: Use Log.d, Log.w, Log.e with appropriate tags for debugging.

Known Issues

Large music libraries may slow down scanning (consider optimizing batch size in MusicRepository).
Network errors during artist image fetching need better retry handling.

Future Improvements

Features:
Add playlist creation and management.
Support for advanced search (e.g., by bitrate or file size).
Implement shuffle and repeat modes for playback.


Performance:
Optimize image loading with smaller thumbnails for lists.
Improve database queries with additional indexes.


UI/UX:
Add Dark Mode support.
Implement animations for smoother transitions.


Testing:
Expand unit tests for repositories and use cases.
Add UI tests for critical flows (e.g., library scanning, playback).



Contributing
Contributions are welcome! Please:

Fork the repository.
Create a feature branch (git checkout -b feature/xyz).
Commit changes (git commit -m "Add feature xyz").
Push to the branch (git push origin feature/xyz).
Open a pull request on GitHub.

License
This project is licensed under the MIT License. See the LICENSE file for details.
Contact
For questions or feedback, contact the maintainer at GitHub Issues.

Generated on May 10, 2025
