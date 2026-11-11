"""
Level 5 (B5): the laboratory of the Noname Complex, its three layers. Built from LEVEL_5 in
map_generator.py (what is where); this file is how: the spaces, what joins them, where they stand in the
world, and every block round the air.

Plan coordinates as in Level 4 (lounge/level.py): x east, z south, boxes (x0, z0, x1, z1) with x1 and
z1 exclusive, y relative to layer 1's floor blocks. The layers share the plan: layer 1, the upper floor,
stands on Level 4's floor; layer 2 lies LAYER_DROP under it, so a cell may hold one space of each; layer
3, the drain, lies DRAIN_DROP under layer 2, mostly beyond it to the east. One flight of stairs joins
layers 1 and 2; a ladder goes down from the chemical room's hatch to layer 3. A space may stand `rise`
blocks over its layer's floor (the director's office, up its steel stairs, and the corridor out of it).
The way in leaves Level 4's dark stretch through a steel frame in its side, by its far end; LEVEL_5's
PLACE_X and PLACE_Z turn the plan so that it does.

The blocks come in two sets. The way in (entry_blocks: the frame, a long dark corridor between the
shelves of Level 3's library, where the Cloud's game is played (CLOUD_GAME), the airlock of steel and its two
sliding doors) is built with Level 4, as the player walks it while the rest goes up. Crossing the frame is
walking into Level 5, though its checkpoint waits for the airlock, and its title shows as the airlock opens.
The airlock's door ahead, in the Lobby's wall, stays shut until the level is built (lab_blocks, in play:
LabHooks); the one behind stands open until the player is in.

Walls, floors and ceilings take the look of the space they face (STYLE_OF, STYLES): the lab's tiles on
layers 1 and 2, the drain's stained concrete on layer 3, steel up its stairs, the office's, the garage's
in the corridor out of the office (Level 1's own blocks, so it looks the same), black down the pit.
"""

import math
import random

from lab.furnish import Furnish

# Plan directions in the world: the plan's south is the world's west.
WORLD_FACING = {"north": "east", "south": "west", "east": "south", "west": "north"}
YAW = {"south": 0, "west": 90, "north": 180, "east": -90}
AROUND = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]
SIDES = [(0, -1, "north"), (0, 1, "south"), (1, 0, "east"), (-1, 0, "west")]


def grid(a, b, spacing):
    """Positions spread over a..b (inclusive) every `spacing`, centred; at least one."""
    length = b - a + 1
    n = max(1, (length + spacing // 2) // spacing)
    start = a + (length - (n - 1) * spacing - 1) // 2
    return [start + k * spacing for k in range(n)]


def stretch_air(lounge, p):
    """Plan cells (x, y, z) of Level 5's plan `p` holding the air of Level 4's dark stretch (`lounge`)."""
    sx0, sz0, sx1, sz1 = lounge.exit.box
    out = []
    for x in range(sx0, sx1):
        for z in range(sz0, sz1):
            wx, wz = lounge.world(x, z)
            out += [(wz - p["PLACE_Z"], y, p["PLACE_X"] - wx) for y in range(1, lounge.exit.height + 1)]
    return out


def box_cells(box):
    x0, z0, x1, z1 = box
    return [(x, z) for x in range(x0, x1) for z in range(z0, z1)]


class Space:
    def __init__(self, sid, zone, name, layer, box, height, kind, floor):
        self.id, self.zone, self.name, self.layer = sid, zone, name, layer
        self.box, self.height, self.kind, self.floor = box, height, kind, floor

    @property
    def size(self):
        return self.box[2] - self.box[0], self.box[3] - self.box[1]

    def cells(self):
        return box_cells(self.box)


class Opening:
    def __init__(self, oid, layer, axis, line, first, width, height, kind, floor):
        self.id, self.layer, self.axis, self.line = oid, layer, axis, line
        self.first, self.width, self.height, self.kind, self.floor = first, width, height, kind, floor

    def cells(self):
        ks = range(self.first, self.first + self.width)
        return [(self.line, k) if self.axis == "x" else (k, self.line) for k in ks]


# Corridors and stairs open onto whatever they touch; any other two spaces keep a wall between them.
OPEN_KINDS = ("corridor", "stairs", "entry", "climb")
# Built with Level 4: the way in.
ENTRY_KINDS = ("entry", "airlock")
ENTRY_OPENINGS = ("frame", "airlock_rear")
# Which look wins where a block faces spaces of two.
STYLE_ORDER = ("library", "lab", "office", "garage", "steel", "pit", "drain")


class Lab:
    WORLD_FACING = WORLD_FACING

    def __init__(self, p):
        self.p = dict(p)
        drop = p["LAYER_DROP"]
        self.layer_floor = {1: 0, 2: -drop, 3: -drop - p["DRAIN_DROP"]}

        def height(kind, h):
            if h is not None:
                return h
            return p["CORRIDOR_HEIGHT"] if kind in OPEN_KINDS else p["ROOM_HEIGHT"]

        self.spaces = [Space(sid, zone, name, layer, box, height(kind, h), kind, self.layer_floor[layer] + (rest[0] if rest else 0))
                       for sid, zone, name, layer, box, h, kind, *rest in p["SPACES"]]
        sid, zone, box = p["FLIGHT"]
        self.flight = Space(sid, zone, "", 1, box, p["FLIGHT_HEIGHT"], "stairs", 0)
        self.openings = [Opening(oid, layer, axis, line, first, width, h, kind, self.layer_floor[layer] + (rest[0] if rest else 0))
                         for oid, layer, axis, line, first, width, h, kind, *rest in p["OPENINGS"]]
        self.entry = [s for s in self.spaces if s.kind in ENTRY_KINDS]
        self.airlock = next(s for s in self.spaces if s.kind == "airlock")
        self.floors = self.carve()

    def carve(self):
        """(layer, x, z) of every cell a player can stand in -> its floor's y. The flight goes down one
        block per cell along z, from layer 1's floor to layer 2's; its cells count as layer 1's. A climb
        goes up one block per cell along x, from its space's floor on."""
        floors = {}
        drop = self.p["LAYER_DROP"]
        for s in self.spaces + [self.flight]:
            for x, z in s.cells():
                key = (s.layer, x, z)
                if key in floors:
                    raise SystemExit(f"Level 5: {s.id} overlaps another space of layer {s.layer} at {(x, z)}")
                if s is self.flight:
                    floors[key] = -min(z - s.box[1] + 1, drop)
                elif s.kind == "climb":
                    floors[key] = s.floor + x - s.box[0]
                else:
                    floors[key] = s.floor
        for o in self.openings:
            for x, z in o.cells():
                key = (o.layer, x, z)
                if key in floors:
                    raise SystemExit(f"Level 5: the opening {o.id} lies inside a space at {(x, z)}")
                floors[key] = o.floor
        return floors

    def world(self, x, z):
        """A plan cell's world x and z."""
        return self.p["PLACE_X"] - z, self.p["PLACE_Z"] + x

    def at(self, x, y, z):
        """A plan cell's world position."""
        wx, wz = self.world(x, z)
        return wx, self.p["FLOOR_Y"] + y, wz

    def height_at(self, layer, x, z):
        """How high the air is over a cell: its space's height, or its opening's."""
        for s in self.spaces + [self.flight]:
            x0, z0, x1, z1 = s.box
            if s.layer == layer and x0 <= x < x1 and z0 <= z < z1:
                return s.height
        for o in self.openings:
            if o.layer == layer and (x, z) in o.cells():
                return o.height
        raise KeyError((layer, x, z))

    def zone_ids(self):
        """The level's zones, in the plan's order, with the spaces each one holds."""
        out = {}
        for s in self.spaces + [self.flight]:
            out.setdefault(s.zone, []).append(s)
        return out

    def style(self, thing):
        """The look of a space's or an opening's surfaces."""
        return self.p["STYLE_OF"].get(thing.id, "lab" if thing.layer < 3 else "drain")

    # --- blocks ----------------------------------------------------------------------

    def carve_air(self, spaces):
        """(x, y, z) -> the floor's y under it, for the air of `spaces`."""
        air = {}
        for s in spaces:
            for x, z in s.cells():
                floor = self.floors[(s.layer, x, z)]
                for y in range(floor + 1, floor + s.height + 1):
                    air[(x, y, z)] = floor
        return air

    def opening_air(self, openings):
        air = {}
        for o in openings:
            for x, z in o.cells():
                for y in range(o.floor + 1, o.floor + o.height + 1):
                    air[(x, y, z)] = o.floor
        return air

    def shell_role(self, c, air):
        """A block next to the air: vinyl under it, a ceiling panel over it, and beside it a wall of
        white tiles with a band of green ones at its foot (and, in the garage's look, a painted stripe
        over that)."""
        x, y, z = c
        if (x, y + 1, z) in air:
            return "vinyl"
        beside = [air[(x + dx, y, z + dz)] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (x + dx, y, z + dz) in air]
        if beside:
            return "band" if y - max(beside) == 1 else "stripe" if y - max(beside) == 2 else "tile"
        if (x, y - 1, z) in air:
            return "ceiling"
        near = [dy for dx, dy, dz in AROUND if (x + dx, y + dy, z + dz) in air]
        return "vinyl" if max(near) > 0 else "ceiling" if min(near) < 0 else "tile"

    def state(self, role, style="lab"):
        if role == "checker":
            return self.p["CHECKER"]
        if style == "lab":
            return self.p["STAND_INS"]["tile" if role == "stripe" else role]
        looks = self.p["STYLES"][style]
        name = looks.get(role) or looks["tile"]
        return self.p["STAND_INS"].get(name, name)

    def shell_style(self, c, styles):
        """The look of a block next to the air: of the space over it, or the one beside it, or else under
        it; where several face it, the first in STYLE_ORDER."""
        x, y, z = c
        for ring in ([(0, 1, 0)], [(dx, 0, dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))], AROUND):
            found = {styles[(x + dx, y + dy, z + dz)] for dx, dy, dz in ring if (x + dx, y + dy, z + dz) in styles}
            if found:
                return min(found, key=STYLE_ORDER.index)
        return "lab"

    def airlock_door(self, o):
        """{world position: state} of an airlock door, shut: four columns, two leaves, each column sliding
        open towards its end of the opening (the same order as LabHooks: along the world axis the opening
        runs on, the lower half towards lower coordinates)."""
        cells = [self.at(x, y, z) for x, z in o.cells() for y in range(o.floor + 1, o.floor + o.height + 1)]
        along_x = len({c[0] for c in cells}) > 1
        coords = sorted({c[0] if along_x else c[2] for c in cells})
        out = {}
        for c in cells:
            low = coords.index(c[0] if along_x else c[2]) < len(coords) // 2
            facing = ("west" if low else "east") if along_x else ("north" if low else "south")
            out[c] = self.p["AIRLOCK_DOOR"].format(facing=facing, width=4)
        return out

    def door(self, o, block, outside):
        """{world position: state} of a door in a one-cell opening, shut, facing `outside` (plan)."""
        (x, z), = o.cells()
        base = f"{block}[facing={WORLD_FACING[outside]},hinge=left,open=false,powered=false"
        return {self.at(x, o.floor + 1, z): base + ",half=lower]", self.at(x, o.floor + 2, z): base + ",half=upper]"}

    def double_door(self, o, block, outside):
        """{world position: state} of a pair of doors in a two-cell opening, shut, facing `outside` (plan), each
        hinged at its end of the opening (the one on the left, as seen looking `outside`, on its left)."""
        lx, lz = {"north": (-1, 0), "south": (1, 0), "east": (0, -1), "west": (0, 1)}[outside]
        cells = sorted(o.cells(), key=lambda c: c[0] * lx + c[1] * lz)
        out = {}
        for (x, z), hinge in zip(cells, ("right", "left")):
            base = f"{block}[facing={WORLD_FACING[outside]},hinge={hinge},open=false,powered=false"
            out[self.at(x, o.floor + 1, z)] = base + ",half=lower]"
            out[self.at(x, o.floor + 2, z)] = base + ",half=upper]"
        return out

    def compose(self, foreign_air=()):
        """The blocks of both sets, in world positions. `foreign_air`: plan cells (x, y, z) of the
        air of Level 4's dark stretch that the way in opens onto, so that no wall goes up across it."""
        p = self.p
        rooms = [s for s in self.spaces if s.kind not in ENTRY_KINDS]
        entry_air = self.carve_air(self.entry)
        entry_air.update(self.opening_air([o for o in self.openings if o.kind in ENTRY_OPENINGS]))
        front = [o for o in self.openings if o.kind == "airlock_front"]
        front_air = self.opening_air(front)
        styles = {}
        for s in rooms + [self.flight] + [s for s in self.entry if s.kind == "entry"]:
            look = self.style(s)
            styles.update({c: look for c in self.carve_air([s])})
        for o in self.openings:
            look = self.style(o)
            styles.update({c: look for c in self.opening_air([o])})
        lab_air = self.carve_air(rooms + [self.flight])
        lab_air.update(self.opening_air([o for o in self.openings if o.kind not in ENTRY_OPENINGS + ("airlock_front",)]))
        drain = self.drain_air()
        lab_air.update(drain)
        styles.update(self.spiral_styles)
        for c in drain:
            styles.setdefault(c, "pit" if c in self.pit_air else "steel" if c in self.shaft_air else "drain")
        air = {**entry_air, **front_air, **lab_air}
        airlock_air = self.carve_air([self.airlock])
        airlock_air.update(self.opening_air([o for o in self.openings if o.kind in ("airlock_rear", "airlock_front")]))
        outside = set(foreign_air)
        roles = {}
        for (x, y, z) in air:
            for dx, dy, dz in AROUND:
                c = (x + dx, y + dy, z + dz)
                if c in air or c in roles or c in outside:
                    continue
                roles[c] = self.shell_role(c, air)

        def near(c, cells):
            return any((c[0] + dx, c[1] + dy, c[2] + dz) in cells for dx, dy, dz in AROUND)

        # The airlock is steel all round, on a checker plate floor, and so are the frames of its doors.
        for c in roles:
            if near(c, airlock_air):
                roles[c] = "checker" if (c[0], c[1] + 1, c[2]) in air else "steel"
        # The frame in the stretch's side: steel jambs and lintel, a checker plate threshold.
        for o in self.openings:
            if o.kind != "frame":
                continue
            cells = o.cells()
            ends = [cells[0], cells[-1]]
            step = (0, 1) if o.axis == "x" else (1, 0)
            jambs = [(ends[0][0] - step[0], ends[0][1] - step[1]), (ends[1][0] + step[0], ends[1][1] + step[1])]
            for x, z in jambs:
                for y in range(o.floor + 1, o.floor + o.height + 2):
                    roles[(x, y, z)] = "steel"
            for x, z in cells:
                roles[(x, o.floor + o.height + 1, z)] = "steel"
                roles[(x, o.floor, z)] = "checker"

        def shell(c, role):
            return self.state(role, "lab" if role in ("steel", "checker") else self.shell_style(c, styles))

        # Next to the way in's air: built with Level 4, its air too (a build over an older one leaves nothing of it).
        # The rest with the level, in play, and the walls between the two in both: a level built without Level 4 (a
        # game continued in it) still has its Lobby whole.
        self.entry_blocks = {self.at(*c): "minecraft:air" for c in entry_air if c not in outside}
        self.entry_blocks.update({self.at(*c): shell(c, r) for c, r in roles.items() if near(c, entry_air)})
        # The level's own air goes in too: a build over an older one puts everything back as new.
        self.lab_blocks = {self.at(*c): "minecraft:air" for c in lab_air}
        self.lab_blocks.update({self.at(*c): shell(c, r) for c, r in roles.items() if not near(c, entry_air) or near(c, lab_air)})
        self.entry_blocks.update(self.shelves())
        # The airlock's door ahead stands shut in both sets: before the level, it is the Lobby's wall.
        for o in front:
            self.entry_blocks.update(self.airlock_door(o))
            self.lab_blocks.update(self.airlock_door(o))

        for o in self.openings:
            if o.kind == "locked":
                self.lab_blocks.update(self.airlock_door(o))
            elif o.kind == "grate":
                ew, ns = ("false", "true") if o.axis == "z" else ("true", "false")
                for x, z in o.cells():
                    for y in range(o.floor + 1, o.floor + o.height + 1):
                        self.lab_blocks[self.at(x, y, z)] = p["GRATE"].format(ew=ew, ns=ns)
            elif o.kind == "door":
                # The office's doors, opened by hand; each looks the way one comes to it from.
                self.lab_blocks.update(self.door(o, p["OFFICE_DOOR"], p["DOOR_OUTSIDE"][o.id]))
            elif o.kind == "director_door":
                # The director's: a pair of dark wood doors, both leaves opening at once (the block opens its partner).
                self.lab_blocks.update(self.double_door(o, p["DIRECTOR_DOOR"], p["DOOR_OUTSIDE"][o.id]))
            elif o.kind == "lab_door":
                # Layer 1's rooms: a pair of white doors with a window, opened by hand.
                self.lab_blocks.update(self.double_door(o, p["LAB_DOOR"], p["DOOR_OUTSIDE"][o.id]))
            elif o.kind == "exit":
                # At the end of the corridor out of the office, Level 1's locked EXIT door, its sign over it.
                outside = p["DOOR_OUTSIDE"][o.id]
                self.lab_blocks.update(self.door(o, p["EXIT_DOOR"], outside))
                (x, z), = o.cells()
                dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[outside]
                self.lab_blocks[self.at(x + dx, o.floor + 3, z + dz)] = f"{p['EXIT_SIGN']}[facing={WORLD_FACING[outside]}]"
        self.lights = []
        self.light_rooms(rooms)
        # The test chambers' windows: glass from the second block up to the corridor's ceiling, in steel (a sill,
        # a lintel, a post at each end).
        for layer, axis, line, first, last in p["WINDOWS"]:
            floor = self.layer_floor[layer]
            top = floor + p["CORRIDOR_HEIGHT"] + 1
            for k in range(first - 1, last + 2):
                x, z = (line, k) if axis == "x" else (k, line)
                for y in range(floor + 1, top + 1):
                    edge = k in (first - 1, last + 1) or y in (floor + 1, top)
                    self.lab_blocks[self.at(x, y, z)] = self.state("steel") if edge else p["GLASS"]
        # The levers on their walls.
        for _, layer, x, z, facing in p["LEVERS"]:
            self.lab_blocks[self.at(x, self.layer_floor[layer] + 2, z)] = p["LEVER"].format(facing=WORLD_FACING[facing])
        self.lay_drain()
        # The furniture and what lies about (furnish.py).
        levers = {(x, self.layer_floor[layer] + 2, z) for _, layer, x, z, _ in p["LEVERS"]}
        self.furnish = Furnish(self, lab_air, levers | set(self.drain_taken) | set(self.lights)).lay()
        self.lab_blocks.update(self.furnish.states())
        self.lab_blocks.update(self.furnish.pit())
        self.grime()
        # The flight: a stair on each cell, climbing towards the plan's north; the climb's towards the east.
        stairs = f"{self.state('stairs')}[facing={WORLD_FACING['north']},half=bottom,shape=straight,waterlogged=false]"
        for x, z in self.flight.cells():
            self.lab_blocks[self.at(x, self.floors[(1, x, z)] + 1, z)] = stairs
        steel = f"{p['STAND_INS']['steel_stairs']}[facing={WORLD_FACING['east']},half=bottom,shape=straight,waterlogged=false]"
        for s in self.spaces:
            if s.kind == "climb":
                for x, z in s.cells():
                    self.lab_blocks[self.at(x, self.floors[(s.layer, x, z)] + 1, z)] = steel
        self.entry_air = [self.at(*c) for c in entry_air]
        self.box = self.bounds({**self.lab_blocks, **self.entry_blocks})
        self.lab_box = self.bounds(self.lab_blocks)
        return self

    def shelves(self):
        """{world position: state} of the bookshelves along both long walls of the entry corridor, facing in,
        SHELF_HEIGHT high: its outermost rows of cells, all the way."""
        p = self.p
        out = {}
        for s in self.entry:
            if s.kind != "entry":
                continue
            x0, z0, x1, z1 = s.box
            h = p["SHELF_HEIGHT"]
            for z, facing in ((z0, "south"), (z1 - 1, "north")):
                for x in range(x0, x1):
                    for y in range(s.floor + 1, s.floor + h + 1):
                        state = p["BOOKSHELF"].format(facing=WORLD_FACING[facing], up=str(y < s.floor + h).lower(),
                                                      down=str(y > s.floor + 1).lower())
                        out[self.at(x, y, z)] = state
        return out

    # --- the drain (layer 3) ---------------------------------------------------------

    def drain_air(self):
        """(x, y, z) -> floor y of the air layer 3 adds round its spaces: the shaft of the ladder up to the
        hatch, the channels down the tunnels, the pools under the catwalks, the pit under the end of the false
        exit's corridor. Kept apart too (self.shaft_air, channel_cells, pools, pit_air), for their blocks."""
        p = self.p
        air = {}
        f3 = self.layer_floor[3]
        layer, x, z, _ = p["LADDER"]
        room = next(s for s in self.spaces if s.layer == layer and (x, z) in s.cells())
        self.shaft_air = {(x, y, z): room.floor for y in range(room.floor + room.height + 1, self.layer_floor[2])}
        air.update(self.shaft_air)
        self.channel_cells = set()
        self.wet_air = set()                # the channels' and pools' cells, down in the floor: in their space's zone
        for x0, z0, x1, z1 in p["CHANNELS"]:
            for cx in range(min(x0, x1), max(x0, x1) + 1):
                for cz in range(min(z0, z1), max(z0, z1) + 1):
                    if self.floors.get((3, cx, cz)) != f3:
                        raise SystemExit(f"Level 5: the channel cell {(cx, cz)} is not on layer 3's floor")
                    self.channel_cells.add((cx, cz))
                    air[(cx, f3, cz)] = f3 - 1
                    self.wet_air.add((cx, f3, cz))
        self.pools = []
        for pid, box, depth, walks, railed in p["POOLS"]:
            cells = set(box_cells(box))
            decks = {c for w in walks for c in box_cells(w)} & cells
            for cx, cz in cells:
                if self.floors.get((3, cx, cz)) != f3:
                    raise SystemExit(f"Level 5: the pool {pid} runs off layer 3's floor at {(cx, cz)}")
                for y in range(f3 - depth, f3 + 1):
                    air[(cx, y, cz)] = f3 - depth - 1
                    self.wet_air.add((cx, y, cz))
            self.pools.append((pid, cells, decks, depth, railed))
        air.update(self.spiral_air())
        box, depth = p["PIT"]
        self.pit_cells = box_cells(box)
        pit_floor = self.floors[(3, *self.pit_cells[0])]
        self.pit_floor, self.pit_depth = pit_floor, depth
        self.pit_air = {(cx, y, cz): pit_floor - depth - 1 for cx, cz in self.pit_cells for y in range(pit_floor - depth, pit_floor)}
        air.update(self.pit_air)
        return air

    def spiral_air(self):
        """(x, y, z) -> floor y of the spiral stairs' shaft (SPIRAL): round, from layer 3's floor up to `rise` over it and
        a room's height more; its upper part counts the top's floor as its own (the skirting's band). Kept apart too
        (self.spiral_cells, self.spiral_styles: the drain's look low down, steel above, the office's at the top)."""
        sp, f3 = self.p["SPIRAL"], self.layer_floor[3]
        cx, cz = sp["centre"]
        top = f3 + sp["rise"]
        self.spiral_cells = {(x, z) for x in range(cx - 5, cx + 6) for z in range(cz - 5, cz + 6) if math.hypot(x - cx, z - cz) < 4.5}
        air, self.spiral_styles = {}, {}
        for x, z in self.spiral_cells:
            for y in range(f3 + 1, top + self.p["ROOM_HEIGHT"]):
                air[(x, y, z)] = f3 if y <= top else top
                self.spiral_styles[(x, y, z)] = "drain" if y <= f3 + sp["drain_up_to"] else "steel" if y <= top else "office"
        return air

    def spiral_link(self):
        """(layer, x, z) of the cell at the spiral stairs' foot (the tunnel's last) -> the one at their top (the doorway
        out): the walk's way up them (checks.py)."""
        sp = self.p["SPIRAL"]
        cx, cz = sp["centre"]
        dx, dz = {"west": (-1, 0), "east": (1, 0), "north": (0, -1), "south": (0, 1)}[sp["entry"]]
        return {(3, cx + 5 * dx, cz + 5 * dz): (3, cx - 5 * dx, cz - 5 * dz)}

    def lay_spiral(self):
        """The spiral stairs (SPIRAL): treads of grating round an open well, half a block a step, `turns` turns from the
        tunnel's end on its `entry` side up to the doorway on its far side; iron bars two high along the well; at the top,
        a landing of grating over the half the treads no longer reach. A caged lamp flickers on the wall half way up, a
        panel light hangs over the well at the top. Returns the plan cells (x, y, z) it holds."""
        p, sp, f3 = self.p, self.p["SPIRAL"], self.layer_floor[3]
        cx, cz = sp["centre"]
        turns, rise = sp["turns"], sp["rise"]
        top = f3 + rise
        well = {(x, z) for x, z in self.spiral_cells if abs(x - cx) <= 1 and abs(z - cz) <= 1}
        railing = {(x, z) for x, z in self.spiral_cells if (x, z) not in well and max(abs(x - cx), abs(z - cz)) <= 2}
        ring = self.spiral_cells - well
        start = {"west": math.pi, "east": 0.0, "north": -math.pi / 2, "south": math.pi / 2}[sp["entry"]]

        def share(x, z):
            return ((math.atan2(z - cz, x - cx) - start) / (2 * math.pi)) % 1.0

        def surface(u):
            """The tread's surface `u` turns up from the bottom, in half blocks."""
            return 2 * (f3 + 1) + round(u / turns * rise * 2)

        taken, bars = set(), set()
        grating = p["CATWALK"].replace("type=top", "type={half}")
        for x, z in ring:
            for t in range(int(math.ceil(turns)) + 1):
                u = t + share(x, z)
                if u > turns:
                    if t < turns:
                        # Its last tread fell short of the top: the landing over it, level with the doorway out.
                        self.lab_blocks[self.at(x, top, z)] = grating.format(half="top")
                        taken.add((x, top, z))
                        if (x, z) in railing:
                            bars |= {(x, top + 1, z), (x, top + 2, z)}
                    break
                h = surface(u)
                if h <= 2 * (f3 + 1):
                    continue                          # the first steps are the floor
                block_y, half = divmod(h, 2)
                at = (x, block_y, z) if half else (x, block_y - 1, z)
                self.lab_blocks[self.at(*at)] = grating.format(half="bottom" if half else "top")
                taken.add(at)
                if (x, z) in railing:
                    bars |= {(x, block_y + half, z), (x, block_y + half + 1, z)}
        offsets = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
        for x, y, z in bars:
            if (x, y, z) in taken:
                continue
            joined = {side: (x + ox, y, z + oz) in bars for side, (ox, oz) in offsets.items()}
            sides = {WORLD_FACING[k]: str(v).lower() for k, v in joined.items()}
            self.lab_blocks[self.at(x, y, z)] = p["SPIRAL_BARS"].format(**sides)
            taken.add((x, y, z))
        # The lamp: on the outer wall where the treads are half way up, two blocks over them, facing the well.
        u = turns / 2
        angle = start + 2 * math.pi * (u % 1.0)
        best = None
        for x, z in ring - railing:
            if all((x + ox, z + oz) in self.spiral_cells for ox, oz in offsets.values()):
                continue                              # not against the wall
            off = abs(math.remainder(math.atan2(z - cz, x - cx) - angle, 2 * math.pi))
            if best is None or off < best[0]:
                best = (off, x, z)
        _, lx, lz = best
        vx, vz = cx - lx, cz - lz
        facing = ("east" if vx > 0 else "west") if abs(vx) >= abs(vz) else ("south" if vz > 0 else "north")
        c = (lx, surface(u) // 2 + 2, lz)
        self.lab_blocks[self.at(*c)] = p["LAMP"].format(facing=WORLD_FACING[facing], mode="flicker", lit="true")
        self.lights.append(c)
        taken.add(c)
        c = (cx, top + p["ROOM_HEIGHT"] - 1, cz)
        self.lab_blocks[self.at(*c)] = p["PANEL_LIGHT"]
        self.lights.append(c)
        taken.add(c)
        return taken

    def lay_drain(self):
        """Layer 3's blocks in its air: the channels' water, the pools' water and catwalks (railed or not),
        the ladder, the collapse across the north tunnel, the pipes along the tunnels. self.drain_taken: the
        plan cells (x, y, z) they hold, kept from the furniture."""
        p = self.p
        f3 = self.layer_floor[3]
        taken = set()
        water = "minecraft:water[level=0]"
        for cx, cz in self.channel_cells:
            self.lab_blocks[self.at(cx, f3, cz)] = water
        self.pool_water = set()
        for pid, cells, decks, depth, railed in self.pools:
            for cx, cz in cells:
                for y in range(f3 - depth, f3):
                    self.lab_blocks[self.at(cx, y, cz)] = water
                    self.pool_water.add((cx, y, cz))
                if (cx, cz) in decks:
                    self.lab_blocks[self.at(cx, f3, cz)] = p["CATWALK"]
                    taken.add((cx, f3, cz))
            if not railed:
                continue
            for cx, cz in decks:
                sides = {name: (cx + dx, cz + dz) in cells and (cx + dx, cz + dz) not in decks for dx, dz, name in SIDES}
                if any(sides.values()):
                    # The rail's sides are the world's: the plan's north is the world's east.
                    world = {WORLD_FACING[k]: str(v).lower() for k, v in sides.items()}
                    self.lab_blocks[self.at(cx, f3 + 1, cz)] = p["RAIL"].format(**world)
                    taken.add((cx, f3 + 1, cz))
        layer, x, z, facing = p["LADDER"]
        for y in range(self.layer_floor[layer] + 1, self.layer_floor[2]):
            self.lab_blocks[self.at(x, y, z)] = p["LADDER_BLOCK"].format(facing=WORLD_FACING[facing])
            taken.add((x, y, z))
        # The collapse: concrete and rubble from wall to wall and floor to ceiling in its middle, its two faces
        # slumping, higher here and lower there.
        x0, z0, x1, z1 = p["COLLAPSE"]
        rng = random.Random("level5-collapse")
        self.collapse_cells = set()
        for cx in range(x0, x1):
            face = cx in (x0, x1 - 1)
            for cz in range(z0, z1):
                self.collapse_cells.add((cx, cz))
                h = self.height_at(3, cx, cz)
                top = rng.randint(2, h - 1) if face else h
                for y in range(f3 + 1, f3 + top + 1):
                    self.lab_blocks[self.at(cx, y, cz)] = self.state("rubble", "drain")
                    taken.add((cx, y, cz))
                if face:
                    self.lab_blocks[self.at(cx, f3 + top + 1, cz)] = f"{self.state('rubble_slab', 'drain')}[type=bottom,waterlogged=false]"
                    taken.add((cx, f3 + top + 1, cz))
        taken |= self.lay_spiral()
        taken |= self.pipes(taken)
        self.drain_taken = taken

    def pipes(self, taken):
        """Rusty pipes down every tunnel of the drain: one under the ceiling against a wall, one lower against
        the other; each run joins its cells and goes into the wall at an end that has one."""
        p = self.p
        runs = []
        for s in self.spaces:
            if s.layer != 3 or s.kind != "corridor" or self.style(s) != "drain":
                continue
            x0, z0, x1, z1 = s.box
            along_x = (x1 - x0) >= (z1 - z0)
            top, low = s.floor + s.height, s.floor + s.height - 1
            if along_x:
                runs.append([(x, top, z0) for x in range(x0, x1)])
                runs.append([(x, low, z1 - 1) for x in range(x0, x1)])
            else:
                runs.append([(x0, top, z) for z in range(z0, z1)])
                runs.append([(x1 - 1, low, z) for z in range(z0, z1)])
        joins = {}
        for run in runs:
            run = [c for c in run if c not in taken and (c[0], c[2]) not in self.collapse_cells]
            for a, b in zip(run, run[1:]):
                if abs(a[0] - b[0]) + abs(a[2] - b[2]) != 1:
                    continue
                joins.setdefault(a, set()).add(b)
                joins.setdefault(b, set()).add(a)
            if len(run) > 1:
                for end, prev in ((run[0], run[1]), (run[-1], run[-2])):
                    wall = (2 * end[0] - prev[0], end[1], 2 * end[2] - prev[2])
                    if self.lab_blocks.get(self.at(*wall), "minecraft:air") != "minecraft:air":
                        joins.setdefault(end, set()).add(wall)
            for c in run:
                joins.setdefault(c, set())
        for (x, y, z), others in joins.items():
            wx, wy, wz = self.at(x, y, z)
            flags = {"north": False, "south": False, "east": False, "west": False, "up": False, "down": False}
            for ox, oy, oz in others:
                owx, owy, owz = self.at(ox, oy, oz)
                flags[{(0, 0, -1): "north", (0, 0, 1): "south", (1, 0, 0): "east", (-1, 0, 0): "west",
                       (0, 1, 0): "up", (0, -1, 0): "down"}[(owx - wx, owy - wy, owz - wz)]] = True
            self.lab_blocks[(wx, wy, wz)] = p["PIPE"] + "[" + ",".join(f"{k}={str(v).lower()}" for k, v in sorted(flags.items())) + "]"
        return set(joins)

    def light_rooms(self, rooms):
        """Every room and corridor's lights. The lab's: in a grid, all on (none on the way in: it stays dark until
        the airlock opens onto the Lobby); on layer 1 set into its ceiling of panels, silent, an air vent beside every
        other one (none in the niches), on layer 2 fluorescent lights hanging under it. The drain's: caged lamps, dim and far apart,
        some flickering and some dead, down the middle of the tunnels and on the walls of its rooms (none in the
        Hideout). Steel stairs, the office and the garage's corridor: lit, all on."""
        p = self.p
        rng = random.Random("level5-drain-lights")

        def lamp(c, facing, mode=None):
            if mode is None:
                roll = rng.random()
                mode = "off" if roll < p["LAMP_DEAD"] else "flicker" if roll < p["LAMP_DEAD"] + p["LAMP_FLICKER"] else "on"
            self.lights.append(c)
            lit = "false" if mode == "off" else "true"
            self.lab_blocks[self.at(*c)] = p["LAMP"].format(facing=facing, mode=mode, lit=lit)

        for s in rooms:
            x0, z0, x1, z1 = s.box
            look = self.style(s)
            axis = "z" if (x1 - x0) >= (z1 - z0) else "x"         # along the space: the plan's x is the world's z
            if look in ("lab", "garage", "steel"):
                if s.kind == "climb":
                    cells = [(x, (z0 + z1) // 2) for x in (x0 + 2, x1 - 3)]
                elif look == "lab":
                    cells = [(x, z) for x in grid(x0, x1 - 1, p["LIGHT_SPACING"]) for z in grid(z0, z1 - 1, p["LIGHT_SPACING"])]
                elif look == "garage":
                    cells = [(x, z) for x in grid(x0, x1 - 1, p["GARAGE_LIGHT_SPACING"]) for z in grid(z0, z1 - 1, p["GARAGE_LIGHT_SPACING"])]
                else:
                    cells = []
                if look == "lab" and s.layer == 1:
                    if s.id.startswith("niche"):
                        continue
                    lights = set(cells)
                    along_x = (x1 - x0) >= (z1 - z0)
                    for k, (x, z) in enumerate(sorted(cells)):
                        c = (x, self.floors[(s.layer, x, z)] + s.height + 1, z)
                        self.lights.append(c)
                        self.lab_blocks[self.at(*c)] = p["CEILING_LIGHT"]
                        vx, vz = (x + p["LIGHT_SPACING"] // 2, z) if along_x else (x, z + p["LIGHT_SPACING"] // 2)
                        if k % 2 == 0 and x0 <= vx < x1 and z0 <= vz < z1 and (vx, vz) not in lights:
                            self.lab_blocks[self.at(vx, self.floors[(s.layer, vx, vz)] + s.height + 1, vz)] = p["CEILING_VENT"]
                    continue
                for x, z in cells:
                    c = (x, self.floors[(s.layer, x, z)] + s.height, z)
                    self.lights.append(c)
                    self.lab_blocks[self.at(*c)] = p["LIGHT"].format(axis=axis)
            elif look == "office":
                for x in grid(x0, x1 - 1, p["OFFICE_LIGHT_SPACING"]):
                    for z in grid(z0, z1 - 1, p["OFFICE_LIGHT_SPACING"]):
                        c = (x, s.floor + s.height, z)
                        self.lights.append(c)
                        self.lab_blocks[self.at(*c)] = p["PANEL_LIGHT"]
            elif s.kind == "corridor":
                # Hanging over the channel, down the tunnel's middle.
                mid = (z0 + z1) // 2 if axis == "z" else (x0 + x1) // 2
                for k in grid(x0 if axis == "z" else z0, (x1 if axis == "z" else z1) - 1, p["LAMP_SPACING"]):
                    x, z = (k, mid) if axis == "z" else (mid, k)
                    lamp((x, s.floor + s.height, z), "down")
            elif s.id in p["LAMP_ROOMS"]:
                # On the walls, high, facing into the room (at most `most` of them); in a room lit all over, from
                # the ceiling instead. A mode of None: drawn like the tunnels'.
                how, spacing, mode, most = p["LAMP_ROOMS"][s.id]
                if how == "ceiling":
                    for x in grid(x0, x1 - 1, spacing):
                        for z in grid(z0, z1 - 1, spacing):
                            lamp((x, s.floor + s.height, z), "down", mode)
                    continue
                y = s.floor + min(4, s.height)
                ring = [(x, z0, "north") for x in grid(x0, x1 - 1, spacing)] + [(x, z1 - 1, "south") for x in grid(x0, x1 - 1, spacing)]
                ring += [(x0, z, "west") for z in grid(z0, z1 - 1, spacing)] + [(x1 - 1, z, "east") for z in grid(z0, z1 - 1, spacing)]
                placed = 0
                for x, z, wall in ring:
                    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[wall]
                    if self.floors.get((s.layer, x + dx, z + dz)) is not None or placed >= most:
                        continue                                    # a doorway or a tunnel's mouth, not a wall
                    away = {"north": "south", "south": "north", "east": "west", "west": "east"}[wall]
                    lamp((x, y, z), WORLD_FACING[away], mode)
                    placed += 1

    def grime(self):
        """The drain's dirt: puddles on the tunnels' walkways, stains low on their walls."""
        p = self.p
        rng = random.Random("level5-grime")
        f3 = self.layer_floor[3]
        for s in self.spaces:
            if s.layer != 3 or self.style(s) != "drain":
                continue
            for x, z in s.cells():
                if (x, z) in self.channel_cells or self.floors[(3, x, z)] != f3 or any((x, z) in cells for _, cells, *_ in self.pools):
                    continue
                pos = self.at(x, f3 + 1, z)
                if self.lab_blocks.get(pos) != "minecraft:air":
                    continue
                if rng.random() < p["PUDDLE_SHARE"]:
                    self.lab_blocks[pos] = f"{p['PUDDLE']}[pattern={rng.randrange(4)}]"
                    continue
                for dx, dz, wall in SIDES:
                    if (3, x + dx, z + dz) in self.floors or (x + dx, z + dz) in self.spiral_cells or rng.random() >= p["STAIN_SHARE"]:
                        continue
                    away = {"north": "south", "south": "north", "east": "west", "west": "east"}[wall]
                    self.lab_blocks[pos] = f"{p['STAIN']}[facing={WORLD_FACING[away]},pattern={rng.randrange(10)}]"
                    break

    @staticmethod
    def bounds(blocks):
        xs, ys, zs = zip(*blocks)
        return min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)

    def world_box(self):
        return self.box

    # --- the level file ----------------------------------------------------------------

    def cells_zone(self, cells, y0, y1):
        """The world box (inclusive) over plan cells, from y0 to y1 over layer 1's floor."""
        corners = [self.world(x, z) for x, z in cells]
        xs, zs = [c[0] for c in corners], [c[1] for c in corners]
        y = self.p["FLOOR_Y"]
        return min(xs), y + y0, min(zs), max(xs), y + y1, max(zs)

    def space_zone(self, s):
        """A space's zone, from its floor up, and down into its water (a channel, a pool: a player in it is still there)."""
        floors = [self.floors[(s.layer, x, z)] for x, z in s.cells()]
        cells = set(s.cells())
        low = min([min(floors) + 1] + [y for x, y, z in self.wet_air if (x, z) in cells])
        return self.cells_zone(s.cells(), low, max(floors) + s.height)

    # Zone tags by kind of space or opening: the airlock's (LabHooks), the locked doors (LabDoorHooks), the
    # restart points (ContinueGame: the Lab hall's, the Junction's).
    # The Distortion Clouds' zones (DistortionHooks; `cloud_often`: more often), the Lab hall's first Cloud
    # (LabHallHooks), the chemical puzzle's room, grate and hatch (ChemicalHooks), the foot of the ladder down
    # from the hatch (DrainHooks), the pools' rooms (DeepWaterHooks: falling in drowns), the director's office and
    # the false exit with its pit (the boss's scene).
    # The entry corridor (the Cloud's game, CloudGameHooks, in its zone `cloud_game`): the battery dead, no sprinting, no
    # checkpoint yet, and a death there goes back to Level 4's key (ResourceHooks, ContinueGame, DeathHooks); its frame
    # holds no checkpoint either.
    TAGS = {"entry": ["dead_battery", "no_sprint", "no_checkpoint", "back_to_key"], "frame": ["no_checkpoint", "back_to_key"],
            "airlock": ["lab_airlock"], "airlock_rear": ["lab_airlock_rear"], "airlock_front": ["lab_welcome"],
            "locked": ["lab_door"], "hub": ["restart_point", "cloud", "first_cloud"], "north_route": ["cloud"],
            "south_route": ["cloud"], "puzzle": ["chemical_room"], "grate": ["lab_grate"], "hatch": ["lab_drain"],
            "drain_ladder": ["drain_bottom"], "drain_tunnels": ["cloud", "cloud_often"],
            "junction": ["restart_point", "cloud", "cloud_often", "deep_water"], "cistern": ["deep_water"],
            "director_office": ["director_office"],
            "false_exit": ["false_exit"]}

    def zones(self):
        """(id, world box inclusive, tags) of Level 5's file: every space (down into its water) and every opening, so
        that going through a door never leaves the level, the ladder's shaft and the pit. A zone of several boxes gives its id to
        each (the corridors round the rooms, the stairs, the routes, the tunnels)."""
        out = [(s.zone, self.space_zone(s), self.TAGS.get(s.zone if s.zone in self.TAGS else s.kind, []))
               for s in self.spaces + [self.flight]]
        for o in self.openings:
            out.append((o.id, self.cells_zone(o.cells(), o.floor + 1, o.floor + o.height), self.TAGS.get(o.kind, [])))
        out.append(("false_exit_pit", self.cells_zone(self.pit_cells, self.pit_floor - self.pit_depth, self.pit_floor), ["false_exit_pit"]))
        # The entry corridor's game, from its start line to the airlock (CloudGameHooks).
        entry = next(s for s in self.spaces if s.kind == "entry")
        x0, z0, x1, z1 = entry.box
        cells = [(x, z) for x in range(x0 + self.p["CLOUD_GAME"]["start"], x1) for z in range(z0, z1)]
        out.append(("cloud_game", self.cells_zone(cells, entry.floor + 1, entry.floor + entry.height), ["cloud_game"]))
        # The spiral stairs' shaft, from layer 3's floor to its top.
        f3, sp = self.layer_floor[3], self.p["SPIRAL"]
        out.append(("office_stairs", self.cells_zone(sorted(self.spiral_cells), f3 + 1, f3 + sp["rise"] + self.p["ROOM_HEIGHT"] - 1), []))
        # The ladder's shaft, from the foot of the ladder's room up to the hatch in layer 2's floor.
        shaft = sorted(self.shaft_air)
        out.append(("drain_shaft", self.cells_zone([(x, z) for x, _, z in shaft], shaft[0][1], self.layer_floor[2]), []))
        return out

    def spawn_points(self):
        """The level file's spawn points: the start, the Lab hall's restart point, the levers, and the loot,
        cards, enemies, figure and the rest that furnish.py places."""
        p = self.p
        start, start_yaw = self.point(p["PLAYER_START"])
        check, check_yaw = self.point(p["CHECKPOINT_POINT"])
        out = [{"id": "player_start", "pos": start, "yaw": start_yaw, "tags": ["player"]},
               {"id": "checkpoint", "pos": check, "yaw": check_yaw, "tags": ["restart_point"]}]
        for lid, layer, x, z, facing in p["LEVERS"]:
            out.append({"id": lid, "pos": list(self.at(x, self.layer_floor[layer] + 2, z)), "yaw": 0, "tags": ["lab_lever"]})
        # The entry corridor's game runs down its middle line, from its start line to its last cell before the airlock.
        entry = next(s for s in self.spaces if s.kind == "entry")
        for pid, x in (("cloud_game_start", entry.box[0] + p["CLOUD_GAME"]["start"]), ("cloud_game_end", entry.box[2] - 1)):
            (ax, ay, az), yaw = self.point((entry.layer, x, entry.box[1], "east"))
            (bx, _, bz), _ = self.point((entry.layer, x, entry.box[3] - 1, "east"))
            out.append({"id": pid, "pos": [(ax + bx) / 2, ay, (az + bz) / 2], "yaw": yaw, "tags": ["cloud_game"]})
        out += self.furnish.spawn_points(lambda facing: YAW[WORLD_FACING[facing]])
        return out

    def point(self, spec):
        """A spawn point's (position, yaw) from (layer, x, z, plan facing): standing on the cell's floor."""
        layer, x, z, facing = spec
        wx, wy, wz = self.at(x, self.floors[(layer, x, z)] + 1, z)
        return [wx + 0.5, wy, wz + 0.5], YAW[WORLD_FACING[facing]]
