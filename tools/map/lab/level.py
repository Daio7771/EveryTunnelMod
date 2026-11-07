"""
Level 5 (B5): the laboratory of the Noname Complex, layers 1 and 2 (the drain under them, layer 3,
comes later). Built from LEVEL_5 in map_generator.py (what is where); this file is how: the spaces,
what joins them, where they stand in the world, and every block round the air.

Plan coordinates as in Level 4 (lounge/level.py): x east, z south, boxes (x0, z0, x1, z1) with x1 and
z1 exclusive, y relative to layer 1's floor blocks. Both layers share the plan: layer 1, the upper
floor, stands on Level 4's floor; layer 2 lies LAYER_DROP under it, so a cell may hold one space of
each. One flight of stairs joins them. The way in leaves Level 4's dark stretch through a steel frame in
its side, by its far end; LEVEL_5's PLACE_X and PLACE_Z turn the plan so that it does.

The blocks come in two sets. The way in (entry_blocks: the frame, a dark corridor of the lab's tiles,
the airlock of steel and its two sliding doors) is built with Level 4, as the player walks it while the
rest goes up. Crossing the frame is walking into Level 5, so its title shows there. The airlock's door
ahead, in the Lobby's wall, stays shut until the level is built (lab_blocks, in play: LabHooks); the
one behind stands open until the player is in.
"""

from lab.furnish import Furnish

# Plan directions in the world: the plan's south is the world's west.
WORLD_FACING = {"north": "east", "south": "west", "east": "south", "west": "north"}
YAW = {"south": 0, "west": 90, "north": 180, "east": -90}
AROUND = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]


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


class Space:
    def __init__(self, sid, zone, name, layer, box, height, kind, floor):
        self.id, self.zone, self.name, self.layer = sid, zone, name, layer
        self.box, self.height, self.kind, self.floor = box, height, kind, floor

    @property
    def size(self):
        return self.box[2] - self.box[0], self.box[3] - self.box[1]

    def cells(self):
        x0, z0, x1, z1 = self.box
        return [(x, z) for x in range(x0, x1) for z in range(z0, z1)]


class Opening:
    def __init__(self, oid, layer, axis, line, first, width, height, kind):
        self.id, self.layer, self.axis, self.line = oid, layer, axis, line
        self.first, self.width, self.height, self.kind = first, width, height, kind

    def cells(self):
        ks = range(self.first, self.first + self.width)
        return [(self.line, k) if self.axis == "x" else (k, self.line) for k in ks]


# Corridors and stairs open onto whatever they touch; any other two spaces keep a wall between them.
OPEN_KINDS = ("corridor", "stairs", "entry")
# Built with Level 4: the way in.
ENTRY_KINDS = ("entry", "airlock")
ENTRY_OPENINGS = ("frame", "airlock_rear")


class Lab:
    WORLD_FACING = WORLD_FACING

    def __init__(self, p):
        self.p = dict(p)
        drop = p["LAYER_DROP"]
        self.layer_floor = {1: 0, 2: -drop}

        def height(kind, h):
            if h is not None:
                return h
            return p["CORRIDOR_HEIGHT"] if kind in OPEN_KINDS else p["ROOM_HEIGHT"]

        self.spaces = [Space(sid, zone, name, layer, box, height(kind, h), kind, self.layer_floor[layer])
                       for sid, zone, name, layer, box, h, kind in p["SPACES"]]
        sid, zone, box = p["FLIGHT"]
        self.flight = Space(sid, zone, "", 1, box, p["CORRIDOR_HEIGHT"], "stairs", 0)
        self.openings = [Opening(*o) for o in p["OPENINGS"]]
        self.entry = [s for s in self.spaces if s.kind in ENTRY_KINDS]
        self.airlock = next(s for s in self.spaces if s.kind == "airlock")
        self.floors = self.carve()

    def carve(self):
        """(layer, x, z) of every cell a player can stand in -> its floor's y. The flight goes down one
        block per cell along z, from layer 1's floor to layer 2's; its cells count as layer 1's."""
        floors = {}
        drop = self.p["LAYER_DROP"]
        for s in self.spaces + [self.flight]:
            for x, z in s.cells():
                key = (s.layer, x, z)
                if key in floors:
                    raise SystemExit(f"Level 5: {s.id} overlaps another space of layer {s.layer} at {(x, z)}")
                floors[key] = -min(z - s.box[1] + 1, drop) if s is self.flight else s.floor
        for o in self.openings:
            for x, z in o.cells():
                key = (o.layer, x, z)
                if key in floors:
                    raise SystemExit(f"Level 5: the opening {o.id} lies inside a space at {(x, z)}")
                floors[key] = self.layer_floor[o.layer]
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
            floor = self.layer_floor[o.layer]
            for x, z in o.cells():
                for y in range(floor + 1, floor + o.height + 1):
                    air[(x, y, z)] = floor
        return air

    def shell_role(self, c, air):
        """A block next to the air: vinyl under it, a ceiling panel over it, and beside it a wall of
        white tiles with a band of green ones at its foot."""
        x, y, z = c
        if (x, y + 1, z) in air:
            return "vinyl"
        beside = [air[(x + dx, y, z + dz)] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (x + dx, y, z + dz) in air]
        if beside:
            return "band" if y - max(beside) == 1 else "tile"
        if (x, y - 1, z) in air:
            return "ceiling"
        near = [dy for dx, dy, dz in AROUND if (x + dx, y + dy, z + dz) in air]
        return "vinyl" if max(near) > 0 else "ceiling" if min(near) < 0 else "tile"

    def state(self, role):
        if role == "checker":
            return self.p["CHECKER"]
        return self.p["STAND_INS"][role]

    def airlock_door(self, o):
        """{world position: state} of an airlock door, shut: four columns, two leaves, each column sliding
        open towards its end of the opening (the same order as LabHooks: along the world axis the opening
        runs on, the lower half towards lower coordinates)."""
        floor = self.layer_floor[o.layer]
        cells = [self.at(x, y, z) for x, z in o.cells() for y in range(floor + 1, floor + o.height + 1)]
        along_x = len({c[0] for c in cells}) > 1
        coords = sorted({c[0] if along_x else c[2] for c in cells})
        out = {}
        for c in cells:
            low = coords.index(c[0] if along_x else c[2]) < len(coords) // 2
            facing = ("west" if low else "east") if along_x else ("north" if low else "south")
            out[c] = self.p["AIRLOCK_DOOR"].format(facing=facing, width=4)
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
        lab_air = self.carve_air(rooms + [self.flight])
        lab_air.update(self.opening_air([o for o in self.openings if o.kind not in ENTRY_OPENINGS + ("airlock_front",)]))
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
            floor = self.layer_floor[o.layer]
            cells = o.cells()
            ends = [cells[0], cells[-1]]
            step = (0, 1) if o.axis == "x" else (1, 0)
            jambs = [(ends[0][0] - step[0], ends[0][1] - step[1]), (ends[1][0] + step[0], ends[1][1] + step[1])]
            for x, z in jambs:
                for y in range(floor + 1, floor + o.height + 2):
                    roles[(x, y, z)] = "steel"
            for x, z in cells:
                roles[(x, floor + o.height + 1, z)] = "steel"
                roles[(x, floor, z)] = "checker"

        # Next to the way in's air: built with Level 4. The rest with the level, in play.
        self.entry_blocks = {self.at(*c): self.state(r) for c, r in roles.items() if near(c, entry_air)}
        # The level's own air goes in too: a build over an older one puts everything back as new.
        self.lab_blocks = {self.at(*c): "minecraft:air" for c in lab_air}
        self.lab_blocks.update({self.at(*c): self.state(r) for c, r in roles.items() if not near(c, entry_air)})
        # The airlock's door ahead stands shut in both sets: before the level, it is the Lobby's wall.
        for o in front:
            self.entry_blocks.update(self.airlock_door(o))
            self.lab_blocks.update(self.airlock_door(o))

        for o in self.openings:
            if o.kind == "locked":
                self.lab_blocks.update(self.airlock_door(o))
            elif o.kind == "grate":
                ew, ns = ("false", "true") if o.axis == "z" else ("true", "false")
                floor = self.layer_floor[o.layer]
                for x, z in o.cells():
                    for y in range(floor + 1, floor + o.height + 1):
                        self.lab_blocks[self.at(x, y, z)] = p["GRATE"].format(ew=ew, ns=ns)
        # Fluorescent lights hang in every room and corridor of the level, all on. None on the way in: it
        # stays dark until the airlock opens onto the Lobby.
        self.lights = []
        for s in rooms:
            x0, z0, x1, z1 = s.box
            axis = "z" if (x1 - x0) >= (z1 - z0) else "x"         # along the space: the plan's x is the world's z
            for x in grid(x0, x1 - 1, p["LIGHT_SPACING"]):
                for z in grid(z0, z1 - 1, p["LIGHT_SPACING"]):
                    c = (x, s.floor + s.height, z)
                    self.lights.append(c)
                    self.lab_blocks[self.at(*c)] = p["LIGHT"].format(axis=axis)
        # The levers on their walls.
        for _, layer, x, z, facing in p["LEVERS"]:
            self.lab_blocks[self.at(x, self.layer_floor[layer] + 2, z)] = p["LEVER"].format(facing=WORLD_FACING[facing])
        # Layer 1's furniture and what lies about (furnish.py).
        levers = {(x, self.layer_floor[layer] + 2, z) for _, layer, x, z, _ in p["LEVERS"]}
        self.furnish = Furnish(self, lab_air, levers).lay()
        self.lab_blocks.update(self.furnish.states())
        # The flight: a stair on each cell, climbing towards the plan's north.
        stairs = f"{self.state('stairs')}[facing={WORLD_FACING['north']},half=bottom,shape=straight,waterlogged=false]"
        for x, z in self.flight.cells():
            self.lab_blocks[self.at(x, self.floors[(1, x, z)] + 1, z)] = stairs
        self.entry_air = [self.at(*c) for c in entry_air]
        self.box = self.bounds({**self.lab_blocks, **self.entry_blocks})
        self.lab_box = self.bounds(self.lab_blocks)
        return self

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
        floors = [self.floors[(s.layer, x, z)] for x, z in s.cells()]
        return self.cells_zone(s.cells(), min(floors) + 1, max(floors) + s.height)

    # Zone tags by kind of space or opening: the airlock's (LabHooks), the locked doors (LabDoorHooks), the
    # Lab hall's restart point (ContinueGame).
    TAGS = {"airlock": ["lab_airlock"], "airlock_rear": ["lab_airlock_rear"], "airlock_front": ["lab_welcome"],
            "locked": ["lab_door"], "hub": ["restart_point"]}

    def zones(self):
        """(id, world box inclusive, tags) of Level 5's file: every space and every opening, so that
        going through a door never leaves the level. A zone of several boxes gives its id to each (the
        corridors round the rooms, the stairs, the routes)."""
        out = [(s.zone, self.space_zone(s), self.TAGS.get(s.kind, [])) for s in self.spaces + [self.flight]]
        for o in self.openings:
            floor = self.layer_floor[o.layer]
            out.append((o.id, self.cells_zone(o.cells(), floor + 1, floor + o.height), self.TAGS.get(o.kind, [])))
        return out

    def spawn_points(self):
        """The level file's spawn points: the start, the Lab hall's restart point, the levers, and layer 1's
        loot, cards, enemies and figure (furnish.py)."""
        p = self.p
        start, start_yaw = self.point(p["PLAYER_START"])
        check, check_yaw = self.point(p["CHECKPOINT_POINT"])
        out = [{"id": "player_start", "pos": start, "yaw": start_yaw, "tags": ["player"]},
               {"id": "checkpoint", "pos": check, "yaw": check_yaw, "tags": ["restart_point"]}]
        for lid, layer, x, z, facing in p["LEVERS"]:
            out.append({"id": lid, "pos": list(self.at(x, self.layer_floor[layer] + 2, z)), "yaw": 0, "tags": ["lab_lever"]})
        out += self.furnish.spawn_points(lambda facing: YAW[WORLD_FACING[facing]])
        return out

    def point(self, spec):
        """A spawn point's (position, yaw) from (layer, x, z, plan facing): standing on the cell's floor."""
        layer, x, z, facing = spec
        wx, wy, wz = self.at(x, self.floors[(layer, x, z)] + 1, z)
        return [wx + 0.5, wy, wz + 0.5], YAW[WORLD_FACING[facing]]
