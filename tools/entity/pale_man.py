"""
The Pale man's model, animations and texture for GeckoLib.

Run after changing anything here (from the repository root):   python3 tools/entity/pale_man.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/pale_man.geo.json                 bones and cubes (Bedrock geometry, 16 units = 1 block)
  animations/entity/pale_man.animation.json    idle, listen, walk, run, attack
  textures/entity/pale_man.png                 the skin, box UV, SCALE pixels per unit
  textures/entity/pale_man_glowmask.png        what glows in the dark (his eyes)
  models/item/pale_man_spawn_egg.json, and the names in lang/en_us.json
and, in tools/entity/output/ (git-ignored): pale_man_preview.png (the texture enlarged, plus
front and side views of the model) and a painting template, pale_man_template.png (real
size) and pale_man_template_big.png (enlarged, labelled): every face of every cube in its
own colour, red = front (north), blue = back, green = east, yellow = west, white = top,
grey = bottom.

The look follows tools/entity/reference/pale_man_concept.png (AI concept art, reference
only): about 2.6 blocks tall, very long thin limbs, big hands with long fingers reaching
the knees, long bare feet, a long neck that lets the head hang forwards. Beige skin with
bluish veins, milky white eyes (blind; they glow faintly), big ears (he hunts by hearing).
He wears what is left of a night-guard uniform: an open, torn navy shirt with a SECURITY
tag and a shield patch, a black belt with a holster, trousers shredded to mid-shin. He was
the guard before you. The clothes are a second, slightly bigger layer of cubes with holes
(transparent pixels), like the jacket layer of a player skin, so the rags hang free.

Conventions (Bedrock / GeckoLib): the model faces north (-z); rotations are degrees
[x, y, z]; a negative x rotation swings a hanging limb forwards, so a positive one leans
an upright part forwards. Box UV: a cube of size (w, h, d) takes 2(w+d) x (d+h) units of
the texture, faces laid out as in Blockbench. The texture is declared LAYOUT x LAYOUT units
and painted at SCALE pixels per unit (GeckoLib maps UVs by the declared size, so the image
can be finer than the layout).
"""

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"
LAYOUT = 64                   # texture size in model units (the geo file's texture_width/height)
SCALE = 2                     # pixels per unit in the image
PX = LAYOUT * SCALE

SKIN = (206, 190, 180)
SKIN_SHADE = (176, 160, 152)
VEIN = (148, 162, 198)
EAR = (212, 168, 162)
EYE = (236, 242, 246)
SOCKET = (150, 128, 124)
NAVY = (36, 42, 72)
NAVY_LIGHT = (58, 66, 104)
NAVY_DARK = (22, 26, 46)
BELT = (24, 24, 26)
BUCKLE = (128, 128, 132)
TAG = (196, 198, 204)
NAIL = (150, 130, 118)
DIRT = (120, 104, 92)

TORSO_LEAN = 8                # the back is almost straight...
NECK_DROOP = 45               # ...but the neck falls forwards
HEAD_LIFT = -15               # and the head lifts a little to "look" ahead


# =====================================================================================
# Geometry

class Bone:
    def __init__(self, name, parent, pivot, rotation=(0, 0, 0)):
        self.name, self.parent, self.pivot, self.rotation = name, parent, list(pivot), list(rotation)
        self.cubes = []        # (origin, size, painter kind, inflate)


def skeleton():
    """Bones and cubes, in model units (y up from the soles, front towards -z)."""
    b = {}

    def bone(name, parent, pivot, rotation=(0, 0, 0)):
        b[name] = Bone(name, parent, pivot, rotation)

    def cube(bone_name, origin, size, kind, inflate=0.0):
        b[bone_name].cubes.append((list(origin), list(size), kind, inflate))

    bone("root", None, (0, 0, 0))
    cube("root", (-3, 23, -1.5), (6, 3, 3), "pelvis")                    # belted hips; they never lean
    for side, x in (("left", 1.6), ("right", -1.6)):
        bone(f"{side}_thigh", "root", (x, 23, 0))
        cube(f"{side}_thigh", (x - 1, 12, -1), (2, 11, 2), "thigh")
        cube(f"{side}_thigh", (x - 1, 12, -1), (2, 11, 2), "trousers", 0.35)
        bone(f"{side}_shin", f"{side}_thigh", (x, 12, 0))
        cube(f"{side}_shin", (x - 1, 2, -1), (2, 10, 2), "shin")
        cube(f"{side}_shin", (x - 1, 6, -1), (2, 6, 2), "trouser_rags", 0.35)
        cube(f"{side}_shin", (x - 1.5, 0, -4), (3, 2, 6), "foot")          # long feet, toes forward
    bone("torso", "root", (0, 26, 0), (TORSO_LEAN, 0, 0))
    cube("torso", (-3, 26, -1.5), (6, 4, 3), "belly")
    cube("torso", (-3.5, 30, -2), (7, 6, 4), "chest")
    cube("torso", (-3, 26, -1.5), (6, 4, 3), "shirt_belly", 0.3)
    cube("torso", (-3.5, 30, -2), (7, 6, 4), "shirt_chest", 0.3)
    bone("neck", "torso", (0, 36, -0.5), (NECK_DROOP, 0, 0))
    cube("neck", (-1, 36, -1.5), (2, 3, 2), "neck")
    bone("head", "neck", (0, 39, -0.5), (HEAD_LIFT, 0, 0))
    cube("head", (-2.5, 39, -3), (5, 6, 5), "head")
    cube("head", (2.5, 40.5, -1.5), (1, 3, 2), "ear")
    cube("head", (-3.5, 40.5, -1.5), (1, 3, 2), "ear")
    for side, x in (("left", 4.5), ("right", -4.5)):
        bone(f"{side}_arm", "torso", (x, 35, 0), (-TORSO_LEAN, 0, 0))     # hanging straight down
        cube(f"{side}_arm", (x - 1, 25, -1), (2, 10, 2), "upper_arm")
        cube(f"{side}_arm", (x - 1, 31, -1), (2, 4, 2), f"sleeve_{side}", 0.4)
        bone(f"{side}_forearm", f"{side}_arm", (x, 25, 0))
        cube(f"{side}_forearm", (x - 1, 16, -1), (2, 9, 2), "forearm")
        bone(f"{side}_hand", f"{side}_forearm", (x, 16, 0))
        cube(f"{side}_hand", (x - 1.5, 13, -0.5), (3, 3, 1), "palm")
        cube(f"{side}_hand", (x - 1.5, 9, -0.5), (3, 4, 1), "fingers")
    return b


# =====================================================================================
# Texture layout (box UV, shelf-packed)

def box_area(size):
    w, h, d = (math.ceil(s) for s in size)
    return 2 * (w + d), d + h


def pack(bones):
    """Shelf packing of every cube's box UV area (in units); returns {(bone, index): (u, v)}."""
    items = [(bn, i, box_area(c[1])) for bn, bone in bones.items() for i, c in enumerate(bone.cubes)]
    items.sort(key=lambda it: (-it[2][1], -it[2][0]))
    uv, x, y, shelf = {}, 0, 0, 0
    for bn, i, (w, h) in items:
        if x + w > LAYOUT:
            x, y = 0, y + shelf
            shelf = 0
        if y + h > LAYOUT:
            raise SystemExit(f"the Pale man's cubes do not fit in a {LAYOUT}x{LAYOUT} texture")
        uv[(bn, i)] = (x, y)
        x += w
        shelf = max(shelf, h)
    return uv


def faces(u, v, size, scale=SCALE):
    """Pixel rectangles (x0, y0, x1, y1) of each face in box UV (Bedrock/Blockbench layout)."""
    w, h, d = (math.ceil(s) for s in size)
    r = {
        "up": (u + d, v, u + d + w, v + d), "down": (u + d + w, v, u + d + 2 * w, v + d),
        "east": (u, v + d, u + d, v + d + h), "north": (u + d, v + d, u + d + w, v + d + h),
        "west": (u + d + w, v + d, u + 2 * d + w, v + d + h), "south": (u + 2 * d + w, v + d, u + 2 * (d + w), v + d + h),
    }
    return {k: tuple(c * scale for c in rect) for k, rect in r.items()}


# =====================================================================================
# Painting (pixel coordinates; SCALE pixels per unit)

class Painter:
    def __init__(self, rng):
        self.rng = rng
        self.img = Image.new("RGBA", (PX, PX), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (PX, PX), (0, 0, 0, 0))

    def jitter(self, c, amount):
        d = self.rng.uniform(-amount, amount)
        return tuple(max(0, min(255, int(round(ch + d)))) for ch in c[:3])

    def put(self, x, y, c, amount=0):
        self.img.putpixel((x, y), (self.jitter(c, amount) if amount else tuple(int(v) for v in c[:3])) + (255,))

    def clear(self, x, y):
        self.img.putpixel((x, y), (0, 0, 0, 0))

    def fill(self, rect, c, amount=7):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c, amount)

    def skin(self, rect, veins=0.12):
        """Pale skin with bluish vein speckles, a little darker towards the face's bottom."""
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            shade = (y - y0) / max(1, y1 - y0) * 0.25
            for x in range(x0, x1):
                c = tuple(SKIN[i] + (SKIN_SHADE[i] - SKIN[i]) * shade for i in range(3))
                self.put(x, y, VEIN if self.rng.random() < veins else c, 7)

    def cloth(self, rect, holes=0.08, rag_bottom=0):
        """Navy cloth with folds, holes (transparent) and a torn, uneven bottom edge."""
        x0, y0, x1, y1 = rect
        torn = [self.rng.randint(0, rag_bottom) for _ in range(x1 - x0)] if rag_bottom else [0] * (x1 - x0)
        for x in range(x0, x1):
            fold = self.rng.random() < 0.18
            for y in range(y0, y1):
                if y >= y1 - torn[x - x0] or self.rng.random() < holes:
                    self.clear(x, y)
                else:
                    self.put(x, y, NAVY_DARK if fold else (NAVY_LIGHT if self.rng.random() < 0.12 else NAVY), 5)
        for _ in range(int((x1 - x0) * (y1 - y0) * holes * 0.15)):     # holes come in clumps
            x, y = self.rng.randrange(x0, x1), self.rng.randrange(y0, y1)
            for dx, dy in ((1, 0), (0, 1), (1, 1)):
                if x + dx < x1 and y + dy < y1:
                    self.clear(x + dx, y + dy)


def paint(p, kind, rects):
    S = SCALE
    rng = p.rng
    side_faces = ("north", "east", "south", "west")
    if kind in ("thigh", "shin", "upper_arm", "forearm", "neck", "belly", "chest", "palm"):
        for r in rects.values():
            p.skin(r)
        if kind == "chest":                       # the sternum and ribs showing through the open shirt
            x0, y0, x1, y1 = rects["north"]
            for y in range(y0 + 2, y1 - 1, 3):
                for x in range(x0 + 2, x1 - 2):
                    p.put(x, y, SKIN_SHADE, 5)
        if kind == "shin":                        # dirt rising from the floor
            for name in side_faces:
                x0, y0, x1, y1 = rects[name]
                for y in range(y1 - 3 * S, y1):
                    for x in range(x0, x1):
                        if rng.random() < 0.4:
                            p.put(x, y, DIRT, 8)
    elif kind == "head":
        for r in rects.values():
            p.skin(r, veins=0.08)
        x0, y0, x1, y1 = rects["north"]           # 5 x 6 units = 10 x 12 pixels
        for x in range(x0, x1):                   # brow shadow over sunken eyes
            p.put(x, y0 + 3, SKIN_SHADE, 5)
        for ex in (x0 + 2, x1 - 4):               # milky eyes, 2x2 pixels, glowing faintly
            for dx in (0, 1):
                for dy in (0, 1):
                    p.put(ex + dx, y0 + 4 + dy, EYE)
                    p.glow.putpixel((ex + dx, y0 + 4 + dy), EYE + (255,))
                p.put(ex + dx, y0 + 6, SOCKET, 4)
        cx = (x0 + x1) // 2
        for y in (y0 + 6, y0 + 7):                # nose ridge
            p.put(cx - 1, y, SKIN_SHADE, 4)
        for x in range(cx - 2, cx + 2):           # a thin, closed mouth
            p.put(x, y0 + 9, (96, 70, 70))
        for x in range(x0, x1):                   # jaw shadow
            p.put(x, y1 - 1, SKIN_SHADE, 5)
    elif kind == "ear":
        for r in rects.values():
            p.fill(r, EAR, 6)
        for name in ("east", "west"):
            x0, y0, x1, y1 = rects[name]
            p.fill((x0 + 1, y0 + 1, x1 - 1, y1 - 1), (176, 128, 124), 5)
    elif kind == "fingers":
        # Long fingers; on the front and back the gaps between them are see-through.
        for name, (x0, y0, x1, y1) in rects.items():
            if name in ("up", "down"):
                p.skin((x0, y0, x1, y1))
                continue
            for x in range(x0, x1):
                if name in ("north", "south") and (x - x0) % 2 == 1:
                    for y in range(y0, y1):
                        p.clear(x, y)
                    continue
                length = (y1 - y0) - rng.randint(0, 2)
                for y in range(y0, y1):
                    if y - y0 < length:
                        p.put(x, y, VEIN if rng.random() < 0.1 else SKIN, 7)
                    else:
                        p.clear(x, y)
                p.put(x, y0 + length - 1, NAIL, 6)                     # long dirty nails
    elif kind == "foot":
        for r in rects.values():
            p.skin(r, veins=0.06)
        x0, y0, x1, y1 = rects["north"]           # toes
        for x in range(x0 + 1, x1, 2):
            for y in range(y0, y1):
                p.put(x, y, SKIN_SHADE, 4)
        for x in range(x0, x1):
            p.put(x, y1 - 1, NAIL, 5)
        p.fill(rects["down"], DIRT, 10)
    elif kind == "pelvis":
        for r in rects.values():
            p.cloth(r, holes=0.0)
        for name in side_faces:                   # belt along the top
            x0, y0, x1, y1 = rects[name]
            p.fill((x0, y0, x1, y0 + S), BELT, 3)
        x0, y0, x1, y1 = rects["north"]
        cx = (x0 + x1) // 2
        p.fill((cx - 1, y0, cx + 1, y0 + S), BUCKLE, 6)
        x0, y0, x1, y1 = rects["west"]            # holster on his right hip
        p.fill((x0 + 1, y0 + S, x1 - 1, y1), BELT, 4)
    elif kind == "trousers":
        for name, r in rects.items():
            if name not in ("up", "down"):         # open at both ends: the leg shows through
                p.cloth(r, holes=0.1)
    elif kind == "trouser_rags":
        for name, r in rects.items():
            if name not in ("up", "down"):
                p.cloth(r, holes=0.12, rag_bottom=(r[3] - r[1]) - 2)   # shredded, long strips here and there
    elif kind in ("shirt_chest", "shirt_belly"):
        for name, r in rects.items():
            if name == "up" and kind == "shirt_chest":
                p.cloth(r, holes=0.0)              # the shoulders
            elif name not in ("up", "down"):
                p.cloth(r, holes=0.1, rag_bottom=3 if kind == "shirt_belly" else 0)
        x0, y0, x1, y1 = rects["north"]
        cx = (x0 + x1) // 2
        for y in range(y0, y1):                   # the shirt hangs open down the middle
            half = 2 if kind == "shirt_chest" and y < y0 + 2 * S else 1
            for x in range(cx - half, cx + half):
                p.clear(x, y)
        if kind == "shirt_chest":
            for x in range(x0, x1):               # collar
                if abs(x - cx) > 2:
                    p.put(x, y0, NAVY_LIGHT, 4)
            tx, ty = x1 - 6, y0 + 4               # SECURITY tag on his left chest
            p.fill((tx, ty, tx + 4, ty + 2), TAG, 6)
            for x in range(tx, tx + 4, 2):
                p.put(x, ty + 1, (60, 62, 70))
    elif kind.startswith("sleeve_"):
        for name, r in rects.items():
            if name not in ("up", "down"):
                p.cloth(r, holes=0.08, rag_bottom=3)
        if kind == "sleeve_left":                 # shield patch on the outer side
            x0, y0, x1, y1 = rects["east"]
            p.fill((x0, y0 + 1, x1, y0 + 4), TAG, 5)
            p.put((x0 + x1) // 2, y0 + 2, NAVY_DARK)


def texture(bones, uv):
    p = Painter(random.Random("pale-man-skin-2"))
    for bn, bone in bones.items():
        for i, (origin, size, kind, inflate) in enumerate(bone.cubes):
            paint(p, kind, faces(*uv[(bn, i)], size))
    return p.img, p.glow


# =====================================================================================
# Files

def geo(bones, uv):
    out = []
    for bn, bone in bones.items():
        entry = {"name": bn, "pivot": bone.pivot}
        if bone.parent:
            entry["parent"] = bone.parent
        if any(bone.rotation):
            entry["rotation"] = bone.rotation
        cubes = []
        for i, (o, s, _, inflate) in enumerate(bone.cubes):
            c = {"origin": o, "size": s, "uv": list(uv[(bn, i)])}
            if inflate:
                c["inflate"] = inflate
            cubes.append(c)
        if cubes:
            entry["cubes"] = cubes
        out.append(entry)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.pale_man", "texture_width": LAYOUT, "texture_height": LAYOUT,
                        "visible_bounds_width": 3, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out}]}


def keys(*frames):
    """[(time, [x, y, z]), ...] -> Bedrock keyframes."""
    return {f"{t:.4g}": list(v) for t, v in frames}


def swing(length, amplitude, phase=0.0, offset=(0, 0, 0), axis=0, steps=8):
    """A sine swing over one loop, sampled into keyframes."""
    frames = []
    for i in range(steps + 1):
        t = length * i / steps
        v = list(offset)
        v[axis] += amplitude * math.sin(2 * math.pi * (i / steps) + phase)
        frames.append((t, [round(c, 2) for c in v]))
    return keys(*frames)


def animations():
    walk, run = 1.6, 0.55
    A = {}
    A["animation.pale_man.idle"] = {"loop": True, "animation_length": 4.0, "bones": {
        "torso": {"rotation": swing(4.0, 2.0)},                                   # slow, deep breathing
        "head": {"rotation": swing(4.0, 6.0, axis=2)},                            # a slow sway
        "left_arm": {"rotation": swing(4.0, 3.0, math.pi / 2)},
        "right_arm": {"rotation": swing(4.0, 3.0, math.pi)},
        "left_hand": {"rotation": swing(4.0, 6.0, math.pi / 3)},                  # fingers twitching
    }}
    A["animation.pale_man.listen"] = {"loop": True, "animation_length": 3.0, "bones": {
        # He stops, cocks his head hard to one side, then the other: listening.
        "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [-8, 0, 28]), (1.4, [-8, 4, 30]),
                                  (1.65, [-8, 0, -26]), (2.8, [-8, -4, -28]), (3.0, [0, 0, 0]))},
        "neck": {"rotation": keys((0, [0, 0, 0]), (0.25, [-14, 0, 0]), (2.8, [-14, 0, 0]), (3.0, [0, 0, 0]))},
        "torso": {"rotation": swing(3.0, 1.0)},
        "left_hand": {"rotation": keys((0, [0, 0, 0]), (0.2, [-15, 0, 0]), (0.3, [0, 0, 0]), (1.7, [-10, 0, 0]), (1.8, [0, 0, 0]))},
    }}
    A["animation.pale_man.walk"] = {"loop": True, "animation_length": walk, "bones": {
        "left_thigh": {"rotation": swing(walk, 24, offset=(-6, 0, 0))},
        "right_thigh": {"rotation": swing(walk, 24, math.pi, offset=(-6, 0, 0))},
        "left_shin": {"rotation": swing(walk, 14, -math.pi / 2, offset=(14, 0, 0))},
        "right_shin": {"rotation": swing(walk, 14, math.pi / 2, offset=(14, 0, 0))},
        "left_arm": {"rotation": swing(walk, 10, math.pi)},
        "right_arm": {"rotation": swing(walk, 10)},
        "root": {"position": swing(walk, 0.6, offset=(0, -0.8, 0), axis=1)},     # knees a little bent, a slow bob
        "head": {"rotation": swing(walk / 2, 4, axis=2)},
    }}
    A["animation.pale_man.run"] = {"loop": True, "animation_length": run, "bones": {
        "torso": {"rotation": keys((0, [30, 0, 0]))},                            # leaning far in
        "neck": {"rotation": keys((0, [-20, 0, 0]))},
        "left_thigh": {"rotation": swing(run, 50, offset=(-20, 0, 0))},
        "right_thigh": {"rotation": swing(run, 50, math.pi, offset=(-20, 0, 0))},
        "left_shin": {"rotation": swing(run, 30, -math.pi / 2, offset=(40, 0, 0))},
        "right_shin": {"rotation": swing(run, 30, math.pi / 2, offset=(40, 0, 0))},
        "left_arm": {"rotation": swing(run, 25, math.pi, offset=(-75, 0, -8))},  # arms reaching ahead
        "right_arm": {"rotation": swing(run, 25, offset=(-75, 0, 8))},
        "root": {"position": swing(run / 2, 0.8, offset=(0, -2.5, 0), axis=1)},
    }}
    A["animation.pale_man.attack"] = {"loop": False, "animation_length": 0.6, "bones": {
        "torso": {"rotation": keys((0, [0, 0, 0]), (0.15, [-8, 0, 0]), (0.3, [28, 0, 0]), (0.6, [16, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.15, [-130, 0, -20]), (0.3, [-90, 0, 10]), (0.6, [-80, 0, 0]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.15, [-130, 0, 20]), (0.3, [-90, 0, -10]), (0.6, [-80, 0, 0]))},
        "neck": {"rotation": keys((0, [0, 0, 0]), (0.3, [-30, 0, 0]), (0.6, [-20, 0, 0]))},
    }}
    return {"format_version": "1.8.0", "animations": A, "geckolib_format_version": 2}


# =====================================================================================
# Previews

def rotate(p, pivot, rot):
    """Rotates point p around pivot by Bedrock-style degrees (x, then y, then z)."""
    x, y, z = (p[i] - pivot[i] for i in range(3))
    rx, ry, rz = (math.radians(a) for a in rot)
    # x axis: a positive angle moves +y towards -z (the top leans forwards)
    y, z = y * math.cos(rx) + z * math.sin(rx), -y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return [x + pivot[0], y + pivot[1], z + pivot[2]]


def world_corners(bones, bn, origin, size):
    pts = [[origin[0] + dx * size[0], origin[1] + dy * size[1], origin[2] + dz * size[2]]
           for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    b = bones[bn]
    while b:                                   # innermost bone first, then up the hierarchy
        pts = [rotate(q, b.pivot, b.rotation) for q in pts]
        b = bones.get(b.parent) if b.parent else None
    return pts


def extent(bones):
    pts = [q for bn, bone in bones.items() for o, s, k, inf in bone.cubes for q in world_corners(bones, bn, o, s)]
    return {a: (min(q[i] for q in pts), max(q[i] for q in pts)) for i, a in enumerate("xyz")}


def write_preview(bones, img):
    shown = 3
    sheet = Image.new("RGB", (PX * shown + 2 * 240 + 40, max(PX * shown, 480) + 20), (24, 25, 28))
    sheet.paste(img.resize((PX * shown, PX * shown), Image.NEAREST).convert("RGB"), (10, 10))
    d = ImageDraw.Draw(sheet)
    for view, ox in (("front", PX * shown + 30), ("side, front to the right", PX * shown + 270)):
        d.text((ox, 10), view, fill=(200, 200, 200))
        d.line((ox, 470, ox + 220, 470), fill=(90, 90, 90))
        for bn, bone in bones.items():
            for origin, size, kind, inflate in bone.cubes:
                pts = world_corners(bones, bn, origin, size)
                hx = [q[0] for q in pts] if view == "front" else [-q[2] for q in pts]
                hy = [q[1] for q in pts]
                x0, x1 = ox + 110 + min(hx) * 10, ox + 110 + max(hx) * 10
                y0, y1 = 470 - max(hy) * 10, 470 - min(hy) * 10
                d.rectangle((x0, y0, x1, y1), outline=NAVY_LIGHT if inflate else SKIN)
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "pale_man_preview.png")


FACE_COLOURS = {"north": (220, 70, 70), "south": (70, 110, 220), "east": (70, 190, 90),
                "west": (230, 200, 60), "up": (235, 235, 235), "down": (130, 130, 130)}


def write_template(bones, uv):
    """A painting template: every face of every cube in its own colour (north = the front, red),
    at real size and enlarged with labels."""
    real = Image.new("RGBA", (PX, PX), (0, 0, 0, 0))
    zoom = 4
    big = Image.new("RGB", (PX * zoom, PX * zoom), (30, 30, 34))
    d = ImageDraw.Draw(big)
    for bn, bone in bones.items():
        for i, (origin, size, kind, inflate) in enumerate(bone.cubes):
            for face, (x0, y0, x1, y1) in faces(*uv[(bn, i)], size).items():
                if x1 <= x0 or y1 <= y0:
                    continue
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        real.putpixel((x, y), FACE_COLOURS[face] + (255,))
                d.rectangle((x0 * zoom, y0 * zoom, x1 * zoom - 1, y1 * zoom - 1), fill=FACE_COLOURS[face], outline=(0, 0, 0))
            u, v = uv[(bn, i)]
            d.text((u * SCALE * zoom + 2, v * SCALE * zoom + 1), f"{bn}:{kind}", fill=(0, 0, 0))
    OUT.mkdir(exist_ok=True)
    for old in ("pale_man_template_64.png", "pale_man_template_512.png"):
        (OUT / old).unlink(missing_ok=True)
    real.save(OUT / "pale_man_template.png")
    big.save(OUT / "pale_man_template_big.png")


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    bones = skeleton()
    uv = pack(bones)
    img, glow = texture(bones, uv)
    write_json(ASSETS / "geo" / "entity" / "pale_man.geo.json", geo(bones, uv))
    write_json(ASSETS / "animations" / "entity" / "pale_man.animation.json", animations())
    (ASSETS / "textures" / "entity").mkdir(parents=True, exist_ok=True)
    img.save(ASSETS / "textures" / "entity" / "pale_man.png")
    glow.save(ASSETS / "textures" / "entity" / "pale_man_glowmask.png")
    write_preview(bones, img)
    write_template(bones, uv)
    write_json(ASSETS / "models" / "item" / "pale_man_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["entity.every_tunnel.pale_man"] = "Pale Man"
    lang["item.every_tunnel.pale_man_spawn_egg"] = "Pale Man Spawn Egg"
    write_json(lang_path, lang)
    e = extent(bones)
    cubes = sum(len(b.cubes) for b in bones.values())
    print(f"Pale man: {len(bones)} bones, {cubes} cubes, {len(animations()['animations'])} animations; "
          f"{e['y'][1] / 16:.2f} blocks tall, {(e['z'][1] - e['z'][0]) / 16:.2f} deep")


if __name__ == "__main__":
    sys.exit(main())
