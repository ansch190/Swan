package com.schwanitz.ui.screens.settings

import android.net.Uri
import androidx.work.WorkInfo
import com.schwanitz.data.backup.BackupJobCoordinator
import com.schwanitz.data.backup.BackupOperation
import com.schwanitz.data.backup.BackupWorkState
import com.schwanitz.data.backup.BackupWorker
import com.schwanitz.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var coordinator: BackupJobCoordinator
    private lateinit var uri: Uri
    private lateinit var work: MutableStateFlow<BackupWorkState?>
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        coordinator = mockk(relaxed = true)
        uri = mockk()
        work = MutableStateFlow(null)
        every { coordinator.workState } returns work
        coEvery { coordinator.enqueueRestore(any(), any()) } returns true
        coEvery { coordinator.enqueueExport(any(), any(), any()) } returns true
        viewModel = BackupViewModel(coordinator)
    }

    @Test
    fun `selected file requests a password`() {
        viewModel.selectImport(uri)
        assertEquals(RestoreUiState.AwaitingPassword(), viewModel.restoreState.value)
    }

    @Test
    fun `password submission enqueues restore and closes prompt`() = runTest {
        viewModel.selectImport(uri)
        viewModel.startImport("correct password")
        advanceUntilIdle()

        assertEquals(RestoreUiState.Idle, viewModel.restoreState.value)
        coVerify(exactly = 1) { coordinator.enqueueRestore(uri, "correct password") }
    }

    @Test
    fun `wrong password returns to password request and retains selected file`() = runTest {
        viewModel.selectImport(uri)
        viewModel.startImport("wrong password")
        work.value = finished(BackupWorker.ERROR_WRONG_PASSWORD)
        advanceUntilIdle()

        assertEquals(
            RestoreUiState.AwaitingPassword(RestoreError.WRONG_PASSWORD),
            viewModel.restoreState.value,
        )
        verify { coordinator.acknowledge(any(), releasePermission = false) }
    }

    @Test
    fun `general failure can retry with the same selected file`() = runTest {
        viewModel.selectImport(uri)
        viewModel.startImport("correct password")
        work.value = finished(BackupWorker.ERROR_FAILED)
        advanceUntilIdle()

        assertEquals(RestoreUiState.Failed(RestoreError.IMPORT_FAILED), viewModel.restoreState.value)
        viewModel.retryImport()
        assertEquals(RestoreUiState.AwaitingPassword(), viewModel.restoreState.value)
    }

    @Test
    fun `second submission cannot enqueue a parallel restore`() = runTest {
        viewModel.selectImport(uri)
        viewModel.startImport("first password")
        viewModel.startImport("second password")
        advanceUntilIdle()

        coVerify(exactly = 1) { coordinator.enqueueRestore(uri, "first password") }
    }

    @Test
    fun `closing password clears pending restore and releases permission`() {
        viewModel.selectImport(uri)
        viewModel.dismissPasswordRequest()

        assertEquals(RestoreUiState.Idle, viewModel.restoreState.value)
        verify { coordinator.releasePermission(uri, BackupOperation.RESTORE) }
    }

    private fun finished(error: String) = BackupWorkState(
        workId = "00000000-0000-0000-0000-000000000001",
        jobId = "job",
        operation = BackupOperation.RESTORE,
        state = WorkInfo.State.FAILED,
        progress = null,
        error = error,
        failureCode = null,
        failureDetail = null,
        uri = uri,
    )
}
