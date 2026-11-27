package com.monmontage.app.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.monmontage.app.R
import android.net.Uri
import android.os.StatFs
import com.monmontage.app.model.BrandKit
import com.monmontage.app.model.ClipType
import com.monmontage.app.model.Branding
import com.monmontage.app.model.MusicSettings
import com.monmontage.app.model.Project
import com.monmontage.app.model.TransitionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Paramètres de sortie. Hypothèse : 16:9 en 1080p (format standard YouTube). */
data class OutputSpec(val width: Int, val height: Int, val videoBitrate: Int) {
    companion object {
        val EXPORT = OutputSpec(1920, 1080, 12_000_000)   // 12 Mb/s : recommandation YouTube 1080p30 SDR (8 Mb/s) + marge
        val PREVIEW = OutputSpec(854, 480, 2_500_000)
    }
}

/** Tout ce qu'il faut pour construire la composition Media3, préparé hors du thread principal. */
data class PreparedProject(
    val timeline: Timeline,
    val titleCardFile: File?,
    val endCardFile: File?,
    val photoFiles: Map<Long, File>,
    val musicUri: Uri,
    val musicDurationMs: Long,
    val music: MusicSettings,
    val transition: TransitionSettings,
    /** Calque du logo en miniature (null = désactivé). */
    val watermarkFile: File?,
    /** Couleurs de la charte du projet (légendes). */
    val colors: KitColors,
    /** Image de chaque carton (intertitre ou citation). */
    val cartonFiles: Map<Long, File> = emptyMap(),
)

class ProjectPreparer(private val context: Context) {

    suspend fun prepare(project: Project, branding: Branding, spec: OutputSpec, onStep: (String) -> Unit): PreparedProject =
        withContext(Dispatchers.IO) {
            val timeline = TimelinePlanner.plan(project, branding)
            require(timeline.items.any { it is TimelineItem.PhotoItem || it is TimelineItem.VideoItem }) {
                "Ajoutez au moins une photo ou une vidéo (ou vérifiez qu'il reste un segment)."
            }
            val dir = File(context.cacheDir, "render").apply { mkdirs() }

            // Médias supprimés de la galerie depuis la création du projet : on le dit clairement.
            val missing = project.clips.filter { it.hasMedia }.filter { clip ->
                runCatching { context.contentResolver.openFileDescriptor(clip.uri, "r")?.use { true } ?: false }.getOrDefault(false).not()
            }
            require(missing.isEmpty()) {
                "Média introuvable : " + missing.joinToString { it.displayName } +
                    ". Retirez-le du montage ou remplacez-le."
            }

            onStep("Préparation des génériques…")
            val kit = branding.kitFor(project)
            val colors = KitColors(kit.accent, kit.primary, kit.dark)
            val logo = loadLogo(kit, spec.height / 2)
            val background = project.cardBackgroundUri?.let { uri ->
                runCatching { MediaUtils.decodeBitmap(context, uri, spec.width * 2, spec.height * 2) }.getOrNull()
            }
            val titleFile = if (project.titleCard.enabled)
                writePng(Painter.renderTitleCard(context, spec.width, spec.height, project.cardStyle, project.titleCard, logo, background, colors), File(dir, "title_${spec.height}.png"))
            else null
            val endFile = if (project.endCard.enabled)
                writePng(Painter.renderEndCard(context, spec.width, spec.height, project.cardStyle, project.endCard, logo, background, colors), File(dir, "end_${spec.height}.png"))
            else null
            background?.recycle()
            val cartonFiles = project.clips.filter { it.isCarton }.associate { c ->
                c.id to writePng(
                    Painter.renderCarton(context, spec.width, spec.height, project.cardStyle, c.cartonText, c.cartonAuthor, c.cartonQuote, colors),
                    File(dir, "carton_${c.id}_${spec.height}.png"),
                )
            }
            val watermarkFile = if (project.clips.any { it.showLogo } && logo != null)
                writePng(Painter.renderWatermark(spec.width, spec.height, logo), File(dir, "watermark_${spec.height}.png"))
            else null
            logo?.recycle()

            // Photos : décodées une par une, redimensionnées à la taille de sortie, orientation EXIF appliquée.
            onStep("Préparation des photos…")
            val photoFiles = project.clips.filter { it.type == ClipType.PHOTO }.associate { clip ->
                val f = File(dir, "photo_${clip.id}_${spec.height}.jpg")
                if (!f.exists()) {
                    val bmp = MediaUtils.decodeBitmap(context, clip.uri, spec.width, spec.height)
                    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    bmp.recycle()
                }
                clip.id to f
            }

            onStep("Préparation de la musique…")
            val custom = project.music.customUri
            val (musicUri, musicMs) = if (custom == null) {
                Uri.fromFile(MusicLibrary.ensure(context, project.music.track)) to MusicLibrary.durationMs(project.music.track)
            } else {
                val d = MediaUtils.durationMs(context, custom)
                require(d >= 1000) { "La musique importée est illisible. Choisissez-en une autre dans la liste." }
                custom to d
            }

            ensureFreeSpace(dir, timeline.totalMs, spec)
            PreparedProject(timeline, titleFile, endFile, photoFiles, musicUri, musicMs, project.music, project.transition, watermarkFile, colors, cartonFiles)
        }

    /** Logo de la charte, sinon logo PBP Médias fourni avec l'application. */
    fun loadLogo(kit: BrandKit, maxSide: Int): Bitmap? {
        kit.logoUri?.let { uri ->
            runCatching { return MediaUtils.decodeBitmap(context, uri, maxSide, maxSide) }
        }
        return runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.logo_pbp_medias, BitmapFactory.Options().apply { inScaled = false })
        }.getOrNull()
    }

    private fun writePng(bitmap: Bitmap, file: File): File {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /** Le MP4 est écrit en cache puis copié dans la galerie : il faut ~2× sa taille. */
    private fun ensureFreeSpace(dir: File, totalMs: Long, spec: OutputSpec) {
        val estimated = (totalMs / 1000.0 * (spec.videoBitrate + 192_000) / 8.0).toLong()
        val needed = estimated * 2 + 50L * 1024 * 1024
        val available = StatFs(dir.path).availableBytes
        check(available >= needed) {
            "Espace de stockage insuffisant : environ ${needed / 1_000_000} Mo libres sont nécessaires."
        }
    }

    fun clearCache() {
        File(context.cacheDir, "render").listFiles()?.forEach { it.delete() }
    }
}
