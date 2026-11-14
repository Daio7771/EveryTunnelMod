"""
Chapter 2's menu theme: a slow waltz in D minor for an out-of-tune upright piano and a solo
violin, on a worn old recording. About 75 s. The notes are written here; the piano is the CC0
"Old Piano FB" SoundFont (FluidSynth, sampler.Part) and the violin the CC0 Solo Violin of VSCO 2 CE
(sampler.SamplePlayer); the old-recording finish (wow and flutter, band limit, saturation, hiss
and crackle) is synthesized here. Sources: tools/sounds/soundfonts/SOURCES.md; fetch them with
tools/sounds/soundfonts/fetch.py. No audio generator is involved.

It follows the shape of the old cartoon-era menu theme the user pointed to (violin in a minor
key, a phrase of about 20 s said twice) but not its notes: the melody and harmony are ours. Three
parts, A - B - A':

- A (bars 1-6, 20 s): 3/4 at 54 bpm over Dm | Dm | Gm | A7 | Dm | A7. The piano carries the melody
  in octaves over the waltz (bass on beat 1, chord on beats 2 and 3, a hair late, the pedal
  changed every bar) and rolls the last chord into an arpeggio; the violin only doubles it, very
  softly. It sighs low (A4 and down) and ends on the dominant, asking.
- B (bars 7-14, 27 s): the violin goes and the piano is alone, softer, the waltz loosening (the
  tempo slows into the middle and catches up, chords dragged and rolled, a beat left empty), over
  a path that never lands where it points:
    A7 asks, and the answer is B flat major (the sixth degree), not D minor;
    Gm, and the melody leans on A flat, a semitone under what the chord wants, before G;
    F minor (a chord that D minor does not have) with the melody on G, its ninth;
    E flat major over a D in the bass, the melody on B flat and an A (a tritone against the E flat);
    a soft D minor that sighs the opening gesture again;
    G MAJOR, with a B natural where B flat has been all along (the Dorian lift);
    A7sus4: the D hangs over the A, and falls to C sharp, the leading note;
    A7 rolled upwards (C sharp, E, G) into the return.
- A' (bars 15-20): the violin comes back in front, the line an octave up and louder, and the last
  bar goes to B flat major instead of home, the melody holding D. It does not resolve.

Rubato: the tempo breathes over each phrase, and there is a short pause before B and before A'.

It is not a loop: the game (client.effects.MenuMusic) waits in silence before playing it again.
"""

from pathlib import Path

import numpy as np

import sampler
from menu_piano import SR, convolve_channels, fft_filter, finalize

BPM = 54.0
BEAT = 60.0 / BPM

SOUNDFONTS = Path(__file__).resolve().parent / "soundfonts"


# ===========================================================================
# The piece

# Chords: bass MIDI and the three notes of the chord above it (the left hand stays under the melody)
DM = (38, [50, 53, 57])         # D2 | D3 F3 A3
GM = (43, [50, 55, 58])         # G2 | D3 G3 Bb3
A7 = (45, [49, 52, 55])         # A2 | C#3 E3 G3
BB = (46, [50, 53, 58])         # Bb2 | D3 F3 Bb3


def waltz(chord):
    """A bar of the waltz: the bass on beat 1 is the piece's own, the chord comes on beats 2 and 3."""
    bass, notes = chord
    return bass, [(1.0, notes, 44, 0.0), (2.0, notes, 38, 0.0)]


# Each bar: (bass MIDI, [(beat, notes, velocity, seconds between the notes of the roll)])
BARS_A = [waltz(DM), waltz(DM), waltz(GM), waltz(A7), waltz(DM), waltz(A7)]
BARS_B = [
    (46, [(1.0, [50, 53, 57], 36, 0.05), (2.0, [50, 53, 57], 30, 0.05)]),    # B flat major 7 (D F A over B flat)
    (43, [(1.0, [50, 55, 58], 36, 0.05), (2.0, [50, 55, 58], 30, 0.05)]),    # G minor
    (41, [(1.0, [48, 53, 56], 38, 0.06)]),                                   # F minor, and no chord on beat 3
    (38, [(0.0, [51, 55, 58], 46, 0.07)]),                                   # E flat major over the D: struck and let ring
    (38, [(1.0, [50, 53, 57], 34, 0.05)]),                                   # D minor, soft, one chord
    (43, [(1.0, [50, 55, 59], 40, 0.05), (2.0, [50, 55, 59], 34, 0.05)]),    # G major: the B natural
    (45, [(1.0, [50, 52, 55], 38, 0.04), (2.0, [49, 52, 55], 40, 0.04)]),    # A7sus4, then A7
    (45, []),                                                                # A7 rolled (ARPEGGIO)
]
BARS_A2 = [waltz(DM), waltz(DM), waltz(GM), waltz(A7), waltz(DM), waltz(BB)]
BARS = BARS_A + BARS_B + BARS_A2

B_START = len(BARS_A)                   # first bar of B
A2_START = B_START + len(BARS_B)        # first bar of A'

# A bar's last chord rolled one note every half beat from beat 1, instead of struck: bar -> notes
ARPEGGIO = {5: [49, 52, 55, 61, 64, 67],                 # A7 rising
            13: [49, 52, 55, 61, 64, 67],                # A7 rising again, into the return
            19: [50, 53, 58, 62, 65, 70]}                # B flat rising and fading: the end

# (bar, beat in the bar 0..3, held beats, MIDI, velocity 0..1)
MELODY_A = [
    (0, 0.0, 2.0, 69, 0.40), (0, 2.0, 1.0, 70, 0.38),                          # A4 Bb4
    (1, 0.0, 1.5, 69, 0.40), (1, 1.5, 0.5, 67, 0.36), (1, 2.0, 1.0, 65, 0.38),  # A4 G4 F4
    (2, 0.0, 2.0, 70, 0.42), (2, 2.0, 1.0, 69, 0.40),                          # Bb4 A4
    (3, 0.0, 1.0, 67, 0.40), (3, 1.0, 1.0, 69, 0.42), (3, 2.0, 1.0, 73, 0.46),  # G4 A4 C#5 (rising)
    (4, 0.0, 2.0, 74, 0.48), (4, 2.0, 1.0, 76, 0.46),                          # D5 E5
    (5, 0.0, 2.0, 77, 0.48), (5, 2.0, 1.0, 76, 0.40),                          # F5 E5: it waits on the dominant
]
# B: the piano alone. The last element says if the piano doubles the note an octave up
MELODY_B = [
    (6, 0.0, 2.0, 74, 0.30, False), (6, 2.0, 1.0, 72, 0.27, False),                                      # D5 C5 over B flat
    (7, 0.0, 1.5, 70, 0.30, False), (7, 1.5, 0.5, 69, 0.27, False), (7, 2.0, 1.0, 68, 0.30, False),      # Bb4 A4 Ab4: leans
    (8, 0.0, 2.0, 67, 0.32, False), (8, 2.0, 1.0, 65, 0.28, False),                                      # G4 (the Ab's answer) F4
    (9, 0.0, 2.0, 70, 0.40, True), (9, 2.0, 1.0, 69, 0.38, True),                                        # Bb4 A4 over E flat / D
    (10, 0.0, 1.5, 69, 0.32, False), (10, 1.5, 0.5, 67, 0.28, False), (10, 2.0, 1.0, 65, 0.30, False),   # A4 G4 F4: the opening again
    (11, 0.0, 2.0, 71, 0.42, True), (11, 2.0, 1.0, 69, 0.38, True),                                      # B4 (natural) A4 over G major
    (12, 0.0, 2.0, 74, 0.38, False), (12, 2.0, 1.0, 73, 0.38, False),                                    # D5 hanging, C#5
    (13, 0.0, 1.0, 73, 0.38, False), (13, 1.0, 1.0, 76, 0.42, False), (13, 2.0, 1.0, 79, 0.46, False),   # C#5 E5 G5 up to the return
]
MELODY_A2 = [
    (14, 0.0, 2.0, 81, 0.55), (14, 2.0, 1.0, 82, 0.52),                         # A5 Bb5
    (15, 0.0, 1.5, 81, 0.55), (15, 1.5, 0.5, 79, 0.50), (15, 2.0, 1.0, 77, 0.52),  # A5 G5 F5
    (16, 0.0, 2.0, 82, 0.58), (16, 2.0, 1.0, 81, 0.55),                         # Bb5 A5
    (17, 0.0, 1.0, 79, 0.55), (17, 1.0, 1.0, 81, 0.58), (17, 2.0, 1.0, 85, 0.62),  # G5 A5 C#6
    (18, 0.0, 2.0, 86, 0.64), (18, 2.0, 1.0, 88, 0.60),                         # D6 E6
    (19, 0.0, 3.0, 86, 0.60),                                                   # D6 held over B flat
]

PHRASE_BARS = 6
VIOLIN_BEHIND = (0.55, 0.85)    # how much of the written velocity the violin plays in A and in A'
PAUSES = {B_START: 0.5, A2_START: 0.55}   # a breath before B and before A'
RUBATO_S = 0.10                 # the tempo leans by this much over a phrase
LOOSE_S = 0.5                   # B slows by this much into its middle and catches up


def at(bar, beat):
    """Seconds of a beat in a bar: the tempo breathes, B is looser, and there is a pause before B and A'."""
    b = bar * 3.0 + beat
    t = b * BEAT + RUBATO_S * np.sin(2.0 * np.pi * b / (PHRASE_BARS * 3.0))
    b0, b1 = B_START * 3.0, A2_START * 3.0
    if b0 <= b <= b1:
        t += LOOSE_S * np.sin(np.pi * (b - b0) / (b1 - b0)) ** 2
    return t + sum(pause for start, pause in PAUSES.items() if bar >= start)


def violin_part(rng):
    """The violin plays in A and A' only: B is the piano alone."""
    violin = sampler.SamplePlayer(SOUNDFONTS / "solo_violin")
    for bar, beat, held, midi, vel in MELODY_A + MELODY_A2:
        start = at(bar, beat) + rng.normal(0.0, 0.022)
        length = at(bar, beat + held) - at(bar, beat) - 0.04
        violin.note(max(0.0, start), length, midi, vel * VIOLIN_BEHIND[bar >= A2_START] + rng.normal(0.0, 0.02), release=0.45)
    return violin


def piano_part(rng):
    piano = sampler.Part(SOUNDFONTS / "old_piano_fb.sf2")
    for bar, (bass, hits) in enumerate(BARS):
        in_b = B_START <= bar < A2_START
        louder = 6 if bar >= A2_START else -3 if in_b else 0
        down = at(bar, 0.0)
        up = at(bar + 1, 0.0) - 0.06
        piano.control(down, 64, 127)                       # sustain pedal, changed every bar
        piano.control(up, 64, 0)
        struck_with_bass = bool(hits) and hits[0][0] == 0.0
        piano.note(down + rng.normal(0.0, 0.012), (up if struck_with_bass else at(bar, 2.0)) - down, bass,
                   58 + louder + rng.integers(-3, 4))
        if bar in ARPEGGIO:                                # the last chord of a part, rolled
            notes = ARPEGGIO[bar]
            for i, midi in enumerate(notes):
                fade = 1.0 - 0.5 * i / len(notes) if bar == len(BARS) - 1 else 0.8 + 0.4 * i / len(notes)
                piano.note(at(bar, 1.0 + 0.5 * i) + rng.normal(0.0, 0.01), 2.5, midi, (40 + louder) * fade + rng.integers(-3, 4))
            continue
        for k, (beat, notes, vel, roll) in enumerate(hits):
            start = at(bar, beat)
            end = at(bar, hits[k + 1][0]) if k + 1 < len(hits) else up
            for i, midi in enumerate(notes):
                piano.note(start + 0.03 + roll * i + rng.normal(0.0, 0.012), end - start, midi,
                           vel + louder + rng.integers(-4, 5))
    # The melody: in octaves (the written note and the one above it, softer) where it says so
    for bar, beat, held, midi, vel, *octave in ([e + (True,) for e in MELODY_A] + MELODY_B + [e + (True,) for e in MELODY_A2]):
        start = at(bar, beat) + rng.normal(0.0, 0.018)
        length = at(bar, beat + held) - at(bar, beat) - 0.03
        v = 56.0 + 60.0 * vel + rng.normal(0.0, 2.0)
        piano.note(max(0.0, start), length, midi, v)
        if octave[0]:
            piano.note(max(0.0, start + 0.012), length, midi + 12, v * 0.62)
    piano.control(at(len(BARS), 0.0) + 2.0, 64, 0)
    return piano


# ===========================================================================
# The worn recording

def band(stereo, lowcut=None, highcut=None):
    """menu_piano's FFT filter on each channel."""
    return np.stack([fft_filter(stereo[:, c], lowcut=lowcut, highcut=highcut) for c in range(stereo.shape[1])], axis=1)


def room(bus, rng, seconds=1.8):
    """A small dark room: only the wet part, a noise response falling 60 dB in `seconds`."""
    n_ir = int(seconds * SR)
    t = np.arange(n_ir) / SR
    irs = []
    for _ in range(2):
        ir = fft_filter(rng.standard_normal(n_ir) * np.exp(-6.91 * t / seconds), lowcut=200.0, highcut=3500.0)
        ir[:int(0.012 * SR)] = 0.0
        irs.append(ir / (np.sqrt(np.sum(ir ** 2)) + 1e-12))
    return convolve_channels(bus, irs)


def wow_and_flutter(stereo, rng):
    """The disc's speed wandering: a slow wow, a faster one and a flutter, together about 0.3% at most."""
    n = len(stereo)
    t = np.arange(n) / SR
    speed = (0.0022 * np.sin(2.0 * np.pi * 0.55 * t + rng.uniform(0, 6.28))
             + 0.0010 * np.sin(2.0 * np.pi * 1.9 * t + rng.uniform(0, 6.28))
             + 0.0005 * np.sin(2.0 * np.pi * 7.3 * t + rng.uniform(0, 6.28)))
    position = np.arange(n) + np.cumsum(speed)
    return np.stack([np.interp(position, np.arange(n), stereo[:, c]) for c in range(2)], axis=1)


def surface(stereo, rng):
    """Hiss, crackle and the odd pop on top of the music: a worn shellac disc."""
    n = len(stereo)
    rms = np.sqrt(np.mean(stereo ** 2))
    out = stereo + band(rng.standard_normal((n, 2)), lowcut=600.0, highcut=5500.0) * rms * 0.06
    seconds = n / SR
    for _ in range(rng.poisson(9.0 * seconds)):                      # crackle: little clicks
        i = int(rng.integers(0, n - 8))
        out[i:i + 4] += rng.choice([-1.0, 1.0]) * rng.exponential(0.07) * rms * np.array([[1.0], [-0.6], [0.4], [-0.2]])
    for _ in range(rng.poisson(0.22 * seconds)):                     # pops: a click and a short thump
        i = int(rng.integers(0, n - 2000))
        thump = np.exp(-np.arange(1800) / 260.0) * np.sin(2.0 * np.pi * 85.0 * np.arange(1800) / SR)
        out[i:i + 1800] += (rng.choice([-1.0, 1.0]) * rms * 0.9 * thump)[:, None]
        out[i:i + 3] += rng.choice([-1.0, 1.0]) * rms * 2.5 * np.array([[1.0], [-0.5], [0.3]])
    return out


def limit(stereo, crest_db, block=220, release_s=0.5):
    """Brings the peaks down to `crest_db` above the RMS, a block (5 ms) ahead and letting go over `release_s`."""
    ceiling = np.sqrt(np.mean(stereo ** 2)) * 10.0 ** (crest_db / 20.0)
    n = len(stereo)
    blocks = -(-n // block)
    peak = np.zeros(blocks * block)
    peak[:n] = np.abs(stereo).max(axis=1)
    need = np.minimum(1.0, ceiling / (peak.reshape(blocks, block).max(axis=1) + 1e-12))
    need[:-1] = np.minimum(need[:-1], need[1:])
    gain = np.empty(blocks)
    keep = np.exp(-block / SR / release_s)
    cur = 1.0
    for i in range(blocks):
        cur = min(need[i], 1.0 - (1.0 - cur) * keep)
        gain[i] = cur
    gain = np.convolve(gain, np.hanning(5) / np.hanning(5).sum(), mode="same")
    return stereo * np.interp(np.arange(n), (np.arange(blocks) + 0.5) * block, gain)[:, None]


# Mix, in dB, each instrument against its own render
GAIN_DB = {"violin": -3.0, "piano": 6.0}
ROOM_SEND = 0.9          # how much of the sum goes into the room, and how much of that comes back
ROOM_RETURN = 0.45
CREST_DB = 15.0


def play(rng):
    """The violin and the piano, dry, as stereo float arrays of the same length."""
    stems = {"violin": violin_part(rng).render(tail_s=4.0), "piano": piano_part(rng).render(tail_s=4.0)}
    n = max(len(x) for x in stems.values())
    return {name: np.pad(x, ((0, n - len(x)), (0, 0))) for name, x in stems.items()}


def render(rng):
    """The whole piece, stereo float, peak 0.9 (the catalog sets the level)."""
    dry = sum(x * 10.0 ** (GAIN_DB[name] / 20.0) for name, x in play(rng).items())
    mix = dry + ROOM_RETURN * room(dry * ROOM_SEND, rng)
    mix = wow_and_flutter(mix, rng)
    mix = band(mix, lowcut=130.0, highcut=5200.0)                     # the disc's band
    mix = np.tanh(1.5 * mix / np.abs(mix).max()) / np.tanh(1.5)        # a little saturation
    mix = surface(mix, rng)
    return finalize(limit(mix, CREST_DB), fade_in_s=0.05, fade_out_s=6.0, peak=0.9)
