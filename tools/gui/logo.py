"""
The game's logo for the main menu (client/screen/MainMenuScreen.kt): "EVERY / TUNNEL" in
classic Roman capitals with serifs, off-white and worn, as pixel art on two left-aligned lines.
Inspired by a high-contrast serif title (chalky, scratched white letters on black).

Run after changing anything here (from the repository root):   python3 tools/gui/logo.py

Writes src/main/resources/assets/every_tunnel/textures/gui/logo.png (256x128, committed;
the menu draws it 2:1, left-aligned) and tools/gui/output/logo_preview.png (git-ignored;
enlarged, on black).

How: each line is set in a display serif at 8x size, scaled down to its pixel height and
cut to hard pixels (alpha in or out), so the serifs stay as crisp pixel steps. Then the
letters are worn: lit from above (lighter top rows, a darker bottom edge), chips bitten
out of the edges, and fine diagonal scratches inside. Seeded, so every run is the same.

The font is the first of FONTS found on this system (Noto Serif Display is the one the
committed logo was made with); the PNG is committed, so the font is only needed to change it.
"""

from pathlib import Path
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui" / "logo.png"
OUT = Path(__file__).resolve().parent / "output"

FONTS = [
    "/usr/share/fonts/noto/NotoSerifDisplay-Medium.ttf",
    "/usr/share/fonts/noto/NotoSerifDisplay-Regular.ttf",
    "/usr/share/fonts/truetype/noto/NotoSerifDisplay-Medium.ttf",
    "/usr/share/fonts/gsfonts/C059-Roman.otf",
    "/usr/share/fonts/TTF/DejaVuSerif.ttf",
]

WIDTH, HEIGHT = 256, 128
LINES = ["EVERY", "TUNNEL"]
CAP_HEIGHT = 40          # pixels from the baseline to the top of a capital
LINE_TOPS = [10, 68]     # where each line's capitals start
LEFT = 2
LETTER_SPACING = 2       # extra pixels between letters, at final size
SUPERSAMPLE = 8
CUT = 0.3                # alpha above this is ink (low, so the hairlines survive)
THICKEN = 5              # at 8x size: a max filter that keeps the thinnest strokes a pixel wide

TOP_COLOUR = (240, 236, 228)
BOTTOM_COLOUR = (196, 192, 186)
EDGE_COLOUR = (128, 126, 124)
SCRATCH_COLOUR = (150, 148, 146)
SEED = 7771


def find_font():
    for path in FONTS:
        if Path(path).is_file():
            return path
    raise SystemExit("no serif font found; add one to FONTS")


def line_mask(text, font_path):
    """The line as a hard-edged mask at final size, its capitals CAP_HEIGHT tall."""
    s = SUPERSAMPLE
    font = ImageFont.truetype(font_path, 100 * s)
    # Scale the font so a capital is exactly CAP_HEIGHT (measured on "E").
    box = font.getbbox("E")
    cap = box[3] - box[1]
    font = ImageFont.truetype(font_path, int(100 * s * CAP_HEIGHT * s / cap))
    box = font.getbbox("E")
    top_offset = box[1]
    widths = [font.getbbox(ch)[2] - font.getbbox(ch)[0] for ch in text]
    total = sum(widths) + LETTER_SPACING * s * (len(text) - 1) + 4 * s
    big = Image.new("L", (total, (CAP_HEIGHT + 4) * s), 0)
    draw = ImageDraw.Draw(big)
    x = 0
    for ch, w in zip(text, widths):
        left = font.getbbox(ch)[0]
        draw.text((x - left, -top_offset), ch, fill=255, font=font)
        x += w + LETTER_SPACING * s
    big = big.filter(ImageFilter.MaxFilter(THICKEN))
    small = big.resize((total // s, CAP_HEIGHT + 4), Image.LANCZOS)
    return small.point(lambda a: 255 if a >= CUT * 255 else 0)


def wear(image, rng):
    """Light from above, a darker bottom edge, chips out of the edges, scratches inside."""
    px = image.load()
    w, h = image.size
    ink = lambda x, y: 0 <= x < w and 0 <= y < h and px[x, y][3] > 0

    # Gradient per line: top rows lighter. Rows are coloured by their place in the line.
    for y in range(h):
        line_top = max(t for t in LINE_TOPS if t <= y) if y >= LINE_TOPS[0] else LINE_TOPS[0]
        t = min(1.0, max(0.0, (y - line_top) / CAP_HEIGHT))
        colour = tuple(int(a + (b - a) * t) for a, b in zip(TOP_COLOUR, BOTTOM_COLOUR))
        for x in range(w):
            if ink(x, y):
                n = rng.randint(-6, 6)
                px[x, y] = tuple(max(0, min(255, c + n)) for c in colour) + (255,)

    # A darker lower-right edge, like engraved letters catching the light from the top left.
    for y in range(h):
        for x in range(w):
            if ink(x, y) and (not ink(x, y + 1) or not ink(x + 1, y)) and ink(x - 1, y - 1):
                px[x, y] = EDGE_COLOUR + (255,)

    # Chips: a few edge pixels bitten out, sometimes two; only where the stroke is thick
    # enough (most of the 3x3 around it is ink), so hairlines and serifs are never cut.
    solid = lambda x, y: sum(ink(x + dx, y + dy) for dx in (-1, 0, 1) for dy in (-1, 0, 1)) >= 6
    edges = [(x, y) for y in range(h) for x in range(w)
             if ink(x, y) and solid(x, y)
             and any(not ink(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    for x, y in rng.sample(edges, len(edges) // 14):
        px[x, y] = (0, 0, 0, 0)
        if rng.random() < 0.3:
            nx, ny = x + rng.choice((-1, 1)), y + rng.choice((-1, 0, 1))
            if ink(nx, ny) and solid(nx, ny):
                px[nx, ny] = (0, 0, 0, 0)

    # Scratches: short diagonal strokes of grey across the letters.
    for _ in range(26):
        x, y = rng.randrange(w), rng.randrange(h)
        length = rng.randint(3, 9)
        step = rng.choice((-1, 1))
        for i in range(length):
            sx, sy = x + i, y + i * step // 2
            if ink(sx, sy) and ink(sx - 1, sy) and ink(sx + 1, sy):
                px[sx, sy] = SCRATCH_COLOUR + (255,)


def main():
    rng = random.Random(SEED)
    font = find_font()
    image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    for text, top in zip(LINES, LINE_TOPS):
        mask = line_mask(text, font)
        if LEFT + mask.width > WIDTH:
            raise SystemExit(f"\"{text}\" is {mask.width} pixels wide; make CAP_HEIGHT smaller")
        white = Image.new("RGBA", mask.size, TOP_COLOUR + (255,))
        image.paste(white, (LEFT, top), mask)
    wear(image, rng)

    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURE)
    OUT.mkdir(exist_ok=True)
    preview = Image.new("RGBA", (WIDTH * 3, HEIGHT * 3), (0, 0, 0, 255))
    preview.alpha_composite(image.resize((WIDTH * 3, HEIGHT * 3), Image.NEAREST))
    preview.save(OUT / "logo_preview.png")
    print(f"font {Path(font).name}; wrote {TEXTURE.relative_to(ROOT)} and {(OUT / 'logo_preview.png').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
