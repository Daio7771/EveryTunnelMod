"""
Checks on the built Level 3, in plan coordinates (see level.py). Each returns its report
lines and raises SystemExit when the level is wrong, so a bad LEVEL_3 never reaches a world.

- walk(): a breadth-first walk over the places a player can stand, from the start in the
  antechamber: up and down one block, falls of up to three, ladders and the hatch, through
  closed doors (the puzzle will open them), round solid furniture (never over it) and
  through what a player walks through. Every room, the library, the hatch, the top of
  the stairs and the door to Level 4 must be reached, and no standing place may be left out.
  Every room must also have a locker whose door opens onto a reached place (to hide in).
- enclosed(): every room and the library is closed by walls but for its doors, so two spaces
  given side by side without a wall between them (a slip in the plan's numbers) fail here.
"""

from collections import deque

NOT_SUPPORT = ("every_tunnel:pipe", "every_tunnel:valve", "minecraft:chain", "every_tunnel:fluorescent_light",
               "minecraft:ladder")


def walk(lv):
    w = lv.world.blocks
    inside = set(lv.air) | lv.passable

    def passable(c):
        return c in inside and (c not in w or c in lv.passable or c in lv.walk_through)

    def climbable(c):
        return c in lv.passable and ("ladder" in w.get(c, "") or "trapdoor" in w.get(c, ""))

    def support(c):
        b = w.get(c)
        if c in lv.furniture or c in lv.walk_through:
            return False                     # nobody needs to climb the furniture to get anywhere
        return b is not None and not b.split("[")[0] in NOT_SUPPORT and "_door" not in b

    def stand(c):
        x, y, z = c
        return passable(c) and passable((x, y + 1, z)) and (support((x, y - 1, z)) or climbable(c))

    p = lv.p
    sx, sz, _ = p.PLAYER_START
    start = (sx, 1, sz)
    if not stand(start):
        raise SystemExit(f"LEVEL_3: the player start {start} is not a place to stand")
    seen = {start}
    todo = deque([start])
    while todo:
        x, y, z = todo.popleft()
        here = (x, y, z)
        steps = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if stand((nx, y + 1, nz)) and passable((x, y + 2, z)):          # one block up
                steps.append((nx, y + 1, nz))
                continue
            if not (passable((nx, y, nz)) and passable((nx, y + 1, nz))):
                continue
            for dy in (0, -1, -2, -3):                                      # level, or a fall
                if stand((nx, y + dy, nz)):
                    steps.append((nx, y + dy, nz))
                    break
                if not passable((nx, y + dy - 1, nz)):
                    break
        up, down = (x, y + 1, z), (x, y - 1, z)
        if (climbable(here) or climbable(up)) and passable(up) and (stand(up) or climbable(up)):
            steps.append(up)
        if climbable(down) and passable(down):
            steps.append(down)
        for n in steps:
            if n not in seen:
                seen.add(n)
                todo.append(n)

    standing = {c for c in inside if stand(c)}
    left = standing - seen
    if left:
        raise SystemExit(f"LEVEL_3: {len(left)} standing places are not reached from the start, e.g. {sorted(left)[:5]}")
    reached_xz = {(x, z) for x, y, z in seen if 0 < y <= p.HEIGHTS["library"]}
    missing = [s.id for s, _ in lv.rooms if not any((x, z) in reached_xz for x in range(s.box[0], s.box[2]) for z in range(s.box[1], s.box[3]))]
    targets = {
        "library": any((x, z) in reached_xz for x in range(p.LIBRARY[0], p.LIBRARY[2]) for z in range(p.LIBRARY[1], p.LIBRARY[3])),
        "hatch": any((x, 1, z) in seen for x in range(p.HATCH["hole"][0], p.HATCH["hole"][2])
                     for z in range(p.HATCH["hole"][1], p.HATCH["hole"][3])),
        "bunker corridor": any(y == lv.bunker_floor + 1 for _, y, _ in seen),
        "door to Level 4": (lv.l4_door[0], lv.l4_door[1], lv.l4_door[2] - 1) in seen,
        "top of the stairs": any((x, lv.top_landing_y + 1, p.STAIRS[3] - 1) in seen for x in range(p.STAIRS[0], p.STAIRS[2])),
    }
    missing += [k for k, ok in targets.items() if not ok]
    # Every room has a locker to hide in whose door opens onto a reached standing place.
    steps = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    lockers = [(x, z, state.split("facing=")[1].split(",")[0].split("]")[0])
               for (x, y, z), state in lv.fittings.items() if y == 1 and state.startswith("every_tunnel:locker[")]
    for s, _ in lv.rooms:
        x0, z0, x1, z1 = s.box
        if not any(x0 <= x < x1 and z0 <= z < z1 and (x + steps[f][0], 1, z + steps[f][1]) in seen for x, z, f in lockers):
            missing.append(f"a locker to hide in, in {s.id}")
    if missing:
        raise SystemExit(f"LEVEL_3: not reached from the start: {', '.join(missing)}")
    return [f"B3 walk: {len(seen)} standing places, all reached from the antechamber: "
            f"{len(lv.rooms)} rooms (each with a locker to hide in), the library, the hatch, the bunker and the door to Level 4"]


def enclosed(lv):
    """Openings in each room's and the library's walls, at foot height, must all be doors."""
    lines = []
    bad = []
    for s in [s for s, _ in lv.rooms] + [lv.library]:
        x0, z0, x1, z1 = s.box
        ring = [(x, z) for x in range(x0 - 1, x1 + 1) for z in (z0 - 1, z1)] + \
               [(x, z) for z in range(z0, z1) for x in (x0 - 1, x1)]
        openings = 0
        for x, z in ring:
            a = lv.air.get((x, 1, z))
            if a is None:
                continue
            if a[0] != "door":
                bad.append(f"{s.id} at ({x}, {z})")
            openings += 1
        lines.append(f"{s.id}: {openings} door cells")
    if bad:
        raise SystemExit("LEVEL_3: spaces touch without a wall between them: " + ", ".join(bad[:8]))
    return lines
