package com.schwanitz.data.source

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ImageScaler {

    private const val SMALL_MAX_DIMENSION = 256
    private const val LARGE_MAX_DIMENSION = 840

    fun scaleToSmall(bytes: ByteArray): ByteArray = scale(bytes, SMALL_MAX_DIMENSION)

    fun scaleToLarge(bytes: ByteArray): ByteArray = scale(bytes, LARGE_MAX_DIMENSION)

    private fun scale(bytes: ByteArray, maxDimension: Int): ByteArray {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val origWidth = options.outWidth
        val origHeight = options.outHeight

        return scaleAndCompress(bytes, origWidth, origHeight, maxDimension)
    }

    private fun scaleAndCompress(
        bytes: ByteArray,
        origWidth: Int,
        origHeight: Int,
        maxDimension: Int
    ): ByteArray {
        if (origWidth <= maxDimension && origHeight <= maxDimension) {
            return bytes
        }

        val sampleSize = calculateSampleSize(origWidth, origHeight, maxDimension)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: return bytes

        val scaledWidth: Int
        val scaledHeight: Int
        if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            scaledWidth = (bitmap.width * scale).toInt()
            scaledHeight = (bitmap.height * scale).toInt()
        } else {
            scaledWidth = bitmap.width
            scaledHeight = bitmap.height
        }

        val scaled = if (scaledWidth != bitmap.width || scaledHeight != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
        scaled.recycle()
        return output.toByteArray()
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > maxDimension * 2 || height / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
