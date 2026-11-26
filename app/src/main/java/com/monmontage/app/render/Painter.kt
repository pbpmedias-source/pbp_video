package com.monmontage.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.monmontage.app.model.EndCard
import com.monmontage.app.model.TitleCard
import kotlin.math.max
import kotlin.math.min

/**
 * Dessin des génériques et des bandeaux de légende.
 * Le même code sert à l'aperçu dans l'interface et au rendu final : ce que l'on voit est ce qui est exporté.
 * Toutes les dimensions sont proportionnelles à la hauteur (référence 1080 px).
 */
object Painter {
    private const val ACCENT = 0xFFE53935.toInt()
    private const val NEWS_BLUE = 0xF00D2B52.toInt()
    private val BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val REGULAR: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    // ---------- Légende façon journal télévisé ----------

    fun drawCaption(canvas: Canvas, w: Int, h: Int, name: String, role: String) {
        val s = h / 1080f
        val left = 96f * s
        val maxTextW = w - 2 * left - 56f * s
        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 54f * s; typeface = BOLD }
        val rolePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1A1A.toInt(); textSize = 38f * s; typeface = REGULAR }
        val nameText = TextUtils.ellipsize(name.ifBlank { " " }, namePaint, maxTextW, TextUtils.TruncateAt.END).toString()
        val roleText = TextUtils.ellipsize(role, rolePaint, maxTextW, TextUtils.TruncateAt.END).toString()
        val padX = 28f * s
        val nameH = 84f * s
        val roleH = if (roleText.isBlank()) 0f else 60f * s
        val top = h * 0.72f
        val nameW = namePaint.measureText(nameText) + 2 * padX
        val roleW = if (roleH > 0) rolePaint.measureText(roleText) + 2 * padX else 0f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fill.color = ACCENT
        canvas.drawRect(left - 14f * s, top, left, top + nameH + roleH, fill)
        fill.color = NEWS_BLUE
        canvas.drawRect(left, top, left + nameW, top + nameH, fill)
        canvas.drawText(nameText, left + padX, centerBaseline(top, nameH, namePaint), namePaint)
        if (roleH > 0) {
            fill.color = 0xF2FFFFFF.toInt()
            canvas.drawRect(left, top + nameH, left + roleW, top + nameH + roleH, fill)
            canvas.drawText(roleText, left + padX, centerBaseline(top + nameH, roleH, rolePaint), rolePaint)
        }
    }

    /** Image plein cadre transparente contenant uniquement le bandeau (utilisée comme calque vidéo). */
    fun renderCaptionBitmap(w: Int, h: Int, name: String, role: String): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            drawCaption(Canvas(it), w, h, name, role)
        }

    /** Vignette d'aperçu pour l'écran d'édition (fond gris simulant la vidéo). */
    fun renderCaptionPreview(w: Int, h: Int, name: String, role: String): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            val c = Canvas(it)
            c.drawColor(0xFF5F6B7A.toInt())
            drawCaption(c, w, h, name, role)
        }

    private fun centerBaseline(top: Float, height: Float, p: Paint): Float =
        top + height / 2f - (p.descent() + p.ascent()) / 2f

    // ---------- Génériques ----------

    private data class Line(val text: String, val sizePx: Float, val bold: Boolean, val color: Int)

    fun renderTitleCard(w: Int, h: Int, card: TitleCard, logo: Bitmap?): Bitmap {
        val s = h / 1080f
        return renderCard(
            w, h, logo, listOf(
                Line(card.title, 88f * s, true, Color.WHITE),
                Line(card.subtitle, 52f * s, false, 0xFFDCE6F2.toInt()),
                Line(card.author, 40f * s, false, 0xFFB8C7DA.toInt()),
                Line(card.date, 34f * s, false, 0xFF9FB2CA.toInt()),
            )
        )
    }

    fun renderEndCard(w: Int, h: Int, card: EndCard, logo: Bitmap?): Bitmap {
        val s = h / 1080f
        return renderCard(
            w, h, logo, listOf(
                Line(card.mainText, 76f * s, true, Color.WHITE),
                Line(card.secondaryText, 46f * s, false, 0xFFDCE6F2.toInt()),
                Line(card.credit, 34f * s, false, 0xFF9FB2CA.toInt()),
            )
        )
    }

    private fun renderCard(w: Int, h: Int, logo: Bitmap?, rawLines: List<Line>): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = h / 1080f
        canvas.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0B1730.toInt(), 0xFF1F3A5F.toInt(), Shader.TileMode.CLAMP)
        })

        val lines = rawLines.filter { it.text.isNotBlank() }
        val textW = (w * 0.84f).toInt()
        val layouts = lines.map { l ->
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = l.color; textSize = l.sizePx; typeface = if (l.bold) BOLD else REGULAR
            }
            StaticLayout.Builder.obtain(l.text, 0, l.text.length, p, textW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        }
        val gap = 24f * s
        val textBlockH = layouts.sumOf { it.height } + gap * max(0, layouts.size - 1)

        // Logo : en haut si du texte suit, centré sinon.
        var y: Float
        if (logo != null) {
            val maxLogoH = h * (if (layouts.isEmpty()) 0.45f else 0.28f)
            val maxLogoW = w * 0.4f
            val k = min(maxLogoW / logo.width, maxLogoH / logo.height)
            val lw = logo.width * k
            val lh = logo.height * k
            val totalH = lh + (if (layouts.isEmpty()) 0f else 60f * s + textBlockH)
            val top = max(h * 0.08f, (h - totalH) / 2f)
            canvas.drawBitmap(logo, null, RectF((w - lw) / 2f, top, (w + lw) / 2f, top + lh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            y = top + lh + 60f * s
        } else {
            y = (h - textBlockH) / 2f
        }

        layouts.forEachIndexed { i, layout ->
            canvas.save()
            canvas.translate((w - textW) / 2f, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height
            if (i == 0 && layouts.size > 1) {   // petit filet rouge sous le texte principal
                val p = Paint().apply { color = ACCENT }
                canvas.drawRect(w / 2f - 60f * s, y + gap / 2f - 3f * s, w / 2f + 60f * s, y + gap / 2f + 3f * s, p)
            }
            y += gap
        }
        return bmp
    }
}
