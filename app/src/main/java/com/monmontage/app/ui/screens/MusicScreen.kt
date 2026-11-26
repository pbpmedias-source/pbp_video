@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.monmontage.app.model.Branding
import com.monmontage.app.model.ClipType
import com.monmontage.app.model.MusicTrack
import com.monmontage.app.model.Project
import com.monmontage.app.render.MusicEnvelope
import com.monmontage.app.render.TimelineItem
import com.monmontage.app.render.TimelinePlanner
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.theme.AppColors
import com.monmontage.app.ui.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MusicScreen(vm: MontageViewModel, onBack: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val branding by vm.branding.collectAsStateWithLifecycle()
    val music = project.music
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.setCustomMusic(uri)
    }

    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    DisposableEffect(player) { onDispose { player.release() } }
    /** Clé de ce qui est en écoute : id du morceau ou « custom ». */
    var playingKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(music.volume) { player.volume = music.volume }

    fun toggle(key: String, uri: suspend () -> Uri) {
        if (playingKey == key) { player.pause(); playingKey = null; return }
        playingKey = key
        scope.launch {
            player.setMediaItem(MediaItem.fromUri(uri()))
            player.prepare()
            player.play()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Musique de fond") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Choisir une musique · libres de droits", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
                MusicTrack.entries.forEach { track ->
                    TrackRow(
                        title = track.label, subtitle = track.description,
                        selected = music.customUri == null && music.track == track,
                        playing = playingKey == track.id,
                        onSelect = { vm.setMusicTrack(track) },
                        onPlay = { toggle(track.id) { vm.musicFile(track) } },
                    )
                }
                val custom = music.customUri
                if (custom != null) {
                    TrackRow(
                        title = music.customName.ifBlank { "Musique importée" }, subtitle = "Importée depuis le téléphone",
                        selected = true, playing = playingKey == "custom",
                        onSelect = {}, onPlay = { toggle("custom") { custom } },
                    )
                }
                OutlinedButton(
                    onClick = { importer.launch(arrayOf("audio/*")) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.Outline),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Default.Add, null, tint = AppColors.OnSurface, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importer une musique du téléphone", color = AppColors.OnSurface)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text("Volume de la musique", modifier = Modifier.weight(1f))
                    Text("${(music.volume * 100).roundToInt()} %", fontWeight = FontWeight.SemiBold)
                }
                Slider(value = music.volume, onValueChange = vm::setMusicVolume, valueRange = 0f..1f)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Couper pendant les interviews")
                    Text("Sinon atténuée à 12 % sous la voix", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                Switch(checked = music.muteDuringInterviews, onCheckedChange = vm::setMuteDuringInterviews)
            }

            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(containerColor = AppColors.Background),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Volume de la musique sur votre montage", style = MaterialTheme.typography.titleSmall)
                    MusicEnvelopeChart(project, branding)
                    Text(
                        "Fondus de 0,8 s : la musique s'efface juste avant que la voix commence et revient après. " +
                            "Elle est répétée si elle est plus courte que le montage.",
                        style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(title: String, subtitle: String, selected: Boolean, playing: Boolean, onSelect: () -> Unit, onPlay: () -> Unit) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) AppColors.SurfaceHigh else AppColors.Surface,
        border = if (selected) BorderStroke(2.dp, AppColors.Accent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, maxLines = 1)
            }
            FilledIconButton(
                onClick = onPlay,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (playing) AppColors.Accent else AppColors.Track,
                    contentColor = if (playing) AppColors.OnAccent else AppColors.OnSurface,
                ),
            ) {
                Icon(if (playing) AppIcons.Pause else Icons.Default.PlayArrow, if (playing) "Arrêter l'écoute" else "Écouter $title", Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Schéma calculé à partir du VRAI montage : la courbe est l'enveloppe qui sera appliquée à l'export,
 * la frise dessous montre les éléments (bleu : génériques et illustrations, ambre : photos, orange : interviews).
 */
@Composable
private fun MusicEnvelopeChart(project: Project, branding: Branding) {
    val data = remember(project, branding) {
        val timeline = TimelinePlanner.plan(project, branding)
        val env = MusicEnvelope(
            timeline.interviewRanges, timeline.totalMs, 1f,
            if (project.music.muteDuringInterviews) 0f else 0.12f,
        )
        val samples = 240
        val curve = if (timeline.totalMs <= 0) FloatArray(0)
        else FloatArray(samples + 1) { i -> env.gainAtUs(timeline.totalMs * 1000L * i / samples) }
        Triple(timeline, curve, timeline.totalMs)
    }
    val (timeline, curve, total) = data
    if (total <= 0) {
        Text("Ajoutez des médias pour voir la courbe.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        return
    }
    Canvas(
        Modifier.fillMaxWidth().height(60.dp)
            .semantics { contentDescription = "Courbe du volume de la musique : elle baisse pendant les interviews" }
    ) {
        val top = 4.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val path = Path()
        curve.forEachIndexed { i, g ->
            val x = size.width * i / (curve.size - 1)
            val y = bottom - (bottom - top) * g
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, AppColors.Music, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
    Canvas(Modifier.fillMaxWidth().height(26.dp)) {
        val gap = 2.dp.toPx()
        timeline.items.forEach { item ->
            val x0 = size.width * item.startMs / total
            val x1 = size.width * item.endMs / total
            val type: ClipType? = when (item) {
                is TimelineItem.PhotoItem -> ClipType.PHOTO
                is TimelineItem.VideoItem -> item.clip.type
                else -> null
            }
            drawRoundRect(
                AppColors.blockColor(type), Offset(x0, 0f), Size((x1 - x0 - gap).coerceAtLeast(1f), size.height),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}
