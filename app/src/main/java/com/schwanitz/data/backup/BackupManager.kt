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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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

    fun exportTo(contentResolver: ContentResolver, uri: Uri, backup: BackupFile) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(backup.toJson().toString(2))
            }
        }
    }

    fun importFrom(contentResolver: ContentResolver, uri: Uri): BackupFile {
        val json = contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        } ?: throw IllegalStateException("Cannot read backup file")

        val jsonObject = org.json.JSONObject(json)
        return BackupFile.fromJson(jsonObject)
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
