"""
Level 4's furniture, notes and loot, room by room, in plan coordinates (see level.py). Everything in
order and with air round it, but for three things that do not fit (DETAILS below, in the lists).
RESERVED are gaps left for what a puzzle brings with it; nothing may go there.

A piece's facing is the way it looks, or the way someone sitting on it looks, in the plan. The joined
states (rows, table tiles, stacks) are worked out here the same way the game does, as in
offices/furnish.py, since `/setblock` takes a state as given.
"""

from offices.furnish import ROW, STACK, TILE, WALK_THROUGH

TILES = TILE | {"pool_table", "lounge_desk"}
RACKS = {"record_rack", "reel_rack"}
THROUGH = WALK_THROUGH | {"cinema_seat", "bar_stool", "served_glass", "diploma", "office_chair", "cinema_screen",
                         "throw_line", "dart_plate_left", "dart_plate_right", "wall_sheet"}
WORLD_FACING = {"north": "west", "west": "south", "south": "east", "east": "north"}
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
LOOT_COLOUR = {"battery": (235, 205, 70), "water": (90, 215, 235), "food_plate": (225, 80, 70),
               "food_popcorn": (225, 80, 70), "food_snack": (225, 80, 70)}


def row(name, x0, x1, z, facing):
    return [(name, x, z, facing) for x in range(x0, x1 + 1)]


def column(name, x, z0, z1, facing):
    return [(name, x, z, facing) for z in range(z0, z1 + 1)]


def table(x, z, chairs=("north", "south"), name="reading_table"):
    """A table with its chairs, each looking at it. What holds a note or loot is a lounge_desk, a
    full block high: on a lower table they would float, as they lie in the block above."""
    return [(name, x, z, "north")] + [
        ("wooden_chair", x - DIRS[c][0], z - DIRS[c][1], c) for c in chairs]


PLANTS = [(42, 51), (57, 51), (42, 58), (57, 58),                     # Reception
          (37, 29), (37, 44), (63, 44),                               # the Bar (the record player's corner stays clear)
          (45, 12), (54, 12), (45, 27), (54, 27),                     # the Hall
          (85, 30), (70, 43), (85, 43),                               # the Spa
          (68, 13), (83, 13), (83, 26),                               # the Game room
          (91, 30), (102, 30), (91, 43), (102, 43)]                   # the Calm room

FURNITURE = (
    # Reception: the desk, a sofa to wait on.
    column("library_counter", 44, 53, 56, "east") + row("sofa", 53, 55, 58, "north") + [("armchair", 57, 55, "west")]
    # The Bar: the counter with the stools, the shelf behind it, tables, sofas.
    + row("library_counter", 39, 45, 32, "south") + row("library_counter", 39, 45, 30, "south")
    + [("bar_stool", x, 33, "north") for x in (40, 42, 44)]
    + table(42, 41) + table(60, 33, ("east", "west"))
    # DETAILS: this table's second chair stands well away from it, turned to the wall.
    + [("reading_table", 56, 40, "north"), ("wooden_chair", 56, 41, "north"), ("wooden_chair", 59, 43, "west")]
    + row("sofa", 39, 42, 44, "north") + row("sofa", 53, 57, 29, "south")
    # The Cinema: rows of seats looking at the screen's wall, either side of the aisle; the booth; the kiosk.
    + [("cinema_seat", x, z, "west") for x in (19, 22) for z in (31, 32, 33, 34, 35)]
    + [("cinema_seat", x, z, "west") for x in (19, 22, 25) for z in (39, 40, 41, 42, 43)]
    + [("lounge_desk", 29, 30, "north"), ("wooden_chair", 29, 31, "north")]
    + row("library_counter", 27, 30, 42, "north")
    # The Spa: its desk, the loungers, a nightstand for the water.
    + column("library_counter", 71, 31, 33, "east") + [("lounger", x, 43, "north") for x in (73, 76, 79, 82)]
    + [("nightstand", 84, 31, "south")]
    # The Suites: a bed each (level.py), a nightstand by each, a desk in two of them.
    + [("nightstand", 20, 13, "south"), ("nightstand", 28, 13, "south"), ("nightstand", 20, 26, "north"), ("nightstand", 28, 26, "north")]
    + table(17, 25, ("west",), "lounge_desk") + table(30, 14, ("west",), "lounge_desk") + [("lounge_desk", 30, 15, "north")]
    # The Game room: the pool table, tables for cards, the machine, a sofa.
    + [("pool_table", x, z, "north") for x in range(72, 76) for z in (18, 19)]
    + table(71, 14, ("east",), "lounge_desk") + table(76, 25, ("east", "west")) + row("sofa", 69, 72, 26, "north")
    + [("vending_machine", 69, 14, "south")]
    # The darts' throwing line, a guide 5 blocks from the board, and the little table with the tray.
    + column("throw_line", 78, 18, 22, "west") + [("nightstand", 78, 23, "west")]
    # The Hall: somewhere to sit.
    + column("sofa", 45, 23, 26, "east") + column("sofa", 54, 23, 26, "west")
    # The Security room: the desk, cabinets (the monitors' place stays clear).
    + row("office_desk", 41, 45, 5, "south") + [("office_chair", 43, 6, "north"), ("filing_cabinet", 40, 0, "south"), ("filing_cabinet", 41, 0, "south")]
    # The Calm room: bookshelves, tables, armchairs.
    + [("bookshelf", x, 30, "south") for x in range(93, 101)] + [("bookshelf", x, 43, "north") for x in range(93, 101)]
    + [("lounge_desk", x, z, "north") for x in (96, 97) for z in (36, 37)]
    + table(100, 33, ("east", "west")) + table(100, 40, ("east", "west"))
    + [("armchair", 93, 34, "east"), ("armchair", 93, 40, "east")]
    + [("potted_plant", x, z, "north") for x, z in PLANTS]
)

# Stacked on the ground floor's pieces: (name, x, z, facing, y).
# DETAILS: a glass already served at the end of the bar, with nobody there.
STACKED = [("served_glass", 45, 32, "north", 2), ("vending_machine", 69, 14, "south", 2), ("dart_tray", 78, 23, "west", 2)] + [
    ("bookshelf", x, z, f, 2) for z, f in ((30, "south"), (43, "north")) for x in range(93, 101)]

# (note id, x, z): a sheet on the furniture there.
NOTES = [
    ("ch1_l4_note_reception", 44, 54),
    ("ch1_l4_note_bar_menu", 41, 32),
    ("ch1_l4_note_bartender", 43, 32),
    ("ch1_l4_note_projection", 29, 30),
    ("ch1_l4_note_clients", 71, 31),
    ("ch1_l4_note_guard_12", 20, 13),
    ("ch1_l4_note_guard_13", 17, 25),
    ("ch1_l4_note_calm", 96, 36),
]

# (note id, x, z, y): a sheet taped to the wall behind it, looking west.
WALL_NOTES = [("ch1_l4_note_score_sheet", 83, 17, 3)]

# (tag, x, z, y): loot on the furniture there (y 2) or the floor (y 1).
LOOT = [
    ("battery", 44, 53, 2), ("water", 44, 56, 2),                                                  # Reception
    ("water", 40, 32, 2), ("water", 42, 32, 2), ("water", 44, 32, 2),
    ("food_plate", 41, 30, 2), ("food_plate", 43, 30, 2),                                          # the Bar
    ("food_popcorn", 27, 42, 2), ("food_popcorn", 28, 42, 2), ("water", 30, 42, 2),                # the Cinema's kiosk
    ("water", 71, 33, 2), ("water", 84, 31, 2),                                                    # the Spa
    ("food_plate", 30, 14, 2), ("food_snack", 30, 15, 2), ("water", 28, 13, 2),
    ("battery", 28, 26, 2), ("battery", 20, 26, 2),                                                # the Suites
    ("food_snack", 71, 14, 2), ("battery", 73, 20, 1), ("battery", 74, 20, 1),                     # the Game room, by the pool table
    ("battery", 42, 5, 2), ("battery", 44, 5, 2),                                                  # the Security room's desk
]

# Places that the puzzles and the closing fill: (x0, z0, x1, z1), kept clear. The final key goes on
# the Calm room's central table, beside its card.
RESERVED = {
    "record player": (59, 29, 64, 32),
    "projector": (24, 31, 27, 34),
    "cinema screen": (15, 32, 17, 42),
    "pool": (74, 33, 83, 41),
    "dartboard": (79, 16, 84, 24),
    "monitors": (52, 1, 59, 4),
}

# Level 4's puzzles: (name, x, z, facing, y, what it holds). The records stand out of the menu's order,
# and so do the reels out of their running time; the reels are on a shelf on the booth's north wall, at
# eye height. Read by LoungePuzzleHooks by their ids.
PUZZLE_RACKS = [("record_rack", 59, 29, "south", 1, 3), ("record_rack", 60, 29, "south", 1, 2),
                ("record_rack", 62, 29, "south", 1, 0), ("record_rack", 63, 29, "south", 1, 1),
                ("reel_rack", 25, 30, "south", 2, 2), ("reel_rack", 26, 30, "south", 2, 0),
                ("reel_rack", 27, 30, "south", 2, 3), ("reel_rack", 28, 30, "south", 2, 1)]
PUZZLE_DEVICES = [("record_player", 61, 29, "south"), ("projector", 25, 32, "west")]
PIECE_SPOTS = [("piece_bar", 61, 30), ("piece_cinema", 26, 32)]
# The Game room's far wall: the board, 3 x 3 (col 0 on the left as seen from the room, at the lower z;
# row 0 at the bottom), the hatch under its middle, the plate to its right.
BOARD = [(83, 19 + col, 2 + row, col, row) for col in range(3) for row in range(3)]
DART_THINGS = [("dart_hatch", 83, 20, 1), ("dart_plate_left", 83, 22, 3), ("dart_plate_right", 83, 23, 3)]
DART_SPOTS = [("dartboard", 83, 20, 3), ("dart_tray", 78, 23, 2), ("dart_hatch", 83, 20, 1)]
SCREEN = [(15, z, y) for z in range(33, 42) for y in range(1, 5)]

DIPLOMAS = [(49, z, "east") for z in range(62, 79, 4)] + [(52, z, "west") for z in range(62, 79, 4)]


class Furnish:
    RESERVED = RESERVED

    def __init__(self, lv, air):
        self.lv, self.air = lv, air
        self.items = {}        # (x, y, z) -> (name, plan facing)
        self.holds = {}        # (x, y, z) -> what a rack holds (its index)
        self.board = {}        # (x, y, z) -> the part of the dartboard (col, row)

    def put(self, name, x, z, facing, y=1, in_gap=False):
        floor = self.lv.floors.get((x, z), 0)
        y += floor
        if (x, y, z) not in self.air or (x, y, z) in self.items:
            raise SystemExit(f"Level 4: {name} at {(x, y, z)}: the cell is not free")
        for what, (x0, z0, x1, z1) in RESERVED.items():
            if x0 <= x < x1 and z0 <= z < z1 and name != "diploma" and not in_gap:
                raise SystemExit(f"Level 4: {name} at {(x, z)} is in the gap for the {what}")
        self.items[(x, y, z)] = (name, facing)
        if y == floor + 1 and name not in THROUGH:
            self.lv.blocked.add((x, z))

    def lay(self):
        for name, x, z, facing in FURNITURE:
            self.put(name, x, z, facing)
        for name, x, z, facing, y in STACKED:
            self.put(name, x, z, facing, y)
        for name, x, z, facing, y, index in PUZZLE_RACKS:
            self.put(name, x, z, facing, y, in_gap=True)
            self.holds[(x, self.lv.floors[(x, z)] + y, z)] = index
        for name, x, z, facing in PUZZLE_DEVICES:
            self.put(name, x, z, facing, in_gap=True)
        for x, z, y in SCREEN:
            self.put("cinema_screen", x, z, "east", y, in_gap=True)
        for x, z, y, col, row in BOARD:
            self.put("dartboard", x, z, "west", y, in_gap=True)
            self.board[(x, self.lv.floors[(x, z)] + y, z)] = (col, row)
        for name, x, z, y in DART_THINGS:
            self.put(name, x, z, "west", y, in_gap=True)
        for _, x, z, y in WALL_NOTES:
            self.put("wall_sheet", x, z, "west", y, in_gap=True)
        for _, x, z in NOTES:
            self.on_top("note_paper", x, z)
        for x, z, facing in DIPLOMAS:
            self.put("diploma", x, z, facing, 2)
        self.check_loot()
        for pos, state in self.states().items():
            x, y, z = pos
            target = self.lv.corridor_blocks if self.items[pos][0] == "diploma" else self.lv.lounge_blocks
            target[self.lv.at(x, y, z)] = state
        return self

    def top_of(self, x, z):
        """The first free y over a piece of furniture at the cell (the cell must have one)."""
        ys = [y for (a, y, b) in self.items if (a, b) == (x, z)]
        if not ys:
            raise SystemExit(f"Level 4: nothing at {(x, z)} to put something on")
        return max(ys) + 1

    def on_top(self, name, x, z):
        y = self.top_of(x, z)
        self.items[(x, y, z)] = (name, "north")

    def check_loot(self):
        for tag, x, z, y in LOOT:
            floor = self.lv.floors[(x, z)]
            if y == 2 and (x, floor + 1, z) not in self.items:
                raise SystemExit(f"Level 4: {tag} at {(x, z)} has no furniture under it")
            if (x, floor + y, z) in self.items:
                raise SystemExit(f"Level 4: {tag} at {(x, z)}: the cell is taken")
            if (x, z) in self.lv.blocked and y == 1:
                raise SystemExit(f"Level 4: {tag} at {(x, z)}: the cell is taken")

    def states(self):
        items = {}
        for (x, y, z), (name, facing) in self.items.items():
            wx, wy, wz = self.lv.at(x, y, z)
            items[(wx, wy, wz)] = (name, WORLD_FACING[facing], (x, y, z))

        def same(pos, name, facing):
            other = items.get(pos)
            return other is not None and other[0] == name and (facing is None or other[1] == facing)

        out = {}
        for (x, y, z), (name, facing, plan) in items.items():
            bid = f"every_tunnel:{name}"
            if name in ROW:
                lx, lz = DIRS[{"north": "east", "east": "south", "south": "west", "west": "north"}[facing]]
                left, right = same((x + lx, y, z + lz), name, facing), same((x - lx, y, z - lz), name, facing)
                state = f"{bid}[facing={facing},left={str(left).lower()},right={str(right).lower()}]"
            elif name == "dartboard":
                col, row = self.board[plan]
                state = f"{bid}[col={col},facing={facing},hint=0,row={row}]"
            elif name in RACKS:
                state = f"{bid}[facing={facing},has=true,hint=false,index={self.holds[plan]}]"
            elif name in TILES:
                sides = {s: same((x + d[0], y, z + d[1]), name, None) for s, d in DIRS.items()}
                state = bid + "[" + ",".join(f"{s}={str(sides[s]).lower()}" for s in ("east", "north", "south", "west")) + "]"
            elif name in STACK:
                up, down = same((x, y + 1, z), name, facing), same((x, y - 1, z), name, facing)
                state = f"{bid}[down={str(down).lower()},facing={facing},up={str(up).lower()}]"
            else:
                state = f"{bid}[facing={facing}]"
            out[plan] = state
        return out

    def spawn_points(self):
        """(id, world position, tag) of the notes and the loot."""
        out = []
        for nid, x, z in NOTES:
            y = max(y for (a, y, b), it in self.items.items() if (a, b) == (x, z) and it[0] == "note_paper")
            out.append((nid, self.centre(x, y, z), "note"))
        for nid, x, z, y in WALL_NOTES:
            out.append((nid, self.centre(x, self.lv.floors[(x, z)] + y, z), "note"))
        for sid, x, z, y in DART_SPOTS:
            out.append((sid, self.centre(x, y, z), "puzzle"))
        for k, (tag, x, z, y) in enumerate(LOOT):
            out.append((f"ch1_l4_loot_{k + 1}", self.centre(x, self.lv.floors[(x, z)] + y, z), tag))
        # The ids say which: record_rack_<index>, reel_rack_<index>, record_player, projector, piece_bar, piece_cinema.
        for name, x, z, _, y, index in PUZZLE_RACKS:
            out.append((f"{name}_{index}", self.centre(x, y, z), "puzzle"))
        for name, x, z, _ in PUZZLE_DEVICES:
            out.append((name, self.centre(x, 1, z), "puzzle"))
        for sid, x, z in PIECE_SPOTS:
            out.append((sid, self.centre(x, 1, z), "puzzle"))
        # The screen's corners, for the film.
        (x0, z0, y0), (x1, z1, y1) = SCREEN[0], SCREEN[-1]
        out.append(("film_screen_a", self.centre(x0, y0, z0), "puzzle"))
        out.append(("film_screen_b", self.centre(x1, y1, z1), "puzzle"))
        px, pz = self.lv.p["POOL"]["piece"]
        out.append(("ch1_l4_piece_spa", self.centre(px, 1 - self.lv.p["POOL"]["depth"], pz), "master_card_sw"))
        return out

    def centre(self, x, y, z):
        wx, wy, wz = self.lv.at(x, y, z)
        return wx + 0.5, wy, wz + 0.5

    def plan_cells(self):
        """For the preview: (x, z) -> what stands there, without the notes on top."""
        return {(x, z): it[0] for (x, y, z), it in self.items.items() if it[0] not in ("note_paper", "diploma", "dart_tray", "dart_plate_left", "dart_plate_right")}

    def dots(self):
        """For the preview: ((x, z), colour) of the notes and the loot."""
        return ([((x, z), (255, 255, 255)) for _, x, z in NOTES] + [((x, z), (255, 255, 255)) for _, x, z, _ in WALL_NOTES]
                + [((x, z), LOOT_COLOUR[tag]) for tag, x, z, _ in LOOT])
