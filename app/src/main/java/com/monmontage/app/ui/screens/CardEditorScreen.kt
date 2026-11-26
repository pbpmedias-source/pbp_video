@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.model.CardStyle
import com.monmontage.app.render.MediaUtils
import com.monmontage.app.render.Painter
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.components.MediaThumbnail
import com.monmontage.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Génériques du projet ouvert (début si [isTitle], sinon fin) :
 * style façon JT (commun aux deux génériques du projet), photo d'arrière-plan, textes, durée.
 * Le logo est commun à tous les projets ; l'incrustation du logo sur les rushs est un réglage du projet.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CardEditorScreen(vm: MontageViewModel, isTitle: Boolean, onBack: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val branding by vm.branding.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val title = project.titleCard
    val end = project.endCard

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setLogo(uri) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setCardBackground(uri) }

    val logo by produceState<Bitmap?>(null, branding.logoUri) { value = vm.logoBitmap() }
    val background by produceState<Bitmap?>(null, project.cardBackgroundUri) {
        value = project.cardBackgroundUri?.let { u -> withContext(Dispatchers.IO) { runCatching { MediaUtils.decodeBitmap(context, u, 960, 540) }.getOrNull() } }
    }
    fun render(style: CardStyle, w: Int, h: Int): Bitmap =
        if (isTitle) Painter.renderTitleCard(context, w, h, style, title, logo, background)
        else Painter.renderEndCard(context, w, h, style, end, logo, background)

    val preview by produceState<ImageBitmap?>(null, title, end, project.cardStyle, logo, background, isTitle) {
        delay(120) // évite de redessiner à chaque frappe
        value = withContext(Dispatchers.Default) { render(project.cardStyle, 960, 540).asImageBitmap() }
    }
    val styleThumbs by produceState<Map<CardStyle, ImageBitmap>>(emptyMap(), title, end, logo, background, isTitle) {
        delay(300)
        value = withContext(Dispatchers.Default) { CardStyle.entries.associateWith { render(it, 320, 180).asImageBitmap() } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isTitle) "Générique de début" else "Générique de fin")
                        Text("Propre à ce projet · logo commun", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(AppColors.Track)) {
                preview?.let { Image(it, "Aperçu du générique", Modifier.fillMaxSize()) }
            }

            // ----- Style -----
            Text("Style (appliqué aux deux génériques du projet)", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CardStyle.entries.forEach { style ->
                    val selected = project.cardStyle == style
                    Surface(
                        onClick = { vm.setCardStyle(style) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) AppColors.SurfaceHigh else AppColors.Surface,
                        border = if (selected) BorderStroke(2.dp, AppColors.Accent) else null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(AppColors.Track)) {
                                styleThumbs[style]?.let { Image(it, null, Modifier.fillMaxSize()) }
                            }
                            Text(style.label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                        }
                    }
                }
            }
            Text(project.cardStyle.description, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)

            // ----- Photo d'arrière-plan -----
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 72.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.Track)) {
                        project.cardBackgroundUri?.let { MediaThumbnail(it, false, Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Photo d'arrière-plan", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (project.cardBackgroundUri == null) "Fond uni pour l'instant" else "Utilisée pour le début et la fin",
                            style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
                        )
                    }
                    TextButton(onClick = { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (project.cardBackgroundUri == null) "Choisir" else "Changer", color = AppColors.Link)
                    }
                }
            }
            if (project.cardBackgroundUri != null) TextButton(onClick = { vm.setCardBackground(null) }) { Text("Retirer la photo", color = AppColors.Link) }

            // ----- Textes -----
            val enabled = if (isTitle) title.enabled else end.enabled
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Afficher ce générique", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = {
                    if (isTitle) vm.setTitleCard(title.copy(enabled = it)) else vm.setEndCard(end.copy(enabled = it))
                })
            }
            if (isTitle) {
                Field("Étiquette (ex. REPORTAGE, À LA UNE)", title.tag) { vm.setTitleCard(title.copy(tag = it.take(24))) }
                Field("Titre", title.title) { vm.setTitleCard(title.copy(title = it.take(60))) }
                Field("Sous-titre", title.subtitle) { vm.setTitleCard(title.copy(subtitle = it.take(80))) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Field("Auteur", title.author) { vm.setTitleCard(title.copy(author = it.take(40))) } }
                    Box(Modifier.weight(1f)) { Field("Date", title.date) { vm.setTitleCard(title.copy(date = it.take(30))) } }
                }
                Field("Lieu (facultatif)", title.place) { vm.setTitleCard(title.copy(place = it.take(40))) }
            } else {
                Field("Étiquette (ex. À BIENTÔT, FIN)", end.tag) { vm.setEndCard(end.copy(tag = it.take(24))) }
                Field("Titre", end.title) { vm.setEndCard(end.copy(title = it.take(60))) }
                Field("Sous-titre", end.subtitle) { vm.setEndCard(end.copy(subtitle = it.take(80))) }
                OutlinedTextField(
                    value = end.credits, onValueChange = { vm.setEndCard(end.copy(credits = it.take(400))) },
                    label = { Text("Crédits") },
                    supportingText = { Text("Une ligne par crédit, au format « Rôle : Nom »") },
                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(),
                )
            }

            val durationMs = if (isTitle) title.durationMs else end.durationMs
            val seconds = (durationMs / 1000f).roundToInt()
            Text("Durée : $seconds s")
            Slider(value = seconds.toFloat(), valueRange = 2f..8f, steps = 5, onValueChange = {
                val ms = it.roundToInt() * 1000L
                if (isTitle) vm.setTitleCard(title.copy(durationMs = ms)) else vm.setEndCard(end.copy(durationMs = ms))
            })

            // ----- Logo -----
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.Track), contentAlignment = Alignment.Center) {
                        logo?.let { Image(it.asImageBitmap(), null, Modifier.size(38.dp), contentScale = ContentScale.Fit) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Logo", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (branding.logoUri == null) "PBP Médias (par défaut) · commun à tous les projets" else "Logo personnalisé · commun à tous les projets",
                            style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
                        )
                    }
                    TextButton(onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Changer", color = AppColors.Link)
                    }
                }
            }
            if (branding.logoUri != null) TextButton(onClick = { vm.setLogo(null) }) { Text("Revenir au logo PBP Médias", color = AppColors.Link) }
            Text(
                "Le logo en miniature se règle média par média, à côté des légendes (toucher un média dans le montage).",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
