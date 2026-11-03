"""
The painted font of the wall messages (client/render/WallMessageRenderer.kt): capitals written with a
brush in a hurry, uneven, with a drip now and then. Three drawings of each character, so the same letter is
not quite the same twice, in a private range of Unicode (core/wall/WallFont.kt). Drawn white: the game tints it.

Run after changing anything here (from the repository root):   python3 tools/gui/wall_font.py

Writes (committed) under src/main/resources/assets/every_tunnel/: textures/font/wall_paint.png and
font/wall_paint.json. Our own drawing: no licence to carry.
"""

import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"

ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'.,?!-"     # WallFont.ALPHABET
VARIANTS = 3
FIRST = 0xE000
CELL_W, CELL_H = 16, 24
HEIGHT_UNITS, ASCENT = 12, 9                             # a cell is 12 font units high, 9 over the baseline
BASELINE = 18                                            # in the cell's pixels: ASCENT * 2
STEP = 2                                                 # pixels per dot of the skeleton

GLYPHS = {
    "A": "01110 10001 10001 11111 10001 10001 10001", "B": "11110 10001 10001 11110 10001 10001 11110",
    "C": "01111 10000 10000 10000 10000 10000 01111", "D": "11110 10001 10001 10001 10001 10001 11110",
    "E": "11111 10000 10000 11110 10000 10000 11111", "F": "11111 10000 10000 11110 10000 10000 10000",
    "G": "01111 10000 10000 10011 10001 10001 01111", "H": "10001 10001 10001 11111 10001 10001 10001",
    "I": "11111 00100 00100 00100 00100 00100 11111", "J": "00111 00010 00010 00010 00010 10010 01100",
    "K": "10001 10010 10100 11000 10100 10010 10001", "L": "10000 10000 10000 10000 10000 10000 11111",
    "M": "10001 11011 10101 10101 10001 10001 10001", "N": "10001 11001 11001 10101 10011 10011 10001",
    "O": "01110 10001 10001 10001 10001 10001 01110", "P": "11110 10001 10001 11110 10000 10000 10000",
    "Q": "01110 10001 10001 10001 10101 10010 01101", "R": "11110 10001 10001 11110 10100 10010 10001",
    "S": "01111 10000 10000 01110 00001 00001 11110", "T": "11111 00100 00100 00100 00100 00100 00100",
    "U": "10001 10001 10001 10001 10001 10001 01110", "V": "10001 10001 10001 10001 01010 01010 00100",
    "W": "10001 10001 10001 10101 10101 11011 10001", "X": "10001 10001 01010 00100 01010 10001 10001",
    "Y": "10001 10001 01010 00100 00100 00100 00100", "Z": "11111 00001 00010 00100 01000 10000 11111",
    "0": "01110 10001 10011 10101 11001 10001 01110", "1": "00100 01100 00100 00100 00100 00100 01110",
    "2": "01110 10001 00001 00110 01000 10000 11111", "3": "11110 00001 00001 01110 00001 00001 11110",
    "4": "00010 00110 01010 10010 11111 00010 00010", "5": "11111 10000 11110 00001 00001 10001 01110",
    "6": "00110 01000 10000 11110 10001 10001 01110", "7": "11111 00001 00010 00100 01000 01000 01000",
    "8": "01110 10001 10001 01110 10001 10001 01110", "9": "01110 10001 10001 01111 00001 00010 01100",
    "'": "00100 00100 00000 00000 00000 00000 00000", ".": "00000 00000 00000 00000 00000 00000 00100",
    ",": "00000 00000 00000 00000 00000 00100 01000", "?": "01110 10001 00001 00110 00100 00000 00100",
    "!": "00100 00100 00100 00100 00100 00000 00100", "-": "00000 00000 00000 11111 00000 00000 00000",
}


def draw(img, ox, oy, char, rng):
    rows = GLYPHS[char].split()
    # The hand: the letter leans, and every dot lands a little off.
    lean = rng.choice((-1, 0, 0, 1))
    top = BASELINE - len(rows) * STEP + rng.randint(-1, 1)
    lowest = {}
    for r, row in enumerate(rows):
        shift = round(lean * (len(rows) - r) / len(rows))
        for c, bit in enumerate(row):
            if bit != "1":
                continue
            x = 2 + c * STEP + shift + (rng.randint(-1, 1) if rng.random() < 0.18 else 0)
            y = top + r * STEP + (1 if rng.random() < 0.12 else 0)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                if rng.random() < 0.93:
                    px, py = ox + x + dx, oy + y + dy
                    if ox <= px < ox + CELL_W and oy <= py < oy + CELL_H:
                        img.putpixel((px, py), (255, 255, 255, rng.randint(205, 255)))
            lowest[x] = max(lowest.get(x, 0), y + 1)
    # A drip from the bottom of a stroke, thinning as it runs.
    if lowest and char not in "'-" and rng.random() < 0.28:
        x = rng.choice(list(lowest))
        start = lowest[x] + 1
        for k in range(rng.randint(2, 5)):
            y = start + k
            if y < CELL_H:
                img.putpixel((ox + x, oy + y), (255, 255, 255, 235 - k * 28))


def main():
    rng = random.Random("wall-paint")
    columns = VARIANTS * 6
    rows = (len(ALPHABET) * VARIANTS + columns - 1) // columns
    img = Image.new("RGBA", (columns * CELL_W, rows * CELL_H), (0, 0, 0, 0))
    chars = [[] for _ in range(rows)]
    for k in range(len(ALPHABET) * VARIANTS):
        char = ALPHABET[k // VARIANTS]
        r, c = k // columns, k % columns
        draw(img, c * CELL_W, r * CELL_H, char, rng)
        chars[r].append(chr(FIRST + k))
    # Every row of the grid the same length: the rest of the last one is blank.
    for row in chars:
        row.extend("\0" for _ in range(columns - len(row)))
    (ASSETS / "textures" / "font").mkdir(parents=True, exist_ok=True)
    (ASSETS / "font").mkdir(parents=True, exist_ok=True)
    img.save(ASSETS / "textures" / "font" / "wall_paint.png")
    font = {"providers": [
        {"type": "space", "advances": {" ": 5}},
        {"type": "bitmap", "file": "every_tunnel:font/wall_paint.png", "height": HEIGHT_UNITS, "ascent": ASCENT,
         "chars": ["".join(row) for row in chars]},
    ]}
    (ASSETS / "font" / "wall_paint.json").write_text(json.dumps(font, indent=1) + "\n")


if __name__ == "__main__":
    main()
