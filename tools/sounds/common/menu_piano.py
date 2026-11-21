"""
The main menu theme: a slow, uneasy felt piano in 3/4, about 70 s, synthesized with numpy.

Composed by the user (from their song.py; same notes, timbre and seed). It is not a loop:
it ends on an unresolved chord and a 6 s fade, and the game (client.effects.MenuMusic)
leaves a silence before playing it again.

- 17 bars at 54 bpm with rubato: a bass note and its octave on beat 1, the chord rolled on
  beats 2 and 3, a melody above, now and then a far, detuned high note, and "hesitation"
  pauses after some bars.
- First pass Dmaj7 - Dadd9 - Bbmaj7#11 - Gmaj7 | Dmaj7 - Dm(maj7) - Bbmaj7#11 - Asus b9; the
  second pass climbs higher with more notes; the last bar hangs on Dmaj7 with an E flat on top.
- Each note: one to three slightly detuned stiff strings (inharmonic partials, two-stage
  decay, a soft treble cutoff) and a felt hammer thump; then a short soundboard resonance and
  a dark reverb tail, all convolved by FFT.

render() always uses its own seed (SEED), not the catalog's per-id seed, so the audio stays
the same as the composer's original. Its helpers are its own (not dsp's) for the same reason.
"""

import numpy as np

SR = 44100
NOTE_RING = 13.0      # the longest a low note can ring (s)
TAIL = 6.0            # extra seconds for the reverb
SEED = 7
BPM = 54.0
REVERB = 0.8          # wet amount, 0..2


# ===========================================================================
# Basics

def midi_to_hz(note):
    """MIDI note -> Hz (A4 = 69 = 440 Hz)."""
    return 440.0 * 2.0 ** ((note - 69) / 12.0)


def pan_mono(sig, pan):
    """Mono (n,) -> stereo (n, 2). pan: -1 (left) .. 1 (right)."""
    ang = (pan + 1.0) * np.pi / 4.0
    return np.stack([sig * np.cos(ang), sig * np.sin(ang)], axis=1)


def mix_in(buf, sig, start_s, gain=1.0, pan=0.0):
    """Adds sig into the stereo buffer buf from start_s seconds on."""
    i = int(round(start_s * SR))
    if i < 0 or i >= len(buf) or len(sig) == 0:
        return
    if sig.ndim == 1:
        sig = pan_mono(sig, pan)
    j = min(len(buf), i + len(sig))
    buf[i:j] += sig[: j - i] * gain


def fft_filter(sig, lowcut=None, highcut=None):
    """Gentle high/low pass, applied in the frequency domain."""
    spec = np.fft.rfft(sig)
    freqs = np.fft.rfftfreq(len(sig), 1.0 / SR)
    mask = np.ones_like(freqs)
    if lowcut:
        mask *= 1.0 / (1.0 + (lowcut / np.maximum(freqs, 1e-3)) ** 4)
    if highcut:
        mask *= 1.0 / (1.0 + (freqs / highcut) ** 4)
    return np.fft.irfft(spec * mask, n=len(sig))


def normalize(sig):
    """Peak 1 (no division by zero)."""
    m = np.max(np.abs(sig))
    return sig / m if m > 1e-12 else sig


def convolve_channels(stereo, irs):
    """FFT convolution of each channel with its impulse response, cut to the original length."""
    nfft = 1 << int(np.ceil(np.log2(len(stereo) + max(len(ir) for ir in irs))))
    out = np.zeros_like(stereo)
    for ch in range(2):
        spec = np.fft.rfft(stereo[:, ch], nfft) * np.fft.rfft(irs[ch], nfft)
        out[:, ch] = np.fft.irfft(spec, nfft)[: len(stereo)]
    return out


# ===========================================================================
# Piano

def piano_note(freq, vel, rng, dur=None, detune=0.0):
    """
    One soft, felt piano note (mono). vel: 0..1. detune: extra cents for every string.
    """
    if dur is None:
        dur = NOTE_RING if freq < 120 else (10.0 if freq < 300 else 8.0)
    n = int(dur * SR)
    t = np.arange(n) / SR

    # Strings per note: 1 in the bass, 2 in the middle, 3 in the treble
    n_strings = 1 if freq < 100 else (2 if freq < 200 else 3)
    detunes = rng.uniform(-1.4, 1.4, n_strings) + detune

    # Inharmonicity and how many partials
    b_coef = 0.00012 * (freq / 110.0) ** 1.5
    max_partials = int(min(18, 9000.0 / freq))

    # Soft timbre: brightness barely follows the velocity, and the ceiling is low
    cutoff = 1200.0 + 3000.0 * vel
    base_decay = 0.30 * (1.0 + freq / 260.0)

    sig = np.zeros(n)
    for k in range(1, max_partials + 1):
        fk = k * freq * np.sqrt(1.0 + b_coef * k * k)
        if fk > 11000:
            break
        amp = (1.0 / k ** 0.95) * np.exp(-(fk / cutoff) ** 2)
        if amp < 1e-3:
            continue
        d = base_decay * (1.0 + 0.55 * k)
        decay = 0.70 * np.exp(-t * d) + 0.30 * np.exp(-t * d * 0.22)
        for cents in detunes:
            f = fk * 2.0 ** (cents / 1200.0)
            sig += amp * decay * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi))
    sig = normalize(sig) * 0.85

    # Soft hammer thump
    h_n = min(int(0.03 * SR), n)
    th = np.arange(h_n) / SR
    thump = fft_filter(rng.standard_normal(h_n), lowcut=150, highcut=2500 + 1500 * vel)
    sig[:h_n] += normalize(thump) * np.exp(-th * 140.0) * (0.06 + 0.14 * vel)

    # ~3 ms attack with no click, a short release at the end
    sig *= 1.0 - np.exp(-t * 900.0)
    r = min(int(0.5 * SR), n)
    sig[n - r:] *= np.cos(np.linspace(0.0, np.pi / 2.0, r)) ** 2
    return sig * vel


def pan_for(midi):
    """Pan by register (low notes left, high notes right)."""
    return float(np.clip((midi - 60) / 60.0 * 0.5, -0.45, 0.45))


# ===========================================================================
# Soundboard, reverb, final touches

def body_resonance(stereo, rng, amount=0.35):
    """
    Soundboard: convolution with a short (0.35 s) response of noise filtered to 80-2500 Hz.
    Gives body and makes it sound less like a synthesizer.
    """
    n_ir = int(0.35 * SR)
    t = np.arange(n_ir) / SR
    irs = []
    for _ in range(2):
        ir = fft_filter(rng.standard_normal(n_ir) * np.exp(-t / 0.10), lowcut=80, highcut=2500)
        irs.append(ir / (np.sqrt(np.sum(ir ** 2)) + 1e-12))
    return stereo + amount * convolve_channels(stereo, irs)


def reverb_tail(stereo, rng, seconds=4.0):
    """Only the wet part: a dark noise impulse response falling 60 dB in seconds."""
    n_ir = int(seconds * SR)
    t = np.arange(n_ir) / SR
    decay = np.exp(-6.91 * t / seconds)
    irs = []
    for _ in range(2):
        ir = fft_filter(rng.standard_normal(n_ir) * decay, lowcut=100, highcut=5000)
        irs.append(ir / (np.sqrt(np.sum(ir ** 2)) + 1e-12))
    return convolve_channels(stereo, irs)


def trim_trailing_silence(stereo, rel_threshold=0.003, keep_s=1.0):
    """Cuts the silence at the end: keeps up to the last audible point plus keep_s."""
    env = np.abs(stereo).max(axis=1)
    peak = env.max()
    if peak <= 0:
        return stereo
    loud = np.nonzero(env > rel_threshold * peak)[0]
    if len(loud) == 0:
        return stereo
    end = min(len(stereo), loud[-1] + int(keep_s * SR))
    return stereo[:end]


def finalize(stereo, fade_in_s=0.01, fade_out_s=6.0, peak=0.9):
    """Removes DC, trims the silence at the end, fades in (no click) and out, sets the peak."""
    stereo = trim_trailing_silence(stereo - stereo.mean(axis=0, keepdims=True))

    fi = min(int(fade_in_s * SR), len(stereo))
    if fi > 0:
        stereo[:fi] *= np.linspace(0.0, 1.0, fi)[:, None]
    fo = min(int(fade_out_s * SR), len(stereo))
    if fo > 0:
        stereo[-fo:] *= (np.cos(np.linspace(0, np.pi / 2, fo)) ** 2)[:, None]

    m = np.max(np.abs(stereo))
    if m > 0:
        stereo = stereo * (peak / m)
    return stereo


# ===========================================================================
# The piece

# Each bar (3 beats): (bass MIDI, chord MIDI, melody [(beat, note, velocity)], options)
#   bass     : on beat 1, with its octave
#   chord    : rolled on beats 2 and 3 (softer on 3)
#   options  : "no_chord_beat3" -> no chord on beat 3
#              "no_lh_chords"   -> no chords in the bar, only bass and melody
#              "upper"          -> (note, velocity, detune in cents): a far high note
#              "pause_after"    -> extra seconds of pause after the bar
#              "final"          -> the last bar
BARS = [
    # --- First pass: Dmaj7 - Dadd9 - Bbmaj7#11 - Gmaj7 | Dmaj7 - Dm(maj7) - Bbmaj7#11 - Asus b9
    (38, [57, 61, 66], [(0.5, 69, 0.50), (2.0, 73, 0.44)], {}),
    (38, [57, 61, 64], [(1.0, 74, 0.46), (2.5, 71, 0.42)], {}),
    (46, [57, 62, 64], [(0.5, 69, 0.46), (2.0, 64, 0.42)], {}),                     # E = #11, the rub
    (43, [59, 62, 66], [(0.5, 66, 0.40)], {"no_chord_beat3": True, "upper": (93, 0.16, 25)}),
    (38, [57, 61, 66], [(0.5, 69, 0.50), (2.0, 73, 0.46)], {}),
    (38, [53, 57, 61], [(0.5, 65, 0.46), (2.0, 64, 0.42)], {}),                     # F natural: the colour turns
    (46, [57, 62, 64], [(0.5, 70, 0.44), (2.0, 69, 0.40)], {}),
    (45, [62, 64, 70], [(0.5, 70, 0.40), (2.0, 69, 0.36)], {"pause_after": 1.4, "upper": (90, 0.14, -22)}),
    # --- Second pass: more notes, higher up
    (38, [57, 61, 66], [(0.5, 74, 0.52), (1.5, 73, 0.46), (2.5, 69, 0.44)], {}),
    (38, [57, 61, 64], [(0.5, 76, 0.50), (2.0, 74, 0.46)], {}),
    (46, [57, 62, 64], [(0.5, 74, 0.48), (2.0, 76, 0.44)], {}),
    (43, [59, 62, 66], [(0.5, 78, 0.46), (2.0, 74, 0.42)], {"no_lh_chords": True, "upper": (93, 0.15, 28)}),
    (38, [57, 61, 66], [(0.5, 81, 0.50), (2.0, 78, 0.46)], {}),
    (38, [53, 57, 61], [(0.5, 77, 0.48), (2.0, 73, 0.44)], {}),
    (46, [57, 62, 64], [(0.5, 76, 0.44), (2.0, 74, 0.40)], {}),
    (45, [62, 64, 70], [(0.5, 70, 0.42), (2.0, 74, 0.40)], {"pause_after": 0.6}),
    # --- Hanging end: D maj7 with E flat on top, unresolved
    (38, [57, 61, 63], [(1.0, 69, 0.34)], {"final": True}),
]


def schedule(bpm, rng):
    """
    BARS as a list of events (second, MIDI note, velocity, detune) with rubato: each bar is a
    little longer or shorter, and some have a hesitation after them.
    Returns (events, second of the last event).
    """
    beat = 60.0 / bpm
    events = []
    t = 0.0

    def add(start, midi, vel, detune=0.0):
        events.append((max(0.0, start), midi, vel, detune))

    for i, (bass, chord, melody, opts) in enumerate(BARS):
        first = (i == 0)  # the first bar starts exactly at t = 0

        # Bass and its octave on beat 1 (sounds right away)
        jit = 0.0 if first else rng.normal(0.0, 0.015)
        add(t + jit, bass, 0.50 * rng.uniform(0.94, 1.06))
        add(t + jit + 0.03, bass + 12, 0.36 * rng.uniform(0.94, 1.06))

        # Chords rolled on beats 2 and 3
        if not opts.get("no_lh_chords") and not opts.get("final"):
            beats = (1, 2)
            if opts.get("no_chord_beat3"):
                beats = (1,)
            for b in beats:
                base_vel = 0.30 if b == 1 else 0.25
                for k, midi in enumerate(chord):
                    add(t + b * beat + k * 0.07 + rng.normal(0.0, 0.015), midi,
                        base_vel * rng.uniform(0.92, 1.06))
        if opts.get("final"):
            for k, midi in enumerate(chord):
                add(t + beat + k * 0.12, midi, 0.28 * rng.uniform(0.94, 1.04))

        # Melody
        for off, midi, vel in melody:
            add(t + off * beat + rng.normal(0.0, 0.025), midi, vel * rng.uniform(0.93, 1.07))

        # The far, detuned high note
        if "upper" in opts:
            midi, vel, cents = opts["upper"]
            add(t + 2.5 * beat, midi, vel, cents)

        # Rubato: every bar a little different, sometimes a pause after it
        t += 3 * beat * rng.uniform(0.98, 1.04) + opts.get("pause_after", 0.0)

    return events, max(e[0] for e in events)


def render(bpm=BPM, reverb_amount=REVERB, seed=SEED):
    """The whole piece, stereo (n, 2), peak 0.9."""
    rng = np.random.default_rng(seed)
    events, last = schedule(bpm, rng)

    # Overall dynamics: up to the middle, down at the end
    total = last + 1.0
    n = int((last + NOTE_RING + TAIL) * SR)
    piano = np.zeros((n, 2))

    for start, midi, vel, detune in events:
        v = vel * float(np.interp(start, [0.0, 0.5 * total, 0.85 * total, total], [0.85, 1.0, 0.95, 0.70]))
        v = float(np.clip(v, 0.12, 0.9))
        mix_in(piano, piano_note(midi_to_hz(midi), v, rng, detune=detune), start, pan=pan_for(midi))

    piano = body_resonance(piano, rng)
    wet = reverb_tail(piano, rng) if reverb_amount > 0 else np.zeros_like(piano)
    mix = piano * 0.90 + wet * reverb_amount
    return finalize(mix)
