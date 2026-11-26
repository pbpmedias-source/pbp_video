package com.monmontage.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import com.monmontage.app.model.Caption
import com.monmontage.app.model.Clip
import com.monmontage.app.model.ClipType
import com.monmontage.app.model.EndCard
import com.monmontage.app.model.MusicSettings
import com.monmontage.app.model.Project
import com.monmontage.app.model.Segment
import com.monmontage.app.model.TitleCard
import com.monmontage.app.render.CompositionFactory
import com.monmontage.app.render.DefaultMusicGenerator
import com.monmontage.app.render.GallerySaver
import com.monmontage.app.render.MediaUtils
import com.monmontage.app.render.OutputSpec
import com.monmontage.app.render.ProjectPreparer
import com.monmontage.app.render.VideoRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface RenderState {
    data object Idle : RenderState
    /**
     * stage : 0 = préparation, 1 = montage/encodage, 2 = enregistrement.
     * encodeStartMs sert à estimer le temps restant ; totalMs = durée du montage.
     */
    data class Working(
        val step: String,
        val progress: Int?,
        val stage: Int = 0,
        val encodeStartMs: Long = 0,
        val totalMs: Long = 0,
    ) : RenderState
    data class PreviewReady(val file: File, val version: Long) : RenderState
    data class Exported(val uri: Uri, val fileName: String) : RenderState
    data class Failed(val message: String) : RenderState
}

@UnstableApi
class MontageViewModel(app: Application) : AndroidViewModel(app) {

    private val _project = MutableStateFlow(Project())
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _previewState = MutableStateFlow<RenderState>(RenderState.Idle)
    val previewState: StateFlow<RenderState> = _previewState.asStateFlow()

    private val _exportState = MutableStateFlow<RenderState>(RenderState.Idle)
    val exportState: StateFlow<RenderState> = _exportState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val preparer = ProjectPreparer(app)
    private val renderer = VideoRenderer(app)
    private var renderJob: Job? = null
    private var renderIsPreview = false
    private var lastPreviewProject: Project? = null

    init {
        // Génère la musique par défaut dès le lancement (une seule fois, ~1 s en arrière-plan).
        viewModelScope.launch(Dispatchers.Default) { runCatching { DefaultMusicGenerator.ensure(app) } }
    }

    private val ctx get() = getApplication<Application>()

    // ---------- Médias ----------

    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            val clips = withContext(Dispatchers.IO) { uris.mapNotNull { runCatching { buildClip(it) }.getOrNull() } }
            _project.update { it.copy(clips = it.clips + clips) }
            val ignored = uris.size - clips.size
            if (ignored > 0) _messages.tryEmit("$ignored fichier(s) illisible(s) ignoré(s).")
            _busy.value = false
        }
    }

    private fun buildClip(uri: Uri): Clip? {
        val mime = MediaUtils.mimeType(ctx, uri)
        val name = MediaUtils.displayName(ctx, uri)
        return when {
            mime.startsWith("video/") -> {
                val d = MediaUtils.durationMs(ctx, uri)
                if (d <= 0) null else Clip(
                    uri = uri, mimeType = mime, displayName = name,
                    // Type proposé par défaut (modifiable) : courte = illustration, longue = interview.
                    type = if (d <= Clip.INTERVIEW_THRESHOLD_MS) ClipType.ILLUSTRATION else ClipType.INTERVIEW,
                    sourceDurationMs = d, segments = listOf(Segment(0, d)),
                )
            }
            mime.startsWith("image/") -> Clip(uri = uri, mimeType = mime, type = ClipType.PHOTO, displayName = name)
            else -> null
        }
    }

    fun updateClip(clip: Clip) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == clip.id) clip else it })
    }

    fun setClipType(id: Long, type: ClipType) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == id && it.isVideo && type != ClipType.PHOTO) it.copy(type = type) else it })
    }

    fun setSegments(id: Long, segments: List<Segment>) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == id) it.copy(segments = segments) else it })
    }

    fun setCaptions(id: Long, captions: List<Caption>) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == id) it.copy(captions = captions) else it })
    }

    fun removeClip(id: Long) = _project.update { p -> p.copy(clips = p.clips.filterNot { it.id == id }) }

    fun moveClip(id: Long, delta: Int) = _project.update { p ->
        val list = p.clips.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) p else p.copy(clips = list.apply { add(j, removeAt(i)) })
    }

    // ---------- Génériques ----------

    fun setTitleCard(card: TitleCard) = _project.update { it.copy(titleCard = card) }
    fun setEndCard(card: EndCard) = _project.update { it.copy(endCard = card) }
    fun setLogo(uri: Uri?) = _project.update { it.copy(logoUri = uri) }

    // ---------- Musique ----------

    fun setMusic(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val (duration, name) = withContext(Dispatchers.IO) {
                MediaUtils.durationMs(ctx, uri) to MediaUtils.displayName(ctx, uri)
            }
            if (duration < 1000) {
                _messages.tryEmit("Ce fichier audio n'est pas lisible.")
            } else {
                _project.update { it.copy(music = it.music.copy(uri = uri, displayName = name)) }
            }
        }
    }

    fun resetMusic() = _project.update {
        it.copy(music = it.music.copy(uri = null, displayName = MusicSettings.DEFAULT_NAME))
    }

    fun setMusicVolume(v: Float) = _project.update { it.copy(music = it.music.copy(volume = v)) }
    fun setMuteDuringInterviews(mute: Boolean) =
        _project.update { it.copy(music = it.music.copy(muteDuringInterviews = mute)) }

    // ---------- Aperçu et export ----------

    fun startPreview() {
        val current = _project.value
        if (_previewState.value is RenderState.PreviewReady && current == lastPreviewProject) return
        launchRender(OutputSpec.PREVIEW, _previewState, isPreview = true) { file ->
            lastPreviewProject = current
            RenderState.PreviewReady(file, System.currentTimeMillis())
        }
    }

    fun startExport() {
        launchRender(OutputSpec.EXPORT, _exportState, isPreview = false) { file ->
            val name = "Montage_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".mp4"
            val uri = withContext(Dispatchers.IO) {
                GallerySaver.saveVideo(ctx, file, name).also { file.delete() }
            }
            RenderState.Exported(uri, name)
        }
    }

    fun cancelRender() {
        renderJob?.cancel()
        renderJob = null
    }

    fun cancelPreviewIfRunning() {
        if (renderIsPreview && renderJob?.isActive == true) cancelRender()
    }

    private fun launchRender(
        spec: OutputSpec,
        state: MutableStateFlow<RenderState>,
        isPreview: Boolean,
        finish: suspend (File) -> RenderState,
    ) {
        renderJob?.cancel()
        renderIsPreview = isPreview
        val snapshot = _project.value
        renderJob = viewModelScope.launch {
            state.value = RenderState.Working("Préparation…", null)
            try {
                val prepared = preparer.prepare(snapshot, spec) { step -> state.value = RenderState.Working(step, null) }
                val composition = CompositionFactory.build(prepared, spec)
                val out = File(ctx.cacheDir, "render/" + if (isPreview) "preview.mp4" else "export.mp4")
                val label = if (isPreview) "Rendu de l'aperçu (480p)" else "Encodage de la vidéo"
                val started = System.currentTimeMillis()
                val total = prepared.timeline.totalMs
                state.value = RenderState.Working(label, 0, 1, started, total)
                renderer.render(composition, out, spec.videoBitrate) { p ->
                    state.value = RenderState.Working(label, p, 1, started, total)
                }
                if (!isPreview) state.value = RenderState.Working("Enregistrement dans la galerie", 100, 2, started, total)
                state.value = finish(out)
            } catch (e: CancellationException) {
                state.value = RenderState.Idle
                throw e
            } catch (e: Throwable) {
                Log.e("MonMontage", "Rendu échoué", e)
                state.value = RenderState.Failed(describe(e))
            }
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is OutOfMemoryError -> "Mémoire insuffisante. Fermez les autres applications puis réessayez."
        is ExportException -> "Échec de l'encodage (code ${e.errorCode}). ${e.message ?: ""}".trim()
        else -> e.message ?: e.javaClass.simpleName
    }

    fun resetExportState() {
        if (_exportState.value !is RenderState.Working) _exportState.value = RenderState.Idle
    }
}
