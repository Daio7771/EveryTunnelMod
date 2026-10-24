"""
The figure in the dark of the main menu (client/screen/MenuFigure.kt): the Pale man's silhouette
seen from far away, standing, arms hanging past the knees, the head hanging forwards on a long
neck. Barely lit from the left, the rest lost in the dark. The menu draws it very faint.

Run after changing anything here (from the repository root):   python3 tools/gui/menu_figure.py

Writes src/main/resources/assets/every_tunnel/textures/gui/menu_figure.png (32x128, committed)
and tools/gui/output/menu_figure_preview.png (git-ignored; enlarged, on black).

The figure is a mask, one row per pixel row, top to bottom: '#' body, 'l' lit edge (left),
's' shadow side (right), '.' transparent. 16 columns, drawn centred on the 32-pixel canvas,
each mask row stretched over 2 pixel rows (hence 64 rows of mask for 128 pixels).
"""

from pathlib import Path
import random

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui" / "menu_figure.png"
OUT = Path(__file__).resolve().parent / "output"

# The Pale man's skin (tools/entity/pale_man.py), darkened and desaturated: seen in the dark.
BODY = (150, 142, 138)
LIT = (196, 188, 182)
SHADOW = (92, 88, 90)

MASK = [
    "................",
    "................",
    "......###.......",
    ".....l####......",
    ".....l####......",
    ".....l###s......",
    "......###s......",
    "......l#s.......",
    ".......##.......",
    ".....l####s.....",
    "....l######s....",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#l####s#s...",
    "...l#.l###s.#s..",
    "...l#.l###s.#s..",
    "...l#.l###s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...l#.l#.#s.#s..",
    "...##.l#.#s.##..",
    "...##.l#.#s.##..",
    "....#.l#.#s..#..",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    "......l#.#s.....",
    ".....l##.#ss....",
    "................",
]


def main():
    assert all(len(row) == 16 for row in MASK) and len(MASK) == 64, "mask must be 16 x 64"
    rng = random.Random(7771)
    image = Image.new("RGBA", (32, 128), (0, 0, 0, 0))
    colours = {"#": BODY, "l": LIT, "s": SHADOW}
    for row, line in enumerate(MASK):
        for column, cell in enumerate(line):
            if cell not in colours:
                continue
            for dy in (0, 1):
                # A little noise, so it does not look like a flat cut-out.
                c = tuple(max(0, min(255, ch + rng.randint(-8, 8))) for ch in colours[cell])
                image.putpixel((8 + column, row * 2 + dy), c + (255,))
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURE)
    OUT.mkdir(exist_ok=True)
    preview = Image.new("RGBA", (32 * 4, 128 * 4), (0, 0, 0, 255))
    preview.alpha_composite(image.resize((32 * 4, 128 * 4), Image.NEAREST))
    preview.save(OUT / "menu_figure_preview.png")
    print(f"wrote {TEXTURE.relative_to(ROOT)} and {(OUT / 'menu_figure_preview.png').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
