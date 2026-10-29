"""
Assets of the mod's own blocks: block and item models, blockstates, 16x16 textures with
their LabPBR maps (_n, _s), English names and loot tables.

Run after changing a block here (from the repository root):   python3 tools/blocks/generate.py

Writes into src/main/resources/ (committed with the mod):
  assets/every_tunnel/blockstates/<block>.json
  assets/every_tunnel/models/block/<model>.json, models/item/<block>.json
  assets/every_tunnel/textures/block/<texture>.png  (+ _n.png, _s.png)
  assets/every_tunnel/lang/en_us.json               (the block names; other keys are kept)
  data/every_tunnel/loot_tables/blocks/<block>.json
and a preview, tools/blocks/output/blocks_sheet.png (git-ignored): every texture as
albedo | normal | specular | emission.

The blocks themselves (properties, shapes, sounds) are registered in Kotlin
(platform/ModBlocks.kt); the ids in BLOCKS must match, which this tool checks. Model
rotations must match the shapes there too (platform/BlockShapes.kt,
FluorescentLightBlock.modelRotation). Textures reuse the level generator's painters and
materials (tools/map/garage/textures.py), so the props look like the level around them.
"""

import io
import json
import random
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "map"))

import map_generator as G                                              # noqa: E402
from garage import pbr, preview                                         # noqa: E402
from garage import textures as T                                        # noqa: E402
from garage.pbr import Material                                         # noqa: E402
from garage.textures import mix, new, noise, px, rect, speckle         # noqa: E402
from offices import textures as OT                                     # noqa: E402

NS = "every_tunnel"
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / NS
LOOT = RESOURCES / "data" / NS / "loot_tables" / "blocks"
OUT = Path(__file__).resolve().parent / "output"
MOD_BLOCKS_KT = ROOT / "src" / "main" / "kotlin" / "daio7771" / "everytunnel" / "platform" / "ModBlocks.kt"

HORIZONTAL = {"north": 0, "east": 90, "south": 180, "west": 270}
# FloorIndicatorBlock.LABELS, top floor first.
FLOOR_LABELS = ["3", "2", "1", "G", "B1"]
SIDES = ["north", "east", "south", "west", "up", "down"]


def rng_for(name):
    return random.Random(f"block-{name}")


# =====================================================================================
# Textures

def wood_tex(rng, top):
    """Pallet: planks with gaps (top), or the cut end grain of the runners (sides)."""
    img = new((156, 120, 78))
    noise(img, rng, 9)
    for _ in range(14):                                          # grain
        x, y = rng.randrange(16), rng.randrange(16)
        for i in range(rng.randrange(2, 6)):
            px(img, x + i, y, (120, 88, 54), 0.5)
    if top:
        for y in range(16):
            if y % 4 == 3:
                rect(img, 0, y, 15, y, (52, 40, 26))
        speckle(img, rng, 4, (70, 64, 60), 0.8)                    # nail heads
    else:
        rect(img, 0, 0, 15, 2, (128, 96, 60))
    T.gradient(img, (70, 62, 50), 0.0, 0.3)                      # grime
    return img


def drum_tex(rng, top):
    """Oil drum: blue painted steel with rolling hoops (side), lid with two bungs (top)."""
    base = (44, 72, 118)
    img = new(base)
    noise(img, rng, 5)
    if top:
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if 6.5 < d < 7.6:
                    px(img, x, y, (70, 98, 140), 0.8)              # rim
        for cx, cy in ((4, 5), (11, 10)):
            rect(img, cx - 1, cy - 1, cx, cy, (34, 34, 36))
    else:
        for y in (4, 11):
            rect(img, 0, y, 15, y, (70, 98, 140))
            rect(img, 0, y + 1, 15, y + 1, (30, 48, 80))
        for _ in range(6):
            T.blob(img, rng, T.RUST, rng.uniform(1, 2.5), 0.6)
        T.streaks(img, rng, 3, T.RUST, 0.4)
    return img


def bin_tex(rng, top):
    """Plastic wheelie bin: dark green ribs (side), lid with a handle groove (top)."""
    img = new((52, 70, 58))
    noise(img, rng, 4)
    if top:
        rect(img, 0, 2, 15, 2, (36, 48, 40))
        rect(img, 0, 13, 15, 13, (36, 48, 40))
    else:
        for x in (2, 6, 9, 13):
            rect(img, x, 1, x, 14, (66, 86, 72))
        rect(img, 0, 15, 15, 15, (30, 40, 34))
    T.gradient(img, (60, 60, 56), 0.0, 0.3)
    return img


def wheel_stop_tex(rng):
    img = T.floor_tex(rng, "worn")
    for x in range(16):
        if (x // 4) % 2 == 0:
            for y in range(16):
                if rng.random() > 0.15:
                    px(img, x, y, T.PAINT_YELLOW, 0.85)
    return img


def pipe_tex(rng):
    """Painted steel pipe, lengthwise along the texture: lit on top, shaded below (a cylinder)."""
    img = new((116, 128, 116))
    for y in range(16):
        shade = 1.18 - abs(y - 5) / 12 if y < 5 else 1.18 - (y - 5) / 14
        for x in range(16):
            r, g, b, a = img.getpixel((x, y))
            img.putpixel((x, y), (T.clamp(r * shade), T.clamp(g * shade), T.clamp(b * shade), a))
    noise(img, rng, 4)
    rect(img, 0, 0, 0, 15, (80, 88, 80), 0.6)                    # weld seam between pipe lengths
    for _ in range(4):
        T.blob(img, rng, T.RUST, rng.uniform(1, 2), 0.5)
    return img


def tray_tex(rng, part):
    """Cable tray: perforated galvanized steel (bottom, see-through holes), side rail, cable bundle."""
    if part == "cables":
        img = new((24, 24, 26))
        colours = [(24, 24, 26), (60, 62, 64), (24, 24, 26), (150, 72, 30), (24, 24, 26), (40, 60, 110)]
        for y in range(16):
            c = colours[(y // 2) % len(colours)]
            for x in range(16):
                img.putpixel((x, y), c + (255,))
        noise(img, rng, 5)
        for y in range(16):                                      # roundness of each cable
            if y % 2 == 1:
                rect(img, 0, y, 15, y, (0, 0, 0), 0.3)
        return img
    img = new((158, 162, 164))
    noise(img, rng, 4)
    if part == "bottom":
        for y in range(1, 16, 4):
            for x in range(1, 16, 4):
                rect(img, x, y, x + 1, y + 1, (0, 0, 0, 0))
    else:
        rect(img, 0, 0, 15, 0, (190, 194, 196))
        for x in range(2, 16, 5):
            rect(img, x, 1, x + 1, 2, (0, 0, 0, 0))
    speckle(img, rng, 3, (120, 110, 96), 0.5)
    return img


def cable_tex(rng):
    img = new((28, 28, 30))
    noise(img, rng, 3)
    rect(img, 6, 0, 6, 15, (64, 64, 68))                         # highlight along the cable
    return img


def metal_panel_tex(rng):
    """Brushed stainless panel of the service elevator, with a seam and rivets."""
    img = new((150, 152, 154))
    for y in range(16):
        d = rng.uniform(-6, 6)
        for x in range(16):
            r, g, b, a = img.getpixel((x, y))
            e = d + rng.uniform(-2, 2)
            img.putpixel((x, y), (T.clamp(r + e), T.clamp(g + e), T.clamp(b + e), a))
    rect(img, 0, 15, 15, 15, (100, 102, 104))
    rect(img, 15, 0, 15, 15, (110, 112, 114))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px(img, x, y, (186, 188, 190))
        px(img, x + 1, y + 1, (96, 98, 100))
    T.blob(img, rng, (110, 106, 96), 5, 0.25)                    # handprints and grime
    dents(img, rng, 4)
    return img


def dents(img, rng, count, scratches=3):
    """Dents (a dark pixel with a lit one beside it) and fine light scratches on metal."""
    for _ in range(count):
        x, y = rng.randrange(1, 14), rng.randrange(1, 15)
        px(img, x, y, (84, 86, 90), 0.55)
        px(img, x + 1, y, (196, 198, 202), 0.45)
        if rng.random() < 0.5:
            px(img, x, y + 1, (96, 98, 102), 0.35)
    for _ in range(scratches):
        x, y = rng.randrange(0, 12), rng.randrange(0, 16)
        for k in range(rng.randrange(3, 7)):
            px(img, x + k, y + (k // 3) * (1 if rng.random() < 0.5 else -1), (206, 208, 212), 0.35)


def elevator_door_tex(rng):
    """Sliding elevator door: brushed stainless steel with a vertical grain, worn darker
    where hands push it, a thin rubber strip along both edges."""
    img = new((156, 158, 160))
    for x in range(16):
        d = rng.uniform(-7, 7)
        for y in range(16):
            r, g, b, a = img.getpixel((x, y))
            e = d + rng.uniform(-2, 2)
            img.putpixel((x, y), (T.clamp(r + e), T.clamp(g + e), T.clamp(b + e), a))
    rect(img, 0, 0, 0, 15, (58, 58, 60))
    rect(img, 15, 0, 15, 15, (58, 58, 60))
    T.blob(img, rng, (120, 118, 112), 4, 0.2)                    # smudges
    for _ in range(2):                                            # handprints: a palm and finger smears
        hx, hy = rng.randrange(3, 10), rng.randrange(2, 9)
        for dx, dy in ((0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (0, -1), (1, -2), (2, -1), (3, 1)):
            px(img, hx + dx, hy + dy, (108, 110, 114), 0.35)
    dents(img, rng, 5, scratches=4)
    return img


def battery_tex(rng):
    """Item: a big flashlight battery lying diagonally, like vanilla's tools: a black
    cylinder lit along its upper edge, a cold teal brand band, a steel cap and terminal at
    the top end. Outlined in a darker shade of whatever each edge pixel is."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    ax, ay, bx, by = 3.0, 12.5, 12.0, 3.5                   # axis: bottom end to terminal end
    dx, dy = bx - ax, by - ay
    length = (dx * dx + dy * dy) ** 0.5
    radius = 2.9
    cells = {}
    for y in range(16):
        for x in range(16):
            px_, py_ = x + 0.5, y + 0.5
            t = ((px_ - ax) * dx + (py_ - ay) * dy) / (length * length)
            across = ((px_ - ax) * dy - (py_ - ay) * dx) / length        # < 0: upper left side
            if -0.04 <= t <= 1.04 and abs(across) <= radius:
                cells[(x, y)] = (t, across)
    for (x, y), (t, across) in cells.items():
        light = 0 if across < -1.0 else 2 if across > 1.0 else 1
        if t > 0.84:
            c = ((196, 202, 210), (150, 156, 164), (104, 110, 118))[light]     # steel cap
        elif 0.5 < t < 0.68:
            c = ((96, 196, 194), (58, 150, 150), (36, 104, 108))[light]        # brand band
        else:
            c = ((70, 74, 82), (36, 38, 44), (22, 23, 27))[light]             # black body
        outline = any((x + ox, y + oy) not in cells for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if outline:
            c = tuple(int(v * 0.5) for v in c)
        img.putpixel((x, y), (*c, 255))
    img.putpixel((13, 2), (176, 182, 190, 255))                 # the terminal nub
    img.putpixel((14, 2), (96, 100, 108, 255))
    img.putpixel((13, 1), (96, 100, 108, 255))
    return img


def water_bottle_tex(rng):
    """Item: a small clear plastic water bottle, three quarters full: a ridged blue cap and
    neck ring, white label with a blue wave, cold reflections on the plastic."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    edge, plastic, glint = (118, 140, 156, 235), (196, 212, 222, 110), (236, 246, 252, 230)
    water, deep, surface = (88, 150, 200, 215), (64, 118, 172, 225), (156, 204, 232, 235)
    shape = {0: (6, 9), 1: (6, 9), 2: (7, 8), 3: (6, 9), 4: (5, 10), 5: (5, 10)}
    shape.update({y: (5, 10) for y in range(6, 15)})
    shape[15] = (6, 9)
    for y, (x0, x1) in shape.items():
        for x in range(x0, x1 + 1):
            if y <= 1:                                          # cap, ridged
                c = (40, 84, 186, 255) if (x + y) % 2 else (64, 112, 210, 255)
                if x in (x0, x1):
                    c = (26, 52, 128, 255)
            elif y == 3:
                c = (40, 84, 186, 255) if x in (x0, x1) else (60, 104, 200, 255)   # neck ring
            elif x in (x0, x1) or y == 15:
                c = edge
            elif y == 6:
                c = surface
            elif y >= 7:
                c = deep if x >= x1 - 1 else water
            else:
                c = plastic
            if 9 <= y <= 11 and x0 < x < x1:                    # label with a blue stripe
                c = (60, 110, 200, 255) if y == 10 else (228, 233, 238, 255)
            img.putpixel((x, y), c)
    for y in (4, 5, 7, 8, 12, 13):                              # reflections down the left side
        img.putpixel((6, y), glint)
    img.putpixel((9, 13), (236, 246, 252, 160))
    return img


def keycard_tex(rng):
    """Item: the guard's keycard on a black lanyard clip: a white plastic card with a
    blue-grey band, a photo, name lines and a gold chip."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    white, edge = (230, 233, 236, 255), (150, 156, 164, 255)
    for y in range(5, 14):
        for x in range(2, 14):
            if (x, y) in ((2, 5), (13, 5), (2, 13), (13, 13)):
                continue                                        # rounded corners
            c = white
            if y in (5, 6):
                c = (66, 96, 140, 255)                          # band
            if x in (2, 13) or y == 13:
                c = edge if y > 6 else (44, 66, 100, 255)
            img.putpixel((x, y), c)
    img.putpixel((7, 6), (24, 26, 30, 255))                     # slot for the clip
    img.putpixel((8, 6), (24, 26, 30, 255))
    for y in (1, 2, 3, 4, 5):                                   # lanyard clip and strap
        img.putpixel((7, y), (34, 36, 40, 255))
        img.putpixel((8, y), (52, 56, 62, 255) if y > 2 else (34, 36, 40, 255))
    img.putpixel((6, 1), (34, 36, 40, 255))
    img.putpixel((9, 1), (34, 36, 40, 255))
    for y in range(8, 12):                                      # photo: hair, face, shirt
        for x in range(3, 6):
            img.putpixel((x, y), (40, 34, 32, 255) if y == 8 else (178, 148, 130, 255) if y < 10 else (30, 32, 37, 255))
    for x in range(7, 13):
        img.putpixel((x, 8), (110, 116, 124, 255))              # name
        if x < 11:
            img.putpixel((x, 10), (160, 166, 174, 255))         # number
    for x, y in ((10, 11), (11, 11), (10, 12), (11, 12)):
        img.putpixel((x, y), (204, 166, 72, 255))               # chip
    img.putpixel((11, 12), (168, 132, 52, 255))
    return img


def card_reader_tex(rng, part):
    """Card reader: dark plastic body (side), or its face: an LED (red locked, green open),
    a card slot and a few keys, in the pixels the model's front shows (x 5-10, y 4-11)."""
    img = new((40, 42, 46))
    speckle(img, rng, 30, (52, 54, 58), 0.5)
    if part == "side":
        return img
    rect(img, 5, 4, 10, 11, (30, 31, 34))
    led = (226, 52, 44) if part == "locked" else (64, 220, 96)
    rect(img, 7, 5, 8, 5, led)
    rect(img, 6, 7, 9, 7, (12, 12, 14))                                           # card slot
    for x, y in ((6, 9), (8, 9), (6, 10), (8, 10)):
        px(img, x, y, (90, 92, 98))
    return img


# Painted letters of the valves' plates, 3 x 5 pixels.
VALVE_LETTERS = {
    "a": [".#.", "#.#", "###", "#.#", "#.#"],
    "b": ["##.", "#.#", "##.", "#.#", "##."],
    "c": [".##", "#..", "#..", "#..", ".##"],
    "d": ["##.", "#.#", "#.#", "#.#", "##."],
    "e": ["###", "#..", "##.", "#..", "###"],
}


def valve_wheel_tex(rng, turned):
    """A round red handwheel, seen face on, on a clear background (drawn cutout): rim, four
    spokes (straight, or an eighth round when `turned`) and the hub. The model shows texels
    2-13; the wheel is centred between texels 7 and 8, as the model's stem is."""
    img = new((0, 0, 0), alpha=0)
    red = (158, 42, 38)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = (dx * dx + dy * dy) ** 0.5
            rim = 4.4 <= d <= 5.9
            spoke = d < 4.6 and (abs(dx + dy) < 1.0 or abs(dx - dy) < 1.0 if turned else abs(dx) < 1 or abs(dy) < 1)
            if rim or spoke or d < 1.8:
                shade = 1.15 if dy < -1 else 0.85 if dy > 1.5 else 1.0          # lit from above
                if d < 1.8:
                    shade *= 0.8
                c = tuple(T.clamp(v * shade + rng.uniform(-6, 6)) for v in red)
                img.putpixel((x, y), (*c, 255))
    return img


def valve_body_tex(rng):
    """The valve's cast body: the pipe's paint, darker and duller."""
    img = new((92, 104, 94))
    noise(img, rng, 4)
    speckle(img, rng, 14, (74, 84, 76), 0.6)
    rect(img, 0, 0, 15, 0, (70, 80, 72), 0.5)                       # cast seams
    rect(img, 0, 15, 15, 15, (70, 80, 72), 0.5)
    return img


def valve_stem_tex(rng):
    """The threaded steel stem and hub: bright and dark rows."""
    img = new((150, 154, 160))
    for y in range(0, 16, 2):
        rect(img, 0, y, 15, y, (104, 108, 114), 0.8)
    noise(img, rng, 3)
    return img


# Painted digits of the valves' dial windows, 3 x 5 pixels.
VALVE_DIGITS = [
    ["###", "#.#", "#.#", "#.#", "###"], [".#.", "##.", ".#.", ".#.", "###"], ["###", "..#", "###", "#..", "###"],
    ["###", "..#", ".##", "..#", "###"], ["#.#", "#.#", "###", "..#", "..#"], ["###", "#..", "###", "..#", "###"],
    ["###", "#..", "###", "#.#", "###"], ["###", "..#", ".#.", ".#.", ".#."], ["###", "#.#", "###", "#.#", "###"],
    ["###", "#.#", "###", "..#", "###"],
]
PLATE_EDGE = (120, 122, 118)


def valve_plate_tex(rng, letter):
    """The left half of a side plate: an enamel plate with the letter painted on, in the pixels
    the model shows (x 4-7, y 4-10; the dial window takes x 8-11)."""
    img = new((92, 104, 94))
    rect(img, 4, 4, 7, 10, (214, 212, 200))
    for x in range(4, 8):
        px(img, x, 4, PLATE_EDGE, 0.8)
        px(img, x, 10, PLATE_EDGE, 0.8)
    for y in range(4, 11):
        px(img, 4, y, PLATE_EDGE, 0.8)
    for row, line in enumerate(VALVE_LETTERS[letter]):
        for col, c in enumerate(line):
            if c == "#":
                px(img, 5 + col, 5 + row, (28, 28, 30))
    noise(img, rng, 2)
    return img


def valve_digit_tex(rng, digit):
    """The right half of a side plate: the dial's little dark window with its turns, 0 to 9
    (x 8-11, y 4-10)."""
    img = new((92, 104, 94))
    rect(img, 8, 4, 11, 10, (30, 30, 32))
    for x in range(8, 12):
        px(img, x, 4, PLATE_EDGE, 0.8)
        px(img, x, 10, PLATE_EDGE, 0.8)
    for y in range(4, 11):
        px(img, 11, y, PLATE_EDGE, 0.8)
    for row, line in enumerate(VALVE_DIGITS[digit]):
        for col, c in enumerate(line):
            if c == "#":
                px(img, 8 + col, 5 + row, (236, 214, 150))
    return img


def cork_tex(rng):
    img = new((150, 110, 70))
    noise(img, rng, 10)
    speckle(img, rng, 30, (110, 78, 46), 0.7)
    speckle(img, rng, 20, (178, 138, 92), 0.6)
    for i in range(16):
        px(img, i, 0, (96, 70, 44), 0.8)
        px(img, i, 15, (96, 70, 44), 0.8)
        px(img, 0, i, (96, 70, 44), 0.8)
        px(img, 15, i, (96, 70, 44), 0.8)
    return img


def pinned_note_tex(rng):
    """Typed sheets pinned to the board: rows of grey type, a heading line."""
    img = new((232, 230, 220))
    noise(img, rng, 2)
    for x in range(2, 12):
        px(img, x, 2, (60, 60, 66), 0.9)
    for y in range(5, 15, 2):
        for x in range(2, rng.randrange(9, 14)):
            if rng.random() < 0.85:
                px(img, x, y, (120, 120, 126), 0.7)
    return img


def note_paper_tex(rng):
    """A loose typed sheet (NotePaperBlock), the whole texture mapped onto the sheet: a dark
    heading, a rule under it, then rows of grey type with ragged ends."""
    img = new((234, 232, 224))
    noise(img, rng, 2)
    for x in range(2, 10):
        px(img, x, 2, (52, 52, 58), 0.9)
    for x in range(2, 14):
        px(img, x, 4, (150, 150, 156), 0.5)
    for y in range(6, 15, 2):
        for x in range(2, rng.randrange(9, 15)):
            if rng.random() < 0.85:
                px(img, x, y, (118, 118, 124), 0.7)
    return img


# --- Level 3 furniture ------------------------------------------------------------------
# Offices: powder-coated light grey metal and a light beech laminate; the library: the dark
# wood of its walls (offices/textures.py); staff rooms in plain plastics. Clean, no wear.

LAMINATE = (196, 172, 132)
OFFICE_METAL = (168, 172, 176)
LIBRARY_WOOD = OT.WOOD_DARK
BOOK_COLOURS = [(112, 42, 40), (52, 82, 62), (44, 54, 84), (102, 72, 46), (150, 120, 62), (88, 88, 92), (70, 40, 58)]


def laminate_tex(rng, base=LAMINATE):
    """Wood-look laminate: a faint grain running along the texture."""
    img = new(base)
    for y in range(16):
        shade = rng.uniform(-5, 5)
        for x in range(16):
            px(img, x, y, tuple(T.clamp(c + shade) for c in base), 0.7)
    noise(img, rng, 2)
    for _ in range(3):
        y = rng.randrange(16)
        for x in range(rng.randrange(0, 6), rng.randrange(10, 16)):
            px(img, x, y, tuple(c - 14 for c in base), 0.35)
    return img


def powder_metal_tex(rng, base=OFFICE_METAL):
    img = new(base)
    noise(img, rng, 2)
    speckle(img, rng, 10, tuple(c - 10 for c in base), 0.4)
    return img


def dark_wood_tex(rng):
    """The library's dark wood, plain (tops, chairs, legs)."""
    return OT.wood_tex(rng, "plain")


def filing_front_tex(rng):
    """Four drawers, each with a handle and a white label slot."""
    img = powder_metal_tex(rng)
    for top in (0, 4, 8, 12):
        rect(img, 1, top, 14, top, (130, 134, 138), 0.8)                 # gap between drawers
        rect(img, 6, top + 2, 9, top + 2, (90, 94, 98))                   # handle
        rect(img, 6, top + 1, 9, top + 1, (226, 226, 220))                # label
    return img


def whiteboard_tex(rng):
    img = new((236, 238, 236))
    noise(img, rng, 1)
    T.blob(img, rng, (244, 246, 244), 6, 0.4)                              # a sheen, nothing written
    return img


def server_front_tex(rng):
    """A rack's perforated door with a column of status LEDs."""
    img = new((28, 30, 32))
    for y in range(1, 15):
        for x in range(2, 14):
            if (x + y) % 2 == 0:
                px(img, x, y, (12, 12, 14))
    for y in range(2, 14, 2):
        px(img, 14, y, (60, 220, 90) if rng.random() < 0.8 else (230, 170, 40))
    rect(img, 0, 0, 15, 0, (50, 52, 56))
    rect(img, 0, 15, 15, 15, (50, 52, 56))
    return img


def leds_glow(x, y, c):
    if max(c) > 160 and min(c) < 120:
        return T.PLASTIC.but(smoothness=0.7, relief=0.0, emission=GLOW["TUBE_GLOW"])
    return T.PAINTED_METAL.but(smoothness=0.3)


def electrical_front_tex(rng):
    """A grey wall cabinet's door: seams, a lock and a yellow warning label."""
    img = powder_metal_tex(rng, (178, 180, 176))
    rect(img, 0, 0, 15, 0, (140, 142, 138))
    rect(img, 8, 0, 8, 15, (140, 142, 138), 0.7)                            # two leaves
    for x, y in ((6, 4), (7, 3), (8, 2), (9, 3), (10, 4), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5)):
        px(img, x - 4, y + 3, (226, 190, 40))                              # warning triangle
    px(img, 4, 7, (30, 30, 30))
    rect(img, 10, 8, 11, 9, (70, 72, 76))                                   # lock
    return img


def locker_tex(rng, part):
    """A grey-blue locker door: vents at the top of the upper block, the handle on the lower."""
    base = (120, 132, 148)
    img = powder_metal_tex(rng, base)
    rect(img, 0, 0, 0, 15, (90, 100, 114))
    rect(img, 15, 0, 15, 15, (90, 100, 114))
    if part == "upper":
        for y in (2, 4, 6):
            rect(img, 4, y, 11, y, (70, 78, 90))
    else:
        rect(img, 11, 2, 12, 5, (70, 74, 80))
        rect(img, 0, 15, 15, 15, (90, 100, 114))
    return img


def vending_tex(rng, part):
    """The drinks machine: the lit window of cans on the upper block, the slot and coin panel below."""
    img = new((150, 40, 36))
    noise(img, rng, 3)
    if part == "upper":
        rect(img, 1, 1, 10, 14, (214, 220, 222))                           # lit window
        for row, y in enumerate((2, 6, 10)):
            for x in range(2, 10, 2):
                c = rng.choice([(200, 40, 40), (40, 90, 170), (230, 200, 60), (60, 150, 80), (240, 240, 240)])
                rect(img, x, y, x, y + 2, c)
        rect(img, 12, 3, 14, 12, (40, 40, 44))                             # button column
        for y in range(4, 12, 2):
            px(img, 13, y, (240, 230, 160))
    else:
        rect(img, 2, 9, 13, 13, (24, 24, 26))                               # delivery slot
        rect(img, 11, 2, 13, 6, (60, 62, 66))                               # coin panel
        px(img, 12, 3, (20, 20, 22))
    return img


def vending_glow(x, y, c):
    if min(c) > 190:
        return T.PLASTIC.but(smoothness=0.8, relief=0.0, emission=GLOW["DIFFUSER_GLOW"])
    return T.PAINTED_METAL


def leaves_tex(rng):
    """Artificial leaves on a clear background (drawn cutout): too even, too green."""
    img = new((0, 0, 0), alpha=0)
    for _ in range(26):
        x, y = rng.randrange(1, 15), rng.randrange(0, 14)
        c = rng.choice([(58, 112, 58), (70, 128, 64), (48, 96, 52)])
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 1)):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                img.putpixel((x + dx, y + dy), (*c, 255))
    for y in range(8, 16):                                                  # the stems
        img.putpixel((7, y), (60, 70, 40, 255))
    return img


def clock_tex(rng):
    """A white clock face in a black rim on a clear background, its hands stopped at 3:47."""
    img = new((0, 0, 0), alpha=0)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 5.4:
                img.putpixel((x, y), (30, 30, 32, 255) if d > 4.4 else (236, 236, 230, 255))
    for x, y in ((8, 7), (9, 7), (10, 7)):                                   # hour hand towards 3-4
        img.putpixel((x, y + (1 if x == 10 else 0)), (20, 20, 20, 255))
    for x, y in ((7, 7), (6, 6), (5, 5), (5, 4)):                            # minute hand at 47
        img.putpixel((x, y), (20, 20, 20, 255))
    for x, y in ((7, 3), (11, 7), (7, 11), (3, 7)):
        img.putpixel((x, y), (60, 60, 60, 255))
    return img


PURPLE = (168, 72, 220)


def purple_box_tex(rng, face):
    """Level 3's purple archive box (the box puzzle): bright purple cardboard. `side`: the lid's
    band (rows 0-2), a hand hole and a blank white label; `top`: the lid, its rim darker."""
    img = new(PURPLE)
    noise(img, rng, 6)
    if face == "top":
        rect(img, 0, 0, 15, 15, (184, 92, 232))
        noise(img, rng, 5)
        for i in range(16):
            for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
                px(img, x, y, (136, 52, 186))
        rect(img, 1, 7, 14, 8, (150, 60, 200), 0.5)                       # the lid's fold
    else:
        rect(img, 0, 0, 15, 2, (146, 56, 194))                            # the lid's band
        rect(img, 0, 2, 15, 2, (118, 40, 160), 0.7)
        rect(img, 6, 5, 9, 6, (52, 18, 72))                               # hand hole
        rect(img, 3, 9, 12, 12, (228, 222, 236))                          # label
        rect(img, 4, 10, 10, 10, (150, 140, 160), 0.6)
        for i in range(16):
            px(img, i, 15, (110, 40, 150), 0.6)
    return img


def office_book_tex(rng, face):
    """Level 3's office book (the book puzzle): a corporate blue-grey hardback. `cover`: cloth
    with a darker spine band and a pale blank label; `pages`: the page edges between the boards."""
    if face == "pages":
        img = new((226, 222, 210))
        noise(img, rng, 4)
        for y in range(0, 16, 2):
            rect(img, 0, y, 15, y, (204, 200, 188), 0.5)
        rect(img, 0, 0, 15, 1, (92, 110, 132))                               # the boards
        rect(img, 0, 14, 15, 15, (92, 110, 132))
        return img
    img = new((96, 116, 138))
    noise(img, rng, 5)
    rect(img, 0, 0, 2, 15, (70, 86, 106))                                     # the spine
    rect(img, 6, 5, 12, 9, (214, 216, 220))                                   # the label
    rect(img, 7, 7, 11, 7, (130, 138, 150), 0.6)
    for i in range(16):
        px(img, 15, i, (120, 138, 160), 0.5)
    return img


def books_tex(rng):
    """Two shelves of book spines (rows 1-7 and 9-15) in muted colours and heights, the shelf
    boards (rows 0 and 8) and the carcass (columns 0 and 15) in dark wood."""
    img = dark_wood_tex(rng)
    for top in (1, 9):
        rect(img, 1, top, 14, top + 6, (26, 18, 12))                       # the dark inside
        x = 1
        while x < 15:
            w = rng.choice([1, 1, 2, 2, 2])
            h = rng.randrange(4, 8)
            c = rng.choice(BOOK_COLOURS)
            for i in range(w):
                if x + i > 14:
                    break
                shade = 1.0 if i == 0 else 0.85
                for yy in range(top + 7 - h, top + 7):
                    px(img, x + i, yy, tuple(T.clamp(v * shade) for v in c))
                if h >= 6:
                    px(img, x + i, top + 7 - h + 1, tuple(T.clamp(v + 40) for v in c), 0.6)   # a band
            x += w
            if rng.random() < 0.1:
                x += 1                                                     # a gap
    return img


def card_catalog_tex(rng):
    """Rows of small drawers with brass pulls and white label cards."""
    img = dark_wood_tex(rng)
    for y0 in (1, 5, 9):
        for x0 in (1, 5, 9, 12):
            rect(img, x0, y0, x0 + 2, y0 + 2, (84, 62, 44))
            px(img, x0 + 1, y0, (226, 224, 214))
            px(img, x0 + 1, y0 + 2, (176, 146, 70))
    return img


def led_glow(x, y, c):

    if max(c) > 200 and min(c) < 120:
        return T.PLASTIC.but(smoothness=0.7, relief=0.0, emission=GLOW["TUBE_GLOW"])
    return T.PLASTIC.but(smoothness=0.5, relief=0.3)


def steel_door_tex(rng, half, colour, kind):
    """One half of a painted steel door (bottom or top), handle side on the right like vanilla's
    doors (the models mirror it for the other hinge). kind: "storage" / "electrical" (service
    doors: a wired-glass window and the room's plate on the top half, a lever handle) or
    "emergency" (no window; a push bar across the bottom half's top)."""
    img = new(colour)
    noise(img, rng, 4)
    dark = tuple(max(0, c - 34) for c in colour)
    light = tuple(min(255, c + 22) for c in colour)
    for i in range(16):                                           # frame edges
        px(img, 0, i, dark, 0.7)
        px(img, 15, i, dark, 0.7)
    if half == "top":
        for x in range(16):
            px(img, x, 0, dark, 0.7)
    else:
        for x in range(16):
            px(img, x, 15, dark, 0.8)
    for _ in range(4):                                            # dents and scratches
        x, y = rng.randrange(2, 14), rng.randrange(1, 15)
        px(img, x, y, dark, 0.45)
        px(img, x + 1, y, light, 0.35)
    for _ in range(3):
        y = rng.randrange(1, 15)
        for x in range(rng.randrange(1, 6), rng.randrange(8, 15)):
            px(img, x, y, light, 0.25)
    if half == "bottom":
        T.gradient(img, (40, 42, 46), 0.0, 0.35)                  # kicked and dirty at the bottom
    if kind == "emergency":
        if half == "bottom":
            rect(img, 2, 1, 13, 2, (170, 176, 184))               # push bar
            rect(img, 2, 3, 13, 3, (92, 96, 104))
            rect(img, 2, 1, 2, 3, (60, 62, 68))
            rect(img, 13, 1, 13, 3, (60, 62, 68))
        else:
            rect(img, 4, 6, 11, 9, (40, 150, 86))                 # green running-man plate
            for x, y in ((7, 6), (6, 7), (7, 7), (8, 7), (7, 8), (6, 9), (8, 9)):
                px(img, x, y, (236, 244, 238), 0.95)
        return img
    if half == "top":
        rect(img, 4, 3, 11, 9, (54, 64, 74))                      # wired glass, dusty
        for x in range(4, 12):
            for y in range(3, 10):
                if (x + y) % 3 == 0:
                    px(img, x, y, (96, 104, 112), 0.7)
        px(img, 4, 3, (120, 132, 144), 0.6)
        rect(img, 3, 2, 12, 2, dark)
        rect(img, 3, 10, 12, 10, light)
        # The room's plate under the window.
        rect(img, 5, 12, 10, 14, (214, 196, 60) if kind == "electrical" else (210, 214, 218))
        if kind == "electrical":                                  # warning triangle with a bolt
            for x, y in ((7, 12), (8, 12), (6, 13), (9, 13), (5, 14), (10, 14)):
                px(img, x, y, (24, 24, 26), 0.9)
            px(img, 8, 13, (24, 24, 26), 0.9)
            px(img, 7, 14, (24, 24, 26), 0.9)
        else:                                                     # a box: shelves
            rect(img, 6, 12, 9, 14, (150, 116, 74))
            rect(img, 6, 13, 9, 13, (110, 84, 52))
    else:
        rect(img, 12, 1, 14, 1, (178, 184, 192))                  # lever handle and its rose
        px(img, 13, 2, (100, 104, 112), 1.0)
        px(img, 13, 3, (60, 62, 68), 1.0)                         # keyhole
    return img


def door_item_tex(bottom, top):
    """A door item: both halves shrunk into a 16x16 picture, like vanilla's door items."""
    whole = Image.new("RGBA", (16, 32))
    whole.paste(top, (0, 0))
    whole.paste(bottom, (0, 16))
    small = whole.resize((8, 16), Image.NEAREST)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    img.paste(small, (4, 0))
    return img


def exit_sign_tex(rng):
    """The lit EXIT box: its face (12x6 at the top left) green with white letters and a running
    man's arrow; the rest is the grey casing for its sides."""
    img = new((150, 154, 160))
    noise(img, rng, 3)
    rect(img, 0, 0, 11, 5, (34, 168, 88))
    letters = {"E": ["##", "#.", "##", "#.", "##"], "X": ["#.#", "#.#", ".#.", "#.#", "#.#"],
               "I": ["#", "#", "#", "#", "#"], "T": ["###", ".#.", ".#.", ".#.", ".#."]}
    x = 1
    for ch in "EXIT":
        rows = letters[ch]
        for ry, row in enumerate(rows):
            for rx, bit in enumerate(row):
                if bit == "#":
                    img.putpixel((x + rx, 0 + ry), (240, 250, 244, 255))
        x += len(rows[0]) + 1
    return img


def exit_sign_glow(x, y, c):
    if x < 12 and y < 6:
        return T.PLASTIC.but(smoothness=0.7, relief=0.0, emission=GLOW["TUBE_GLOW"])
    return T.PAINTED_METAL


def checker_plate_tex(rng):
    """Steel checker plate (the elevator's floor): staggered raised lozenges, worn bright on top,
    dirt in the low parts."""
    img = new((112, 118, 124))
    noise(img, rng, 4)
    for gy in range(0, 16, 4):
        for gx in range(0, 16, 4):
            ox = gx + (2 if (gy // 4) % 2 else 0)
            diagonal = (gx // 4 + gy // 4) % 2 == 0
            cells = [(ox, gy + 1), (ox + 1, gy + 2)] if diagonal else [(ox + 1, gy + 1), (ox, gy + 2)]
            for i, (x, y) in enumerate(cells):
                px(img, x, y, (170, 176, 182) if i == 0 else (78, 82, 88), 0.85)
    T.blob(img, rng, (70, 72, 76), 5, 0.3)
    speckle(img, rng, 8, (60, 62, 66), 0.5)
    return img


def bunker_trapdoor_tex(rng):
    """One block of a bunker hatch leaf (the hinge along the bottom rows, the south of a model
    facing north): dark painted steel checker plate, a yellow and black band along the hinge,
    a bolt head in each corner."""
    img = checker_plate_tex(rng)
    for y in range(16):
        for x in range(16):
            r, g, b, a = img.getpixel((x, y))
            img.putpixel((x, y), (T.clamp(r * 0.62), T.clamp(g * 0.66), T.clamp(b * 0.6), a))
    for y in range(13, 16):
        for x in range(16):
            px(img, x, y, (214, 168, 32) if (x + y) // 3 % 2 == 0 else (28, 28, 30), 0.95)
    rect(img, 0, 12, 15, 12, (36, 38, 40), 0.8)
    for x, y in ((1, 1), (14, 1), (1, 10), (14, 10)):
        px(img, x, y, (150, 154, 158))
    return img


def grating_tex(rng):
    """Steel bar grating, painted dark and worn bright along the bars: a grid of square holes
    (see-through) with rust in the corners. The treads of the spiral stairs down to B3."""
    img = new((84, 88, 92))
    noise(img, rng, 5)
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0:
                px(img, x, y, (128, 132, 136), 0.35)              # bar tops, rubbed by boots
    for y in range(1, 16, 4):
        for x in range(1, 16, 4):
            rect(img, x, y, x + 2, y + 2, (0, 0, 0, 0))
    speckle(img, rng, 10, (112, 72, 44), 0.6)                    # rust
    T.blob(img, rng, (60, 62, 64), 4, 0.25)
    return img


def moving_pad_tex(rng):
    """A quilted moving blanket, faded blue-grey, diamond stitching (it joins across blocks)."""
    img = new((74, 88, 104))
    noise(img, rng, 4)
    for y in range(16):
        for x in range(16):
            if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                px(img, x, y, (52, 62, 74), 0.8)                  # stitches
            elif (x + y) % 8 in (1, 2) and (x - y) % 8 in (1, 2):
                px(img, x, y, (98, 112, 128), 0.5)                # the padding bulging between them
    T.blob(img, rng, (66, 70, 74), 4, 0.25)
    return img


def handrail_tex(rng):
    img = new((168, 172, 178))
    noise(img, rng, 4)
    for x in range(16):
        px(img, x, 0, (210, 214, 218), 0.6)
        px(img, x, 15, (110, 114, 120), 0.6)
    return img


def elevator_buttons_tex(rng, call):
    """The cabin's panel (two columns of round buttons, B1 lit amber, a speaker grille and a
    keyhole; its face is 8x14 at x 4-11, y 1-14) or the landing's call panel (one lit down button
    under a dark up button; 6x10 at x 5-10, y 3-12)."""
    img = new((150, 154, 160))
    noise(img, rng, 3)
    dents(img, rng, 1, scratches=1)
    ring, lit, dark = (70, 72, 78), (236, 164, 52), (104, 108, 114)
    if call:
        rect(img, 5, 3, 10, 12, (138, 142, 148))
        for x in range(6, 10):                                    # up: a dark triangle
            px(img, x, 6, dark)
        px(img, 7, 5, dark)
        px(img, 8, 5, dark)
        for x in range(6, 10):                                    # down: a lit one
            px(img, x, 9, lit)
        px(img, 7, 10, lit)
        px(img, 8, 10, lit)
        return img
    rect(img, 4, 1, 11, 14, (138, 142, 148))
    for x in range(6, 10):                                        # speaker grille
        for y in (2, 4):
            px(img, x, y, (60, 62, 66), 0.9)
    buttons = [(6, 6), (9, 6), (6, 8), (9, 8), (6, 10), (9, 10)]
    for i, (x, y) in enumerate(buttons):
        px(img, x, y, ring)
        px(img, x + 1, y, lit if i == 5 else dark)
    px(img, 8, 13, (40, 40, 44))                                  # keyhole
    return img


def floor_indicator_tex(label, moving):
    """The floor indicator's LED display (12x5 at the top left): a down arrow, lit while the
    car moves, then the floor; unlit segments glow faintly."""
    img = new((150, 154, 160))
    off, on, bg = (70, 20, 16), (255, 76, 44), (16, 12, 12)
    rect(img, 0, 0, 11, 4, bg)
    arrow = [".#.", ".#.", ".#.", "###", ".#."]
    for y, row in enumerate(arrow):
        for x, bit in enumerate(row):
            if bit == "#":
                px(img, x, y, on if moving else off)
    width = len(label) * 4 - 1
    x0 = 4 + (8 - width) // 2
    for i, ch in enumerate(label):
        for y, row in enumerate(T.FONT[ch]):
            for x, bit in enumerate(row):
                if bit == "#":
                    px(img, x0 + i * 4 + x, y, on)
    return img


def led_and_metal(x, y, c):
    if c[0] > 200 and c[2] < 120:
        return T.PLASTIC.but(smoothness=0.7, relief=0.0, emission=GLOW["DIFFUSER_GLOW"])
    return T.STEEL.but(smoothness=0.55, metal=255, relief=0.2)


def static_frames(rng, frames=8):
    """The CCTV monitor's screen: grey static with a slow rolling bar, over the faint shapes
    of a dark parking aisle (the camera still works). The picture is 10x7 at the top left."""
    out = []
    for f in range(frames):
        img = new((20, 22, 24))
        bar = (f * 7 // frames) % 7
        for y in range(7):
            for x in range(10):
                v = rng.randint(40, 130)
                if y == bar:
                    v += 50
                if y >= 4 and x in (2, 3, 6, 7):                   # pillars down the aisle
                    v -= 25
                if y == 6 and 3 < x < 6:                          # the aisle's line
                    v += 20
                v = max(0, min(255, v))
                img.putpixel((x, y), (v, v + 4 if v < 250 else v, v + 8 if v < 246 else v, 255))
        out.append(img)
    return out


def monitor_tex(rng):
    """The monitor's dark plastic casing, vents on top, a dim green power light."""
    img = new((38, 40, 44))
    noise(img, rng, 3)
    for x in range(3, 13, 2):
        px(img, x, 1, (22, 22, 24), 0.9)                          # vents
    rect(img, 4, 12, 9, 12, (70, 72, 78))                         # brand strip under the screen
    px(img, 12, 12, (90, 200, 110))                               # power light
    return img


def plastic_tex(rng, colour, kind=None):
    """Plain moulded plastic or fabric, for small props; kind adds detail."""
    img = new(colour)
    noise(img, rng, 4 if kind == "fabric" else 2)
    if kind == "fabric":
        for y in range(0, 16, 2):
            for x in range(16):
                px(img, x, y, tuple(max(0, c - 10) for c in colour), 0.4)
    if kind == "keypad":
        for x in (6, 8, 10):
            for y in (2, 4, 6):
                px(img, x, y, (150, 154, 160), 0.9)
    return img


def logbook_tex(rng, part):
    """The logbook: ruled pages with handwriting (pages) or its dark red cloth cover."""
    if part == "cover":
        img = new((92, 30, 32))
        noise(img, rng, 4)
        return img
    img = new((224, 222, 212))
    noise(img, rng, 2)
    for y in range(1, 16, 2):
        for x in range(16):
            px(img, x, y, (170, 182, 196), 0.5)                   # ruled lines
        if y < 13:
            for x in range(1, rng.randrange(6, 15)):
                if rng.random() < 0.6:
                    px(img, x, y - 1, (40, 46, 70), 0.7)          # entries, in ink
    for y in range(16):
        px(img, 8, y, (180, 176, 166), 0.7)                       # the spine's fold
    return img


def mug_tex(rng, part):
    if part == "coffee":
        img = new((48, 30, 20))
        noise(img, rng, 3)
        return img
    img = new((222, 224, 226))
    noise(img, rng, 2)
    for x in range(16):
        px(img, x, 5, (60, 100, 170), 0.9)                        # a blue stripe
    return img


def torch_tex(rng):
    """The old flashlight: a yellow body, a black rubber head."""
    img = new((206, 170, 52))
    noise(img, rng, 3)
    rect(img, 0, 0, 4, 15, (30, 30, 32))
    return img


def key_rack_tex(rng):
    """A dark wooden board with small numbered tags; the hooks are separate."""
    img = new((70, 50, 36))
    noise(img, rng, 4)
    for y in range(16):
        if y % 4 == 0:
            for x in range(16):
                px(img, x, y, (56, 40, 28), 0.5)                  # grain
    for x in (3, 5, 7, 9, 11):
        px(img, x, 9, (214, 208, 190), 0.9)                       # the tags under the hooks
    return img


def shutter_tex(rng):
    """Rolling shutter curtain: four galvanized steel slats, each lit on its rounded top and
    shaded into the groove below, with rust creeping from the joints and grime."""
    img = new((138, 142, 145))
    for slat in range(4):
        y0 = slat * 4
        shades = (24, 10, -4, -34)                                  # top edge, face, lower face, groove
        for dy, shade in enumerate(shades):
            for x in range(16):
                e = shade + rng.uniform(-4, 4)
                r, g, b, a = img.getpixel((x, y0 + dy))
                img.putpixel((x, y0 + dy), (T.clamp(r + e), T.clamp(g + e), T.clamp(b + e), a))
        for _ in range(rng.randint(1, 3)):                           # rust bleeding out of the joint
            x = rng.randint(0, 15)
            px(img, x, y0 + 3, (112, 70, 44))
            if rng.random() < 0.6:
                px(img, x, (y0 + 4) % 16, (126, 86, 58))
    T.blob(img, rng, (96, 92, 84), 4, 0.3)                           # grime
    return img


def light_tex(rng, part):
    if part == "housing":
        img = new((196, 196, 190))
        noise(img, rng, 4)
        T.blob(img, rng, (150, 140, 110), 5, 0.35)                # nicotine-yellowed enamel
        speckle(img, rng, 4, (90, 88, 84), 0.7)                   # dead insects
        return img
    lit = part == "tubes_on"
    img = new((246, 248, 244) if lit else (146, 148, 146))
    for x in range(16):
        for y in range(16):
            edge = abs(y - 7.5) / 8
            c = mix((255, 255, 252), (222, 226, 224), edge) if lit else mix((156, 158, 156), (118, 120, 118), edge)
            img.putpixel((x, y), c + (255,))
    if not lit:
        rect(img, 0, 0, 1, 15, (70, 66, 60))                      # blackened tube ends
        rect(img, 14, 0, 15, 15, (70, 66, 60))
    return img


# =====================================================================================
# Models

def tex_ref(name):
    return f"{NS}:block/{name}"


def element(frm, to, texture, faces=SIDES, shade=True, uv_rotation=None):
    """A box; each listed face shows `texture` (a key in the model's textures, without '#')."""
    e = {"from": list(frm), "to": list(to), "faces": {}}
    for f in faces:
        face = {"texture": f"#{texture}"}
        if uv_rotation and f in ("up", "down"):
            face["rotation"] = uv_rotation
        e["faces"][f] = face
    if not shade:
        e["shade"] = False
    return e


def model(textures, elements, parent="minecraft:block/block", ao=True):
    m = {"parent": parent, "textures": {k: tex_ref(v) for k, v in textures.items()}, "elements": elements}
    m["textures"].setdefault("particle", next(iter(m["textures"].values())))
    if not ao:
        m["ambientocclusion"] = False
    return m


def cube_all(texture):
    return {"parent": "minecraft:block/cube_all", "textures": {"all": tex_ref(texture)}}


def cube(up, down, side, north=None):
    return {"parent": "minecraft:block/cube", "textures": {
        "up": tex_ref(up), "down": tex_ref(down), "north": tex_ref(north or side), "south": tex_ref(side),
        "east": tex_ref(side), "west": tex_ref(side), "particle": tex_ref(side)}}


def plane(texture):
    """A flat picture lying on the floor (y = 0.1), seen from above. Turned onto walls and
    ceilings by the blockstate (same rotations as StainBlock's shapes)."""
    return {"parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"t": tex_ref(texture), "particle": tex_ref(texture)},
            "elements": [{"from": [0, 0.1, 0], "to": [16, 0.1, 16], "faces": {"up": {"texture": "#t"}}}]}


# Rotations that turn the floor picture to face each way (the mark faces away from its surface).
PLANE_ROTATION = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
                  "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}


def door_variants(prefix):
    """A door's blockstate variants, laid out like vanilla's (checked against iron_door.json)."""
    base = {"east": 0, "south": 90, "west": 180, "north": 270}
    out = {}
    for facing, y in base.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in ("false", "true"):
                    rot = y + ((90 if hinge == "left" else 270) if opened == "true" else 0)
                    v = {"model": f"{prefix}_{part}_{hinge}{'_open' if opened == 'true' else ''}"}
                    if rot % 360:
                        v["y"] = rot % 360
                    out[f"facing={facing},half={half},hinge={hinge},open={opened}"] = v
    return out


def horizontal_blockstate(model_name):
    variants = {}
    for facing, y in HORIZONTAL.items():
        v = {"model": f"{NS}:block/{model_name}"}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    return {"variants": variants}


def connecting_blockstate(name, sides, extra_parts=()):
    parts = [{"apply": {"model": f"{NS}:block/{name}_core"}}]
    for side in sides:
        parts.append({"when": {side: "true"}, "apply": {"model": f"{NS}:block/{name}_{side}"}})
    parts += list(extra_parts)
    return {"multipart": parts}


# =====================================================================================
# Blocks

GLOW = G.PBR

BLOCK_ITEM_DISPLAY_LIGHT = {
    "gui": {"rotation": [-60, 30, 0], "translation": [0, -3, 0], "scale": [0.7, 0.7, 0.7]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [-90, 0, 0], "translation": [0, 0, -6], "scale": [0.6, 0.6, 0.6]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


class Assets:
    """Everything one run writes, collected first so it can be checked and then written at once."""

    def __init__(self):
        self.textures = {}       # name -> (image, material or rule, tiles)
        self.models = {}         # block model name -> json
        self.items = {}          # block id -> item model json
        self.blockstates = {}    # block id -> json
        self.names = {}          # block id -> English name
        self.item_textures = {}  # item texture name -> image (textures/item/)
        self.animated = {}       # block texture name -> ([16x16 frames], ticks per frame); no PBR maps
        self.loot_doors = set()  # door block ids: they drop from their lower half only

    def texture(self, name, img, material, tiles=True):
        self.textures[name] = (img, material, tiles)

    def block(self, block_id, name, blockstate, item):
        self.names[block_id] = name
        self.blockstates[block_id] = blockstate
        self.items[block_id] = item


def lit_tubes(x, y, c):
    if pbr.luminance(c) > 0.93:
        return T.PLASTIC.but(smoothness=0.6, relief=0.0, emission=GLOW["TUBE_GLOW"])
    return T.PLASTIC.but(smoothness=0.6, relief=0.0, emission=GLOW["DIFFUSER_GLOW"])


def build():
    a = Assets()
    r = rng_for

    # --- Fluorescent light: a shallow housing, a lip each side, two tubes -----------------
    a.texture("fluorescent_light_housing", light_tex(r("housing"), "housing"), T.PAINTED_METAL)
    a.texture("fluorescent_light_tubes_on", light_tex(r("on"), "tubes_on"), lit_tubes)
    a.texture("fluorescent_light_tubes_off", light_tex(r("off"), "tubes_off"), T.PLASTIC.but(smoothness=0.6, relief=0.2))
    for state in ("on", "off"):
        a.models[f"fluorescent_light_{state}"] = model(
            {"housing": "fluorescent_light_housing", "tubes": f"fluorescent_light_tubes_{state}"},
            [element((0, 13, 3), (16, 16, 13), "housing"),
             element((0, 12, 3), (16, 13, 4), "housing"),
             element((0, 12, 12), (16, 13, 13), "housing"),
             element((0, 12, 5), (16, 13, 7), "tubes", shade=state == "off"),
             element((0, 12, 9), (16, 13, 11), "tubes", shade=state == "off")])
    variants = {}
    for facing, (x_rot, y_by_axis) in {
            "down": (0, {"x": 0, "z": 90}), "up": (180, {"x": 0, "z": 90}),
            "north": (270, {"x": 0, "z": 0}), "east": (270, {"x": 90, "z": 90}),
            "south": (270, {"x": 180, "z": 180}), "west": (270, {"x": 270, "z": 270})}.items():
        for axis, y_rot in y_by_axis.items():
            for lit in ("true", "false"):
                v = {"model": f"{NS}:block/fluorescent_light_{'on' if lit == 'true' else 'off'}"}
                if x_rot:
                    v["x"] = x_rot
                if y_rot:
                    v["y"] = y_rot
                variants[f"facing={facing},axis={axis},lit={lit}"] = v
    a.block("fluorescent_light", "Fluorescent Light", {"variants": variants},
            {"parent": f"{NS}:block/fluorescent_light_on", "display": BLOCK_ITEM_DISPLAY_LIGHT})

    # --- Floor props ------------------------------------------------------------------
    a.texture("cardboard_box_top", T.cardboard_tex(r("box_top"), "top"), T.CARDBOARD)
    a.texture("cardboard_box_side", T.cardboard_tex(r("box_side"), "side"),
              lambda x, y, c: T.PAPER if T.near(c, (220, 216, 204), 20) else T.CARDBOARD)
    a.models["cardboard_box"] = cube("cardboard_box_top", "cardboard_box_top", "cardboard_box_side")
    a.block("cardboard_box", "Cardboard Box", horizontal_blockstate("cardboard_box"), {"parent": f"{NS}:block/cardboard_box"})

    a.texture("traffic_cone", T.cone_tex(r("cone")),
              lambda x, y, c: T.PLASTIC.but(smoothness=0.8) if T.near(c, (236, 236, 228), 24) else T.PLASTIC)
    a.models["traffic_cone"] = model({"t": "traffic_cone"}, [
        element((3, 0, 3), (13, 1, 13), "t"), element((5, 1, 5), (11, 5, 11), "t"),
        element((6, 5, 6), (10, 9, 10), "t"), element((7, 9, 7), (9, 13, 9), "t")])
    a.block("traffic_cone", "Traffic Cone", horizontal_blockstate("traffic_cone"), {"parent": f"{NS}:block/traffic_cone"})

    def extinguisher_material(x, y, c):
        if y <= 1:
            return T.STEEL
        return T.PAPER if T.near(c, (230, 226, 210), 24) else T.PAINTED_METAL.but(smoothness=0.75, relief=0.1)
    a.texture("fire_extinguisher", T.extinguisher_tex(r("ext")), extinguisher_material)
    a.texture("fire_extinguisher_hose", new((24, 24, 26)), T.PLASTIC.but(smoothness=0.3, relief=0.0))
    a.models["fire_extinguisher"] = model({"t": "fire_extinguisher", "h": "fire_extinguisher_hose"}, [
        element((5, 0, 5), (11, 10, 11), "t"), element((7, 10, 7), (9, 12, 9), "h"),
        element((6, 12, 7.5), (10, 12.5, 8.5), "h"), element((5, 4, 7.5), (5.5, 11, 8.5), "h")])
    a.block("fire_extinguisher", "Fire Extinguisher", horizontal_blockstate("fire_extinguisher"),
            {"parent": f"{NS}:block/fire_extinguisher"})

    a.texture("shopping_cart_mesh", T.mesh_tex(r("mesh")), T.CHROME, tiles=False)
    a.texture("shopping_cart_plastic", new((50, 50, 54)), T.PLASTIC.but(smoothness=0.35, relief=0.0))
    a.models["shopping_cart"] = model({"m": "shopping_cart_mesh", "d": "shopping_cart_plastic"}, [
        element((1, 4, 2), (15, 12, 14), "m"),
        element((1, 11, 14), (15, 12, 16), "d"),                    # handle, at the back (south)
        element((2, 2, 3), (14, 4, 13), "m", faces=["up", "down"]),  # bottom shelf
        element((3, 0, 3), (5, 2, 5), "d"), element((11, 0, 3), (13, 2, 5), "d"),
        element((3, 0, 11), (5, 2, 13), "d"), element((11, 0, 11), (13, 2, 13), "d")], ao=False)
    a.block("shopping_cart", "Shopping Cart", horizontal_blockstate("shopping_cart"), {"parent": f"{NS}:block/shopping_cart"})

    a.texture("pallet_top", wood_tex(r("pallet_top"), True), T.Material(0.12, porosity=0.8, relief=0.8))
    a.texture("pallet_side", wood_tex(r("pallet_side"), False), T.Material(0.10, porosity=0.8, relief=0.8))
    a.models["pallet"] = model({"top": "pallet_top", "side": "pallet_side"}, [
        element((0, 2, 0), (16, 3, 16), "top"),
        element((0, 0, 0), (16, 2, 3), "side"), element((0, 0, 6.5), (16, 2, 9.5), "side"),
        element((0, 0, 13), (16, 2, 16), "side")])
    a.block("pallet", "Pallet", horizontal_blockstate("pallet"), {"parent": f"{NS}:block/pallet"})

    a.texture("oil_drum_side", drum_tex(r("drum_side"), False),
              lambda x, y, c: T.RUST_FLAKE if c[0] > c[2] else T.PAINTED_METAL.but(smoothness=0.4))
    a.texture("oil_drum_top", drum_tex(r("drum_top"), True), T.PAINTED_METAL.but(smoothness=0.4))
    a.models["oil_drum"] = model({"side": "oil_drum_side", "top": "oil_drum_top"}, [
        element((2, 0, 2), (14, 16, 14), "side", faces=["north", "east", "south", "west"]),
        element((2, 0, 2), (14, 16, 14), "top", faces=["up", "down"]),
        element((1.5, 4, 1.5), (14.5, 5, 14.5), "side", faces=["north", "east", "south", "west"]),
        element((1.5, 11, 1.5), (14.5, 12, 14.5), "side", faces=["north", "east", "south", "west"])])
    a.block("oil_drum", "Oil Drum", horizontal_blockstate("oil_drum"), {"parent": f"{NS}:block/oil_drum"})

    a.texture("trash_bin_side", bin_tex(r("bin_side"), False), T.PLASTIC.but(smoothness=0.3))
    a.texture("trash_bin_top", bin_tex(r("bin_top"), True), T.PLASTIC.but(smoothness=0.3))
    a.models["trash_bin"] = model({"side": "trash_bin_side", "top": "trash_bin_top"}, [
        element((3, 0, 3), (13, 13, 13), "side", faces=["north", "east", "south", "west", "down"]),
        element((2.5, 13, 2.5), (13.5, 14, 13.5), "top")])
    a.block("trash_bin", "Trash Bin", horizontal_blockstate("trash_bin"), {"parent": f"{NS}:block/trash_bin"})

    a.texture("wheel_stop", wheel_stop_tex(r("wheel_stop")),
              lambda x, y, c: T.FLOOR_PAINT if T.near(c, T.PAINT_YELLOW, 50) else T.CONCRETE_FLOOR)
    a.models["wheel_stop"] = model({"t": "wheel_stop"}, [element((0, 0, 5), (16, 3, 11), "t")])
    a.block("wheel_stop", "Wheel Stop", horizontal_blockstate("wheel_stop"), {"parent": f"{NS}:block/wheel_stop"})

    # --- Ceiling services: connecting runs ---------------------------------------------------
    a.texture("pipe", pipe_tex(r("pipe")), lambda x, y, c: T.RUST_FLAKE if T.near(c, T.RUST, 30) else T.PAINTED_METAL)
    a.texture("duct", T.duct_tex(r("duct")), lambda x, y, c: T.RUST_FLAKE if T.near(c, (120, 110, 96), 16) else T.STEEL)
    for name, core, arms in (
            ("pipe", ((5, 5, 5), (11, 11, 11)), {
                "north": ((5, 5, 0), (11, 11, 5)), "south": ((5, 5, 11), (11, 11, 16)),
                "west": ((0, 5, 5), (5, 11, 11)), "east": ((11, 5, 5), (16, 11, 11)),
                "up": ((5, 11, 5), (11, 16, 11)), "down": ((5, 0, 5), (11, 5, 11))}),
            ("duct", ((2, 3, 2), (14, 13, 14)), {
                "north": ((2, 3, 0), (14, 13, 2)), "south": ((2, 3, 14), (14, 13, 16)),
                "west": ((0, 3, 2), (2, 13, 14)), "east": ((14, 3, 2), (16, 13, 14)),
                "up": ((2, 13, 2), (14, 16, 14)), "down": ((2, 0, 2), (14, 3, 14))})):
        a.models[f"{name}_core"] = model({"t": name}, [element(*core, "t")])
        for side, box in arms.items():
            a.models[f"{name}_{side}"] = model({"t": name}, [element(*box, "t")])
        a.models[f"{name}_inventory"] = model({"t": name}, [element(*core, "t"), element(*arms["east"], "t"),
                                                            element(*arms["west"], "t")])
        a.block(name, {"pipe": "Pipe", "duct": "Air Duct"}[name], connecting_blockstate(name, SIDES),
                {"parent": f"{NS}:block/{name}_inventory"})

    # Cable tray: a perforated bottom with cables in it; a rail closes every side that does
    # not connect, so straight runs, corners and junctions all get the right edges.
    a.texture("cable_tray_bottom", tray_tex(r("tray_bottom"), "bottom"), T.STEEL.but(relief=0.3), tiles=False)
    a.texture("cable_tray_rail", tray_tex(r("tray_rail"), "rail"), T.STEEL.but(relief=0.3), tiles=False)
    a.texture("cable_tray_cables", tray_tex(r("tray_cables"), "cables"), T.PLASTIC.but(smoothness=0.35, relief=0.6))
    tray = {"b": "cable_tray_bottom", "r": "cable_tray_rail", "c": "cable_tray_cables"}
    a.models["cable_tray_core"] = model(tray, [element((2, 9, 2), (14, 10, 14), "b"),
                                              element((3, 10, 3), (13, 11.5, 13), "c")], ao=False)
    arm_boxes = {"north": ((2, 9, 0), (14, 10, 2), (3, 10, 0), (13, 11.5, 3)),
                 "south": ((2, 9, 14), (14, 10, 16), (3, 10, 13), (13, 11.5, 16)),
                 "west": ((0, 9, 2), (2, 10, 14), (0, 10, 3), (3, 11.5, 13)),
                 "east": ((14, 9, 2), (16, 10, 14), (13, 10, 3), (16, 11.5, 13))}
    rail_boxes = {"north": ((2, 9, 1), (14, 13, 2)), "south": ((2, 9, 14), (14, 13, 15)),
                  "west": ((1, 9, 2), (2, 13, 14)), "east": ((14, 9, 2), (15, 13, 14))}
    for side, (b0, b1, c0, c1) in arm_boxes.items():
        rot = 90 if side in ("north", "south") else None
        a.models[f"cable_tray_{side}"] = model(tray, [element(b0, b1, "b"), element(c0, c1, "c", uv_rotation=rot)], ao=False)
        a.models[f"cable_tray_rail_{side}"] = model(tray, [element(*rail_boxes[side], "r")], ao=False)
    rails = [{"when": {side: "false"}, "apply": {"model": f"{NS}:block/cable_tray_rail_{side}"}} for side in rail_boxes]
    a.models["cable_tray_inventory"] = model(tray, [
        element((0, 9, 2), (16, 10, 14), "b"), element((0, 10, 3), (16, 11.5, 13), "c"),
        element(*rail_boxes["north"], "r"), element(*rail_boxes["south"], "r")], ao=False)
    a.block("cable_tray", "Cable Tray", connecting_blockstate("cable_tray", ["north", "east", "south", "west"], rails),
            {"parent": f"{NS}:block/cable_tray_inventory"})

    a.texture("hanging_cable", cable_tex(r("cable")), T.PLASTIC.but(smoothness=0.3, relief=0.2))
    a.models["hanging_cable"] = model({"t": "hanging_cable"}, [
        element((7, 8, 7), (8.5, 16, 8.5), "t"), element((7.5, 0, 7.5), (9, 8, 9), "t")], ao=False)
    a.block("hanging_cable", "Hanging Cable", horizontal_blockstate("hanging_cable"),
            {"parent": "minecraft:item/generated", "textures": {"layer0": tex_ref("hanging_cable")}})

    # --- Surfaces -------------------------------------------------------------------------
    a.texture("metal_panel", metal_panel_tex(r("panel")), T.STEEL.but(smoothness=0.6, metal=255, relief=0.3))
    a.models["metal_panel"] = cube_all("metal_panel")
    a.block("metal_panel", "Metal Panel", {"variants": {"": {"model": f"{NS}:block/metal_panel"}}},
            {"parent": f"{NS}:block/metal_panel"})

    # Elevator door: a 2-pixel sheet that slides 5 pixels per stage into the wall beside it
    # (ElevatorDoorBlock.SLIDE_PIXELS); open is air. axis = the way it runs, positive = opens to +x/+z.
    a.texture("elevator_door", elevator_door_tex(r("elevator_door")), T.STEEL.but(smoothness=0.65, metal=255, relief=0.2))
    variants = {}
    for positive, sign in (("true", 1), ("false", -1)):
        for stage in range(3):
            name = f"elevator_door_{'pos' if sign > 0 else 'neg'}_{stage}"
            dx = sign * stage * 5
            # Explicit UVs: the sheet reaches outside the block, where automatic UVs would leave the texture.
            uvs = {"north": [0, 0, 16, 16], "south": [0, 0, 16, 16], "east": [7, 0, 9, 16], "west": [7, 0, 9, 16],
                   "up": [0, 7, 16, 9], "down": [0, 7, 16, 9]}
            a.models[name] = model({"t": "elevator_door"}, [{
                "from": [dx, 0, 7], "to": [16 + dx, 16, 9],
                "faces": {f: {"texture": "#t", "uv": uv} for f, uv in uvs.items()}}])
            for axis, y in (("x", 0), ("z", 90)):
                v = {"model": f"{NS}:block/{name}"}
                if y:
                    v["y"] = y
                variants[f"axis={axis},positive={positive},stage={stage}"] = v
    a.block("elevator_door", "Elevator Door", {"variants": variants}, {"parent": f"{NS}:block/elevator_door_pos_0"})

    # Loot lying around: the client draws the item itself (LootRenderer), so the model is empty
    # (only a particle texture); the block item looks like the item.
    for block_id, name, item in (("loot_battery", "Battery (Loot)", "battery"),
                                 ("loot_water_bottle", "Water Bottle (Loot)", "water_bottle"),
                                 ("loot_keycard", "Keycard (Loot)", "keycard")):
        a.models[block_id] = {"textures": {"particle": f"{NS}:item/{item}"}}
        a.block(block_id, name, horizontal_blockstate(block_id), {"parent": f"{NS}:item/{item}"})

    # Card reader: a small box on the wall (CardReaderBlock's shape), red light locked, green open.
    a.texture("card_reader_side", card_reader_tex(r("reader_side"), "side"), T.PLASTIC.but(smoothness=0.5, relief=0.3))
    variants = {}
    for unlocked, part in (("false", "locked"), ("true", "open")):
        a.texture(f"card_reader_{part}", card_reader_tex(r("reader"), part), led_glow)
        a.models[f"card_reader_{part}"] = model(
            {"front": f"card_reader_{part}", "side": "card_reader_side"},
            [{"from": [5, 4, 14.5], "to": [11, 12, 16], "faces": {
                "north": {"texture": "#front"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
                "west": {"texture": "#side"}, "up": {"texture": "#side"}, "down": {"texture": "#side"}}}])
        for facing, y in HORIZONTAL.items():
            v = {"model": f"{NS}:block/card_reader_{part}"}
            if y:
                v["y"] = y
            variants[f"facing={facing},unlocked={unlocked}"] = v
    a.block("card_reader", "Card Reader", {"variants": variants}, {"parent": f"{NS}:block/card_reader_locked"})

    def facing_parts(models, when=None):
        """Multipart parts turning `models` (one name, or several picked at random) to each facing."""
        out = []
        for facing, y in HORIZONTAL.items():
            apply = [{"model": f"{NS}:block/{m}", **({"y": y} if y else {})} for m in ([models] if isinstance(models, str) else models)]
            out.append({"when": {"facing": facing, **(when or {})}, "apply": apply if len(apply) > 1 else apply[0]})
        return out

    # Valve: a gate valve inline in a wall pipe (ValveBlock's shapes), facing north: the pipe
    # through it, a stub back into the wall, the body, the red handwheel on its hub; open, the
    # rising stem stands out through the wheel. On each side a plate: the letter on the left,
    # the dial's window with its turns on the right, as seen from that side. A multipart: the
    # body by open, the letter halves by letter, the windows by turns.
    a.texture("valve_body", valve_body_tex(r("valve_body")), T.PAINTED_METAL.but(smoothness=0.35))
    a.texture("valve_stem", valve_stem_tex(r("valve_stem")), T.STEEL.but(smoothness=0.6, metal=255))
    for turned in (False, True):
        a.texture(f"valve_wheel{'_turned' if turned else ''}", valve_wheel_tex(r("valve_wheel"), turned),
                  T.PAINTED_METAL.but(smoothness=0.5, relief=0.4), tiles=False)
    parts = []
    for opened in ("false", "true"):
        name = f"valve_{'open' if opened == 'true' else 'closed'}"
        elements = [
            element((5, 0, 5), (11, 16, 11), "pipe"),
            element((5, 5, 11), (11, 11, 16), "pipe"),
            element((4, 4, 4), (12, 12, 12), "body"),
            element((7, 7, 1), (9, 9, 4), "stem"),
            {"from": [2, 2, 2], "to": [14, 14, 3], "faces": {
                "north": {"uv": [2, 2, 14, 14], "texture": "#wheel"},
                "south": {"uv": [2, 2, 14, 14], "texture": "#wheel"}}},
        ]
        if opened == "true":
            elements.append(element((7.5, 7.5, -2), (8.5, 8.5, 1), "stem"))
        a.models[name] = model({"pipe": "pipe", "body": "valve_body", "stem": "valve_stem",
                                "wheel": "valve_wheel_turned" if opened == "true" else "valve_wheel"}, elements)
        parts.append((name, {"open": opened}))

    def plate_halves(key, east_z, west_z, uv):
        return [{"from": [12, 5, east_z[0]], "to": [12.25, 12, east_z[1]], "faces": {"east": {"uv": uv, "texture": f"#{key}"}}},
                {"from": [3.75, 5, west_z[0]], "to": [4, 12, west_z[1]], "faces": {"west": {"uv": uv, "texture": f"#{key}"}}}]

    for letter in VALVE_LETTERS:
        a.texture(f"valve_plate_{letter}", valve_plate_tex(r("valve_plate"), letter), T.PAINTED_METAL.but(smoothness=0.6, relief=0.2))
        a.models[f"valve_letter_{letter}"] = model({"p": f"valve_plate_{letter}"}, plate_halves("p", (8, 12), (4, 8), [4, 4, 8, 11]))
        parts.append((f"valve_letter_{letter}", {"letter": letter}))
    for digit in range(10):
        a.texture(f"valve_digit_{digit}", valve_digit_tex(r("valve_digit"), digit), T.PAINTED_METAL.but(smoothness=0.7, relief=0.1))
        a.models[f"valve_digit_{digit}"] = model({"d": f"valve_digit_{digit}"}, plate_halves("d", (4, 8), (8, 12), [8, 4, 12, 11]))
        parts.append((f"valve_digit_{digit}", {"turns": str(digit)}))
    multipart = []
    for name, when in parts:
        multipart += facing_parts(name, when)
    inventory = a.models["valve_closed"]["elements"] + a.models["valve_letter_a"]["elements"] + a.models["valve_digit_0"]["elements"]
    a.models["valve_inventory"] = model({"pipe": "pipe", "body": "valve_body", "stem": "valve_stem", "wheel": "valve_wheel",
                                         "p": "valve_plate_a", "d": "valve_digit_0"}, inventory)
    a.block("valve", "Valve", {"multipart": multipart}, {"parent": f"{NS}:block/valve_inventory"})

    # Note board: cork on a wall (NoteBoardBlock's shape), typed sheets pinned to it.
    a.texture("cork", cork_tex(r("cork")), T.Material(0.05, porosity=0.9, relief=0.7))
    a.texture("pinned_note", pinned_note_tex(r("pinned_note")), T.PAPER)
    a.texture("pin_red", plastic_tex(r("pin_red"), (180, 40, 36)), T.PLASTIC.but(smoothness=0.7))
    a.models["note_board"] = model({"c": "cork", "n": "pinned_note", "r": "pin_red"}, [
        element((1, 2, 15), (15, 14, 16), "c"),
        element((3, 4, 14.8), (9, 13, 15), "n"), element((8, 3, 14.6), (13, 10, 14.8), "n"),
        element((5.5, 12, 14.4), (6.5, 13, 14.8), "r"), element((10, 9, 14.2), (11, 10, 14.6), "r")])
    a.block("note_board", "Note Board", horizontal_blockstate("note_board"), {"parent": f"{NS}:block/note_board"})

    # --- Level 3 furniture (FurnitureBlocks.kt): models facing north, the front at z = 0. Row
    # pieces put their end pieces where they do not join (left = east, as seen from the front),
    # stacks their base where nothing is below, tiles a leg at each corner that is an outside one.
    a.texture("laminate", laminate_tex(r("laminate")), T.Material(0.5, porosity=0.1, relief=0.2))
    a.texture("office_metal", powder_metal_tex(r("office_metal")), T.PAINTED_METAL.but(smoothness=0.4))
    a.texture("dark_metal", powder_metal_tex(r("dark_metal"), (60, 62, 66)), T.PAINTED_METAL.but(smoothness=0.35))
    a.texture("library_wood", dark_wood_tex(r("library_wood")), T.Material(0.4, porosity=0.2, relief=0.5))
    a.texture("filing_front", filing_front_tex(r("filing_front")), T.PAINTED_METAL.but(smoothness=0.4))
    a.texture("whiteboard", whiteboard_tex(r("whiteboard")), T.Material(0.85, relief=0.0))
    a.texture("aluminium", powder_metal_tex(r("aluminium"), (192, 196, 200)), T.STEEL.but(smoothness=0.6, metal=232))
    a.texture("sofa_fabric", plastic_tex(r("sofa_fabric"), (72, 80, 94), "fabric"), T.Material(0.05, porosity=0.9, relief=0.5))
    a.texture("rack_black", powder_metal_tex(r("rack_black"), (30, 32, 34)), T.PAINTED_METAL.but(smoothness=0.3))
    a.texture("server_front", server_front_tex(r("server_front")), leds_glow)
    a.texture("electrical_front", electrical_front_tex(r("electrical_front")), T.PAINTED_METAL.but(smoothness=0.4))
    for part in ("upper", "lower"):
        a.texture(f"locker_{part}", locker_tex(r("locker"), part), T.PAINTED_METAL.but(smoothness=0.4))
        a.texture(f"vending_{part}", vending_tex(r("vending"), part), vending_glow)
    a.texture("vending_side", plastic_tex(r("vending_side"), (150, 40, 36)), T.PAINTED_METAL)
    a.texture("white_plastic", plastic_tex(r("white_plastic"), (226, 226, 222)), T.PLASTIC.but(smoothness=0.55))
    a.texture("grey_plastic", plastic_tex(r("grey_plastic"), (150, 160, 168)), T.PLASTIC)
    a.texture("black_plastic", plastic_tex(r("black_plastic"), (28, 28, 30)), T.PLASTIC.but(smoothness=0.55))
    a.texture("counter_front", laminate_tex(r("counter_front"), (150, 152, 154)), T.Material(0.5, porosity=0.1, relief=0.2))
    a.texture("bottle_blue", plastic_tex(r("bottle_blue"), (110, 160, 200)), T.Material(0.85, relief=0.0))
    a.texture("plant_leaves", leaves_tex(r("plant_leaves")), T.PLASTIC.but(smoothness=0.5), tiles=False)
    a.texture("wall_clock", clock_tex(r("wall_clock")), T.PLASTIC.but(smoothness=0.6), tiles=False)
    a.texture("card_catalog_front", card_catalog_tex(r("card_catalog")), T.Material(0.4, porosity=0.2, relief=0.5))
    a.texture("library_panel", OT.wood_tex(r("library_panel"), "panel"), T.Material(0.4, porosity=0.2, relief=0.5))
    books = [f"books_{i}" for i in range(4)]
    for name in books:
        a.texture(name, books_tex(r(name)), T.Material(0.2, porosity=0.4, relief=0.6))

    def el(frm, to, tex, **faces):
        """A box showing `tex` on every face, but where `faces` say: key, (key, uv), or None (no face)."""
        e = element(frm, to, tex)
        for f, v in faces.items():
            if v is None:
                del e["faces"][f]
                continue
            key, uv = (v, None) if isinstance(v, str) else v
            e["faces"][f] = {"texture": f"#{key}", **({"uv": list(uv)} if uv else {})}
        return e

    def row(block_id, name, textures, core, left=(), right=(), ao=True):
        """A row piece: `core` always, `left` / `right` (the end pieces) where it does not join."""
        parts = []
        for key, els in (("core", core), ("left", left), ("right", right)):
            if els:
                a.models[f"{block_id}_{key}"] = model(textures, list(els), ao=ao)
                parts += facing_parts(f"{block_id}_{key}", None if key == "core" else {key: "false"})
        a.models[f"{block_id}_inventory"] = model(textures, list(core) + list(left) + list(right), ao=ao)
        a.block(block_id, name, {"multipart": parts}, {"parent": f"{NS}:block/{block_id}_inventory"})

    def stack(block_id, name, textures, core, base=(), upper=None):
        """A stack piece: `core` (several = picked at random, as names of models already made, or
        one list of elements), `base` where nothing is below, and with `upper` a different body
        for the blocks that stand on another (core is then the lowest one's)."""
        parts = []
        if isinstance(core, list) and core and isinstance(core[0], str):
            parts += facing_parts(core)
            inventory = a.models[core[0]]["elements"]
        else:
            a.models[f"{block_id}_core"] = model(textures, list(core))
            parts += facing_parts(f"{block_id}_core", {"down": "false"} if upper else None)
            inventory = list(core)
        if upper:
            a.models[f"{block_id}_upper"] = model(textures, list(upper))
            parts += facing_parts(f"{block_id}_upper", {"down": "true"})
        if base:
            a.models[f"{block_id}_base"] = model(textures, list(base))
            parts += facing_parts(f"{block_id}_base", {"down": "false"})
        a.models[f"{block_id}_inventory"] = model(textures, list(inventory) + list(base))
        a.block(block_id, name, {"multipart": parts}, {"parent": f"{NS}:block/{block_id}_inventory"})

    def tile(block_id, name, textures, top, leg):
        """A table tile: the top always, a leg (x0, x1) x (z0, z1) at each outside corner."""
        corners = {"nw": ((1, 3), (1, 3), "north", "west"), "ne": ((13, 15), (1, 3), "north", "east"),
                   "sw": ((1, 3), (13, 15), "south", "west"), "se": ((13, 15), (13, 15), "south", "east")}
        a.models[f"{block_id}_top"] = model(textures, list(top))
        parts = [{"apply": {"model": f"{NS}:block/{block_id}_top"}}]
        legs = []
        for key, ((x0, x1), (z0, z1), s1, s2) in corners.items():
            e = el((x0, 0, z0), (x1, leg[0], z1), leg[1])
            a.models[f"{block_id}_leg_{key}"] = model(textures, [e])
            parts.append({"when": {s1: "false", s2: "false"}, "apply": {"model": f"{NS}:block/{block_id}_leg_{key}"}})
            legs.append(e)
        a.models[f"{block_id}_inventory"] = model(textures, list(top) + legs)
        a.block(block_id, name, {"multipart": parts}, {"parent": f"{NS}:block/{block_id}_inventory"})

    def prop(block_id, name, textures, els, ao=True):
        a.models[block_id] = model(textures, list(els), ao=ao)
        a.block(block_id, name, horizontal_blockstate(block_id), {"parent": f"{NS}:block/{block_id}"})

    # Offices.
    row("office_desk", "Office Desk", {"l": "laminate", "m": "office_metal"},
        [el((0, 15, 0), (16, 16, 16), "l"), el((0, 2, 13), (16, 15, 14), "m")],
        left=[el((15, 0, 1), (16, 15, 15), "m")], right=[el((0, 0, 1), (1, 15, 15), "m")])
    prop("filing_cabinet", "Filing Cabinet", {"m": "office_metal", "f": "filing_front"},
         [el((1, 0, 2), (15, 16, 16), "m", north="f")])
    tile("meeting_table", "Meeting Table", {"l": "laminate", "m": "office_metal"},
         [el((0, 11, 0), (16, 12, 16), "l")], (11, "m"))
    row("whiteboard", "Whiteboard", {"w": "whiteboard", "a": "aluminium"},
        [el((0, 4, 15), (16, 13, 16), "w", east=None, west=None), el((0, 13, 14.5), (16, 14, 16), "a"),
         el((0, 3, 14), (16, 4, 16), "a")],
        left=[el((15, 4, 14.5), (16, 13, 16), "a")], right=[el((0, 4, 14.5), (1, 13, 16), "a")])
    row("sofa", "Sofa", {"f": "sofa_fabric", "d": "dark_metal"},
        [el((0, 0, 2), (16, 2, 14), "d"), el((0, 2, 1), (16, 6, 13), "f"), el((0, 2, 12), (16, 14, 16), "f")],
        left=[el((13, 2, 1), (16, 10, 16), "f")], right=[el((0, 2, 1), (3, 10, 16), "f")])

    # Services.
    stack("server_rack", "Server Rack", {"b": "rack_black", "f": "server_front"},
          [el((1, 0, 1), (15, 16, 15), "b", north="f")], base=[el((1, 0, 1), (15, 1, 15), "b")])
    box_side = "cardboard_box_side"
    stack("metal_shelf", "Metal Shelf", {"m": "office_metal", "c": box_side},
          [el((0, 0, 2), (1, 16, 3), "m"), el((15, 0, 2), (16, 16, 3), "m"), el((0, 0, 15), (1, 16, 16), "m"),
           el((15, 0, 15), (16, 16, 16), "m"), el((0, 7, 2), (16, 8, 16), "m"),
           el((1, 8, 4), (7, 13, 14), "c"), el((8, 8, 6), (15, 12, 15), "c"), el((2, 0, 5), (9, 5, 15), "c")],
          base=[el((0, 0, 2), (16, 1, 16), "m")])
    prop("electrical_panel", "Electrical Panel", {"m": "office_metal", "f": "electrical_front"},
         [el((2, 2, 12), (14, 15, 16), "m", north="f")])

    # Staff rooms.
    stack("locker", "Locker", {"m": "office_metal", "l": "locker_lower", "u": "locker_upper"},
          [el((1, 0, 1), (15, 16, 16), "m", north="l")], upper=[el((1, 0, 1), (15, 16, 16), "m", north="u")])
    row("bench", "Bench", {"l": "laminate", "m": "office_metal"},
        [el((0, 6, 4), (16, 7.5, 12), "l")],
        left=[el((13, 0, 5), (15, 6, 11), "m")], right=[el((1, 0, 5), (3, 6, 11), "m")])
    prop("cafe_table", "Cafe Table", {"w": "white_plastic", "m": "office_metal"},
         [el((1, 11, 1), (15, 12, 15), "w"), el((7, 1, 7), (9, 11, 9), "m"), el((4, 0, 4), (12, 1, 12), "m")])
    prop("cafe_chair", "Cafe Chair", {"p": "grey_plastic", "m": "office_metal"},
         [el((3, 6, 3), (13, 7, 13), "p"), el((3, 7, 12), (13, 15, 13), "p")]
         + [el((x, 0, z), (x + 1, 6, z + 1), "m") for x in (3, 12) for z in (3, 12)])
    row("counter", "Counter", {"l": "laminate", "f": "counter_front"},
        [el((0, 0, 1), (16, 15, 16), "f"), el((0, 15, 0), (16, 16, 16), "l")])
    stack("vending_machine", "Vending Machine", {"s": "vending_side", "l": "vending_lower", "u": "vending_upper"},
          [el((1, 0, 1), (15, 16, 16), "s", north="l")], upper=[el((1, 0, 1), (15, 16, 16), "s", north="u")])
    prop("coffee_machine", "Coffee Machine", {"b": "black_plastic", "s": "aluminium"},
         [el((4, 0, 6), (12, 10, 13), "b"), el((6, 5, 5), (10, 7, 6), "s"), el((5, 0, 4), (11, 1, 6), "s")])
    prop("water_dispenser", "Water Dispenser", {"w": "white_plastic", "b": "bottle_blue", "s": "aluminium"},
         [el((4, 0, 4), (12, 11, 12), "w"), el((5, 11, 5), (11, 16, 11), "b"), el((6, 7, 3), (10, 8, 4), "s")])

    # Details.
    leaves = [{"from": [0.8, 4, 8], "to": [15.2, 16, 8], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle, "rescale": True},
               "shade": False, "faces": {"north": {"texture": "#v"}, "south": {"texture": "#v"}}} for angle in (45, -45)]
    prop("potted_plant", "Potted Plant", {"d": "dark_metal", "v": "plant_leaves"},
         [el((5, 0, 5), (11, 5, 11), "d")] + leaves, ao=False)
    prop("wall_clock", "Wall Clock", {"c": "wall_clock"},
         [{"from": [3, 3, 15], "to": [13, 13, 16], "faces": {"north": {"uv": [2.5, 2.5, 12.5, 12.5], "texture": "#c"}}}], ao=False)

    # The library.
    for i, name in enumerate(books):
        textures = {"w": "library_wood", "b": name}
        a.models[f"bookshelf_core_{i}"] = model(textures, [
            el((1, 0, 2), (15, 16, 14), "w", north=("b", (1, 0, 15, 16)), south=("b", (1, 0, 15, 16))),
            el((0, 0, 1), (1, 16, 15), "w"), el((15, 0, 1), (16, 16, 15), "w"),
            el((1, 7.5, 1), (15, 8.5, 15), "w"), el((1, 15, 1), (15, 16, 15), "w")])
    stack("bookshelf", "Bookshelf", {"w": "library_wood"}, [f"bookshelf_core_{i}" for i in range(len(books))],
          base=[el((0, 0, 1), (16, 1, 15), "w")])
    tile("reading_table", "Reading Table", {"w": "library_wood"}, [el((0, 11, 0), (16, 12, 16), "w")], (11, "w"))
    prop("wooden_chair", "Wooden Chair", {"w": "library_wood"},
         [el((3, 6, 3), (13, 7, 13), "w"), el((3, 7, 12), (13, 16, 13), "w")]
         + [el((x, 0, z), (x + 1, 6, z + 1), "w") for x in (3, 12) for z in (3, 12)])
    row("library_counter", "Library Counter", {"w": "library_wood", "p": "library_panel"},
        [el((0, 0, 1), (16, 15, 16), "w", north="p", south="p"), el((0, 15, 0), (16, 16, 16), "w")])
    # The bunker hatch's leaves (a TrapdoorBlock of steel, opened only by BookHooks): models facing
    # north with the hinge on the south, closed flush at the top or bottom of the block or open
    # standing at the hinge; hinge knuckles and a grab handle stand out of the plate.
    a.texture("bunker_trapdoor", bunker_trapdoor_tex(r("bunker_trapdoor")), T.PAINTED_METAL.but(relief=0.8))
    hatch_tex = {"p": "bunker_trapdoor", "e": "dark_metal"}

    def leaf(y0):
        y1 = y0 + 3
        return [el((0, y0, 0), (16, y1, 16), "e", up="p"),
                el((2, y1, 13.5), (5, y1 + 1, 16), "e"), el((11, y1, 13.5), (14, y1 + 1, 16), "e"),
                el((6, y1, 2), (10, y1 + 0.75, 3), "e")]
    a.models["bunker_trapdoor_bottom"] = model(hatch_tex, leaf(0))
    a.models["bunker_trapdoor_top"] = model(hatch_tex, leaf(13))
    a.models["bunker_trapdoor_open"] = model(hatch_tex, [
        el((0, 0, 13), (16, 16, 16), "e", north="p"),
        el((2, 0, 12), (5, 3, 13), "e"), el((11, 0, 12), (14, 3, 13), "e"),
        el((6, 13, 12.25), (10, 14, 13), "e")])
    variants = {}
    for facing, y in HORIZONTAL.items():
        for half in ("bottom", "top"):
            for opened in ("false", "true"):
                name = "bunker_trapdoor_open" if opened == "true" else f"bunker_trapdoor_{half}"
                variants[f"facing={facing},half={half},open={opened}"] = {"model": f"{NS}:block/{name}", **({"y": y} if y else {})}
    a.block("bunker_trapdoor", "Bunker Hatch", {"variants": variants}, {"parent": f"{NS}:block/bunker_trapdoor_bottom"})

    # A brass desk lamp with a green glass shade, on the librarian's desk (lit, ModBlocks).
    a.texture("brass", plastic_tex(r("brass"), (176, 140, 70)), T.STEEL.but(smoothness=0.6))
    a.texture("lamp_shade", plastic_tex(r("lamp_shade"), (40, 104, 60)), T.PLASTIC.but(smoothness=0.8, emission=GLOW["DIFFUSER_GLOW"]))
    prop("desk_lamp", "Desk Lamp", {"b": "brass", "g": "lamp_shade"},
         [el((5, 0, 6), (11, 1, 10), "b"), el((7.5, 1, 7.5), (8.5, 6, 8.5), "b"), el((4, 6, 6), (12, 8.5, 10), "g")])
    # A full block high, so a book can lie on top (the book puzzle).
    prop("card_catalog", "Card Catalog", {"w": "library_wood", "f": "card_catalog_front"},
         [el((1, 0, 2), (15, 16, 16), "w", north="f")])
    # A library book cart (the book puzzle: a book may lie on top): dark wood, two shelves of
    # books, four small wheels; its top a full block high.
    cart_books = (1, 1, 13, 6)
    prop("book_cart", "Book Cart", {"w": "library_wood", "b": books[1], "m": "dark_metal"},
         [el((x, 0, z), (x + 2, 2, z + 2), "m") for x in (2, 12) for z in (4, 10)]
         + [el((1, 2, 3), (15, 3, 13), "w"), el((1, 8, 3), (15, 9, 13), "w"), el((1, 15, 3), (15, 16, 13), "w")]
         + [el((x, 3, z), (x + 1, 15, z + 1), "w") for x in (1, 14) for z in (3, 12)]
         + [el((2, y, 4), (14, y + 5, 12), "b", up=None, down=None, north=("b", cart_books), south=("b", cart_books),
               east=("b", (1, 1, 9, 6)), west=("b", (1, 1, 9, 6))) for y in (3, 9)])

    # The box puzzle (BoxHooks): the purple archive box, drawn full bright in game
    # (PurpleBoxBlock; it glows a little with shaders too), and the empty shelf in Storage.
    box_glow = T.CARDBOARD.but(emission=GLOW["DIFFUSER_GLOW"])
    a.texture("purple_box_side", purple_box_tex(r("purple_box_side"), "side"),
              lambda x, y, c: T.PAPER.but(emission=GLOW["DIFFUSER_GLOW"]) if T.near(c, (228, 222, 236), 20) else box_glow)
    a.texture("purple_box_top", purple_box_tex(r("purple_box_top"), "top"), box_glow)
    lid_band = (0, 0, 16, 3)
    prop("purple_box", "Purple Box", {"s": "purple_box_side", "t": "purple_box_top"},
         [el((2, 0, 2), (14, 8.5, 14), "s", up=None, down="t",
             north=("s", (2, 4.5, 14, 13)), south=("s", (2, 4.5, 14, 13)), east=("s", (2, 4.5, 14, 13)), west=("s", (2, 4.5, 14, 13))),
          el((1.5, 8.5, 1.5), (14.5, 11, 14.5), "s", up="t", down="t",
             north=("s", lid_band), south=("s", lid_band), east=("s", lid_band), west=("s", lid_band))])
    # The office book (OfficeBookBlock), lying flat: boards top and bottom, page edges round
    # three sides, the spine on the west; drawn full bright, glowing a little with shaders.
    book_glow = T.PAPER.but(emission=GLOW["DIFFUSER_GLOW"])
    a.texture("office_book_cover", office_book_tex(r("office_book_cover"), "cover"), book_glow)
    a.texture("office_book_pages", office_book_tex(r("office_book_pages"), "pages"), book_glow)
    pages_uv = (0, 0, 16, 16)
    prop("office_book", "Office Book", {"c": "office_book_cover", "p": "office_book_pages"},
         [el((4, 0, 3), (12, 2.5, 13), "c", north=("p", pages_uv), south=("p", pages_uv), east=("p", pages_uv),
             west=("c", (0, 0, 3, 16)))])
    # A loose typed sheet on a desk (NotePaperBlock, Level 3's documents): the texture covers its top.
    a.texture("note_paper", note_paper_tex(r("note_paper")), T.PAPER)
    sheet_uv = (0, 0, 16, 16)
    prop("note_paper", "Note Paper", {"p": "note_paper"},
         [el((3, 0, 1.5), (13, 0.25, 14.5), "p", up=("p", sheet_uv), down=("p", sheet_uv))])
    # The shelf: 2 blocks wide, 3 high (BoxShelfBlock: col 0 on the left as seen from the front,
    # the east of a model facing north; row 0 at the bottom). Five boards 8 pixels apart from the
    # floor, a top, a back and the two outer sides; the boxes stand on the boards (drawn by
    # BoxShelfRenderer), board k's top at 1 + 8k pixels over the shelf's foot.
    variants = {}
    for col in (0, 1):
        outer = (15.5, 16) if col == 0 else (0, 0.5)
        post = (15, 16) if col == 0 else (0, 1)
        for row_ in (0, 1, 2):
            boards = [(0, 1), (8, 9)] if row_ < 2 else [(0, 1), (15, 16)]
            els = [el((0, y0, 2), (16, y1, 16), "m") for y0, y1 in boards]
            els += [el((0, 0, 15.5), (16, 16, 16), "m"),                                    # the back
                    el((outer[0], 0, 2), (outer[1], 16, 16), "m"),                           # the outer side
                    el((post[0], 0, 2), (post[1], 16, 3), "m")]                              # the front post
            name = f"box_shelf_{col}_{row_}"
            a.models[name] = model({"m": "office_metal"}, els)
            for facing, y in HORIZONTAL.items():
                variants[f"col={col},facing={facing},row={row_}"] = {"model": f"{NS}:block/{name}", **({"y": y} if y else {})}
    a.block("box_shelf", "Box Shelf", {"variants": variants}, {"parent": f"{NS}:block/box_shelf_0_0"})

    # --- The service elevator ------------------------------------------------------------
    a.texture("checker_plate", checker_plate_tex(r("checker_plate")), T.STEEL.but(smoothness=0.5, metal=255, relief=0.9))
    a.models["checker_plate"] = cube_all("checker_plate")
    a.block("checker_plate", "Checker Plate", {"variants": {"": {"model": f"{NS}:block/checker_plate"}}},
            {"parent": f"{NS}:block/checker_plate"})

    # --- Metal grating: a slab drawn as a thin see-through plate at its top -------------------
    a.texture("metal_grating", grating_tex(r("grating")), T.STEEL.but(smoothness=0.35, metal=255, relief=0.7))
    plates = {"bottom": [6], "top": [14], "double": [6, 14]}
    for kind, tops in plates.items():
        a.models[f"metal_grating_{kind}"] = model({"t": "metal_grating"}, [
            element((0, top, 0), (16, top + 2, 16), "t") for top in tops], ao=False)
    a.block("metal_grating", "Metal Grating",
            {"variants": {f"type={kind}": {"model": f"{NS}:block/metal_grating_{kind}"} for kind in plates}},
            {"parent": f"{NS}:block/metal_grating_bottom"})

    a.texture("moving_pad", moving_pad_tex(r("moving_pad")), T.Material(0.05, porosity=0.9, relief=0.7))
    a.models["moving_pad"] = model({"t": "moving_pad"}, [element((0, 0, 14), (16, 16, 16), "t")])
    a.block("moving_pad", "Moving Blanket", horizontal_blockstate("moving_pad"), {"parent": f"{NS}:block/moving_pad"})

    a.texture("handrail", handrail_tex(r("handrail")), T.STEEL.but(smoothness=0.7, metal=255, relief=0.2))
    a.models["handrail"] = model({"t": "handrail"}, [
        element((0, 8.5, 13.5), (16, 10.5, 15.5), "t"), element((7, 9, 15.5), (9, 10, 16), "t")], ao=False)
    a.block("handrail", "Handrail", horizontal_blockstate("handrail"), {"parent": f"{NS}:block/handrail"})

    for block_id, name, call, box in (("elevator_buttons", "Elevator Buttons", False, ((4, 1, 15), (12, 15, 16))),
                                      ("call_buttons", "Call Buttons", True, ((5, 3, 15), (11, 13, 16)))):
        a.texture(block_id, elevator_buttons_tex(r(block_id), call), led_and_metal)
        a.models[block_id] = model({"t": block_id}, [element(*box, "t")], ao=False)
        a.block(block_id, name, horizontal_blockstate(block_id), {"parent": f"{NS}:block/{block_id}"})

    # Floor indicator: a 12x5 display (FloorIndicatorBlock's shape); one model per floor and arrow.
    variants = {}
    for floor, label in enumerate(FLOOR_LABELS):
        for moving in ("false", "true"):
            name = f"floor_indicator_{label.lower()}_{'moving' if moving == 'true' else 'still'}"
            a.texture(name, floor_indicator_tex(label, moving == "true"), led_and_metal, tiles=False)
            a.models[name] = model({"t": name}, [{
                "from": [2, 8, 14.5], "to": [14, 13, 16], "faces": {
                    "north": {"texture": "#t", "uv": [0, 0, 12, 5]},
                    "east": {"texture": "#t", "uv": [12, 8, 14, 13]}, "west": {"texture": "#t", "uv": [12, 8, 14, 13]},
                    "up": {"texture": "#t", "uv": [0, 8, 12, 10]}, "down": {"texture": "#t", "uv": [0, 8, 12, 10]},
                    "south": {"texture": "#t", "uv": [0, 8, 12, 13]}}}], ao=False)
            for facing, y in HORIZONTAL.items():
                v = {"model": f"{NS}:block/{name}"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},floor={floor},moving={moving}"] = v
    a.block("floor_indicator", "Floor Indicator", {"variants": variants}, {"parent": f"{NS}:block/floor_indicator_b1_still"})

    # --- The parking booth -----------------------------------------------------------------
    a.texture("monitor_body", monitor_tex(r("monitor")), T.PLASTIC.but(smoothness=0.4, relief=0.3))
    a.animated["monitor_static"] = (static_frames(r("monitor_static")), 2)
    a.models["cctv_monitor"] = model({"b": "monitor_body", "s": "monitor_static"}, [
        element((2, 0, 3), (14, 10, 13), "b"),
        {"from": [3, 2, 2.9], "to": [13, 9, 3], "shade": False, "faces": {"north": {"texture": "#s", "uv": [0, 0, 10, 7]}}}])
    a.block("cctv_monitor", "CCTV Monitor", horizontal_blockstate("cctv_monitor"), {"parent": f"{NS}:block/cctv_monitor"})

    a.texture("phone", plastic_tex(r("phone"), (34, 34, 38), "keypad"), T.PLASTIC.but(smoothness=0.55, relief=0.3))
    a.models["desk_phone"] = model({"t": "phone"}, [
        element((4, 0, 5), (12, 2, 11), "t"), element((4, 2, 5), (12, 3.5, 7.5), "t")])
    a.block("desk_phone", "Desk Phone", horizontal_blockstate("desk_phone"), {"parent": f"{NS}:block/desk_phone"})

    a.texture("logbook_pages", logbook_tex(r("logbook_pages"), "pages"), T.PAPER)
    a.texture("logbook_cover", logbook_tex(r("logbook_cover"), "cover"), T.Material(0.1, porosity=0.6, relief=0.4))
    a.texture("mug", mug_tex(r("mug"), "mug"), T.Material(0.7, relief=0.1))
    a.texture("coffee", mug_tex(r("coffee"), "coffee"), T.Material(0.9, relief=0.0))
    a.texture("old_flashlight", torch_tex(r("torch")), T.PLASTIC)
    book = element((2, 0, 5), (10, 1, 12), "c")
    book["faces"]["up"]["texture"] = "#p"
    mug = element((11, 0, 6), (14, 4, 9), "m")
    mug["faces"]["up"]["texture"] = "#k"
    a.models["logbook"] = model({"p": "logbook_pages", "c": "logbook_cover", "m": "mug", "k": "coffee", "f": "old_flashlight"}, [
        book, mug, element((3, 0, 2), (9, 1.5, 3.5), "f")])
    a.block("logbook", "Logbook", horizontal_blockstate("logbook"), {"parent": f"{NS}:block/logbook"})

    a.texture("chair_fabric", plastic_tex(r("chair_fabric"), (46, 48, 54), "fabric"), T.Material(0.05, porosity=0.9, relief=0.5))
    a.texture("chair_plastic", plastic_tex(r("chair_plastic"), (22, 22, 24)), T.PLASTIC)
    a.models["office_chair"] = model({"f": "chair_fabric", "p": "chair_plastic"}, [
        element((3, 6, 3), (13, 8, 13), "f"), element((3, 8, 12), (13, 16, 14), "f"),
        element((7, 2, 7), (9, 6, 9), "p"), element((2, 0, 7), (14, 1.5, 9), "p"), element((7, 0, 2), (9, 1.5, 14), "p")])
    a.block("office_chair", "Office Chair", horizontal_blockstate("office_chair"), {"parent": f"{NS}:block/office_chair"})

    a.texture("key_rack", key_rack_tex(r("key_rack")), T.Material(0.2, porosity=0.4, relief=0.5))
    a.texture("brass", plastic_tex(r("brass"), (176, 146, 70)), T.STEEL.but(smoothness=0.6, metal=255))
    a.models["key_rack"] = model({"w": "key_rack", "h": "brass"}, [element((2, 4, 15), (14, 11, 16), "w")] + [
        element((x, 7, 14), (x + 1, 8, 15), "h") for x in (3, 5, 7, 9, 11)], ao=False)
    a.block("key_rack", "Key Rack", horizontal_blockstate("key_rack"), {"parent": f"{NS}:block/key_rack"})

    # Doors: painted steel, the models are vanilla's door shapes (door_bottom_left ...).
    for door_id, name, colour, kind in (("storage_door", "Storage Door", (92, 100, 108), "storage"),
                                        ("electrical_door", "Electrical Room Door", (92, 100, 108), "electrical"),
                                        ("emergency_door", "Emergency Exit Door", (70, 82, 90), "emergency")):
        halves = {}
        for half in ("bottom", "top"):
            halves[half] = steel_door_tex(r(f"{door_id}_{half}"), half, colour, kind)
            a.texture(f"{door_id}_{half}", halves[half], T.PAINTED_METAL.but(smoothness=0.4, relief=0.4))
        for part in ("bottom", "top"):
            for hinge in ("left", "right"):
                for opened in ("", "_open"):
                    a.models[f"{door_id}_{part}_{hinge}{opened}"] = {
                        "parent": f"minecraft:block/door_{part}_{hinge}{opened}",
                        "textures": {"bottom": tex_ref(f"{door_id}_bottom"), "top": tex_ref(f"{door_id}_top")}}
        a.block(door_id, name, {"variants": door_variants(f"{NS}:block/{door_id}")},
                {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{door_id}"}})
        a.item_textures[door_id] = door_item_tex(halves["bottom"], halves["top"])
        a.loot_doors.add(door_id)

    # The exit sign: a lit box against the wall (PropBlock's shape: 2..14 x 2..8, 2 deep).
    a.texture("exit_sign", exit_sign_tex(r("exit_sign")), exit_sign_glow, tiles=False)
    a.models["exit_sign"] = model({"t": "exit_sign"}, [{
        "from": [2, 2, 14], "to": [14, 8, 16], "shade": False, "faces": {
            "north": {"texture": "#t", "uv": [0, 0, 12, 6]},
            "east": {"texture": "#t", "uv": [12, 8, 14, 14]}, "west": {"texture": "#t", "uv": [12, 8, 14, 14]},
            "up": {"texture": "#t", "uv": [0, 8, 12, 10]}, "down": {"texture": "#t", "uv": [0, 8, 12, 10]},
            "south": {"texture": "#t", "uv": [0, 8, 12, 14]}}}], ao=False)
    a.block("exit_sign", "Exit Sign", horizontal_blockstate("exit_sign"), {"parent": f"{NS}:block/exit_sign"})

    # Rolling shutter: a 2-pixel steel sheet through the middle of the block; axis = the way it runs.
    a.texture("shutter", shutter_tex(r("shutter")), T.STEEL.but(smoothness=0.45, metal=255, relief=0.7))
    a.models["shutter"] = model({"t": "shutter"}, [element((0, 0, 7), (16, 16, 9), "t")])
    a.block("shutter", "Rolling Shutter", {"variants": {
        "axis=x": {"model": f"{NS}:block/shutter"},
        "axis=z": {"model": f"{NS}:block/shutter", "y": 90}}},
        {"parent": f"{NS}:block/shutter"})

    a.texture("sign_tile", T.wall_tex(r("sign_tile"), "plain"), T.CONCRETE_WALL)
    a.models["sign_tile"] = cube_all("sign_tile")
    a.block("sign_tile", "Sign Tile", {"variants": {"": {"model": f"{NS}:block/sign_tile"}}},
            {"parent": f"{NS}:block/sign_tile"})

    # Stains: the level generator's decal pictures; the pattern's meaning depends on the surface.
    variants = {}
    stain_kinds = {"up": ("floor", [k for k in T.FLOOR_DECALS if k != "puddle"]),
                   "down": ("ceiling", T.CEILING_DECALS)}
    for side in ("north", "east", "south", "west"):
        stain_kinds[side] = ("wall", T.WALL_DECALS)
    first_texture = None
    for facing, (surface, kinds) in stain_kinds.items():
        pictures = [f"stain_{surface}_{kind}_{i}" for kind in kinds for i in range(2)]
        for picture in pictures:
            if picture not in a.textures:
                kind = picture.split("_")[2]
                a.texture(picture, T.decal(r(picture), kind), T.DECAL_MATERIALS[kind], tiles=False)
                a.models[picture] = plane(picture)
                first_texture = first_texture or picture
        for pattern in range(16):
            variants[f"facing={facing},pattern={pattern}"] = dict(
                {"model": f"{NS}:block/{pictures[pattern % len(pictures)]}"}, **PLANE_ROTATION[facing])
    a.block("stain", "Stain", {"variants": variants},
            {"parent": "minecraft:item/generated", "textures": {"layer0": tex_ref(first_texture)}})

    variants = {}
    for pattern in range(4):
        picture = f"puddle_{pattern}"
        a.texture(picture, T.decal(r(picture), "puddle"), T.WATER, tiles=False)
        a.models[picture] = plane(picture)
        variants[f"pattern={pattern}"] = {"model": f"{NS}:block/{picture}"}
    a.block("puddle", "Puddle", {"variants": variants},
            {"parent": "minecraft:item/generated", "textures": {"layer0": tex_ref("puddle_0")}})
    return a


# =====================================================================================

def registered_ids():
    """Block ids registered in ModBlocks.kt (register("id", ...))."""
    return set(re.findall(r'register\(\s*"([a-z0-9_]+)"', MOD_BLOCKS_KT.read_text()))


def png(img):
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    a = build()

    kotlin = registered_ids()
    if kotlin != set(a.blockstates):
        missing_here = sorted(kotlin - set(a.blockstates))
        missing_there = sorted(set(a.blockstates) - kotlin)
        raise SystemExit(f"blocks differ from ModBlocks.kt: not here {missing_here}, not registered {missing_there}")

    sheet = {}
    tex_dir = ASSETS / "textures" / "block"
    tex_dir.mkdir(parents=True, exist_ok=True)
    for name, (img, material, tiles) in a.textures.items():
        normal, specular = pbr.maps(img, material, G.PBR["RELIEF"], tiles=tiles)
        for suffix, image in (("", img), ("_n", normal), ("_s", specular)):
            data = png(image)
            (tex_dir / f"{name}{suffix}.png").write_bytes(data)
            sheet[name + suffix] = data
    for name, (frames, frametime) in a.animated.items():
        strip = Image.new("RGBA", (16, 16 * len(frames)))
        for i, frame in enumerate(frames):
            strip.paste(frame, (0, 16 * i))
        (tex_dir / f"{name}.png").write_bytes(png(strip))
        write_json(tex_dir / f"{name}.png.mcmeta", {"animation": {"frametime": frametime}})
    for name, data in a.models.items():
        write_json(ASSETS / "models" / "block" / f"{name}.json", data)
    for block_id, data in a.items.items():
        write_json(ASSETS / "models" / "item" / f"{block_id}.json", data)
    for block_id, data in a.blockstates.items():
        write_json(ASSETS / "blockstates" / f"{block_id}.json", data)
        conditions = [{"condition": "minecraft:survives_explosion"}]
        if block_id in a.loot_doors:                     # like vanilla doors: only the lower half drops
            conditions.insert(0, {"condition": "minecraft:block_state_property", "block": f"{NS}:{block_id}",
                                  "properties": {"half": "lower"}})
        write_json(LOOT / f"{block_id}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{block_id}"}],
            "conditions": conditions}]})

    # Item textures and models that belong to no block.
    item_dir = ASSETS / "textures" / "item"
    item_dir.mkdir(parents=True, exist_ok=True)
    for name, img in a.item_textures.items():
        (item_dir / f"{name}.png").write_bytes(png(img))
    for item, painter in (("battery", battery_tex), ("water_bottle", water_bottle_tex), ("keycard", keycard_tex)):
        (item_dir / f"{item}.png").write_bytes(png(painter(rng_for(item))))
        write_json(ASSETS / "models" / "item" / f"{item}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{item}"}})

    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["itemGroup.every_tunnel"] = "Every Tunnel"
    for block_id, name in a.names.items():
        lang[f"block.{NS}.{block_id}"] = name
    write_json(lang_path, lang)

    # Files of blocks or textures that no longer exist here.
    stale = [p for p in (ASSETS / "models" / "block").glob("*.json") if p.stem not in a.models]
    stale += [p for p in tex_dir.glob("*.png")
              if re.sub(r"_[ns]$", "", p.stem) not in a.textures and p.stem not in a.animated]
    for p in stale:
        p.unlink()

    OUT.mkdir(exist_ok=True)
    preview.write_pbr_sheet(OUT / "blocks_sheet.png", sheet)
    print(f"{len(a.blockstates)} blocks, {len(a.models)} models, {len(a.textures)} textures (+ LabPBR maps)"
          + (f"; removed {len(stale)} stale files" if stale else ""))


if __name__ == "__main__":
    main()
