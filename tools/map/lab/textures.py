"""
Level 5's own textures: a clean laboratory. White wall tiles with a band of pale surgical green ones at
their foot, grey speckled vinyl on the floor, a ceiling of white perforated panels; the airlock's
riveted steel panels, which the stairs up from the drain are made of too. Under it the drain: stained
concrete, wet underfoot, a dark tide line along the foot of the walls, the rubble of a collapse. Up its
stairs the director's office: polished dark stone, pale panels over a dark steel skirting, a white
ceiling.

Like Level 4's (lounge/textures.py), each texture re-skins a stand-in vanilla block (STAND_INS in
LEVEL_5), with LabPBR maps for shader packs, in the same world resource pack (add_to_pack). Texture
names start with "b5_".
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

TILE = Material(0.7, porosity=0.05, relief=0.25)
VINYL = Material(0.45, porosity=0.1, relief=0.2)
PANEL = Material(0.15, porosity=0.4, relief=0.3)
STEEL = Material(0.55, metal=230, relief=0.35)

WHITE = (226, 230, 228)
GROUT = (188, 194, 192)
GREEN = (150, 192, 168)
GREEN_GROUT = (116, 156, 134)
GREY = (140, 146, 148)
PANEL_WHITE = (222, 224, 220)


def tiles(rng, base, grout):
    """Square tiles, four to a block face, with their grout lines."""
    img = T.new(base)
    T.noise(img, rng, 2)
    for i in range(16):
        for j in (0, 8):
            T.px(img, i, j, grout, 0.9)
            T.px(img, j, i, grout, 0.9)
    for tx in (0, 8):                                                 # a glaze highlight on each tile
        for ty in (0, 8):
            T.px(img, tx + 2, ty + 2, (255, 255, 255), 0.35)
            T.px(img, tx + 3, ty + 2, (255, 255, 255), 0.2)
    return img


def tile_tex(rng, kind):
    img = tiles(rng, WHITE, GROUT)
    if kind == "worn":                                                # a hairline crack, now and then
        x, y = rng.randrange(1, 7), rng.randrange(9, 15)
        for k in range(5):
            T.px(img, x + k, y - k // 2, (170, 176, 174), 0.6)
    return img


def band_tex(rng, kind):
    img = tiles(rng, GREEN, GREEN_GROUT)
    for i in range(16):                                               # the band's top edge
        T.px(img, i, 0, (96, 132, 112), 1.0)
    return img


def vinyl_tex(rng, kind):
    img = T.new(tuple(T.clamp(c + kind) for c in GREY))
    T.noise(img, rng, 3)
    T.speckle(img, rng, 18, (110, 116, 118), 0.6)
    T.speckle(img, rng, 14, (178, 184, 186), 0.6)
    T.speckle(img, rng, 4, (96, 140, 120), 0.4)                       # a fleck of the walls' green
    return img


def panel_tex(rng, kind):
    img = T.new(PANEL_WHITE)
    T.noise(img, rng, 2)
    for i in range(16):                                               # the panel's frame
        for j in (0, 15):
            T.px(img, i, j, (176, 180, 178), 0.8)
            T.px(img, j, i, (176, 180, 178), 0.8)
    for x in range(2, 15, 3):                                         # perforations
        for y in range(2, 15, 3):
            if rng.random() < 0.85:
                T.px(img, x, y, (192, 194, 190), 0.7)
    return img


def steel_tex(rng, kind):
    """The airlock: brushed steel panels, a block each, riveted at their corners."""
    img = T.new((150, 156, 160))
    T.noise(img, rng, 3)
    for y in range(16):                                               # brushed along
        streak = rng.uniform(-1, 1)
        for x in range(16):
            T.px(img, x, y, (190, 196, 200) if streak > 0 else (124, 128, 132), 0.12 * abs(streak))
    for i in range(16):                                               # the panel's seams
        T.px(img, i, 0, (92, 96, 100), 0.8)
        T.px(img, 0, i, (92, 96, 100), 0.8)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):                 # rivets
        T.px(img, x, y, (210, 214, 218), 0.9)
        T.px(img, x + 1, y + 1, (96, 100, 104), 0.6)
    return img


# --- the drain -------------------------------------------------------------------------

CONCRETE = Material(0.15, porosity=0.6, relief=0.5)
WET = Material(0.5, porosity=0.35, relief=0.45)
POLISHED = Material(0.85, relief=0.1)
PLASTER = Material(0.2, porosity=0.3, relief=0.15)
RUST = (118, 78, 50)


def stains(img, rng, streaks, colours):
    """Streaks running down from the top, a pixel wide, fading."""
    for _ in range(streaks):
        x, colour = rng.randrange(16), rng.choice(colours)
        for y in range(rng.randrange(4, 16)):
            T.px(img, x, y, colour, 0.5 * (1 - y / 16))


def drain_floor_tex(rng, kind):
    img = T.new((88, 88, 82))
    T.noise(img, rng, 5)
    for _ in range(2):
        T.blob(img, rng, (64, 66, 58), 4, 0.45)                       # wet patches
    T.speckle(img, rng, 12, (112, 110, 102), 0.6)
    if kind == "cracked":
        x, y = rng.randrange(2, 8), rng.randrange(2, 8)
        for k in range(8):
            T.px(img, x + k, y + (k * 2) // 3, (48, 48, 44), 0.8)
    return img


def drain_wall_tex(rng, kind):
    img = T.new((118, 116, 108))
    T.noise(img, rng, 4)
    for x, y in ((3, 4), (12, 4), (3, 12), (12, 12)):                 # the formwork's tie holes
        T.px(img, x, y, (70, 68, 62), 0.9)
    stains(img, rng, 4 if kind == "stained" else 2, [RUST, (78, 76, 68), (96, 104, 84)])
    T.blob(img, rng, (98, 96, 86), 4, 0.3)
    return img


def drain_band_tex(rng, kind):
    img = T.new((80, 82, 68))
    T.noise(img, rng, 4)
    T.blob(img, rng, (60, 66, 50), 5, 0.5)                            # silt and slime
    for x in range(16):                                               # the tide line along its top
        T.px(img, x, 0, (70, 92, 58), 0.9)
        T.px(img, x, 1, (88, 98, 70), 0.5)
    return img


def drain_ceiling_tex(rng, kind):
    img = T.new((98, 98, 94))
    T.noise(img, rng, 4)
    T.speckle(img, rng, 6, (58, 64, 54), 0.7)                         # mould
    T.blob(img, rng, (80, 76, 66), 5, 0.35)
    return img


def rubble_tex(rng, kind):
    img = T.new((104, 102, 96))
    for _ in range(7):                                                # chunks of concrete, greys apart
        T.blob(img, rng, rng.choice([(130, 128, 120), (86, 84, 78), (116, 112, 104)]), rng.randrange(2, 5), 0.8)
    T.speckle(img, rng, 16, (46, 46, 42), 0.9)                        # the gaps between them
    y = rng.randrange(3, 13)
    for x in range(rng.randrange(0, 4), rng.randrange(10, 16)):       # a bent bit of rebar
        T.px(img, x, y + (x // 5) % 2, RUST, 0.9)
    return img


# --- the director's office ---------------------------------------------------------------

def office_floor_tex(rng, kind):
    img = T.new((40, 40, 44))
    T.noise(img, rng, 2)
    for _ in range(2):                                                # faint veins in the stone
        x, y = rng.randrange(16), rng.randrange(16)
        for k in range(12):
            T.px(img, (x + k) % 16, (y + k // 3) % 16, (74, 74, 80), 0.35)
    for i in range(16):                                               # the joint round the slab
        T.px(img, i, 0, (26, 26, 28), 0.8)
        T.px(img, 0, i, (26, 26, 28), 0.8)
    return img


def office_wall_tex(rng, kind):
    img = T.new((198, 194, 188))
    T.noise(img, rng, 1)
    for y in range(16):                                               # the seam between panels
        T.px(img, 0, y, (170, 166, 160), 0.7)
    return img


def office_band_tex(rng, kind):
    img = T.new((60, 62, 66))
    T.noise(img, rng, 2)
    for y in range(16):
        streak = rng.uniform(-1, 1)
        for x in range(16):
            T.px(img, x, y, (96, 98, 104) if streak > 0 else (44, 46, 50), 0.15 * abs(streak))
    for x in range(16):                                               # its top edge, catching the light
        T.px(img, x, 0, (130, 132, 138), 0.8)
    return img


def office_ceiling_tex(rng, kind):
    img = T.new((234, 234, 230))
    T.noise(img, rng, 1)
    return img


def material_for(name):
    if name.startswith("b5_steel"):
        return STEEL
    if name.startswith("b5_vinyl"):
        return VINYL
    if name.startswith("b5_ceiling"):
        return PANEL
    return TILE


# Each role: (texture name prefix, kinds, painter, rotate the variants about y, material or None for
# material_for's).
ROLES = {
    "tile": ("b5_tile", ["plain", "plain", "plain", "worn"], tile_tex, False, None),
    "band": ("b5_band", ["plain"], band_tex, False, None),
    "vinyl": ("b5_vinyl", [0, -3, 3], vinyl_tex, True, None),
    "ceiling": ("b5_ceiling", ["panel"], panel_tex, True, None),
    "steel": ("b5_steel", ["panel"], steel_tex, False, None),
    "drain_floor": ("b5_drain_floor", ["plain", "plain", "cracked"], drain_floor_tex, True, WET),
    "drain_wall": ("b5_drain_wall", ["plain", "stained", "stained"], drain_wall_tex, False, CONCRETE),
    "drain_band": ("b5_drain_band", ["plain", "plain"], drain_band_tex, False, CONCRETE),
    "drain_ceiling": ("b5_drain_ceiling", ["plain", "plain"], drain_ceiling_tex, True, CONCRETE),
    "rubble": ("b5_rubble", ["plain", "plain"], rubble_tex, True, CONCRETE),
    "office_floor": ("b5_office_floor", ["plain", "plain"], office_floor_tex, True, POLISHED),
    "office_wall": ("b5_office_wall", ["plain"], office_wall_tex, False, PLASTER),
    "office_band": ("b5_office_band", ["plain"], office_band_tex, False, STEEL),
    "office_ceiling": ("b5_office_ceiling", ["plain"], office_ceiling_tex, False, PLASTER),
}


def files(params, seed, relief):
    """{path in the pack: bytes} for Level 5's textures, models and blockstates."""
    S = params["STAND_INS"]
    out = {}

    def png(img):
        buf = io.BytesIO()
        img.save(buf, "PNG")
        return buf.getvalue()

    for role, (prefix, kinds, painter, rotate, material) in ROLES.items():
        variants = []
        for i, kind in enumerate(kinds):
            name = f"{prefix}_{i}"
            img = painter(random.Random(f"{seed}-{name}"), kind)
            normal, specular = pbr.maps(img, material or material_for(name), relief, None, True)
            path = f"assets/{NS}/textures/block/{name}"
            out[f"{path}.png"], out[f"{path}_n.png"], out[f"{path}_s.png"] = png(img), png(normal), png(specular)
            out[f"assets/{NS}/models/block/{name}.json"] = json.dumps(T.cube_model(all_=name), indent=1)
            for rot in ((0, 90, 180, 270) if rotate else (0,)):
                variants.append({"model": f"{NS}:block/{name}", **({"y": rot} if rot else {})})
        out[f"assets/minecraft/blockstates/{S[role].split(':')[1]}.json"] = json.dumps({"variants": {"": variants}}, indent=1)

    stairs_tex = {"bottom": f"{NS}:block/b5_vinyl_0", "top": f"{NS}:block/b5_vinyl_0", "side": f"{NS}:block/b5_band_0"}
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        out[f"assets/{NS}/models/block/b5_stairs{suffix}.json"] = json.dumps(
            {"parent": f"minecraft:block/{parent}", "textures": stairs_tex}, indent=1)
    out[f"assets/minecraft/blockstates/{S['stairs'].split(':')[1]}.json"] = json.dumps(stairs_blockstate("b5_stairs"), indent=1)
    # The steel stairs up from the drain, and the collapse's slabs of rubble.
    steel = {"bottom": f"{NS}:block/b5_steel_0", "top": f"{NS}:block/b5_steel_0", "side": f"{NS}:block/b5_steel_0"}
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        out[f"assets/{NS}/models/block/b5_steel_stairs{suffix}.json"] = json.dumps(
            {"parent": f"minecraft:block/{parent}", "textures": steel}, indent=1)
    out[f"assets/minecraft/blockstates/{S['steel_stairs'].split(':')[1]}.json"] = json.dumps(stairs_blockstate("b5_steel_stairs"), indent=1)
    rubble = {"bottom": f"{NS}:block/b5_rubble_0", "top": f"{NS}:block/b5_rubble_0", "side": f"{NS}:block/b5_rubble_1"}
    for name, parent in (("b5_rubble_slab", "slab"), ("b5_rubble_slab_top", "slab_top")):
        out[f"assets/{NS}/models/block/{name}.json"] = json.dumps({"parent": f"minecraft:block/{parent}", "textures": rubble}, indent=1)
    out[f"assets/minecraft/blockstates/{S['rubble_slab'].split(':')[1]}.json"] = json.dumps({"variants": {
        "type=bottom": {"model": f"{NS}:block/b5_rubble_slab"}, "type=top": {"model": f"{NS}:block/b5_rubble_slab_top"},
        "type=double": {"model": f"{NS}:block/b5_rubble_0"}}}, indent=1)
    return {k: v if isinstance(v, bytes) else v.encode() for k, v in out.items()}


def add_to_pack(pack_zip, params, seed, relief):
    """The world resource pack with Level 5's files added: (zip bytes, {texture name: png}) of
    Level 5's textures alone, for their sheet. A path already in the pack is an error."""
    extra = files(params, seed, relief)
    with zipfile.ZipFile(io.BytesIO(pack_zip)) as z:
        existing = {n: z.read(n) for n in z.namelist()}
    clash = sorted(set(existing) & set(extra))
    if clash:
        raise SystemExit(f"Level 5 textures: these pack files already exist: {clash[:4]}")
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted({**existing, **extra}):
            z.writestr(path, existing.get(path) or extra[path])
    textures = {p.split("/")[-1][:-4]: d for p, d in extra.items() if p.endswith(".png")}
    return out.getvalue(), textures
