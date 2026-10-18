"""
A whole level: parking halls chained by corridors and ramps, an elevator lobby at the
start, side rooms, and an exit ramp down to the next level.

Layout (top view, Minecraft directions: east = +x, south = +z):

    [elevator][lobby] HALL 1 ---------------------->      hall 1 runs east
                                              |corr|
              <---------------------- HALL 2           hall 2 runs west
              |corr|
              HALL 3 ---------------------->            ...
                                              |ramp|    (half a level down)
    ...                         HALL N ---------> [exit ramp down] [B2 landing]

Each hall is built by build.build() in its own frame and copied in; corridors, ramps and
rooms are built here directly. Coordinates are relative to hall 1's corner (floor y = 0).
"""

import random
from types import SimpleNamespace

from garage import build as B
from garage.glyphs import Glyphs

ZONE_LETTERS = ["AB", "CD", "EF", "GH", "JK", "LM", "NP", "RS"]


class Level:
    def __init__(self, p, variant):
        self.p = p
        self.variant = variant
        self.S = p.STAND_INS
        self.world = B.World()
        self.glyphs = Glyphs(self.S["glyph"])
        self.zones = []          # (id, (x0, y0, z0, x1, y1, z1), tags)
        self.floor = {}          # (x, z) -> floor y, for the preview
        self.labels = []         # (x, z, text) for the preview
        self.fixtures = []       # (x, z, state) for the preview
        self.halls = []          # dicts with frame and report
        self.spawn = None        # (x, y, z, yaw)
        self.rng = random.Random(f"{p.SEED}-level")

    # --- small builders --------------------------------------------------------------

    def set(self, x, y, z, state):
        self.world.set(x, y, z, state)

    def room(self, x0, x1, z0, z1, y, h, open_sides=(), wall=None):
        """Floor at y, air y+1..y+h, ceiling at y+h+1, walls around the box x0..x1 / z0..z1."""
        S = self.S
        wall = wall or S["wall"]
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                inside = x0 <= x <= x1 and z0 <= z <= z1
                self.set(x, y, z, S["floor"])
                self.set(x, y + h + 1, z, S["ceiling"])
                if inside:
                    self.floor[(x, z)] = y
                    continue
                side = "west" if x < x0 else "east" if x > x1 else "north" if z < z0 else "south"
                if side in open_sides:
                    continue
                for yy in range(y + 1, y + h + 1):
                    block = wall
                    if wall == S["wall"]:
                        if yy == y + 1:
                            block = S["wall_base"]
                        elif yy == y + self.p.WALL_STRIPE_HEIGHT:
                            block = S["paint_band"]
                    self.set(x, yy, z, block)

    def carve(self, x0, x1, z0, z1, y0, y1):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(y0, y1 + 1):
                    self.world.remove(x, y, z)

    def fixture(self, x, z, ceiling_y, state):
        """A light in the ceiling at (x, z): state lit / dead / dim (vanilla or mod blocks)."""
        S = self.S
        self.fixtures.append((x, z, state))
        if self.variant == "mod":
            mode = {"lit": ("on", "true"), "dead": ("off", "false"), "dim": ("flicker", "true")}[state]
            self.set(x, ceiling_y - 1, z, self.p.MOD_LIGHT % mode)
        elif state == "dim":
            self.set(x, ceiling_y - 1, z, f"{S['fixture_dim']}[up=true]")
        else:
            self.set(x, ceiling_y, z, S["fixture_lit"] if state == "lit" else S["fixture_dead"])

    def light_state(self, quality):
        """quality 1 = all lit .. 0 = mostly dead/dim."""
        r = self.rng.random()
        if r < quality:
            return "lit"
        return self.rng.choice(["dim", "dead", "dead"])

    def wall_sign(self, tiles, x, y, z, facing, step):
        """Sign tiles starting at (x, y, z), advancing by step (dx, dz)."""
        for i, t in enumerate(tiles):
            self.set(x + step[0] * i, y, z + step[1] * i, self.glyphs.wall_state(t, facing))

    def wear(self, cells, amount):
        """Floor decals on some of the given (x, z) cells (not on ramp slabs)."""
        D = self.S["decal"]
        for x, z in cells:
            y = self.floor.get((x, z))
            if y is None or self.world.get(x, y, z) != self.S["floor"]:
                continue
            if self.world.free(x, y + 1, z) and self.rng.random() < amount * 0.14:
                self.set(x, y + 1, z, f"{D}[down=true]")

    def zone(self, zid, x0, y0, z0, x1, y1, z1, tags=()):
        self.zones.append((zid, (x0, y0, z0, x1, y1, z1), list(tags)))

    # --- the level ---------------------------------------------------------------------

    def compose(self):
        p = self.p
        H = p.CEILING_HEIGHT
        configs = []
        for i, overrides in enumerate(p.HALLS):
            hp = SimpleNamespace(**vars(p))
            for k, v in overrides.items():
                setattr(hp, k, v)
            hp.SEED = p.SEED * 100 + i
            configs.append((hp, B.Garage(hp)))

        # Openings and frames. Hall i runs east when i is even, west when odd.
        frames = []
        hx, hy, hz = 0, 0, 0
        n = len(configs)
        for i, (hp, g) in enumerate(configs):
            east = i % 2 == 0
            openings = []
            if i == 0:
                openings.append({"side": "west", "a": g.center_z - 2, "b": g.center_z + 1, "height": 3})
            if i > 0:
                a, b = frames[i - 1]["exit_x"]
                # entry on this hall's north wall, at the end where the previous hall left
                ea = (g.pillar_xs[-2] + 2) if not east else (g.pillar_xs[0] + 2)
                hx = frames[i - 1]["hx"] + a - ea
                openings.append({"side": "north", "a": ea, "b": ea + 3, "height": 3})
            if i < n - 1:
                xa = (g.pillar_xs[-2] + 2) if east else (g.pillar_xs[0] + 2)
                openings.append({"side": "south", "a": xa, "b": xa + 3, "height": 3})
                exit_x = (xa, xa + 3)
            else:
                end = "east" if east else "west"
                openings.append({"side": end, "a": g.center_z - 2, "b": g.center_z + 1, "height": 3})
                exit_x = None
            frames.append({"hx": hx, "hy": hy, "hz": hz, "east": east, "openings": openings,
                           "exit_x": exit_x, "hp": hp, "g": g})
            if i < n - 1:
                c = p.CONNECTORS[i]
                hz = hz + hp.WIDTH + c["length"]
                hy = hy - c.get("drop", 0)

        # Side rooms are attached to free stretches of the hall walls: add their doorways first.
        rooms = []
        for spec in p.SIDE_ROOMS:
            i = spec["hall"]
            if i >= n:
                continue
            f = frames[i]
            g = f["g"]
            # The wall facing the previous hall (north) has the entry at one end: use the other end.
            if i == 0:
                wall = "north"
            elif i == n - 1:
                wall = "south"
            else:
                wall = "north"
            entry_end_east = not f["east"]
            if wall == "north" and i > 0:
                door_x = (g.pillar_xs[0] + 3) if entry_end_east else (g.pillar_xs[-2] + 3)
            else:
                door_x = (g.pillar_xs[1] + 3)
            f["openings"].append({"side": wall, "a": door_x, "b": door_x, "height": 2})
            rooms.append((spec, i, wall, door_x))

        # Halls
        for i, f in enumerate(frames):
            hp, g = f["hp"], f["g"]
            w, g, report, lights = B.build(hp, self.variant, self.glyphs, f["openings"], ZONE_LETTERS[i % len(ZONE_LETTERS)])
            self.merge_hall(w, f, report, lights, i)

        # Lobby and elevator in front of hall 1's west end
        self.lobby(frames[0])

        # Connectors
        for i in range(n - 1):
            self.connector(frames[i], frames[i + 1], p.CONNECTORS[i], i)

        # Side rooms
        for spec, i, wall, door_x in rooms:
            self.side_room(spec, frames[i], wall, door_x, len(frames))

        # Exit ramp down to the next level
        self.exit_ramp(frames[-1])
        return self

    def merge_hall(self, w, f, report, lights, i):
        hx, hy, hz = f["hx"], f["hy"], f["hz"]
        hp, g = f["hp"], f["g"]
        for (x, y, z), st in w.blocks.items():
            self.world.blocks[(hx + x, hy + y, hz + z)] = st
        for (x, z), tag in w.tags.items():
            self.world.tags[(hx + x, hz + z)] = tag
        for x in range(1, hp.LENGTH - 1):
            for z in range(1, hp.WIDTH - 1):
                self.floor[(hx + x, hz + z)] = hy
        for x0, x1, z, state in lights:
            for x in range(x0, x1 + 1):
                if state != "gap":
                    self.fixtures.append((hx + x, hz + z, state))
        H = hp.CEILING_HEIGHT
        self.zone(f"hall_{i + 1}", hx, hy, hz, hx + hp.LENGTH - 1, hy + H + 1, hz + hp.WIDTH - 1)
        self.labels.append((hx + hp.LENGTH // 2, hz + hp.WIDTH // 2, f"Hall {i + 1}"))
        f["report"] = report
        self.halls.append({"index": i, "frame": f, "report": report})

    def lobby(self, f):
        p, S = self.p, self.S
        H = p.CEILING_HEIGHT
        g = f["g"]
        cz = f["hz"] + g.center_z
        x1 = f["hx"] - 1                       # the hall's west wall (with its opening) is the lobby's east wall
        x0 = x1 - p.LOBBY_LENGTH + 1
        z0, z1 = cz - 4, cz + 4
        y = f["hy"]
        self.room(x0, x1, z0, z1, y, H, open_sides=("east",))
        self.fixture(x0 + 2, cz, y + H + 1, "lit")
        self.fixture(x0 + 2, cz - 1, y + H + 1, "lit")
        self.wear([(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)], 0.3)
        # Elevator cabin behind the lobby's west wall, doors open.
        cx1 = x0 - 2
        cx0 = cx1 - 2
        self.room(cx0, cx1, cz - 1, cz + 1, y, 3, wall=S["duct"])
        for zz in (cz - 1, cz, cz + 1):
            for yy in range(y + 1, y + 4):
                self.world.remove(x0 - 1, yy, zz)
        for zz, hinge in ((cz - 1, "left"), (cz, "right")):
            self.set(x0 - 1, y + 1, zz, f"minecraft:iron_door[facing=east,half=lower,hinge={hinge},open=true,powered=false]")
            self.set(x0 - 1, y + 2, zz, f"minecraft:iron_door[facing=east,half=upper,hinge={hinge},open=true,powered=false]")
        self.set(x0 - 1, y + 1, cz + 1, S["duct"])
        self.set(x0 - 1, y + 2, cz + 1, S["duct"])
        self.set(x0 - 1, y + 3, cz + 1, S["duct"])
        self.set(x0, y + 2, cz + 2, "minecraft:stone_button[face=wall,facing=east,powered=false]")   # call button
        self.set(cx0, y + 2, cz + 1, "minecraft:polished_blackstone_button[face=wall,facing=east,powered=false]")  # cabin panel
        self.wall_sign(list(p.LEVEL_SIGN), x0 - 1, y + 3, cz + 4, "east", (0, -1))
        self.fixture(cx0 + 1, cz, y + 4, "lit")
        self.spawn = (cx0 + 1.5, y + 1, cz + 0.5, -90)
        self.zone("elevator", cx0, y, cz - 1, x0 - 1, y + 4, cz + 1, ["start"])     # includes the doorway
        self.zone("lobby", x0, y, z0, x1, y + H + 1, z1)
        self.labels.append((x0 + 2, z0 - 2, "Lobby / elevator (start)"))

    def connector(self, fa, fb, c, i):
        p, S = self.p, self.S
        H = p.CEILING_HEIGHT
        a, b = fa["exit_x"]
        x0, x1 = fa["hx"] + a, fa["hx"] + b
        z0 = fa["hz"] + fa["hp"].WIDTH          # first cell south of hall a's south wall
        z1 = fb["hz"] - 1                       # last cell north of hall b's north wall
        drop = c.get("drop", 0)
        top = fa["hy"]                          # floor block level at the start
        for k, z in enumerate(range(z0, z1 + 1)):
            t = min(max(k - 1, 0), 2 * drop)
            surface = top + 1 - 0.5 * t         # walking surface height
            for x in range(x0 - 1, x1 + 2):
                wall = x in (x0 - 1, x1 + 1)
                if surface == int(surface):
                    fy = int(surface) - 1
                    self.set(x, fy, z, S["floor"])
                else:
                    fy = int(surface - 0.5) - 1
                    self.set(x, fy, z, S["floor"])
                    if not wall:
                        self.set(x, fy + 1, z, "minecraft:smooth_stone_slab[type=bottom]")
                ceil = int(-(-surface // 1)) + H
                self.set(x, ceil, z, S["ceiling"])
                low = fy + 1
                for yy in range(low, ceil):
                    if wall:
                        self.set(x, yy, z, S["wall"])
                if not wall:
                    self.floor[(x, z)] = fy
            if k % 5 == 2 and drop == 0:
                self.fixture(x0 + 1, z, int(-(-surface // 1)) + H, self.light_state(c.get("light", 0.7)))
                self.fixture(x0 + 2, z, int(-(-surface // 1)) + H, self.light_state(c.get("light", 0.7)))
            elif drop and k == (z1 - z0) // 2:
                self.fixture(x0 + 1, z, int(-(-surface // 1)) + H, "dim")
        self.wear([(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)], 0.5)
        # Sign on the corridor's east wall: level and an arrow pointing on (+z, the way forward).
        if z1 - z0 >= 4:
            self.wall_sign(list(p.LEVEL_SIGN) + [">"], x1 + 1, top + 3, z0 + 1, "west", (0, 1))
        kind = "ramp" if drop else "corridor"
        self.zone(f"{kind}_{i + 1}", x0, min(top, top - drop), z0, x1, top + H + 1, z1)
        self.labels.append((x1 + 2, (z0 + z1) // 2, "ramp (half level down)" if drop else "corridor"))

    def side_room(self, spec, f, wall, door_x, n):
        p, S = self.p, self.S
        H = p.CEILING_HEIGHT
        kind = spec["type"]
        hx, hy, hz = f["hx"], f["hy"], f["hz"]
        dx = hx + door_x
        y = hy
        north = wall == "north"
        wall_z = hz if north else hz + f["hp"].WIDTH - 1
        step = -1 if north else 1
        facing_out = "north" if north else "south"
        depth = spec.get("depth", 5)
        z_near = wall_z + step
        z_far = wall_z + step * depth
        za, zb = min(z_near, z_far), max(z_near, z_far)
        width = spec.get("width", 5)
        xa, xb = dx - width // 2, dx - width // 2 + width - 1
        if kind == "dead_end":
            xa, xb = dx - 1, dx + 1
        self.room(xa, xb, za, zb, y, 3 if kind != "dead_end" else H)
        # doorway through both walls (the hall's and the room's)
        self.carve(dx, dx, min(wall_z, z_near), max(wall_z, z_near), y + 1, y + 2)
        if kind == "storage":
            self.set(dx, y + 1, wall_z, f"minecraft:spruce_door[facing={facing_out},half=lower,hinge=left,open=false,powered=false]")
            self.set(dx, y + 2, wall_z, f"minecraft:spruce_door[facing={facing_out},half=upper,hinge=left,open=false,powered=false]")
            for x in range(xa, xb + 1):
                for z in (z_far,):
                    self.set(x, y + 1, z, "minecraft:barrel[facing=up,open=false]" if (x + z) % 2 else S["box"])
                    if self.rng.random() < 0.5:
                        self.set(x, y + 2, z, S["box"])
            self.set(xa, y + 1, z_near + step, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]")
            self.fixture(dx, z_near + step * 2, y + 4, "dead")
            label = "storage room"
        elif kind == "electrical":
            self.set(dx, y + 1, wall_z, f"minecraft:dark_oak_door[facing={facing_out},half=lower,hinge=right,open=true,powered=false]")
            self.set(dx, y + 2, wall_z, f"minecraft:dark_oak_door[facing={facing_out},half=upper,hinge=right,open=true,powered=false]")
            far_facing = "south" if north else "north"
            for x in range(xa, xb + 1):                         # switch panels on the far wall
                self.set(x, y + 2, z_far, f"minecraft:iron_trapdoor[facing={far_facing},half=top,open=true]")
            for yy in (y + 1, y + 2, y + 3):
                self.set(xb, yy, (za + zb) // 2, "minecraft:chain[axis=y]")
            self.fixture(dx, (za + zb) // 2, y + 4, "dim")
            label = "electrical room"
        elif kind == "stairwell":
            self.set(dx, y + 1, wall_z, f"minecraft:iron_door[facing={facing_out},half=lower,hinge=left,open=false,powered=false]")
            self.set(dx, y + 2, wall_z, f"minecraft:iron_door[facing={facing_out},half=upper,hinge=left,open=false,powered=false]")
            inside = "south" if north else "north"
            self.wall_sign(["EX", "IT"], dx - 1 if north else dx + 1, y + 3, wall_z,
                           inside, (1, 0) if north else (-1, 0))
            for k in range(min(3, depth - 1)):
                z = z_near + step * (k + 1)
                for x in range(xa, xb + 1):
                    self.set(x, y - k, z, f"minecraft:andesite_stairs[facing={'south' if north else 'north'},half=bottom,shape=straight]")
                    self.world.remove(x, y - k + 1, z)
            label = "locked door marked EXIT"
        else:  # dead_end
            self.fixture(dx, z_far, y + H + 1, "dim")
            self.fixture(dx, z_near + step, y + H + 1, "dead")
            label = "dead-end corridor"
        self.wear([(x, z) for x in range(xa, xb + 1) for z in range(za, zb + 1)], 0.7)
        self.zone(f"{kind}_hall_{spec['hall'] + 1}", xa, y, za, xb, y + H + 1, zb)
        self.labels.append((xa, za - 1 if north else zb + 2, label))

    def exit_ramp(self, f):
        p, S = self.p, self.S
        H = p.CEILING_HEIGHT
        g, hp = f["g"], f["hp"]
        east = f["east"]
        cz = f["hz"] + g.center_z
        z0, z1 = cz - 2, cz + 1
        drop = p.EXIT_DROP
        top = f["hy"]
        length = 2 * drop + 2
        start = f["hx"] + hp.LENGTH if east else f["hx"] - 1
        d = 1 if east else -1
        xs = [start + d * k for k in range(length)]
        for k, x in enumerate(xs):
            t = min(max(k - 1, 0), 2 * drop)
            surface = top + 1 - 0.5 * t
            for z in range(z0 - 1, z1 + 2):
                wall = z in (z0 - 1, z1 + 1)
                if surface == int(surface):
                    fy = int(surface) - 1
                    self.set(x, fy, z, S["floor"])
                else:
                    fy = int(surface - 0.5) - 1
                    self.set(x, fy, z, S["floor"])
                    if not wall:
                        self.set(x, fy + 1, z, "minecraft:smooth_stone_slab[type=bottom]")
                ceil = int(-(-surface // 1)) + H
                self.set(x, ceil, z, S["ceiling"])
                if wall:
                    for yy in range(fy + 1, ceil):
                        self.set(x, yy, z, S["wall"])
                else:
                    self.floor[(x, z)] = fy
            if k == length // 2:
                self.fixture(x, cz, int(-(-surface // 1)) + H, "dim")
        # Landing at the bottom: dark, with the next level's sign.
        ly = top - drop
        lx0 = xs[-1] + d
        lx1 = lx0 + d * (p.LANDING_LENGTH - 1)
        xa, xb = min(lx0, lx1), max(lx0, lx1)
        self.room(xa, xb, cz - 3, cz + 3, ly, H, open_sides=("west",) if east else ("east",))
        for z in range(cz - 3, cz + 4):                      # close the landing's open side around the ramp
            if not (z0 <= z <= z1):
                for yy in range(ly + 1, ly + H + 1):
                    self.set(lx0 - d, yy, z, S["wall"])
        far = xb + 1 if east else xa - 1
        self.wall_sign(list(p.NEXT_LEVEL_SIGN), far, ly + 3, cz - 1 if east else cz + 1,
                       "west" if east else "east", (0, 1) if east else (0, -1))
        self.wear([(x, z) for x in range(xa, xb + 1) for z in range(cz - 3, cz + 4)], 0.9)
        self.zone("exit_ramp", min(xs), ly, z0, max(xs), top + H + 1, z1)
        self.zone("exit", xa, ly, cz - 3, xb, ly + H + 1, cz + 3, ["exit"])
        self.labels.append((xa, cz + 5, f"exit ramp -> {p.NEXT_LEVEL_SIGN}"))
