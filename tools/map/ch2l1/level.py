"""
Chapter 2, Level 1: the factory. What is where (the spaces, the doors, the glass, the lights, the named places and the
patrol paths) and how it becomes blocks. Drawn by the author on paper (relative sizes); the measures here are that
drawing in blocks, with the 43 x 3 of the authorized corridor as the scale (about 16 px of the drawing a block), grown
after a first walk through the level (see SPACES).

Plan coordinates, as in the other levels: x east, z south (the entrance is at the north), y up from the floor of the air
(the floor's blocks are at y = -1). A space is a set of cells, each with the air above it up to its height; every cell next
to the air that is not air is the shell: a floor under it, a ceiling over it, a wall beside it with a darker first course.
Two spaces are told apart by a wall of one block. A space may lie lower (its y0: the basement, 8 blocks under the factory),
and a flight of stairs is a space whose cells each have a floor of their own (`floors`), one lower at each row. The look is the prologue's laboratory, all clean and modern: white
concrete, a light grey floor, a dark grey dado, glass and the ceiling lamps of the lab.

This file writes nothing: `generate.py` turns it into the plan the mod builds (`map.json`), the named places
(`anchors.json`) and a top-down picture. The mod puts the plan in the world at ORIGIN when Chapter 2 starts.
"""

import json
import math
import sys
from collections import deque
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "prologue"))
import room as PROLOGUE  # noqa: E402  (the prologue's room: its bench and what stands on it, for the film set)

# The world position of the plan's cell (0, 0, 0): a free corner of the map world, far from Chapter 1's levels and from
# the prologue's room. The floor's blocks are one under (y = 63).
ORIGIN = (1024, 64, 1024)
DIMENSION = "minecraft:overworld"

MOD = "every_tunnel:"
BLOCKS = {
    "floor": "minecraft:light_gray_concrete",
    "ceiling": "minecraft:white_concrete",
    "wall": "minecraft:white_concrete",
    "dado": "minecraft:gray_concrete",
    "glass": MOD + "factory_glass",
    "light": MOD + "lab_ceiling_light",
}
# Level 5's doors: the lab's white one with a tall window (a pair of leaves opens at once), and the grey emergency one that
# hands do not open.
DOOR_BLOCKS = {"normal": MOD + "factory_door", "locked": MOD + "emergency_door"}
DOOR_HEIGHT = 2
LIGHT_SPACING = 5

AROUND = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]


class Space:
    """A space: its cells (x, z), its height in blocks of air over its floor (y0), and the boxes the lights are laid out over, a lamp
    every `spacing` blocks. A cell of `floors` and `tops` has its air from that floor up to under that top instead (a flight of stairs,
    whose ceiling steps down with it)."""

    def __init__(self, sid, name, cells, height, boxes, restricted=False, y0=0, floors=None, tops=None, spacing=None):
        self.id, self.name, self.cells, self.height, self.boxes, self.restricted = sid, name, set(cells), height, boxes, restricted
        self.y0, self.floors, self.tops = y0, floors or {}, tops or {}
        self.spacing = spacing or LIGHT_SPACING

    @property
    def top(self):
        """The first y over the air: where the ceiling and its lamps are."""
        return self.y0 + self.height

    def floor(self, x, z):
        return self.floors.get((x, z), self.y0)

    def top_at(self, x, z):
        return self.tops.get((x, z), self.top)

    @property
    def bounds(self):
        xs = [x for x, _ in self.cells]
        zs = [z for _, z in self.cells]
        return min(xs), min(zs), max(xs) + 1, max(zs) + 1


def rect(sid, name, x0, z0, x1, z1, height, restricted=False, y0=0, spacing=None):
    """A rectangle of cells, x0 .. x1 - 1 and z0 .. z1 - 1."""
    return Space(sid, name, [(x, z) for x in range(x0, x1) for z in range(z0, z1)], height, [(x0, z0, x1, z1)], restricted, y0,
                 spacing=spacing)


def stairs(sid, name, x0, x1, z0, steps, top, headroom, restricted=False):
    """A flight of stairs going down southwards: x0 .. x1 - 1 wide, one row of cells a step from z0 on, row k's floor at -(k + 1)
    (its stair block), its ceiling `headroom` over it (never over `top`), a lamp every other row. The last row's floor is the space it
    leads down to (y0 = -steps)."""
    cells = [(x, z0 + k) for x in range(x0, x1) for k in range(steps)]
    floors = {(x, z0 + k): -(k + 1) for x in range(x0, x1) for k in range(steps)}
    tops = {c: min(top, y + headroom) for c, y in floors.items()}
    return Space(sid, name, cells, top + steps, [(x0, z0, x1, z0 + steps)], restricted, -steps, floors, tops, spacing=2)


def ring(sid, name, boxes, height):
    cells = [(x, z) for x0, z0, x1, z1 in boxes for x in range(x0, x1) for z in range(z0, z1)]
    return Space(sid, name, cells, height, boxes)


def arch(sid, name, x0, x1, z0, z_base, height):
    """The entrance: half an ellipse over x0 .. x1 - 1, its flat side (the doors) at z_base, rising north to z0."""
    centre, a, b = (x0 + x1) / 2, (x1 - x0) / 2, z_base - z0 + 0.5
    cells = [(x, z) for x in range(x0, x1) for z in range(z0, z_base)
             if ((x + 0.5 - centre) / a) ** 2 + ((z_base - (z + 0.5)) / b) ** 2 <= 1]
    return Space(sid, name, cells, height, [(x0, z0, x1, z_base)])


# The spaces. Heights: 4 for corridors and small rooms, 5 for the main corridor and the auxiliary lab, 6 for the LAB and the
# dining room, 9 under the entrance's arch. After the first walk through it the LAB, the corridors and the auxiliary lab
# grew: the LAB 30 x 36 (was 24 x 29), the main corridor 11 wide (was 9), the ring and the authorized corridor 4 (was 3)
# and the auxiliary lab 14 x 24 (was 6 x 20); the lockers 14 x 18 (was 8 x 11). The ring's west stretch (x 16 .. 19) lies along the auxiliary lab's glass.
# Phase 6 made the auxiliary lab wider to the west: a room of its own behind a partition (aux_chamber, 9 x 24), reached by an opening at
# the partition's north end, holds sealed chamber 02, out of every line of sight from the ring through the glass (check_chamber_hidden).
BASEMENT_DEPTH = 8
SPACES = [
    arch("entrance", "ENTRANCE", 20, 62, 1, 10, 9),
    rect("main_corridor", "MAIN CORRIDOR", 20, 11, 31, 47, 5),
    rect("lab", "LAB", 32, 11, 62, 47, 6),
    rect("wc_west", "WC", 14, 11, 19, 16, 4),
    rect("lockers", "LOCKERS", 5, 17, 19, 35, 4),
    ring("ring", "RING", [(16, 48, 62, 52), (16, 69, 62, 73), (16, 52, 20, 69), (58, 52, 62, 69)], 4),
    rect("dining", "DINING ROOM", 21, 53, 57, 68, 6),
    rect("aux_lab", "AUXILIARY LAB", 1, 48, 15, 72, 5, restricted=True),
    rect("aux_chamber", "CHAMBER 02", -9, 48, 0, 72, 5, restricted=True),
    rect("authorized", "AUTHORIZED CORRIDOR", 15, 74, 61, 78, 4, restricted=True),
    rect("security", "SECURITY", 15, 79, 28, 85, 4, restricted=True),
    rect("office", "OFFICE", 29, 79, 41, 86, 4, restricted=True),
    rect("wc_east", "WC", 42, 79, 48, 82, 4, restricted=True),
    rect("sealed_zone", "SEALED ZONE", 63, 25, 74, 31, 4, restricted=True),
    # Phase 9: behind the sealed door, a landing with two ways on. East, the way to the factory's other part, closed for works (a short
    # stub behind bars); south, the stairs down to the basement, where everything is stored, 8 blocks under the factory.
    rect("construction", "UNDER CONSTRUCTION", 75, 26, 80, 32, 3, restricted=True),
    stairs("stairs", "STAIRS", 70, 73, 31, BASEMENT_DEPTH, 4, 6, restricted=True),
    rect("basement", "BASEMENT", 63, 39, 89, 63, 6, restricted=True, y0=-BASEMENT_DEPTH, spacing=4),
]

SPACE_HEIGHTS = {s.id: s.height for s in SPACES}
SPACE_TOPS = {s.id: s.top for s in SPACES}

# Spaces joined with no door: the landing and the stairs, the stairs and the basement (their air simply meets).
LINKS = [("sealed_zone", "stairs"), ("stairs", "basement")]

# The doors: (id, the wall's axis, the wall's line, first cell along it, width, kind). Axis "z": the wall runs along x at
# z = line, and the passage goes north to south; axis "x": the wall runs along z at x = line. Kinds: "normal" (a door of
# white steel with a window, opened by hand), "restricted" (the same, but the way into a forbidden zone: the drawing's red circle),
# "locked" (a grey emergency door that hands do not open: a later phase opens it, with a badge, a code or Rowan's card) and
# "decor" (the same grey door, opening onto nothing), and "opening": no door at all, a passage two blocks high through a partition.
DOORS = [
    ("entrance_main", "z", 10, 24, 2, "normal"),
    ("entrance_lab_1", "z", 10, 34, 2, "normal"),
    ("entrance_lab_2", "z", 10, 47, 2, "normal"),
    ("main_lab_1", "x", 31, 16, 2, "normal"),
    ("main_lab_2", "x", 31, 42, 2, "normal"),
    ("wc_west", "x", 19, 13, 1, "normal"),
    ("lockers_1", "x", 19, 19, 2, "normal"),
    ("lockers_2", "x", 19, 31, 2, "normal"),
    ("main_ring", "z", 47, 21, 2, "normal"),
    ("lab_ring", "z", 47, 42, 2, "normal"),
    ("dining_n1", "z", 52, 29, 2, "normal"),
    ("dining_n2", "z", 52, 46, 2, "normal"),
    ("dining_s1", "z", 68, 25, 2, "normal"),
    ("dining_s2", "z", 68, 44, 2, "normal"),
    ("aux_lab", "x", 15, 49, 2, "locked"),
    ("aux_chamber", "x", 0, 48, 3, "opening"),
    ("ring_authorized", "z", 73, 34, 2, "restricted"),
    ("security", "z", 78, 21, 1, "normal"),
    ("office", "z", 78, 31, 2, "locked"),
    ("wc_east", "z", 78, 44, 1, "normal"),
    ("sealed", "x", 62, 26, 4, "locked"),
    ("construction", "x", 74, 27, 3, "opening"),
    ("restricted_east", "x", 61, 75, 1, "decor"),
]

# Walls of glass: (the wall's x, first z, last z, the top y): the auxiliary lab's, towards the ring's west stretch.
GLASS_WALLS = [(15, 52, 68, 3)]

# Solid columns inside a space: (x0, z0, x1, z1), the far corner excluded. The lockers' central one, 2 x 2.
PILLARS = [(11, 25, 13, 27)]

# What lies behind a locked door: the auxiliary lab (the coworker's badge), the office (Rowan's code) and the sealed zone.
LOCKED_SPACES = {"aux_lab", "aux_chamber", "office", "sealed_zone", "construction", "stairs", "basement"}

CLOCK = {"at": (29, 1, 9), "facing": "north"}      # on the entrance's south wall, between its first two doors
# Furniture and props, by cell: task 1's bench along the LAB's west wall with its three tubes and the terminal, task 2's analyzer on a
# bench against the LAB's south wall, task 3's broom against the east wall of the ring's west stretch and the dirt on its floor, and
# the ceiling speakers of the four zones where Rowan's announcements are heard (each in the last layer of air).
SPEAKERS = {"speaker_entrance": (29, 8, 6), "speaker_lab": (40, 5, 24), "speaker_main": (25, 4, 29), "speaker_west": (18, 3, 60)}
TUBES = [(32, 1, 22), (32, 1, 23), (32, 1, 24)]
TERMINAL = (32, 1, 26)
ANALYZER = (51, 1, 46)
BROOM = (19, 0, 54)
DIRT_SPOTS = [(18, 55), (16, 57), (19, 58), (17, 60), (18, 62), (16, 63), (19, 65), (17, 66), (18, 68), (16, 54)]
FACINGS = ["north", "east", "south", "west"]

# The factory's life (phase 4). The LAB's work stations: benches with a monitor, where people stand and type (two rows against the north
# wall, one against the south); the lockers along three walls of the lockers' room (the Level 3 lockers: a stack of two blocks facing into the
# room) with the predecessor's among them; and the decorative security cameras, one hanging from the ceiling in every space but the lockers
# (cell, the way it rests). The people's places and routes are NPC_SPOTS and NPC_ROUTES; what they do is npcs.json.
STATION_BENCHES = [(x, 13, "south") for x in (37, 38, 39, 52, 53, 54)] + [(x, 46, "north") for x in (38, 39, 40)]
STATION_MONITORS = [(37, 13, "south"), (39, 13, "south"), (52, 13, "south"), (54, 13, "south"), (38, 46, "north"), (40, 46, "north")]
PREDECESSOR_LOCKER = (5, 0, 30)
CAMERAS = {
    "entrance": [(24, 5, "east")],
    "main_corridor": [(30, 13, "south"), (20, 44, "north")],
    "lab": [(61, 12, "west")],
    "ring": [(24, 48, "east"), (53, 51, "west"), (24, 72, "east"), (53, 69, "west"), (19, 60, "north"), (58, 60, "south")],
    "dining": [(21, 53, "east")],
    "aux_lab": [(1, 48, "south")],
    "aux_chamber": [(-1, 49, "west")],
    "authorized": [(22, 74, "east"), (53, 77, "west")],
    "security": [(27, 79, "south")],
    "office": [(29, 79, "south")],
    "sealed_zone": [(73, 25, "west")],
    "basement": [(88, 39, "west")],
}

# People's places: name -> (x, z, the way they face, degrees), for typing at a station or standing, and their routes: name -> (points, closed).
NPC_SPOTS = {
    "npc_lab_01": (37.5, 14.5, 180), "npc_lab_02": (39.5, 14.5, 180), "npc_lab_03": (52.5, 14.5, 180), "npc_lab_04": (54.5, 14.5, 180),
    "npc_lab_05": (38.5, 45.5, 0), "npc_lab_06": (40.5, 45.5, 0),
    "npc_lab_07": (36.5, 17.5, 180), "npc_lab_08": (35.5, 36.5, 90), "npc_lab_09": (57.5, 18.5, 0), "npc_lab_10": (47.5, 27.5, 270),
    "npc_main_01": (27.5, 14.5, 0), "npc_main_02": (23.5, 44.5, 180), "npc_main_03": (29.5, 30.5, 90),
    "npc_dining_01": (30.5, 56.5, 270), "npc_dining_02": (38.5, 60.5, 0), "npc_dining_03": (24.5, 58.5, 0),
}
NPC_ROUTES = {
    "route_lab_a": ([(36.5, 17.5), (36.5, 30.5), (44.5, 30.5), (44.5, 17.5)], True),
    "route_lab_b": ([(35.5, 36.5), (58.5, 36.5)], False),
    "route_lab_c": ([(57.5, 18.5), (57.5, 40.5), (48.5, 40.5)], False),
    "route_main_a": ([(27.5, 14.5), (27.5, 44.5)], False),
    "route_main_b": ([(23.5, 44.5), (23.5, 15.5)], False),
    "route_dining_a": ([(30.5, 56.5), (46.5, 56.5), (46.5, 64.5), (30.5, 64.5)], True),
    "route_dining_b": ([(24.5, 58.5), (24.5, 64.5)], False),
    # Act 2: the coworker, from where he waits by the broom to the lockers (through the ring's door, up the main corridor and in by
    # the lockers' second door) and to his place in the meeting; and the way everyone leaves the lockers after it.
    "coworker_route": ([(16.5, 50.5), (22.0, 50.5), (22.0, 45.0), (22.0, 32.0), (16.5, 32.0), (9.0, 31.0), (6.6, 30.5)], False),
    "meeting_leave": ([(12.5, 31.8), (17.5, 32.0), (21.5, 32.0), (25.5, 32.0), (25.5, 14.5)], False),
}

# Act 2's meeting in the lockers (phase 5): eight people in a closed circle round MEETING_CENTRE, each facing its middle (in a
# circle nobody stands between two others, so the player sees every face): the coworker on the west, his back to the predecessor's
# scratched locker, the player across from him on the east, and the six of the group between them (taquillas_npc_01..06 at the
# angles of MEETING_SEATS, degrees from the player's side, north negative: worker_a and worker_b face each other across the circle);
# where he waits during the third task (leaning on the corner of the ring by the broom); and, for each of the lockers' doors, a spot
# just outside it (its leaves are found round it) and the spot inside where someone stands to close it.
MEETING_CENTRE = (9.2, 30.5)
MEETING_RADIUS = 2.6
MEETING_SEATS = [-90, 90, -45, 45, -135, 135]
COWORKER_WAIT = (16.5, 50.5, 0)
LOCKERS_DOORS = {"lockers_door_1": (20.5, 20.0), "lockers_door_2": (20.5, 32.0)}   # just outside, in the main corridor


# Act 3 (phase 6). The locks by the two locked doors, on the wall outside each (cell, the way its front looks): the badge reader by the
# auxiliary lab's door (on the ring's side) and the keypad by the office's (in the authorized corridor). The auxiliary lab's computer on a
# desk against the partition; Rowan's office: his desk in the middle with his chair and computer, his portrait on the east wall with its
# plaque under it, a bookshelf behind him, a sofa, plants and a runner. Sealed chamber 02: a cell of glass up to the ceiling in the
# middle of its room, the scientist (the Pale man) standing in it with his back to its front, and a label over it.
READER = ((16, 1, 51), "east")
KEYPAD = ((30, 1, 77), "north")
AUX_DESK = [(1, 54), (1, 55)]                   # benches against the partition, facing east
AUX_COMPUTER = (1, 1, 54)
OFFICE_DESK = [(34, 82), (35, 82)]              # the executive desk, facing north (the visitor's side)
OFFICE_CHAIR = (34, 0, 83)
ROWAN_COMPUTER = (35, 1, 82)
PORTRAIT = ((40, 2, 82), "west")
OFFICE_SHELVES = [(x, 85) for x in range(33, 38)]
OFFICE_SOFA = [(29, 81), (29, 82)]              # against the west wall, facing east
OFFICE_PLANTS = [(29, 0, 79), (40, 0, 85), (29, 0, 85)]
OFFICE_RUG = [(34, 80), (35, 80)]
CHAMBER = (-7, 63, -3, 67)                      # the cell's glass: x0, z0, x1, z1 (both included), up to the ceiling
CHAMBER_LABEL = ((-5, 3, 62), "north")
# The film set: the prologue's bench (tools/prologue/room.py) against the auxiliary lab's south wall, turned to face north, the part of it
# the cinematic's camera saw (its x 5 .. 18) with the same things on it and on the wall over it; behind it, looking at it, two studio
# lights and the cine camera on their tripods.
SET_FROM, SET_TO = 5, 18                        # the prologue's x range copied
SET_X0, SET_Z = 1, 71                           # where its west end lands, and the row against the wall (the bench is z 70 .. 71)
FILM_LIGHTS = [(2, 0, 66), (13, 0, 66)]
FILM_CAMERA = (8, 0, 65)
# Phase 9. The sealed door's reader on the LAB's side (cell, the way its front looks). Behind the door: bars across the way to the factory's
# other part, and bricks left there; the stairs down (stair blocks, one a row); the basement, lit, rows of metal shelves three high
# (west and east of a middle aisle that comes from the stairs), what was brought here from the Noname complex of Chapter 1 (on the
# shelves' tops, on the floor and on the walls: cell -> block), the broom by the foot of the stairs and the dirt of the overtime's task
# (the overtime: Rowan's three hours, sweeping the basement) on the floor of the aisles.
SEALED_READER = ((61, 1, 30), "west")
CONSTRUCTION_BARS = [(75, y, z) for y in range(3) for z in range(26, 32)]
CONSTRUCTION_RUBBLE = [(78, 0, 27), (78, 1, 27), (79, 0, 27), (79, 0, 30), (77, 0, 30)]
STAIR_BLOCK = "minecraft:polished_andesite_stairs"
BASEMENT_Y = -BASEMENT_DEPTH
# The basement is a store kept in order: two blocks of shelves, six long, four rows each, a wide aisle between them from the stairs to the
# south wall, nothing on the floor but the dirt.
SHELF_ROWS = [(x, z) for z in (43, 47, 51, 55) for x in list(range(65, 71)) + list(range(81, 87))]
BASEMENT_BROOM = (69, BASEMENT_Y, 39)
BASEMENT_DIRT = [(73, 42), (77, 45), (68, 45), (84, 49), (74, 50), (67, 53), (79, 54), (72, 58)]
# Its NonameOS terminal, visible but out of the way: on a bench standing free against the south wall at the end of the middle aisle, away
# from the shelves, a chalk "???" over it. The aux lab's strange recording is on it (act3.json's `basement` computer).
BASEMENT_DESK = [(77, 62), (78, 62)]
BASEMENT_COMPUTER = (78, BASEMENT_Y + 1, 62)
BASEMENT_THINGS = {
    (75, BASEMENT_Y, 41): "wet_floor_sign[facing=north]",
    (66, BASEMENT_Y + 3, 43): "director_mask[facing=south]",
    (84, BASEMENT_Y + 3, 47): "trophy_cup[facing=south]",
    (82, BASEMENT_Y + 3, 51): "security_monitor[facing=south]",
    (68, BASEMENT_Y + 3, 55): "tin_cans[facing=south]",
    (85, BASEMENT_Y + 3, 55): "dead_flashlight[facing=south]",
    (88, BASEMENT_Y + 1, 42): "lounge_painting[facing=west]",
    (63, BASEMENT_Y + 1, 49): "diploma[facing=east]",
    (63, BASEMENT_Y + 1, 53): "month_plaque[facing=east]",
}

# --- Phase 10: the furnishing and the company's face ------------------------------------
# The rebuilt company, NONAME INDUSTRIES, shows itself off: corporate, clean, white with its blue (the logo, the posters), glass, screens,
# plants. Every room gets what it is for; the 10b tasks' corner of the LAB (its south wall east of the analyzer) is kept free.

def _stack2(out, x, z, facing, block, y=0):
    """A furniture stack two high (StackFurnitureBlock: up/down)."""
    out[(x, y, z)] = MOD + f"{block}[facing={facing},up=true,down=false]"
    out[(x, y + 1, z)] = MOD + f"{block}[facing={facing},up=false,down=true]"


def _row(out, cells, facing, block, y=0):
    """A furniture row (RowFurnitureBlock: left/right joined where the neighbour is in the row)."""
    states = row_states(cells, facing)
    for x, z in cells:
        out[(x, y, z)] = MOD + f"{block}[facing={facing},{states[(x, z)]}]"


def _door(out, x, z, facing, block="stall_door"):
    for half, y in (("lower", 0), ("upper", 1)):
        out[(x, y, z)] = MOD + f"{block}[facing={facing},half={half},hinge=left,open=false,powered=false]"


# The entrance: the reception desk in front of the logo wall (the logo on a white panel), a waiting corner to the west (sofas, a low table),
# plants along the south wall between its doors.
RECEPTION_DESK = [(x, 4) for x in range(37, 45)]
RECEPTION_MONITORS = [(39, 1, 4), (42, 1, 4)]
WAITING_SOFAS = [(x, 4) for x in range(25, 29)]
WAITING_TABLE = [(26, 6), (27, 6)]
ENTRANCE_PLANTS = [(36, 0, 4), (45, 0, 4), (24, 0, 4), (21, 0, 9), (31, 0, 9), (40, 0, 9), (43, 0, 9), (52, 0, 9), (60, 0, 9), (57, 0, 4)]
# The main corridor: plants and a bench along its walls, clear of its doors and of the people's ways.
CORRIDOR_PLANTS = [(20, 0, 16), (20, 0, 26), (20, 0, 40), (30, 0, 22), (30, 0, 36)]
CORRIDOR_BENCH = [(30, 31), (30, 32), (30, 33)]
# The LAB's empty half: fume hoods along the north wall, sample storage along the east wall (shelves, then cabinets past the sealed
# door), two islands of benches in the middle (one of work stations, one of instruments).
FUME_HOODS = [(x, 11) for x in range(40, 46)]
SAMPLE_SHELVES = [(61, z) for z in range(13, 23)]
SAMPLE_CABINETS = [(61, z) for z in range(34, 44)]
ISLAND_STATIONS = [(x, 24) for x in range(48, 56)]
ISLAND_STATION_MONITORS = [(49, 1, 24), (51, 1, 24), (53, 1, 24), (55, 1, 24)]
ISLAND_INSTRUMENTS = [(x, 33) for x in range(48, 56)]
ISLAND_INSTRUMENT_PROPS = {(48, 1, 33): "microscope", (50, 1, 33): "lab_beakers", (52, 1, 33): "tube_rack", (54, 1, 33): "microscope",
                           (55, 1, 33): "lab_beakers"}
LAB_CARTS = [(34, 0, 44), (59, 0, 15)]
# The dining room: tables of four inside and outside the people's loop, the counter and the machines along the south wall, plants in
# the corners, warm sconces on the walls (and its own warm ceiling lights: SPACE_LIGHTS).
DINING_TABLES = [(34, 59), (42, 59), (34, 62), (42, 62), (50, 57), (54, 57), (50, 61), (54, 61), (27, 58), (27, 62)]
DINING_COUNTER = [(x, 67) for x in range(31, 41)]
DINING_COFFEE = [(33, 1, 67), (37, 1, 67)]
DINING_WATER = (41, 0, 67)
DINING_VENDING = [(49, 67), (51, 67), (53, 67)]
DINING_PLANTS = [(22, 0, 54), (56, 0, 54), (56, 0, 66), (22, 0, 66)]
DINING_SCONCES = [((24, 2, 53), "south"), ((33, 2, 53), "south"), ((43, 2, 53), "south"), ((52, 2, 53), "south"),
                  ((23, 2, 67), "north"), ((48, 2, 67), "north"), ((55, 2, 67), "north")]
# The WCs (no cameras in them): stalls with a toilet each, partitions two high, a door; sinks with a mirror over them.
WC_WEST_TOILETS = [((14, 0, 11), "south"), ((16, 0, 11), "south")]
WC_WEST_PARTITIONS = [(x, z) for x in (15, 17) for z in (11, 12, 13)]
WC_WEST_DOORS = [((14, 13), "south"), ((16, 13), "south")]
WC_WEST_SINKS = [(x, 15) for x in (14, 15, 16)]
WC_EAST_TOILETS = [((42, 0, 81), "north"), ((44, 0, 81), "north")]
WC_EAST_PARTITIONS = [(x, z) for x in (43, 45) for z in (80, 81)]
WC_EAST_DOORS = [((42, 80), "north"), ((44, 80), "north")]
WC_EAST_SINKS = [(47, z) for z in (79, 80)]
# Security (guard B rests here): the monitor bank on the east wall over a desk, a chair; the guards' break corner to the west (lockers, a
# table, a counter with a coffee machine) and the key rack. Its middle (x 20..22) is guard B's way and stays clear.
SECURITY_DESK = [(27, z) for z in range(80, 84)]
SECURITY_MONITORS = [(27, y, z) for y in (1, 2) for z in range(80, 84)]
SECURITY_CHAIR = (26, 0, 81)
SECURITY_LOCKERS = [(x, 79) for x in range(15, 19)]
SECURITY_TABLE = (17, 0, 82)
SECURITY_COUNTER = [(x, 84) for x in range(23, 26)]
SECURITY_COFFEE = (24, 1, 84)
SECURITY_KEYS = (19, 1, 84)
# Plants in the ring's corners and the authorized corridor.
MORE_PLANTS = [(61, 0, 48), (61, 0, 72), (16, 0, 72), (16, 0, 77), (48, 0, 74)]

# Lights of their own: the dining room's ceiling lights are warm.
SPACE_LIGHTS = {"dining": MOD + "warm_ceiling_light"}


def furnishing():
    """Phase 10's props: see the constants above."""
    out = Cells()
    _row(out, RECEPTION_DESK, "south", "counter")
    for cell in RECEPTION_MONITORS:
        out[cell] = MOD + "lab_monitor[facing=south]"
    _row(out, WAITING_SOFAS, "south", "sofa")
    for x, z in WAITING_TABLE:
        out[(x, 0, z)] = MOD + "low_table[facing=north]"
    for cell in ENTRANCE_PLANTS + CORRIDOR_PLANTS + DINING_PLANTS + MORE_PLANTS:
        out[cell] = MOD + "potted_plant[facing=north]"
    _row(out, CORRIDOR_BENCH, "west", "bench")
    for x, z in FUME_HOODS:
        _stack2(out, x, z, "south", "fume_hood")
    for x, z in SAMPLE_SHELVES:
        _stack2(out, x, z, "west", "lab_shelf")
    _row(out, SAMPLE_CABINETS, "west", "lab_cabinet")
    _row(out, ISLAND_STATIONS, "north", "lab_bench")
    for cell in ISLAND_STATION_MONITORS:
        out[cell] = MOD + "lab_monitor[facing=north]"
    _row(out, ISLAND_INSTRUMENTS, "south", "lab_bench")
    for cell, block in ISLAND_INSTRUMENT_PROPS.items():
        out[cell] = MOD + f"{block}[facing=south]"
    for cell in LAB_CARTS:
        out[cell] = MOD + "lab_cart[facing=north]"
    for x, z in DINING_TABLES:
        out[(x, 0, z)] = MOD + "cafe_table[facing=north]"
        for (dx, dz), facing in (((0, -1), "south"), ((0, 1), "north"), ((-1, 0), "east"), ((1, 0), "west")):
            out[(x + dx, 0, z + dz)] = MOD + f"cafe_chair[facing={facing}]"
    _row(out, DINING_COUNTER, "north", "counter")
    for cell in DINING_COFFEE:
        out[cell] = MOD + "coffee_machine[facing=north]"
    out[DINING_WATER] = MOD + "water_dispenser[facing=north]"
    for x, z in DINING_VENDING:
        _stack2(out, x, z, "north", "vending_machine")
    for cell, facing in DINING_SCONCES:
        out[cell] = MOD + f"wall_sconce[facing={facing}]"
    for toilets, partitions, doors, sinks, sink_facing in (
            (WC_WEST_TOILETS, WC_WEST_PARTITIONS, WC_WEST_DOORS, WC_WEST_SINKS, "north"),
            (WC_EAST_TOILETS, WC_EAST_PARTITIONS, WC_EAST_DOORS, WC_EAST_SINKS, "west")):
        for cell, facing in toilets:
            out[cell] = MOD + f"toilet[facing={facing}]"
        for x, z in partitions:
            _stack2(out, x, z, "north" if x in (15, 17) else "east", "stall_wall")
        for (x, z), facing in doors:
            _door(out, x, z, facing)
        for x, z in sinks:
            out[(x, 0, z)] = MOD + f"wc_sink[facing={sink_facing}]"
        _row(out, sinks, sink_facing, "wc_mirror", y=1)
    _row(out, SECURITY_DESK, "west", "office_desk")
    for i, cell in enumerate(SECURITY_MONITORS):
        out[cell] = MOD + f"monitor_bank[cam={i % 15},facing=west]"
    out[SECURITY_CHAIR] = MOD + "lab_chair[facing=east]"
    for x, z in SECURITY_LOCKERS:
        _stack2(out, x, z, "south", "locker")
    out[SECURITY_TABLE] = MOD + "cafe_table[facing=north]"
    tx, _, tz = SECURITY_TABLE
    for (dx, dz), facing in (((0, -1), "south"), ((0, 1), "north"), ((-1, 0), "east"), ((1, 0), "west")):
        out[(tx + dx, 0, tz + dz)] = MOD + f"cafe_chair[facing={facing}]"
    _row(out, SECURITY_COUNTER, "north", "counter")
    out[SECURITY_COFFEE] = MOD + "coffee_machine[facing=north]"
    out[SECURITY_KEYS] = MOD + "key_rack[facing=north]"
    return out


# Room names over the doors (lab plates, on the side one comes from), arrows, the logo and its slogan, the company's posters and screens.
DOOR_SIGNS = [
    ("entrance_main", "north", "main_corridor"), ("entrance_lab_1", "north", "lab"), ("entrance_lab_2", "north", "lab"),
    ("main_lab_1", "west", "lab"), ("main_lab_2", "west", "lab"), ("wc_west", "east", "wc"), ("lockers_1", "east", "lockers"),
    ("lockers_2", "east", "lockers"), ("main_ring", "north", "ring"), ("lab_ring", "south", "lab"),
    ("dining_n1", "north", "dining"), ("dining_n2", "north", "dining"), ("dining_s1", "south", "dining"), ("dining_s2", "south", "dining"),
    ("aux_lab", "east", "aux_lab"), ("ring_authorized", "north", "authorized"), ("security", "north", "security"),
    ("office", "north", "office"), ("wc_east", "north", "wc"), ("sealed", "west", "sealed"), ("restricted_east", "west", "restricted"),
]
LOGO = ((40, 5, 1), "south", "every_tunnel.ch2.l1.sign.logo", "logo", 0.5)
POSTERS = [
    ((54, 2, 3), "south", "every_tunnel.ch2.l1.poster.loyalty", "poster", 0.0),
    ((30, 2, 28), "west", "every_tunnel.ch2.l1.poster.clean", "poster", 0.0),
    ((20, 2, 36), "east", "every_tunnel.ch2.l1.poster.employee", "poster", 0.0),
    ((38, 2, 53), "south", "every_tunnel.ch2.l1.poster.break", "poster", 0.5),
    ((32, 2, 34), "east", "every_tunnel.ch2.l1.poster.accuracy", "poster", 0.0),
    ((37, 2, 77), "north", "every_tunnel.ch2.l1.poster.supervisor", "poster", 0.0),
    ((26, 2, 72), "north", "every_tunnel.ch2.l1.poster.rebuilt", "poster", 0.0),
    ((20, 2, 12), "east", "every_tunnel.ch2.l1.sign.arrow_dining", "lab", 0.0),
]


def door_signs():
    """[(cell, facing, key, style, offset)]: a plate over each door of DOOR_SIGNS, on the given side, centred on the door."""
    doors = {d[0]: d for d in DOORS}
    out = []
    for door_id, side, room in DOOR_SIGNS:
        _, axis, line, first, width, _ = doors[door_id]
        d = (width - 1) / 2
        if axis == "z":
            z = line - 1 if side == "north" else line + 1
            cell, offset = (first, 2, z), (-d if side == "north" else d)
        else:
            x = line - 1 if side == "west" else line + 1
            cell, offset = (x, 2, first), (d if side == "west" else -d)
        out.append((cell, side, f"every_tunnel.ch2.l1.sign.room.{room}", "lab", offset))
    return out


# Text on walls (cell, the way the text faces, its lang key, its style, blocks to the reader's right): chamber 02's label, the portrait's
# plaque, the landing's two signs, and phase 10's room plates, logo, posters and screens.
WALL_TEXTS = [
    (CHAMBER_LABEL[0], CHAMBER_LABEL[1], "every_tunnel.ch2.l1.sign.chamber_02", "lab", 0.0),
    ((40, 1, 82), "west", "every_tunnel.ch2.l1.sign.portrait", "plaque", 0.0),
    ((73, 2, 28), "west", "every_tunnel.ch2.l1.sign.under_construction", "plate", 0.0),
    ((69, 2, 30), "north", "every_tunnel.ch2.l1.sign.basement", "lab", 0.0),
] + door_signs() + [LOGO] + POSTERS

# Scrawls on the basement's walls (wall_message blocks, in chalk; for now only the "???" over the terminal: cell, the way the text faces, its lang key, a line's height in blocks,
# degrees off level). Always there, always in English; the chalk is black on a wall that looks white and white on one that looks dark,
# as the basement's do (Level.chalk).
WALL_MESSAGES = [
    ((78, BASEMENT_Y + 3, 62), "north", "every_tunnel.ch2.l1.scrawl.terminal", 0.7, 4.0),
]

OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def row_states(cells, facing):
    """{(x, z): "left=..,right=.."} for a row of furniture: joined where the neighbour is in the row. Left and right are as seen from
    in front of it (a row facing north has its left to the east)."""
    step = {"north": (1, 0), "south": (-1, 0), "east": (0, 1), "west": (0, -1)}[facing]
    out = {}
    for x, z in cells:
        left = (x + step[0], z + step[1]) in cells
        right = (x - step[0], z - step[1]) in cells
        out[(x, z)] = f"left={str(left).lower()},right={str(right).lower()}"
    return out


def film_set():
    """{(x, y, z): block} of the film set: the prologue's bench and props, turned round onto the south wall."""
    out = {}
    width = SET_TO - SET_FROM
    cells = [(SET_X0 + i, z) for i in range(width + 1) for z in (SET_Z - 1, SET_Z)]
    states = row_states(cells, "north")
    for x, z in cells:
        out[(x, 0, z)] = MOD + f"lab_bench[facing=north,{states[(x, z)]}]"

    def place(px_, pz):
        return SET_X0 + (SET_TO - px_), SET_Z - pz

    for px_, pz, block in PROLOGUE.PROPS:
        if SET_FROM <= px_ <= SET_TO:
            x, z = place(px_, pz)
            out[(x, 1, z)] = MOD + f"{block}[facing=north]"
    for px_, y, block in PROLOGUE.WALL_DECOR:
        if SET_FROM <= px_ <= SET_TO:
            x, z = place(px_, 0)
            out[(x, y, z)] = MOD + f"{block}[facing=north]"
    return out


class Cells(dict):
    """A dict of cells that refuses to set one twice: two props on the same cell is a mistake of the layout."""

    def __setitem__(self, cell, block):
        if cell in self:
            raise SystemExit(f"two props on the cell {cell}: {self[cell]} and {block}")
        super().__setitem__(cell, block)


def props():
    """{(x, y, z): block} of everything that is furniture or a prop: see the constants above."""
    bench = MOD + "lab_bench[facing={},left=false,right=false]"
    out = Cells()
    for z in range(21, 29):
        out[(32, 0, z)] = bench.format("east")
    for cell in TUBES:
        out[cell] = MOD + "sample_tube[facing=east]"
    out[TERMINAL] = MOD + "noname_terminal[facing=east]"
    for x in range(50, 54):
        out[(x, 0, 46)] = bench.format("north")
    out[ANALYZER] = MOD + "analyzer[facing=north]"
    out[BROOM] = MOD + "leaning_broom[facing=west]"
    for i, (x, z) in enumerate(DIRT_SPOTS):
        out[(x, 0, z)] = MOD + f"dirt_spot[facing={FACINGS[i % 4]}]"
    for cell in SPEAKERS.values():
        out[cell] = MOD + "ceiling_speaker[facing=north]"
    for x, z, facing in STATION_BENCHES:
        out[(x, 0, z)] = bench.format(facing)
    for x, z, facing in STATION_MONITORS:
        out[(x, 1, z)] = MOD + f"lab_monitor[facing={facing}]"
    for cell, block in lockers().items():
        out[cell] = block
    for space, cameras in CAMERAS.items():
        for x, z, facing in cameras:
            out[(x, SPACE_TOPS[space] - 1, z)] = MOD + f"factory_camera[facing={facing},part=mount]"
    for cell, block in act3_props().items():
        out[cell] = block
    for cell, block in furnishing().items():
        out[cell] = block
    return out


def act3_props():
    """Act 3's: the locks, the auxiliary lab's desk and computer, the film set, chamber 02's cell, Rowan's office, the wall texts."""
    out = {}
    (cell, facing) = READER
    out[cell] = MOD + f"badge_reader[facing={facing},unlocked=false]"
    (cell, facing) = KEYPAD
    out[cell] = MOD + f"keypad[facing={facing},unlocked=false]"
    states = row_states(AUX_DESK, "east")
    for x, z in AUX_DESK:
        out[(x, 0, z)] = MOD + f"lab_bench[facing=east,{states[(x, z)]}]"
    out[AUX_COMPUTER] = MOD + "noname_terminal[facing=east]"
    out[(AUX_DESK[1][0], 1, AUX_DESK[1][1])] = MOD + "paper_stack[facing=east]"
    out.update(film_set())
    for x, y, z in FILM_LIGHTS:
        out[(x, y, z)] = MOD + "film_light[facing=south]"
    out[FILM_CAMERA] = MOD + "film_camera[facing=south]"
    x0, z0, x1, z1 = CHAMBER
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                for y in range(SPACE_HEIGHTS["aux_chamber"]):
                    out[(x, y, z)] = MOD + "factory_glass"
    states = row_states(OFFICE_DESK, "north")
    for x, z in OFFICE_DESK:
        out[(x, 0, z)] = MOD + f"exec_desk[facing=north,{states[(x, z)]}]"
    out[OFFICE_CHAIR] = MOD + "exec_chair[facing=north]"
    out[ROWAN_COMPUTER] = MOD + "noname_terminal[facing=south]"
    out[(OFFICE_DESK[0][0], 1, OFFICE_DESK[0][1])] = MOD + "paper_stack[facing=south]"
    (cell, facing) = PORTRAIT
    out[cell] = MOD + f"rowan_portrait[facing={facing}]"
    for x, z in OFFICE_SHELVES:
        out[(x, 0, z)] = MOD + "bookshelf[facing=north,up=true,down=false]"
        out[(x, 1, z)] = MOD + "bookshelf[facing=north,up=false,down=true]"
    states = row_states(OFFICE_SOFA, "east")
    for x, z in OFFICE_SOFA:
        out[(x, 0, z)] = MOD + f"leather_sofa[facing=east,{states[(x, z)]}]"
    for cell in OFFICE_PLANTS:
        out[cell] = MOD + "potted_plant[facing=north]"
    for x, z in OFFICE_RUG:
        out[(x, 0, z)] = MOD + "office_rug[facing=north]"
    for cell, facing, *_ in WALL_TEXTS:
        out[cell] = MOD + f"wall_text[facing={facing}]"
    out.update(phase9_props())
    return out


def phase9_props():
    """Phase 9's: the sealed door's reader, the barred way and its rubble, the stairs, the basement's shelves and things, the broom and the
    dirt, the scrawls."""
    out = {}
    (cell, facing) = SEALED_READER
    out[cell] = MOD + f"badge_reader[facing={facing},unlocked=false]"
    for cell in CONSTRUCTION_BARS:
        out[cell] = "minecraft:iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]"
    for cell in CONSTRUCTION_RUBBLE:
        out[cell] = "minecraft:bricks"
    flight = next(sp for sp in SPACES if sp.id == "stairs")
    for (x, z), y in flight.floors.items():
        out[(x, y, z)] = f"{STAIR_BLOCK}[facing=north,half=bottom,shape=straight,waterlogged=false]"
    for x, z in SHELF_ROWS:
        for k in range(3):
            out[(x, BASEMENT_Y + k, z)] = MOD + f"metal_shelf[facing=south,down={str(k > 0).lower()},up={str(k < 2).lower()}]"
    for cell, block in BASEMENT_THINGS.items():
        out[cell] = MOD + block
    out[BASEMENT_BROOM] = MOD + "leaning_broom[facing=south]"
    states = row_states(BASEMENT_DESK, "north")
    for x, z in BASEMENT_DESK:
        out[(x, BASEMENT_Y, z)] = MOD + f"lab_bench[facing=north,{states[(x, z)]}]"
    out[BASEMENT_COMPUTER] = MOD + "noname_terminal[facing=north]"
    for i, (x, z) in enumerate(BASEMENT_DIRT):
        out[(x, BASEMENT_Y, z)] = MOD + f"dirt_spot[facing={FACINGS[i % 4]}]"
    for cell, facing, *_ in WALL_MESSAGES:
        out[cell] = MOD + f"wall_message[facing={facing}]"
    return out


def lockers():
    """The lockers' room's: a stack of two for each cell along the west, north and south walls, the predecessor's among them."""
    out = {}

    def stack(x, z, facing, block):
        out[(x, 0, z)] = MOD + f"{block}[facing={facing},up=true,down=false]"
        out[(x, 1, z)] = MOD + f"{block}[facing={facing},up=false,down=true]"

    for z in range(18, 34):
        stack(5, z, "east", "predecessor_locker" if (5, 0, z) == PREDECESSOR_LOCKER else "locker")
    for x in range(6, 18):
        stack(x, 17, "south", "locker")
        stack(x, 34, "north", "locker")
    return out


CAMERA_FREE = {"lockers", "construction", "stairs", "wc_west", "wc_east"}   # the spaces with no (decorative) security cameras


def world(x, y, z):
    return ORIGIN[0] + x, ORIGIN[1] + y, ORIGIN[2] + z


def door_cells(door):
    """[(x, y, z)] of the passage a door fills, and the two cells (x, z) beside each of its columns."""
    _, axis, line, first, width, _ = door
    cells, sides = [], []
    for k in range(first, first + width):
        for y in range(DOOR_HEIGHT):
            cells.append((k, y, line) if axis == "z" else (line, y, k))
        sides.append(((k, line - 1), (k, line + 1)) if axis == "z" else ((line - 1, k), (line + 1, k)))
    return cells, sides


class Level:
    def __init__(self):
        self.spaces = {s.id: s for s in SPACES}
        self.air = {}                  # (x, y, z) -> the space's id
        for s in SPACES:
            for x, z in s.cells:
                for y in range(s.floor(x, z), s.top_at(x, z)):
                    if (x, y, z) in self.air:
                        raise SystemExit(f"{s.id} overlaps {self.air[(x, y, z)]} at {(x, y, z)}")
                    self.air[(x, y, z)] = s.id
        for x0, z0, x1, z1 in PILLARS:
            for x in range(x0, x1):
                for z in range(z0, z1):
                    for y in range(10):
                        self.air.pop((x, y, z), None)
            for s in SPACES:
                s.cells -= {(x, z) for x in range(x0, x1) for z in range(z0, z1)}
        self.openings = {}             # (x, y, z) -> the door's id: wall cells that are a passage
        self.decor = {}                # (x, y, z) -> the door's id: wall cells that hold a door onto nothing
        for door in DOORS:
            cells, _ = door_cells(door)
            for c in cells:
                (self.decor if door[5] == "decor" else self.openings)[c] = door[0]
        self.shell = self.build_shell()

    def build_shell(self):
        """{(x, y, z): role}: every cell beside the air that is not air, as floor, ceiling, wall, dado or glass."""
        glass = {(x, y, z) for x, z0, z1, top in GLASS_WALLS for z in range(z0, z1 + 1) for y in range(top + 1)}
        shell = {}
        for x, y, z in self.air:
            for dx, dy, dz in AROUND:
                c = (x + dx, y + dy, z + dz)
                if c in self.air or c in shell or c in self.openings:
                    continue
                if c in glass:
                    shell[c] = "glass"
                elif (c[0], c[1] + 1, c[2]) in self.air:
                    shell[c] = "floor"
                elif (c[0], c[1] - 1, c[2]) in self.air:
                    shell[c] = "ceiling"
                else:
                    shell[c] = "dado" if c[1] == 0 else "wall"
        for (x, y, z), door in self.decor.items():           # a door onto nothing: a backing wall behind it
            shell.pop((x, y, z), None)
            behind = (x + 1, y, z)
            shell[behind] = "wall"
        return shell

    def chalk(self, cell, facing):
        """The chalk a scrawl at `cell`, its text facing `facing`, is written in: black on a wall that looks white, white on one that looks
        dark. Underground the white concrete reads as a strong grey in the game's light (the basement's), so there it is white too."""
        dx, dz = {"north": (0, 1), "south": (0, -1), "east": (-1, 0), "west": (1, 0)}[facing]
        wall = self.shell.get((cell[0] + dx, cell[1], cell[2] + dz))
        return "black" if wall == "wall" and cell[1] >= 0 else "white"

    # --- lights -----------------------------------------------------------------------

    def lights(self):
        """[(x, y, z)] of the lamps, in the ceiling's layer, spread over each space's boxes."""
        out = set()
        for s in SPACES:
            for x0, z0, x1, z1 in s.boxes:
                for x in grid(x0, x1 - 1, s.spacing):
                    for z in grid(z0, z1 - 1, s.spacing):
                        top = s.top_at(x, z)
                        if (x, z) in s.cells and self.shell.get((x, top, z)) == "ceiling":
                            out.add((x, top, z))
        return sorted(out)

    # --- the plan ---------------------------------------------------------------------

    def blocks(self):
        """{(x, y, z): block}: the shell with its lamps, the doors and the clock."""
        out = {c: BLOCKS[role] for c, role in self.shell.items()}
        for x, y, z in self.lights():
            out[(x, y, z)] = SPACE_LIGHTS.get(self.air.get((x, y - 1, z)), BLOCKS["light"])
        for door in DOORS:
            _, axis, line, first, width, kind = door
            if kind == "opening":
                continue
            block = DOOR_BLOCKS["locked" if kind in ("locked", "decor") else "normal"]
            facing = "south" if axis == "z" else "east"
            # A door's hinge side is seen looking the way it faces: a door facing south has its left hinge on the east edge
            # of its cell, one facing east has it on the north edge. In a pair the hinges go on the outer edges, so the
            # first leaf (the lowest x or z) hinges on its low edge: "right" going south, "left" going east.
            low_edge = "right" if axis == "z" else "left"
            high_edge = "left" if axis == "z" else "right"
            for i, k in enumerate(range(first, first + width)):
                hinge = low_edge if i % 2 == 0 else high_edge
                for half, y in (("lower", 0), ("upper", 1)):
                    cell = (k, y, line) if axis == "z" else (line, y, k)
                    out[cell] = f"{block}[facing={facing},half={half},hinge={hinge},open=false,powered=false]"
        x, y, z = CLOCK["at"]
        out[(x, y, z)] = f"{MOD}time_clock[facing={CLOCK['facing']},unlocked=false]"
        out.update(props())
        return out

    def fills(self):
        """The blocks as boxes of one block: [((x0, y0, z0), (x1, y1, z1), block)], both corners included."""
        return merge_boxes(self.blocks())

    def bounds(self):
        """(low, high) corners of everything the plan touches, walls included."""
        cells = list(self.shell) + list(self.air)
        lo = tuple(min(c[i] for c in cells) for i in range(3))
        hi = tuple(max(c[i] for c in cells) for i in range(3))
        return lo, hi


def grid(a, b, spacing):
    """Positions spread over a .. b (inclusive) every `spacing`, centred; at least one."""
    length = b - a + 1
    n = max(1, (length + spacing // 2) // spacing)
    start = a + (length - (n - 1) * spacing - 1) // 2
    return [start + k * spacing for k in range(n)]


def merge_boxes(blocks):
    """Greedy: runs along x, then the same runs on the next z, then the same boxes on the next y."""
    runs = {}                                   # (y, z) -> [(x0, x1, block)]
    for (x, y, z), block in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        row = runs.setdefault((y, z), [])
        if row and row[-1][1] == x - 1 and row[-1][2] == block:
            row[-1] = (row[-1][0], x, block)
        else:
            row.append((x, x, block))
    slabs = {}                                  # y -> [(x0, x1, z0, z1, block)] merged across z
    for (y, z) in sorted(runs):
        open_boxes = slabs.setdefault(y, [])
        for x0, x1, block in runs[(y, z)]:
            for i, box in enumerate(open_boxes):
                if box[0] == x0 and box[1] == x1 and box[4] == block and box[3] == z - 1:
                    open_boxes[i] = (x0, x1, box[2], z, block)
                    break
            else:
                open_boxes.append((x0, x1, z, z, block))
    out, columns = [], {}
    for y in sorted(slabs):                     # merged across y
        for x0, x1, z0, z1, block in slabs[y]:
            key = (x0, x1, z0, z1, block)
            if key in columns and columns[key][1] == y - 1:
                columns[key] = (columns[key][0], y)
            else:
                if key in columns:
                    out.append(((x0, columns[key][0], z0), (x1, columns[key][1], z1), block))
                columns[key] = (y, y)
    for (x0, x1, z0, z1, block), (y0, y1) in columns.items():
        out.append(((x0, y0, z0), (x1, y1, z1), block))
    return sorted(set(out), key=lambda f: (f[0][1], f[0][2], f[0][0], f[2]))


# --- the named places --------------------------------------------------------------------

def centre(x, z):
    """The middle of a cell, for somewhere to stand."""
    return x + 0.5, z + 0.5


def named_places(level):
    """(anchors, zones, paths) in plan coordinates: anchors (id, (x, y, z), yaw, provisional), zones (id, box, provisional)
    with the box's far corner excluded in x and z, paths (id, [(x, y, z)], closed, provisional). A place is provisional
    while the furniture or the routine it stands for is yet to be designed."""
    a = []
    sp = level.spaces

    def add(anchor_id, x, z, yaw, provisional, y=0.0):
        a.append((anchor_id, (x, y, z), yaw, provisional))

    cl = CLOCK["at"]
    add("spawn_clockin", cl[0] + 0.5, 6.5, 0, False)
    add("time_clock", cl[0] + 0.5, cl[2] + 0.5, 180, False, y=cl[1])
    add("task1_terminal", 34.5, 26.5, 90, False)
    add("task2_analyzer", 51.5, 44.5, 0, False)
    # The blocks of the tasks (tasks.json names them) and the ceiling speakers (the first announcement's cinematic turns to the LAB's).
    for i, (x, y, z) in enumerate(TUBES):
        add(f"task1_tube_{i + 1}", x + 0.5, z + 0.5, 0, False, y=float(y))
    add("task1_terminal_block", TERMINAL[0] + 0.5, TERMINAL[2] + 0.5, 0, False, y=float(TERMINAL[1]))
    add("task2_analyzer_block", ANALYZER[0] + 0.5, ANALYZER[2] + 0.5, 0, False, y=float(ANALYZER[1]))
    add("task3_broom_block", BROOM[0] + 0.5, BROOM[2] + 0.5, 0, False, y=float(BROOM[1]))
    for zone_id, (x, y, z) in SPEAKERS.items():
        add(f"{zone_id}_src", x + 0.5, z + 0.5, 0, False, y=y + 0.9)
    # The meeting: him, the player and the six in a circle, each facing its middle.
    cx, cz = MEETING_CENTRE

    def seat(deg):
        ang = math.radians(deg)
        x, z = round(cx + MEETING_RADIUS * math.cos(ang), 2), round(cz + MEETING_RADIUS * math.sin(ang), 2)
        return x, z, round(math.degrees(math.atan2(-(cx - x), cz - z)))

    add("meeting_coworker", *seat(180), True)
    add("meeting_player", *seat(0), True)
    for i, deg in enumerate(MEETING_SEATS):
        add(f"taquillas_npc_{i + 1:02d}", *seat(deg), True)
    add("coworker_wait", COWORKER_WAIT[0], COWORKER_WAIT[1], COWORKER_WAIT[2], True)
    for door, (x, z) in LOCKERS_DOORS.items():
        add(door, x, z, -90, True)
        add(f"{door}_inside", x - 3.0, z, -90, True)
    add("predecessor_locker", PREDECESSOR_LOCKER[0] + 1.5, PREDECESSOR_LOCKER[2] + 0.5, 90, False)
    add("predecessor_locker_block", PREDECESSOR_LOCKER[0] + 0.5, PREDECESSOR_LOCKER[2] + 0.5, 90, False, y=0.0)
    for name, (x, z, yaw) in NPC_SPOTS.items():
        add(name, x, z, yaw, True)
    add("taquillas_exit", 21.5, 22.5, -90, False)
    add("aux_lab_door", 15.0, 50.0, 90, False)
    add("aux_lab_reader", READER[0][0] + 0.5, READER[0][2] + 0.5, 90, False, y=float(READER[0][1]))
    add("aux_lab_computer", AUX_COMPUTER[0] + 0.5, AUX_COMPUTER[2] + 0.5, 90, False, y=float(AUX_COMPUTER[1]))
    cx0, cz0, cx1, cz1 = CHAMBER
    add("chamber_02", (cx0 + cx1 + 1) / 2, (cz0 + cz1 + 1) / 2, 0, False)        # facing south: his back to the cell's front
    add("door_anchor", 32.0, 78.0, 0, False)
    add("desk_anchor", 35.0, 82.5, 180, True)
    add("office_keypad", KEYPAD[0][0] + 0.5, KEYPAD[0][2] + 0.5, 0, False, y=float(KEYPAD[0][1]))
    add("rowan_computer", ROWAN_COMPUTER[0] + 0.5, ROWAN_COMPUTER[2] + 0.5, 180, False, y=float(ROWAN_COMPUTER[1]))
    add("paper_medical", 34.5, 82.5, 180, True, y=1.0)
    add("paper_contract_14", 35.5, 82.5, 180, True, y=1.0)
    add("painting", PORTRAIT[0][0] + 0.5, PORTRAIT[0][2] + 0.5, 90, False, y=float(PORTRAIT[0][1]))
    # The office's cinematic (phase 8): the player held behind the desk at Rowan's computer, facing the door; Rowan's way in (from the
    # corridor through the doorway to his place just inside, beside the door, where he talks and then waits for the player to go), the
    # front of the desk where he slides the pass across it to the player.
    add("office_player_mark", 35.0, 83.6, 180, False)
    add("card_slide_start", 35.0, 82.15, 0, False, y=1.02)
    add("card_slide_end", 35.1, 82.85, 0, False, y=1.02)
    add("rowan_spawn", 32.5, 76.5, 0, False)
    add("rowan_doorway", 32.0, 78.5, 0, False)
    add("rowan_threshold", 30.4, 79.7, -90, False)
    add("rowan_desk_front", 35.0, 81.2, 0, False)
    add("restricted_door_east", 61.0, 75.5, -90, False)
    add("sealed_door", 62.0, 28.0, -90, False)
    add("patrol_a_pause", 36.0, 71.0, 180, False)              # by the door down into the authorized corridor, facing it
    add("patrol_b_spawn", 21.5, 82.5, 180, False)               # in Security, where patrol B rests at each end of its round
    # Where a player caught by a guard is put back (Act 3's patrols): before the auxiliary lab's door, before the way down into the
    # authorized corridor, and in the ring by the main corridor's door (caught anywhere else).
    add("return_aux_lab", 18.5, 50.5, 90, False)
    add("return_authorized", 36.5, 70.0, 0, False)
    add("return_ring", 22.0, 50.5, 180, False)
    add("return_lab", 28.5, 17.0, -90, False)                  # in the main corridor, by the LAB's first door (caught in the dark LAB)
    add("patrol_c_pause", 58.5, 28.0, 90, False)                # patrol C stops by the sealed door, looking at it
    # Phase 9: the sealed door's reader; the broom at the foot of the stairs; where one stands on arriving in the basement.
    add("sealed_reader", SEALED_READER[0][0] + 0.5, SEALED_READER[0][2] + 0.5, 90, False, y=float(SEALED_READER[0][1]))
    add("basement_broom_block", BASEMENT_BROOM[0] + 0.5, BASEMENT_BROOM[2] + 0.5, 0, False, y=float(BASEMENT_BROOM[1]))
    add("basement_landing", 71.5, 40.5, 0, False, y=float(BASEMENT_Y))
    add("basement_computer", BASEMENT_COMPUTER[0] + 0.5, BASEMENT_COMPUTER[2] + 0.5, 0, False, y=float(BASEMENT_COMPUTER[1]))

    zones = []

    def zone(zone_id, space, provisional=False, box=None):
        x0, z0, x1, z1 = box or sp[space].bounds
        zones.append((zone_id, (x0, z0, x1, z1, sp[space].height if space else 4, sp[space].y0 if space else 0), provisional))

    zone("entrance", "entrance")
    zone("main_corridor", "main_corridor")
    zone("lab", "lab")
    zone("wc_west", "wc_west")
    zone("taquillas_trigger", "lockers")
    zone("dining", "dining")
    zone("ring_north", "ring", box=(16, 48, 62, 52))
    zone("ring_south", "ring", box=(16, 69, 62, 73))
    zone("ring_west", "ring", box=(16, 52, 20, 69))
    zone("ring_east", "ring", box=(58, 52, 62, 69))
    zone("task3_sweep_area", "ring", box=(16, 52, 20, 69))
    zone("aux_lab", "aux_lab")
    zone("aux_chamber", "aux_chamber")
    zone("aux_lab_glass", "ring", True, box=(16, 52, 17, 69))
    zone("prologue_set", "aux_lab", False, box=(1, 64, 15, 72))
    zone("authorized_corridor", "authorized")
    zone("security_room", "security")
    zone("office", "office")
    zone("wc_east", "wc_east")
    zone("sealed_corridor", "sealed_zone")
    zone("basement", "basement")
    zone("speaker_entrance", "entrance")
    zone("speaker_lab", "lab")
    zone("speaker_main", "main_corridor")
    zone("speaker_west", "ring", box=(16, 52, 20, 69))

    paths = []
    # The middle of the ring (its stretches are 4 wide: the middle is where two blocks meet) and of the authorized corridor.
    ring_pts = [(18.0, 50.0), (39.0, 50.0), (60.0, 50.0), (60.0, 60.0), (60.0, 71.0), (48.0, 71.0), (36.0, 71.0), (18.0, 71.0), (18.0, 60.0)]
    paths.append(("patrol_a_waypoints", [(x, 0.0, z) for x, z in ring_pts], True, False))
    # Patrol C, after Rowan's office: round the dark LAB, between the work stations and the sealed door, anticlockwise.
    lab_pts = [(35.5, 20.5), (35.5, 40.5), (58.5, 40.5), (58.5, 28.0), (58.5, 20.5)]
    paths.append(("patrol_c_waypoints", [(x, 0.0, z) for x, z in lab_pts], True, False))
    paths.append(("task3_dirt_spots", [(x + 0.5, 0.0, z + 0.5) for x, z in DIRT_SPOTS], False, False))
    paths.append(("basement_dirt_spots", [(x + 0.5, float(BASEMENT_Y), z + 0.5) for x, z in BASEMENT_DIRT], False, False))
    for name, (points, closed) in NPC_ROUTES.items():
        paths.append((name, [(x, 0.0, z) for x, z in points], closed, True))
    # Patrol B comes out of Security (through its door at x 21), goes along the authorized corridor to its east end and back.
    paths.append(("patrol_b_waypoints", [(21.5, 0.0, 82.5), (21.5, 0.0, 76.0), (38.0, 0.0, 76.0), (58.0, 0.0, 76.0)], False, False))
    return a, zones, paths


# --- checks -----------------------------------------------------------------------------

def check(level):
    """Stops with a message on anything the plan gets wrong."""
    air = level.air
    anchors, zones, paths = named_places(level)
    ids = [i for i, *_ in anchors] + [i for i, *_ in zones] + [i for i, *_ in paths]
    if len(ids) != len(set(ids)):
        raise SystemExit(f"a named place is repeated: {sorted(i for i in ids if ids.count(i) > 1)}")
    # Every door joins two cells of air (a decor one has only its front).
    for door in DOORS:
        _, sides = door_cells(door)
        for a, b in sides:
            if door[5] == "decor":
                if (a[0], 0, a[1]) not in air:
                    raise SystemExit(f"door {door[0]} opens onto no air")
            elif (a[0], 0, a[1]) not in air or (b[0], 0, b[1]) not in air:
                raise SystemExit(f"door {door[0]} does not join two spaces at {a} and {b}")
    # Every prop stands in air; the cameras hang in the last layer of their own space, and none in the lockers.
    solid_props = props()
    for (x, y, z), block in solid_props.items():
        if (x, y, z) not in air:
            raise SystemExit(f"{block} at {(x, y, z)} is not in a space")
    for space, cameras in CAMERAS.items():
        if space in CAMERA_FREE:
            raise SystemExit(f"cameras in {space}, which has none")
        for x, z, _ in cameras:
            if air.get((x, SPACE_TOPS[space] - 1, z)) != space:
                raise SystemExit(f"a camera at {(x, z)} is not in {space}'s top layer of air")
    for space in SPACE_HEIGHTS:
        if space not in CAMERA_FREE and not CAMERAS.get(space):
            raise SystemExit(f"{space} has no camera")
    # Everyone stands on air with room over their head, except what is fixed to a wall.
    for anchor_id, (x, y, z), _, _ in anchors:
        if anchor_id.endswith("_block") or anchor_id.endswith("_src") or anchor_id.startswith("task1_tube_"):
            continue
        if anchor_id in ("time_clock", "office_keypad", "paper_medical", "paper_contract_14", "painting", "card_slide_start", "card_slide_end", "sealed_door",
                         "restricted_door_east", "aux_lab_door", "door_anchor", "aux_lab_reader", "aux_lab_computer", "rowan_computer",
                         "rowan_doorway", "sealed_reader", "basement_computer"):
            continue
        cell = (math.floor(x), math.floor(y), math.floor(z))
        if cell not in air or (cell[0], cell[1] + 1, cell[2]) not in air:
            raise SystemExit(f"{anchor_id} at {(x, y, z)} has no room to stand")
    for name, (x, z, _) in NPC_SPOTS.items():
        if (int(x), 0, int(z)) in solid_props or (int(x), 1, int(z)) in solid_props:
            raise SystemExit(f"{name} stands in a prop")
    for route, (points, _) in NPC_ROUTES.items():
        for x, z in points:
            if (int(x), 0, int(z)) in solid_props:
                raise SystemExit(f"{route} goes through a prop at {(x, z)}")
    for zone_id, (x0, z0, x1, z1, h, y0), _ in zones:
        if not any((x, y0, z) in air for x in range(x0, x1) for z in range(z0, z1)):
            raise SystemExit(f"zone {zone_id} has no air in it")
    for path_id, points, _, _ in paths:
        for x, y, z in points:
            if (math.floor(x), math.floor(y), math.floor(z)) not in air:
                raise SystemExit(f"path {path_id} goes through a wall at {(x, y, z)}")
    # Nobody walks into furniture: every patrol's and person's way, followed step by step, stays off the props that stand on the floor
    # (dirt and the like excepted); and nothing stands just in front of a door.
    flat = ("dirt_spot", "floor_line", "office_rug", "lounge_rug")
    floor_props = {(x, y, z) for (x, y, z), b in solid_props.items() if not any(f in b for f in flat)}
    for path_id, points, closed, _ in paths:
        if path_id.endswith("_dirt_spots"):
            continue
        pts = points + points[:1] if closed else points
        for (x0, y0, z0), (x1, y1, z1) in zip(pts, pts[1:]):
            steps = int(max(abs(x1 - x0), abs(z1 - z0)) * 4) + 1
            for k in range(steps + 1):
                x, z = x0 + (x1 - x0) * k / steps, z0 + (z1 - z0) * k / steps
                if (math.floor(x), math.floor(y0), math.floor(z)) in floor_props:
                    raise SystemExit(f"path {path_id} runs into a prop at {(round(x, 1), round(z, 1))}")
    for door in DOORS:
        if door[5] == "decor" or door[0] == "construction":         # the way under construction is barred on purpose
            continue
        for a, b in door_cells(door)[1]:
            for c in (a, b):
                if (c[0], 0, c[1]) in floor_props:
                    raise SystemExit(f"a prop stands in front of the door {door[0]} at {c}")
    # The doors' graph: every space is reachable from the entrance; by free doors only (normal ones) just the unrestricted
    # spaces are; and without passing a locked door everything but what lies behind one.
    graph = {s.id: set() for s in SPACES}
    for door in DOORS:
        if door[5] == "decor":
            continue
        _, sides = door_cells(door)
        a, b = sides[0]
        sa, sb = air[(a[0], 0, a[1])], air[(b[0], 0, b[1])]
        graph[sa].add((sb, door[5]))
        graph[sb].add((sa, door[5]))
    for sa, sb in LINKS:
        graph[sa].add((sb, "opening"))
        graph[sb].add((sa, "opening"))

    def reach(allowed):
        seen, queue = {"entrance"}, deque(["entrance"])
        while queue:
            for nxt, kind in graph[queue.popleft()]:
                if kind in allowed and nxt not in seen:
                    seen.add(nxt)
                    queue.append(nxt)
        return seen

    everything = {s.id for s in SPACES}
    if reach({"normal", "opening", "restricted", "locked"}) != everything:
        raise SystemExit(f"no way to {sorted(everything - reach({'normal', 'opening', 'restricted', 'locked'}))} from the entrance")
    free = reach({"normal", "opening"})
    if free != {s.id for s in SPACES if not s.restricted}:
        raise SystemExit(f"by free doors one reaches {sorted(free)}, not the unrestricted spaces")
    if everything - reach({"normal", "opening", "restricted"}) != LOCKED_SPACES:
        raise SystemExit(f"behind locked doors there is {sorted(everything - reach({'normal', 'opening', 'restricted'}))}, not {sorted(LOCKED_SPACES)}")
    check_chamber_hidden(level)
    for cell, _, key, *_ in WALL_TEXTS + WALL_MESSAGES:
        if cell not in air:
            raise SystemExit(f"the wall text {key} at {cell} is not in a space")
    return graph


def check_chamber_hidden(level):
    """Nobody in the ring can see into chamber 02 through the auxiliary lab's glass (Act 1 walks past it): every line from an eye in the
    ring's west stretch to a point of the cell's inside meets a wall (the partition), the doors being shut."""
    solid = {c for c, role in level.shell.items() if role != "glass"}
    solid |= {c for door in DOORS if door[5] != "opening" for c in door_cells(door)[0]}
    x0, z0, x1, z1 = CHAMBER
    targets = [(x + 0.5, y + 0.5, z + 0.5) for x in range(x0 + 1, x1) for z in range(z0 + 1, z1) for y in range(3)]
    eyes = [(x + 0.5, 1.6, z + 0.5) for x in range(16, 20) for z in range(48, 73)]
    for ex, ey, ez in eyes:
        for tx, ty, tz in targets:
            steps = int(math.dist((ex, ey, ez), (tx, ty, tz)) * 4) + 1
            if not any((math.floor(ex + (tx - ex) * k / steps), math.floor(ey + (ty - ey) * k / steps),
                        math.floor(ez + (tz - ez) * k / steps)) in solid for k in range(1, steps)):
                raise SystemExit(f"chamber 02 can be seen from the ring at {(ex, ez)} (towards {(tx, tz)})")
