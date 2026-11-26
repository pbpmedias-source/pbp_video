@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.data.ProjectSummary
import com.monmontage.app.model.formatMs
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.components.DurationBadge
import com.monmontage.app.ui.components.MediaThumbnail
import com.monmontage.app.ui.theme.AppColors
import com.monmontage.app.ui.theme.AppIcons
import java.text.Normalizer

private enum class LibFilter(val label: String) { ALL("Tous"), DRAFT("Brouillons"), EXPORTED("Exportés"), YOUTUBE("YouTube") }
private enum class LibSort(val label: String) { UPDATED("modifiés récemment"), NAME("nom (A → Z)"), CREATED("créés récemment"), DURATION("durée") }

private fun ProjectSummary.matches(f: LibFilter) = when (f) {
    LibFilter.ALL -> true
    LibFilter.DRAFT -> lastExport == null && youtube == null
    LibFilter.EXPORTED -> lastExport != null && youtube == null
    LibFilter.YOUTUBE -> youtube != null
}

private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/** Tous les projets : recherche, filtres, tri, grille ou liste. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryScreen(vm: MontageViewModel, onBack: () -> Unit, onOpen: () -> Unit) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LibFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(LibSort.UPDATED) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var sortMenu by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ProjectSummary?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.refreshProjects() }

    val shown = remember(projects, query, filter, sort) {
        val q = normalize(query.trim())
        projects.filter { it.matches(filter) && (q.isEmpty() || normalize(it.name).contains(q)) }.let { list ->
            when (sort) {
                LibSort.UPDATED -> list.sortedByDescending { it.updatedAt }
                LibSort.NAME -> list.sortedBy { normalize(it.name) }
                LibSort.CREATED -> list.sortedByDescending { it.createdAt }
                LibSort.DURATION -> list.sortedByDescending { it.durationMs }
            }
        }
    }
    val totalMin = projects.sumOf { it.durationMs } / 60_000

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Bibliothèque")
                        Text("${projects.size} projet(s) · $totalMin min de montage", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour à mes projets") } },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (grid) 2 else 1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Rechercher un projet") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibFilter.entries.forEach { f ->
                        FilterChip(
                            selected = filter == f, onClick = { filter = f },
                            label = { Text("${f.label} · ${projects.count { it.matches(f) }}") },
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { sortMenu = true }) {
                            Text("Trier : ${sort.label}", color = AppColors.OnSurface)
                            Icon(Icons.Default.ArrowDropDown, null, tint = AppColors.OnSurface)
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            LibSort.entries.forEach { o ->
                                DropdownMenuItem(text = { Text(o.label.replaceFirstChar { it.uppercase() }) }, onClick = { sort = o; sortMenu = false })
                            }
                        }
                    }
                    IconToggleButton(checked = grid, onCheckedChange = { grid = true }) {
                        Icon(AppIcons.Grid, "Affichage en grille", tint = if (grid) AppColors.Link else AppColors.Muted)
                    }
                    IconToggleButton(checked = !grid, onCheckedChange = { grid = false }) {
                        Icon(Icons.AutoMirrored.Filled.List, "Affichage en liste", tint = if (!grid) AppColors.Link else AppColors.Muted)
                    }
                }
            }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (projects.isEmpty()) "Aucun projet pour l'instant." else "Aucun projet ne correspond.",
                    color = AppColors.Muted, modifier = Modifier.padding(16.dp),
                )
            }
            items(shown, key = { it.id }) { p ->
                if (grid) GridProjectCard(p, onOpen = { vm.openProject(p.id, onOpen) }, onMenu = { menuFor = p })
                else ProjectCard(p, onOpen = { vm.openProject(p.id, onOpen) }, onMenu = { menuFor = p })
            }
        }
    }
    ProjectMenus(vm, menuFor, onDismiss = { menuFor = null }, onOpen = onOpen)
}

@Composable
private fun GridProjectCard(p: ProjectSummary, onOpen: () -> Unit, onMenu: () -> Unit) {
    Card(onClick = onOpen, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp)).background(AppColors.Track)) {
                p.firstClipUri?.let { MediaThumbnail(it, p.firstClipIsVideo, Modifier.fillMaxSize()) }
                DurationBadge(formatMs(p.durationMs).substringBeforeLast('.'), Modifier.align(Alignment.BottomStart).padding(5.dp))
            }
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            p.youtube != null -> "Publié ${relative(p.youtube.publishedAt)}"
                            p.lastExport != null -> "Exporté ${relative(p.lastExport.exportedAt)}"
                            else -> "Modifié ${relative(p.updatedAt)}"
                        },
                        style = MaterialTheme.typography.labelSmall, color = AppColors.Muted, maxLines = 1,
                    )
                    StatusBadge(p)
                }
                IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) { Icon(AppIcons.More, "Options du projet ${p.name}", tint = AppColors.Muted) }
            }
        }
    }
}
