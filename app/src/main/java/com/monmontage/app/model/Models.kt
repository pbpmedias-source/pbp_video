package com.monmontage.app.model

import android.net.Uri
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Type d'un élément de la timeline. */
enum class ClipType(val label: String) {
    PHOTO("Photo"),
    ILLUSTRATION("Illustration"),
    INTERVIEW("Interview"),
}

/** Portion conservée d'une vidéo source, en millisecondes. */
data class Segment(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Légende « bandeau JT » attachée à un clip. Les temps sont exprimés dans la durée
 * MONTÉE du clip (après découpe), donc la légende suit le clip si on le déplace.
 */
data class Caption(
    val id: Long = Ids.next(),
    val name: String,
    val role: String,
    val startMs: Long,
    val durationMs: Long,
)

data class Clip(
    val id: Long = Ids.next(),
    val uri: Uri,
    val mimeType: String,
    val type: ClipType,
    val displayName: String,
    val sourceDurationMs: Long = 0,
    val segments: List<Segment> = emptyList(),
    val photoDurationMs: Long = DEFAULT_PHOTO_MS,
    val captions: List<Caption> = emptyList(),
) {
    val isVideo: Boolean get() = type != ClipType.PHOTO
    val editedDurationMs: Long
        get() = if (isVideo) segments.sumOf { it.durationMs } else photoDurationMs

    companion object {
        const val DEFAULT_PHOTO_MS = 4_000L
        /** Seuil utilisé pour proposer le type par défaut d'une vidéo importée. */
        const val INTERVIEW_THRESHOLD_MS = 30_000L
    }
}

data class TitleCard(
    val enabled: Boolean = true,
    val title: String = "Titre de la série",
    val subtitle: String = "",
    val author: String = "",
    /** Facultative : masquée si vide (le générique est commun à tous les projets). */
    val date: String = "",
    val durationMs: Long = 4_000,
)

data class EndCard(
    val enabled: Boolean = true,
    val mainText: String = "Merci d'avoir regardé",
    val secondaryText: String = "",
    val credit: String = "",
    val durationMs: Long = 4_000,
)

/** Génériques et logo : communs à TOUS les projets (enregistrés à part). */
data class Branding(
    val titleCard: TitleCard = TitleCard(),
    val endCard: EndCard = EndCard(),
    val logoUri: Uri? = null,
)

/** Musiques embarquées, composées par l'application (libres de droits). */
enum class MusicTrack(val id: String, val label: String, val description: String) {
    DOUCE("douce", "Douce", "Nappes et arpège, calme · 80 BPM"),
    ENERGIQUE("energique", "Énergique", "Rythmée, basse pulsée · 120 BPM"),
    CINEMATIQUE("cinematique", "Cinématique", "Cordes amples, émotion · 64 BPM"),
    LOFI("lofi", "Lo-fi", "Détendue, accords jazz · 72 BPM");

    companion object {
        fun fromId(id: String?): MusicTrack = entries.firstOrNull { it.id == id } ?: DOUCE
    }
}

data class MusicSettings(
    val track: MusicTrack = MusicTrack.DOUCE,
    /** Musique importée : prioritaire sur [track] quand elle est définie. */
    val customUri: Uri? = null,
    val customName: String = "",
    val volume: Float = 0.7f,
    /** true : coupée pendant les interviews ; false : atténuée à 12 %. */
    val muteDuringInterviews: Boolean = true,
) {
    val label: String get() = if (customUri != null) customName.ifBlank { "Musique importée" } else track.label
}

enum class TransitionStyle(val label: String, val description: String) {
    NONE("Aucune", "Coupe franche, sans effet"),
    FADE_BLACK("Fondu au noir", "L'image s'éteint puis la suivante apparaît"),
    FLASH_WHITE("Flash blanc", "Bref éclair lumineux, dynamique"),
    ZOOM("Zoom doux", "L'image suivante entre en léger zoom"),
}

data class TransitionSettings(
    val style: TransitionStyle = TransitionStyle.FADE_BLACK,
    val durationMs: Long = 600,
)

/** Dernier export du projet (vidéo enregistrée dans la galerie). */
data class ExportInfo(val uri: Uri, val fileName: String, val exportedAt: Long, val durationMs: Long)

/** Dernière publication sur YouTube. */
data class YouTubeInfo(val videoId: String, val privacy: String, val publishedAt: Long)

data class Project(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Nouveau projet",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val clips: List<Clip> = emptyList(),
    val music: MusicSettings = MusicSettings(),
    val transition: TransitionSettings = TransitionSettings(),
    val lastExport: ExportInfo? = null,
    val youtube: YouTubeInfo? = null,
)

object Ids {
    // Amorcé sur l'horloge : jamais de collision avec les identifiants des projets rechargés.
    private val counter = AtomicLong(System.currentTimeMillis() * 1000)
    fun next(): Long = counter.getAndIncrement()
}

fun today(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))

/** 83 456 ms -> "1:23.4" */
fun formatMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    return "%d:%02d.%d".format(safe / 60_000, (safe / 1000) % 60, (safe / 100) % 10)
}

/** Opérations de découpe : une vidéo = liste ordonnée de segments conservés. */
object SegmentOps {
    const val MIN_MS = 300L

    fun indexAt(segments: List<Segment>, posMs: Long): Int =
        segments.indexOfFirst { posMs in it.startMs..it.endMs }

    /** Scinde le segment qui contient la position en deux segments. */
    fun split(segments: List<Segment>, posMs: Long): List<Segment> = segments.flatMap {
        if (posMs > it.startMs + MIN_MS && posMs < it.endMs - MIN_MS)
            listOf(Segment(it.startMs, posMs), Segment(posMs, it.endMs))
        else listOf(it)
    }

    /** Déplace le début d'un segment (permet aussi de récupérer une portion supprimée). */
    fun setStart(segments: List<Segment>, index: Int, posMs: Long): List<Segment> {
        val s = segments.getOrNull(index) ?: return segments
        val min = if (index > 0) segments[index - 1].endMs else 0L
        if (posMs < min || posMs > s.endMs - MIN_MS) return segments
        return segments.toMutableList().also { it[index] = s.copy(startMs = posMs) }
    }

    fun setEnd(segments: List<Segment>, index: Int, posMs: Long, sourceDurationMs: Long): List<Segment> {
        val s = segments.getOrNull(index) ?: return segments
        val max = if (index < segments.lastIndex) segments[index + 1].startMs else sourceDurationMs
        if (posMs > max || posMs < s.startMs + MIN_MS) return segments
        return segments.toMutableList().also { it[index] = s.copy(endMs = posMs) }
    }

    fun remove(segments: List<Segment>, index: Int): List<Segment> =
        segments.filterIndexed { i, _ -> i != index }
}
