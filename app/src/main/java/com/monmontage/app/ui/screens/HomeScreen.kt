@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.model.Clip
import com.monmontage.app.model.ClipType
import com.monmontage.app.model.formatMs
import com.monmontage.app.render.TimelinePlanner
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.components.DurationBadge
import com.monmontage.app.ui.components.IconTile
import com.monmontage.app.ui.components.MediaThumbnail
import com.monmontage.app.ui.components.TypeTag
import com.monmontage.app.ui.theme.AppColors
import com.monmontage.app.ui.theme.AppIcons
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(16.dp)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun HomeScreen(
    vm: MontageViewModel,
    onEditClip: (Long) -> Unit,
    onEditTitle: () -> Unit,
    onEditEnd: () -> Unit,
    onMusic: () -> Unit,
    onPreview: () -> Unit,
    onExport: () -> Unit,
) {
    val project by vm.project.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    // Sélecteur de photos système : aucune permission de stockage nécessaire.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        vm.addMedia(uris)
    }
    val totalMs = remember(project) { TimelinePlanner.plan(project).totalMs }
    val hasClips = project.clips.isNotEmpty()

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
                title = {
                    Column {
                        Text("Mon Montage", style = MaterialTheme.typography.headlineSmall)
                        Text("Durée totale · ${formatMs(totalMs)}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                },
                actions = {
                    TextButton(onClick = onPreview, enabled = hasClips) { Text("Aperçu", color = if (hasClips) AppColors.Link else AppColors.Muted) }
                    Button(onClick = onExport, enabled = hasClips, modifier = Modifier.padding(end = 8.dp).height(44.dp)) {
                        Text("Exporter")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Ajouter des médias") },
                containerColor = AppColors.Accent,
                contentColor = AppColors.OnAccent,
                shape = RoundedCornerShape(18.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                val t = project.titleCard
                SectionCard(
                    AppIcons.Title, AppColors.TitleBg, AppColors.Link, "Générique de début",
                    if (t.enabled) "${t.title.ifBlank { "(sans titre)" }} · ${t.durationMs / 1000} s" else "Désactivé",
                    onEditTitle,
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Séquences", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${project.clips.size} élément(s)", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
                }
            }
            if (!hasClips) item {
                Text(
                    "Ajoutez des photos et des vidéos depuis votre galerie avec le bouton ci-dessous. " +
                        "Elles s'enchaînent dans l'ordre de la liste.",
                    style = MaterialTheme.typography.bodyMedium, color = AppColors.Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            itemsIndexed(project.clips, key = { _, c -> c.id }) { index, clip ->
                ClipRow(
                    clip = clip, index = index, count = project.clips.size,
                    onEdit = { onEditClip(clip.id) },
                    onUp = { vm.moveClip(clip.id, -1) },
                    onDown = { vm.moveClip(clip.id, +1) },
                    onDelete = { vm.removeClip(clip.id) },
                    onType = { vm.setClipType(clip.id, it) },
                )
            }
            item {
                val e = project.endCard
                SectionCard(
                    AppIcons.Lines, AppColors.TitleBg, AppColors.Link, "Générique de fin",
                    if (e.enabled) "${e.mainText.ifBlank { "(vide)" }} · ${e.durationMs / 1000} s" else "Désactivé",
                    onEditEnd,
                )
            }
            item {
                val m = project.music
                SectionCard(
                    AppIcons.Music, AppColors.MusicBg, AppColors.Music, "Musique de fond",
                    (if (m.uri == null) "Par défaut" else m.displayName) + " · ${(m.volume * 100).roundToInt()} % · " +
                        if (m.muteDuringInterviews) "coupée en interview" else "atténuée en interview",
                    onMusic,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(icon: ImageVector, tileBg: Color, tileTint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = CardShape, colors = CardDefaults.cardColors(containerColor = AppColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tileBg, tileTint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("Modifier", color = AppColors.Link, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ClipRow(
    clip: Clip, index: Int, count: Int,
    onEdit: () -> Unit, onUp: () -> Unit, onDown: () -> Unit, onDelete: () -> Unit,
    onType: (ClipType) -> Unit,
) {
    val highlighted = clip.type == ClipType.INTERVIEW && clip.captions.isNotEmpty()
    Card(
        onClick = onEdit,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = if (highlighted) BorderStroke(1.dp, AppColors.Outline) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 72.dp, height = 54.dp)) {
                MediaThumbnail(clip.uri, clip.isVideo, Modifier.fillMaxSize())
                DurationBadge(formatMs(clip.editedDurationMs).substringBeforeLast('.'), Modifier.align(Alignment.BottomStart).padding(4.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${index + 1}. ${clip.displayName}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                )
                if (clip.isVideo) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(ClipType.ILLUSTRATION, ClipType.INTERVIEW).forEach { t ->
                            TypeTag(t, selected = clip.type == t, onClick = { onType(t) })
                        }
                    }
                } else {
                    TypeTag(ClipType.PHOTO)
                }
                val details = buildList {
                    if (clip.isVideo && clip.segments.size > 1) add("${clip.segments.size} segments")
                    if (clip.captions.isNotEmpty()) add("${clip.captions.size} légende(s)")
                }
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
            }
            Column {
                IconButton(onClick = onUp, enabled = index > 0, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Monter", tint = AppColors.Muted) }
                IconButton(onClick = onDown, enabled = index < count - 1, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Descendre", tint = AppColors.Muted) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Supprimer", tint = AppColors.Muted) }
        }
    }
}
