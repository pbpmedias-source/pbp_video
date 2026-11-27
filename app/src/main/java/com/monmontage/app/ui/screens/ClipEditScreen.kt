@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.model.Caption
import com.monmontage.app.model.Clip
import com.monmontage.app.model.ClipType
import com.monmontage.app.model.SegmentOps
import com.monmontage.app.render.KitColors
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
    val branding by vm.branding.collectAsStateWithLifecycle()
    val kit = branding.kitFor(project)
    val colors = KitColors(kit.accent, kit.primary, kit.dark)
    if (clip.isCarton) {
        CartonEditScreen(vm, clip, project.cardStyle, colors, onBack)
        return
    }
    val logo by produceState<android.graphics.Bitmap?>(null, kit.id, kit.logoUri) { value = vm.logoBitmap(kit) }
    /** Dernière légende modifiée : montrée dans l'aperçu tant qu'aucune autre n'est à l'écran. */
    var focusedCaptionId by remember { mutableStateOf<Long?>(null) }
    val focused = clip.captions.firstOrNull { it.id == focusedCaptionId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (clip.isVideo) "Découpe, légendes et logo" else "Photo, légendes et logo") },
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
                // Un seul aperçu : la vidéo, avec la légende (à son moment) et le logo simulés par-dessus.
                VideoTrimEditor(
                    clip = clip,
                    onSegmentsChange = { vm.setSegments(clip.id, it) },
                    overlay = { pos -> MediaOverlay(clip, SegmentOps.editedTimeAt(clip.segments, pos), focused, logo, colors) },
                )
            } else {
                PhotoEditor(clip, overlay = { MediaOverlay(clip, null, focused ?: clip.captions.firstOrNull(), logo, colors) }) {
                    vm.updateClip(clip.copy(photoDurationMs = it))
                }
            }
            Text(
                "L'aperçu simule le rendu final : légende façon JT et logo en miniature à leur place réelle.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
            )
            HorizontalDivider()
            LogoOnClip(vm, clip)
            HorizontalDivider()
            CaptionsEditor(
                captions = clip.captions,
                clipDurationMs = clip.editedDurationMs,
                onChange = { vm.setCaptions(clip.id, it) },
                onFocus = { focusedCaptionId = it },
            )
        }
    }
}

/**
 * Calque de simulation par-dessus le média (cadre 16:9 de la vidéo finale) :
 * légende active à [editedMs] (sinon [fallback]) et logo si activé pour ce média.
 */
@Composable
private fun BoxScope.MediaOverlay(clip: Clip, editedMs: Long?, fallback: Caption?, logo: android.graphics.Bitmap?, colors: KitColors) {
    val active = editedMs?.let { t -> clip.captions.firstOrNull { t >= it.startMs && t <= it.startMs + it.durationMs } }
    val caption = active ?: fallback
    if (caption != null) {
        val bmp = remember(caption.name, caption.role, colors) { Painter.renderCaptionBitmap(640, 360, caption.name, caption.role, colors).asImageBitmap() }
        Image(bmp, "Simulation de la légende", Modifier.matchParentSize())
    }
    if (clip.showLogo && logo != null) {
        BoxWithConstraints(Modifier.matchParentSize()) {
            Image(
                logo.asImageBitmap(), "Simulation du logo",
                Modifier.align(Alignment.BottomEnd).padding(end = maxHeight * 0.03f, bottom = maxHeight * 0.03f)
                    .height(maxHeight * 0.07f).aspectRatio(logo.width.toFloat() / logo.height),
                alpha = 0.9f,
            )
        }
    }
}

@Composable
private fun PhotoEditor(clip: Clip, overlay: @Composable BoxScope.() -> Unit, onDuration: (Long) -> Unit) {
    // Photo en « ajustement » sur fond noir : comme dans la vidéo (bandes noires si elle n'est pas en 16:9).
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
        MediaThumbnail(clip.uri, false, Modifier.fillMaxSize(), sizePx = 1024, contentScale = ContentScale.Fit)
        overlay()
    }
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
private fun CaptionsEditor(captions: List<Caption>, clipDurationMs: Long, onChange: (List<Caption>) -> Unit, onFocus: (Long) -> Unit) {
    Text("Légendes (bandeau façon journal télévisé)", style = MaterialTheme.typography.titleMedium)
    Text(
        "Le bandeau apparaît au moment choisi, compté dans la partie conservée du média. " +
            "Dans l'aperçu, il s'affiche quand la lecture atteint ce moment, ou dès que vous le modifiez.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    captions.forEach { c ->
        CaptionCard(
            caption = c,
            clipDurationMs = clipDurationMs,
            onUpdate = { updated -> onFocus(updated.id); onChange(captions.map { if (it.id == updated.id) updated else it }) },
            onDelete = { onChange(captions.filterNot { it.id == c.id }) },
        )
    }
    OutlinedButton(
        enabled = clipDurationMs >= 1000,
        onClick = {
            val start = if (clipDurationMs > 3000) 1000L else 0L
            val added = Caption(name = "Prénom Nom", role = "Fonction", startMs = start, durationMs = min(5000L, clipDurationMs - start))
            onFocus(added.id)
            onChange(captions + added)
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

/** Logo en miniature sur ce média : simple interrupteur, le rendu est visible dans l'aperçu ci-dessus. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun LogoOnClip(vm: MontageViewModel, clip: Clip) {
    val project by vm.project.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Logo en miniature sur ce média", style = MaterialTheme.typography.titleMedium)
            Text("En bas à droite, pendant toute sa durée", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
        Switch(checked = clip.showLogo, onCheckedChange = { vm.setClipLogo(clip.id, it) })
    }
    val all = project.clips.all { it.showLogo }
    TextButton(onClick = { vm.setLogoOnAllClips(!all) }) {
        Text(if (all) "Retirer le logo de tous les médias" else "Mettre le logo sur tous les médias du projet", color = AppColors.Link)
    }
}

/** Carton façon JT : intertitre ou citation, dans le style et la charte du projet. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CartonEditScreen(vm: MontageViewModel, clip: Clip, style: com.monmontage.app.model.CardStyle, colors: KitColors, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, clip.cartonText, clip.cartonAuthor, clip.cartonQuote, style, colors) {
        kotlinx.coroutines.delay(120)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            Painter.renderCarton(context, 960, 540, style, clip.cartonText, clip.cartonAuthor, clip.cartonQuote, colors).asImageBitmap()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (clip.cartonQuote) "Carton · citation" else "Carton · intertitre") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
                preview?.let { Image(it, "Aperçu du carton", Modifier.fillMaxSize()) }
            }
            Text("Style « ${style.label} » et couleurs de la charte du projet.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "Intertitre", true to "Citation").forEachIndexed { i, (q, label) ->
                    SegmentedButton(
                        selected = clip.cartonQuote == q, onClick = { vm.setCarton(clip.id, clip.cartonText, clip.cartonAuthor, q) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            Text(
                if (clip.cartonQuote) "Une phrase forte d'une interview, mise en avant à l'écran."
                else "Annonce une nouvelle partie. Les intertitres deviennent aussi des chapitres YouTube.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted,
            )
            OutlinedTextField(
                value = clip.cartonText,
                onValueChange = { vm.setCarton(clip.id, it.take(if (clip.cartonQuote) 180 else 50), clip.cartonAuthor, clip.cartonQuote) },
                label = { Text(if (clip.cartonQuote) "Citation" else "Intertitre") },
                minLines = if (clip.cartonQuote) 3 else 1, maxLines = 5, modifier = Modifier.fillMaxWidth(),
            )
            if (clip.cartonQuote) OutlinedTextField(
                value = clip.cartonAuthor, onValueChange = { vm.setCarton(clip.id, clip.cartonText, it.take(50), true) },
                label = { Text("Auteur (ex. Marie Le Goff, patronne pêcheuse)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            val seconds = (clip.photoDurationMs / 1000f).roundToInt()
            Text("Durée : $seconds s")
            Slider(
                value = seconds.toFloat(), valueRange = 2f..8f, steps = 5,
                onValueChange = { vm.updateClip(clip.copy(photoDurationMs = it.roundToInt() * 1000L)) },
            )
        }
    }
}

