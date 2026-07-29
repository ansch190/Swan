package com.schwanitz.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "shared_import")

@Singleton
class SharedImportPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val hiddenSourceIdsKey = stringSetPreferencesKey("hidden_source_ids")
    private val apiKeysHiddenKey = booleanPreferencesKey("api_keys_hidden")

    val hiddenSourceIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[hiddenSourceIdsKey] ?: emptySet()
    }

    val areApiKeysHidden: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[apiKeysHiddenKey] ?: false
    }

    suspend fun setHiddenSourceIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[hiddenSourceIdsKey] = ids
        }
    }

    suspend fun setApiKeysHidden(hidden: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[apiKeysHiddenKey] = hidden
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(hiddenSourceIdsKey)
            prefs.remove(apiKeysHiddenKey)
        }
    }

    suspend fun isSourceIdHidden(sourceId: String): Boolean {
        return context.dataStore.data.first()[hiddenSourceIdsKey]?.contains(sourceId) ?: false
    }

    suspend fun isAnyApiKeysHidden(): Boolean {
        return context.dataStore.data.first()[apiKeysHiddenKey] ?: false
    }
}
