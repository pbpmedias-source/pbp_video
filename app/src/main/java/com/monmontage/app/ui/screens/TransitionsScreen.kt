@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.model.TransitionStyle
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.theme.AppColors

private val FrameA = Color(0xFF3C5C99)
private val FrameB = Color(0xFF5B4636)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TransitionsScreen(vm: MontageViewModel, onBack: () -> Unit, onPreview: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val t = project.transition

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Transitions")
                        Text("Un seul style, appliqué à tout le projet", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TransitionStyle.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { style ->
                        StyleCard(style, selected = t.style == style, onClick = { vm.setTransitionStyle(style) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text("Durée de la transition", modifier = Modifier.weight(1f))
                    Text("%.1f s".format(t.durationMs / 1000f), fontWeight = FontWeight.SemiBold)
                }
                val options = listOf(300L to "Rapide", 600L to "Normale", 1000L to "Lente")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (ms, label) ->
                        SegmentedButton(
                            selected = t.durationMs == ms,
                            onClick = { vm.setTransitionDuration(ms) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            enabled = t.style != TransitionStyle.NONE,
                        ) { Text(label) }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Info, null, tint = AppColors.Link, modifier = Modifier.size(20.dp))
                Text(
                    "Appliquée entre chaque média, après le générique de début et avant celui de fin. " +
                        "Le son suit l'image : pas de coupure sèche dans la voix.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFC4CCD8),
                )
            }

            OutlinedButton(
                onClick = onPreview, border = BorderStroke(1.dp, AppColors.Outline),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = AppColors.OnSurface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Voir dans l'aperçu", color = AppColors.OnSurface)
            }
        }
    }
}

@Composable
private fun StyleCard(style: TransitionStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AppColors.SurfaceHigh else AppColors.Surface,
        border = if (selected) BorderStroke(2.dp, AppColors.Accent) else null,
        modifier = modifier,
    ) {
        Box {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StylePicto(style, Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp)))
                Text(style.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(style.description, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, minLines = 2)
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(AppColors.Accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = AppColors.OnAccent, modifier = Modifier.size(14.dp)) }
            }
        }
    }
}

/** Petit schéma « image A → image B » illustrant chaque style. */
@Composable
private fun StylePicto(style: TransitionStyle, modifier: Modifier) {
    when (style) {
        TransitionStyle.NONE -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(FrameA))
            Box(Modifier.weight(1f).fillMaxHeight().background(FrameB))
        }
        TransitionStyle.FADE_BLACK -> Box(modifier.background(Brush.horizontalGradient(
            0f to FrameA, 0.18f to FrameA, 0.5f to Color.Black, 0.82f to FrameB, 1f to FrameB)))
        TransitionStyle.FLASH_WHITE -> Box(modifier.background(Brush.horizontalGradient(
            0f to FrameA, 0.28f to FrameA, 0.5f to Color.White, 0.72f to FrameB, 1f to FrameB)))
        TransitionStyle.ZOOM -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.weight(0.4f).fillMaxHeight().background(FrameA))
            Box(Modifier.weight(0.6f).fillMaxHeight().background(FrameB), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize(0.56f).border(1.5.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(3.dp)))
            }
        }
    }
}
