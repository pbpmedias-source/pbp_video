@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.data.ProjectSummary
import com.monmontage.app.model.formatMs
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.components.DurationBadge
import com.monmontage.app.ui.components.IconTile
import com.monmontage.app.ui.components.MediaThumbnail
import com.monmontage.app.ui.components.RenameDialog
import com.monmontage.app.ui.theme.AppColors
import com.monmontage.app.ui.theme.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écran d'ouverture : la liste des montages enregistrés. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ProjectsScreen(vm: MontageViewModel, onOpen: () -> Unit, onSettings: () -> Unit, onLibrary: () -> Unit) {
    val branding by vm.branding.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<ProjectSummary?>(null) }
    LaunchedEffect(Unit) { vm.refreshProjects() }
    val recents = projects.take(RECENT_COUNT)

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 20.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mes projets", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (projects.isEmpty()) "Aucun montage pour l'instant" else "${projects.size} montage(s) · enregistrés sur ce téléphone",
                        style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
                    )
                }
            }
            item {
                Button(
                    onClick = { vm.newProject(); onOpen() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Nouveau projet")
                }
            }
            if (projects.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Récents", style = MaterialTheme.typography.labelLarge, color = AppColors.Muted, modifier = Modifier.weight(1f))
                    TextButton(onClick = onLibrary) { Text("Tout voir (${projects.size})", color = AppColors.Link) }
                }
            }
            items(recents, key = { it.id }) { p ->
                ProjectCard(p, onOpen = { vm.openProject(p.id, onOpen) }, onMenu = { menuFor = p })
            }
            if (projects.isNotEmpty()) item {
                OutlinedButton(
                    onClick = onLibrary, shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Toute la bibliothèque · ${projects.size} projet(s)", color = AppColors.OnSurface) }
            }
            item {
                Card(
                    onClick = onSettings,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Background),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(AppIcons.Settings, AppColors.TitleBg, AppColors.Link)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Réglages généraux", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Charte par défaut : ${branding.defaultKit.name} · ${branding.kits.size} charte(s)",
                                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text("Modifier", color = AppColors.Link, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    ProjectMenus(vm, menuFor, onDismiss = { menuFor = null }, onOpen = onOpen)
}

private const val RECENT_COUNT = 4

/** Options d'un projet (ouvrir, renommer, dupliquer, supprimer) : utilisées par l'accueil et la bibliothèque. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ProjectMenus(vm: MontageViewModel, menuFor: ProjectSummary?, onDismiss: () -> Unit, onOpen: () -> Unit) {
    var renameFor by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteFor by remember { mutableStateOf<ProjectSummary?>(null) }
    menuFor?.let { p ->
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.Surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) {
                    Box(Modifier.size(width = 72.dp, height = 40.dp)) {
                        p.firstClipUri?.let { MediaThumbnail(it, p.firstClipIsVideo, Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${formatMs(p.durationMs).substringBeforeLast('.')} · ${p.clipCount} média(s) · ${relative(p.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                }
                SheetAction(AppIcons.Lines, "Ouvrir le montage") { onDismiss(); vm.openProject(p.id, onOpen) }
                SheetAction(Icons.Default.Edit, "Renommer") { onDismiss(); renameFor = p }
                SheetAction(AppIcons.Transition, "Dupliquer") { onDismiss(); vm.duplicateProject(p.id) }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SheetAction(Icons.Default.Delete, "Supprimer le projet", color = MaterialTheme.colorScheme.error) { onDismiss(); deleteFor = p }
            }
        }
    }

    renameFor?.let { p ->
        RenameDialog(p.name, onDismiss = { renameFor = null }) { vm.renameProject(p.id, it); renameFor = null }
    }

    deleteFor?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            containerColor = Color(0xFF1F2430),
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer « ${p.name} » ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Le montage (ordre, découpes, légendes, réglages) sera définitivement effacé.")
                    Text(
                        "Vos photos, vidéos et les vidéos déjà exportées restent dans la galerie.",
                        color = AppColors.Muted, style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.deleteProject(p.id); deleteFor = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color(0xFF2A0B08)),
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, color: Color = AppColors.OnSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ProjectCard(p: ProjectSummary, onOpen: () -> Unit, onMenu: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 104.dp, height = 58.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.Track)) {
                p.firstClipUri?.let { MediaThumbnail(it, p.firstClipIsVideo, Modifier.fillMaxSize()) }
                DurationBadge(formatMs(p.durationMs).substringBeforeLast('.'), Modifier.align(Alignment.BottomStart).padding(4.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        p.youtube != null -> "Publié ${relative(p.youtube.publishedAt)}"
                        p.lastExport != null -> "Exporté ${relative(p.lastExport.exportedAt)}"
                        else -> "Modifié ${relative(p.updatedAt)}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
                )
                StatusBadge(p)
            }
            IconButton(onClick = onMenu) { Icon(AppIcons.More, "Options du projet ${p.name}", tint = AppColors.Muted) }
        }
    }
}

@Composable
internal fun StatusBadge(p: ProjectSummary) {
    val (bg, fg, label, icon) = when {
        p.missingMedia > 0 -> Quad(Color(0xFF3A2614), Color(0xFFFFB27A), "${p.missingMedia} média(s) introuvable(s)", Icons.Default.Warning)
        p.youtube != null -> Quad(Color(0xFF2A3550), Color(0xFFDCE6FF), "Sur YouTube", AppIcons.YouTube)
        p.lastExport != null -> Quad(Color(0xFF163229), AppColors.Success, "Exporté", null)
        else -> Quad(AppColors.Track, Color(0xFFC4CCD8), "Brouillon", null)
    }
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)) }
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
    }
}

private data class Quad(val bg: Color, val fg: Color, val label: String, val icon: ImageVector?)

internal fun relative(time: Long): String {
    val diff = System.currentTimeMillis() - time
    return when {
        diff < 60_000 -> "à l'instant"
        diff < 3_600_000 -> "il y a ${diff / 60_000} min"
        diff < 86_400_000 -> "il y a ${diff / 3_600_000} h"
        else -> "le " + SimpleDateFormat("d MMMM", Locale.FRENCH).format(Date(time))
    }
}
