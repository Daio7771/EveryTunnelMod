"""
Pieces of the recordings that go into the mod, and how they are cut: the CC0 ones (cc0/)
and the few under a license that needs credit (licensed/, see licensed/SOURCES.md and
CREDITS.md at the repository root).

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

HERE = Path(__file__).resolve().parent


@dataclass
class Cut:
    id: str
    source: str                                     # file name inside `folder`
    use: str
    channels: str                                   # "mono" or "stereo" (stereo only for non-positional sounds)
    segments: Optional[List[Tuple[float, float]]] = None
    auto: Optional[Callable] = None                 # (samples, count) -> [(start, end)] in seconds
    count: int = 1
    fade_in: float = 0.05
    fade_out: float = 0.5
    peak_db: float = -3.0
    notes: str = field(default="")
    # In-game balance, written to sounds.json like catalog.Sound's: base volume and the
    # distance in blocks at which a positioned sound fades out (None = Minecraft's 16).
    game_volume: float = 1.0
    attenuation: Optional[int] = None
    folder: str = "cc0"                             # "cc0" or "licensed" (credited in CREDITS.md)


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
    x, sr = audio_io.read(HERE / cut.folder / cut.source)
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
    # Three of the recording's calmer breaths; 7.3-11.3 s (louder, rasping) is not used.
    Cut("entity.pale_man.breathing", "breathing_creepy.wav",
        "The Pale man breathing; heard within 8 blocks, and he holds his breath while he listens",
        "mono", segments=[(2.8, 6.6), (11.8, 15.4), (16.9, 20.6)], fade_in=0.15, fade_out=0.6, attenuation=8),
    # Four single gulps (the fifth, 6.3 s, is louder and different); the game plays one about
    # every half second while the player drinks a water bottle.
    Cut("player.drink", "drinking_gulps.mp3", "One gulp while drinking a water bottle", "mono",
        segments=[(0.05, 0.6), (1.5, 2.0), (3.15, 3.65), (4.75, 5.3)], fade_in=0.02, fade_out=0.12, game_volume=0.8),
    # CC BY 4.0, credited in CREDITS.md. The intro waits 25 s and then distorts and fades it
    # over 3 s, so only the first 30 s are needed.
    Cut("music.elevator", "local_forecast_elevator.mp3",
        "The song in the elevator at the start of a new game (Kevin MacLeod, \"Local Forecast - Elevator\")",
        "stereo", segments=[(0.0, 30.0)], fade_in=0.0, fade_out=1.0, folder="licensed"),
]
