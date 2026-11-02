"""
Level 4 (B4): the VIP lounge of the Noname Complex. Built from LEVEL_4 in map_generator.py (what
is where); this file is how: the spaces and what joins them, then every block round the air.

Plan coordinates as in Level 3 (offices/level.py): x east, z south, boxes (x0, z0, x1, z1) with x1
and z1 exclusive, y relative to the main floor's blocks. The plan is drawn with Reception at the
south, where the corridor from the door to B4 comes in; LEVEL_4's PLACE_X and PLACE_Z turn it into
the world.

The diploma corridor is built with Level 3 and the rest in play, when Level 3 is erased, so the
blocks come in two sets: corridor_blocks (with the wall and the door to Reception) and lounge_blocks.
"""

from lounge.furnish import Furnish

AROUND = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]


def grid(a, b, spacing):
    """Positions spread over a..b (inclusive) every `spacing`, centred; at least one."""
    length = b - a + 1
    n = max(1, (length + spacing // 2) // spacing)
    start = a + (length - (n - 1) * spacing - 1) // 2
    return [start + k * spacing for k in range(n)]


class Space:
    def __init__(self, sid, name, kind, box, height, floor=0):
        self.id, self.name, self.kind = sid, name, kind
        self.box, self.height, self.floor = box, height, floor

    @property
    def size(self):
        return self.box[2] - self.box[0], self.box[3] - self.box[1]


class Lounge:
    def __init__(self, p):
        self.p = dict(p)
        self.rooms = [Space(sid, name, kind, box, height) for sid, name, box, height, kind in p["ROOMS"]]
        sid, name, box, height, kind, floor = p["CALM_ROOM"]
        self.rooms.append(Space(sid, name, kind, box, height, floor))
        height = p["CORRIDOR_HEIGHT"]
        self.corridors = [Space(sid, "", "corridor", box, height) for sid, box in p["CORRIDORS"]]
        sid, box = p["STEPS"]
        self.steps = Space(sid, "", "steps", box, height)
        sid, name, box, height = p["DIPLOMA_CORRIDOR"]
        self.diplomas = Space(sid, name, "diplomas", box, height)
        sid, name, box, height = p["EXIT_STRETCH"]
        self.exit = Space(sid, name, "exit", box, height)
        self.spaces = self.rooms + self.corridors + [self.steps, self.diplomas, self.exit]
        self.openings = list(p["OPENINGS"])
        self.floors = self.carve()

    def carve(self):
        """(x, z) of every cell a player can stand in -> its floor's y. The steps go down one per
        cell after the landing, ending level with the Calm room."""
        floors = {}
        for s in self.spaces:
            x0, z0, x1, z1 = s.box
            for x in range(x0, x1):
                for z in range(z0, z1):
                    floor = max(-(x - x0), self.rooms[-1].floor) if s is self.steps else s.floor
                    if (x, z) in floors:
                        raise SystemExit(f"Level 4: {s.id} overlaps another space at {(x, z)}")
                    floors[(x, z)] = floor
        for _, axis, line, first, width, _, _ in self.openings:
            for k in range(first, first + width):
                floors[(line, k) if axis == "x" else (k, line)] = 0
        x, z = self.p["B4_DOOR"]
        floors[(x, z)] = 0
        return floors

    def world(self, x, z):
        """A plan cell's world x and z."""
        return z + self.p["PLACE_X"], self.p["PLACE_Z"] - x

    def at(self, x, y, z):
        """A plan cell's world position."""
        wx, wz = self.world(x, z)
        return wx, self.p["FLOOR_Y"] + y, wz

    # --- blocks ----------------------------------------------------------------------

    def carve_air(self, spaces):
        air = {}
        for s in spaces:
            x0, z0, x1, z1 = s.box
            for x in range(x0, x1):
                for z in range(z0, z1):
                    floor = self.floors[(x, z)]
                    for y in range(floor + 1, floor + s.height + 1):
                        air[(x, y, z)] = floor
        return air

    def state(self, role):
        return self.p["STAND_INS"][role]

    def shell(self, air):
        """Every cell next to the air becomes solid: carpet under it, ceiling over it, and beside
        it a wall with a walnut wainscot: base, then a rail with its brass strip, then cream."""
        out = {}
        for x, y, z in air:
            for dx, dy, dz in AROUND:
                c = (x + dx, y + dy, z + dz)
                if c in air or c in out:
                    continue
                out[c] = self.shell_role(c, air)
        return out

    def shell_role(self, c, air):
        x, y, z = c
        if (x, y + 1, z) in air:
            return "carpet"
        beside = [air[(x + dx, y, z + dz)] for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if (x + dx, y, z + dz) in air]
        if beside:
            height = y - max(beside)
            return "walnut" if height == 1 else "rail" if height == 2 else "cream"
        if (x, y - 1, z) in air:
            return "ceiling"
        near = [dy for dx, dy, dz in AROUND if (x + dx, y + dy, z + dz) in air]
        return "carpet" if max(near) > 0 else "ceiling" if min(near) < 0 else "cream"

    def door_cells(self, kind):
        """(plan x, y, z, state) of a kind of door's cells: a pair facing the way the player comes
        (the plan's north, the world's west), hinges outward so they part in the middle."""
        cells = []
        for _, _, line, first, width, _, k in self.openings:
            if k != kind:
                continue
            for i in range(width):
                hinge = "left" if i < width // 2 else "right"
                for y, half in ((1, "lower"), (2, "upper")):
                    state = f"{self.p['DOORS'][k]}[facing=west,half={half},hinge={hinge},open=false,powered=false]"
                    cells.append((first + i, y, line, state))
        return cells

    def compose(self):
        """The blocks of both sets, in world positions."""
        p = self.p
        rooms = [s for s in self.spaces if s is not self.diplomas]
        air = self.carve_air(rooms)
        for _, axis, line, first, width, height, kind in self.openings:
            if kind == "big_door":
                continue
            for k in range(first, first + width):
                for y in range(1, height + 1):
                    air[(k, y, line)] = 0
        corridor_air = self.carve_air([self.diplomas])
        self.blocked = set()         # plan cells (x, z) nobody walks over: thin walls and solid furniture
        partition_doors = {(x, z) for x, z, _ in p["PARTITION_DOORS"]}
        for x0, z0, x1, z1 in p["PARTITIONS"]:
            for x in range(x0, x1):
                for z in range(z0, z1):
                    door = (x, z) in partition_doors
                    if not door:
                        self.blocked.add((x, z))
                    top = next(s.height for s in self.rooms if s.box[0] <= x < s.box[2] and s.box[1] <= z < s.box[3])
                    for y in range(3 if door else 1, top + 1):
                        air.pop((x, y, z), None)
        steel = {(x, y, z) for x, y, z, _ in self.door_cells("steel_door")}
        for c in steel:
            air.pop(c, None)             # shut until the level stands: a wall for both shells
        roles = self.shell(air)
        corridor_roles = self.shell(corridor_air)
        self.lamps = []

        def lamps(space, spacing):
            x0, z0, x1, z1 = space.box
            cells = [(x, z) for x in grid(x0, x1 - 1, spacing) for z in grid(z0, z1 - 1, spacing)]
            return [(x, self.floors[(x, z)] + space.height, z) for x, z in cells]

        for s in self.rooms + self.corridors:
            spacing = p["LAMP_SPACING_BAR"] if s.id == "ch1_l4_bar" else p["LAMP_SPACING"]
            self.lamps += [c for c in lamps(s, spacing) if c in air]
        corridor_lamps = lamps(self.diplomas, 5)
        self.lamps += corridor_lamps

        # The Calm room is the one colourful room.
        calm = self.rooms[-1].box
        for c, r in roles.items():
            if calm[0] - 1 <= c[0] <= calm[2] and calm[1] - 1 <= c[2] <= calm[3]:
                roles[c] = {"carpet": "calm_carpet", "cream": "calm_wall", "walnut": "calm_wall", "rail": "calm_wall"}.get(r, r)
        self.lounge_blocks = {self.at(*c): self.state(r) for c, r in roles.items()}
        self.corridor_blocks = {self.at(*c): self.state(r) for c, r in corridor_roles.items()}
        for x, y, z, st in self.door_cells("door"):
            self.lounge_blocks[self.at(x, y, z)] = st
        for x, y, z, st in self.door_cells("steel_door"):
            self.lounge_blocks[self.at(x, y, z)] = st
            self.corridor_blocks[self.at(x, y, z)] = st
        for x, z, open_ in p["PARTITION_DOORS"]:
            for y, half in ((1, "lower"), (2, "upper")):
                self.lounge_blocks[self.at(x, y, z)] = (
                    f"{p['DOORS']['door']}[facing=west,half={half},hinge=left,open={str(open_).lower()},powered=false]")
        for c in self.lamps:
            (self.corridor_blocks if c in corridor_lamps else self.lounge_blocks)[self.at(*c)] = p["LAMP"]
        # The steps down: the world's south is the plan's west, the way they climb.
        x0, z0, x1, z1 = self.steps.box
        for x in range(x0 + 1, x1):
            for z in range(z0, z1):
                self.lounge_blocks[self.at(x, self.floors[(x, z)] + 1, z)] = (
                    f"{self.state('stairs')}[facing=south,half=bottom,shape=straight,waterlogged=false]")
        # The beds: the plan's north is the world's west.
        world_facing = {"north": "west", "west": "south", "south": "east", "east": "north"}
        for x, z, facing in p["BEDS"]:
            dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
            for cell, head in (((x, z), "true"), ((x - dx, z - dz), "false")):
                self.lounge_blocks[self.at(cell[0], 1, cell[1])] = f"every_tunnel:suite_bed[facing={world_facing[facing]},head={head}]"
        for x, z, facing in p["BEDS"]:
            dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing]
            self.blocked |= {(x, z), (x - dx, z - dz)}
        self.furnish = Furnish(self, {**air, **corridor_air}).lay()
        self.corridor_top = max(y for _, y, _ in self.corridor_blocks)
        self.box = self.bounds(self.lounge_blocks)
        return self

    @staticmethod
    def bounds(blocks):
        xs, ys, zs = zip(*blocks)
        return min(xs), min(ys), min(zs), max(xs), max(ys), max(zs)

    def area(self, other):
        """What Level 4's build must have loaded: its box and `other`'s."""
        b = self.box
        return min(b[0], other[0]), 0, min(b[2], other[2]), max(b[3], other[3]), 0, max(b[5], other[5])

    def space_zone(self, s):
        x0, z0, x1, z1 = s.box
        floors = [self.floors[(x, z)] for x in range(x0, x1) for z in range(z0, z1)]
        ax, _, az = self.at(x0, 0, z0)
        bx, _, bz = self.at(x1 - 1, 0, z1 - 1)
        y = self.p["FLOOR_Y"]
        return min(ax, bx), y + min(floors) + 1, min(az, bz), max(ax, bx), y + max(floors) + s.height, max(az, bz)

    def build_zone(self):
        """Walking in starts the build. It stops a cell short of the door to B4, which shuts then,
        so nobody stands in its way."""
        x0, y0, z0, x1, y1, z1 = self.space_zone(self.diplomas)
        return x0, y0, z0, x1 - 1, y1, z1

    def zones(self):
        """(id, world box inclusive, tags): every space, the part of the corridor that starts the
        build, and the openings between them, so that going through a door never leaves the level."""
        out = [(s.id, self.space_zone(s), ["end"] if s is self.exit else []) for s in self.spaces]
        out.append((f"{self.diplomas.id}_build", self.build_zone(), ["lounge_build"]))
        y = self.p["FLOOR_Y"]
        for oid, _, line, first, width, height, kind in self.openings:
            ax, _, az = self.at(first, 0, line)
            bx, _, bz = self.at(first + width - 1, 0, line)
            tags = ["lounge_door"] if kind == "steel_door" else []
            out.append((oid, (min(ax, bx), y + 1, min(az, bz), max(ax, bx), y + height, max(az, bz)), tags))
        return out
