"""
Level 5's safety briefing in the Waiting room (BriefingBoardBlock, BriefingSignBlock): a big cork board in a
wooden frame, six blocks wide and two high, a column of a block and a half for each technical sheet (the
Hunters, the Distortion Cloud, the Distortion Junior, the wrench and the detector), a pencil drawing pinned
above and the typed sheet below; over it a printed plastic plate, SAFETY BRIEFING. generate.py calls
board_image() and sign_image() and cuts them into the blocks' textures, PX pixels a block.

The drawings are rendered from the entities' own models (the committed geo/entity/*.geo.json and their
skins), three-quarters from the front, and turned into graphite on paper: outlines where faces meet, hatching
as dark as the surface (crossed where darker), white eyes left as bare paper, a light shadow on the ground.
The wrench and the detector have no 3D model (their items are flat pictures), so they are built here out of
boxes the same way. The sheets carry their card's own text (notes/en/ch1_l5_card_*.txt), typed small.

Run alone for a preview (from the repository root):   python3 tools/blocks/briefing.py
    tools/blocks/output/briefing_preview.png (git-ignored): the board and the plate, enlarged
"""

import json
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"
sys.path.insert(0, str(ROOT / "tools" / "entity"))
from pale_man import faces  # noqa: E402

PX = 128                      # texture pixels a block
COLS, ROWS = 6, 2             # the board, in blocks
SIGN_COLS = 4                 # the plate over it
# The board's four columns, each a block and a half: (its card, the title typed on the sheet, the drawing).
COLUMNS = (("ch1_l5_card_hunters", ("TECHNICAL SHEET", "HUNTERS"), "hunter"),
           ("ch1_l5_card_cloud", ("TECHNICAL SHEET", "DISTORTION", "CLOUD"), "distortion_cloud"),
           ("ch1_l5_card_junior", ("TECHNICAL SHEET", "DISTORTION", "JUNIOR"), "distortion_junior"),
           ("ch1_l5_card_equipment", ("EQUIPMENT SHEET", "WRENCH AND", "DETECTOR"), "equipment"))
LAB_GREEN = (86, 150, 120)
FONT_BOLD = "DejaVuSans-Bold.ttf"
FONT_MONO = "DejaVuSansMono.ttf"


def font(name, size):
    try:
        return ImageFont.truetype(name, size)
    except OSError:
        raise SystemExit(f"briefing.py needs the font {name} (DejaVu, e.g. pacman -S ttf-dejavu)")


# =====================================================================================
# Models: boxes in model units (16 a block), their faces painted from a skin or a flat colour

def rotation(deg):
    """A bone's rotation [x, y, z] in degrees, GeckoLib's way (x and y turned the other way round)."""
    x, y, z = (math.radians(a) for a in (-deg[0], -deg[1], deg[2]))
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def about(pivot, deg):
    """(matrix, offset) turning points by `deg` round `pivot`."""
    m = rotation(deg)
    p = np.array(pivot, float)
    return m, p - m @ p


def compose(outer, inner):
    return outer[0] @ inner[0], outer[0] @ inner[1] + outer[1]


IDENTITY = (np.eye(3), np.zeros(3))


def box_faces(x0, y0, z0, x1, y1, z1):
    """Each face's corners (top left, top right, bottom left) as its picture lies on it, seen from outside."""
    return {"north": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0)), "south": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1)),
            "east": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1)), "west": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0)),
            "up": ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1)), "down": ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0))}


def geo_quads(name, skin=None):
    """An entity's faces from its committed geometry and skin: [(three corners, paint)], paint being
    (skin, its glowmask or None, pixel rectangle)."""
    g = json.loads((ASSETS / "geo" / "entity" / f"{name}.geo.json").read_text())["minecraft:geometry"][0]
    tex = Image.open(ASSETS / "textures" / "entity" / f"{skin or name}.png").convert("RGBA")
    glow_file = ASSETS / "textures" / "entity" / f"{skin or name}_glowmask.png"
    glow = Image.open(glow_file).convert("RGBA") if glow_file.exists() else None
    scale = tex.width / g["description"]["texture_width"]
    bones = {b["name"]: b for b in g["bones"]}
    placed = {}

    def place(bn):
        if bn not in placed:
            b = bones[bn]
            local = about(b.get("pivot", (0, 0, 0)), b.get("rotation", (0, 0, 0)))
            placed[bn] = compose(place(b["parent"]), local) if b.get("parent") in bones else local
        return placed[bn]

    quads = []
    for bn, b in bones.items():
        m, t = place(bn)
        for c in b.get("cubes", []):
            inf = c.get("inflate", 0.0)
            (ox, oy, oz), (sx, sy, sz) = c["origin"], c["size"]
            rects = faces(*c["uv"], c["size"], scale=scale)
            cube = IDENTITY if "rotation" not in c else about(c.get("pivot", (0, 0, 0)), c["rotation"])
            for face, corners in box_faces(ox - inf, oy - inf, oz - inf, ox + sx + inf, oy + sy + inf, oz + sz + inf).items():
                pts = [m @ (cube[0] @ np.array(p, float) + cube[1]) + t for p in corners]
                quads.append((pts, (tex, glow, rects[face])))
    return quads


def solid_quads(boxes, turn=IDENTITY):
    """Flat-coloured boxes: [((x0, y0, z0, x1, y1, z1), colour, its own turn or None)]."""
    quads = []
    for (x0, y0, z0, x1, y1, z1), colour, own in boxes:
        m, t = compose(turn, own) if own else turn
        for corners in box_faces(x0, y0, z0, x1, y1, z1).values():
            quads.append(([m @ np.array(p, float) + t for p in corners], colour))
    return quads


def equipment_quads():
    """The wrench, heavy, lying slant on the left; the detector on the right: a box with its dial, a handle on
    top, the probe by it (the cable is drawn later, by hand)."""
    steel, dark, body, dial = (165, 168, 172), (70, 72, 76), (196, 168, 60), (236, 234, 224)
    wrench = [((-11, -1.2, -0.5, 6, 1.2, 0.5), steel, None),                               # the handle
              ((6, -2.6, -0.7, 10.5, -1.0, 0.7), steel, None), ((6, 1.0, -0.7, 10.5, 2.6, 0.7), steel, None),
              ((10.5, -2.6, -0.7, 11.8, 2.6, 0.7), steel, None),                         # the box end round its hole
              ((6, -1.0, -0.7, 7.2, 1.0, 0.7), steel, None),
              ((-15.5, -2.4, -0.7, -11, -0.9, 0.7), steel, None), ((-15.5, 0.9, -0.7, -11, 2.4, 0.7), steel, None),
              ((-12, -0.9, -0.7, -11, 0.9, 0.7), steel, None)]                           # the open jaws
    quads = solid_quads(wrench, compose((np.eye(3), np.array([-12.0, 7.0, 0.0])), about((0, 0, 0), (60, 0, 35))))
    detector = [((-4, 0, -2.5, 4, 5, 2.5), body, None), ((-2.8, 1.2, -2.7, 2.8, 4.2, -2.5), dial, None),
                ((-0.2, 2.0, -2.8, 0.2, 3.9, -2.7), dark, about((0, 2.0, -2.8), (0, 0, -35))),
                ((-3, 5, -0.6, -2, 7, 0.6), dark, None), ((2, 5, -0.6, 3, 7, 0.6), dark, None), ((-3, 7, -0.6, 3, 8, 0.6), dark, None),
                ((5.5, 0, -1.0, 7.5, 2, 1.0), dark, None), ((5.5, 0, 1.0, 7.5, 2, 7.0), body, None)]
    quads += solid_quads(detector, (1.5 * np.eye(3), np.array([8.0, -6.0, 0.0])))
    return quads


# =====================================================================================
# Rendering: an orthographic view, a z-buffer, then graphite

def render(quads, yaw, pitch, size):
    """Draws `quads` seen from `yaw`/`pitch` degrees, fitted into `size` (w, h) pixels. Returns the buffers
    tone (0 dark .. 1 light), face (which face, -1 none), coverage, glow (what shines: eyes) and depth, then
    where a model point falls on the screen (`screen(view @ p)`) and `view`."""
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    view = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    cam = [([view @ p for p in pts], paint) for pts, paint in quads]
    allp = np.array([p for pts, _ in cam for p in pts])
    lo, hi = allp.min(axis=0), allp.max(axis=0)
    w, h = size
    scale = min((w * 0.86) / (hi[0] - lo[0]), (h * 0.86) / (hi[1] - lo[1]))
    cx, cyy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2

    def screen(p):
        return np.array([w / 2 + (p[0] - cx) * scale, h / 2 - (p[1] - cyy) * scale, p[2]])

    zbuf = np.full((h, w), np.inf)
    tone = np.ones((h, w))
    face = np.full((h, w), -1)
    glowing = np.zeros((h, w), bool)
    light = np.array([-0.55, 0.7, -0.45])
    light /= np.linalg.norm(light)
    for i, (pts, paint) in enumerate(cam):
        a, b, c = (np.array(p) for p in pts)
        normal = np.cross(c - a, b - a)                         # outwards: the corners run clockwise from outside
        n_len = np.linalg.norm(normal)
        if n_len < 1e-9:
            continue
        normal /= n_len
        if normal[2] > 0.0:                                     # its back is to us (the camera looks down +z)
            continue
        shade = 0.25 + 0.75 * max(0.0, float(normal @ light))
        p0, p1, p2 = screen(a), screen(b), screen(c)
        e1, e2 = p1[:2] - p0[:2], p2[:2] - p0[:2]
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9:
            continue
        xs = [p[0] for p in (p0, p1, p2, p1 + p2 - p0)]
        ys = [p[1] for p in (p0, p1, p2, p1 + p2 - p0)]
        x_lo, x_hi = max(0, int(min(xs))), min(w - 1, int(max(xs)) + 1)
        y_lo, y_hi = max(0, int(min(ys))), min(h - 1, int(max(ys)) + 1)
        if x_lo > x_hi or y_lo > y_hi:
            continue
        gx, gy = np.meshgrid(np.arange(x_lo, x_hi + 1) + 0.5, np.arange(y_lo, y_hi + 1) + 0.5)
        dx, dy = gx - p0[0], gy - p0[1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (dy * e1[0] - dx * e1[1]) / det
        inside = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
        depth = p0[2] + s * (p1[2] - p0[2]) + t * (p2[2] - p0[2])
        shines = np.zeros(s.shape, bool)
        if isinstance(paint[0], Image.Image):
            tex, glow, (rx0, ry0, rx1, ry1) = paint
            u = np.clip((rx0 + s * (rx1 - rx0)).astype(int), 0, tex.width - 1)
            v = np.clip((ry0 + t * (ry1 - ry0)).astype(int), 0, tex.height - 1)
            rgba = np.asarray(tex)[v, u].astype(float)
            inside &= rgba[..., 3] > 127
            lum = (0.3 * rgba[..., 0] + 0.59 * rgba[..., 1] + 0.11 * rgba[..., 2]) / 255.0
            if glow is not None:
                shines = np.asarray(glow)[v, u][..., 3] > 127
        else:
            lum = np.full(s.shape, (0.3 * paint[0] + 0.59 * paint[1] + 0.11 * paint[2]) / 255.0)
        region = (slice(y_lo, y_hi + 1), slice(x_lo, x_hi + 1))
        nearer = inside & (depth < zbuf[region])
        zbuf[region] = np.where(nearer, depth, zbuf[region])
        # Dark things still show their form: the light lifts even a black surface a little.
        surface = 0.1 + 0.6 * lum + 0.3 * shade * (0.35 + 0.65 * lum)
        tone[region] = np.where(nearer, surface, tone[region])
        face[region] = np.where(nearer, i, face[region])
        glowing[region] = np.where(nearer, shines, glowing[region])
    return tone, face, face >= 0, glowing, zbuf, screen, view


def smooth_noise(shape, cell, rng):
    """Smooth random field about -1..1, changing every `cell` pixels."""
    h, w = shape
    small = rng.uniform(-1, 1, (h // cell + 3, w // cell + 3))
    big = Image.fromarray(((small + 1) * 127.5).astype(np.uint8)).resize(((w // cell + 3) * cell, (h // cell + 3) * cell), Image.BICUBIC)
    return np.asarray(big, float)[:h, :w] / 127.5 - 1.0


def hatch(shape, angle, period, rng, width=0.2):
    """Pencil lines across the paper at `angle`, each line a little wavy, of its own pressure, broken here and there."""
    h, w = shape
    y, x = np.mgrid[0:h, 0:w].astype(float)
    a = math.radians(angle)
    g = (x * math.cos(a) + y * math.sin(a)) / period + 0.35 * smooth_noise(shape, 24, rng)
    k = np.floor(g + 0.5).astype(int)
    pressure = rng.uniform(0.55, 1.0, k.max() - k.min() + 1)[k - k.min()]
    line = np.clip(1.0 - np.abs(g - np.round(g)) / width, 0.0, 1.0)
    breaks = (smooth_noise(shape, 10, rng) > -0.75).astype(float)
    return line * pressure * breaks


def shifted(a, dy, dx):
    return np.roll(np.roll(a, dy, axis=0), dx, axis=1)


def edges(face, cover, depth, jump=1.5):
    """The outlines: strong where the drawing meets the paper or one part passes in front of another (a jump of
    `jump` model units in depth), faint where two faces of one surface meet."""
    strong = np.zeros(face.shape, bool)
    faint = np.zeros(face.shape, bool)
    for dy, dx in ((0, 1), (1, 0), (1, 1)):
        other_cover, other_face, other_depth = shifted(cover, dy, dx), shifted(face, dy, dx), shifted(depth, dy, dx)
        strong |= cover != other_cover
        both = cover & other_cover
        jumps = both & (np.abs(np.where(both, depth, 0.0) - np.where(both, other_depth, 0.0)) > jump)
        strong |= jumps
        faint |= both & (face != other_face) & ~jumps
    return strong, faint


def widen(mask, size):
    return np.asarray(Image.fromarray(mask.astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(size)), float) / 255.0


def graphite(tone, face, cover, glow, depth, rng, shadow=None):
    """The pencil drawing of a render: how dark each pixel's graphite is (0 bare paper .. 1). Lighter surfaces get
    one layer of hatching, darker ones more, crossed, the darkest a smudge under them; what shines stays bare paper,
    ringed with a line."""
    shape = tone.shape
    layers = ((0.74, 38, 10.0, 0.75), (0.5, -48, 10.5, 0.8), (0.3, 8, 9.0, 0.8), (0.18, 82, 8.0, 0.7))
    dark = np.zeros(shape)
    for limit, angle, period, strength in layers:
        weight = np.clip((limit - tone) / 0.15, 0.0, 1.0) * cover
        dark = 1.0 - (1.0 - dark) * (1.0 - strength * weight * hatch(shape, angle, period, rng, width=0.24))
    dark = np.maximum(dark, 0.35 * np.clip((0.3 - tone) / 0.15, 0.0, 1.0) * cover)
    if shadow is not None:
        dark = np.maximum(dark, 0.5 * shadow * hatch(shape, 30, 9.0, rng, width=0.24) * ~cover)
    shine = widen(glow, 5) > 0.5
    dark = np.where(shine, 0.0, dark)
    strong, faint = edges(face, cover, depth)
    strong |= (shine != shifted(shine, 0, 1)) | (shine != shifted(shine, 1, 0))
    line = np.maximum(0.92 * widen(strong, 3), 0.35 * faint)
    line *= (smooth_noise(shape, 14, rng) > -0.8)
    sketch = shifted(widen(strong, 3), 2, 1) * 0.25
    return np.clip(np.maximum(dark, np.maximum(line, sketch)), 0.0, 1.0)


def on_paper(dark, size, rng):
    """Graphite of darkness `dark` on off-white paper, brought down to `size` (antialiased)."""
    h, w = dark.shape
    grain = smooth_noise((h, w), 2, rng)
    paper = np.array([241.0, 237.0, 226.0]) + 5.0 * grain[..., None]
    lead = np.array([52.0, 54.0, 60.0])
    d = (dark * (0.85 + 0.15 * (grain + 1) / 2))[..., None]
    img = paper * (1.0 - d) + lead * d
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize(size, Image.LANCZOS)


def drawing(kind, size, seed):
    """One column's pencil drawing, `size` (w, h) pixels."""
    rng = np.random.default_rng(seed)
    hi = (size[0] * 3, size[1] * 3)
    if kind == "equipment":
        quads, yaw, pitch = equipment_quads(), 20.0, 18.0
    elif kind == "distortion_junior":
        quads, yaw, pitch = geo_quads("distortion_junior", "distortion_junior_0"), 28.0, 10.0
    else:
        quads, yaw, pitch = geo_quads(kind), 28.0, 10.0
    tone, face, cover, glow, depth, screen, view = render(quads, yaw, pitch, hi)
    # A light shadow on the ground under it (not under the Junior, which floats at face height).
    shadow = None
    if kind in ("hunter", "distortion_cloud"):
        ys, xs = np.nonzero(cover)
        foot, mid, half = ys.max(), (xs.min() + xs.max()) / 2, (xs.max() - xs.min()) * 0.55
        y, x = np.mgrid[0:hi[1], 0:hi[0]].astype(float)
        shadow = np.clip(1.0 - ((x - mid) / half) ** 2 - ((y - foot - 4) / (half * 0.18)) ** 2, 0.0, 1.0) ** 0.5
    dark = graphite(tone, face, cover, glow, depth, rng, shadow)
    if kind == "equipment":
        # The detector's cable to its probe, by hand.
        a = screen(view @ np.array([8.0 + 1.5 * 4.0, -6.0 + 1.5 * 2.5, 0.0]))
        b = screen(view @ np.array([8.0 + 1.5 * 6.5, -6.0 + 1.5 * 1.0, 1.5 * 7.0]))
        img = Image.fromarray((dark * 255).astype(np.uint8))
        d = ImageDraw.Draw(img)
        pts = [(a[0] + (b[0] - a[0]) * s + 40 * math.sin(math.pi * s), a[1] + (b[1] - a[1]) * s + 30 * math.sin(math.pi * s))
               for s in np.linspace(0, 1, 30)]
        d.line(pts, fill=220, width=4)
        dark = np.asarray(img, float) / 255.0
    return on_paper(dark, size, rng)


# =====================================================================================
# The board and the plate

def cork(w, h, rng):
    base = np.array([184.0, 138.0, 90.0])
    n = rng.normal(0, 1, (h, w))
    img = base + 14.0 * n[..., None] * np.array([1.0, 0.9, 0.7])
    for _ in range(w * h // 30):                                   # the granules
        x, y = rng.integers(0, w), rng.integers(0, h)
        img[y, x] = rng.choice([[140, 98, 60], [214, 172, 120], [120, 84, 50]])
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6))


def wood(w, h, rng):
    y, x = np.mgrid[0:h, 0:w].astype(float)
    grain = np.sin(x * 0.9 + 3.0 * np.sin(y * 0.05) + rng.uniform(0, 6)) * 0.5 + rng.normal(0, 0.3, (h, w))
    img = np.array([108.0, 70.0, 42.0]) + 16.0 * grain[..., None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def sheet(card, title, size):
    """A typed sheet: its title and the card's text, small."""
    w, h = size
    img = Image.new("RGB", size, (247, 246, 240))
    d = ImageDraw.Draw(img)
    small, big, body = font(FONT_BOLD, 6), font(FONT_BOLD, 11), font(FONT_MONO, 5)
    d.text((7, 6), title[0], font=small, fill=(90, 92, 96))
    y = 14
    for line in title[1:]:
        d.text((7, y), line, font=big, fill=(20, 20, 22))
        y += 12
    d.line((7, y + 2, w - 7, y + 2), fill=LAB_GREEN, width=2)
    y += 7
    text = (ASSETS / "notes" / "en" / f"{card}.txt").read_text().split("\n", 1)[1].replace("\n", " ")
    words, line = text.split(), ""
    for word in words:
        trial = f"{line} {word}".strip()
        if d.textlength(trial, font=body) > w - 14:
            d.text((7, y), line, font=body, fill=(60, 60, 64))
            y += 6
            line = word
            if y > h - 10:
                break
        else:
            line = trial
    if y <= h - 10:
        d.text((7, y), line, font=body, fill=(60, 60, 64))
    return img


def pin(board, x, y):
    d = ImageDraw.Draw(board)
    d.ellipse((x - 2, y - 1, x + 6, y + 7), fill=(90, 62, 40))          # its shadow on the cork
    d.ellipse((x - 4, y - 4, x + 4, y + 4), fill=(178, 36, 32))
    d.ellipse((x - 2, y - 3, x + 1, y), fill=(232, 120, 110))


def pinned(board, paper, x, y, angle, rng, at=0.5):
    """Pins a paper on the board with its top left near (x, y), turned a little, its shadow under it; the pin
    `at` across its top."""
    turned = paper.convert("RGBA").rotate(angle, resample=Image.BICUBIC, expand=True)
    shadow = Image.new("RGBA", turned.size, (40, 26, 14, 0))
    shadow.putalpha(turned.getchannel("A").point(lambda a: a * 0.45).filter(ImageFilter.GaussianBlur(1.5)))
    board.alpha_composite(shadow, (x + 2, y + 3))
    board.alpha_composite(turned, (x, y))
    pin(board, x + int(turned.width * at) + rng.randint(-4, 4), y + 5)


def board_image(px=PX):
    """The whole board, COLS x ROWS blocks of `px` pixels."""
    rng = np.random.default_rng(5)
    w, h = COLS * px, ROWS * px
    frame = max(6, px // 16)
    board = wood(w, h, rng).convert("RGBA")
    board.paste(cork(w - 2 * frame, h - 2 * frame, rng), (frame, frame))
    d = ImageDraw.Draw(board)
    d.rectangle((frame - 1, frame - 1, w - frame, h - frame), outline=(70, 44, 26))
    col_w = w // len(COLUMNS)
    r = random.Random(7)
    for c, (card, title, kind) in enumerate(COLUMNS):
        left = c * col_w
        art = drawing(kind, (int(col_w * 0.84), int(px * 0.78)), seed=11 + c)
        pinned(board, art, left + (col_w - art.width) // 2 - 4, frame + 4, r.uniform(-2.0, 2.0), r)
        paper = sheet(card, title, (int(col_w * 0.66), int(px * 0.86)))
        pinned(board, paper, left + (col_w - paper.width) // 2 - 4, px + 2, r.uniform(-1.5, 1.5), r, at=0.82)
    return board.convert("RGB")


def sign_image(px=PX):
    """The plate, SIGN_COLS blocks wide: printed white plastic, black capitals, a surgical green stripe along its foot,
    a screw at each corner. Only the band between plate_band() is used; the rest is painted the plate's white."""
    w, h = SIGN_COLS * px, px
    top, bottom = plate_band(px)
    img = Image.new("RGB", (w, h), (238, 238, 234))
    d = ImageDraw.Draw(img)
    d.rectangle((0, top, w - 1, bottom - 1), fill=(244, 244, 240), outline=(190, 192, 192))
    stripe = bottom - max(6, px // 14)
    d.rectangle((1, stripe, w - 2, bottom - 2), fill=LAB_GREEN)
    size = int((stripe - top) * 0.62)
    f = font(FONT_BOLD, size)
    while d.textlength("SAFETY BRIEFING", font=f) > w * 0.86:
        size -= 1
        f = font(FONT_BOLD, size)
    text_w = d.textlength("SAFETY BRIEFING", font=f)
    box = d.textbbox((0, 0), "SAFETY BRIEFING", font=f)
    d.text(((w - text_w) / 2, top + (stripe - top - (box[3] - box[1])) / 2 - box[1]), "SAFETY BRIEFING", font=f, fill=(22, 22, 24))
    for x in (8, w - 9):
        for y in (top + 7, stripe - 6):
            d.ellipse((x - 3, y - 3, x + 3, y + 3), fill=(176, 178, 180), outline=(120, 122, 126))
    return img


def plate_band(px=PX):
    """The plate's top and bottom rows in its picture (it hangs low in its blocks, just over the board)."""
    return int(px * 0.3), int(px * 0.94)


def main():
    OUT.mkdir(exist_ok=True)
    board, plate = board_image(), sign_image()
    top, bottom = plate_band()
    sheet_img = Image.new("RGB", (board.width + 40, board.height + bottom - top + 60), (60, 62, 66))
    sheet_img.paste(plate.crop((0, top, plate.width, bottom)), (20 + (board.width - plate.width) // 2, 20))
    sheet_img.paste(board, (20, 40 + bottom - top))
    sheet_img.resize((sheet_img.width * 2, sheet_img.height * 2), Image.NEAREST).save(OUT / "briefing_preview.png")
    print(f"wrote {OUT / 'briefing_preview.png'}")


if __name__ == "__main__":
    main()
