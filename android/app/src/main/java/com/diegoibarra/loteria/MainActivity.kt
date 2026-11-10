package com.diegoibarra.loteria

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import com.diegoibarra.loteria.ui.GalleryScreen
import com.diegoibarra.loteria.ui.GameScreen
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
fun LoteriaApp(vm: LoteriaViewModel = viewModel()) {
    val inGame by vm.inGame.collectAsStateWithLifecycle()

    // Iconos claros sobre el muro rosa y sobre el fondo del juego; oscuros sobre la galería.
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(inGame, activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !inGame
            }
        }
        onDispose { }
    }

    Crossfade(targetState = inGame, label = "screen") { playing ->
        if (playing) GameScreen(vm) else GalleryScreen(vm)
    }
}
