package com.schwanitz.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupJobSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY).apply { mkdirs() }

    @Synchronized
    fun save(jobId: String, password: String) {
        require(jobId.matches(ID_PATTERN))
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val target = file(jobId)
        val temporary = File(directory, "$jobId.tmp")
        temporary.outputStream().use { output ->
            output.write(MAGIC)
            output.write(cipher.iv)
            output.write(cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
            output.fd.sync()
        }
        if (!temporary.renameTo(target)) {
            target.delete()
            check(temporary.renameTo(target)) { "Could not store backup job secret" }
        }
    }

    @Synchronized
    fun load(jobId: String): String {
        val bytes = file(jobId).readBytes()
        require(bytes.size > MAGIC.size + IV_LENGTH && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC))
        val ivStart = MAGIC.size
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, bytes.copyOfRange(ivStart, ivStart + IV_LENGTH)),
            )
        }
        return cipher.doFinal(bytes.copyOfRange(ivStart + IV_LENGTH, bytes.size)).toString(Charsets.UTF_8)
    }

    @Synchronized
    fun delete(jobId: String) {
        file(jobId).delete()
        File(directory, "$jobId.tmp").delete()
    }

    @Synchronized
    fun cleanupExcept(activeJobIds: Set<String>) {
        directory.listFiles()?.forEach { candidate ->
            val id = candidate.name.removeSuffix(".secret").removeSuffix(".tmp")
            if (id !in activeJobIds) candidate.delete()
        }
    }

    private fun file(jobId: String) = File(directory, "$jobId.secret")

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        val ID_PATTERN = Regex("[A-Za-z0-9-]{1,80}")
        val MAGIC = "SWANJOB1".toByteArray(Charsets.US_ASCII)
        const val DIRECTORY = "backup-jobs"
        const val KEY_ALIAS = "swan.backup.jobs.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
