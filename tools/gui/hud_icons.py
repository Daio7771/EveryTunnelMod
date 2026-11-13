"""
The HUD's icons (client/hud/StatusHud.kt): 9x9 vanilla-style icons in the cold palette of the
art direction, outlined in near black, lit from the top left.

Run after changing anything here (from the repository root):   python3 tools/gui/hud_icons.py

Writes src/main/resources/assets/every_tunnel/textures/gui/hud_icons.png (81x36, committed)
and tools/gui/output/hud_icons_preview.png (git-ignored; enlarged, on a dark background).
Also writes textures/gui/vignette.png: black, transparent in the middle and darker towards
the edges, for the exhaustion vignette (client/hud/ExhaustionVignette.kt), and
textures/gui/detector_icons.png (39x13) for Level 5's detector (client/hud/DetectorHud.kt):
at u=0 the radiation sign greyed out, at u=13 the same in yellow and black (drawn over the grey
from the bottom up, as far as the reading goes), at u=26 the red warning triangle; and
textures/gui/turn_icon.png (32x32), the turn round sign of Level 5's entry corridor
(client/effects/DistortionEffect.kt).

Layout (what StatusHud expects), icons at (u, v):
- rows: v=0 stamina (lungs), v=9 thirst (water drop), v=18 battery, v=27 health (heart)
- u=0 empty container, u=9 full, u=18 half (the left half only, drawn over the container)
- u=27/36/45: the same three in an alternate state (stamina: exhausted, battery: low)
- u=54/63/72: the stamina row again, gold, while a food's energy lasts (a row of its own: GOLD)
Full and half icons only contain their filled pixels.

Each icon is a 9x9 mask: '#' outline, 'o' fill, 'h' highlight, 's' shade, '.' transparent.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui" / "hud_icons.png"
OUT = Path(__file__).resolve().parent / "output"

OUTLINE = (14, 15, 19, 255)
CONTAINER = (44, 47, 54, 255)

MASKS = {
    "lungs": [
        "...###...",
        "...#o#...",
        ".###o###.",
        "#hho#ohh#",
        "#hoo#ooo#",
        "#ooo#ooo#",
        "#oos#soo#",
        ".#s#.#s#.",
        "..#...#..",
    ],
    "drop": [
        "....#....",
        "...#h#...",
        "..#hoo#..",
        ".#hooos#.",
        "#hooooos#",
        "#hoooooo#",
        "#oooooos#",
        ".#sssss#.",
        "..#####..",
    ],
    "battery": [
        ".........",
        ".........",
        "########.",
        "#hhhhhh#.",
        "#oooooo##",
        "#oooooo##",
        "#ssssss#.",
        "########.",
        ".........",
    ],
    "heart": [
        ".##...##.",
        "#hh#.#ho#",
        "#hoo#ooo#",
        "#ooooooo#",
        ".#oooos#.",
        "..#oos#..",
        "...#s#...",
        "....#....",
        ".........",
    ],
}

# (fill, highlight, shade) per icon and state
COLOURS = {
    ("lungs", False): ((176, 200, 214), (232, 244, 250), (120, 140, 156)),
    ("lungs", True): ((96, 100, 108), (130, 134, 140), (70, 72, 78)),          # exhausted
    ("drop", False): ((70, 150, 196), (170, 224, 240), (40, 98, 140)),
    ("battery", False): ((120, 206, 150), (196, 246, 210), (70, 146, 100)),    # sickly fluorescent green
    ("battery", True): ((206, 60, 56), (246, 140, 130), (150, 34, 34)),        # low: blinks red
    ("heart", False): ((176, 38, 52), (232, 120, 128), (120, 22, 34)),
}
ROWS = {"lungs": 0, "drop": 9, "battery": 18, "heart": 27}

# The stamina row while a food's energy lasts: (fill, highlight, shade) and the container's outline
GOLD = ((255, 206, 64), (255, 240, 150), (206, 140, 24))
GOLD_OUTLINE = (150, 100, 12, 255)
GOLD_U = 54


def draw(img, u, v, mask, part, colours, alternate_outline=None):
    fill, high, shade = colours
    paint = {"o": fill, "h": high, "s": shade}
    for y, row in enumerate(mask):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if part == "container":
                c = (alternate_outline or OUTLINE) if ch == "#" else CONTAINER
            else:
                if ch == "#" or (part == "half" and x > 4):
                    continue
                c = (*paint[ch], 255)
            img.putpixel((u + x, v + y), c)


# The radiation sign, 13x13: three blades fanning out from a dot, on a disc lit from the top left
# ('h' its lit rim, 'y' the disc, 's' its shaded rim, 'b' the sign, '#' outline).
RADIATION = [
    "....#####....",
    "...#hhhhy#...",
    "..#hbbbbby#..",
    ".#hyybbbyyy#.",
    "#hyyyyyyyyys#",
    "#hyyyybyyyys#",
    "#hbbybbbybbs#",
    "#ybbbybybbbs#",
    "#ybbbyyybbbs#",
    ".#ybbyyybbs#.",
    "..#yyyyyss#..",
    "...#sssss#...",
    "....#####....",
]
RADIATION_GREY = {"h": (152, 154, 160, 255), "y": (112, 114, 120, 255), "s": (82, 84, 90, 255), "b": (46, 48, 52, 255)}
RADIATION_LIT = {"h": (255, 228, 110, 255), "y": (242, 192, 34, 255), "s": (194, 138, 18, 255), "b": (24, 22, 20, 255)}

# The warning triangle, 13 wide: lit on its left, shaded on its right and foot, a white "!".
TRIANGLE = [
    "......#......",
    ".....#h#.....",
    ".....#h#.....",
    "....#hrr#....",
    "....#hwr#....",
    "...#hrwrd#...",
    "...#hrwrd#...",
    "..#hrrwrrd#..",
    "..#hrrrrrd#..",
    ".#hrrrwrrrd#.",
    ".#ddddddddd#.",
    "#############",
]
TRIANGLE_COLOURS = {"h": (246, 110, 96, 255), "r": (214, 46, 40, 255), "d": (150, 24, 24, 255), "w": (250, 240, 232, 255)}


def paint_mask(img, rows, colours, u, v):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((u + x, v + y), OUTLINE if ch == "#" else colours[ch])


def detector_icons():
    img = Image.new("RGBA", (39, 13), (0, 0, 0, 0))
    paint_mask(img, RADIATION, RADIATION_GREY, 0, 0)
    paint_mask(img, RADIATION, RADIATION_LIT, 13, 0)
    paint_mask(img, TRIANGLE, TRIANGLE_COLOURS, 26, 1)
    return img


def turn_icon(size=32):
    """The turn round sign of Level 5's entry corridor (client/effects/DistortionEffect.kt): a circle arrow going
    clockwise, pale violet in a dark outline, its head at the top pointing on round."""
    import math
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2
    r0, r1 = size * 0.27, size * 0.39
    gap = (50.0, 110.0)                                  # degrees, counterclockwise from the right, y up
    mid = (r0 + r1) / 2
    head = math.radians(gap[1])
    hx, hy = c + mid * math.cos(head), c - mid * math.sin(head)
    tx, ty = math.sin(head), math.cos(head)              # the clockwise tangent there, in image coordinates
    nx, ny = math.cos(head), -math.sin(head)             # outwards
    tip = (hx + tx * size * 0.2, hy + ty * size * 0.2)
    left = (hx + nx * size * 0.17, hy + ny * size * 0.17)
    right = (hx - nx * size * 0.17, hy - ny * size * 0.17)

    def in_head(x, y):
        def side(a, b):
            return (b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0])
        s1, s2, s3 = side(left, tip), side(tip, right), side(right, left)
        return (s1 >= 0 and s2 >= 0 and s3 >= 0) or (s1 <= 0 and s2 <= 0 and s3 <= 0)

    filled = set()
    for y in range(size):
        for x in range(size):
            px, py = x, y
            r = math.hypot(px - c, py - c)
            angle = math.degrees(math.atan2(c - py, px - c)) % 360
            if (r0 <= r <= r1 and not gap[0] < angle < gap[1]) or in_head(px, py):
                filled.add((x, y))
    for x, y in filled:
        shade = 0 if (x - c) + (y - c) < 0 else 1
        img.putpixel((x, y), (236, 222, 255, 255) if shade == 0 else (206, 184, 246, 255))
    for x, y in filled:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in filled and 0 <= q[0] < size and 0 <= q[1] < size:
                    img.putpixel(q, (28, 8, 40, 255))
    return img


def vignette(size=256):
    """Black with an alpha that is 0 inside a soft oval and rises to full at the corners."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = (((x - c) / c) ** 2 + ((y - c) / c) ** 2) ** 0.5 / 2 ** 0.5     # 0 centre .. 1 corner
            t = min(1.0, max(0.0, (r - 0.38) / 0.62))
            img.putpixel((x, y), (0, 0, 0, int(255 * t * t * (3 - 2 * t))))
    return img


def main():
    vignette().save(TEXTURE.parent / "vignette.png")
    detector_icons().save(TEXTURE.parent / "detector_icons.png")
    turn_icon().save(TEXTURE.parent / "turn_icon.png")
    img = Image.new("RGBA", (81, 36), (0, 0, 0, 0))
    for name, v in ROWS.items():
        for alternate in (False, True):
            colours = COLOURS.get((name, alternate))
            if colours is None:
                continue
            u0 = 27 if alternate else 0
            outline = (110, 22, 22, 255) if (name, alternate) == ("battery", True) else None
            draw(img, u0, v, MASKS[name], "container", colours, outline)
            draw(img, u0 + 9, v, MASKS[name], "full", colours)
            draw(img, u0 + 18, v, MASKS[name], "half", colours)
    draw(img, GOLD_U, ROWS["lungs"], MASKS["lungs"], "container", GOLD, GOLD_OUTLINE)
    draw(img, GOLD_U + 9, ROWS["lungs"], MASKS["lungs"], "full", GOLD)
    draw(img, GOLD_U + 18, ROWS["lungs"], MASKS["lungs"], "half", GOLD)
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    img.save(TEXTURE)

    OUT.mkdir(exist_ok=True)
    big = img.resize((81 * 10, 36 * 10), Image.NEAREST)
    preview = Image.new("RGBA", big.size, (34, 36, 40, 255))
    preview.alpha_composite(big)
    preview.save(OUT / "hud_icons_preview.png")
    print(f"wrote {TEXTURE.relative_to(ROOT)} and {(OUT / 'hud_icons_preview.png').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
