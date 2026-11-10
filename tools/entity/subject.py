"""
The four test subjects of Level 5's rooms S-1 to S-4, for GeckoLib: people hired on six-month contracts,
turning, room by room, into what the Pale man is (the Pale men were people). Sedated, all of them, strapped
to a vertical stretcher: a padded board leaning back on two steel legs, straps at the wrists, the ankles, the
chest and the forehead; eyes shut, the head fallen to one side, the arms limp.

Run after changing anything here (from the repository root):   python3 tools/entity/subject.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/subject_<1..4>.geo.json             one body per stage
  animations/entity/subject.animation.json       one animation that holds the pose (they never move)
  textures/entity/subject_<1..4>.png (nothing of them glows: their eyes are shut)
and, in tools/entity/output/ (git-ignored): subject_preview.png (the four side by side, front and side).

One body, bent by a stage t from 0 (S-1) to 1 (S-4), between a person's proportions and the Pale man's
(tools/entity/pale_man.py): legs, arms, fingers and neck grow long and thin, the back stoops and the
neck falls forwards, the head shrinks and the ears grow; the skin goes from a living colour to his
bluish beige, the hair falls out. All wear a patient's gown, light green with a
number tag on the chest: clean on S-1, stained on S-2, torn on S-3, rags on S-4.

Conventions as in pale_man.py: the model faces north (-z); rotations are degrees [x, y, z]; box UV,
LAYOUT x LAYOUT units painted at SCALE pixels per unit; cleared pixels are holes.
"""

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from observer import draw_model  # noqa: E402
from pale_man import Bone, faces, write_json  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"
LAYOUT = 128
SCALE = 2
PX = LAYOUT * SCALE
STAGES = 4

SKIN_ALIVE = (226, 184, 156)
SKIN_PALE = (206, 190, 180)                 # the Pale man's
VEIN = (148, 162, 198)
HAIR = (62, 44, 34)
GOWN = (160, 204, 196)
GOWN_SHADE = (126, 170, 164)
GOWN_DOT = (120, 160, 176)
STAIN = (150, 136, 96)
TAG = (240, 240, 236)
BOARD = (176, 188, 184)                     # the stretcher's padded vinyl
STEEL = (168, 172, 176)
STRAP = (58, 54, 50)
BUCKLE = (196, 198, 200)
TILT = 12                                   # degrees the stretcher leans back


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c0, c1, t):
    return tuple(int(round(lerp(x, y, t))) for x, y in zip(c0, c1))


# =====================================================================================
# Geometry

def skeleton(t):
    """Bones and cubes for stage t (0 a person, 1 almost the Pale man), in model units, y up from the soles."""
    b = {}

    def bone(name, parent, pivot, rotation=(0, 0, 0)):
        b[name] = Bone(name, parent, pivot, rotation)

    def cube(bone_name, origin, size, kind, inflate=0.0):
        b[bone_name].cubes.append(([round(v, 2) for v in origin], [int(round(v)) for v in size], kind, inflate))

    thin = 2 if t >= 0.5 else 3                         # limbs' thickness
    shin, thigh = round(lerp(7, 11, t)), round(lerp(7, 11, t))
    hip = shin + thigh
    belly, chest = 4, round(lerp(8, 7, t))
    shoulder = hip + belly + chest
    neck = round(lerp(1, 3, t))
    head_h = round(lerp(7, 6, t))
    head_w = round(lerp(7, 5, t))
    width = round(lerp(8, 6, t))
    # On the stretcher the back lies flat but for the stage's stoop, the head falls forwards and to one side.
    lean, droop, lift = lerp(0, 4, t), lerp(0, 45, t), lerp(10, -5, t)
    upper, fore = round(lerp(6, 10, t)), round(lerp(5, 9, t))
    fingers = round(lerp(2, 4, t))
    half = thin / 2

    top = shoulder + neck
    # The stretcher, leaning back from its foot, the body on it: a padded board between two steel rails, and two
    # steel legs from its back to the floor behind.
    board_w = width + 2 * thin + 2
    board_h = top + head_h + 2
    bone("frame", None, (0, 0, 1.5), (-TILT, 0, 0))
    cube("frame", (-board_w / 2, 0, 1.5), (board_w, board_h, 1), "board")
    for x in (-board_w / 2 - 1, board_w / 2):
        cube("frame", (x, 0, 1), (1, board_h, 2), "rail")
    leg_top = round(board_h * 0.55)
    leg = leg_top * math.cos(math.radians(TILT)) / math.cos(math.radians(TILT + 11)) + 1
    bone("stand", "frame", (0, leg_top, 2.5), (2 * TILT + 11, 0, 0))     # 11 degrees past upright, backwards
    for x in (-board_w / 2 + 0.5, board_w / 2 - 1.5):
        cube("stand", (x, leg_top - leg, 2), (1, leg, 1), "rail")
    bone("root", "frame", (0, 0, 0))
    cube("root", (-width / 2, hip, -1.5), (width, 3, 3), "hips")
    for side, x in (("left", width / 4), ("right", -width / 4)):
        bone(f"{side}_thigh", "root", (x, hip, 0))
        cube(f"{side}_thigh", (x - half, shin, -half), (thin, thigh, thin), "leg")
        bone(f"{side}_shin", f"{side}_thigh", (x, shin, 0))
        cube(f"{side}_shin", (x - half, 1, -half), (thin, shin - 1, thin), "leg")
        cube(f"{side}_shin", (x - 1.5, 0, -3 - t * 1.5), (3, 1, 4 + round(t * 2)), "foot")
        cube(f"{side}_shin", (x - half, 2, -half), (thin, 2, thin), "strap", 0.45)
    bone("torso", "root", (0, hip + 3, 0), (lean, 0, 0))
    cube("torso", (-width / 2, hip + 3, -1.5), (width, belly - 3 + chest, 3), "body")
    skirt = round(thigh * 0.7)                          # the gown hangs to above the knees
    cube("torso", (-width / 2, hip - skirt, -1.5), (width, belly + chest + skirt, 3), "gown", 0.4)
    cube("torso", (-width / 2 - thin, shoulder - 4, -1.5), (width + 2 * thin, 2, 3), "strap", 0.7)
    bone("neck", "torso", (0, shoulder, -0.5), (droop, 0, 0))
    cube("neck", (-1, shoulder, -1.5), (2, neck, 2), "neck")
    bone("head", "neck", (0, shoulder + neck, -0.5), (lift, 0, 14))
    cube("head", (-head_w / 2, top, -head_w / 2 - 0.5), (head_w, head_h, head_w), "head")
    cube("head", (-head_w / 2, top + head_h - 2, -head_w / 2 - 0.5), (head_w, 2, head_w), "strap", 0.35)
    ear = round(lerp(1, 3, t))
    cube("head", (head_w / 2, top + head_h / 2 - ear / 2, -1), (1, ear, 2), "ear")
    cube("head", (-head_w / 2 - 1, top + head_h / 2 - ear / 2, -1), (1, ear, 2), "ear")
    for side, x in (("left", width / 2 + half), ("right", -width / 2 - half)):
        bone(f"{side}_arm", "torso", (x, shoulder - 0.5, 0), (-lean, 0, 0))
        cube(f"{side}_arm", (x - half, shoulder - 0.5 - upper, -half), (thin, upper, thin), "arm")
        cube(f"{side}_arm", (x - half, shoulder - 0.5 - 3, -half), (thin, 3, thin), "sleeve", 0.35)
        bone(f"{side}_forearm", f"{side}_arm", (x, shoulder - 0.5 - upper, 0))
        cube(f"{side}_forearm", (x - half, shoulder - 0.5 - upper - fore, -half), (thin, fore, thin), "arm")
        cube(f"{side}_forearm", (x - half, shoulder - 0.5 - upper - fore, -half), (thin, 2, thin), "strap", 0.45)
        bone(f"{side}_hand", f"{side}_forearm", (x, shoulder - 0.5 - upper - fore, 0))
        hand = 3 if t < 0.5 else 2
        cube(f"{side}_hand", (x - hand / 2, shoulder - 0.5 - upper - fore - 2 - fingers, -0.5), (hand, 2 + fingers, 1), "hand")
    return b


def box_area(size):
    w, h, d = (math.ceil(s) for s in size)
    return 2 * (w + d), d + h


def pack(bones):
    items = [(bn, i, box_area(c[1])) for bn, bone in bones.items() for i, c in enumerate(bone.cubes)]
    items.sort(key=lambda it: (-it[2][1], -it[2][0]))
    uv, x, y, shelf = {}, 0, 0, 0
    for bn, i, (w, h) in items:
        if x + w > LAYOUT:
            x, y = 0, y + shelf
            shelf = 0
        if y + h > LAYOUT:
            raise SystemExit(f"a subject's cubes do not fit in a {LAYOUT}x{LAYOUT} texture")
        uv[(bn, i)] = (x, y)
        x += w
        shelf = max(shelf, h)
    return uv


# =====================================================================================
# Painting

class Painter:
    def __init__(self, rng, t):
        self.rng, self.t = rng, t
        self.img = Image.new("RGBA", (PX, PX), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (PX, PX), (0, 0, 0, 0))
        self.skin_colour = mix(SKIN_ALIVE, SKIN_PALE, t)

    def put(self, x, y, c, amount=0):
        d = self.rng.uniform(-amount, amount) if amount else 0
        self.img.putpixel((x, y), tuple(max(0, min(255, int(round(ch + d)))) for ch in c[:3]) + (255,))

    def fill(self, rect, c, amount=0):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c, amount)

    def clear(self, x, y):
        self.img.putpixel((x, y), (0, 0, 0, 0))

    def skin(self, rect):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                vein = self.rng.random() < 0.12 * self.t
                self.put(x, y, VEIN if vein else self.skin_colour, 6)

    def gown(self, rect, holes):
        """The gown's cloth: light green with small dots, stains from S-2 on, holes from S-3."""
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                if self.rng.random() < holes:
                    self.clear(x, y)
                    continue
                c = GOWN_DOT if (x + 2 * y) % 5 == 0 else GOWN_SHADE if self.rng.random() < 0.1 else GOWN
                if self.t > 0.2 and self.rng.random() < 0.08 * self.t * 3:
                    c = STAIN
                self.put(x, y, c, 4)


def paint(p, kind, rects, number):
    t = p.t
    holes = 0.0 if t < 0.5 else 0.12 if t < 0.9 else 0.35
    if kind in ("leg", "arm", "neck", "body"):
        for r in rects.values():
            p.skin(r)
    elif kind in ("foot", "hand"):
        for r in rects.values():
            p.skin(r)
    elif kind == "ear":
        for r in rects.values():
            p.skin(r)
    elif kind == "board":
        for r in rects.values():
            p.fill(r, BOARD, 5)
            x0, y0, x1, y1 = r
            for y in range(y0 + 3, y1, 12):            # the padding's seams
                for x in range(x0, x1):
                    p.put(x, y, mix(BOARD, (90, 100, 98), 0.5), 3)
    elif kind == "rail":
        for r in rects.values():
            p.fill(r, STEEL, 8)
    elif kind == "strap":
        for name, r in rects.items():
            p.fill(r, STRAP, 5)
        x0, y0, x1, y1 = rects["north"]                # a buckle in front
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        for x in range(cx - 1, cx + 1):
            for y in range(max(y0, cy - 1), min(y1, cy + 1)):
                p.put(x, y, BUCKLE, 6)
    elif kind == "hips":
        for r in rects.values():
            p.gown(r, holes * 0.5)
    elif kind in ("gown", "sleeve"):
        for name, r in rects.items():
            if name in ("up", "down") and kind == "gown":
                continue
            p.gown(r, holes)
        if kind == "gown":
            x0, y0, x1, y1 = rects["north"]
            # Ragged hem from S-3 on.
            if t >= 0.5:
                for x in range(x0, x1):
                    for y in range(y1 - p.rng.choice((0, 1, 2, 4, 6)), y1):
                        p.clear(x, y)
            tx, ty = x1 - 7, y0 + 4                     # the number tag on the left chest
            for x in range(tx, tx + 5):
                for y in range(ty, ty + 3):
                    p.put(x, y, TAG, 2)
            for i in range(number):
                p.put(tx + 1 + i, ty + 1, (40, 40, 44))
    elif kind == "head":
        for name, r in rects.items():
            p.skin(r)
        hair_left = max(0.0, 1 - t * 1.6)
        for name in ("up", "south", "east", "west"):    # hair, thinning to nothing
            x0, y0, x1, y1 = rects[name]
            limit = y1 if name == "up" else y0 + (y1 - y0) // (1 if name == "south" else 3)
            for y in range(y0, limit):
                for x in range(x0, x1):
                    if p.rng.random() < hair_left:
                        p.put(x, y, HAIR, 8)
        x0, y0, x1, y1 = rects["north"]
        w, h = x1 - x0, y1 - y0
        if hair_left > 0:                                # a fringe
            for x in range(x0, x1):
                if p.rng.random() < hair_left:
                    p.put(x, y0, HAIR, 8)
        ey = y0 + h // 2 - 1
        for ex in (x0 + w // 4 - 1, x1 - w // 4 - 1):     # shut: a dark lash line under a heavy lid
            for dx in (0, 1):
                p.put(ex + dx, ey, mix(p.skin_colour, (60, 44, 42), 0.6), 3)
                p.put(ex + dx, ey - 1, mix(p.skin_colour, (120, 100, 96), 0.4 + t * 0.3), 3)
        cx = x0 + w // 2
        for x in range(cx - 2, cx + 2):                  # the mouth, a thin line
            p.put(x, y1 - 3, (120, 80, 76))


def texture(bones, uv, t, number):
    p = Painter(random.Random(f"subject-{number}"), t)
    for bn, bone in bones.items():
        for i, (origin, size, kind, inflate) in enumerate(bone.cubes):
            paint(p, kind, faces(*uv[(bn, i)], size), number)
    return p.img, p.glow


# =====================================================================================
# Files

def geo(name, bones, uv):
    out = []
    for bn, bone in bones.items():
        entry = {"name": bn, "pivot": [round(v, 2) for v in bone.pivot]}
        if bone.parent:
            entry["parent"] = bone.parent
        if any(bone.rotation):
            entry["rotation"] = [round(v, 2) for v in bone.rotation]
        cubes = [{"origin": o, "size": s, "uv": list(uv[(bn, i)]), **({"inflate": inf} if inf else {})}
                 for i, (o, s, _, inf) in enumerate(bone.cubes)]
        if cubes:
            entry["cubes"] = cubes
        out.append(entry)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{name}", "texture_width": LAYOUT, "texture_height": LAYOUT,
                        "visible_bounds_width": 3, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out}]}


def still():
    return {"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {
        "animation.subject.still": {"loop": True, "animation_length": 1.0, "bones": {}}}}


def write_preview(stages):
    h = 520
    sheet = Image.new("RGB", (len(stages) * 2 * 180 + 40, h + 20), (46, 48, 56))
    d = ImageDraw.Draw(sheet)
    for n, (bones, uv, tex) in enumerate(stages):
        straight = skeleton(n / (STAGES - 1))
        for name in straight:
            straight[name].rotation = [0, 0, 0]
        for k, (view, pose) in enumerate((("front", straight), ("side", bones))):
            ox = 20 + (n * 2 + k) * 180
            d.text((ox, 10), f"S-{n + 1}, {view}", fill=(220, 220, 220))
            base = h - 10
            d.line((ox, base, ox + 170, base), fill=(110, 110, 110))
            for blocks in (1, 2):
                y = base - blocks * 160
                d.line((ox, y, ox + 170, y), fill=(80, 70, 70))
                d.text((ox + 150, y - 12), f"{blocks}", fill=(120, 110, 110))
            draw_model(sheet, pose, uv, tex, ox + 85, base, "front" if view == "front" else "side")
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "subject_preview.png")


def main():
    stages = []
    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)
    for n in range(1, STAGES + 1):
        t = (n - 1) / (STAGES - 1)
        bones = skeleton(t)
        uv = pack(bones)
        tex, glow = texture(bones, uv, t, n)
        write_json(ASSETS / "geo" / "entity" / f"subject_{n}.geo.json", geo(f"subject_{n}", bones, uv))
        tex.save(tex_dir / f"subject_{n}.png")
        # Only what glows gets a glow mask (GeckoLib refuses an empty one): S-4's eyes.
        mask = tex_dir / f"subject_{n}_glowmask.png"
        if glow.getbbox():
            glow.save(mask)
        else:
            mask.unlink(missing_ok=True)
        stages.append((bones, uv, tex))
    write_json(ASSETS / "animations" / "entity" / "subject.animation.json", still())
    write_preview(stages)
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["entity.every_tunnel.subject"] = "Test Subject"
    write_json(lang_path, lang)
    print(f"Subjects: {STAGES} stages")


if __name__ == "__main__":
    sys.exit(main())
