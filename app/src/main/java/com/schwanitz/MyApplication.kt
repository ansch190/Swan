package com.schwanitz

import android.app.Application
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.schwanitz.data.local.LanguagePreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class EscalatingTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        android.util.Log.e(tag ?: "Timber", message, t)
    }
}

class FileLoggingTree(private val logDir: File) : Timber.Tree() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logFile get() = File(logDir, "swan_log.txt")

    init {
        logDir.mkdirs()
        logFile.writeText("=== Swan Log Start ===\n")
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            android.util.Log.ERROR -> "E"
            android.util.Log.WARN -> "W"
            android.util.Log.INFO -> "I"
            android.util.Log.DEBUG -> "D"
            android.util.Log.VERBOSE -> "V"
            else -> "?"
        }
        val timestamp = dateFormat.format(Date())
        val line = "$timestamp $level/${tag ?: "Timber"}: $message\n"
        try {
            FileWriter(logFile, true).use { it.write(line) }
            if (t != null) {
                FileWriter(logFile, true).use { fw ->
                    PrintWriter(fw).use { pw -> t.printStackTrace(pw) }
                }
            }
        } catch (_: Exception) {
            // silent — can't log logging errors
        }
    }
}

@HiltAndroidApp
class MyApplication : Application() {

    @Inject
    lateinit var languagePreferences: LanguagePreferences

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(EscalatingTree())
            val logDir = getExternalFilesDir(null)
            if (logDir != null) {
                Timber.plant(FileLoggingTree(logDir))
                Timber.e("File logging started at %s", logDir.absolutePath)
            }
        }
        applySavedLanguage()
    }

    private fun applySavedLanguage() {
        val code = runBlocking { languagePreferences.getLanguageSync() }
        if (code != LanguagePreferences.SYSTEM_DEFAULT) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
        }
    }
}
