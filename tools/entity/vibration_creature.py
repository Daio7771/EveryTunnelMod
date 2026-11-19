"""
Chapter 2 Level 2's vibration creature: installs the Stone Maw model for GeckoLib and writes its animations.

Run after changing anything here or in the source (from the repository root):   python3 tools/entity/vibration_creature.py

The model is the user's Stone Maw (tools/entity/source/stone_maw/: geo, 256 x 256 texture, its idle, walk and attack
animations, the editable Blockbench file and its LEEME.md). It is the source: change it in Blockbench and export it there,
never edit what this tool writes. This tool writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/vibration_creature.geo.json                 the source geo, renamed geometry.vibration_creature
  animations/entity/vibration_creature.animation.json    idle and walk from the source; orient, stalk, charge, strike,
                                                         recover, listen, dying and crushed written here on its bones
  textures/entity/vibration_creature.png                 the source texture
  and the entity's name in Chapter 2's lang part (tools/lang/src/chapter2); no spawn egg: it is never summoned
and tools/entity/output/vibration_creature_preview.png (git-ignored): front and side views of the key poses.

The model: 3.5 blocks tall (56 units), facing north (-z, the teeth in front), bones root > pelvis > body > head > jaw, arms
(arm > forearm > hand > fingers 1-4 and thumb) on the body, legs (leg > shin > foot) on the pelvis. Rotations are degrees
[x, y, z] as in the source: a negative x swings a hanging limb forwards (the attack raises the arms at -83), a positive x
leans an upright part forwards and opens the jaw. The strike's slam lands at 0.9 s, the brain's windup (level.json
creature.windup_seconds); the orient's jaw snaps shut at 0.95 s, the end of its 1 s.
"""

import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lang"))
import langfiles  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
SOURCE = Path(__file__).resolve().parent / "source" / "stone_maw"
OUT = Path(__file__).resolve().parent / "output"
NAME = "vibration_creature"


# =====================================================================================
# Animations

def keys(*frames):
    """[(time, [x, y, z]), ...] -> Bedrock keyframes."""
    return {f"{t:.4g}": [round(c, 2) for c in v] for t, v in frames}


def swing(length, amplitude, phase=0.0, offset=(0, 0, 0), axis=0, steps=8, cycles=1):
    """A sine swing over one loop (cycles times), sampled into keyframes."""
    frames = []
    for i in range(steps + 1):
        v = list(offset)
        v[axis] += amplitude * math.sin(2 * math.pi * cycles * i / steps + phase)
        frames.append((length * i / steps, v))
    return keys(*frames)


def still(v):
    return keys((0, v))


def legs(length, amplitude, bend, crouch=0.0):
    """Both legs in a stride: thighs swinging round crouch, shins bent by bend and folding as the leg comes forward."""
    return {
        "left_leg": {"rotation": swing(length, amplitude, offset=(-crouch, 0, 0))},
        "right_leg": {"rotation": swing(length, amplitude, math.pi, offset=(-crouch, 0, 0))},
        "left_shin": {"rotation": swing(length, amplitude * 0.6, -math.pi / 2, offset=(bend, 0, 0))},
        "right_shin": {"rotation": swing(length, amplitude * 0.6, math.pi / 2, offset=(bend, 0, 0))},
    }


def our_animations():
    n = NAME
    A = {}
    A[f"animation.{n}.orient"] = {"loop": False, "animation_length": 1.0, "bones": {
        # The 1 s warning: the blind head sweeps to one side, then the other, the jaw opening; at 0.95 s the teeth snap shut.
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-4, -25, 0]), (0.6, [-4, 20, 0]), (0.85, [-6, 0, 0]), (1.0, [0, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.5, [10, 0, 0]), (0.88, [22, 0, 0]), (0.95, [0, 0, 0]), (1.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.85, [5, 0, 0]), (1.0, [3, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.85, [-6, 0, -4]), (1.0, [-4, 0, -3]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.85, [-6, 0, 4]), (1.0, [-4, 0, 3]))},
    }}
    stalk = 1.6
    A[f"animation.{n}.stalk"] = {"loop": True, "animation_length": stalk, "bones": {
        # Creeping after a soft step: bent low, the head forward, the long arms reaching ahead, slow careful strides.
        "pelvis": {"position": swing(stalk / 2, 0.3, offset=(0, -1.6, 0), axis=1)},
        "body": {"rotation": swing(stalk, 2, axis=2, offset=(20, 0, 0))},
        "head": {"rotation": swing(stalk, 3, axis=1, offset=(-14, 0, 0))},
        "left_arm": {"rotation": swing(stalk, 8, math.pi, offset=(-22, 0, -3))},
        "right_arm": {"rotation": swing(stalk, 8, offset=(-22, 0, 3))},
        "left_forearm": {"rotation": still([-10, 0, 0])},
        "right_forearm": {"rotation": still([-10, 0, 0])},
        **legs(stalk, 12, 22, crouch=14),
    }}
    charge = 0.7
    A[f"animation.{n}.charge"] = {"loop": True, "animation_length": charge, "bones": {
        # Running at a point: leaned far forwards, jaw open, long strides, the arms swinging wide.
        "pelvis": {"position": swing(charge / 2, 0.8, offset=(0, -1.0, 0), axis=1)},
        "body": {"rotation": still([25, 0, 0])},
        "head": {"rotation": still([-18, 0, 0])},
        "jaw": {"rotation": swing(charge, 3, offset=(14, 0, 0))},
        "left_arm": {"rotation": swing(charge, 40, math.pi, offset=(-25, 0, -6))},
        "right_arm": {"rotation": swing(charge, 40, offset=(-25, 0, 6))},
        "left_forearm": {"rotation": still([-20, 0, 0])},
        "right_forearm": {"rotation": still([-20, 0, 0])},
        **legs(charge, 30, 18, crouch=8),
    }}
    A[f"animation.{n}.strike"] = {"loop": False, "animation_length": 1.3, "bones": {
        # The 0.9 s tell: both fists rise over the head, the body rearing back, the jaw wide; at 0.9-1.0 s the slam; then it
        # settles with its fists on the floor.
        "pelvis": {"position": keys((0, [0, 0, 0]), (0.9, [0, 0.5, 0]), (1.0, [0, -2.0, 0]), (1.3, [0, -1.5, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.45, [-6, 0, 0]), (0.9, [-12, 0, 0]), (1.0, [22, 0, 0]), (1.3, [16, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.9, [8, 0, 0]), (1.0, [-16, 0, 0]), (1.3, [-10, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [20, 0, 0]), (0.9, [26, 0, 0]), (1.0, [8, 0, 0]), (1.3, [4, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-110, 0, -8]), (0.9, [-150, 0, -6]), (1.0, [-35, 0, 0]), (1.3, [-30, 0, 0]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-110, 0, 8]), (0.9, [-150, 0, 6]), (1.0, [-35, 0, 0]), (1.3, [-30, 0, 0]))},
        "left_forearm": {"rotation": keys((0, [0, 0, 0]), (0.9, [-30, 0, 0]), (1.0, [0, 0, 0]))},
        "right_forearm": {"rotation": keys((0, [0, 0, 0]), (0.9, [-30, 0, 0]), (1.0, [0, 0, 0]))},
        "left_leg": {"rotation": keys((0, [0, 0, 0]), (0.9, [4, 0, 0]), (1.0, [-20, 0, 0]), (1.3, [-15, 0, 0]))},
        "right_leg": {"rotation": keys((0, [0, 0, 0]), (0.9, [4, 0, 0]), (1.0, [-20, 0, 0]), (1.3, [-15, 0, 0]))},
        "left_shin": {"rotation": keys((0, [0, 0, 0]), (1.0, [30, 0, 0]), (1.3, [25, 0, 0]))},
        "right_shin": {"rotation": keys((0, [0, 0, 0]), (1.0, [30, 0, 0]), (1.3, [25, 0, 0]))},
    }}
    A[f"animation.{n}.recover"] = {"loop": True, "animation_length": 2.0, "bones": {
        # After the blow: slumped over its fists on the floor, the shoulders rising and falling slowly.
        "pelvis": {"position": still([0, -1.5, 0])},
        "body": {"rotation": swing(2.0, 2, offset=(18, 0, 0))},
        "head": {"rotation": still([-8, 0, 0])},
        "left_arm": {"rotation": swing(2.0, 2, offset=(-28, 0, 0))},
        "right_arm": {"rotation": swing(2.0, 2, offset=(-28, 0, 0))},
        "left_leg": {"rotation": still([-15, 0, 0])},
        "right_leg": {"rotation": still([-15, 0, 0])},
        "left_shin": {"rotation": still([25, 0, 0])},
        "right_shin": {"rotation": still([25, 0, 0])},
    }}
    A[f"animation.{n}.listen"] = {"loop": True, "animation_length": 2.0, "bones": {
        # Listening to the floor: still, head down and turned to one side, the jaw trembling.
        "body": {"rotation": still([8, 0, 0])},
        "head": {"rotation": keys((0, [10, 18, 10]), (1.0, [12, 22, 12]), (2.0, [10, 18, 10]))},
        "jaw": {"rotation": swing(2.0, 1.5, offset=(5, 0, 0), steps=16, cycles=8)},
        "left_arm": {"rotation": still([-6, 0, -2])},
        "right_arm": {"rotation": still([-6, 0, 2])},
    }}
    A[f"animation.{n}.dying"] = {"loop": "hold_on_last_frame", "animation_length": 2.6, "bones": {
        # Phase 6.1, Stone Maw's last stand: after the last hit it sinks to the floor without lying down: the knees buckle (it squats low,
        # thighs forward, shins folded back), the body leans over its long arms until they hang to the floor, and the head drops till it
        # looks straight down at the stone. Held at the end.
        "pelvis": {"position": keys((0, [0, 0, 0]), (0.5, [0, 0, 0]), (1.2, [0, 0, 0]), (2.0, [0, -3.3, 0]), (2.6, [0, -3.2, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.6, [6, 0, 0]), (1.4, [24, 0, 0]), (2.2, [40, 0, 0]), (2.6, [42, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.8, [8, 6, 0]), (1.6, [32, 2, 0]), (2.2, [52, 0, 0]), (2.6, [56, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (1.0, [10, 0, 0]), (2.6, [4, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [-14, 0, -3]), (2.0, [-62, 0, -4]), (2.6, [-70, 0, -4]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [-14, 0, 3]), (2.0, [-62, 0, 4]), (2.6, [-70, 0, 4]))},
        "left_forearm": {"rotation": keys((0, [0, 0, 0]), (2.0, [-12, 0, 0]), (2.6, [-14, 0, 0]))},
        "right_forearm": {"rotation": keys((0, [0, 0, 0]), (2.0, [-12, 0, 0]), (2.6, [-14, 0, 0]))},
        "left_leg": {"rotation": keys((0, [0, 0, 0]), (0.8, [-14, 0, 0]), (1.6, [-52, 0, 0]), (2.2, [-72, 0, 0]), (2.6, [-74, 0, 0]))},
        "right_leg": {"rotation": keys((0, [0, 0, 0]), (0.8, [-14, 0, 0]), (1.6, [-52, 0, 0]), (2.2, [-72, 0, 0]), (2.6, [-74, 0, 0]))},
        "left_shin": {"rotation": keys((0, [0, 0, 0]), (0.8, [20, 0, 0]), (1.6, [70, 0, 0]), (2.2, [104, 0, 0]), (2.6, [106, 0, 0]))},
        "right_shin": {"rotation": keys((0, [0, 0, 0]), (0.8, [20, 0, 0]), (1.6, [70, 0, 0]), (2.2, [104, 0, 0]), (2.6, [106, 0, 0]))},
    }}
    A[f"animation.{n}.crushed"] = {"loop": "hold_on_last_frame", "animation_length": 1.4, "bones": {
        # The ceiling comes down on it: it flinches, both arms thrown over its head, the body folding lower, then they drop limp.
        "pelvis": {"position": keys((0, [0, -3.2, 0]), (0.25, [0, -3.2, 0]), (1.4, [0, 1.6, 0]))},
        "body": {"rotation": keys((0, [42, 0, 0]), (0.2, [34, 0, 0]), (0.5, [48, 0, 0]), (1.4, [50, 0, 0]))},
        "head": {"rotation": keys((0, [56, 0, 0]), (0.2, [40, 0, 0]), (0.5, [62, 0, 0]), (1.4, [64, 0, 0]))},
        "jaw": {"rotation": keys((0, [4, 0, 0]), (0.2, [24, 0, 0]), (0.5, [6, 0, 0]))},
        "left_arm": {"rotation": keys((0, [-70, 0, -4]), (0.2, [-150, 0, -10]), (0.6, [-120, 0, -8]), (1.4, [-74, 0, -4]))},
        "right_arm": {"rotation": keys((0, [-70, 0, 4]), (0.2, [-150, 0, 10]), (0.6, [-120, 0, 8]), (1.4, [-74, 0, 4]))},
        "left_forearm": {"rotation": keys((0, [-14, 0, 0]), (0.2, [-50, 0, 0]), (1.4, [-10, 0, 0]))},
        "right_forearm": {"rotation": keys((0, [-14, 0, 0]), (0.2, [-50, 0, 0]), (1.4, [-10, 0, 0]))},
        "left_leg": {"rotation": still([-74, 0, 0])},
        "right_leg": {"rotation": still([-74, 0, 0])},
        "left_shin": {"rotation": still([106, 0, 0])},
        "right_shin": {"rotation": still([106, 0, 0])},
    }}
    return A


def animations(source):
    """The source's idle and walk, renamed, and ours."""
    A = {}
    for name in ("idle", "walk"):
        A[f"animation.{NAME}.{name}"] = source["animations"][f"animation.stone_maw.{name}"]
    A.update(our_animations())
    return {"format_version": "1.8.0", "animations": A, "geckolib_format_version": 2}


# =====================================================================================
# Preview

def sample(channel, t):
    """A bone channel ({time: [x, y, z]}) at time t, linear between keyframes, held at the ends."""
    frames = sorted((float(k), v) for k, v in channel.items())
    if t <= frames[0][0]:
        return frames[0][1]
    for (t0, v0), (t1, v1) in zip(frames, frames[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return frames[-1][1]


def rotate(p, pivot, rot):
    """Rotates point p around pivot by Bedrock-style degrees (x, then y, then z)."""
    x, y, z = (p[i] - pivot[i] for i in range(3))
    rx, ry, rz = (math.radians(a) for a in rot)
    y, z = y * math.cos(rx) + z * math.sin(rx), -y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    # GeckoLib turns a bone about z the other way from a plain right-handed rotation of the Blockbench model (it mirrors x when it bakes
    # the model, and turns z by the animation's value as it is): a positive z swings the arm at +x (the "left" one) towards the body.
    # x and y match Blockbench's. Checked against GeckoLib 4.8.4's BakedModelFactory and RenderUtils.rotateMatrixAroundBone.
    x, y = x * math.cos(rz) + y * math.sin(rz), -x * math.sin(rz) + y * math.cos(rz)
    return [x + pivot[0], y + pivot[1], z + pivot[2]]


def posed_cubes(geo, anim, t):
    """Every cube's 8 corners in model space with the animation at time t applied."""
    bones = {b["name"]: b for b in geo["bones"]}
    bones_anim = anim["bones"] if anim else {}

    def pose(name):
        a = bones_anim.get(name, {})
        rot = sample(a["rotation"], t) if "rotation" in a else [0, 0, 0]
        pos = sample(a["position"], t) if "position" in a else [0, 0, 0]
        return rot, pos

    out = []
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            pts = [[o[0] + dx * s[0], o[1] + dy * s[1], o[2] + dz * s[2]] for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
            if c.get("rotation"):
                pts = [rotate(q, c.get("pivot", [0, 0, 0]), c["rotation"]) for q in pts]
            bone = b
            while bone:
                rot, pos = pose(bone["name"])
                pts = [[v + d for v, d in zip(rotate(q, bone["pivot"], rot), pos)] for q in pts]
                bone = bones.get(bone.get("parent"))
            out.append(pts)
    return out


# The 6 faces of a cube by corner index (dx * 4 + dy * 2 + dz).
FACES = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]


def draw_view(d, cubes, ox, oy, view, scale):
    """Shaded faces, far ones first (front: looking at -z from the north; side: from the east, front to the right)."""
    def project(q):
        return (q[0], q[1], -q[2]) if view == "front" else (-q[2], q[1], -q[0])

    polys = []
    for pts in cubes:
        p = [project(q) for q in pts]
        for f in FACES:
            a, b, c = (p[i] for i in f[:3])
            u = [b[i] - a[i] for i in range(3)]
            v = [c[i] - a[i] for i in range(3)]
            nz = u[0] * v[1] - u[1] * v[0]
            n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], nz]
            ln = math.sqrt(sum(x * x for x in n)) or 1.0
            light = 0.35 + 0.65 * max(0.0, (0.4 * n[0] + 0.6 * n[1] - 0.7 * n[2]) / ln)
            depth = sum(p[i][2] for i in f) / 4
            polys.append((depth, [(ox + p[i][0] * scale, oy - p[i][1] * scale) for i in f], int(70 + 120 * light)))
    for depth, poly, g in sorted(polys, key=lambda x: -x[0]):
        d.polygon(poly, fill=(g, g, g - 4), outline=(40, 40, 44))


def write_preview(geo, anims):
    poses = [("idle", "idle", 0.0), ("orient: click", "orient", 0.94), ("stalk", "stalk", 0.4),
             ("charge", "charge", 0.2), ("strike: windup top", "strike", 0.9), ("strike: impact", "strike", 1.0),
             ("recover", "recover", 0.0), ("listen", "listen", 0.5),
             ("dying: sinking", "dying", 1.4), ("dying: held", "dying", 2.6), ("crushed: arms up", "crushed", 0.2), ("crushed: held", "crushed", 1.4)]
    scale, cell_w, cell_h = 4, 300, 300
    sheet = Image.new("RGB", (cell_w * len(poses), cell_h * 2 + 40), (24, 25, 28))
    d = ImageDraw.Draw(sheet)
    for i, (label, name, t) in enumerate(poses):
        cubes = posed_cubes(geo, anims[f"animation.{NAME}.{name}"], t)
        for row, view in enumerate(("front", "side")):
            ox, oy = cell_w * i + cell_w // 2, 30 + cell_h * row + 260
            d.line((ox - 130, oy, ox + 130, oy), fill=(90, 90, 90))
            d.line((ox - 130, oy - 56 * scale, ox + 130, oy - 56 * scale), fill=(50, 70, 50))     # 3.5 blocks up
            draw_view(d, cubes, ox, oy, view, scale)
        d.text((cell_w * i + 8, 8), label, fill=(210, 210, 210))
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / f"{NAME}_preview.png")


# =====================================================================================

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    geo_file = json.loads((SOURCE / "geo" / "stone_maw.geo.json").read_text(encoding="utf-8"))
    source_anims = json.loads((SOURCE / "animations" / "stone_maw.animation.json").read_text(encoding="utf-8"))
    texture = Image.open(SOURCE / "textures" / "stone_maw.png")
    geo = geo_file["minecraft:geometry"][0]
    geo["description"]["identifier"] = f"geometry.{NAME}"
    anims = animations(source_anims)

    write_json(ASSETS / "geo" / "entity" / f"{NAME}.geo.json", geo_file)
    write_json(ASSETS / "animations" / "entity" / f"{NAME}.animation.json", anims)
    (ASSETS / "textures" / "entity").mkdir(parents=True, exist_ok=True)
    texture.save(ASSETS / "textures" / "entity" / f"{NAME}.png")
    write_preview(geo, anims["animations"])
    langfiles.update("chapter2", "en_us", {"entity.every_tunnel.vibration_creature": "Vibration Creature"})
    langfiles.update("chapter2", "es_es", {"entity.every_tunnel.vibration_creature": "Criatura de vibraciones"})
    cubes = sum(len(b.get("cubes", [])) for b in geo["bones"])
    print(f"Vibration creature (Stone Maw): {len(geo['bones'])} bones, {cubes} cubes, {len(anims['animations'])} animations "
          f"({', '.join(a.rsplit('.', 1)[1] for a in anims['animations'])})")


if __name__ == "__main__":
    sys.exit(main())
