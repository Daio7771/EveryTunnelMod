"""
The director, for GeckoLib: the man behind the desk at the top of Level 5, who talks to you in the
boss's cinematic. Fifty-odd, tired: grey hair gone back at the temples, short at the sides, grey eyes in
dark rings, a few days' grey stubble. A black tuxedo with satin lapels, a white shirt, a black bow tie,
bare hands. Over his face, the mask from the Cinema's film (film_walker.py's, the same drawing) on a thin
plate a little wider than his face, its own bone, so he can take it off.

He sits in his executive chair with his forearms on the desk: that is the model's own pose; standing up
and every gesture are animations (director.animation.json), which the game picks and blends between.

Run after changing anything here (from the repository root):   python3 tools/entity/director.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/director.geo.json                 a player's body (the 64x64 skin's boxes, wide arms), sitting,
                                               with the mask's plate and a bow tie
  animations/entity/director.animation.json    breathing, a raised hand, a shrug, a tilt of the head, the mask
                                               off, standing, pointing
  textures/entity/director.png                 nothing of him glows
and, in tools/entity/output/ (git-ignored): director_preview.png (standing with and without the mask,
sitting from the side, his face close with and without it).

Conventions as in dead_guard.py: the model faces north (-z); rotations are degrees [x, y, z]; box UV on
the standard skin layout, the mask's plate in the hat layer's place (unused: no overlay layers).
"""

import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dead_guard import BOXES  # noqa: E402
from film_walker import HOLLOW, MASK_SHADE, paint_mask  # noqa: E402
from film_walker import faces as skin_faces  # noqa: E402
from observer import draw_model  # noqa: E402
from pale_man import Bone, write_json  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"

SKIN, SKIN_SHADE, SKIN_DARK = (188, 152, 132), (160, 126, 108), (128, 98, 86)
HAIR, HAIR_DARK = (158, 156, 152), (112, 110, 108)
STUBBLE = (128, 122, 116)
EYE_WHITE, IRIS, BROW = (214, 208, 200), (92, 104, 112), (96, 92, 88)
LIPS = (128, 88, 80)
TUX, TUX_DARK, SATIN = (24, 24, 28), (14, 14, 17), (46, 46, 54)
SHIRT, SHIRT_SHADE = (222, 220, 214), (190, 188, 182)
SHOE = (10, 10, 12)

# Boxes beyond the skin's: the mask's plate (8 x 8 x 1, inflated half a pixel all round) and the bow tie.
MASK_UV, BOW_UV = (32, 0), (52, 0)
MASK_INFLATE = 0.5

# The pose, in his chair (degrees, as dead_guard.py's): thighs forward on the seat, the back upright against
# the chair's, the forearms forward onto the desk, the head level, looking at the door.
POSE = {"right_leg": (-88, 4, 0), "left_leg": (-88, -4, 0), "right_arm": (-62, 8, 0), "left_arm": (-62, -8, 0)}
# Sitting, the hips are this far down from a standing player's (on the chair's seat, 9 px up, a leg's half
# above it); and he sits this far back of his spawn point (the middle of the chair's block), his back on the
# chair's.
SIT = 1
BACK = 1.5


def skeleton(posed=True):
    """The bones and cubes, in model units, y up from the floor (sitting: down by SIT). The mask's bone hangs from
    the root (so leaning to lay it down does not carry it) and turns about the head's pivot, so the game can turn
    it with the head while it is on."""
    drop = SIT if posed else 0
    b = {}

    def bone(name, parent, pivot):
        x, y, z = pivot
        b[name] = Bone(name, parent, (x, y - drop, z + BACK), POSE.get(name, (0, 0, 0)) if posed else (0, 0, 0))

    def cube(name, origin, size, uv, inflate=0.0):
        x, y, z = origin
        b[name].cubes.append(([x, y - drop, z + BACK], list(size), uv, inflate))

    def skin(name, origin, kind):
        uv, size = BOXES[kind]
        cube(name, origin, size, uv)

    b["root"] = Bone("root", None, (0, 12 - drop, BACK))
    bone("body", "root", (0, 12, 0))
    skin("body", (-4, 12, -2), "body")
    cube("body", (-2, 22, -3), (4, 2, 1), BOW_UV)                     # the bow tie, at the collar
    bone("head", "body", (0, 24, 0))
    skin("head", (-4, 24, -4), "head")
    bone("mask", "root", (0, 24, 0))
    cube("mask", (-4, 24, -5), (8, 8, 1), MASK_UV, MASK_INFLATE)
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

def fill(img, rng, rect, colour, noise=4):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            d = rng.randint(-noise, noise)
            img.putpixel((x, y), (*[max(0, min(255, c + d)) for c in colour], 255))


def put(img, origin, x, y, colour):
    img.putpixel((origin[0] + x, origin[1] + y), (*colour, 255))


def part(kind):
    """film_walker.faces' (u, v, width, height, depth) of one of the skin's boxes."""
    (u, v), (w, h, d) = BOXES[kind]
    return u, v, w, h, d


def paint_head(img, rng):
    head = part("head")
    for rect in skin_faces(head).values():
        fill(img, rng, rect, HAIR, 5)
    face = skin_faces(head)["front"]
    fill(img, rng, face, SKIN, 3)
    o = face[:2]
    # Hair gone back at the temples: a tuft in the middle of the brow, grey to the sides, the forehead bare.
    for x in (0, 3, 4, 7):
        put(img, o, x, 0, HAIR)
    for x in (0, 7):
        put(img, o, x, 1, HAIR_DARK)
    for x in (1, 2, 5, 6):
        put(img, o, x, 1, SKIN_SHADE)                                 # lines on the forehead
    # Brows low and heavy at their outer ends; grey eyes in dark rings, the lids half down.
    for x, y in ((1, 2), (2, 2), (5, 2), (6, 2)):
        put(img, o, x, y, BROW)
    for x, c in ((1, EYE_WHITE), (2, IRIS), (5, IRIS), (6, EYE_WHITE)):
        put(img, o, x, 3, c)
    for x in (1, 2, 5, 6):
        put(img, o, x, 4, SKIN_DARK)                                  # the rings under them
    for x in (3, 4):
        put(img, o, x, 3, SKIN_SHADE)
        put(img, o, x, 4, SKIN_SHADE)                                 # the nose
    # A few days' grey stubble over the jaw, the lip and the chin; a flat mouth.
    for y in (5, 6, 7):
        for x in range(8):
            if (y == 5 and x in (0, 7)) or y > 5:
                if rng.random() < 0.6:
                    put(img, o, x, y, STUBBLE)
    for x in (2, 3, 4, 5):
        put(img, o, x, 5, STUBBLE if rng.random() < 0.5 else SKIN_SHADE)
        put(img, o, x, 6, LIPS)
    # The sides: an ear's shadow and short grey hair over it, stubble down the jaw.
    for side in ("right", "left"):
        x0, y0, w, _ = skin_faces(head)[side]
        for k in range(w):
            for y in range(3):
                img.putpixel((x0 + k, y0 + y), (*HAIR_DARK, 255))
        put(img, (x0, y0), 3 if side == "right" else 4, 4, SKIN_SHADE)
        put(img, (x0, y0), 3 if side == "right" else 4, 3, SKIN)
        for k in range(w):
            if rng.random() < 0.5:
                put(img, (x0, y0), k, 7, STUBBLE)
    # The top: bare above the forehead, grey behind.
    x0, y0, w, h = skin_faces(head)["top"]
    for x in range(1, 7):
        for y in range(h - 3, h):
            img.putpixel((x0 + x, y0 + y), (*SKIN_SHADE, 255))


def paint_suit(img, rng):
    for kind in ("body", "right_arm", "left_arm", "right_leg", "left_leg"):
        for rect in skin_faces(part(kind)).values():
            fill(img, rng, rect, TUX, 3)
    front = skin_faces(part("body"))["front"][:2]
    # A white shirt in a narrow V, satin lapels either side of it, two buttons down the middle.
    for y in range(7):
        half = max(0, 3 - y // 2)
        for x in range(4 - half, 4 + half):
            put(img, front, x, y, SHIRT if y < 6 else SHIRT_SHADE)
        for x in (4 - half - 1, 4 + half):
            if 0 <= x < 8:
                put(img, front, x, y, SATIN)
    for y in (8, 10):
        put(img, front, 4, y, TUX_DARK)
    for arm in ("right_arm", "left_arm"):                             # white cuffs, bare old hands
        u, v, w, h, d = part(arm)
        fill(img, rng, (u, v + d + h - 3, 2 * (w + d), 1), SHIRT, 2)
        fill(img, rng, (u, v + d + h - 2, 2 * (w + d), 2), SKIN_SHADE, 4)
        fill(img, rng, skin_faces(part(arm))["bottom"], SKIN_SHADE, 4)
    for leg in ("right_leg", "left_leg"):                             # black patent shoes, a satin stripe
        u, v, w, h, d = part(leg)
        fill(img, rng, (u, v + d + h - 2, 2 * (w + d), 2), SHOE, 2)
        fill(img, rng, skin_faces(part(leg))["bottom"], SHOE, 2)
        put(img, (u, v + d), 0, 4, SATIN)


def paint_bow(img, rng):
    u, v = BOW_UV
    box = (u, v, 4, 2, 1)
    for rect in skin_faces(box).values():
        fill(img, rng, rect, TUX_DARK, 2)
    o = skin_faces(box)["front"][:2]
    for x in (0, 3):                                                  # the wings catch the light; the knot does not
        put(img, o, x, 0, SATIN)
        put(img, o, x, 1, SATIN)


def paint_plate(img, rng):
    u, v = MASK_UV
    box = (u, v, 8, 8, 1)
    for rect in skin_faces(box).values():
        fill(img, rng, rect, MASK_SHADE, 3)
    paint_mask(img, rng, skin_faces(box)["front"])
    back = skin_faces(box)["back"]
    fill(img, rng, back, (60, 56, 50), 3)                             # its inside, dark with use
    for x, y in ((1, 2), (2, 2), (5, 2), (6, 2)):
        put(img, back[:2], x, y, HOLLOW)


def texture():
    rng = random.Random("director")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    paint_suit(img, rng)
    paint_head(img, rng)
    paint_bow(img, rng)
    paint_plate(img, rng)
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
        "description": {"identifier": "geometry.director", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": out}]}


# Where the mask comes to rest on his desk: face up, its brow towards him, on the cell in front of him (16 px
# ahead, the desk's top 16 px up), at the near edge of it (the block director_mask, which takes its place,
# lies there too). Turned face up about the head's pivot (geo x -90), the plate's middle comes to MASK_TURNED;
# the animation moves it the rest of the way.
MASK_REST = (0.0, 17.0, -13.0)
MASK_TURNED = (0.0, 27.5, 5.5)
MASK_DOWN = [round(r - t, 2) for r, t in zip(MASK_REST, MASK_TURNED)]

SINE = "easeInOutSine"

# Looking away, he looks a little down (DirectorModel's LOOK_DOWN): the mask's move starts and ends there.
LOOK_DOWN = (12, 0, 0)


def keys(frames, easing=SINE):
    """{time: [x, y, z]} as GeckoLib keyframes, eased into each one."""
    return {f"{t:g}": {"vector": list(v), "easing": easing} for t, v in frames.items()}


def animations():
    """His moves, from sitting (the geo's pose; rotations add to it, degrees as the geo's: a negative x swings
    an arm forward, a positive x bows the head). The game picks one at a time (core.cutscene.DirectorScene, platform.DirectorEntity) and blends
    between them; his head turns to the player in code, on top of these (DirectorModel)."""
    a = {}
    # Sitting, breathing: a slow lean in and out, the shoulders with it.
    a["idle"] = {"loop": True, "animation_length": 4.0, "bones": {
        "body": {"rotation": keys({0: (0, 0, 0), 2: (-1.5, 0, 0), 4: (0, 0, 0)})},
        "right_arm": {"rotation": keys({0: (0, 0, 0), 2: (1.5, 0, 0), 4: (0, 0, 0)})},
        "left_arm": {"rotation": keys({0: (0, 0, 0), 2: (1.5, 0, 0), 4: (0, 0, 0)})}}}
    # A hand lifted off the desk, open, and put back.
    a["raise_hand"] = {"animation_length": 1.6, "bones": {
        "right_arm": {"rotation": keys({0: (0, 0, 0), 0.4: (-28, 12, 0), 1.1: (-26, 10, 0), 1.6: (0, 0, 0)})}}}
    # A tired shrug: both shoulders up a little and down, the body back.
    shoulders = {0: (0, 0, 0), 0.3: (0, 0.8, 0), 0.7: (0, 0.8, 0), 1.2: (0, 0, 0)}
    a["shrug"] = {"animation_length": 1.2, "bones": {
        "body": {"rotation": keys({0: (0, 0, 0), 0.4: (2, 0, 0), 1.2: (0, 0, 0)})},
        "right_arm": {"position": keys(shoulders), "rotation": keys({0: (0, 0, 0), 0.4: (4, 0, 6), 1.2: (0, 0, 0)})},
        "left_arm": {"position": keys(shoulders), "rotation": keys({0: (0, 0, 0), 0.4: (4, 0, -6), 1.2: (0, 0, 0)})}}}
    # His head to one side for a moment (round z only: the game turns it round x and y, DirectorModel).
    a["tilt"] = {"animation_length": 2.0, "bones": {
        "head": {"rotation": keys({0: (0, 0, 0), 0.5: (0, 0, 10), 1.5: (0, 0, 9), 2.0: (0, 0, 0)})},
        "mask": {"rotation": keys({0: (0, 0, 0), 0.5: (0, 0, 10), 1.5: (0, 0, 9), 2.0: (0, 0, 0)})}}}
    # The mask off (3 s): both hands up to it, lifted away, held a moment while he looks at it, then laid face up
    # on the desk in front of him, leaning in to reach; it stays there (the game hides it and puts its block down).
    up, hold = (-88, 20, 0), (-55, 12, 0)
    a["mask_off"] = {"animation_length": 3.0, "bones": {
        "right_arm": {"rotation": keys({0: (0, 0, 0), 0.6: up, 1.0: (-80, 15, 0), 1.4: hold, 2.0: hold,
                                        2.7: (-25, 10, 0), 3.0: (0, 0, 0)})},
        "left_arm": {"rotation": keys({0: (0, 0, 0), 0.6: (up[0], -up[1], 0), 1.0: (-80, -15, 0), 1.4: (hold[0], -hold[1], 0),
                                       2.0: (hold[0], -hold[1], 0), 2.7: (-25, -10, 0), 3.0: (0, 0, 0)})},
        "body": {"rotation": keys({0: (0, 0, 0), 2.0: (0, 0, 0), 2.7: (-14, 0, 0), 3.0: (0, 0, 0)})},
        "head": {"rotation": keys({0: LOOK_DOWN, 0.6: (-4, 0, 0), 1.4: (18, 0, 0), 2.0: (16, 0, 0), 2.7: (20, 0, 0),
                                   3.0: LOOK_DOWN})},
        "mask": {"rotation": keys({0: LOOK_DOWN, 0.6: (-4, 0, 0), 1.0: (-4, 0, 0), 1.4: (-25, 0, 0), 2.0: (-30, 0, 0),
                                   2.7: (-90, 0, 0), 3.0: (-90, 0, 0)}),
                 "position": keys({0: (0, 0, 0), 0.6: (0, 0, 0), 1.0: (0, 0, -3), 1.4: (0, -6, -6), 2.0: (0, -6, -6.5),
                                   2.7: MASK_DOWN, 3.0: MASK_DOWN})}}}
    # On his feet behind the desk, the arms down, a slight sway; and pointing at the door with the right arm.
    standing = {"root": {"position": keys({0: (0, SIT, 0), 2: (0, SIT, 0)})},
                "right_leg": {"rotation": keys({0: (88, -4, 0), 2: (88, -4, 0)})},
                "left_leg": {"rotation": keys({0: (88, 4, 0), 2: (88, 4, 0)})},
                "body": {"rotation": keys({0: (0, 0, 0), 1: (0, 0, 1), 2: (0, 0, 0)})},
                "left_arm": {"rotation": keys({0: (60, 8, 2), 2: (60, 8, 2)})}}
    a["stand"] = {"loop": True, "animation_length": 2.0, "bones": {
        **standing, "right_arm": {"rotation": keys({0: (60, -8, -2), 2: (60, -8, -2)})}}}
    a["point"] = {"loop": True, "animation_length": 2.0, "bones": {
        **standing, "right_arm": {"rotation": keys({0: (-28, -6, 0), 2: (-29, -6, 0)})}}}
    return {"format_version": "1.8.0", "geckolib_format_version": 2,
            "animations": {f"animation.director.{name}": anim for name, anim in a.items()}}


def write_preview(tex):
    """Standing, front, with the mask and without; sitting, from the side; his face close, with and without."""
    h = 380
    sheet = Image.new("RGB", (3 * 200 + 2 * 220 + 40, h + 20), (46, 48, 56))
    d = ImageDraw.Draw(sheet)
    panels = (("standing, mask", False, "front", True), ("standing, no mask", False, "front", False),
              ("sitting, side", True, "side", True))
    for k, (label, posed, view, masked) in enumerate(panels):
        bones = skeleton(posed)
        if not masked:
            bones["mask"].cubes = []
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
    for k, (label, rect) in enumerate((("face, mask", (33, 1, 41, 9)), ("face", (8, 8, 16, 16)))):
        ox = 20 + 3 * 200 + k * 220
        d.text((ox, 10), label, fill=(220, 220, 220))
        sheet.paste(tex.crop(rect).convert("RGB").resize((192, 192), Image.NEAREST), (ox, 40))
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "director_preview.png")


def main():
    bones = skeleton()
    tex = texture()
    write_json(ASSETS / "geo" / "entity" / "director.geo.json", geo(bones))
    tex.save(ASSETS / "textures" / "entity" / "director.png")
    write_json(ASSETS / "animations" / "entity" / "director.animation.json", animations())
    write_preview(tex)
    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["entity.every_tunnel.director"] = "The Director"
    write_json(lang_path, lang)
    print("The director: sitting in his chair, his mask on")


if __name__ == "__main__":
    sys.exit(main())
