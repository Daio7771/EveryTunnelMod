"""
Every Tunnel map generator: Level 1, the parking garage (Minecraft 1.20.1).

One command regenerates everything:   python3 tools/map/map_generator.py

Out (tools/map/output/, not in git):
  every_tunnel_level1_vanilla/   datapack for plain Minecraft  (builds itself when the world opens;
                                 by hand: /function garage:build, garage:clear)
  every_tunnel_level1_mod/       datapack for the mod: its fluorescent lights + the level file
                                 (zones, start in the elevator, exit at the ramp, ambience); the
                                 mod runs garage:build every time the world opens, behind its
                                 loading screen (the level files name it: "build")
  level_resources.zip            resource pack; worlds carry it as <world>/resources.zip
  level_preview.png              top-down plan of the whole level
  texture_sheet.png              every generated texture, enlarged
  pbr_sheet.png                  each texture's LabPBR maps: albedo | normal | specular | emission
Into the mod (src/main/resources/assets/every_tunnel/, in git): the main menu's scene, one hall
seen from a fixed camera (menu/scene.json and the models and textures under block/menu/;
see garage/menu_scene.py).
Every world in INSTALL that exists gets its datapack and resources.zip after each run
(a missing one is reported and skipped; create run/saves/noname_complex first, see README).

Checked against the 1.20.1 game files: data and resource pack format 15; functions in
data/<ns>/functions/; /fill limited by the gamerule commandModificationBlockLimit (default
32768); functions up to 65536 commands; a world's resources.zip is applied automatically
when that world is opened (and only then); the 11 chunks around world spawn are always
loaded; the stand-in blocks' states and render layers.
"""

import json
import random
import shutil
import sys
import zipfile
import zlib
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent))

from garage import menu_scene as MS    # noqa: E402
from garage import preview as V        # noqa: E402
from garage import textures as T       # noqa: E402
from garage.level import Level         # noqa: E402

# =====================================================================================
# PARAMETERS
# =====================================================================================

# --- The level -----------------------------------------------------------------------
LEVEL_SIGN = "B1"
NEXT_LEVEL_SIGN = "B2"
# The elevator's floor indicator counts these down during the intro (FloorIndicatorBlock.LABELS).
ELEVATOR_FLOORS = ["3", "2", "1", "G", "B1"]
LEVEL_TITLE = "Level 1"          # shown when the elevator doors open
NEXT_LEVEL_TITLE = "Level 2"     # shown on walking into the next level
# The new game's intro: seconds waiting in the closed elevator while the song plays.
INTRO_SECONDS = 25
INTRO_MUSIC = "every_tunnel:music.elevator"
PLACE_AT = (0, 64, 0)     # the level is centred on this x, z (keep it near world spawn); hall 1's floor at this y

# Halls in the order they are walked (each can override any general value below). They
# get worse as you go: more wear, fewer cars, more dead lights, more oddities.
HALLS = [
    dict(LENGTH=44, WIDTH=20, WEAR=0.25, DEAD_LIGHT_SHARE=0.0, GAP_SHARE=0.0, DARK_ZONES=0,
         ODDITIES=0, HANGING_CABLES=2, CARTS=1),
    dict(LENGTH=48, WIDTH=22, WEAR=0.4, DEAD_LIGHT_SHARE=0.05, GAP_SHARE=0.03, DARK_ZONES=0,
         ODDITIES=1, ODDITY_TYPES=["wrong_sign", "extra_pillar"]),
    dict(LENGTH=40, WIDTH=20, WEAR=0.55, DEAD_LIGHT_SHARE=0.12, GAP_SHARE=0.06, DARK_ZONES=1,
         ODDITIES=2, ODDITY_TYPES=["walled_off", "odd_door", "blocked_lane"]),
    dict(LENGTH=52, WIDTH=22, WEAR=0.7, DEAD_LIGHT_SHARE=0.2, GAP_SHARE=0.1, DARK_ZONES=2,
         ODDITIES=2, ODDITY_TYPES=["collapse", "stairs_to_ceiling", "walled_off"]),
    dict(LENGTH=44, WIDTH=20, WEAR=0.85, DEAD_LIGHT_SHARE=0.3, GAP_SHARE=0.12, DARK_ZONES=2,
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
# Halls (1 = first) with a Pale man spawn point in the middle of the aisle; he never leaves
# his hall (the spawn point's "zone"). Level 1 has none: it is the level without enemies.
# Only for testing him in this map until Level 2 exists, e.g. [2].
PALE_MAN_HALLS = []
# Loot lying around (mod version): (item, zone id), item "battery" or "water". It lies on
# the floor against a wall or a prop somewhere in that zone; the same seed gives the same spot.
# The game puts it there the first time the level is entered (LootSpawner).
LOOT = [
    ("battery", "storage_hall_2"),
    ("water", "electrical_hall_3"),
    ("water", "hall_4"),
    ("battery", "dead_end_hall_5"),
]
START = "elevator"        # "elevator" (lobby and service elevator) or "entry" (a corridor in from the previous level)
END = "ramp"              # "ramp" (exit ramp, shutter and landing down to the next level) or "none"
KEYCARD_HALL = 0          # hall (1 = first) with the glass booth holding the keycard; 0 = none
EXIT_STEPS = 12           # keycard_exit side room: steps down behind the EXIT door
ENTRY_LENGTH = 6          # START "entry": length of the corridor in
PREVIOUS_LEVEL_SIGN = ""
EXIT_DROP = 5             # how far the exit ramp goes down
LANDING_LENGTH = 7        # dark landing at the bottom of the exit ramp

# --- Level 2 (B2) ----------------------------------------------------------------------
# Built by the same code with these values over the ones above, joined to Level 1's dark
# landing: its entry corridor continues through a doorway under the landing's "B2" sign.
# Darker and more worn than B1 (light like B1's last hall), one Pale man per hall and two in
# the bigger last one, each kept in his hall. The keycard lies in a glass booth in hall 5; the
# way out is the EXIT door with a card reader by hall 3, stairs down to B3 (the demo's end).
LEVEL_2 = dict(
    LEVEL_SIGN="B2",
    NEXT_LEVEL_SIGN="B3",
    PREVIOUS_LEVEL_SIGN="B1",
    SEED=23,
    START="entry",
    END="none",
    KEYCARD_HALL=5,
    HALLS=[
        dict(LENGTH=44, WIDTH=20, WEAR=0.8, DEAD_LIGHT_SHARE=0.3, GAP_SHARE=0.12, DARK_ZONES=2,
             ODDITIES=1, ODDITY_TYPES=["collapse"], HANGING_CABLES=8),
        dict(LENGTH=48, WIDTH=22, WEAR=0.85, DEAD_LIGHT_SHARE=0.3, GAP_SHARE=0.12, DARK_ZONES=2,
             ODDITIES=2, ODDITY_TYPES=["collapse", "blocked_lane"], HANGING_CABLES=10),
        dict(LENGTH=44, WIDTH=20, WEAR=0.9, DEAD_LIGHT_SHARE=0.3, GAP_SHARE=0.12, DARK_ZONES=2,
             ODDITIES=1, ODDITY_TYPES=["walled_off"], HANGING_CABLES=10),
        dict(LENGTH=48, WIDTH=22, WEAR=0.9, DEAD_LIGHT_SHARE=0.3, GAP_SHARE=0.12, DARK_ZONES=2,
             ODDITIES=2, ODDITY_TYPES=["collapse", "collapse"], HANGING_CABLES=12),
        dict(LENGTH=60, WIDTH=26, WEAR=0.95, DEAD_LIGHT_SHARE=0.3, GAP_SHARE=0.12, DARK_ZONES=3,
             ODDITIES=2, ODDITY_TYPES=["collapse", "collapse"], HANGING_CABLES=14),
    ],
    CONNECTORS=[
        dict(length=10, light=0.3),
        dict(length=14, light=0.3),
        dict(length=12, drop=3),
        dict(length=10, light=0.2),
    ],
    SIDE_ROOMS=[
        dict(hall=0, type="storage", width=7, depth=5),
        dict(hall=1, type="electrical", width=5, depth=4),
        dict(hall=2, type="keycard_exit"),
        dict(hall=3, type="storage", width=7, depth=5),
        dict(hall=4, type="dead_end", depth=8),
    ],
    # Pale men per hall (1 = first); each stays in his hall.
    PALE_MEN={1: 1, 2: 1, 3: 1, 4: 1, 5: 2},
    LOOT=[
        ("battery", "storage_hall_1"),
        ("water", "electrical_hall_2"),
        ("water", "storage_hall_4"),
        ("battery", "dead_end_hall_5"),
    ],
)

# --- The main menu's scene -------------------------------------------------------------
# One hall like the level's (these values over the general ones below), drawn behind the menu
# from a fixed camera at the west end of its aisle, looking east to a closed emergency door
# under a lit EXIT sign (garage/menu_scene.py). Its lamps flicker now and then in the menu.
MENU_SCENE = dict(LENGTH=40, WIDTH=20, SEED=41, WEAR=0.5, DEAD_LIGHT_SHARE=0.1, GAP_SHARE=0.04,
                  DARK_ZONES=0, ODDITIES=0, HANGING_CABLES=4, CARTS=0)
# x: blocks from the west wall's outer face; eye: eye height over the floor; yaw/pitch in
# Minecraft degrees (yaw -90 looks east, a positive pitch looks down); vanish_x: where the
# aisle's far end is drawn, as a share of the screen width (the menu's left part is black).
MENU_CAMERA = dict(x=1.6, eye=1.62, yaw=-90.0, pitch=4.0, fov=70.0, vanish_x=0.68)

# --- General values (every hall, unless the hall overrides them) ---------------------
CEILING_HEIGHT = 4
AISLE_WIDTH = 6
PILLAR_SPACING = 8
STALLS_BETWEEN_PILLARS = 2
SEED = 7
TEXTURE_SEED = 3
WEAR = 0.5
DECALS = True
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
MOD_LIGHT = "every_tunnel:fluorescent_light[facing=down,axis=x,mode=%s,lit=%s]"
WALL_STRIPE_HEIGHT = 2
RESOURCE_PACK_FORMAT = 15
# LabPBR maps for shader packs (garage/pbr.py; tuned for Complementary Reimagined). Glows
# are 0..1 of full emission: kept low so the flashlight stays the main light.
PBR = dict(
    RELIEF=1.0,            # strength of the surface relief from the textures' brightness
    TUBE_GLOW=0.5,         # lit ceiling fixtures: the tubes
    DIFFUSER_GLOW=0.2,     # lit ceiling fixtures: the diffuser around them
    DIM_PANEL_GLOW=0.15,   # dim yellowed panels
)

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
    "cone": "minecraft:pointed_dripstone[thickness=tip,vertical_direction=up]",
    "extinguisher": "minecraft:red_candle[candles=1,lit=false]",
    "cart": "minecraft:hopper",
    "arrow": "minecraft:white_glazed_terracotta",     # floor arrow; its facing is where it points
    "hatch": "minecraft:yellow_glazed_terracotta",    # keep-clear hatching
    "line_red": "minecraft:red_concrete",             # red line along the edge of a keep-clear box
    "hazard": "minecraft:black_glazed_terracotta",    # pillar base in black and yellow stripes
}

# Other things the level places, as plain vanilla blocks (not re-skinned).
BLOCKS = {
    "wheel_stop": "minecraft:smooth_stone_slab[type=bottom]",
    "pipe": "minecraft:lightning_rod[facing=east,powered=false]",
    "tray": "minecraft:iron_trapdoor[facing=north,half=top,open=false]",
    "cable": "minecraft:chain[axis=y]",
    "pallet": "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]",
    "drum": "minecraft:barrel[facing=up,open=false]",
    "bin": "minecraft:cauldron",
    "elevator_wall": "minecraft:iron_block",
    "storage_door": "minecraft:spruce_door",
    "electrical_door": "minecraft:dark_oak_door",
    "emergency_door": "minecraft:iron_door",
}

# The mod version uses the mod's own blocks instead (tools/blocks/generate.py makes their
# assets). Their orientation, connections and patterns are set by garage/mod_blocks.py.
MOD_BLOCKS = {
    "duct": "every_tunnel:duct",
    "decal": "every_tunnel:stain",
    "glyph": "every_tunnel:sign_tile",
    "box": "every_tunnel:cardboard_box",
    "cone": "every_tunnel:traffic_cone",
    "extinguisher": "every_tunnel:fire_extinguisher",
    "cart": "every_tunnel:shopping_cart",
    "wheel_stop": "every_tunnel:wheel_stop",
    "pipe": "every_tunnel:pipe",
    "tray": "every_tunnel:cable_tray",
    "cable": "every_tunnel:hanging_cable",
    "pallet": "every_tunnel:pallet",
    "drum": "every_tunnel:oil_drum",
    "bin": "every_tunnel:trash_bin",
    "elevator_wall": "every_tunnel:metal_panel",
    "storage_door": "every_tunnel:storage_door",
    "electrical_door": "every_tunnel:electrical_door",
    "emergency_door": "every_tunnel:emergency_door",
}

# Worlds that get the result after each run (skipped, with a warning, when there is no world
# there; "optional" ones silently). The dev template of the map world (see DevWorlds in the
# mod) gets it too, or the next launch would put the old level back.
REPO = Path(__file__).resolve().parents[2]
INSTALL = [
    {"world": str(REPO / "run" / "saves" / "noname_complex"), "pack": "mod"},
    {"world": str(REPO / "run" / "dev_templates" / "noname_complex"), "pack": "mod", "optional": True},
]

# =====================================================================================

OUT = Path(__file__).resolve().parent / "output"
PACKS = {"vanilla": "every_tunnel_level1_vanilla", "mod": "every_tunnel_level1_mod"}
OLD_PACKS = ["every_tunnel_garage_test", "every_tunnel_garage_mod"]      # earlier tests, removed on install
OLD_BOXES = [(-1, 64, -1, 40, 70, 20)]      # what the earlier garage test built, cleared by every build
FILL_LIMIT = 32768
SCORE = "garage_build"      # scoreboard objective that remembers which version of the level is built (vanilla pack)
BUILD_FUNCTION = "garage:build"     # what the mod runs when the world opens ("build" in the level files)


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


# Blocks that break (and drop as items) without the block they hang on, stand on or
# stick to. The build places them after everything else, so their support is already there.
SUPPORTED = {"minecraft:sculk_vein", "minecraft:glow_lichen", "minecraft:pointed_dripstone"}
SUPPORTED_SUFFIXES = ("_carpet", "_candle", "_door", "_button")


def needs_support(state):
    block = state.split("[")[0]
    return block in SUPPORTED or block.endswith(SUPPORTED_SUFFIXES)


def without_drops(cmds, boxes):
    """Wraps commands that remove or place blocks: no block drops while they run (the
    world's doTileDrops is restored afterwards), and item entities left in `boxes` (by
    this or earlier builds) are removed at the end."""
    before = ["# No drops while blocks change (the gamerule is restored at the end).",
              f"execute store result score #drops {SCORE} run gamerule doTileDrops",
              "gamerule doTileDrops false"]
    after = ["# Remove dropped items lying in the level."]
    for x1, y1, z1, x2, y2, z2 in boxes:
        after.append(f"kill @e[type=minecraft:item,x={x1},y={y1},z={z1},dx={x2 - x1},dy={y2 - y1},dz={z2 - z1}]")
    after.append(f"execute if score #drops {SCORE} matches 1 run gamerule doTileDrops true")
    return before + cmds + after


def place(c, blocks, offset):
    """Appends setblock/fill commands for {(x, y, z): state}: bottom to top, merged into runs along x."""
    ox, oy, oz = offset
    rows = {}
    for (x, y, z), state in blocks.items():
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


def commands(level, offset, box, previous_box, summary):
    ox, oy, oz = offset
    c = ["# Clear the earlier garage test, the previous build and this one."]
    boxes = sorted({previous_box, box, *OLD_BOXES} - {None})
    for b in boxes:
        c += fills(*b, "minecraft:air")
    blocks = level.world.blocks
    c.append("# Blocks, bottom to top, merged into runs along x")
    place(c, {pos: st for pos, st in blocks.items() if not needs_support(st)}, offset)
    c.append("# Then the blocks that need one of those to hang on, stand on or stick to")
    place(c, {pos: st for pos, st in blocks.items() if needs_support(st)}, offset)
    c = ["# Generated by tools/map/map_generator.py (edit the parameters there)."] + without_drops(c, boxes)
    if level.variant == "mod":
        # Rebuilding starts the level over: its Pale men go (their spawn points spawn them again),
        # and the mod puts the loot back (the rebuild wiped it) and forgets the keycard's lights.
        for x1, y1, z1, x2, y2, z2 in boxes:
            c.append(f"kill @e[type=every_tunnel:pale_man,x={x1},y={y1},z={z1},dx={x2 - x1},dy={y2 - y1},dz={z2 - z1}]")
        c.append("everytunnel rebuilt")
        # No player runs it: the mod builds when the world opens, then starts the new game.
        return c
    sx, sy, sz, yaw = level.spawn
    c.append(f"tp @s {ox + sx} {oy + sy} {oz + sz} {yaw} 0")
    c.append('tellraw @s {"text":"%s","color":"gray"}' % summary)
    return c


def pale_man_spawns(level, counts):
    """(id, (x, y, z), hall zone id) for counts {hall (1 = first): how many}: spread along
    the aisle's centre line (one in the middle, two at a third and two thirds...), each the
    free spot (3 blocks of headroom, he is 2.8 tall) closest to its place."""
    out = []
    for n, count in sorted(counts.items()):
        if not 1 <= n <= len(level.halls):
            raise SystemExit(f"Pale men: there is no hall {n}")
        f = level.halls[n - 1]["frame"]
        hp, g = f["hp"], f["g"]
        y, z = f["hy"] + 1, f["hz"] + g.center_z
        for k in range(count):
            mid = f["hx"] + hp.LENGTH * (k + 1) // (count + 1)
            for x in sorted(range(f["hx"] + 2, f["hx"] + hp.LENGTH - 2), key=lambda x: abs(x - mid)):
                if all(level.world.free(x, y + dy, z) for dy in range(3)):
                    pid = f"pale_man_hall_{n}" if count == 1 else f"pale_man_hall_{n}_{k + 1}"
                    out.append((pid, (x + 0.5, y, z + 0.5), f"hall_{n}"))
                    break
            else:
                raise SystemExit(f"Pale men: no free spot in the aisle of hall {n}")
    return out


def loot_spots(level, loot):
    """(id, (x, y, z), item, yaw) for each (item, zone) of `loot`: a free floor cell of its
    zone, with headroom, next to a wall or a prop, picked with the level's seed."""
    rng = random.Random(f"loot-{level.p.SEED}")
    zones = {zid: box for zid, box, _ in level.zones}
    w = level.world
    out, used = [], set()
    for i, (item, zid) in enumerate(loot):
        if item not in ("battery", "water"):
            raise SystemExit(f"LOOT: unknown item {item!r} (battery or water)")
        if zid not in zones:
            raise SystemExit(f"LOOT: no zone {zid!r}; zones: {', '.join(sorted(zones))}")
        x0, y0, z0, x1, y1, z1 = zones[zid]
        cells = []
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                fy = level.floor.get((x, z))
                if fy is None or not y0 <= fy + 1 <= y1:
                    continue
                y = fy + 1
                if w.get(x, fy, z) is None or not w.free(x, y, z) or not w.free(x, y + 1, z) or (x, y, z) in used:
                    continue
                if any(not w.free(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    cells.append((x, y, z))
        if not cells:
            raise SystemExit(f"LOOT: no free floor cell against a wall or prop in {zid}")
        x, y, z = rng.choice(sorted(cells))
        used.add((x, y, z))
        out.append((f"{item}_{i + 1}", (x + 0.5, y, z + 0.5), item, rng.choice([0, 90, 180, 270])))
    return out


AMBIENCE = {
    "loop": "every_tunnel:ambient.garage.drone", "loop_volume": 0.7,
    "random": [
        {"sound": "every_tunnel:ambient.drip", "interval": [6, 18], "distance": [3, 12], "volume": 0.5},
        {"sound": "every_tunnel:ambient.rumble", "interval": [30, 90], "distance": [8, 16], "volume": 0.9},
        {"sound": "every_tunnel:ambient.relay", "interval": [25, 70], "distance": [4, 14], "volume": 0.6},
        {"sound": "every_tunnel:ambient.sparks", "interval": [40, 100], "distance": [4, 12], "volume": 0.5},
    ],
}


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
        "spawn_points": [{"id": "player_start", "pos": [ox + sx, oy + sy, oz + sz], "yaw": yaw, "tags": ["player"]}]
        + [{"id": pid, "pos": [ox + x, oy + y, oz + z], "yaw": 0, "tags": ["pale_man"], "zone": zone}
           for pid, (x, y, z), zone in pale_man_spawns(level, {n: 1 for n in PALE_MAN_HALLS})]
        + [{"id": lid, "pos": [ox + x, oy + y, oz + z], "yaw": yaw, "tags": [item]}
           for lid, (x, y, z), item, yaw in loot_spots(level, LOOT)],
        "ambience": AMBIENCE,
        "entry": {"title": LEVEL_TITLE},
        "intro": {"seconds": INTRO_SECONDS, "music": INTRO_MUSIC},
        "build": BUILD_FUNCTION,
    }, indent=1) + "\n"


def next_level_json(level, level2, offset, joint):
    """Level 2: Level 1's dark landing past the exit shutter (its start) and everything built
    from LEVEL_2, moved by `joint` (see attach). Walking in shows its title, plays the
    transition sound and closes the shutter behind the player."""
    ox, oy, oz = offset
    jx, jy, jz = joint
    sx, sy, sz, yaw = level.next_spawn

    def zone_json(zid, box, tags, d):
        x0, y0, z0, x1, y1, z1 = box
        return {"id": zid, "from": [x0 + d[0] + ox, y0 + d[1] + oy, z0 + d[2] + oz],
                "to": [x1 + d[0] + ox, y1 + d[1] + oy, z1 + d[2] + oz], **({"tags": tags} if tags else {})}

    def at(x, y, z):
        return [x + jx + ox, y + jy + oy, z + jz + oz]

    zones = [zone_json(zid, box, tags, (0, 0, 0)) for zid, box, tags in level.next_zones]
    zones += [zone_json(zid, box, tags, joint) for zid, box, tags in level2.zones]
    p2 = level2.p
    kx, ky, kz = level2.keycard
    return json.dumps({
        "name": f"Level 2 - Parking garage {NEXT_LEVEL_SIGN} (generated)",
        "order": 2,
        "dimension": "minecraft:overworld",
        "zones": zones,
        "spawn_points": [{"id": "player_start", "pos": [ox + sx, oy + sy, oz + sz], "yaw": yaw, "tags": ["player"]}]
        + [{"id": pid, "pos": at(x, y, z), "yaw": 0, "tags": ["pale_man"], "zone": zone}
           for pid, (x, y, z), zone in pale_man_spawns(level2, p2.PALE_MEN)]
        + [{"id": lid, "pos": at(x, y, z), "yaw": yaw, "tags": [item]}
           for lid, (x, y, z), item, yaw in loot_spots(level2, p2.LOOT)]
        + [{"id": "keycard", "pos": at(kx, ky, kz), "yaw": 0, "tags": ["keycard"]}],
        "ambience": AMBIENCE,
        "entry": {"title": NEXT_LEVEL_TITLE, "subtitle": "", "sound": "every_tunnel:event.level_transition"},
        "build": BUILD_FUNCTION,
    }, indent=1) + "\n"


def attach(level, level2):
    """
    Joins Level 2 to Level 1: a doorway opens in the far wall of Level 1's dark landing, under
    its "B2" sign, and Level 2's entry corridor continues from it. Level 2's blocks are copied
    into Level 1's world, moved by the returned (dx, dy, dz) (the entry corridor leaves the
    doorway's wall column to Level 1). Fails if Level 2 would overlap Level 1 anywhere. Labels, floor and lights
    are copied too, for the preview.
    """
    land = level.landing
    if land is None or not land["east"]:
        raise SystemExit("Level 2 joins Level 1's landing on its east side: Level 1 needs an odd number of halls")
    ex, ey, ez = level2.entry
    joint = (land["far"] + 1 - ex, land["ly"] - ey, land["cz"] - ez)
    level.open_landing()
    dx, dy, dz = joint
    clashes = []
    for (x, y, z), state in level2.world.blocks.items():
        pos = (x + dx, y + dy, z + dz)
        if pos in level.world.blocks:
            clashes.append(pos)
        level.world.blocks[pos] = state
    if clashes:
        raise SystemExit(f"Level 2 overlaps Level 1 at {len(clashes)} blocks, e.g. {clashes[:3]}; move or shorten its halls")
    for (x, z), y in level2.floor.items():
        level.floor[(x + dx, z + dz)] = y + dy
    level.labels += [(x + dx, z + dz, text) for x, z, text in level2.labels]
    level.fixtures += [(x + dx, z + dz, state) for x, z, state in level2.fixtures]
    return joint


def write_pack(name, cmds, box, previous, level=None, offset=None, level2=None, joint=None):
    pack = OUT / name
    if pack.exists():
        shutil.rmtree(pack)
    functions = pack / "data" / "garage" / "functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 15, "description": f"Every Tunnel {LEVEL_SIGN} (level 1){' for the mod' if level else ''}, seed {SEED}"}}, indent=2) + "\n")
    # The level is bigger than what is loaded around a player, so building and clearing first
    # force-load its chunks (and those of the previous build), wait until they are loaded,
    # do the work and release them. Scheduled functions run without a player, so the vanilla
    # build runs as the (single) player and the messages go to everyone. The mod's build runs
    # with no player and says nothing: the mod runs it every time the world opens, behind its
    # loading screen, and starts the new game when `/everytunnel rebuilt` (its last command) runs.
    area = box if previous is None else (min(box[0], previous[0]), 0, min(box[2], previous[2]),
                                         max(box[3], previous[3]), 0, max(box[5], previous[5]))
    chunks = (area[3] // 16 - area[0] // 16 + 1) * (area[5] // 16 - area[2] // 16 + 1)
    if chunks > 256:
        raise SystemExit(f"the level spans {chunks} chunks; /forceload takes at most 256: make it smaller")
    forceload = f"forceload add {area[0]} {area[2]} {area[3]} {area[5]}"
    release = f"forceload remove {area[0]} {area[2]} {area[3]} {area[5]}"
    corners = [(area[0], box[1], area[2]), (area[3], box[1], area[5]), (area[0], box[1], area[5]), (area[3], box[1], area[2])]
    loaded = " ".join(f"if loaded {x} {y} {z}" for x, y, z in corners)
    unloaded = " ".join(f"unless loaded {x} {y} {z}" for x, y, z in corners[:1])
    loading = '{"text":"Loading the level area...","color":"gray"}'
    mod = level is not None
    (functions / "build.mcfunction").write_text("\n".join(
        [forceload] + ([] if mod else [f"tellraw @a {loading}"]) + ["schedule function garage:build_wait 5t replace"]) + "\n")
    (functions / "build_wait.mcfunction").write_text("\n".join([
        f"execute {loaded} run function garage:build_now" if mod else f"execute {loaded} as @a[limit=1] run function garage:build_now",
        f"execute {loaded} run {release}",
        f"execute {unloaded} run schedule function garage:build_wait 5t replace",
    ]) + "\n")
    # Vanilla auto build: the first time a player is in the world with this version of the
    # level (a fingerprint of its commands, kept in a scoreboard), build it once. A new version
    # (other parameters) builds again; the same one never does, even after garage:clear.
    # The mod's pack has none of it but the objective (the build keeps doTileDrops in it): the
    # mod builds every time the world opens.
    version = zlib.crc32("\n".join(cmds).encode()) & 0x7FFFFFFF
    built = f"score #level {SCORE} matches {version}"
    (functions / "load.mcfunction").write_text(f"scoreboard objectives add {SCORE} dummy\n")
    if mod:
        (functions / "build_now.mcfunction").write_text("\n".join(cmds) + "\n")
    else:
        (functions / "build_now.mcfunction").write_text("\n".join(cmds + [f"scoreboard players set #level {SCORE} {version}"]) + "\n")
        (functions / "tick.mcfunction").write_text(f"execute if entity @a unless {built} unless score #waiting {SCORE} matches {version} run function garage:auto_build\n")
        (functions / "auto_build.mcfunction").write_text("\n".join([
            f"scoreboard players set #waiting {SCORE} {version}",
            "function garage:build",
        ]) + "\n")
    x1, y1, z1, x2, y2, z2 = box
    kill_pale_men = [f"kill @e[type=every_tunnel:pale_man,x={x1},y={y1},z={z1},dx={x2 - x1},dy={y2 - y1},dz={z2 - z1}]"] if level else []
    (functions / "clear.mcfunction").write_text("\n".join([
        forceload, f"tellraw @a {loading}", "schedule function garage:clear_wait 5t replace"]) + "\n")
    (functions / "clear_wait.mcfunction").write_text("\n".join([
        f"execute {loaded} run function garage:clear_now",
        f"execute {unloaded} run schedule function garage:clear_wait 5t replace",
    ]) + "\n")
    (functions / "clear_now.mcfunction").write_text("\n".join(
        without_drops(fills(*box, "minecraft:air"), [box]) + kill_pale_men
        + [release, 'tellraw @a {"text":"Level cleared.","color":"gray"}']) + "\n")
    tags = pack / "data" / "minecraft" / "tags" / "functions"
    tags.mkdir(parents=True)
    for tag in ("load",) if mod else ("load", "tick"):
        (tags / f"{tag}.json").write_text(json.dumps({"values": [f"garage:{tag}"]}, indent=2) + "\n")
    if level:
        levels = pack / "data" / "every_tunnel" / "levels"
        levels.mkdir(parents=True)
        (levels / "level_1.json").write_text(level_json(level, offset))
        (levels / "level_2.json").write_text(next_level_json(level, level2, offset, joint))
    with zipfile.ZipFile(OUT / f"{name}.zip", "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(pack.rglob("*")):
            if f.is_file():
                z.write(f, f.relative_to(pack).as_posix())


def install(resource_zip):
    for target in INSTALL:
        world = Path(target["world"])
        if not (world / "level.dat").is_file():
            if target.get("optional"):
                continue
            print(f"WARNING: no world at {world}, nothing installed there (create the world first, see README)")
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

    p2 = SimpleNamespace(**dict(vars(p), **LEVEL_2))
    levels, levels2, joints = {}, {}, {}
    for v in ("vanilla", "mod"):
        levels[v] = Level(p, v).compose()
        levels2[v] = Level(p2, v, glyphs=levels[v].glyphs).compose()
        joints[v] = attach(levels[v], levels2[v])
    gv, gm = levels["vanilla"].glyphs, levels["mod"].glyphs
    if gv.wall != gm.wall or gv.pillar != gm.pillar or gv.pieces != gm.pieces:
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
        mod = variant == "mod"
        write_pack(PACKS[variant], cmds, box, previous, level if mod else None, offset, levels2[variant] if mod else None, joints[variant])
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
    v2 = levels2["vanilla"]
    reports2 = [h["report"] for h in v2.halls]
    stats += [
        f"{LEVEL_2['LEVEL_SIGN']}: {len(v2.halls)} halls, joined at {joints['vanilla']}; Pale men "
        + ", ".join(f"hall {n}: {c}" for n, c in sorted(LEVEL_2["PALE_MEN"].items())),
        f"{LEVEL_2['LEVEL_SIGN']} clear line of sight per hall: " + ", ".join(str(r["line_of_sight"]) for r in reports2) + " blocks",
    ] + [f"{LEVEL_2['LEVEL_SIGN']} hall {i + 1} oddity: {o['what']}" for i, r in enumerate(reports2) for o in r["oddities"]]
    V.write_level(OUT / "level_preview.png", v, stats)
    V.write_texture_sheet(OUT / "texture_sheet.png", textures)
    V.write_pbr_sheet(OUT / "pbr_sheet.png", textures)
    state_file.write_text(json.dumps({"box": box}) + "\n")

    stats.append(MS.write(p, REPO / "src" / "main" / "resources" / "assets" / "every_tunnel"))

    for line in stats:
        print(line)
    if min(r["line_of_sight"] for r in reports) < 30:
        print("WARNING: a hall has a clear line of sight under 30 blocks")
    install(resource_zip)


if __name__ == "__main__":
    main()
