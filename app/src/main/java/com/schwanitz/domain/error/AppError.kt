package com.schwanitz.domain.error

import com.schwanitz.R

sealed class AppError {
    data class Network(val message: String, val cause: Throwable? = null) : AppError()
    data class Database(val message: String, val cause: Throwable? = null) : AppError()
    data class Source(val sourceName: String, val message: String) : AppError()
    data class Playback(val message: String, val cause: Throwable? = null) : AppError()
    data class Io(val message: String, val cause: Throwable? = null) : AppError()
    data class Unknown(val message: String, val cause: Throwable? = null) : AppError()

    fun toUserMessage(): String = when (this) {
        is Network -> message.ifBlank { "Network error" }
        is Database -> message.ifBlank { "Database error" }
        is Source -> message.ifBlank { "Source error: $sourceName" }
        is Playback -> message.ifBlank { "Playback error" }
        is Io -> message.ifBlank { "Storage error" }
        is Unknown -> message.ifBlank { "An error occurred" }
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
