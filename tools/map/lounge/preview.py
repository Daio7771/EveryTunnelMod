"""
Top-down plan of Level 4 (plan coordinates, north up, like the user's drawing), with a 10-block
grid, each room's inner size and height, the openings and the stats below. Run from tools/map:

    python3 -m lounge.preview
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

KIND = {
    "service": ((60, 64, 68), (150, 158, 166), (215, 222, 230)),
    "piece": ((98, 56, 8), (232, 160, 60), (250, 200, 110)),
    "rest": ((10, 82, 66), (70, 190, 150), (140, 230, 200)),
    "key": ((112, 36, 62), (220, 110, 150), (245, 180, 205)),
    "neutral": ((70, 70, 66), (170, 170, 162), (225, 225, 215)),
    "corridor": ((52, 52, 50), None, None),
    "steps": ((88, 72, 92), None, None),
    "diplomas": ((70, 50, 34), (190, 140, 90), (235, 200, 160)),
    "exit": ((24, 24, 28), (110, 110, 120), (170, 170, 180)),
}
WALL = (150, 150, 146)
OPENING = {"arch": (120, 200, 120), "door": (230, 170, 80), "steel_door": (230, 170, 80), "big_door": (130, 120, 230)}
B4_DOOR = (230, 90, 80)
FURNITURE = (24, 24, 26)
RESERVED = (255, 255, 255)
START = (90, 200, 255)


def write(path, lv, stats, s=9):
    xs = [x for x, _ in lv.floors]
    zs = [z for _, z in lv.floors]
    minx, maxx, minz, maxz = min(xs) - 2, max(xs) + 2, min(zs) - 2, max(zs) + 2
    pad = 34
    width = (maxx - minx + 1) * s + 2 * pad
    height = (maxz - minz + 1) * s + 2 * pad + 20 + 18 * len(stats)
    font = ImageFont.load_default(size=15)
    small = ImageFont.load_default(size=12)
    img = Image.new("RGB", (width, height), (20, 20, 22))
    d = ImageDraw.Draw(img, "RGBA")

    def px(x, z):
        return pad + (x - minx) * s, pad + (z - minz) * s

    def cell(x, z, fill):
        X, Z = px(x, z)
        d.rectangle((X, Z, X + s - 1, Z + s - 1), fill=fill)

    # Walls: every cell beside a cell of air, diagonals too.
    for x, z in lv.floors:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                c = (x + dx, z + dz)
                if c not in lv.floors:
                    cell(c[0], c[1], WALL)
    for sp in lv.spaces:
        fill = KIND[sp.kind][0]
        x0, z0, x1, z1 = sp.box
        for x in range(x0, x1):
            for z in range(z0, z1):
                cell(x, z, fill)
    x0, z0, x1, z1 = lv.steps.box          # the steps: darker going down
    for x in range(x0, x1):
        shade = 60 * -lv.floors[(x, z0)]
        for z in range(z0, z1):
            cell(x, z, (88 + shade // 2, 72, 92 + shade))
    for x, z in lv.blocked:
        cell(x, z, WALL)
    for (x, z), name in lv.furnish.plan_cells().items():
        cell(x, z, FURNITURE)
    for x0, z0, x1, z1 in lv.furnish.RESERVED.values():
        a, b = px(x0, z0), px(x1, z1)
        d.rectangle((a[0], a[1], b[0] - 1, b[1] - 1), outline=RESERVED, width=2)
    for (x, z), colour in lv.furnish.dots():
        X, Z = px(x, z)
        d.ellipse((X + 1, Z + 1, X + s - 2, Z + s - 2), fill=colour)
    for x, z, _ in lv.p["PARTITION_DOORS"]:
        cell(x, z, OPENING["door"])
    for _, axis, line, first, w, _, kind in lv.openings:
        for k in range(first, first + w):
            cell(*((line, k) if axis == "x" else (k, line)), OPENING[kind])
    cell(*lv.p["B4_DOOR"], B4_DOOR)
    # A 10-block grid, numbered on the plan's coordinates.
    for x in range((minx // 10 + 1) * 10, maxx, 10):
        X, _ = px(x, 0)
        d.line((X, pad, X, pad + (maxz - minz + 1) * s), fill=(255, 255, 255, 22))
        d.text((X + 2, pad - 16), str(x), font=small, fill=(150, 150, 150))
    for z in range((minz // 10 + 1) * 10, maxz, 10):
        _, Z = px(0, z)
        d.line((pad, Z, pad + (maxx - minx + 1) * s, Z), fill=(255, 255, 255, 22))
        d.text((4, Z - 6), str(z), font=small, fill=(150, 150, 150))
    sx, sz = lv.p["START"]
    X, Z = px(sx, sz)
    d.ellipse((X - 3, Z - 3, X + s + 2, Z + s + 2), fill=START)
    # Labels in the rooms.
    for sp in lv.rooms + [lv.diplomas, lv.exit]:
        x0, z0, x1, z1 = sp.box
        X0, Z0 = px(x0, z0)
        X1, Z1 = px(x1, z1)
        cx, cz = (X0 + X1) / 2, (Z0 + Z1) / 2
        w, h = sp.size
        colors = KIND[sp.kind]
        narrow = (X1 - X0) < 110
        if narrow:
            d.text((X1 + 8, cz - 16), sp.name, font=font, fill=colors[2], anchor="lm")
            d.text((X1 + 8, cz + 2), f"{w} x {h}, height {sp.height}", font=small, fill=colors[1], anchor="lm")
        else:
            d.text((cx, cz - 10), sp.name, font=font, fill=colors[2], anchor="mm")
            d.text((cx, cz + 9), f"{w} x {h}, height {sp.height}", font=small, fill=colors[1], anchor="mm")
    y = pad + (maxz - minz + 1) * s + 12
    for line in stats:
        d.text((pad, y), line, font=small, fill=(205, 205, 200))
        y += 18
    img.save(path)


if __name__ == "__main__":
    from lounge import checks
    from lounge.level import Lounge
    from map_generator import LEVEL_4

    level = Lounge(LEVEL_4).compose()
    report = checks.walled(level) + checks.steps(level) + checks.walk(level)
    box = level.world_box()
    chunks = (box[3] // 16 - box[0] // 16 + 1) * (box[5] // 16 - box[2] // 16 + 1)
    report += [
        f"World box {box}, {chunks} chunks. Reception's door {level.world(50, 84)}, door to B4 {level.world(*LEVEL_4['B4_DOOR'])}",
        "Green: open arch. Orange: normal door. Violet: the big door. Red: the door to B4. Blue dot: the checkpoint.",
        "Black: furniture. White boxes: gaps for the puzzles. Dots: white notes, yellow batteries, cyan water, red food.",
    ]
    out = Path(__file__).resolve().parent.parent / "output" / "level4_preview.png"
    write(out, level, report)
    print("\n".join(report))
    print(out)
