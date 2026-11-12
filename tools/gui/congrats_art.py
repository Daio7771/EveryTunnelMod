"""
The congratulations window's pixel art (client/screen/CongratulationsScreen.kt): the Observer, sat on a
chair with a cup of coffee, at the end of his shift. 40x48 pixels, drawn on a white window, so outlined in near
black like the achievement icons. His look is the model's (tools/entity/observer.py): a blank grey head, a black
suit, a white shirt, a dark red tie, white gloves.

Run after changing anything here (from the repository root):   python3 tools/gui/congrats_art.py

Writes src/main/resources/assets/every_tunnel/textures/gui/congrats_observer.png (committed) and
tools/gui/output/congrats_observer_preview.png (git-ignored; enlarged, on the window's white).
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui" / "congrats_observer.png"
OUT = Path(__file__).resolve().parent / "output"

W, H = 48, 48

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
CHAIR_SHADE = (66, 70, 80, 255)
CUP = (244, 242, 236, 255)
CUP_SHADE = (204, 200, 190, 255)
COFFEE = (104, 64, 38, 255)
STEAM = (176, 182, 192, 255)


def new():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def rect(img, x0, y0, x1, y1, colour):
    """Fills x0..x1, y0..y1 inclusive."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), colour)


def px(img, points, colour):
    for x, y in points:
        img.putpixel((x, y), colour)


def outline(img):
    """A one pixel outline round everything drawn, in near black."""
    src = img.copy()
    for y in range(H):
        for x in range(W):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < H and src.getpixel((nx, ny))[3] != 0:
                    img.putpixel((x, y), OUTLINE)
                    break


def draw():
    img = new()

    # The chair: a backrest peeking out on both sides of him, and the seat.
    rect(img, 8, 17, 31, 26, CHAIR)
    rect(img, 8, 17, 31, 18, CHAIR_LIGHT)
    rect(img, 30, 19, 31, 26, CHAIR_SHADE)
    rect(img, 7, 31, 32, 34, CHAIR)
    rect(img, 7, 31, 32, 31, CHAIR_LIGHT)
    rect(img, 7, 34, 32, 34, CHAIR_SHADE)

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
    # The jacket's buttons.
    px(img, [(19, 29), (20, 29), (19, 32)], SUIT_LIGHT)

    # The neck and the head: a blank grey cube.
    rect(img, 18, 12, 21, 14, SUIT_SHADE)
    rect(img, 13, 1, 26, 11, HEAD)
    rect(img, 13, 1, 26, 2, HEAD_LIGHT)       # the cube's top, lit
    rect(img, 13, 3, 14, 11, HEAD_LIGHT)
    rect(img, 25, 3, 26, 11, HEAD_SHADE)
    rect(img, 13, 10, 26, 11, HEAD_SHADE)

    # His left arm (to our left) rests on his knee, the glove on it.
    rect(img, 7, 16, 10, 28, SUIT)
    rect(img, 7, 16, 7, 28, SUIT_LIGHT)
    rect(img, 8, 29, 11, 32, GLOVE)
    rect(img, 8, 29, 8, 32, GLOVE)
    rect(img, 11, 29, 11, 32, GLOVE_SHADE)

    # His right arm (to our right): the sleeve comes out at the elbow and the glove holds the cup up beside him.
    rect(img, 29, 16, 32, 25, SUIT)
    rect(img, 31, 16, 32, 25, SUIT_SHADE)
    rect(img, 29, 24, 34, 27, SUIT)
    rect(img, 29, 27, 34, 27, SUIT_SHADE)
    rect(img, 34, 21, 37, 26, GLOVE)
    rect(img, 34, 26, 37, 26, GLOVE_SHADE)

    # The cup, steaming, against the white above his shoulder.
    rect(img, 36, 18, 42, 25, CUP)
    rect(img, 42, 18, 42, 25, CUP_SHADE)
    rect(img, 36, 18, 42, 19, COFFEE)
    rect(img, 43, 20, 44, 20, CUP)            # the handle
    rect(img, 44, 21, 44, 23, CUP)
    rect(img, 43, 24, 43, 24, CUP)

    outline(img)
    # The steam, two wisps, drawn after the outline so that it stays thin and light.
    px(img, [(38, 16), (39, 15), (38, 14), (39, 13), (38, 12)], STEAM)
    px(img, [(42, 15), (41, 14), (42, 13), (41, 12), (42, 11)], STEAM)
    return img


def main():
    img = draw()
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    img.save(TEXTURE)
    OUT.mkdir(parents=True, exist_ok=True)
    scale = 10
    sheet = Image.new("RGBA", (W * scale + 40, H * scale + 40), (255, 255, 255, 255))
    sheet.alpha_composite(img.resize((W * scale, H * scale), Image.NEAREST), (20, 20))
    sheet.save(OUT / "congrats_observer_preview.png")
    print(f"wrote {TEXTURE}")


if __name__ == "__main__":
    main()
