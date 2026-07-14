package com.schwanitz.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILENAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(sourceId: String, username: String, password: String) {
        prefs.edit()
            .putString(key(sourceId, FIELD_USERNAME), username)
            .putString(key(sourceId, FIELD_PASSWORD), password)
            .apply()
        Timber.d("Credentials saved for source %s", sourceId)
    }

    fun load(sourceId: String): Pair<String, String>? {
        val username = prefs.getString(key(sourceId, FIELD_USERNAME), null)
        val password = prefs.getString(key(sourceId, FIELD_PASSWORD), null)
        return if (username != null && password != null) username to password else null
    }

    fun delete(sourceId: String) {
        prefs.edit()
            .remove(key(sourceId, FIELD_USERNAME))
            .remove(key(sourceId, FIELD_PASSWORD))
            .apply()
        Timber.d("Credentials deleted for source %s", sourceId)
    }

    private fun key(sourceId: String, field: String): String = "${sourceId}_$field"

    companion object {
        private const val FILENAME = "webdav_credentials"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PASSWORD = "password"
    }
}
