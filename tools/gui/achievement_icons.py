"""
The achievement icons that are not an item (client/AchievementIcons.kt): 16x16, item-style,
outlined in near black and lit from the top left, so they sit beside the item icons both on
the toast's dark frame and on the ACHIEVEMENTS screen's paper.

Run after changing anything here (from the repository root):   python3 tools/gui/achievement_icons.py

Writes src/main/resources/assets/every_tunnel/textures/gui/achievement_icons.png (272x16,
committed) and tools/gui/output/achievement_icons_preview.png (git-ignored; enlarged, on the
toast's grey and on paper).

Icons at u (what AchievementIcons expects):
- u=0: locked, a grey "?" on a dark tile.
- u=16: the guard's badge (First Day at Work): a gold shield with the uniform's black and a star.
- u=32: a skull (Ouch!), bone white with dark sockets.
- u=48: the Pale man's silhouette (Beware of Tall Men): tall, thin, arms down to his knees.
- u=64: a puzzle piece (Big Brain), warm orange.
- u=80: a brass padlock (Privacy Policy).
- u=96: the Director's mask, an ivory face with slit eyes and a thin smile (Nothing in There).
- u=112 on, Chapter 2's ten (`AchievementDef.icon` "sprite:<u / 16>", in `chapter2.core.Ch2Achievement`'s order): a time clock, a badge,
  an eye, a record, a pale face, an hourglass, a "?" with ripples, a tear, a compass and a target missed by a pipe.

Each icon is a 16x16 mask: '#' outline, 'o' fill, 'h' highlight, 's' shade, 'k' dark, 'y' its
mark (light), '.' transparent.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui" / "achievement_icons.png"
OUT = Path(__file__).resolve().parent / "output"

OUTLINE = (14, 15, 19, 255)


def mask_rows():
    """The Director's mask: an ivory oval, lit from the top left, with slanted slit eyes and a thin smile."""
    inside = lambda x, y: ((x - 7.5) / 6.3) ** 2 + ((y - 7.5) / 7.2) ** 2 <= 1.0
    grid = [["." for _ in range(16)] for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if inside(x, y):
                edge = not all(inside(nx, ny) for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
                grid[y][x] = "#" if edge else ("h" if x + y < 12 else ("s" if x + y > 17 else "o"))
    for x, y in [(3, 5), (4, 6), (5, 6), (6, 5), (9, 5), (10, 6), (11, 6), (12, 5),
                 (4, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (11, 10)]:
        grid[y][x] = "k"
    return ["".join(row) for row in grid]


ICONS = [
    ("locked", {
        "o": (62, 66, 76, 255), "h": (84, 89, 100, 255), "s": (44, 47, 54, 255), "y": (176, 182, 192, 255),
    }, [
        "................",
        ".##############.",
        ".#hhhhhhhhhhhs#.",
        ".#hoooyyyyooos#.",
        ".#hooyyooyyoos#.",
        ".#hooooooyyoos#.",
        ".#hoooooyyooos#.",
        ".#hooooyyoooos#.",
        ".#hooooyyoooos#.",
        ".#hoooooooooos#.",
        ".#hooooyyoooos#.",
        ".#hooooyyoooos#.",
        ".#hoooooooooos#.",
        ".#ssssssssssss#.",
        ".##############.",
        "................",
    ]),
    ("badge", {
        "o": (206, 164, 48, 255), "h": (246, 214, 104, 255), "s": (146, 106, 26, 255), "k": (26, 26, 30, 255),
        "y": (238, 200, 70, 255),
    }, [
        "................",
        ".....######.....",
        "...##hhhhhh##...",
        "..#hhoooooooo#..",
        "..#hkkkkkkkks#..",
        "..#hkkkyykkks#..",
        "..#hkkkyykkks#..",
        "..#hkyyyyyyks#..",
        "..#hkkyyyykks#..",
        "..#hkkyykyyks#..",
        "...#hkykkkys#...",
        "...#hkkkkkks#...",
        "....#hoooos#....",
        ".....#soos#.....",
        "......####......",
        "................",
    ]),
    ("skull", {
        "o": (214, 210, 196, 255), "h": (240, 238, 228, 255), "s": (150, 146, 136, 255), "k": (26, 26, 30, 255),
    }, [
        "................",
        "....########....",
        "...#hhhhhhhh#...",
        "..#hhoooooooo#..",
        "..#hooooooooo#..",
        "..#okkkookkko#..",
        "..#okkkookkko#..",
        "..#hokkookkos#..",
        "..#hoooooooos#..",
        "..#soookkooos#..",
        "...#ssooooss#...",
        "...#okokokok#...",
        "...#soooooos#...",
        "....########....",
        "................",
        "................",
    ]),
    ("pale_man", {
        "o": (206, 204, 196, 255), "h": (232, 230, 224, 255), "s": (150, 150, 146, 255),
    }, [
        "......####......",
        ".....#hhoo#.....",
        ".....#hoos#.....",
        "......#os#......",
        "....##hoos##....",
        "...#hoooooos#...",
        "...#o#hoos#o#...",
        "...#o#hoos#o#...",
        "...#o#hoos#o#...",
        "...#o#hoss#o#...",
        "...#o#o##o#o#...",
        "...#o#o##o#o#...",
        "..#oo#o##o#oo#..",
        "..####o##o####..",
        ".....#o##o#.....",
        ".....##..##.....",
    ]),
    ("puzzle", {
        "o": (214, 150, 60, 255), "h": (240, 190, 100, 255), "s": (150, 96, 34, 255),
    }, [
        "................",
        "......####......",
        ".....#hhoo#.....",
        ".....#hoos#.....",
        ".#####hoos#####.",
        ".#hhhoooooooos#.",
        ".#hoooooooooos#.",
        ".###hooooooooos#",
        "...#hooooooooos#",
        ".###hooooooooos#",
        ".#hoooooooooos#.",
        ".#hoooooooooos#.",
        ".#ssssssssssss#.",
        ".##############.",
        "................",
        "................",
    ]),
    ("padlock", {
        "o": (186, 146, 74, 255), "h": (228, 192, 116, 255), "s": (120, 88, 40, 255), "k": (34, 26, 16, 255),
    }, [
        "................",
        ".....######.....",
        "....#hoooos#....",
        "...#hs####os#...",
        "...#o#....#o#...",
        "...#o#....#o#...",
        "..############..",
        "..#hhhhhhhhhs#..",
        "..#hoookkooos#..",
        "..#hoookkooos#..",
        "..#hooookooos#..",
        "..#hooookooos#..",
        "..#hoooooooos#..",
        "..#ssssssssss#..",
        "..############..",
        "................",
    ]),
    ("mask", {
        "o": (226, 222, 208, 255), "h": (248, 246, 238, 255), "s": (160, 156, 142, 255), "k": (26, 26, 30, 255),
    }, mask_rows()),
]

# --- Chapter 2's ten (drawn as shapes, then outlined) --------------------------------------------------------------

def canvas():
    return [["." for _ in range(16)] for _ in range(16)]


def put(g, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        g[y][x] = c


def fill(g, inside, c):
    for y in range(16):
        for x in range(16):
            if inside(x, y):
                g[y][x] = c


def disc(g, cx, cy, r, c):
    fill(g, lambda x, y: (x - cx) ** 2 + (y - cy) ** 2 <= r * r, c)


def lit(g, colours, light="h", shade="s"):
    """Lights the top left of every cell of the [colours] and shades the bottom right, by the diagonal through the shape's middle."""
    cells = [(x, y) for y in range(16) for x in range(16) if g[y][x] in colours]
    if not cells:
        return
    mid = sum(x + y for x, y in cells) / len(cells)
    for x, y in cells:
        if g[y][x] != colours[0]:
            continue
        d = x + y - mid
        g[y][x] = light if d < -3 else (shade if d > 3 else colours[0])


def outlined(g):
    out = [row[:] for row in g]
    for y in range(16):
        for x in range(16):
            if g[y][x] == ".":
                near = ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))
                if any(0 <= nx < 16 and 0 <= ny < 16 and g[ny][nx] != "." for nx, ny in near):
                    out[y][x] = "#"
    return ["".join(row) for row in out]


def clock_rows():
    g = canvas()
    disc(g, 7.5, 7.5, 6.3, "o")
    lit(g, "o")
    for x, y in [(7, 3), (8, 3), (3, 7), (3, 8), (12, 7), (12, 8), (7, 12), (8, 12)]:
        put(g, x, y, "y")
    for y in range(5, 9):
        put(g, 7, y, "k")
        put(g, 8, y, "k")
    for x in range(9, 12):
        put(g, x, 8, "k")
        put(g, x, 7, "k")
    return outlined(g)


def eye_rows():
    g = canvas()
    fill(g, lambda x, y: ((x - 7.5) / 7.0) ** 2 + ((y - 7.5) / 4.4) ** 2 <= 1.0, "o")
    lit(g, "o")
    disc(g, 7.5, 7.5, 3.1, "y")
    disc(g, 7.5, 7.5, 1.6, "k")
    put(g, 6, 6, "h")
    return outlined(g)


def record_rows():
    g = canvas()
    disc(g, 7.5, 7.5, 7.0, "o")
    fill(g, lambda x, y: 4.6 <= ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5 <= 5.4, "h")
    disc(g, 7.5, 7.5, 2.6, "y")
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        put(g, x, y, "k")
    for x, y in [(3, 3), (4, 2), (11, 12), (12, 11)]:
        put(g, x, y, "s")
    return outlined(g)


def pale_face_rows():
    g = canvas()
    fill(g, lambda x, y: ((x - 7.5) / 5.0) ** 2 + ((y - 7.5) / 7.2) ** 2 <= 1.0, "o")
    lit(g, "o")
    for x, y in [(5, 5), (6, 5), (5, 6), (6, 6), (5, 7), (6, 7), (9, 5), (10, 5), (9, 6), (10, 6), (9, 7), (10, 7)]:
        put(g, x, y, "k")
    put(g, 6, 6, "w")
    put(g, 10, 6, "w")
    for x in (7, 8):
        put(g, x, 11, "s")
    return outlined(g)


def tear_rows():
    g = canvas()
    disc(g, 7.5, 9.5, 4.6, "o")
    for y, half in zip(range(1, 7), (0.6, 1.1, 1.7, 2.4, 3.2, 3.9)):
        for x in range(16):
            if abs(x - 7.5) <= half:
                g[y][x] = "o"
    lit(g, "o")
    for x, y in [(5, 9), (5, 10), (6, 8)]:
        put(g, x, y, "w")
    return outlined(g)


def compass_rows():
    g = canvas()
    disc(g, 7.5, 7.5, 6.6, "o")
    lit(g, "o")
    disc(g, 7.5, 7.5, 4.7, "w")
    for t in range(5):
        for dx in (0, 1):
            put(g, 8 + t - 1 + dx, 8 - t, "r")
            put(g, 7 - t + 1 - dx, 7 + t, "k")
    put(g, 7, 7, "y")
    put(g, 8, 8, "y")
    return outlined(g)


def target_missed_rows():
    g = canvas()
    disc(g, 5, 5, 4.7, "r")
    disc(g, 5, 5, 3.3, "w")
    disc(g, 5, 5, 2.0, "r")
    disc(g, 5, 5, 0.8, "w")
    for y in range(16):
        for x in range(16):
            d = x + y
            if 7 <= x <= 14 and 7 <= y <= 14 and 21 <= d <= 23:
                g[y][x] = "h" if d == 21 else ("o" if d == 22 else "s")
    return outlined(g)


HOURGLASS = [
    "................",
    ".##############.",
    ".#bbbbbbbbbbbb#.",
    ".##############.",
    "...#gyyyyyyg#...",
    "....#gyyyyg#....",
    ".....#gyyg#.....",
    "......#yy#......",
    "......#yy#......",
    ".....#g..g#.....",
    "....#g.yy.g#....",
    "...#g.yyyy.g#...",
    "..#gyyyyyyyyg#..",
    ".##############.",
    ".#bbbbbbbbbbbb#.",
    ".##############.",
]

QUESTION = [
    "................",
    "................",
    ".....######.....",
    "....#yyyyyy#....",
    "...#yy####yy#...",
    "...#yy#..#yy#...",
    "....##...#yy#...",
    "......#.#yy#....",
    ".....#yyyy#.....",
    ".....#yy##......",
    ".....#yy#.......",
    "......##........",
    ".....#yy#.......",
    ".....#yy#.......",
    "......##........",
    "................",
]

CHAPTER_2 = [
    ("clock", {"o": (232, 228, 214, 255), "h": (252, 250, 240, 255), "s": (170, 166, 152, 255), "k": (26, 26, 30, 255), "y": (200, 50, 44, 255)}, clock_rows()),
    ("badge", {"o": (96, 150, 206, 255), "h": (140, 190, 236, 255), "s": (58, 98, 148, 255), "k": (26, 34, 52, 255), "y": (240, 240, 236, 255)}, [
        "................",
        "......####......",
        ".....#oooo#.....",
        ".#####o##o#####.",
        ".#hhhhhhhhhhhhs#",
        ".#hooooooooooos#",
        ".#hkkkkoyyyyyos#",
        ".#hkkkkooooooos#",
        ".#hkkkkoyyyyyos#",
        ".#hkkkkooooooos#",
        ".#hooooooyyyyos#",
        ".#hooooooooooos#",
        ".#ssssssssssss#.",
        ".##############.",
        "................",
        "................",
    ]),
    ("eye", {"o": (240, 238, 232, 255), "h": (255, 255, 252, 255), "s": (176, 172, 164, 255), "k": (20, 20, 24, 255), "y": (196, 48, 48, 255)}, eye_rows()),
    ("record", {"o": (52, 54, 64, 255), "h": (96, 100, 116, 255), "s": (30, 32, 40, 255), "k": (12, 12, 16, 255), "y": (150, 70, 190, 255)}, record_rows()),
    ("pale_face", {"o": (214, 216, 218, 255), "h": (240, 242, 244, 255), "s": (146, 150, 156, 255), "k": (16, 16, 20, 255), "w": (255, 255, 255, 255)}, pale_face_rows()),
    ("hourglass", {"b": (150, 100, 52, 255), "g": (196, 228, 244, 255), "y": (232, 190, 84, 255)}, HOURGLASS),
    ("question", {"y": (150, 214, 236, 255)}, QUESTION),
    ("tear", {"o": (70, 140, 222, 255), "h": (130, 190, 248, 255), "s": (38, 88, 160, 255), "w": (232, 244, 255, 255)}, tear_rows()),
    ("compass", {"o": (200, 158, 70, 255), "h": (238, 204, 122, 255), "s": (136, 100, 40, 255), "w": (244, 238, 220, 255), "r": (204, 52, 44, 255), "k": (40, 40, 48, 255), "y": (200, 158, 70, 255)}, compass_rows()),
    ("target_missed", {"r": (206, 56, 48, 255), "w": (244, 240, 232, 255), "h": (186, 190, 198, 255), "o": (132, 138, 150, 255), "s": (84, 90, 104, 255)}, target_missed_rows()),
]

ICONS += CHAPTER_2


def icon(palette, mask):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    for y, row in enumerate(mask):
        for x, c in enumerate(row):
            if c == "#":
                px[x, y] = OUTLINE
            elif c != ".":
                px[x, y] = palette[c]
    return image


def main():
    sheet = Image.new("RGBA", (16 * len(ICONS), 16), (0, 0, 0, 0))
    for i, (_, palette, mask) in enumerate(ICONS):
        if len(mask) != 16 or any(len(row) != 16 for row in mask):
            raise SystemExit(f"icon {i}: the mask must be 16 rows of 16")
        sheet.alpha_composite(icon(palette, mask), (16 * i, 0))
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(TEXTURE)

    OUT.mkdir(exist_ok=True)
    scale = 8
    w, h = sheet.width * scale, 16 * scale
    preview = Image.new("RGBA", (w, h * 2), (0, 0, 0, 255))
    preview.paste((33, 33, 33, 255), (0, 0, w, h))            # the toast's frame
    preview.paste((226, 220, 204, 255), (0, h, w, h * 2))     # the screen's paper
    big = sheet.resize((w, h), Image.NEAREST)
    preview.alpha_composite(big, (0, 0))
    preview.alpha_composite(big, (0, h))
    preview.save(OUT / "achievement_icons_preview.png")
    print(f"achievement_icons.png {sheet.width}x16; preview in {OUT / 'achievement_icons_preview.png'}")


if __name__ == "__main__":
    main()
