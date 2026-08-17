package com.schwanitz.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.StandardCopyOption
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val file = File(context.noBackupFilesDir, NEW_FILENAME)
    private val values = linkedMapOf<String, String>()

    init {
        if (file.exists()) values.putAll(readEncrypted()) else migrateLegacyStore()
    }

    @Synchronized
    fun save(sourceId: String, username: String, password: String) {
        values[key(sourceId, FIELD_USERNAME)] = username
        values[key(sourceId, FIELD_PASSWORD)] = password
        persist()
    }

    @Synchronized
    fun load(sourceId: String): Pair<String, String>? {
        val username = values[key(sourceId, FIELD_USERNAME)]
        val password = values[key(sourceId, FIELD_PASSWORD)]
        return if (username != null && password != null) username to password else null
    }

    @Synchronized
    fun delete(sourceId: String) {
        values.remove(key(sourceId, FIELD_USERNAME))
        values.remove(key(sourceId, FIELD_PASSWORD))
        persist()
    }

    @Synchronized
    fun clear() {
        values.clear()
        persist()
    }

    @Synchronized
    fun replaceAll(replacement: Map<String, String>) {
        values.clear()
        values.putAll(replacement)
        persist()
    }

    @Synchronized
    fun snapshot(): Map<String, String> = values.toMap()

    fun backupValues(
        credentials: Map<String, Pair<String, String>>,
        discogsKey: String?,
        discogsSecret: String?,
        lastfmKey: String?,
        geniusToken: String?,
    ): Map<String, String> = buildMap {
        credentials.forEach { (sourceId, credential) ->
            put(key(sourceId, FIELD_USERNAME), credential.first)
            put(key(sourceId, FIELD_PASSWORD), credential.second)
        }
        discogsKey?.let { put(API_DISCOGS_KEY, it) }
        discogsSecret?.let { put(API_DISCOGS_SECRET, it) }
        lastfmKey?.let { put(API_LASTFM_KEY, it) }
        geniusToken?.let { put(API_GENIUS_TOKEN, it) }
    }

    fun getApiDiscogsKey(): String? = get(API_DISCOGS_KEY)
    fun setApiDiscogsKey(value: String) = put(API_DISCOGS_KEY, value)
    fun getApiDiscogsSecret(): String? = get(API_DISCOGS_SECRET)
    fun setApiDiscogsSecret(value: String) = put(API_DISCOGS_SECRET, value)
    fun getApiLastfmKey(): String? = get(API_LASTFM_KEY)
    fun setApiLastfmKey(value: String) = put(API_LASTFM_KEY, value)
    fun getApiGeniusToken(): String? = get(API_GENIUS_TOKEN)
    fun setApiGeniusToken(value: String) = put(API_GENIUS_TOKEN, value)

    @Synchronized private fun get(name: String): String? = values[name]

    @Synchronized
    private fun put(name: String, value: String) {
        values[name] = value
        persist()
    }

    private fun persist() {
        val plain = JSONObject(values as Map<*, *>).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            // Android Keystore must generate a fresh IV for encryption. Supplying a
            // caller-generated IV is rejected when randomized encryption is required.
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val iv = cipher.iv
        check(iv.size == IV_LENGTH) { "Android Keystore returned an invalid GCM IV" }
        val temp = File(file.parentFile, "$NEW_FILENAME.tmp")
        temp.outputStream().use { output ->
            output.write(MAGIC)
            output.write(iv)
            output.write(cipher.doFinal(plain))
            output.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readEncrypted(): Map<String, String> {
        val bytes = file.readBytes()
        require(bytes.size > MAGIC.size + IV_LENGTH && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Invalid credential store"
        }
        val iv = bytes.copyOfRange(MAGIC.size, MAGIC.size + IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        val json = JSONObject(cipher.doFinal(bytes.copyOfRange(MAGIC.size + IV_LENGTH, bytes.size)).toString(Charsets.UTF_8))
        return json.keys().asSequence().associateWith(json::getString)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    @Suppress("DEPRECATION")
    private fun migrateLegacyStore() {
        val legacy = runCatching {
            EncryptedSharedPreferences.create(
                context,
                LEGACY_FILENAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            Timber.w(it, "Legacy credential store could not be opened")
            persist()
            return
        }
        values.putAll(legacy.all.mapNotNull { (name, value) -> (value as? String)?.let { name to it } })
        persist()
        check(legacy.edit().clear().commit()) { "Old credential store could not be cleared after migration" }
        Timber.i("Migrated %d credential values to Android Keystore storage", values.size)
    }

    private fun key(sourceId: String, field: String): String = "${sourceId}_$field"

    companion object {
        private val MAGIC = "SWANCRED2".toByteArray(Charsets.US_ASCII)
        private const val NEW_FILENAME = "credentials.v2"
        private const val LEGACY_FILENAME = "webdav_credentials"
        private const val KEY_ALIAS = "swan.credentials.v2"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FIELD_USERNAME = "username"
        private const val FIELD_PASSWORD = "password"
        private const val API_DISCOGS_KEY = "api_discogs_key"
        private const val API_DISCOGS_SECRET = "api_discogs_secret"
        private const val API_LASTFM_KEY = "api_lastfm_key"
        private const val API_GENIUS_TOKEN = "api_genius_token"
    }
}
