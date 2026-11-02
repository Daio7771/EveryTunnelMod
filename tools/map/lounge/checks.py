"""
Checks on Level 4's plan (see level.py). Each returns its report lines and raises SystemExit when
the plan is wrong, so a bad LEVEL_4 never reaches a world.

- walk(): from the start in Reception, a walk over the plan's cells, round thin walls and solid
  furniture, up or down one block at most, must reach every space, and no cell may be left out.
- walled(): every two spaces are kept apart by at least one block, unless a corridor or an opening
  joins them on purpose (a slip in the numbers fails here).
- steps(): three steps down to the Calm room, ending level with its floor.
- zoned(): everywhere a player can be, the pool's water too, lies in one of the level's zones, or
  they would be out of the level, and of its safe rules.
"""

from collections import deque


def walk(lv):
    start = lv.p["START"]
    seen = {start}
    queue = deque([start])
    while queue:
        x, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (x + dx, z + dz)
            if c in lv.floors and c not in lv.blocked and c not in seen and abs(lv.floors[c] - lv.floors[(x, z)]) <= 1:
                seen.add(c)
                queue.append(c)
    missing = set(lv.floors) - lv.blocked - seen
    if missing:
        raise SystemExit(f"Level 4: {len(missing)} cells cannot be reached, e.g. {sorted(missing)[:3]}")
    return [f"Walk: all {len(seen)} free cells reached from Reception, round {len(lv.blocked)} of walls and furniture"]


JOINED_BY_DESIGN = ("corridor", "steps")


def walled(lv):
    def touches(a, b):
        ax0, az0, ax1, az1 = a.box
        bx0, bz0, bx1, bz1 = b.box
        return ax0 <= bx1 and bx0 <= ax1 and az0 <= bz1 and bz0 <= az1

    rooms = [s for s in lv.spaces if s.kind not in JOINED_BY_DESIGN]
    for i, a in enumerate(rooms):
        for b in rooms[i + 1:]:
            if touches(a, b):
                raise SystemExit(f"Level 4: {a.id} and {b.id} have no wall between them")
    return [f"Walls: the {len(rooms)} rooms are all at least one block apart"]


def steps(lv):
    x0, z0, x1, _ = lv.steps.box
    line = [lv.floors[(x, z0)] for x in range(x0, x1)]
    calm = lv.rooms[-1].floor
    drops = [a - b for a, b in zip(line, line[1:])]
    if line[-1] != calm or drops != [1] * (len(line) - 1):
        raise SystemExit(f"Level 4: the steps go {line}, the Calm room is at {calm}")
    return [f"Steps: {len(drops)} down, floor heights {line}, Calm room {calm}"]


def zoned(lv):
    zones = [box for _, box, _ in lv.zones()]

    def inside(pos):
        return any(b[0] <= pos[0] <= b[3] and b[1] <= pos[1] <= b[4] and b[2] <= pos[2] <= b[5] for b in zones)

    # A door's own cell can be another level's (the door to B4 is Level 3's).
    door = lv.p["B4_DOOR"]
    cells = [lv.at(x, floor + 1, z) for (x, z), floor in lv.floors.items() if (x, z) != door]
    x0, z0, x1, z1 = lv.p["POOL"]["box"]
    cells += [lv.at(x, y, z) for x in range(x0, x1) for z in range(z0, z1) for y in range(1 - lv.p["POOL"]["depth"], 1)]
    outside = [c for c in cells if not inside(c)]
    if outside:
        raise SystemExit(f"Level 4: {len(outside)} cells lie in no zone, e.g. {outside[:3]}")
    return [f"Zones: all {len(cells)} cells a player can be in lie in a zone"]
