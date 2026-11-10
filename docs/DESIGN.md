# Sistema de diseño · Lotería

**Tesis:** la carta es la fiesta. La app es un muro de color al sol (al estilo Barragán) que enmarca las cartas del usuario. La estructura y el comportamiento son de Samsung One UI; el carácter es de lotería mexicana.

## Color

| Rol | Hex | Uso |
|---|---|---|
| Rosa mexicano | `#E4007C` | Muro (barra expandida), botón "Jugar", anillo de selección, barra de avance |
| Rosa tinte | `#FCDDEC` | Botón tonal "Añadir imágenes" |
| Rosa tinta | `#8A004B` | Texto sobre el tinte y acciones de diálogo |
| Cempasúchil | `#F5A800` | Papel picado |
| Verde | `#00A35C` | Papel picado |
| Añil | `#2F5DD8` | Papel picado |
| Naranja | `#FF6A13` | Papel picado |
| Jacaranda | `#6B3FA0` | Papel picado |
| Turquesa | `#00A9B5` | Papel picado |
| Suelo | `#F2F2F2` | Fondo de la galería (One UI) |
| Tinta / Tinta 2 | `#141414` / `#5C5C5C` | Texto principal / secundario |
| Peligro | `#D7261E` | Acciones destructivas |
| Noche | `#080808`, `#1A1A1A`, `#262626` | Juego: fondo, diálogos, botones |

El muro lleva una **sombra diagonal** (negro al 10 %) que simula luz de sol sobre una pared.

## Tipografía

Usa la fuente del sistema (en Galaxy, la de One UI; en el resto, Roboto) con la escala de One UI. No se escribe texto todo en MAYÚSCULAS.

| Elemento | Tamaño | Peso |
|---|---|---|
| Título del muro ("Lotería") | 40 sp (52 en tablet) | Light |
| Contador del juego | 34 sp (46 en tablet) | Medium, cifras tabulares |
| Título de diálogo | 20 sp | Medium |
| Cuerpo | 16 sp | Regular |
| Botón píldora | 15–17 sp | Medium |

## Estructura (One UI)

- **Barra expandida:** ocupa 39.67 % del alto en teléfono vertical y 18.78 % en tablet. Colapsa a una barra compacta al desplazar.
- **Horizontal:** no lleva barra expandida. En la galería el muro pasa a ser un panel lateral (37.5 % en tablet). En el juego, el contador va a la izquierda y los botones a la derecha.
- **Diálogos:** aparecen al pie, al alcance del pulgar. El ancho es 100 % en teléfono vertical, 60 % en horizontal o tablet vertical y 37.5 % en tablet horizontal.
- **Márgenes:** 24 dp a los lados. Los objetivos táctiles miden ≥ 48 dp.
- **Botones píldora:** 54 dp de alto (60 en tablet) con **20 dp de margen interno**; el texto nunca toca el borde.
- **Miniaturas:** proporción de carta 1500:2125 y esquinas de 14 dp.
- **Selección:** pulsación larga con vibración. La carta se encoge al 90 % con un anillo rosa. Cuando no hay nada seleccionado, la barra muestra "Todas" y "Eliminar".

## Papel picado

Cada banderita mide 50 × 54 unidades y tiene borde inferior en zigzag. Lleva recortes (círculo central, rombos y puntos) dibujados con relleno par-impar, y cuelgan de un hilo curvo con una leve rotación alterna. Aparece en dos lugares:
- en el muro de la galería;
- en el aviso "Se acabaron las cartas", el único momento festivo dentro del juego.

## Juego

La pantalla es negra para que la carta sea lo único iluminado. Las cartas vecinas asoman al 30 % de opacidad y al 88 % de escala para invitar a deslizar. Cada gesto avanza **una carta**.

## Tableros impresos

Son solo cartas, lo más grandes posible, con esquinas de 2.4 mm y 3 mm de separación. El número de tabla va al pie en Roboto Medium 8 pt.
