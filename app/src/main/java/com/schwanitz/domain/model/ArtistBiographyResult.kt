package com.schwanitz.domain.model

enum class BioSource { WEBDAV, LASTFM }

data class ArtistBiographyResult(
    val text: String,
    val source: BioSource
)
