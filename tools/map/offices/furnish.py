"""
Level 3's furniture (phase B2): every room furnished from a template for its kind (FURNISH in
LEVEL_3 says which), the library laid out in rows of bookshelves round a clearing, a few
plants and clocks in the ring. Everything in order: the same template gives the same room.

A template works in the room's own frame (Frame): u runs along the wall with the room's main
door (the one to the ring), left to right as seen coming in, and v runs from that wall to the
back. Nothing goes within DOOR_CLEARANCE of a door, nor where the room's lights or other
fittings are, so a template written for one size still fits another (pieces that do not fit
are left out).

The furniture's joined states (FurnitureBlocks.kt: rows left and right, table tiles on all
sides, stacks up and down) are worked out here the same way the game does, since `/setblock`
takes a state as given.
"""

import math
import random

DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
NAMES = {v: k for k, v in DIRS.items()}

ROW = {"office_desk", "whiteboard", "sofa", "bench", "counter", "library_counter"}
TILE = {"meeting_table", "reading_table"}
STACK = {"server_rack", "metal_shelf", "locker", "vending_machine", "bookshelf"}
# Furniture with a full-block top a box can stand on (the box puzzle's spots).
FLAT_TOPS = {"office_desk", "counter", "filing_cabinet", "library_counter"}
# The ones a player walks through (ModBlocks: noCollision).
WALK_THROUGH = {"desk_lamp", "whiteboard", "electrical_panel", "cafe_chair", "coffee_machine", "potted_plant", "wall_clock",
                "wooden_chair", "desk_phone", "note_board", "note_paper"}


class Frame:
    """A room's own frame: cell(u, v) -> plan (x, z); facing("front" | "back" | "left" | "right")."""

    def __init__(self, box, front):
        x0, z0, x1, z1 = box
        out = DIRS[front]
        self.din = (-out[0], -out[1])                    # from the front wall into the room
        self.r = (-self.din[1], self.din[0])             # to the right, seen coming in
        cells = [(x, z) for x in range(x0, x1) for z in range(z0, z1)]
        us = [x * self.r[0] + z * self.r[1] for x, z in cells]
        vs = [x * self.din[0] + z * self.din[1] for x, z in cells]
        self.u0, self.v0 = min(us), min(vs)
        self.W, self.D = max(us) - self.u0 + 1, max(vs) - self.v0 + 1

    def cell(self, u, v):
        uu, vv = u + self.u0, v + self.v0
        return uu * self.r[0] + vv * self.din[0], uu * self.r[1] + vv * self.din[1]

    def facing(self, local):
        d = {"front": (-self.din[0], -self.din[1]), "back": self.din,
             "right": self.r, "left": (-self.r[0], -self.r[1])}[local]
        return NAMES[d]


class Furnisher:
    def __init__(self, lv):
        self.lv = lv
        self.p = lv.p
        self.items = {}            # (x, y, z) -> (block name, facing or None)
        self.parts = {}            # (x, y, z) -> (col, row): the box shelf's parts
        self.reserved = set()      # (x, z) kept clear
        self.locker_fronts = set() # (x, z) in front of a locker's door, kept clear to step out

    def free(self, x, y, z):
        lv = self.lv
        return (x, y, z) in lv.air and (x, y, z) not in lv.fittings and (x, y, z) not in self.items

    def put(self, x, y, z, name, facing=None, force=False):
        if not self.free(x, y, z) or (not force and (x, z) in self.reserved):
            return False
        self.items[(x, y, z)] = (name, facing)
        return True

    # --- rooms -----------------------------------------------------------------------

    def doors_of(self, box):
        """{side: [door cells (x, z)]} in the walls round a room's box."""
        x0, z0, x1, z1 = box
        sides = {"north": [(x, z0 - 1) for x in range(x0, x1)], "south": [(x, z1) for x in range(x0, x1)],
                 "west": [(x0 - 1, z) for z in range(z0, z1)], "east": [(x1, z) for z in range(z0, z1)]}
        out = {}
        for side, cells in sides.items():
            doors = [(x, z) for x, z in cells if self.lv.air.get((x, 1, z), ("",))[0] == "door"]
            if doors:
                out[side] = doors
        return out

    def room(self, s, template):
        doors = self.doors_of(s.box)
        if not doors:
            raise SystemExit(f"LEVEL_3: room {s.id} has no door")
        # The front is the wall with the door to the ring; a corner room's first door otherwise.
        front = None
        for side, cells in sorted(doors.items()):
            dx, dz = DIRS[side]
            if any(self.lv.air.get((x + dx, 1, z + dz), ("",))[0] == "corridor" for x, z in cells):
                front = side
        front = front or sorted(doors)[0]
        fr = Frame(s.box, front)
        c = self.p.DOOR_CLEARANCE
        x0, z0, x1, z1 = s.box
        self.reserved = {(x, z) for x in range(x0, x1) for z in range(z0, z1)
                         if any(abs(x - dx) <= c and abs(z - dz) <= c for cells in doors.values() for dx, dz in cells)}
        anchor = TEMPLATES[template](self, fr)
        if not any(name == "locker" and y == 1 and x0 <= x < x1 and z0 <= z < z1 for (x, y, z), (name, _) in self.items.items()):
            if not self.hiding_locker(fr):
                raise SystemExit(f"LEVEL_3: no room for a locker to hide in, in {s.id}")
        return anchor

    # --- helpers for the templates ---------------------------------------------------

    def at(self, fr, u, v, name, facing=None, y=1, force=False):
        if not (0 <= u < fr.W and 0 <= v < fr.D):
            return False
        x, z = fr.cell(u, v)
        return self.put(x, y, z, name, fr.facing(facing) if facing else None, force)

    def stack(self, fr, u, v, name, facing, height):
        if self.at(fr, u, v, name, facing):
            for y in range(2, height + 1):
                self.at(fr, u, v, name, facing, y=y, force=True)

    def desk_pod(self, fr, u0, v, desks=4):
        """Two rows of desks back to back, a chair at each, a phone on every other desk."""
        for i in range(desks):
            u = u0 + i
            if self.at(fr, u, v, "office_desk", "front"):
                self.at(fr, u, v - 1, "office_chair", "back")
                if i % 2 == 0:
                    self.at(fr, u, v, "desk_phone", "front", y=2)
            if self.at(fr, u, v + 1, "office_desk", "back"):
                self.at(fr, u, v + 2, "office_chair", "front")
                if i % 2 == 1:
                    self.at(fr, u, v + 1, "desk_phone", "back", y=2)

    def hiding_locker(self, fr):
        """A locker to hide in (every room has one but the library; the Lockers and Changing
        rooms are full of them): put in last, against a wall, facing into the room, with the
        floor in front of its door free to step out onto. Tried along the side walls from the
        front (the nearest to the way in), then along the back wall from the middle out."""
        spots = [(0, v, "right") for v in range(fr.D)] + [(fr.W - 1, v, "left") for v in range(fr.D)]
        spots.sort(key=lambda s: s[1])
        mid = fr.W // 2
        spots += [(u, fr.D - 1, "front") for u in sorted(range(fr.W), key=lambda u: abs(u - mid))]
        step = {"right": (1, 0), "left": (-1, 0), "front": (0, -1)}
        for u, v, facing in spots:
            du, dv = step[facing]
            fu, fv = u + du, v + dv
            if not (0 <= fu < fr.W and 0 <= fv < fr.D):
                continue
            fx, fz = fr.cell(fu, fv)
            x, z = fr.cell(u, v)
            if not (self.free(x, 1, z) and self.free(x, 2, z)) or (x, z) in self.reserved:
                continue
            if not (self.free(fx, 1, fz) and self.free(fx, 2, fz)):
                continue
            self.stack(fr, u, v, "locker", facing, 2)
            return True
        return False

    def box_shelf(self, fr, u0):
        """The box puzzle's empty shelf (BoxShelfBlock): 2 wide and 3 high against the back wall
        at u0 and u0 + 1, facing into the room; column 0 is the left one as seen from in front
        (the lower u). Put in before the back wall's other shelves; its foot's (x, z), facing."""
        cells = [(u0 + col, row_) for col in (0, 1) for row_ in (0, 1, 2)]
        if not all(self.free(*self._at(fr, u, fr.D - 1, row_ + 1)) for u, row_ in cells):
            raise SystemExit("LEVEL_3: no room for the box shelf in Storage")
        for u, row_ in cells:
            x, z = fr.cell(u, fr.D - 1)
            self.put(x, row_ + 1, z, "box_shelf", fr.facing("front"), force=True)
            self.parts[(x, row_ + 1, z)] = (u - u0, row_)
        x, z = fr.cell(u0, fr.D - 1)
        return x, z, fr.facing("front")

    @staticmethod
    def _at(fr, u, v, y):
        x, z = fr.cell(u, v)
        return x, y, z

    def back_wall(self, fr, u0, u1, name, height=1, y=1):
        for u in range(u0, u1 + 1):
            if height > 1:
                self.stack(fr, u, fr.D - 1, name, "front", height)
            else:
                self.at(fr, u, fr.D - 1, name, "front", y=y)

    def details(self, fr):
        """Plants in the back corners, a clock high on the back wall."""
        self.at(fr, 0, fr.D - 1, "potted_plant")
        self.at(fr, fr.W - 1, fr.D - 1, "potted_plant")
        self.at(fr, fr.W // 2, fr.D - 1, "wall_clock", "front", y=3)

    def book_cabinet(self, fr, u, v, facing="front"):
        """A filing cabinet near (u, v), tried a little to each side; its (x, z), or None."""
        for du in (0, 1, -1, 2, -2, 3, -3):
            if self.at(fr, u + du, v, "filing_cabinet", facing):
                return fr.cell(u + du, v)
        return None

    # --- the library -----------------------------------------------------------------

    def library(self):
        """Bookshelves round the walls; rows of double-sided shelves (BOOKSHELF_HEIGHT high,
        AISLE apart) in the four quarters, kept off the aisles from the four doors and off a
        clearing round the hatch, where the librarian's desk stands in a U round the hatch
        (hatch_desk); the card catalogue by the south door."""
        p, L = self.p, self.p.LIBRARY_LAYOUT
        x0, z0, x1, z1 = p.LIBRARY
        h, aisle, edge = L["shelf_height"], L["aisle"], L["perimeter"]
        doors = [(x, z) for x in range(x0 - 1, x1 + 1) for z in range(z0 - 1, z1 + 1)
                 if self.lv.air.get((x, 1, z), ("",))[0] == "door"]
        cross_x = (min(x for x, z in doors if z in (z0 - 1, z1)), max(x for x, z in doors if z in (z0 - 1, z1)))
        cross_z = (min(z for x, z in doors if x in (x0 - 1, x1)), max(z for x, z in doors if x in (x0 - 1, x1)))
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        half = L["clearing"] // 2
        clearing = (cx - half, cz - half, cx + half, cz + half)              # x0, z0, x1, z1 (exclusive)
        keep = [((cross_x[0] - 1, z0, cross_x[1] + 2, z1)),                   # the north-south aisle
                ((x0, cross_z[0] - 1, x1, cross_z[1] + 2)),                   # the west-east aisle
                (clearing[0] - 1, clearing[1] - 1, clearing[2] + 1, clearing[3] + 1)]
        desk = L["desk"]
        keep += [desk["catalog_area"]]

        def kept(x, z):
            return any(a <= x < b and c <= z < d for a, c, b, d in keep)

        def shelf(x, z, facing):
            if self.put(x, 1, z, "bookshelf", facing):
                for y in range(2, h + 1):
                    self.put(x, y, z, "bookshelf", facing)

        # Against the walls, facing in, but by the doors.
        near_door = {(x, z) for x in range(x0, x1) for z in range(z0, z1)
                     if any(abs(x - a) <= 2 and abs(z - b) <= 2 for a, b in doors)}
        for x in range(x0, x1):
            for z, facing in ((z0, "south"), (z1 - 1, "north")):
                if (x, z) not in near_door:
                    shelf(x, z, facing)
        for z in range(z0 + 1, z1 - 1):
            for x, facing in ((x0, "east"), (x1 - 1, "west")):
                if (x, z) not in near_door:
                    shelf(x, z, facing)
        # Rows running west-east, one block thick, AISLE apart, off the aisles and the clearing.
        for z in range(z0 + 1 + edge, z1 - 1 - edge, aisle + 1):
            run = [x for x in range(x0 + 1 + edge, x1 - 1 - edge) if not kept(x, z)]
            for x in run:
                if (x - 1 in run) or (x + 1 in run):                         # no lone shelves
                    shelf(x, z, "north")
        self.hatch_desk()
        cat_x, (cat_z0, cat_z1), cat_facing = desk["catalog"]
        for z in range(cat_z0, cat_z1):
            self.put(cat_x, 1, z, "card_catalog", cat_facing)
        self.library_books(keep, near_door)

    def library_books(self, keep, near_door):
        """The book puzzle's spots, all in the library (BookHooks puts the books at random ones):
        LIBRARY_LAYOUT["books"] says how many on book carts, on the floor and on the card
        catalogue. Carts and floor spots are in the aisles between the shelf rows, against a
        shelf, off the kept aisles and the doors, spread round the library and 4 blocks or more
        apart, carts and floor in turn; the catalogue's from its middle. Into lv.book_spots,
        [(x, y, z)]: y 2 on a cart or the catalogue, 1 on the floor."""
        p, cfg = self.p, self.p.LIBRARY_LAYOUT["books"]
        x0, z0, x1, z1 = p.LIBRARY

        def kept(x, z):
            return any(a <= x < b and c <= z < d for a, c, b, d in keep)

        shelves = {(x, z) for (x, y, z), (name, _) in self.items.items() if name == "bookshelf" and y == 1}
        aisle = sorted((x, z) for x in range(x0 + 1, x1 - 1) for z in range(z0 + 1, z1 - 1)
                       if self.free(x, 1, z) and self.free(x, 2, z) and not kept(x, z) and (x, z) not in near_door
                       and ((x, z - 1) in shelves or (x, z + 1) in shelves)
                       and (x - 1, z) not in shelves and (x + 1, z) not in shelves)
        cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
        aisle.sort(key=lambda c: math.atan2(c[1] - cz, c[0] - cx))
        n = cfg["carts"] + cfg["floor"]
        picked = []
        for k in range(n):                                                  # evenly round, the next one if too close
            for j in range(len(aisle)):
                x, z = aisle[((k * len(aisle)) // n + j) % len(aisle)]
                if all(abs(x - a) + abs(z - b) >= 4 for a, b in picked):
                    picked.append((x, z))
                    break
        if len(picked) < n:
            raise SystemExit(f"LEVEL_3 LIBRARY_LAYOUT books: room for {len(picked)} cart and floor spots, {n} wanted")
        spots, carts = [], cfg["carts"]
        for i, (x, z) in enumerate(picked):
            if carts > 0 and (i % 2 == 0 or n - i <= carts):
                carts -= 1
                self.put(x, 1, z, "book_cart", "north", force=True)        # its long side along the aisle
                spots.append((x, 2, z))
            else:
                spots.append((x, 1, z))
        cat_x, (cat_z0, cat_z1), _ = p.LIBRARY_LAYOUT["desk"]["catalog"]
        for k in range(cfg["catalog"]):
            spots.append((cat_x, 2, (cat_z0 + cat_z1) // 2 + k))
        self.lv.book_spots = spots

    def hatch_desk(self):
        """The librarian's desk: a U of library counters round the hatch's frame, `ring` blocks
        of floor between them, open on the `open` side, each piece facing out. The books go
        back onto it: `returns` places spread along it, off its corners (lv.book_returns,
        [(x, z, facing)]); a lamp on the `lamp` corner."""
        p, desk = self.p, self.p.LIBRARY_LAYOUT["desk"]
        fx0, fz0, fx1, fz1 = p.HATCH["frame"]
        r = desk["ring"]
        x0, z0, x1, z1 = fx0 - r - 1, fz0 - r - 1, fx1 + r, fz1 + r          # the desk's cells, inclusive
        sides = {"north": [(x, z0) for x in range(x0, x1 + 1)],
                 "east": [(x1, z) for z in range(z0 + 1, z1)],
                 "south": [(x, z1) for x in range(x1, x0 - 1, -1)],
                 "west": [(x0, z) for z in range(z1 - 1, z0, -1)]}
        if desk["open"] not in sides:
            raise SystemExit(f"LEVEL_3 LIBRARY_LAYOUT desk: open must be one of {', '.join(sides)}")
        # Round the U from one open end to the other: the corners go with the north and south rows.
        order = ["north", "east", "south", "west"]
        start = (order.index(desk["open"]) + 1) % 4
        path = []
        for side in order[start:] + order[:start]:
            if side == desk["open"]:
                continue
            for x, z in sides[side]:
                if not self.put(x, 1, z, "library_counter", side, force=True):
                    raise SystemExit(f"LEVEL_3: no room for the librarian's desk at ({x}, {z})")
                path.append((x, z, side))
        corners = {(x0, z0), (x1, z0), (x0, z1), (x1, z1)}
        lamp = {"northwest": (x0, z0), "northeast": (x1, z0), "southwest": (x0, z1), "southeast": (x1, z1)}[desk["lamp"]]
        if lamp not in [(x, z) for x, z, _ in path]:
            raise SystemExit(f"LEVEL_3 LIBRARY_LAYOUT desk: the lamp's corner {desk['lamp']} is on the open side")
        self.put(lamp[0], 2, lamp[1], "desk_lamp", "south")
        places = [c for c in path if (c[0], c[1]) not in corners]
        n = desk["returns"]
        self.lv.book_returns = [places[round(i * (len(places) - 1) / (n - 1))] for i in range(n)]

    # --- the result ------------------------------------------------------------------

    def finish(self):
        """The furniture's block states, joins worked out, into the level's fittings."""
        lv = self.lv
        items = self.items

        def same(pos, name, facing):
            other = items.get(pos)
            return other is not None and other[0] == name and (facing is None or other[1] == facing)

        for (x, y, z), (name, facing) in sorted(items.items()):
            bid = f"every_tunnel:{name}"
            if name in ROW:
                lx, lz = DIRS[{"north": "east", "east": "south", "south": "west", "west": "north"}[facing]]
                left = same((x + lx, y, z + lz), name, facing)
                right = same((x - lx, y, z - lz), name, facing)
                state = f"{bid}[facing={facing},left={str(left).lower()},right={str(right).lower()}]"
            elif name in TILE:
                sides = {s: same((x + d[0], y, z + d[1]), name, None) for s, d in DIRS.items()}
                state = bid + "[" + ",".join(f"{s}={str(sides[s]).lower()}" for s in ("east", "north", "south", "west")) + "]"
            elif name == "box_shelf":
                col, row_ = self.parts[(x, y, z)]
                state = f"{bid}[col={col},facing={facing},row={row_}]"
            elif name in STACK:
                up, down = same((x, y + 1, z), name, facing), same((x, y - 1, z), name, facing)
                state = f"{bid}[down={str(down).lower()},facing={facing},up={str(up).lower()}]"
            else:
                state = f"{bid}[facing={facing or 'north'}]"
            lv.fittings[(x, y, z)] = state
            if name in WALK_THROUGH:
                lv.walk_through.add((x, y, z))
            else:
                lv.furniture.add((x, y, z))


# --- the templates: (furnisher, frame) -> a filing cabinet's (x, z) or None (unused) -----

def open_office(f, fr):
    for u0 in (2, fr.W - 6):
        f.desk_pod(fr, u0, 5)
    f.back_wall(fr, fr.W // 2 - 2, fr.W // 2 + 1, "filing_cabinet")
    f.details(fr)


def desks(f, fr, book=False):
    for u0 in (3, 10, 17):
        f.desk_pod(fr, u0, 5)
    anchor = f.book_cabinet(fr, fr.W // 2, fr.D - 1) if book else None
    f.back_wall(fr, fr.W // 2 - 4, fr.W // 2 + 3, "filing_cabinet")
    f.details(fr)
    return anchor


def finance(f, fr):
    return desks(f, fr, book=True)


def hr(f, fr):
    for u0 in (3, 10):
        f.desk_pod(fr, u0, 5)
    for u in range(17, 20):
        f.at(fr, u, fr.D - 1, "sofa", "front")
    f.at(fr, 20, fr.D - 1, "potted_plant")
    anchor = f.book_cabinet(fr, 6, fr.D - 1)
    f.back_wall(fr, 4, 9, "filing_cabinet")
    f.details(fr)
    return anchor


def archive(f, fr):
    gap = (fr.W // 2 - 1, fr.W // 2)
    anchor = f.book_cabinet(fr, fr.W // 2 - 3, 4)
    for v_front in (4, 8):
        for u in range(2, fr.W - 2):
            if u in gap:
                continue
            f.at(fr, u, v_front, "filing_cabinet", "front")
            f.at(fr, u, v_front + 1, "filing_cabinet", "back")
    f.back_wall(fr, 2, fr.W - 3, "filing_cabinet")
    f.details(fr)
    return anchor


def meeting(f, fr):
    u0, u1, v0, v1 = 6, fr.W - 6, 5, 8
    for u in range(u0, u1):
        for v in range(v0, v1):
            f.at(fr, u, v, "meeting_table")
    for u in range(u0 + 1, u1, 2):
        f.at(fr, u, v0 - 1, "office_chair", "back")
        f.at(fr, u, v1, "office_chair", "front")
    f.at(fr, u0 - 1, (v0 + v1) // 2, "office_chair", "right")
    f.at(fr, u1, (v0 + v1) // 2, "office_chair", "left")
    for u in range(fr.W // 2 - 2, fr.W // 2 + 2):
        f.at(fr, u, fr.D - 1, "whiteboard", "front", y=2)
    f.details(fr)


def management(f, fr):
    mid = fr.W // 2
    for u in range(mid - 1, mid + 2):
        f.at(fr, u, 8, "office_desk", "back")
    f.at(fr, mid - 1, 8, "desk_phone", "back", y=2)
    f.at(fr, mid, 9, "office_chair", "front")
    f.at(fr, mid - 1, 7, "office_chair", "back")
    f.at(fr, mid + 1, 7, "office_chair", "back")
    for v in range(4, 7):
        f.at(fr, 0, v, "sofa", "right")
    anchor = f.book_cabinet(fr, fr.W - 6, fr.D - 1)
    f.back_wall(fr, fr.W - 8, fr.W - 4, "filing_cabinet")
    f.details(fr)
    return anchor


def servers(f, fr):
    gap = (fr.W // 2 - 1, fr.W // 2)
    for v_front in (4, 8):
        for u in range(2, fr.W - 2):
            if u not in gap:
                f.stack(fr, u, v_front, "server_rack", "front", 2)
                f.stack(fr, u, v_front + 1, "server_rack", "back", 2)
    for u in (4, 6):
        f.at(fr, u, fr.D - 1, "electrical_panel", "front", y=2)


def storage(f, fr):
    u0 = fr.W // 2 - 1
    f.lv.box_shelf = f.box_shelf(fr, u0)
    # Storage's locker stands right of the shelf, on the back wall: the Observer comes in by the
    # door, so one by the door would mean running towards him after putting a box up.
    if not f.at(fr, u0 + 2, fr.D - 1, "locker", "front"):
        raise SystemExit("LEVEL_3: no room for Storage's locker beside the box shelf")
    f.at(fr, u0 + 2, fr.D - 1, "locker", "front", y=2, force=True)
    # Shelves only along the walls, the middle left open: from the box shelf the door is in
    # plain sight, so the Observer is seen coming in.
    f.back_wall(fr, 2, fr.W - 3, "metal_shelf", height=2)
    for v in range(fr.D - 1):
        f.stack(fr, 0, v, "metal_shelf", "right", 2)
        f.stack(fr, fr.W - 1, v, "metal_shelf", "left", 2)
    f.at(fr, fr.W - 2, fr.D - 3, "cardboard_box", "front")
    f.at(fr, fr.W - 2, fr.D - 4, "cardboard_box", "left")


def printing(f, fr):
    f.back_wall(fr, 2, fr.W - 3, "metal_shelf", height=2)
    for u in range(fr.W // 2 - 3, fr.W // 2 + 3):
        f.at(fr, u, 6, "office_desk", "front")
    for u, v in ((3, 10), (4, 10), (3, 9)):
        f.at(fr, u, v, "cardboard_box", "front")
    f.details(fr)


def maintenance(f, fr):
    f.back_wall(fr, 6, fr.W - 3, "metal_shelf", height=2)
    for v in range(3, 9):
        f.at(fr, 0, v, "counter", "right")
    anchor = f.book_cabinet(fr, 2, fr.D - 1)
    f.at(fr, fr.W - 1, 5, "electrical_panel", "left", y=2)
    f.at(fr, fr.W - 2, 9, "cardboard_box", "front")
    return anchor


def electrical(f, fr):
    for u in range(2, fr.W - 2, 2):
        f.at(fr, u, fr.D - 1, "electrical_panel", "front", y=2)
    for v in range(4, fr.D - 2, 2):
        f.at(fr, 0, v, "electrical_panel", "right", y=2)


def lockers(f, fr, plant=False):
    f.back_wall(fr, 1, fr.W - 2, "locker", height=2)
    for v in range(4, fr.D - 2):
        f.stack(fr, 0, v, "locker", "right", 2)
        f.stack(fr, fr.W - 1, v, "locker", "left", 2)
    for u in range(4, fr.W - 4):
        f.at(fr, u, 7, "bench", "front")
    # A desk by the front wall, in the corner left of the way in: the room's note lies on it.
    f.at(fr, 1, 0, "office_desk", "back")
    f.at(fr, 1, 1, "office_chair", "front")
    if plant:
        f.at(fr, fr.W - 1, fr.D - 1, "potted_plant")


def changing(f, fr):
    f.back_wall(fr, 1, fr.W - 2, "locker", height=2)
    for v in range(5, fr.D - 2):
        f.stack(fr, 0, v, "locker", "right", 2)
    for u in range(4, fr.W - 4):
        f.at(fr, u, 7, "bench", "front")
    f.at(fr, fr.W - 1, fr.D - 1, "potted_plant")
    f.at(fr, fr.W - 1, 5, "water_dispenser", "left")


def cafeteria(f, fr):
    for v in (4, 8):
        for u in range(3, fr.W - 2, 4):
            if f.at(fr, u, v, "cafe_table", "front"):
                f.at(fr, u, v - 1, "cafe_chair", "back")
                f.at(fr, u, v + 1, "cafe_chair", "front")
    for u in range(4, 12):
        f.at(fr, u, fr.D - 1, "counter", "front")
    for u in (6, 9):
        f.at(fr, u, fr.D - 1, "coffee_machine", "front", y=2)
    for u in (14, 16):
        f.stack(fr, u, fr.D - 1, "vending_machine", "front", 2)
    f.at(fr, 19, fr.D - 1, "water_dispenser", "front")
    f.details(fr)


def break_room(f, fr):
    for u in range(fr.W // 2 - 2, fr.W // 2 + 2):
        f.at(fr, u, fr.D - 1, "sofa", "front")
    for u in (fr.W // 2 - 1, fr.W // 2):
        f.at(fr, u, fr.D - 3, "cafe_table", "front")
    for v in range(4, 7):
        f.at(fr, 0, v, "counter", "right")
    f.at(fr, 0, 5, "coffee_machine", "right", y=2)
    f.stack(fr, fr.W - 1, 6, "vending_machine", "left", 2)
    f.at(fr, fr.W - 1, 9, "water_dispenser", "left")
    f.at(fr, fr.W - 1, fr.D - 1, "potted_plant")
    f.at(fr, fr.W - 3, fr.D - 1, "wall_clock", "front", y=3)


TEMPLATES = {
    "open_office": open_office, "desks": desks, "finance": finance, "hr": hr, "archive": archive,
    "meeting": meeting, "management": management, "servers": servers, "storage": storage,
    "printing": printing, "maintenance": maintenance, "electrical": electrical, "lockers": lockers,
    "changing": changing, "cafeteria": cafeteria, "break": break_room,
}


def furnish(lv):
    """Furnishes the level (also the electrical room and its note, and the documents, into
    lv.notes; the book puzzle's spots into lv.book_spots)."""
    p = lv.p
    f = Furnisher(lv)
    for s, kind in lv.rooms:
        template = p.FURNISH.get(s.id)
        if template is None:
            continue
        if template not in TEMPLATES:
            raise SystemExit(f"LEVEL_3 FURNISH: no template {template!r} (for {s.id}); there are {', '.join(sorted(TEMPLATES))}")
        f.room(s, template)
    f.reserved = set()
    f.library()
    room = p.ELECTRICAL_ROOM
    for x, z, facing in room["panels"]:
        f.put(x, 2, z, "electrical_panel", facing)
    for x, z, facing in room["cabinets"]:
        f.put(x, 1, z, "filing_cabinet", facing)
    note = room["note"]
    if not f.put(note["x"], 2, note["z"], "note_board", note["facing"]):
        raise SystemExit(f"LEVEL_3 ELECTRICAL_ROOM: no room for the note board at ({note['x']}, {note['z']})")
    lv.notes[note["id"]] = (note["x"], 2, note["z"])
    for x, z in p.CORRIDOR_PLANTS:
        f.put(x, 1, z, "potted_plant", "north")
    for x, z, facing in p.CORRIDOR_CLOCKS:
        f.put(x, 3, z, "wall_clock", facing)
    note_papers(f)
    lv.box_spots = box_spots(f)
    lv.loot = loot_spots(f)
    lv.water_sources = water_sources(f)
    f.finish()


def note_papers(f):
    """The company's documents (NOTE_PAPERS: room id -> note id): a loose sheet on a desk or
    counter in the room (a filing cabinet when there is none), the one
    nearest the room's doors; into lv.notes. Put in before the box spots, which then keep off it."""
    lv, p = f.lv, f.lv.p
    rooms = {s.id: s for s, _ in lv.rooms}
    for room_id, note_id in p.NOTE_PAPERS.items():
        if room_id not in rooms:
            raise SystemExit(f"LEVEL_3 NOTE_PAPERS: no room {room_id!r}")
        box = rooms[room_id].box
        x0, z0, x1, z1 = box
        doors = [c for cells in f.doors_of(box).values() for c in cells]
        tops = [(x, z, name, facing) for (x, y, z), (name, facing) in f.items.items()
                if y == 1 and name in FLAT_TOPS and x0 <= x < x1 and z0 <= z < z1
                and f.free(x, 2, z)]
        if not tops:
            raise SystemExit(f"LEVEL_3 NOTE_PAPERS: nothing in {room_id} to lay the note {note_id!r} on")
        x, z, _, facing = min(tops, key=lambda t: (t[2] == "filing_cabinet",
                                                   min(abs(t[0] - dx) + abs(t[1] - dz) for dx, dz in doors), t[0], t[1]))
        f.put(x, 2, z, "note_paper", facing, force=True)
        lv.notes[note_id] = (x, 2, z)


def loot_spots(f):
    """The loot (LOOT: (item, room id) per piece): each on the floor against a wall of its room,
    clear of the doors, of the floor in front of lockers and of the box spots, 3 blocks or more
    apart, picked the same way every run (seeded by the room). [(item, x, z, room id)]."""
    lv, p = f.lv, f.lv.p
    rooms = {s.id: s for s, _ in lv.rooms}
    steps = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    fronts = {(x + steps[fc][0], z + steps[fc][1]) for (x, y, z), (name, fc) in f.items.items() if name == "locker" and y == 1}
    taken = {(x, z) for x, _, z, _ in lv.box_spots}
    out = []
    for room_id in dict.fromkeys(room for _, room in p.LOOT):
        if room_id not in rooms:
            raise SystemExit(f"LEVEL_3 LOOT: no room {room_id!r}")
        x0, z0, x1, z1 = rooms[room_id].box
        free = sorted((x, z) for x in range(x0, x1) for z in range(z0, z1)
                      if (x in (x0, x1 - 1) or z in (z0, z1 - 1)) and f.free(x, 1, z) and f.free(x, 2, z)
                      and (x, z) not in fronts and (x, z) not in taken
                      and not any(lv.air.get((x + dx, 1, z + dz), ("",))[0] == "door"
                                  for dx in range(-3, 4) for dz in range(-3, 4)))
        items = [item for item, room in p.LOOT if room == room_id]
        if len(free) < len(items):
            raise SystemExit(f"LEVEL_3 LOOT: room for {len(free)} pieces in {room_id}, {len(items)} wanted")
        rng = random.Random(f"loot:{room_id}")
        rng.shuffle(free)
        picked = []
        for x, z in free:                                                   # spread out, 3 blocks or more apart
            if len(picked) < len(items) and all(abs(x - a) + abs(z - b) >= 3 for a, b in picked):
                picked.append((x, z))
        if len(picked) < len(items):
            raise SystemExit(f"LEVEL_3 LOOT: no room for {len(items)} pieces 3 blocks apart in {room_id}")
        for item, (x, z) in zip(items, picked):
            out.append((item, x, z, room_id))
    return out


def water_sources(f):
    """The water dispensers of the rooms in WATER_SOURCES, drunk from in game: [(x, z, room id)]."""
    lv, p = f.lv, f.lv.p
    rooms = {s.id: s for s, _ in lv.rooms}
    out = []
    for room_id in p.WATER_SOURCES:
        x0, z0, x1, z1 = rooms[room_id].box
        found = [(x, z, room_id) for (x, y, z), (name, _) in f.items.items()
                 if name == "water_dispenser" and y == 1 and x0 <= x < x1 and z0 <= z < z1]
        if not found:
            raise SystemExit(f"LEVEL_3 WATER_SOURCES: no water dispenser in {room_id}")
        out += found
    return out


def ring_zone(p, x, z):
    """The ring's zone (level.py, data()) holding the cell (x, z)."""
    n, s_, w, e = p.INNER_CORRIDOR
    for zid, (x0, z0, x1, z1) in (("inner_corridor_north", n), ("inner_corridor_south", s_),
                                  ("inner_corridor_west", (w[0], n[3], w[2], s_[1])),
                                  ("inner_corridor_east", (e[0], n[3], e[2], s_[1]))):
        if x0 <= x < x1 and z0 <= z < z1:
            return zid
    raise SystemExit(f"LEVEL_3 BOX_PUZZLE: ({x}, {z}) is not in the ring")


def box_spots(f):
    """The box puzzle's spots (BoxHooks; a new game puts a box at BOX_PUZZLE["boxes"] of them):
    one in every room but Storage, every other one on a piece of furniture with a flat top and
    the rest on the floor against a wall (clear of doors and lockers), and BOX_PUZZLE["ring"]
    spread round the ring against the library's wall, clear of its doors. Picked the same way
    every run (seeded by the room). [(x, y, z, zone id)]."""
    lv, p = f.lv, f.lv.p
    cfg = p.BOX_PUZZLE
    items = f.items
    steps = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    fronts = {(x + steps[fc][0], z + steps[fc][1]) for (x, y, z), (name, fc) in items.items() if name == "locker" and y == 1}

    def clear_floor(x, z):
        return f.free(x, 1, z) and f.free(x, 2, z) and (x, z) not in fronts

    def near_door(x, z, reach):
        return any(lv.air.get((x + dx, 1, z + dz), ("",))[0] == "door"
                   for dx in range(-reach, reach + 1) for dz in range(-reach, reach + 1))

    spots = []
    for i, (s, _) in enumerate(lv.rooms):
        if s.id in cfg["not_in"]:
            continue
        rng = random.Random(f"box:{s.id}")
        x0, z0, x1, z1 = s.box
        tops = [(x, 2, z) for (x, y, z), (name, _) in items.items()
                if y == 1 and name in FLAT_TOPS and x0 <= x < x1 and z0 <= z < z1
                and f.free(x, 2, z)]
        floor = [(x, 1, z) for x in range(x0, x1) for z in range(z0, z1)
                 if (x in (x0, x1 - 1) or z in (z0, z1 - 1)) and clear_floor(x, z)
                 and not near_door(x, z, p.DOOR_CLEARANCE + 1)]
        pick = tops if (i % 2 == 0 and tops) or not floor else floor
        if not pick:
            raise SystemExit(f"LEVEL_3 BOX_PUZZLE: no spot for a box in {s.id}")
        x, y, z = rng.choice(sorted(pick))
        spots.append((x, y, z, s.id))
    # The ring: against the library's walls, spread round it.
    lx0, lz0, lx1, lz1 = p.LIBRARY
    ring = []
    for x in range(lx0 - 1, lx1 + 1):
        ring += [(x, lz0 - 2), (x, lz1 + 1)]
    for z in range(lz0, lz1):
        ring += [(lx0 - 2, z), (lx1 + 1, z)]
    ring = [(x, z) for x, z in ring if clear_floor(x, z) and not near_door(x, z, 3) and (x, 1, z) in lv.air]
    cx, cz = (lx0 + lx1) / 2, (lz0 + lz1) / 2
    ring.sort(key=lambda c: math.atan2(c[1] - cz, c[0] - cx))
    n = cfg["ring"]
    for k in range(n):
        x, z = ring[(k * len(ring)) // n + len(ring) // (2 * n)]
        spots.append((x, 1, z, ring_zone(p, x, z)))
    if len(spots) < cfg["boxes"]:
        raise SystemExit(f"LEVEL_3 BOX_PUZZLE: {len(spots)} spots for {cfg['boxes']} boxes")
    return spots
