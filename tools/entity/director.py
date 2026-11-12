"""
The director, for GeckoLib: the man behind the desk at the top of Level 5, who talks to you in the
boss's cinematic. For now a STAND-IN: a mannequin in flat chroma green, sitting in his executive chair
with his forearms on the desk, so the cinematic's camera can be judged before his real model (tuxedo,
the film's mask, his tired face) takes its place.

Run after changing anything here (from the repository root):   python3 tools/entity/director.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/director.geo.json                 a player's body (the 64x64 skin's boxes, wide arms), sitting
  animations/entity/director.animation.json    one animation that holds the pose
  textures/entity/director.png                 chroma green, nothing glows
and, in tools/entity/output/ (git-ignored): director_preview.png (front and side).

Conventions as in dead_guard.py: the model faces north (-z); rotations are degrees [x, y, z]; box UV on
the standard skin layout.
"""

import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dead_guard import BOXES, faces  # noqa: E402
from observer import draw_model  # noqa: E402
from pale_man import Bone, write_json  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"

CHROMA = (0, 177, 64)

# The pose, in his chair (degrees, as dead_guard.py's): thighs forward on the seat, the back upright against
# the chair's, the forearms forward onto the desk, the head level, looking at the door.
POSE = {"right_leg": (-88, 4, 0), "left_leg": (-88, -4, 0), "body": (0, 0, 0), "head": (0, 0, 0),
        "right_arm": (-62, 8, 0), "left_arm": (-62, -8, 0)}
# Sitting, the hips are this far down from a standing player's (on the chair's seat, 9 px up, a leg's half
# above it); and he sits this far back of his spawn point (the middle of the chair's block), his back on the
# chair's.
SIT = 1
BACK = 1.5


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


def texture():
    """Flat chroma green over every face of the skin's boxes, a faint grain so it is not a hole."""
    rng = random.Random("director-stand-in")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for (u, v), (w, h, d) in BOXES.values():
        for x0, y0, x1, y1 in faces(u, v, w, h, d).values():
            for y in range(y0, y1):
                for x in range(x0, x1):
                    g = rng.randint(-5, 5)
                    px[x, y] = (CHROMA[0], CHROMA[1] + g, CHROMA[2] + g // 2, 255)
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
        "description": {"identifier": "geometry.director", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": out}]}


def still():
    return {"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {
        "animation.director.still": {"loop": True, "animation_length": 1.0, "bones": {}}}}


def write_preview(tex):
    """Sitting, front and side; draw_model wants (origin, size, kind, inflate) cubes and uv by (bone, index)."""
    h = 360
    sheet = Image.new("RGB", (2 * 220 + 40, h + 20), (46, 48, 56))
    d = ImageDraw.Draw(sheet)
    for k, view in enumerate(("front", "side")):
        bones = skeleton()
        uv = {}
        for bn, bone in bones.items():
            for i, (o, s, (u, v), _) in enumerate(bone.cubes):
                uv[(bn, i)] = (u, v)
            bone.cubes = [(o, s, "skin", 0.0) for o, s, _, _ in bone.cubes]
        ox = 20 + k * 220
        d.text((ox, 10), f"sitting, {view}", fill=(220, 220, 220))
        base = h - 10
        d.line((ox, base, ox + 210, base), fill=(110, 110, 110))
        draw_model(sheet, bones, uv, tex.resize((128, 128), Image.NEAREST), ox + 105, base, view)
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "director_preview.png")


def main():
    bones = skeleton()
    tex = texture()
    write_json(ASSETS / "geo" / "entity" / "director.geo.json", geo(bones))
    tex.save(ASSETS / "textures" / "entity" / "director.png")
    write_json(ASSETS / "animations" / "entity" / "director.animation.json", still())
    write_preview(tex)
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["entity.every_tunnel.director"] = "The Director"
    write_json(lang_path, lang)
    print("The director (stand-in): sitting in his chair")


if __name__ == "__main__":
    sys.exit(main())
