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
                CREATE VIEW IF NOT EXISTS `SongWithNames` AS
                SELECT s.id, s.title, s.artistId, asm.albumId,
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

    val all = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
