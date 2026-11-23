"""
The banners of CURSEFORGE.md (the project's page on CurseForge): the header and one strip per section, in
chalk on a blackboard like the logo (tools/gui/common/logo.py, whose chalk brush and letters it uses).

Run after changing anything here (from the repository root):

    python3 tools/gui/common/curseforge_banners.py            previews only, in tools/gui/common/output/ (git-ignored)
    python3 tools/gui/common/curseforge_banners.py --write    also writes the PNGs into docs/media/ (committed)

Files (docs/media/): curseforge_header.png (1000x280: the logo, as the menu draws it, on the board) and
curseforge_<section>.png (1000x96: the section's title in chalk, with a line under it), for the sections in
SECTIONS. CURSEFORGE.md links them by their raw GitHub address, so they show once they are on `main`.

Only letters: logo.py's glyphs plus the few more this file adds (EXTRA). Seeded, so every run is the same.
"""

import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import logo  # noqa: E402  (the chalk brush and the letters)

ROOT = Path(__file__).resolve().parents[3]
MEDIA = ROOT / "docs" / "media"
OUT = Path(__file__).resolve().parent / "output"

WIDTH, SECTION_HEIGHT, HEADER_HEIGHT = 1000, 96, 280
CAP = 46                    # a section title's capital height, in pixels
SEED = 7771
BOARD = (24, 26, 26)

# (file name, title)
SECTIONS = [
    ("complex", "THE NONAME COMPLEX"),
    ("features", "FEATURES"),
    ("requirements", "REQUIREMENTS"),
    ("installing", "INSTALLING"),
    ("recommended", "RECOMMENDED"),
    ("good_to_know", "GOOD TO KNOW"),
    ("bugs", "BUGS AND SOURCE"),
    ("credits", "CREDITS"),
]

_a = logo.arc
# The letters logo.py does not have, in the same boxes (one capital high, y down)
EXTRA = {
    "O": (0.56, [_a(0.28, 0.5, 0.26, 0.5, 0, 360, 24)]),
    "Q": (0.56, [_a(0.28, 0.5, 0.26, 0.5, 0, 360, 24), [(0.34, 0.74), (0.55, 1.06)]]),
    "D": (0.56, [[(0.05, 0.0), (0.05, 1.0), (0.25, 1.0)] + _a(0.25, 0.5, 0.28, 0.5, 90, -90, 14) + [(0.25, 0.0), (0.05, 0.0)]]),
    "S": (0.56, [_a(0.28, 0.25, 0.22, 0.25, -40, -270, 14) + _a(0.28, 0.75, 0.24, 0.25, -90, 140, 14)[1:]]),
    "M": (0.64, [[(0.05, 1.0), (0.05, 0.0), (0.32, 0.62), (0.59, 0.0), (0.59, 1.0)]]),
    "G": (0.58, [_a(0.30, 0.5, 0.26, 0.5, -45, -315, 18), [(0.31, 0.55), (0.56, 0.55), (0.54, 0.82)]]),
    "B": (0.56, [[(0.05, 1.0), (0.05, 0.0), (0.28, 0.0)] + _a(0.28, 0.25, 0.22, 0.25, -90, 90, 10) + [(0.05, 0.5)],
                 [(0.05, 0.5), (0.32, 0.5)] + _a(0.32, 0.75, 0.22, 0.25, -90, 90, 10) + [(0.05, 1.0)]]),
    "K": (0.56, [[(0.05, 0.0), (0.05, 1.0)], [(0.5, 0.0), (0.05, 0.56)], [(0.2, 0.42), (0.52, 1.0)]]),
    "X": (0.60, [[(0.03, 0.0), (0.57, 1.0)], [(0.57, 0.0), (0.03, 1.0)]]),
    "W": (0.66, [[(0.0, 0.0), (0.17, 1.0), (0.33, 0.4), (0.49, 1.0), (0.66, 0.0)]]),
}
logo.GLYPHS.update(EXTRA)


def board(width, height, seed):
    """A blackboard: a dark grain, a faint cloud of old chalk dust and darker corners."""
    rng = np.random.default_rng(seed)
    grain = rng.random((height, width)).astype(np.float32)
    coarse = np.asarray(Image.fromarray((rng.random((height // 8 + 1, width // 8 + 1)) * 255).astype(np.uint8))
                        .resize((width, height), Image.BICUBIC), dtype=np.float32) / 255.0
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float32)
    edge = np.minimum.reduce([xx, width - 1 - xx, yy * 3.0, (height - 1 - yy) * 3.0]) / 80.0
    vignette = 0.72 + 0.28 * np.clip(edge, 0.0, 1.0)
    shade = (0.9 + 0.1 * grain) * (0.85 + 0.3 * coarse) * vignette
    rgb = np.stack([np.clip(c * shade, 0, 255) for c in BOARD], axis=-1).astype(np.uint8)
    return Image.fromarray(rgb, "RGB").convert("RGBA")


def title_layer(text, seed):
    """The title and a line under it, in chalk, on a transparent layer the size of a section strip."""
    rng, nprng = random.Random(seed), np.random.default_rng(seed)
    chalk = logo.Chalk(WIDTH, SECTION_HEIGHT, rng, nprng)
    stroke_w, wobble, passes, serif = logo.STYLES["fine"]
    top, x = 14, 28
    for ch in text:
        if ch == " ":
            x += 0.5 * CAP
            continue
        strokes, advance = logo.letter_strokes(ch, x, top, CAP, rng, serif)
        for stroke in strokes:
            chalk.stroke(stroke, max(2.4, stroke_w * CAP * 1.15), wobble * 0.7, passes)
        x += advance + 0.2 * CAP
    y = top + CAP + 14
    chalk.stroke([(24, y), (WIDTH * 0.45 + rng.uniform(-30, 30), y + rng.uniform(-1, 1)), (WIDTH - 24, y + rng.uniform(-2, 2))],
                 2.6, 1.4, 2)
    return chalk.render()


def section_banner(index, text):
    image = board(WIDTH, SECTION_HEIGHT, SEED + index)
    image.alpha_composite(title_layer(text, SEED + 100 + index))
    return image


def header_banner():
    """The logo as the menu draws it (EVERY, TUNNEL and the emblem), centred on the board."""
    rest, ever, tunnel, _final, _sub = logo.build("fine")
    image = board(WIDTH, HEADER_HEIGHT, SEED + 99)
    left, top = (WIDTH - logo.WIDTH) // 2, (HEADER_HEIGHT - logo.HEIGHT) // 2
    for layer in (rest, tunnel, ever):
        image.alpha_composite(layer, (left, top))
    return image


def main():
    write = "--write" in sys.argv
    OUT.mkdir(exist_ok=True)
    banners = [("curseforge_header.png", header_banner())]
    banners += [(f"curseforge_{name}.png", section_banner(i, title)) for i, (name, title) in enumerate(SECTIONS)]
    sheet = Image.new("RGBA", (WIDTH, sum(b.height + 8 for _, b in banners)), (60, 60, 60, 255))
    y = 0
    for name, image in banners:
        sheet.alpha_composite(image, (0, y))
        y += image.height + 8
        if write:
            MEDIA.mkdir(parents=True, exist_ok=True)
            image.convert("RGB").save(MEDIA / name, optimize=True)
    sheet.save(OUT / "curseforge_banners_preview.png")
    print("preview: tools/gui/common/output/curseforge_banners_preview.png")
    if write:
        print("wrote", ", ".join(name for name, _ in banners), "in docs/media/")


if __name__ == "__main__":
    main()
