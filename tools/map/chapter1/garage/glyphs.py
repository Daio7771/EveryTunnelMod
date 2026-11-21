"""
Sign texts carried by the glyph stand-in block (chiseled bookshelf), or in the mod
version by every_tunnel:sign_tile (same facings and 64 slots, numbered).

The block has 4 facings x 64 slot combinations. Wall signs need the facing to point the
text into the room, so a wall text takes a whole slot (all four facings show it, rotated).
Pillar codes show the same text on all four sides, so each pillar text needs only one
(facing, slot) pair. Wall slots are handed out from 0 upwards, pillar slots from 63
downwards; together they hold up to 256 texts.
"""

FACINGS = ["north", "east", "south", "west"]


class Glyphs:
    def __init__(self, block):
        self.block = block
        self.wall = {}      # slot -> text
        self.pillar = {}    # (facing, slot) -> text
        self.pieces = {}    # (facing, slot) -> key of one tile of big wall lettering (see piece_state)
        self._next_pillar = 0

    def _state(self, facing, slot):
        if self.block.startswith("every_tunnel:"):           # the mod's sign tile numbers its slots
            return f"{self.block}[facing={facing},slot={slot}]"
        bits = ",".join(f"slot_{b}_occupied={'true' if slot >> b & 1 else 'false'}" for b in range(6))
        return f"{self.block}[facing={facing},{bits}]"

    def wall_state(self, text, facing):
        for slot, t in self.wall.items():
            if t == text:
                return self._state(facing, slot)
        slot = len(self.wall)
        if any(s == slot for _, s in list(self.pillar) + list(self.pieces)):
            raise SystemExit("too many different sign texts")
        self.wall[slot] = text
        return self._state(facing, slot)

    def piece_state(self, text, cols, rows, col, row):
        """One tile of `text` painted big over cols x rows wall blocks (row 0 at the top). Shares
        the pillar codes' pool of (facing, slot) states: a tile shows its picture on every side,
        so the facing it gets does not matter on a wall."""
        key = (text, cols, rows, col, row)
        for at, k in self.pieces.items():
            if k == key:
                return self._state(*at)
        n = self._next_pillar
        self._next_pillar += 1
        facing, slot = FACINGS[n % 4], 63 - n // 4
        if slot in self.wall:
            raise SystemExit("too many different sign texts")
        self.pieces[(facing, slot)] = key
        return self._state(facing, slot)

    def pillar_state(self, text):
        for (facing, slot), t in self.pillar.items():
            if t == text:
                return self._state(facing, slot)
        n = self._next_pillar
        self._next_pillar += 1
        facing, slot = FACINGS[n % 4], 63 - n // 4
        if slot in self.wall:
            raise SystemExit("too many different sign texts")
        self.pillar[(facing, slot)] = text
        return self._state(facing, slot)
