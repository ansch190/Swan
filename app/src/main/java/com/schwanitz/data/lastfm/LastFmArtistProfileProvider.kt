package com.schwanitz.data.lastfm

import android.text.Html
import com.schwanitz.domain.model.ArtistProfile
import com.schwanitz.domain.source.ArtistProfileProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LastFmArtistProfileProvider @Inject constructor(
    private val apiService: LastFmApiService
) : ArtistProfileProvider {

    override suspend fun fetchProfile(artistName: String): ArtistProfile? {
        val artist = apiService.getArtistInfo(artistName) ?: return null
        val bio = artist.bio
        if (bio.content.isBlank()) return null

        val cleanContent = Html.fromHtml(bio.content, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        val cleanSummary = if (bio.summary.isNotBlank()) {
            Html.fromHtml(bio.summary, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else null

        return ArtistProfile(
            artistName = artistName,
            summary = cleanSummary,
            content = cleanContent,
            source = "lastfm",
            lastUpdated = System.currentTimeMillis()
        )
    }
}
