"""Detecta el área de cada carta (sin franjas claras ni marco negro), la normaliza y empaqueta.

Uso (desde la raíz del repo):
  python tools/cards_tool.py build                 # cards/source/*.jpg -> cards/normalized + build/Cards_normalizadas.zip
  python tools/cards_tool.py check Loteria_05.jpg  # hoja de contacto con el recuadro detectado
  python tools/cards_tool.py sheet-out             # hoja de contacto de las 54 cartas normalizadas
"""
import glob
import os
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(REPO, "cards", "source")       # capturas originales (no versionadas)
OUT = os.path.join(REPO, "cards", "normalized")   # 54 cartas limpias de 1500x2125
PREVIEW = os.path.join(REPO, "build", "preview")  # hojas de contacto para revisar
TARGET = (1500, 2125)  # ~0.706, mediana de las cartas


def edge(frac):
    """Desde la orilla: salta franja clara opcional, luego el marco oscuro."""
    i, n = 0, len(frac)
    while i < 120 and frac[i] < 0.85:
        i += 1
    if i >= 120:
        return 0
    while i < n // 4 and frac[i] > 0.15:
        i += 1
    return i


def detect(path):
    img = Image.open(path).convert("RGB")
    a = np.asarray(img, dtype=np.int16)
    h, w = a.shape[:2]
    dark = a.max(axis=2) < 40
    rows = dark[:, int(w * .1):int(w * .9)].mean(1)
    cols = dark[int(h * .1):int(h * .9), :].mean(0)
    t, b = edge(rows), h - edge(rows[::-1])
    l, r = edge(cols), w - edge(cols[::-1])
    return img, (l + 3, t + 3, r - 3, b - 3)


def number(path):
    return int(os.path.splitext(os.path.basename(path))[0].split("_")[-1])


def files():
    return sorted(glob.glob(os.path.join(SRC, "*.jpg")), key=number)


def sheet(names, out, boxes=True, cols=6, tw=240, th=340):
    rows = (len(names) + cols - 1) // cols
    s = Image.new("RGB", (cols * (tw + 10) + 10, rows * (th + 10) + 10), (255, 0, 255))
    for k, p in enumerate(names):
        img, box = detect(p) if boxes else (Image.open(p).convert("RGB"), None)
        if box:
            ImageDraw.Draw(img).rectangle(box, outline=(0, 255, 0), width=10)
        img.thumbnail((tw, th))
        s.paste(img, (10 + (k % cols) * (tw + 10), 10 + (k // cols) * (th + 10)))
    os.makedirs(PREVIEW, exist_ok=True)
    s.save(os.path.join(PREVIEW, out))


def build():
    os.makedirs(OUT, exist_ok=True)
    tr = TARGET[0] / TARGET[1]
    for p in files():
        img, (l, t, r, b) = detect(p)
        w, h = r - l, b - t
        # Recorte centrado mínimo para igualar proporción (solo quita margen decorativo)
        if w / h > tr:
            nw = round(h * tr); l += (w - nw) // 2; r = l + nw
        else:
            nh = round(w / tr); t += (h - nh) // 2; b = t + nh
        card = img.crop((l, t, r, b)).resize(TARGET, Image.LANCZOS)
        card.save(os.path.join(OUT, f"Loteria_{number(p):02d}.jpg"), quality=92, optimize=True)
    zp = os.path.join(REPO, "build", "Cards_normalizadas.zip")
    os.makedirs(os.path.dirname(zp), exist_ok=True)
    with zipfile.ZipFile(zp, "w", zipfile.ZIP_STORED) as z:
        for p in sorted(glob.glob(os.path.join(OUT, "*.jpg"))):
            z.write(p, os.path.basename(p))
    print(zp)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "check":
        sheet([os.path.join(SRC, n) for n in sys.argv[2:]], "check.png")
    elif cmd == "build":
        build()
    elif cmd == "sheet-out":
        sheet(sorted(glob.glob(os.path.join(OUT, "*.jpg"))), "out.png", boxes=False, cols=9, tw=160, th=227)
