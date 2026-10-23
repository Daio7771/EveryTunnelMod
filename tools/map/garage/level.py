"""
A whole level: parking halls chained by corridors and ramps, an elevator lobby (START
"elevator") or an entry corridor from the previous level (START "entry") at the start, side
rooms, and an exit ramp down to the next level (END "ramp") or no exit at the end (END
"none": Level 2 leaves through a keycard door by hall 3). KEYCARD_HALL puts a glass parking
booth with the keycard in the middle of that hall.

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
from garage import mod_blocks
from garage.glyphs import Glyphs

ZONE_LETTERS = ["AB", "CD", "EF", "GH", "JK", "LM", "NP", "RS"]


class Level:
    def __init__(self, p, variant, glyphs=None):
        # The blocks for each role (STAND_INS + BLOCKS), with the mod's own in the mod version.
        blocks = {**p.STAND_INS, **p.BLOCKS, **(p.MOD_BLOCKS if variant == "mod" else {})}
        self.p = SimpleNamespace(**dict(vars(p), STAND_INS=blocks))
        p = self.p
        self.variant = variant
        self.S = p.STAND_INS
        self.world = B.World()
        self.glyphs = glyphs or Glyphs(self.S["glyph"])     # shared by the levels of one world
        self.zones = []          # (id, (x0, y0, z0, x1, y1, z1), tags)
        self.floor = {}          # (x, z) -> floor y, for the preview
        self.labels = []         # (x, z, text) for the preview
        self.fixtures = []       # (x, z, state) for the preview
        self.halls = []          # dicts with frame and report
        self.spawn = None        # (x, y, z, yaw)
        self.next_zones = []     # the next level's first zones (the landing past the exit shutter)
        self.next_spawn = None   # the next level's start, (x, y, z, yaw)
        self.landing = None      # END "ramp": the dark landing (far wall x, floor y, centre z, east)
        self.entry = None        # START "entry": (x, y, z) of the entry corridor's first cell (west end, centre)
        self.keycard = None      # KEYCARD_HALL: (x, y, z) where the keycard lies
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

    def exit_marking(self, dx, y, wall_z, step):
        """Over an exit door in a hall wall: the mod version mounts a lit EXIT sign on the hall
        side; the vanilla version paints EXIT on the wall. Both register the painted texts, so
        the two versions share one resource pack."""
        hall_side = wall_z - step
        facing = "south" if step < 0 else "north"
        x0, dir_x = (dx - 1, 1) if step < 0 else (dx + 1, -1)
        if self.variant == "mod":
            for t in ("EX", "IT"):
                self.glyphs.wall_state(t, facing)
            self.world.remove(dx, y + 3, hall_side)
            self.set(dx, y + 3, hall_side, f"every_tunnel:exit_sign[facing={facing}]")
        else:
            self.wall_sign(["EX", "IT"], x0, y + 3, wall_z, facing, (dir_x, 0))

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
                if p.END == "ramp":
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

        # Lobby and elevator, or the corridor in from the previous level, at hall 1's west end
        if p.START == "elevator":
            self.lobby(frames[0])
        else:
            self.entry_corridor(frames[0])

        # Connectors
        for i in range(n - 1):
            self.connector(frames[i], frames[i + 1], p.CONNECTORS[i], i)

        # Side rooms
        for spec, i, wall, door_x in rooms:
            self.side_room(spec, frames[i], wall, door_x, len(frames))

        # The keycard's booth
        if p.KEYCARD_HALL:
            self.booth(frames[p.KEYCARD_HALL - 1])

        # Exit ramp down to the next level
        if p.END == "ramp":
            self.exit_ramp(frames[-1])

        if self.variant == "mod":
            mod_blocks.finish(self.world, p, random.Random(f"{p.SEED}-mod-blocks"))
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
        # Elevator cabin behind the lobby's west wall: a doorway 2 wide and 2 high under a lintel.
        # Mod version: left open; the game closes the sliding doors for the intro and opens them
        # (zone tagged elevator_door). Vanilla version: two iron doors, open.
        cx1 = x0 - 2
        cx0 = cx1 - 2
        self.room(cx0, cx1, cz - 1, cz + 1, y, 3, wall=S["elevator_wall"])
        for zz in (cz - 1, cz, cz + 1):
            for yy in range(y + 1, y + 4):
                self.world.remove(x0 - 1, yy, zz)
        for zz, hinge in ((cz - 1, "left"), (cz, "right")):
            self.set(x0 - 1, y + 3, zz, S["elevator_wall"])
            if self.variant != "mod":
                self.set(x0 - 1, y + 1, zz, f"minecraft:iron_door[facing=east,half=lower,hinge={hinge},open=true,powered=false]")
                self.set(x0 - 1, y + 2, zz, f"minecraft:iron_door[facing=east,half=upper,hinge={hinge},open=true,powered=false]")
        self.zone("elevator_door", x0 - 1, y + 1, cz - 1, x0 - 1, y + 2, cz, ["elevator_door"])
        self.set(x0 - 1, y + 1, cz + 1, S["elevator_wall"])
        self.set(x0 - 1, y + 2, cz + 1, S["elevator_wall"])
        self.set(x0 - 1, y + 3, cz + 1, S["elevator_wall"])
        if self.variant == "mod":
            # The service cabin: checker plate floor, moving blankets hung on the north wall, a
            # handrail along the south one, the button panel beside the doors, floor indicators
            # over the doors inside and out (the intro counts them down), call buttons outside.
            for x in range(cx0, cx1 + 1):
                for z in range(cz - 1, cz + 2):
                    self.set(x, y, z, "every_tunnel:checker_plate")
                for yy in (y + 1, y + 2, y + 3):
                    self.set(x, yy, cz - 1, "every_tunnel:moving_pad[facing=south]")
                self.set(x, y + 1, cz + 1, "every_tunnel:handrail[facing=north]")
            self.set(cx1, y + 2, cz + 1, "every_tunnel:elevator_buttons[facing=west]")
            last = len(p.ELEVATOR_FLOORS) - 1
            self.set(cx1, y + 3, cz, f"every_tunnel:floor_indicator[facing=west,floor={last},moving=false]")
            self.set(x0, y + 3, cz, f"every_tunnel:floor_indicator[facing=east,floor={last},moving=false]")
            self.set(x0, y + 2, cz + 1, "every_tunnel:call_buttons[facing=east]")
        else:
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
        if kind == "keycard_exit":
            self.keycard_exit(spec, f, wall, dx, wall_z, step, facing_out)
            return
        self.room(xa, xb, za, zb, y, 3 if kind != "dead_end" else H)
        # doorway through both walls (the hall's and the room's)
        self.carve(dx, dx, min(wall_z, z_near), max(wall_z, z_near), y + 1, y + 2)
        if kind == "storage":
            self.set(dx, y + 1, wall_z, f"{S['storage_door']}[facing={facing_out},half=lower,hinge=left,open=false,powered=false]")
            self.set(dx, y + 2, wall_z, f"{S['storage_door']}[facing={facing_out},half=upper,hinge=left,open=false,powered=false]")
            for x in range(xa, xb + 1):
                for z in (z_far,):
                    self.set(x, y + 1, z, S["drum"] if (x + z) % 2 else S["box"])
                    if self.rng.random() < 0.5:
                        self.set(x, y + 2, z, S["box"])
            self.set(xa, y + 1, z_near + step, S["pallet"])
            self.fixture(dx, z_near + step * 2, y + 4, "dead")
            label = "storage room"
        elif kind == "electrical":
            self.set(dx, y + 1, wall_z, f"{S['electrical_door']}[facing={facing_out},half=lower,hinge=right,open=true,powered=false]")
            self.set(dx, y + 2, wall_z, f"{S['electrical_door']}[facing={facing_out},half=upper,hinge=right,open=true,powered=false]")
            far_facing = "south" if north else "north"
            for x in range(xa, xb + 1):                         # switch panels on the far wall
                self.set(x, y + 2, z_far, f"minecraft:iron_trapdoor[facing={far_facing},half=top,open=true]")
            for yy in (y + 1, y + 2, y + 3):
                self.set(xb, yy, (za + zb) // 2, S["cable"])
            self.fixture(dx, (za + zb) // 2, y + 4, "dim")
            label = "electrical room"
        elif kind == "stairwell":
            self.set(dx, y + 1, wall_z, f"{S['emergency_door']}[facing={facing_out},half=lower,hinge=left,open=false,powered=false]")
            self.set(dx, y + 2, wall_z, f"{S['emergency_door']}[facing={facing_out},half=upper,hinge=left,open=false,powered=false]")
            self.exit_marking(dx, y, wall_z, step)
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

    def keycard_exit(self, spec, f, wall, dx, wall_z, step, facing_out):
        """
        Level 2's way out: an iron door marked EXIT in the hall wall, a card reader beside it on
        the hall side (zone tagged keycard_door), and behind it emergency stairs going down,
        EXIT_STEPS steps, enclosed and dark, to a wall marked with the next level's sign. A few
        steps down is the zone tagged end, where the demo ends.
        """
        p, S = self.p, self.S
        y = f["hy"]
        xa, xb = dx - 1, dx + 1
        steps = p.EXIT_STEPS
        stair_facing = "south" if step < 0 else "north"          # stairs face back up, towards the door
        hall_side = wall_z - step
        # Cell k from the door: 0 is flat (floor y), 1..steps are stairs one lower each (the
        # first one level with the floor), steps + 1 is the flat bottom.
        def floor_of(k):
            return y if k == 0 else y - (k - 1) if k <= steps else y - steps

        # Every cell the stairwell takes must be free (it digs under the neighbouring hall).
        cells = []
        for k in range(0, steps + 2):
            z = wall_z + step * (k + 1)
            floor_y = floor_of(k)
            for x in range(xa - 1, xb + 2):
                for yy in range(floor_y - 1, floor_y + 5):
                    cells.append((x, yy, z))
        bottom = wall_z + step * (steps + 2)
        cells += [(x, yy, bottom) for x in range(xa - 1, xb + 2) for yy in range(y - steps - 1, y - steps + 5)]
        taken = [c for c in cells if not self.world.free(*c)]
        if taken:
            raise SystemExit(f"keycard_exit by hall {spec['hall'] + 1}: the stairwell runs into other blocks, e.g. {taken[:3]}")
        for k in range(0, steps + 2):
            z = wall_z + step * (k + 1)
            last = k == steps + 1
            floor_y = floor_of(k)
            for x in range(xa - 1, xb + 2):
                side = x in (xa - 1, xb + 1)
                self.set(x, floor_y - 1, z, S["floor"])
                if k == 0 or last or side:
                    self.set(x, floor_y, z, S["floor"])
                else:
                    self.set(x, floor_y, z, f"minecraft:andesite_stairs[facing={stair_facing},half=bottom,shape=straight]")
                self.set(x, floor_y + 4, z, S["ceiling"])
                if side:
                    for yy in range(floor_y + 1, floor_y + 4):
                        self.set(x, yy, z, S["wall"])
        # The sign at the bottom, on the end wall.
        bottom_z = wall_z + step * (steps + 2)
        for x in range(xa - 1, xb + 2):
            for yy in range(y - steps - 1, y - steps + 5):
                self.set(x, yy, bottom_z, S["wall"])
        inside = "south" if step < 0 else "north"
        self.wall_sign(list(p.NEXT_LEVEL_SIGN), dx - 1 if step < 0 else dx + 1, y - steps + 2, bottom_z,
                       inside, (1, 0) if step < 0 else (-1, 0))
        # Door through the hall wall, EXIT over it, the reader on the hall side.
        self.carve(dx, dx, wall_z, wall_z, y + 1, y + 2)
        self.set(dx, y + 1, wall_z, f"{S['emergency_door']}[facing={facing_out},half=lower,hinge=left,open=false,powered=false]")
        self.set(dx, y + 2, wall_z, f"{S['emergency_door']}[facing={facing_out},half=upper,hinge=left,open=false,powered=false]")
        for x in range(dx - 1, dx + 2):                          # a clear spot in front of the door
            for z in (hall_side, hall_side - step):
                for yy in range(y + 1, y + 4):
                    self.world.remove(x, yy, z)
        self.exit_marking(dx, y, wall_z, step)
        reader_facing = "south" if step < 0 else "north"
        if self.variant == "mod":
            reader = f"every_tunnel:card_reader[facing={reader_facing},unlocked=false]"
        else:
            reader = f"minecraft:polished_blackstone_button[face=wall,facing={reader_facing},powered=false]"
        self.set(dx + 1, y + 2, hall_side, reader)
        self.zone("keycard_door", dx - 1, y + 1, min(wall_z, hall_side), dx + 1, y + 2, max(wall_z, hall_side), ["keycard_door"])
        za, zb = sorted((wall_z + step, wall_z + step * (steps + 1)))
        self.zone(f"keycard_exit_hall_{spec['hall'] + 1}", xa, y - steps, za, xb, y + 4, zb)
        e0, e1 = sorted((wall_z + step * 4, wall_z + step * 6))
        self.zone("end", xa, y - 7, e0, xb, y, e1, ["end"])
        self.labels.append((xa, za - 1 if step < 0 else zb + 2, f"EXIT (keycard): stairs down to {p.NEXT_LEVEL_SIGN}"))

    def booth(self, f):
        """
        A glass parking booth in the middle of the hall's aisle, left tidy as if the guard will
        be back: a metal frame, glass from waist height up, a door towards the north wall, a
        counter along the back with (mod version) the CCTV monitor, the keycard, the logbook
        with a mug and an old flashlight, and the phone; the chair tucked in, an empty key rack
        on the wall, a flickering light over the keycard. Whatever stood there is cleared first.
        """
        p, S = self.p, self.S
        hp, g = f["hp"], f["g"]
        y = f["hy"]
        cx, cz = f["hx"] + hp.LENGTH // 2, f["hz"] + g.center_z
        x0, x1, z0, z1 = cx - 3, cx + 2, cz - 2, cz + 1
        door_x = cx - 1
        for x in range(x0 - 1, x1 + 2):
            for z in range(z0 - 1, z1 + 2):
                for yy in range(y + 1, y + p.CEILING_HEIGHT + 1):
                    self.world.remove(x, yy, z)
        metal = S["elevator_wall"]
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                edge_x, edge_z = x in (x0, x1), z in (z0, z1)
                self.set(x, y + 4, z, metal)                                   # roof
                if not (edge_x or edge_z):
                    continue
                if x == door_x and z == z0:                                     # the door
                    self.set(x, y + 3, z, metal)
                    continue
                if edge_x and edge_z:                                           # corner posts
                    for yy in (y + 1, y + 2, y + 3):
                        self.set(x, yy, z, metal)
                    continue
                self.set(x, y + 1, z, metal)
                pane = "minecraft:glass_pane[east=true,west=true,north=false,south=false,waterlogged=false]" if edge_z else \
                    "minecraft:glass_pane[east=false,west=false,north=true,south=true,waterlogged=false]"
                self.set(x, y + 2, z, pane)
                self.set(x, y + 3, z, pane)
        counter_z = z1 - 1
        for x in range(x0 + 1, x1):                                              # the counter
            self.set(x, y + 1, counter_z, metal)
        self.fixture(door_x, counter_z, y + 4, "dim")
        self.keycard = (door_x + 0.5, y + 2, counter_z + 0.5)
        if self.variant == "mod":
            self.set(x0 + 1, y + 2, counter_z, "every_tunnel:cctv_monitor[facing=north]")
            self.set(cx, y + 2, counter_z, "every_tunnel:logbook[facing=north]")
            self.set(x1 - 1, y + 2, counter_z, "every_tunnel:desk_phone[facing=north]")
            self.set(x1 - 1, y + 1, z0 + 1, "every_tunnel:office_chair[facing=south]")
            self.set(x1, y + 3, z0 + 1, metal)                                   # a panel to hang the keys on
            self.set(x1 - 1, y + 3, z0 + 1, "every_tunnel:key_rack[facing=west]")
        self.labels.append((cx, z1 + 3, "booth (keycard)"))

    def entry_corridor(self, f):
        """
        START "entry": a corridor ENTRY_LENGTH long from hall 1's west opening, open at its west
        end, where the previous level's landing joins it (see attach in map_generator.py).
        """
        p, S = self.p, self.S
        H = p.CEILING_HEIGHT
        g = f["g"]
        cz = f["hz"] + g.center_z
        y = f["hy"]
        x1 = f["hx"] - 1
        x0 = x1 - p.ENTRY_LENGTH + 1
        z0, z1 = cz - 2, cz + 1
        self.room(x0, x1, z0, z1, y, H, open_sides=("west", "east"))
        # The column west of the corridor is the previous level's (its landing's far wall with the
        # doorway): leave it out, so nothing of this level lands on it.
        for z in range(z0 - 1, z1 + 2):
            for yy in range(y, y + H + 2):
                self.world.remove(x0 - 1, yy, z)
        self.fixture((x0 + x1) // 2, cz, y + H + 1, "dim")
        self.wear([(x, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)], 0.8)
        self.zone("entry", x0, y, z0, x1, y + H + 1, z1)
        self.entry = (x0, y, cz)
        self.labels.append((x0 + 1, z0 - 2, f"from {p.PREVIOUS_LEVEL_SIGN}"))

    def open_landing(self):
        """A doorway, 3 wide and 2 high, in the landing's far wall under the next level's sign."""
        land = self.landing
        for z in range(land["cz"] - 1, land["cz"] + 2):
            for yy in (land["ly"] + 1, land["ly"] + 2):
                self.world.remove(land["far"], yy, z)

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
        self.landing = {"far": far, "ly": ly, "cz": cz, "east": east}
        self.wall_sign(list(p.NEXT_LEVEL_SIGN), far, ly + 3, cz - 1 if east else cz + 1,
                       "west" if east else "east", (0, 1) if east else (0, -1))
        self.wear([(x, z) for x in range(xa, xb + 1) for z in range(cz - 3, cz + 4)], 0.9)
        self.zone("exit_ramp", min(xs), ly, z0, max(xs), top + H + 1, z1, ["exit"])
        self.labels.append((xa, cz + 5, f"exit ramp -> {p.NEXT_LEVEL_SIGN}"))

        # The rolling shutter at the foot of the ramp (its last, flat column): the game fills the
        # opening with the mod's shutter once the player is past it, so there is no way back.
        # Its housing is a metal strip in the ceiling. The landing beyond is the next level's start.
        sx = xs[-1]
        for z in range(z0, z1 + 1):
            self.set(sx, ly + H + 1, z, S["elevator_wall"])
        self.next_zones = [
            ("shutter", (sx, ly + 1, z0, sx, ly + H, z1), ["shutter"]),
            ("landing", (xa, ly, cz - 3, xb, ly + H + 1, cz + 3), ["start"]),
        ]
        self.next_spawn = (lx0 + d * 3 + 0.5, ly + 1, cz + 0.5, -90 if east else 90)
