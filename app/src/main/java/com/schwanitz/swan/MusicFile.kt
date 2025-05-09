package com.schwanitz.swan

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MusicFile(
    val uri: Uri,
    val name: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val discNumber: String?,
    val trackNumber: String?,
    val year: String?,
    val genre: String?,
    val fileSize: Int,
    val audioCodec: String?,
    val sampleRate: Int,
    val bitrate: Long,
    val tagVersion: String?
) : Parcelable