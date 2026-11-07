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
  level3_preview.png             top-down plan of Level 3 (the offices), read from its blocks
  level3_furniture.png           the same, bigger, to read the furniture
  texture_sheet.png              every generated texture, enlarged
  level3_texture_sheet.png       Level 3's own textures, enlarged
  pbr_sheet.png                  each texture's LabPBR maps: albedo | normal | specular | emission
Level 3 (LEVEL_3, built by offices/) is only in the mod's pack. garage:build builds in two
stages, each force-loading its own area (one area over all three levels would pass the 256
chunks /forceload takes): Levels 1 and 2 (build_now), then Level 3 (level3_now), which ends
with `/everytunnel rebuilt`.
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
from offices import checks as OC       # noqa: E402
from offices import level as OF        # noqa: E402
from offices import preview as OV      # noqa: E402
from lounge.level import Lounge        # noqa: E402
from lounge import checks as LC        # noqa: E402
from lounge import preview as LV       # noqa: E402
from lounge import textures as LT      # noqa: E402
from lab.level import Lab, stretch_air  # noqa: E402
from lab import checks as BC           # noqa: E402
from lab import preview as BV          # noqa: E402
from lab import textures as BT         # noqa: E402
from offices import textures as OT     # noqa: E402

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
# keycard_exit side room: behind the EXIT door, a round shaft with metal stairs spiralling down
# (EXIT_TURNS turns, EXIT_TURN_DROP blocks each) to a landing with the next level's sign.
EXIT_TURNS = 4
EXIT_TURN_DROP = 8
EXIT_DOOR_OPEN = True     # the door under the next level's sign at the bottom stands open: Level 3 lies behind it
EXIT_END = False          # the bottom landing ends the demo (a zone tagged end); Level 3's bunker does now
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

# --- Level 3 (B3) ----------------------------------------------------------------------
# The offices of the complex around the central library, in perfect order and empty (built by
# offices/level.py). Plan coordinates as in the level's drawing: x east, z south, boxes
# (x0, z0, x1, z1) with x1 and z1 exclusive, one block of wall between spaces. Its stairs
# climb to the open door at the bottom of Level 2's spiral stairs (attach_level_3), so the
# plan is turned half round: its north lies to the world's south. Only in the mod's pack.
# Phase A, the structure: zones and spawn points are the hooks for the observant guard, the
# books, the valves, the notes and the loot to come.
LEVEL_3 = dict(
    LEVEL_SIGN="B3",
    NEXT_LEVEL_SIGN="B4",            # over the door at the end of the bunker corridor
    TITLE="Level 3",                 # shown on walking into the antechamber
    # Air blocks over the floor; doors through walls are lower than the spaces they join.
    HEIGHTS=dict(corridor=4, room=4, library=12, antechamber=3, stairs=4),
    DOOR_HEIGHTS=dict(room=3, library=4),
    LIBRARY=(48, 66, 112, 130),
    # The inner corridor, a ring 4 wide round the library: north, south, west, east.
    INNER_CORRIDOR=[(43, 61, 117, 65), (43, 131, 117, 135), (43, 61, 47, 135), (113, 61, 117, 135)],
    LIBRARY_DOORS=[(78, 65, 82, 66), (78, 130, 82, 131), (47, 96, 48, 100), (112, 96, 113, 100)],
    # The belt of rooms round the ring, 14 deep: (zone id, name, box, type). "service": a
    # service room (vinyl floor); "office": an ordinary room (carpet).
    ROOMS=[
        ("archive", "Archive", (43, 46, 68, 60), "office"),
        ("meeting_room", "Meeting room", (69, 46, 91, 60), "office"),
        ("management", "Management", (92, 46, 117, 60), "office"),
        ("finance", "Finance", (118, 61, 132, 85), "office"),
        ("servers", "Servers", (118, 86, 132, 110), "service"),
        ("office", "Office", (118, 111, 132, 135), "office"),
        ("maintenance", "Maintenance", (43, 136, 61, 150), "office"),
        ("storage", "Storage", (62, 136, 77, 150), "service"),
        ("electrical", "Electrical", (83, 136, 99, 150), "service"),
        ("lockers", "Lockers", (100, 136, 117, 150), "office"),
        ("hr", "HR", (28, 61, 42, 85), "office"),
        ("cafeteria", "Kitchen", (28, 86, 42, 110), "office"),
        ("printing", "Printing", (28, 111, 42, 135), "service"),
        ("open_office_nw", "Open office", (28, 46, 42, 60), "office"),
        ("open_office_ne", "Open office", (118, 46, 132, 60), "office"),
        ("changing_room", "Changing room", (28, 136, 42, 150), "office"),
        ("break_room", "Break room", (118, 136, 132, 150), "office"),
    ],
    # Doors through the one-block walls, ROOM_DOOR_WIDTH wide: ("z", z, centre x) in a wall
    # running east-west, ("x", x, centre z) in one running north-south.
    ROOM_DOOR_WIDTH=2,
    ROOM_DOORS=([("z", 60, x) for x in (55, 80, 104)]            # north rooms to the ring
                + [("x", 117, z) for z in (73, 98, 123)]         # east rooms
                + [("z", 135, x) for x in (52, 69, 91, 108)]     # south rooms
                + [("x", 42, z) for z in (73, 98, 123)]          # west rooms
                # The corner rooms, through their neighbours.
                + [("x", 42, 53), ("z", 60, 35), ("x", 117, 53), ("z", 60, 125),
                   ("x", 42, 143), ("z", 135, 35), ("x", 117, 143), ("z", 135, 125)]),
    # The way in from the antechamber: the valve corridor, through the belt into the ring.
    VALVE_CORRIDOR=(78, 135, 82, 190),
    CHECKPOINT_Z=154,                # a wall across it with a closed double door
    CHECKPOINT_DEPTH=3,              # the checkpoint zone: this many blocks past that door
    VALVES=[("A", 160), ("B", 166), ("C", 172), ("D", 178), ("E", 184)],    # (letter, z) on the east wall
    VALVE_HEIGHT=2,                  # the valve's block in its pipe, over the floor's blocks (hand height)
    ANTECHAMBER=(74, 190, 86, 200),  # the electrical room (ELECTRICAL_ROOM)
    # The stairs up to Level 2, climbing south: a landing of STAIRS_LANDING at each end and
    # STAIRS_DROP steps (one block each) between them; its length must be 2 landings + the steps.
    STAIRS=(76, 200, 84, 212),
    STAIRS_DROP=8,
    STAIRS_LANDING=2,
    PLAYER_START=(80, 196, "north"),                     # (x, z, facing), in the antechamber
    # The hatch in the library's centre: a 4 x 4 frame round a 2 x 2 hole down to the bunker.
    HATCH=dict(frame=(78, 96, 82, 100), hole=(79, 97, 81, 99)),
    # The bunker: its tunnel's floor `depth` blocks under the main floor, `width` wide and
    # `height` high, from the foot of the shaft to the door to Level 4 along `path`: (direction,
    # length) legs, each starting on the last one's end cell (so a turn shares its corner).
    # Pipes run along its ceiling on one side, a cable tray on the other; `blast_doors`: open
    # blast doors, at these distances along the path (on a straight stretch, 2 blocks or more
    # from a turn and with 3 more after them for the leaf folded against the wall).
    BUNKER=dict(depth=7, width=3, height=4, path=[("south", 24), ("west", 22), ("south", 22), ("east", 26)],
                blast_doors=[16, 56]),
    # Zones in the last leg of the tunnel, in blocks before the door to Level 4 (from, to):
    # where the collapse goes off, and the stretch behind the player its rubble fills.
    COLLAPSE=dict(trigger=(14, 12), blocks=(20, 17)),
    GUARD_SPAWN=(80, 88),            # the Observer: (x, z), in the library's north-south aisle; he patrols the library
    # Ambience (AmbienceHooks): the empty offices' room tone and, rarely, a phone ringing far off,
    # everywhere; the library has its slow clock instead (with the phone), the bunker silence.
    AMBIENCE={"loop": "every_tunnel:ambient.offices.room_tone", "loop_volume": 1.0,
              "random": [{"sound": "every_tunnel:ambient.offices.phone", "interval": [90, 240], "distance": [10, 16], "volume": 0.8}]},
    ZONE_AMBIENCE={
        "library": {"loop": "every_tunnel:ambient.library.clock", "loop_volume": 1.0,
                    "random": [{"sound": "every_tunnel:ambient.offices.phone", "interval": [90, 240], "distance": [10, 16], "volume": 0.8}]},
        "bunker_shaft": {}, "bunker_corridor": {},
    },
    # Furniture (offices/furnish.py): each room's template; the same template, the same room.
    FURNISH=dict(
        open_office_nw="open_office", open_office_ne="open_office", archive="archive", meeting_room="meeting",
        management="management", finance="finance", servers="servers", office="desks", maintenance="maintenance",
        storage="storage", electrical="electrical", lockers="lockers", hr="hr", cafeteria="cafeteria",
        printing="printing", changing_room="changing", break_room="break",
    ),
    DOOR_CLEARANCE=2,                # no furniture within this many blocks of a room's doors
    # The library: shelves this high, rows `aisle` apart, a `perimeter` aisle along the walls,
    # a square clearing round the hatch; the librarian's desk in a U round the hatch, `ring`
    # blocks of floor between it and the hatch's frame, open on the `open` side, `returns`
    # places on it for the books and a lamp at the `lamp` corner; the book puzzle's spots
    # (`books`: how many on book carts, on the floor of the aisles and on the card catalogue); the card catalogue (x, (z0, z1), facing) by the south door, in its area
    # (x0, z0, x1, z1; no shelves there).
    LIBRARY_LAYOUT=dict(shelf_height=4, aisle=2, perimeter=2, clearing=16, books=dict(carts=6, floor=6, catalog=1), desk=dict(
        ring=1, open="south", returns=5, lamp="northwest",
        catalog=(84, (120, 123), "west"), catalog_area=(83, 116, 93, 127))),
    # The antechamber is the electrical room: panels on its side walls (x, z, facing), cabinets,
    # and the cork board with the electrician's note (`id` names its text, notes/<lang>/<id>.txt
    # in the mod; it tells this game's valve digits).
    ELECTRICAL_ROOM=dict(
        panels=[(74, 191, "east"), (74, 193, "east"), (74, 197, "east"),
                (85, 191, "west"), (85, 193, "west"), (85, 195, "west"), (85, 197, "west")],
        cabinets=[(74, 199, "east"), (85, 199, "west")],
        note=dict(id="pressure_line", x=74, z=195, facing="east")),
    # Painted on the walls (wall_messages.py): (id, x, y, z, the way it looks).
    WALL_MESSAGES=[("never_paid", 131, 3, 68, "west"), ("face_to_work", 98, 3, 46, "south"), ("silence", 78, 3, 172, "east")],
    # Big lettering over the way into the antechamber, seen coming down the stairs (a wall_text
    # block in front of the wall: its lang key, (x, y, z) of the block, the way the text faces,
    # and how far right of the block's centre the text's centre is).
    ENTRANCE_SIGN=dict(text="sign.every_tunnel.level_3_entrance", at=(79, 7, 200), facing="south", offset=0.5),
    CORRIDOR_PLANTS=[(43, 61), (116, 61), (43, 134), (116, 134)],                 # (x, z): the ring's corners
    CORRIDOR_CLOCKS=[(67, 61, "south"), (100, 134, "north"), (43, 110, "east"), (116, 110, "west")],
    # The box puzzle (BoxHooks): how many boxes a new game puts out, the spots round the ring
    # (one more in every room but these); the empty shelf they go on is in Storage.
    BOX_PUZZLE=dict(boxes=10, ring=9, not_in=["storage"]),
    # The company's documents (the lore): room id -> note id (notes/<lang>/<id>.txt in the mod).
    # Each lies as a loose sheet on a desk or cabinet in its room, the one nearest the main door.
    NOTE_PAPERS={"hr": "reading_room_supervision", "archive": "archive_procedure",
                 "lockers": "staff_lockers", "management": "staff_complaint", "finance": "reactor_invoice"},
    # Loot (LootSpawner): (item, room id) per piece, "battery" or "water", lying on the floor
    # against a wall of that room (clear of doors, lockers and the box spot); the same spot
    # every run. Water comes from WATER_SOURCES instead: the water dispensers of these rooms
    # are drunk from (WaterDispenserHooks), as often as the player likes.
    LOOT=[("battery", "electrical"), ("battery", "electrical"), ("battery", "electrical")],
    WATER_SOURCES=["cafeteria"],
    LIGHT_SPACING=6,
    PENDANT=dict(height=5, spacing=8),   # the library's lights hang on chains, this high over the floor
    # What each kind of space is made of (roles of STAND_INS below).
    MATERIALS=dict(
        office=dict(floor="carpet", wall="plaster", base="skirting", ceiling="ceiling_tile"),
        service=dict(floor="vinyl", wall="plaster", base="skirting", ceiling="ceiling_tile"),
        corridor=dict(floor="vinyl", wall="plaster", base="skirting", ceiling="ceiling_tile"),
        door=dict(floor="vinyl", wall="plaster", base="skirting", ceiling="ceiling_tile"),
        library=dict(floor="library_carpet", wall="wood", base="wood", ceiling="ceiling_tile"),
        bunker=dict(floor="concrete_floor", wall="concrete", base="concrete", ceiling="concrete"),
    ),
    # A wall between two kinds of space takes the materials of the first in this list.
    WALL_PRIORITY=["library", "bunker", "office", "service", "corridor", "door"],
    # Stand-in blocks re-skinned by Level 3's textures (offices/textures.py). Never a block
    # Levels 1 and 2 use: the world's resource pack re-skins a block everywhere.
    STAND_INS=dict(
        carpet="minecraft:light_blue_wool",
        library_carpet="minecraft:green_wool",
        vinyl="minecraft:light_gray_terracotta",
        plaster="minecraft:white_terracotta",
        skirting="minecraft:gray_terracotta",
        wood="minecraft:dark_oak_planks",
        ceiling_tile="minecraft:calcite",
        concrete="minecraft:smooth_basalt",
        concrete_floor="minecraft:deepslate_tiles",
        stairs="minecraft:mud_brick_stairs",
    ),
    # Other blocks, as they are.
    BLOCKS=dict(
        door="every_tunnel:emergency_door",
        ladder="minecraft:ladder",
        pipe="every_tunnel:pipe",
        valve="every_tunnel:valve",
        hatch_frame="every_tunnel:checker_plate",
        hatch_lid="every_tunnel:bunker_trapdoor",
        blast_frame="every_tunnel:hazard_steel",
        blast_leaf="every_tunnel:blast_door",
        cable_tray="every_tunnel:cable_tray",
    ),
)

# --- The main menu's scene -------------------------------------------------------------
# One hall like the level's (these values over the general ones below), drawn behind the menu
# from a fixed camera at the west end of its aisle, looking east to a closed emergency door
# under a lit EXIT sign (garage/menu_scene.py). Its lamps flicker now and then in the menu.
# Level 4 (B4), the VIP lounge. Plan coordinates as in Level 3: x east, z south, boxes (x0, z0, x1, z1)
# with x1 and z1 exclusive, the sizes are of the air inside. The user's table, with its rooms at 2/3 (its
# corridors kept) and a wall between the rooms that touched (Security, Hall, Bar).
LEVEL_4 = dict(
    TITLE="Level 4",
    FLOOR_Y=9,                       # the world y of the main floor's blocks (the B4 door's floor)
    # (id, name, box, height, kind); kinds: "piece" gives a piece, "rest", "key", "neutral".
    ROOMS=[
        ("ch1_l4_security", "Security room", (40, 0, 60, 11), 4, "neutral"),
        ("ch1_l4_hall", "Hall", (45, 12, 55, 28), 4, "neutral"),
        ("ch1_l4_bar", "Bar lounge", (37, 29, 64, 45), 5, "piece"),
        ("ch1_l4_cinema", "Cinema", (15, 30, 31, 44), 4, "piece"),
        ("ch1_l4_spa", "Spa and pool", (70, 30, 86, 44), 4, "piece"),
        ("ch1_l4_suites", "Suites", (16, 13, 32, 27), 4, "rest"),
        ("ch1_l4_game", "Game room", (68, 13, 84, 27), 4, "piece"),
        ("ch1_l4_reception", "Reception", (42, 51, 58, 59), 4, "neutral"),
    ],
    # Lower than the rest: (id, name, box, height, kind, floor relative to the main floor).
    CALM_ROOM=("ch1_l4_calm", "Calm room", (90, 30, 104, 44), 4, "key", -3),
    # Corridors 6 wide, open onto the rooms they join; height as the rooms'.
    CORRIDOR_HEIGHT=4,
    CORRIDORS=[
        ("ch1_l4_corridor_reception", (47, 45, 53, 51)),
        ("ch1_l4_corridor_cinema", (31, 34, 37, 40)),
        ("ch1_l4_corridor_spa", (64, 34, 70, 40)),
        ("ch1_l4_corridor_suites", (32, 17, 45, 23)),
        ("ch1_l4_corridor_game", (55, 17, 68, 23)),
    ],
    # Down to the Calm room, west to east: one cell of landing, then one step down per cell.
    STEPS=("ch1_l4_steps", (86, 34, 90, 40)),
    # Behind Reception's east wall, by a passage 2 high: the Maintenance room, bare concrete and fluorescents.
    MAINTENANCE=dict(id="ch1_l4_maintenance", name="Maintenance", box=(60, 47, 70, 57), height=4, door_id="ch1_l4_door_maintenance",
                     passage=(58, 54, 60, 55), door=(58, 54), lights=[(63, 52), (67, 52)]),
    # On the way back from the Calm room, the Cinema wakes on coming round the pool: its west side, the
    # corridor and the Bar. A box (x0, z0, x1, z1), exclusive at the far side.
    PAST_POOL=(37, 29, 74, 45),
    # Not stand-ins of its own: Level 3's bare concrete, the same look on purpose.
    SERVICE=dict(service_floor="minecraft:deepslate_tiles", service_wall="minecraft:smooth_basalt"),
    # The wall at the top of the steps, on its landing, with the Calm room's double door.
    CALM_WALL=dict(x=86, z0=34, z1=40, door=(36, 37)),
    # Openings through the one-block walls: (id, axis of the wall's line, line, first cell, width, height, kind).
    OPENINGS=[
        ("ch1_l4_arch_bar", "z", 28, 47, 6, 4, "arch"),
        ("ch1_l4_door_security", "z", 11, 49, 2, 2, "door"),
        ("ch1_l4_door_reception", "z", 59, 50, 2, 2, "steel_door"),
        ("ch1_l4_door_big", "z", -1, 47, 6, 4, "big_door"),      # shut until the Master Key opens it
    ],
    # Between the door to B4 and Reception: 4 wide, 20 long, diplomas on its walls.
    DIPLOMA_CORRIDOR=("ch1_l4_diplomas", "Diploma corridor", (49, 60, 53, 80), 4),
    B4_DOOR=(50, 80),                # its cell (x, z) in the corridor's far wall; world (33, 10, 119)
    # Past the big door, dark and the lounge's to its end: walking it starts Level 5's build, and a steel
    # frame in its side by the far end opens onto Level 5's entry corridor (LEVEL_5; built with this level).
    EXIT_STRETCH=("ch1_l4_exit", "Dark stretch", (47, -45, 53, -1), 4),
    EXIT_SHUT_AT=35,                 # cells from its far end, inclusive: the door slams from here on
    START=(50, 55),                  # the checkpoint, in Reception, facing north
    # Plan to world: the plan's north is the world's west, so the level meets the corridor from B4:
    # world x = z + PLACE_X, world z = PLACE_Z - x.
    PLACE_X=-47,
    PLACE_Z=169,
    # Stand-in blocks re-skinned by Level 4's textures (lounge/textures.py). Never a block another
    # level uses: the world's resource pack re-skins a block everywhere.
    STAND_INS=dict(
        carpet="minecraft:red_wool",
        walnut="minecraft:brown_terracotta",
        rail="minecraft:orange_terracotta",
        cream="minecraft:lime_terracotta",
        ceiling="minecraft:pink_terracotta",
        stairs="minecraft:red_nether_brick_stairs",
        calm_carpet="minecraft:cyan_terracotta",
        calm_wall="minecraft:purple_terracotta",
        pool_tile="minecraft:light_blue_terracotta",
    ),
    # Thin walls inside rooms, (x0, z0, x1, z1): the Suites' four rooms round an inner corridor, and the
    # Cinema's projection booth. A door in one: (x, z, open), on a wall along x. The furniture is in furnish.py.
    PARTITIONS=[(16, 17, 32, 18), (16, 22, 32, 23), (24, 13, 25, 17), (24, 23, 25, 27), (23, 30, 24, 34), (23, 34, 31, 35)],
    PARTITION_DOORS=[(20, 17, False), (28, 17, False), (20, 22, False), (28, 22, True), (27, 34, False)],
    BOOTH=(24, 30, 31, 35),          # the Cinema's projection booth with its door's row
    # Beds in the Suites: the head's cell and the way it points, the foot on the far side.
    BEDS=[(19, 13, "north"), (27, 13, "north"), (19, 26, "south"), (27, 26, "south")],
    # The Spa's pool: its water's box, how deep, and the cell of the piece on its floor.
    POOL=dict(box=(74, 33, 83, 41), depth=6, piece=(78, 37)),
    LAMP="every_tunnel:warm_lamp[lit=true]",
    LAMP_SPACING=5,
    LAMP_SPACING_BAR=4,              # the Bar has the highest ceiling, so more of them
    DOORS=dict(door="minecraft:dark_oak_door", steel_door="every_tunnel:emergency_door", calm_door="every_tunnel:calm_door"),
    # Level 3's door to B4 and the corridor behind it (the corridor is built with Level 3, the rest
    # in play: see level4_steps). A step holds at most so many commands and blocks changed.
    STEP_COMMANDS=40,
    STEP_VOLUME=33000,
)

# Level 5 (B5), the laboratory: layers 1 and 2 (layer 3, the drain, comes later). Plan coordinates as in
# Level 4: x east, z south, boxes (x0, z0, x1, z1) with x1 and z1 exclusive, the sizes are of the air
# inside. The user's prototype at 2/3, corridors 4 wide, straight; layer 2 as two loops from the Lab hall
# (north past the Office and back, south past Storage to the chemical room). Both layers share the plan:
# layer 1 is the upper floor, layer 2 lies LAYER_DROP under it, so a cell may hold a space of each.
LEVEL_5 = dict(
    TITLE="Level 5",
    FLOOR_Y=9,                       # the world y of layer 1's floor blocks (Level 4's floor)
    LAYER_DROP=10,                   # layer 2's floor blocks are this much lower
    ROOM_HEIGHT=5,
    CORRIDOR_HEIGHT=4,
    # (id, zone, name, layer, box, height or None for the kind's, kind). Kinds: "entry", "room",
    # "equipment", "loot", "lore", "hub", "puzzle", "hatch", "stairs" and "corridor" (corridors and stairs
    # open onto what they touch; any other two spaces keep a wall between them). The zone ids are the plan's.
    SPACES=[
        # Layer 1: in from Level 4's dark stretch through a steel frame, a dark corridor of the lab's tiles
        # and the airlock, then the Lobby and a ring of corridors.
        ("entry", "entry_corridor", "Entry corridor", 1, (-16, 12, -4, 16), None, "entry"),
        ("airlock", "airlock", "Airlock", 1, (-3, 12, 1, 16), 4, "airlock"),
        ("lobby", "lobby", "Lobby", 1, (2, 7, 21, 22), None, "room"),
        ("workshop", "workshop", "Workshop", 1, (29, 2, 54, 13), None, "equipment"),
        ("waiting", "waiting", "Waiting room", 1, (58, 2, 75, 13), None, "room"),
        ("lockers", "lockers", "Lockers", 1, (24, 21, 44, 29), None, "loot"),
        ("ring_north", "layer1_corridor", "", 1, (21, 14, 75, 18), None, "corridor"),
        ("ring_east", "layer1_corridor", "", 1, (71, 18, 75, 33), None, "corridor"),
        ("ring_south", "layer1_corridor", "", 1, (9, 33, 75, 37), None, "corridor"),
        ("ring_west", "layer1_corridor", "", 1, (9, 22, 13, 33), None, "corridor"),
        ("lockers_north", "layer1_corridor", "", 1, (32, 18, 36, 21), None, "corridor"),
        ("lockers_south", "layer1_corridor", "", 1, (32, 29, 36, 33), None, "corridor"),
        ("stairs_top", "layer1_stairs", "Stairs", 1, (6, 38, 16, 44), None, "stairs"),
        # Layer 2: the subjects' corridor to the Lab hall, and the two loops round it.
        ("stairs_bottom", "layer1_stairs", "Stairs", 2, (5, 54, 17, 63), None, "stairs"),
        ("subjects", "subjects_corridor", "Subjects corridor", 2, (17, 57, 73, 61), None, "corridor"),
        ("s1", "s1", "S-1", 2, (21, 45, 35, 56), None, "lore"),
        ("s2", "s2", "S-2", 2, (29, 62, 43, 72), None, "lore"),
        ("s3", "s3", "S-3", 2, (43, 45, 57, 56), None, "lore"),
        ("s4", "s4", "S-4", 2, (51, 62, 65, 72), None, "lore"),
        ("lab_hall", "lab_hall", "Lab hall", 2, (73, 50, 98, 71), 7, "hub"),
        ("north_west", "north_route", "", 2, (75, 26, 79, 50), None, "corridor"),
        ("north_east", "north_route", "", 2, (92, 26, 96, 50), None, "corridor"),
        ("office", "office", "Office", 2, (74, 13, 97, 25), None, "lore"),
        ("south_a", "south_route", "", 2, (76, 72, 80, 84), None, "corridor"),
        ("south_b", "south_route", "", 2, (76, 84, 98, 88), None, "corridor"),
        ("storage", "storage", "Storage", 2, (98, 80, 116, 90), None, "loot"),
        ("south_c", "south_route", "", 2, (116, 70, 120, 84), None, "corridor"),
        ("chemical_room", "chemical_room", "Chemical room", 2, (108, 50, 138, 70), None, "puzzle"),
        ("shortcut", "shortcut", "Shortcut", 2, (99, 58, 108, 62), None, "corridor"),
        ("drain_hatch", "drain_hatch", "Drain hatch", 2, (124, 71, 134, 79), None, "hatch"),
    ],
    # From layer 1's stairs down to layer 2's: one block down per cell along z, from the first cell on.
    FLIGHT=("stairs_flight", "layer1_stairs", (9, 44, 13, 54)),
    # Openings through the one-block walls: (id, layer, axis of the wall's line, line, first cell, width,
    # height, kind). Kinds: "doorway" (open), "locked" (a sliding steel door like the airlock's, shut until
    # a lever or an event opens it: LabDoorHooks), "frame" (open, in steel: the way in from Level 4's dark stretch, in its side wall),
    # "airlock_rear" and "airlock_front" (the airlock's sliding doors: the one behind stands open until the
    # player is in, the one ahead shut until the level stands; LabHooks), "grate" (iron bars the chemical
    # puzzle will dissolve).
    OPENINGS=[
        ("entry_frame", 1, "x", -17, 12, 4, 3, "frame"),
        ("airlock_rear", 1, "x", -4, 12, 4, 3, "airlock_rear"),
        ("welcome_door", 1, "x", 1, 12, 4, 3, "airlock_front"),
        ("workshop_door", 1, "z", 13, 41, 2, 3, "doorway"),
        ("waiting_door", 1, "z", 13, 66, 2, 3, "doorway"),
        ("stairs_door", 1, "z", 37, 10, 2, 3, "locked"),            # the lever by the fuse box in the ring
        ("s1_door", 2, "z", 56, 27, 2, 3, "doorway"),
        ("s3_door", 2, "z", 56, 49, 2, 3, "doorway"),
        ("s2_door", 2, "z", 61, 35, 2, 3, "doorway"),
        ("s4_door", 2, "z", 61, 57, 2, 3, "doorway"),
        ("office_door_west", 2, "z", 25, 76, 2, 3, "doorway"),
        ("office_door_east", 2, "z", 25, 93, 2, 3, "doorway"),
        ("south_door", 2, "z", 71, 77, 2, 3, "locked"),             # opens on leaving the Office
        ("shortcut_door", 2, "x", 98, 59, 2, 3, "locked"),          # the chemical room's lever, from inside
        ("hatch_grate", 2, "z", 70, 127, 4, 3, "grate"),
    ],
    START=(-16, 13),                 # layer 1: the entry corridor's first cell, past the frame
    CHECKPOINT=(77, 67),             # layer 2: in the Lab hall
    # Plan to world: the plan's south is the world's west, so the entry corridor leaves Level 4's dark
    # stretch at its far end, to its left: world x = PLACE_X - z, world z = PLACE_Z + x.
    PLACE_X=-77,
    PLACE_Z=140,
    # The player's start (a death comes back here) and the Lab hall's restart point, the checkpoint walking
    # into the Lab hall makes: (layer, x, z, plan facing).
    PLAYER_START=(1, 4, 14, "east"),
    CHECKPOINT_POINT=(2, 77, 67, "east"),
    # The levers that open locked doors, Level 4's Maintenance lever on a wall at chest height: (id, layer, x,
    # z, plan facing, away from its wall); "lever_<door>" opens that door. Where they go for good is for the
    # levels' content: for now the stairs' is on the ring's east wall, the shortcut's by the shortcut.
    LEVERS=[("lever_stairs_door", 1, 74, 25, "west"), ("lever_shortcut_door", 2, 108, 55, "east")],
    LEVER="every_tunnel:breaker_lever[facing={facing},on=false]",
    # Loot on the floor: (id, tag, layer, x, z). The wrench and the detector lie in the Workshop for now.
    # Stand-in blocks re-skinned by Level 5's textures (lab/textures.py): white wall tiles, a pale green
    # band of them at the foot of the walls, grey vinyl, a ceiling of white panels, vinyl stairs. Never a
    # block another level uses: the world's resource pack re-skins a block everywhere.
    STAND_INS=dict(
        tile="minecraft:quartz_bricks",
        band="minecraft:lime_concrete",
        vinyl="minecraft:cyan_concrete",
        ceiling="minecraft:smooth_quartz",
        stairs="minecraft:polished_andesite_stairs",
        steel="minecraft:waxed_cut_copper",
    ),
    CHECKER="every_tunnel:checker_plate",     # the airlock's floor and the thresholds of steel
    AIRLOCK_DOOR="every_tunnel:airlock_door[facing={facing},width={width}]",
    LIGHT="every_tunnel:fluorescent_light[facing=down,axis={axis},mode=on,lit=true]",
    LIGHT_SPACING=5,
    GRATE="minecraft:iron_bars[east={ew},north={ns},south={ns},west={ew},waterlogged=false]",
    # Built in play while the player walks Level 4's dark stretch and the entry corridor (LabHooks): a step
    # holds at most so many commands and blocks changed, one step every 2 ticks.
    STEP_COMMANDS=40,
    STEP_VOLUME=33000,
)

# The set for filming the Cinema's film, away from the levels (`/everytunnel film set`): a parking
# corridor in perfect order, along x. FilmSetHooks knows where the figure starts: keep them together.
FILM_SET = dict(x=300, y=64, z=300, length=60, width=8, height=4, light_every=6, stall_every=3)

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
    "grating": "minecraft:smooth_stone_slab",       # the spiral stairs' treads (a slab: type is added)
    "plate": "minecraft:iron_block",                # landing floors by the spiral stairs
    "railing": "minecraft:iron_bars",
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
    "grating": "every_tunnel:metal_grating",
    "plate": "every_tunnel:checker_plate",
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
SUPPORTED = {"minecraft:sculk_vein", "minecraft:glow_lichen", "minecraft:pointed_dripstone",
             "minecraft:ladder"}
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


def place_rects(c, blocks):
    """Appends setblock/fill commands for {(x, y, z): state} (world positions): layer by layer
    from the bottom, each layer as rectangles (runs along x merged across z), within the fill
    limit. Far fewer commands than place() for a level of big plain surfaces."""
    layers = {}
    for (x, y, z), state in blocks.items():
        layers.setdefault(y, {}).setdefault(z, []).append((x, state))
    for y in sorted(layers):
        rects, open_ = [], {}           # open_: (x0, x1, state) -> [z0, z1]
        for z in sorted(layers[y]):
            runs = []
            for x, state in sorted(layers[y][z]):
                if runs and runs[-1][1] == x - 1 and runs[-1][2] == state:
                    runs[-1][1] = x
                else:
                    runs.append([x, x, state])
            current = {tuple(r) for r in runs}
            for key in list(open_):
                if key not in current or open_[key][1] != z - 1:
                    rects.append((key, *open_.pop(key)))
            for key in current:
                if key in open_:
                    open_[key][1] = z
                else:
                    open_[key] = [z, z]
        rects += [(key, z0, z1) for key, (z0, z1) in open_.items()]
        for (x0, x1, state), z0, z1 in sorted(rects, key=lambda r: (r[1], r[0][0], r[0][2])):
            rows = max(1, FILL_LIMIT // (x1 - x0 + 1))
            for za in range(z0, z1 + 1, rows):
                zb = min(z1, za + rows - 1)
                if x0 == x1 and za == zb:
                    c.append(f"setblock {x0} {y} {za} {state}")
                else:
                    c.append(f"fill {x0} {y} {za} {x1} {y} {zb} {state}")


def level3_commands(blocks, box, previous_box, extra_clear=()):
    """The second stage of the mod's build: Level 3 alone, in its own force-loaded area. Whatever
    was built in play over it goes too (`extra_clear`: Level 4's boxes)."""
    c = ["# Clear the previous Level 3 build and this one, and Level 4."]
    boxes = sorted({previous_box, box, *extra_clear} - {None})
    for b in boxes:
        c += fills(*b, "minecraft:air")
    c.append("# Blocks, layer by layer from the bottom, merged into rectangles")
    place_rects(c, {pos: st for pos, st in blocks.items() if not needs_support(st)})
    c.append("# Then the blocks that need one of those to hang on, stand on or stick to")
    place_rects(c, {pos: st for pos, st in blocks.items() if needs_support(st)})
    c = ["# Generated by tools/map/map_generator.py (Level 3: LEVEL_3 there)."] + without_drops(c, boxes)
    # Rebuilding starts the level over: its Observers go (their spawn point spawns them again).
    # This also clears one saved in a world packed into the jar without the mod's spawn record.
    for b in boxes:
        c += kill_observers(b)
    c.append("everytunnel rebuilt")
    return c


def kill_observers(box):
    x1, y1, z1, x2, y2, z2 = box
    return [f"kill @e[type=every_tunnel:observer,x={x1},y={y1},z={z1},dx={x2 - x1},dy={y2 - y1},dz={z2 - z1}]"]


def commands(level, offset, box, previous_box, summary, then=None):
    """Levels 1 and 2. In the mod's pack `then` runs last, or `/everytunnel rebuilt` if None."""
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
        c.append(then or "everytunnel rebuilt")
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


def level3_json(lv3, place):
    """Level 3: zones and spawn points from offices/level.py, moved into the world by `place`.
    Walking into the antechamber shows its title (the stairs down to it belong to no zone)."""
    p3 = lv3.p
    zones = [{"id": zid, "from": list(place.box(b)[:3]), "to": list(place.box(b)[3:]), **({"tags": tags} if tags else {}),
              **({"ambience": p3.ZONE_AMBIENCE[zid]} if zid in p3.ZONE_AMBIENCE else {})}
             for zid, b, tags in lv3.zones]
    unknown = set(p3.ZONE_AMBIENCE) - {zid for zid, _, _ in lv3.zones}
    if unknown:
        raise SystemExit(f"LEVEL_3 ZONE_AMBIENCE: no zone {', '.join(sorted(unknown))}")
    spawns = []
    for sid, pos, yaw, tags, zone in lv3.spawns:
        spawns.append({"id": sid, "pos": list(place.point(*pos)), "yaw": place.yaw(yaw), "tags": tags,
                       **({"zone": zone} if zone else {})})
    return json.dumps({
        "name": f"Level 3 - Offices {p3.LEVEL_SIGN} (generated)",
        "order": 3,
        "dimension": "minecraft:overworld",
        "zones": zones,
        "spawn_points": spawns,
        "ambience": p3.AMBIENCE,
        "entry": {"title": p3.TITLE, "subtitle": "", "sound": "every_tunnel:event.level_transition"},
        "build": BUILD_FUNCTION,
    }, indent=1) + "\n"


def attach_level_3(level, offset, lv3, door):
    """
    Puts Level 3 in the world: the wall row behind its top landing on the open door at the
    bottom of Level 2's spiral stairs (`door`: x, feet y, wall z, step, in world positions).
    Returns (placement, {world position: state}, clear box). In that wall row Level 2's
    blocks win (its door and metal wall); anywhere else Level 3 must not touch Levels 1 and 2.
    Level 3's clear box leaves the joint's wall row out (the first stage clears it), so
    clearing Level 3 never removes a block of Level 2.
    """
    p3 = lv3.p
    jx, jz = (p3.STAIRS[0] + p3.STAIRS[2]) // 2, p3.STAIRS[3]
    place = OF.Placement.at_door(door, (jx, jz), p3.STAIRS_DROP)
    ox, oy, oz = offset
    old = {(x + ox, y + oy, z + oz): st for (x, y, z), st in level.world.blocks.items()}
    wx, wy, wz, step = door
    if "door" not in old.get((wx, wy, wz), "") or "open=true" not in old[(wx, wy, wz)]:
        raise SystemExit(f"Level 3: no open door at the bottom of Level 2's stairs at {(wx, wy, wz)}")
    blocks, clashes = {}, []
    for pos, st in place.blocks(lv3.world).items():
        if pos in old:
            if pos[2] != wz:
                clashes.append(pos)
            continue
        blocks[pos] = st
    if clashes:
        raise SystemExit(f"Level 3 overlaps Levels 1 and 2 at {len(clashes)} blocks, e.g. {clashes[:3]}; move it")
    xs = [x for x, _, _ in blocks]
    ys = [y for _, y, _ in blocks]
    zs = [z for _, _, z in blocks if z != wz]
    box = [min(xs) - 1, min(ys), min(zs) - 1, max(xs) + 1, max(ys), max(zs) + 1]
    if step < 0:
        box[2] = max(box[2], wz + 1)
    else:
        box[5] = min(box[5], wz - 1)
    box = tuple(box)
    inside = [pos for pos in old if box[0] <= pos[0] <= box[3] and box[1] <= pos[1] <= box[4] and box[2] <= pos[2] <= box[5]]
    if inside:
        raise SystemExit(f"Level 3's clear box would remove {len(inside)} blocks of Levels 1 and 2, e.g. {inside[:3]}")
    return place, blocks, box


def film_set_commands(p):
    """The corridor: floor with its bays painted along both walls, plain walls with their band, every
    light on, not a stain anywhere."""
    x0, y0, z0 = p["x"], p["y"], p["z"]
    length, width, height = p["length"], p["width"], p["height"]
    blocks = {}
    for x in range(-1, length + 1):
        for z in range(-1, width + 1):
            edge = x in (-1, length) or z in (-1, width)
            blocks[(x0 + x, y0, z0 + z)] = STAND_INS["floor"]
            blocks[(x0 + x, y0 + height + 1, z0 + z)] = STAND_INS["ceiling"]
            for y in range(1, height + 1):
                if edge:
                    blocks[(x0 + x, y0 + y, z0 + z)] = STAND_INS["wall_base"] if y == 1 else STAND_INS["paint_band"] if y == 2 else STAND_INS["wall"]
    for x in range(0, length, p["stall_every"]):
        for z in (0, 1, width - 2, width - 1):
            blocks[(x0 + x, y0, z0 + z)] = STAND_INS["line_white"]
    for x in range(p["light_every"] // 2, length, p["light_every"]):
        for z in (width // 2 - 1, width // 2):
            blocks[(x0 + x, y0 + height, z0 + z)] = MOD_LIGHT % ("on", "true")
    c = fills(x0 - 1, y0 + 1, z0 - 1, x0 + length, y0 + height, z0 + width, "minecraft:air")
    place_rects(c, blocks)
    return c


def split_steps(cmds, max_commands, max_volume):
    """Commands into lists that each change at most `max_volume` blocks and hold at most
    `max_commands` commands, in order: a tick's worth of work (see LoungeHooks)."""
    steps, current, volume = [], [], 0
    for cmd in cmds:
        words = cmd.split()
        size = 1
        if words[0] == "fill":
            x1, y1, z1, x2, y2, z2 = map(int, words[1:7])
            size = (abs(x2 - x1) + 1) * (abs(y2 - y1) + 1) * (abs(z2 - z1) + 1)
        if current and (len(current) >= max_commands or volume + size > max_volume):
            steps.append(current)
            current, volume = [], 0
        current.append(cmd)
        volume += size
    return steps + [current] if current else steps


def level4_steps(lv4, box3):
    """What walking into the diploma corridor runs, one step at a time (LoungeHooks): Level 3 goes
    but for the corridor (under its floor, and west of the door to B4), and Level 4 goes up in the
    space. Reception's door stays shut: LoungeHooks opens it after the last step (a /setblock
    cannot, as it takes a door's state from its other half)."""
    p = lv4.p
    x0, y0, z0, x1, y1, z1 = box3
    door_x = lv4.world(*p["B4_DOOR"])[0]
    corridor_top = lv4.corridor_top
    c = [f"execute store result score #drops {SCORE} run gamerule doTileDrops", "gamerule doTileDrops false"]
    c += fills(x0, corridor_top + 1, z0, x1, y1, z1, "minecraft:air")
    c += fills(door_x + 1, y0, z0, x1, corridor_top, z1, "minecraft:air")
    c += kill_observers(box3)
    place_rects(c, {pos: st for pos, st in lv4.lounge_blocks.items() if not needs_support(st)})
    place_rects(c, {pos: st for pos, st in lv4.lounge_blocks.items() if needs_support(st)})
    a, b = box3, lv4.box
    c += [f"kill @e[type=minecraft:item,x={k[0]},y={k[1]},z={k[2]},dx={k[3] - k[0]},dy={k[4] - k[1]},dz={k[5] - k[2]}]" for k in (a, b)]
    c.append(f"execute if score #drops {SCORE} matches 1 run gamerule doTileDrops true")
    c.append(f"forceload remove {lv4.area(box3)[0]} {lv4.area(box3)[2]} {lv4.area(box3)[3]} {lv4.area(box3)[5]}")
    return split_steps(c, p["STEP_COMMANDS"], p["STEP_VOLUME"])


def level3_extras(json_text, lv4, door):
    """Level 3's file with what joins it to Level 4: the door to B4 (its own zone, so that going
    through never leaves the level) and the place in the corridor behind it."""
    data = json.loads(json_text)
    x, y, z = door
    zone = lv4.build_zone()
    data["zones"].append({"id": "b4_door", "from": [x, y, z], "to": [x, y + 1, z], "ambience": {}})
    data["spawn_points"] += [
        {"id": "b4_door", "pos": [x + 0.5, y, z + 0.5], "yaw": 90, "tags": ["b4_door"]},
        {"id": "b4_corridor", "pos": [zone[3] + 0.5, y, z + 0.5], "yaw": 90, "tags": ["b4_corridor"]},
    ]
    return json.dumps(data, indent=1) + "\n"


def level4_json(lv4, extra_zones=()):
    """Level 4: from the corridor behind the door to B4, so Level 3 is left behind for good and
    its title shows as that door shuts. It has no `build`: LoungeHooks builds it in play.
    `extra_zones`: (id, box, tags) of Level 5's entry corridor that belong to Level 4."""
    p = lv4.p
    sx, sz = p["START"]
    wx, wz = lv4.world(sx, sz)
    zones = [{"id": zid, "from": list(b[:3]), "to": list(b[3:]), **({"tags": tags} if tags else {})}
             for zid, b, tags in list(lv4.zones()) + list(extra_zones)]
    return json.dumps({
        "name": "Level 4 - VIP lounge B4 (generated)",
        "order": 4,
        "dimension": "minecraft:overworld",
        "zones": zones,
        "spawn_points": [{"id": "player_start", "pos": [wx + 0.5, p["FLOOR_Y"] + 1, wz + 0.5], "yaw": 90, "tags": ["player"]}]
        + [{"id": sid, "pos": list(pos), "yaw": 0, "tags": [tag]} for sid, pos, tag in lv4.furnish.spawn_points()],
        "entry": {"title": p["TITLE"], "subtitle": "", "sound": "every_tunnel:event.level_transition"},
        "safe": True,
    }, indent=1) + "\n"


def level5_steps(lv5):
    """What walking into Level 4's dark stretch runs, one step at a time (LabHooks): Level 5 goes up
    beyond it, its air included, so that a build over an older one leaves nothing of it. The Lobby's
    wall at the end of the entry corridor stays whole: LabHooks opens it after the last step."""
    p = lv5.p
    box = lv5.lab_box
    c = [f"execute store result score #drops {SCORE} run gamerule doTileDrops", "gamerule doTileDrops false"]
    c.append(f"kill @e[type=minecraft:item,x={box[0]},y={box[1]},z={box[2]},dx={box[3] - box[0]},dy={box[4] - box[1]},dz={box[5] - box[2]}]")
    place_rects(c, {pos: st for pos, st in lv5.lab_blocks.items() if not needs_support(st)})
    place_rects(c, {pos: st for pos, st in lv5.lab_blocks.items() if needs_support(st)})
    c.append(f"execute if score #drops {SCORE} matches 1 run gamerule doTileDrops true")
    c.append(f"forceload remove {box[0]} {box[2]} {box[3]} {box[5]}")
    return split_steps(c, p["STEP_COMMANDS"], p["STEP_VOLUME"])


def level5_json(lv5):
    """Level 5: from the steel frame in Level 4's dark stretch on, so that the checkpoint and its rules
    start there; its title waits for the airlock to open. It has no `build`: LabHooks builds it in play."""
    p = lv5.p
    zones = [{"id": zid, "from": list(b[:3]), "to": list(b[3:]), **({"tags": tags} if tags else {})} for zid, b, tags in lv5.zones()]
    return json.dumps({
        "name": "Level 5 - Laboratory B5 (generated)",
        "order": 5,
        "dimension": "minecraft:overworld",
        "zones": zones,
        "spawn_points": lv5.spawn_points(),
        # The title waits for the airlock's door ahead to open (LabHooks).
        "entry": {"title": p["TITLE"], "subtitle": "", "sound": "every_tunnel:event.level_transition", "manual": True},
    }, indent=1) + "\n"


def chunk_count(box):
    return (box[3] // 16 - box[0] // 16 + 1) * (box[5] // 16 - box[2] // 16 + 1)


def wait_and_go(functions, name, area, y, work, release=True):
    """`<name>_wait`, run by a schedule once `area` (x0, _, z0, x1, _, z1) is being force-loaded:
    it asks for another look in 5 ticks first, then, if every corner is loaded (at height y),
    runs `<name>_go`, which calls that look off, runs the `work` commands and releases the
    area. (Waiting again only while one given corner is unloaded would stall for good when
    that corner loads before another.)"""
    corners = [(area[0], y, area[2]), (area[3], y, area[5]), (area[0], y, area[5]), (area[3], y, area[2])]
    loaded = " ".join(f"if loaded {x} {cy} {z}" for x, cy, z in corners)
    (functions / f"{name}_wait.mcfunction").write_text("\n".join([
        f"schedule function garage:{name}_wait 5t replace",
        f"execute {loaded} run function garage:{name}_go",
    ]) + "\n")
    (functions / f"{name}_go.mcfunction").write_text("\n".join(
        [f"schedule clear garage:{name}_wait"] + work + ([f"forceload remove {area[0]} {area[2]} {area[3]} {area[5]}"] if release else [])) + "\n")


def write_pack(name, cmds, box, previous, level=None, offset=None, level2=None, joint=None, stage3=None):
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
    loading = '{"text":"Loading the level area...","color":"gray"}'
    mod = level is not None
    (functions / "build.mcfunction").write_text("\n".join(
        [forceload] + ([] if mod else [f"tellraw @a {loading}"]) + ["schedule function garage:build_wait 5t replace"]) + "\n")
    wait_and_go(functions, "build", area, box[1],
                ["function garage:build_now" if mod else "execute as @a[limit=1] run function garage:build_now"])
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
    wait_and_go(functions, "clear", area, box[1], ["function garage:clear_now"])
    cleared = 'tellraw @a {"text":"Level cleared.","color":"gray"}'
    (functions / "clear_now.mcfunction").write_text("\n".join(
        without_drops(fills(*box, "minecraft:air"), [box]) + kill_pale_men
        + ["schedule function garage:level3_clear 1t replace" if stage3 else cleared]) + "\n")
    if stage3:
        # The second stage, Level 3, the same way in its own area: build_now schedules
        # level3_build, which ends with `/everytunnel rebuilt`; clear_now schedules level3_clear.
        b3 = stage3["box"]
        joined = [b for b in (b3, stage3["previous"], *stage3["extra_clear"]) if b is not None]
        area3 = (min(b[0] for b in joined), 0, min(b[2] for b in joined), max(b[3] for b in joined), 0, max(b[5] for b in joined))
        if chunk_count(area3) > 256:
            raise SystemExit(f"Level 3 spans {chunk_count(area3)} chunks; /forceload takes at most 256: make it smaller")
        forceload3 = f"forceload add {area3[0]} {area3[2]} {area3[3]} {area3[5]}"
        for stage, now in (("build", "now"), ("clear", "clear_now")):
            (functions / f"level3_{stage}.mcfunction").write_text("\n".join(
                [forceload3, f"schedule function garage:level3_{stage}_wait 5t replace"]) + "\n")
            wait_and_go(functions, f"level3_{stage}", area3, b3[1], [f"function garage:level3_{now}"])
        (functions / "level3_now.mcfunction").write_text("\n".join(stage3["cmds"]) + "\n")
        (functions / "level3_clear_now.mcfunction").write_text("\n".join(
            without_drops([f for b in (b3, *stage3["extra_clear"]) for f in fills(*b, "minecraft:air")], [b3, *stage3["extra_clear"]])
            + kill_observers(b3) + [cleared]) + "\n")
        stage3["chunks"] = chunk_count(area3)
        # Level 4, built in play: LoungeHooks runs level4_begin, the wait says when the chunks are
        # there (`everytunnel loungeready`), and then it runs the steps in turn.
        area4 = stage3["area4"]
        (functions / "level4_begin.mcfunction").write_text("\n".join(
            [f"forceload add {area4[0]} {area4[2]} {area4[3]} {area4[5]}", "schedule function garage:level4_wait 5t replace"]) + "\n")
        wait_and_go(functions, "level4", area4, b3[1], ["everytunnel loungeready"], release=False)
        # The film's set, only built when asked for (`/everytunnel film set`).
        fs = FILM_SET
        area = (fs["x"] - 1, 0, fs["z"] - 1, fs["x"] + fs["length"], 0, fs["z"] + fs["width"])
        (functions / "film_set.mcfunction").write_text("\n".join(
            [f"forceload add {area[0]} {area[2]} {area[3]} {area[5]}", "schedule function garage:film_set_wait 5t replace"]) + "\n")
        wait_and_go(functions, "film_set", area, fs["y"], ["function garage:film_set_now", "everytunnel filmready"], release=False)
        (functions / "film_set_now.mcfunction").write_text("\n".join(film_set_commands(fs)) + "\n")
        for k, step in enumerate(stage3["steps4"]):
            (functions / f"level4_step_{k}.mcfunction").write_text("\n".join(step) + "\n")
        # Level 5, built in play the same way: LabHooks runs level5_begin, `everytunnel labready` says
        # the chunks are there, then the steps run in turn.
        area5 = stage3["area5"]
        (functions / "level5_begin.mcfunction").write_text("\n".join(
            [f"forceload add {area5[0]} {area5[2]} {area5[3]} {area5[5]}", "schedule function garage:level5_wait 5t replace"]) + "\n")
        wait_and_go(functions, "level5", area5, area5[1], ["everytunnel labready"], release=False)
        for k, step in enumerate(stage3["steps5"]):
            (functions / f"level5_step_{k}.mcfunction").write_text("\n".join(step) + "\n")
    tags = pack / "data" / "minecraft" / "tags" / "functions"
    tags.mkdir(parents=True)
    for tag in ("load",) if mod else ("load", "tick"):
        (tags / f"{tag}.json").write_text(json.dumps({"values": [f"garage:{tag}"]}, indent=2) + "\n")
    if level:
        levels = pack / "data" / "every_tunnel" / "levels"
        levels.mkdir(parents=True)
        (levels / "level_1.json").write_text(level_json(level, offset))
        (levels / "level_2.json").write_text(next_level_json(level, level2, offset, joint))
        if stage3:
            (levels / "level_3.json").write_text(stage3["json"])
            (levels / "level_4.json").write_text(stage3["json4"])
            (levels / "level_5.json").write_text(stage3["json5"])
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
    last = json.loads(state_file.read_text()) if state_file.exists() else {}
    previous = tuple(last["box"]) if "box" in last else None
    previous3 = tuple(last["box3"]) if "box3" in last else None
    previous4 = tuple(last["box4"]) if "box4" in last else None

    p2 = SimpleNamespace(**dict(vars(p), **LEVEL_2))
    levels, levels2, joints = {}, {}, {}
    for v in ("vanilla", "mod"):
        # The vanilla pack has no Level 3: there the door at the bottom of the stairs stays shut.
        pv = p2 if v == "mod" else SimpleNamespace(**dict(vars(p2), EXIT_DOOR_OPEN=False))
        levels[v] = Level(p, v).compose()
        levels2[v] = Level(pv, v, glyphs=levels[v].glyphs).compose()
        joints[v] = attach(levels[v], levels2[v])
    gv, gm = levels["vanilla"].glyphs, levels["mod"].glyphs

    # Level 3, mod only. Its sign is registered in the vanilla registry too, as the two
    # builds share one resource pack (built from the vanilla one).
    p3 = SimpleNamespace(**dict(vars(p), **LEVEL_3))
    lv3 = OF.Offices(p3, gm).compose()
    gv.wall_state(p3.NEXT_LEVEL_SIGN, "north")
    used = {s.split("[")[0] for lv in levels.values() for s in lv.world.blocks.values()}
    used |= {s.split("[")[0] for d in (STAND_INS, BLOCKS, MOD_BLOCKS) for s in d.values()}
    shared = sorted({s.split("[")[0] for s in p3.STAND_INS.values()} & used)
    if shared:
        raise SystemExit(f"LEVEL_3 STAND_INS: Levels 1 and 2 use {', '.join(shared)}; pick blocks they never use")
    report3 = OC.walk(lv3) + OC.enclosed(lv3)

    # Level 4, mod only: its plan and blocks; its stand-ins must be its own as well.
    lv4 = Lounge(LEVEL_4).compose()
    used |= {s.split("[")[0] for s in p3.STAND_INS.values()} | {s.split("[")[0] for s in lv3.world.blocks.values()}
    shared4 = sorted({s.split("[")[0] for s in LEVEL_4["STAND_INS"].values()} & used)
    if shared4:
        raise SystemExit(f"LEVEL_4 STAND_INS: other levels use {', '.join(shared4)}; pick blocks they never use")
    report4 = LC.walled(lv4) + LC.steps(lv4) + LC.walk(lv4) + LC.zoned(lv4)

    # Level 5, mod only, beyond Level 4's dark stretch. Its entry corridor goes up with Level 4 (the
    # player walks it while the rest is built in play): it opens the stretch's far end, and no wall of
    # its own goes up across the stretch. Its stand-ins must be its own as well.
    lv5 = Lab(LEVEL_5).compose(stretch_air(lv4, LEVEL_5))
    used |= {s.split("[")[0] for s in LEVEL_4["STAND_INS"].values()} | {s.split("[")[0] for s in lv4.lounge_blocks.values()}
    shared5 = sorted({s.split("[")[0] for s in LEVEL_5["STAND_INS"].values()} & used)
    if shared5:
        raise SystemExit(f"LEVEL_5 STAND_INS: other levels use {', '.join(shared5)}; pick blocks they never use")
    report5 = BC.walk(lv5) + BC.walled(lv5) + BC.stacked(lv5) + BC.joins_level_4(lv5, lv4) + BC.zoned(lv5)
    clashes5 = [pos for pos in lv5.lab_blocks if pos in lv4.lounge_blocks]
    if clashes5:
        raise SystemExit(f"Level 5 overlaps Level 4 at {len(clashes5)} blocks, e.g. {clashes5[:3]}")
    for pos in lv5.entry_air:
        lv4.lounge_blocks.pop(pos, None)
    lv4.lounge_blocks.update(lv5.entry_blocks)
    lv4.box = Lounge.bounds(lv4.lounge_blocks)
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
    # Level 3 joins the open door at the bottom of Level 2's stairs.
    dx, dy, dz, step = levels2["mod"].next_door
    jx, jy, jz = joints["mod"]
    door = (dx + jx + offset[0], dy + jy + offset[1], dz + jz + offset[2], step)
    place3, blocks3, box3 = attach_level_3(levels["mod"], offset, lv3, door)
    outside = [pos for pos in blocks3 if pos[2] == door[2] and not (box[0] <= pos[0] <= box[3] and box[1] <= pos[1] <= box[4])]
    if outside:
        raise SystemExit(f"Level 3: blocks in the joint's wall row outside the first stage's clear box, e.g. {outside[:3]}")
    # The diploma corridor stands with Level 3, behind the door to B4. In that door's wall Level 3 wins.
    door4 = place3.cell(*lv3.l4_door)
    if door4[0] != lv4.world(*LEVEL_4["B4_DOOR"])[0] or door4[2] != lv4.world(*LEVEL_4["B4_DOOR"])[1] or door4[1] != LEVEL_4["FLOOR_Y"] + 1:
        raise SystemExit(f"Level 4: Level 3's door to B4 is at {door4}; LEVEL_4's B4_DOOR, PLACE_X, PLACE_Z and FLOOR_Y say otherwise")
    clashes4 = [pos for pos in lv4.corridor_blocks if pos in blocks3 and pos[0] != door4[0]]
    if clashes4:
        raise SystemExit(f"Level 4's corridor overlaps Level 3 at {len(clashes4)} blocks, e.g. {clashes4[:3]}")
    if lv4.box[4] >= box[1]:
        raise SystemExit(f"Level 4 reaches y {lv4.box[4]}, into the first stage's clear box from y {box[1]}")
    for pos, st in lv4.corridor_blocks.items():
        blocks3.setdefault(pos, st)
    clear4 = sorted({lv4.box, previous4} - {None})
    cmds3 = level3_commands(blocks3, box3, previous3, clear4)
    steps4 = level4_steps(lv4, box3)
    area4 = lv4.area(box3)
    if chunk_count(area4) > 256:
        raise SystemExit(f"Level 4's build spans {chunk_count(area4)} chunks; /forceload takes at most 256")
    clashes5 = [pos for pos in lv5.lab_blocks if pos in blocks3]
    if clashes5:
        raise SystemExit(f"Level 5 overlaps Level 3 at {len(clashes5)} blocks, e.g. {clashes5[:3]}")
    steps5 = level5_steps(lv5)
    area5 = lv5.lab_box
    if chunk_count(area5) > 256:
        raise SystemExit(f"Level 5's build spans {chunk_count(area5)} chunks; /forceload takes at most 256")
    stage3 = dict(cmds=cmds3, box=box3, previous=previous3, json=level3_extras(level3_json(lv3, place3), lv4, door4),
                  extra_clear=clear4, area4=area4, steps4=steps4,
                  json4=level4_json(lv4),
                  area5=area5, steps5=steps5, json5=level5_json(lv5))

    counts = {}
    for variant, level in levels.items():
        mod = variant == "mod"
        cmds = commands(level, offset, box, previous, summary, "schedule function garage:level3_build 1t replace" if mod else None)
        write_pack(PACKS[variant], cmds, box, previous, level if mod else None, offset, levels2[variant] if mod else None,
                   joints[variant], stage3 if mod else None)
        counts[variant] = len([c for c in cmds if not c.startswith("#")])
    count = counts["vanilla"]
    area = box if previous is None else (min(box[0], previous[0]), 0, min(box[2], previous[2]),
                                         max(box[3], previous[3]), 0, max(box[5], previous[5]))
    count3 = len([c for c in cmds3 if not c.startswith("#")])
    for n, what in ((counts["mod"], "build_now"), (count3, "level3_now")):
        if n > 65536:
            raise SystemExit(f"{what} has {n} commands; a function runs at most 65536")

    resource_zip, textures = T.build_pack(p, v.glyphs)
    resource_zip, textures3 = OT.add_to_pack(resource_zip, p3)
    resource_zip, textures4 = LT.add_to_pack(resource_zip, LEVEL_4, TEXTURE_SEED, PBR["RELIEF"])
    resource_zip, textures5 = BT.add_to_pack(resource_zip, LEVEL_5, TEXTURE_SEED, PBR["RELIEF"])
    (OUT / "level_resources.zip").write_bytes(resource_zip)
    size3 = (box3[3] - box3[0] + 1, box3[5] - box3[2] + 1)
    stats3 = [
        f"{p3.LEVEL_SIGN}: {len(lv3.rooms)} rooms, the library, {len(lv3.zones)} zones, {len(lv3.spawns)} spawn points, "
        f"{len(lv3.lights)} lights; {len(blocks3)} blocks; world box {box3} ({size3[0]} x {size3[1]})",
        f"Stage 1 (Levels 1 and 2): {chunk_count(area)} chunks, build_now {counts['mod']} commands. "
        f"Stage 2 (Level 3): {stage3['chunks']} chunks, level3_now {count3} commands. (Limits: 256 chunks, 65536 commands)",
        f"{p3.LEVEL_SIGN} joins {p2.LEVEL_SIGN}'s door at {door[:3]}; plan (x, y, z) -> world "
        f"({place3.ax} {'+' if place3.step > 0 else '-'} x, {place3.cy} + y, {place3.bz} {'+' if place3.step > 0 else '-'} z)",
    ] + report3[:1]
    OV.write(OUT / "level3_preview.png", lv3, stats3)
    OV.write(OUT / "level3_furniture.png", lv3, stats3[:1], s=9)
    V.write_texture_sheet(OUT / "level3_texture_sheet.png", textures3)
    stats4 = report4 + [
        f"B4: {len(lv4.lounge_blocks)} blocks, corridor {len(lv4.corridor_blocks)}, {len(lv4.lamps)} lamps; world box {lv4.box}; "
        f"{len(steps4)} steps over {chunk_count(area4)} chunks",
    ]
    LV.write(OUT / "level4_preview.png", lv4, stats4 + [
        "Green: open arch. Orange: door. Violet: big door. Red: door to B4. Blue dot: checkpoint. Black: furniture. "
        "White boxes: gaps for puzzles. Dots: white notes, yellow batteries, cyan water, red food."])
    V.write_texture_sheet(OUT / "level4_texture_sheet.png", textures4)
    stats5 = report5 + [
        f"B5 (layers 1 and 2): {len(lv5.lab_blocks)} blocks, entry corridor {len(lv5.entry_blocks)}, {len(lv5.lights)} lights; "
        f"world box {lv5.box}; {len(steps5)} steps over {chunk_count(area5)} chunks",
        "Green: open doorway. Red: locked door. Grey: the steel frame from Level 4's dark stretch. Violet: the airlock's sliding doors. Orange: the grate.",
        "Blue dot: the start, through the frame from Level 4's dark stretch (top left). White ring: the Lab hall checkpoint. Faint: the other layer.",
    ]
    BV.write(OUT / "level5_preview.png", lv5, lv4, stats5)
    V.write_texture_sheet(OUT / "level5_texture_sheet.png", textures5)
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
    state_file.write_text(json.dumps({"box": box, "box3": box3, "box4": lv4.box}) + "\n")

    stats.append(MS.write(p, REPO / "src" / "main" / "resources" / "assets" / "every_tunnel"))

    for line in stats + stats3 + report3[1:] + stats4 + stats5[:-2]:
        print(line)
    if min(r["line_of_sight"] for r in reports) < 30:
        print("WARNING: a hall has a clear line of sight under 30 blocks")
    install(resource_zip)


if __name__ == "__main__":
    main()
