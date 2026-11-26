package com.monmontage.app.render

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Applique un gain variable dans le temps à un flux PCM (16 bits ou float).
 * [offsetUs] = position de l'élément dans la timeline finale : le processeur compte les
 * échantillons reçus pour connaître le temps exact, sans dépendre des horodatages internes.
 * Traitement en flux, tampon par tampon : aucune piste n'est chargée en mémoire.
 */
@UnstableApi
class GainEnvelopeProcessor(
    private val offsetUs: Long,
    private val gainAtUs: (Long) -> Float,
) : BaseAudioProcessor() {

    private var framesProcessed = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val format = this.inputAudioFormat
        inputBuffer.order(ByteOrder.nativeOrder())
        val out = replaceOutputBuffer(remaining)
        val channels = format.channelCount
        val sampleRate = format.sampleRate.toLong()
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val frameSize = format.bytesPerFrame
        var gain = 1f

        while (inputBuffer.remaining() >= frameSize) {
            if (framesProcessed % 32L == 0L) {      // gain recalculé toutes les 32 trames (~0,7 ms)
                gain = gainAtUs(offsetUs + framesProcessed * 1_000_000L / sampleRate)
            }
            for (c in 0 until channels) {
                if (isFloat) {
                    out.putFloat(inputBuffer.float * gain)
                } else {
                    val v = (inputBuffer.short * gain).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    out.putShort(v.toShort())
                }
            }
            framesProcessed++
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onReset() {
        framesProcessed = 0
    }
}
