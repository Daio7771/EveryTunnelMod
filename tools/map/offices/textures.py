"""
Level 3's own textures: the offices in perfect order, in the mod's cold palette. Clean
surfaces, no wear: blue-grey carpet tiles, off-white plaster with a grey skirting board,
white acoustic ceiling tiles, light grey vinyl in the corridors and service rooms; dark wood
panelling and a muted green carpet in the library; plain concrete in the bunker.

Like the garage's (garage/textures.py), each texture re-skins a stand-in vanilla block
(STAND_INS in LEVEL_3), with LabPBR maps for shader packs, in the same world resource pack
(add_to_pack). The stand-ins are blocks Levels 1 and 2 never use, so the two sets of
textures never meet. Texture names start with "b3_".
"""

import io
import json
import random
import zipfile

from garage import pbr
from garage import textures as T
from garage.pbr import Material

NS = T.NS

CARPET = Material(0.04, porosity=0.9, relief=0.35)
VINYL = Material(0.55, porosity=0.05, relief=0.15)
PLASTER = Material(0.16, porosity=0.4, relief=0.2)
SKIRTING = Material(0.45, porosity=0.05, relief=0.2)
WOOD = Material(0.42, porosity=0.2, relief=0.5)
TILE = Material(0.05, porosity=0.6, relief=0.5)
CONCRETE = Material(0.14, porosity=0.7, relief=0.6)

OFFICE_CARPET = (84, 94, 108)
LIBRARY_CARPET = (66, 84, 74)
VINYL_GREY = (170, 174, 176)
PLASTER_WHITE = (212, 214, 210)
SKIRTING_GREY = (88, 92, 98)
WOOD_DARK = (72, 52, 38)
TILE_WHITE = (220, 221, 216)
CONCRETE_GREY = (134, 138, 142)


def carpet_tex(rng, base, shade):
    img = T.new(tuple(T.clamp(c + shade) for c in base))
    T.noise(img, rng, 6)
    T.noise(img, rng, 3, grain=2)
    T.speckle(img, rng, 18, tuple(c + 14 for c in base), 0.5)       # loops catching the light
    T.speckle(img, rng, 14, tuple(c - 16 for c in base), 0.5)
    for i in range(16):                                              # the tiles' seams, every half block
        for j in (0, 8):
            T.px(img, i, j, tuple(c - 18 for c in base), 0.3)
            T.px(img, j, i, tuple(c - 18 for c in base), 0.3)
    return img


def vinyl_tex(rng, kind):
    img = T.new(VINYL_GREY)
    T.noise(img, rng, 3)
    T.speckle(img, rng, 14, (126, 130, 134), 0.6)                    # flecks in the vinyl
    T.speckle(img, rng, 10, (196, 198, 200), 0.6)
    for i in range(16):
        T.px(img, i, 0, (148, 152, 154), 0.55)
        T.px(img, 0, i, (148, 152, 154), 0.55)
    if kind == "sheen":
        T.blob(img, rng, (186, 190, 192), rng.uniform(4, 6), 0.3)     # polish
    return img


def plaster_tex(rng, kind):
    img = T.new(PLASTER_WHITE)
    T.noise(img, rng, 2)
    T.noise(img, rng, 2, grain=2)
    if kind == "shade":
        T.blob(img, rng, (204, 206, 202), rng.uniform(5, 8), 0.3)
    return img


def skirting_tex(rng):
    img = plaster_tex(rng, "plain")
    T.rect(img, 0, 12, 15, 15, SKIRTING_GREY)
    T.noise(img, rng, 2)
    for x in range(16):
        T.px(img, x, 12, (122, 126, 132), 0.8)                         # the board's rounded top
        T.px(img, x, 15, (70, 74, 80), 0.6)
    return img


def wood_tex(rng, kind):
    img = T.new(WOOD_DARK)
    for x in range(16):                                              # vertical grain
        shade = rng.uniform(-8, 8)
        for y in range(16):
            T.px(img, x, y, tuple(T.clamp(c + shade) for c in WOOD_DARK), 0.8)
    T.noise(img, rng, 3)
    for _ in range(3):
        x = rng.randrange(1, 15)
        for y in range(16):
            if rng.random() < 0.7:
                T.px(img, x, y, (58, 40, 28), 0.5)
    if kind == "plain":                                              # no frame: the furniture's wood
        return img
    for i in range(16):                                              # the panel's frame
        T.px(img, 0, i, (48, 34, 24), 0.8)
        T.px(img, 1, i, (96, 72, 54), 0.35)
        if kind == "rail":
            T.px(img, i, 0, (48, 34, 24), 0.8)
            T.px(img, i, 1, (96, 72, 54), 0.35)
    return img


def tile_tex(rng, kind):
    img = T.new(TILE_WHITE)
    T.noise(img, rng, 2)
    for _ in range(26):                                              # acoustic fissures
        x, y = rng.randrange(16), rng.randrange(16)
        T.px(img, x, y, (186, 187, 182), 0.6)
        if rng.random() < 0.5:
            T.px(img, x + 1, y, (196, 197, 192), 0.5)
    for i in range(16):                                              # the T-bar grid
        T.px(img, i, 0, (236, 236, 232), 0.9)
        T.px(img, 0, i, (236, 236, 232), 0.9)
    if kind == "plain":
        T.blob(img, rng, (226, 227, 222), 5, 0.2)
    return img


def concrete_tex(rng, kind):
    base = CONCRETE_GREY if kind == "wall" else (112, 116, 120)
    img = T.new(base)
    T.noise(img, rng, 4)
    T.noise(img, rng, 3, grain=2)
    T.speckle(img, rng, 10, tuple(c + 14 for c in base), 0.5)
    T.pores(img, rng, 4, tuple(c - 30 for c in base), 0.6)
    return img


def material_for(name):
    if name.startswith(("b3_carpet", "b3_library_carpet")):
        return CARPET
    if name.startswith("b3_vinyl"):
        return VINYL
    if name.startswith("b3_skirting"):
        return lambda x, y, c: SKIRTING if y >= 12 else PLASTER
    if name.startswith("b3_plaster"):
        return PLASTER
    if name.startswith("b3_wood"):
        return WOOD
    if name.startswith("b3_tile"):
        return TILE
    return CONCRETE


# Each role: (texture name prefix, kinds, painter, rotate the variants about y).
ROLES = {
    "carpet": ("b3_carpet", [0, -3, 3], lambda g, k: carpet_tex(g, OFFICE_CARPET, k), True),
    "library_carpet": ("b3_library_carpet", [0, -3, 3], lambda g, k: carpet_tex(g, LIBRARY_CARPET, k), True),
    "vinyl": ("b3_vinyl", ["plain", "plain", "sheen"], vinyl_tex, True),
    "plaster": ("b3_plaster", ["plain", "plain", "shade"], plaster_tex, False),
    "skirting": ("b3_skirting", ["s"], lambda g, k: skirting_tex(g), False),
    "wood": ("b3_wood", ["panel", "panel", "rail"], wood_tex, False),
    "ceiling_tile": ("b3_tile", ["plain", "fissured"], tile_tex, True),
    "concrete": ("b3_concrete", ["wall", "wall"], concrete_tex, False),
    "concrete_floor": ("b3_concrete_floor", ["floor", "floor"], concrete_tex, True),
}


def stairs_blockstate(model):
    """The full blockstate of a stairs block, as vanilla's, for the models
    `model`, `model`_inner and `model`_outer."""
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y0 in base_y.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                name = model + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                y = y0
                if shape.endswith("left") and half == "bottom":
                    y -= 90
                if shape.endswith("right") and half == "top":
                    y += 90
                v = {"model": f"{NS}:block/{name}"}
                if half == "top":
                    v["x"] = 180
                if y % 360:
                    v["y"] = y % 360
                if "x" in v or "y" in v:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": variants}


def files(params):
    """{path in the pack: bytes} for Level 3's textures, models and blockstates."""
    S = params.STAND_INS
    out = {}

    def png(img):
        buf = io.BytesIO()
        img.save(buf, "PNG")
        return buf.getvalue()

    for role, (prefix, kinds, painter, rotate) in ROLES.items():
        variants = []
        for i, kind in enumerate(kinds):
            name = f"{prefix}_{i}"
            img = painter(random.Random(f"{params.TEXTURE_SEED}-{name}"), kind)
            normal, specular = pbr.maps(img, material_for(name), params.PBR["RELIEF"], None, True)
            path = f"assets/{NS}/textures/block/{name}"
            out[f"{path}.png"], out[f"{path}_n.png"], out[f"{path}_s.png"] = png(img), png(normal), png(specular)
            out[f"assets/{NS}/models/block/{name}.json"] = json.dumps(T.cube_model(all_=name), indent=1)
            for rot in ((0, 90, 180, 270) if rotate else (0,)):
                variants.append({"model": f"{NS}:block/{name}", **({"y": rot} if rot else {})})
        out[f"assets/minecraft/blockstates/{S[role].split(':')[1]}.json"] = json.dumps({"variants": {"": variants}}, indent=1)

    stairs_tex = {k: f"{NS}:block/b3_vinyl_0" for k in ("bottom", "top", "side")}
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        out[f"assets/{NS}/models/block/b3_stairs{suffix}.json"] = json.dumps(
            {"parent": f"minecraft:block/{parent}", "textures": stairs_tex}, indent=1)
    out[f"assets/minecraft/blockstates/{S['stairs'].split(':')[1]}.json"] = json.dumps(stairs_blockstate("b3_stairs"), indent=1)
    return {k: v if isinstance(v, bytes) else v.encode() for k, v in out.items()}


def add_to_pack(pack_zip, params):
    """The world resource pack with Level 3's files added: (zip bytes, {texture name: png})
    of Level 3's textures alone, for their sheet. A path already in the pack is an error."""
    extra = files(params)
    with zipfile.ZipFile(io.BytesIO(pack_zip)) as z:
        existing = {n: z.read(n) for n in z.namelist()}
    clash = sorted(set(existing) & set(extra))
    if clash:
        raise SystemExit(f"Level 3 textures: these pack files already exist: {clash[:4]}")
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted({**existing, **extra}):
            z.writestr(path, existing.get(path) or extra[path])
    textures = {p.split("/")[-1][:-4]: d for p, d in extra.items() if p.endswith(".png")}
    return out.getvalue(), textures
