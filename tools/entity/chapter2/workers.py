"""
The factory's people (Chapter 2, Level 1): the skins of the six workers of the union, all in a white lab coat, and a
sheet that shows them next to Rowan. Three of them speak in the level (the coworker, a nervous young man, an older
one); the other three are the faces the factory is full of. 64x64 player skins with classic arms (4 px) and only the
base layer used, as Rowan's, which the renderer draws on the player's model (`client/render/FactoryNpcRenderer.kt`).

    coworker    an adult, short dark hair side-swept, a light stubble, a blue badge
    worker_a    young and nervous: untidy light hair, wide eyes, dark rings under them, a bead of sweat, a green badge
    worker_b    older: receding grey hair, a grey moustache, wrinkles, an orange badge
    generic_1   a woman with long auburn hair, a red badge
    generic_2   a bald man with glasses, a teal badge
    generic_3   a man with black curly hair and a beard, a purple badge

and the two guards who patrol the factory (Act 3's patrols), in Chapter 1's night guard's uniform (guard.png, by guard.py: the
player was not the first to wear it), each with a face of their own painted over it:

    guard_1     short black hair, a shadow of beard
    guard_2     a darker man, bald, with a beard

and the player of Chapter 2 (the new hire, the scientist hired in the place of the one who "fell ill"), whose skin the
chapter puts on the player in Level 1 in place of the night guard's uniform (`PlayerRules.Rules.skin`):

    new_hire    an ordinary young adult: short brown hair, clean-shaven, a dark shirt and tie under the same coat, grey
                trousers, black shoes, a plain grey badge (new: no team's colour yet). Written to textures/entity/new_hire.png

`rowan.png` is NOT made here: it is the author's own skin (a beige suit and a green tie), copied as it came.

Run after changing anything here (from the repository root):   python3 tools/entity/chapter2/workers.py

Writes src/main/resources/assets/every_tunnel/textures/entity/npc/<name>.png (committed) and
tools/entity/common/output/workers_preview.png (git-ignored): every skin, front and back, enlarged, with Rowan's.

Layout: the standard 64x64 skin (see guard.py). The second layer (hat, jacket, sleeves, trouser legs) stays empty.
In a side face of the head, the column next to the front face is the face's side (x = 7 in the right face, x = 0 in the
left one); the other end is the back of the head.
"""

import random
from pathlib import Path

from PIL import Image, ImageDraw

import sys as _sys
from pathlib import Path as _Path
_sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))
import toolpaths  # noqa: E402  (where each area keeps its common/chapter folders)
toolpaths.use('entity')
from film_walker import BODY, HEAD, LEFT_ARM, LEFT_LEG, RIGHT_ARM, RIGHT_LEG, faces

ROOT = Path(__file__).resolve().parents[3]
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "entity" / "npc"
GUARD_UNIFORM = TEXTURES.parent / "guard.png"
OUT = Path(__file__).resolve().parent / "output"

COAT, COAT_SHADE, COAT_FOLD = (236, 238, 240), (205, 209, 216), (220, 223, 228)
BUTTON = (168, 172, 180)
BADGE_PAPER = (244, 244, 240)
EYE_WHITE, EYE = (232, 234, 236), (46, 52, 62)
SWEAT = (196, 224, 238)

# name -> everything that makes one worker. Colours are (r, g, b).
PEOPLE = {
    "coworker": dict(skin=(226, 176, 146), hair=(58, 44, 36), style="parted", brows=(46, 36, 30), shirt=(86, 128, 170),
                     trousers=(40, 46, 64), shoes=(26, 26, 30), badge=(60, 110, 200), stubble=True),
    "worker_a": dict(skin=(238, 198, 170), hair=(150, 118, 78), style="messy", brows=(120, 94, 62), shirt=(196, 196, 188),
                     trousers=(58, 60, 66), shoes=(30, 30, 34), badge=(70, 160, 100), nervous=True),
    "worker_b": dict(skin=(214, 168, 140), hair=(168, 168, 172), style="receding", brows=(140, 140, 146), shirt=(150, 130, 106),
                     trousers=(70, 66, 60), shoes=(52, 38, 30), badge=(220, 140, 50), older=True),
    "generic_1": dict(skin=(232, 184, 154), hair=(138, 66, 40), style="long", brows=(110, 54, 34), shirt=(120, 84, 100),
                      trousers=(44, 48, 58), shoes=(26, 26, 30), badge=(200, 70, 70)),
    "generic_2": dict(skin=(200, 150, 116), hair=(60, 52, 48), style="bald", brows=(54, 46, 42), shirt=(88, 112, 104),
                      trousers=(52, 54, 60), shoes=(30, 30, 34), badge=(60, 170, 180), glasses=True),
    "generic_3": dict(skin=(148, 100, 72), hair=(24, 22, 22), style="curly", brows=(22, 20, 20), shirt=(170, 170, 176),
                      trousers=(36, 40, 52), shoes=(22, 22, 26), badge=(140, 90, 180), beard=True),
}

# The player of Chapter 2: not one of the factory's people, so not in npc/.
NEW_HIRE = dict(skin=(230, 184, 154), hair=(96, 66, 42), style="parted", brows=(80, 54, 36), shirt=(48, 56, 72),
                tie=(28, 30, 40), trousers=(96, 98, 104), shoes=(22, 22, 26), badge=(150, 154, 160))
NEW_HIRE_TEXTURE = TEXTURES.parent / "new_hire.png"


def put(img, rect, x, y, colour):
    img.putpixel((rect[0] + x, rect[1] + y), (*colour, 255))


def block(img, rect, x, y, w, h, colour):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            put(img, rect, xx, yy, colour)


def shade(colour, amount):
    return tuple(max(0, min(255, c + amount)) for c in colour)


def paint_body(img, p):
    f = faces(BODY)
    for name in ("top", "bottom", "right", "left"):
        block(img, f[name], 0, 0, f[name][2], f[name][3], COAT)
    for name in ("right", "left"):                       # the coat's sides, a little in shadow, and its hem
        block(img, f[name], 0, 0, f[name][2], f[name][3], COAT_FOLD)
        block(img, f[name], 0, 11, f[name][2], 1, COAT_SHADE)
    front, back = f["front"], f["back"]
    block(img, front, 0, 0, 8, 12, COAT)
    block(img, front, 0, 11, 8, 1, COAT_SHADE)           # the hem
    block(img, front, 0, 0, 8, 1, COAT_SHADE)            # the collar band
    block(img, front, 3, 0, 2, 4, p["shirt"])            # the shirt in the open collar
    block(img, front, 2, 1, 1, 4, COAT_SHADE)            # the lapels
    block(img, front, 5, 1, 1, 4, COAT_SHADE)
    block(img, front, 3, 4, 2, 1, shade(p["shirt"], -30))
    if "tie" in p:                                       # a tie down the open collar: its knot, then the blade
        block(img, front, 3, 1, 2, 1, shade(p["tie"], 16))
        block(img, front, 3, 2, 2, 3, p["tie"])
    block(img, front, 3, 5, 1, 6, COAT_FOLD)             # the opening down the middle, with its buttons
    put(img, front, 4, 6, BUTTON)
    put(img, front, 4, 9, BUTTON)
    block(img, front, 0, 8, 3, 1, COAT_SHADE)            # the pockets
    block(img, front, 5, 8, 3, 1, COAT_SHADE)
    block(img, front, 6, 3, 2, 3, BADGE_PAPER)           # the badge on the wearer's left chest (the viewer's right)
    block(img, front, 6, 3, 2, 1, p["badge"])
    put(img, front, 6, 5, shade(BADGE_PAPER, -50))
    block(img, back, 0, 0, 8, 12, COAT)
    block(img, back, 0, 0, 8, 1, COAT_SHADE)
    block(img, back, 3, 1, 2, 11, COAT_FOLD)             # the back seam
    block(img, back, 3, 8, 2, 4, COAT_SHADE)             # the vent
    block(img, back, 0, 11, 8, 1, COAT_SHADE)


def paint_arm(img, arm, p):
    f = faces(arm)
    for name in ("top", "front", "back", "right", "left"):
        w, h = f[name][2], f[name][3]
        block(img, f[name], 0, 0, w, h, COAT)
        if name != "top":
            block(img, f[name], 0, 8, w, 1, COAT_SHADE)  # the cuff
            block(img, f[name], 0, 9, w, 1, COAT)
            block(img, f[name], 0, 10, w, 2, p["skin"])  # the hand
    block(img, f["bottom"], 0, 0, f["bottom"][2], f["bottom"][3], shade(p["skin"], -14))
    block(img, f["right"], 1, 1, 1, 6, COAT_FOLD)
    block(img, f["left"], 2, 1, 1, 6, COAT_FOLD)


def paint_leg(img, leg, p):
    f = faces(leg)
    for name in ("front", "back", "right", "left"):
        w, h = f[name][2], f[name][3]
        block(img, f[name], 0, 0, w, 4, COAT)            # the coat's hem over the thigh
        block(img, f[name], 0, 3, w, 1, COAT_SHADE)
        block(img, f[name], 0, 4, w, 6, p["trousers"])
        block(img, f[name], 0, 10, w, 2, p["shoes"])
    block(img, f["front"], 1, 5, 1, 5, shade(p["trousers"], 12))      # the crease
    block(img, f["top"], 0, 0, f["top"][2], f["top"][3], p["trousers"])
    block(img, f["bottom"], 0, 0, f["bottom"][2], f["bottom"][3], shade(p["shoes"], 14))

# The guards: Chapter 1's uniform (body, arms, legs of guard.png) under a head painted like the workers'.
GUARDS = {
    "guard_1": dict(skin=(224, 180, 150), hair=(30, 28, 28), style="parted", brows=(30, 28, 28), stubble=True),
    "guard_2": dict(skin=(150, 104, 78), hair=(26, 22, 20), style="bald", brows=(26, 22, 20), beard=True),
}


def paint_head(img, p):
    f = faces(HEAD)
    skin, hair, brows = p["skin"], p["hair"], p["brows"]
    top, bottom, right, front, left, back = (f[n] for n in ("top", "bottom", "right", "front", "left", "back"))
    for rect in (top, bottom, right, front, left, back):
        block(img, rect, 0, 0, 8, 8, skin)
    block(img, bottom, 0, 0, 8, 8, shade(skin, -18))     # the neck
    # The face: brows on row 3, eyes on row 4, the nose on row 5, the mouth on row 6.
    for x in (1, 2, 5, 6):
        put(img, front, x, 3, brows)
    for white, pupil in ((1, 2), (5, 6)):
        put(img, front, white, 4, EYE_WHITE)
        put(img, front, pupil, 4, EYE)
    block(img, front, 3, 5, 2, 1, shade(skin, -22))
    block(img, front, 3, 6, 2, 1, shade(skin, -46))
    for rect in (right, left):                           # an ear on each side
        block(img, rect, 3 if rect is right else 4, 4, 1, 2, shade(skin, -24))
    if p.get("nervous"):
        for x in (1, 2, 5, 6):                           # brows slanting up towards the middle, rings under the eyes
            put(img, front, x, 3, skin)
        for x, y in ((1, 3), (2, 2), (5, 2), (6, 3)):
            put(img, front, x, y, brows)
        for x in (1, 2, 5, 6):
            put(img, front, x, 5, shade(skin, -34))
        put(img, front, 7, 3, SWEAT)
        put(img, front, 7, 4, SWEAT)
    if p.get("older"):
        put(img, front, 0, 4, shade(skin, -34))          # crow's feet
        put(img, front, 7, 4, shade(skin, -34))
        block(img, front, 2, 1, 4, 1, shade(skin, -20))  # a line across the forehead
        put(img, front, 2, 5, shade(skin, -26))          # the lines beside the mouth
        put(img, front, 5, 5, shade(skin, -26))
        block(img, front, 2, 6, 4, 1, hair)              # a moustache over the mouth
    if p.get("stubble"):
        block(img, front, 1, 7, 6, 1, shade(skin, -26))  # a shadow of beard along the jaw
        for x in (0, 1, 6, 7):
            put(img, front, x, 6, shade(skin, -26))
        block(img, bottom, 0, 0, 8, 8, shade(skin, -30))
    rng = random.Random(len(hair) + hair[0])
    style = p["style"]
    if style in ("parted", "messy", "curly"):
        block(img, top, 0, 0, 8, 8, hair)
        block(img, front, 0, 0, 8, 2, hair)
        block(img, front, 0, 2, 1, 1, hair)
        block(img, front, 7, 2, 1, 1, hair)
        for rect, cols in ((right, (0, 8)), (left, (0, 8))):
            block(img, rect, 0, 0, 8, 3, hair)
        block(img, right, 0, 3, 5, 2, hair)
        block(img, left, 3, 3, 5, 2, hair)
        block(img, back, 0, 0, 8, 6, hair)
        block(img, back, 1, 6, 6, 1, hair)
        if style == "parted":                            # side-swept: the fringe cut short on one side, a parting
            put(img, front, 5, 1, skin)
            put(img, front, 6, 1, skin)
            put(img, front, 7, 1, skin)
            put(img, front, 2, 0, shade(hair, 22))
            put(img, front, 2, 1, shade(hair, 22))
        if style == "messy":                             # untidy: ragged edge, tufts, lighter strands
            for x in (2, 5):
                put(img, front, x, 1, skin)
            put(img, front, 3, 0, shade(hair, 30))
            put(img, front, 6, 0, shade(hair, 30))
            for _ in range(10):
                put(img, top, rng.randrange(8), rng.randrange(8), shade(hair, rng.choice((-16, 18))))
        if style == "curly":                             # curls: a pattern of lights and darks over the hair
            for rect in (top, back):
                for y in range(8):
                    for x in range(8):
                        if (x + y) % 2 == 0:
                            put(img, rect, x, y, shade(hair, 14))
            for y in (0, 1):
                for x in range(8):
                    if (x + y) % 2 == 0:
                        put(img, front, x, y, shade(hair, 14))
    elif style == "receding":
        for rect in (top,):
            block(img, rect, 0, 0, 8, 8, shade(skin, -6))
            block(img, rect, 0, 0, 2, 8, hair)
            block(img, rect, 6, 0, 2, 8, hair)
            block(img, rect, 0, 5, 8, 3, hair)
        for x, y in ((0, 0), (1, 0), (0, 1), (6, 0), (7, 0), (7, 1)):
            put(img, front, x, y, hair)
        block(img, right, 0, 0, 6, 2, hair)
        block(img, left, 2, 0, 6, 2, hair)
        block(img, right, 0, 2, 3, 3, hair)
        block(img, left, 5, 2, 3, 3, hair)
        block(img, back, 0, 0, 8, 5, hair)
    elif style == "long":
        block(img, top, 0, 0, 8, 8, hair)
        block(img, front, 0, 0, 8, 2, hair)
        block(img, front, 0, 2, 3, 1, hair)              # a fringe swept over one brow
        block(img, front, 0, 3, 1, 3, hair)              # the hair framing the face
        block(img, front, 7, 2, 1, 4, hair)
        block(img, right, 0, 0, 8, 3, hair)
        block(img, left, 0, 0, 8, 3, hair)
        block(img, right, 0, 3, 6, 5, hair)
        block(img, left, 2, 3, 6, 5, hair)
        block(img, back, 0, 0, 8, 8, hair)
        for y in range(2, 8, 2):                         # a lighter strand now and then
            put(img, back, 3, y, shade(hair, 26))
            put(img, back, 5, y + 1, shade(hair, 26))
    elif style == "bald":
        block(img, top, 0, 0, 8, 8, shade(skin, 8))
        for rect, cols in ((right, (0, 4)), (left, (4, 8))):
            block(img, rect, cols[0], 3, cols[1] - cols[0], 3, hair)   # what hair is left, round the back
        block(img, back, 0, 3, 8, 3, hair)
        block(img, back, 0, 2, 1, 1, hair)
        block(img, back, 7, 2, 1, 1, hair)
    if p.get("glasses"):
        frame = (46, 48, 56)                             # a browline: a bar over each lens, the sides and the bridge
        for x in (1, 2, 5, 6):
            put(img, front, x, 3, frame)
        for x in (0, 3, 4, 7):
            put(img, front, x, 4, frame)
        block(img, right, 5, 4, 3, 1, frame)             # the temples over the ears
        block(img, left, 0, 4, 3, 1, frame)
    if p.get("beard"):
        beard = shade(hair, 6)
        block(img, front, 0, 5, 1, 3, beard)
        block(img, front, 7, 5, 1, 3, beard)
        block(img, front, 1, 6, 6, 2, beard)
        block(img, front, 2, 5, 1, 1, beard)
        block(img, front, 5, 5, 1, 1, beard)
        block(img, front, 3, 6, 2, 1, shade(p["skin"], -40))   # the mouth in it
        block(img, right, 3, 5, 5, 3, beard)
        block(img, left, 0, 5, 5, 3, beard)
        block(img, bottom, 0, 0, 8, 8, shade(hair, 10))


def paint(p):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    paint_head(img, p)
    paint_body(img, p)
    paint_arm(img, RIGHT_ARM, p)
    paint_arm(img, LEFT_ARM, p)
    paint_leg(img, RIGHT_LEG, p)
    paint_leg(img, LEFT_LEG, p)
    return img


def crop(img, rect):
    x, y, w, h = rect
    return img.crop((x, y, x + w, y + h))


def view(img, back=False):
    """The skin as seen from the front (or the back): 16 x 32 pixels, the head, the body, both arms and both legs."""
    out = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    side = "back" if back else "front"
    out.paste(crop(img, faces(HEAD)[side]), (4, 0))
    out.paste(crop(img, faces(BODY)[side]), (4, 8))
    left_of_view, right_of_view = (LEFT_ARM, RIGHT_ARM) if back else (RIGHT_ARM, LEFT_ARM)
    out.paste(crop(img, faces(left_of_view)[side]), (0, 8))
    out.paste(crop(img, faces(right_of_view)[side]), (12, 8))
    left_leg, right_leg = (LEFT_LEG, RIGHT_LEG) if back else (RIGHT_LEG, LEFT_LEG)
    out.paste(crop(img, faces(left_leg)[side]), (4, 20))
    out.paste(crop(img, faces(right_leg)[side]), (8, 20))
    return out


def preview(skins):
    scale, pad, label = 8, 12, 16
    cell_w, cell_h = 16 * scale, 32 * scale
    sheet = Image.new("RGBA", (len(skins) * (2 * cell_w + pad) + pad, cell_h + label + 2 * pad), (112, 114, 120, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(skins):
        x0 = pad + i * (2 * cell_w + pad)
        for j, back in enumerate((False, True)):
            sheet.alpha_composite(view(img, back).resize((cell_w, cell_h), Image.NEAREST), (x0 + j * cell_w, pad + label))
        draw.text((x0, pad), name, fill=(240, 240, 240, 255))
    return sheet


def main():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    skins = []
    for name, person in PEOPLE.items():
        img = paint(person)
        img.save(TEXTURES / f"{name}.png")
        skins.append((name, img))
    uniform = Image.open(GUARD_UNIFORM).convert("RGBA")
    for name, person in GUARDS.items():
        img = uniform.copy()
        paint_head(img, person)
        img.save(TEXTURES / f"{name}.png")
        skins.append((name, img))
    hire = paint(NEW_HIRE)
    hire.save(NEW_HIRE_TEXTURE)
    skins.append(("new_hire", hire))
    rowan = TEXTURES / "rowan.png"
    if rowan.exists():
        skins.append(("rowan", Image.open(rowan).convert("RGBA")))
    preview(skins).save(OUT / "workers_preview.png")
    print(f"{len(PEOPLE) + len(GUARDS) + 1} skins in {TEXTURES.relative_to(ROOT)}, preview in {OUT.relative_to(ROOT)}/workers_preview.png")


if __name__ == "__main__":
    main()
