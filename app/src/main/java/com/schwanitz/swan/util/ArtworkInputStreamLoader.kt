package com.schwanitz.swan

import android.content.Context
import android.net.Uri
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.data.StreamAssetPathFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.schwanitz.swan.domain.usecase.MetadataExtractor
import java.io.InputStream

data class ArtworkModel(val uri: Uri, val index: Int)

class ArtworkInputStreamLoader(
    private val context: Context,
    private val metadataExtractor: MetadataExtractor
) : ModelLoader<ArtworkModel, InputStream> {

    override fun buildLoadData(
        model: ArtworkModel,
        width: Int,
        height: Int,
        options: com.bumptech.glide.load.Options
    ): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(
            com.bumptech.glide.signature.ObjectKey(model),
            ArtworkDataFetcher(context, metadataExtractor, model)
        )
    }

    override fun handles(model: ArtworkModel): Boolean {
        return true
    }

    class Factory(
        private val context: Context,
        private val metadataExtractor: MetadataExtractor
    ) : ModelLoaderFactory<ArtworkModel, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<ArtworkModel, InputStream> {
            return ArtworkInputStreamLoader(context, metadataExtractor)
        }

        override fun teardown() {
            // Nichts zu tun
        }
    }
}

class ArtworkDataFetcher(
    private val context: Context,
    private val metadataExtractor: MetadataExtractor,
    private val model: ArtworkModel
) : DataFetcher<InputStream> {

    override fun loadData(priority: com.bumptech.glide.Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        try {
            val bytes = metadataExtractor.getArtworkBytes(model.uri, model.index)
            if (bytes != null) {
                callback.onDataReady(bytes.inputStream())
            } else {
                callback.onLoadFailed(Exception("No artwork found at index ${model.index}"))
            }
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        // Nichts zu tun
    }

    override fun cancel() {
        // Nichts zu tun
    }

    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }

    override fun getDataSource(): com.bumptech.glide.load.DataSource {
        return com.bumptech.glide.load.DataSource.LOCAL
    }
}