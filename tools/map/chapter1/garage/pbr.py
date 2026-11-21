"""
LabPBR 1.3 material maps for the 16x16 pixel-art textures, read by Iris shader packs
(tuned against Complementary Reimagined). Each texture `name.png` gets two maps next to it:

  name_n.png   R, G  surface normal X, Y (DirectX convention: +Y points down the texture)
               B     ambient occlusion (255 = none)
               A     height (255 = top surface, lower = deeper; never 0, some POM breaks on it)
  name_s.png   R     perceptual smoothness (255 = mirror)
               G     F0 of a dielectric (0-229, linear), or a metal id (230 iron, 232 aluminium,
                     233 chrome, 255 = the albedo is the reflectance)
               B     porosity 0-64 (how much rain soaks in and darkens it; SSS is never used)
               A     emission 1-254 (the albedo is the emitted colour); 0 = none (255 is "ignored"
                     too, but 0 is unambiguous)

There is no hand-painted height: a pixel's height comes from its brightness against the
surface around it (dark pores, joints and cracks sit lower), scaled by the material's
`relief`. That matches how the albedo painters draw: shadows are always darker paint.
"""

import math
from dataclasses import dataclass, replace

from PIL import Image


@dataclass(frozen=True)
class Material:
    smoothness: float              # perceptual, 0 = rough concrete .. 1 = still water
    f0: float = 0.04               # reflectance of a dielectric: ~0.04 concrete, paint, plastic; 0.02 water
    metal: int | None = None       # LabPBR metal id, used instead of f0 (see the module header)
    porosity: float = 0.0          # 0..1
    relief: float = 1.0            # brightness-to-height strength; 0 = perfectly flat
    emission: float = 0.0          # 0..1 of full emission

    def but(self, **changes):
        return replace(self, **changes)


FLAT_NORMAL = (128, 128, 255, 255)
NO_SPECULAR = (0, 0, 0, 0)
HEIGHT_RANGE = 96          # alpha span of the height channel: the deepest cavity is 255 - 96 (~9% deep)
SLOPE_RANGE = 4.0          # brightness difference (0..1) that maps to a full -1..1 height step


def luminance(rgb):
    r, g, b = rgb[:3]
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255


def distance(a, b):
    return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))


def encode_specular(m):
    green = m.metal if m.metal is not None else max(0, min(229, round(m.f0 * 255)))
    emission = 0 if m.emission <= 0 else max(1, min(254, round(m.emission * 254)))
    return (round(max(0.0, min(1.0, m.smoothness)) * 255), green,
            round(max(0.0, min(1.0, m.porosity)) * 64), emission)


def maps(img, material, relief_scale=1.0, reference=None, tiles=True):
    """Returns (normal map, specular map) for an RGBA texture.

    material:   a Material for the whole texture, or a function (x, y, rgba) -> Material
                that picks one per pixel (paint on concrete, tubes in a frame...).
    reference:  the luminance of "the surface" that heights are measured from; by default
                the texture's own average. Decals pass the surface they lie on, so a crack
                decal (all dark pixels) still reads as deeper than the floor around it.
    tiles:      full block faces wrap around at the edges when taking slopes; cut-out
                textures (decals, panels, mesh) do not.
    """
    img = img.convert("RGBA")
    w, h = img.size
    pixels = [[img.getpixel((x, y)) for x in range(w)] for y in range(h)]
    pick = material if callable(material) else (lambda x, y, c: material)
    mats = [[pick(x, y, pixels[y][x]) if pixels[y][x][3] else None for x in range(w)] for y in range(h)]

    opaque = [luminance(c) for row in pixels for c in row if c[3]]
    if not opaque:
        return Image.new("RGBA", (w, h), FLAT_NORMAL), Image.new("RGBA", (w, h), NO_SPECULAR)
    ref = sum(opaque) / len(opaque) if reference is None else reference

    # Height -1 (deep) .. +1 (raised) per pixel; transparent pixels take no part.
    height = [[None] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            m = mats[y][x]
            if m is not None:
                d = (luminance(pixels[y][x]) - ref) * SLOPE_RANGE * m.relief * relief_scale
                height[y][x] = max(-1.0, min(1.0, d))

    def at(x, y, fallback):
        if tiles:
            x, y = x % w, y % h
        elif not (0 <= x < w and 0 <= y < h):
            return fallback
        v = height[y][x]
        return fallback if v is None else v

    normal = Image.new("RGBA", (w, h), FLAT_NORMAL)
    specular = Image.new("RGBA", (w, h), NO_SPECULAR)
    for y in range(h):
        for x in range(w):
            hv = height[y][x]
            if hv is None:
                continue
            # The normal tilts away from higher neighbours (image Y grows downwards, as LabPBR wants).
            nx = -(at(x + 1, y, hv) - at(x - 1, y, hv)) * 0.5
            ny = -(at(x, y + 1, hv) - at(x, y - 1, hv)) * 0.5
            length = math.sqrt(nx * nx + ny * ny + 1.0)
            nx, ny = nx / length, ny / length
            ao = 1.0 - 0.35 * max(0.0, -hv)                 # cavities hold a little shadow
            alpha = 255 - round((1.0 - (hv + 1.0) / 2.0) * HEIGHT_RANGE)
            normal.putpixel((x, y), (round((nx + 1) / 2 * 255), round((ny + 1) / 2 * 255),
                                     round(ao * 255), max(1, alpha)))
            specular.putpixel((x, y), encode_specular(mats[y][x]))
    return normal, specular
