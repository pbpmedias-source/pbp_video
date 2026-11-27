package com.monmontage.app.ui

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ExportException
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.monmontage.app.data.ProjectRepository
import com.monmontage.app.data.ProjectSummary
import com.monmontage.app.model.*
import com.monmontage.app.render.*
import com.monmontage.app.youtube.UploadMetadata
import com.monmontage.app.youtube.UploadWorker
import com.monmontage.app.youtube.YouTubeAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface RenderState {
    data object Idle : RenderState
    /** stage : 0 = préparation, 1 = montage/encodage, 2 = enregistrement. */
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

sealed interface UploadState {
    data object Idle : UploadState
    data object WaitingNetwork : UploadState
    data class Running(val sent: Long, val total: Long) : UploadState
    data class Done(val videoId: String) : UploadState
    data class Failed(val message: String) : UploadState
}

@OptIn(FlowPreview::class)
@UnstableApi
class MontageViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val repo = ProjectRepository(app)
    private val preparer = ProjectPreparer(app)
    private val renderer = VideoRenderer(app)

    private val _projects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val projects: StateFlow<List<ProjectSummary>> = _projects.asStateFlow()

    private val _project = MutableStateFlow(Project())
    val project: StateFlow<Project> = _project.asStateFlow()
    private var editing = false

    private val _branding = MutableStateFlow(Branding())
    val branding: StateFlow<Branding> = _branding.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _previewState = MutableStateFlow<RenderState>(RenderState.Idle)
    val previewState: StateFlow<RenderState> = _previewState.asStateFlow()

    private val _exportState = MutableStateFlow<RenderState>(RenderState.Idle)
    val exportState: StateFlow<RenderState> = _exportState.asStateFlow()

    private val _youtubeEmail = MutableStateFlow(YouTubeAuth.connectedEmail(app))
    val youtubeEmail: StateFlow<String?> = _youtubeEmail.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var renderJob: Job? = null
    private var renderIsPreview = false
    private var lastPreviewKey: Pair<Project, Branding>? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _branding.value = repo.loadBranding()
            refreshProjects()
        }
        // Musique par défaut prête dès le lancement.
        viewModelScope.launch(Dispatchers.Default) { runCatching { MusicLibrary.ensure(app, MusicTrack.DOUCE) } }
        // Enregistrement automatique : 0,4 s après la dernière modification.
        viewModelScope.launch {
            _project.drop(1).debounce(400).collect { p -> if (editing) withContext(Dispatchers.IO) { repo.save(p.copy(updatedAt = System.currentTimeMillis())) } }
        }
        viewModelScope.launch {
            _branding.drop(1).debounce(400).collect { b -> withContext(Dispatchers.IO) { repo.saveBranding(b) } }
        }
    }

    // ---------- Projets ----------

    fun refreshProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            val b = _branding.value
            _projects.value = repo.loadAll().map { p ->
                val first = p.clips.firstOrNull { it.hasMedia }
                ProjectSummary(
                    id = p.id, name = p.name, createdAt = p.createdAt, updatedAt = p.updatedAt,
                    durationMs = if (p.clips.isEmpty()) 0 else TimelinePlanner.plan(p, b).totalMs,
                    clipCount = p.clips.size, firstClipUri = first?.uri, firstClipIsVideo = first?.isVideo == true,
                    lastExport = p.lastExport, youtube = p.youtube, missingMedia = repo.countMissing(p),
                )
            }
        }
    }

    fun newProject(): String {
        val count = _projects.value.size + 1
        val p = Project(name = "Projet $count", titleCard = TitleCard(title = "Projet $count"), kitId = _branding.value.defaultKitId)
        _project.value = p
        editing = true
        viewModelScope.launch(Dispatchers.IO) { repo.save(p) }
        resetRenderStates()
        return p.id
    }

    fun openProject(id: String, onOpened: () -> Unit) {
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) { repo.load(id) }
            if (p == null) { _messages.tryEmit("Projet introuvable."); refreshProjects(); return@launch }
            _project.value = p
            editing = true
            resetRenderStates()
            onOpened()
        }
    }

    /** Retour à la liste : enregistrement immédiat puis rafraîchissement. */
    fun closeProject() {
        val p = _project.value
        editing = false
        cancelRender()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.save(p.copy(updatedAt = System.currentTimeMillis())) }
            refreshProjects()
        }
    }

    fun renameCurrent(name: String) = _project.update { it.copy(name = name) }

    fun renameProject(id: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.load(id)?.let { repo.save(it.copy(name = name.trim().ifBlank { it.name }, updatedAt = System.currentTimeMillis())) }
            refreshProjects()
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.load(id)?.let { src ->
                val now = System.currentTimeMillis()
                repo.save(src.copy(
                    id = java.util.UUID.randomUUID().toString(), name = "${src.name} (copie)",
                    createdAt = now, updatedAt = now, lastExport = null, youtube = null,
                    clips = src.clips.map { c -> c.copy(id = Ids.next(), captions = c.captions.map { it.copy(id = Ids.next()) }) },
                ))
            }
            refreshProjects()
        }
    }

    /** Supprime le montage seulement : photos, vidéos et MP4 exportés restent dans la galerie. */
    fun deleteProject(id: String) {
        UploadWorker.cancel(ctx, id)
        viewModelScope.launch(Dispatchers.IO) {
            repo.delete(id)
            refreshProjects()
        }
    }

    private fun resetRenderStates() {
        _previewState.value = RenderState.Idle
        _exportState.value = RenderState.Idle
        lastPreviewKey = null
    }

    // ---------- Médias ----------

    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            val clips = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    keepAccess(uri)
                    runCatching { buildClip(uri) }.getOrNull()
                }
            }
            _project.update { it.copy(clips = it.clips + clips) }
            val ignored = uris.size - clips.size
            if (ignored > 0) _messages.tryEmit("$ignored fichier(s) illisible(s) ignoré(s).")
            _busy.value = false
        }
    }

    /** Garde l'accès au média après redémarrage du téléphone, pour rouvrir le projet plus tard. */
    private fun keepAccess(uri: Uri) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun buildClip(uri: Uri): Clip? {
        val mime = MediaUtils.mimeType(ctx, uri)
        val name = MediaUtils.displayName(ctx, uri)
        return when {
            mime.startsWith("video/") -> {
                val d = MediaUtils.durationMs(ctx, uri)
                if (d <= 0) null else Clip(
                    uri = uri, mimeType = mime, displayName = name,
                    type = if (d <= Clip.INTERVIEW_THRESHOLD_MS) ClipType.ILLUSTRATION else ClipType.INTERVIEW,
                    sourceDurationMs = d, segments = listOf(Segment(0, d)),
                )
            }
            mime.startsWith("image/") -> Clip(uri = uri, mimeType = mime, type = ClipType.PHOTO, displayName = name)
            else -> null
        }
    }

    fun updateClip(clip: Clip) = _project.update { p -> p.copy(clips = p.clips.map { if (it.id == clip.id) clip else it }) }

    fun setClipType(id: Long, type: ClipType) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == id && it.isVideo && type != ClipType.PHOTO) it.copy(type = type) else it })
    }

    fun setSegments(id: Long, segments: List<Segment>) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == id) it.copy(segments = segments) else it })
    }

    fun setCaptions(id: Long, captions: List<Caption>) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == id) it.copy(captions = captions) else it })
    }

    /** Ajoute un carton (intertitre par défaut) à la fin du montage et renvoie son identifiant. */
    fun addCarton(): Long {
        val c = Clip(
            uri = Uri.EMPTY, mimeType = "", type = ClipType.CARTON, displayName = "Carton",
            photoDurationMs = 3_000, cartonText = "Intertitre",
        )
        _project.update { it.copy(clips = it.clips + c) }
        return c.id
    }

    fun setCarton(id: Long, text: String, author: String, quote: Boolean) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == id) it.copy(cartonText = text, cartonAuthor = author, cartonQuote = quote) else it })
    }

    /** Description YouTube pré-remplie (présentation, chapitres, crédits). */
    fun youtubeDescription(): String = com.monmontage.app.youtube.Description.build(_project.value)

    fun removeClip(id: Long) = _project.update { p -> p.copy(clips = p.clips.filterNot { it.id == id }) }

    fun moveClip(id: Long, delta: Int) = _project.update { p ->
        val list = p.clips.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) p else p.copy(clips = list.apply { add(j, removeAt(i)) })
    }

    // ---------- Génériques (propres au projet) et logo (commun) ----------

    fun setTitleCard(card: TitleCard) = _project.update { it.copy(titleCard = card) }
    fun setEndCard(card: EndCard) = _project.update { it.copy(endCard = card) }
    fun setCardStyle(style: CardStyle) = _project.update { it.copy(cardStyle = style) }
    fun setClipLogo(id: Long, on: Boolean) = _project.update { p ->
        p.copy(clips = p.clips.map { if (it.id == id) it.copy(showLogo = on) else it })
    }
    fun setLogoOnAllClips(on: Boolean) = _project.update { p -> p.copy(clips = p.clips.map { it.copy(showLogo = on) }) }
    fun setCardBackground(uri: Uri?) {
        uri?.let(::keepAccess)
        _project.update { it.copy(cardBackgroundUri = uri) }
    }

    // ---------- Réglages généraux : chartes graphiques ----------

    /** Charte du projet ouvert. */
    fun currentKit(): BrandKit = _branding.value.kitFor(_project.value)

    fun setProjectKit(kitId: String) = _project.update { it.copy(kitId = kitId) }

    /** Crée une charte (copie de la charte par défaut) et renvoie son identifiant. */
    fun newKit(): String {
        val base = _branding.value.defaultKit
        val kit = base.copy(id = java.util.UUID.randomUUID().toString(), name = "Nouvelle charte")
        _branding.update { it.copy(kits = it.kits + kit) }
        return kit.id
    }

    fun saveKit(kit: BrandKit) {
        kit.logoUri?.let(::keepAccess)
        _branding.update { b -> b.copy(kits = b.kits.map { if (it.id == kit.id) kit else it }) }
    }

    fun duplicateKit(id: String) = _branding.update { b ->
        val src = b.kits.firstOrNull { it.id == id } ?: return@update b
        b.copy(kits = b.kits + src.copy(id = java.util.UUID.randomUUID().toString(), name = "${src.name} (copie)"))
    }

    /** La dernière charte ne peut pas être supprimée ; les projets qui l'utilisaient passent à la charte par défaut. */
    fun deleteKit(id: String) = _branding.update { b ->
        if (b.kits.size <= 1) return@update b
        val kits = b.kits.filterNot { it.id == id }
        b.copy(kits = kits, defaultKitId = if (b.defaultKitId == id) kits.first().id else b.defaultKitId)
    }

    fun setDefaultKit(id: String) = _branding.update { it.copy(defaultKitId = id) }

    /** Logo d'une charte (celle du projet ouvert par défaut), réduit pour l'affichage. */
    suspend fun logoBitmap(kit: BrandKit? = null): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) { preparer.loadLogo(kit ?: currentKit(), 240) }

    /** Couleurs dominantes du logo d'une charte. */
    suspend fun logoPalette(kit: BrandKit): List<Int> = withContext(Dispatchers.Default) {
        logoBitmap(kit)?.let { Palette.dominant(it) } ?: emptyList()
    }

    // ---------- Musique et transitions ----------

    fun setMusicTrack(track: MusicTrack) = _project.update { it.copy(music = it.music.copy(track = track, customUri = null, customName = "")) }

    fun setCustomMusic(uri: Uri) {
        viewModelScope.launch {
            keepAccess(uri)
            val (duration, name) = withContext(Dispatchers.IO) { MediaUtils.durationMs(ctx, uri) to MediaUtils.displayName(ctx, uri) }
            if (duration < 1000) _messages.tryEmit("Ce fichier audio n'est pas lisible.")
            else _project.update { it.copy(music = it.music.copy(customUri = uri, customName = name)) }
        }
    }

    fun setMusicVolume(v: Float) = _project.update { it.copy(music = it.music.copy(volume = v)) }
    fun setMuteDuringInterviews(mute: Boolean) = _project.update { it.copy(music = it.music.copy(muteDuringInterviews = mute)) }

    suspend fun musicFile(track: MusicTrack): Uri = withContext(Dispatchers.IO) { Uri.fromFile(MusicLibrary.ensure(ctx, track)) }

    fun setTransitionStyle(style: TransitionStyle) = _project.update { it.copy(transition = it.transition.copy(style = style)) }
    fun setTransitionDuration(ms: Long) = _project.update { it.copy(transition = it.transition.copy(durationMs = ms)) }

    // ---------- Aperçu et export ----------

    fun startPreview() {
        val key = _project.value to _branding.value
        if (_previewState.value is RenderState.PreviewReady && key == lastPreviewKey) return
        launchRender(OutputSpec.PREVIEW, _previewState, isPreview = true) { file ->
            lastPreviewKey = key
            RenderState.PreviewReady(file, System.currentTimeMillis())
        }
    }

    fun startExport() {
        launchRender(OutputSpec.EXPORT, _exportState, isPreview = false) { file ->
            val name = "Montage_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date()) + ".mp4"
            val uri = withContext(Dispatchers.IO) { GallerySaver.saveVideo(ctx, file, name).also { file.delete() } }
            val total = TimelinePlanner.plan(_project.value, _branding.value).totalMs
            _project.update { it.copy(lastExport = ExportInfo(uri, name, System.currentTimeMillis(), total)) }
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

    fun resetExportState() {
        if (_exportState.value !is RenderState.Working) _exportState.value = RenderState.Idle
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
        val brand = _branding.value
        renderJob = viewModelScope.launch {
            state.value = RenderState.Working("Préparation…", null)
            try {
                val prepared = preparer.prepare(snapshot, brand, spec) { step -> state.value = RenderState.Working(step, null) }
                val composition = CompositionFactory.build(prepared, spec)
                val out = File(ctx.cacheDir, "render/" + if (isPreview) "preview.mp4" else "export.mp4")
                val label = if (isPreview) "Rendu de l'aperçu (480p)" else "Encodage de la vidéo"
                val started = System.currentTimeMillis()
                val total = prepared.timeline.totalMs
                state.value = RenderState.Working(label, 0, 1, started, total)
                renderer.render(composition, out, spec.videoBitrate) { p -> state.value = RenderState.Working(label, p, 1, started, total) }
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
        is ExportException -> "Échec de l'encodage (${e.errorCodeName}). ${e.message ?: ""}".trim()
        else -> e.message ?: e.javaClass.simpleName
    }

    // ---------- YouTube ----------

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    /** Lance l'autorisation Google ; [launch] affiche l'écran de consentement si nécessaire. */
    fun connectYouTube(launch: (PendingIntent) -> Unit) {
        _authError.value = null
        viewModelScope.launch {
            try {
                val r = YouTubeAuth.authorize(ctx)
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) launch(pending) else onAuthorized(r.accessToken)
            } catch (e: Exception) {
                _authError.value = YouTubeAuth.describe(e)
            }
        }
    }

    fun onAuthResult(data: Intent?) {
        viewModelScope.launch {
            try {
                onAuthorized(YouTubeAuth.resultFromIntent(ctx, data).accessToken)
            } catch (e: Exception) {
                _authError.value = YouTubeAuth.describe(e)
            }
        }
    }

    private suspend fun onAuthorized(token: String?) {
        if (token == null) { _authError.value = "Autorisation refusée."; return }
        val email = YouTubeAuth.fetchEmail(token) ?: "Compte Google connecté"
        YouTubeAuth.saveEmail(ctx, email)
        _youtubeEmail.value = email
    }

    fun disconnectYouTube() {
        viewModelScope.launch {
            YouTubeAuth.disconnect(ctx)
            _youtubeEmail.value = null
        }
    }

    fun publishToYouTube(meta: UploadMetadata, wifiOnly: Boolean): Boolean {
        val export = _project.value.lastExport ?: run { _messages.tryEmit("Exportez d'abord la vidéo."); return false }
        UploadWorker.enqueue(ctx, _project.value.id, export.uri, meta, wifiOnly)
        return true
    }

    fun cancelUpload() = UploadWorker.cancel(ctx, _project.value.id)

    /** Suivi de l'envoi du projet ouvert. */
    fun uploadState(projectId: String): Flow<UploadState> =
        WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(UploadWorker.uniqueName(projectId)).map { infos ->
            val info = infos.lastOrNull() ?: return@map UploadState.Idle
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> UploadState.WaitingNetwork
                WorkInfo.State.RUNNING -> {
                    val total = info.progress.getLong(UploadWorker.KEY_TOTAL, 0)
                    UploadState.Running(info.progress.getLong(UploadWorker.KEY_SENT, 0), total)
                }
                WorkInfo.State.SUCCEEDED -> {
                    val id = info.outputData.getString(UploadWorker.KEY_VIDEO_ID).orEmpty()
                    // Le travail a mis à jour le fichier du projet : on recharge l'info dans le projet ouvert.
                    if (_project.value.id == projectId && _project.value.youtube?.videoId != id) {
                        _project.update { it.copy(youtube = YouTubeInfo(id, it.youtube?.privacy ?: "private", System.currentTimeMillis())) }
                    }
                    UploadState.Done(id)
                }
                WorkInfo.State.FAILED -> UploadState.Failed(info.outputData.getString(UploadWorker.KEY_ERROR) ?: "Envoi impossible.")
                WorkInfo.State.CANCELLED -> UploadState.Idle
            }
        }
}
