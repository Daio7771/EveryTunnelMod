"""
The paper of the player guide (client/screen/GuideScreen.kt): a worn white sheet, as pixel art.

Run after changing anything here (from the repository root):   python3 tools/gui/guide_paper.py

Writes (committed) into src/main/resources/assets/every_tunnel/textures/gui/:
- guide_paper.png (64x64): the paper itself, tiled over the whole sheet and its tabs, so it
  repeats with no seam. Off-white, faintly uneven, with paper fibres and a few specks.
- guide_stains.png (128x64): two stains drawn over the sheet here and there: a coffee ring
  (left half) and a damp blotch (right half). Black where there is no stain is never used:
  everything outside a stain is transparent.
Preview (git-ignored): tools/gui/output/guide_paper_preview.png, enlarged.

Seeded, so every run gives the same files. Low-frequency unevenness is made of waves with a
whole number of cycles across the tile, which is what makes the tile seamless.
"""

from pathlib import Path
import math
import random

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
GUI = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui"
OUT = Path(__file__).resolve().parent / "output"

SEED = 11
TILE = 64
# Worn white: a little warm, never pure white. Must read well under dark ink (GuideScreen.INK).
PAPER = (224, 222, 214)
UNEVEN = 5          # how far the slow waves move the brightness, per channel
GRAIN = 3           # per-pixel noise
FIBRES = 26         # short streaks, lighter or darker
SPECKS = 5          # dark dots

COFFEE = (118, 84, 52)
DAMP = (158, 150, 118)


def clamp(v):
    return max(0, min(255, int(round(v))))


def paper(rng):
    waves = [(rng.randint(1, 3), rng.randint(-2, 2), rng.uniform(0, math.tau), rng.uniform(0.4, 1.0)) for _ in range(5)]
    total = sum(w[3] for w in waves)
    image = Image.new("RGBA", (TILE, TILE))
    px = image.load()
    for y in range(TILE):
        for x in range(TILE):
            slow = sum(a * math.sin(math.tau * (kx * x + ky * y) / TILE + p) for kx, ky, p, a in waves) / total
            shade = slow * UNEVEN + rng.uniform(-GRAIN, GRAIN)
            # The slow waves also yellow the paper a little where it is darker.
            px[x, y] = (clamp(PAPER[0] + shade), clamp(PAPER[1] + shade), clamp(PAPER[2] + shade * 1.4), 255)

    for _ in range(FIBRES):
        x, y = rng.randrange(TILE), rng.randrange(TILE)
        length = rng.randint(2, 5)
        delta = rng.choice((-7, -5, 4, 6))
        for i in range(length):
            fx, fy = (x + i) % TILE, (y + (i // 3) * rng.choice((0, 1))) % TILE
            r, g, b, a = px[fx, fy]
            px[fx, fy] = (clamp(r + delta), clamp(g + delta), clamp(b + delta), a)

    for _ in range(SPECKS):
        x, y = rng.randrange(TILE), rng.randrange(TILE)
        r, g, b, a = px[x, y]
        px[x, y] = (clamp(r - 40), clamp(g - 42), clamp(b - 44), a)
    return image


def coffee_ring(rng, image, ox):
    """A ring left by a cup: a broken edge, thicker and darker on one side, a faint inside."""
    px = image.load()
    cx, cy, radius = ox + 32, 32, 22.5
    heavy_side = rng.uniform(0, math.tau)
    gaps = [rng.uniform(0, math.tau) for _ in range(3)]
    for y in range(64):
        for x in range(ox, ox + 64):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            angle = math.atan2(dy, dx)
            heavy = 0.5 + 0.5 * math.cos(angle - heavy_side)
            width = 1.2 + 1.6 * heavy
            alpha = 0.0
            if abs(r - radius) < width:
                alpha = 0.45 + 0.35 * heavy
                if any(abs(math.remainder(angle - g, math.tau)) < 0.18 for g in gaps):
                    alpha *= 0.25
            elif r < radius - width:
                alpha = 0.07 + 0.05 * heavy
            if alpha > 0:
                # Hard pixel steps, as pixel art: quarter levels only.
                alpha = round(alpha * 8) / 8 * rng.uniform(0.85, 1.0)
                px[x, y] = (*COFFEE, clamp(alpha * 255))


def damp_blotch(rng, image, ox):
    """A dried damp patch: an irregular soft area with a darker tide line."""
    px = image.load()
    cx, cy = ox + 32, 32
    # Uneven lobes (different counts, small sizes) so it never looks like a flower.
    lobes = [(k, rng.uniform(0, math.tau), rng.uniform(0.8, 2.6)) for k in (1, 2, 3, 5, 7)]
    for y in range(64):
        for x in range(ox, ox + 64):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            angle = math.atan2(dy, dx)
            edge = 19 + sum(a * math.sin(k * angle + p) for k, p, a in lobes)
            r = math.hypot(dx * 0.9, dy * 1.3)
            if r < edge - 1.5:
                alpha = 0.10 + 0.06 * (r / edge)
            elif r < edge:
                alpha = 0.32
            else:
                continue
            alpha = round(alpha * 8) / 8 if alpha > 0.2 else alpha
            px[x, y] = (*DAMP, clamp(alpha * 255 * rng.uniform(0.85, 1.0)))


def main():
    rng = random.Random(SEED)
    tile = paper(rng)
    stains = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    coffee_ring(rng, stains, 0)
    damp_blotch(rng, stains, 64)

    GUI.mkdir(parents=True, exist_ok=True)
    tile.save(GUI / "guide_paper.png")
    stains.save(GUI / "guide_stains.png")

    OUT.mkdir(exist_ok=True)
    sheet = Image.new("RGBA", (TILE * 3, TILE * 2))
    for ty in range(2):
        for tx in range(3):
            sheet.paste(tile, (tx * TILE, ty * TILE))
    sheet.alpha_composite(stains.crop((0, 0, 64, 64)), (8, 0))
    sheet.alpha_composite(stains.crop((64, 0, 128, 64)), (120, 64))
    preview = sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST)
    preview.save(OUT / "guide_paper_preview.png")
    print("wrote textures/gui/guide_paper.png, guide_stains.png and a preview")


if __name__ == "__main__":
    main()
