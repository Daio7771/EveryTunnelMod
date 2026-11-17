"""
Chapter 2, Level 2's pictures, seen from above, to check the map by eye before building it (generate.py writes them into
tools/map/ch2l2/output/, git-ignored):

- `level_preview.png`: the whole level, each place drawn at the height a player stands there (lighter is higher), the route
  walked from the entry to the exit in yellow, its anchors named.
- `level_H.png`, `level_M.png`, `level_L.png`, `level_D.png`: one height each: what stands on it in colour, the rest grey.
- `flood_0.png` to `flood_3.png`: the still water, then the water after each flood stage, deeper bluer.

Colours: the old catacombs' masonry warm grey, the factory's drainage a brownish concrete, machinery steel blue; water blue;
stairs and ramps lighter; rails, bars and ladders white; openings orange; the gate red.
"""

from PIL import Image, ImageDraw, ImageFont

import checks as C

SCALE = 4
MARGIN = 24
LEGEND = 22
STYLE_COLOURS = {"masonry": (150, 142, 128), "drain": (128, 112, 92), "machine": (96, 118, 140)}
WATER = (40, 90, 170)
WALL = (40, 40, 44)
VOID = (14, 14, 16)
ROUTE = (250, 210, 40)
OPENING = (230, 140, 40)
GATE = (220, 50, 50)
RAIL = (235, 235, 235)


def font(size):
    for path in ("/usr/share/fonts/TTF/DejaVuSans-Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


class Canvas:
    def __init__(self, level, title):
        self.lv = level
        lo, hi = level.bounds()
        self.lo = lo
        w = (hi[0] - lo[0] + 1) * SCALE + 2 * MARGIN
        h = (hi[2] - lo[2] + 1) * SCALE + 2 * MARGIN + 30 + LEGEND
        self.img = Image.new("RGB", (w, h), VOID)
        self.draw = ImageDraw.Draw(self.img)
        self.draw.text((MARGIN, 6), title, fill=(230, 230, 230), font=font(14))

    def xy(self, x, z):
        return MARGIN + (x - self.lo[0]) * SCALE, MARGIN + 30 + (z - self.lo[2]) * SCALE

    def cell(self, x, z, colour):
        px, pz = self.xy(x, z)
        self.draw.rectangle([px, pz, px + SCALE - 1, pz + SCALE - 1], fill=colour)

    def dot(self, x, z, colour, r=2):
        px, pz = self.xy(x, z)
        self.draw.ellipse([px - r, pz - r, px + r, pz + r], fill=colour, outline=(0, 0, 0))

    def label(self, x, z, text, colour=(255, 255, 255), size=10):
        px, pz = self.xy(x, z)
        f = font(size)
        self.draw.text((px + 4, pz - 6), text, fill=colour, font=f, stroke_width=2, stroke_fill=(0, 0, 0))

    def save(self, path):
        self.img.save(path)


def shade(colour, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in colour)


def columns(level, walker, band=None):
    """{(x, z): (y, style, kind)}: the highest place to stand (or water's top) in each column, within `band` (lo, hi) if given."""
    out = {}
    for (x, y, z), owner in level.air.items():
        if band and not band[0] <= y < band[1]:
            continue
        kind = None
        if (x, y, z) in level.water and (x, y + 1, z) not in level.water:
            kind = "water"
        elif walker.standable((x, y, z)):
            kind = "floor"
        if not kind:
            continue
        if (x, z) in out and out[(x, z)][0] >= y:
            continue
        space = owner[8:] if owner.startswith("opening:") else owner
        style = level.spaces[space].style if space in level.spaces else "drain"
        out[(x, z)] = (y, style, "opening" if owner.startswith("opening:") else kind)
    return out


def paint(canvas, level, cols, ymin, ymax, faded=None):
    lv = level
    for (x, y, z), (role, style) in lv.shell.items():
        if role == "wall" or role == "dado":
            canvas.cell(x, z, WALL)
    for (x, z), (y, style, kind) in cols.items():
        if kind == "water":
            canvas.cell(x, z, WATER)
            continue
        t = (y - ymin) / max(1, ymax - ymin)
        colour = shade(STYLE_COLOURS.get(style, (120, 120, 120)), 0.55 + 0.75 * t)
        if kind == "opening":
            colour = OPENING
        block = lv.blocks.get((x, y - 1, z))
        if isinstance(block, str) and "_stairs" in block:
            colour = shade(colour, 1.25)
        canvas.cell(x, z, colour)
    if faded:
        for (x, z), (y, style, kind) in faded.items():
            if (x, z) not in cols:
                canvas.cell(x, z, (58, 58, 62) if kind != "water" else (30, 44, 70))
    for c, block in lv.blocks.items():
        if isinstance(block, str) and ("iron_bars" in block or "_wall[" in block or "ladder" in block):
            if faded is None or any(lo <= c[1] < hi for lo, hi in [(ymin - 1, ymax + 2)]):
                canvas.cell(c[0], c[2], RAIL)
    for gid, (cells, states, built) in lv.gates.items():
        for x, y, z in cells:
            canvas.cell(x, z, GATE)


def anchors(canvas, level, band=None):
    for a in level.layout["anchors"]:
        x, y, z = a["pos"]
        if band and not band[0] <= y < band[1]:
            continue
        colour = (120, 220, 255) if a.get("provisional") else (255, 255, 255)
        canvas.dot(x, z, colour)
        canvas.label(x, z, a["id"], colour, 9)


def legend(canvas, lines):
    """A row of colour keys under the map."""
    f = font(10)
    w, h = canvas.img.size
    x, y = MARGIN, h - LEGEND - 4
    for text, colour in lines:
        canvas.draw.rectangle([x, y + 3, x + 8, y + 11], fill=colour)
        canvas.draw.text((x + 12, y + 1), text, fill=(220, 220, 220), font=f)
        x += 22 + int(f.getlength(text))


def draw_all(level, ways, out_dir):
    lv = level
    walker = C.Walker(lv, {gid: g[2] for gid, g in lv.gates.items()})
    lo, hi = lv.bounds()
    cols = columns(lv, walker)
    canvas = Canvas(lv, "Chapter 2 Level 2 - from above (lighter = higher), the route in yellow")
    paint(canvas, lv, cols, lo[1], hi[1])
    for way in ways:
        for a, b in zip(way, way[1:]):
            pa, pb = canvas.xy(a[0] + 0.5, a[2] + 0.5), canvas.xy(b[0] + 0.5, b[2] + 0.5)
            canvas.draw.line([pa, pb], fill=ROUTE, width=2)
    anchors(canvas, lv)
    legend(canvas, [("masonry (old catacombs)", STYLE_COLOURS["masonry"]), ("drain (the factory's)", STYLE_COLOURS["drain"]),
                    ("machine", STYLE_COLOURS["machine"]), ("water", WATER), ("opening", OPENING), ("gate (grate)", GATE),
                    ("rails, bars, ladders", RAIL), ("route", ROUTE)])
    canvas.save(out_dir / "level_preview.png")

    levels = sorted(lv.levels.items(), key=lambda kv: kv[1])
    for i, (name, y) in enumerate(levels):
        top = levels[i + 1][1] if i + 1 < len(levels) else hi[1] + 1
        band = (y - (6 if name == "D" else 0), top)
        canvas = Canvas(lv, f"Height {name} (y {band[0]} to {top - 1}): in colour what stands there, grey the rest")
        paint(canvas, lv, columns(lv, walker, band), band[0], top, faded=cols)
        anchors(canvas, lv, band)
        canvas.save(out_dir / f"level_{name}.png")

    still = set(lv.water)
    stages = [("still water", still, None)]
    for stage in lv.layout["flood"]["stages"]:
        gates = dict({gid: g[2] for gid, g in lv.gates.items()}, **stage["gates"])
        stages.append((f"stage {stage['id']}: up to y {stage['level']} in {stage['seconds']} s (from {stage['trigger']})",
                       C.flooded(lv, stage, gates) | still, stage))
    for i, (title, water, stage) in enumerate(stages):
        canvas = Canvas(lv, f"Flood {i}: {title}")
        depth = {}
        for x, y, z in water:
            depth.setdefault((x, z), [y, y])
            depth[(x, z)][0] = min(depth[(x, z)][0], y)
            depth[(x, z)][1] = max(depth[(x, z)][1], y)
        for (x, y, z), (role, style) in lv.shell.items():
            if role in ("wall", "dado"):
                canvas.cell(x, z, WALL)
        for (x, z), (y, style, kind) in cols.items():
            canvas.cell(x, z, (70, 70, 74))
        for (x, z), (ylo, yhi) in depth.items():
            d = yhi - ylo + 1
            canvas.cell(x, z, shade(WATER, 0.7 + min(d, 17) / 17 * 0.9))
        for gid, (cells, states, built) in lv.gates.items():
            state = stage["gates"].get(gid, built) if stage else built
            for x, y, z in cells:
                canvas.cell(x, z, GATE if state != "open" else OPENING)
        anchors(canvas, lv)
        canvas.save(out_dir / f"flood_{i}.png")
