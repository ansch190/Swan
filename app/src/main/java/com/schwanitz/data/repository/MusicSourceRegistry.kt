package com.schwanitz.data.repository

import com.schwanitz.domain.source.MusicSource
import com.schwanitz.domain.source.SourceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicSourceRegistry @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards MusicSource>
) {
    private val sourceMap: Map<SourceType, MusicSource> = sources.associateBy { it.type }

    fun get(type: SourceType): MusicSource? = sourceMap[type]
}
