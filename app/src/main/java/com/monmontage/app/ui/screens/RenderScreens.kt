@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.model.formatMs
import com.monmontage.app.render.GallerySaver
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.RenderState
import com.monmontage.app.ui.components.FilePlayer
import com.monmontage.app.ui.components.KeepScreenOn
import com.monmontage.app.ui.theme.AppColors
import com.monmontage.app.ui.theme.AppIcons
import kotlinx.coroutines.delay

/** Anneau de progression façon maquette (indéterminé tant que le pourcentage n'est pas connu). */
@Composable
private fun ProgressRing(state: RenderState.Working) {
    // Rafraîchit l'estimation du temps restant chaque seconde.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val p = state.progress
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        if (p != null) {
            CircularProgressIndicator(
                progress = { p / 100f }, modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp,
                color = AppColors.Accent, trackColor = AppColors.Track, strokeCap = StrokeCap.Round,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp,
                color = AppColors.Accent, trackColor = AppColors.Track, strokeCap = StrokeCap.Round,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (p != null) "$p %" else "…", style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp))
            val remaining = remainingLabel(state, now)
            if (remaining != null) Text(remaining, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
    }
}

private fun remainingLabel(state: RenderState.Working, now: Long): String? {
    val p = state.progress ?: return null
    if (state.stage != 1 || p < 3 || state.encodeStartMs == 0L) return null
    val elapsed = now - state.encodeStartMs
    val remainingS = (elapsed * (100 - p) / p / 1000)
    return when {
        remainingS < 60 -> "moins d'une minute"
        else -> "environ ${(remainingS + 30) / 60} min restante(s)"
    }
}

@Composable
private fun StepRow(label: String, status: Int) { // 0 à venir, 1 en cours, 2 terminé
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            2 -> Box(Modifier.size(24.dp).clip(CircleShape).background(AppColors.Success), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = androidx.compose.ui.graphics.Color(0xFF0B1F18), modifier = Modifier.size(16.dp))
            }
            1 -> Box(Modifier.size(24.dp).border(3.dp, AppColors.Accent, CircleShape))
            else -> Box(Modifier.size(24.dp).border(2.dp, AppColors.Outline, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (status == 0) AppColors.Muted else AppColors.OnSurface,
            fontWeight = if (status == 1) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StepsCard(stage: Int, isPreview: Boolean) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            val labels = buildList {
                add("Préparation des médias et génériques")
                add("Montage, musique et encodage")
                if (!isPreview) add("Enregistrement dans Films/${GallerySaver.FOLDER}")
            }
            labels.forEachIndexed { i, l -> StepRow(l, if (i < stage) 2 else if (i == stage) 1 else 0) }
        }
    }
}

/** Aperçu : rendu rapide 480p par le MÊME pipeline que l'export, puis lecture. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PreviewScreen(vm: MontageViewModel, onBack: () -> Unit, onExport: () -> Unit) {
    val state by vm.previewState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.startPreview() }
    DisposableEffect(Unit) { onDispose { vm.cancelPreviewIfRunning() } }
    KeepScreenOn()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aperçu du montage") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                is RenderState.Working -> {
                    Spacer(Modifier.height(16.dp))
                    ProgressRing(s)
                    Text(s.step, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    StepsCard(s.stage, isPreview = true)
                    InfoCard()
                }
                is RenderState.PreviewReady -> {
                    FilePlayer(s.file, s.version, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
                    InfoCard()
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onExport, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Tout est bon : exporter en 1080p") }
                    TextButton(onClick = onBack) { Text("Retour au montage", color = AppColors.Link) }
                }
                is RenderState.Failed -> FailedBlock("L'aperçu a échoué", s.message, onRetry = vm::startPreview, onBack = onBack)
                else -> {}
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ce que vous voyez sera exporté", style = MaterialTheme.typography.titleSmall)
            Text(
                "Rendu rapide en 480p avec le même moteur que l'export : enchaînement, découpes, musique " +
                    "et bandeaux sont identiques à la vidéo finale.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
            )
        }
    }
}

@Composable
private fun FailedBlock(title: String, message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Spacer(Modifier.height(40.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(message, textAlign = TextAlign.Center, color = AppColors.Muted)
    Button(onClick = onRetry, modifier = Modifier.height(44.dp)) { Text("Réessayer") }
    TextButton(onClick = onBack) { Text("Retour au montage", color = AppColors.Link) }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExportScreen(vm: MontageViewModel, onBack: () -> Unit, onYouTube: () -> Unit) {
    val state by vm.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmCancel by remember { mutableStateOf(false) }
    val working = state is RenderState.Working
    KeepScreenOn()
    BackHandler { if (working) confirmCancel = true else onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export MP4") },
                navigationIcon = {
                    IconButton(onClick = { if (working) confirmCancel = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (val s = state) {
                is RenderState.Working -> {
                    Spacer(Modifier.height(8.dp))
                    ProgressRing(s)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.step, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        val detail = if (s.totalMs > 0) "H.264 1920×1080 · AAC · ${formatMs(s.totalMs).substringBeforeLast('.')}" else "H.264 1920×1080 · AAC"
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                    StepsCard(s.stage, isPreview = false)
                    Text("Gardez l'application ouverte pendant l'export.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { confirmCancel = true }, border = BorderStroke(1.dp, AppColors.Outline),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Annuler", color = AppColors.OnSurface) }
                }
                is RenderState.Exported -> {
                    Spacer(Modifier.height(32.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Success, modifier = Modifier.size(72.dp))
                    Text("Vidéo enregistrée !", style = MaterialTheme.typography.headlineSmall)
                    Text("Galerie › Films/${GallerySaver.FOLDER}/${s.fileName}", textAlign = TextAlign.Center, color = AppColors.Muted)
                    Text("MP4 · H.264 1920×1080 · AAC — prêt pour YouTube.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onYouTube, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(AppIcons.YouTube, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Publier sur YouTube")
                    }
                    OutlinedButton(
                        border = BorderStroke(1.dp, AppColors.Outline),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, s.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Envoyer la vidéo (YouTube…)"))
                        },
                    ) { Text("Partager…", color = AppColors.OnSurface) }
                    OutlinedButton(
                        border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.fillMaxWidth().height(48.dp),
                        onClick = {
                            val view = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(s.uri, "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(view) }
                        },
                    ) { Text("Ouvrir la vidéo", color = AppColors.OnSurface) }
                    TextButton(onClick = onBack) { Text("Retour au montage", color = AppColors.Link) }
                }
                is RenderState.Failed -> FailedBlock("L'export a échoué", s.message, onRetry = vm::startExport, onBack = onBack)
                else -> {
                    Spacer(Modifier.height(40.dp))
                    Text("MP4 · H.264 1080p · AAC", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = vm::startExport, modifier = Modifier.height(48.dp)) { Text("Exporter le MP4") }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            containerColor = AppColors.Surface,
            title = { Text("Annuler l'export ?") },
            text = { Text("La vidéo en cours de création sera perdue.") },
            confirmButton = { TextButton(onClick = { vm.cancelRender(); confirmCancel = false; onBack() }) { Text("Annuler l'export") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Continuer") } },
        )
    }
}
