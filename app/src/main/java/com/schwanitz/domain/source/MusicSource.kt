package com.schwanitz.domain.source

interface MusicSource {
    val type: SourceType
    suspend fun loadSongs(
        config: SourceConfig,
        onProgress: (scanned: Int, total: Int) -> Unit = { _, _ -> },
        onBatch: suspend (LoadSongsResult) -> Unit = { }
    ): LoadSongsResult
}
