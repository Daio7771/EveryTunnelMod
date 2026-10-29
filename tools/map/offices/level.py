"""
Level 3 (B3): the offices of the Noname Complex around the central library, in perfect
order and empty. Built from LEVEL_3 in map_generator.py (what is where); this file is how.

Plan coordinates, as in the level's drawing: x east, z south, north up; boxes are
(x0, z0, x1, z1) with x1 and z1 exclusive. y is relative: the main floor's blocks lie at
y = 0, so a player stands at y = 1. The level is drawn with the stairs from Level 2 at the
south end; Placement (below) turns and moves it so the top of the stairs meets the door at
the bottom of Level 2's spiral stairs.

    [ belt of 17 rooms around the inner corridor (ring) ]
    [   ring    [ LIBRARY, bunker hatch in the centre ]  ]
    [ belt ...      | valve corridor (checkpoint door, valves A-E) ]
                    [ antechamber ] [ stairs up to Level 2 ]
    under the library: a shaft with a ladder down to a long lit corridor running south,
    the collapse zone near its end and the door to Level 4.

How it is built: every space (room, door, corridor...) is a box of air over a floor at its
own height. The shell is every block around that air: below it a floor, above it a ceiling,
beside it a wall, each in the materials of the space it faces (MATERIALS). So the plan's
one-block gaps between spaces become the walls, and a door is just a small space through
one. Then the fittings go in: stairs, lights, the checkpoint wall and doors, the valves,
the hatch, the ladder, the door to Level 4, and the furniture (furnish.py).
"""

from garage.build import World
from offices.furnish import furnish

OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
NAMES = {v: k for k, v in DIRS.items()}
DIRS6 = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
YAW = {"south": 0, "west": 90, "north": 180, "east": 270}
SIDES = ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0))
AROUND = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]


def cells(box):
    x0, z0, x1, z1 = box
    return [(x, z) for x in range(x0, x1) for z in range(z0, z1)]


def grid(a, b, spacing):
    """Positions spread over a..b (inclusive) every `spacing`, centred; at least one."""
    length = b - a + 1
    n = max(1, (length + spacing // 2) // spacing)
    start = a + (length - (n - 1) * spacing - 1) // 2
    return [start + k * spacing for k in range(n)]


class Space:
    def __init__(self, sid, name, kind, box, floor_y, height):
        self.id, self.name, self.kind = sid, name, kind
        self.box = box                  # (x0, z0, x1, z1), x1 and z1 exclusive
        self.floor_y = floor_y          # the floor's blocks; air from floor_y + 1
        self.height = height            # air blocks over the floor


class Offices:
    def __init__(self, p, glyphs):
        self.p = p
        self.glyphs = glyphs
        self.world = World()
        self.air = {}            # (x, y, z) -> (kind, floor y of that column)
        self.solid = {}          # (x, y, z) -> role: cells kept solid inside a space (the checkpoint wall)
        self.fittings = {}       # (x, y, z) -> block state, placed over the shell
        self.spaces = []         # every Space, in the order carved
        self.rooms = []          # (Space, type) of the belt
        self.zones = []          # (id, (x0, y0, z0, x1, y1, z1) inclusive, tags)
        self.spawns = []         # (id, (x, y, z), yaw, tags, zone or None)
        self.labels = []         # (x, z, text) for the preview
        self.lights = []         # (x, y, z) of every light
        self.valves = []         # (letter, (x, y, z)) of each valve
        self.passable = set()    # door, ladder and hatch cells a player gets through
        self.furniture = set()   # cells of solid furniture
        self.walk_through = set()  # cells of furniture a player walks through (chairs, plants...)
        self.book_spots = []     # [(x, y, z)]: in the library, where the books may lie (furnish.py)
        self.box_spots = []      # (x, y, z, zone id): where the box puzzle's boxes may lie (furnish.py)
        self.box_shelf = None    # (x, z, facing) of the box shelf's foot, in Storage (furnish.py)
        self.notes = {}          # note id -> (x, y, z) of its board or sheet
        self.book_returns = []   # [(x, z, facing)]: the places on the librarian's desk the books go back to
        self.loot = []           # [(item, x, z, room id)] (furnish.py)
        self.water_sources = []  # [(x, z, room id)]: the water dispensers drunk from

    # --- spaces --------------------------------------------------------------------

    def space(self, sid, name, kind, box, height, floor_y=0):
        s = Space(sid, name, kind, box, floor_y, height)
        for x, z in cells(box):
            for y in range(floor_y + 1, floor_y + height + 1):
                self.air.setdefault((x, y, z), (kind, floor_y))
        self.spaces.append(s)
        return s

    def door_gap(self, axis, line, centre, width, height):
        """A door through the one-block wall at x = line (axis "x") or z = line (axis "z"),
        `width` wide centred on `centre` (cells centre - width // 2 .. centre + (width - 1) // 2)."""
        a = centre - width // 2
        box = (line, a, line + 1, a + width) if axis == "x" else (a, line, a + width, line + 1)
        self.space(f"door_{axis}{line}_{centre}", "", "door", box, height)

    def zone(self, zid, box, y0, y1, tags=()):
        x0, z0, x1, z1 = box
        self.zones.append((zid, (x0, y0, z0, x1 - 1, y1, z1 - 1), list(tags)))

    def spawn(self, sid, x, y, z, facing, tags, zone=None):
        self.spawns.append((sid, (x + 0.5, y, z + 0.5), YAW[facing], list(tags), zone))

    # --- the level -----------------------------------------------------------------

    def compose(self):
        p = self.p
        H, DH = p.HEIGHTS, p.DOOR_HEIGHTS

        self.library = self.space("library", "Central library", "library", p.LIBRARY, H["library"])
        for i, box in enumerate(p.INNER_CORRIDOR):
            self.space(f"inner_corridor_{'nsew'[i]}", "", "corridor", box, H["corridor"])
        for box in p.LIBRARY_DOORS:
            self.space(f"library_door_{box[0]}_{box[1]}", "", "door", box, DH["library"])
        for sid, name, box, kind in p.ROOMS:
            s = self.space(sid, name, "service" if kind == "service" else "office", box, H["room"])
            self.rooms.append((s, kind))
        for axis, line, centre in p.ROOM_DOORS:
            self.door_gap(axis, line, centre, p.ROOM_DOOR_WIDTH, DH["room"])
        self.corridor = self.space("valve_corridor", "Valve corridor", "corridor", p.VALVE_CORRIDOR, H["corridor"])
        self.antechamber = self.space("antechamber", "Antechamber", "corridor", p.ANTECHAMBER, H["antechamber"])
        self.stairs()
        self.checkpoint()
        self.bunker()
        self.shell()
        self.fit_lights()
        self.bunker_fittings()
        self.valve_fittings()
        self.hatch()
        furnish(self)
        self.write_blocks()
        self.data()
        return self

    def stairs(self):
        """The stairs up to Level 2: a landing at each end (STAIRS_LANDING long), between them
        one step down per block (STAIRS_DROP in all), climbing south. Each column's air starts
        on its own step and goes up to one flat ceiling, the top landing's: a tall hall, so the
        lettering over the way into the antechamber (ENTRANCE_SIGN) is seen from the top."""
        p = self.p
        x0, z0, x1, z1 = p.STAIRS
        drop, landing, h = p.STAIRS_DROP, p.STAIRS_LANDING, p.HEIGHTS["stairs"]
        if (z1 - z0) != 2 * landing + drop:
            raise SystemExit(f"LEVEL_3 stairs: {z1 - z0} long, but 2 landings of {landing} and {drop} steps need {2 * landing + drop}")
        self.stair_steps = []
        for z in range(z0, z1):
            k = z - z0                                   # 0 at the bottom (north) end
            fy = 0 if k < landing else drop if k >= landing + drop else k - landing + 1
            for x in range(x0, x1):
                for y in range(fy + 1, drop + h + 1):
                    self.air.setdefault((x, y, z), ("corridor", fy))
                if landing <= k < landing + drop:
                    self.stair_steps.append((x, fy, z))
        self.top_landing_y = drop                        # floor block y at the top
        self.spaces.append(Space("stairs", "Stairs from Level 2", "corridor", p.STAIRS, 0, h))

    def checkpoint(self):
        """A wall across the valve corridor at CHECKPOINT_Z with a closed double emergency door in
        the middle; the player comes from the south and goes on north."""
        p = self.p
        x0, _, x1, _ = p.VALVE_CORRIDOR
        z = p.CHECKPOINT_Z
        mid = (x0 + x1) // 2
        for x in range(x0, x1):
            for y in range(1, p.HEIGHTS["corridor"] + 1):
                self.air.pop((x, y, z), None)
                self.solid[(x, y, z)] = ("corridor", "wall")
        for x, hinge in ((mid - 1, "left"), (mid, "right")):
            for y, half in ((1, "lower"), (2, "upper")):
                self.solid.pop((x, y, z), None)
                self.fittings[(x, y, z)] = (f"{p.BLOCKS['door']}[facing=north,half={half},hinge={hinge},"
                                            "open=false,powered=false]")
                self.passable.add((x, y, z))

    def bunker(self):
        """Under the hatch in the library's centre: a shaft down with a ladder on its north wall,
        and at its foot a long lit tunnel along BUNKER["path"] (legs at right angles) to the
        door of Level 4. The legs' centre line is kept in bunker_line, [(x, z, direction)] from
        the shaft to the last cell before the door."""
        p = self.p
        b = p.BUNKER
        hx0, hz0, hx1, hz1 = p.HATCH["hole"]
        floor = -b["depth"]                              # the tunnel's floor blocks
        top = floor + b["height"]                        # its last air row
        half = b["width"] // 2
        x, z = (hx0 + hx1) // 2, hz0                     # under the shaft
        self.bunker_line = []
        for k, (direction, length) in enumerate(b["path"]):
            dx, dz = DIRS[direction]
            line = [(x + dx * i, z + dz * i) for i in range(length)]
            rx, rz = -dz, dx                             # across the leg
            xs = [cx + rx * o for cx, _ in line for o in (-half, half)]
            zs = [cz + rz * o for _, cz in line for o in (-half, half)]
            self.space(f"bunker_corridor_{k + 1}", "Bunker tunnel", "bunker",
                       (min(xs), min(zs), max(xs) + 1, max(zs) + 1), b["height"], floor)
            self.bunker_line += [(cx, cz, direction) for cx, cz in (line if k == 0 else line[1:])]
            x, z = line[-1]
        xs = [c[0] for c in self.bunker_line]
        zs = [c[1] for c in self.bunker_line]
        self.bunker_box = (min(xs) - half, min(zs) - half, max(xs) + half + 1, max(zs) + half + 1)
        shaft = Space("bunker_shaft", "Bunker shaft", "bunker", p.HATCH["hole"], top, -1 - top)
        for cx, cz in cells(p.HATCH["hole"]):
            for y in range(top + 1, 0):
                self.air.setdefault((cx, y, cz), ("bunker", floor))
        self.spaces.append(shaft)
        for cx in range(hx0, hx1):                       # the ladder, on the shaft's north wall
            for y in range(floor + 1, 0):
                self.fittings[(cx, y, hz0)] = f"{p.BLOCKS['ladder']}[facing=south,waterlogged=false]"
                self.passable.add((cx, y, hz0))
        # The door to Level 4 in the wall at the end, its sign over it, both facing back down the tunnel.
        ex, ez, direction = self.bunker_line[-1]
        dx, dz = DIRS[direction]
        back = OPPOSITE[direction]
        self.l4_door = (ex + dx, floor + 1, ez + dz)
        self.l4_front = (ex, floor + 1, ez)
        for y, part in ((floor + 1, "lower"), (floor + 2, "upper")):
            self.fittings[(ex + dx, y, ez + dz)] = f"{p.BLOCKS['door']}[facing={back},half={part},hinge=left,open=false,powered=false]"
            self.passable.add((ex + dx, y, ez + dz))
        self.fittings[(ex + dx, floor + 3, ez + dz)] = self.glyphs.wall_state(p.NEXT_LEVEL_SIGN, back)
        self.bunker_floor = floor

    def bunker_from_door(self, k0, k1):
        """The tunnel's cells k1..k0 blocks before the door to Level 4 (on its last leg), its
        full width: a box (x0, z0, x1, z1)."""
        half = self.p.BUNKER["width"] // 2
        ex, ez, direction = self.bunker_line[-1]
        dx, dz = DIRS[direction]
        pts = [(ex - dx * (k - 1), ez - dz * (k - 1)) for k in (k0, k1)]
        xs = [x + o for x, _ in pts for o in ((-half, half) if dz else (0,))]
        zs = [z + o for _, z in pts for o in ((-half, half) if dx else (0,))]
        return min(xs), min(zs), max(xs) + 1, max(zs) + 1

    def bunker_fittings(self):
        """The tunnel's open blast doors and the services along it. A blast door: a frame of
        hazard-striped steel round the tunnel (walls, ceiling, a threshold in the floor) and
        its leaf, 3 blocks long, folded back against the right-hand wall just past the frame.
        Then pipes along the ceiling on the left, a cable tray on the right, joined round the
        turns, kept off the lights, the ladder, the leaves and the shaft's opening."""
        p = self.p
        b = p.BUNKER
        half, floor = b["width"] // 2, self.bunker_floor
        top = floor + b["height"]
        line = self.bunker_line
        for at in b["blast_doors"]:
            x, z, direction = line[at]
            dx, dz = DIRS[direction]
            if any(line[j][2] != direction for j in range(at - 2, at + 5)):
                raise SystemExit(f"LEVEL_3 BUNKER blast door at {at}: too near a turn")
            rx, rz = -dz, dx                             # to the right, going on
            for o in (-half - 1, half + 1):
                for y in range(floor + 1, top + 1):
                    self.fittings[(x + rx * o, y, z + rz * o)] = p.BLOCKS["blast_frame"]
            for o in range(-half - 1, half + 2):
                self.fittings[(x + rx * o, top + 1, z + rz * o)] = p.BLOCKS["blast_frame"]
            for o in range(-half, half + 1):
                self.fittings[(x + rx * o, floor, z + rz * o)] = p.BLOCKS["blast_frame"]
            inward = NAMES[(-rx, -rz)]
            for j in range(1, 4):
                for y in range(floor + 1, top + 1):
                    self.fittings[(x + dx * j + rx * half, y, z + dz * j + rz * half)] = f"{p.BLOCKS['blast_leaf']}[facing={inward}]"

        def run(offset):
            out = set()
            for x, z, direction in line:
                dx, dz = DIRS[direction]
                rx, rz = -dz, dx
                for j in (-1, 0, 1):                     # a cell past each end, to meet round a turn
                    c = (x + rx * offset + dx * j, top, z + rz * offset + dz * j)
                    if c in self.air and c not in self.fittings and (c[0], top + 1, c[2]) not in self.air:
                        out.add(c)
            return out

        flat = {n: (v[0], 0, v[1]) for n, v in DIRS.items()}               # a tray joins only sideways
        for cells_, block, sides in ((run(-half), p.BLOCKS["pipe"], DIRS6), (run(half), p.BLOCKS["cable_tray"], flat)):
            for (x, y, z) in cells_:
                arms = {n: (x + d[0], y + d[1], z + d[2]) in cells_ for n, d in sides.items()}
                self.fittings[(x, y, z)] = block + "[" + ",".join(f"{k}={'true' if v else 'false'}" for k, v in sorted(arms.items())) + "]"

    # --- the shell -----------------------------------------------------------------

    def shell(self):
        """Every cell next to the air (sides, edges, corners) that is not air becomes solid:
        a floor under air, a wall beside it, a ceiling over it."""
        p = self.p
        out = {}
        for pos, role in self.solid.items():
            out[pos] = role
        candidates = set()
        for x, y, z in self.air:
            for dx, dy, dz in AROUND:
                c = (x + dx, y + dy, z + dz)
                if c not in self.air:
                    candidates.add(c)
        priority = {k: i for i, k in enumerate(p.WALL_PRIORITY)}
        for c in candidates:
            if c in out:
                continue
            x, y, z = c
            above = self.air.get((x, y + 1, z))
            if above:
                out[c] = (above[0], "floor")
                continue
            beside = [self.air[(x + dx, y, z + dz)] for dx, _, dz in SIDES[:4] if (x + dx, y, z + dz) in self.air]
            if beside:
                kind, fy = min(beside, key=lambda a: priority[a[0]])
                out[c] = (kind, "base" if any(y == a[1] + 1 for a in beside if a[0] == kind) else "wall")
                continue
            below = self.air.get((x, y - 1, z))
            if below:
                out[c] = (below[0], "ceiling")
                continue
            # Only an edge or a corner touches the air: floor under it, ceiling over it, else wall.
            near = [(self.air[(x + dx, y + dy, z + dz)], dy) for dx, dy, dz in AROUND if (x + dx, y + dy, z + dz) in self.air]
            (kind, _), dy = min(near, key=lambda n: (priority[n[0][0]], -n[1]))
            out[c] = (kind, "floor" if dy > 0 else "ceiling" if dy < 0 else "wall")
        self.shell_roles = out

    def role_block(self, kind, part):
        return self.p.STAND_INS[self.p.MATERIALS[kind][part]]

    # --- fittings ------------------------------------------------------------------

    def light(self, x, y, z, axis):
        self.fittings[(x, y, z)] = f"every_tunnel:fluorescent_light[facing=down,axis={axis},mode=on,lit=true]"
        self.lights.append((x, y, z))

    def fit_lights(self):
        """Ceiling lights, all on: a grid in the rooms, two-block rows down the middle of the
        corridors, pendants on chains in the library."""
        p = self.p
        sp = p.LIGHT_SPACING
        for s, _ in self.rooms:
            x0, z0, x1, z1 = s.box
            axis = "x" if x1 - x0 >= z1 - z0 else "z"
            for x in grid(x0, x1 - 1, sp):
                for z in grid(z0, z1 - 1, sp):
                    self.light(x, s.height, z, axis)
        for s in self.spaces:
            if s.id.startswith(("inner_corridor", "bunker_corridor")) or s.id == "valve_corridor":
                self.corridor_lights(s)
        x0, z0, x1, z1 = p.ANTECHAMBER
        for x in grid(x0, x1 - 1, sp):
            for z in grid(z0, z1 - 1, sp):
                self.light(x, p.HEIGHTS["antechamber"], z, "x")
        # The stairs: rows across the flat ceiling, over each landing and halfway.
        x0, z0, x1, z1 = p.STAIRS
        mid = (x0 + x1) // 2
        top = self.top_landing_y + p.HEIGHTS["stairs"]
        for z in (z0 + 1, (z0 + z1) // 2, z1 - 1):
            for x in (mid - 1, mid):
                self.light(x, top, z, "x")
        # Library pendants: a chain from the ceiling down to the light, PENDANT height over the floor.
        x0, z0, x1, z1 = p.LIBRARY
        top = p.HEIGHTS["library"]
        for x in grid(x0, x1 - 1, p.PENDANT["spacing"]):
            for z in grid(z0, z1 - 1, p.PENDANT["spacing"]):
                ly = p.PENDANT["height"]
                for y in range(ly + 1, top + 1):
                    self.fittings[(x, y, z)] = "minecraft:chain[axis=y,waterlogged=false]"
                self.light(x, ly, z, "x")

    def corridor_lights(self, s):
        """A row down the middle of a corridor (two blocks across when its width is even), along
        its length; none where the bunker shaft opens into the bunker corridor's ceiling."""
        p = self.p
        x0, z0, x1, z1 = s.box
        along_z = z1 - z0 >= x1 - x0
        a0, a1 = (z0, z1 - 1) if along_z else (x0, x1 - 1)
        b0, b1 = (x0, x1 - 1) if along_z else (z0, z1 - 1)
        mids = [(b0 + b1) // 2] if (b1 - b0) % 2 == 0 else [(b0 + b1) // 2, (b0 + b1) // 2 + 1]
        y = s.floor_y + s.height
        # Two blocks across join into one fixture across the corridor; one block runs along it.
        across, along = ("x", "z") if along_z else ("z", "x")
        axis = across if len(mids) == 2 else along
        for a in grid(a0, a1, p.LIGHT_SPACING):
            for b in mids:
                x, z = (b, a) if along_z else (a, b)
                if (x, y + 1, z) in self.air or (x, y, z) in self.solid:
                    continue
                self.light(x, y, z, axis)

    def valve_fittings(self):
        """Each valve: a pipe up the corridor's east wall, floor to ceiling, with the valve
        inline at VALVE_HEIGHT, its wheel to the corridor (west), shut, its letter on its plates.
        The valve carries its own length of pipe, and the pipe joins it above and below
        (ConnectingPropBlock, throughValves); a riser grows no arms into the wall, so the
        valve's stub is the pipe's one bracket."""
        p = self.p
        x0, _, x1, _ = p.VALVE_CORRIDOR
        px = x1 - 1
        h, vy = p.HEIGHTS["corridor"], p.VALVE_HEIGHT
        for letter, z in p.VALVES:
            for y in range(1, h + 1):
                if y == vy:
                    continue
                arms = dict(north=False, south=False, east=False, west=False, up=y < h, down=y > 1)
                self.fittings[(px, y, z)] = p.BLOCKS["pipe"] + "[" + ",".join(
                    f"{k}={'true' if v else 'false'}" for k, v in arms.items()) + "]"
            self.fittings[(px, vy, z)] = f"{p.BLOCKS['valve']}[facing=west,letter={letter.lower()},open=false,turns=0]"
            self.valves.append((letter, (px, vy, z)))

    def hatch(self):
        """The bunker hatch in the floor: a frame of checker plate, the hole shut with two leaves
        of steel trapdoors (lying flush with the floor), the west half hinged on its west side and
        the east half on its east side, so they open up out of the way of the ladder."""
        p = self.p
        for x, z in cells(p.HATCH["frame"]):
            self.fittings[(x, 0, z)] = p.BLOCKS["hatch_frame"]
        hx0, _, hx1, _ = p.HATCH["hole"]
        for x, z in cells(p.HATCH["hole"]):
            facing = "east" if x < (hx0 + hx1) / 2 else "west"            # a trapdoor faces away from its hinge
            self.fittings[(x, 0, z)] = f"{p.BLOCKS['hatch_lid']}[facing={facing},half=top,open=false,powered=false,waterlogged=false]"
            self.passable.add((x, 0, z))

    def write_blocks(self):
        p = self.p
        for (x, y, z), (kind, part) in self.shell_roles.items():
            self.world.set(x, y, z, self.role_block(kind, part))
        for x, fy, z in self.stair_steps:
            self.world.set(x, fy, z, f"{p.STAND_INS['stairs']}[facing=south,half=bottom,shape=straight,waterlogged=false]")
        for (x, y, z), state in self.fittings.items():
            self.world.set(x, y, z, state)

    # --- zones and spawn points ----------------------------------------------------

    def data(self):
        p = self.p
        H = p.HEIGHTS
        for s, kind in self.rooms:
            self.zone(s.id, s.box, 1, H["room"])
            self.labels.append(((s.box[0] + s.box[2]) // 2, (s.box[1] + s.box[3]) // 2, s.name))
        self.zone("library", p.LIBRARY, 1, H["library"], ["observer_home"])          # the Observer patrols it
        self.labels.append(((p.LIBRARY[0] + p.LIBRARY[2]) // 2, p.LIBRARY[1] + 8, "Central library"))
        n, s_, w, e = p.INNER_CORRIDOR
        self.zone("inner_corridor_north", n, 1, H["corridor"])
        self.zone("inner_corridor_south", s_, 1, H["corridor"])
        self.zone("inner_corridor_west", (w[0], n[3], w[2], s_[1]), 1, H["corridor"])
        self.zone("inner_corridor_east", (e[0], n[3], e[2], s_[1]), 1, H["corridor"])
        gx0 = min(b[0] for b in p.INNER_CORRIDOR)
        gz0 = min(b[1] for b in p.INNER_CORRIDOR)
        gx1 = max(b[2] for b in p.INNER_CORRIDOR)
        gz1 = max(b[3] for b in p.INNER_CORRIDOR)
        self.zone("guard_area", (gx0, gz0, gx1, gz1), 1, H["library"])
        # Every doorway its own zone: walking through one never leaves the level (no gap that
        # would drop the ambience and fire level changes at every door).
        for s in self.spaces:
            if s.kind == "door":
                self.zone(s.id, s.box, 1, s.height)
        self.zone("hatch", p.HATCH["hole"], 0, 0, ["book_hatch"])          # the trapdoors the fifth book opens
        self.zone("valve_corridor", p.VALVE_CORRIDOR, 1, H["corridor"])
        cx0, _, cx1, _ = p.VALVE_CORRIDOR
        cz = p.CHECKPOINT_Z
        self.zone("checkpoint", (cx0, cz - p.CHECKPOINT_DEPTH, cx1, cz), 1, H["corridor"], ["checkpoint"])
        mid = (cx0 + cx1) // 2                            # the double door the valves open
        self.zone("checkpoint_door", (mid - 1, cz, mid + 1, cz + 1), 1, 2, ["valve_door"])
        self.zone("antechamber", p.ANTECHAMBER, 1, H["antechamber"])
        floor, bh = self.bunker_floor, p.BUNKER["height"]
        self.zone("bunker_shaft", p.HATCH["hole"], floor + bh + 1, 0, ["observer_never"])
        self.zone("bunker_corridor", self.bunker_box, floor + 1, floor + bh, ["observer_never"])
        (t0, t1), (b0, b1) = p.COLLAPSE["trigger"], p.COLLAPSE["blocks"]
        self.zone("collapse", self.bunker_from_door(t0, t1), floor + 1, floor + bh, ["collapse"])
        self.zone("collapse_blocks", self.bunker_from_door(b0, b1), floor + 1, floor + bh, ["collapse_blocks"])
        self.zone("end", self.bunker_from_door(3, 1), floor + 1, floor + 3, ["end"])

        sx, sz, facing = p.PLAYER_START
        self.spawn("player_start", sx, 1, sz, facing, ["player"])
        self.spawn("checkpoint", (cx0 + cx1) // 2, 1, cz - p.CHECKPOINT_DEPTH // 2 - 1, "north", ["checkpoint"])
        gx, gz = p.GUARD_SPAWN
        self.spawn("observant_guard", gx, 1, gz, "north", ["observant_guard"], "guard_area")
        for i, (x, y, z) in enumerate(self.book_spots):
            self.spawn(f"book_{i + 1}", x, y, z, "north", ["book_spot"], "library")
        for i, (x, y, z, zone) in enumerate(self.box_spots):
            self.spawn(f"box_{i + 1}", x, y, z, "north", ["box_spot"], zone)
        if self.box_shelf:
            x, z, facing = self.box_shelf
            self.spawn("box_shelf", x, 1, z, facing, ["box_shelf"], "storage")
        # The book puzzle: the places along the librarian's desk round the hatch the books go back to.
        for i, (x, z, facing) in enumerate(self.book_returns):
            self.spawn(f"book_return_{i + 1}", x, 2, z, facing, ["book_return"], "library")
        for letter, (x, y, z) in self.valves:
            self.spawn(f"valve_{letter.lower()}", x, y, z, "west", ["valve"])
        for note_id, (x, y, z) in self.notes.items():
            self.spawn(note_id, x, y, z, "north", ["note"])
        # The loot (LootSpawner reads the tag: the item) and the dispensers that give water.
        for i, (item, x, z, room) in enumerate(self.loot):
            self.spawn(f"{item}_{i + 1}", x, 1, z, "north", [item], room)
        for x, z, room in self.water_sources:
            self.spawn(f"water_source_{room}", x, 1, z, "north", ["water_source"], room)
        self.labels += [(p.VALVE_CORRIDOR[0] - 10, p.CHECKPOINT_Z, "checkpoint"),
                        ((p.ANTECHAMBER[0] + p.ANTECHAMBER[2]) // 2, p.ANTECHAMBER[1] + 6, "Electrical room"),
                        ((p.STAIRS[0] + p.STAIRS[2]) // 2, p.STAIRS[3] + 3, "stairs up to B2")]


class Placement:
    """Where the plan goes in the world: a plan cell (x, y, z) is at
    (ax + step * x, cy + y, bz + step * z). step -1 turns the plan half round (its north
    faces the world's south), so states are turned too; step 1 keeps it as drawn."""

    def __init__(self, ax, cy, bz, step):
        self.ax, self.cy, self.bz, self.step = ax, cy, bz, step

    @classmethod
    def at_door(cls, door, joint, stairs_drop):
        """The plan's door cell (`joint`: x and z of the wall row behind the top landing, the
        landing's floor block at stairs_drop) onto a world door (x, feet y, wall z, step)."""
        wx, wy, wz, step = door
        jx, jz = joint
        return cls(wx - step * jx, wy - (stairs_drop + 1), wz - step * jz, step)

    def cell(self, x, y, z):
        return self.ax + self.step * x, self.cy + y, self.bz + self.step * z

    def point(self, x, y, z):
        """A position (block centres at .5) rather than a cell."""
        return (self.ax + self.step * (x - 0.5) + 0.5, self.cy + y, self.bz + self.step * (z - 0.5) + 0.5)

    def box(self, b):
        x0, y0, z0, x1, y1, z1 = b
        a, c = self.cell(x0, y0, z0), self.cell(x1, y1, z1)
        return (min(a[0], c[0]), a[1], min(a[2], c[2]), max(a[0], c[0]), c[1], max(a[2], c[2]))

    def yaw(self, yaw):
        return (yaw + 180) % 360 if self.step < 0 else yaw

    def state(self, state):
        if self.step > 0 or "[" not in state:
            return state
        nbt = state[state.index("{"):] if "{" in state else ""            # block entity data, kept as is
        bid, props = state[:len(state) - len(nbt) - 1].split("[", 1)
        out = []
        for kv in props.split(","):
            k, v = kv.split("=")
            if k == "facing":
                v = OPPOSITE.get(v, v)
            elif k in OPPOSITE:
                k = OPPOSITE[k]
            out.append((k, v))
        return f"{bid}[{','.join(f'{k}={v}' for k, v in sorted(out))}]{nbt}"

    def blocks(self, world):
        return {self.cell(*pos): self.state(st) for pos, st in world.blocks.items()}
