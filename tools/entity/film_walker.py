"""
The figure in the Cinema's film: the head of the Noname Complex in disguise, in a dark executive's
suit and an evil mask, only ever seen on film. A 64 x 64 texture in the player's layout (the renderer
uses the player model), written to assets/every_tunnel/textures/entity/film_walker.png.

    python3 tools/entity/film_walker.py
"""

import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "entity" / "film_walker.png"

SUIT, SUIT_DARK, SHIRT, TIE = (30, 31, 36), (20, 20, 24), (214, 212, 204), (96, 18, 22)
SHOE, HAND, HAIR = (12, 12, 14), (40, 38, 38), (14, 13, 13)
MASK, MASK_SHADE, HOLLOW = (216, 212, 198), (168, 162, 146), (6, 5, 5)

# Each part's top left in the texture and its size: width, height, depth (the player's layout).
HEAD, BODY = (0, 0, 8, 8, 8), (16, 16, 8, 12, 4)
RIGHT_ARM, LEFT_ARM = (40, 16, 4, 12, 4), (32, 48, 4, 12, 4)
RIGHT_LEG, LEFT_LEG = (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)


def faces(part):
    """{face: (x, y, width, height)} of a part."""
    u, v, w, h, d = part
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def fill(img, rng, rect, colour, noise=4):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            shade = rng.randint(-noise, noise)
            img.putpixel((x, y), (*[max(0, min(255, c + shade)) for c in colour], 255))


def px(img, origin, x, y, colour):
    img.putpixel((origin[0] + x, origin[1] + y), (*colour, 255))


def paint_mask(img, rng, face):
    """The mask's face on an 8 x 8 rectangle `face` (x, y, width, height): pale, slanted hollow eyes, a jagged
    grin (also the director's, tools/entity/director.py)."""
    fill(img, rng, face, MASK, 3)
    o = face[:2]
    for x, y in ((1, 2), (2, 2), (2, 3), (3, 3), (6, 2), (5, 2), (5, 3), (4, 3)):     # eyes slanting in, angry
        px(img, o, x, y, HOLLOW)
    for x in (3, 4):                                                                    # a slit for a nose
        px(img, o, x, 4, MASK_SHADE)
    for x, y in ((1, 5), (2, 6), (3, 5), (4, 6), (5, 5), (6, 6), (1, 6), (6, 5)):       # the grin's teeth
        px(img, o, x, y, HOLLOW)
    for x in range(8):                                                                  # the brow and the jaw in shadow
        px(img, o, x, 1, MASK_SHADE)
        px(img, o, x, 7, MASK_SHADE)


def paint():
    rng = random.Random(14)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for part, colour in ((BODY, SUIT), (RIGHT_ARM, SUIT), (LEFT_ARM, SUIT), (RIGHT_LEG, SUIT), (LEFT_LEG, SUIT), (HEAD, HAIR)):
        for rect in faces(part).values():
            fill(img, rng, rect, colour)
    for arm in (RIGHT_ARM, LEFT_ARM):                       # dark gloved hands
        fill(img, rng, faces(arm)["bottom"], HAND)
        u, v, w, h, d = arm
        fill(img, rng, (u, v + d + h - 2, 2 * (w + d), 2), HAND)
    for leg in (RIGHT_LEG, LEFT_LEG):                       # black shoes
        fill(img, rng, faces(leg)["bottom"], SHOE)
        u, v, w, h, d = leg
        fill(img, rng, (u, v + d + h - 2, 2 * (w + d), 2), SHOE, 2)

    # The suit's front: a white shirt in a V, a dark red tie, lapels, a seam down the middle.
    front = faces(BODY)["front"][:2]
    for y in range(6):
        for x in range(4 - (6 - y) // 2 if y < 6 else 4, 4 + (6 - y) // 2):
            px(img, front, x, y, SHIRT)
    for y in range(5):
        px(img, front, 3, y, TIE)
        px(img, front, 4, y, TIE)
    for y in range(6, 12):
        px(img, front, 4, y, SUIT_DARK)
    for x, y in ((1, 1), (6, 1), (2, 3), (5, 3)):
        px(img, front, x, y, SUIT_DARK)

    # The mask covers the whole face: pale, with slanted hollow eyes and a jagged grin.
    paint_mask(img, rng, faces(HEAD)["front"])
    for side in ("right", "left"):                                                      # the strap round the head
        x, y, w, _ = faces(HEAD)[side]
        for k in range(w):
            img.putpixel((x + k, y + 3), (*MASK_SHADE, 255))
    return img


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    paint().save(OUT)
    print(OUT)
