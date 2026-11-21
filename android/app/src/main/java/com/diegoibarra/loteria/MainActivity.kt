package com.diegoibarra.loteria

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diegoibarra.loteria.ui.BoardViewerScreen
import com.diegoibarra.loteria.ui.EditorScreen
import com.diegoibarra.loteria.ui.GameDetailScreen
import com.diegoibarra.loteria.ui.GameScreen
import com.diegoibarra.loteria.ui.HomeScreen
import com.diegoibarra.loteria.ui.theme.LoteriaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            LoteriaTheme {
                LoteriaApp()
            }
        }
    }
}

@Composable
fun LoteriaApp(vm: LoteriaViewModel = viewModel(), editor: EditorViewModel = viewModel()) {
    val routeName by vm.route.collectAsStateWithLifecycle()
    val route = Route.valueOf(routeName)

    // Iconos claros sobre el muro rosa y las pantallas oscuras; oscuros sobre el editor.
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(route, activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = route == Route.EDITOR
                isAppearanceLightNavigationBars = route == Route.HOME || route == Route.GAME || route == Route.EDITOR
            }
        }
        onDispose { }
    }

    BackHandler(enabled = route == Route.GAME) { vm.back() }

    Crossfade(targetState = route, label = "route") { r ->
        when (r) {
            Route.HOME -> HomeScreen(vm)
            Route.GAME -> GameDetailScreen(vm, editor)
            Route.EDITOR -> EditorScreen(vm, editor)
            Route.VIEWER -> BoardViewerScreen(vm, onExport = vm::requestExport)
            Route.PLAY -> GameScreen(vm)
        }
    }
}
