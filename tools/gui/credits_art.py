"""
The final credits' four drawings (client/screen/FinalCreditsScreen.kt), by the project's author, kept full size in
tools/gui/credits/ (white lines, and the archive's purple, on black): guard.webp (the night guard and his
flashlight), terminal.webp (a CRT on a desk, a badge falling), observer.webp (the cube-headed figure among books),
archive.webp (shelved archive boxes, one burst open). Shrunk to at most SIDE pixels a side and written as PNGs in a
small palette (lines on black need few colours).

Also prints the credits' purple, sampled from the archive's boxes (FinalCreditsScreen's PURPLE).

Run after changing a drawing (from the repository root):   python3 tools/gui/credits_art.py

Writes src/main/resources/assets/every_tunnel/textures/gui/credits/<name>.png (committed).
"""

from collections import Counter
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
SOURCES = HERE / "credits"
OUT = HERE.parents[1] / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui" / "credits"
NAMES = ("guard", "terminal", "observer", "archive")
SIDE = 1024
COLOURS = 64


def purple(img):
    """The boxes' purple: the commonest strong purple, its shades averaged."""
    counts = Counter()
    for r, g, b in img.convert("RGB").get_flattened_data():
        if b > 150 and r > 100 and g < 130 and b - g > 60:
            counts[(r // 8 * 8, g // 8 * 8, b // 8 * 8)] += 1
    top = [c for c, _ in counts.most_common(5)]
    return tuple(sum(c[i] for c in top) // len(top) for i in range(3))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name in NAMES:
        img = Image.open(SOURCES / f"{name}.webp").convert("RGB")
        img.thumbnail((SIDE, SIDE), Image.LANCZOS)
        small = img.quantize(COLOURS, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        small.save(OUT / f"{name}.png", optimize=True)
        print(f"{name}: {img.size[0]}x{img.size[1]}, {(OUT / f'{name}.png').stat().st_size // 1024} KB")
    r, g, b = purple(Image.open(SOURCES / "archive.webp"))
    print(f"purple: 0x{r:02X}{g:02X}{b:02X}")


if __name__ == "__main__":
    main()
