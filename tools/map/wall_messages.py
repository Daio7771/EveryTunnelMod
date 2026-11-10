"""
The messages painted on the walls (platform/WallMessageBlock.kt), and how a generator places one: a
wall_message block in the air in front of the wall, with its data. Their text is in the lang file, under the
key `wall.every_tunnel.<id>`, and a line break there makes two lines.

Fields of a message: size (a line's height in blocks), rotation (degrees off level), trigger (fixed,
on_approach or on_look_away_change), once, changed (the id it turns into), patch (a pale patch behind it, for
a dark wall: the paint stays black).
"""

MESSAGES = {
    "never_paid": dict(size=0.42, rotation=-2.0, trigger="on_approach"),
    "face_to_work": dict(size=0.5, rotation=1.5, trigger="fixed"),
    "silence": dict(size=0.5, rotation=-1.0, trigger="fixed"),
    "water_warm": dict(size=0.42, rotation=2.0, trigger="on_approach"),
    "dont_stay": dict(size=0.8, rotation=-1.5, trigger="on_look_away_change", changed="stay"),
    "dont_look": dict(size=0.5, rotation=-1.0, trigger="fixed"),
}


def state(message_id, facing):
    """The block's full state and data, for /setblock."""
    m = MESSAGES[message_id]
    changed = f'wall.every_tunnel.{m["changed"]}' if "changed" in m else ""
    return (f'every_tunnel:wall_message[facing={facing}]{{Text:"wall.every_tunnel.{message_id}",Changed:"{changed}",'
            f'Size:{m["size"]}f,Rotation:{m["rotation"]}f,Trigger:"{m["trigger"]}",Once:{"1b" if m.get("once", True) else "0b"},'
            f'Patch:{"1b" if m.get("patch") else "0b"}}}')
