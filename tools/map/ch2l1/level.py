"""
Chapter 2, Level 1: the factory. What is where (the spaces, the doors, the glass, the lights, the named places and the
patrol paths) and how it becomes blocks. Drawn by the author on paper (relative sizes); the measures here are that
drawing in blocks, with the 43 x 3 of the authorized corridor as the scale (about 16 px of the drawing a block), grown
after a first walk through the level (see SPACES).

Plan coordinates, as in the other levels: x east, z south (the entrance is at the north), y up from the floor of the air
(the floor's blocks are at y = -1). A space is a set of cells, each with the air above it up to its height; every cell next
to the air that is not air is the shell: a floor under it, a ceiling over it, a wall beside it with a darker first course.
Two spaces are told apart by a wall of one block. The look is the prologue's laboratory, all clean and modern: white
concrete, a light grey floor, a dark grey dado, glass and the ceiling lamps of the lab.

This file writes nothing: `generate.py` turns it into the plan the mod builds (`map.json`), the named places
(`anchors.json`) and a top-down picture. The mod puts the plan in the world at ORIGIN when Chapter 2 starts.
"""

import math
from collections import deque

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
    "glass": "minecraft:glass",
    "light": MOD + "lab_ceiling_light",
}
# Level 5's doors: the lab's white one with a tall window (a pair of leaves opens at once), and the grey emergency one that
# hands do not open.
DOOR_BLOCKS = {"normal": MOD + "factory_door", "locked": MOD + "emergency_door"}
DOOR_HEIGHT = 2
LIGHT_SPACING = 5

AROUND = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]


class Space:
    """A space: its cells (x, z), its height in blocks of air, and the boxes the lights are laid out over."""

    def __init__(self, sid, name, cells, height, boxes, restricted=False):
        self.id, self.name, self.cells, self.height, self.boxes, self.restricted = sid, name, set(cells), height, boxes, restricted

    @property
    def bounds(self):
        xs = [x for x, _ in self.cells]
        zs = [z for _, z in self.cells]
        return min(xs), min(zs), max(xs) + 1, max(zs) + 1


def rect(sid, name, x0, z0, x1, z1, height, restricted=False):
    """A rectangle of cells, x0 .. x1 - 1 and z0 .. z1 - 1."""
    return Space(sid, name, [(x, z) for x in range(x0, x1) for z in range(z0, z1)], height, [(x0, z0, x1, z1)], restricted)


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
# and the auxiliary lab 14 x 24 (was 6 x 20). The ring's west stretch (x 16 .. 19) lies along the auxiliary lab's glass.
SPACES = [
    arch("entrance", "ENTRANCE", 20, 62, 1, 10, 9),
    rect("main_corridor", "MAIN CORRIDOR", 20, 11, 31, 47, 5),
    rect("lab", "LAB", 32, 11, 62, 47, 6),
    rect("wc_west", "WC", 14, 11, 19, 16, 4),
    rect("lockers", "LOCKERS", 11, 17, 19, 28, 4),
    ring("ring", "RING", [(16, 48, 62, 52), (16, 69, 62, 73), (16, 52, 20, 69), (58, 52, 62, 69)], 4),
    rect("dining", "DINING ROOM", 21, 53, 57, 68, 6),
    rect("aux_lab", "AUXILIARY LAB", 1, 48, 15, 72, 5, restricted=True),
    rect("authorized", "AUTHORIZED CORRIDOR", 15, 74, 61, 78, 4, restricted=True),
    rect("security", "SECURITY", 15, 79, 28, 85, 4, restricted=True),
    rect("office", "OFFICE", 29, 79, 41, 86, 4, restricted=True),
    rect("wc_east", "WC", 42, 79, 48, 82, 4, restricted=True),
    rect("level2", "LEVEL 2 CORRIDOR", 63, 25, 74, 31, 4, restricted=True),
]

# The doors: (id, the wall's axis, the wall's line, first cell along it, width, kind). Axis "z": the wall runs along x at
# z = line, and the passage goes north to south; axis "x": the wall runs along z at x = line. Kinds: "normal" (a door of
# white steel with a window, opened by hand), "restricted" (the same, but the way into a forbidden zone: the drawing's red circle),
# "locked" (a grey emergency door that hands do not open: a later phase opens it, with a badge, a code or Rowan's card) and
# "decor" (the same grey door, opening onto nothing).
DOORS = [
    ("entrance_main", "z", 10, 24, 2, "normal"),
    ("entrance_lab_1", "z", 10, 34, 2, "normal"),
    ("entrance_lab_2", "z", 10, 47, 2, "normal"),
    ("main_lab_1", "x", 31, 16, 2, "normal"),
    ("main_lab_2", "x", 31, 42, 2, "normal"),
    ("wc_west", "x", 19, 13, 1, "normal"),
    ("lockers_1", "x", 19, 19, 2, "normal"),
    ("lockers_2", "x", 19, 25, 2, "normal"),
    ("main_ring", "z", 47, 21, 2, "normal"),
    ("lab_ring", "z", 47, 42, 2, "normal"),
    ("dining_n1", "z", 52, 29, 2, "normal"),
    ("dining_n2", "z", 52, 46, 2, "normal"),
    ("dining_s1", "z", 68, 25, 2, "normal"),
    ("dining_s2", "z", 68, 44, 2, "normal"),
    ("aux_lab", "x", 15, 49, 2, "locked"),
    ("ring_authorized", "z", 73, 34, 2, "restricted"),
    ("security", "z", 78, 21, 1, "normal"),
    ("office", "z", 78, 31, 2, "locked"),
    ("wc_east", "z", 78, 44, 1, "normal"),
    ("sealed", "x", 62, 26, 4, "locked"),
    ("restricted_east", "x", 61, 75, 1, "decor"),
]

# Walls of glass: (the wall's x, first z, last z, the top y): the auxiliary lab's, towards the ring's west stretch.
GLASS_WALLS = [(15, 52, 68, 3)]

# Solid columns inside a space: (x0, z0, x1, z1), the far corner excluded. The lockers' central one, 2 x 2.
PILLARS = [(14, 21, 16, 23)]

# What lies behind a locked door: the auxiliary lab (the coworker's badge), the office (Rowan's code) and the sealed zone.
LOCKED_SPACES = {"aux_lab", "office", "level2"}

CLOCK = {"at": (29, 1, 9), "facing": "north"}      # on the entrance's south wall, between its first two doors
CAMERA_FREE = {"lockers"}                          # the one space with no (decorative) security cameras


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
                for y in range(s.height):
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

    # --- lights -----------------------------------------------------------------------

    def lights(self):
        """[(x, y, z)] of the lamps, in the ceiling's layer, spread over each space's boxes."""
        out = set()
        for s in SPACES:
            for x0, z0, x1, z1 in s.boxes:
                for x in grid(x0, x1 - 1, LIGHT_SPACING):
                    for z in grid(z0, z1 - 1, LIGHT_SPACING):
                        if (x, z) in s.cells and self.shell.get((x, s.height, z)) == "ceiling":
                            out.add((x, s.height, z))
        return sorted(out)

    # --- the plan ---------------------------------------------------------------------

    def blocks(self):
        """{(x, y, z): block}: the shell with its lamps, the doors and the clock."""
        out = {c: BLOCKS[role] for c, role in self.shell.items()}
        for c in self.lights():
            out[c] = BLOCKS["light"]
        for door in DOORS:
            _, axis, line, first, width, kind = door
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
    add("task1_terminal", 35.5, 28.5, 270, True)
    add("task2_analyzer", 52.5, 35.5, 180, True)
    # Six people round the lockers' column (a 2 x 2 at x 14..15, z 21..22), looking at it.
    col = (15.0, 22.0)
    for i in range(6):
        ang = math.radians(60 * i + 30)
        x, z = col[0] + 2.7 * math.cos(ang), col[1] + 2.7 * math.sin(ang)
        x, z = math.floor(x) + 0.5, math.floor(z) + 0.5
        yaw = math.degrees(math.atan2(-(col[0] - x), col[1] - z))
        add(f"taquillas_npc_{i + 1:02d}", x, z, round(yaw), True)
    add("predecessor_locker", 11.5, 26.5, 270, True)
    add("taquillas_exit", 21.5, 22.5, -90, False)
    add("aux_lab_door", 15.0, 50.0, 90, False)
    add("chamber_02", 11.5, 60.5, -90, True)
    add("chamber_log", 2.5, 56.5, 90, True)
    add("authorization_desk", 2.5, 51.5, 90, True)
    add("door_anchor", 32.0, 78.0, 0, False)
    add("desk_anchor", 35.0, 82.5, 180, True)
    add("office_keypad", 30.5, 77.5, 0, True, y=1.0)
    add("paper_medical", 34.5, 82.5, 180, True, y=1.0)
    add("paper_contract_14", 35.5, 82.5, 180, True, y=1.0)
    add("painting", 40.5, 82.5, 90, True, y=2.0)
    add("card_slide_start", 35.0, 81.0, 0, True, y=1.0)
    add("card_slide_end", 35.0, 83.2, 0, True, y=1.0)
    add("rowan_spawn", 32.5, 76.5, 0, True)
    add("restricted_door_east", 61.0, 75.5, -90, False)
    add("sealed_door", 62.0, 28.0, -90, False)
    add("patrol_a_pause", 36.5, 70.5, 0, True)

    zones = []

    def zone(zone_id, space, provisional=False, box=None):
        x0, z0, x1, z1 = box or sp[space].bounds
        zones.append((zone_id, (x0, z0, x1, z1, sp[space].height if space else 4), provisional))

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
    zone("task3_sweep_area", "ring", True, box=(16, 52, 20, 69))
    zone("aux_lab", "aux_lab")
    zone("aux_lab_glass", "ring", True, box=(16, 52, 17, 69))
    zone("prologue_set", "aux_lab", True, box=(1, 64, 15, 72))
    zone("authorized_corridor", "authorized")
    zone("security_room", "security")
    zone("office", "office")
    zone("wc_east", "wc_east")
    zone("level2_corridor", "level2")
    zone("speaker_lab", "lab", True)
    zone("speaker_main", "main_corridor", True)
    zone("speaker_west", "ring", True, box=(16, 52, 20, 69))

    paths = []
    # The middle of the ring (its stretches are 4 wide: the middle is where two blocks meet) and of the authorized corridor.
    ring_pts = [(18.0, 50.0), (39.0, 50.0), (60.0, 50.0), (60.0, 60.0), (60.0, 71.0), (48.0, 71.0), (36.0, 71.0), (18.0, 71.0), (18.0, 60.0)]
    paths.append(("patrol_a_waypoints", [(x, 0.0, z) for x, z in ring_pts], True, True))
    paths.append(("patrol_b_waypoints", [(21.0, 0.0, 76.0), (38.0, 0.0, 76.0), (58.0, 0.0, 76.0)], False, True))
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
    # Everyone stands on air with room over their head, except what is fixed to a wall.
    for anchor_id, (x, y, z), _, _ in anchors:
        if anchor_id in ("time_clock", "office_keypad", "paper_medical", "paper_contract_14", "painting", "card_slide_start", "card_slide_end", "sealed_door", "restricted_door_east", "aux_lab_door", "door_anchor"):
            continue
        cell = (int(x), int(y), int(z))
        if cell not in air or (cell[0], cell[1] + 1, cell[2]) not in air:
            raise SystemExit(f"{anchor_id} at {(x, y, z)} has no room to stand")
    for zone_id, (x0, z0, x1, z1, h), _ in zones:
        if not any((x, 0, z) in air for x in range(x0, x1) for z in range(z0, z1)):
            raise SystemExit(f"zone {zone_id} has no air in it")
    for path_id, points, _, _ in paths:
        for x, y, z in points:
            if (int(x), 0, int(z)) not in air:
                raise SystemExit(f"path {path_id} goes through a wall at {(x, y, z)}")
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

    def reach(allowed):
        seen, queue = {"entrance"}, deque(["entrance"])
        while queue:
            for nxt, kind in graph[queue.popleft()]:
                if kind in allowed and nxt not in seen:
                    seen.add(nxt)
                    queue.append(nxt)
        return seen

    everything = {s.id for s in SPACES}
    if reach({"normal", "restricted", "locked"}) != everything:
        raise SystemExit(f"no way to {sorted(everything - reach({'normal', 'restricted', 'locked'}))} from the entrance")
    free = reach({"normal"})
    if free != {s.id for s in SPACES if not s.restricted}:
        raise SystemExit(f"by free doors one reaches {sorted(free)}, not the unrestricted spaces")
    if everything - reach({"normal", "restricted"}) != LOCKED_SPACES:
        raise SystemExit(f"behind locked doors there is {sorted(everything - reach({'normal', 'restricted'}))}, not {sorted(LOCKED_SPACES)}")
    return graph
