package com.diegoibarra.loteria.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Muro de color (rosa mexicano) y papel picado, sobre la estructura de One UI.
val Rosa = Color(0xFFE4007C)
val RosaTint = Color(0xFFFCDDEC)
val RosaInk = Color(0xFF8A004B)

val Cempasuchil = Color(0xFFF5A800)
val Verde = Color(0xFF00A35C)
val Anil = Color(0xFF2F5DD8)
val Naranja = Color(0xFFFF6A13)
val Jacaranda = Color(0xFF6B3FA0)
val Turquesa = Color(0xFF00A9B5)
val PapelColors = listOf(Cempasuchil, Verde, Anil, Naranja, Jacaranda, Turquesa)

val Ground = Color(0xFFF2F2F2)
val Ink = Color(0xFF141414)
val Ink2 = Color(0xFF5C5C5C)
val Danger = Color(0xFFD7261E)
val DisabledContainer = Color(0xFFE2E2E2)
val DisabledContent = Color(0xFF8C8C8C)

// Juego: noche para que la carta sea lo único iluminado.
val Night = Color(0xFF080808)
val Night2 = Color(0xFF1A1A1A)
val Night3 = Color(0xFF262626)
val NightInk = Color(0xFFF5F5F5)
val NightInk2 = Color(0xFFA8A8A8)
val NightAccent = Color(0xFFFF7DB8)

private val LoteriaColors = lightColorScheme(
    primary = Rosa,
    onPrimary = Color.White,
    primaryContainer = RosaTint,
    onPrimaryContainer = RosaInk,
    secondary = Cempasuchil,
    background = Ground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Ink2,
    inverseSurface = Night3,
    inverseOnSurface = NightInk,
    error = Danger,
)

@Composable
fun LoteriaTheme(content: @Composable () -> Unit) {
    // La fuente por defecto del sistema es la de One UI en dispositivos Galaxy.
    MaterialTheme(colorScheme = LoteriaColors, content = content)
}
