package com.schwanitz.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.schwanitz.data.local.ArtistDataSourcePreferences
import com.schwanitz.data.local.CredentialStore
import com.schwanitz.data.local.LanguagePreferences
import com.schwanitz.data.local.dao.SourceConfigDao
import com.schwanitz.data.local.entity.SourceConfigEntity
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val sourceConfigDao: SourceConfigDao,
    private val credentialStore: CredentialStore,
    private val languagePreferences: LanguagePreferences,
    private val artistDataSourcePreferences: ArtistDataSourcePreferences
) {
    suspend fun createBackup(): BackupFile {
        val sourceEntities = sourceConfigDao.getAll().first()
        val allSourceIds = sourceEntities.map { it.id }

        val credentials = allSourceIds.associateWith { sourceId ->
            val (user, pass) = credentialStore.load(sourceId) ?: (null to null)
            BackupCredentials(username = user, password = pass)
        }

        return BackupFile(
            sources = sourceEntities.map { it.toBackup() },
            credentials = credentials,
            apiKeys = BackupApiKeys(
                discogsKey = credentialStore.getApiDiscogsKey(),
                discogsSecret = credentialStore.getApiDiscogsSecret(),
                lastfmKey = credentialStore.getApiLastfmKey(),
                geniusToken = credentialStore.getApiGeniusToken()
            ),
            languageCode = languagePreferences.getLanguageSync(),
            artistDataSource = BackupArtistDataSource(
                sourceId = artistDataSourcePreferences.getSourceIdSync(),
                basePath = artistDataSourcePreferences.getBasePathSync()
            )
        )
    }

    suspend fun restore(backup: BackupFile) {
        for (source in backup.sources) {
            sourceConfigDao.upsert(source.toEntity())
        }

        for ((sourceId, creds) in backup.credentials) {
            if (creds.username != null && creds.password != null) {
                credentialStore.save(sourceId, creds.username, creds.password)
            }
        }

        backup.apiKeys.discogsKey?.let { credentialStore.setApiDiscogsKey(it) }
        backup.apiKeys.discogsSecret?.let { credentialStore.setApiDiscogsSecret(it) }
        backup.apiKeys.lastfmKey?.let { credentialStore.setApiLastfmKey(it) }
        backup.apiKeys.geniusToken?.let { credentialStore.setApiGeniusToken(it) }

        languagePreferences.setLanguage(backup.languageCode)

        artistDataSourcePreferences.setSourceId(backup.artistDataSource.sourceId)
        artistDataSourcePreferences.setBasePath(backup.artistDataSource.basePath)

        Timber.d("Backup restored successfully, %d sources", backup.sources.size)
    }

    fun exportTo(contentResolver: ContentResolver, uri: Uri, backup: BackupFile, password: String) {
        val json = backup.toJson().toString(2).toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(json, password)
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(encrypted)
        }
    }

    fun importFrom(contentResolver: ContentResolver, uri: Uri, password: String): BackupFile {
        val encrypted = contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        } ?: throw IllegalStateException("Cannot read backup file")

        val json = decrypt(encrypted, password)
        val jsonObject = org.json.JSONObject(json.toString(Charsets.UTF_8))
        return BackupFile.fromJson(jsonObject)
    }

    private fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val ciphertext = cipher.doFinal(data)
        return ByteArrayOutputStream().apply {
            write(salt)
            write(iv)
            write(ciphertext)
        }.toByteArray()
    }

    private fun decrypt(encrypted: ByteArray, password: String): ByteArray {
        var offset = 0
        val salt = encrypted.copyOfRange(offset, SALT_LENGTH).also { offset += SALT_LENGTH }
        val iv = encrypted.copyOfRange(offset, offset + IV_LENGTH).also { offset += IV_LENGTH }
        val ciphertext = encrypted.copyOfRange(offset, encrypted.size)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val rawKey = factory.generateSecret(spec).encoded
        return SecretKeySpec(rawKey, "AES")
    }

    companion object {
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_LENGTH_BITS = 256
        private const val ITERATION_COUNT = 100_000
        private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

private fun SourceConfigEntity.toBackup() = BackupSource(
    id = id,
    name = name,
    type = type,
    isEnabled = isEnabled,
    folderUri = folderUri,
    url = url,
    path = path
)

private fun BackupSource.toEntity() = SourceConfigEntity(
    id = id,
    name = name,
    type = type,
    isEnabled = isEnabled,
    folderUri = folderUri,
    url = url,
    path = path
)
