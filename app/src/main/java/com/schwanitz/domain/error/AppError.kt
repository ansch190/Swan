package com.schwanitz.domain.error

import androidx.annotation.StringRes
import com.schwanitz.R

sealed class AppError {
    abstract val message: String

    data class Network(override val message: String, val cause: Throwable? = null) : AppError()
    data class Database(override val message: String, val cause: Throwable? = null) : AppError()
    data class Source(val sourceName: String, override val message: String) : AppError()
    data class Playback(override val message: String, val cause: Throwable? = null) : AppError()
    data class Io(override val message: String, val cause: Throwable? = null) : AppError()
    data class Unknown(override val message: String, val cause: Throwable? = null) : AppError()

    @StringRes
    fun fallbackStringRes(): Int = when (this) {
        is Network -> R.string.error_network
        is Database -> R.string.error_database
        is Source -> R.string.error_source
        is Playback -> R.string.error_playback
        is Io -> R.string.error_io
        is Unknown -> R.string.error_unknown
    }

    companion object {
        fun network(cause: Throwable? = null, message: String = "Network error") =
            Network(message = message, cause = cause)

        fun database(cause: Throwable? = null, message: String = "Database error") =
            Database(message = message, cause = cause)

        fun source(sourceName: String, message: String = "Source error: $sourceName") =
            Source(sourceName = sourceName, message = message)

        fun playback(cause: Throwable? = null, message: String = "Playback error") =
            Playback(message = message, cause = cause)

        fun io(cause: Throwable? = null, message: String = "Storage error") =
            Io(message = message, cause = cause)

        fun unknown(cause: Throwable? = null, message: String = "An error occurred") =
            Unknown(message = message, cause = cause)

        fun from(throwable: Throwable, fallback: String = "An error occurred"): AppError {
            val msg = throwable.message ?: fallback
            return when (throwable) {
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.io.IOException -> network(cause = throwable, message = msg)
                is OutOfMemoryError -> io(cause = throwable, message = "Out of memory")
                else -> unknown(cause = throwable, message = msg)
            }
        }
    }
}
