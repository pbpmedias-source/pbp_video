package com.monmontage.app.render

import kotlin.math.max
import kotlin.math.min

/**
 * Courbe de volume de la musique sur toute la timeline.
 * - fondu d'entrée au début, fondu de sortie à la fin ;
 * - descente douce qui se TERMINE au début de chaque interview (la voix démarre sur un silence musical),
 *   puis remontée douce qui COMMENCE à la fin de l'interview ;
 * - courbes en « smoothstep » pour éviter tout effet de marche.
 */
class MusicEnvelope(
    interviewRangesMs: List<Pair<Long, Long>>,
    private val totalMs: Long,
    private val volume: Float,
    private val duckLevel: Float,          // 0 = coupure, 0.12 = sourdine
    private val fadeMs: Double = 800.0,
    private val introMs: Double = 1_000.0,
    private val outroMs: Double = 2_000.0,
) {
    // Deux interviews trop proches : on ne fait pas remonter la musique entre les deux.
    private val ranges: List<Pair<Long, Long>> = interviewRangesMs.fold(mutableListOf<Pair<Long, Long>>()) { acc, r ->
        val last = acc.lastOrNull()
        if (last != null && r.first - last.second < 2 * fadeMs) acc[acc.lastIndex] = last.first to r.second
        else acc += r
        acc
    }

    fun gainAtUs(timeUs: Long): Float {
        val t = timeUs / 1000.0
        var ramp = 1.0
        for ((a, b) in ranges) {
            val r = when {
                t >= a && t <= b -> 0.0
                t >= a - fadeMs && t < a -> (a - t) / fadeMs
                t > b && t <= b + fadeMs -> (t - b) / fadeMs
                else -> 1.0
            }
            ramp = min(ramp, smooth(r))
        }
        var g = duckLevel + (1.0 - duckLevel) * ramp
        if (t < introMs) g *= smooth(t / introMs)
        val remaining = totalMs - t
        if (remaining < outroMs) g *= smooth(max(0.0, remaining) / outroMs)
        return (g * volume).toFloat()
    }

    private fun smooth(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }
}
