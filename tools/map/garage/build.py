"""
Builds the garage as a block map: structure, wear, props, ceiling infrastructure,
oddities and lights. Everything random comes from SEED, so the same parameters always
give the same garage.

Local coordinates: x along the length (0..LENGTH-1), z along the width (0..WIDTH-1),
y up: 0 floor, 1..H air, H+1 ceiling, H+2 above the ceiling (collapse pockets).
x = -1 / LENGTH and z = -1 / WIDTH are just outside the walls (pockets behind doors).
"""

import math
import random

from garage import props as P_


class World:
    def __init__(self):
        self.blocks = {}
        self.tags = {}            # (x, z) -> label for the preview (props, oddities)
        self.reserved = set()     # (x, z) cells no further prop may use
        self.dark = set()         # (x, z) cells whose ceiling lights stay dead (walled-off, collapse)

    def set(self, x, y, z, state):
        self.blocks[(x, y, z)] = state

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.blocks[(x, y, z)] = state

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def free(self, x, y, z):
        return (x, y, z) not in self.blocks

    def remove(self, x, y, z):
        self.blocks.pop((x, y, z), None)


class Garage:
    """Geometry derived from the parameters (same rules as round 1, plus stalls)."""

    def __init__(self, p):
        self.p = p
        L, W, H = p.LENGTH, p.WIDTH, p.CEILING_HEIGHT
        if L < 16 or W < p.AISLE_WIDTH + 8:
            raise SystemExit("LENGTH must be >= 16 and WIDTH >= AISLE_WIDTH + 8")
        if H < 4:
            raise SystemExit("CEILING_HEIGHT must be >= 4 (pipes and beams hang below the ceiling)")
        inner = W - 2
        self.bay_a = (inner - p.AISLE_WIDTH) // 2
        self.bay_b = inner - p.AISLE_WIDTH - self.bay_a
        self.aisle_z0 = 1 + self.bay_a
        self.aisle_z1 = self.aisle_z0 + p.AISLE_WIDTH - 1
        self.center_z = self.aisle_z0 + p.AISLE_WIDTH // 2

        count = (L - 2 - 4 - 1) // p.PILLAR_SPACING + 1
        span = (count - 1) * p.PILLAR_SPACING + 1
        start = 1 + (L - 2 - span) // 2
        self.pillar_xs = [start + i * p.PILLAR_SPACING for i in range(count)]
        self.pillar_zs = [self.aisle_z0 - 1, self.aisle_z1 + 1]

        lines = set()
        k = -100
        while True:
            x = self.pillar_xs[0] + math.floor(k * p.PILLAR_SPACING / p.STALLS_BETWEEN_PILLARS + 0.5)
            if x > L - 2:
                break
            if 3 <= x <= L - 4:
                lines.add(x)
            k += 1
        self.line_xs = sorted(lines)

        # Stalls: (side, x0, x1, z0, z1, wall_z) between consecutive lines (and the end walls).
        bounds = [0] + self.line_xs + [L - 1]
        self.stalls = []
        for side, z0, z1, wall_z in (("A", 1, self.aisle_z0 - 1, 1), ("B", self.aisle_z1 + 1, W - 2, W - 2)):
            for a, b in zip(bounds, bounds[1:]):
                if b - a - 1 >= 2:
                    self.stalls.append((side, a + 1, b - 1, z0, z1, wall_z))

    def in_aisle(self, z):
        return self.aisle_z0 <= z <= self.aisle_z1

    def is_pillar(self, x, z):
        return x in self.pillar_xs and z in self.pillar_zs


def build(p, variant, glyphs, openings=(), zone_letters="AB"):
    """
    One parking hall. Returns (world, garage, report, lights).

    variant: 'vanilla' or 'mod' (only the light fixtures differ).
    glyphs: the level's shared Glyphs registry (sign texts).
    openings: doorways in the walls, dicts {"side": north|south|east|west, "a": .., "b": ..,
      "height": ..}. Minecraft directions: north = -z, so the north wall is z = 0, the south
      wall z = W-1, the west wall x = 0, the east wall x = L-1. a..b run along x for
      north/south walls and along z for east/west walls. Stalls in front of a side opening
      become a driveway.
    zone_letters: letters of the pillar codes on the two stall rows (e.g. "CD").
    """
    g = Garage(p)
    w = World()
    side_of = {"north": "A", "south": "B"}          # stall row A is next to the north wall
    driveways = [(side_of[o["side"]], o["a"] - 1, o["b"] + 1) for o in openings if o["side"] in side_of]
    g.stalls = [s for s in g.stalls
                if not any(s[0] == side and not (s[2] < a or s[1] > b) for side, a, b in driveways)]
    S = p.STAND_INS
    L, W, H = p.LENGTH, p.WIDTH, p.CEILING_HEIGHT
    rng = random.Random(p.SEED)
    report = {"oddities": [], "props": {}}

    def count(name, n=1):
        report["props"][name] = report["props"].get(name, 0) + n

    # --- Shell -------------------------------------------------------------------
    w.fill(0, 0, 0, L - 1, 0, W - 1, S["floor"])
    w.fill(0, H + 1, 0, L - 1, H + 1, W - 1, S["ceiling"])
    for x0, z0, x1, z1 in ((0, 0, L - 1, 0), (0, W - 1, L - 1, W - 1), (0, 0, 0, W - 1), (L - 1, 0, L - 1, W - 1)):
        w.fill(x0, 1, z0, x1, H, z1, S["wall"])
        w.fill(x0, 1, z0, x1, 1, z1, S["wall_base"])
        if 2 <= p.WALL_STRIPE_HEIGHT <= H:
            w.fill(x0, p.WALL_STRIPE_HEIGHT, z0, x1, p.WALL_STRIPE_HEIGHT, z1, S["paint_band"])

    # --- Floor paint and repairs -------------------------------------------------
    for x in g.line_xs:
        for side, z0, z1 in (("A", 1, g.aisle_z0 - 1), ("B", g.aisle_z1 + 1, W - 2)):
            if not any(d[0] == side and d[1] <= x <= d[2] for d in driveways):
                w.fill(x, 0, z0, x, 0, z1, S["line_white"])
    for x in range(2, L - 2):
        if (x - 2) % 5 < 3:
            w.set(x, 0, g.center_z, S["line_yellow"])
    for _ in range(int(p.WEAR * L * W / 40)):                      # patched slabs
        cx, cz = rng.randrange(1, L - 1), rng.randrange(1, W - 1)
        for x in range(cx, min(L - 1, cx + rng.randrange(1, 4))):
            for z in range(cz, min(W - 1, cz + rng.randrange(1, 3))):
                if w.get(x, 0, z) == S["floor"]:
                    w.set(x, 0, z, S["floor_patch"])

    # --- Openings and driveways ------------------------------------------------------
    for o in openings:
        for t in range(o["a"], o["b"] + 1):
            x, z = {"north": (t, 0), "south": (t, W - 1), "west": (0, t), "east": (L - 1, t)}[o["side"]]
            for y in range(1, o["height"] + 1):
                w.remove(x, y, z)
    for side, a, b in driveways:
        zs = range(1, g.aisle_z0) if side == "A" else range(g.aisle_z1 + 1, W - 1)
        for x in range(a, b + 1):
            for z in zs:
                w.reserved.add((x, z))

    # --- Pillars with painted base and zone code -----------------------------------
    for side_index, z in enumerate(g.pillar_zs):
        for i, x in enumerate(g.pillar_xs):
            if any(d[0] == "AB"[side_index] and d[1] < x < d[2] for d in driveways):
                continue
            w.fill(x, 0, z, x, H, z, S["pillar"])
            w.set(x, 1, z, S["paint_band"])
            if p.SIGNS:
                w.set(x, 2, z, glyphs.pillar_state(f"{zone_letters[side_index]}{i + 1}"))
            w.reserved.add((x, z))

    # --- Level signs on the walls (visible from the aisle) ----------------------------
    def wall_sign(text_tiles, wall, along, y=3):
        """Tiles on a wall: wall 'east'/'west' (end walls, along = z) or 'north'/'south' (side walls, along = x)."""
        for i, t in enumerate(text_tiles):
            if wall == "east":       # x = L-1, read facing east: left to right is +z (south)
                pos, facing = (L - 1, y, along + i), "west"
            elif wall == "west":     # x = 0, read facing west: left to right is -z
                pos, facing = (0, y, along - i), "east"
            elif wall == "north":    # z = 0, read facing north: left to right is +x
                pos, facing = (along + i, y, 0), "south"
            else:                    # z = W-1, read facing south: left to right is -x
                pos, facing = (along - i, y, W - 1), "north"
            if w.get(*pos) in (S["wall"], S["wall_base"], S["paint_band"]):
                w.set(*pos, glyphs.wall_state(t, facing))

    def near_opening(side, a, b):
        return any(o["side"] == side and not (b < o["a"] - 2 or a > o["b"] + 2) for o in openings)

    if p.SIGNS and H >= 3:
        level = list(p.LEVEL_SIGN)
        if not near_opening("east", g.center_z - 3, g.center_z + 3):
            wall_sign(level, "east", g.center_z - len(level) // 2)                  # at the end of the aisle
        if not near_opening("west", g.center_z - 3, g.center_z + 3):
            wall_sign(level, "west", g.center_z + len(level) // 2)
        n = len(level) + 1
        for x in range(8, L - 8, p.SIGN_SPACING):
            if not near_opening("north", x, x + n):
                wall_sign(level + ["<"], "north", x)
            if not near_opening("south", x, x + n):
                wall_sign(level + [">"], "south", x + n - 1)

    # --- Oddities (reserve their space first) --------------------------------------------
    stalls = list(g.stalls)
    rng.shuffle(stalls)
    used_stalls = set()

    def take_stall(min_width=3, side=None):
        for s in stalls:
            if s in used_stalls or (side and s[0] != side) or s[2] - s[1] + 1 < min_width:
                continue
            used_stalls.add(s)
            return s
        return None

    kinds = [k for k in p.ODDITY_TYPES]
    rng.shuffle(kinds)
    for kind in kinds[: p.ODDITIES]:
        placed = P_.place_oddity(kind, w, g, p, rng, take_stall, glyphs)
        if placed:
            report["oddities"].append(placed)

    # --- Stalls: cars, wheel stops, small props -----------------------------------------------
    for s in g.stalls:
        side, x0, x1, z0, z1, wall_z = s
        if s in used_stalls:
            continue
        if p.WHEEL_STOPS:
            for x in range(x0, x1 + 1):
                if x1 - x0 < 3 or x not in (x0, x1):
                    w.set(x, 1, wall_z, "minecraft:smooth_stone_slab[type=bottom]")
        r = rng.random()
        if r < p.CAR_DENSITY and x1 - x0 + 1 >= 3 and z1 - z0 + 1 >= 6:
            P_.car(w, g, p, rng, s)
            count("car")
        elif r < p.CAR_DENSITY + p.CLUTTER_DENSITY:
            P_.clutter(w, g, p, rng, s, count)

    P_.small_props(w, g, p, rng, count)

    # --- Ceiling infrastructure -------------------------------------------------------------
    beam_xs = set()
    if p.BEAMS:
        for x in g.pillar_xs:
            for z in range(1, W - 1):
                if w.free(x, H, z):
                    w.set(x, H, z, S["ceiling"])
            beam_xs.add(x)
    if p.CEILING_PIPES:
        P_.ceiling_services(w, g, p, rng, beam_xs)

    # --- Lights -------------------------------------------------------------------------------
    lights = P_.light_plan(g, p, rng, beam_xs, w)
    for x0, x1, z, state in lights:
        for x in range(x0, x1 + 1):
            if variant == "mod":
                mode = {"lit": ("on", "true"), "dead": ("off", "false"), "dim": ("flicker", "true")}.get(state)
                if mode and w.free(x, H, z):
                    w.set(x, H, z, p.MOD_LIGHT % mode)
            else:
                if state == "lit":
                    w.set(x, H + 1, z, S["fixture_lit"])
                elif state == "dead":
                    w.set(x, H + 1, z, S["fixture_dead"])
                elif state == "dim" and w.free(x, H, z):
                    w.set(x, H, z, f"{S['fixture_dim']}[up=true]")
    report["lights"] = {k: sum(1 for *_, s in lights if s == k) for k in ("lit", "dead", "dim", "gap")}

    # --- Wear decals (last: only on free surfaces) ------------------------------------------------
    if p.DECALS:
        P_.decals(w, g, p, rng, count)

    report["line_of_sight"] = line_of_sight(w, g, p)
    return w, g, report, lights


def line_of_sight(w, g, p):
    """Longest straight run along the length, inside the aisle, with both body blocks (y 1-2) free."""
    best = 0
    for z in range(g.aisle_z0, g.aisle_z1 + 1):
        run = 0
        for x in range(1, p.LENGTH - 1):
            decal = p.STAND_INS["decal"]
            clear = all(w.get(x, y, z) is None or w.get(x, y, z).startswith(decal) for y in (1, 2))
            run = run + 1 if clear else 0
            best = max(best, run)
    return best
