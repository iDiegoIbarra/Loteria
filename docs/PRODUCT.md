# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Diego y su familia. Un "cantor" sostiene un Galaxy S26 Ultra o una Galaxy Tab S11 Ultra y voltea la pantalla hacia los jugadores para mostrar cada carta mientras la canta. Se juega en reuniones familiares y fiestas en casa, casi siempre con mucha luz.

## Product Purpose
"Lotería" es un presentador digital de cartas de lotería personalizadas. Reemplaza la baraja física: el usuario carga sus propias imágenes, la app las baraja y las presenta en grande, una por una. Éxito: los jugadores leen la carta a distancia sin esfuerzo y el cantor nunca pierde el ritmo del juego.

## Positioning
Las cartas son del usuario (fotos, dibujos, escaneos), no una baraja tradicional fija. La app no trae contenido propio; solo organiza, baraja y presenta.

## Operating Context
- Galería: el usuario añade imágenes con el Photo Picker nativo, las ve en cuadrícula de 4 columnas, las elimina por selección o todas a la vez.
- Juego: mazo barajado (Fisher-Yates), deslizar para avanzar/retroceder, contador "12 / 54", Reiniciar y Salir, aviso "Se acabaron las cartas".
- Dispositivo en mano del cantor, girado hacia el público; vertical u horizontal.

## Capabilities and Constraints
- Kotlin + Jetpack Compose (Material 3), una sola Activity; Coil; persistencia local (Room o JSON, por decidir).
- Imágenes copiadas a filesDir; sobreviven a borrar el original.
- Pantalla siempre encendida durante el juego; estado del mazo sobrevive a rotación y cambio de app.
- Interfaz en español; nada de MAYÚSCULAS sostenidas.
- Sin cuentas, red ni permisos de almacenamiento.

## Brand Commitments
- Estructura y comportamiento de Samsung One UI (guía oficial adjuntada por el usuario): zona de visualización arriba, interacción al alcance del pulgar, diálogos abajo, márgenes 24dp, esquinas muy redondeadas, modo selección con pulsación larga.
- Carácter festivo de lotería mexicana (papel picado, colores vivos) dentro de esa estructura. Las cartas siempre son el protagonista.

## Evidence on Hand
No hay imágenes reales de cartas todavía; los comps usan cartas de muestra. No inventar contenido de baraja tradicional como si fuera parte del producto.

## Product Principles
1. La carta manda: en juego, nada compite con la imagen.
2. Legible a distancia y bajo mucha luz.
3. Un pulgar basta: acciones frecuentes abajo, destructivas siempre confirmadas.
4. Nativo Samsung primero, fiesta después.

## Accessibility & Inclusion
Contraste mínimo 4.5:1; objetivos táctiles de 48dp; textos en sp; descripciones de contenido en imágenes y botones.
