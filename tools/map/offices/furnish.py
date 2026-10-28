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

DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
NAMES = {v: k for k, v in DIRS.items()}

ROW = {"office_desk", "whiteboard", "sofa", "bench", "counter", "library_counter"}
TILE = {"meeting_table", "reading_table"}
STACK = {"server_rack", "metal_shelf", "locker", "vending_machine", "bookshelf"}
# The ones a player walks through (ModBlocks: noCollision).
WALK_THROUGH = {"whiteboard", "electrical_panel", "cafe_chair", "coffee_machine", "potted_plant", "wall_clock",
                "wooden_chair", "desk_phone"}


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
        self.reserved = set()      # (x, z) kept clear

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
        return TEMPLATES[template](self, fr)

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
        """A filing cabinet the room's book will lie on; its (x, z), or None."""
        for du in (0, 1, -1, 2, -2, 3, -3):
            if self.at(fr, u + du, v, "filing_cabinet", facing):
                return fr.cell(u + du, v)
        return None

    # --- the library -----------------------------------------------------------------

    def library(self):
        """Bookshelves round the walls; rows of double-sided shelves (BOOKSHELF_HEIGHT high,
        AISLE apart) in the four quarters, kept off the aisles from the four doors and off a
        clearing round the hatch, where reading tables stand at the corners; the librarian's
        counter and the card catalogue by the south door."""
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
        keep += [desk["area"], desk["catalog_area"]]

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
        # Reading tables at the clearing's corners (the hatch stays in sight down the aisles).
        tw, td = L["table"]
        for ax, az in ((clearing[0] + 1, clearing[1] + 1), (clearing[2] - 1 - tw, clearing[1] + 1),
                       (clearing[0] + 1, clearing[3] - 1 - td), (clearing[2] - 1 - tw, clearing[3] - 1 - td)):
            for x in range(ax, ax + tw):
                for z in range(az, az + td):
                    self.put(x, 1, z, "reading_table")
                self.put(x, 1, az - 1, "wooden_chair", "south")
                self.put(x, 1, az + td, "wooden_chair", "north")
        # The librarian's counter facing the south door, a chair behind it; the card catalogue.
        (cx0, cx1), cz_, facing = desk["counter"]
        for x in range(cx0, cx1):
            self.put(x, 1, cz_, "library_counter", facing)
        self.put((cx0 + cx1) // 2, 1, cz_ - DIRS[facing][1], "wooden_chair", facing)
        cat_x, (cat_z0, cat_z1), cat_facing = desk["catalog"]
        for z in range(cat_z0, cat_z1):
            self.put(cat_x, 1, z, "card_catalog", cat_facing)

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


# --- the templates: (furnisher, frame) -> the book's (x, z) or None ---------------------

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
    for v_front in (4, 8):
        for u in range(2, fr.W - 2):
            f.stack(fr, u, v_front, "metal_shelf", "front", 2)
            f.stack(fr, u, v_front + 1, "metal_shelf", "back", 2)
    f.back_wall(fr, 2, fr.W - 3, "metal_shelf", height=2)
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
    """Furnishes the level; returns {room id: (x, z) of the cabinet its book lies on}."""
    p = lv.p
    f = Furnisher(lv)
    books = {}
    for s, kind in lv.rooms:
        template = p.FURNISH.get(s.id)
        if template is None:
            continue
        if template not in TEMPLATES:
            raise SystemExit(f"LEVEL_3 FURNISH: no template {template!r} (for {s.id}); there are {', '.join(sorted(TEMPLATES))}")
        anchor = f.room(s, template)
        if s.id in p.BOOK_SPOTS:
            x, z = p.BOOK_SPOTS[s.id]
            f.items.pop((x, 1, z), None)
            f.put(x, 1, z, "filing_cabinet", "north", force=True)
            anchor = (x, z)
        if kind == "book":
            if anchor is None:
                raise SystemExit(f"LEVEL_3: room {s.id} holds a book but its template left no cabinet for it")
            books[s.id] = anchor
    f.reserved = set()
    f.library()
    for x, z in p.CORRIDOR_PLANTS:
        f.put(x, 1, z, "potted_plant", "north")
    for x, z, facing in p.CORRIDOR_CLOCKS:
        f.put(x, 3, z, "wall_clock", facing)
    f.finish()
    return books
