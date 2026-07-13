package com.schwanitz.ui.common

import com.schwanitz.domain.error.AppError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber

class ErrorHolder {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 5)
    val errors: SharedFlow<AppError> = _errors

    fun emit(error: AppError) {
        Timber.w("ErrorHolder emitting: %s", error.toUserMessage())
        _errors.tryEmit(error)
    }

    fun emit(throwable: Throwable, fallbackMessage: String = "An error occurred") {
        emit(AppError.from(throwable, fallbackMessage))
    }

    fun emitIfError(result: Result<*>, fallbackMessage: String = "An error occurred") {
        result.exceptionOrNull()?.let { emit(it, fallbackMessage) }
    }
}
