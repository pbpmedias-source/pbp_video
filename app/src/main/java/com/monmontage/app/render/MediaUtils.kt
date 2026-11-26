package com.monmontage.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object MediaUtils {

    fun mimeType(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri) ?: "application/octet-stream"

    fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "média"

    /** Durée d'un fichier audio ou vidéo, sans le décoder. */
    fun durationMs(context: Context, uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            r.release()
        }
    }

    /**
     * Décode une image en respectant l'orientation EXIF, directement sous-échantillonnée
     * (une photo de 50 Mpx n'est jamais décodée en pleine résolution), puis ajustée à maxW × maxH.
     * Bitmap logiciel : dessinable sur un Canvas et compressible.
     */
    fun decodeBitmap(context: Context, uri: Uri, maxW: Int, maxH: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            val target = max(maxW, maxH)
            var sample = 1
            while (longest / (sample * 2) >= target) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
        val scale = min(1f, min(maxW.toFloat() / decoded.width, maxH.toFloat() / decoded.height))
        if (scale >= 1f) return decoded
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(2),
            (decoded.height * scale).roundToInt().coerceAtLeast(2),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** Miniature pour la liste : vignette système, sinon image de la vidéo, sinon décodage réduit. */
    fun thumbnail(context: Context, uri: Uri, isVideo: Boolean, sizePx: Int): Bitmap? {
        runCatching { return context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }
        return runCatching {
            if (isVideo) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    r.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx, sizePx)
                } finally {
                    r.release()
                }
            } else {
                decodeBitmap(context, uri, sizePx, sizePx)
            }
        }.getOrNull()
    }
}
