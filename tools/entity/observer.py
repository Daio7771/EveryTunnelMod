"""
The Observer's model, animations and texture for GeckoLib (Level 3's observant guard).

Run after changing anything here (from the repository root):   python3 tools/entity/observer.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/observer.geo.json                 bones and cubes (Bedrock geometry, 16 units = 1 block)
  animations/entity/observer.animation.json    idle, walk, run, attack
  textures/entity/observer.png                 the skin, box UV, SCALE pixels per unit
  models/item/observer_spawn_egg.json, and the names in lang/en_us.json
and, in tools/entity/output/ (git-ignored): observer_preview.png (the texture enlarged, front
and side views of the model, and the run pose from the side).

The look draws on tools/entity/reference/observer_concept.png (AI concept art, reference only;
nothing of it is copied): a tall, thin guard in a formal black uniform, about 2.5 blocks, whose
head is a big smooth grey block with no face at all, a little too big for the body. Looking
round is not an animation: the server turns his head (his sight follows it) and the model's
`head` bone turns with it (client/render/ObserverRenderer.kt). A white
shirt shows at the collar with a dark red tie; white gloves; black shoes; the jacket buttoned,
a pale stripe down the trousers. He walks stiffly, arms close to the body, like a mannequin,
and runs leaning far forwards, arms pumping.

He stands 2.5 blocks so he passes the rooms' 3-high doors upright; his hitbox is 1.9 blocks
(ModEntities), so his paths fit wherever the player's do.

Conventions as in pale_man.py (whose geometry helpers this reuses): the model faces north (-z);
rotations are degrees [x, y, z]; a negative x rotation swings a hanging limb forwards, a
positive one leans an upright part forwards; box UV, LAYOUT x LAYOUT units painted at SCALE
pixels per unit.
"""

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from pale_man import Bone, faces, hull, keys, swing, world_corners, write_json  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"
LAYOUT = 64
SCALE = 2
PX = LAYOUT * SCALE

SUIT = (30, 30, 35)
SUIT_LIGHT = (46, 46, 53)
SUIT_DARK = (18, 18, 22)
SHIRT = (230, 230, 224)
SHIRT_SHADE = (198, 198, 192)
TIE = (128, 22, 28)
TIE_DARK = (92, 14, 20)
GLOVE = (226, 226, 220)
GLOVE_SHADE = (190, 190, 184)
SHOE = (12, 12, 14)
SHOE_SHINE = (44, 44, 50)
HEAD = (172, 178, 186)
HEAD_LIGHT = (188, 194, 202)
HEAD_DARK = (146, 152, 160)
BUTTON = (70, 70, 78)
STRIPE = (60, 60, 68)

RUN_LEAN = 28                 # degrees the torso leans when running


# =====================================================================================
# Geometry

def skeleton():
    """Bones and cubes, in model units (y up from the soles, front towards -z). About 40 tall."""
    b = {}

    def bone(name, parent, pivot, rotation=(0, 0, 0)):
        b[name] = Bone(name, parent, pivot, rotation)

    def cube(bone_name, origin, size, kind, inflate=0.0):
        b[bone_name].cubes.append((list(origin), list(size), kind, inflate))

    bone("root", None, (0, 0, 0))
    cube("root", (-3.5, 15, -2), (7, 3, 4), "hips")
    for side, x in (("left", 1.75), ("right", -1.75)):
        bone(f"{side}_thigh", "root", (x, 16, 0))
        cube(f"{side}_thigh", (x - 1.5, 8, -1.5), (3, 8, 3), "trouser")
        bone(f"{side}_shin", f"{side}_thigh", (x, 8, 0))
        cube(f"{side}_shin", (x - 1.5, 2, -1.5), (3, 6, 3), "trouser")
        cube(f"{side}_shin", (x - 1.5, 0, -3), (3, 2, 5), "shoe")
    bone("torso", "root", (0, 18, 0))
    cube("torso", (-4, 18, -2), (8, 12, 4), "jacket")
    bone("neck", "torso", (0, 30, 0))
    cube("neck", (-1, 30, -1), (2, 1, 2), "collar")
    bone("head", "neck", (0, 31, 0))
    cube("head", (-4.5, 31, -4.5), (9, 9, 9), "head")
    for side, x in (("left", 5.5), ("right", -5.5)):
        bone(f"{side}_arm", "torso", (x, 29, 0))
        cube(f"{side}_arm", (x - 1.5, 21, -1.5), (3, 8, 3), "sleeve")
        bone(f"{side}_forearm", f"{side}_arm", (x, 21, 0))
        cube(f"{side}_forearm", (x - 1.5, 14, -1.5), (3, 7, 3), "cuff")
        bone(f"{side}_hand", f"{side}_forearm", (x, 14, 0))
        cube(f"{side}_hand", (x - 1.5, 10, -1.5), (3, 4, 3), "glove")
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
            raise SystemExit(f"the Observer's cubes do not fit in a {LAYOUT}x{LAYOUT} texture")
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

    def put(self, x, y, c, amount=0):
        d = self.rng.uniform(-amount, amount) if amount else 0
        self.img.putpixel((x, y), tuple(max(0, min(255, int(round(ch + d)))) for ch in c[:3]) + (255,))

    def fill(self, rect, c, amount=4):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.put(x, y, c, amount)

    def cloth(self, rect, base=SUIT, light=SUIT_LIGHT, dark=SUIT_DARK):
        """Black wool: even, with a few faint folds."""
        x0, y0, x1, y1 = rect
        for x in range(x0, x1):
            fold = self.rng.random() < 0.15
            for y in range(y0, y1):
                c = dark if fold else (light if self.rng.random() < 0.06 else base)
                self.put(x, y, c, 3)


def paint(p, kind, rects):
    S = SCALE
    rng = p.rng
    sides = ("north", "east", "south", "west")
    if kind == "head":
        # Smooth and blank: lighter on top and towards the front's upper edge, darker below.
        for name, (x0, y0, x1, y1) in rects.items():
            for y in range(y0, y1):
                for x in range(x0, x1):
                    t = (y - y0) / max(1, y1 - y0 - 1)
                    c = HEAD_LIGHT if name == "up" else HEAD_DARK if name == "down" else \
                        tuple(HEAD_LIGHT[i] + (HEAD[i] - HEAD_LIGHT[i]) * min(1.0, t * 1.6) for i in range(3))
                    p.put(x, y, c, 2)
        x0, y0, x1, y1 = rects["north"]
        for x in range(x0, x1):                                  # a soft shadow where it meets the collar
            p.put(x, y1 - 1, HEAD_DARK, 2)
    elif kind == "collar":
        for name, r in rects.items():
            p.fill(r, SHIRT, 3)
    elif kind == "jacket":
        for r in rects.values():
            p.cloth(r)
        x0, y0, x1, y1 = rects["north"]                          # 8 x 12 units = 16 x 24 pixels
        cx = (x0 + x1) // 2
        v_depth = 7 * S                                          # the shirt's V under the collar
        for y in range(y0, y0 + v_depth):
            half = max(1, round(5 * (1 - (y - y0) / v_depth)) + 1)
            for x in range(cx - half, cx + half):
                p.put(x, y, SHIRT if abs(x - cx + 0.5) > 1 else SHIRT_SHADE, 3)
            for x in (cx - half - 1, cx + half):                 # the lapels' edges
                p.put(x, y, SUIT_LIGHT, 3)
        for y in range(y0 + 1, y0 + v_depth + 3):                # the tie, down the middle, its tip below the V
            for x in (cx - 1, cx):
                p.put(x, y, TIE if y < y0 + v_depth + 2 else TIE_DARK, 3)
        p.fill((cx - 2, y0, cx + 2, y0 + 2), TIE_DARK, 2)         # its knot under the collar
        for by in (y0 + v_depth + 2, y0 + v_depth + 6, y0 + v_depth + 10):
            p.put(cx - 1, by, BUTTON, 3)
        for x in range(x0, x1):                                  # the hem
            p.put(x, y1 - 1, SUIT_DARK, 2)
        for name in ("east", "west"):                            # a pocket line on each side
            sx0, sy0, sx1, sy1 = rects[name]
            for x in range(sx0 + 1, sx1 - 1):
                p.put(x, sy0 + 15, SUIT_DARK, 2)
    elif kind == "hips":
        for r in rects.values():
            p.cloth(r)
        for name in sides:
            x0, y0, x1, y1 = rects[name]
            p.fill((x0, y0, x1, y0 + 1), SUIT_DARK, 2)            # the belt line under the jacket
    elif kind == "trouser":
        for r in rects.values():
            p.cloth(r)
        for name in ("east", "west"):                            # a stripe down the outer seams
            x0, y0, x1, y1 = rects[name]
            cx = (x0 + x1) // 2
            for y in range(y0, y1):
                p.put(cx, y, STRIPE, 2)
        x0, y0, x1, y1 = rects["north"]                          # the crease
        cx = (x0 + x1) // 2
        for y in range(y0, y1):
            p.put(cx, y, SUIT_LIGHT, 2)
    elif kind == "shoe":
        for name, r in rects.items():
            p.fill(r, SHOE, 2)
        x0, y0, x1, y1 = rects["up"]
        p.fill((x0 + 1, y0 + 1, x1 - 1, y0 + 3), SHOE_SHINE, 3)  # a dull shine on the toes
    elif kind == "sleeve":
        for r in rects.values():
            p.cloth(r)
    elif kind == "cuff":
        for r in rects.values():
            p.cloth(r)
        for name in sides:                                       # the shirt cuff showing at the wrist
            x0, y0, x1, y1 = rects[name]
            p.fill((x0, y1 - S, x1, y1), SHIRT, 3)
    elif kind == "glove":
        for name, (x0, y0, x1, y1) in rects.items():
            for y in range(y0, y1):
                for x in range(x0, x1):
                    p.put(x, y, GLOVE_SHADE if name == "down" or (y - y0) > (y1 - y0) * 0.7 else GLOVE, 3)
        for name in ("north", "south"):                          # the fingers' seams
            x0, y0, x1, y1 = rects[name]
            for x in range(x0 + 1, x1, 2):
                for y in range(y0 + (y1 - y0) // 2, y1):
                    p.put(x, y, GLOVE_SHADE, 2)


def texture(bones, uv):
    p = Painter(random.Random("observer-skin-1"))
    for bn, bone in bones.items():
        for i, (origin, size, kind, inflate) in enumerate(bone.cubes):
            paint(p, kind, faces(*uv[(bn, i)], size))
    return p.img


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
        "description": {"identifier": "geometry.observer", "texture_width": LAYOUT, "texture_height": LAYOUT,
                        "visible_bounds_width": 3, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": out}]}


def animations():
    walk, run = 1.4, 0.45
    A = {}
    A["animation.observer.idle"] = {"loop": True, "animation_length": 6.0, "bones": {
        # Almost perfectly still: the faintest sway, nothing breathing.
        "torso": {"rotation": swing(6.0, 0.6)},
        "head": {"rotation": swing(6.0, 1.5, axis=1)},
    }}
    A["animation.observer.walk"] = {"loop": True, "animation_length": walk, "bones": {
        # Stiff: short leg swings, the knees hardly bend, the arms stay at his sides.
        "left_thigh": {"rotation": swing(walk, 14)},
        "right_thigh": {"rotation": swing(walk, 14, math.pi)},
        "left_shin": {"rotation": swing(walk, 4, -math.pi / 2, offset=(4, 0, 0))},
        "right_shin": {"rotation": swing(walk, 4, math.pi / 2, offset=(4, 0, 0))},
        "left_arm": {"rotation": swing(walk, 2.5, math.pi)},
        "right_arm": {"rotation": swing(walk, 2.5)},
        "root": {"position": swing(walk / 2, 0.25, axis=1)},
    }}
    A["animation.observer.run"] = {"loop": True, "animation_length": run, "bones": {
        # Leaning far in, arms pumping, long strides.
        "torso": {"rotation": keys((0, [RUN_LEAN, 0, 0]))},
        "neck": {"rotation": keys((0, [-RUN_LEAN * 0.6, 0, 0]))},
        "left_thigh": {"rotation": swing(run, 48, offset=(-14, 0, 0))},
        "right_thigh": {"rotation": swing(run, 48, math.pi, offset=(-14, 0, 0))},
        "left_shin": {"rotation": swing(run, 30, -math.pi / 2, offset=(38, 0, 0))},
        "right_shin": {"rotation": swing(run, 30, math.pi / 2, offset=(38, 0, 0))},
        "left_arm": {"rotation": swing(run, 45, math.pi, offset=(-RUN_LEAN, 0, -4))},
        "right_arm": {"rotation": swing(run, 45, offset=(-RUN_LEAN, 0, 4))},
        "left_forearm": {"rotation": keys((0, [-45, 0, 0]))},
        "right_forearm": {"rotation": keys((0, [-45, 0, 0]))},
        "root": {"position": swing(run / 2, 0.6, offset=(0, -1.0, 0), axis=1)},
    }}
    A["animation.observer.attack"] = {"loop": False, "animation_length": 0.6, "bones": {
        # Both gloved hands reach out and grab.
        "torso": {"rotation": keys((0, [0, 0, 0]), (0.2, [-6, 0, 0]), (0.35, [18, 0, 0]), (0.6, [12, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.2, [-110, 0, -12]), (0.35, [-85, 0, 8]), (0.6, [-80, 0, 0]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.2, [-110, 0, 12]), (0.35, [-85, 0, -8]), (0.6, [-80, 0, 0]))},
    }}
    return {"format_version": "1.8.0", "animations": A, "geckolib_format_version": 2}


# =====================================================================================
# Preview

def posed(rotations):
    b = skeleton()
    for name, rot in rotations.items():
        b[name].rotation = list(rot)
    return b


def draw_model(img, bones, uv, tex, ox, base, view, scale=10):
    """Flat, texture-coloured drawing of every cube's faces seen from `view` (front or side)."""
    d = ImageDraw.Draw(img)
    order = []
    for bn, bone in bones.items():
        for i, (origin, size, kind, inflate) in enumerate(bone.cubes):
            pts = world_corners(bones, bn, origin, size)
            depth = sum(q[2] if view == "front" else q[0] for q in pts) / 8
            order.append((depth, bn, i, origin, size, pts))
    order.sort(key=lambda o: -o[0])                               # the farthest first
    straight = all(not any(b.rotation) for b in bones.values())
    for _, bn, i, origin, size, pts in order:
        face = "north" if view == "front" else "west"
        x0, y0, x1, y1 = faces(*uv[(bn, i)], size)[face]
        patch = tex.crop((x0, y0, max(x0 + 1, x1), max(y0 + 1, y1))).convert("RGB")
        flat = [(ox + (q[0] if view == "front" else -q[2]) * scale, base - q[1] * scale) for q in pts]
        if straight:
            # Unposed, every face is square to the view: draw its texture.
            left, top = min(p[0] for p in flat), min(p[1] for p in flat)
            right, bottom = max(p[0] for p in flat), max(p[1] for p in flat)
            img.paste(patch.resize((max(1, int(right - left)), max(1, int(bottom - top))), Image.NEAREST), (int(left), int(top)))
            d.rectangle((left, top, right, bottom), outline=(10, 10, 12))
        else:
            colour = patch.resize((1, 1), Image.BOX).getpixel((0, 0))
            d.polygon(hull(flat), fill=colour, outline=(10, 10, 12))


def write_preview(bones, uv, tex):
    shown = 3
    run_pose = {"torso": (RUN_LEAN, 0, 0), "neck": (-RUN_LEAN * 0.6, 0, 0), "left_thigh": (-50, 0, 0), "right_thigh": (30, 0, 0),
                "left_shin": (50, 0, 0), "right_shin": (20, 0, 0), "left_arm": (10, 0, 0), "right_arm": (-60, 0, 0),
                "left_forearm": (-45, 0, 0), "right_forearm": (-45, 0, 0)}
    views = (("front", bones, "front"), ("side, front to the right", bones, "side"), ("running, side", posed(run_pose), "side"))
    h = 480
    sheet = Image.new("RGB", (PX * shown + len(views) * 240 + 40, h + 20), (46, 48, 56))
    sheet.paste(tex.resize((PX * shown, PX * shown), Image.NEAREST).convert("RGB"), (10, 10))
    d = ImageDraw.Draw(sheet)
    for n, (label, pose, view) in enumerate(views):
        ox = PX * shown + 30 + n * 240
        d.text((ox, 10), label, fill=(220, 220, 220))
        base = h - 10
        d.line((ox, base, ox + 220, base), fill=(110, 110, 110))
        for blocks in (1, 2, 3):                                  # block heights; the rooms' doors are 3 high
            y = base - blocks * 160
            d.line((ox, y, ox + 220, y), fill=(80, 70, 70))
            d.text((ox + 200, y - 12), f"{blocks}", fill=(120, 110, 110))
        draw_model(sheet, pose, uv, tex, ox + 110, base, view)
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "observer_preview.png")


def extent(bones):
    pts = [q for bn, bone in bones.items() for o, s, k, inf in bone.cubes for q in world_corners(bones, bn, o, s)]
    return {a: (min(q[i] for q in pts), max(q[i] for q in pts)) for i, a in enumerate("xyz")}


def main():
    bones = skeleton()
    uv = pack(bones)
    tex = texture(bones, uv)
    write_json(ASSETS / "geo" / "entity" / "observer.geo.json", geo(bones, uv))
    write_json(ASSETS / "animations" / "entity" / "observer.animation.json", animations())
    (ASSETS / "textures" / "entity").mkdir(parents=True, exist_ok=True)
    tex.save(ASSETS / "textures" / "entity" / "observer.png")
    write_preview(bones, uv, tex)
    write_json(ASSETS / "models" / "item" / "observer_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["entity.every_tunnel.observer"] = "Observer"
    lang["item.every_tunnel.observer_spawn_egg"] = "Observer Spawn Egg"
    write_json(lang_path, lang)
    e = extent(bones)
    print(f"Observer: {len(bones)} bones, {sum(len(b.cubes) for b in bones.values())} cubes, "
          f"{len(animations()['animations'])} animations; {e['y'][1] / 16:.2f} blocks tall")


if __name__ == "__main__":
    sys.exit(main())
