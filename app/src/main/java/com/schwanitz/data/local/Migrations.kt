package com.schwanitz.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_album_series_name ON album_series(name)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE source_configs DROP COLUMN username")
            db.execSQL("ALTER TABLE source_configs DROP COLUMN password")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE VIEW IF NOT EXISTS `SongWithNames` AS SELECT s.id, s.title, s.artistId, asm.albumId,
                        s.durationMs, s.sourceId, s.isFavorite, s.isActive,
                        asm.discNumber, asm.trackNumber,
                        al.year, s.genre, s.tagVersion,
                        sti.mimeType, sti.sampleRate, sti.bitrate, sti.fileSize,
                        a.name as artistName,
                        al.name as albumName,
                        aw.uriSmall as albumArtUri,
                        aw.uriLarge as albumArtUriLarge,
                        al.albumArtist as albumArtistName
                    FROM songs s
                    INNER JOIN album_song_mapping asm ON s.id = asm.songId
                    LEFT JOIN artists a ON s.artistId = a.id
                    LEFT JOIN albums al ON asm.albumId = al.id
                    LEFT JOIN album_artwork aw ON asm.albumId = aw.albumId AND aw.sortOrder = 0
                    LEFT JOIN song_technical_info sti ON s.id = sti.songId
            """.trimIndent())
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS index_source_configs_url")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_source_configs_url_path ON source_configs(url, path)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE playlist_song_mapping_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    playlistId INTEGER NOT NULL,
                    songId TEXT NOT NULL,
                    orderIndex INTEGER NOT NULL,
                    FOREIGN KEY (playlistId) REFERENCES playlists(id) ON DELETE CASCADE,
                    FOREIGN KEY (songId) REFERENCES songs(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO playlist_song_mapping_new (playlistId, songId, orderIndex)
                SELECT playlistId, songId, orderIndex FROM playlist_song_mapping
            """.trimIndent())
            db.execSQL("DROP TABLE playlist_song_mapping")
            db.execSQL("ALTER TABLE playlist_song_mapping_new RENAME TO playlist_song_mapping")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_song_mapping_songId ON playlist_song_mapping(songId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_song_mapping_playlistId ON playlist_song_mapping(playlistId)")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                INSERT OR IGNORE INTO album_song_mapping (songId, albumId, trackNumber, discNumber)
                SELECT m.songId,
                    (SELECT MIN(a2.id) FROM albums a2
                     WHERE a2.name = a.name AND a2.albumArtist = a.albumArtist AND a2.year = a.year),
                    m.trackNumber, m.discNumber
                FROM album_song_mapping m
                INNER JOIN albums a ON a.id = m.albumId
                WHERE m.albumId != (SELECT MIN(a2.id) FROM albums a2
                    WHERE a2.name = a.name AND a2.albumArtist = a.albumArtist AND a2.year = a.year)
            """.trimIndent())
            db.execSQL("""
                INSERT OR IGNORE INTO album_artwork (albumId, sortOrder, uriLarge, uriSmall)
                SELECT (SELECT MIN(a2.id) FROM albums a2
                        WHERE a2.name = a.name AND a2.albumArtist = a.albumArtist AND a2.year = a.year),
                    aw.sortOrder, aw.uriLarge, aw.uriSmall
                FROM album_artwork aw
                INNER JOIN albums a ON a.id = aw.albumId
                WHERE aw.albumId != (SELECT MIN(a2.id) FROM albums a2
                    WHERE a2.name = a.name AND a2.albumArtist = a.albumArtist AND a2.year = a.year)
            """.trimIndent())
            db.execSQL("""
                UPDATE album_series_mapping
                SET albumId = (SELECT MIN(a2.id) FROM albums a2
                    WHERE a2.name = (SELECT name FROM albums WHERE id = album_series_mapping.albumId)
                      AND a2.albumArtist = (SELECT albumArtist FROM albums WHERE id = album_series_mapping.albumId)
                      AND a2.year = (SELECT year FROM albums WHERE id = album_series_mapping.albumId))
            """.trimIndent())
            db.execSQL("""
                DELETE FROM albums WHERE id NOT IN (
                    SELECT MIN(id) FROM albums GROUP BY name, albumArtist, year
                )
            """.trimIndent())
            db.execSQL("DROP INDEX IF EXISTS index_albums_name_albumArtist_year")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_albums_identity ON albums(name, albumArtist, year)")

            db.execSQL("CREATE TABLE IF NOT EXISTS scan_sessions (`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS scan_discovered (`sessionId` TEXT NOT NULL, `songId` TEXT NOT NULL, PRIMARY KEY(`sessionId`, `songId`), FOREIGN KEY(`sessionId`) REFERENCES `scan_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_discovered_sessionId ON scan_discovered(sessionId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS scan_songs (
                    `sessionId` TEXT NOT NULL, `songId` TEXT NOT NULL, `title` TEXT NOT NULL,
                    `artistName` TEXT NOT NULL, `albumName` TEXT NOT NULL, `albumArtist` TEXT NOT NULL,
                    `durationMs` INTEGER NOT NULL, `sourceId` TEXT NOT NULL, `discNumber` INTEGER NOT NULL,
                    `trackNumber` INTEGER NOT NULL, `year` INTEGER NOT NULL, `genre` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL, `sampleRate` INTEGER NOT NULL, `bitrate` INTEGER NOT NULL,
                    `fileSize` INTEGER NOT NULL, `tagVersion` TEXT NOT NULL,
                    PRIMARY KEY(`sessionId`, `songId`),
                    FOREIGN KEY(`sessionId`) REFERENCES `scan_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_songs_sessionId ON scan_songs(sessionId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS scan_artworks (
                    `sessionId` TEXT NOT NULL, `albumName` TEXT NOT NULL, `albumArtist` TEXT NOT NULL,
                    `year` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `uriLarge` TEXT NOT NULL,
                    `uriSmall` TEXT, PRIMARY KEY(`sessionId`, `albumName`, `albumArtist`, `year`, `sortOrder`),
                    FOREIGN KEY(`sessionId`) REFERENCES `scan_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_artworks_sessionId ON scan_artworks(sessionId)")
        }
    }

    val all = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

    fun migrateCredentialsToEncryptedStore(context: Context, store: CredentialStore) {
        val dbPath = context.getDatabasePath("music_player_db")
        if (!dbPath.exists()) return

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(
                dbPath.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            val cursor = db.rawQuery("SELECT id, username, password FROM source_configs", null)
            cursor.use {
                var count = 0
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow("id"))
                    val username = it.getString(it.getColumnIndexOrThrow("username"))
                    val password = it.getString(it.getColumnIndexOrThrow("password"))
                    if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                        store.save(id, username, password)
                        count++
                    }
                }
                Timber.i("Migrated %d source credentials to EncryptedSharedPreferences", count)
            }
        } catch (e: Exception) {
            Timber.w(e, "Credential pre-migration skipped (columns may not exist yet)")
        } finally {
            db?.close()
        }
    }
}
