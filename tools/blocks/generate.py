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

NS = "every_tunnel"
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / NS
LOOT = RESOURCES / "data" / NS / "loot_tables" / "blocks"
OUT = Path(__file__).resolve().parent / "output"
MOD_BLOCKS_KT = ROOT / "src" / "main" / "kotlin" / "daio7771" / "everytunnel" / "platform" / "ModBlocks.kt"

HORIZONTAL = {"north": 0, "east": 90, "south": 180, "west": 270}
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
    return img


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


def led_glow(x, y, c):
    if max(c) > 200 and min(c) < 120:
        return T.PLASTIC.but(smoothness=0.7, relief=0.0, emission=GLOW["TUBE_GLOW"])
    return T.PLASTIC.but(smoothness=0.5, relief=0.3)


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
    for name, data in a.models.items():
        write_json(ASSETS / "models" / "block" / f"{name}.json", data)
    for block_id, data in a.items.items():
        write_json(ASSETS / "models" / "item" / f"{block_id}.json", data)
    for block_id, data in a.blockstates.items():
        write_json(ASSETS / "blockstates" / f"{block_id}.json", data)
        write_json(LOOT / f"{block_id}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{block_id}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    # Item textures and models that belong to no block.
    item_dir = ASSETS / "textures" / "item"
    item_dir.mkdir(parents=True, exist_ok=True)
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
              if re.sub(r"_[ns]$", "", p.stem) not in a.textures]
    for p in stale:
        p.unlink()

    OUT.mkdir(exist_ok=True)
    preview.write_pbr_sheet(OUT / "blocks_sheet.png", sheet)
    print(f"{len(a.blockstates)} blocks, {len(a.models)} models, {len(a.textures)} textures (+ LabPBR maps)"
          + (f"; removed {len(stale)} stale files" if stale else ""))


if __name__ == "__main__":
    main()
