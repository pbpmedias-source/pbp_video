@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.monmontage.app.model.Clip
import com.monmontage.app.model.Segment
import com.monmontage.app.model.SegmentOps
import com.monmontage.app.model.formatMs
import com.monmontage.app.ui.theme.AppColors
import com.monmontage.app.ui.theme.AppIcons
import kotlinx.coroutines.delay

/**
 * Découpe d'une vidéo : on place la tête de lecture (lecteur ou barre), puis on scinde,
 * on ajuste début/fin du segment sélectionné, ou on supprime un segment (portion retirée).
 * « Lire la découpe » rejoue uniquement les segments conservés, enchaînés.
 */
@Composable
fun VideoTrimEditor(
    clip: Clip,
    onSegmentsChange: (List<Segment>) -> Unit,
    /** Simulation dessinée par-dessus la vidéo (légende, logo), selon la position dans la source. */
    overlay: @Composable BoxScope.(sourcePositionMs: Long) -> Unit = {},
) {
    val context = LocalContext.current
    val player = remember(clip.uri) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) { onDispose { player.release() } }

    val segments = clip.segments
    var cutPreview by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var selected by remember { mutableIntStateOf(0) }
    val sel = selected.coerceIn(0, (segments.size - 1).coerceAtLeast(0))
    val currentSegments by rememberUpdatedState(segments)

    LaunchedEffect(player, cutPreview) {
        if (cutPreview && segments.isNotEmpty()) {
            player.setMediaItems(segments.map { s ->
                MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(s.startMs).setEndPositionMs(s.endMs).build()
                ).build()
            })
            player.prepare()
            player.play()
        } else {
            val resumeAt = positionMs
            player.setMediaItem(MediaItem.fromUri(clip.uri))
            player.prepare()
            player.seekTo(resumeAt)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            positionMs = if (cutPreview) {
                val seg = currentSegments.getOrNull(player.currentMediaItemIndex)
                (seg?.startMs ?: 0L) + player.currentPosition
            } else player.currentPosition
            delay(100)
        }
    }

    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
            modifier = Modifier.fillMaxSize(),
        )
        overlay(positionMs)
    }

    SegmentBar(
        durationMs = clip.sourceDurationMs,
        segments = segments,
        selected = sel,
        positionMs = positionMs,
        onTap = { ms ->
            if (!cutPreview) player.seekTo(ms)
            val i = SegmentOps.indexAt(segments, ms)
            if (i >= 0) selected = i
        },
    )
    Row(Modifier.fillMaxWidth()) {
        Text("Position ${formatMs(positionMs)} / ${formatMs(clip.sourceDurationMs)}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, modifier = Modifier.weight(1f))
        Text("Conservé ${formatMs(clip.editedDurationMs)}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
    }

    val editing = !cutPreview
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            enabled = editing, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 6.dp),
            modifier = Modifier.weight(1f).height(44.dp),
            onClick = {
                val newSegs = SegmentOps.split(segments, positionMs)
                onSegmentsChange(newSegs)
                selected = SegmentOps.indexAt(newSegs, positionMs).coerceAtLeast(0)
            },
        ) {
            Icon(AppIcons.Scissors, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Scinder ici", maxLines = 1)
        }
        OutlinedButton(
            enabled = editing && segments.isNotEmpty(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 6.dp),
            border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.weight(1f).height(44.dp),
            onClick = { onSegmentsChange(SegmentOps.setStart(segments, sel, positionMs)) },
        ) { Text("Début ici", color = AppColors.OnSurface, maxLines = 1) }
        OutlinedButton(
            enabled = editing && segments.isNotEmpty(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 6.dp),
            border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.weight(1f).height(44.dp),
            onClick = { onSegmentsChange(SegmentOps.setEnd(segments, sel, positionMs, clip.sourceDurationMs)) },
        ) { Text("Fin ici", color = AppColors.OnSurface, maxLines = 1) }
    }
    Button(
        enabled = segments.isNotEmpty(), onClick = { cutPreview = !cutPreview },
        modifier = Modifier.fillMaxWidth().height(44.dp),
    ) { Text(if (cutPreview) "Revenir à la vidéo complète" else "Lire la découpe") }
    TextButton(enabled = editing, onClick = { onSegmentsChange(listOf(Segment(0, clip.sourceDurationMs))); selected = 0 }) {
        Text("Réinitialiser la découpe", color = AppColors.Link)
    }

    Text("Segments conservés", style = MaterialTheme.typography.titleSmall, color = AppColors.Muted)
    if (segments.isEmpty()) Text("Aucun segment : ce clip sera ignoré à l'export.", color = MaterialTheme.colorScheme.error)
    segments.forEachIndexed { i, s ->
        Surface(
            onClick = { selected = i; if (editing) player.seekTo(s.startMs) },
            shape = RoundedCornerShape(10.dp),
            color = if (i == sel) AppColors.SurfaceHigh else AppColors.Surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Segment ${i + 1} · ${formatMs(s.startMs)} → ${formatMs(s.endMs)}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(enabled = editing, onClick = { onSegmentsChange(SegmentOps.remove(segments, i)) }) {
                    Icon(Icons.Default.Delete, "Supprimer le segment ${i + 1}", tint = AppColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun SegmentBar(durationMs: Long, segments: List<Segment>, selected: Int, positionMs: Long, onTap: (Long) -> Unit) {
    val kept = AppColors.Kept
    val selectedColor = AppColors.Accent
    val removed = AppColors.Track
    val head = Color.White
    val currentOnTap by rememberUpdatedState(onTap)
    val dur = durationMs.coerceAtLeast(1)
    Canvas(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp))
            .pointerInput(dur) {
                detectTapGestures { o -> currentOnTap((o.x / size.width * dur).toLong().coerceIn(0, dur)) }
            }
    ) {
        drawRect(removed)
        segments.forEachIndexed { i, s ->
            val x0 = s.startMs.toFloat() / dur * size.width
            val x1 = s.endMs.toFloat() / dur * size.width
            drawRect(if (i == selected) selectedColor else kept, Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(1f), size.height))
            drawLine(removed, Offset(x0, 0f), Offset(x0, size.height), strokeWidth = 2.dp.toPx())
        }
        val px = positionMs.toFloat() / dur * size.width
        drawLine(head, Offset(px, 0f), Offset(px, size.height), strokeWidth = 3.dp.toPx())
    }
}
