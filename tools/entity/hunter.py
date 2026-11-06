"""
The Hunters' model, animations and texture for GeckoLib (Level 5's little ones, in groups of three).

Run after changing anything here (from the repository root):   python3 tools/entity/hunter.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/hunter.geo.json                    bones and cubes (Bedrock geometry, 16 units = 1 block)
  animations/entity/hunter.animation.json       idle, walk, run, attack
  textures/entity/hunter.png                    the skin, box UV, SCALE pixels per unit
  textures/entity/hunter_glowmask.png           the eyes, drawn at full brightness (AutoGlowingGeoLayer)
  and the name in lang/en_us.json
and, in tools/entity/output/ (git-ignored): hunter_preview.png (the texture enlarged, front and
side views, the run pose from the side, and the front in the other coat).

The look: humanoids smaller than the player (about 1.4 blocks), pitch black, with blank white eyes
that glow in the dark, in lab coats ([COAT] picks the coat's colour). Heads a little too big for
their bodies, like children's; long thin arms that hang to the knees; a slight stoop. They walk
lightly and run hunched far forwards with their arms trailing behind.

Conventions as in pale_man.py and observer.py (whose geometry and preview helpers this reuses): the
model faces north (-z); rotations are degrees [x, y, z]; a negative x rotation swings a hanging
limb forwards, a positive one leans an upright part forwards; box UV, LAYOUT x LAYOUT units
painted at SCALE pixels per unit.
"""

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from observer import draw_model, extent  # noqa: E402
from pale_man import Bone, faces, keys, swing, write_json  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"
LAYOUT = 64
SCALE = 2
PX = LAYOUT * SCALE

SKIN = (24, 24, 28)
SKIN_LIGHT = (40, 40, 47)
SKIN_DARK = (12, 12, 15)
EYE = (240, 242, 236)

# The lab coat: (base, light, shade, seam)
COATS = {
    "white": ((206, 210, 204), (226, 228, 222), (168, 174, 170), (142, 148, 146)),
    "black": ((40, 40, 45), (56, 56, 62), (28, 28, 32), (20, 20, 24)),
}
COAT = "white"
STAIN = (120, 96, 72)
BUTTON = (110, 114, 116)

STOOP = 10                    # degrees the torso leans while standing
RUN_LEAN = 38                 # and while running


# =====================================================================================
# Geometry

def skeleton():
    """Bones and cubes, in model units (y up from the soles, front towards -z). About 23 tall."""
    b = {}

    def bone(name, parent, pivot, rotation=(0, 0, 0)):
        b[name] = Bone(name, parent, pivot, rotation)

    def cube(bone_name, origin, size, kind, inflate=0.0):
        b[bone_name].cubes.append((list(origin), list(size), kind, inflate))

    bone("root", None, (0, 0, 0))
    for side, x in (("left", 1.5), ("right", -1.5)):
        bone(f"{side}_thigh", "root", (x, 9, 0))
        cube(f"{side}_thigh", (x - 1, 5, -1), (2, 4, 2), "leg")
        bone(f"{side}_shin", f"{side}_thigh", (x, 5, 0))
        cube(f"{side}_shin", (x - 1, 1, -1), (2, 4, 2), "leg")
        cube(f"{side}_shin", (x - 1, 0, -2), (2, 1, 3), "foot")
    bone("torso", "root", (0, 9, 0), (STOOP, 0, 0))
    cube("torso", (-3, 9, -1.5), (6, 7, 3), "body")
    cube("torso", (-3, 9, -1.5), (6, 7, 3), "coat", 0.45)
    bone("tails", "torso", (0, 9, 0), (-STOOP, 0, 0))                    # the coat's tails, hanging to the knees
    cube("tails", (-3, 4, -1.5), (6, 5, 3), "skirt", 0.6)
    bone("neck", "torso", (0, 16, 0), (-STOOP * 0.6, 0, 0))
    cube("neck", (-1, 16, -1), (2, 1, 2), "neck")
    bone("head", "neck", (0, 17, 0))
    cube("head", (-3, 17, -3), (6, 6, 6), "head")
    for side, x in (("left", 4), ("right", -4)):
        bone(f"{side}_arm", "torso", (x, 15.5, 0), (-STOOP, 0, 0))         # hanging straight down
        cube(f"{side}_arm", (x - 1, 10.5, -1), (2, 5, 2), "arm")
        cube(f"{side}_arm", (x - 1, 10.5, -1), (2, 5, 2), "sleeve", 0.35)
        bone(f"{side}_forearm", f"{side}_arm", (x, 10.5, 0))
        cube(f"{side}_forearm", (x - 1, 5.5, -1), (2, 5, 2), "arm")
        cube(f"{side}_forearm", (x - 1, 7.5, -1), (2, 3, 2), "cuff", 0.35)
        bone(f"{side}_hand", f"{side}_forearm", (x, 5.5, 0))
        cube(f"{side}_hand", (x - 1, 3, -1), (2, 2.5, 2), "hand")
    return b


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
            raise SystemExit(f"the Hunter's cubes do not fit in a {LAYOUT}x{LAYOUT} texture")
        uv[(bn, i)] = (x, y)
        x += w
        shelf = max(shelf, h)
    return uv


# =====================================================================================
# Painting

class Painter:
    def __init__(self, rng):
        self.rng = rng
        self.img = Image.new("RGBA", (PX, PX), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (PX, PX), (0, 0, 0, 0))

    def put(self, x, y, c, amount=0):
        d = self.rng.uniform(-amount, amount) if amount else 0
        self.img.putpixel((x, y), tuple(max(0, min(255, int(round(ch + d)))) for ch in c[:3]) + (255,))

    def clear(self, x, y):
        self.img.putpixel((x, y), (0, 0, 0, 0))

    def fill(self, rect, c, amount=3):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c, amount)

    def skin(self, rect, top=False):
        """Matte black, a faint sheen higher up."""
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                r = self.rng.random()
                c = SKIN_LIGHT if (top or y - y0 < 2) and r < 0.25 else SKIN_DARK if r < 0.12 else SKIN
                self.put(x, y, c, 2)


def paint(p, kind, rects, coat):
    S = SCALE
    base, light, shade, seam = COATS[coat]
    rng = p.rng
    sides = ("north", "east", "south", "west")
    if kind in ("leg", "neck", "body"):
        for r in rects.values():
            p.skin(r)
    elif kind == "foot":
        for r in rects.values():
            p.fill(r, SKIN_DARK, 2)
    elif kind == "hand":
        for name, r in rects.items():
            p.skin(r)
        for name in ("north", "south", "east", "west"):            # long fingers, a little greyer at the tips
            x0, y0, x1, y1 = rects[name]
            for x in range(x0, x1, 2):
                p.put(x, y1 - 1, SKIN_LIGHT, 3)
    elif kind == "head":
        for name, r in rects.items():
            p.skin(r, top=name == "up")
        x0, y0, x1, y1 = rects["north"]                            # 6 x 6 units: 12 x 12 pixels
        for ex in (x0 + 2, x0 + 8):                                # two blank white eyes, 2 x 2 pixels each
            for dx in (0, 1):
                for dy in (0, 1):
                    p.put(ex + dx, y0 + 5 + dy, EYE, 3)
                    p.glow.putpixel((ex + dx, y0 + 5 + dy), EYE + (255,))
            p.put(ex, y0 + 4, SKIN_DARK)                            # a heavy brow above each
            p.put(ex + 1, y0 + 4, SKIN_DARK)
    elif kind in ("coat", "sleeve", "skirt", "cuff"):
        for name, (x0, y0, x1, y1) in rects.items():
            for y in range(y0, y1):
                for x in range(x0, x1):
                    r = rng.random()
                    c = shade if name in ("down", "south") and r < 0.4 else light if name == "up" or r < 0.08 else base
                    p.put(x, y, c, 3)
            if rng.random() < 0.5:                                  # a stain here and there
                sx, sy = rng.randrange(x0, max(x0 + 1, x1 - 1)), rng.randrange(y0, max(y0 + 1, y1 - 1))
                for dx, dy in ((0, 0), (1, 0), (0, 1)):
                    if x0 <= sx + dx < x1 and y0 <= sy + dy < y1:
                        p.put(sx + dx, sy + dy, STAIN, 8)
        if kind == "coat":
            x0, y0, x1, y1 = rects["north"]                        # 6 x 7 units: 12 x 14 pixels
            cx = (x0 + x1) // 2
            for y in range(y0, y0 + 6):                            # the lapels' V, showing the black body
                half = max(0, 3 - (y - y0) // 2)
                for x in range(cx - half, cx + half):
                    p.put(x, y, SKIN, 2)
                p.put(cx - half - 1, y, seam, 2)
                p.put(cx + half, y, seam, 2)
            for y in range(y0 + 6, y1):                            # where it closes
                p.put(cx, y, seam, 2)
            for by in (y0 + 7, y0 + 10, y0 + 13):
                p.put(cx - 1, by, BUTTON, 3)
        elif kind == "skirt":
            x0, y0, x1, y1 = rects["north"]
            cx = (x0 + x1) // 2
            for y in range(y0, y1):                                # it hangs open below the last button
                p.put(cx, y, seam, 2)
                if y > y0 + 3:
                    p.put(cx - 1, y, SKIN, 2)
            for px0 in (x0 + 1, cx + 2):                            # two pockets
                for x in range(px0, px0 + 3):
                    p.put(x, y0 + 2, seam, 2)
                for y in range(y0 + 2, y0 + 5):
                    p.put(px0, y, seam, 2)
                    p.put(px0 + 2, y, seam, 2)
            for name in sides:                                     # a ragged, torn hem
                fx0, fy0, fx1, fy1 = rects[name]
                for x in range(fx0, fx1):
                    for y in range(fy1 - rng.choice((0, 0, 1, 1, 2, 3)), fy1):
                        p.clear(x, y)
        elif kind == "cuff":
            for name in sides:                                     # the sleeve's edge
                x0, y0, x1, y1 = rects[name]
                for x in range(x0, x1):
                    p.put(x, y1 - 1, seam, 2)


def texture(bones, uv, coat=COAT):
    p = Painter(random.Random("hunter-skin-1"))
    for bn, bone in bones.items():
        for i, (origin, size, kind, inflate) in enumerate(bone.cubes):
            paint(p, kind, faces(*uv[(bn, i)], size), coat)
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
        cubes = [{"origin": o, "size": s, "uv": list(uv[(bn, i)]), **({"inflate": inf} if inf else {})}
                 for i, (o, s, _, inf) in enumerate(bone.cubes)]
        if cubes:
            entry["cubes"] = cubes
        out.append(entry)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.hunter", "texture_width": LAYOUT, "texture_height": LAYOUT,
                        "visible_bounds_width": 2, "visible_bounds_height": 2.5, "visible_bounds_offset": [0, 1, 0]},
        "bones": out}]}


def animations():
    walk, run = 0.9, 0.36
    A = {}
    A["animation.hunter.idle"] = {"loop": True, "animation_length": 3.2, "bones": {
        # Breathing quickly, and now and then the head snaps to one side and stays there a moment.
        "torso": {"rotation": swing(1.6, 1.5, offset=(STOOP, 0, 0))},
        "head": {"rotation": keys((0, [0, 0, 0]), (1.2, [0, 0, 0]), (1.26, [6, 14, 22]), (2.1, [6, 14, 24]),
                                  (2.16, [0, 0, 0]), (3.2, [0, 0, 0]))},
        "left_arm": {"rotation": swing(3.2, 2.0, offset=(-STOOP, 0, 0))},
        "right_arm": {"rotation": swing(3.2, 2.0, math.pi, offset=(-STOOP, 0, 0))},
        "left_hand": {"rotation": keys((0, [0, 0, 0]), (2.4, [0, 0, 0]), (2.45, [-25, 0, 0]), (2.55, [0, 0, 0]))},
    }}
    A["animation.hunter.walk"] = {"loop": True, "animation_length": walk, "bones": {
        "left_thigh": {"rotation": swing(walk, 24)},
        "right_thigh": {"rotation": swing(walk, 24, math.pi)},
        "left_shin": {"rotation": swing(walk, 14, -math.pi / 2, offset=(12, 0, 0))},
        "right_shin": {"rotation": swing(walk, 14, math.pi / 2, offset=(12, 0, 0))},
        "left_arm": {"rotation": swing(walk, 14, math.pi, offset=(-STOOP, 0, 0))},
        "right_arm": {"rotation": swing(walk, 14, offset=(-STOOP, 0, 0))},
        "root": {"position": swing(walk / 2, 0.3, axis=1)},
        "head": {"rotation": swing(walk, 3, axis=2)},
    }}
    A["animation.hunter.run"] = {"loop": True, "animation_length": run, "bones": {
        # Hunched far forwards, head up to see, the arms trailing behind.
        "torso": {"rotation": keys((0, [RUN_LEAN, 0, 0]))},
        "neck": {"rotation": keys((0, [-RUN_LEAN * 0.8, 0, 0]))},
        "tails": {"rotation": swing(run / 2, 6, offset=(-RUN_LEAN + 18, 0, 0))},      # flapping behind
        "left_thigh": {"rotation": swing(run, 52, offset=(-16, 0, 0))},
        "right_thigh": {"rotation": swing(run, 52, math.pi, offset=(-16, 0, 0))},
        "left_shin": {"rotation": swing(run, 32, -math.pi / 2, offset=(40, 0, 0))},
        "right_shin": {"rotation": swing(run, 32, math.pi / 2, offset=(40, 0, 0))},
        "left_arm": {"rotation": swing(run, 10, math.pi, offset=(20, 0, -14))},
        "right_arm": {"rotation": swing(run, 10, offset=(20, 0, 14))},
        "root": {"position": swing(run / 2, 0.6, offset=(0, -1.2, 0), axis=1)},
    }}
    A["animation.hunter.attack"] = {"loop": False, "animation_length": 0.5, "bones": {
        # A lunge and a swipe of the right hand.
        "torso": {"rotation": keys((0, [STOOP, 0, 0]), (0.12, [0, 0, 0]), (0.25, [32, 0, 0]), (0.5, [STOOP, 0, 0]))},
        "right_arm": {"rotation": keys((0, [-STOOP, 0, 0]), (0.12, [-160, 0, 20]), (0.25, [-50, 0, -10]), (0.5, [-STOOP, 0, 0]))},
        "left_arm": {"rotation": keys((0, [-STOOP, 0, 0]), (0.25, [-40, 0, -10]), (0.5, [-STOOP, 0, 0]))},
    }}
    return {"format_version": "1.8.0", "animations": A, "geckolib_format_version": 2}


# =====================================================================================
# Preview

def write_preview(bones, uv, tex, other):
    shown = 3
    run_pose = {"torso": (RUN_LEAN, 0, 0), "neck": (-RUN_LEAN * 0.8, 0, 0), "tails": (-RUN_LEAN + 18, 0, 0), "left_thigh": (-60, 0, 0), "right_thigh": (30, 0, 0),
                "left_shin": (60, 0, 0), "right_shin": (20, 0, 0), "left_arm": (20, 0, 0), "right_arm": (30, 0, 0)}
    # Straight (no stoop) so the views show the texture; the stoop shows in the run pose.
    straight = posed_hunter({name: (0, 0, 0) for name in bones})
    views = (("front", straight, "front", tex), ("side, front to the right", straight, "side", tex),
             ("running, side", posed_hunter(run_pose), "side", tex), ("the other coat", straight, "front", other))
    h = 360
    sheet = Image.new("RGB", (PX * shown + len(views) * 200 + 40, max(h, PX * shown) + 20), (46, 48, 56))
    sheet.paste(tex.resize((PX * shown, PX * shown), Image.NEAREST).convert("RGB"), (10, 10))
    d = ImageDraw.Draw(sheet)
    for n, (label, pose, view, skin) in enumerate(views):
        ox = PX * shown + 30 + n * 200
        d.text((ox, 10), label, fill=(220, 220, 220))
        base = h - 10
        d.line((ox, base, ox + 180, base), fill=(110, 110, 110))
        for blocks in (1, 1.8):                                   # a block, and the player's height
            y = base - blocks * 160
            d.line((ox, y, ox + 180, y), fill=(80, 70, 70))
            d.text((ox + 150, y - 12), f"{blocks}", fill=(120, 110, 110))
        draw_model(sheet, pose, uv, skin, ox + 90, base, view)
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "hunter_preview.png")


def posed_hunter(rotations):
    b = skeleton()
    for name, rot in rotations.items():
        b[name].rotation = list(rot)
    return b


def main():
    bones = skeleton()
    uv = pack(bones)
    tex, glow = texture(bones, uv)
    other, _ = texture(bones, uv, next(c for c in COATS if c != COAT))
    write_json(ASSETS / "geo" / "entity" / "hunter.geo.json", geo(bones, uv))
    write_json(ASSETS / "animations" / "entity" / "hunter.animation.json", animations())
    (ASSETS / "textures" / "entity").mkdir(parents=True, exist_ok=True)
    tex.save(ASSETS / "textures" / "entity" / "hunter.png")
    glow.save(ASSETS / "textures" / "entity" / "hunter_glowmask.png")
    write_preview(bones, uv, tex, other)
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["entity.every_tunnel.hunter"] = "Hunter"
    write_json(lang_path, lang)
    e = extent(bones)
    print(f"Hunter: {len(bones)} bones, {sum(len(b.cubes) for b in bones.values())} cubes, "
          f"{len(animations()['animations'])} animations; {e['y'][1] / 16:.2f} blocks tall")


if __name__ == "__main__":
    sys.exit(main())
