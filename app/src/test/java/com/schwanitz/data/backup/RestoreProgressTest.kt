package com.schwanitz.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoreProgressTest {

    @Test
    fun `fraction is only available for a positive known total`() {
        assertNull(RestoreProgress(RestoreStage.DECRYPTING).fraction)
        assertNull(RestoreProgress(RestoreStage.DECRYPTING, totalBytes = 0).fraction)
        assertEquals(
            0.5f,
            RestoreProgress(RestoreStage.DECRYPTING, completedBytes = 50, totalBytes = 100).fraction
        )
        assertEquals(
            1f,
            RestoreProgress(RestoreStage.DECRYPTING, completedBytes = 150, totalBytes = 100).fraction
        )
    }

    @Test
    fun `reporter emits stage changes throttles intermediate updates and always emits completion`() {
        var time = 0L
        val emitted = mutableListOf<RestoreProgress>()
        val reporter = RestoreProgressReporter(emitted::add) { time }

        reporter.report(RestoreProgress(RestoreStage.DECRYPTING, 0, 100))
        reporter.report(RestoreProgress(RestoreStage.DECRYPTING, 10, 100))
        time = 100_000_000L
        reporter.report(RestoreProgress(RestoreStage.DECRYPTING, 20, 100))
        reporter.report(RestoreProgress(RestoreStage.DECRYPTING, 100, 100))
        reporter.report(RestoreProgress(RestoreStage.VALIDATING, 0, 200))

        assertEquals(listOf(0L, 20L, 100L, 0L), emitted.map { it.completedBytes })
        assertEquals(RestoreStage.VALIDATING, emitted.last().stage)
    }
}
