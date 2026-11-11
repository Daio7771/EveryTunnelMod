"""
Top-down plan of Level 5's three layers, one over the other on the same plan coordinates (north up,
like the user's drawings), with a 10-block grid, each room's zone id, inner size and height, the
openings, the end of Level 4's dark stretch, layer 3's water, catwalks and collapse, and the stats
below. Run from tools/map:

    python3 -m lab.preview
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from lab.furnish import POINTS
from lab.level import OPEN_KINDS

# Per kind: fill, label, name.
KIND = {
    "entry": ((30, 30, 36), (120, 120, 132), (190, 190, 200)),
    "room": ((74, 78, 76), (172, 178, 174), (230, 235, 232)),
    "equipment": ((104, 76, 22), (226, 180, 90), (250, 214, 140)),
    "loot": ((26, 84, 74), (90, 196, 170), (160, 236, 214)),
    "lore": ((70, 54, 112), (170, 150, 230), (215, 200, 250)),
    "chamber": ((92, 50, 96), (190, 140, 200), (235, 205, 240)),
    "hub": ((32, 70, 100), (110, 175, 225), (175, 215, 245)),
    "puzzle": ((104, 76, 22), (226, 180, 90), (250, 214, 140)),
    "hatch": ((110, 52, 30), (230, 140, 100), (250, 190, 160)),
    "stairs": ((84, 70, 92), (180, 160, 190), (225, 210, 235)),
    "airlock": ((70, 76, 84), (170, 180, 192), (220, 228, 236)),
    "corridor": ((54, 56, 55), (150, 154, 152), (205, 208, 206)),
    "office": ((112, 84, 30), (236, 196, 110), (252, 226, 170)),
    "climb": ((84, 70, 92), (180, 160, 190), (225, 210, 235)),
}
STRETCH = (44, 44, 54)
WALL = (150, 150, 146)
OPENING = {"doorway": (110, 200, 120), "lab_door": (110, 200, 160), "locked": (226, 70, 70), "frame": (190, 196, 204), "airlock_rear": (150, 130, 240),
           "airlock_front": (150, 130, 240), "grate": (240, 160, 60), "door": (230, 200, 120), "director_door": (200, 150, 90), "exit": (90, 220, 120)}
WATER = (40, 90, 150)
DECK = (150, 156, 164)
RUBBLE = (120, 96, 70)
PIT = (10, 10, 12)
START = (90, 200, 255)
CHECKPOINT = (255, 255, 255)


def write(path, lv, lounge, stats, s=7):
    cells = [(x, z) for _, x, z in lv.floors]
    stretch = []
    sx0, sz0, sx1, sz1 = lounge.exit.box
    for x in range(sx0, sx1):
        for z in range(sz0, sz1):
            wx, wz = lounge.world(x, z)
            stretch.append((wz - lv.p["PLACE_Z"], lv.p["PLACE_X"] - wx))
    xs = [x for x, _ in cells + stretch]
    zs = [z for _, z in cells + stretch]
    minx, maxx, minz, maxz = min(xs) - 2, max(xs) + 2, min(zs) - 2, max(zs) + 2
    pad, gap, top = 34, 56, 40
    panel_w = (maxx - minx + 1) * s
    panel_h = (maxz - minz + 1) * s
    layers = (1, 2, 3)
    width = panel_w + 2 * pad
    height = top + len(layers) * (panel_h + gap) + pad + 18 * len(stats)
    font = ImageFont.load_default(size=14)
    small = ImageFont.load_default(size=11)
    title = ImageFont.load_default(size=17)
    img = Image.new("RGB", (width, height), (20, 20, 22))
    d = ImageDraw.Draw(img, "RGBA")

    for panel, layer in enumerate(layers):
        ox, oy = pad, top + panel * (panel_h + gap)

        def px(x, z):
            return ox + (x - minx) * s, oy + (z - minz) * s

        def cell(x, z, fill):
            X, Z = px(x, z)
            d.rectangle((X, Z, X + s - 1, Z + s - 1), fill=fill)

        floor_y = lv.p["FLOOR_Y"] + 1 + lv.layer_floor[layer]
        name = {1: "Layer 1 (upper floor, world y %d)", 2: "Layer 2 (lower floor, world y %d)",
                3: "Layer 3 (the drain, world y %d; the office up its stairs 10 higher)"}[layer] % floor_y
        d.text((ox, oy - 30), name, font=title, fill=(225, 225, 220))
        # The other layers, faint, to see what lies over or under.
        for other, x, z in lv.floors:
            if other != layer:
                cell(x, z, (34, 34, 38))
        own = {(x, z): f for (l, x, z), f in lv.floors.items() if l == layer}
        for x, z in own:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if (x + dx, z + dz) not in own:
                        cell(x + dx, z + dz, WALL)
        if layer == 1:
            for x, z in stretch:
                cell(x, z, STRETCH)
        for sp in lv.spaces + [lv.flight]:
            if sp.layer != layer and not (sp is lv.flight and layer < 3):
                continue
            for x, z in sp.cells():
                if sp is lv.flight:
                    depth = -own.get((x, z), lv.floors[(1, x, z)])
                    cell(x, z, (84 - depth * 3, 70 - depth * 3, 92 + depth * 6))
                elif sp.kind == "climb":
                    rise = lv.floors[(3, x, z)] - sp.floor
                    cell(x, z, (84 + rise * 4, 70 + rise * 3, 92 + rise * 5))
                else:
                    cell(x, z, KIND[sp.kind][0])
        for o in lv.openings:
            if o.layer == layer:
                for x, z in o.cells():
                    cell(x, z, OPENING[o.kind])
        if layer == 3:
            for x, z in lv.channel_cells:
                cell(x, z, WATER)
            for _, cells, decks, _, _ in lv.pools:
                for x, z in cells:
                    cell(x, z, DECK if (x, z) in decks else WATER)
            for x, z in lv.collapse_cells:
                cell(x, z, RUBBLE)
            for x, z in lv.pit_cells:
                cell(x, z, PIT)
            X, Z = px(lv.p["LADDER"][1], lv.p["LADDER"][2])
            d.rectangle((X, Z, X + s - 1, Z + s - 1), outline=(250, 220, 120), width=2)
        # A 10-block grid, numbered on the plan's coordinates.
        for x in range((minx // 10 + 1) * 10, maxx, 10):
            X, _ = px(x, 0)
            d.line((X, oy, X, oy + panel_h), fill=(255, 255, 255, 20))
            d.text((X + 2, oy - 14), str(x), font=small, fill=(140, 140, 140))
        for z in range((minz // 10 + 1) * 10, maxz, 10):
            _, Z = px(0, z)
            d.line((ox, Z, ox + panel_w, Z), fill=(255, 255, 255, 20))
            d.text((ox - 26, Z - 6), str(z), font=small, fill=(140, 140, 140))
        if layer == 1:
            X, Z = px(*lv.p["START"])
            d.ellipse((X - 3, Z - 3, X + s + 2, Z + s + 2), fill=START)
            X, Z = px(max(x for x, _ in stretch) + 1, min(z for _, z in stretch) + 3)
            d.text((X + 6, Z), "Level 4's dark stretch", font=small, fill=(170, 170, 185), anchor="lm")
        for point in [lv.p["CHECKPOINT_POINT"]] + [(l, x, z, f) for pid, l, x, z, f, tags in POINTS if "restart_point" in tags]:
            if point[0] == layer:
                X, Z = px(point[1], point[2])
                d.ellipse((X - 4, Z - 4, X + s + 3, Z + s + 3), outline=CHECKPOINT, width=2)
        # Labels: rooms with name, zone, size and height; corridors with their zone once.
        named = set()
        for sp in lv.spaces:
            if sp.layer != layer:
                continue
            x0, z0, x1, z1 = sp.box
            X0, Z0 = px(x0, z0)
            X1, Z1 = px(x1, z1)
            cx, cz = (X0 + X1) / 2, (Z0 + Z1) / 2
            colors = KIND[sp.kind]
            if sp.kind in OPEN_KINDS:
                if sp.zone in named:
                    continue
                named.add(sp.zone)
                text = f"{sp.name} ({sp.zone})" if sp.name else sp.zone
                if sp.kind == "entry":
                    d.text((cx, Z0 - 6), text, font=small, fill=colors[2], anchor="mb")
                else:
                    d.text((cx, cz), text, font=small, fill=colors[2], anchor="mm")
                continue
            w, h = sp.size
            big = font if (X1 - X0) >= 90 else small
            lines = [(sp.name, big, colors[2]), (sp.zone, small, colors[1]), (f"{w} x {h}, height {sp.height}", small, colors[1])]
            for i, (text, f, c) in enumerate(lines):
                if (X1 - X0) < 60:          # too small to hold its name: under it
                    d.text((cx, Z1 + 6 + i * 14), text, font=f, fill=c, anchor="mt")
                else:
                    d.text((cx, cz - 15 + i * 15), text, font=f, fill=c, anchor="mm")
        if layer == 1:
            x0, z0, x1, z1 = lv.flight.box
            X1, Z1 = px(x1, (z0 + z1) / 2)
            d.text((X1 + 6, Z1), "stairs down 10", font=small, fill=(200, 185, 215), anchor="lm")
    y = top + len(layers) * (panel_h + gap) - gap + 30
    for line in stats:
        d.text((pad, y), line, font=small, fill=(205, 205, 200))
        y += 18
    img.save(path)


if __name__ == "__main__":
    from lab import checks
    from lab.level import Lab, stretch_air
    from lounge.level import Lounge
    from map_generator import LEVEL_4, LEVEL_5

    lounge = Lounge(LEVEL_4).compose()
    level = Lab(LEVEL_5).compose(stretch_air(lounge, LEVEL_5))
    report = checks.walk(level) + checks.clear_ways(level) + checks.walled(level) + checks.stacked(level) + checks.joins_level_4(level, lounge)
    box = level.world_box()
    chunks = (box[3] // 16 - box[0] // 16 + 1) * (box[5] // 16 - box[2] // 16 + 1)
    l4 = lounge.box
    report += [
        f"World box {box}, {chunks} chunks to force-load for its build (at most 256). Level 4's box {l4}; Levels 1 to 4 lie at world x -64 and east.",
        f"Zones ({len(level.zone_ids())}): " + ", ".join(level.zone_ids()),
        "Green: open doorway. Red: locked door (stairs: the lever by the fuse box; south: on leaving the Office; shortcut: lever in the chemical room). "
        "Grey: the steel frame from Level 4's dark stretch. Violet: the airlock's sliding doors. Orange: the grate the puzzle dissolves.",
        "Layer 3: blue water (channels and pools), grey catwalks, brown the collapse, black the pit under the corridor's end, yellow square "
        "the ladder from the hatch; pale doors: the office's; bright green: Level 1's EXIT door.",
        "Blue dot: the start, through the frame from Level 4's dark stretch (top left). White rings: the restart points (Lab hall, Junction). Faint: the other layers.",
    ]
    out = Path(__file__).resolve().parent.parent / "output" / "level5_preview.png"
    write(out, level, lounge, report)
    print("\n".join(report))
    print(out)
