"""
The dial of the safes in Level 4's Suites (client/screen/SafeScreen.kt): dark steel, a brass rim, 40 marks
and a number every 5, to be turned under the mark at the top. Number 0 is at the top of the picture, and
they go clockwise. The numbers are only there to be looked at: finding the right ones is done by ear.

Run after changing anything here (from the repository root):   python3 tools/gui/safe_dial.py

Writes (committed) src/main/resources/assets/every_tunnel/textures/gui/safe_dial.png, SIZE x SIZE.
"""

import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
GUI = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui"

SIZE = 128
NUMBERS = 40                             # SafeDial.NUMBERS
FONT = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111",
        "5": "111100111001111"}


def number(text):
    """A number's picture, upright, on clear."""
    img = Image.new("RGBA", (len(text) * 4 - 1, 5), (0, 0, 0, 0))
    for k, ch in enumerate(text):
        for i, bit in enumerate(FONT[ch]):
            if bit == "1":
                img.putpixel((k * 4 + i % 3, i // 3), (222, 216, 196, 255))
    return img


def main():
    rng = random.Random("safe-dial")
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    c = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x + 0.5 - c, y + 0.5 - c)
            if r > 63.5:
                continue
            if r > 60:
                col = (150, 112, 48)
            elif r > 26:
                col = (34, 36, 40)
            elif r > 24:
                col = (150, 112, 48)
            else:
                col = (60, 62, 66)
            shade = rng.randint(-3, 3)
            img.putpixel((x, y), tuple(v + shade for v in col) + (255,))
    for k in range(NUMBERS):
        angle = math.radians(k * 360 / NUMBERS)
        length = 7 if k % 5 == 0 else 4
        for d in range(length * 2):
            r = 59.5 - d / 2
            img.putpixel((int(c + r * math.sin(angle)), int(c - r * math.cos(angle))), (214, 208, 190, 255))
    for k in range(0, NUMBERS, 5):
        degrees = k * 360 / NUMBERS
        glyph = number(str(k)).resize((len(str(k)) * 8 - 2, 10), Image.NEAREST).rotate(-degrees, Image.NEAREST, expand=True)
        angle = math.radians(degrees)
        x = round(c + 42 * math.sin(angle) - glyph.width / 2)
        y = round(c - 42 * math.cos(angle) - glyph.height / 2)
        img.alpha_composite(glyph, (x, y))
    GUI.mkdir(parents=True, exist_ok=True)
    img.save(GUI / "safe_dial.png")


if __name__ == "__main__":
    main()
