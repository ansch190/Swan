package com.schwanitz.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryOperationCoordinator @Inject constructor() {
    private val mutex = Mutex()

    @Volatile
    var restoreRequested: Boolean = false
        private set

    suspend fun <T> withScan(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun <T> withExport(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun <T> withRestore(
        onRequested: suspend () -> Unit = {},
        block: suspend () -> T,
    ): T {
        restoreRequested = true
        return try {
            onRequested()
            mutex.withLock { block() }
        } finally {
            restoreRequested = false
        }
    }
}
