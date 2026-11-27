package com.monmontage.app.render

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.monmontage.app.model.CardStyle
import com.monmontage.app.model.EndCard
import com.monmontage.app.model.TitleCard
import kotlin.math.max
import kotlin.math.min

/**
 * Couleurs d'une charte graphique, et teintes dérivées calculées automatiquement
 * (texte lisible sur chaque couleur, nuances claires pour les intitulés).
 */
data class KitColors(val accent: Int, val primary: Int, val dark: Int) {
    /** Texte posé sur l'accent : foncé si l'accent est clair, blanc sinon. */
    val onAccent: Int get() = if (luminance(accent) > 0.35) dark.takeIf { luminance(it) < 0.1 } ?: 0xFF111111.toInt() else Color.WHITE
    /** Texte posé sur la couleur principale. */
    val onPrimary: Int get() = if (luminance(primary) > 0.5) 0xFF111111.toInt() else Color.WHITE
    /** Texte principal (couleur principale si elle est assez foncée pour un fond blanc). */
    val primaryOnWhite: Int get() = if (luminance(primary) < 0.3) primary else dark
    val accentLight: Int get() = mix(accent, Color.WHITE, 0.35f)
    val mutedOnDark: Int get() = mix(Color.WHITE, primary, 0.4f)
    val lightOnDark: Int get() = mix(Color.WHITE, primary, 0.15f)
    val mid: Int get() = mix(dark, primary, 0.5f)

    companion object {
        /** Charte PBP Médias, relevée sur le logo. */
        val PBP = KitColors(0xFFFF8E0A.toInt(), 0xFF72037D.toInt(), 0xFF2A0630.toInt())

        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        /** Luminance relative (0 = noir, 1 = blanc), comme pour les calculs de contraste. */
        fun luminance(c: Int): Double {
            fun lin(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
            return 0.2126 * lin((c shr 16) and 0xFF) + 0.7152 * lin((c shr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
        }
    }
}

/** Fonds des bandeaux « Flash info ». */
private const val BAND_BLACK = 0xFF111111.toInt()

/** Polices des génériques (embarquées dans assets/fonts, licence SIL OFL). */
class CardFonts private constructor(context: Context) {
    private val assets = context.assets
    private val cache = HashMap<String, Typeface>()

    private fun load(file: String, axes: String?): Typeface = cache.getOrPut("$file|$axes") {
        runCatching {
            Typeface.Builder(assets, "fonts/$file").apply { if (axes != null) setFontVariationSettings(axes) }.build()
        }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    fun inter(weight: Int) = load("Inter.ttf", "'wght' $weight")
    fun oswald(weight: Int) = load("Oswald.ttf", "'wght' $weight")
    fun anton() = load("Anton.ttf", null)
    fun fraunces(weight: Int) = load("Fraunces.ttf", "'wght' $weight, 'opsz' 72")

    companion object {
        @Volatile private var instance: CardFonts? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: CardFonts(context.applicationContext).also { instance = it }
        }
    }
}

/**
 * Dessin des génériques (3 styles façon journal télévisé), des bandeaux de légende et du logo en incrustation.
 * Les mêmes fonctions servent à l'aperçu dans l'interface et au rendu final 1920×1080.
 * Les mesures sont celles de la maquette (960×540), multipliées par s = hauteur / 540.
 */
object Painter {

    // =============================== Génériques ===============================

    fun renderTitleCard(context: Context, w: Int, h: Int, style: CardStyle, card: TitleCard, logo: Bitmap?, background: Bitmap?, k: KitColors = KitColors.PBP): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val f = CardFonts.get(context)
        when (style) {
            CardStyle.EDITION_SOIR -> titleA(c, w, h, f, k, card, logo, background)
            CardStyle.FLASH_INFO -> titleB(c, w, h, f, k, card, logo, background)
            CardStyle.MAGAZINE -> titleC(c, w, h, f, k, card, logo, background)
        }
        return bmp
    }

    fun renderEndCard(context: Context, w: Int, h: Int, style: CardStyle, card: EndCard, logo: Bitmap?, background: Bitmap?, k: KitColors = KitColors.PBP): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val f = CardFonts.get(context)
        when (style) {
            CardStyle.EDITION_SOIR -> endA(c, w, h, f, k, card, logo, background)
            CardStyle.FLASH_INFO -> endB(c, w, h, f, k, card, logo, background)
            CardStyle.MAGAZINE -> endC(c, w, h, f, k, card, logo, background)
        }
        return bmp
    }

    // ---------- Style A · Édition du soir ----------

    private fun titleA(c: Canvas, w: Int, h: Int, f: CardFonts, k: KitColors, card: TitleCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        background(c, w, h, bg, k.dark)
        if (bg != null) c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, h.toFloat(), 0f, 0f, intArrayOf(0xE0040A16.toInt(), 0x59040A16, 0x40040A16), floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
        })
        logo?.let { drawFit(c, it, 40 * s, 28 * s, 62 * s, 75 * s) }
        if (card.date.isNotBlank()) {
            val p = paint(f.oswald(600), 15 * s, Color.WHITE, 0.08f)
            val tw = p.measureText(card.date)
            val r = RectF(w - 40 * s - tw - 24 * s, 44 * s, w - 40 * s, 44 * s + lineH(p) + 12 * s)
            c.drawRect(r, fill(0x99040A16.toInt()))
            c.drawText(card.date, r.left + 12 * s, baseline(r.top, r.height(), p), p)
        }
        // Pile de bandeaux, ancrée en bas à gauche, construite de bas en haut.
        val x = 56 * s
        val maxW = w - 2 * x
        var bottom = h - 58 * s
        if (card.author.isNotBlank()) {
            val p = paint(f.inter(500), 15 * s, 0xFFE6ECF3.toInt())
            c.drawText(ellipsize(card.author, p, maxW), x, bottom - p.descent(), p)
            bottom -= lineH(p) + 12 * s
        }
        if (card.subtitle.isNotBlank())
            bottom = bandUp(c, card.subtitle, paint(f.inter(600), 21 * s, k.primaryOnWhite), x, bottom, 22 * s, 22 * s, 8 * s, 8 * s, Color.WHITE, maxW)
        bottom = bandUp(c, card.title.uppercase(), fitPaint(card.title.uppercase(), f.oswald(600), 52 * s, 34 * s, k.onPrimary, maxW - 44 * s),
            x, bottom, 22 * s, 22 * s, 8 * s, 10 * s, k.primary, maxW)
        if (card.tag.isNotBlank())
            bandUp(c, card.tag.uppercase(), paint(f.oswald(600), 17 * s, k.onAccent, 0.12f), x, bottom, 14 * s, 14 * s, 5 * s, 5 * s, k.accent, maxW)
        c.drawRect(0f, h - 6 * s, w.toFloat(), h.toFloat(), fill(k.accent))
    }

    private fun endA(c: Canvas, w: Int, h: Int, f: CardFonts, k: KitColors, card: EndCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        background(c, w, h, bg, k.dark)
        if (bg != null) c.drawColor(0xB8040A16.toInt())
        val x = 56 * s
        val maxW = w - 2 * x
        var top = 56 * s
        logo?.let { drawFit(c, it, x, top, 80 * s, 96 * s); top += 96 * s + 40 * s }
        if (card.tag.isNotBlank())
            top = bandDown(c, card.tag.uppercase(), paint(f.oswald(600), 17 * s, k.onAccent, 0.12f), x, top, 14 * s, 14 * s, 5 * s, 5 * s, k.accent, maxW)
        top = bandDown(c, card.title.uppercase(), fitPaint(card.title.uppercase(), f.oswald(600), 48 * s, 32 * s, k.onPrimary, maxW - 44 * s),
            x, top, 22 * s, 22 * s, 8 * s, 10 * s, k.primary, maxW)
        if (card.subtitle.isNotBlank())
            bandDown(c, card.subtitle, paint(f.inter(600), 20 * s, k.primaryOnWhite), x, top, 22 * s, 22 * s, 8 * s, 8 * s, Color.WHITE, maxW)
        // Crédits en colonnes, en bas.
        val label = paint(f.oswald(600), 12 * s, k.accentLight, 0.12f)
        val value = paint(f.inter(500), 14 * s, 0xFFE6ECF3.toInt())
        var cx = x
        val baseValue = h - 44 * s - value.descent()
        for ((role, name) in card.creditPairs.take(4)) {
            val colW = max(label.measureText(role.uppercase()), value.measureText(name))
            if (cx + colW > w - x) break
            if (role.isNotEmpty()) c.drawText(role.uppercase(), cx, baseValue - lineH(value) - label.descent(), label)
            c.drawText(name, cx, baseValue, value)
            cx += colW + 48 * s
        }
        c.drawRect(0f, h - 6 * s, w.toFloat(), h.toFloat(), fill(k.accent))
    }

    // ---------- Style B · Flash info ----------

    private fun titleB(c: Canvas, w: Int, h: Int, f: CardFonts, k: KitColors, card: TitleCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        background(c, w, h, bg, 0xFF161616.toInt())
        if (bg != null) c.drawColor(0x8008080C.toInt())
        logo?.let { drawFit(c, it, w - 40 * s - 62 * s, 26 * s, 62 * s, 75 * s) }
        val maxW = w - 120 * s
        var top = 250 * s
        if (card.tag.isNotBlank())
            top = bandDown(c, card.tag.uppercase(), paint(f.anton(), 22 * s, k.onAccent, 0.06f), 48 * s, top, 14 * s, 14 * s, 4 * s, 4 * s, k.accent, maxW)
        val tp = fitPaint(card.title.uppercase(), f.anton(), 64 * s, 40 * s, Color.WHITE, maxW - 88 * s)
        top = bandDown(c, card.title.uppercase(), tp, 0f, top, 48 * s, 40 * s, 6 * s, 8 * s, BAND_BLACK, maxW, slantRight = 28 * s)
        if (card.subtitle.isNotBlank())
            bandDown(c, card.subtitle.uppercase(), paint(f.inter(800), 19 * s, k.onPrimary, 0.04f), 0f, top, 48 * s, 40 * s, 7 * s, 7 * s, k.primary, maxW, slantRight = 16 * s)
        ticker(c, w, h, s, f, k, "INFO", listOf(card.date, card.author.takeIf { it.isNotBlank() }?.let { "Un reportage de $it" } ?: "", card.place))
    }

    private fun endB(c: Canvas, w: Int, h: Int, f: CardFonts, k: KitColors, card: EndCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        background(c, w, h, bg, 0xFF161616.toInt())
        if (bg != null) c.drawColor(0xB308080C.toInt())
        logo?.let { drawFit(c, it, (w - 126 * s) / 2, 52 * s, 126 * s, 152 * s) }
        val maxW = w - 120 * s
        val tp = fitPaint(card.title.uppercase(), f.anton(), 56 * s, 36 * s, Color.WHITE, maxW - 88 * s)
        var top = bandDown(c, card.title.uppercase(), tp, 0f, 260 * s, 44 * s, 44 * s, 6 * s, 8 * s, BAND_BLACK, maxW, slantLeft = 24 * s, slantRight = 24 * s, centerIn = w.toFloat())
        if (card.subtitle.isNotBlank())
            top = bandDown(c, card.subtitle.uppercase(), paint(f.inter(800), 18 * s, k.onPrimary, 0.04f), 0f, top, 40 * s, 40 * s, 7 * s, 7 * s, k.primary, maxW, slantLeft = 14 * s, slantRight = 14 * s, centerIn = w.toFloat())
        ticker(c, w, h, s, f, k, "CRÉDITS", card.creditPairs.map { (r, n) -> if (r.isEmpty()) n else "$r : $n" })
    }

    private fun ticker(c: Canvas, w: Int, h: Int, s: Float, f: CardFonts, k: KitColors, label: String, parts: List<String>) {
        val top = h - 44 * s
        c.drawRect(0f, top, w.toFloat(), h.toFloat(), fill(BAND_BLACK))
        val lp = paint(f.anton(), 18 * s, k.onAccent, 0.06f)
        val lw = lp.measureText(label) + 32 * s
        c.drawRect(0f, top, lw, h.toFloat(), fill(k.accent))
        c.drawText(label, 16 * s, baseline(top, 44 * s, lp), lp)
        val text = parts.filter { it.isNotBlank() }.joinToString("  •  ").uppercase()
        if (text.isNotEmpty()) {
            val tp = paint(f.inter(600), 15 * s, Color.WHITE, 0.06f)
            c.drawText(ellipsize(text, tp, w - lw - 36 * s), lw + 18 * s, baseline(top, 44 * s, tp), tp)
        }
    }

    // ---------- Style C · Magazine ----------

    private fun titleC(c: Canvas, w: Int, h: Int, f: CardFonts, k: KitColors, card: TitleCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        val panelW = 384 * s
        background(c, w, h, bg, k.mid)
        c.drawRect(0f, 0f, panelW, h.toFloat(), fill(k.dark))
        val pad = 40 * s
        val inner = (panelW - 2 * pad).toInt()
        logo?.let { drawFit(c, it, pad, pad, 50 * s, 60 * s) }
        // Bas du panneau : auteur à gauche, date à droite.
        val foot = paint(f.inter(500), 13 * s, k.mutedOnDark)
        val footY = h - 36 * s - foot.descent()
        if (card.author.isNotBlank()) c.drawText(ellipsize(card.author, foot, inner * 0.55f), pad, footY, foot)
        if (card.date.isNotBlank()) {
            val d = ellipsize(card.date, foot, inner * 0.45f)
            c.drawText(d, panelW - pad - foot.measureText(d), footY, foot)
        }
        // Bloc central, centré verticalement entre le logo et le pied.
        val tag = paint(f.inter(600), 13 * s, k.accent, 0.22f)
        val title = layout(card.title, paint(f.fraunces(600), 46 * s, Color.WHITE), inner, 4, 1.08f)
        val sub = if (card.subtitle.isNotBlank()) layout(card.subtitle, paint(f.inter(400), 17 * s, k.lightOnDark), inner, 3, 1.5f) else null
        val blockH = (if (card.tag.isNotBlank()) lineH(tag) + 12 * s else 0f) + title.height + 43 * s + (sub?.height ?: 0)
        val areaTop = pad + 60 * s
        val areaBottom = footY - lineH(foot)
        var y = areaTop + max(0f, (areaBottom - areaTop - blockH) / 2)
        if (card.tag.isNotBlank()) { c.drawText(card.tag.uppercase(), pad, y - tag.ascent(), tag); y += lineH(tag) + 12 * s }
        drawLayout(c, title, pad, y); y += title.height + 20 * s
        c.drawRect(pad, y, pad + 44 * s, y + 3 * s, fill(k.accent)); y += 23 * s
        sub?.let { drawLayout(c, it, pad, y) }
    }

    private fun endC(c: Canvas, w: Int, h: Int, f: CardFonts, k: KitColors, card: EndCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        val panelW = 384 * s
        val left = w - panelW
        background(c, w, h, bg, k.mid)
        c.drawRect(left, 0f, w.toFloat(), h.toFloat(), fill(k.dark))
        val pad = 40 * s
        val x = left + pad
        val inner = (panelW - 2 * pad).toInt()
        var y = pad
        if (card.tag.isNotBlank()) {
            val tag = paint(f.inter(600), 13 * s, k.accent, 0.22f)
            c.drawText(card.tag.uppercase(), x, y - tag.ascent(), tag); y += lineH(tag) + 12 * s
        }
        val title = layout(card.title, paint(f.fraunces(600), 38 * s, Color.WHITE), inner, 3, 1.1f)
        drawLayout(c, title, x, y); y += title.height + 20 * s
        c.drawRect(x, y, x + 44 * s, y + 3 * s, fill(k.accent)); y += 3 * s + 22 * s
        if (card.subtitle.isNotBlank()) {
            val sub = layout(card.subtitle, paint(f.inter(400), 14 * s, k.lightOnDark), inner, 2, 1.4f)
            drawLayout(c, sub, x, y); y += sub.height + 18 * s
        }
        val label = paint(f.inter(600), 11 * s, k.mutedOnDark, 0.16f)
        val value = paint(f.inter(500), 16 * s, 0xFFE6ECF3.toInt())
        val logoTop = h - pad - 60 * s
        for ((role, name) in card.creditPairs) {
            val need = (if (role.isNotEmpty()) lineH(label) + 2 * s else 0f) + lineH(value)
            if (y + need > logoTop - 16 * s) break
            if (role.isNotEmpty()) { c.drawText(ellipsize(role.uppercase(), label, inner.toFloat()), x, y - label.ascent(), label); y += lineH(label) + 2 * s }
            c.drawText(ellipsize(name, value, inner.toFloat()), x, y - value.ascent(), value); y += lineH(value) + 14 * s
        }
        logo?.let { drawFit(c, it, x, logoTop, 50 * s, 60 * s) }
    }

    // ---------- Cartons (intertitre / citation), dans le style du projet ----------

    fun renderCarton(context: Context, w: Int, h: Int, style: CardStyle, text: String, author: String, quote: Boolean, k: KitColors = KitColors.PBP): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val f = CardFonts.get(context)
        val s = h / 540f
        val body = text.ifBlank { if (quote) "Votre citation" else "Intertitre" }
        when (style) {
            CardStyle.EDITION_SOIR -> {
                c.drawColor(k.dark)
                if (quote) quoteBlock(c, w, h, s, f.oswald(500), f.inter(500), body, author, k, Color.WHITE, alignCenter = false)
                else {
                    val p = fitPaint(body.uppercase(), f.oswald(600), 60 * s, 34 * s, k.onPrimary, w - 156 * s)
                    val bh = 10 * s + (p.descent() - p.ascent()) + 12 * s
                    bandDown(c, body.uppercase(), p, 56 * s, (h - bh) / 2, 24 * s, 24 * s, 10 * s, 12 * s, k.primary, w - 112 * s)
                }
                c.drawRect(0f, h - 6 * s, w.toFloat(), h.toFloat(), fill(k.accent))
            }
            CardStyle.FLASH_INFO -> {
                c.drawColor(0xFF161616.toInt())
                if (quote) quoteBlock(c, w, h, s, f.anton(), f.inter(800), body, author, k, Color.WHITE, alignCenter = false)
                else {
                    val p = fitPaint(body.uppercase(), f.anton(), 72 * s, 40 * s, Color.WHITE, w - 180 * s)
                    val bh = 6 * s + (p.descent() - p.ascent()) + 8 * s
                    val top = (h - bh) / 2
                    bandDown(c, body.uppercase(), p, 0f, top, 48 * s, 44 * s, 6 * s, 8 * s, BAND_BLACK, w - 60 * s, slantRight = 30 * s)
                    c.drawRect(0f, top + bh, w * 0.35f, top + bh + 8 * s, fill(k.accent))
                }
            }
            CardStyle.MAGAZINE -> {
                c.drawColor(k.dark)
                if (quote) quoteBlock(c, w, h, s, f.fraunces(600), f.inter(400), body, author, k, Color.WHITE, alignCenter = true)
                else {
                    val l = StaticLayout.Builder.obtain(body, 0, body.length, paint(f.fraunces(600), 56 * s, Color.WHITE), (w * 0.8f).toInt())
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).build()
                    val top = (h - l.height - 26 * s) / 2
                    drawLayout(c, l, w * 0.1f, top)
                    c.drawRect(w / 2f - 22 * s, top + l.height + 20 * s, w / 2f + 22 * s, top + l.height + 23 * s, fill(k.accent))
                }
            }
        }
        return bmp
    }

    /** Citation : grand guillemet à la couleur d'accent, texte, puis « — Auteur ». */
    private fun quoteBlock(
        c: Canvas, w: Int, h: Int, s: Float, quoteFont: Typeface, authorFont: Typeface,
        text: String, author: String, k: KitColors, color: Int, alignCenter: Boolean,
    ) {
        val width = (w * 0.72f).toInt()
        val x = if (alignCenter) (w - width) / 2f else 96 * s
        val mark = paint(quoteFont, 140 * s, k.accent)
        val textP = paint(quoteFont, 40 * s, color)
        val l = StaticLayout.Builder.obtain("« $text »", 0, text.length + 4, textP, width)
            .setAlignment(if (alignCenter) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(5).setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, 1.15f).build()
        val authorP = paint(authorFont, 20 * s, k.accentLight, 0.04f)
        val authorH = if (author.isBlank()) 0f else 24 * s + (authorP.descent() - authorP.ascent())
        val top = (h - l.height - authorH) / 2
        if (!alignCenter) c.drawText("“", x - 10 * s, top + 70 * s, mark.apply { alpha = 90 })
        drawLayout(c, l, x, top)
        if (author.isNotBlank()) {
            val a = "— $author"
            val ax = if (alignCenter) (w - authorP.measureText(a)) / 2 else x
            c.drawText(ellipsize(a, authorP, width.toFloat()), ax, top + l.height + 24 * s - authorP.ascent(), authorP)
        }
    }

    // ============================ Outils de dessin ============================

    /** Photo en « remplissage » (recadrée au centre), ou fond uni. */
    private fun background(c: Canvas, w: Int, h: Int, bg: Bitmap?, fallback: Int) {
        if (bg == null) { c.drawColor(fallback); return }
        val k = max(w.toFloat() / bg.width, h.toFloat() / bg.height)
        val dw = bg.width * k
        val dh = bg.height * k
        c.drawBitmap(bg, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawFit(c: Canvas, b: Bitmap, x: Float, y: Float, bw: Float, bh: Float) {
        val k = min(bw / b.width, bh / b.height)
        val dw = b.width * k
        val dh = b.height * k
        c.drawBitmap(b, null, RectF(x + (bw - dw) / 2, y + (bh - dh) / 2, x + (bw + dw) / 2, y + (bh + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun paint(tf: Typeface, size: Float, color: Int, spacing: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tf; textSize = size; this.color = color; letterSpacing = spacing
    }

    /** Réduit la taille jusqu'à ce que le texte tienne (sans descendre sous [minSize]). */
    private fun fitPaint(text: String, tf: Typeface, size: Float, minSize: Float, color: Int, maxW: Float): TextPaint {
        val p = paint(tf, size, color)
        while (p.textSize > minSize && p.measureText(text) > maxW) p.textSize -= 1f
        return p
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun lineH(p: Paint) = p.descent() - p.ascent()
    private fun baseline(top: Float, height: Float, p: Paint) = top + height / 2f - (p.descent() + p.ascent()) / 2f
    private fun ellipsize(t: String, p: TextPaint, maxW: Float) = TextUtils.ellipsize(t, p, maxW, TextUtils.TruncateAt.END).toString()

    private fun layout(text: String, p: TextPaint, width: Int, maxLines: Int, spacing: Float) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, spacing).build()

    private fun drawLayout(c: Canvas, l: StaticLayout, x: Float, y: Float) {
        c.save(); c.translate(x, y); l.draw(c); c.restore()
    }

    /** Bandeau de texte dont le BAS est à [bottom] ; renvoie son haut. */
    private fun bandUp(c: Canvas, text: String, p: TextPaint, x: Float, bottom: Float, padL: Float, padR: Float, padT: Float, padB: Float, bg: Int, maxW: Float): Float {
        val top = bottom - (padT + lineH(p) + padB)
        band(c, text, p, x, top, padL, padR, padT, padB, bg, maxW, 0f, 0f, null)
        return top
    }

    /** Bandeau de texte dont le HAUT est à [top] ; renvoie son bas. */
    private fun bandDown(
        c: Canvas, text: String, p: TextPaint, x: Float, top: Float, padL: Float, padR: Float, padT: Float, padB: Float, bg: Int, maxW: Float,
        slantLeft: Float = 0f, slantRight: Float = 0f, centerIn: Float? = null,
    ): Float {
        band(c, text, p, x, top, padL, padR, padT, padB, bg, maxW, slantLeft, slantRight, centerIn)
        return top + padT + lineH(p) + padB
    }

    /** Dessine un bandeau (rectangle, ou parallélogramme si [slantLeft]/[slantRight]) et son texte. */
    private fun band(
        c: Canvas, text: String, p: TextPaint, x0: Float, top: Float, padL: Float, padR: Float, padT: Float, padB: Float, bg: Int, maxW: Float,
        slantLeft: Float, slantRight: Float, centerIn: Float?,
    ) {
        val t = ellipsize(text, p, maxW - padL - padR)
        val bw = padL + p.measureText(t) + padR
        val bottom = top + padT + lineH(p) + padB
        val x = centerIn?.let { (it - bw) / 2 } ?: x0
        val path = Path().apply {
            moveTo(x + slantLeft, top); lineTo(x + bw, top)
            lineTo(x + bw - slantRight, bottom); lineTo(x, bottom); close()
        }
        c.drawPath(path, fill(bg))
        c.drawText(t, x + padL, top + padT - p.ascent(), p)
    }

    // ============================ Légendes et logo ============================

    /** Bandeau de légende façon JT, aux couleurs PBP Médias. */
    fun drawCaption(canvas: Canvas, w: Int, h: Int, name: String, role: String, k: KitColors = KitColors.PBP) {
        val s = h / 1080f
        val left = 96f * s
        val maxTextW = w - 2 * left - 56f * s
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = k.onPrimary; textSize = 54f * s; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val rolePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = k.primaryOnWhite; textSize = 38f * s; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        val nameText = ellipsize(name.ifBlank { " " }, namePaint, maxTextW)
        val roleText = ellipsize(role, rolePaint, maxTextW)
        val padX = 28f * s
        val nameH = 84f * s
        val roleH = if (roleText.isBlank()) 0f else 60f * s
        val top = h * 0.72f
        val nameW = namePaint.measureText(nameText) + 2 * padX
        val roleW = if (roleH > 0) rolePaint.measureText(roleText) + 2 * padX else 0f
        canvas.drawRect(left - 14f * s, top, left, top + nameH + roleH, fill(k.accent))
        canvas.drawRect(left, top, left + nameW, top + nameH, fill((k.primary and 0x00FFFFFF) or (0xF0 shl 24)))
        canvas.drawText(nameText, left + padX, baseline(top, nameH, namePaint), namePaint)
        if (roleH > 0) {
            canvas.drawRect(left, top + nameH, left + roleW, top + nameH + roleH, fill(0xF2FFFFFF.toInt()))
            canvas.drawText(roleText, left + padX, baseline(top + nameH, roleH, rolePaint), rolePaint)
        }
    }

    fun renderCaptionBitmap(w: Int, h: Int, name: String, role: String, k: KitColors = KitColors.PBP): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { drawCaption(Canvas(it), w, h, name, role, k) }

    fun renderCaptionPreview(w: Int, h: Int, name: String, role: String): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it)
            c.drawColor(0xFF5F6B7A.toInt())
            drawCaption(c, w, h, name, role)
        }

    /** Calque plein cadre transparent avec le logo en miniature en bas à droite (7 % de la hauteur). */
    fun renderWatermark(w: Int, h: Int, logo: Bitmap): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it)
            val boxH = h * 0.07f
            val boxW = boxH * logo.width / logo.height
            val margin = h * 0.03f
            val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { alpha = 230 }
            c.drawBitmap(logo, null, RectF(w - margin - boxW, h - margin - boxH, w - margin, h - margin), p)
        }
}
