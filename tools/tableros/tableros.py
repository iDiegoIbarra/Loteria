"""
Tableros de Lotería para imprimir en hoja Carta (Opalina blanca).

THESIS: El tablero es la misma app hecha papel: muro rosa al sol con papel picado arriba y las
cartas como protagonistas. Refuso el tablero genérico con marco grueso y rejilla negra.
OWN-WORLD: Rosa mexicano #E4007C con sombra diagonal, papel picado cempasúchil/verde/añil/
naranja/jacaranda/turquesa, Roboto Light para "Lotería", número de tabla en Roboto Medium,
cartas con esquinas redondeadas sobre blanco opalina.
STORY: Cada jugador recibe una tabla única, la reconoce por su número y ubica sus 16 cartas de un
vistazo mientras el cantor las muestra en la tablet.
FIRST VIEWPORT: Banda rosa (28 mm) arriba con papel picado colgando, "Lotería" a la izquierda y
"Tabla 07" a la derecha; debajo, cuadrícula 4×4 de cartas completas con 3 mm de separación.
FORM: Extensión del mundo de la app (One UI + muro Barragán). Sin semilla de concepto: el brief
fija el diseño.

Genera:
  Loteria_Tableros_Imprenta.pdf  -> Carta + 3 mm de rebase (TrimBox/BleedBox), margen de corte 5 mm.
  Loteria_Tableros_Casa.pdf      -> Carta exacta, margen blanco seguro para Canon Pixma G3160.
  Loteria_Tableros_Lista.txt     -> qué cartas lleva cada tabla (para verificar).
"""
import glob
import itertools
import os
import random

from PIL import Image
from reportlab.lib.colors import Color, HexColor
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

BASE = os.path.dirname(os.path.abspath(__file__))
CARDS_DIR = os.path.join(os.path.dirname(os.path.dirname(BASE)), "cards", "normalized")
OUTPUT = os.path.join(BASE, "output")
CACHE = os.path.join(BASE, "img")
FONTS = os.path.join(BASE, "fonts")

ROSA = HexColor("#E4007C")
PAPEL = [HexColor(c) for c in ("#F5A800", "#00A35C", "#2F5DD8", "#FF6A13", "#6B3FA0", "#00A9B5")]
INK2 = HexColor("#5C5C5C")

LETTER_W, LETTER_H = 215.9 * mm, 279.4 * mm
BLEED = 3 * mm
N_BOARDS, SIZE, MAX_OVERLAP = 24, 16, 7
CARD_RATIO = 1500 / 2125

pdfmetrics.registerFont(TTFont("Roboto-Light", os.path.join(FONTS, "Roboto-Light.ttf")))
pdfmetrics.registerFont(TTFont("Roboto-Medium", os.path.join(FONTS, "Roboto-Medium.ttf")))
pdfmetrics.registerFont(TTFont("Roboto", os.path.join(FONTS, "Roboto-Regular.ttf")))


# ---------- Reparto balanceado de cartas ----------

def make_boards(card_ids, seed):
    """24 tablas de 16 cartas: cada carta 7 u 8 veces, dos tablas comparten ≤ 7 cartas."""
    rng = random.Random(seed)
    total = N_BOARDS * SIZE
    base, extra = divmod(total, len(card_ids))
    quota = {c: base for c in card_ids}
    for c in rng.sample(card_ids, extra):
        quota[c] += 1
    boards = []
    for b in range(N_BOARDS):
        remaining_boards = N_BOARDS - b
        board = set()
        # Primero las cartas que deben ir en todas las tablas restantes, luego por cuota.
        order = sorted(card_ids, key=lambda c: (-(quota[c] >= remaining_boards), -quota[c], rng.random()))
        for c in order:
            if len(board) == SIZE:
                break
            if quota[c] == 0:
                continue
            if any(len(board & prev) + (c in prev) > MAX_OVERLAP for prev in boards):
                continue
            board.add(c)
        if len(board) < SIZE:
            return None
        for c in board:
            quota[c] -= 1
        boards.append(board)
    if any(quota.values()):
        return None
    return boards


def solve(card_ids):
    for seed in range(1, 5000):
        boards = make_boards(card_ids, seed)
        if boards:
            rng = random.Random(seed * 7919)
            layout = []
            for b in boards:
                cells = list(b)
                rng.shuffle(cells)
                layout.append(cells)
            return seed, layout
    raise SystemExit("No se encontró un reparto válido")


def verify(layout, card_ids):
    sets = [set(b) for b in layout]
    assert len(layout) == N_BOARDS and all(len(s) == SIZE for s in sets)
    assert len({frozenset(s) for s in sets}) == N_BOARDS, "tablas repetidas"
    worst = max(len(a & b) for a, b in itertools.combinations(sets, 2))
    assert worst <= MAX_OVERLAP, worst
    counts = {c: sum(c in s for s in sets) for c in card_ids}
    assert set(counts.values()) <= {7, 8}, counts
    return worst, counts


# ---------- Imágenes optimizadas para impresión (~430 ppp a tamaño final) ----------

def prepared(card_no):
    os.makedirs(CACHE, exist_ok=True)
    out = os.path.join(CACHE, f"Loteria_{card_no:02d}.jpg")
    if not os.path.exists(out):
        im = Image.open(os.path.join(CARDS_DIR, f"Loteria_{card_no:02d}.jpg")).convert("RGB")
        im.resize((720, 1020), Image.LANCZOS).save(out, quality=90, optimize=True)
    return out


# ---------- Papel picado ----------

def flag(c, x, y, unit, color, rot):
    """Banderita 50×54 unidades con recortes; (x, y) es la esquina superior izquierda."""
    c.saveState()
    c.translate(x, y)
    c.rotate(rot)
    c.scale(unit, -unit)  # coordenadas con y hacia abajo, como en la app
    p = c.beginPath()
    p.moveTo(0, 0)
    p.lineTo(50, 0)
    p.lineTo(50, 46)
    xx = 50
    while xx > 0:
        p.lineTo(xx - 5, 54)
        p.lineTo(xx - 10, 46)
        xx -= 10
    p.close()

    def diamond(cx, cy, s):
        p.moveTo(cx, cy - s)
        p.lineTo(cx + s, cy)
        p.lineTo(cx, cy + s)
        p.lineTo(cx - s, cy)
        p.close()

    p.circle(25, 20, 6)
    for cx, cy, s in ((25, 9, 3), (25, 31, 3), (14, 20, 3), (36, 20, 3),
                      (10, 8, 3.5), (40, 8, 3.5), (10, 32, 3.5), (40, 32, 3.5)):
        diamond(cx, cy, s)
    for cx in (10, 20, 30, 40):
        p.circle(cx, 41, 1.6)
    c.setFillColor(color)
    c.drawPath(p, stroke=0, fill=1, fillMode=0)  # 0 = par-impar: los recortes quedan huecos
    c.restoreState()


def papel_picado(c, x0, x1, top, height, offset):
    """Tira de banderitas colgando de un hilo entre x0 y x1; top es la y del hilo (PDF, y hacia arriba)."""
    unit = height / 58.0
    step = 58 * unit
    sag = 6 * unit
    width = x1 - x0
    c.saveState()
    c.setStrokeColor(Color(1, 1, 1, alpha=0.8))
    c.setLineWidth(0.5)
    p = c.beginPath()
    p.moveTo(x0, top)
    p.curveTo(x0 + width / 3, top - sag * 4 / 3, x0 + 2 * width / 3, top - sag * 4 / 3, x1, top)
    c.drawPath(p, stroke=1, fill=0)
    n = int(width // step) + 2
    for i in range(n):
        x = x0 + i * step + 3 * unit
        t = min(1, max(0, (x - x0 + 25 * unit) / width))
        y = top - sag * 4 * t * (1 - t)
        flag(c, x, y, unit, PAPEL[(i + offset) % len(PAPEL)], 2.5 if i % 2 else -2.5)
    c.restoreState()


# ---------- Página ----------

def draw_board(c, number, cells, page_w, page_h, trim, full_bleed):
    """Solo las 16 cartas, lo más grandes posible; trim = origen de la hoja Carta en la página."""
    tx, ty = trim
    # Imprenta: 5 mm de tolerancia de corte. Casa: 6.35 mm, dentro del área imprimible de la G3160.
    safe = 5 * mm if full_bleed else 6.35 * mm
    footer = 5 * mm
    gap = 3 * mm

    avail_h = LETTER_H - 2 * safe - footer
    avail_w = LETTER_W - 2 * safe
    card_h = min((avail_h - 3 * gap) / 4, ((avail_w - 3 * gap) / 4) / CARD_RATIO)
    card_w = card_h * CARD_RATIO
    grid_w = 4 * card_w + 3 * gap
    grid_h = 4 * card_h + 3 * gap
    gx = tx + (LETTER_W - grid_w) / 2
    grid_top = ty + safe + footer + grid_h + (avail_h - grid_h) / 2

    for i, card_no in enumerate(cells):
        r, col = divmod(i, 4)
        x = gx + col * (card_w + gap)
        y = grid_top - (r + 1) * card_h - r * gap
        c.saveState()
        p = c.beginPath()
        p.roundRect(x, y, card_w, card_h, 2.4 * mm)
        c.clipPath(p, stroke=0, fill=0)
        c.drawImage(prepared(card_no), x, y, card_w, card_h)
        c.restoreState()

    # Número discreto al pie para identificar y ordenar las tablas.
    c.setFillColor(INK2)
    c.setFont("Roboto-Medium", 8)
    c.drawCentredString(tx + LETTER_W / 2, ty + safe + 1.2 * mm, f"Tabla {number:02d} de {N_BOARDS}")
    return card_w, card_h

def render(path, layout, full_bleed):
    page_w = LETTER_W + (2 * BLEED if full_bleed else 0)
    page_h = LETTER_H + (2 * BLEED if full_bleed else 0)
    trim = (BLEED, BLEED) if full_bleed else (0, 0)
    c = canvas.Canvas(path, pagesize=(page_w, page_h))
    c.setTitle("Lotería · 24 tablas")
    c.setAuthor("Diego Ibarra")
    c.setSubject("Imprenta: Carta con 3 mm de rebase" if full_bleed else "Impresión en casa: Carta")
    size = None
    for n, cells in enumerate(layout, start=1):
        if full_bleed:
            c.setTrimBox((BLEED, BLEED, BLEED + LETTER_W, BLEED + LETTER_H))
            c.setBleedBox((0, 0, page_w, page_h))
        size = draw_board(c, n, cells, page_w, page_h, trim, full_bleed)
        c.showPage()
    c.save()
    return size


def main():
    ids = sorted(int(os.path.basename(p)[8:10]) for p in glob.glob(os.path.join(CARDS_DIR, "Loteria_*.jpg")))
    assert len(ids) == 54, len(ids)
    os.makedirs(OUTPUT, exist_ok=True)
    seed, layout = solve(ids)
    worst, counts = verify(layout, ids)
    print(f"semilla {seed}; máximo de cartas compartidas entre dos tablas: {worst}; "
          f"apariciones por carta: {sorted(set(counts.values()))}")
    with open(os.path.join(OUTPUT, "Loteria_Tableros_Lista.txt"), "w", encoding="utf-8") as f:
        f.write("Cartas por tabla (filas de izquierda a derecha, de arriba abajo)\n\n")
        for n, cells in enumerate(layout, start=1):
            rows = [" ".join(f"{x:02d}" for x in cells[r * 4:(r + 1) * 4]) for r in range(4)]
            f.write(f"Tabla {n:02d}:  " + "  |  ".join(rows) + "\n")
    for name, bleed in (("Loteria_Tableros_Imprenta.pdf", True), ("Loteria_Tableros_Casa.pdf", False)):
        w, h = render(os.path.join(OUTPUT, name), layout, bleed)
        print(f"{name}: carta de {w / mm:.1f} × {h / mm:.1f} mm")


if __name__ == "__main__":
    main()
