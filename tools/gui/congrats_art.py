"""
The congratulations window's pixel art (client/screen/CongratulationsScreen.kt): the Observer, sat with a cup of
coffee at the end of his shift, animated: the steam rises, and now and then he drinks. 48x48 pixels a frame, on a
white window, so outlined in near black like the achievement icons. His look is the model's
(tools/entity/observer.py): a blank grey head, a black suit, a white shirt, a dark red tie, white gloves.

The sheet is 4 columns by 4 rows of frames: the column is the pose of his right arm (0 the cup at his side, 1
rising, 2 at his head, 3 tilted to drink) and the row the phase of the steam (0 to 3; there is none at the head).
The window picks the frames in the order of TIMELINE below, which CongratulationsScreen.kt repeats.

Run after changing anything here (from the repository root):   python3 tools/gui/congrats_art.py

Writes src/main/resources/assets/every_tunnel/textures/gui/congrats_observer.png (192x192, committed) and, in
tools/gui/output/ (git-ignored), congrats_observer_preview.png (the sheet, enlarged) and
congrats_observer_preview.gif (the animation, enlarged).
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui" / "congrats_observer.png"
OUT = Path(__file__).resolve().parent / "output"

W, H = 48, 48
POSES = 4
PHASES = 4

OUTLINE = (20, 21, 26, 255)
HEAD = (156, 162, 170, 255)
HEAD_LIGHT = (196, 201, 208, 255)
HEAD_SHADE = (118, 124, 134, 255)
SUIT = (40, 41, 48, 255)
SUIT_LIGHT = (64, 66, 76, 255)
SUIT_SHADE = (28, 29, 34, 255)
SHIRT = (238, 240, 242, 255)
SHIRT_SHADE = (196, 200, 208, 255)
TIE = (128, 26, 36, 255)
TIE_SHADE = (88, 16, 26, 255)
GLOVE = (250, 250, 250, 255)
GLOVE_SHADE = (206, 210, 216, 255)
SHOE = (26, 27, 32, 255)
SHOE_LIGHT = (70, 72, 82, 255)
CHAIR = (96, 101, 112, 255)
CHAIR_LIGHT = (130, 136, 148, 255)
CUP = (244, 242, 236, 255)
CUP_SHADE = (204, 200, 190, 255)
COFFEE = (104, 64, 38, 255)
STEAM = (176, 182, 192, 255)
STEAM_FAINT = (204, 208, 216, 255)

# The animation: (pose, steam phase, milliseconds), looped. About 6 seconds: the cup steams at his side, he lifts it,
# sips twice, and puts it back.
TIMELINE = (
    [(0, k % 4, 300) for k in range(12)]
    + [(1, 0, 350), (2, 1, 350), (3, 0, 550), (2, 2, 250), (3, 0, 550), (2, 3, 300), (1, 1, 300)]
)

# The right hand (its middle) in each pose, and the cup's top left corner.
HAND = [(35, 23), (34, 16), (33, 11), (34, 9)]
CUP_AT = [(36, 18), (35, 8), (28, 2), None]


def new(size=(W, H)):
    return Image.new("RGBA", size, (0, 0, 0, 0))


def rect(img, x0, y0, x1, y1, colour):
    """Fills x0..x1, y0..y1 inclusive."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < img.width and 0 <= y < img.height:
                img.putpixel((x, y), colour)


def px(img, points, colour):
    for x, y in points:
        if 0 <= x < img.width and 0 <= y < img.height:
            img.putpixel((x, y), colour)


def thick_line(img, a, b, half, colour):
    """A line from a to b, 2 * half + 1 pixels across."""
    (x0, y0), (x1, y1) = a, b
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        x = round(x0 + (x1 - x0) * i / steps)
        y = round(y0 + (y1 - y0) * i / steps)
        rect(img, x - half, y - half, x + half, y + half, colour)


def outline(img):
    """A one pixel outline round everything drawn, in near black."""
    src = img.copy()
    for y in range(img.height):
        for x in range(img.width):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < img.width and 0 <= ny < img.height and src.getpixel((nx, ny))[3] != 0:
                    img.putpixel((x, y), OUTLINE)
                    break


def cup_image():
    """The cup upright, 10x9: the coffee on top, the handle on the right."""
    cup = new((10, 9))
    rect(cup, 0, 0, 6, 7, CUP)
    rect(cup, 6, 0, 6, 7, CUP_SHADE)
    rect(cup, 0, 0, 6, 1, COFFEE)
    rect(cup, 7, 2, 8, 2, CUP)
    rect(cup, 8, 3, 8, 5, CUP)
    rect(cup, 7, 6, 7, 6, CUP)
    return cup


def body():
    """Everything of him that does not move: the chair, legs, torso, head and his left arm."""
    img = new()

    # The chair: a seat no wider than he is, and a stem between his knees (so nothing sticks out like a hand).
    rect(img, 10, 31, 29, 34, CHAIR)
    rect(img, 10, 31, 29, 31, CHAIR_LIGHT)
    rect(img, 19, 35, 20, 43, CHAIR)
    rect(img, 16, 44, 23, 45, CHAIR)

    # Legs and shoes: sat, knees forward, feet flat on the floor.
    for x0 in (12, 22):
        rect(img, x0, 35, x0 + 5, 41, SUIT)
        rect(img, x0, 35, x0, 41, SUIT_LIGHT)
        rect(img, x0 + 5, 35, x0 + 5, 41, SUIT_SHADE)
        rect(img, x0 - 1, 42, x0 + 6, 45, SHOE)
        rect(img, x0 - 1, 42, x0 + 6, 42, SHOE_LIGHT)

    # The torso: the suit, the shirt's V and the tie.
    rect(img, 11, 15, 28, 33, SUIT)
    rect(img, 11, 15, 12, 33, SUIT_LIGHT)
    rect(img, 27, 15, 28, 33, SUIT_SHADE)
    rect(img, 16, 15, 23, 16, SHIRT)
    rect(img, 17, 17, 22, 18, SHIRT)
    rect(img, 18, 19, 21, 20, SHIRT)
    px(img, [(16, 15), (23, 15), (17, 17), (22, 17), (18, 19), (21, 19)], SHIRT_SHADE)
    rect(img, 19, 16, 20, 17, TIE_SHADE)  # the knot
    rect(img, 19, 18, 20, 26, TIE)
    px(img, [(20, y) for y in range(18, 27)], TIE_SHADE)
    px(img, [(19, 27), (20, 27)], TIE_SHADE)
    px(img, [(19, 29), (20, 29), (19, 32)], SUIT_LIGHT)

    # The neck and the head: a blank grey cube.
    rect(img, 18, 12, 21, 14, SUIT_SHADE)
    rect(img, 13, 1, 26, 11, HEAD)
    rect(img, 13, 1, 26, 2, HEAD_LIGHT)       # the cube's top, lit
    rect(img, 13, 3, 14, 11, HEAD_LIGHT)
    rect(img, 25, 3, 26, 11, HEAD_SHADE)
    rect(img, 13, 10, 26, 11, HEAD_SHADE)

    # His left arm (to our left) hangs, the glove resting at the hem of his jacket.
    rect(img, 7, 16, 10, 29, SUIT)
    rect(img, 7, 16, 7, 29, SUIT_LIGHT)
    rect(img, 8, 30, 11, 33, GLOVE)
    rect(img, 11, 30, 11, 33, GLOVE_SHADE)
    return img


def right_arm(img, pose):
    """His right arm (to our right) and the cup, in [pose]."""
    # The upper arm hangs from the shoulder; the forearm goes from the elbow to the hand.
    rect(img, 29, 16, 32, 25, SUIT)
    rect(img, 31, 16, 32, 25, SUIT_SHADE)
    hx, hy = HAND[pose]
    thick_line(img, (31, 25), (hx - 1, hy + 1), 1, SUIT)
    rect(img, hx - 1, hy - 2, hx + 2, hy + 2, GLOVE)
    rect(img, hx - 1, hy + 2, hx + 2, hy + 2, GLOVE_SHADE)
    cup = cup_image()
    if CUP_AT[pose] is not None:
        img.alpha_composite(cup, CUP_AT[pose])
    else:
        # Tilted to drink: the rim to his head. Turned about its middle, then put where he holds it.
        tilted = cup.rotate(48, expand=True, resample=Image.NEAREST)
        img.alpha_composite(tilted, (27, 0))
    # The hand over the cup's handle, so that he holds it.
    rect(img, hx, hy - 1, hx + 2, hy + 1, GLOVE)


def steam(img, pose, phase):
    """Two wisps over the cup, swaying as they rise; none at his head."""
    if pose > 1:
        return
    top = CUP_AT[pose][1] - 2
    left = CUP_AT[pose][0]
    sway = (0, 1, 0, -1)
    for base, offset in ((left + 2, 0), (left + 6, 2)):
        for i in range(5):
            x = base + sway[(i + phase + offset) % 4]
            y = top - i
            if y >= 0:
                img.putpixel((x, y), STEAM if i < 3 else STEAM_FAINT)


def frame(pose, phase):
    img = body()
    right_arm(img, pose)
    outline(img)
    steam(img, pose, phase)
    return img


def main():
    sheet = new((W * POSES, H * PHASES))
    frames = {}
    for pose in range(POSES):
        for phase in range(PHASES):
            f = frame(pose, phase)
            frames[(pose, phase)] = f
            sheet.paste(f, (pose * W, phase * H))
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(TEXTURE)
    OUT.mkdir(parents=True, exist_ok=True)

    scale = 6
    big = Image.new("RGBA", (sheet.width * scale + 20, sheet.height * scale + 20), (255, 255, 255, 255))
    big.alpha_composite(sheet.resize((sheet.width * scale, sheet.height * scale), Image.NEAREST), (10, 10))
    big.save(OUT / "congrats_observer_preview.png")

    gif = []
    for pose, phase, _ in TIMELINE:
        shot = Image.new("RGBA", (W * 8, H * 8), (255, 255, 255, 255))
        shot.alpha_composite(frames[(pose, phase)].resize((W * 8, H * 8), Image.NEAREST))
        gif.append(shot.convert("P", palette=Image.ADAPTIVE))
    gif[0].save(OUT / "congrats_observer_preview.gif", save_all=True, append_images=gif[1:],
                duration=[ms for _, _, ms in TIMELINE], loop=0, disposal=2)
    print(f"wrote {TEXTURE} ({sheet.width}x{sheet.height}); loop {sum(ms for *_, ms in TIMELINE)} ms")


if __name__ == "__main__":
    main()
