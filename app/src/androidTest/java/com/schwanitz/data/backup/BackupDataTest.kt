package com.schwanitz.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDataTest {
    private fun backup(options: BackupOptions) = BackupFile(
        sources = emptyList(),
        credentials = emptyMap(),
        apiKeys = BackupApiKeys(null, null, null, null),
        languageCode = "de",
        artistDataSource = BackupArtistDataSource(null, ""),
        hideCredentialsAfterRestore = options.hideCredentialsAfterRestore,
        includeLibrary = options.includeLibrary
    )

    @Test
    fun v2OptionsRoundTripIndependently() {
        val restored = BackupFile.fromJson(backup(BackupOptions(true, false)).toJson())
        assertTrue(restored.hideCredentialsAfterRestore)
        assertFalse(restored.includeLibrary)

        val libraryOnly = BackupFile.fromJson(backup(BackupOptions(false, true)).toJson())
        assertFalse(libraryOnly.hideCredentialsAfterRestore)
        assertTrue(libraryOnly.includeLibrary)
    }

    @Test
    fun v1SharedFlagRemainsImportCompatible() {
        val legacy = backup(BackupOptions()).toJson().apply {
            put("version", 1)
            remove("hideCredentialsAfterRestore")
            remove("includeLibrary")
            put("isShared", true)
        }
        val restored = BackupFile.fromJson(JSONObject(legacy.toString()))
        assertTrue(restored.hideCredentialsAfterRestore)
        assertFalse(restored.includeLibrary)
    }
}
