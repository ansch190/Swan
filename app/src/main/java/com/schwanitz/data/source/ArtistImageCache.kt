package com.schwanitz.data.source

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

object ArtistImageCache {

    fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, "artist_images")
        dir.mkdirs()
        return dir
    }

    fun save(bytes: ByteArray, context: Context, artistName: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        val hex = hash.joinToString("") { "%02x".format(it) }
        val file = File(cacheDir(context), "${hex}_$artistName.jpg")
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
