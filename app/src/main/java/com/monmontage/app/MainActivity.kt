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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monmontage.app.ui.screens.*
import com.monmontage.app.ui.screens.CardEditorScreen
import com.monmontage.app.ui.screens.ClipEditScreen
import com.monmontage.app.ui.screens.ExportScreen
import com.monmontage.app.ui.screens.HomeScreen
import com.monmontage.app.ui.screens.MusicScreen
import com.monmontage.app.ui.screens.PreviewScreen
import com.monmontage.app.ui.theme.MonMontageTheme

sealed interface Screen {
    data object Projects : Screen
    data object Library : Screen
    data object Settings : Screen
    data class KitEditor(val kitId: String) : Screen
    data object Home : Screen
    data class EditClip(val clipId: Long) : Screen
    /** from = écran de retour (les génériques s'ouvrent depuis la liste ou depuis un montage). */
    data class TitleCard(val from: Screen) : Screen
    data class EndCard(val from: Screen) : Screen
    data object Music : Screen
    data object Transitions : Screen
    data object Preview : Screen
    data object Export : Screen
    data object YouTubeConnect : Screen
    data object YouTubePublish : Screen
    data object YouTubeUpload : Screen
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
    var screen by remember { mutableStateOf<Screen>(Screen.Projects) }
    val context = LocalContext.current
    val email by vm.youtubeEmail.collectAsStateWithLifecycle()
    val goHome: () -> Unit = { screen = Screen.Home }
    val goProjects: () -> Unit = { vm.closeProject(); screen = Screen.Projects }
    val goYouTube: () -> Unit = { screen = if (email == null) Screen.YouTubeConnect else Screen.YouTubePublish }

    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // Retour système : chaque écran revient à son parent (l'export gère lui-même la confirmation).
    BackHandler(enabled = screen != Screen.Projects && screen != Screen.Export) {
        screen = when (val s = screen) {
            Screen.Home -> { vm.closeProject(); Screen.Projects }
            is Screen.TitleCard -> s.from
            is Screen.EndCard -> s.from
            Screen.Library, Screen.Settings -> Screen.Projects
            is Screen.KitEditor -> Screen.Settings
            else -> Screen.Home
        }
    }

    when (val s = screen) {
        Screen.Projects -> ProjectsScreen(
            vm = vm,
            onOpen = { screen = Screen.Home },
            onSettings = { screen = Screen.Settings },
            onLibrary = { screen = Screen.Library },
        )
        Screen.Library -> LibraryScreen(vm, onBack = { screen = Screen.Projects }, onOpen = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(vm, onBack = { screen = Screen.Projects }, onEditKit = { screen = Screen.KitEditor(it) })
        is Screen.KitEditor -> KitEditorScreen(vm, s.kitId, onBack = { screen = Screen.Settings })
        Screen.Home -> HomeScreen(
            vm = vm,
            onEditClip = { screen = Screen.EditClip(it) },
            onEditTitle = { screen = Screen.TitleCard(Screen.Home) },
            onEditEnd = { screen = Screen.EndCard(Screen.Home) },
            onMusic = { screen = Screen.Music },
            onTransitions = { screen = Screen.Transitions },
            onPreview = { screen = Screen.Preview },
            onExport = { vm.startExport(); screen = Screen.Export },
            onBack = goProjects,
        )
        is Screen.EditClip -> ClipEditScreen(vm, s.clipId, onBack = goHome)
        is Screen.TitleCard -> CardEditorScreen(vm, isTitle = true, onBack = { screen = s.from })
        is Screen.EndCard -> CardEditorScreen(vm, isTitle = false, onBack = { screen = s.from })
        Screen.Music -> MusicScreen(vm, onBack = goHome)
        Screen.Transitions -> TransitionsScreen(vm, onBack = goHome, onPreview = { screen = Screen.Preview })
        Screen.Preview -> PreviewScreen(vm, onBack = goHome, onExport = { vm.startExport(); screen = Screen.Export })
        Screen.Export -> ExportScreen(vm, onBack = { vm.resetExportState(); goHome() }, onYouTube = goYouTube)
        Screen.YouTubeConnect -> YouTubeConnectScreen(vm, onBack = goHome, onConnected = { screen = Screen.YouTubePublish })
        Screen.YouTubePublish -> YouTubePublishScreen(
            vm, onBack = goHome,
            onChangeAccount = { screen = Screen.YouTubeConnect },
            onPublished = { screen = Screen.YouTubeUpload },
        )
        Screen.YouTubeUpload -> YouTubeUploadScreen(vm, onBack = goHome, onRetry = { screen = Screen.YouTubePublish })
    }
}
