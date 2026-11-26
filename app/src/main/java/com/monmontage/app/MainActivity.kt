package com.monmontage.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.monmontage.app.ui.MontageViewModel
import com.monmontage.app.ui.screens.CardEditorScreen
import com.monmontage.app.ui.screens.ClipEditScreen
import com.monmontage.app.ui.screens.ExportScreen
import com.monmontage.app.ui.screens.HomeScreen
import com.monmontage.app.ui.screens.MusicScreen
import com.monmontage.app.ui.screens.PreviewScreen
import com.monmontage.app.ui.theme.MonMontageTheme

sealed interface Screen {
    data object Home : Screen
    data class EditClip(val clipId: Long) : Screen
    data object TitleCard : Screen
    data object EndCard : Screen
    data object Music : Screen
    data object Preview : Screen
    data object Export : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Thème sombre : icônes claires dans les barres système.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { MonMontageTheme { MontageApp() } }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MontageApp(vm: MontageViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val context = LocalContext.current
    val goHome: () -> Unit = { screen = Screen.Home }

    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    // L'écran d'export gère lui-même le retour (confirmation si un export est en cours).
    BackHandler(enabled = screen != Screen.Home && screen != Screen.Export) { goHome() }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            vm = vm,
            onEditClip = { screen = Screen.EditClip(it) },
            onEditTitle = { screen = Screen.TitleCard },
            onEditEnd = { screen = Screen.EndCard },
            onMusic = { screen = Screen.Music },
            onPreview = { screen = Screen.Preview },
            onExport = { vm.startExport(); screen = Screen.Export },
        )
        is Screen.EditClip -> ClipEditScreen(vm, s.clipId, onBack = goHome)
        Screen.TitleCard -> CardEditorScreen(vm, isTitle = true, onBack = goHome)
        Screen.EndCard -> CardEditorScreen(vm, isTitle = false, onBack = goHome)
        Screen.Music -> MusicScreen(vm, onBack = goHome)
        Screen.Preview -> PreviewScreen(vm, onBack = goHome, onExport = { vm.startExport(); screen = Screen.Export })
        Screen.Export -> ExportScreen(vm, onBack = { vm.resetExportState(); goHome() })
    }
}
