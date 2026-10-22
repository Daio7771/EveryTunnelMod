"""
The HUD's icons (client/hud/StatusHud.kt): 9x9 vanilla-style icons in the cold palette of the
art direction, outlined in near black, lit from the top left.

Run after changing anything here (from the repository root):   python3 tools/gui/hud_icons.py

Writes src/main/resources/assets/every_tunnel/textures/gui/hud_icons.png (64x36, committed)
and tools/gui/output/hud_icons_preview.png (git-ignored; enlarged, on a dark background).
Also writes textures/gui/vignette.png: black, transparent in the middle and darker towards
the edges, for the exhaustion vignette (client/hud/ExhaustionVignette.kt).

Layout (what StatusHud expects), icons at (u, v):
- rows: v=0 stamina (lungs), v=9 thirst (water drop), v=18 battery, v=27 health (heart)
- u=0 empty container, u=9 full, u=18 half (the left half only, drawn over the container)
- u=27/36/45: the same three in an alternate state (stamina: exhausted, battery: low)
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
    img = Image.new("RGBA", (64, 36), (0, 0, 0, 0))
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
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    img.save(TEXTURE)

    OUT.mkdir(exist_ok=True)
    big = img.resize((64 * 10, 36 * 10), Image.NEAREST)
    preview = Image.new("RGBA", big.size, (34, 36, 40, 255))
    preview.alpha_composite(big)
    preview.save(OUT / "hud_icons_preview.png")
    print(f"wrote {TEXTURE.relative_to(ROOT)} and {(OUT / 'hud_icons_preview.png').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
