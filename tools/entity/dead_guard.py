"""
The guard before you, for GeckoLib: Night Guard No. 13, who stayed, dead in Level 5's drain, in the
Hideout. He sits on the floor against the wall, legs out in front of him, hands fallen on the floor by
his hips, his head dropped on his chest. Still: he never moves.

He wears the player's uniform (the skin tools/entity/guard.py paints, textures/entity/guard.png; run that
first): this takes it and wears it out. Months down there: the face grey, the eyes closed, a few days'
beard, grime and water stains on the jacket and trousers, a knee worn through.

Run after changing anything here, or the uniform (from the repository root):   python3 tools/entity/dead_guard.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/dead_guard.geo.json                 a player's body (the 64x64 skin's boxes, wide arms), sitting
  animations/entity/dead_guard.animation.json    one animation that holds the pose
  textures/entity/dead_guard.png                 nothing of him glows, so no glow mask
and, in tools/entity/output/ (git-ignored): dead_guard_preview.png (front and side).

Conventions as in pale_man.py: the model faces north (-z); rotations are degrees [x, y, z]; box UV on
the standard skin layout.
"""

import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from observer import draw_model  # noqa: E402
from pale_man import Bone, write_json  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
UNIFORM = ASSETS / "textures" / "entity" / "guard.png"
OUT = Path(__file__).resolve().parent / "output"

# The uniform's skin colours (guard.py), and what they turn to.
SKIN = ((196, 160, 138), (166, 130, 112))
GREY = ((158, 148, 140), (128, 118, 112))
LID = (92, 80, 76)
BEARD = (84, 74, 68)
GRIME = (58, 50, 40)
WATER_MARK = (70, 72, 64)

# The skin's boxes: (bone, uv, size), as a player's (wide arms).
BOXES = {"head": ((0, 0), (8, 8, 8)), "body": ((16, 16), (8, 12, 4)), "right_arm": ((40, 16), (4, 12, 4)),
         "left_arm": ((32, 48), (4, 12, 4)), "right_leg": ((0, 16), (4, 12, 4)), "left_leg": ((16, 48), (4, 12, 4))}

# The pose, sitting against the wall (degrees; a negative x turns what hangs down forward, a positive x leans
# forward what stands up): legs out in front, a knee a little up; the back on the wall, the head on the chest;
# the arms fallen, hands on the floor by the hips.
POSE = {"right_leg": (-86, 6, 0), "left_leg": (-74, -8, 0), "body": (-10, 0, 0), "head": (38, 0, 8),
        "right_arm": (-12, 0, 8), "left_arm": (-16, 0, -6)}
# Sitting, the hips are this far down from a standing player's; and he sits this far back of his spawn point
# (the middle of a block against a wall), so his back is on the wall.
SIT = 10
BACK = 3.5


def skeleton(posed=True):
    """The bones and cubes of a player's body, in model units, y up from the floor (sitting: down by SIT)."""
    drop = SIT if posed else 0
    b = {}

    def bone(name, parent, pivot, kind):
        x, y, z = pivot
        b[name] = Bone(name, parent, (x, y - drop, z + BACK), POSE.get(name, (0, 0, 0)) if posed else (0, 0, 0))
        (u, v), size = BOXES[kind]
        return u, v, size

    def cube(name, origin, uv_size):
        u, v, size = uv_size
        x, y, z = origin
        b[name].cubes.append(([x, y - drop, z + BACK], list(size), (u, v), 0.0))

    b["root"] = Bone("root", None, (0, 12 - drop, BACK))
    cube("body", (-4, 12, -2), bone("body", "root", (0, 12, 0), "body"))
    cube("head", (-4, 24, -4), bone("head", "body", (0, 24, 0), "head"))
    cube("right_arm", (-8, 12, -2), bone("right_arm", "body", (-5, 22, 0), "right_arm"))
    cube("left_arm", (4, 12, -2), bone("left_arm", "body", (5, 22, 0), "left_arm"))
    cube("right_leg", (-3.9, 0, -2), bone("right_leg", "root", (-1.9, 12, 0), "right_leg"))
    cube("left_leg", (-0.1, 0, -2), bone("left_leg", "root", (1.9, 12, 0), "left_leg"))
    return b


def faces(u, v, w, h, d):
    """Pixel rectangles (x0, y0, x1, y1) of a box's faces on the skin (as guard.py's: front is north)."""
    return {"up": (u + d, v, u + d + w, v + d), "down": (u + d + w, v, u + d + 2 * w, v + d),
            "east": (u, v + d, u + d, v + d + h), "north": (u + d, v + d, u + d + w, v + d + h),
            "west": (u + d + w, v + d, u + 2 * d + w, v + d + h), "south": (u + 2 * d + w, v + d, u + 2 * (d + w), v + d + h)}


def near(c, ref, by=14):
    return all(abs(a - b) <= by for a, b in zip(c[:3], ref))


def texture():
    """The uniform's skin, dead and worn: grey skin, eyes shut, beard, grime, a knee through."""
    rng = random.Random("dead-guard")
    img = Image.open(UNIFORM).convert("RGBA")
    px = img.load()

    def put(x, y, c, amount=4):
        d = rng.randint(-amount, amount)
        px[x, y] = tuple(max(0, min(255, ch + d)) for ch in c) + (255,)

    for y in range(64):
        for x in range(64):
            c = px[x, y]
            if c[3] == 0:
                continue
            for alive, dead in zip(SKIN, GREY):
                if near(c, alive):
                    put(x, y, dead)
    x0, y0 = 8, 8                                                    # the face
    for ex in (1, 5):
        for dx in (0, 1):
            put(x0 + ex + dx, y0 + 4, LID, 3)                        # closed, sunk in dark
            put(x0 + ex + dx, y0 + 5, GREY[1], 3)
    for x in range(x0, x0 + 8):                                      # a few days' beard on the jaw and chin
        for y in (y0 + 6, y0 + 7):
            if rng.random() < 0.6 and px[x, y][:3] != (128, 92, 84):
                put(x, y, BEARD, 6)
    # Grime and water marks over the jacket, the trousers and the boots; nothing on the face.
    for bone, ((u, v), (w, h, d)) in BOXES.items():
        if bone == "head":
            continue
        for x0, y0, x1, y1 in faces(u, v, w, h, d).values():
            for y in range(y0, y1):
                for x in range(x0, x1):
                    roll = rng.random()
                    if roll < 0.07:
                        put(x, y, GRIME, 6)
                    elif roll < 0.1:
                        put(x, y, WATER_MARK, 6)
            if bone.endswith("leg"):                                 # tide marks low on the trousers
                for x in range(x0, x1):
                    if rng.random() < 0.7:
                        put(x, y1 - 4, WATER_MARK, 4)
    (u, v), (w, h, d) = BOXES["left_leg"]                            # the left knee worn through
    x0, y0, x1, y1 = faces(u, v, w, h, d)["north"]
    for x in range(x0 + 1, x1 - 1):
        put(x, y0 + 5, GREY[0], 4)
        if rng.random() < 0.5:
            put(x, y0 + 6, GREY[1], 4)
    return img


# =====================================================================================
# Files

def geo(bones):
    out = []
    for bn, bone in bones.items():
        entry = {"name": bn, "pivot": [round(v, 2) for v in bone.pivot]}
        if bone.parent:
            entry["parent"] = bone.parent
        if any(bone.rotation):
            entry["rotation"] = [round(v, 2) for v in bone.rotation]
        cubes = [{"origin": [round(v, 2) for v in o], "size": s, "uv": list(uv)} for o, s, uv, _ in bone.cubes]
        if cubes:
            entry["cubes"] = cubes
        out.append(entry)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.dead_guard", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": out}]}


def still():
    return {"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {
        "animation.dead_guard.still": {"loop": True, "animation_length": 1.0, "bones": {}}}}


def write_preview(tex):
    """Standing (to see the uniform) and sitting, front and side; draw_model wants (origin, size, kind, inflate)
    cubes and uv by (bone, index), so the cubes are told that way."""
    h = 360
    sheet = Image.new("RGB", (3 * 220 + 40, h + 20), (46, 48, 56))
    d = ImageDraw.Draw(sheet)
    for k, (label, posed, view) in enumerate((("standing, front", False, "front"), ("sitting, front", True, "front"),
                                             ("sitting, side", True, "side"))):
        bones = skeleton(posed)
        uv = {}
        for bn, bone in bones.items():
            for i, (o, s, (u, v), _) in enumerate(bone.cubes):
                uv[(bn, i)] = (u, v)
            bone.cubes = [(o, s, "skin", 0.0) for o, s, _, _ in bone.cubes]
        ox = 20 + k * 220
        d.text((ox, 10), label, fill=(220, 220, 220))
        base = h - 10
        d.line((ox, base, ox + 210, base), fill=(110, 110, 110))
        draw_model(sheet, bones, uv, tex.resize((128, 128), Image.NEAREST), ox + 105, base, view)
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "dead_guard_preview.png")


def main():
    bones = skeleton()
    tex = texture()
    write_json(ASSETS / "geo" / "entity" / "dead_guard.geo.json", geo(bones))
    tex.save(ASSETS / "textures" / "entity" / "dead_guard.png")
    write_json(ASSETS / "animations" / "entity" / "dead_guard.animation.json", still())
    write_preview(tex)
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["entity.every_tunnel.dead_guard"] = "Night Guard No. 13"
    write_json(lang_path, lang)
    print("The guard before you: sitting against the wall")


if __name__ == "__main__":
    sys.exit(main())
