@file:OptIn(ExperimentalMaterial3Api::class)

package com.monmontage.app.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.theme.AppColors

/** Logo commun à tous les projets (PBP Médias par défaut). */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LogoScreen(vm: MontageViewModel, onBack: () -> Unit) {
    val branding by vm.branding.collectAsStateWithLifecycle()
    val logo by produceState<Bitmap?>(null, branding.logoUri) { value = vm.logoBitmap() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setLogo(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("Logo"); Text("Commun à tous les projets", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(200.dp).clip(RoundedCornerShape(24.dp)).background(AppColors.Surface), contentAlignment = Alignment.Center) {
                logo?.let { Image(it.asImageBitmap(), "Logo actuel", Modifier.size(150.dp), contentScale = ContentScale.Fit) }
            }
            Text(
                if (branding.logoUri == null) "PBP Médias (logo par défaut)" else "Logo personnalisé",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Il apparaît sur les génériques de début et de fin, et en miniature sur les images des projets qui l'activent.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Choisir un autre logo") }
            if (branding.logoUri != null) TextButton(onClick = { vm.setLogo(null) }) { Text("Revenir au logo PBP Médias", color = AppColors.Link) }
            Text("Conseil : une image PNG à fond transparent.", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
    }
}
