"""
Level 4's own textures: the VIP lounge, warm and in perfect order. Burgundy carpet, dark walnut
panels with a brass strip, cream walls, a cream ceiling with panels.

Like Level 3's (offices/textures.py), each texture re-skins a stand-in vanilla block (STAND_INS in
LEVEL_4), with LabPBR maps for shader packs, in the same world resource pack (add_to_pack). Texture
names start with "b4_".
"""

import io
import json
import random
import zipfile

from garage import pbr
from garage import textures as T
from garage.pbr import Material
from offices.textures import stairs_blockstate

NS = T.NS

CARPET = Material(0.04, porosity=0.9, relief=0.35)
WALNUT = Material(0.5, porosity=0.15, relief=0.45)
BRASS = Material(0.72, metal=231, relief=0.15)
CREAM = Material(0.2, porosity=0.35, relief=0.15)

BURGUNDY = (92, 26, 38)
WALNUT_DARK = (58, 38, 26)
WALNUT_LIGHT = (84, 56, 38)
BRASS_YELLOW = (186, 146, 74)
CREAM_WALL = (222, 206, 172)
CREAM_CEILING = (232, 220, 194)


def carpet_tex(rng, kind):
    img = T.new(tuple(T.clamp(c + kind) for c in BURGUNDY))
    T.noise(img, rng, 5)
    T.noise(img, rng, 3, grain=2)
    T.speckle(img, rng, 14, tuple(c + 16 for c in BURGUNDY), 0.45)
    for i in (2, 10):                                                 # a small repeated figure in the pile
        for j in (2, 10):
            for a, b in ((0, 0), (1, 1), (2, 0), (1, -1)):
                T.px(img, (i + a) % 16, (j + b) % 16, BRASS_YELLOW, 0.22)
    return img


def walnut_tex(rng, kind):
    img = T.new(WALNUT_DARK)
    T.noise(img, rng, 3)
    for x in range(16):                                               # upright grain
        shade = rng.uniform(0, 1)
        for y in range(16):
            T.px(img, x, y, WALNUT_LIGHT, 0.25 * shade)
    for _ in range(10):
        x, y = rng.randrange(16), rng.randrange(16)
        for k in range(rng.randint(2, 5)):
            T.px(img, x, (y + k) % 16, (40, 26, 18), 0.5)
    for i in range(16):                                               # the panels' joints
        T.px(img, 0, i, (34, 22, 16), 0.7)
    if kind == "rail":
        for i in range(16):
            for y, t in ((0, 0.9), (1, 1.0), (2, 0.8)):
                T.px(img, i, y, BRASS_YELLOW, t)
            T.px(img, i, 3, (30, 20, 14), 0.8)
    return img


def cream_tex(rng, kind):
    img = T.new(CREAM_WALL)
    T.noise(img, rng, 2)
    if kind == "figure":                                              # a faint damask
        for a, b in ((8, 3), (7, 4), (9, 4), (6, 5), (10, 5), (7, 6), (9, 6), (8, 7), (0, 11), (15, 12), (1, 12), (0, 13)):
            T.px(img, a, b, (196, 176, 138), 0.5)
    return img


def ceiling_tex(rng, kind):
    img = T.new(CREAM_CEILING)
    T.noise(img, rng, 2)
    for i in range(16):                                               # a sunk panel, a block each
        for j, t in ((0, 0.8), (15, 0.8)):
            T.px(img, i, j, (176, 158, 124), t)
            T.px(img, j, i, (176, 158, 124), t)
        for j in (1, 14):
            T.px(img, i, j, (246, 238, 218), 0.5)
            T.px(img, j, i, (246, 238, 218), 0.5)
    return img


def calm_carpet_tex(rng, kind):
    """The Calm room's floor: a deep teal, with flecks of every colour."""
    img = T.new((36, 104, 112))
    T.noise(img, rng, 5)
    for colour in ((214, 160, 60), (180, 70, 110), (120, 170, 210), (120, 80, 170)):
        T.speckle(img, rng, 6, colour, 0.7)
    return img


def calm_wall_tex(rng, kind):
    """The Calm room's walls: soft upright stripes in pastels."""
    colours = ((226, 176, 186), (176, 206, 226), (196, 226, 190), (232, 214, 160))
    img = T.new(colours[0])
    for x in range(16):
        for y in range(16):
            T.px(img, x, y, colours[x // 4], 1.0)
    T.noise(img, rng, 2)
    return img


def material_for(name):
    if name.startswith(("b4_carpet", "b4_calm_carpet")):
        return CARPET
    if name.startswith("b4_rail"):
        return lambda x, y, c: BRASS if y < 3 else WALNUT
    if name.startswith("b4_walnut"):
        return WALNUT
    return CREAM


# Each role: (texture name prefix, kinds, painter, rotate the variants about y).
ROLES = {
    "carpet": ("b4_carpet", [0, -2, 2], carpet_tex, True),
    "walnut": ("b4_walnut", ["panel", "panel"], walnut_tex, False),
    "rail": ("b4_rail", ["rail"], walnut_tex, False),
    "cream": ("b4_cream", ["plain", "plain", "figure"], cream_tex, False),
    "ceiling": ("b4_ceiling", ["panel"], ceiling_tex, True),
    "calm_carpet": ("b4_calm_carpet", ["a", "a"], calm_carpet_tex, True),
    "calm_wall": ("b4_calm_wall", ["a"], calm_wall_tex, False),
}


def files(params, seed, relief):
    """{path in the pack: bytes} for Level 4's textures, models and blockstates."""
    S = params["STAND_INS"]
    out = {}

    def png(img):
        buf = io.BytesIO()
        img.save(buf, "PNG")
        return buf.getvalue()

    for role, (prefix, kinds, painter, rotate) in ROLES.items():
        variants = []
        for i, kind in enumerate(kinds):
            name = f"{prefix}_{i}"
            img = painter(random.Random(f"{seed}-{name}"), kind)
            normal, specular = pbr.maps(img, material_for(name), relief, None, True)
            path = f"assets/{NS}/textures/block/{name}"
            out[f"{path}.png"], out[f"{path}_n.png"], out[f"{path}_s.png"] = png(img), png(normal), png(specular)
            out[f"assets/{NS}/models/block/{name}.json"] = json.dumps(T.cube_model(all_=name), indent=1)
            for rot in ((0, 90, 180, 270) if rotate else (0,)):
                variants.append({"model": f"{NS}:block/{name}", **({"y": rot} if rot else {})})
        out[f"assets/minecraft/blockstates/{S[role].split(':')[1]}.json"] = json.dumps({"variants": {"": variants}}, indent=1)

    stairs_tex = {"bottom": f"{NS}:block/b4_walnut_0", "top": f"{NS}:block/b4_carpet_0", "side": f"{NS}:block/b4_walnut_0"}
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        out[f"assets/{NS}/models/block/b4_stairs{suffix}.json"] = json.dumps(
            {"parent": f"minecraft:block/{parent}", "textures": stairs_tex}, indent=1)
    out[f"assets/minecraft/blockstates/{S['stairs'].split(':')[1]}.json"] = json.dumps(stairs_blockstate("b4_stairs"), indent=1)
    return {k: v if isinstance(v, bytes) else v.encode() for k, v in out.items()}


def add_to_pack(pack_zip, params, seed, relief):
    """The world resource pack with Level 4's files added: (zip bytes, {texture name: png}) of
    Level 4's textures alone, for their sheet. A path already in the pack is an error."""
    extra = files(params, seed, relief)
    with zipfile.ZipFile(io.BytesIO(pack_zip)) as z:
        existing = {n: z.read(n) for n in z.namelist()}
    clash = sorted(set(existing) & set(extra))
    if clash:
        raise SystemExit(f"Level 4 textures: these pack files already exist: {clash[:4]}")
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted({**existing, **extra}):
            z.writestr(path, existing.get(path) or extra[path])
    textures = {p.split("/")[-1][:-4]: d for p, d in extra.items() if p.endswith(".png")}
    return out.getvalue(), textures
