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

private val Context.dataStore by preferencesDataStore(name = "artist_data")

@Singleton
class ArtistDataSourcePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sourceIdKey = stringPreferencesKey("artist_data_source_id")
    private val basePathKey = stringPreferencesKey("artist_data_base_path")

    fun getSourceId(): Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[sourceIdKey]
    }

    fun getBasePath(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[basePathKey] ?: DEFAULT_BASE_PATH
    }

    suspend fun getSourceIdSync(): String? = context.dataStore.data.first()[sourceIdKey]

    suspend fun getBasePathSync(): String = context.dataStore.data.first()[basePathKey] ?: DEFAULT_BASE_PATH

    suspend fun setSourceId(sourceId: String?) {
        context.dataStore.edit { prefs ->
            if (sourceId != null) {
                prefs[sourceIdKey] = sourceId
            } else {
                prefs.remove(sourceIdKey)
            }
        }
    }

    suspend fun setBasePath(path: String) {
        context.dataStore.edit { prefs ->
            prefs[basePathKey] = path
        }
    }

    companion object {
        const val DEFAULT_BASE_PATH = "/Künstler"
    }
}
