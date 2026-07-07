package com.schwanitz.data.source

import android.net.Uri
import android.util.Base64
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.schwanitz.domain.source.SourceConfig
import com.schwanitz.domain.source.SourceType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthHttpDataSourceFactory @Inject constructor() : HttpDataSource.Factory {

    private val defaultFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
    private val sourceConfigs = ConcurrentHashMap<String, SourceConfig>()

    fun updateSources(sources: List<SourceConfig>) {
        sourceConfigs.clear()
        sources
            .filter { it.type == SourceType.WEBDAV && it.isEnabled && it.url != null }
            .forEach { config ->
                sourceConfigs[config.url!!.trimEnd('/')] = config
            }
    }

    override fun createDataSource(): HttpDataSource {
        return AuthHttpDataSource(defaultFactory.createDataSource(), sourceConfigs)
    }

    override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>): HttpDataSource.Factory {
        defaultFactory.setDefaultRequestProperties(defaultRequestProperties)
        return this
    }
}

private class AuthHttpDataSource(
    private val delegate: HttpDataSource,
    private val sourceConfigs: ConcurrentHashMap<String, SourceConfig>
) : HttpDataSource by delegate {

    override fun open(dataSpec: DataSpec): Long {
        delegate.clearAllRequestProperties()
        val config = findConfig(dataSpec.uri)
        if (config?.username != null && config.password != null) {
            val raw = "${config.username}:${config.password}"
            val encoded = Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
            delegate.setRequestProperty("Authorization", "Basic $encoded")
        }
        return delegate.open(dataSpec)
    }

    private fun findConfig(uri: Uri): SourceConfig? {
        val uriStr = uri.toString()
        for ((prefix, config) in sourceConfigs) {
            if (uriStr.startsWith(prefix, ignoreCase = true)) return config
        }
        return null
    }
}
