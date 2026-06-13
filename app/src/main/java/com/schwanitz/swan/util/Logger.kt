package com.schwanitz.swan.util

import android.util.Log
import com.schwanitz.swan.BuildConfig

/** Centralized logging that is stripped in release builds. */

object Logger {
    private val isDebug get() = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (isDebug) Log.d(tag, message)
    }

    fun d(tag: String, message: () -> String) {
        if (isDebug) Log.d(tag, message())
    }

    fun i(tag: String, message: String) {
        if (isDebug) Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        if (isDebug) Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebug) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
