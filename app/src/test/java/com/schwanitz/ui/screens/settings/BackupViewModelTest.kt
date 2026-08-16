package com.schwanitz.ui.screens.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.schwanitz.data.backup.BackupManager
import com.schwanitz.data.backup.RestoreProgress
import com.schwanitz.data.backup.RestoreStage
import com.schwanitz.util.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.crypto.AEADBadTagException

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var backupManager: BackupManager
    private lateinit var uri: Uri
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        context = mockk()
        resolver = mockk()
        backupManager = mockk()
        uri = mockk()
        every { context.contentResolver } returns resolver
        viewModel = BackupViewModel(context, backupManager)
    }

    @Test
    fun `selected file requests a password`() {
        viewModel.selectImport(uri)

        assertEquals(RestoreUiState.AwaitingPassword(), viewModel.restoreState.value)
    }

    @Test
    fun `manager progress is exposed until successful completion`() = runTest {
        val finish = CompletableDeferred<Unit>()
        coEvery { backupManager.importAndRestore(resolver, uri, any(), any()) } coAnswers {
            arg<(RestoreProgress) -> Unit>(3)(
                RestoreProgress(RestoreStage.DECRYPTING, completedBytes = 50, totalBytes = 100)
            )
            finish.await()
        }
        viewModel.selectImport(uri)

        viewModel.startImport("correct password")

        val running = viewModel.restoreState.value as RestoreUiState.Running
        assertEquals(RestoreStage.DECRYPTING, running.progress.stage)
        assertEquals(50L, running.progress.completedBytes)
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(RestoreUiState.Idle, viewModel.restoreState.value)
    }

    @Test
    fun `wrong password returns to password request and retains selected file`() = runTest {
        coEvery { backupManager.importAndRestore(resolver, uri, any(), any()) } throws AEADBadTagException()
        viewModel.selectImport(uri)

        viewModel.startImport("wrong password")
        advanceUntilIdle()

        assertEquals(
            RestoreUiState.AwaitingPassword(RestoreError.WRONG_PASSWORD),
            viewModel.restoreState.value
        )
        viewModel.clearPasswordError()
        assertEquals(RestoreUiState.AwaitingPassword(), viewModel.restoreState.value)
    }

    @Test
    fun `general failure can retry with the same selected file`() = runTest {
        coEvery { backupManager.importAndRestore(resolver, uri, any(), any()) } throws IllegalStateException("broken")
        viewModel.selectImport(uri)
        viewModel.startImport("correct password")
        advanceUntilIdle()

        assertEquals(RestoreUiState.Failed(RestoreError.IMPORT_FAILED), viewModel.restoreState.value)
        viewModel.retryImport()
        assertEquals(RestoreUiState.AwaitingPassword(), viewModel.restoreState.value)
    }

    @Test
    fun `second submission cannot start a parallel restore`() = runTest {
        val finish = CompletableDeferred<Unit>()
        coEvery { backupManager.importAndRestore(resolver, uri, any(), any()) } coAnswers { finish.await() }
        viewModel.selectImport(uri)

        viewModel.startImport("first password")
        assertTrue(viewModel.restoreState.value is RestoreUiState.Running)
        viewModel.startImport("second password")

        coVerify(exactly = 1) { backupManager.importAndRestore(resolver, uri, any(), any()) }
        finish.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `closing password or failure clears pending restore`() = runTest {
        viewModel.selectImport(uri)
        viewModel.dismissPasswordRequest()
        assertEquals(RestoreUiState.Idle, viewModel.restoreState.value)

        coEvery { backupManager.importAndRestore(resolver, uri, any(), any()) } throws IllegalStateException()
        viewModel.selectImport(uri)
        viewModel.startImport("correct password")
        advanceUntilIdle()
        viewModel.dismissImportFailure()
        assertEquals(RestoreUiState.Idle, viewModel.restoreState.value)
        viewModel.retryImport()
        assertEquals(RestoreUiState.Idle, viewModel.restoreState.value)
    }
}
