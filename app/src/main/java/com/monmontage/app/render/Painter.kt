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

/** Charte PBP Médias, relevée sur le logo. */
object Brand {
    const val ORANGE = 0xFFFF8E0A.toInt()
    const val PURPLE = 0xFF72037D.toInt()
    const val PURPLE_DARK = 0xFF2A0630.toInt()
    const val INK = 0xFF2E0233.toInt()
    const val BLACK = 0xFF111111.toInt()
}

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

    fun renderTitleCard(context: Context, w: Int, h: Int, style: CardStyle, card: TitleCard, logo: Bitmap?, background: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val f = CardFonts.get(context)
        when (style) {
            CardStyle.EDITION_SOIR -> titleA(c, w, h, f, card, logo, background)
            CardStyle.FLASH_INFO -> titleB(c, w, h, f, card, logo, background)
            CardStyle.MAGAZINE -> titleC(c, w, h, f, card, logo, background)
        }
        return bmp
    }

    fun renderEndCard(context: Context, w: Int, h: Int, style: CardStyle, card: EndCard, logo: Bitmap?, background: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val f = CardFonts.get(context)
        when (style) {
            CardStyle.EDITION_SOIR -> endA(c, w, h, f, card, logo, background)
            CardStyle.FLASH_INFO -> endB(c, w, h, f, card, logo, background)
            CardStyle.MAGAZINE -> endC(c, w, h, f, card, logo, background)
        }
        return bmp
    }

    // ---------- Style A · Édition du soir ----------

    private fun titleA(c: Canvas, w: Int, h: Int, f: CardFonts, card: TitleCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        background(c, w, h, bg, Brand.PURPLE_DARK)
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
            bottom = bandUp(c, card.subtitle, paint(f.inter(600), 21 * s, Brand.PURPLE), x, bottom, 22 * s, 22 * s, 8 * s, 8 * s, Color.WHITE, maxW)
        bottom = bandUp(c, card.title.uppercase(), fitPaint(card.title.uppercase(), f.oswald(600), 52 * s, 34 * s, Color.WHITE, maxW - 44 * s),
            x, bottom, 22 * s, 22 * s, 8 * s, 10 * s, Brand.PURPLE, maxW)
        if (card.tag.isNotBlank())
            bandUp(c, card.tag.uppercase(), paint(f.oswald(600), 17 * s, Brand.INK, 0.12f), x, bottom, 14 * s, 14 * s, 5 * s, 5 * s, Brand.ORANGE, maxW)
        c.drawRect(0f, h - 6 * s, w.toFloat(), h.toFloat(), fill(Brand.ORANGE))
    }

    private fun endA(c: Canvas, w: Int, h: Int, f: CardFonts, card: EndCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        background(c, w, h, bg, Brand.PURPLE_DARK)
        if (bg != null) c.drawColor(0xB8040A16.toInt())
        val x = 56 * s
        val maxW = w - 2 * x
        var top = 56 * s
        logo?.let { drawFit(c, it, x, top, 80 * s, 96 * s); top += 96 * s + 40 * s }
        if (card.tag.isNotBlank())
            top = bandDown(c, card.tag.uppercase(), paint(f.oswald(600), 17 * s, Brand.INK, 0.12f), x, top, 14 * s, 14 * s, 5 * s, 5 * s, Brand.ORANGE, maxW)
        top = bandDown(c, card.title.uppercase(), fitPaint(card.title.uppercase(), f.oswald(600), 48 * s, 32 * s, Color.WHITE, maxW - 44 * s),
            x, top, 22 * s, 22 * s, 8 * s, 10 * s, Brand.PURPLE, maxW)
        if (card.subtitle.isNotBlank())
            bandDown(c, card.subtitle, paint(f.inter(600), 20 * s, Brand.PURPLE), x, top, 22 * s, 22 * s, 8 * s, 8 * s, Color.WHITE, maxW)
        // Crédits en colonnes, en bas.
        val label = paint(f.oswald(600), 12 * s, 0xFFFFB45C.toInt(), 0.12f)
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
        c.drawRect(0f, h - 6 * s, w.toFloat(), h.toFloat(), fill(Brand.ORANGE))
    }

    // ---------- Style B · Flash info ----------

    private fun titleB(c: Canvas, w: Int, h: Int, f: CardFonts, card: TitleCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        background(c, w, h, bg, 0xFF161616.toInt())
        if (bg != null) c.drawColor(0x8008080C.toInt())
        logo?.let { drawFit(c, it, w - 40 * s - 62 * s, 26 * s, 62 * s, 75 * s) }
        val maxW = w - 120 * s
        var top = 250 * s
        if (card.tag.isNotBlank())
            top = bandDown(c, card.tag.uppercase(), paint(f.anton(), 22 * s, Brand.BLACK, 0.06f), 48 * s, top, 14 * s, 14 * s, 4 * s, 4 * s, Brand.ORANGE, maxW)
        val tp = fitPaint(card.title.uppercase(), f.anton(), 64 * s, 40 * s, Color.WHITE, maxW - 88 * s)
        top = bandDown(c, card.title.uppercase(), tp, 0f, top, 48 * s, 40 * s, 6 * s, 8 * s, Brand.BLACK, maxW, slantRight = 28 * s)
        if (card.subtitle.isNotBlank())
            bandDown(c, card.subtitle.uppercase(), paint(f.inter(800), 19 * s, Color.WHITE, 0.04f), 0f, top, 48 * s, 40 * s, 7 * s, 7 * s, Brand.PURPLE, maxW, slantRight = 16 * s)
        ticker(c, w, h, s, f, "INFO", listOf(card.date, card.author.takeIf { it.isNotBlank() }?.let { "Un reportage de $it" } ?: "", card.place))
    }

    private fun endB(c: Canvas, w: Int, h: Int, f: CardFonts, card: EndCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        background(c, w, h, bg, 0xFF161616.toInt())
        if (bg != null) c.drawColor(0xB308080C.toInt())
        logo?.let { drawFit(c, it, (w - 126 * s) / 2, 52 * s, 126 * s, 152 * s) }
        val maxW = w - 120 * s
        val tp = fitPaint(card.title.uppercase(), f.anton(), 56 * s, 36 * s, Color.WHITE, maxW - 88 * s)
        var top = bandDown(c, card.title.uppercase(), tp, 0f, 260 * s, 44 * s, 44 * s, 6 * s, 8 * s, Brand.BLACK, maxW, slantLeft = 24 * s, slantRight = 24 * s, centerIn = w.toFloat())
        if (card.subtitle.isNotBlank())
            top = bandDown(c, card.subtitle.uppercase(), paint(f.inter(800), 18 * s, Color.WHITE, 0.04f), 0f, top, 40 * s, 40 * s, 7 * s, 7 * s, Brand.PURPLE, maxW, slantLeft = 14 * s, slantRight = 14 * s, centerIn = w.toFloat())
        ticker(c, w, h, s, f, "CRÉDITS", card.creditPairs.map { (r, n) -> if (r.isEmpty()) n else "$r : $n" })
    }

    private fun ticker(c: Canvas, w: Int, h: Int, s: Float, f: CardFonts, label: String, parts: List<String>) {
        val top = h - 44 * s
        c.drawRect(0f, top, w.toFloat(), h.toFloat(), fill(Brand.BLACK))
        val lp = paint(f.anton(), 18 * s, Brand.BLACK, 0.06f)
        val lw = lp.measureText(label) + 32 * s
        c.drawRect(0f, top, lw, h.toFloat(), fill(Brand.ORANGE))
        c.drawText(label, 16 * s, baseline(top, 44 * s, lp), lp)
        val text = parts.filter { it.isNotBlank() }.joinToString("  •  ").uppercase()
        if (text.isNotEmpty()) {
            val tp = paint(f.inter(600), 15 * s, Color.WHITE, 0.06f)
            c.drawText(ellipsize(text, tp, w - lw - 36 * s), lw + 18 * s, baseline(top, 44 * s, tp), tp)
        }
    }

    // ---------- Style C · Magazine ----------

    private fun titleC(c: Canvas, w: Int, h: Int, f: CardFonts, card: TitleCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        val panelW = 384 * s
        background(c, w, h, bg, 0xFF4A1652.toInt())
        c.drawRect(0f, 0f, panelW, h.toFloat(), fill(Brand.PURPLE_DARK))
        val pad = 40 * s
        val inner = (panelW - 2 * pad).toInt()
        logo?.let { drawFit(c, it, pad, pad, 50 * s, 60 * s) }
        // Bas du panneau : auteur à gauche, date à droite.
        val foot = paint(f.inter(500), 13 * s, 0xFFC7A6CC.toInt())
        val footY = h - 36 * s - foot.descent()
        if (card.author.isNotBlank()) c.drawText(ellipsize(card.author, foot, inner * 0.55f), pad, footY, foot)
        if (card.date.isNotBlank()) {
            val d = ellipsize(card.date, foot, inner * 0.45f)
            c.drawText(d, panelW - pad - foot.measureText(d), footY, foot)
        }
        // Bloc central, centré verticalement entre le logo et le pied.
        val tag = paint(f.inter(600), 13 * s, Brand.ORANGE, 0.22f)
        val title = layout(card.title, paint(f.fraunces(600), 46 * s, Color.WHITE), inner, 4, 1.08f)
        val sub = if (card.subtitle.isNotBlank()) layout(card.subtitle, paint(f.inter(400), 17 * s, 0xFFEAD9EE.toInt()), inner, 3, 1.5f) else null
        val blockH = (if (card.tag.isNotBlank()) lineH(tag) + 12 * s else 0f) + title.height + 43 * s + (sub?.height ?: 0)
        val areaTop = pad + 60 * s
        val areaBottom = footY - lineH(foot)
        var y = areaTop + max(0f, (areaBottom - areaTop - blockH) / 2)
        if (card.tag.isNotBlank()) { c.drawText(card.tag.uppercase(), pad, y - tag.ascent(), tag); y += lineH(tag) + 12 * s }
        drawLayout(c, title, pad, y); y += title.height + 20 * s
        c.drawRect(pad, y, pad + 44 * s, y + 3 * s, fill(Brand.ORANGE)); y += 23 * s
        sub?.let { drawLayout(c, it, pad, y) }
    }

    private fun endC(c: Canvas, w: Int, h: Int, f: CardFonts, card: EndCard, logo: Bitmap?, bg: Bitmap?) {
        val s = h / 540f
        val panelW = 384 * s
        val left = w - panelW
        background(c, w, h, bg, 0xFF4A1652.toInt())
        c.drawRect(left, 0f, w.toFloat(), h.toFloat(), fill(Brand.PURPLE_DARK))
        val pad = 40 * s
        val x = left + pad
        val inner = (panelW - 2 * pad).toInt()
        var y = pad
        if (card.tag.isNotBlank()) {
            val tag = paint(f.inter(600), 13 * s, Brand.ORANGE, 0.22f)
            c.drawText(card.tag.uppercase(), x, y - tag.ascent(), tag); y += lineH(tag) + 12 * s
        }
        val title = layout(card.title, paint(f.fraunces(600), 38 * s, Color.WHITE), inner, 3, 1.1f)
        drawLayout(c, title, x, y); y += title.height + 20 * s
        c.drawRect(x, y, x + 44 * s, y + 3 * s, fill(Brand.ORANGE)); y += 3 * s + 22 * s
        if (card.subtitle.isNotBlank()) {
            val sub = layout(card.subtitle, paint(f.inter(400), 14 * s, 0xFFEAD9EE.toInt()), inner, 2, 1.4f)
            drawLayout(c, sub, x, y); y += sub.height + 18 * s
        }
        val label = paint(f.inter(600), 11 * s, 0xFFC7A6CC.toInt(), 0.16f)
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
    fun drawCaption(canvas: Canvas, w: Int, h: Int, name: String, role: String) {
        val s = h / 1080f
        val left = 96f * s
        val maxTextW = w - 2 * left - 56f * s
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 54f * s; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val rolePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Brand.INK; textSize = 38f * s; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        val nameText = ellipsize(name.ifBlank { " " }, namePaint, maxTextW)
        val roleText = ellipsize(role, rolePaint, maxTextW)
        val padX = 28f * s
        val nameH = 84f * s
        val roleH = if (roleText.isBlank()) 0f else 60f * s
        val top = h * 0.72f
        val nameW = namePaint.measureText(nameText) + 2 * padX
        val roleW = if (roleH > 0) rolePaint.measureText(roleText) + 2 * padX else 0f
        canvas.drawRect(left - 14f * s, top, left, top + nameH + roleH, fill(Brand.ORANGE))
        canvas.drawRect(left, top, left + nameW, top + nameH, fill((Brand.PURPLE and 0x00FFFFFF) or (0xF0 shl 24)))
        canvas.drawText(nameText, left + padX, baseline(top, nameH, namePaint), namePaint)
        if (roleH > 0) {
            canvas.drawRect(left, top + nameH, left + roleW, top + nameH + roleH, fill(0xF2FFFFFF.toInt()))
            canvas.drawText(roleText, left + padX, baseline(top + nameH, roleH, rolePaint), rolePaint)
        }
    }

    fun renderCaptionBitmap(w: Int, h: Int, name: String, role: String): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { drawCaption(Canvas(it), w, h, name, role) }

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
