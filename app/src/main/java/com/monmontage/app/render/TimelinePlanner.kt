package com.monmontage.app.render

import com.monmontage.app.model.Branding
import com.monmontage.app.model.Clip
import com.monmontage.app.model.ClipType
import com.monmontage.app.model.Project
import com.monmontage.app.model.Segment
import kotlin.math.max
import kotlin.math.min

/** Légende ramenée au temps local d'un élément de la timeline (ms). */
data class LocalCaption(val name: String, val role: String, val startMs: Long, val endMs: Long)

/** Élément à plat de la timeline finale (un segment vidéo = un élément). */
sealed interface TimelineItem {
    val startMs: Long
    val durationMs: Long
    val endMs: Long get() = startMs + durationMs

    data class TitleCardItem(override val startMs: Long, override val durationMs: Long) : TimelineItem
    data class EndCardItem(override val startMs: Long, override val durationMs: Long) : TimelineItem
    data class PhotoItem(
        val clip: Clip, override val startMs: Long, override val durationMs: Long,
        val captions: List<LocalCaption>,
    ) : TimelineItem
    data class VideoItem(
        val clip: Clip, val segment: Segment, override val startMs: Long, override val durationMs: Long,
        val captions: List<LocalCaption>,
    ) : TimelineItem
}

data class Timeline(
    val items: List<TimelineItem>,
    val totalMs: Long,
    /** Plages (début, fin) en ms où une interview est à l'écran. */
    val interviewRanges: List<Pair<Long, Long>>,
)

/** Transforme le projet en timeline à plat : source unique pour l'aperçu, l'export et la musique. */
object TimelinePlanner {
    private const val MIN_ITEM_MS = 100L

    fun plan(project: Project, branding: Branding): Timeline {
        val items = mutableListOf<TimelineItem>()
        var t = 0L

        if (branding.titleCard.enabled) {
            items += TimelineItem.TitleCardItem(t, branding.titleCard.durationMs)
            t += branding.titleCard.durationMs
        }
        for (clip in project.clips) {
            if (!clip.isVideo) {
                val d = clip.photoDurationMs
                items += TimelineItem.PhotoItem(clip, t, d, localCaptions(clip, 0, d))
                t += d
            } else {
                var editedOffset = 0L
                for (seg in clip.segments) {
                    val d = seg.durationMs
                    if (d >= MIN_ITEM_MS) {
                        items += TimelineItem.VideoItem(clip, seg, t, d, localCaptions(clip, editedOffset, d))
                        t += d
                    }
                    editedOffset += d
                }
            }
        }
        if (branding.endCard.enabled) {
            items += TimelineItem.EndCardItem(t, branding.endCard.durationMs)
            t += branding.endCard.durationMs
        }

        val ranges = mutableListOf<Pair<Long, Long>>()
        items.filterIsInstance<TimelineItem.VideoItem>()
            .filter { it.clip.type == ClipType.INTERVIEW }
            .forEach { item ->
                val last = ranges.lastOrNull()
                if (last != null && item.startMs - last.second <= 50) {
                    ranges[ranges.lastIndex] = last.first to item.endMs   // interviews consécutives fusionnées
                } else {
                    ranges += item.startMs to item.endMs
                }
            }
        return Timeline(items, t, ranges)
    }

    private fun localCaptions(clip: Clip, offsetMs: Long, lengthMs: Long): List<LocalCaption> =
        clip.captions.mapNotNull { c ->
            val s = max(c.startMs, offsetMs)
            val e = min(c.startMs + c.durationMs, offsetMs + lengthMs)
            if (e - s < 100) null else LocalCaption(c.name, c.role, s - offsetMs, e - offsetMs)
        }
}
