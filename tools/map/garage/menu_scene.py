"""
The main menu's scene: one parking hall, built by the level's own code (garage.build) with
MENU_SCENE's values in map_generator.py, with a closed emergency door under a lit EXIT sign
at the far end of the aisle. The client draws it behind the menu (client.menu.MenuScene)
from the fixed camera MENU_CAMERA, with its own light, and flickers its lamps.

It is not part of the world. write() puts it into the mod (assets/every_tunnel/ in
src/main/resources):
  menu/scene.json              the camera, a palette of block states and the blocks
  models/block/menu/*.json     the models of the re-skinned stand-in blocks it uses
  textures/block/menu/*.png    their textures

Why the models: the world's resource pack re-skins vanilla blocks (gray concrete becomes the
garage floor, a sign tile shows a text) only in worlds that carry it, and the menu has no
world. So each palette entry of a re-skinned block names the model the pack would pick for
it (and its y rotation), copied from the pack under every_tunnel:block/menu/, so nothing
outside the menu changes. Where the pack has several models for a block, the choice is made
here, per position, so the scene always looks the same.

scene.json:
  camera   {"pos": [x, y, z], "yaw": .., "pitch": .., "fov": .., "vanish_x": ..} (yaw and
           pitch in Minecraft degrees; vanish_x = the share of the screen width where the
           aisle's far end is drawn)
  palette  [{"state": "<block state>", "model": "<model id>", "y": <rotation>}, ...]
           ("model" and "y" only for re-skinned blocks)
  blocks   [[x, y, z, palette index], ...]
"""

import io
import json
import random
import shutil
import zipfile
from types import SimpleNamespace

from garage import build as B
from garage import mod_blocks
from garage import textures as T
from garage.glyphs import Glyphs

MENU_NS = "every_tunnel"
MENU_FOLDER = "block/menu"


def scene_params(p):
    """The level's parameters with MENU_SCENE over them, and the mod's blocks for each role."""
    hp = SimpleNamespace(**dict(vars(p), **p.MENU_SCENE))
    hp.STAND_INS = {**p.STAND_INS, **p.BLOCKS, **p.MOD_BLOCKS}
    return hp


def build_hall(hp, glyphs):
    """The hall, finished like a mod level; returns (world, garage)."""
    S = hp.STAND_INS
    cz = B.Garage(hp).center_z
    L = hp.LENGTH
    door = {"side": "east", "a": cz, "b": cz, "height": 2}
    w, g, _, _ = B.build(hp, "mod", glyphs, openings=[door])
    w.set(L - 1, 1, cz, f"{S['emergency_door']}[facing=east,half=lower,hinge=left,open=false,powered=false]")
    w.set(L - 1, 2, cz, f"{S['emergency_door']}[facing=east,half=upper,hinge=left,open=false,powered=false]")
    w.remove(L - 2, 3, cz)
    w.set(L - 2, 3, cz, "every_tunnel:exit_sign[facing=west]")
    mod_blocks.finish(w, hp, random.Random(f"{hp.SEED}-mod-blocks"))
    return w, g


def write(p, assets):
    """Builds the scene and writes it into the mod's assets folder; returns a summary line."""
    hp = scene_params(p)
    glyphs = Glyphs(hp.STAND_INS["glyph"])
    w, g = build_hall(hp, glyphs)
    pack = zipfile.ZipFile(io.BytesIO(T.build_pack(hp, glyphs)[0]))
    in_pack = set(pack.namelist())

    def override(state, pos):
        """The pack's model for this block state at pos ({"model", "y"}), or None if not re-skinned."""
        namespace, name = mod_blocks.block_id(state).split(":")
        path = f"assets/{namespace}/blockstates/{name}.json"
        if path not in in_pack:
            return None
        blockstate = json.loads(pack.read(path))
        if "variants" not in blockstate:
            raise SystemExit(f"menu scene: {state} is re-skinned by a multipart blockstate, which the menu does not draw")
        props = mod_blocks.properties(state)
        for key, options in blockstate["variants"].items():
            wanted = dict(kv.split("=") for kv in key.split(",")) if key else {}
            if all(props.get(k) == v for k, v in wanted.items()):
                options = options if isinstance(options, list) else [options]
                pick = random.Random(f"menu-{pos}").choice(options)
                if pick.get("x") or pick.get("uvlock"):
                    raise SystemExit(f"menu scene: {state}'s model has an x rotation or uvlock, which the menu does not draw")
                return pick
        raise SystemExit(f"menu scene: no variant of the pack's {name} blockstate fits {state}")

    models, textures = {}, set()

    def copy_model(model_id):
        """The model under every_tunnel:block/menu/ (copied with its pack parents and textures)."""
        namespace, path = model_id.split(":")
        if namespace != T.NS:
            return model_id
        new_id = f"{MENU_NS}:{MENU_FOLDER}/{path.split('/')[-1]}"
        if new_id in models:
            return new_id
        data = json.loads(pack.read(f"assets/{namespace}/models/{path}.json"))
        models[new_id] = data
        if data.get("parent", "").startswith(T.NS + ":"):
            data["parent"] = copy_model(data["parent"])
        for key, texture in data.get("textures", {}).items():
            if texture.startswith(T.NS + ":"):
                name = texture.split("/")[-1]
                textures.add(name)
                data["textures"][key] = f"{MENU_NS}:{MENU_FOLDER}/{name}"
        return new_id

    palette, index, blocks = [], {}, []
    for pos in sorted(w.blocks):
        state = w.blocks[pos]
        entry = {"state": state}
        pick = override(state, pos)
        if pick:
            entry["model"] = copy_model(pick["model"])
            if pick.get("y"):
                entry["y"] = pick["y"]
        key = json.dumps(entry, sort_keys=True)
        if key not in index:
            index[key] = len(palette)
            palette.append(entry)
        blocks.append([pos[0], pos[1], pos[2], index[key]])

    c = p.MENU_CAMERA
    aisle_middle = (g.aisle_z0 + g.aisle_z1 + 1) / 2
    camera = {"pos": [c["x"], 1 + c["eye"], aisle_middle], "yaw": c["yaw"], "pitch": c["pitch"],
              "fov": c["fov"], "vanish_x": c["vanish_x"]}

    model_dir = assets / "models" / MENU_FOLDER
    texture_dir = assets / "textures" / MENU_FOLDER
    for folder in (model_dir, texture_dir):
        if folder.exists():
            shutil.rmtree(folder)
        folder.mkdir(parents=True)
    for model_id, data in models.items():
        (model_dir / f"{model_id.split('/')[-1]}.json").write_text(json.dumps(data, indent=1) + "\n")
    for name in sorted(textures):
        source = f"assets/{T.NS}/textures/block/{name}.png"
        (texture_dir / f"{name}.png").write_bytes(pack.read(source))
        if f"{source}.mcmeta" in in_pack:
            (texture_dir / f"{name}.png.mcmeta").write_bytes(pack.read(f"{source}.mcmeta"))
    scene = assets / "menu" / "scene.json"
    scene.parent.mkdir(parents=True, exist_ok=True)
    scene.write_text(json.dumps({"camera": camera, "palette": palette, "blocks": blocks}, separators=(",", ":")) + "\n")

    lamps = [s for s in w.blocks.values() if s.startswith("every_tunnel:fluorescent_light")]
    dead = sum(1 for s in lamps if "lit=false" in s)
    return (f"Menu scene: {hp.LENGTH} x {hp.WIDTH} hall, {len(blocks)} blocks, {len(palette)} block states, "
            f"{len(models)} models, {len(textures)} textures; lamps {len(lamps) - dead} lit, {dead} dead")
