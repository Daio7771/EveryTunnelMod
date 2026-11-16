"""The top-down picture of Chapter 2, Level 1, drawn from the plan's own blocks: the spaces, their doors (a red one is
restricted), the glass, the named places numbered with a key under the plan, and the two patrols' paths."""

from PIL import Image, ImageDraw, ImageFont

import level as L

SCALE = 14
MARGIN = 28
SPACE_COLOURS = {
    "entrance": (206, 214, 226), "main_corridor": (196, 204, 214), "lab": (176, 204, 228), "wc_west": (200, 214, 200),
    "lockers": (222, 206, 176), "ring": (230, 224, 190), "dining": (232, 212, 190), "aux_lab": (170, 190, 232), "aux_chamber": (150, 166, 214),
    "authorized": (232, 190, 190), "security": (206, 196, 214), "office": (222, 176, 160), "wc_east": (226, 196, 196),
    "level2": (200, 160, 176),
}
LABELS = {  # where each name is written: (x, z) in blocks, and whether it runs along a column
    "entrance": (41, 4), "main_corridor": (25.5, 25), "lab": (47, 25), "wc_west": (16.5, 13.5), "lockers": (11.5, 18.5),
    "ring": (39, 50), "dining": (39, 55), "aux_lab": (8, 50), "aux_chamber": (-4.5, 50), "authorized": (38, 74.8), "security": (21.5, 81.5),
    "office": (35, 80.2), "wc_east": (45, 80.5), "level2": (68.5, 28),
}


def font(size):
    for path in ("/usr/share/fonts/TTF/DejaVuSans.ttf", "/usr/share/fonts/TTF/DejaVuSansCondensed.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def draw(level, path):
    lo, hi = level.bounds()
    anchors, zones, paths = L.named_places(level)
    w = (hi[0] - lo[0] + 1) * SCALE + 2 * MARGIN
    h_plan = (hi[2] - lo[2] + 1) * SCALE + 2 * MARGIN
    key_rows = (len(anchors) + 2) // 3
    img = Image.new("RGB", (max(w, 880), h_plan + key_rows * 17 + 40), (28, 29, 33))
    d = ImageDraw.Draw(img)

    def px(x, z):
        return MARGIN + (x - lo[0]) * SCALE, MARGIN + (z - lo[2]) * SCALE

    for (x, y, z), role in level.shell.items():
        if y == 0:
            colour = {"glass": (110, 200, 220), "dado": (74, 76, 84), "wall": (74, 76, 84)}.get(role)
            if colour:
                a, b = px(x, z)
                d.rectangle([a, b, a + SCALE - 1, b + SCALE - 1], fill=colour)
    for (x, y, z), sid in level.air.items():
        if y == 0:
            a, b = px(x, z)
            d.rectangle([a, b, a + SCALE - 1, b + SCALE - 1], fill=SPACE_COLOURS[sid])
    f_small, f_label, f_key = font(10), font(12), font(11)
    for sid, (x, z) in LABELS.items():
        name = level.spaces[sid].name
        a, b = px(x, z)
        tw = d.textlength(name, font=f_label)
        d.text((a - tw / 2, b - 6), name, fill=(40, 40, 46), font=f_label)
    for door in L.DOORS:
        if door[5] == "opening":
            continue
        cells, _ = L.door_cells(door)
        colour = {"normal": (160, 110, 60), "restricted": (200, 130, 60)}.get(door[5], (205, 60, 60))
        for x, y, z in cells:
            if y == 0:
                a, b = px(x, z)
                d.rectangle([a + 1, b + 1, a + SCALE - 2, b + SCALE - 2], fill=colour)
        if door[5] != "normal":
            x, _, z = cells[len(cells) // 4]
            a, b = px(x + (0.5 if door[1] == "z" else 0.5), z + 0.5)
            d.ellipse([a - 6, b - 6, a + 6, b + 6], outline=(255, 255, 255), fill=(205, 60, 60), width=1)
            d.line([a - 3, b, a + 3, b], fill=(255, 255, 255), width=2)
    cx, cy, cz = L.CLOCK["at"]
    a, b = px(cx, cz)
    d.rectangle([a + 3, b + 3, a + SCALE - 4, b + SCALE - 4], fill=(20, 20, 20), outline=(60, 220, 90))
    # furniture and props from above: benches, lockers, the speakers and the cameras
    for (x, y, z), block in L.props().items():
        name = block.split(":")[1].split("[")[0]
        a, b = px(x, z)
        furniture = {"lab_bench": (160, 160, 166), "locker": (96, 116, 156), "predecessor_locker": (200, 90, 90), "exec_desk": (60, 56, 52),
                     "bookshelf": (120, 84, 56), "leather_sofa": (70, 50, 40), "factory_glass": (110, 200, 220), "film_light": (30, 30, 34),
                     "film_camera": (30, 30, 34)}
        if name in furniture and y == 0:
            d.rectangle([a + 1, b + 1, a + SCALE - 2, b + SCALE - 2], fill=furniture[name])
        elif name in ("badge_reader", "keypad", "noname_terminal", "rowan_portrait"):
            d.rectangle([a + 3, b + 3, a + SCALE - 4, b + SCALE - 4], fill=(30, 30, 34), outline=(60, 220, 90))
        elif name == "factory_camera":
            d.ellipse([a + 3, b + 3, a + SCALE - 4, b + SCALE - 4], fill=(220, 40, 40), outline=(255, 255, 255))
        elif name == "ceiling_speaker":
            d.rectangle([a + 4, b + 4, a + SCALE - 5, b + SCALE - 5], outline=(40, 40, 46), fill=(250, 220, 90))
        elif name == "dirt_spot":
            d.ellipse([a + 3, b + 3, a + SCALE - 4, b + SCALE - 4], fill=(110, 90, 66))
    # the patrols, and the people's routes
    for pid, points, closed, _ in paths:
        if pid == "task3_dirt_spots":
            continue
        pts = [px(x, z) for x, _, z in points]
        colour = (40, 90, 210) if pid.startswith("patrol_a") else (40, 150, 80) if pid.startswith("route_") else (230, 130, 30)
        loop = pts + [pts[0]] if closed else pts
        d.line(loop, fill=colour, width=3)
        for (x0, z0), (x1, z1) in zip(loop, loop[1:]):
            mx, mz = (x0 + x1) / 2, (z0 + z1) / 2
            dx, dz = x1 - x0, z1 - z0
            n = max(1.0, (dx * dx + dz * dz) ** 0.5)
            ux, uz = dx / n, dz / n
            d.polygon([(mx + ux * 6, mz + uz * 6), (mx - ux * 4 - uz * 4, mz - uz * 4 + ux * 4), (mx - ux * 4 + uz * 4, mz - uz * 4 - ux * 4)], fill=colour)
    # the named places, numbered
    for n, (aid, (x, y, z), yaw, provisional) in enumerate(anchors, start=1):
        a, b = px(x, z)
        colour = (40, 150, 70) if not provisional else (255, 255, 255)
        d.ellipse([a - 6, b - 6, a + 6, b + 6], fill=colour if not provisional else None, outline=(20, 20, 20), width=1)
        label = str(n)
        d.text((a - d.textlength(label, font=f_small) / 2, b - 5), label, fill=(0, 0, 0) if provisional else (255, 255, 255), font=f_small)
    # north, the scale and the key
    d.text((12, 6), "N ↑   west ← → east   (14 px = 1 block)", fill=(220, 220, 224), font=f_label)
    d.text((w - 640, 6), "brown door = free   red = locked   (circle = into a forbidden zone)   blue = patrol A   orange = patrol B   green = people   red dot = camera", fill=(220, 220, 224), font=f_label)
    ky = h_plan + 6
    for n, (aid, _, _, provisional) in enumerate(anchors, start=1):
        col, row = (n - 1) // key_rows, (n - 1) % key_rows
        d.text((MARGIN + col * 290, ky + row * 17), f"{n:2d}  {aid}" + ("  (provisional)" if provisional else ""), fill=(210, 210, 214), font=f_key)
    img.save(path)
