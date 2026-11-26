package com.monmontage.app.data

import android.content.Context
import android.net.Uri
import com.monmontage.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Résumé affiché dans « Mes projets ». */
data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val durationMs: Long,
    val clipCount: Int,
    val firstClipUri: Uri?,
    val firstClipIsVideo: Boolean,
    val lastExport: ExportInfo?,
    val youtube: YouTubeInfo?,
    val missingMedia: Int,
)

/**
 * Enregistrement des projets en JSON dans le stockage privé de l'application
 * (un fichier par projet) et des génériques communs (branding.json).
 * Écriture atomique : fichier temporaire puis renommage, pour ne jamais corrompre un projet.
 */
class ProjectRepository(private val context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }
    private val brandingFile = File(context.filesDir, "branding.json")

    @Synchronized
    fun save(project: Project) = writeAtomic(File(dir, "${project.id}.json"), toJson(project).toString())

    @Synchronized
    fun load(id: String): Project? = runCatching { fromJson(JSONObject(File(dir, "$id.json").readText())) }.getOrNull()

    @Synchronized
    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    @Synchronized
    fun loadAll(): List<Project> = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { f -> runCatching { fromJson(JSONObject(f.readText())) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    @Synchronized
    fun saveBranding(b: Branding) = writeAtomic(brandingFile, JSONObject().apply {
        put("title", JSONObject().apply {
            put("enabled", b.titleCard.enabled); put("title", b.titleCard.title); put("subtitle", b.titleCard.subtitle)
            put("author", b.titleCard.author); put("date", b.titleCard.date); put("durationMs", b.titleCard.durationMs)
        })
        put("end", JSONObject().apply {
            put("enabled", b.endCard.enabled); put("mainText", b.endCard.mainText)
            put("secondaryText", b.endCard.secondaryText); put("credit", b.endCard.credit); put("durationMs", b.endCard.durationMs)
        })
        b.logoUri?.let { put("logo", it.toString()) }
    }.toString())

    @Synchronized
    fun loadBranding(): Branding = runCatching {
        val o = JSONObject(brandingFile.readText())
        val t = o.getJSONObject("title")
        val e = o.getJSONObject("end")
        Branding(
            titleCard = TitleCard(
                t.optBoolean("enabled", true), t.optString("title"), t.optString("subtitle"),
                t.optString("author"), t.optString("date"), t.optLong("durationMs", 4000),
            ),
            endCard = EndCard(
                e.optBoolean("enabled", true), e.optString("mainText"), e.optString("secondaryText"),
                e.optString("credit"), e.optLong("durationMs", 4000),
            ),
            logoUri = o.optString("logo").takeIf { it.isNotEmpty() }?.let(Uri::parse),
        )
    }.getOrDefault(Branding())

    /** Nombre de médias du projet devenus illisibles (supprimés de la galerie, accès perdu…). */
    fun countMissing(project: Project): Int = project.clips.count { !isReadable(it.uri) }

    fun isReadable(uri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) { target.writeText(text); tmp.delete() }
    }

    // ---------- JSON ----------

    private fun toJson(p: Project) = JSONObject().apply {
        put("v", 1); put("id", p.id); put("name", p.name); put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
        put("clips", JSONArray().apply { p.clips.forEach { put(clipToJson(it)) } })
        put("music", JSONObject().apply {
            put("track", p.music.track.id); p.music.customUri?.let { put("customUri", it.toString()) }
            put("customName", p.music.customName); put("volume", p.music.volume.toDouble())
            put("mute", p.music.muteDuringInterviews)
        })
        put("transition", JSONObject().apply { put("style", p.transition.style.name); put("durationMs", p.transition.durationMs) })
        p.lastExport?.let { x ->
            put("export", JSONObject().apply {
                put("uri", x.uri.toString()); put("fileName", x.fileName); put("at", x.exportedAt); put("durationMs", x.durationMs)
            })
        }
        p.youtube?.let { y -> put("youtube", JSONObject().apply { put("videoId", y.videoId); put("privacy", y.privacy); put("at", y.publishedAt) }) }
    }

    private fun clipToJson(c: Clip) = JSONObject().apply {
        put("id", c.id); put("uri", c.uri.toString()); put("mime", c.mimeType); put("type", c.type.name)
        put("name", c.displayName); put("sourceMs", c.sourceDurationMs); put("photoMs", c.photoDurationMs)
        put("segments", JSONArray().apply { c.segments.forEach { put(JSONArray().put(it.startMs).put(it.endMs)) } })
        put("captions", JSONArray().apply {
            c.captions.forEach { k ->
                put(JSONObject().apply { put("id", k.id); put("name", k.name); put("role", k.role); put("start", k.startMs); put("dur", k.durationMs) })
            }
        })
    }

    private fun fromJson(o: JSONObject): Project {
        val clips = o.optJSONArray("clips") ?: JSONArray()
        val m = o.optJSONObject("music") ?: JSONObject()
        val t = o.optJSONObject("transition") ?: JSONObject()
        return Project(
            id = o.getString("id"),
            name = o.optString("name", "Projet"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            clips = (0 until clips.length()).map { clipFromJson(clips.getJSONObject(it)) },
            music = MusicSettings(
                track = MusicTrack.fromId(m.optString("track")),
                customUri = m.optString("customUri").takeIf { it.isNotEmpty() }?.let(Uri::parse),
                customName = m.optString("customName"),
                volume = m.optDouble("volume", 0.7).toFloat(),
                muteDuringInterviews = m.optBoolean("mute", true),
            ),
            transition = TransitionSettings(
                style = runCatching { TransitionStyle.valueOf(t.optString("style")) }.getOrDefault(TransitionStyle.FADE_BLACK),
                durationMs = t.optLong("durationMs", 600),
            ),
            lastExport = o.optJSONObject("export")?.let { x ->
                ExportInfo(Uri.parse(x.getString("uri")), x.optString("fileName"), x.optLong("at"), x.optLong("durationMs"))
            },
            youtube = o.optJSONObject("youtube")?.let { y -> YouTubeInfo(y.getString("videoId"), y.optString("privacy"), y.optLong("at")) },
        )
    }

    private fun clipFromJson(o: JSONObject): Clip {
        val segs = o.optJSONArray("segments") ?: JSONArray()
        val caps = o.optJSONArray("captions") ?: JSONArray()
        return Clip(
            id = o.getLong("id"),
            uri = Uri.parse(o.getString("uri")),
            mimeType = o.optString("mime"),
            type = runCatching { ClipType.valueOf(o.getString("type")) }.getOrDefault(ClipType.PHOTO),
            displayName = o.optString("name"),
            sourceDurationMs = o.optLong("sourceMs"),
            photoDurationMs = o.optLong("photoMs", Clip.DEFAULT_PHOTO_MS),
            segments = (0 until segs.length()).map { i -> segs.getJSONArray(i).let { Segment(it.getLong(0), it.getLong(1)) } },
            captions = (0 until caps.length()).map { i ->
                caps.getJSONObject(i).let { Caption(it.getLong("id"), it.optString("name"), it.optString("role"), it.optLong("start"), it.optLong("dur")) }
            },
        )
    }
}
