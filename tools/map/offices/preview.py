"""
Top-down plan of the built Level 3, read from its blocks (plan coordinates, north up, like
the level's drawing): the main floor on the left, the bunker under the library on the right
at the same scale, a 10-block grid, the stats below.
"""

from PIL import Image, ImageDraw, ImageFont

KIND_COLORS = {"office": (22, 72, 64), "service": (40, 58, 98), "corridor": (62, 64, 70),
               "library": (58, 50, 104), "door": (96, 98, 104), "bunker": (110, 96, 58)}
WALL = (150, 150, 146)
FURNITURE_COLORS = {
    "bookshelf": (120, 84, 56), "reading_table": (150, 110, 74), "wooden_chair": (110, 80, 54),
    "library_counter": (150, 110, 74), "card_catalog": (120, 84, 56),
    "office_desk": (206, 182, 140), "meeting_table": (206, 182, 140), "counter": (206, 182, 140),
    "office_chair": (60, 62, 70), "cafe_chair": (140, 150, 160), "cafe_table": (230, 230, 226),
    "filing_cabinet": (176, 180, 184), "metal_shelf": (176, 180, 184), "server_rack": (30, 32, 34),
    "locker": (120, 132, 148), "bench": (206, 182, 140), "sofa": (80, 90, 110), "vending_machine": (170, 48, 42),
    "water_dispenser": (120, 170, 210), "potted_plant": (70, 140, 70), "cardboard_box": (168, 130, 80),
    "box_shelf": (200, 150, 236),
}
BOX_SPOT = (200, 70, 255)
DOOR = (170, 110, 60)


def write(path, lv, stats, s=4):
    """`s`: pixels per block (the furniture close-up uses a bigger one)."""
    p = lv.p
    w = lv.world.blocks
    xs = [x for x, _, _ in lv.air]
    zs = [z for _, _, z in lv.air]
    minx, maxx, minz, maxz = min(xs) - 3, max(xs) + 3, min(zs) - 3, max(zs) + 3
    pad, gap = 30, 60
    bx0, bz0, bx1, bz1 = lv.bunker_box
    bminx, bmaxx = bx0 - 6, bx1 + 6
    main_w = (maxx - minx + 1) * s
    bunker_w = (bmaxx - bminx + 1) * s
    text_h = 30 + 16 * len(stats)
    font = ImageFont.load_default(size=11)
    small = ImageFont.load_default(size=9)
    text_w = max(ImageDraw.Draw(Image.new("RGB", (1, 1))).textlength(line, font=font) for line in stats)
    width = max(main_w + bunker_w + gap + 140, int(text_w)) + 2 * pad
    img = Image.new("RGB", (width, (maxz - minz + 1) * s + 2 * pad + text_h + 20), (16, 17, 20))
    d = ImageDraw.Draw(img, "RGBA")

    def rect(ox, x, z):
        X, Z = ox + (x - (minx if ox == pad else bminx)) * s, pad + (z - minz) * s
        return (X, Z, X + s - 1, Z + s - 1)

    def draw_layer(ox, xr, feet):
        for x in xr:
            for z in range(minz, maxz + 1):
                y = feet(x, z)
                if y is None:
                    continue
                a = lv.air.get((x, y, z))
                body = w.get((x, y, z)) or ""
                if "_door" in body:
                    d.rectangle(rect(ox, x, z), fill=DOOR)
                elif a is not None and (x, y, z) not in w or "ladder" in body:
                    d.rectangle(rect(ox, x, z), fill=KIND_COLORS[a[0]])
                elif (x, y, z) in w or any((x + dx, y, z + dz) in lv.air for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
                    d.rectangle(rect(ox, x, z), fill=WALL if (x, y, z) in w else (40, 40, 44))

    def column_feet(x, z):
        for y in range(1, p.STAIRS_DROP + 2):
            if (x, y, z) in lv.air or (x, y, z) in lv.passable:
                return y
        return None

    def main_feet(x, z):
        """The feet height of an open column; for a wall, that of an open column next to it."""
        y = column_feet(x, z)
        if y is not None:
            return y
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                y = column_feet(x + dx, z + dz)
                if y is not None:
                    return y
        return None

    def bunker_feet(x, z):
        return lv.bunker_floor + 1

    draw_layer(pad, range(minx, maxx + 1), main_feet)
    ox2 = pad + main_w + gap
    draw_layer(ox2, range(bminx, bmaxx + 1), bunker_feet)

    # Furniture seen from above (its lowest block), by kind.
    for (x, y, z) in sorted(lv.furniture | lv.walk_through):
        if y < 1 or (x, y - 1, z) in lv.furniture or (x, y - 1, z) in lv.walk_through:
            continue
        name = w.get((x, y, z), "").split("[")[0].split(":")[-1]
        X0, Z0, X1, Z1 = rect(pad, x, z)
        d.rectangle((X0, Z0, X1, Z1), fill=FURNITURE_COLORS.get(name, (150, 150, 150)))
    # Fittings seen from above.
    for (x, y, z) in lv.lights:
        if y >= 0:
            X0, Z0, X1, Z1 = rect(pad, x, z)
            d.rectangle((X0 + 1, Z0 + 1, X1 - 1, Z1 - 1), fill=(255, 248, 200))
        else:
            X0, Z0, X1, Z1 = rect(ox2, x, z)
            d.rectangle((X0 + 1, Z0 + 1, X1 - 1, Z1 - 1), fill=(255, 248, 200))
    for x, z in [(x, z) for x in range(p.HATCH["frame"][0], p.HATCH["frame"][2]) for z in range(p.HATCH["frame"][1], p.HATCH["frame"][3])]:
        hole = p.HATCH["hole"][0] <= x < p.HATCH["hole"][2] and p.HATCH["hole"][1] <= z < p.HATCH["hole"][3]
        d.rectangle(rect(pad, x, z), fill=(20, 20, 20) if hole else (232, 112, 90))
        if hole:
            d.rectangle(rect(ox2, x, z), fill=(20, 20, 20))
    for x, y, z, _ in lv.box_spots:                     # the box puzzle's spots: a purple dot, ringed on furniture
        X0, Z0, X1, Z1 = rect(pad, x, z)
        d.rectangle((X0 + 1, Z0 + 1, X1 - 1, Z1 - 1), fill=BOX_SPOT, outline=(255, 255, 255) if y > 1 else None)
    for letter, (x, y, z) in lv.valves:
        d.rectangle(rect(pad, x, z), fill=(240, 170, 50))
        X0, Z0, _, _ = rect(pad, x + 2, z)
        d.text((X0 + 6, Z0 - 5), letter, fill=(240, 170, 50), font=font)

    # Zones drawn as outlines on the bunker: collapse, the hole for its blocks, the end.
    zone_colors = {"collapse": (232, 112, 90), "collapse_blocks": (200, 140, 60), "end": (250, 90, 90),
                   "checkpoint": (240, 170, 50)}
    for zid, (x0, y0, z0, x1, y1, z1), tags in lv.zones:
        if zid not in zone_colors:
            continue
        ox, base = (ox2, bminx) if y0 < 0 else (pad, minx)
        X0, Z0 = ox + (x0 - base) * s, pad + (z0 - minz) * s
        X1, Z1 = ox + (x1 + 1 - base) * s - 1, pad + (z1 + 1 - minz) * s - 1
        d.rectangle((X0, Z0, X1, Z1), outline=zone_colors[zid], width=2)
        d.text((X1 + 4, Z0), zid, fill=zone_colors[zid], font=small)

    # Grid every 10 blocks, labels.
    for gx in range((minx // 10 + 1) * 10, maxx + 1, 10):
        X = pad + (gx - minx) * s
        d.line((X, pad, X, pad + (maxz - minz + 1) * s), fill=(255, 255, 255, 18))
        if gx % 20 == 0:
            d.text((X - 6, pad - 14), str(gx), fill=(120, 120, 120), font=small)
    for gz in range((minz // 10 + 1) * 10, maxz + 1, 10):
        Z = pad + (gz - minz) * s
        d.line((pad, Z, pad + main_w, Z), fill=(255, 255, 255, 18))
        if gz % 20 == 0:
            d.text((4, Z - 5), str(gz), fill=(120, 120, 120), font=small)
    for x, z, text in lv.labels:
        X, Z = pad + (x - minx) * s, pad + (z - minz) * s
        tw = d.textlength(text, font=font)
        d.text((X - tw / 2, Z - 6), text, fill=(235, 235, 235), font=font)
    d.text((ox2 - 20, pad - 18), "Bunker, under the library", fill=(235, 235, 235), font=font)
    d.text((ox2 + bunker_w // 2 - 20, pad + (lv.bunker_box[3] - minz) * s + 8), f"door {p.NEXT_LEVEL_SIGN}", fill=(250, 90, 90), font=font)
    y = pad + (maxz - minz + 1) * s + 16
    for line in stats:
        d.text((pad, y), line, fill=(200, 200, 200), font=font)
        y += 16
    img.save(path)
