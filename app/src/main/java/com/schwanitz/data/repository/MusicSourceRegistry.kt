package com.schwanitz.data.repository

import com.schwanitz.domain.source.MusicSource
import com.schwanitz.domain.source.SourceType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicSourceRegistry @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards MusicSource>
) {
    private val sourceMap: Map<SourceType, MusicSource> = sources.associateBy { it.type }

    fun get(type: SourceType): MusicSource? {
        val source = sourceMap[type]
        if (source == null) {
            Timber.w("No MusicSource registered for type %s", type)
        }
        return source
    }
}
