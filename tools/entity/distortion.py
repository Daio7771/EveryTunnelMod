"""
The Distortions' models, animations and textures for GeckoLib (Level 5): the Distortion Cloud and
the Distortion Junior, a small one of the same kind.

Run after changing anything here (from the repository root):   python3 tools/entity/distortion.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/distortion_cloud.geo.json, geo/entity/distortion_junior.geo.json
  animations/entity/distortion_cloud.animation.json   idle, leave
  animations/entity/distortion_junior.animation.json  idle, die
  textures/entity/distortion_cloud.png (+ _glowmask)
  textures/entity/distortion_junior_<0..3>.png (+ _glowmask): its eye cracking more at each stage,
      as it is stared at (client/render/DistortionRenderer.kt picks the stage)
  and the names in lang/en_us.json
and, in tools/entity/output/ (git-ignored): distortion_preview.png (the textures enlarged, both
models from the front and the side, and the Junior's eye at each stage).

The look: black clouds, puffs of smoke heaped on one another with ragged edges, streaked with
purple glitches, and in the middle one angry purple eye with a black slit, its upper lid pressed
down into a V. The eye and the brightest glitches glow in the dark (the glowmasks). The Cloud is
about 2.4 blocks tall and floats a little off the floor, its eye at the height of the player's;
the Junior is a ball of the same smoke about a block across, floating at face height, its eye
much bigger for its size. Each puff is a bone of its own, so the model code can jerk them about
(the glitch: client/render/DistortionModel.kt); here they only swell and drift.

Conventions as in pale_man.py: the model faces north (-z); box UV, LAYOUT x LAYOUT units painted
at SCALE pixels per unit; cleared pixels are holes (the entities render cut out).
"""

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from observer import draw_model  # noqa: E402
from pale_man import Bone, faces, keys, swing, write_json  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
OUT = Path(__file__).resolve().parent / "output"
SCALE = 2

SMOKE = (12, 12, 15)
SMOKE_LIGHT = (30, 29, 36)
SMOKE_MID = (20, 19, 25)
GLITCH = [(150, 60, 205), (98, 26, 128), (196, 120, 250), (60, 14, 80)]
GLITCH_GLOWS = 2              # the first two of GLITCH glow
EYE_EDGE = (84, 16, 128)
EYE_MID = (156, 52, 222)
EYE_CORE = (222, 132, 255)
EYE_LINE = (34, 4, 44)
PUPIL = (8, 2, 10)
GLINT = (250, 236, 255)
CRACK = (238, 226, 255)

# (name, centre x, y, z, width, height, depth) in model units, y up from the floor.
CLOUD = {
    "layout": 128,
    "puffs": [
        ("body", 0, 20, 1, 16, 18, 12),
        ("top", 0, 32, 1, 12, 9, 10),
        ("left", -8, 23, 2, 8, 12, 9),
        ("right", 8, 18, 1, 9, 13, 9),
        ("low", -1, 9, 1, 12, 8, 10),
        ("tail", 2, 4.5, 1, 6, 5, 6),
        ("wisp_left", -5, 35, 3, 6, 6, 6),
        ("wisp_right", 6, 30, 0, 7, 7, 7),
        ("back", 0, 22, 7, 12, 14, 6),
    ],
    "eye": (0, 26, -5.5, 8, 5),          # centre x, y, z (its back on the body's front), width, height
}
JUNIOR = {
    "layout": 64,
    "puffs": [
        ("body", 0, 24, 1, 8, 8, 7),
        ("top", 0, 29, 1, 6, 4, 5),
        ("left", -4.5, 25, 1.5, 4, 5, 5),
        ("right", 4.5, 23, 1, 4, 5, 5),
        ("low", 0, 19.5, 1, 6, 3, 5),
        ("tail", 1, 17, 1, 3, 3, 3),
    ],
    "eye": (0, 25, -3, 6, 4),
}
JUNIOR_STAGES = 4


# =====================================================================================
# Geometry

def skeleton(spec):
    b = {"root": Bone("root", None, (0, 0, 0))}
    for name, cx, cy, cz, w, h, d in spec["puffs"]:
        b[name] = Bone(name, "root", (cx, cy, cz))
        b[name].cubes.append(([cx - w / 2, cy - h / 2, cz - d / 2], [w, h, d], "puff", 0.0))
    cx, cy, cz, w, h = spec["eye"]
    b["eye"] = Bone("eye", "root", (cx, cy, cz))
    b["eye"].cubes.append(([cx - w / 2, cy - h / 2, cz - 0.5], [w, h, 1], "eye", 0.0))
    return b


def box_area(size):
    w, h, d = (math.ceil(s) for s in size)
    return 2 * (w + d), d + h


def pack(bones, layout):
    items = [(bn, i, box_area(c[1])) for bn, bone in bones.items() for i, c in enumerate(bone.cubes)]
    items.sort(key=lambda it: (-it[2][1], -it[2][0]))
    uv, x, y, shelf = {}, 0, 0, 0
    for bn, i, (w, h) in items:
        if x + w > layout:
            x, y = 0, y + shelf
            shelf = 0
        if y + h > layout:
            raise SystemExit(f"the Distortion's cubes do not fit in a {layout}x{layout} texture")
        uv[(bn, i)] = (x, y)
        x += w
        shelf = max(shelf, h)
    return uv


# =====================================================================================
# Painting

class Painter:
    def __init__(self, rng, px):
        self.rng = rng
        self.img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        self.glow = Image.new("RGBA", (px, px), (0, 0, 0, 0))

    def put(self, x, y, c, amount=0, glow=False):
        d = self.rng.uniform(-amount, amount) if amount else 0
        c = tuple(max(0, min(255, int(round(ch + d)))) for ch in c[:3]) + (255,)
        self.img.putpixel((x, y), c)
        if glow:
            self.glow.putpixel((x, y), c)

    def clear(self, x, y):
        self.img.putpixel((x, y), (0, 0, 0, 0))
        self.glow.putpixel((x, y), (0, 0, 0, 0))


def smoke(p, rect):
    """A puff's face: black smoke with faint swirls, ragged at the edges, now and then a purple glitch."""
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    phase = p.rng.uniform(0, math.tau)
    for y in range(y0, y1):
        for x in range(x0, x1):
            u, v = (x - x0) / max(1, w), (y - y0) / max(1, h)
            swirl = math.sin(u * 7 + v * 3 + phase) + math.sin(v * 9 - u * 2 + phase * 2)
            c = SMOKE_LIGHT if swirl > 1.3 else SMOKE_MID if swirl > 0.5 else SMOKE
            p.put(x, y, c, 2)
            edge = min(x - x0, x1 - 1 - x, y - y0, y1 - 1 - y)
            if (edge == 0 and p.rng.random() < 0.55) or (edge == 1 and p.rng.random() < 0.25) or \
                    (edge == 2 and p.rng.random() < 0.06):
                p.clear(x, y)
    for _ in range(max(1, w * h // 90)):                           # glitch streaks, a row each
        gy = p.rng.randrange(y0 + 1, max(y0 + 2, y1 - 1))
        gx = p.rng.randrange(x0, max(x0 + 1, x1 - 2))
        k = p.rng.randrange(len(GLITCH))
        for x in range(gx, min(x1 - 1, gx + p.rng.randint(2, 6))):
            p.put(x, gy, GLITCH[k], 6, glow=k < GLITCH_GLOWS)


def eye_mask(w, h):
    """{(x, y): depth} of the eye's open pixels on a w x h face: an almond, round below, its upper
    lid flat and dipping a little in the middle (a glare). depth is 0 at its rim, 1 at the slit."""
    out = {}
    for y in range(h):
        for x in range(w):
            u = (x + 0.5) / w * 2 - 1
            if abs(u) > 0.94:
                continue
            top = h * (0.16 + 0.14 * (1 - abs(u)))                 # the lid: lowest in the middle
            bottom = h * (0.9 - 0.52 * u * u)
            yy = y + 0.5
            if top <= yy <= bottom:
                out[(x, y)] = max(0.0, 1 - abs(u) * 1.25) * min(1.0, 0.4 + (yy - top) / max(1.0, bottom - top))
    return out


def paint_eye(p, rect, cracks=0, rng=None):
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    mask = eye_mask(w, h)
    for y in range(h):
        for x in range(w):
            if (x, y) not in mask:
                near = any((x + dx, y + dy) in mask for dx in (-1, 0, 1) for dy in (-1, 0, 1))
                if near:
                    p.put(x0 + x, y0 + y, EYE_LINE, 2)               # the lids' dark rim
                else:
                    p.clear(x0 + x, y0 + y)
                continue
            t = mask[(x, y)]
            c = EYE_CORE if t > 0.55 else EYE_MID if t > 0.22 else EYE_EDGE
            p.put(x0 + x, y0 + y, c, 4, glow=True)
    cx = w // 2
    for (x, y) in mask:                                              # the slit, two pixels wide, tapering
        tip = y in (min(q[1] for q in mask if q[0] == x), max(q[1] for q in mask if q[0] == x))
        if x == cx - 1 or (x == cx and not tip):
            p.put(x0 + x, y0 + y, PUPIL, 1)
    gl = min(mask, key=lambda q: (abs(q[0] - (cx - 3)) + abs(q[1] - h * 0.45)))
    p.put(x0 + gl[0], y0 + gl[1], GLINT, 0, glow=True)               # a glint, left of the slit
    rng = rng or random.Random("eye-cracks")
    starts = [(cx - 2, int(h * 0.45)), (cx + 1, int(h * 0.6)), (cx + 1, int(h * 0.35)), (cx - 2, int(h * 0.7))]
    for k in range(min(len(starts), cracks + cracks // 3)):        # thin cracks out from the slit, longer each stage
        x, y = starts[k]
        dx = -1 if x < cx else 1
        for step in range(1 + cracks * 2):
            if (x, y) in mask:
                p.put(x0 + x, y0 + y, CRACK, 3, glow=True)
            x += dx
            y += rng.choice((-1, 0, 0, 1))


def texture(bones, uv, layout, cracks=0):
    p = Painter(random.Random("distortion-smoke-1"), layout * SCALE)
    for bn, bone in bones.items():
        for i, (origin, size, kind, inflate) in enumerate(bone.cubes):
            rects = faces(*uv[(bn, i)], size)
            if kind == "puff":
                for r in rects.values():
                    smoke(p, r)
            else:
                for name, r in rects.items():
                    if name == "north":
                        paint_eye(p, r, cracks, random.Random(f"eye-cracks-{cracks}"))
                    else:
                        for y in range(r[1], r[3]):
                            for x in range(r[0], r[2]):
                                p.put(x, y, EYE_LINE, 2)
    return p.img, p.glow


# =====================================================================================
# Files

def geo(name, bones, uv, layout, height):
    out = []
    for bn, bone in bones.items():
        entry = {"name": bn, "pivot": bone.pivot}
        if bone.parent:
            entry["parent"] = bone.parent
        cubes = [{"origin": o, "size": s, "uv": list(uv[(bn, i)])} for i, (o, s, _, _) in enumerate(bone.cubes)]
        if cubes:
            entry["cubes"] = cubes
        out.append(entry)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{name}", "texture_width": layout, "texture_height": layout,
                        "visible_bounds_width": 3, "visible_bounds_height": height, "visible_bounds_offset": [0, height / 2, 0]},
        "bones": out}]}


def breathing(spec, length, swell, drift):
    """Every puff swelling and drifting a little, each in its own time."""
    bones = {}
    for i, (name, *_rest) in enumerate(spec["puffs"]):
        phase = i * 2.1
        s = swing(length, swell, phase, offset=(1, 1, 1))
        bones[name] = {
            "scale": {t: [v[0], v[0], v[0]] for t, v in s.items()},
            "position": swing(length, drift, phase + 1, axis=i % 2),
        }
    return bones


def cloud_animations():
    A = {}
    idle = breathing(CLOUD, 4.0, 0.06, 0.5)
    # Now and then the eye narrows, glaring.
    idle["eye"] = {"scale": keys((0, [1, 1, 1]), (2.6, [1, 1, 1]), (2.75, [1.05, 0.5, 1]), (3.2, [1.05, 0.5, 1]), (3.4, [1, 1, 1]))}
    A["animation.distortion_cloud.idle"] = {"loop": True, "animation_length": 4.0, "bones": idle}
    leave = {}
    for i, (name, *_rest) in enumerate(CLOUD["puffs"]):
        start = 0.1 + 0.07 * i
        leave[name] = {"scale": keys((0, [1, 1, 1]), (start, [1.1, 1.1, 1.1]), (start + 0.4, [0, 0, 0]), (1.2, [0, 0, 0]))}
    leave["eye"] = {"scale": keys((0, [1, 1, 1]), (0.2, [1.2, 0.1, 1]), (0.3, [0, 0, 0]), (1.2, [0, 0, 0]))}
    A["animation.distortion_cloud.leave"] = {"loop": "hold_on_last_frame", "animation_length": 1.2, "bones": leave}
    return {"format_version": "1.8.0", "animations": A, "geckolib_format_version": 2}


def junior_animations():
    A = {}
    idle = breathing(JUNIOR, 2.4, 0.08, 0.3)
    idle["root"] = {"position": swing(2.4, 0.8, axis=1)}             # bobbing in the air
    A["animation.distortion_junior.idle"] = {"loop": True, "animation_length": 2.4, "bones": idle}
    die = {}
    for i, (name, *_rest) in enumerate(JUNIOR["puffs"]):
        die[name] = {"scale": keys((0, [1, 1, 1]), (0.15 + 0.05 * i, [1.25, 1.25, 1.25]), (0.5 + 0.05 * i, [0, 0, 0]), (1.0, [0, 0, 0]))}
    die["eye"] = {"scale": keys((0, [1, 1, 1]), (0.1, [1.3, 1.3, 1]), (0.35, [0, 0, 0]), (1.0, [0, 0, 0])),
                  "rotation": keys((0, [0, 0, 0]), (0.05, [0, 0, 12]), (0.1, [0, 0, -12]), (0.15, [0, 0, 8]), (0.2, [0, 0, 0]))}
    A["animation.distortion_junior.die"] = {"loop": "hold_on_last_frame", "animation_length": 1.0, "bones": die}
    return {"format_version": "1.8.0", "animations": A, "geckolib_format_version": 2}


# =====================================================================================
# Preview

def write_preview(models, junior_stages):
    """models: [(label, bones, uv, tex, layout)]; junior_stages: the Junior's eye at each stage."""
    tex_shown = 256
    h = 460
    sheet = Image.new("RGB", (tex_shown * 2 + 4 * 200 + 60, h + 120), (46, 48, 56))
    d = ImageDraw.Draw(sheet)
    for n, (label, bones, uv, tex, layout) in enumerate(models):
        sheet.paste(tex.resize((tex_shown, tex_shown), Image.NEAREST).convert("RGB"), (10 + n * (tex_shown + 10), 10))
        for k, view in enumerate(("front", "side")):
            ox = tex_shown * 2 + 40 + (n * 2 + k) * 200
            d.text((ox, 10), f"{label}, {view}", fill=(220, 220, 220))
            base = h - 10
            d.line((ox, base, ox + 180, base), fill=(110, 110, 110))
            for blocks in (1, 1.62, 2):                               # a block, the player's eyes, two blocks
                y = base - blocks * 160
                d.line((ox, y, ox + 180, y), fill=(80, 70, 70))
                d.text((ox + 150, y - 12), f"{blocks}", fill=(120, 110, 110))
            draw_model(sheet, bones, uv, tex, ox + 90, base, view)
    for k, eye in enumerate(junior_stages):
        sheet.paste(eye.resize((eye.width * 8, eye.height * 8), Image.NEAREST).convert("RGB"), (10 + k * 110, h + 20))
    d.text((10, h + 4), "the Junior's eye, stage by stage", fill=(220, 220, 220))
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / "distortion_preview.png")


def main():
    tex_dir = ASSETS / "textures" / "entity"
    tex_dir.mkdir(parents=True, exist_ok=True)
    models = []

    bones = skeleton(CLOUD)
    uv = pack(bones, CLOUD["layout"])
    tex, glow = texture(bones, uv, CLOUD["layout"])
    write_json(ASSETS / "geo" / "entity" / "distortion_cloud.geo.json", geo("distortion_cloud", bones, uv, CLOUD["layout"], 3))
    write_json(ASSETS / "animations" / "entity" / "distortion_cloud.animation.json", cloud_animations())
    tex.save(tex_dir / "distortion_cloud.png")
    glow.save(tex_dir / "distortion_cloud_glowmask.png")
    models.append(("Cloud", bones, uv, tex, CLOUD["layout"]))

    bones = skeleton(JUNIOR)
    uv = pack(bones, JUNIOR["layout"])
    write_json(ASSETS / "geo" / "entity" / "distortion_junior.geo.json", geo("distortion_junior", bones, uv, JUNIOR["layout"], 2.5))
    write_json(ASSETS / "animations" / "entity" / "distortion_junior.animation.json", junior_animations())
    eyes = []
    for stage in range(JUNIOR_STAGES):
        tex, glow = texture(bones, uv, JUNIOR["layout"], cracks=stage)
        tex.save(tex_dir / f"distortion_junior_{stage}.png")
        glow.save(tex_dir / f"distortion_junior_{stage}_glowmask.png")
        eyes.append(tex.crop(faces(*uv[("eye", 0)], bones["eye"].cubes[0][1])["north"]))
        if stage == 0:
            models.append(("Junior", bones, uv, tex, JUNIOR["layout"]))
    write_preview(models, eyes)

    lang_path = ASSETS / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    lang["entity.every_tunnel.distortion_cloud"] = "Distortion Cloud"
    lang["entity.every_tunnel.distortion_junior"] = "Distortion Junior"
    write_json(lang_path, lang)
    print(f"Distortion Cloud: {len(CLOUD['puffs'])} puffs; Junior: {len(JUNIOR['puffs'])} puffs, {JUNIOR_STAGES} eye stages")


if __name__ == "__main__":
    sys.exit(main())
