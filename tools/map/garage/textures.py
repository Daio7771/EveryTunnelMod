"""
Procedural 16x16 pixel-art textures and the resource pack that applies them.

Every texture is drawn from a seed, so the same seed always gives the same pack. The
pack never replaces a vanilla texture file: it overrides the *blockstates* of the
stand-in blocks listed in STAND_INS (garage_generator.py) and points them at its own
models and textures (namespace `etg`). Only those blocks change, and only in worlds
that carry the pack (as resources.zip in the world folder).

Every texture also gets LabPBR maps (`_n`, `_s`, see pbr.py) for shader packs, from the
material that `material_for` gives its name.
"""

import io
import json
import math
import random
import zipfile

from PIL import Image, ImageDraw, ImageFont

from . import pbr
from .pbr import Material

NS = "etg"


# ---------------------------------------------------------------------------
# Pixel helpers

def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def clamp(v):
    return max(0, min(255, int(round(v))))


def new(base, alpha=255):
    return Image.new("RGBA", (16, 16), tuple(base) + (alpha,))


def px(img, x, y, color, t=1.0):
    """Blend `color` into pixel (x, y) by t (wraps around, so textures tile)."""
    x %= 16
    y %= 16
    old = img.getpixel((x, y))
    c = mix(old[:3], color[:3], t)
    img.putpixel((x, y), c + (old[3] if len(color) == 3 else color[3],))


def noise(img, rng, amount, grain=1):
    """Per-pixel brightness jitter (grain > 1 makes 2x2 clumps)."""
    for y in range(0, 16, grain):
        for x in range(0, 16, grain):
            d = rng.uniform(-amount, amount)
            for yy in range(y, min(16, y + grain)):
                for xx in range(x, min(16, x + grain)):
                    r, g, b, a = img.getpixel((xx, yy))
                    if a:
                        img.putpixel((xx, yy), (clamp(r + d), clamp(g + d), clamp(b + d), a))


def speckle(img, rng, count, color, t=1.0):
    for _ in range(count):
        px(img, rng.randrange(16), rng.randrange(16), color, t)


def blob(img, rng, color, radius, strength, cx=None, cy=None):
    """Soft irregular stain."""
    cx = rng.uniform(0, 16) if cx is None else cx
    cy = rng.uniform(0, 16) if cy is None else cy
    wobble = [rng.uniform(0.7, 1.3) for _ in range(8)]
    for y in range(16):
        for x in range(16):
            dx = min(abs(x - cx), 16 - abs(x - cx))
            dy = min(abs(y - cy), 16 - abs(y - cy))
            angle = int((math.atan2(dy, dx) + math.pi) / (2 * math.pi) * 8) % 8
            d = math.hypot(dx, dy) / (radius * wobble[angle])
            if d < 1.0:
                px(img, x, y, color, strength * (1.0 - d) ** 0.7 * rng.uniform(0.8, 1.0))


def crack(img, rng, color, length, t=0.85, start=None, branch=0.15):
    x, y = start if start else (rng.randrange(16), rng.randrange(16))
    dx, dy = rng.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
    for _ in range(length):
        px(img, x, y, color, t)
        if rng.random() < 0.35:
            dx, dy = rng.choice([(dx, dy), (dx, 0) if dx else (1, dy), (0, dy) if dy else (dx, 1)])
        if rng.random() < branch:
            crack(img, rng, color, length // 3, t * 0.8, (x, y), 0)
        x += dx
        y += dy


def streaks(img, rng, count, color, strength, top_to_bottom=True):
    """Vertical damp streaks running down (fading)."""
    for _ in range(count):
        x = rng.randrange(16)
        start = rng.randrange(0, 8)
        length = rng.randrange(5, 16)
        for i in range(length):
            y = start + i
            if y > 15:
                break
            fade = (i / length) if not top_to_bottom else 1 - i / length * 0.6
            px(img, x, y, color, strength * fade)
            if rng.random() < 0.25:
                x += rng.choice([-1, 1])


def gradient(img, color, top_strength, bottom_strength):
    for y in range(16):
        t = top_strength + (bottom_strength - top_strength) * y / 15
        for x in range(16):
            px(img, x, y, color, t)


def rect(img, x0, y0, x1, y1, color, t=1.0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, color, t)


# ---------------------------------------------------------------------------
# Palette

FLOOR = (84, 86, 89)
WALL = (150, 150, 144)
CEILING = (124, 124, 120)
PILLAR = (142, 142, 137)
PAINT_WHITE = (206, 206, 196)
PAINT_YELLOW = (212, 166, 38)
OIL = (24, 24, 28)
RUST = (120, 74, 40)
DAMP = (92, 96, 88)
DIRT = (82, 66, 48)


# ---------------------------------------------------------------------------
# Surfaces

def floor_tex(rng, kind):
    img = new(FLOOR)
    noise(img, rng, 6)
    noise(img, rng, 4, grain=2)
    speckle(img, rng, 10, (120, 122, 124), 0.6)      # aggregate
    speckle(img, rng, 8, (60, 62, 64), 0.6)
    if kind == "stained":
        blob(img, rng, (64, 62, 58), rng.uniform(4, 7), 0.5)
        blob(img, rng, (70, 70, 74), rng.uniform(3, 5), 0.4)
    elif kind == "cracked":
        crack(img, rng, (44, 45, 48), rng.randrange(10, 20))
    elif kind == "worn":
        blob(img, rng, (104, 106, 108), rng.uniform(4, 7), 0.45)
        speckle(img, rng, 6, (112, 112, 112), 0.7)
    return img


def patch_tex(rng):
    img = new((96, 97, 99))
    noise(img, rng, 5)
    speckle(img, rng, 12, (118, 118, 118), 0.6)
    for i in range(16):                                   # saw-cut edges of a repaired slab
        px(img, i, 0, (58, 58, 60), 0.6)
        px(img, 0, i, (58, 58, 60), 0.6)
    return img


def wall_tex(rng, kind):
    img = new(WALL)
    noise(img, rng, 5)
    speckle(img, rng, 10, (126, 126, 120), 0.5)          # pores
    if kind == "streaked":
        streaks(img, rng, 4, DAMP, 0.35)
    elif kind == "stained":
        blob(img, rng, (150, 132, 100), rng.uniform(4, 7), 0.35)
        streaks(img, rng, 2, (130, 112, 84), 0.3)
    return img


def wall_base_tex(rng, kind):
    img = new(WALL)
    noise(img, rng, 5)
    gradient(img, DAMP, 0.05, 0.55)                       # moisture rising from the floor
    speckle(img, rng, 8, (196, 198, 190), 0.6)            # efflorescence
    if kind == "dirty":
        gradient(img, (70, 66, 60), 0.0, 0.35)
        blob(img, rng, (80, 80, 76), 5, 0.4, cy=15)
    return img


def paint_band_tex(rng):
    img = new(PAINT_YELLOW)
    noise(img, rng, 7)
    for _ in range(5):                                    # chipped paint showing the concrete
        x, y = rng.randrange(16), rng.randrange(16)
        for dx, dy in [(0, 0), (1, 0), (0, 1)][: rng.randrange(1, 4)]:
            px(img, x + dx, y + dy, WALL, 0.9)
    gradient(img, (90, 80, 50), 0.0, 0.25)
    return img


def ceiling_tex(rng, kind):
    img = new(CEILING)
    noise(img, rng, 4)
    for y in (0, 8):                                       # formwork board joints
        for x in range(16):
            px(img, x, y, (104, 104, 100), 0.6)
    speckle(img, rng, 8, (106, 106, 102), 0.6)
    if kind == "stained":
        blob(img, rng, (128, 112, 88), rng.uniform(4, 6), 0.4)
    elif kind == "dark":
        blob(img, rng, (96, 96, 92), rng.uniform(5, 8), 0.5)
    return img


def pillar_tex(rng, kind):
    img = new(PILLAR)
    noise(img, rng, 5)
    for i in range(16):                                    # chamfered edges catch the light
        px(img, 0, i, (168, 168, 162), 0.6)
        px(img, 15, i, (110, 110, 106), 0.6)
    speckle(img, rng, 8, (120, 120, 116), 0.5)
    if kind == "scuffed":
        for _ in range(3):
            y = rng.randrange(8, 16)
            for x in range(rng.randrange(0, 6), rng.randrange(9, 16)):
                px(img, x, y, (96, 94, 90), 0.5)
    return img


def floor_line_tex(rng, color, axis):
    """Floor concrete with a painted line ~6 px wide (axis 'z' = north-south, 'x' = east-west)."""
    img = floor_tex(rng, "plain")
    for a in range(16):
        for b in range(5, 11):
            x, y = (b, a) if axis == "z" else (a, b)
            if rng.random() > 0.12:                         # worn spots
                px(img, x, y, color, rng.uniform(0.75, 0.95))
    return img


def duct_tex(rng):
    img = new((150, 154, 158))
    noise(img, rng, 4)
    for x in range(16):
        px(img, x, 0, (110, 114, 118), 0.7)
        px(img, x, 8, (174, 178, 182), 0.5)
    for y in range(16):
        px(img, 0, y, (110, 114, 118), 0.6)
    speckle(img, rng, 4, (120, 110, 96), 0.5)               # rust specks
    return img


def troffer_tex(rng, lit):
    """Recessed fluorescent fixture seen from below: frame, two tubes, diffuser louvres."""
    frame = (180, 182, 184) if lit else (120, 122, 124)
    img = new(frame)
    inner = (238, 240, 236) if lit else (150, 152, 152)
    rect(img, 1, 1, 14, 14, inner)
    tube = (255, 255, 252) if lit else (170, 172, 170)
    for tx in (4, 5, 10, 11):
        rect(img, tx, 1, tx, 14, tube)
    for y in (3, 6, 9, 12):                                  # louvres
        rect(img, 1, y, 14, y, mix(inner, frame, 0.5), 0.8)
    if not lit:
        blob(img, rng, (110, 108, 100), 5, 0.4)
        speckle(img, rng, 6, (90, 88, 84), 0.7)               # dead insects
    return img


def dim_panel_tex(rng):
    """Dim, yellowed panel on a transparent background (drawn on the glow lichen plane)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(4, 12):
        for x in range(1, 15):
            edge = x in (1, 14) or y in (4, 11)
            img.putpixel((x, y), (130, 128, 116, 255) if edge else mix((208, 196, 150), (180, 170, 130), rng.random()) + (255,))
    return img


# ---------------------------------------------------------------------------
# Decals (on a transparent background; drawn on sculk vein planes)

def decal(rng, kind):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def put(x, y, c, a=255):
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), tuple(c) + (a,))

    def splotch(color, radius, cx=8, cy=8, jitter=12):
        wob = [rng.uniform(0.65, 1.25) for _ in range(8)]
        for y in range(16):
            for x in range(16):
                ang = int((math.atan2(y - cy, x - cx) + math.pi) / (2 * math.pi) * 8) % 8
                if math.hypot(x - cx, y - cy) < radius * wob[ang]:
                    d = rng.uniform(-jitter, jitter)
                    put(x, y, (clamp(color[0] + d), clamp(color[1] + d), clamp(color[2] + d)))

    if kind == "oil":
        splotch(OIL, rng.uniform(4, 6.5), rng.uniform(6, 10), rng.uniform(6, 10))
        for _ in range(3):
            put(rng.randrange(5, 11), rng.randrange(5, 11), (70, 70, 84))      # sheen
    elif kind == "puddle":
        splotch((52, 58, 64), rng.uniform(5, 7), jitter=4)
        for _ in range(5):
            put(rng.randrange(4, 12), rng.randrange(4, 12), (120, 130, 138))  # reflections
    elif kind == "stain":
        splotch((66, 60, 52), rng.uniform(4, 7), rng.uniform(5, 11), rng.uniform(5, 11), jitter=6)
    elif kind == "dirt":
        for _ in range(40):
            put(rng.randrange(16), rng.randrange(16), mix(DIRT, (60, 50, 38), rng.random()))
    elif kind == "paper":
        x0, y0 = rng.randrange(2, 7), rng.randrange(2, 7)
        w, h = rng.randrange(5, 8), rng.randrange(6, 9)
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                put(x, y, (196, 192, 178) if (y - y0) % 2 or x in (x0, x0 + w - 1) else (150, 150, 146))
        put(x0 + w - 1, y0, (0, 0, 0), 0)                                        # torn corner
    elif kind == "tire":
        for x in range(16):
            for y in (5, 6, 10, 11):
                if rng.random() < 0.75:
                    put(x, y, (40, 40, 42))
    elif kind == "crack":
        for _ in range(2):
            x, y = rng.randrange(16), rng.randrange(16)
            for _ in range(14):
                put(x, y, (36, 36, 38))
                x += rng.choice([-1, 0, 1, 1])
                y += rng.choice([0, 1])
    elif kind == "drain":
        for y in range(4, 12):
            for x in range(4, 12):
                put(x, y, (40, 40, 42) if (x + y) % 2 else (78, 78, 80))
        for i in range(4, 12):
            put(i, 3, (100, 100, 100))
            put(i, 12, (100, 100, 100))
    # wall decals
    elif kind == "damp":
        for _ in range(5):
            x = rng.randrange(16)
            for y in range(rng.randrange(0, 5), 16):
                if rng.random() < 0.85:
                    put(x, y, (84, 90, 82), int(rng.uniform(110, 200)))
                if rng.random() < 0.2:
                    x = min(15, max(0, x + rng.choice([-1, 1])))
    elif kind == "rust":
        x = rng.randrange(4, 12)
        for y in range(16):
            put(x, y, RUST, int(255 * (1 - y / 18)))
            if rng.random() < 0.4:
                put(x + 1, y, RUST, 140)
    elif kind == "wallcrack":
        x, y = rng.randrange(3, 13), 0
        while y < 16:
            put(x, y, (70, 70, 66))
            x = min(15, max(0, x + rng.choice([-1, 0, 1])))
            y += 1
    elif kind == "tally":
        for i in range(4):
            for y in range(5, 12):
                put(4 + i * 2, y, (56, 54, 50))
        for i in range(9):
            put(3 + i, 11 - i * 6 // 8, (56, 54, 50))
    elif kind == "hand":
        for y in range(6, 13):
            for x in range(5, 10):
                if rng.random() < 0.8:
                    put(x, y, (70, 64, 58), 170)
        for f in range(4):
            for y in range(2 + (f % 2), 6):
                put(5 + f + (f > 1), y, (70, 64, 58), 170)
    # ceiling decals
    elif kind == "ring":
        r = rng.uniform(4, 6.5)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 8, y - 8)
                if r - 1.2 < d < r:
                    put(x, y, (124, 104, 74), 210)
                elif d < r - 1.2 and rng.random() < 0.25:
                    put(x, y, (140, 126, 100), 120)
    elif kind == "mold":
        for _ in range(26):
            x = int(rng.gauss(8, 3))
            y = int(rng.gauss(8, 3))
            put(x, y, (40, 46, 38), int(rng.uniform(150, 255)))
    return img


FLOOR_DECALS = ["oil", "puddle", "stain", "dirt", "paper", "tire", "crack", "drain"]
WALL_DECALS = ["damp", "rust", "wallcrack", "tally", "hand"]
CEILING_DECALS = ["ring", "mold", "crack"]


# ---------------------------------------------------------------------------
# Props

def cardboard_tex(rng, face):
    img = new((164, 128, 82))
    noise(img, rng, 6)
    if face == "top":
        rect(img, 7, 0, 8, 15, (196, 178, 140))
        rect(img, 0, 7, 15, 8, (120, 92, 56), 0.5)
    else:
        rect(img, 0, 3, 15, 3, (196, 178, 140))
        rect(img, 3, 7, 8, 10, (220, 216, 204))               # label
        rect(img, 4, 8, 7, 8, (60, 60, 60), 0.7)
    for i in range(16):
        px(img, i, 15, (110, 84, 50), 0.6)
    return img


def cone_tex(rng):
    img = new((226, 92, 24))
    noise(img, rng, 6)
    for y in (6, 7):                                          # reflective band
        rect(img, 0, y, 15, y, (236, 236, 228))
    return img


def extinguisher_tex(rng):
    img = new((172, 22, 20))
    noise(img, rng, 6)
    rect(img, 0, 6, 15, 8, (230, 226, 210))                   # label
    rect(img, 0, 0, 15, 1, (40, 40, 40))                       # valve
    return img


def mesh_tex(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if x % 3 == 0 or y % 3 == 0:
                img.putpixel((x, y), mix((160, 166, 170), (120, 120, 120), rng.random() * 0.4) + (255,))
    return img


# ---------------------------------------------------------------------------
# Materials for the LabPBR maps (pbr.py). Dark, flashlight-led: surfaces stay matte
# unless wet, and the lights glow softly instead of blooming.

CONCRETE_FLOOR = Material(0.22, porosity=0.75)
CONCRETE_WALL = Material(0.12, porosity=0.85, relief=0.8)
CONCRETE_CEILING = Material(0.10, porosity=0.8, relief=0.8)
CONCRETE_PILLAR = Material(0.18, porosity=0.7, relief=0.6)
FLOOR_PAINT = Material(0.38, porosity=0.15, relief=0.3)
WALL_PAINT = Material(0.35, porosity=0.2, relief=0.6)
SIGN_LETTERS = Material(0.40, porosity=0.1, relief=0.2)
WET = Material(0.55, porosity=0.0, relief=0.0)
WATER = Material(0.96, f0=0.02, relief=0.0)
OIL_FILM = Material(0.85, relief=0.0)
STEEL = Material(0.50, metal=230, relief=0.5)
CHROME = Material(0.65, metal=233, relief=0.4)
RUST_FLAKE = Material(0.12, porosity=0.3, relief=0.4)
PAINTED_METAL = Material(0.45, relief=0.3)
PLASTIC = Material(0.45, relief=0.2)
CARDBOARD = Material(0.05, porosity=1.0, relief=0.6)
PAPER = Material(0.18, porosity=0.6, relief=0.4)

DECAL_MATERIALS = {
    "oil": OIL_FILM, "puddle": WATER, "stain": Material(0.30, porosity=0.5, relief=0.3),
    "dirt": Material(0.08, porosity=0.6), "paper": PAPER, "tire": Material(0.25, relief=0.3),
    "crack": Material(0.05, porosity=0.8, relief=1.5), "drain": STEEL.but(smoothness=0.35, relief=1.2),
    "damp": WET, "rust": RUST_FLAKE, "wallcrack": Material(0.05, porosity=0.8, relief=1.5),
    "tally": Material(0.30, relief=0.0), "hand": Material(0.35, relief=0.0),
    "ring": Material(0.20, porosity=0.6, relief=0.0), "mold": Material(0.05, porosity=0.9, relief=0.8),
}
SURFACE_LUMINANCE = {"floor": pbr.luminance(FLOOR), "wall": pbr.luminance(WALL),
                     "ceiling": pbr.luminance(CEILING), "pillar": pbr.luminance(PILLAR)}


def near(color, target, within=40):
    return pbr.distance(color, target) < within


def material_for(name, params):
    """(material or per-pixel rule, reference luminance or None, tiles) for a texture name."""
    glow = params.PBR

    def concrete_or_paint(concrete, paint, paint_color):
        return lambda x, y, c: paint if near(c, paint_color) else concrete

    if name.startswith("floor_") or name.startswith("patch_"):
        return CONCRETE_FLOOR, None, True
    if name.startswith("wallbase_"):
        # Rising damp: wetter, so smoother, towards the floor.
        return (lambda x, y, c: CONCRETE_WALL.but(smoothness=0.12 + 0.30 * y / 15)), None, True
    if name.startswith("wall_"):
        # Damp streaks are the pixels where green beats red (DAMP); in the wall concrete they
        # are equal, and the per-pixel noise shifts all three channels alike.
        return (lambda x, y, c: WET.but(relief=0.3) if c[1] - c[0] >= 1 else CONCRETE_WALL), None, True
    if name.startswith("band_"):
        return (lambda x, y, c: CONCRETE_WALL if near(c, WALL, 24) else WALL_PAINT), None, True
    if name.startswith("ceiling_"):
        return CONCRETE_CEILING, None, True
    if name.startswith("pillar_"):
        return CONCRETE_PILLAR, None, True
    # Paint and letters are measured from the bare surface, or the bright paint would lift
    # the average and sink all the concrete around it.
    if name.startswith("line_white_"):
        return concrete_or_paint(CONCRETE_FLOOR, FLOOR_PAINT, PAINT_WHITE), SURFACE_LUMINANCE["floor"], True
    if name.startswith("line_yellow_"):
        return concrete_or_paint(CONCRETE_FLOOR, FLOOR_PAINT, PAINT_YELLOW), SURFACE_LUMINANCE["floor"], True
    if name.startswith("duct_"):
        return (lambda x, y, c: RUST_FLAKE if near(c, (120, 110, 96), 16) else STEEL), None, True
    if name == "troffer_lit":
        def lit(x, y, c):
            if pbr.luminance(c) > 0.97:                       # the tubes
                return PLASTIC.but(smoothness=0.6, relief=0.0, emission=glow["TUBE_GLOW"])
            if pbr.luminance(c) > 0.85:                       # diffuser and louvres
                return PLASTIC.but(relief=0.0, emission=glow["DIFFUSER_GLOW"])
            return PAINTED_METAL
        return lit, None, True
    if name == "troffer_dead":
        return PLASTIC.but(relief=0.5), None, True
    if name == "dim_panel":
        return (lambda x, y, c: PLASTIC.but(relief=0.0, emission=glow["DIM_PANEL_GLOW"])
                if pbr.luminance(c) > 0.6 else PAINTED_METAL), None, False
    if name.startswith("decal_"):
        _, surface, kind, _ = name.split("_")
        return DECAL_MATERIALS[kind], SURFACE_LUMINANCE[surface], False
    if name.startswith("box_"):
        return (lambda x, y, c: PAPER if near(c, (220, 216, 204), 20) else CARDBOARD), None, True
    if name == "cone":
        return (lambda x, y, c: PLASTIC.but(smoothness=0.8) if near(c, (236, 236, 228), 24) else PLASTIC), None, True
    if name == "extinguisher":
        def extinguisher(x, y, c):
            if y <= 1:
                return STEEL
            return PAPER if near(c, (230, 226, 210), 24) else PAINTED_METAL.but(smoothness=0.75, relief=0.1)
        return extinguisher, None, True
    if name == "mesh":
        return CHROME, None, False
    if name == "cart_dark":
        return PLASTIC.but(smoothness=0.35, relief=0.0), None, True
    if name.startswith("sign_"):
        return concrete_or_paint(CONCRETE_WALL, SIGN_LETTERS, params.SIGN_PAINT), SURFACE_LUMINANCE["wall"], True
    if name.startswith("code_"):
        return concrete_or_paint(CONCRETE_PILLAR, SIGN_LETTERS, params.SIGN_PAINT), SURFACE_LUMINANCE["pillar"], True
    raise ValueError(f"no PBR material for texture {name!r}: add it to material_for")


# 3x5 stencil pixel font (crisp at 16 px; a scaled-down TrueType font is not).
FONT = {
    "A": ["###", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    "C": ["###", "#..", "#..", "#..", "###"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "E": ["###", "#..", "##.", "#..", "###"], "F": ["###", "#..", "##.", "#..", "#.."],
    "G": ["###", "#..", "#.#", "#.#", "###"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "K": ["#.#", "#.#", "##.", "#.#", "#.#"],
    "L": ["#..", "#..", "#..", "#..", "###"], "N": ["##.", "#.#", "#.#", "#.#", "#.#"],
    "O": ["###", "#.#", "#.#", "#.#", "###"], "P": ["###", "#.#", "###", "#..", "#.."],
    "R": ["##.", "#.#", "##.", "#.#", "#.#"], "S": ["###", "#..", "###", "..#", "###"],
    "T": ["###", ".#.", ".#.", ".#.", ".#."], "U": ["#.#", "#.#", "#.#", "#.#", "###"],
    "X": ["#.#", "#.#", ".#.", "#.#", "#.#"], "Z": ["###", "..#", ".#.", "#..", "###"],
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["##.", "..#", ".#.", "#..", "###"], "3": ["##.", "..#", ".#.", "..#", "##."],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "##.", "..#", "##."],
    "6": [".##", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "##."],
    "?": ["##.", "..#", ".#.", "...", ".#."], "-": ["...", "...", "###", "...", "..."],
}


def glyph_tex(text, background, color, size=None):
    """Stencil-painted text (or an arrow for '<' / '>') on a copy of `background`."""
    img = background.copy()
    if text in ("<", ">"):
        big = Image.new("L", (64, 64), 0)
        pts = [(10, 32), (34, 10), (34, 24), (56, 24), (56, 40), (34, 40), (34, 54)]
        if text == ">":
            pts = [(64 - x, y) for x, y in pts]
        ImageDraw.Draw(big).polygon(pts, fill=255)
        small = big.resize((16, 16), Image.BILINEAR)
        for y in range(16):
            for x in range(16):
                if small.getpixel((x, y)) > 100:
                    px(img, x, y, color, 0.9)
        return img
    scale = 3 if len(text) == 1 else 2
    width = len(text) * 3 * scale + (len(text) - 1) * scale
    x0 = (16 - width) // 2
    y0 = (16 - 5 * scale) // 2
    for i, ch in enumerate(text.upper()):
        rows = FONT.get(ch, FONT["?"])
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "#":
                    for sy in range(scale):
                        for sx in range(scale):
                            px(img, x0 + i * 4 * scale + rx * scale + sx, y0 + ry * scale + sy, color, 0.92)
    return img


# ---------------------------------------------------------------------------
# Models and blockstates

def cube_model(down=None, up=None, north=None, south=None, east=None, west=None, all_=None, particle=None):
    t = {k: v for k, v in dict(down=down, up=up, north=north, south=south, east=east, west=west).items()}
    if all_:
        t = {k: (v or all_) for k, v in t.items()}
    textures = {k: f"{NS}:block/{v}" for k, v in t.items()}
    textures["particle"] = textures.get("north") or textures.get("down")
    return {"parent": "minecraft:block/cube", "textures": textures}


def plane_model(texture):
    """Same geometry as vanilla sculk vein / glow lichen (a plane against the north side)."""
    return {
        "ambientocclusion": False,
        "textures": {"particle": f"{NS}:block/{texture}", "t": f"{NS}:block/{texture}"},
        "elements": [{"from": [0, 0, 0.1], "to": [16, 16, 0.1], "faces": {
            "north": {"uv": [16, 0, 0, 16], "texture": "#t"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#t"}}}],
    }


def elements_model(texture, boxes, extra_textures=None):
    textures = {"particle": f"{NS}:block/{texture}", "t": f"{NS}:block/{texture}"}
    for k, v in (extra_textures or {}).items():
        textures[k] = f"{NS}:block/{v}"
    elements = []
    for box in boxes:
        frm, to = box[:2]
        tex = box[2] if len(box) > 2 else "#t"
        faces = {f: {"texture": tex} for f in ("down", "up", "north", "south", "east", "west")}
        elements.append({"from": frm, "to": to, "faces": faces})
    return {"textures": textures, "elements": elements}


# Sculk vein / glow lichen: which rotation puts the north-side plane on each face (from vanilla).
FACE_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def build_pack(params, glyphs):
    """Returns (resource pack zip bytes, {texture name: png bytes}), the dict including the
    `_n` and `_s` maps. `glyphs` is the level's Glyphs registry."""
    rng_root = random.Random(f"tex-{params.TEXTURE_SEED}")
    files = {}

    def png(img):
        buf = io.BytesIO()
        img.save(buf, "PNG")
        return buf.getvalue()

    def tex(name, img):
        """The texture plus its LabPBR normal (_n) and specular (_s) maps."""
        material, reference, tiles = material_for(name, params)
        normal, specular = pbr.maps(img, material, params.PBR["RELIEF"], reference, tiles)
        path = f"assets/{NS}/textures/block/{name}"
        files[f"{path}.png"] = png(img)
        files[f"{path}_n.png"] = png(normal)
        files[f"{path}_s.png"] = png(specular)

    def model(name, data):
        files[f"assets/{NS}/models/block/{name}.json"] = json.dumps(data, indent=1)

    def blockstate(block, data):
        files[f"assets/minecraft/blockstates/{block.split(':')[1].split('[')[0]}.json"] = json.dumps(data, indent=1)

    def r(name):
        return random.Random(f"{params.TEXTURE_SEED}-{name}")

    def random_cube(block, prefix, kinds, painter, rotate=True):
        variants = []
        for i, kind in enumerate(kinds):
            name = f"{prefix}_{i}"
            tex(name, painter(r(name), kind))
            model(name, cube_model(all_=name))
            for rot in ((0, 90, 180, 270) if rotate else (0,)):
                v = {"model": f"{NS}:block/{name}"}
                if rot:
                    v["y"] = rot
                variants.append(v)
        blockstate(block, {"variants": {"": variants}})

    S = params.STAND_INS
    random_cube(S["floor"], "floor", ["plain", "plain", "stained", "cracked", "worn", "plain"], floor_tex)
    random_cube(S["floor_patch"], "patch", ["p"], lambda g, k: patch_tex(g), rotate=False)
    random_cube(S["wall"], "wall", ["plain", "plain", "streaked", "stained"], wall_tex, rotate=False)
    random_cube(S["wall_base"], "wallbase", ["plain", "dirty"], wall_base_tex, rotate=False)
    random_cube(S["paint_band"], "band", ["b"], lambda g, k: paint_band_tex(g), rotate=False)
    random_cube(S["ceiling"], "ceiling", ["plain", "plain", "stained", "dark"], ceiling_tex, rotate=False)
    random_cube(S["pillar"], "pillar", ["plain", "scuffed"], pillar_tex, rotate=False)
    random_cube(S["duct"], "duct", ["d"], lambda g, k: duct_tex(g), rotate=False)

    # Floor paint: the line is drawn as a ~6 px stripe in the right direction.
    for key, color, axis in (("line_white", PAINT_WHITE, "z"), ("line_yellow", PAINT_YELLOW, "x")):
        names = []
        for i in range(2):
            name = f"{key}_{i}"
            tex(name, floor_line_tex(r(name), color, axis))
            model(name, cube_model(up=name, down="floor_0", north="floor_0", south="floor_0", east="floor_0", west="floor_0"))
            names.append({"model": f"{NS}:block/{name}"})
        blockstate(S[key], {"variants": {"": names}})

    # Light fixtures in the ceiling: only the bottom face shows the fixture.
    tex("troffer_lit", troffer_tex(r("tl"), True))
    tex("troffer_dead", troffer_tex(r("td"), False))
    model("fixture_lit", cube_model(down="troffer_lit", all_="ceiling_0"))
    model("fixture_dead", cube_model(down="troffer_dead", all_="ceiling_0"))
    blockstate(S["fixture_lit"], {"variants": {"": {"model": f"{NS}:block/fixture_lit"}}})
    blockstate(S["fixture_dead"], {"variants": {"": {"model": f"{NS}:block/fixture_dead"}}})

    # Dim fixture: a panel on the ceiling-side plane of glow lichen.
    tex("dim_panel", dim_panel_tex(r("dim")))
    model("dim_panel", plane_model("dim_panel"))
    blockstate(S["fixture_dim"], {"multipart": [
        {"when": {face: "true"}, "apply": dict({"model": f"{NS}:block/dim_panel", "uvlock": True}, **rot)}
        for face, rot in FACE_ROT.items()]})

    # Decals on sculk vein: floor ones on 'down', wall ones on the sides, ceiling ones on 'up'.
    groups = {"down": FLOOR_DECALS, "up": CEILING_DECALS}
    for side in ("north", "east", "south", "west"):
        groups[side] = WALL_DECALS
    made = set()
    parts = []
    for face, kinds in groups.items():
        surface = "floor" if face == "down" else ("ceiling" if face == "up" else "wall")
        models = []
        for kind in kinds:
            for i in range(2):
                name = f"decal_{surface}_{kind}_{i}"
                if name not in made:
                    tex(name, decal(r(name), kind))
                    model(name, plane_model(name))
                    made.add(name)
                models.append(dict({"model": f"{NS}:block/{name}", "uvlock": True}, **FACE_ROT[face]))
        parts.append({"when": {face: "true"}, "apply": models})
    blockstate(S["decal"], {"multipart": parts})

    # Props
    tex("box_top", cardboard_tex(r("bt"), "top"))
    tex("box_side", cardboard_tex(r("bs"), "side"))
    model("box", cube_model(up="box_top", down="box_top", all_="box_side"))
    blockstate(S["box"], {"variants": {"": [{"model": f"{NS}:block/box", "y": a} for a in (0, 90, 180, 270)]}})

    tex("cone", cone_tex(r("cone")))
    model("cone", elements_model("cone", [
        ([3, 0, 3], [13, 1, 13]), ([5, 1, 5], [11, 5, 11]), ([6, 5, 6], [10, 9, 10]), ([7, 9, 7], [9, 13, 9])]))
    blockstate(S["cone"], {"variants": {"": {"model": f"{NS}:block/cone"}}})

    tex("extinguisher", extinguisher_tex(r("ext")))
    model("extinguisher", elements_model("extinguisher", [
        ([6, 0, 6], [10, 9, 10]), ([7, 9, 7], [9, 11, 9]), ([9, 8, 7.5], [11, 9, 8.5])]))
    blockstate(S["extinguisher"], {"variants": {"": {"model": f"{NS}:block/extinguisher"}}})

    tex("mesh", mesh_tex(r("mesh")))
    tex("cart_dark", new((50, 50, 54)))
    model("cart", elements_model("mesh", [
        ([2, 5, 1], [14, 12, 13]),
        ([2, 12, 13], [14, 13, 15], "#d"),
        ([3, 0, 2], [5, 2, 4], "#d"), ([11, 0, 2], [13, 2, 4], "#d"),
        ([3, 0, 11], [5, 2, 13], "#d"), ([11, 0, 11], [13, 2, 13], "#d"),
        ([3, 2, 2], [13, 5, 12], "#d")], {"d": "cart_dark"}))
    blockstate(S["cart"], {"variants": {"": [{"model": f"{NS}:block/cart", "y": a} for a in (0, 90, 180, 270)]}})

    # Signs: chiseled bookshelf states carry the text (6 slot bits, plus the facing for pillar codes).
    wall_bg = wall_tex(r("glyph_bg"), "plain")
    pillar_bg = pillar_tex(r("glyph_pillar_bg"), "plain")
    for slot, text in glyphs.wall.items():
        name = f"sign_{slot}"
        tex(name, glyph_tex(text, wall_bg, params.SIGN_PAINT))
        model(name, cube_model(north=name, all_="wall_0"))
    for (facing, slot), text in glyphs.pillar.items():
        name = f"code_{facing}_{slot}"
        tex(name, glyph_tex(text, pillar_bg, params.SIGN_PAINT))
        model(name, cube_model(north=name, south=name, east=name, west=name, all_="pillar_0"))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for slot in range(64):
            key = f"facing={facing}," + ",".join(f"slot_{b}_occupied={'true' if slot >> b & 1 else 'false'}" for b in range(6))
            if slot in glyphs.wall:
                v = {"model": f"{NS}:block/sign_{slot}"}
                if rot:
                    v["y"] = rot
            elif (facing, slot) in glyphs.pillar:
                v = {"model": f"{NS}:block/code_{facing}_{slot}"}
            else:
                v = {"model": f"{NS}:block/wall_0"}
            variants[key] = v
    blockstate(S["glyph"], {"variants": variants})

    files["pack.mcmeta"] = json.dumps({"pack": {
        "pack_format": params.RESOURCE_PACK_FORMAT,
        "description": "Every Tunnel garage textures (world resource pack)"}}, indent=1)
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(files):
            data = files[path]
            z.writestr(path, data if isinstance(data, bytes) else data.encode())
    textures = {p.split("/")[-1][:-4]: d for p, d in files.items() if p.endswith(".png")}
    return out.getvalue(), textures
