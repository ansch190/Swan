package com.schwanitz.data.source.smb

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import com.schwanitz.data.source.AuthHttpDataSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpFactory: AuthHttpDataSourceFactory,
    private val smbDataSourceFactory: SmbDataSourceFactory
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return SmartDataSource(
            context = context,
            httpFactory = httpFactory,
            smbFactory = smbDataSourceFactory
        )
    }
}

@UnstableApi
private class SmartDataSource(
    private val context: Context,
    private val httpFactory: AuthHttpDataSourceFactory,
    private val smbFactory: SmbDataSourceFactory
) : BaseDataSource(false) {

    private var delegate: DataSource? = null
    private var totalBytes = 0L
    private var bytesRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val scheme = dataSpec.uri.scheme?.lowercase()
        delegate = if (scheme == "smb") {
            smbFactory.createDataSource()
        } else {
            DefaultDataSource(context, httpFactory.createDataSource())
        }
        totalBytes = delegate!!.open(dataSpec)
        bytesRead = 0L
        transferStarted(dataSpec)
        return totalBytes
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate!!.read(buffer, offset, length)
        if (read > 0) {
            bytesRead += read
            bytesTransferred(read)
        }
        return read
    }

    override fun close() {
        delegate?.close()
        delegate = null
    }

    override fun getUri() = delegate?.uri
}
