package com.monmontage.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import java.io.File

/**
 * Logo en miniature en bas à droite (calque plein cadre préparé une fois en PNG).
 * Le bitmap est chargé à la première image de l'élément et libéré à la fin : mémoire maîtrisée.
 */
@UnstableApi
class WatermarkOverlay(private val file: File) : BitmapOverlay() {
    private var bitmap: Bitmap? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        bitmap ?: BitmapFactory.decodeFile(file.absolutePath).also { bitmap = it }

    override fun release() {
        super.release()
        bitmap?.recycle()
        bitmap = null
    }
}
