package com.monmontage.app.render

import android.graphics.Matrix
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.RgbMatrix
import com.monmontage.app.model.TransitionSettings
import com.monmontage.app.model.TransitionStyle
import kotlin.math.max
import kotlin.math.min

/**
 * Transitions calculées sur chaque élément seul (début et fin), sans superposer deux vidéos :
 * c'est ce qui les rend aussi fiables que le reste de l'export.
 *  - Fondu au noir : l'image s'assombrit en fin d'élément, la suivante part du noir.
 *  - Flash blanc : l'image blanchit en fin d'élément, la suivante part du blanc.
 *  - Zoom doux : chaque élément (sauf le premier) entre avec un léger zoom qui se résorbe.
 */
class EdgeShape(
    private val itemDurationUs: Long,
    halfDurationUs: Long,
    private val atStart: Boolean,
    private val atEnd: Boolean,
) {
    private val half = min(halfDurationUs, itemDurationUs / 3).coerceAtLeast(1)

    /** 0 = image normale, 1 = transition au maximum. */
    fun amount(localUs: Long): Float {
        var a = 0f
        if (atStart && localUs < half) a = max(a, 1f - localUs.toFloat() / half)
        val remaining = itemDurationUs - localUs
        if (atEnd && remaining < half) a = max(a, 1f - remaining.coerceAtLeast(0).toFloat() / half)
        val c = a.coerceIn(0f, 1f)
        return c * c * (3 - 2 * c)
    }

    companion object {
        /** Forme de la transition pour l'élément n° [index] sur [count]. */
        fun forItem(settings: TransitionSettings, index: Int, count: Int, itemDurationMs: Long): EdgeShape? {
            val first = index == 0
            val last = index == count - 1
            val (start, end) = when (settings.style) {
                TransitionStyle.NONE -> return null
                TransitionStyle.FADE_BLACK -> true to true          // ouverture et fermeture au noir comprises
                TransitionStyle.FLASH_WHITE -> !first to !last      // pas de flash au tout début ni à la toute fin
                TransitionStyle.ZOOM -> !first to false
            }
            if (!start && !end) return null
            return EdgeShape(itemDurationMs * 1000, settings.durationMs * 1000 / 2, start, end)
        }
    }
}

/** Mesure le temps local de l'élément à partir de la première image reçue. */
private class LocalClock {
    private var base = C.TIME_UNSET
    fun local(ptsUs: Long): Long {
        if (base == C.TIME_UNSET) base = ptsUs
        return ptsUs - base
    }
}

/** Fondu vers le noir (toWhite = false) ou vers le blanc (toWhite = true), par matrice de couleur. */
@UnstableApi
class FadeColorEffect(private val shape: EdgeShape, private val toWhite: Boolean) : RgbMatrix {
    private val clock = LocalClock()
    private val m = FloatArray(16)

    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
        val a = shape.amount(clock.local(presentationTimeUs))
        val k = 1f - a
        android.opengl.Matrix.setIdentityM(m, 0)
        m[0] = k; m[5] = k; m[10] = k                         // couleur × (1 − a)
        if (toWhite) { m[12] = a; m[13] = a; m[14] = a }      // + a (vers le blanc)
        return m.copyOf()
    }
}

/** Entrée en zoom : l'image démarre agrandie de 12 % et revient à sa taille. */
@UnstableApi
class ZoomInEffect(private val shape: EdgeShape) : MatrixTransformation {
    private val clock = LocalClock()

    override fun getMatrix(presentationTimeUs: Long): Matrix {
        val s = 1f + 0.12f * shape.amount(clock.local(presentationTimeUs))
        return Matrix().apply { postScale(s, s) }
    }
}

object TransitionEffects {
    /** Effets vidéo de transition à ajouter APRÈS la mise à l'échelle de l'élément. */
    @UnstableApi
    fun videoEffects(settings: TransitionSettings, index: Int, count: Int, itemDurationMs: Long): List<Effect> {
        val shape = EdgeShape.forItem(settings, index, count, itemDurationMs) ?: return emptyList()
        return when (settings.style) {
            TransitionStyle.FADE_BLACK -> listOf(FadeColorEffect(shape, toWhite = false))
            TransitionStyle.FLASH_WHITE -> listOf(FadeColorEffect(shape, toWhite = true))
            TransitionStyle.ZOOM -> listOf(ZoomInEffect(shape))
            TransitionStyle.NONE -> emptyList()
        }
    }

    /**
     * Gain du son d'origine d'un élément : suit le fondu de l'image (fondu au noir / flash),
     * et dans tous les cas un micro-fondu de 20 ms aux bords pour éviter les « clics ».
     */
    fun audioGain(settings: TransitionSettings, index: Int, count: Int, itemDurationMs: Long): (Long) -> Float {
        val shape = when (settings.style) {
            TransitionStyle.FADE_BLACK, TransitionStyle.FLASH_WHITE -> EdgeShape.forItem(settings, index, count, itemDurationMs)
            else -> null
        }
        val durUs = itemDurationMs * 1000
        return { tUs ->
            val declick = min(1f, min(tUs / 20_000f, (durUs - tUs) / 20_000f)).coerceIn(0f, 1f)
            val fade = shape?.let { 1f - it.amount(tUs) } ?: 1f
            min(declick, fade)
        }
    }
}
