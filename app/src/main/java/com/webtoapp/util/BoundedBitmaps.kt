package com.webtoapp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.webtoapp.core.logging.AppLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max















object BoundedBitmaps {

    private const val TAG = "BoundedBitmaps"

    const val DEFAULT_MAX_DIMENSION = 2048

    
    const val ICON_MAX_DIMENSION = 512

    
    private const val MAX_DECODE_BYTES = 48 * 1024 * 1024

    



    fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, maxDimension: Int): Int {
        if (maxDimension <= 0) return 1
        if (srcWidth <= 0 || srcHeight <= 0) return 1
        var sample = 1
        while (srcWidth / sample > maxDimension || srcHeight / sample > maxDimension) {
            if (sample >= 1 shl 30) break
            sample *= 2
        }
        return sample
    }

    fun decodeBoundedBitmapFile(path: String, maxDimension: Int = DEFAULT_MAX_DIMENSION): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().also {
                it.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                it.inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, opts)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Bounded file decode failed: $path (${t.message})")
            null
        }
    }

    fun decodeBoundedBitmapBytes(data: ByteArray, maxDimension: Int = DEFAULT_MAX_DIMENSION): Bitmap? {
        return try {
            if (data.isEmpty() || data.size > MAX_DECODE_BYTES) return null
            val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().also {
                it.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                it.inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Bounded bytes decode failed (${data.size} bytes, ${t.message})")
            null
        }
    }

    fun decodeBoundedBitmapStream(stream: InputStream, maxDimension: Int = DEFAULT_MAX_DIMENSION): Bitmap? {
        return try {
            
            
            val out = ByteArrayOutputStream(8192)
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_DECODE_BYTES) {
                    AppLogger.w(TAG, "Stream exceeds $MAX_DECODE_BYTES bytes, refusing decode")
                    return null
                }
                out.write(buf, 0, n)
            }
            decodeBoundedBitmapBytes(out.toByteArray(), maxDimension)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Bounded stream decode failed (${t.message})")
            null
        }
    }

    




    fun Drawable.toBoundedBitmap(maxDimension: Int = ICON_MAX_DIMENSION): Bitmap? {
        return try {
            if (maxDimension <= 0) return null
            if (this is BitmapDrawable && bitmap != null) {
                return bitmap.downscaledToFit(maxDimension)
            }
            var w = intrinsicWidth
            var h = intrinsicHeight
            if (w <= 0 || h <= 0) {
                w = maxDimension
                h = maxDimension
            } else {
                val longest = max(w, h)
                if (longest > maxDimension) {
                    val scale = maxDimension / longest.toFloat()
                    w = max(1, (w * scale).toInt())
                    h = max(1, (h * scale).toInt())
                }
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) ?: return null
            val canvas = Canvas(bmp)
            setBounds(0, 0, w, h)
            draw(canvas)
            bmp
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Bounded drawable raster failed (${t.message})")
            null
        }
    }

    



    fun Bitmap.downscaledToFit(maxDimension: Int): Bitmap {
        return try {
            val longest = max(width, height)
            if (maxDimension <= 0 || longest <= maxDimension || longest <= 0) return this
            val scale = maxDimension / longest.toFloat()
            val tw = max(1, (width * scale).toInt())
            val th = max(1, (height * scale).toInt())
            Bitmap.createScaledBitmap(this, tw, th, true) ?: this
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Downscale failed, keeping original (${t.message})")
            this
        }
    }

    
    fun isProbablyImageFile(file: File): Boolean {
        if (!file.isFile || !file.canRead() || file.length() <= 0) return false
        if (file.length() > MAX_DECODE_BYTES) return false
        return true
    }
}
