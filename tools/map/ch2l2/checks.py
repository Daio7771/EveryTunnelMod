"""
Chapter 2, Level 2: the checks generate.py runs on the level before writing anything (layout.json's `checks` sets them).

- **Structure**: no space overlaps another, every opening joins two spaces, every feature stands in its space (Level's own
  problems), and every anchor that is a place to stand has floor under it and room over it.
- **Walking**: the way from each anchor of the route to the next, with the gates as built (open), walked as a player walks:
  a step up of one block, a drop of at most three, ladders climbed, no swimming. Its length gives the route's time.
- **No shortcuts**: every way from the entry to the exit crosses each zone of `cuts` (a way round one would skip its event);
  with the gate down (`locked`), what lies past it cannot be reached. These look for ways a player could abuse: any drop,
  swimming, ladders.
- **The pump room**: the shortest way across it, swimming included, is long enough to need several cycles of the pump.
- **Doors**: every opening is clear: two blocks of air through it, and room to stand on both sides.
- **Water**: still water and each flood stage stay where they belong (layout.json's `flood`): no cell of water touches air,
  or a block water flows into, outside its own spaces.
- **Build**: how many cells the mod clears and fills and how long it takes it in batches (`Ch2L2Build`: 8000 cells written a
  tick, cells already empty read at 8 times that), against `max_build_seconds`.
"""

from collections import deque

from level import AIR, permeable

DOWN_WALK = 3          # the farthest drop of an honest walk
DOWN_ANY = 64          # any drop (a shortcut may cost health)
CLIMB_OUT = 2          # out of the water onto a floor at most this many blocks over the water's top cell


class Walker:
    """The places a player can be in the level and the moves between them, for one state of the gates."""

    def __init__(self, level, gates=None, max_drop=DOWN_WALK, swim=False):
        self.lv = level
        self.max_drop = max_drop
        self.swim = swim
        self.blocked = set()
        self.opened = set()
        for gid, state in (gates or {}).items():
            cells, states, _ = level.gates[gid]
            if states[state] == AIR:
                self.opened.update(cells)
            else:
                self.blocked.update(cells)

    def passable(self, c):
        if c in self.blocked:
            return False
        if c in self.opened:
            return True
        if c not in self.lv.air or c in self.lv.water:
            return False
        block = self.lv.blocks.get(c)
        return block is None or permeable(block) and not (block.startswith("minecraft:iron_bars") or "_wall[" in block)

    def solid(self, c):
        if c in self.blocked:
            return True
        if c in self.opened or c in self.lv.water:
            return False
        return c not in self.lv.air or c in self.lv.blocks and not self.passable(c)

    def standable(self, c):
        x, y, z = c
        below = (x, y - 1, z)
        return self.passable(c) and self.passable((x, y + 1, z)) and self.solid(below) and not self.tall(below)

    def tall(self, c):
        """A block a block and a half high (a wall, a catwalk's rail): nobody jumps onto it."""
        block = self.lv.blocks.get(c)
        return isinstance(block, str) and ("_wall[" in block or block.startswith("every_tunnel:catwalk_rail"))

    def neighbours(self, c):
        x, y, z = c
        lv = self.lv
        if c in lv.water:
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
                n = (x + dx, y + dy, z + dz)
                if n in lv.water and n not in self.blocked:
                    yield n
            if c in lv.climb:
                up = (x, y + 1, z)
                if up in lv.climb or self.passable(up):
                    yield up
            if (x, y + 1, z) not in lv.water:          # at the top: out onto a floor close enough over it
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    for h in range(1, CLIMB_OUT + 1):
                        n = (x + dx, y + h, z + dz)
                        if self.standable(n):
                            yield n
                            break
            return
        if c in lv.climb:
            for dy in (1, -1):
                n = (x, y + dy, z)
                if n in lv.climb or (dy == -1 and n in lv.water and self.swim):
                    yield n
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if self.standable((nx, y + 1, nz)) and self.passable((x, y + 2, z)):
                yield (nx, y + 1, nz)
            if (nx, y, nz) in lv.climb and self.passable((nx, y, nz)):
                yield (nx, y, nz)
            if c in lv.climb and self.standable((nx, y + 1, nz)):
                yield (nx, y + 1, nz)
            for drop in range(0, self.max_drop + 1):
                n = (nx, y - drop, nz)
                if n in lv.water:
                    if self.swim:
                        yield n
                    break
                if n in lv.climb and drop > 0:
                    yield n
                    break
                if self.standable(n):
                    yield n
                    break
                if not self.passable(n) or not self.passable((nx, y - drop + 1, nz)):
                    break

    def search(self, start, avoid=None, goal=None):
        """Breadth-first from `start`: {cell: (steps, previous)}; stops at `goal` if given. `avoid`: cells not to enter."""
        seen = {start: (0, None)}
        queue = deque([start])
        while queue:
            c = queue.popleft()
            if c == goal:
                break
            for n in self.neighbours(c):
                if n in seen or (avoid and n in avoid):
                    continue
                seen[n] = (seen[c][0] + 1, c)
                queue.append(n)
        return seen


def path(seen, goal):
    out = []
    c = goal
    while c is not None:
        out.append(c)
        c = seen[c][1]
    return out[::-1]


def start_cell(level, walker, aid):
    """The anchor's cell, or the standable cell nearest under it."""
    x, y, z = level.anchor_cell(aid)
    for dy in (0, 1, -1, 2, -2):
        if walker.standable((x, y + dy, z)):
            return (x, y + dy, z)
    return None


def zone_cells(level, zone_id):
    z = next(z for z in level.layout["zones"] if z["id"] == zone_id)
    x0, y0, z0, x1, y1, z1 = level.zone_box(z)
    return {c for c in level.air if x0 <= c[0] <= x1 and y0 <= c[1] <= y1 and z0 <= c[2] <= z1}


def run(level):
    """All the checks: (errors, report lines, the route's ways as lists of cells for the preview)."""
    lv = level
    cfg = lv.layout["checks"]
    errors = list(lv.problems)
    report = []
    built = {gid: g[2] for gid, g in lv.gates.items()}

    # Anchors stand.
    walk = Walker(lv, built)
    for a in lv.layout["anchors"]:
        if a.get("kind") == "wall":
            continue
        if start_cell(lv, walk, a["id"]) is None:
            errors.append(f"anchor {a['id']} at {a['pos']}: nowhere to stand there")

    # The route's zones apart: the mod tells where the player is by them (a zone of an L-shaped space is its box, which can reach
    # into its neighbours: give it a box of its own).
    route = lv.layout["route"]
    zones = {z["id"]: lv.zone_box(z) for z in lv.layout["zones"] if not z.get("all")}
    placed = [f"zone_{p}" for p in dict.fromkeys(route) if f"zone_{p}" in zones]
    nested = {frozenset(pair) for pair in cfg.get("nested", [])}
    for i, a in enumerate(placed):
        for b in placed[i + 1:]:
            ba, bb = zones[a], zones[b]
            apart = any(ba[k] > bb[k + 3] or bb[k] > ba[k + 3] for k in range(3))
            if not apart and frozenset((a, b)) not in nested:
                errors.append(f"zones {a} and {b} overlap")

    # The route, walked.
    ways, total = [], 0
    for a, b in zip(route, route[1:]):
        sa, sb = start_cell(lv, walk, a), start_cell(lv, walk, b)
        if sa is None or sb is None:
            continue
        seen = walk.search(sa, goal=sb)
        if sb not in seen:
            errors.append(f"route: no way walking from {a} to {b}")
            continue
        ways.append(path(seen, sb))
        total += seen[sb][0]
    seconds = total * cfg["walk_seconds_per_block"]
    report.append(f"route: {len(route)} places, {total} blocks walked, about {seconds / 60:.1f} min of walking alone")

    # No shortcuts: every way to the exit crosses each cut.
    anyway = Walker(lv, built, max_drop=DOWN_ANY, swim=True)
    entry = start_cell(lv, anyway, route[0])
    exit_ = start_cell(lv, anyway, route[-1])
    for zone_id in cfg["cuts"]:
        seen = anyway.search(entry, avoid=zone_cells(lv, zone_id), goal=exit_)
        if exit_ in seen:
            way = path(seen, exit_)
            errors.append(f"shortcut: the exit can be reached without crossing {zone_id} (by {way[len(way) // 2]})")
    report.append(f"cuts: every way to the exit crosses {', '.join(cfg['cuts'])}")

    # No place to get stuck: wherever a player can get from the entry (falling, swimming), the exit can still be reached.
    reach, back = {entry}, {}
    queue = deque([entry])
    while queue:
        c = queue.popleft()
        for n in anyway.neighbours(c):
            back.setdefault(n, []).append(c)
            if n not in reach:
                reach.add(n)
                queue.append(n)
    out = {exit_}
    queue = deque([exit_])
    while queue:
        c = queue.popleft()
        for p in back.get(c, ()):
            if p not in out:
                out.add(p)
                queue.append(p)
    stuck = reach - out
    if stuck:
        where = {}
        for c in stuck:
            where.setdefault(lv.air.get(c, "?"), []).append(c)
        for owner, cells in sorted(where.items(), key=lambda kv: -len(kv[1]))[:6]:
            errors.append(f"stuck: {len(cells)} places in {owner} from which the exit cannot be reached (e.g. {min(cells)})")
    report.append(f"stuck: {len(reach)} places reachable from the entry, every one with a way on to the exit"
                  if not stuck else f"stuck: {len(stuck)} of {len(reach)} places reachable from the entry lead nowhere")

    # The gate down.
    locked = cfg["locked"]
    shut = dict(built, **{locked["gate"]: locked["state"]})
    seen = Walker(lv, shut, max_drop=DOWN_ANY, swim=True).search(entry)
    for aid in locked["unreachable"]:
        c = start_cell(lv, anyway, aid)
        if c in seen:
            errors.append(f"locked: {aid} can be reached with {locked['gate']} {locked['state']}")
    report.append(f"locked: with {locked['gate']} {locked['state']}, {', '.join(locked['unreachable'])} cannot be reached")

    # The pump room.
    pump = cfg["pump"]
    a, b = start_cell(lv, anyway, pump["from"]), start_cell(lv, anyway, pump["to"])
    seen = anyway.search(a, goal=b)
    steps = seen[b][0] if b in seen else None
    if steps is None or steps < pump["min"]:
        errors.append(f"pump room: the shortest way across is {steps} blocks, under {pump['min']}")
    report.append(f"pump room: the shortest way across, swimming included, is {steps} blocks "
                  f"({steps * cfg['walk_seconds_per_block']:.0f} s walking, {steps / 5.6 / 6:.1f} pump cycles running)")

    # Doors clear.
    for oid, cells in lv.openings.items():
        if oid in lv.gates and lv.gates[oid][1][lv.gates[oid][2]] != AIR:
            continue
        if all(c in lv.blocks for c in cells):
            continue                                  # a window of bars
        bottom = min(c[1] for c in cells)
        columns = {(c[0], c[2]) for c in cells}
        if not any(walk.passable((x, bottom, z)) and walk.passable((x, bottom + 1, z)) for x, z in columns):
            errors.append(f"opening {oid}: not two blocks of air through it")
        sides = 0
        for x, z in columns:
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (0, 1, -1):
                    if walk.standable((x + dx, bottom + dy, z + dz)) and (x + dx, z + dz) not in columns:
                        sides += 1
                        break
        if sides < 2:
            errors.append(f"opening {oid}: no room to stand on both sides")

    # Water.
    still = set(lv.water)
    errors += leaks(lv, still, built, "still water")
    flood = lv.layout["flood"]
    for stage in flood["stages"]:
        gates = dict(built, **stage["gates"])
        cells = flooded(lv, stage, gates) | still
        errors += leaks(lv, cells, gates, f"flood stage {stage['id']}", stage["spaces"])
        report.append(f"flood stage {stage['id']}: {len(cells - still)} cells of water up to y {stage['level']} in "
                      f"{stage['seconds']} s ({len(cells - still) / (stage['seconds'] * 20):.0f} a tick)")

    # The look: no vanilla stone anywhere, and a lit lamp in every space that is not left dark on purpose.
    banned = tuple(cfg.get("banned", ()))
    seen = set()
    for m in lv.materials_cache.values():
        for block, _ in (lv.mix_of(m) if isinstance(m, tuple) else [[m, 1]]):
            name = block.split("[")[0]
            if name.startswith(banned) and name not in seen:
                seen.add(name)
                errors.append(f"look: {name} is vanilla stone (banned in checks.banned)")
    unlit = set(cfg.get("unlit", ()))
    for sid in lv.spaces:
        lit = [c for c in lv.lamps if lv.air.get(c) == sid and "mode=off" not in lv.blocks[c]]
        if not lit and sid not in unlit:
            errors.append(f"lights: {sid} has no lit lamp (list it in checks.unlit if it is dark on purpose)")
        if lit and sid in unlit:
            errors.append(f"lights: {sid} is listed as unlit but has lamps")
    counts = {}
    for c in lv.lamps:
        counts[lv.air.get(c)] = counts.get(lv.air.get(c), 0) + 1
    report.append("lamps: " + ", ".join(f"{k} {v}" for k, v in sorted(counts.items(), key=lambda kv: str(kv[0]))))

    # The build.
    lo, hi = lv.bounds()
    clear = (hi[0] - lo[0] + 1) * (hi[1] - lo[1] + 1) * (hi[2] - lo[2] + 1)
    filled = len(lv.materials_cache)
    # Built over an old build: what it had filled is written empty first, then filled again.
    ticks = clear / (8000 * 8) + 2 * filled / 8000
    report.append(f"build: {clear} cells cleared, {filled} filled; at most about {ticks / 20:.1f} s in batches")
    if ticks / 20 > cfg["max_build_seconds"]:
        errors.append(f"build: about {ticks / 20:.0f} s, over {cfg['max_build_seconds']}")
    return errors, report, ways


def flooded(lv, stage, gates):
    """The cells a flood stage fills: the air of its spaces (and of the openings between them, and what stands in them)
    under its level."""
    spaces = set(stage["spaces"])
    out = set()
    for c, owner in lv.air.items():
        if c[1] >= stage["level"]:
            continue
        if owner in spaces or owner.startswith("opening:") and opening_between(lv, owner[8:], spaces):
            out.add(c)
    for c, space in lv.owner.items():
        if space in spaces and c[1] < stage["level"] and c in lv.blocks and permeable(lv.blocks[c]):
            out.add(c)
    return out


def opening_between(lv, oid, spaces):
    found = set()
    for x, y, z in lv.openings[oid]:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            o = lv.air.get((x + dx, y, z + dz))
            if o and not o.startswith("opening:"):
                found.add(o)
    return found <= spaces


def leaks(lv, water, gates, what, spaces=None):
    """Where `water` touches, beside or under it, air or a block water flows into that is not water itself."""
    blocked, opened = set(), set()
    for gid, state in gates.items():
        cells, states, _ = lv.gates[gid]
        (opened if permeable(states[state]) else blocked).update(cells)      # bars hold no water back
    out = []
    for x, y, z in water:
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
            n = (x + dx, y + dy, z + dz)
            if n in water or n in blocked:
                continue
            if n in opened or n in lv.air and n not in lv.blocks or n in lv.blocks and permeable(lv.blocks[n]):
                out.append(f"{what}: leaks from {(x, y, z)} into {n} ({lv.air.get(n) or lv.owner.get(n) or 'a block'})")
                break
        if len(out) >= 5:
            out.append(f"{what}: ...")
            break
    return out
