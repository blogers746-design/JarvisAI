package com.jarvis.ai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.jarvis.ai.model.AttachedMedia
import kotlin.math.max
import kotlin.math.roundToInt

object ImageUtils {

    fun loadMediaFrame(context: Context, uri: Uri, maxSide: Int): AttachedMedia? {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val displayName = getDisplayName(context, uri)
        val rawBitmap: Bitmap? = if (mime.startsWith("video/")) {
            extractVideoFrame(context, uri)
        } else {
            decodeSampledBitmap(context, uri, maxSide)
        }
        if (rawBitmap == null) return null

        val scaled = scaleDownToMultipleOf14(rawBitmap, maxSide)
        if (scaled !== rawBitmap) {
            rawBitmap.recycle()
        }

        val rgb = bitmapToRgb24(scaled)
        val label = if (mime.startsWith("video/")) {
            "Кадр видео · ${scaled.width}×${scaled.height}"
        } else {
            "${displayName.take(22)} · ${scaled.width}×${scaled.height}"
        }

        return AttachedMedia(
            preview = scaled,
            rgbBytes = rgb,
            width = scaled.width,
            height = scaled.height,
            label = label
        )
    }

    private fun extractVideoFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            // Extract frame at ~1s (or first keyframe) for Reels hook analysis
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var inSample = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / (inSample * 2) >= maxSide) {
                inSample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = inSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDownToMultipleOf14(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val scale = if (max(w, h) > maxSide) maxSide.toFloat() / max(w, h).toFloat() else 1f
        // Align to multiples of 28 (optimal patch grid for Qwen-VL / Qwen3.5 vision)
        val targetW = max(28, ((w * scale) / 28f).roundToInt() * 28)
        val targetH = max(28, ((h * scale) / 28f).roundToInt() * 28)
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    private fun bitmapToRgb24(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h * 3)
        var j = 0
        for (p in pixels) {
            out[j++] = ((p shr 16) and 0xFF).toByte()
            out[j++] = ((p shr 8) and 0xFF).toByte()
            out[j++] = (p and 0xFF).toByte()
        }
        return out
    }

    private fun getDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "Кадр"
            }
        }
        return "Кадр"
    }
}
