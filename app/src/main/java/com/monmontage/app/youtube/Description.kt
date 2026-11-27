package com.monmontage.app.youtube

import com.monmontage.app.model.Project
import com.monmontage.app.render.TimelineItem
import com.monmontage.app.render.TimelinePlanner

/**
 * Description YouTube pré-remplie à partir du projet : présentation (générique),
 * chapitres (légendes et intertitres) et crédits (générique de fin).
 */
object Description {

    /**
     * Chapitres YouTube : le premier doit être à 0:00, il en faut au moins 3, espacés d'au moins 10 s.
     * Sinon on n'en met pas (YouTube les ignorerait).
     */
    fun chapters(project: Project): List<Pair<Long, String>> {
        val timeline = TimelinePlanner.plan(project)
        val marks = mutableListOf<Pair<Long, String>>()
        marks += 0L to (project.titleCard.title.takeIf { project.titleCard.enabled && it.isNotBlank() } ?: "Introduction")
        val seen = HashSet<String>()
        for (item in timeline.items) when (item) {
            is TimelineItem.CartonItem -> if (!item.clip.cartonQuote && item.clip.cartonText.isNotBlank())
                marks += item.startMs to item.clip.cartonText.trim()
            is TimelineItem.VideoItem -> item.captions.forEach { c ->
                val label = listOf(c.name, c.role).filter { it.isNotBlank() }.joinToString(" — ")
                if (label.isNotBlank() && seen.add(label)) marks += (item.startMs + c.startMs) to label
            }
            is TimelineItem.PhotoItem -> item.captions.forEach { c ->
                val label = listOf(c.name, c.role).filter { it.isNotBlank() }.joinToString(" — ")
                if (label.isNotBlank() && seen.add(label)) marks += (item.startMs + c.startMs) to label
            }
            else -> {}
        }
        val sorted = marks.sortedBy { it.first }
        val kept = mutableListOf<Pair<Long, String>>()
        for (m in sorted) if (kept.isEmpty() || m.first - kept.last().first >= 10_000) kept += m
        if (timeline.totalMs - kept.last().first < 10_000 && kept.size > 1) kept.removeAt(kept.lastIndex)
        return if (kept.size >= 3) kept else emptyList()
    }

    fun build(project: Project): String = buildString {
        val t = project.titleCard
        if (t.subtitle.isNotBlank()) appendLine(t.subtitle.trim()).appendLine()
        val by = listOfNotNull(
            t.author.takeIf { it.isNotBlank() }?.let { "Un reportage de ${it.trim()}" },
            listOf(t.place, t.date).filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() },
        ).joinToString(" — ")
        if (by.isNotBlank()) appendLine("$by.").appendLine()
        val ch = chapters(project)
        if (ch.isNotEmpty()) {
            appendLine("Chapitres :")
            ch.forEach { (ms, label) -> appendLine("${timestamp(ms)} $label") }
            appendLine()
        }
        val credits = project.endCard.creditPairs
        if (credits.isNotEmpty()) {
            appendLine("Crédits :")
            credits.forEach { (role, name) -> appendLine(if (role.isEmpty()) name else "$role : $name") }
        }
    }.trim()

    fun timestamp(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
