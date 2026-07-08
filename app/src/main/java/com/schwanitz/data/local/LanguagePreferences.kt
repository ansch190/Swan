package com.schwanitz.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class LanguagePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val languageKey = stringPreferencesKey("language_code")

    fun getLanguage(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[languageKey] ?: SYSTEM_DEFAULT
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { prefs ->
            if (code == SYSTEM_DEFAULT) {
                prefs.remove(languageKey)
            } else {
                prefs[languageKey] = code
            }
        }
    }

    suspend fun getLanguageSync(): String = context.dataStore.data.first()[languageKey] ?: SYSTEM_DEFAULT

    companion object {
        const val SYSTEM_DEFAULT = "system"
        const val GERMAN = "de"
        const val ENGLISH = "en"
    }
}
