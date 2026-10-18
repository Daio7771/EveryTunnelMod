"""
Pieces of the CC0 recordings (cc0/) that go into the mod, and how they are cut.

Each Cut turns one source recording into one sound event with one or more variations:
segments are chosen (by hand as (start, end) seconds, or automatically), resampled to
44.1 kHz, optionally mixed to mono (needed for sounds positioned in the world), faded
and normalized. export.py writes them next to the generated sounds.
"""

from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, List, Optional, Tuple

import numpy as np
from scipy.signal import resample_poly

import audio_io
from dsp import SR, fade, normalize

CC0 = Path(__file__).resolve().parent / "cc0"


@dataclass
class Cut:
    id: str
    source: str
    use: str
    channels: str                                   # "mono" or "stereo" (stereo only for non-positional sounds)
    segments: Optional[List[Tuple[float, float]]] = None
    auto: Optional[Callable] = None                 # (samples, count) -> [(start, end)] in seconds
    count: int = 1
    fade_in: float = 0.05
    fade_out: float = 0.5
    peak_db: float = -3.0
    notes: str = field(default="")


def loudest_windows(length_seconds):
    """Automatic segments: the most intense windows of the given length, not overlapping."""
    def pick(x, count):
        mono = x if x.ndim == 1 else x.mean(axis=1)
        hop = SR // 10
        energy = np.sqrt(np.convolve(mono ** 2, np.ones(hop) / hop, mode="valid")[::hop])
        width = int(length_seconds * 10)
        scores = np.convolve(energy, np.ones(width), mode="valid")
        chosen = []
        for i in np.argsort(scores)[::-1]:
            if all(abs(i - j) >= width for j in chosen):
                chosen.append(i)
            if len(chosen) == count:
                break
        return [(i / 10.0, i / 10.0 + length_seconds) for i in sorted(chosen)]
    return pick


def load(cut):
    """Source recording at 44.1 kHz in the cut's channel layout."""
    x, sr = audio_io.read(CC0 / cut.source)
    if sr != SR:
        g = np.gcd(SR, sr)
        x = resample_poly(x, SR // g, sr // g, axis=0)
    if cut.channels == "mono" and x.ndim == 2:
        x = x.mean(axis=1)
    if cut.channels == "stereo" and x.ndim == 1:
        x = np.stack([x, x], axis=1)
    return x


def render(cut):
    """The cut's variations as normalized sample arrays."""
    x = load(cut)
    segments = cut.segments if cut.segments is not None else cut.auto(x, cut.count)
    out = []
    for start, end in segments:
        piece = x[int(start * SR):int(end * SR)].copy()
        out.append(normalize(fade(piece, cut.fade_in, cut.fade_out), cut.peak_db))
    return out, segments


CUTS = [
    Cut("player.breathing_exhausted", "breathing_heavy_exhausted.wav",
        "The player out of stamina (plays in the player's head, not positioned)",
        "stereo", auto=loudest_windows(8.0), count=2, fade_in=0.05, fade_out=2.5),
]
