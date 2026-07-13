package com.schwanitz.data.source

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import timber.log.Timber

data class ArtworkResult(
    val smallUri: String?,
    val largeUri: String
)

object ArtworkCache {

    private fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, "album_art")
        dir.mkdirs()
        return dir
    }

    fun saveScaled(bytes: ByteArray, context: Context, index: Int): ArtworkResult {
        val largeBytes = ImageScaler.scaleToLarge(bytes)
        val largeUri = saveToDisk(largeBytes, context, index, "l")

        val smallUri = if (index == 0) {
            val smallBytes = ImageScaler.scaleToSmall(bytes)
            saveToDisk(smallBytes, context, index, "s")
        } else {
            null
        }

        return ArtworkResult(smallUri = smallUri, largeUri = largeUri)
    }

    private fun saveToDisk(bytes: ByteArray, context: Context, index: Int, suffix: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(bytes)
            val hex = hash.joinToString("") { "%02x".format(it) }
            val file = File(cacheDir(context), "${hex}_${index}_${suffix}.jpg")
            if (!file.exists()) {
                file.writeBytes(bytes)
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save artwork to disk")
            ""
        }
    }

    fun deleteUnused(context: Context, usedUris: Set<String>) {
        val usedFiles = usedUris.mapNotNull { uri ->
            try { File(Uri.parse(uri).path ?: "").name } catch (_: Exception) { null }
        }.toSet()
        cacheDir(context).listFiles()?.forEach { file ->
            if (file.name !in usedFiles) file.delete()
        }
    }
}
