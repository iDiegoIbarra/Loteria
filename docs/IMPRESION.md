# Guía de impresión de los tableros

Hay dos PDF en `tools/tableros/output/`. Los dos tienen las mismas 24 tablas.

## Imprenta — `Loteria_Tableros_Imprenta.pdf`

- La página mide **221.9 × 285.4 mm**, que es tamaño Carta más 3 mm de rebase por lado. El área de corte está definida en el PDF (TrimBox).
- Pide al 100 % (tamaño real), en **Opalina blanca**, a una cara y a color.
- Pide el **corte a tamaño Carta** (215.9 × 279.4 mm). Las cartas quedan a 5 mm del corte.

## Casa (Canon Pixma G3160) — `Loteria_Tableros_Casa.pdf`

- La página mide exactamente tamaño **Carta**, con un margen blanco de 6.35 mm que queda dentro del área imprimible.
- Pon la Opalina en la **bandeja trasera**.
- Configura el controlador así:
  - **Tipo de papel:** Papel fotográfico mate (o "Otro papel fotográfico grueso").
  - **Calidad:** Alta.
  - **Escala:** 100 % / Tamaño real. No uses "Ajustar a página".
  - **Sin bordes:** desactivado.
- Imprime **una hoja de prueba** antes de gastar toda la Opalina.

## Verificar el reparto

`Loteria_Tableros_Lista.txt` indica las 16 cartas de cada tabla, fila por fila. Al generar los PDF, el script comprueba que:

- haya 24 tablas distintas de 16 cartas cada una;
- cada una de las 54 cartas aparezca 7 u 8 veces;
- dos tablas compartan como máximo 7 cartas.
