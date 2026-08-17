package com.schwanitz.data.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupJobSecretStoreTest {
    @Test
    fun secretIsEncryptedAndDeletedByJobId() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = BackupJobSecretStore(context)
        val jobId = "00000000-0000-0000-0000-000000000099"
        val password = "correct horse battery staple"

        store.save(jobId, password)

        assertEquals(password, store.load(jobId))
        val storedBytes = File(context.noBackupFilesDir, "backup-jobs/$jobId.secret").readBytes()
        assertFalse(storedBytes.toString(Charsets.UTF_8).contains(password))

        store.delete(jobId)
        assertFalse(File(context.noBackupFilesDir, "backup-jobs/$jobId.secret").exists())
    }

    @Test
    fun cleanupKeepsOnlyActiveSecrets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = BackupJobSecretStore(context)
        val active = "00000000-0000-0000-0000-000000000101"
        val orphan = "00000000-0000-0000-0000-000000000102"
        store.save(active, "123456789012")
        store.save(orphan, "abcdefghijkl")

        store.cleanupExcept(setOf(active))

        assertEquals("123456789012", store.load(active))
        assertTrue(File(context.noBackupFilesDir, "backup-jobs/$active.secret").exists())
        assertFalse(File(context.noBackupFilesDir, "backup-jobs/$orphan.secret").exists())
        store.delete(active)
    }
}
