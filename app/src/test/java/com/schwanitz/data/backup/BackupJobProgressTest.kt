package com.schwanitz.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupJobProgressTest {
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
