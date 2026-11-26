@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.model.Caption
import com.monmontage.app.model.Clip
import com.monmontage.app.model.ClipType
import com.monmontage.app.render.Painter
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.components.MediaThumbnail
import com.monmontage.app.ui.components.TypeTag
import com.monmontage.app.ui.theme.AppColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ClipEditScreen(vm: MontageViewModel, clipId: Long, onBack: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val clip = project.clips.firstOrNull { it.id == clipId }
    if (clip == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (clip.isVideo) "Découpe et légendes" else "Photo et légendes") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(clip.displayName, style = MaterialTheme.typography.titleMedium)
            if (clip.isVideo) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Type", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted)
                    listOf(ClipType.ILLUSTRATION, ClipType.INTERVIEW).forEach { t ->
                        TypeTag(t, selected = clip.type == t, onClick = { vm.setClipType(clip.id, t) })
                    }
                }
                Text(
                    if (clip.type == ClipType.INTERVIEW) "Interview : son d'origine conservé, musique coupée en douceur."
                    else "Illustration : son d'origine coupé, accompagnée par la musique.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VideoTrimEditor(clip = clip, onSegmentsChange = { vm.setSegments(clip.id, it) })
            } else {
                PhotoEditor(clip) { vm.updateClip(clip.copy(photoDurationMs = it)) }
            }
            HorizontalDivider()
            LogoOnClip(vm, clip)
            CaptionsEditor(
                captions = clip.captions,
                clipDurationMs = clip.editedDurationMs,
                onChange = { vm.setCaptions(clip.id, it) },
            )
        }
    }
}

@Composable
private fun PhotoEditor(clip: Clip, onDuration: (Long) -> Unit) {
    MediaThumbnail(clip.uri, false, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)), sizePx = 1024)
    val seconds = (clip.photoDurationMs / 1000f).roundToInt()
    Text("Durée d'affichage : $seconds s")
    Slider(
        value = seconds.toFloat(),
        onValueChange = { onDuration(it.roundToInt() * 1000L) },
        valueRange = 2f..10f,
        steps = 7,
    )
}

@Composable
private fun CaptionsEditor(captions: List<Caption>, clipDurationMs: Long, onChange: (List<Caption>) -> Unit) {
    Text("Légendes (bandeau façon journal télévisé)", style = MaterialTheme.typography.titleMedium)
    Text(
        "Le bandeau apparaît sur ce clip au moment choisi (temps compté dans la partie conservée du clip).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    captions.forEach { c ->
        CaptionCard(
            caption = c,
            clipDurationMs = clipDurationMs,
            onUpdate = { updated -> onChange(captions.map { if (it.id == updated.id) updated else it }) },
            onDelete = { onChange(captions.filterNot { it.id == c.id }) },
        )
    }
    OutlinedButton(
        enabled = clipDurationMs >= 1000,
        onClick = {
            val start = if (clipDurationMs > 3000) 1000L else 0L
            onChange(captions + Caption(name = "Prénom Nom", role = "Fonction", startMs = start, durationMs = min(5000L, clipDurationMs - start)))
        },
        modifier = Modifier.fillMaxWidth().height(44.dp),
        border = BorderStroke(1.dp, AppColors.Outline),
    ) { Text("Ajouter une légende", color = AppColors.OnSurface) }
}

@Composable
private fun CaptionCard(caption: Caption, clipDurationMs: Long, onUpdate: (Caption) -> Unit, onDelete: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = AppColors.Background),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val preview = remember(caption.name, caption.role) {
                Painter.renderCaptionPreview(640, 360, caption.name, caption.role).asImageBitmap()
            }
            Image(preview, "Aperçu du bandeau", Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)))
            OutlinedTextField(caption.name, { onUpdate(caption.copy(name = it)) }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(caption.role, { onUpdate(caption.copy(role = it)) }, label = { Text("Fonction") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            val maxStartS = max(0f, (clipDurationMs - 500) / 1000f)
            val startS = (caption.startMs / 1000f).coerceIn(0f, maxStartS)
            Text("Apparition à %.1f s".format(startS))
            if (maxStartS > 0f) {
                Slider(value = startS, valueRange = 0f..maxStartS, onValueChange = { v ->
                    val newStart = (v * 1000).roundToLong()
                    onUpdate(caption.copy(startMs = newStart, durationMs = min(caption.durationMs, clipDurationMs - newStart).coerceAtLeast(500)))
                })
            }
            val maxDurS = min(30f, (clipDurationMs - caption.startMs) / 1000f)
            val durS = (caption.durationMs / 1000f).coerceIn(0.5f, max(0.5f, maxDurS))
            Text("Durée d'affichage : %.1f s".format(durS))
            if (maxDurS > 0.6f) {
                Slider(value = durS, valueRange = 0.5f..maxDurS, onValueChange = { v ->
                    onUpdate(caption.copy(durationMs = (v * 1000).roundToLong()))
                })
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = AppColors.Link)
                Spacer(Modifier.width(6.dp))
                Text("Supprimer la légende")
            }
        }
    }
}

/** Logo en miniature sur ce média : aperçu fidèle (7 % de la hauteur, en bas à droite) + interrupteur. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LogoOnClip(vm: MontageViewModel, clip: Clip) {
    val project by vm.project.collectAsStateWithLifecycle()
    val logo by produceState<android.graphics.Bitmap?>(null) { value = vm.logoBitmap() }
    Text("Logo en miniature", style = MaterialTheme.typography.titleMedium)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
        MediaThumbnail(clip.uri, clip.isVideo, Modifier.fillMaxSize(), sizePx = 1024)
        val l = logo
        if (clip.showLogo && l != null) {
            val h = maxHeight * 0.07f
            Image(
                l.asImageBitmap(), "Logo",
                Modifier.align(Alignment.BottomEnd).padding(end = maxHeight * 0.03f, bottom = maxHeight * 0.03f)
                    .height(h).aspectRatio(l.width.toFloat() / l.height),
                alpha = 0.9f,
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Afficher le logo sur ce média")
            Text("En bas à droite, pendant toute sa durée", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
        Switch(checked = clip.showLogo, onCheckedChange = { vm.setClipLogo(clip.id, it) })
    }
    val all = project.clips.all { it.showLogo }
    TextButton(onClick = { vm.setLogoOnAllClips(!all) }) {
        Text(if (all) "Retirer le logo de tous les médias" else "Mettre le logo sur tous les médias du projet", color = AppColors.Link)
    }
}

