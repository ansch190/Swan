package com.schwanitz.data.backup

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.schwanitz.data.local.AppDatabase
import com.schwanitz.data.local.ArtistDataSourcePreferences
import com.schwanitz.data.local.CredentialStore
import com.schwanitz.data.local.LanguagePreferences
import com.schwanitz.data.local.SharedImportPreferences
import com.schwanitz.data.local.dao.SourceConfigDao
import com.schwanitz.data.local.entity.SourceConfigEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val sourceConfigDao: SourceConfigDao,
    private val credentialStore: CredentialStore,
    private val languagePreferences: LanguagePreferences,
    private val artistDataSourcePreferences: ArtistDataSourcePreferences,
    private val sharedImportPreferences: SharedImportPreferences
) {
    suspend fun createBackup(options: BackupOptions = BackupOptions()): BackupFile {
        val sourceEntities = sourceConfigDao.getAll().first()
        return BackupFile(
            sources = sourceEntities.map { it.toBackup() },
            credentials = sourceEntities.associate { entity ->
                val (user, pass) = credentialStore.load(entity.id) ?: (null to null)
                entity.id to BackupCredentials(user, pass)
            },
            apiKeys = BackupApiKeys(
                credentialStore.getApiDiscogsKey(),
                credentialStore.getApiDiscogsSecret(),
                credentialStore.getApiLastfmKey(),
                credentialStore.getApiGeniusToken()
            ),
            languageCode = languagePreferences.getLanguageSync(),
            artistDataSource = BackupArtistDataSource(
                artistDataSourcePreferences.getSourceIdSync(),
                artistDataSourcePreferences.getBasePathSync()
            ),
            hideCredentialsAfterRestore = options.hideCredentialsAfterRestore,
            includeLibrary = options.includeLibrary
        )
    }

    suspend fun exportTo(
        contentResolver: ContentResolver,
        uri: Uri,
        backup: BackupFile,
        password: String
    ) = withContext(Dispatchers.IO) {
        require(password.length >= MIN_PASSWORD_LENGTH) { "Backup password must contain at least 12 characters" }
        val salt = ByteArray(SALT_LENGTH).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_LENGTH).also(SecureRandom()::nextBytes)
        val output = checkNotNull(contentResolver.openOutputStream(uri)) { "Cannot write backup file" }
        output.use { raw ->
            DataOutputStream(raw).apply {
                write(MAGIC_V2)
                writeByte(FORMAT_VERSION)
                writeByte(KDF_VERSION)
                writeInt(V2_ITERATIONS)
                write(salt)
                write(iv)
                flush()
            }
            val cipher = newCipher(Cipher.ENCRYPT_MODE, password, salt, iv, V2_ITERATIONS, pepper = null)
            ZipOutputStream(CipherOutputStream(raw, cipher)).use { zip ->
                writeZipEntry(zip, MANIFEST_ENTRY, backup.toJson().toString())
                if (backup.includeLibrary) exportLibrary(zip, database.openHelper.readableDatabase)
            }
        }
    }

    suspend fun importAndRestore(contentResolver: ContentResolver, uri: Uri, password: String) {
        val first = contentResolver.openInputStream(uri)?.use { input -> ByteArray(MAGIC_V2.size).also { input.read(it) } }
            ?: error("Cannot read backup file")
        if (first.contentEquals(MAGIC_V2)) {
            importV2AndRestore(contentResolver, uri, password)
        } else {
            val backup = importV1(contentResolver, uri, password)
            restoreConfiguration(backup, libraryZip = null)
        }
    }

    private suspend fun importV2AndRestore(contentResolver: ContentResolver, uri: Uri, password: String) =
        withContext(Dispatchers.IO) {
            val temp = File.createTempFile("swan-restore-", ".zip", context.cacheDir)
            try {
                contentResolver.openInputStream(uri)?.use { raw ->
                    val input = DataInputStream(raw)
                    require(ByteArray(MAGIC_V2.size).also(input::readFully).contentEquals(MAGIC_V2)) { "Invalid backup magic" }
                    require(input.readUnsignedByte() == FORMAT_VERSION) { "Unsupported backup format" }
                    require(input.readUnsignedByte() == KDF_VERSION) { "Unsupported backup KDF" }
                    val iterations = input.readInt()
                    require(iterations in 100_000..2_000_000) { "Unsafe KDF iteration count" }
                    val salt = ByteArray(SALT_LENGTH).also(input::readFully)
                    val iv = ByteArray(IV_LENGTH).also(input::readFully)
                    val cipher = newCipher(Cipher.DECRYPT_MODE, password, salt, iv, iterations, pepper = null)
                    try {
                        CipherInputStream(input, cipher).use { encrypted -> temp.outputStream().use(encrypted::copyTo) }
                    } catch (e: Exception) {
                        findBadTag(e)?.let { throw it }
                        throw e
                    }
                } ?: error("Cannot read backup file")
                validateContainer(temp)
                ZipFile(temp).use { zip ->
                    val manifest = zip.getEntry(MANIFEST_ENTRY) ?: error("Backup manifest is missing")
                    val backup = zip.getInputStream(manifest).bufferedReader().use { BackupFile.fromJson(JSONObject(it.readText())) }
                    require(backup.version == FORMAT_VERSION) { "Manifest version does not match container" }
                    restoreConfiguration(backup, if (backup.includeLibrary) zip else null)
                }
            } finally {
                temp.delete()
            }
        }

    private fun importV1(contentResolver: ContentResolver, uri: Uri, password: String): BackupFile {
        val encrypted = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read backup file")
        require(encrypted.size > SALT_LENGTH + IV_LENGTH) { "Invalid legacy backup" }
        val salt = encrypted.copyOfRange(0, SALT_LENGTH)
        val iv = encrypted.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val cipher = newCipher(Cipher.DECRYPT_MODE, password, salt, iv, V1_ITERATIONS, V1_PEPPER)
        val json = cipher.doFinal(encrypted.copyOfRange(SALT_LENGTH + IV_LENGTH, encrypted.size))
        return BackupFile.fromJson(JSONObject(json.toString(Charsets.UTF_8)))
    }

    private suspend fun restoreConfiguration(backup: BackupFile, libraryZip: ZipFile?) {
        val oldInternalAssets = collectInternalAssets(database.openHelper.readableDatabase).keys
        val extractedAssets = if (libraryZip != null) extractAssets(libraryZip) else emptyMap()
        try {
            database.withTransaction {
                val db = database.openHelper.writableDatabase
                clearReplaceableData(db)
                backup.sources.forEach { sourceConfigDao.upsert(it.toEntityForRestore()) }
                if (libraryZip != null) restoreLibrary(db, libraryZip, extractedAssets)
            }

            credentialStore.clear()
            backup.credentials.forEach { (sourceId, creds) ->
                if (creds.username != null && creds.password != null) credentialStore.save(sourceId, creds.username, creds.password)
            }
            backup.apiKeys.discogsKey?.let(credentialStore::setApiDiscogsKey)
            backup.apiKeys.discogsSecret?.let(credentialStore::setApiDiscogsSecret)
            backup.apiKeys.lastfmKey?.let(credentialStore::setApiLastfmKey)
            backup.apiKeys.geniusToken?.let(credentialStore::setApiGeniusToken)
            languagePreferences.setLanguage(backup.languageCode)
            artistDataSourcePreferences.setSourceId(backup.artistDataSource.sourceId)
            artistDataSourcePreferences.setBasePath(backup.artistDataSource.basePath)
            sharedImportPreferences.setHiddenSourceIds(
                if (backup.hideCredentialsAfterRestore) backup.credentials.keys else emptySet()
            )
            sharedImportPreferences.setApiKeysHidden(backup.hideCredentialsAfterRestore)
            sharedImportPreferences.setLocalSourcesRequiringAuthorization(
                backup.sources.filter { it.type == "LOCAL" }.map { it.id }.toSet()
            )
            oldInternalAssets.forEach { oldUri ->
                runCatching { File(Uri.parse(oldUri).path ?: oldUri).delete() }
            }
            Timber.i("Backup restored: %d sources, library=%s", backup.sources.size, libraryZip != null)
        } catch (e: Exception) {
            extractedAssets.values.map(::File).mapNotNull(File::getParentFile).distinct().forEach(File::deleteRecursively)
            throw e
        }
    }

    private fun exportLibrary(zip: ZipOutputStream, db: SupportSQLiteDatabase) {
        LIBRARY_TABLES.forEach { table ->
            zip.putNextEntry(ZipEntry("library/$table.jsonl"))
            db.query("SELECT * FROM $table").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = cursorToJson(cursor, forceFavoriteFalse = table == "songs")
                    zip.write(row.toString().toByteArray(Charsets.UTF_8))
                    zip.write('\n'.code)
                }
            }
            zip.closeEntry()
        }
        val assets = collectInternalAssets(db)
        writeZipEntry(zip, ASSET_MAP_ENTRY, JSONObject(assets).toString())
        assets.values.distinct().forEach { entryName ->
            val sourcePath = assets.entries.first { it.value == entryName }.key
            zip.putNextEntry(ZipEntry(entryName))
            File(Uri.parse(sourcePath).path ?: sourcePath).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun restoreLibrary(db: SupportSQLiteDatabase, zip: ZipFile, assetPaths: Map<String, String>) {
        LIBRARY_TABLES.forEach { table ->
            val entry = zip.getEntry("library/$table.jsonl") ?: error("Missing library table: $table")
            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.filter(String::isNotBlank).forEach { line ->
                    val values = jsonToValues(JSONObject(line), assetPaths)
                    check(db.insert(table, 0, values) != -1L) { "Could not restore table $table" }
                }
            }
        }
    }

    private fun clearReplaceableData(db: SupportSQLiteDatabase) {
        listOf(
            "playlist_song_mapping", "playlists", "scan_artworks", "scan_songs", "scan_discovered", "scan_sessions",
            "album_series_mapping", "album_artwork", "album_song_mapping", "song_technical_info", "song_lyrics",
            "artist_pics", "songs", "albums", "album_series", "artists", "source_configs"
        ).forEach { db.execSQL("DELETE FROM $it") }
    }

    private fun cursorToJson(cursor: Cursor, forceFavoriteFalse: Boolean): JSONObject = JSONObject().apply {
        cursor.columnNames.forEachIndexed { index, name ->
            val cell = JSONObject()
            when {
                forceFavoriteFalse && name == "isFavorite" -> cell.put("t", "i").put("v", 0)
                cursor.isNull(index) -> cell.put("t", "n")
                cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER -> cell.put("t", "i").put("v", cursor.getLong(index))
                cursor.getType(index) == Cursor.FIELD_TYPE_FLOAT -> cell.put("t", "f").put("v", cursor.getDouble(index))
                cursor.getType(index) == Cursor.FIELD_TYPE_BLOB -> cell.put("t", "b").put("v", android.util.Base64.encodeToString(cursor.getBlob(index), android.util.Base64.NO_WRAP))
                else -> cell.put("t", "s").put("v", cursor.getString(index))
            }
            put(name, cell)
        }
    }

    private fun jsonToValues(row: JSONObject, assetPaths: Map<String, String>) = ContentValues().apply {
        row.keys().forEach { name ->
            val cell = row.getJSONObject(name)
            when (cell.getString("t")) {
                "n" -> putNull(name)
                "i" -> put(name, cell.getLong("v"))
                "f" -> put(name, cell.getDouble("v"))
                "b" -> put(name, android.util.Base64.decode(cell.getString("v"), android.util.Base64.NO_WRAP))
                "s" -> put(name, assetPaths[cell.getString("v")] ?: cell.getString("v"))
                else -> error("Unsupported backup value type")
            }
        }
    }

    private fun collectInternalAssets(db: SupportSQLiteDatabase): Map<String, String> {
        val result = linkedMapOf<String, String>()
        listOf("album_artwork" to listOf("uriLarge", "uriSmall"), "artist_pics" to listOf("uriLarge", "uriSmall"))
            .forEach { (table, columns) ->
                db.query("SELECT ${columns.joinToString()} FROM $table").use { cursor ->
                    while (cursor.moveToNext()) columns.indices.forEach { index ->
                        if (!cursor.isNull(index)) {
                            val uri = cursor.getString(index)
                            val file = File(Uri.parse(uri).path ?: uri)
                            val canonical = runCatching(file::getCanonicalFile).getOrNull()
                            val internal = canonical != null && canonical.isFile && (
                                canonical.path.startsWith(context.filesDir.canonicalPath + File.separator) ||
                                    canonical.path.startsWith(context.cacheDir.canonicalPath + File.separator)
                                )
                            if (internal) result[uri] = "assets/${UUID.randomUUID()}-${canonical!!.name}"
                        }
                    }
                }
            }
        return result
    }

    private fun extractAssets(zip: ZipFile): Map<String, String> {
        val mapEntry = zip.getEntry(ASSET_MAP_ENTRY) ?: return emptyMap()
        val mapping = zip.getInputStream(mapEntry).bufferedReader().use { JSONObject(it.readText()) }
        val outputDir = File(context.filesDir, "restored-cache/${UUID.randomUUID()}").apply { mkdirs() }
        return mapping.keys().asSequence().associateWith { oldUri ->
            val entryName = mapping.getString(oldUri)
            val entry = zip.getEntry(entryName) ?: error("Missing backup asset")
            val target = File(outputDir, File(entryName).name)
            zip.getInputStream(entry).use { input -> target.outputStream().use(input::copyTo) }
            Uri.fromFile(target).toString()
        }
    }

    private fun validateContainer(file: File) {
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.size <= MAX_ENTRIES) { "Backup contains too many entries" }
            var total = 0L
            entries.forEach { entry ->
                require(!entry.name.startsWith('/') && !entry.name.contains("..") && !entry.name.contains('\\')) { "Unsafe backup path" }
                require(entry.size >= 0) { "Backup entry has unknown size" }
                total = Math.addExact(total, entry.size)
                require(total <= MAX_UNPACKED_BYTES && total <= context.filesDir.usableSpace) { "Not enough space for backup" }
                zip.getInputStream(entry).use { input ->
                    val crc = CRC32()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var count: Int
                    while (input.read(buffer).also { count = it } >= 0) crc.update(buffer, 0, count)
                    require(entry.crc == -1L || crc.value == entry.crc) { "Corrupt backup entry" }
                }
            }
            require(entries.any { it.name == MANIFEST_ENTRY }) { "Backup manifest is missing" }
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, contents: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(contents.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun newCipher(mode: Int, password: String, salt: ByteArray, iv: ByteArray, iterations: Int, pepper: String?): Cipher {
        val material = if (pepper == null) password else password + pepper
        val spec = PBEKeySpec(material.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val raw = SecretKeyFactory.getInstance(KEY_ALGORITHM).generateSecret(spec).encoded
        spec.clearPassword()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(raw, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
    }

    private fun findBadTag(error: Throwable): AEADBadTagException? {
        var current: Throwable? = error
        while (current != null) {
            if (current is AEADBadTagException) return current
            current = current.cause
        }
        return null
    }

    companion object {
        private val MAGIC_V2 = "SWANBAK2".toByteArray(Charsets.US_ASCII)
        private const val FORMAT_VERSION = 2
        private const val KDF_VERSION = 1
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_LENGTH_BITS = 256
        private const val V1_ITERATIONS = 100_000
        private const val V2_ITERATIONS = 600_000
        private const val MIN_PASSWORD_LENGTH = 12
        private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val V1_PEPPER = "&HGq8^C7Y%"
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val ASSET_MAP_ENTRY = "assets/map.json"
        private const val MAX_ENTRIES = 1_000_000
        private const val MAX_UNPACKED_BYTES = 50L * 1024 * 1024 * 1024
        private val LIBRARY_TABLES = listOf(
            "artists", "songs", "albums", "album_series", "album_song_mapping", "album_artwork",
            "artist_pics", "song_lyrics", "song_technical_info", "album_series_mapping"
        )
    }
}

private fun SourceConfigEntity.toBackup() = BackupSource(id, name, type, isEnabled, folderUri, url, path)

private fun BackupSource.toEntityForRestore() = SourceConfigEntity(
    id = id,
    name = name,
    type = type,
    isEnabled = isEnabled,
    folderUri = folderUri,
    url = url,
    path = path
)
