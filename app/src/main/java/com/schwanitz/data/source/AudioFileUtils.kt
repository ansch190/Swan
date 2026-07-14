package com.schwanitz.data.source

private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "opus")

fun String.isAudioFile(): Boolean {
    val ext = substringAfterLast('.', "").lowercase()
    return ext in AUDIO_EXTENSIONS
}
