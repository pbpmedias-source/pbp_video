@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.render.MediaUtils
import com.monmontage.app.render.Painter
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Édition du générique de début (isTitle = true) ou de fin, avec aperçu fidèle au rendu. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CardEditorScreen(vm: MontageViewModel, isTitle: Boolean, onBack: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setLogo(uri)
    }
    val logoUri = project.logoUri
    val logo by produceState<Bitmap?>(null, logoUri) {
        value = logoUri?.let { u -> withContext(Dispatchers.IO) { runCatching { MediaUtils.decodeBitmap(context, u, 640, 360) }.getOrNull() } }
    }
    val title = project.titleCard
    val end = project.endCard
    val preview by produceState<ImageBitmap?>(null, title, end, logo, isTitle) {
        delay(120) // évite de redessiner à chaque frappe
        value = withContext(Dispatchers.Default) {
            (if (isTitle) Painter.renderTitleCard(640, 360, title, logo) else Painter.renderEndCard(640, 360, end, logo)).asImageBitmap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isTitle) "Générique de début" else "Générique de fin") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            preview?.let { Image(it, "Aperçu du générique", Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) }

            val enabled = if (isTitle) title.enabled else end.enabled
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Afficher ce générique", Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = {
                    if (isTitle) vm.setTitleCard(title.copy(enabled = it)) else vm.setEndCard(end.copy(enabled = it))
                })
            }

            if (isTitle) {
                Field("Titre", title.title) { vm.setTitleCard(title.copy(title = it)) }
                Field("Sous-titre", title.subtitle) { vm.setTitleCard(title.copy(subtitle = it)) }
                Field("Auteur", title.author) { vm.setTitleCard(title.copy(author = it)) }
                Field("Date", title.date) { vm.setTitleCard(title.copy(date = it)) }
            } else {
                Field("Texte principal", end.mainText) { vm.setEndCard(end.copy(mainText = it)) }
                Field("Texte secondaire", end.secondaryText) { vm.setEndCard(end.copy(secondaryText = it)) }
                Field("Mention / crédits", end.credit) { vm.setEndCard(end.copy(credit = it)) }
            }

            val durationMs = if (isTitle) title.durationMs else end.durationMs
            val seconds = (durationMs / 1000f).roundToInt()
            Text("Durée : $seconds s")
            Slider(value = seconds.toFloat(), valueRange = 2f..8f, steps = 5, onValueChange = {
                val ms = it.roundToInt() * 1000L
                if (isTitle) vm.setTitleCard(title.copy(durationMs = ms)) else vm.setEndCard(end.copy(durationMs = ms))
            })

            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.TitleBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        val l = logo
                        if (l != null) Image(l.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp))
                        else Text("LOGO", style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Logo", style = MaterialTheme.typography.titleSmall)
                        Text("Commun aux deux génériques · PNG transparent conseillé", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                    OutlinedButton(
                        onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        border = BorderStroke(1.dp, AppColors.Outline), modifier = Modifier.height(44.dp),
                    ) { Text(if (logoUri == null) "Choisir" else "Changer", color = AppColors.OnSurface) }
                }
            }
            if (logoUri != null) TextButton(onClick = { vm.setLogo(null) }) { Text("Retirer le logo", color = AppColors.Link) }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}
