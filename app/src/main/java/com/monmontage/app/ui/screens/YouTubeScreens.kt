@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.model.formatMs
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.UploadState
import com.monmontage.app.ui.components.KeepScreenOn
import com.monmontage.app.ui.components.MediaThumbnail
import com.monmontage.app.ui.theme.AppColors
import com.monmontage.app.ui.theme.AppIcons
import com.monmontage.app.youtube.UploadMetadata

// ---------------------------------------------------------------------------------------------
// 1. Connexion (première fois)
// ---------------------------------------------------------------------------------------------

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun YouTubeConnectScreen(vm: MontageViewModel, onBack: () -> Unit, onConnected: () -> Unit) {
    val email by vm.youtubeEmail.collectAsStateWithLifecycle()
    val error by vm.authError.collectAsStateWithLifecycle()
    var waiting by remember { mutableStateOf(false) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.onAuthResult(result.data)
    }
    LaunchedEffect(email) { if (email != null) { waiting = false; onConnected() } }
    LaunchedEffect(error) { if (error != null) waiting = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publier sur YouTube") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(96.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF2A3550)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.YouTube, null, tint = AppColors.Link, modifier = Modifier.size(48.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Connectez votre chaîne", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Une seule fois : ensuite, vos montages partent sur YouTube directement depuis l'application.",
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC4CCD8), textAlign = TextAlign.Center,
                )
            }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    PermissionRow(Icons.Default.Check, AppColors.Success, "Envoyer des vidéos sur votre chaîne")
                    HorizontalDivider(color = AppColors.Track)
                    PermissionRow(Icons.Default.Close, AppColors.Muted, "Ne peut ni lire, ni modifier, ni supprimer vos vidéos existantes")
                    HorizontalDivider(color = AppColors.Track)
                    PermissionRow(Icons.Default.Lock, AppColors.Muted, "Votre mot de passe reste chez Google, jamais dans l'application")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !waiting,
                onClick = {
                    waiting = true
                    vm.connectYouTube { pending -> consent.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (waiting) "Connexion…" else "Se connecter avec Google") }
            Text(
                "Vous pourrez déconnecter le compte à tout moment, ici ou dans les réglages de votre compte Google.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, tint: Color, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Formulaire de publication
// ---------------------------------------------------------------------------------------------

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun YouTubePublishScreen(vm: MontageViewModel, onBack: () -> Unit, onChangeAccount: () -> Unit, onPublished: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val email by vm.youtubeEmail.collectAsStateWithLifecycle()
    val export = project.lastExport
    var title by remember { mutableStateOf(project.name.take(100)) }
    var description by remember { mutableStateOf(vm.youtubeDescription()) }
    var scheduled by remember { mutableStateOf(false) }
    // Par défaut : demain à 18 h.
    var scheduleAt by remember {
        mutableStateOf(java.time.LocalDate.now().plusDays(1).atTime(18, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf("private") }
    var kids by remember { mutableStateOf(false) }
    var wifiOnly by remember { mutableStateOf(true) }

    fun publish() {
        val publishAt = if (scheduled) java.time.Instant.ofEpochMilli(scheduleAt).toString() else null
        if (vm.publishToYouTube(UploadMetadata(title.trim(), description.trim(), if (scheduled) "private" else privacy, kids, publishAt), wifiOnly)) onPublished()
    }
    // Android 13+ : la notification de progression demande une autorisation (l'envoi marche sans).
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { publish() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publier sur YouTube") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF5A3422)), contentAlignment = Alignment.Center) {
                        Text((email ?: "?").take(1).uppercase(), color = Color(0xFFFFC6A8), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(email ?: "Compte Google", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                        Text("Compte connecté", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                    TextButton(onClick = { vm.disconnectYouTube(); onChangeAccount() }) { Text("Changer", color = AppColors.Link) }
                }
            }

            if (export == null) {
                Text("Exportez d'abord la vidéo : c'est le MP4 exporté qui est envoyé.", color = MaterialTheme.colorScheme.error)
                Button(onClick = onBack, modifier = Modifier.height(44.dp)) { Text("Retour") }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 120.dp, height = 68.dp).clip(RoundedCornerShape(10.dp))) {
                    MediaThumbnail(export.uri, true, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(export.fileName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                    Text("${formatMs(export.durationMs).substringBeforeLast('.')} · 1080p · H.264", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    Text("Miniature : choisie par YouTube", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it.take(100).replace("<", "").replace(">", "") },
                label = { Text("Titre · ${title.length} / 100") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it.take(5000).replace("<", "").replace(">", "") },
                label = { Text("Description") }, minLines = 4, maxLines = 12, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Pré-remplie : présentation, chapitres (légendes et intertitres) et crédits") },
            )
            TextButton(onClick = { description = vm.youtubeDescription() }) { Text("Régénérer depuis le projet", color = AppColors.Link) }

            // ----- Publication programmée -----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 52.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Programmer la publication")
                    Text("Envoyée en privée, rendue publique à l'heure choisie", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                Switch(checked = scheduled, onCheckedChange = { scheduled = it })
            }
            if (scheduled) {
                val zoned = java.time.Instant.ofEpochMilli(scheduleAt).atZone(java.time.ZoneId.systemDefault())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.weight(1f).height(48.dp)) {
                        Text(zoned.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.FRENCH)), color = AppColors.OnSurface)
                    }
                    OutlinedButton(onClick = { showTime = true }, border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.weight(0.6f).height(48.dp)) {
                        Text(zoned.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), color = AppColors.OnSurface)
                    }
                }
                if (scheduleAt < System.currentTimeMillis() + 15 * 60_000) {
                    Text("Choisissez une heure au moins 15 minutes plus tard.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!scheduled) Text("Visibilité", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
            val options = listOf("private" to "Privée", "unlisted" to "Non répertoriée", "public" to "Publique")
            if (!scheduled) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = privacy == value, onClick = { privacy = value },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size), icon = {},
                    ) { Text(label, maxLines = 1) }
                }
            }
            if (!scheduled) Text(
                when (privacy) {
                    "private" -> "Privée : vous vérifiez sur YouTube avant de la rendre publique."
                    "unlisted" -> "Non répertoriée : visible seulement avec le lien."
                    else -> "Publique : visible par tous dès la fin du traitement par YouTube."
                },
                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
            )

            Text("Public visé (obligatoire pour YouTube)", style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(false to "Pas pour les enfants", true to "Conçue pour les enfants").forEach { (value, label) ->
                    OutlinedButton(
                        onClick = { kids = value },
                        border = BorderStroke(if (kids == value) 2.dp else 1.dp, if (kids == value) AppColors.Accent else AppColors.Outline),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (kids == value) AppColors.SurfaceHigh else Color.Transparent),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) { Text(label, color = AppColors.OnSurface, maxLines = 1, style = MaterialTheme.typography.labelLarge) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Envoyer uniquement en Wi-Fi", modifier = Modifier.weight(1f))
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
            }

            Button(
                enabled = title.isNotBlank() && (!scheduled || scheduleAt >= System.currentTimeMillis() + 15 * 60_000),
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else publish()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (scheduled) "Envoyer et programmer" else "Publier") }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDate) {
        val zone = java.time.ZoneId.systemDefault()
        val current = java.time.Instant.ofEpochMilli(scheduleAt).atZone(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        scheduleAt = day.atTime(current.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val zone = java.time.ZoneId.systemDefault()
        val current = java.time.Instant.ofEpochMilli(scheduleAt).atZone(zone)
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = AppColors.Surface,
            title = { Text("Heure de publication") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    scheduleAt = current.toLocalDate().atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli()
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Annuler") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Envoi en cours / vidéo envoyée
// ---------------------------------------------------------------------------------------------

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun YouTubeUploadScreen(vm: MontageViewModel, onBack: () -> Unit, onRetry: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val state by remember(project.id) { vm.uploadState(project.id) }.collectAsStateWithLifecycle(UploadState.WaitingNetwork)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    KeepScreenOn()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Envoi sur YouTube") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (val s = state) {
                is UploadState.Done -> {
                    val link = "https://youtu.be/${s.videoId}"
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.size(88.dp).clip(CircleShape).background(Color(0xFF163229)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = AppColors.Success, modifier = Modifier.size(44.dp))
                    }
                    Text("Vidéo envoyée !", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "YouTube la traite encore quelques minutes : la haute définition peut apparaître un peu après.",
                        textAlign = TextAlign.Center, color = Color(0xFFC4CCD8), style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(link, color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Ouvrir sur YouTube") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(link)) },
                            border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Copier le lien", color = AppColors.OnSurface) }
                        OutlinedButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
                                context.startActivity(Intent.createChooser(send, "Partager le lien"))
                            },
                            border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Partager", color = AppColors.OnSurface) }
                    }
                    TextButton(onClick = onBack) { Text("Retour au montage", color = AppColors.Link) }
                }
                is UploadState.Failed -> {
                    Spacer(Modifier.height(40.dp))
                    Text("L'envoi a échoué", style = MaterialTheme.typography.titleMedium)
                    Text(s.message, textAlign = TextAlign.Center, color = AppColors.Muted)
                    Button(onClick = onRetry, modifier = Modifier.height(44.dp)) { Text("Réessayer") }
                    TextButton(onClick = onBack) { Text("Retour au montage", color = AppColors.Link) }
                }
                else -> {
                    val running = s as? UploadState.Running
                    val fraction = running?.takeIf { it.total > 0 }?.let { it.sent.toFloat() / it.total }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                        if (fraction != null) CircularProgressIndicator(
                            progress = { fraction }, modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp,
                            color = AppColors.Accent, trackColor = AppColors.Track, strokeCap = StrokeCap.Round,
                        ) else CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(), strokeWidth = 12.dp,
                            color = AppColors.Accent, trackColor = AppColors.Track, strokeCap = StrokeCap.Round,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (fraction != null) "${(fraction * 100).toInt()} %" else "…", style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp))
                            if (running != null && running.total > 0) Text(
                                "${running.sent / 1_000_000} / ${running.total / 1_000_000} Mo",
                                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
                            )
                        }
                    }
                    Text("« ${project.name} »", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (s is UploadState.WaitingNetwork) Text("En attente du réseau (Wi-Fi si demandé)…", color = AppColors.Muted, style = MaterialTheme.typography.bodySmall)
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Notifications, null, tint = AppColors.Link, modifier = Modifier.size(20.dp))
                            Text(
                                "L'envoi continue si vous quittez l'application : suivez-le dans la notification. " +
                                    "En cas de coupure réseau, il reprend là où il s'était arrêté.",
                                style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4CCD8),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onBack, border = BorderStroke(1.dp, AppColors.Outline),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text("Continuer à monter pendant l'envoi", color = AppColors.OnSurface) }
                    TextButton(onClick = { vm.cancelUpload(); onBack() }) { Text("Annuler l'envoi", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
