package com.schwanitz.swan

import retrofit2.http.GET
import retrofit2.http.Query

interface TheAudioDBService {
    @GET("artist.php")
    suspend fun getArtistById(@Query("i") artistId: String): AudioDBResponse
}

data class AudioDBResponse(
    val artists: List<Artist>?
)

data class Artist(
    val idArtist: String?,
    val strArtist: String?,
    val strArtistThumb: String?
)