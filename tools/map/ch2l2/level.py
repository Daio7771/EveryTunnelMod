"""
Chapter 2, Level 2: the catacombs under the factory. Reads layout.json, where everything the map is made of is data, and
turns it into cells: the air of each space, the openings between spaces, the features that stand in them (the pump room's
overlook and channels, the walkways, the stairs, ladders and rails), the water, and the shell round all of it with the look
of the space it faces. This file writes nothing: generate.py turns a Level into the plan the mod builds (map.json), the
named places (anchors.json) and the flood's stages (flood.json); checks.py walks it; preview.py draws it.

Plan coordinates: x east, z south, y up. A space's floor is the y of its first cell of air; its floor's blocks lie one
under. A box is [x0, z0, x1, z1] with the far ends left out. Two spaces are told apart by a wall at least a block thick;
an opening is a passage through a wall one block thick, and a link joins two spaces whose air simply meets.

The look: each space has a style of layout.json (`masonry`, the old catacombs; `drain`, the factory's drainage, Level 5's
drain re-skinned by the world's resource pack; `machine`). A style gives each role of the shell (wall, dado, floor, ceiling)
one block or a weighted mix; the mod picks a mix's block for each cell with `cell_hash` (the same function in Kotlin,
`chapter2.core.MapPlan.pick`), so the seed alone decides those details and the preview matches the game.
"""

import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
LAYOUT = HERE / "layout.json"

AROUND = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]
SIDES = [(1, 0), (-1, 0), (0, 1), (0, -1)]
FACING = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
# Where several styles face one block of shell (a wall one block thick between two spaces), the first of these wins: the
# old masonry was there first, the factory built against it.
STYLE_ORDER = ["machine", "masonry", "drain"]
# Blocks that water flows into (it waterlogs them, or they are not in its way): a flood stage must never touch one of these
# outside its own spaces. Everything else the plan places is watertight.
PERMEABLE = ("minecraft:air", "minecraft:iron_bars", "_slab", "_stairs", "_wall", "every_tunnel:ceiling_cable", "every_tunnel:hanging_cable", "every_tunnel:puddle",
             "every_tunnel:metal_grating", "every_tunnel:drain_ladder", "minecraft:ladder", "every_tunnel:cage_lamp", "_trapdoor",
             "_fence", "minecraft:chain")
WATER = "minecraft:water"
AIR = "minecraft:air"


def cell_hash(x, y, z, seed):
    """A well-mixed 32-bit number for a cell and a seed. Kotlin's MapPlan.cellHash is the same function, bit for bit."""
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (seed * 2654435761)
    h &= 0xFFFFFFFF
    h ^= h >> 16
    h = (h * 0x45D9F3B) & 0xFFFFFFFF
    h ^= h >> 16
    h = (h * 0x45D9F3B) & 0xFFFFFFFF
    h ^= h >> 16
    return h


def pick(mix, x, y, z, seed):
    """The block of a weighted mix [[block, weight]...] for a cell, as the mod picks it."""
    total = sum(w for _, w in mix)
    r = cell_hash(x, y, z, seed) % total
    for block, weight in mix:
        if r < weight:
            return block
        r -= weight
    return mix[-1][0]


def permeable(block):
    """True for a block water flows into; a mix (always a solid's) is not one."""
    if not isinstance(block, str):
        return False
    return any(block == p or (p.startswith("_") and block.split("[")[0].endswith(p)) or block.startswith(p + "[")
               for p in PERMEABLE)


class Space:
    """A space of layout.json: its cells (x, z), the floor under each (a ramp steps it) and its air's height over it."""

    def __init__(self, raw, levels):
        self.raw = raw
        self.id = raw["id"]
        self.name = raw.get("name", "")
        self.style = raw["style"]
        self.boxes = [tuple(b) for b in raw["boxes"]]
        self.floor = level_y(raw["floor"], levels)
        self.height = raw["height"]
        self.ramp = raw.get("ramp")
        self.lamps = raw.get("lamps")
        self.optional = raw.get("optional", False)
        self.cells = {(x, z) for x0, z0, x1, z1 in self.boxes for x in range(x0, x1) for z in range(z0, z1)}

    def floor_at(self, x, z):
        return ramp_floor(self.ramp, x, z) if self.ramp else self.floor

    def top_at(self, x, z):
        """The first y over the air: where the ceiling is."""
        return self.floor_at(x, z) + self.height

    @property
    def bounds(self):
        xs = [x for x, _ in self.cells]
        zs = [z for _, z in self.cells]
        return min(xs), min(zs), max(xs) + 1, max(zs) + 1


def level_y(value, levels):
    return levels[value] if isinstance(value, str) else int(value)


def ramp_floor(ramp, x, z):
    """A ramp's floor at a cell: `from` at its `start` row, one more towards `to` every row in the `step` direction, then `to`."""
    along = x if ramp["axis"] == "x" else z
    rows = (along - ramp["start"]) * ramp["step"]
    if rows <= 0:
        return ramp["from"]
    climb = 1 if ramp["to"] > ramp["from"] else -1
    return ramp["from"] + climb * min(rows, abs(ramp["to"] - ramp["from"]))


def ramp_facing(ramp):
    """The way a ramp's stairs face: towards where it rises."""
    step = (ramp["step"], 0) if ramp["axis"] == "x" else (0, ramp["step"])
    rising = ramp["to"] > ramp["from"]
    return FACING[step if rising else (-step[0], -step[1])]


def stairs(block, facing):
    return f"{block}[facing={facing},half=bottom,shape=straight,waterlogged=false]"


class Level:
    def __init__(self, layout=None):
        self.layout = layout or json.loads(LAYOUT.read_text(encoding="utf-8"))
        L = self.layout
        self.seed = L["seed"]
        self.levels = L["levels"]
        self.styles = L["styles"]
        self.spaces = {raw["id"]: Space(raw, self.levels) for raw in L["spaces"]}
        self.problems = []
        self.air = {}          # (x, y, z) -> the id of the space (or "opening:<id>") whose air it is
        self.water = set()     # cells of water (they are air of their space too)
        self.blocks = {}       # (x, y, z) -> a block the plan puts there (features, openings' bars, gates, lamps)
        self.owner = {}        # (x, y, z) -> the space a feature's block or solid belongs to
        self.solid_style = {}  # (x, y, z) -> the style of a feature's solid (its surfaces take it)
        self.climb = set()     # ladder cells (air one can climb)
        self.gates = {}        # gate id -> (cells, states, built)
        self.openings = {}     # opening id -> its cells
        self.lamps = []
        self.texts = []        # {"at": cell, "text": lang key, "style": ...}: what the plan's wall_text blocks say
        self.lay_air()
        self.lay_openings()
        self.lay_features()
        self.water &= set(self.air)    # a solid laid over water (a flight's steps, a walkway) is no longer water
        self.lay_niches()
        self.shell = self.build_shell()
        self.lay_bricked()
        self.lay_lamps()
        self.lay_scatter()
        self.materials_cache = self.materials()

    # --- air ---------------------------------------------------------------------------------

    def lay_air(self):
        for s in self.spaces.values():
            for x, z in s.cells:
                for y in range(s.floor_at(x, z), s.top_at(x, z)):
                    c = (x, y, z)
                    if c in self.air:
                        self.problems.append(f"{s.id} overlaps {self.air[c]} at {c}")
                    self.air[c] = s.id
            if s.ramp:
                # A stair on every row over the ramp's lowest, and on the lowest too when it stands a step over what leads
                # into it (`first_step`).
                facing = ramp_facing(s.ramp)
                block = self.styles[s.style]["stairs"]
                low = min(s.ramp["from"], s.ramp["to"]) - (1 if s.ramp.get("first_step") else 0)
                for x, z in s.cells:
                    f = s.floor_at(x, z)
                    if f > low:
                        self.blocks[(x, f - 1, z)] = stairs(block, facing)
                        self.owner[(x, f - 1, z)] = s.id

    def lay_openings(self):
        for o in self.layout["openings"]:
            cells = []
            a, b = o["span"]
            for k in range(a, b + 1):
                for y in range(o["y"][0], o["y"][1] + 1):
                    cells.append((o["line"], y, k) if o["wall"] == "x" else (k, y, o["line"]))
            sides = set()
            for x, y, z in cells:
                if (x, y, z) in self.air:
                    self.problems.append(f"opening {o['id']}: {(x, y, z)} is not in a wall (it is {self.air[(x, y, z)]}'s air)")
                for dx, dz in ((1, 0), (-1, 0)) if o["wall"] == "x" else ((0, 1), (0, -1)):
                    side = self.air.get((x + dx, y, z + dz))
                    if side:
                        sides.add(side)
            if len(sides) < 2:
                self.problems.append(f"opening {o['id']} does not join two spaces (it touches {sorted(sides) or 'nothing'})")
            self.openings[o["id"]] = cells
            kind = o.get("kind", "arch")
            if kind == "bars":
                connect = "north=true,south=true,east=false,west=false" if o["wall"] == "x" else "east=true,west=true,north=false,south=false"
                for c in cells:
                    self.blocks[c] = f"minecraft:iron_bars[{connect},waterlogged=false]"
                continue
            if kind == "gate":
                self.gates[o["id"]] = (cells, o["states"], o["built"])
                built = o["states"][o["built"]]
                for c in cells:
                    if built == AIR:
                        self.air[c] = f"opening:{o['id']}"
                    else:
                        self.blocks[c] = built
                continue
            for c in cells:
                self.air[c] = f"opening:{o['id']}"

    # --- features -----------------------------------------------------------------------------

    def box_cells(self, box):
        x0, z0, x1, z1 = box
        return [(x, z) for x in range(x0, x1) for z in range(z0, z1)]

    def remove_air(self, c, space, style=None):
        if self.air.get(c) == space:
            del self.air[c]
        self.owner[c] = space
        if style:
            self.solid_style[c] = style

    def lay_features(self):
        for f in self.layout["features"]:
            kind = f["type"]
            s = self.spaces.get(f.get("space"))
            if kind not in ("bricked",) and s is None:
                self.problems.append(f"feature {kind} names no space: {f.get('space')}")
                continue
            if "box" in f and s is not None:
                outside = [c for c in self.box_cells(f["box"]) if c not in s.cells]
                if outside and kind != "channel":
                    self.problems.append(f"feature {kind} {f['box']} reaches out of {s.id} at {outside[0]}")
            getattr(self, "feature_" + kind)(f, s)

    def feature_partition(self, f, s):
        for x, z in self.box_cells(f["box"]):
            for y in range(s.floor_at(x, z), s.top_at(x, z)):
                self.remove_air((x, y, z), s.id)

    feature_pillar = feature_partition

    def feature_solid(self, f, s):
        for x, z in self.box_cells(f["box"]):
            for y in range(s.floor_at(x, z), f["top"]):
                self.remove_air((x, y, z), s.id, f.get("style", s.style))

    def feature_flight(self, f, s):
        facing = ramp_facing(f)
        block = self.styles[s.style]["stairs"]
        for x, z in self.box_cells(f["box"]):
            top = ramp_floor(f, x, z)
            base = s.floor_at(x, z)
            if top <= base:
                continue
            for y in range(base, top):
                self.remove_air((x, y, z), s.id, s.style)
            self.blocks[(x, top - 1, z)] = stairs(block, facing)

    def feature_deck(self, f, s):
        block = f.get("block", self.styles[s.style]["deck"])
        for x, z in self.box_cells(f["box"]):
            c = (x, f["top"] - 1, z)
            self.remove_air(c, s.id)
            self.blocks[c] = block

    def feature_channel(self, f, s):
        for x, z in self.box_cells(f["box"]):
            floor = s.floor_at(x, z)
            for y in range(floor - f["depth"], floor):
                c = (x, y, z)
                if c in self.air:
                    self.problems.append(f"channel in {s.id} runs into {self.air[c]} at {c}")
                self.air[c] = s.id
                if y < floor - f["depth"] + f["water"]:
                    self.water.add(c)

    def feature_water(self, f, s):
        for c, owner in self.air.items():
            if owner == s.id and c[1] < f["below"]:
                self.water.add(c)

    def feature_rail(self, f, s):
        block = f.get("block", self.styles[s.style]["railing"])
        connect = "east=true,west=true,north=false,south=false" if f["line"] == "z" else "north=true,south=true,east=false,west=false"
        if block == "minecraft:iron_bars":
            block = f"minecraft:iron_bars[{connect},waterlogged=false]"
        elif block.endswith("_wall"):
            sides = "east=low,west=low,north=none,south=none" if f["line"] == "z" else "north=low,south=low,east=none,west=none"
            block = f"{block}[{sides},up=true,waterlogged=false]"
        gaps = set(f.get("gaps", []))
        a, b = f["span"]
        for k in range(a, b + 1):
            if k in gaps:
                continue
            for y in range(f["y"], f["y"] + f["height"]):
                c = (k, y, f["at"]) if f["line"] == "z" else (f["at"], y, k)
                self.remove_air(c, s.id)
                self.blocks[c] = block

    def feature_ladder(self, f, s):
        x, z = f["at"]
        block = f.get("block", "every_tunnel:drain_ladder")
        for y in range(f["y"][0], f["y"][1] + 1):
            c = (x, y, z)
            if c not in self.air:
                self.problems.append(f"ladder in {s.id} at {c}: no air there")
                continue
            self.climb.add(c)
            self.blocks[c] = f"{block}[facing={f['facing']},waterlogged={'true' if c in self.water else 'false'}]"

    def feature_patch(self, f, s):
        for x, z in self.box_cells(f["box"]):
            self.blocks[(x, s.floor_at(x, z) - 1, z)] = f["block"]

    def feature_prop(self, f, s):
        """A block of furniture or a sign standing in a space's air: `at` [x, z], `dy` blocks over the space's floor there, the `block`;
        `text` (a lang key) and `style` if it is a wall_text. Like a rail, it is no longer air."""
        x, z = f["at"]
        c = (x, s.floor_at(x, z) + f.get("dy", 0), z)
        if c not in self.air:
            self.problems.append(f"prop in {s.id} at {c}: no air there")
            return
        self.remove_air(c, s.id)
        self.blocks[c] = f["block"]
        if "text" in f:
            entry = {"at": list(c), "text": f["text"], "style": f.get("style", "poster")}
            if f.get("offset"):
                entry["offset"] = f["offset"]
            self.texts.append(entry)

    def feature_fill(self, f, s):
        """A block set in every cell of a box of a space's air, for the `y` range (its floor's y, both ends in): a door leaning on a wall.
        Like a rail, those cells are no longer air."""
        a, b = f["y"]
        for x, z in self.box_cells(f["box"]):
            for y in range(a, b + 1):
                c = (x, y, z)
                if c not in self.air:
                    self.problems.append(f"fill in {s.id} at {c}: no air there")
                    continue
                self.remove_air(c, s.id)
                self.blocks[c] = f["block"]

    def feature_bricked(self, f, s):
        self._bricked = getattr(self, "_bricked", []) + [f]

    # --- niches -------------------------------------------------------------------------------

    def lay_niches(self):
        """Empty niches in the old walls: a recess a block wide, two high and one deep at waist height, every few blocks along
        masonry walls where the wall is thick enough to keep a back behind it. Which ones the seed decides."""
        taken = set()
        for s in self.spaces.values():
            st = self.styles[s.style]
            every = st.get("niche_every")
            if not every:
                continue
            share = st.get("niche_share", 0.5)
            for x, z in sorted(s.cells):
                y = s.floor_at(x, z) + 1
                if self.air.get((x, y, z)) != s.id or self.air.get((x, y + 1, z)) != s.id:
                    continue
                for dx, dz in SIDES:
                    w = (x + dx, y, z + dz)
                    if (w[0] * abs(dz) + w[2] * abs(dx)) % every:     # along the wall, one every `every` blocks
                        continue
                    ok = all(self.free_wall((x + dx * k, y + dy, z + dz * k)) for k in (1, 2, 3) for dy in (-1, 0, 1, 2))
                    ok = ok and all(self.free_wall((x + dx + ex, y + dy, z + dz + ez))
                                    for ex, ez in ((dz, dx), (-dz, -dx)) for dy in (0, 1))
                    if not ok or cell_hash(*w, self.seed) % 1000 >= share * 1000 or w in taken:
                        continue
                    for dy in (0, 1):
                        self.air[(w[0], y + dy, w[2])] = s.id
                    taken.add(w)

    def free_wall(self, c):
        """A cell that is wall or nothing: no air, no feature, no opening near."""
        return c not in self.air and c not in self.blocks and c not in self.owner

    # --- the shell ----------------------------------------------------------------------------

    def style_of(self, owner):
        if owner.startswith("opening:"):
            return None
        return self.spaces[owner].style

    def build_shell(self):
        """{(x, y, z): (role, style)}: every cell beside the air that is neither air nor a placed block: floor under air,
        ceiling over it, wall beside it (a dado on the first course), in the style of what it faces."""
        shell = {}
        for (x, y, z), owner in self.air.items():
            for dx, dy, dz in AROUND:
                c = (x + dx, y + dy, z + dz)
                if c in self.air or c in self.blocks or c in shell:
                    continue
                shell[c] = None
        for c in shell:
            shell[c] = self.shell_role(c)
        return shell

    def shell_role(self, c):
        x, y, z = c
        above, below = self.air.get((x, y + 1, z)), self.air.get((x, y - 1, z))
        styles = []
        for dx, dy, dz in AROUND:
            o = self.air.get((x + dx, y + dy, z + dz))
            if o:
                st = self.style_of(o)
                if st:
                    styles.append(st)
        if c in self.solid_style:
            style = self.solid_style[c]
        elif styles:
            style = min(styles, key=STYLE_ORDER.index)
        else:
            style = "drain"
        if above is not None:
            return "floor", style
        if below is not None:
            return "ceiling", style
        for dx, dz in SIDES:
            o = self.air.get((x + dx, y, z + dz))
            if o and not o.startswith("opening:") and self.spaces[o].floor_at(x + dx, z + dz) == y and c not in self.solid_style:
                return "dado", style
        return "wall", style

    def lay_bricked(self):
        for f in getattr(self, "_bricked", []):
            a, b = f["span"]
            for k in range(a, b + 1):
                for y in range(f["y"][0], f["y"][1] + 1):
                    c = (f["line"], y, k) if f["wall"] == "x" else (k, y, f["line"])
                    if c not in self.shell:
                        self.problems.append(f"bricked {f['wall']}={f['line']} {f['span']}: {c} is not a wall")
                        continue
                    role, style = self.shell[c]
                    self.blocks[c] = ("mix", "bricked", style)

    # --- lamps and scatter ----------------------------------------------------------------------

    def lay_lamps(self):
        """The lamps of every space (`lamps`, one spec or a list): `ceiling`, a lamp under the ceiling every `spacing` blocks over
        the boxes; `cable`, a cable strung along each box's long axis (`rows` runs) with a bulb hanging from it every `spacing`
        blocks, at most `hang` over the floor; `wall`, a lamp `above` blocks over each standing surface, on the wall, every
        `spacing` blocks along it. Gaps (`lamp_gaps`, or a spec's `gaps`): a share of the lamps is left out (the cable stays),
        flickers or is dead, picked by `cell_hash`; lamps near an anchor are always on."""
        for s in self.spaces.values():
            specs = s.lamps if isinstance(s.lamps, list) else [s.lamps] if s.lamps else []
            for spec in specs:
                getattr(self, "lamps_" + spec.get("kind", "ceiling"))(s, spec)

    def lamp_mode(self, c, spec):
        """'on', 'flicker', 'off' or None (no lamp) for the lamp at a cell."""
        gaps = {**self.layout.get("lamp_gaps", {}), **spec.get("gaps", {})}
        for a in self.layout["anchors"]:
            px_, py_, pz_ = a["pos"]
            if abs(px_ - c[0]) <= gaps.get("safe_radius", 6) and abs(pz_ - c[2]) <= gaps.get("safe_radius", 6) and abs(py_ - c[1]) <= 5:
                return "on"
        r = cell_hash(*c, self.seed + 211) % 10000 / 10000
        dark, dead, flicker = gaps.get("dark", 0), gaps.get("dead", 0), gaps.get("flicker", 0)
        if r < dark:
            return None
        if r < dark + dead:
            return "off"
        if r < dark + dead + flicker:
            return "flicker"
        return "on"

    def put_lamp(self, c, facing, axis, spec):
        mode = self.lamp_mode(c, spec)
        if mode is None:
            return False
        self.blocks[c] = self.layout["lamp_blocks"]["cage"].format(facing=facing, axis=axis, mode=mode, lit="false" if mode == "off" else "true")
        self.lamps.append(c)
        return True

    def free(self, c, s):
        return self.air.get(c) == s.id and c not in self.blocks and c not in self.water and c not in self.climb

    def lamps_ceiling(self, s, spec):
        for x0, z0, x1, z1 in s.boxes:
            for x in grid(x0, x1 - 1, spec["spacing"]):
                for z in grid(z0, z1 - 1, spec["spacing"]):
                    c = (x, s.top_at(x, z) - 1, z)
                    if self.air.get(c) == s.id and self.shell.get((x, c[1] + 1, z), (None,))[0] == "ceiling":
                        self.put_lamp(c, "down", "x", spec)

    def lamps_cable(self, s, spec):
        blocks = self.layout["lamp_blocks"]
        for x0, z0, x1, z1 in s.boxes:
            along_x = (x1 - x0) >= (z1 - z0)
            a0, a1, c0, c1 = (x0, x1 - 1, z0, z1 - 1) if along_x else (z0, z1 - 1, x0, x1 - 1)
            rows = spec.get("rows", 1)
            for i in range(rows):
                cross = c0 + int((i + 0.5) * (c1 - c0 + 1) / rows)
                cell = (lambda t: (t, cross)) if along_x else (lambda t: (cross, t))
                axis = "x" if along_x else "z"
                for t in range(a0, a1 + 1):
                    x, z = cell(t)
                    c = (x, s.top_at(x, z) - 1, z)
                    if self.free(c, s) and self.shell.get((x, c[1] + 1, z), (None,))[0] == "ceiling":
                        self.blocks[c] = blocks["cable"].format(axis=axis)
                for t in grid(a0, a1, spec["spacing"]):
                    x, z = cell(t)
                    top = (x, s.top_at(x, z) - 1, z)
                    if self.air.get(top) != s.id or self.shell.get((x, top[1] + 1, z), (None,))[0] != "ceiling":
                        continue
                    y = min(top[1], s.floor_at(x, z) + spec.get("hang", 4))
                    lamp = (x, y, z)
                    if not self.free(lamp, s) and lamp != top:
                        continue
                    if self.put_lamp(lamp, "down", axis, spec):
                        for yy in range(y + 1, top[1]):
                            if self.free((x, yy, z), s):
                                self.blocks[(x, yy, z)] = blocks["drop"]

    def lamps_wall(self, s, spec):
        above, spacing = spec.get("above", 3), spec["spacing"]
        for (x, y, z), owner in sorted(self.air.items(), key=lambda kv: kv[0]):
            if owner != s.id or (x, y - 1, z) in self.air:
                continue
            lamp = (x, y + above, z)
            if not self.free(lamp, s):
                continue
            for dx, dz in SIDES:
                wall = self.shell.get((x + dx, y + above, z + dz))
                if wall is None or wall[0] != "wall" or (x + dx, y + above, z + dz) in self.blocks:
                    continue
                if (z if dx else x) % spacing:
                    continue
                self.put_lamp(lamp, FACING[(-dx, -dz)], "x" if dz else "z", spec)
                break

    def lay_scatter(self):
        """Puddles on the drain's floors, moss on the catacombs': a share of the floor cells, chosen by the seed."""
        for (x, y, z), owner in list(self.air.items()):
            if owner.startswith("opening:") or (x, y, z) in self.blocks or (x, y, z) in self.water:
                continue
            under = self.shell.get((x, y - 1, z))
            if not under or under[0] != "floor":
                continue
            st = self.styles[self.spaces[owner].style]
            mix, share = st.get("floor_scatter"), st.get("floor_scatter_share", 0)
            if mix and cell_hash(x, y, z, self.seed + 101) % 10000 < share * 10000:
                self.blocks[(x, y, z)] = pick(mix, x, y, z, self.seed + 102)

    # --- what the plan holds ------------------------------------------------------------------

    def materials(self):
        """{(x, y, z): material}: a block (a string) or a mix (("mix", role, style)); every cell the build sets (the air is the
        clear's)."""
        out = {}
        for c, (role, style) in self.shell.items():
            out[c] = ("mix", role, style)
        for c in self.water:
            out[c] = WATER
        out.update(self.blocks)
        return out

    def mix_of(self, material):
        _, role, style = material
        st = self.styles[style]
        mix = st.get(role) or st["wall"]
        return mix if isinstance(mix, list) else [[mix, 1]]

    def bounds(self):
        cells = list(self.air) + list(self.shell) + list(self.blocks)
        lo = tuple(min(c[i] for c in cells) for i in range(3))
        hi = tuple(max(c[i] for c in cells) for i in range(3))
        return lo, hi

    def block_at(self, c):
        """The block the built plan has at a cell (a mix resolved as the mod resolves it), or air."""
        m = self.materials_cache.get(c) if hasattr(self, "materials_cache") else self.materials().get(c)
        if m is None:
            return AIR
        if isinstance(m, tuple):
            return pick(self.mix_of(m), *c, self.mix_seed(m))
        return m

    def mix_seed(self, material):
        _, role, style = material
        return self.seed * 31 + sorted(self.styles).index(style) * 7 + ["wall", "dado", "floor", "ceiling", "bricked"].index(role)

    # --- named places -------------------------------------------------------------------------

    def zone_box(self, z):
        """A zone's box [x0, y0, z0, x1, y1, z1], both corners in, from its spaces (their floor's blocks to their air's top) or
        its own box."""
        if z.get("all"):
            lo, hi = self.bounds()
            return (lo[0], lo[1], lo[2], hi[0], hi[1], hi[2])
        if "box" in z:
            x0, z0, x1, z1 = z["box"]
            return (x0, z["y"][0] - 1, z0, x1 - 1, z["y"][1], z1 - 1)
        xs0, ys0, zs0, xs1, ys1, zs1 = [], [], [], [], [], []
        for sid in z["spaces"]:
            s = self.spaces[sid]
            bx0, bz0, bx1, bz1 = s.bounds
            floors = [s.floor_at(x, zz) for x, zz in s.cells]
            xs0.append(bx0); zs0.append(bz0); xs1.append(bx1 - 1); zs1.append(bz1 - 1)
            ys0.append(min(floors) - 1); ys1.append(max(floors) + s.height - 1)
        return (min(xs0), min(ys0), min(zs0), max(xs1), max(ys1), max(zs1))

    def anchor(self, aid):
        for a in self.layout["anchors"]:
            if a["id"] == aid:
                return a
        raise KeyError(aid)

    def anchor_cell(self, aid):
        x, y, z = self.anchor(aid)["pos"]
        return int(x // 1), int(y), int(z // 1)


def grid(a, b, spacing):
    """Positions spread over a .. b (inclusive) every `spacing`, centred; at least one."""
    length = b - a + 1
    n = max(1, (length + spacing // 2) // spacing)
    start = a + (length - (n - 1) * spacing - 1) // 2
    return [start + k * spacing for k in range(n)]


def merge_boxes(blocks):
    """Greedy, as Level 1's generator does: runs along x, then the same runs on the next z, then the same boxes on the next y.
    `blocks` maps cells to any hashable material; returns [((x0, y0, z0), (x1, y1, z1), material)], both corners included."""
    runs = {}
    for (x, y, z), block in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        row = runs.setdefault((y, z), [])
        if row and row[-1][1] == x - 1 and row[-1][2] == block:
            row[-1] = (row[-1][0], x, block)
        else:
            row.append((x, x, block))
    slabs = {}
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
    for y in sorted(slabs):
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
    return sorted(set(out), key=lambda f: (f[0][1], f[0][2], f[0][0], str(f[2])))
