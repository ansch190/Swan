package com.schwanitz.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration6To7_deduplicatesAlbumsAndCreatesScanTables() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL("INSERT INTO albums (id, name, albumArtist, year) VALUES (10, 'Same', 'Artist', 2020)")
            execSQL("INSERT INTO albums (id, name, albumArtist, year) VALUES (11, 'Same', 'Artist', 2020)")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, Migrations.MIGRATION_6_7).use { db ->
            assertEquals(1, db.longQuery("SELECT COUNT(*) FROM albums WHERE name = 'Same'") )
            assertEquals(10, db.longQuery("SELECT id FROM albums WHERE name = 'Same'"))
            assertEquals(1, db.longQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='scan_sessions'"))
        }
    }

    @Test
    fun migration1To7_validatesCompleteChain() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(TEST_DB, 7, true, *Migrations.all).close()
    }

    private fun SupportSQLiteDatabase.longQuery(sql: String): Long =
        query(sql).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

    private companion object { const val TEST_DB = "migration-7-test" }
}
