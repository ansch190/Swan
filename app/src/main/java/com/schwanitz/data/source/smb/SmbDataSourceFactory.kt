package com.schwanitz.data.source.smb

import androidx.media3.datasource.DataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmbDataSourceFactory @Inject constructor(
    private val connectionManager: SmbConnectionManager
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return SmbDataSource(connectionManager)
    }
}
