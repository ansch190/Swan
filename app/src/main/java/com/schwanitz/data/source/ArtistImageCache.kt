package com.schwanitz.data.source

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

data class ArtistPicResult(
    val smallUri: String?,
    val largeUri: String?
)

object ArtistImageCache {

    private const val SMALL_MAX_DIMENSION = 256
    private const val LARGE_MAX_DIMENSION = 840

    fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, "artist_images")
        dir.mkdirs()
        return dir
    }

    fun saveScaled(bytes: ByteArray, context: Context, artistName: String): ArtistPicResult {
        val smallBytes = ImageScaler.scaleToSmall(bytes)
        val largeBytes = ImageScaler.scaleToLarge(bytes)

        val smallUri = saveToDisk(smallBytes, context, artistName, "s")
        val largeUri = saveToDisk(largeBytes, context, artistName, "l")

        return ArtistPicResult(smallUri = smallUri, largeUri = largeUri)
    }

    private fun saveToDisk(bytes: ByteArray, context: Context, artistName: String, suffix: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        val hex = hash.joinToString("") { "%02x".format(it) }
        val file = File(cacheDir(context), "${hex}_${artistName}_$suffix.jpg")
        if (!file.exists()) {
            file.writeBytes(bytes)
        }
        return Uri.fromFile(file).toString()
    }

    fun deleteUris(context: Context, uris: Set<String>) {
        val filesToKeep = uris.mapNotNull { uri ->
            try { File(Uri.parse(uri).path ?: "").name } catch (_: Exception) { null }
        }.toSet()
        cacheDir(context).listFiles()?.forEach { file ->
            if (file.name !in filesToKeep) file.delete()
        }
    }
}
