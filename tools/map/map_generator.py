"""
Every Tunnel map generator: Level 1, the parking garage (Minecraft 1.20.1).

One command regenerates everything:   python3 tools/map/map_generator.py

Out (tools/map/output/, not in git):
  every_tunnel_level1_vanilla/   datapack for plain Minecraft  (/function garage:build, garage:clear)
  every_tunnel_level1_mod/       datapack for the mod: its fluorescent lights + the level file
                                 (zones, start in the elevator, exit at the ramp, ambience)
  level_resources.zip            resource pack; worlds carry it as <world>/resources.zip
  level_preview.png              top-down plan of the whole level
  texture_sheet.png              every generated texture, enlarged
Every world in INSTALL that exists gets its datapack and resources.zip after each run.

Checked against the 1.20.1 game files: data and resource pack format 15; functions in
data/<ns>/functions/; /fill limited by the gamerule commandModificationBlockLimit (default
32768); functions up to 65536 commands; a world's resources.zip is applied automatically
when that world is opened (and only then); the 11 chunks around world spawn are always
loaded; the stand-in blocks' states and render layers.
"""

import json
import shutil
import sys
import zipfile
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent))

from garage import preview as V        # noqa: E402
from garage import textures as T       # noqa: E402
from garage.level import Level         # noqa: E402

# =====================================================================================
# PARAMETERS
# =====================================================================================

# --- The level -----------------------------------------------------------------------
LEVEL_SIGN = "B1"
NEXT_LEVEL_SIGN = "B2"
PLACE_AT = (0, 64, 0)     # the level is centred on this x, z (keep it near world spawn); hall 1's floor at this y

# Halls in the order they are walked (each can override any general value below). They
# get worse as you go: more wear, fewer cars, more dead lights, more oddities.
HALLS = [
    dict(LENGTH=44, WIDTH=20, WEAR=0.25, CAR_DENSITY=0.45, DEAD_LIGHT_SHARE=0.0, GAP_SHARE=0.0, DARK_ZONES=0,
         ODDITIES=0, HANGING_CABLES=2, CARTS=1),
    dict(LENGTH=48, WIDTH=22, WEAR=0.4, CAR_DENSITY=0.35, DEAD_LIGHT_SHARE=0.05, GAP_SHARE=0.03, DARK_ZONES=0,
         ODDITIES=1, ODDITY_TYPES=["wrong_sign", "extra_pillar"]),
    dict(LENGTH=40, WIDTH=20, WEAR=0.55, CAR_DENSITY=0.25, DEAD_LIGHT_SHARE=0.12, GAP_SHARE=0.06, DARK_ZONES=1,
         ODDITIES=2, ODDITY_TYPES=["walled_off", "odd_door", "blocked_lane"]),
    dict(LENGTH=52, WIDTH=22, WEAR=0.7, CAR_DENSITY=0.2, DEAD_LIGHT_SHARE=0.2, GAP_SHARE=0.1, DARK_ZONES=2,
         ODDITIES=2, ODDITY_TYPES=["collapse", "stairs_to_ceiling", "walled_off"]),
    dict(LENGTH=44, WIDTH=20, WEAR=0.85, CAR_DENSITY=0.1, DEAD_LIGHT_SHARE=0.3, GAP_SHARE=0.12, DARK_ZONES=2,
         ODDITIES=3, ODDITY_TYPES=["collapse", "odd_door", "extra_pillar", "wrong_sign"], HANGING_CABLES=10),
]
# Between hall i and i+1: a corridor ("length" blocks) or a ramp that also drops "drop"
# blocks (length >= 2 * drop + 2). "light" = share of lit fixtures in a corridor.
CONNECTORS = [
    dict(length=10, light=0.9),
    dict(length=12, light=0.7),
    dict(length=12, drop=3),
    dict(length=10, light=0.4),
]
# Rooms off the halls: storage, electrical, stairwell (a locked door marked EXIT), dead_end.
SIDE_ROOMS = [
    dict(hall=1, type="storage", width=7, depth=5),
    dict(hall=2, type="electrical", width=5, depth=4),
    dict(hall=3, type="stairwell", width=3, depth=5),
    dict(hall=4, type="dead_end", depth=8),
]
LOBBY_LENGTH = 6          # lobby between the elevator and hall 1
EXIT_DROP = 5             # how far the exit ramp goes down
LANDING_LENGTH = 7        # dark landing at the bottom of the exit ramp

# --- General values (every hall, unless the hall overrides them) ---------------------
CEILING_HEIGHT = 4
AISLE_WIDTH = 6
PILLAR_SPACING = 8
STALLS_BETWEEN_PILLARS = 2
SEED = 7
TEXTURE_SEED = 3
WEAR = 0.5
DECALS = True
CAR_DENSITY = 0.35
CLUTTER_DENSITY = 0.15
WHEEL_STOPS = True
CONES = 2
CARTS = 0
SIGNS = True
SIGN_SPACING = 16
SIGN_PAINT = (236, 236, 228)
BEAMS = True
CEILING_PIPES = True
HANGING_CABLES = 4
ODDITIES = 1
ODDITY_TYPES = ["collapse", "walled_off", "odd_door", "stairs_to_ceiling", "extra_pillar", "wrong_sign", "blocked_lane"]
LIGHT_SPACING = 6
LIGHT_LENGTH = 2
LIGHT_ROWS = "all"
DEAD_LIGHT_SHARE = 0.08
GAP_SHARE = 0.06
DARK_ZONES = 1
DARK_ZONE_LENGTH = 10
MOD_LIGHT = "every_tunnel:fluorescent_light[facing=down,mode=%s,lit=%s]"
WALL_STRIPE_HEIGHT = 2
RESOURCE_PACK_FORMAT = 15

# Stand-in blocks re-skinned by the resource pack (only in worlds that carry it).
STAND_INS = {
    "floor": "minecraft:gray_concrete",
    "floor_patch": "minecraft:tuff",
    "wall": "minecraft:light_gray_concrete",
    "wall_base": "minecraft:polished_andesite",
    "paint_band": "minecraft:yellow_terracotta",
    "ceiling": "minecraft:smooth_stone",
    "pillar": "minecraft:stone",
    "line_white": "minecraft:white_concrete",
    "line_yellow": "minecraft:yellow_concrete",
    "duct": "minecraft:iron_block",
    "fixture_lit": "minecraft:sea_lantern",
    "fixture_dead": "minecraft:quartz_block",
    "fixture_dim": "minecraft:glow_lichen",
    "decal": "minecraft:sculk_vein",
    "glyph": "minecraft:chiseled_bookshelf",
    "box": "minecraft:dried_kelp_block",
    "cone": "minecraft:pointed_dripstone",
    "extinguisher": "minecraft:red_candle",
    "cart": "minecraft:hopper",
}

# Worlds that get the result after each run (skipped when the folder does not exist).
REPO = Path(__file__).resolve().parents[2]
INSTALL = [
    {"world": str(REPO / "run" / "saves" / "noname_complex"), "pack": "mod"},
    {"world": "/home/super/.sklauncher/instances/every-tunnel-test/saves/New World", "pack": "vanilla"},
]

# =====================================================================================

OUT = Path(__file__).resolve().parent / "output"
PACKS = {"vanilla": "every_tunnel_level1_vanilla", "mod": "every_tunnel_level1_mod"}
OLD_PACKS = ["every_tunnel_garage_test", "every_tunnel_garage_mod"]      # earlier tests, removed on install
FILL_LIMIT = 32768


def fills(x1, y1, z1, x2, y2, z2, block):
    x1, x2 = sorted((x1, x2))
    y1, y2 = sorted((y1, y2))
    z1, z2 = sorted((z1, z2))
    step = max(1, FILL_LIMIT // ((y2 - y1 + 1) * (z2 - z1 + 1)))
    out = []
    x = x1
    while x <= x2:
        end = min(x2, x + step - 1)
        out.append(f"fill {x} {y1} {z1} {end} {y2} {z2} {block}")
        x = end + 1
    return out


def bounds(world, offset):
    ox, oy, oz = offset
    xs = [x for x, _, _ in world.blocks]
    ys = [y for _, y, _ in world.blocks]
    zs = [z for _, _, z in world.blocks]
    return (min(xs) + ox - 1, min(ys) + oy, min(zs) + oz - 1, max(xs) + ox + 1, max(ys) + oy, max(zs) + oz + 1)


def commands(level, offset, box, previous_box, summary):
    ox, oy, oz = offset
    c = ["# Generated by tools/map/map_generator.py (edit the parameters there).",
         "# Clear the previous build and this one."]
    for b in {previous_box, box} - {None}:
        c += fills(*b, "minecraft:air")
    c.append("# Blocks, bottom to top, merged into runs along x")
    rows = {}
    for (x, y, z), state in level.world.blocks.items():
        rows.setdefault((y, z), []).append((x, state))
    for (y, z) in sorted(rows):
        run = None
        for x, state in sorted(rows[(y, z)]) + [(None, None)]:
            if run and x is not None and x == run[1] + 1 and state == run[2]:
                run[1] = x
                continue
            if run:
                a, b, st = run
                if a == b:
                    c.append(f"setblock {ox + a} {oy + y} {oz + z} {st}")
                else:
                    c.append(f"fill {ox + a} {oy + y} {oz + z} {ox + b} {oy + y} {oz + z} {st}")
            run = [x, x, state] if x is not None else None
    sx, sy, sz, yaw = level.spawn
    c.append(f"tp @s {ox + sx} {oy + sy} {oz + sz} {yaw} 0")
    c.append('tellraw @s {"text":"%s","color":"gray"}' % summary)
    return c


def level_json(level, offset):
    ox, oy, oz = offset
    sx, sy, sz, yaw = level.spawn
    zones = [{"id": zid, "from": [x0 + ox, y0 + oy, z0 + oz], "to": [x1 + ox, y1 + oy, z1 + oz], **({"tags": tags} if tags else {})}
             for zid, (x0, y0, z0, x1, y1, z1), tags in level.zones]
    return json.dumps({
        "name": f"Level 1 - Parking garage {LEVEL_SIGN} (generated)",
        "order": 1,
        "dimension": "minecraft:overworld",
        "zones": zones,
        "spawn_points": [{"id": "player_start", "pos": [ox + sx, oy + sy, oz + sz], "yaw": yaw, "tags": ["player"]}],
        "ambience": {
            "loop": "every_tunnel:ambient.garage.drone", "loop_volume": 0.7,
            "random": [
                {"sound": "every_tunnel:ambient.drip", "interval": [6, 18], "distance": [3, 12], "volume": 0.5},
                {"sound": "every_tunnel:ambient.rumble", "interval": [30, 90], "distance": [8, 16], "volume": 0.9},
                {"sound": "every_tunnel:ambient.relay", "interval": [25, 70], "distance": [4, 14], "volume": 0.6},
                {"sound": "every_tunnel:ambient.sparks", "interval": [40, 100], "distance": [4, 12], "volume": 0.5},
            ],
        },
    }, indent=1) + "\n"


def write_pack(name, cmds, box, level=None, offset=None):
    pack = OUT / name
    if pack.exists():
        shutil.rmtree(pack)
    functions = pack / "data" / "garage" / "functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 15, "description": f"Every Tunnel {LEVEL_SIGN} (level 1){' for the mod' if level else ''}, seed {SEED}"}}, indent=2) + "\n")
    corners = [(box[0], box[1], box[2]), (box[3], box[1], box[5]), (box[0], box[1], box[5]), (box[3], box[1], box[2])]
    loaded = " ".join(f"if loaded {x} {y} {z}" for x, y, z in corners)
    msg = '{"text":"The level area is not loaded. Keep PLACE_AT near world spawn, or stand closer, and try again.","color":"red"}'
    (functions / "build.mcfunction").write_text("\n".join(
        [f"execute {loaded} run function garage:build_now"]
        + [f"execute unless loaded {x} {y} {z} run tellraw @s {msg}" for x, y, z in corners]) + "\n")
    (functions / "build_now.mcfunction").write_text("\n".join(cmds) + "\n")
    (functions / "clear.mcfunction").write_text("\n".join(fills(*box, "minecraft:air") + ['tellraw @s {"text":"Level cleared.","color":"gray"}']) + "\n")
    if level:
        levels = pack / "data" / "every_tunnel" / "levels"
        levels.mkdir(parents=True)
        (levels / "level_1.json").write_text(level_json(level, offset))
    with zipfile.ZipFile(OUT / f"{name}.zip", "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(pack.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(pack).as_posix())


def install(resource_zip):
    for target in INSTALL:
        world = Path(target["world"])
        if not world.is_dir():
            continue
        datapacks = world / "datapacks"
        datapacks.mkdir(exist_ok=True)
        for name in list(PACKS.values()) + OLD_PACKS:
            for old in (datapacks / name, datapacks / f"{name}.zip"):
                if old.is_dir():
                    shutil.rmtree(old)
                elif old.exists():
                    old.unlink()
        shutil.copytree(OUT / PACKS[target["pack"]], datapacks / PACKS[target["pack"]])
        (world / "resources.zip").write_bytes(resource_zip)
        print(f"Installed into {world} ({target['pack']} datapack + resources.zip)")


def main():
    p = SimpleNamespace(**{k: v for k, v in globals().items() if k.isupper()})
    OUT.mkdir(exist_ok=True)
    state_file = OUT / "last_build.json"
    previous = tuple(json.loads(state_file.read_text())["box"]) if state_file.exists() else None

    levels = {v: Level(p, v).compose() for v in ("vanilla", "mod")}
    if levels["vanilla"].glyphs.wall != levels["mod"].glyphs.wall or levels["vanilla"].glyphs.pillar != levels["mod"].glyphs.pillar:
        raise SystemExit("internal error: sign texts differ between the vanilla and mod builds")

    # Centre the level on PLACE_AT (x, z); hall 1's floor goes to PLACE_AT's y.
    raw = bounds(levels["vanilla"].world, (0, 0, 0))
    offset = (PLACE_AT[0] - (raw[0] + raw[3]) // 2, PLACE_AT[1], PLACE_AT[2] - (raw[2] + raw[5]) // 2)
    box = bounds(levels["vanilla"].world, offset)

    v = levels["vanilla"]
    reports = [h["report"] for h in v.halls]
    oddities = [o for r in reports for o in r["oddities"]]
    summary = (f"{LEVEL_SIGN} built: {len(v.halls)} halls, {len(SIDE_ROOMS)} side rooms, {len(oddities)} oddities. "
               f"Find the ramp down to {NEXT_LEVEL_SIGN}.")
    for variant, level in levels.items():
        cmds = commands(level, offset, box, previous, summary)
        write_pack(PACKS[variant], cmds, box, level if variant == "mod" else None, offset)
        if variant == "vanilla":
            count = len([c for c in cmds if not c.startswith("#")])

    resource_zip, textures = T.build_pack(p, v.glyphs)
    (OUT / "level_resources.zip").write_bytes(resource_zip)
    lights = {}
    for _, _, state in v.fixtures:
        lights[state] = lights.get(state, 0) + 1
    props = {}
    for r in reports:
        for k, n in r["props"].items():
            props[k] = props.get(k, 0) + n
    size = (box[3] - box[0] + 1, box[5] - box[2] + 1)
    stats = [
        f"{LEVEL_SIGN}: {len(v.halls)} halls, {len(CONNECTORS)} connectors, {len(SIDE_ROOMS)} side rooms; "
        f"{size[0]} x {size[1]} blocks; {count} commands; seed {SEED}",
        "Clear line of sight per hall: " + ", ".join(str(r["line_of_sight"]) for r in reports) + " blocks",
        "Lights: " + ", ".join(f"{n} {k}" for k, n in sorted(lights.items())),
        "Props: " + ", ".join(f"{n} {k}" for k, n in sorted(props.items())),
    ] + [f"Hall {i + 1} oddity: {o['what']}" for i, r in enumerate(reports) for o in r["oddities"]]
    V.write_level(OUT / "level_preview.png", v, stats)
    V.write_texture_sheet(OUT / "texture_sheet.png", textures)
    state_file.write_text(json.dumps({"box": box}) + "\n")

    for line in stats:
        print(line)
    if min(r["line_of_sight"] for r in reports) < 30:
        print("WARNING: a hall has a clear line of sight under 30 blocks")
    install(resource_zip)


if __name__ == "__main__":
    main()
