"""
The achievement icons that are not an item (client/AchievementIcons.kt): 16x16, item-style,
outlined in near black and lit from the top left, so they sit beside the item icons both on
the toast's dark frame and on the ACHIEVEMENTS screen's paper.

Run after changing anything here (from the repository root):   python3 tools/gui/achievement_icons.py

Writes src/main/resources/assets/every_tunnel/textures/gui/achievement_icons.png (64x16,
committed) and tools/gui/output/achievement_icons_preview.png (git-ignored; enlarged, on the
toast's grey and on paper).

Icons at u (what AchievementIcons expects):
- u=0: locked, a grey "?" on a dark tile.
- u=16: the guard's badge (First Day at Work): a gold shield with the uniform's black and a star.
- u=32: a skull (Ouch!), bone white with dark sockets.
- u=48: the Pale man's silhouette (Beware of Tall Men): tall, thin, arms down to his knees.

Each icon is a 16x16 mask: '#' outline, 'o' fill, 'h' highlight, 's' shade, 'k' dark, 'y' its
mark (light), '.' transparent.
"""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
TEXTURE = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui" / "achievement_icons.png"
OUT = Path(__file__).resolve().parent / "output"

OUTLINE = (14, 15, 19, 255)

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
]


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
