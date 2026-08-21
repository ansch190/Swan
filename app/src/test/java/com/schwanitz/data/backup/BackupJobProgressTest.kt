package com.schwanitz.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class BackupJobProgressTest {
    @Test
    fun `stream close failure is classified as finalization failure`() {
        val failure = classifyBackupExportFailure(
            IOException("provider rejected close"),
            BackupJobStage.FINALIZING,
        )

        assertEquals(BackupFailureCode.FINALIZATION_FAILED, failure.first)
        assertEquals("IOException", failure.second)
    }

    @Test
    fun `specific verification failure survives worker classification`() {
        val failure = classifyBackupExportFailure(
            BackupExportException(BackupFailureCode.VERIFICATION_FAILED),
            BackupJobStage.FINALIZING,
        )

        assertEquals(BackupFailureCode.VERIFICATION_FAILED, failure.first)
    }

    @Test
    fun `all configured song sources are accepted`() {
        validateBackupSourceCoverage(
            configuredSourceIds = setOf("local", "webdav", "smb"),
            songSourceIds = setOf("local", "webdav", "smb"),
        )
    }

    @Test
    fun `orphan song source aborts export with specific failure`() {
        val error = assertThrows(BackupExportException::class.java) {
            validateBackupSourceCoverage(
                configuredSourceIds = setOf("local", "smb"),
                songSourceIds = setOf("local", "missing"),
            )
        }

        assertEquals(BackupFailureCode.SOURCE_MISMATCH, error.failureCode)
        assertEquals("missing", error.safeDetail)
    }

    @Test
    fun `byte progress is bounded`() {
        assertEquals(
            0.5f,
            BackupJobProgress(
                BackupOperation.EXPORT,
                BackupJobStage.EXPORTING_ASSETS,
                completedBytes = 50,
                totalBytes = 100,
            ).fraction,
        )
        assertEquals(
            1f,
            BackupJobProgress(
                BackupOperation.EXPORT,
                BackupJobStage.EXPORTING_ASSETS,
                completedBytes = 120,
                totalBytes = 100,
            ).fraction,
        )
    }

    @Test
    fun `item progress is used when byte total is unavailable`() {
        assertEquals(
            0.25f,
            BackupJobProgress(
                BackupOperation.EXPORT,
                BackupJobStage.EXPORTING_LIBRARY,
                completedItems = 1,
                totalItems = 4,
            ).fraction,
        )
    }

    @Test
    fun `unknown total has no artificial fraction`() {
        assertNull(
            BackupJobProgress(
                BackupOperation.RESTORE,
                BackupJobStage.PREPARING_KEY,
            ).fraction,
        )
    }

    @Test
    fun `reporter never moves counters backwards within a stage`() {
        val reported = mutableListOf<BackupJobProgress>()
        var time = 0L
        val reporter = BackupProgressReporter(reported::add) { time }
        reporter.report(
            BackupJobProgress(
                BackupOperation.EXPORT,
                BackupJobStage.EXPORTING_ASSETS,
                completedBytes = 80,
                totalBytes = 100,
                completedItems = 2,
                totalItems = 4,
            ),
            force = true,
        )
        time += 200_000_000L
        reporter.report(
            BackupJobProgress(
                BackupOperation.EXPORT,
                BackupJobStage.EXPORTING_ASSETS,
                completedBytes = 60,
                totalBytes = 100,
                completedItems = 1,
                totalItems = 4,
            )
        )

        assertEquals(80L, reported.last().completedBytes)
        assertEquals(2, reported.last().completedItems)
    }
}
