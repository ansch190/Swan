package com.schwanitz.swan.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.schwanitz.swan.data.local.dao.ArtistDao
import com.schwanitz.swan.data.local.dao.FilterDao
import com.schwanitz.swan.data.local.dao.LibraryPathDao
import com.schwanitz.swan.data.local.dao.MusicFileDao
import com.schwanitz.swan.data.local.dao.PlaylistDao
import com.schwanitz.swan.data.local.entity.*

@Database(
    entities = [
        LibraryPathEntity::class,
        MusicFileEntity::class,
        FilterEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryPathDao(): LibraryPathDao
    abstract fun musicFileDao(): MusicFileDao
    abstract fun filterDao(): FilterDao
    abstract fun artistDao(): ArtistDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "swan_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX index_music_files_libraryPathUri ON music_files(libraryPathUri)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS filters (criterion TEXT NOT NULL, displayName TEXT NOT NULL, PRIMARY KEY(criterion))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS artists (artistName TEXT NOT NULL, imageUrl TEXT, PRIMARY KEY(artistName))")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS playlists (id TEXT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playlist_songs (
                        playlistId TEXT NOT NULL,
                        songUri TEXT NOT NULL,
                        PRIMARY KEY(playlistId, songUri),
                        FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE,
                        FOREIGN KEY(songUri) REFERENCES music_files(uri) ON DELETE CASCADE
                    )
                    """
                )
                db.execSQL("CREATE INDEX index_playlist_songs_songUri ON playlist_songs(songUri)")
            }
        }
    }
}