package com.monmontage.app.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.monmontage.app.render.MediaUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Miniature chargée en arrière-plan. */
@Composable
fun MediaThumbnail(uri: Uri, isVideo: Boolean, modifier: Modifier = Modifier, sizePx: Int = 320) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri, sizePx) {
        value = withContext(Dispatchers.IO) { MediaUtils.thumbnail(context, uri, isVideo, sizePx)?.asImageBitmap() }
    }
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(if (isVideo) "Vidéo" else "Photo", style = MaterialTheme.typography.labelSmall)
    }
}

/** Lecteur d'un fichier local (aperçu du montage). */
@Composable
fun FilePlayer(file: File, version: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(file, version) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player },
        modifier = modifier,
    )
}

/** Empêche la mise en veille de l'écran pendant un rendu. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Étiquette colorée du type de média (Photo / Illustration / Interview), comme sur la maquette. */
@Composable
fun TypeTag(type: com.monmontage.app.model.ClipType, selected: Boolean = true, onClick: (() -> Unit)? = null) {
    val (bg, fg) = com.monmontage.app.ui.theme.AppColors.tagColors(type)
    val shape = RoundedCornerShape(6.dp)
    val base = Modifier.clip(shape)
    val mod = if (selected) base.background(bg)
    else base.border(1.dp, com.monmontage.app.ui.theme.AppColors.Outline, shape)
    val clickable = if (onClick != null) mod.clickable(onClick = onClick) else mod
    Text(
        text = if (type == com.monmontage.app.model.ClipType.PHOTO) "PHOTO" else type.label,
        color = if (selected) fg else com.monmontage.app.ui.theme.AppColors.Muted,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        modifier = clickable.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** Pastille d'icône colorée des cartes de section. */
@Composable
fun IconTile(icon: ImageVector, background: Color, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2)) }
}

/** Badge de durée posé sur une miniature. */
@Composable
fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x8C000000)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** Boîte de dialogue « Renommer le projet ». */
@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.monmontage.app.ui.theme.AppColors.Surface,
        title = { Text("Renommer le projet") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text, onValueChange = { text = it.take(80) }, singleLine = true, label = { Text("Nom") },
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("Renommer") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
