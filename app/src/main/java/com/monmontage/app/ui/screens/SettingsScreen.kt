@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.model.BrandKit
import com.monmontage.app.model.CardStyle
import com.monmontage.app.model.EndCard
import com.monmontage.app.model.TitleCard
import com.monmontage.app.render.KitColors
import com.monmontage.app.render.Painter
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.theme.AppColors
import com.monmontage.app.ui.theme.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun BrandKit.colors() = KitColors(accent, primary, dark)
private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
private fun parseHex(s: String): Int? = s.trim().removePrefix("#").takeIf { it.length == 6 }
    ?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }

private val PRESETS = listOf(
    0xFFFF8E0A, 0xFF72037D, 0xFF2A0630, 0xFF0B2A5B, 0xFF1565C0, 0xFF0F7C7A,
    0xFF2E7D32, 0xFFD7261E, 0xFFC2185B, 0xFFFFD400, 0xFF111111, 0xFFFFFFFF,
).map { it.toInt() }

// =====================================================================================
// Réglages généraux : liste des chartes
// =====================================================================================

/** Réglages généraux : chartes graphiques (logo + couleurs), plusieurs configurations enregistrables. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsScreen(vm: MontageViewModel, onBack: () -> Unit, onEditKit: (String) -> Unit) {
    val branding by vm.branding.collectAsStateWithLifecycle()
    var deleteFor by remember { mutableStateOf<BrandKit?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Réglages généraux"); Text("Chartes graphiques : logo et couleurs", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "La charte par défaut s'applique aux nouveaux projets. Chaque projet peut en choisir une autre dans son générique.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
            )
            branding.kits.forEach { kit ->
                KitCard(
                    vm, kit, isDefault = kit.id == branding.defaultKitId, canDelete = branding.kits.size > 1,
                    onEdit = { onEditKit(kit.id) },
                    onDefault = { vm.setDefaultKit(kit.id) },
                    onDuplicate = { vm.duplicateKit(kit.id) },
                    onDelete = { deleteFor = kit },
                )
            }
            Button(onClick = { onEditKit(vm.newKit()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nouvelle charte")
            }
        }
    }

    deleteFor?.let { k ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            containerColor = AppColors.Surface,
            title = { Text("Supprimer la charte « ${k.name} » ?") },
            text = { Text("Les projets qui l'utilisent passeront à la charte par défaut.") },
            confirmButton = { TextButton(onClick = { vm.deleteKit(k.id); deleteFor = null }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Annuler") } },
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun KitCard(
    vm: MontageViewModel, kit: BrandKit, isDefault: Boolean, canDelete: Boolean,
    onEdit: () -> Unit, onDefault: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit,
) {
    val logo by produceState<Bitmap?>(null, kit.logoUri) { value = vm.logoBitmap(kit) }
    Card(
        onClick = onEdit, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = if (isDefault) BorderStroke(2.dp, AppColors.Accent) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(kit.dark)), contentAlignment = Alignment.Center) {
                    logo?.let { Image(it.asImageBitmap(), null, Modifier.size(40.dp), contentScale = ContentScale.Fit) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(kit.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (kit.logoUri == null) "Logo PBP Médias" else "Logo personnalisé", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                if (isDefault) {
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(AppColors.SurfaceHigh).padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Star, null, tint = AppColors.Link, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp))
                        Text("Par défaut", style = MaterialTheme.typography.labelSmall, color = AppColors.Link)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(kit.accent to "Accent", kit.primary to "Principale", kit.dark to "Foncée").forEach { (c, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(c)).border(1.dp, AppColors.Outline, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted)
                            Text(hex(c), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Modifier", color = AppColors.Link) }
                if (!isDefault) TextButton(onClick = onDefault) { Text("Par défaut", color = AppColors.Link) }
                TextButton(onClick = onDuplicate) { Text("Dupliquer", color = AppColors.Link) }
                Spacer(Modifier.weight(1f))
                if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Supprimer la charte ${kit.name}", tint = AppColors.Muted) }
            }
        }
    }
}

// =====================================================================================
// Édition d'une charte
// =====================================================================================

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun KitEditorScreen(vm: MontageViewModel, kitId: String, onBack: () -> Unit) {
    val branding by vm.branding.collectAsStateWithLifecycle()
    val kit = branding.kits.firstOrNull { it.id == kitId }
    if (kit == null) { LaunchedEffect(Unit) { onBack() }; return }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.saveKit(kit.copy(logoUri = uri)) }
    val logo by produceState<Bitmap?>(null, kit.logoUri) { value = vm.logoBitmap(kit) }
    val logoColors by produceState(emptyList<Int>(), kit.logoUri) { value = vm.logoPalette(kit) }

    val sampleTitle = TitleCard(tag = "REPORTAGE", title = "Titre du reportage", subtitle = "Sous-titre du reportage", author = "Un reportage de …", date = "26.11.2026", place = "Lieu")
    val sampleEnd = EndCard()
    val previews by produceState<List<ImageBitmap>>(emptyList(), kit, logo) {
        delay(150)
        value = withContext(Dispatchers.Default) {
            val k = kit.colors()
            listOf(
                Painter.renderTitleCard(context, 640, 360, CardStyle.EDITION_SOIR, sampleTitle, logo, null, k),
                Painter.renderTitleCard(context, 320, 180, CardStyle.FLASH_INFO, sampleTitle, logo, null, k),
                Painter.renderEndCard(context, 320, 180, CardStyle.MAGAZINE, sampleEnd, logo, null, k),
                Painter.renderCaptionBitmap(320, 180, "Prénom Nom", "Fonction", k).let { cap ->
                    Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).also { b ->
                        val c = android.graphics.Canvas(b); c.drawColor(0xFF5F6B7A.toInt()); c.drawBitmap(cap, 0f, 0f, null); cap.recycle()
                    }
                },
            ).map { it.asImageBitmap() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charte graphique") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Aperçu : générique « Édition du soir », puis Flash info, Magazine (fin) et une légende.
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(AppColors.Track)) {
                previews.getOrNull(0)?.let { Image(it, "Aperçu de la charte", Modifier.fillMaxSize()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..3).forEach { i ->
                    Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(AppColors.Track)) {
                        previews.getOrNull(i)?.let { Image(it, null, Modifier.fillMaxSize()) }
                    }
                }
            }

            OutlinedTextField(
                value = kit.name, onValueChange = { vm.saveKit(kit.copy(name = it.take(40))) },
                label = { Text("Nom de la charte") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(kit.dark)), contentAlignment = Alignment.Center) {
                        logo?.let { Image(it.asImageBitmap(), null, Modifier.size(44.dp), contentScale = ContentScale.Fit) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Logo", style = MaterialTheme.typography.titleSmall)
                        Text(if (kit.logoUri == null) "PBP Médias (par défaut)" else "Logo personnalisé", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                    TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("Changer", color = AppColors.Link)
                    }
                }
            }
            if (kit.logoUri != null) TextButton(onClick = { vm.saveKit(kit.copy(logoUri = null)) }) { Text("Revenir au logo PBP Médias", color = AppColors.Link) }

            Text("Couleurs", style = MaterialTheme.typography.titleMedium)
            if (logoColors.isNotEmpty()) Text("Les premières pastilles sont les couleurs de votre logo.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            ColorRow("Accent", "Étiquettes, filets, barre des légendes", kit.accent, logoColors) { vm.saveKit(kit.copy(accent = it)) }
            ColorRow("Principale", "Bandeaux de titre, nom dans les légendes", kit.primary, logoColors) { vm.saveKit(kit.copy(primary = it)) }
            ColorRow("Foncée", "Panneau « Magazine », fond sans photo", kit.dark, logoColors) { vm.saveKit(kit.copy(dark = it)) }
            TextButton(onClick = { val d = BrandKit(); vm.saveKit(kit.copy(accent = d.accent, primary = d.primary, dark = d.dark)) }) {
                Text("Revenir aux couleurs PBP Médias", color = AppColors.Link)
            }

            if (branding.defaultKitId != kit.id) {
                OutlinedButton(
                    onClick = { vm.setDefaultKit(kit.id) }, border = BorderStroke(1.dp, AppColors.Outline),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Utiliser par défaut pour les nouveaux projets", color = AppColors.OnSurface) }
            } else {
                Text("Charte par défaut des nouveaux projets.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Une couleur : pastille, code hexadécimal modifiable, et pastilles proposées (logo puis palette). */
@Composable
private fun ColorRow(label: String, usage: String, value: Int, logoColors: List<Int>, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(hex(value)) }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColors.Surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(value)).border(1.dp, AppColors.Outline, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(usage, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> text = t.take(7).uppercase(); parseHex(text)?.let(onChange) },
                    singleLine = true, isError = parseHex(text) == null,
                    modifier = Modifier.width(118.dp),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (logoColors + PRESETS).distinct().forEach { c ->
                    val selected = (c and 0xFFFFFF) == (value and 0xFFFFFF)
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                            .border(if (selected) 3.dp else 1.dp, if (selected) AppColors.Accent else AppColors.Outline, CircleShape)
                            .clickable(onClickLabel = "Choisir ${hex(c)}") { onChange(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = if (KitColors.luminance(c) > 0.5) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
