"""
Chapter 2, Level 2's pictures, seen from above, to check the map by eye before building it (generate.py writes them into
tools/map/ch2l2/output/, git-ignored):

- `level_preview.png`: the whole level, each place drawn at the height a player stands there (lighter is higher), the route
  walked from the entry to the exit in yellow, its anchors named.
- `level_H.png`, `level_M.png`, `level_L.png`, `level_D.png`: one height each: what stands on it in colour, the rest grey.
- `flood_0.png` to `flood_3.png`: the still water, then the water after each flood stage, deeper bluer.

- `light_H.png` ... `light_D.png` (phase 8): the floor light of each height as the light check counts it (flickering lamps off, the fill
  on): green at or over the target, orange within 2 under it, red under; the lamps as dots in their tint (a ring: flickering, grey: dead),
  the fill blocks as white crosses, the emergency lamps red squares, the alarm lights magenta.
- `decor_H.png` ... `decor_D.png` (phase 8): the decoration of each height: pipes and trays blue, valves and gauges yellow, rust and stains
  brown, floor clutter orange, set pieces and other props white.

Colours: the old catacombs' masonry warm grey, the factory's drainage a brownish concrete, machinery steel blue; water blue;
stairs and ramps lighter; rails, bars and ladders white; openings orange; the gate red.
"""

from PIL import Image, ImageDraw, ImageFont

import checks as C
import light as Light

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

    draw_light(lv, out_dir)
    draw_decor(lv, out_dir)

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


TINT_COLOURS = {"warm": (255, 196, 122), "amber": (255, 154, 46), "cold": (150, 200, 255), "red": (255, 64, 48), "white": (244, 244, 238)}
GREEN, ORANGE, RED = (60, 170, 80), (230, 150, 40), (200, 50, 50)
DECOR_COLOURS = [("pipe", (80, 150, 255)), ("cable_tray", (80, 150, 255)), ("valve_wheel", (240, 220, 60)), ("pressure_gauge", (240, 220, 60)),
                 ("rust_stain", (160, 90, 40)), ("stain", (160, 90, 40)), ("oil_drum", (240, 140, 30)), ("pallet", (240, 140, 30)),
                 ("cardboard_box", (240, 140, 30)), ("rubble_pile", (200, 130, 90)), ("loose_bricks", (200, 130, 90))]


def bands(lv):
    lo, hi = lv.bounds()
    levels = sorted(lv.levels.items(), key=lambda kv: kv[1])
    for i, (name, y) in enumerate(levels):
        top = levels[i + 1][1] if i + 1 < len(levels) else hi[1] + 1
        yield name, (y - (6 if name == "D" else 0), top)


def draw_light(lv, out_dir):
    walker = C.Walker(lv, {gid: g[2] for gid, g in lv.gates.items()})
    cells, no_fill = lv.fill_report["cells"], lv.fill_report["no_fill"]
    with_fill = Light.light_map(lv, Light.emitters(lv, True))
    lamps_only = Light.light_map(lv, Light.emitters(lv))
    for name, band in bands(lv):
        canvas = Canvas(lv, f"Floor light at height {name} (y {band[0]} to {band[1] - 1}): green at target, orange within 2, red under; flickering lamps counted off")
        paint(canvas, lv, columns(lv, walker, band), band[0], band[1], faded=columns(lv, walker))
        for c, target in cells.items():
            if not band[0] <= c[1] < band[1]:
                continue
            value = (lamps_only if lv.air[c] in no_fill else with_fill).get(c, 0)
            canvas.cell(c[0], c[2], GREEN if value >= target else ORANGE if value >= target - 2 else RED)
        for c in lv.lamps:
            if not band[0] - 2 <= c[1] < band[1] + 20 and not band[0] <= c[1] < band[1]:
                continue
            mode = lv.lamp_info[c][0]
            tint = lv.blocks[c].split("tint=")[1].rstrip("]") if "tint=" in lv.blocks[c] else "warm"
            px, pz = canvas.xy(c[0] + 0.5, c[2] + 0.5)
            colour = TINT_COLOURS[tint] if mode == "on" else (110, 110, 110)
            canvas.draw.ellipse([px - 2, pz - 2, px + 2, pz + 2], fill=colour if mode != "flicker" else None, outline=(0, 0, 0) if mode != "flicker" else colour)
        for c in lv.fill:
            if band[0] <= c[1] - 2 < band[1]:
                px, pz = canvas.xy(c[0] + 0.5, c[2] + 0.5)
                canvas.draw.line([px - 2, pz, px + 2, pz], fill=(255, 255, 255))
                canvas.draw.line([px, pz - 2, px, pz + 2], fill=(255, 255, 255))
        for c in lv.glow:
            if band[0] <= c[1] < band[1] + 4:
                px, pz = canvas.xy(c[0] + 0.5, c[2] + 0.5)
                canvas.draw.rectangle([px - 1, pz - 1, px + 1, pz + 1], fill=(230, 40, 40))
        for c in lv.alarms:
            px, pz = canvas.xy(c[0] + 0.5, c[2] + 0.5)
            canvas.draw.rectangle([px - 2, pz - 2, px + 2, pz + 2], outline=(255, 0, 255))
        legend(canvas, [("at target", GREEN), ("within 2", ORANGE), ("under", RED), ("lamp on (tint)", TINT_COLOURS["warm"]), ("fill block", (255, 255, 255)),
                        ("emergency lamp", (230, 40, 40)), ("alarm light", (255, 0, 255))])
        canvas.save(out_dir / f"light_{name}.png")


def draw_decor(lv, out_dir):
    walker = C.Walker(lv, {gid: g[2] for gid, g in lv.gates.items()})
    for name, band in bands(lv):
        canvas = Canvas(lv, f"Decoration at height {name} (y {band[0]} to {band[1] - 1})")
        paint(canvas, lv, columns(lv, walker, band), band[0], band[1], faded=columns(lv, walker))
        for c, block in lv.blocks.items():
            if not isinstance(block, str) or not block.startswith("every_tunnel:") or not band[0] <= c[1] < band[1] + 6:
                continue
            kind = block.split(":")[1].split("[")[0]
            colour = next((col for key, col in DECOR_COLOURS if kind == key), None)
            if colour is None:
                if "lamp" in kind or kind in ("ceiling_cable", "hanging_cable", "alarm_light", "wall_sconce", "drain_ladder", "catwalk_rail", "metal_grating", "checker_plate", "metal_panel", "pump_beacon", "trap_breaker", "sluice_gate", "note_board", "counter") or "catacomb" in kind or kind.startswith("puddle"):
                    continue
                colour = (255, 255, 255)
            px, pz = canvas.xy(c[0] + 0.5, c[2] + 0.5)
            canvas.draw.rectangle([px - 1, pz - 1, px + 1, pz + 1], fill=colour)
        legend(canvas, [("pipes, trays", (80, 150, 255)), ("valves, gauges", (240, 220, 60)), ("rust, stains", (160, 90, 40)), ("floor clutter", (240, 140, 30)),
                        ("rubble, bricks", (200, 130, 90)), ("set pieces and other props", (255, 255, 255))])
        canvas.save(out_dir / f"decor_{name}.png")
