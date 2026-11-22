"""
The game's logo for the main menu (client/screen/MainMenuScreen.kt): "EVERY / TUNNEL" in chalk,
drawn by hand on a blackboard like the project icon (docs/media/logo.png): wobbly, doubled
strokes, a grain that crumbles the edges and a little dust, with a tunnel emblem (a stone arch, rails
and a flashlight) in the corner beside EVERY.

Run after changing anything here (from the repository root):

    python3 tools/gui/common/logo.py            previews only, in tools/gui/common/output/ (git-ignored)
    python3 tools/gui/common/logo.py --write    also writes the textures into the mod (committed)

The letters are not a font: each is a few strokes (GLYPHS, in a box one capital high), set with a
small wobble and tilt, then drawn with a chalk brush (a stroke mask, a grain that eats its edges). No
font is needed. Seeded, so every run is the same.

Textures (src/main/resources/assets/every_tunnel/textures/gui/), all 512 wide:
- logo_rest.png (512x256): the Y of EVERY.
- logo_tunnel.png (512x256): all of TUNNEL, where it stands in the logo.
- logo_ever.png (512x256): the E, V, E and R of EVERY and the tunnel emblem, where they stand in the logo.
  Together they are the logo. The menu draws them all; now and then it puts the rest and TUNNEL out and leaves
  EVER lit beside the emblem.
- logo_final.png (512x256): FINAL, in TUNNEL's place: the menu draws it instead of logo_tunnel.png, reading EVERY FINAL, once
  the player has quit to the menu before the Director's office (`common.client.MenuLogoOverride`).
- logo_chapter_2.png (512x64): "CHAPTER 2", drawn under the logo in Chapter 2's menu.
"""

import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[3]
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui"
OUT = Path(__file__).resolve().parent / "output"

WIDTH, HEIGHT = 512, 256
SUBTITLE_HEIGHT = 64
SS = 2                      # strokes are drawn at this many times the final size
CAP = 108                   # a capital's height, in final pixels
LEFT = 14
LINE_TOPS = [12, 138]
SPACING = 0.15              # between letters, in capitals
CHALK = (238, 235, 226)
DUST = (200, 200, 194)
SEED = 7771

# The letters of EVER: the first four of EVERY (the emblem goes with them)
EVER = {(0, 0), (0, 1), (0, 2), (0, 3)}


# ===========================================================================
# Letters: strokes in a box one capital high (y down), each with its own width in capitals

def arc(cx, cy, rx, ry, a0, a1, n=16):
    """Points of an ellipse arc from angle a0 to a1 (degrees; 0 right, 90 down)."""
    return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]


GLYPHS = {
    "E": (0.52, [[(0.50, 0.0), (0.05, 0.0), (0.05, 1.0), (0.50, 1.0)], [(0.05, 0.5), (0.42, 0.5)]]),
    "V": (0.62, [[(0.0, 0.0), (0.31, 1.0), (0.62, 0.0)]]),
    "R": (0.56, [[(0.05, 1.0), (0.05, 0.0), (0.30, 0.0)] + arc(0.30, 0.25, 0.25, 0.25, -90, 90) + [(0.05, 0.5)],
                 [(0.28, 0.5), (0.56, 1.0)]]),
    "Y": (0.62, [[(0.0, 0.0), (0.31, 0.52)], [(0.62, 0.0), (0.31, 0.52), (0.31, 1.0)]]),
    "T": (0.58, [[(0.0, 0.0), (0.58, 0.0)], [(0.29, 0.0), (0.29, 1.0)]]),
    "U": (0.56, [[(0.05, 0.0), (0.05, 0.62)] + arc(0.28, 0.62, 0.23, 0.38, 180, 0, 14) + [(0.51, 0.0)]]),
    "N": (0.60, [[(0.05, 1.0), (0.05, 0.0), (0.55, 1.0), (0.55, 0.0)]]),
    "L": (0.46, [[(0.05, 0.0), (0.05, 1.0), (0.46, 1.0)]]),
    "C": (0.56, [arc(0.30, 0.5, 0.26, 0.5, -45, -315, 18)]),
    "H": (0.60, [[(0.05, 0.0), (0.05, 1.0)], [(0.55, 0.0), (0.55, 1.0)], [(0.05, 0.5), (0.55, 0.5)]]),
    "A": (0.62, [[(0.0, 1.0), (0.31, 0.0), (0.62, 1.0)], [(0.12, 0.66), (0.50, 0.66)]]),
    "P": (0.52, [[(0.05, 1.0), (0.05, 0.0), (0.28, 0.0)] + arc(0.28, 0.25, 0.22, 0.25, -90, 90) + [(0.05, 0.5)]]),
    "F": (0.50, [[(0.05, 1.0), (0.05, 0.0), (0.48, 0.0)], [(0.05, 0.5), (0.40, 0.5)]]),
    "I": (0.34, [[(0.17, 0.0), (0.17, 1.0)], [(0.03, 0.0), (0.31, 0.0)], [(0.03, 1.0), (0.31, 1.0)]]),
    "2": (0.56, [arc(0.28, 0.27, 0.23, 0.27, -180, 35, 16) + [(0.05, 1.0), (0.55, 1.0)]]),
}


def serifs(strokes, half):
    """Short ticks (`half` a capital to each side) across the ends of the strokes that stop at the top or bottom of the letter."""
    ticks = []
    for stroke in strokes:
        for (x, y), (nx, ny) in ((stroke[0], stroke[1]), (stroke[-1], stroke[-2])):
            if (y < 0.02 or y > 0.98) and abs(ny - y) > 0.05:
                ticks.append([(x - half, y), (x + half, y)])
    return ticks


# ===========================================================================
# The chalk brush

class Chalk:
    """A stroke mask at SS times the size, turned into chalk: crumbled edges, grain, a little dust."""

    def __init__(self, width, height, rng, np_rng):
        self.w, self.h = width, height
        self.rng, self.np = rng, np_rng
        self.hi = Image.new("L", (width * SS, height * SS), 0)
        self.draw = ImageDraw.Draw(self.hi)

    def stroke(self, points, width, wobble, passes=2):
        """A polyline in final pixels, drawn `passes` times with its own wobble each: a hand going over it again."""
        if len(points) < 2:
            return
        # Resample every ~4 px so the wobble can bend long straight strokes
        path = []
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            n = max(1, int(math.hypot(x1 - x0, y1 - y0) / 4))
            path += [(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n) for i in range(n)]
        path.append(points[-1])
        for p in range(passes):
            phases = [self.rng.uniform(0, 6.28) for _ in range(4)]
            amp = wobble * (1.0 if p == 0 else 0.8)
            pressure = [self.rng.uniform(0.75, 1.0) for _ in range(3)]
            pts = []
            for i, (x, y) in enumerate(path):
                t = i / max(1, len(path) - 1)
                dx = amp * (math.sin(i * 0.31 + phases[0]) + 0.5 * math.sin(i * 0.77 + phases[1]))
                dy = amp * (math.sin(i * 0.27 + phases[2]) + 0.5 * math.sin(i * 0.83 + phases[3]))
                pts.append((x + dx + (p * 0.9 if passes > 1 else 0), y + dy - (p * 0.7 if passes > 1 else 0), t))
            for (xa, ya, ta), (xb, yb, tb) in zip(pts, pts[1:]):
                press = pressure[0] + (pressure[1] - pressure[0]) * math.sin(math.pi * ta) + 0.1 * pressure[2]
                wd = max(1.0, width * press * (1.0 if p == 0 else 0.7)) * SS
                self.draw.line([(xa * SS, ya * SS), (xb * SS, yb * SS)], fill=255, width=int(round(wd)))
                r = wd / 2
                self.draw.ellipse([xb * SS - r, yb * SS - r, xb * SS + r, yb * SS + r], fill=255)

    def render(self):
        """The finished layer as RGBA at final size."""
        mask = np.asarray(self.hi.resize((self.w, self.h), Image.BOX), dtype=np.float32) / 255.0
        fine = self.np.random((self.h, self.w)).astype(np.float32)
        coarse = np.asarray(Image.fromarray((self.np.random((self.h // 4 + 1, self.w // 4 + 1)) * 255).astype(np.uint8))
                            .resize((self.w, self.h), Image.BICUBIC), dtype=np.float32) / 255.0
        grain = 0.65 * fine + 0.35 * coarse
        ink = mask * (0.55 + 0.9 * grain)
        alpha = np.clip((ink - 0.40) * 3.2, 0.0, 1.0)
        # A little dust: faint specks near the strokes
        near = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))
                          .filter(ImageFilter.GaussianBlur(4)), dtype=np.float32) / 255.0
        specks = (self.np.random((self.h, self.w)) < 0.012 * near).astype(np.float32) * self.np.uniform(0.25, 0.7, (self.h, self.w))
        alpha = np.clip(np.maximum(alpha * (0.88 + 0.12 * grain), specks.astype(np.float32)), 0.0, 1.0)
        shade = 0.84 + 0.16 * grain
        rgba = np.zeros((self.h, self.w, 4), dtype=np.uint8)
        for c in range(3):
            rgba[..., c] = np.clip(np.where(specks > alpha * 0.9, DUST[c], CHALK[c]) * shade, 0, 255)
        rgba[..., 3] = (alpha * 255).astype(np.uint8)
        return Image.fromarray(rgba, "RGBA")


# ===========================================================================
# The logo

STYLES = {
    # name: (stroke width in capitals, wobble in px, passes, serif half-length in capitals, 0 for none)
    "fine": (0.062, 1.2, 2, 0.085),
    "bold": (0.115, 1.5, 2, 0.12),
}


def letter_strokes(ch, x, top, cap, rng, serif):
    """A letter's strokes in pixels: tilted, scaled and set on a baseline that wander a little, the strokes running past their corners."""
    width, strokes = GLYPHS[ch]
    strokes = strokes + (serifs(strokes, serif) if serif else [])
    tilt = math.radians(rng.uniform(-2.4, 2.4))
    scale = rng.uniform(0.95, 1.05)
    dy = rng.uniform(-2.5, 2.5)
    cx, cy = width / 2, 0.5
    out = []
    for stroke in strokes:
        stroke = list(stroke)
        for end, nxt in ((0, 1), (-1, -2)):                    # overshoot: a hand does not stop on the corner
            (x0, y0), (x1, y1) = stroke[end], stroke[nxt]
            d = math.hypot(x1 - x0, y1 - y0) or 1.0
            o = rng.uniform(0.0, 0.045)
            stroke[end] = (x0 - (x1 - x0) / d * o, y0 - (y1 - y0) / d * o)
        pts = []
        for px, py in stroke:
            rx, ry = (px - cx) * scale, (py - cy) * scale
            tx = rx * math.cos(tilt) - ry * math.sin(tilt) + cx
            ty = rx * math.sin(tilt) + ry * math.cos(tilt) + cy
            pts.append((x + tx * cap, top + dy + ty * cap))
        out.append(pts)
    return out, width * cap


def emblem_strokes(left, top, size):
    """A tunnel in a few strokes, like the project icon's: a stone arch between two heaps, the rails going in, a flashlight above, its rays."""
    u = lambda px, py: (left + px * size, top + py * size * 1.18)
    strokes = []
    strokes.append([u(0.08, 1.0), u(0.08, 0.72)] + [u(*p) for p in arc(0.5, 0.72, 0.42, 0.30, 180, 360, 16)] + [u(0.92, 0.72), u(0.92, 1.0)])   # outer arch
    strokes.append([u(0.26, 1.0), u(0.26, 0.76)] + [u(*p) for p in arc(0.5, 0.76, 0.24, 0.24, 180, 360, 12)] + [u(0.74, 0.76), u(0.74, 1.0)])   # inner arch
    for a in range(200, 345, 28):                                                                                       # the stones
        c, sn = math.cos(math.radians(a)), math.sin(math.radians(a))
        strokes.append([u(0.5 + c * 0.24, 0.76 + sn * 0.24), u(0.5 + c * 0.42, 0.72 + sn * 0.30)])
    strokes.append([u(0.38, 1.0), u(0.47, 0.68)])                                                                       # rails
    strokes.append([u(0.62, 1.0), u(0.53, 0.68)])
    for y in (0.92, 0.82):
        strokes.append([u(0.40 - (1.0 - y) * 0.10, y), u(0.60 + (1.0 - y) * 0.10, y)])
    strokes.append([u(0.0, 1.0), u(1.0, 1.0)])                                                                          # ground
    for sx in (0, 1):                                                                                                   # a heap each side
        f = (lambda x: x) if sx == 0 else (lambda x: 1.0 - x)
        strokes.append([u(f(0.0), 1.0), u(f(0.0), 0.86), u(f(0.12), 0.86)])
        strokes.append([u(f(0.03), 0.86), u(f(0.03), 0.72), u(f(0.12), 0.72), u(f(0.12), 0.86)])
    strokes.append([u(0.40, 0.30), u(0.40, 0.18), u(0.60, 0.18), u(0.60, 0.30), u(0.40, 0.30)])                          # flashlight: head
    strokes.append([u(0.45, 0.30), u(0.45, 0.46), u(0.55, 0.46), u(0.55, 0.30)])                                         # body
    strokes.append([u(0.5, 0.12), u(0.5, 0.02)])                                                                         # rays
    strokes.append([u(0.36, 0.15), u(0.28, 0.07)])
    strokes.append([u(0.64, 0.15), u(0.72, 0.07)])
    return strokes


def build(style, seed=SEED):
    """The layers for a style: (rest, ever, tunnel, final, chapter 2 line)."""
    stroke_w, wobble, passes, serif = STYLES[style]
    rng = random.Random(seed)
    nprng = np.random.default_rng(seed)
    rest, ever, tunnel = (Chalk(WIDTH, HEIGHT, rng, nprng) for _ in range(3))
    for line, (text, top) in enumerate(zip(("EVERY", "TUNNEL"), LINE_TOPS)):
        x = LEFT
        for i, ch in enumerate(text):
            strokes, advance = letter_strokes(ch, x, top, CAP, rng, serif)
            layer = ever if (line, i) in EVER else (tunnel if line == 1 else rest)
            for stroke in strokes:
                layer.stroke(stroke, stroke_w * CAP, wobble, passes)
            x += advance + SPACING * CAP
    size = 100
    for stroke in emblem_strokes(WIDTH - size - 6, 3, size):
        ever.stroke(stroke, max(2.6, stroke_w * size * 0.7), 0.8, passes)
    sub = Chalk(WIDTH, SUBTITLE_HEIGHT, rng, nprng)
    sub_cap, x = 38, LEFT + 4
    for ch in "CHAPTER 2":
        if ch == " ":
            x += 0.45 * sub_cap
            continue
        strokes, advance = letter_strokes(ch, x, 12, sub_cap, rng, serif)
        for stroke in strokes:
            sub.stroke(stroke, max(2.2, stroke_w * sub_cap * 1.1), wobble * 0.6, passes)
        x += advance + 0.34 * sub_cap
    # FINAL, with a generator of its own so that the other layers come out as they always did
    final_rng, final_np = random.Random(seed + 1), np.random.default_rng(seed + 1)
    final = Chalk(WIDTH, HEIGHT, final_rng, final_np)
    x = LEFT
    for ch in "FINAL":
        strokes, advance = letter_strokes(ch, x, LINE_TOPS[1], CAP, final_rng, serif)
        for stroke in strokes:
            final.stroke(stroke, stroke_w * CAP, wobble, passes)
        x += advance + SPACING * CAP
    return rest.render(), ever.render(), tunnel.render(), final.render(), sub.render()


def previews(style, rest, ever, tunnel, final, sub):
    OUT.mkdir(exist_ok=True)
    black = lambda size: Image.new("RGBA", size, (0, 0, 0, 255))
    both = black((WIDTH, HEIGHT + SUBTITLE_HEIGHT))
    both.alpha_composite(rest)
    both.alpha_composite(tunnel)
    both.alpha_composite(ever)
    both.alpha_composite(sub, (0, HEIGHT))
    egg = black((WIDTH, HEIGHT))
    egg.alpha_composite(ever)
    every_final = black((WIDTH, HEIGHT + SUBTITLE_HEIGHT))
    for layer in (rest, final, ever):
        every_final.alpha_composite(layer)
    sheet = black((WIDTH * 3 + 16, HEIGHT + SUBTITLE_HEIGHT))
    sheet.alpha_composite(both)
    sheet.alpha_composite(egg, (WIDTH + 8, 0))
    sheet.alpha_composite(every_final, (WIDTH * 2 + 16, 0))
    sheet.save(OUT / f"logo_preview_{style}.png")


def main():
    write = "--write" in sys.argv
    styles = [a for a in sys.argv[1:] if a in STYLES] or ["fine"]
    for style in styles:
        rest, ever, tunnel, final, sub = build(style)
        previews(style, rest, ever, tunnel, final, sub)
        print(f"preview: tools/gui/common/output/logo_preview_{style}.png")
        if write:
            TEXTURES.mkdir(parents=True, exist_ok=True)
            rest.save(TEXTURES / "logo_rest.png")
            ever.save(TEXTURES / "logo_ever.png")
            tunnel.save(TEXTURES / "logo_tunnel.png")
            final.save(TEXTURES / "logo_final.png")
            sub.save(TEXTURES / "logo_chapter_2.png")
            print("wrote logo_rest.png, logo_ever.png, logo_tunnel.png, logo_final.png, logo_chapter_2.png")


if __name__ == "__main__":
    main()
