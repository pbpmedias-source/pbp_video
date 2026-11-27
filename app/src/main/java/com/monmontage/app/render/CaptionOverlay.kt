package com.monmontage.app.render

import android.graphics.Bitmap
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlaySettings
import kotlin.math.min

/**
 * Calque GPU qui affiche un bandeau de légende sur un élément vidéo/photo, avec fondu de 300 ms.
 * Le temps local est mesuré à partir de la première image reçue, ce qui le rend indépendant
 * de la convention d'horodatage du pipeline. Le bitmap est créé à la demande et libéré à la fin.
 */
@UnstableApi
class CaptionOverlay(
    private val caption: LocalCaption,
    private val width: Int,
    private val height: Int,
    private val colors: KitColors = KitColors.PBP,
) : BitmapOverlay() {

    private var basePtsUs = C.TIME_UNSET
    private var bitmap: Bitmap? = null
    private val hidden = OverlaySettings.Builder().setAlphaScale(0f).build()
    private val visible = OverlaySettings.Builder().build()

    private fun localMs(ptsUs: Long): Long {
        if (basePtsUs == C.TIME_UNSET) basePtsUs = ptsUs
        return (ptsUs - basePtsUs) / 1000
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        localMs(presentationTimeUs)
        return bitmap ?: Painter.renderCaptionBitmap(width, height, caption.name, caption.role, colors)
            .also { bitmap = it }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val t = localMs(presentationTimeUs)
        if (t < caption.startMs || t > caption.endMs) return hidden
        val fade = 300f
        val a = min((t - caption.startMs) / fade, (caption.endMs - t) / fade).coerceIn(0f, 1f)
        return when {
            a <= 0f -> hidden
            a >= 1f -> visible
            else -> OverlaySettings.Builder().setAlphaScale(a).build()
        }
    }

    override fun release() {
        super.release()
        bitmap?.recycle()
        bitmap = null
    }
}
