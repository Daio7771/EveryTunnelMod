"""
The night guard's uniform: the player's look in worlds with levels (a 64x64 player skin,
wide arms). Black jacket with SECURITY in yellow on the back and a badge on the chest,
yellow shoulder patches, black trousers, a belt with a radio, black boots; short dark hair.
Cold palette (see the art direction): blue-black blacks, a pale, tired face.

Run after changing anything here (from the repository root):   python3 tools/entity/guard.py

Writes src/main/resources/assets/every_tunnel/textures/entity/guard.png (committed; the mod
puts it on every player in a world with levels, see client/render/GuardUniform.kt) and
tools/entity/output/guard_preview.png (git-ignored): the skin enlarged, with front and back
views of the body.

Layout: the standard 64x64 skin. A box of size (w, h, d) at texture origin (u, v) has its
faces at top (u+d, v), bottom (u+d+w, v), right (u, v+d), front (u+d, v+d), left
(u+d+w, v+d), back (u+2d+w, v+d). "Right" is the model's own right (the viewer's left when
facing it). The second layer (hat, jacket, sleeves, trouser legs) stays empty.
"""

import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "entity" / "guard.png"
OUT = Path(__file__).resolve().parent / "output"

# Colours
JACKET = (30, 32, 37)
JACKET_DARK = (20, 21, 25)
JACKET_LIGHT = (46, 49, 56)
YELLOW = (222, 188, 44)
YELLOW_DARK = (168, 138, 30)
SKIN = (196, 160, 138)
SKIN_SHADE = (166, 130, 112)
HAIR = (38, 32, 30)
HAIR_LIGHT = (56, 48, 44)
EYE_WHITE = (222, 224, 226)
EYE = (52, 64, 74)
MOUTH = (128, 92, 84)
TROUSERS = (26, 27, 31)
TROUSERS_DARK = (18, 19, 22)
BELT = (12, 12, 14)
BUCKLE = (128, 130, 136)
RADIO = (44, 46, 50)
BOOTS = (16, 16, 18)
SOLE = (34, 34, 36)

rng = random.Random("guard")
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def put(x, y, c):
    img.putpixel((x, y), (*c, 255))


def fill(x, y, w, h, c, noise=0):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            n = rng.randint(-noise, noise) if noise else 0
            put(xx, yy, tuple(max(0, min(255, v + n)) for v in c))


def faces(u, v, w, h, d):
    """The six faces of a box: name -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def head():
    f = faces(0, 0, 8, 8, 8)
    for name, (x, y, w, h) in f.items():
        fill(x, y, w, h, SKIN, 3)
    fill(*f["top"], HAIR, 4)
    x, y, w, h = f["back"]
    fill(x, y, w, 6, HAIR, 4)                                       # hair down to the nape
    for side in ("right", "left"):
        x, y, w, h = f[side]
        fill(x, y, w, 3, HAIR, 4)
        fill(x + (0 if side == "left" else w - 3), y + 3, 3, 1, HAIR, 3)   # behind the ears
        put(x + (5 if side == "right" else 2), y + 4, SKIN_SHADE)          # ear
    x, y, w, h = f["front"]
    fill(x, y, w, 2, HAIR, 4)                                       # short fringe
    put(x, y + 2, HAIR)
    put(x + 7, y + 2, HAIR)
    for ex in (1, 5):                                               # tired eyes, brows above
        put(x + ex, y + 3, HAIR_LIGHT)
        put(x + ex + 1, y + 3, HAIR_LIGHT)
        put(x + ex, y + 4, EYE_WHITE if ex == 1 else EYE)
        put(x + ex + 1, y + 4, EYE if ex == 1 else EYE_WHITE)
        put(x + ex, y + 5, SKIN_SHADE)
        put(x + ex + 1, y + 5, SKIN_SHADE)
    put(x + 3, y + 5, SKIN_SHADE)                                   # nose
    put(x + 4, y + 5, SKIN_SHADE)
    fill(x + 3, y + 6, 2, 1, MOUTH)
    fill(x + 1, y + 7, 6, 1, SKIN_SHADE)                            # stubble line along the jaw
    fill(*f["bottom"], SKIN_SHADE)


def body():
    f = faces(16, 16, 8, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(x, y, w, h, JACKET, 3)
    x, y, w, h = f["front"]
    fill(x + 3, y, 2, 2, SKIN_SHADE)                                # open collar
    put(x + 2, y, JACKET_LIGHT)
    put(x + 5, y, JACKET_LIGHT)
    for yy in range(2, 10):                                         # zip
        put(x + 4, y + yy, JACKET_LIGHT)
    fill(x + 5, y + 2, 2, 2, YELLOW)                                # badge on the chest
    put(x + 6, y + 3, YELLOW_DARK)
    fill(x + 1, y + 2, 2, 1, JACKET_LIGHT)                          # pocket flap
    fill(x, y + 9, w, 1, JACKET_DARK)                               # hem
    fill(x, y + 10, w, 1, BELT)
    fill(x + 3, y + 10, 2, 1, BUCKLE)
    fill(x, y + 11, w, 1, TROUSERS)
    x, y, w, h = f["back"]
    # SECURITY across the back, two rows of block letters (S E C U / R I T Y), with the
    # dark gaps that make them read as print from a distance.
    fill(x, y + 2, w, 2, YELLOW)
    fill(x, y + 5, w, 2, YELLOW)
    for gx in (1, 3, 5, 7):
        put(x + gx, y + 3, YELLOW_DARK)
        put(x + gx - 1, y + 6, YELLOW_DARK)
    fill(x, y + 9, w, 1, JACKET_DARK)
    fill(x, y + 10, w, 1, BELT)
    fill(x, y + 11, w, 1, TROUSERS)
    for side in ("right", "left"):
        x, y, w, h = f[side]
        fill(x, y + 9, w, 1, JACKET_DARK)
        fill(x, y + 10, w, 1, BELT)
        fill(x, y + 11, w, 1, TROUSERS)
    x, y, w, h = f["right"]                                         # the radio on the belt
    fill(x + 1, y + 8, 2, 3, RADIO)
    put(x + 1, y + 7, BELT)                                         # antenna
    put(x + 2, y + 8, (90, 160, 90))                                # its little green light
    fill(*f["top"], JACKET_DARK)
    fill(*f["bottom"], TROUSERS_DARK)


def arm(u, v, outer):
    """A wide arm: a black sleeve, a yellow patch on the shoulder's outer side, a cuff, the hand."""
    f = faces(u, v, 4, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(x, y, w, h, JACKET, 3)
        if name not in ("top", "bottom"):
            fill(x, y + 9, w, 1, JACKET_DARK)                       # cuff
            fill(x, y + 10, w, 2, SKIN, 3)                          # hand
    x, y, w, h = f[outer]
    fill(x + 1, y + 1, 2, 2, YELLOW)                                # SECURITY shoulder patch
    put(x + 2, y + 2, YELLOW_DARK)
    fill(*f["top"], JACKET_LIGHT)
    fill(*f["bottom"], SKIN_SHADE)
    x, y, w, h = f["front"]
    put(x, y + 11, SKIN_SHADE)                                      # thumb
    put(x + 3, y + 11, SKIN_SHADE)


def leg(u, v):
    f = faces(u, v, 4, 12, 4)
    for name, (x, y, w, h) in f.items():
        fill(x, y, w, h, TROUSERS, 3)
        if name not in ("top", "bottom"):
            fill(x, y + 9, w, 3, BOOTS, 2)
            fill(x, y + 11, w, 1, SOLE)
    x, y, w, h = f["front"]
    for yy in range(0, 9, 3):                                       # crease
        put(x + 1, y + yy, TROUSERS_DARK)
    put(x + 1, y + 9, SOLE)                                         # laces
    put(x + 2, y + 10, SOLE)
    fill(*f["top"], TROUSERS_DARK)
    fill(*f["bottom"], SOLE)


head()
body()
arm(40, 16, "right")     # right arm: its outer side is its own right
arm(32, 48, "left")      # left arm
leg(0, 16)               # right leg
leg(16, 48)              # left leg

TEXTURE.parent.mkdir(parents=True, exist_ok=True)
img.save(TEXTURE)


def preview():
    """The texture enlarged, and the body seen from the front and from the back."""
    s = 8

    def crop(x, y, w, h, flip=False):
        part = img.crop((x, y, x + w, y + h))
        return part.transpose(Image.FLIP_LEFT_RIGHT) if flip else part

    def figure(side):
        fig = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
        hf = faces(0, 0, 8, 8, 8)[side]
        bf = faces(16, 16, 8, 12, 4)[side]
        ra = faces(40, 16, 4, 12, 4)[side]
        la = faces(32, 48, 4, 12, 4)[side]
        rl = faces(0, 16, 4, 12, 4)[side]
        ll = faces(16, 48, 4, 12, 4)[side]
        fig.paste(crop(*hf), (4, 0))
        fig.paste(crop(*bf), (4, 8))
        right, left = (crop(*ra), crop(*la)) if side == "front" else (crop(*la), crop(*ra))
        fig.paste(right, (0, 8))
        fig.paste(left, (12, 8))
        rleg, lleg = (crop(*rl), crop(*ll)) if side == "front" else (crop(*ll), crop(*rl))
        fig.paste(rleg, (4, 20))
        fig.paste(lleg, (8, 20))
        return fig

    sheet = Image.new("RGBA", (64 * s + 40 + 2 * 16 * s + 20, 64 * s), (70, 74, 80, 255))
    sheet.paste(img.resize((64 * s, 64 * s), Image.NEAREST), (0, 0), img.resize((64 * s, 64 * s), Image.NEAREST))
    for i, side in enumerate(("front", "back")):
        fig = figure(side).resize((16 * s, 32 * s), Image.NEAREST)
        sheet.paste(fig, (64 * s + 40 + i * (16 * s + 20), 0), fig)
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "guard_preview.png")


preview()
print(f"wrote {TEXTURE.relative_to(ROOT)} and {(OUT / 'guard_preview.png').relative_to(ROOT)}")
