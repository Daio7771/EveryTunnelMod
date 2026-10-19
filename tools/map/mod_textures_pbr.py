"""
LabPBR maps (_n, _s) for the mod's own block textures, written next to them in
src/main/resources/assets/every_tunnel/textures/block/ (the maps are committed with the mod).

Run after changing one of those textures:   python3 tools/map/mod_textures_pbr.py

Same materials and glow values as the generated level (garage/pbr.py, PBR in
map_generator.py), so the mod's fluorescent light and the level's fixtures match.
"""

import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

import map_generator as G                                        # noqa: E402
from garage import pbr                                            # noqa: E402
from garage.textures import PAINTED_METAL, PLASTIC               # noqa: E402

BLOCK_TEXTURES = G.REPO / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "block"


def lit_tubes(x, y, c):
    if pbr.luminance(c) > 0.95:                       # the tubes
        return PLASTIC.but(smoothness=0.6, relief=0.0, emission=G.PBR["TUBE_GLOW"])
    if pbr.luminance(c) > 0.8:                        # the diffuser
        return PLASTIC.but(relief=0.0, emission=G.PBR["DIFFUSER_GLOW"])
    return PAINTED_METAL


# texture name -> (material or per-pixel rule, tiles)
MATERIALS = {
    "fluorescent_light_housing": (PAINTED_METAL, True),
    "fluorescent_light_tubes_on": (lit_tubes, True),
    "fluorescent_light_tubes_off": (PLASTIC.but(smoothness=0.6, relief=0.3), True),
}


def main():
    for name, (material, tiles) in MATERIALS.items():
        img = Image.open(BLOCK_TEXTURES / f"{name}.png")
        normal, specular = pbr.maps(img, material, G.PBR["RELIEF"], tiles=tiles)
        normal.save(BLOCK_TEXTURES / f"{name}_n.png")
        specular.save(BLOCK_TEXTURES / f"{name}_s.png")
        print(f"{name}: _n, _s")
    missing = sorted(p.stem for p in BLOCK_TEXTURES.glob("*.png")
                     if not p.stem.endswith(("_n", "_s")) and p.stem not in MATERIALS)
    if missing:
        print("WARNING: block textures without a material here: " + ", ".join(missing))


if __name__ == "__main__":
    main()
