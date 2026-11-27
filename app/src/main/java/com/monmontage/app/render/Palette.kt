package com.monmontage.app.render

import android.graphics.Bitmap
import android.graphics.Color

/** Couleurs dominantes d'un logo (pour remplir la charte en un geste). Blanc, noir et transparent ignorés. */
object Palette {
    fun dominant(bitmap: Bitmap, count: Int = 4): List<Int> {
        val small = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        val buckets = HashMap<Int, IntArray>()   // clé quantifiée -> [n, r, g, b]
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            if (Color.alpha(c) < 200) continue
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            val max = maxOf(r, g, b); val min = minOf(r, g, b)
            if (max > 235 && min > 225) continue        // blanc
            if (max < 25) continue                      // noir
            val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
            val acc = buckets.getOrPut(key) { IntArray(4) }
            acc[0]++; acc[1] += r; acc[2] += g; acc[3] += b
        }
        if (small !== bitmap) small.recycle()
        return buckets.values.sortedByDescending { it[0] }.take(count)
            .map { Color.rgb(it[1] / it[0], it[2] / it[0], it[3] / it[0]) }
    }
}
