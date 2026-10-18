"""Top-down plan of a whole level and a sheet of the generated textures, as PNG."""

import io

from PIL import Image, ImageDraw, ImageFont

TAG_COLORS = {
    "car": (150, 160, 175), "box": (168, 130, 80), "bin": (110, 110, 110), "cart": (90, 140, 200),
    "cone": (235, 110, 30), "extinguisher": (210, 30, 30), "barrier": (190, 190, 180),
}
ODDITY_TAGS = {"collapse", "walled_off", "stairs", "door", "wrong_sign", "extra_pillar"}
FIXTURE_COLORS = {"lit": (255, 248, 200), "dim": (175, 150, 70), "dead": (120, 120, 120)}


def write_level(path, level, stats):
    p, S, w = level.p, level.S, level.world
    walls = {S["wall"], S["wall_base"], S["paint_band"], S["duct"], S["pillar"], "minecraft:stone_bricks"}
    cols = {}
    for (x, y, z) in w.blocks:
        cols.setdefault((x, z), []).append(y)
    xs = [x for x, _ in cols]
    zs = [z for _, z in cols]
    minx, maxx, minz, maxz = min(xs) - 2, max(xs) + 2, min(zs) - 2, max(zs) + 2
    s = 6
    pad = 30
    text_h = 40 + 16 * (len(stats) + 2)
    width = (maxx - minx + 1) * s + 2 * pad + 230
    img = Image.new("RGB", (width, (maxz - minz + 1) * s + 2 * pad + text_h), (16, 17, 20))
    d = ImageDraw.Draw(img, "RGBA")
    font = ImageFont.load_default(size=12)
    small = ImageFont.load_default(size=10)

    def box(x, z, inset=0):
        X, Z = pad + (x - minx) * s, pad + (z - minz) * s
        return (X + inset, Z + inset, X + s - 1 - inset, Z + s - 1 - inset)

    def ref_y(x, z):
        if (x, z) in level.floor:
            return level.floor[(x, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, z + dz) in level.floor:
                return level.floor[(x + dx, z + dz)]
        return None

    for (x, z) in cols:
        y = ref_y(x, z)
        if y is None:
            continue
        body = w.get(x, y + 2, z)
        if body in walls or (body and "door" in body):
            d.rectangle(box(x, z), fill=(140, 140, 134) if body in walls else (120, 80, 50))
            continue
        floor = w.get(x, y, z)
        above = w.get(x, y + 1, z) or ""
        color = (74, 76, 80)
        if floor == S["line_white"]:
            color = (200, 200, 190)
        elif floor == S["line_yellow"]:
            color = (205, 160, 40)
        elif floor == S["floor_patch"]:
            color = (92, 93, 96)
        if "slab" in above:
            color = (104, 104, 104)
        d.rectangle(box(x, z), fill=color)
        if above.startswith(S["decal"]):
            d.rectangle(box(x, z, 2), fill=(30, 30, 34, 170))
    for (x, z), tag in w.tags.items():
        if tag in TAG_COLORS:
            d.rectangle(box(x, z), fill=TAG_COLORS[tag])
        elif tag in ODDITY_TAGS:
            d.rectangle(box(x, z), outline=(240, 70, 225), width=1)
    for x, z, state in level.fixtures:
        d.rectangle(box(x, z, 1), fill=FIXTURE_COLORS[state])
    # start, exit
    for zid, (x0, y0, z0, x1, y1, z1), tags in level.zones:
        color = (90, 200, 120) if "exit" in tags else (90, 160, 255) if "start" in tags else None
        if color:
            d.rectangle((box(x0, z0)[0] - 1, box(x0, z0)[1] - 1, box(x1, z1)[2] + 1, box(x1, z1)[3] + 1), outline=color, width=2)
    # direction of travel through each hall
    for h in level.halls:
        f = h["frame"]
        g = f["g"]
        z = f["hz"] + g.center_z
        xa, xb = f["hx"] + 3, f["hx"] + f["hp"].LENGTH - 4
        if not f["east"]:
            xa, xb = xb, xa
        ya = pad + (z - minz) * s + s // 2
        d.line((pad + (xa - minx) * s, ya, pad + (xb - minx) * s, ya), fill=(80, 200, 255, 200), width=2)
        tip = pad + (xb - minx) * s
        dirn = 1 if f["east"] else -1
        d.polygon([(tip, ya), (tip - 8 * dirn, ya - 5), (tip - 8 * dirn, ya + 5)], fill=(80, 200, 255))
    for x, z, text in level.labels:
        X, Z = pad + (x - minx) * s, pad + (z - minz) * s
        d.text((X, Z - 6), text, fill=(235, 235, 235), font=font, stroke_width=2, stroke_fill=(16, 17, 20))

    # legend
    lx = pad + (maxx - minx + 1) * s + 16
    items = [((90, 160, 255), "start (elevator)"), ((90, 200, 120), "exit (ramp to next level)"),
             ((80, 200, 255), "way through each hall"), ((255, 248, 200), "lit light"), ((175, 150, 70), "dim light"),
             ((120, 120, 120), "dead light"), ((240, 70, 225), "oddity"), ((104, 104, 104), "ramp"),
             ((30, 30, 34), "stain / decal")] + [(c, t) for t, c in TAG_COLORS.items()]
    for i, (col, label) in enumerate(items):
        y = pad + i * 16
        d.rectangle((lx, y, lx + 10, y + 10), fill=col)
        d.text((lx + 16, y - 1), label, fill=(200, 200, 200), font=small)
    ty = pad + (maxz - minz + 1) * s + 18
    for i, line in enumerate(stats):
        d.text((pad, ty + i * 16), line, fill=(200, 200, 200), font=small)
    img.save(path)


def write_texture_sheet(path, textures):
    names = sorted(textures)
    cols, cell = 12, 72
    sheet = Image.new("RGB", (cols * cell + 20, ((len(names) + cols - 1) // cols) * (cell + 14) + 20), (22, 23, 26))
    sd = ImageDraw.Draw(sheet)
    checker = Image.new("RGBA", (64, 64))
    cd = ImageDraw.Draw(checker)
    for i in range(0, 64, 8):
        for j in range(0, 64, 8):
            cd.rectangle((i, j, i + 7, j + 7), fill=(70, 70, 74) if (i + j) // 8 % 2 else (50, 50, 54))
    font = ImageFont.load_default(size=9)
    for i, name in enumerate(names):
        t = Image.open(io.BytesIO(textures[name])).convert("RGBA").resize((64, 64), Image.NEAREST)
        x, y = 10 + (i % cols) * cell, 10 + (i // cols) * (cell + 14)
        bg = checker.copy()
        bg.alpha_composite(t)
        sheet.paste(bg.convert("RGB"), (x, y))
        sd.text((x, y + 65), name[:12], fill=(170, 170, 170), font=font)
    sheet.save(path)
