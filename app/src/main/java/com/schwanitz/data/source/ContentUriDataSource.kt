package com.schwanitz.data.source

import android.content.res.AssetFileDescriptor
import com.schwanitz.io.SeekableDataSource
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class ContentUriDataSource(
    private val afd: AssetFileDescriptor,
    private val uriStr: String
) : SeekableDataSource {

    private val fis = FileInputStream(afd.parcelFileDescriptor.fileDescriptor)
    private val channel: FileChannel = fis.channel

    override fun length(): Long {
        val declared = afd.declaredLength
        return if (declared > 0L) declared else channel.size()
    }

    override fun read(offset: Long, buf: ByteArray, bufOff: Int, len: Int): Int {
        return channel.read(ByteBuffer.wrap(buf, bufOff, len), offset)
    }

    override fun name(): String = uriStr

    override fun close() {
        try { fis.close() } catch (_: Exception) {}
        afd.close()
    }
}
