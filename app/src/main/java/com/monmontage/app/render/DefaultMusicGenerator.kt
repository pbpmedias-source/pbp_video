package com.monmontage.app.render

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Musique de fond par défaut : composée par l'application elle-même au premier lancement
 * (accords Do – La m – Fa – Sol, nappe douce + basse + arpège), donc sans aucun droit d'auteur.
 * Boucle parfaite de 24 s, écrite une seule fois en WAV dans le stockage privé de l'app.
 */
object DefaultMusicGenerator {
    const val DURATION_MS = 24_000L
    private const val SR = 44_100
    private const val FILE_NAME = "default_music_v1.wav"

    @Synchronized
    fun ensure(context: Context): File {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists() && file.length() > 44) return file
        val pcm = synthesize()
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        writeWav(tmp, pcm)
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        return file
    }

    private fun synthesize(): FloatArray {
        val n = (SR * DURATION_MS / 1000).toInt()
        val buf = FloatArray(n)
        val chords = arrayOf(
            intArrayOf(48, 52, 55, 60),   // Do
            intArrayOf(45, 52, 57, 60),   // La mineur
            intArrayOf(41, 48, 53, 57),   // Fa
            intArrayOf(43, 50, 55, 59),   // Sol
        )
        val chordLen = 6.0                 // 2 mesures à 80 BPM
        val arp = intArrayOf(0, 1, 2, 3, 2, 1, 2, 3)
        chords.forEachIndexed { i, ch ->
            val start = i * chordLen
            ch.forEach { m -> addTone(buf, start, chordLen, m, 0.085, 1.2, 1.8, pluck = false) }
            addTone(buf, start, 2.8, ch[0] - 12, 0.16, 0.02, 0.5, pluck = false)
            addTone(buf, start + 3.0, 2.8, ch[0] - 12, 0.14, 0.02, 0.5, pluck = false)
            for (k in 0 until 16) {
                addTone(buf, start + k * 0.375, 0.3, ch[arp[k % 8]] + 12, if (k % 4 == 0) 0.075 else 0.05, 0.004, 0.9, pluck = true)
            }
        }
        var peak = 0f
        for (v in buf) peak = max(peak, abs(v))
        if (peak > 0f) { val k = 0.8f / peak; for (i in buf.indices) buf[i] *= k }
        return buf
    }

    /** Ajoute une note ; les queues qui dépassent la fin reviennent au début (boucle sans couture). */
    private fun addTone(
        buf: FloatArray, startS: Double, lenS: Double, midi: Int, amp: Double,
        attackS: Double, releaseS: Double, pluck: Boolean,
    ) {
        val n = buf.size
        val f = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val start = (startS * SR).toInt()
        val len = ((lenS + releaseS) * SR).toInt()
        for (i in 0 until len) {
            val t = i.toDouble() / SR
            val env = if (pluck) {
                min(1.0, t / attackS) * exp(-t * 4.5)
            } else {
                val a = min(1.0, t / attackS)
                val r = if (t > lenS) max(0.0, 1.0 - (t - lenS) / releaseS) else 1.0
                a * r
            }
            val ph = 2.0 * PI * f * t
            val v = if (pluck) sin(ph) + 0.25 * sin(2 * ph)
            else (sin(ph) + 0.5 * sin(ph * 1.004) + 0.18 * sin(2 * ph)) / 1.68
            val idx = (start + i) % n
            buf[idx] += (amp * env * v).toFloat()
        }
    }

    private fun writeWav(file: File, pcm: FloatArray) {
        val dataBytes = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SR); putInt(SR * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }
        file.outputStream().buffered().use { out ->
            out.write(header.array())
            val chunk = ByteBuffer.allocate(16_384).order(ByteOrder.LITTLE_ENDIAN)
            for (v in pcm) {
                chunk.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                if (!chunk.hasRemaining()) { out.write(chunk.array(), 0, chunk.position()); chunk.clear() }
            }
            out.write(chunk.array(), 0, chunk.position())
        }
    }
}
