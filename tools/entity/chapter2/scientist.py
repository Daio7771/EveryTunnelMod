"""
The scientist, for GeckoLib: the anonymous man at the bench in Chapter 2's opening cinematic (the prologue),
whose records say he is on his sixth day of work in front of a counter that keeps climbing. Mid-thirties, tired:
short dark hair, a pale face with rings under the eyes and a day's stubble, a white lab coat to the knees over a
blue-grey shirt, dark trousers and shoes, a blank badge clipped to his chest (no name: he is nobody the game
names). The camera sees him from behind; the face is for the one turn he makes at the end.

He stands upright: that is the model's own pose; working at the bench (bent a little forward, head down, the
forearms out over its edge, one hand swirling a tube, the other holding it) and every move after it are
animations (scientist.animation.json), which the game picks and blends between.

Run after changing anything here (from the repository root):   python3 tools/entity/chapter2/scientist.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/scientist.geo.json                a player's body (the 64x64 skin's boxes, wide arms) with the coat's
                                               skirt as one more box over the thighs
  animations/entity/scientist.animation.json   idle, work, tremble (the first signs: "my hands"), sick (hunched,
                                               the hands curling), stretch (arms and legs lengthening, the head
                                               thrown back): the stages of his change, before he is a Pale man
  textures/entity/scientist.png                nothing of him glows
  textures/entity/scientist_pale.png           the same, sick: pale blue-white skin, milky eyes, grey lips, dark veins
and, in tools/entity/common/output/ (git-ignored): scientist_preview.png (front, back, side standing; side working; the
face close).

Conventions as in director.py: the model faces north (-z); rotations are degrees [x, y, z]; box UV on the
standard skin layout, the skirt's box in the body's overlay layer's place (unused: no overlay layers).
"""

import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

import sys as _sys
from pathlib import Path as _Path
_sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))
import toolpaths  # noqa: E402  (where each area keeps its common/chapter folders)
toolpaths.use('entity', 'lang')
sys.path.insert(0, str(Path(__file__).resolve().parent))
from dead_guard import BOXES  # noqa: E402
from director import fill, keys, part, put  # noqa: E402
from film_walker import faces as skin_faces  # noqa: E402
from observer import draw_model  # noqa: E402
from pale_man import Bone, write_json  # noqa: E402

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "lang" / "common"))
import langfiles  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"

SKIN, SKIN_SHADE, SKIN_DARK = (214, 176, 150), (190, 150, 128), (150, 112, 98)
HAIR, HAIR_DARK = (64, 46, 36), (42, 30, 24)
STUBBLE = (152, 124, 108)
EYE_WHITE, IRIS, BROW = (226, 220, 214), (74, 62, 52), (58, 42, 34)
LIPS = (166, 118, 108)
COAT, COAT_SHADE, COAT_DARK = (238, 238, 236), (212, 214, 216), (180, 184, 190)
SHIRT, SHIRT_SHADE = (112, 134, 156), (88, 108, 130)
TROUSERS, TROUSERS_SHADE = (58, 62, 72), (44, 48, 58)
SHOE = (26, 26, 30)
BADGE, BADGE_BAND = (246, 246, 244), (70, 110, 170)
PEN = (40, 70, 160)

# The sick man's skin (scientist_pale.png): the same drawing in the colours of a body that is giving up: a blue-white
# skin with dark rings, eyes gone milky (the iris barely darker than the white), grey lips, no stubble showing.
PALE_COLOURS = {
    "SKIN": (222, 226, 234), "SKIN_SHADE": (192, 198, 212), "SKIN_DARK": (110, 116, 140), "STUBBLE": (176, 180, 194),
    "EYE_WHITE": (244, 244, 248), "IRIS": (222, 224, 234), "LIPS": (146, 148, 170),
}
MAN_COLOURS = {name: globals()[name] for name in PALE_COLOURS}
VEIN = (92, 102, 146)

# The coat's skirt: 8 wide, 6 high, 4 deep, from the waist over the thighs, a little wider than the body; its UV
# in the body's overlay layer's place.
SKIRT_UV = (16, 32)
SKIRT_SIZE = (8, 6, 4)
SKIRT_INFLATE = 0.45

# The model's standing pose, degrees: nothing (the animations move him).
WORK_POSE = {"body": (-6, 0, 0), "head": (22, 0, 0), "right_arm": (-62, 8, 0), "left_arm": (-62, -8, 0)}


def skeleton(pose=None):
    """The bones and cubes, in model units, y up from the soles; [pose] is a {bone: rotation} for the preview."""
    pose = pose or {}
    b = {}

    def bone(name, parent, pivot):
        b[name] = Bone(name, parent, pivot, pose.get(name, (0, 0, 0)))

    def skin(name, origin, kind):
        uv, size = BOXES[kind]
        b[name].cubes.append((list(origin), list(size), uv, 0.0))

    b["root"] = Bone("root", None, (0, 12, 0))
    bone("body", "root", (0, 12, 0))
    skin("body", (-4, 12, -2), "body")
    b["body"].cubes.append(([-4, 6, -2], list(SKIRT_SIZE), SKIRT_UV, SKIRT_INFLATE))
    bone("head", "body", (0, 24, 0))
    skin("head", (-4, 24, -4), "head")
    bone("right_arm", "body", (-5, 22, 0))
    skin("right_arm", (-8, 12, -2), "right_arm")
    bone("left_arm", "body", (5, 22, 0))
    skin("left_arm", (4, 12, -2), "left_arm")
    bone("right_leg", "root", (-1.9, 12, 0))
    skin("right_leg", (-3.9, 0, -2), "right_leg")
    bone("left_leg", "root", (1.9, 12, 0))
    skin("left_leg", (-0.1, 0, -2), "left_leg")
    return b


# =====================================================================================
# Texture

def paint_head(img, rng):
    head = part("head")
    for rect in skin_faces(head).values():
        fill(img, rng, rect, HAIR, 4)
    face = skin_faces(head)["front"]
    fill(img, rng, face, SKIN, 3)
    o = face[:2]
    # Short hair down to a straight fringe on the forehead.
    for x in range(8):
        put(img, o, x, 0, HAIR)
    for x in (0, 7):
        put(img, o, x, 1, HAIR_DARK)
    # Brows level; brown eyes, the lids heavy; dark rings under them.
    for x in (1, 2, 5, 6):
        put(img, o, x, 2, BROW)
    for x, c in ((1, EYE_WHITE), (2, IRIS), (5, IRIS), (6, EYE_WHITE)):
        put(img, o, x, 3, c)
    for x in (1, 2, 5, 6):
        put(img, o, x, 4, SKIN_DARK)
    for x in (3, 4):
        put(img, o, x, 3, SKIN_SHADE)
        put(img, o, x, 4, SKIN_SHADE)                                 # the nose
    # A day's stubble on the jaw, lip and chin; a flat mouth.
    for y in (5, 6, 7):
        for x in range(8):
            if (y == 5 and x in (0, 1, 6, 7)) or y > 5:
                if rng.random() < 0.55:
                    put(img, o, x, y, STUBBLE)
    for x in (2, 3, 4, 5):
        put(img, o, x, 6, LIPS)
    # The sides: the hair over the temple, an ear, stubble down the jaw; the back is all hair.
    for side in ("right", "left"):
        x0, y0, w, _ = skin_faces(head)[side]
        for k in range(w):
            for y in range(3):
                img.putpixel((x0 + k, y0 + y), (*HAIR_DARK, 255))
        for k in (3, 4):
            put(img, (x0, y0), k, 4, SKIN_SHADE)
            put(img, (x0, y0), k, 3, SKIN)
        for k in range(w):
            if rng.random() < 0.4:
                put(img, (x0, y0), k, 7, STUBBLE)
    # The nape: skin below the hairline at the back.
    x0, y0, w, h = skin_faces(head)["back"]
    for x in range(1, 7):
        put(img, (x0, y0), x, 7, SKIN_SHADE)


def paint_coat(img, rng):
    for kind in ("body", "right_arm", "left_arm"):
        for rect in skin_faces(part(kind)).values():
            fill(img, rng, rect, COAT, 2)
    for kind in ("right_leg", "left_leg"):
        for rect in skin_faces(part(kind)).values():
            fill(img, rng, rect, TROUSERS, 2)
    body = part("body")
    front = skin_faces(body)["front"][:2]
    back = skin_faces(body)["back"][:2]
    # The front: a V of the blue-grey shirt at the neck, a lapel's shade down each side of it, three buttons,
    # a breast pocket on the left with a pen in it, the blank badge clipped above it.
    for y in range(4):
        half = max(0, 2 - y // 2)
        for x in range(4 - half, 4 + half):
            put(img, front, x, y, SHIRT if y < 3 else SHIRT_SHADE)
        for x in (4 - half - 1, 4 + half):
            if 0 <= x < 8:
                put(img, front, x, y, COAT_SHADE)
    for y in range(4, 12):
        put(img, front, 4, y, COAT_SHADE)
    for y in (5, 8, 11):
        put(img, front, 3, y, COAT_DARK)
    for x in range(5, 7):                                             # the pocket
        put(img, front, x, 7, COAT_DARK)
    put(img, front, 5, 6, PEN)
    put(img, front, 6, 6, COAT_DARK)
    put(img, front, 6, 4, BADGE)
    put(img, front, 7, 4, BADGE)
    put(img, front, 6, 5, BADGE_BAND)
    put(img, front, 7, 5, BADGE_BAND)
    # The back: a seam down the middle, a shade where the shoulder blades are.
    for y in range(12):
        put(img, back, 3, y, COAT_SHADE)
        put(img, back, 4, y, COAT_SHADE)
    for x in (1, 2, 5, 6):
        put(img, back, x, 2, COAT_SHADE)
    for arm in ("right_arm", "left_arm"):                             # a cuff's shade, then bare hands
        u, v, w, h, d = part(arm)
        fill(img, rng, (u, v + d + h - 3, 2 * (w + d), 1), COAT_DARK, 2)
        fill(img, rng, (u, v + d + h - 2, 2 * (w + d), 2), SKIN, 3)
        fill(img, rng, skin_faces(part(arm))["bottom"], SKIN_SHADE, 3)
    for leg in ("right_leg", "left_leg"):                             # dark shoes
        u, v, w, h, d = part(leg)
        fill(img, rng, (u, v + d + h - 2, 2 * (w + d), 2), SHOE, 2)
        fill(img, rng, skin_faces(part(leg))["bottom"], SHOE, 2)
        fill(img, rng, (u, v + d, 2 * (w + d), 3), TROUSERS_SHADE, 2)  # the coat's shadow on the thigh


def paint_skirt(img, rng):
    u, v = SKIRT_UV
    w, h, d = SKIRT_SIZE
    box = (u, v, w, h, d)
    faces = skin_faces(box)
    for rect in faces.values():
        fill(img, rng, rect, COAT, 2)
    front = faces["front"][:2]
    back = faces["back"][:2]
    for y in range(h):
        put(img, front, 4, y, COAT_SHADE)                             # the coat's front edge
    for x in (1, 2, 5, 6):
        put(img, front, x, 1, COAT_DARK)                              # the pockets' mouths
    for x in range(w):
        put(img, front, x, h - 1, COAT_SHADE)
        put(img, back, x, h - 1, COAT_SHADE)                          # the hem
    for y in range(3, h):
        put(img, back, 3, y, COAT_DARK)                               # the vent at the back


def paint_veins(img):
    """Dark blue veins under the sick man's skin: on the face and down the sides of the head, on his hands."""
    face = skin_faces(part("head"))["front"][:2]
    for x, y in ((0, 2), (1, 1), (6, 1), (7, 2), (0, 5), (7, 5), (2, 5), (5, 5), (1, 6), (6, 6)):
        put(img, face, x, y, VEIN)
    for side in ("right", "left"):
        x0, y0, w, _ = skin_faces(part("head"))[side]
        for k, y in ((1, 4), (2, 5), (4, 5), (5, 6)):
            put(img, (x0, y0), k, y, VEIN)
    for arm in ("right_arm", "left_arm"):
        u, v, w, h, d = part(arm)
        for x in range(0, 2 * (w + d), 2):
            img.putpixel((u + x, v + d + h - 2), (*VEIN, 255))


def texture(pale=False):
    """The scientist's skin; [pale] is the sick one (the module's colours swapped for [PALE_COLOURS] while it is drawn)."""
    rng = random.Random("scientist")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    globals().update(PALE_COLOURS if pale else MAN_COLOURS)
    try:
        paint_coat(img, rng)
        paint_head(img, rng)
        paint_skirt(img, rng)
        if pale:
            paint_veins(img)
    finally:
        globals().update(MAN_COLOURS)
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
        cubes = []
        for o, s, uv, inflate in bone.cubes:
            c = {"origin": [round(v, 2) for v in o], "size": s, "uv": list(uv)}
            if inflate:
                c["inflate"] = inflate
            cubes.append(c)
        if cubes:
            entry["cubes"] = cubes
        out.append(entry)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.scientist", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": out}]}


def animations():
    """His moves, from standing (the geo's pose; rotations add to it, degrees as the geo's: a negative x swings an
    arm forward, a positive x bows the head, a negative x leans the body forward). The game picks one at a time
    (platform.ScientistEntity) and blends between them."""
    a = {}
    # Standing, breathing.
    a["idle"] = {"loop": True, "animation_length": 4.0, "bones": {
        "body": {"rotation": keys({0: (0, 0, 0), 2: (-1, 0, 0), 4: (0, 0, 0)})},
        "right_arm": {"rotation": keys({0: (0, 0, 0), 2: (1, 0, 0), 4: (0, 0, 0)})},
        "left_arm": {"rotation": keys({0: (0, 0, 0), 2: (1, 0, 0), 4: (0, 0, 0)})}}}
    # Working at the bench (8 s, looping): bent a little forward, the head down on his hands. The right hand
    # swirls a tube, a little more each time round; around 4 s he lifts it to the light, and his head follows,
    # and puts it back; the left hand holds the rack steady and lets go to steady the tube once.
    a["work"] = {"loop": True, "animation_length": 8.0, "bones": {
        "body": {"rotation": keys({0: (-6, 0, 0), 4: (-7.5, 0, 0), 8: (-6, 0, 0)})},
        "head": {"rotation": keys({0: (22, 0, 0), 2: (26, 0, -2), 3.4: (24, 2, 0), 4.4: (8, 4, 0), 5.4: (10, 4, 0),
                                   6.4: (22, 0, 0), 8: (22, 0, 0)})},
        "right_arm": {"rotation": keys({0: (-60, 8, 0), 0.8: (-66, 12, 0), 1.6: (-58, 7, 0), 2.4: (-66, 12, 0),
                                        3.2: (-60, 8, 0), 4.0: (-96, 6, 0), 5.4: (-98, 6, 0), 6.4: (-60, 8, 0),
                                        7.2: (-66, 11, 0), 8: (-60, 8, 0)})},
        "left_arm": {"rotation": keys({0: (-62, -8, 0), 3.0: (-62, -8, 0), 3.6: (-70, -14, 0), 4.2: (-62, -8, 0),
                                       6.0: (-62, -8, 0), 6.8: (-72, -16, 0), 7.4: (-62, -8, 0), 8: (-62, -8, 0)})}}}
    # "My hands...": both arms come up in front of his chest, his head bowed to look at them, and the whole of him
    # shakes (1.2 s, looping): each part's turn drawn at random, once, from a fixed seed, so the file is the same
    # every time. The game blends into it from working, so the arms rise by themselves.
    shake = random.Random("tremble")

    def jitter(base, spread, steps=12, length=1.2):
        frames = {}
        for i in range(steps):
            frames[length * i / steps] = tuple(round(b + shake.uniform(-sp, sp), 2) for b, sp in zip(base, spread))
        frames[length] = frames[0.0]
        return frames
    a["tremble"] = {"loop": True, "animation_length": 1.2, "bones": {
        "body": {"rotation": keys(jitter((-11, 0, 0), (1.5, 1.2, 1.5)), easing="linear")},
        "head": {"rotation": keys(jitter((26, 0, 0), (4, 5, 4)), easing="linear")},
        "right_arm": {"rotation": keys(jitter((-88, 14, 0), (6, 6, 5)), easing="linear")},
        "left_arm": {"rotation": keys(jitter((-86, -14, 0), (6, 6, 5)), easing="linear")}}}
    # The first stage of his change, "sick" (1.4 s, looping): bent double over his own hands, which he holds up and
    # curls and shakes harder, the head hung low, the knees giving (the legs turn the other way to each other).
    a["sick"] = {"loop": True, "animation_length": 1.4, "bones": {
        "body": {"rotation": keys(jitter((-24, 0, 0), (2.5, 3, 2.5), steps=14, length=1.4), easing="linear")},
        "head": {"rotation": keys(jitter((34, 0, 0), (5, 7, 6), steps=14, length=1.4), easing="linear")},
        "right_arm": {"rotation": keys(jitter((-96, 22, 0), (9, 8, 7), steps=14, length=1.4), easing="linear")},
        "left_arm": {"rotation": keys(jitter((-92, -22, 0), (9, 8, 7), steps=14, length=1.4), easing="linear")},
        "right_leg": {"rotation": keys(jitter((-12, 0, 0), (2, 1, 1), steps=14, length=1.4), easing="linear")},
        "left_leg": {"rotation": keys(jitter((9, 0, 0), (2, 1, 1), steps=14, length=1.4), easing="linear")}}}
    # The second stage, "stretch" (1.6 s, looping): his arms grow half as long again and hang forward, his legs a
    # little longer (the root rises by as much, so his feet stay on the floor), the body drawn straight and thin, the head
    # thrown back and twitching. Scales are about each limb's own pivot (the shoulder, the hip).
    long_arm, long_leg = 1.45, 1.12

    def stretch(base, spread):
        return keys(jitter(base, spread, steps=16, length=1.6), easing="linear")

    a["stretch"] = {"loop": True, "animation_length": 1.6, "bones": {
        "root": {"position": keys({0: (0, 1.44, 0), 1.6: (0, 1.44, 0)}, easing="linear")},
        "body": {"rotation": stretch((-6, 0, 0), (1.5, 2, 2))},
        "head": {"rotation": stretch((-24, 0, 0), (5, 9, 8))},
        "right_arm": {"rotation": stretch((-38, 10, 0), (7, 6, 6)), "scale": keys({0: (1, long_arm, 1), 1.6: (1, long_arm, 1)}, easing="linear")},
        "left_arm": {"rotation": stretch((-34, -10, 0), (7, 6, 6)), "scale": keys({0: (1, long_arm, 1), 1.6: (1, long_arm, 1)}, easing="linear")},
        "right_leg": {"scale": keys({0: (1, long_leg, 1), 1.6: (1, long_leg, 1)}, easing="linear")},
        "left_leg": {"scale": keys({0: (1, long_leg, 1), 1.6: (1, long_leg, 1)}, easing="linear")}}}
    return {"format_version": "1.8.0", "geckolib_format_version": 2,
            "animations": {f"animation.scientist.{name}": anim for name, anim in a.items()}}


def write_preview(tex):
    """Standing from the front, the back and the side; working, from the side and the back; his face close."""
    h = 380
    sheet = Image.new("RGB", (5 * 200 + 220 + 40, h + 20), (46, 48, 56))
    d = ImageDraw.Draw(sheet)
    panels = (("standing, front", None, "front"), ("standing, back", None, "back"), ("standing, side", None, "side"),
              ("working, side", WORK_POSE, "side"), ("working, back", WORK_POSE, "back"))
    for k, (label, pose, view) in enumerate(panels):
        bones = skeleton(pose)
        uv = {}
        for bn, bone in bones.items():
            for i, (o, s, cube_uv, _) in enumerate(bone.cubes):
                uv[(bn, i)] = cube_uv
            bone.cubes = [(o, s, "skin", inflate) for o, s, _, inflate in bone.cubes]
        ox = 20 + k * 200
        d.text((ox, 10), label, fill=(220, 220, 220))
        base = h - 10
        d.line((ox, base, ox + 190, base), fill=(110, 110, 110))
        draw_model(sheet, bones, uv, tex.resize((128, 128), Image.NEAREST), ox + 95, base, view)
    ox = 20 + 5 * 200
    d.text((ox, 10), "face", fill=(220, 220, 220))
    sheet.paste(tex.crop((8, 8, 16, 16)).convert("RGB").resize((192, 192), Image.NEAREST), (ox, 40))
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "scientist_preview.png")


def main():
    bones = skeleton()
    tex = texture()
    write_json(ASSETS / "geo" / "entity" / "scientist.geo.json", geo(bones))
    tex.save(ASSETS / "textures" / "entity" / "scientist.png")
    texture(pale=True).save(ASSETS / "textures" / "entity" / "scientist_pale.png")
    write_json(ASSETS / "animations" / "entity" / "scientist.animation.json", animations())
    write_preview(tex)
    langfiles.update("chapter2", "en_us", {"entity.every_tunnel.scientist": "The Scientist"})
    print("The scientist: standing, in his white coat")


if __name__ == "__main__":
    sys.exit(main())
