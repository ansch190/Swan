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
    version = 1,
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
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}