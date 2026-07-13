package com.schwanitz

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.schwanitz.data.local.LanguagePreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {

    @Inject
    lateinit var languagePreferences: LanguagePreferences

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
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
