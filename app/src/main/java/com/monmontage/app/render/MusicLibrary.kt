package com.monmontage.app.render

import android.content.Context
import com.monmontage.app.model.MusicTrack
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Les 4 musiques embarquées, composées par l'application elle-même au premier usage
 * (aucun droit d'auteur, aucun fichier audio dans le dépôt). Chaque morceau est une boucle
 * sans couture, écrite une seule fois en WAV 44,1 kHz mono dans le stockage privé.
 */
object MusicLibrary {
    private const val SR = 44_100
    private const val VERSION = 3   // ROCK ajouté : nouveau fichier, les autres restent valides

    fun durationMs(track: MusicTrack): Long = when (track) {
        MusicTrack.DOUCE -> 24_000L        // 80 BPM, 8 mesures
        MusicTrack.ENERGIQUE -> 16_000L    // 120 BPM, 8 mesures
        MusicTrack.CINEMATIQUE -> 30_000L  // 64 BPM, 8 mesures
        MusicTrack.LOFI -> 26_667L         // 72 BPM, 8 mesures
        MusicTrack.ROCK -> 15_000L         // 128 BPM, 8 mesures
    }

    @Synchronized
    fun ensure(context: Context, track: MusicTrack): File {
        val file = File(context.filesDir, "music_${track.id}_v$VERSION.wav")
        if (file.exists() && file.length() > 44) return file
        val pcm = when (track) {
            MusicTrack.DOUCE -> douce()
            MusicTrack.ENERGIQUE -> energique()
            MusicTrack.CINEMATIQUE -> cinematique()
            MusicTrack.LOFI -> lofi()
            MusicTrack.ROCK -> rock()
        }
        val tmp = File(context.filesDir, file.name + ".tmp")
        writeWav(tmp, pcm)
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        return file
    }

    // ---------------- Morceaux ----------------

    private fun douce(): FloatArray = Synth(durationMs(MusicTrack.DOUCE)).run {
        val chords = arrayOf(intArrayOf(48, 52, 55, 60), intArrayOf(45, 52, 57, 60), intArrayOf(41, 48, 53, 57), intArrayOf(43, 50, 55, 59))
        val arp = intArrayOf(0, 1, 2, 3, 2, 1, 2, 3)
        chords.forEachIndexed { i, ch ->
            val s = i * 6.0
            ch.forEach { m -> tone(s, 6.0, hz(m), 0.085, attack = 1.2, release = 1.8, wave = PAD) }
            tone(s, 2.8, hz(ch[0] - 12), 0.16, 0.02, 0.5)
            tone(s + 3.0, 2.8, hz(ch[0] - 12), 0.14, 0.02, 0.5)
            for (k in 0 until 16) tone(s + k * 0.375, 0.3, hz(ch[arp[k % 8]] + 12), if (k % 4 == 0) 0.075 else 0.05, 0.004, 0.9, decay = 4.5)
        }
        finish()
    }

    private fun energique(): FloatArray = Synth(durationMs(MusicTrack.ENERGIQUE)).run {
        val beat = 0.5
        val chords = arrayOf(intArrayOf(57, 60, 64), intArrayOf(53, 57, 60), intArrayOf(55, 60, 64), intArrayOf(55, 59, 62))
        chords.forEachIndexed { i, ch ->
            val s = i * 4.0
            ch.forEach { m -> tone(s, 4.0, hz(m), 0.035, 0.3, 0.6, wave = SAW) }
            for (e in 0 until 16) {          // basse en croches
                val t = s + e * beat / 2
                tone(t, 0.18, hz(ch[0] - 24), if (e % 2 == 0) 0.2 else 0.13, 0.005, 0.05, wave = SAW, decay = 7.0)
            }
            for (q in 0 until 32) {          // arpège en doubles croches
                tone(s + q * beat / 4, 0.1, hz(ch[q % 3] + 12 + if (q % 8 >= 6) 12 else 0), 0.035, 0.002, 0.25, decay = 9.0)
            }
        }
        val bars = 8
        for (b in 0 until bars * 4) {
            val t = b * beat
            kick(t, 0.5)
            hat(t + beat / 2, 0.08)
            if (b % 2 == 1) snare(t, 0.16)
        }
        finish()
    }

    private fun cinematique(): FloatArray = Synth(durationMs(MusicTrack.CINEMATIQUE)).run {
        val chords = arrayOf(intArrayOf(50, 53, 57, 62), intArrayOf(46, 50, 53, 58), intArrayOf(41, 48, 53, 57), intArrayOf(48, 52, 55, 60))
        val chordLen = 7.5
        chords.forEachIndexed { i, ch ->
            val s = i * chordLen
            ch.forEach { m ->
                tone(s, chordLen, hz(m) * 1.0025, 0.045, attack = 2.5, release = 2.5, wave = SAW)
                tone(s, chordLen, hz(m) * 0.9975, 0.045, attack = 2.5, release = 2.5, wave = SAW)
            }
            tone(s, chordLen, hz(ch[0] - 12), 0.09, 2.0, 2.5, wave = PAD)
            boom(s, 0.35)
            for (k in 0 until 4) {            // notes aiguës éparses, façon piano
                tone(s + k * 1.875 + 0.94, 0.5, hz(ch[(k + 1) % 4] + 24), 0.045, 0.004, 2.5, decay = 2.2)
            }
        }
        lowpass(2_200.0)
        finish()
    }

    private fun lofi(): FloatArray = Synth(durationMs(MusicTrack.LOFI)).run {
        val beat = 60.0 / 72
        val swing = beat * 2 / 3
        val chords = arrayOf(
            intArrayOf(50, 53, 57, 60, 64), intArrayOf(43, 53, 57, 59, 64),
            intArrayOf(48, 52, 55, 59, 62), intArrayOf(45, 52, 55, 60, 64),
        )
        chords.forEachIndexed { i, ch ->
            val s = i * beat * 8
            for (bar in 0 until 2) {
                val b = s + bar * beat * 4
                ch.forEach { m ->            // piano électrique : temps 1 et « et » du 2
                    tone(b, 1.5, hz(m), 0.045, 0.01, 1.2, wave = EP, decay = 1.2)
                    tone(b + beat + swing, 1.0, hz(m), 0.03, 0.01, 1.0, wave = EP, decay = 1.6)
                }
                tone(b, 0.8, hz(ch[0] - 12), 0.2, 0.01, 0.3, decay = 2.5)
                tone(b + 2 * beat + swing, 0.6, hz(ch[0] - 12), 0.15, 0.01, 0.3, decay = 3.0)
                kick(b, 0.32); kick(b + 2 * beat + swing, 0.25)
                snare(b + beat, 0.09, soft = true); snare(b + 3 * beat, 0.09, soft = true)
                for (q in 0 until 4) { hat(b + q * beat, 0.03); hat(b + q * beat + swing, 0.022) }
            }
        }
        crackle(9.0, 0.03)
        lowpass(3_500.0)
        finish()
    }

    /** Rock : accords de puissance saturés en croches, basse, batterie franche (Mi – Do – Sol – Ré). */
    private fun rock(): FloatArray = Synth(durationMs(MusicTrack.ROCK)).run {
        val beat = 60.0 / 128
        val bar = beat * 4
        val roots = intArrayOf(40, 36, 43, 38)          // Mi2, Do2, Sol2, Ré2
        roots.forEachIndexed { i, r ->
            val s0 = i * bar * 2
            for (e in 0 until 16) {                     // 2 mesures de croches « étouffées », accent sur les temps
                val t = s0 + e * beat / 2
                val last = i == 3 && e >= 12             // fin de boucle : accord tenu
                val len = if (last) beat * 2 else beat * 0.42
                if (last && e > 12) continue
                val amp = if (e % 2 == 0) 0.16 else 0.11
                for (m in intArrayOf(r + 12, r + 19, r + 24)) {
                    tone(t, len, hz(m), amp, 0.003, 0.06, wave = SAW, decay = if (last) 0.8 else 5.0, target = guitar)
                }
                tone(t, beat * 0.45, hz(r), if (e % 2 == 0) 0.22 else 0.16, 0.004, 0.05, wave = SAW, decay = 4.0)
            }
            for (k in 0 until 8) {                      // petite mélodie aiguë sur la 2e mesure
                if (k % 2 == 1) tone(s0 + bar + k * beat / 2, beat * 0.4, hz(r + 31 + (if (k == 3) 2 else 0)), 0.035, 0.005, 0.2, decay = 3.0)
            }
        }
        for (b in 0 until 32) {
            val t = b * beat
            kick(t, if (b % 2 == 0) 0.55 else 0.35)
            if (b % 2 == 1) snare(t, 0.24)
            hat(t, 0.06); hat(t + beat / 2, 0.05)
            if (b % 4 == 3) kick(t + beat / 2, 0.3)
        }
        crash(0.0, 0.12)
        mixGuitar(drive = 3.5, level = 0.5)
        finish()
    }

    // ---------------- Synthèse ----------------

    private const val SINE = 0
    private const val SAW = 1
    private const val PAD = 2
    private const val EP = 3
    private const val TABLE = 4096

    private val tables: Array<FloatArray> by lazy {
        fun table(harmonics: (Int) -> Double, count: Int) = FloatArray(TABLE) { i ->
            val ph = 2 * PI * i / TABLE
            var v = 0.0
            for (h in 1..count) v += harmonics(h) * sin(h * ph)
            v.toFloat()
        }.also { t -> val p = t.maxOf { abs(it) }; for (i in t.indices) t[i] /= p }
        arrayOf(
            table({ if (it == 1) 1.0 else 0.0 }, 1),
            table({ 1.0 / it }, 10),                                  // dent de scie adoucie
            table({ when (it) { 1 -> 1.0; 2 -> 0.18; else -> 0.0 } }, 2),
            table({ when (it) { 1 -> 1.0; 2 -> 0.3; 3 -> 0.08; else -> 0.0 } }, 3),
        )
    }

    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private class Synth(durationMs: Long) {
        val n = (SR * durationMs / 1000.0).roundToInt()
        val buf = FloatArray(n)
        /** Piste séparée pour la guitare, saturée avant d'être mélangée. */
        val guitar by lazy { FloatArray(n) }
        private val rnd = java.util.Random(42)

        /** Ajoute un son ; ce qui dépasse la fin revient au début : boucle parfaite. */
        fun tone(
            startS: Double, lenS: Double, freq: Double, amp: Double,
            attack: Double = 0.01, release: Double = 0.2, wave: Int = SINE, decay: Double = 0.0,
            target: FloatArray = buf,
        ) {
            val table = tables[wave]
            val start = (startS * SR).toInt()
            val len = ((lenS + release) * SR).toInt()
            val inc = freq / SR * TABLE
            var phase = 0.0
            for (i in 0 until len) {
                val t = i.toDouble() / SR
                var env = min(1.0, t / attack)
                env *= if (decay > 0) exp(-t * decay) else if (t > lenS) max(0.0, 1 - (t - lenS) / release) else 1.0
                target[(start + i) % n] += (amp * env * table[phase.toInt() and (TABLE - 1)]).toFloat()
                phase += inc
                if (phase >= TABLE) phase -= TABLE
            }
        }

        fun kick(startS: Double, amp: Double) {
            val start = (startS * SR).toInt()
            var phase = 0.0
            for (i in 0 until (0.35 * SR).toInt()) {
                val t = i.toDouble() / SR
                val f = 45 + 75 * exp(-t * 25)
                phase += 2 * PI * f / SR
                buf[(start + i) % n] += (amp * exp(-t * 9) * min(1.0, t / 0.002) * sin(phase)).toFloat()
            }
        }

        fun snare(startS: Double, amp: Double, soft: Boolean = false) {
            val start = (startS * SR).toInt()
            var lp = 0.0
            for (i in 0 until (0.25 * SR).toInt()) {
                val t = i.toDouble() / SR
                val noise = rnd.nextGaussian() * 0.5
                lp += (if (soft) 0.25 else 0.7) * (noise - lp)
                val body = 0.4 * sin(2 * PI * 185 * t) * exp(-t * 30)
                buf[(start + i) % n] += (amp * (lp * exp(-t * (if (soft) 14 else 18)) + body)).toFloat()
            }
        }

        fun hat(startS: Double, amp: Double) {
            val start = (startS * SR).toInt()
            var prev = 0.0
            for (i in 0 until (0.06 * SR).toInt()) {
                val t = i.toDouble() / SR
                val noise = rnd.nextGaussian() * 0.5
                val hp = noise - prev; prev = noise                     // passe-haut simple
                buf[(start + i) % n] += (amp * hp * exp(-t * 60)).toFloat()
            }
        }

        fun boom(startS: Double, amp: Double) {
            val start = (startS * SR).toInt()
            var phase = 0.0
            for (i in 0 until (2.0 * SR).toInt()) {
                val t = i.toDouble() / SR
                phase += 2 * PI * (40 + 30 * exp(-t * 4)) / SR
                buf[(start + i) % n] += (amp * exp(-t * 1.6) * min(1.0, t / 0.01) * sin(phase)).toFloat()
            }
        }

        /** Saturation douce (tanh) de la guitare, puis mélange avec le reste. */
        fun mixGuitar(drive: Double, level: Double) {
            var lp = 0.0
            val a = 1 - exp(-2 * PI * 3_000 / SR)            // adoucit les aigus de la saturation
            for (i in 0 until n) {
                val d = kotlin.math.tanh(drive * guitar[i])
                lp += a * (d - lp)
                buf[i] += (level * lp).toFloat()
            }
        }

        /** Cymbale : bruit aigu à longue décroissance. */
        fun crash(startS: Double, amp: Double) {
            val start = (startS * SR).toInt()
            var prev = 0.0
            for (i in 0 until (1.8 * SR).toInt()) {
                val t = i.toDouble() / SR
                val noise = rnd.nextGaussian() * 0.5
                val hp = noise - prev; prev = noise
                buf[(start + i) % n] += (amp * hp * exp(-t * 2.2)).toFloat()
            }
        }

        /** Grésillement de vinyle : petits clics aléatoires. */
        fun crackle(perSecond: Double, amp: Double) {
            val count = (perSecond * n / SR).toInt()
            repeat(count) {
                val at = rnd.nextInt(n)
                val a = amp * (0.3 + rnd.nextDouble())
                for (k in 0 until 40) buf[(at + k) % n] += (a * exp(-k / 6.0) * (if (k % 2 == 0) 1 else -1)).toFloat()
            }
        }

        /** Passe-bas une pôle, appliqué en deux passes pour un état continu au point de bouclage. */
        fun lowpass(cutoffHz: Double) {
            val a = 1 - exp(-2 * PI * cutoffHz / SR)
            var y = 0.0
            for (x in buf) y += a * (x - y)
            for (i in buf.indices) { y += a * (buf[i] - y); buf[i] = y.toFloat() }
        }

        /** Même niveau perçu pour les 4 morceaux (RMS −16 dBFS) ; les crêtes sont arrondies en douceur. */
        fun finish(): FloatArray {
            var sum = 0.0
            for (v in buf) sum += v * v
            val rms = kotlin.math.sqrt(sum / n)
            if (rms <= 0) return buf
            val k = (0.158 / rms).toFloat()
            for (i in buf.indices) {
                val v = buf[i] * k
                val a = abs(v)
                buf[i] = if (a <= 0.7f) v else Math.copySign(0.7f + 0.25f * kotlin.math.tanh((a - 0.7f) / 0.25f), v)
            }
            return buf
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
