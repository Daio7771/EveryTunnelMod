"""
Checks on Level 4's plan (see level.py). Each returns its report lines and raises SystemExit when
the plan is wrong, so a bad LEVEL_4 never reaches a world.

- walk(): from the start in Reception, a walk over the plan's cells, up or down one block at most,
  must reach every space, and no cell may be left out.
- walled(): every two spaces are kept apart by at least one block, unless a corridor or an opening
  joins them on purpose (a slip in the numbers fails here).
- steps(): three steps down to the Calm room, ending level with its floor.
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
            if c in lv.floors and c not in seen and abs(lv.floors[c] - lv.floors[(x, z)]) <= 1:
                seen.add(c)
                queue.append(c)
    missing = set(lv.floors) - seen
    if missing:
        raise SystemExit(f"Level 4: {len(missing)} cells cannot be reached, e.g. {sorted(missing)[:3]}")
    return [f"Walk: all {len(seen)} cells reached from Reception"]


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
