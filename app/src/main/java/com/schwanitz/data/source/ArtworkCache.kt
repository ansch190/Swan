package com.schwanitz.data.source

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

object ArtworkCache {

    private fun cacheDir(context: Context): File {
        val dir = File(context.cacheDir, "album_art")
        dir.mkdirs()
        return dir
    }

    fun save(bytes: ByteArray, context: Context, index: Int): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(bytes)
        val hex = hash.joinToString("") { "%02x".format(it) }
        val file = File(cacheDir(context), "${hex}_$index.jpg")
        if (!file.exists()) {
            file.writeBytes(bytes)
        }
        return Uri.fromFile(file).toString()
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
