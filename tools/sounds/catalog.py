"""
The Python-generated sounds of the Levels 1-2 demo, one generator per sound.

Each generator takes (rng, variant) and returns a mono (1-D) or stereo ((n, 2)) array.
`variant` is 0..variants-1; the seed is derived from the sound id and the variant, so
every run produces the same files. Sound ids follow the planned Minecraft sound events
(`every_tunnel:<id>`). Files are dry (no reverb): Sound Physics Remastered adds it in game.
"""

from dataclasses import dataclass
from typing import Callable, Optional

import numpy as np

import menu_piano
from scipy import signal as sp_signal
from dsp import (
    SR, TAU, bandpass, burst, crossfade_loop, damped_partials, drive, envelope, fade, fft_filter,
    glide_tone, highpass, loop_frequency, lowpass, mains_hum, pan, place, resonator, samples, saw,
    shaped_noise, smooth_random, stereo, time_axis,
)


@dataclass
class Sound:
    id: str
    group: str
    use: str
    channels: str          # "mono" (positional in the world) or "stereo" (plays in the player's head)
    loop: bool
    variants: int
    generate: Callable
    peak_db: float = -1.0  # preview loudness; in game volumes are set in sounds.json
    notes: str = ""
    # Streamed from disk in game? None = by length (see export.py). Minecraft plays only a few
    # streamed sounds at once, so sounds that play many times at once must not stream.
    stream: Optional[bool] = None
    # In-game balance, written to sounds.json: base volume (code and level files multiply it)
    # and the distance in blocks at which a positioned sound fades out (None = Minecraft's 16).
    game_volume: float = 1.0
    attenuation: Optional[int] = None
    # Random seed as (sound id, variant) instead of this sound's own: keeps the audio of a
    # sound that was renamed or split out of another one.
    seed: Optional[tuple] = None


# ===========================================================================
# Shared building blocks

def ballast_buzz(t, rng, f0=100.0):
    """Harsh fluorescent ballast buzz: clipped mains wave, band-limited."""
    raw = np.tanh(3.0 * np.sin(TAU * f0 * t + rng.uniform(0, TAU)))
    return bandpass(raw, 300.0, 5000.0, order=2)


def tink(n, rng, start, amp=1.0):
    """Glow starter / small metal 'tink'."""
    base = rng.uniform(2900, 3600)
    return damped_partials(n, [base, base * 1.58, base * 2.31], [amp, amp * 0.5, amp * 0.25],
                           [0.03, 0.02, 0.012], rng, start)


def plastic_click(n, rng, start, brightness=6000.0, amp=1.0):
    """Small plastic click: a very short noise tick plus a tiny body resonance."""
    y = burst(n, rng, start, 0.002, lo=700.0, hi=brightness, tau=0.0007, amp=amp)
    body = rng.uniform(650, 950)
    y += damped_partials(n, [body, body * 2.6, body * 4.9], [0.12 * amp, 0.08 * amp, 0.05 * amp],
                         [0.008, 0.005, 0.003], rng, start)
    return y


def relay_clunk(n, rng, start=0.0, heavy=False):
    """Relay or breaker: low thump, contact click, contact bounce, short metal ring."""
    s0 = samples(start)
    m = n - s0
    t = time_axis(m)
    f_hi, f_lo, tau = (70.0, 40.0, 0.09) if heavy else (95.0, 55.0, 0.05)
    thump = np.zeros(n)
    thump[s0:] = glide_tone(f_lo + (f_hi - f_lo) * np.exp(-t / 0.03), (1.0, 0.3)) * np.exp(-t / tau)
    y = 0.9 * thump
    y += burst(n, rng, start, 0.003, lo=1500.0, hi=7000.0, tau=0.001, amp=0.8)
    y += damped_partials(n, [1800, 2900, 4400], [0.25, 0.15, 0.1], [0.015, 0.01, 0.007], rng, start)
    bounce = start + rng.uniform(0.012, 0.025)
    y += burst(n, rng, bounce, 0.002, lo=1500.0, hi=6000.0, tau=0.0008, amp=0.35)
    y += damped_partials(n, [950, 2420], [0.12, 0.06], [0.12, 0.07], rng, start)
    if heavy:
        y += burst(n, rng, start + 0.06, 0.004, lo=800.0, hi=5000.0, tau=0.0015, amp=0.5)
    return y


def heartbeat_beat(rng, length=0.65):
    """
    One heartbeat as heard from inside: S1 ("lub", longer and lower, two close parts) and
    S2 ("dub", shorter and crisper), a fleshy low thump under both and a faint pulse of
    blood after S1. Muffled like through the body, saturated so small speakers play it.
    Each sound also has a knock and harmonics between about 150 and 700 Hz: the thump
    alone is almost all below 150 Hz, which laptop and monitor speakers barely play.
    """
    n = samples(length)
    t = time_axis(n)
    y = np.zeros(n)
    s2 = rng.uniform(0.26, 0.29)
    pitch = rng.uniform(0.93, 1.07)
    parts = [  # start, start Hz, end Hz, decay, level
        (0.0, 58.0, 38.0, 0.065, 1.0),
        (rng.uniform(0.015, 0.025), 72.0, 46.0, 0.05, 0.45),
        (s2, 86.0, 58.0, 0.04, rng.uniform(0.5, 0.65)),
        (s2 + rng.uniform(0.008, 0.015), 98.0, 66.0, 0.03, 0.25),
    ]
    for start, f_hi, f_lo, tau, amp in parts:
        s0 = samples(start)
        tt = t[:n - s0]
        tone = glide_tone(pitch * (f_lo + (f_hi - f_lo) * np.exp(-tt / 0.035)), (1.0, 0.6, 0.4, 0.25, 0.12))
        y[s0:] += amp * tone * (1.0 - np.exp(-tt / 0.007)) * np.exp(-tt / tau)
    y += burst(n, rng, 0.0, 0.14, hi=180.0, tau=0.045, amp=0.4)
    y += burst(n, rng, s2, 0.1, hi=260.0, tau=0.03, amp=0.25)
    y += burst(n, rng, s2, 0.015, lo=120.0, hi=500.0, tau=0.006, amp=0.12)
    y += burst(n, rng, 0.0, 0.05, lo=150.0, hi=650.0, tau=0.018, amp=0.5)       # S1 knock
    y += burst(n, rng, s2, 0.04, lo=200.0, hi=750.0, tau=0.014, amp=0.4)       # S2 knock
    flow = lowpass(rng.standard_normal(n), 220.0, order=2)
    y += 0.06 * flow * envelope(n, [(0.0, 0.0), (0.06, 0.0), (0.12, 1.0), (0.24, 0.0), (length, 0.0)])
    y = lowpass(y, 1400.0, order=2)
    return fade(drive(y * 1.4, 2.6), 0.002, 0.04)


def ring(n, rng, freq, start, tau, amp, detune=12.0):
    """Ear ringing tone, slightly different per ear so it beats."""
    s0 = samples(start)
    t = time_axis(n - s0)
    env = np.zeros(n)
    env[s0:] = (1.0 - np.exp(-t / 0.03)) * np.exp(-t / tau)
    left = np.sin(TAU * freq * time_axis(n)) * env
    right = np.sin(TAU * (freq + detune) * time_axis(n)) * env
    hiss = bandpass(rng.standard_normal(n), freq * 0.95, freq * 1.05, order=2) * env * 0.08
    return amp * stereo(left + hiss, right + hiss)


def detuned_cluster(t, freqs, rng):
    """Dissonant chord of sawtooth voices, each panned; returns stereo."""
    out = np.zeros((len(t), 2))
    for i, f in enumerate(freqs):
        voice = saw(t, f, rng) / len(freqs)
        out += pan(voice, -0.7 + 1.4 * i / max(1, len(freqs) - 1))
    return out


def dark_to_bright(x, dark_hz, bright_hz, curve):
    """Time-varying brightness: crossfade between a dark and a bright filtered copy."""
    dark = lowpass(x, dark_hz, order=2)
    bright = lowpass(x, bright_hz, order=2)
    c = curve if x.ndim == 1 else curve[:, None]
    return dark * (1.0 - c) + bright * c


# ===========================================================================
# Garage ambience

def garage_drone(rng, variant):
    seconds = 60.0
    n = samples(seconds)
    t = time_axis(n)
    common = shaped_noise(n, rng, exponent=2.0, lo=18.0, hi=220.0)
    room = shaped_noise(n, rng, exponent=1.0, lo=80.0, hi=500.0)
    channels = []
    for _ in range(2):
        low = 0.6 * common + 0.4 * shaped_noise(n, rng, exponent=2.0, lo=18.0, hi=220.0)
        mid = 0.6 * room + 0.4 * shaped_noise(n, rng, exponent=1.0, lo=80.0, hi=500.0)
        channels.append(low + 0.25 * mid)
    sub = (0.25 * np.sin(TAU * loop_frequency(41.0, seconds) * t) * (1 + 0.4 * smooth_random(n, 0.05, rng, True))
           + 0.15 * np.sin(TAU * loop_frequency(55.0, seconds) * t) * (1 + 0.4 * smooth_random(n, 0.05, rng, True)))
    hum = 0.03 * mains_hum(t, 100.0, rng, harmonics=6, tilt=2.0)
    swell = 1.0 + 0.25 * smooth_random(n, 0.03, rng, True)
    return stereo((channels[0] * 0.3 + sub + hum) * swell, (channels[1] * 0.3 + sub + hum) * swell)


def fluorescent_hum(rng, variant):
    n = samples(10.0)
    t = time_axis(n)
    base = 0.6 * mains_hum(t, 100.0, rng, harmonics=25, tilt=1.5)
    buzz = fft_filter(np.tanh(3.0 * np.sin(TAU * 100.0 * t + rng.uniform(0, TAU))), 400.0, 5000.0, order=2)
    buzz *= 0.15 * (1.0 + 0.25 * smooth_random(n, 2.0, rng, True))
    hiss = 0.02 * shaped_noise(n, rng, lo=3000.0, hi=12000.0)
    return (base + buzz) * (1.0 + 0.03 * smooth_random(n, 0.5, rng, True)) + hiss


def fluorescent_flicker(rng, variant):
    seconds = rng.uniform(0.4, 2.0)
    n = samples(seconds + 0.1)
    t = time_axis(n)
    gate = np.zeros(n)
    pos = rng.uniform(0.0, 0.05)
    on = True
    switches = []
    while pos < seconds:
        length = rng.uniform(0.015, 0.15)
        if on:
            gate[samples(pos):samples(pos + length)] = rng.uniform(0.5, 1.0)
        switches.append(pos)
        pos += length
        on = not on
    gate = lowpass(gate, 300.0, order=2)
    sound = (0.5 * mains_hum(t, 100.0, rng, harmonics=25, tilt=1.4) + 0.6 * ballast_buzz(t, rng)) * gate
    for s in switches:
        if rng.random() < 0.6:
            sound += burst(n, rng, s, rng.uniform(0.001, 0.003), lo=2000.0, tau=0.0008, amp=rng.uniform(0.2, 0.6))
        if rng.random() < 0.25:
            sound += tink(n, rng, s, amp=0.25)
    return fade(sound, 0.002, 0.05)


def fluorescent_start(rng, variant):
    n = samples(3.6)
    t = time_axis(n)
    y = np.zeros(n)
    pos = 0.05
    for _ in range(int(rng.integers(2, 5))):
        y += tink(n, rng, pos, amp=0.5)
        flash = rng.uniform(0.05, 0.12)
        gate = np.zeros(n)
        gate[samples(pos + 0.01):samples(pos + 0.01 + flash)] = 1.0
        gate = lowpass(gate, 200.0, order=2)
        y += (0.4 * mains_hum(t, 100.0, rng, 20, 1.4) + 0.5 * ballast_buzz(t, rng)) * gate
        y += burst(n, rng, pos + 0.01, 0.002, lo=2000.0, tau=0.0008, amp=0.4)
        pos += rng.uniform(0.35, 0.7)
    y += tink(n, rng, pos, amp=0.6)
    settle = envelope(n, [(0.0, 0.0), (pos, 0.0), (pos + 0.35, 1.0), (3.0, 1.0), (3.55, 0.0), (3.6, 0.0)])
    jitter = 1.0 - 0.5 * np.clip(smooth_random(n, 25.0, rng), 0, 1) * envelope(n, [(pos, 1.0), (pos + 0.4, 0.0), (3.6, 0.0)])
    y += (0.6 * mains_hum(t, 100.0, rng, 25, 1.5) + 0.15 * ballast_buzz(t, rng)) * settle * jitter
    return fade(y, 0.002, 0.02)


def fluorescent_off(rng, variant):
    n = samples(2.6)
    t = time_axis(n)
    freq = 100.0 * (0.25 + 0.75 * np.exp(-t / (0.5 + 0.3 * variant)))
    tone = glide_tone(freq, [1.0 / k ** 1.5 for k in range(1, 16)], rng.uniform(0, TAU))
    y = 0.7 * tone * np.exp(-t / 0.6) + relay_clunk(n, rng, 0.0) * 0.6
    return fade(y, 0.002, 0.2)


def vent(rng, variant):
    seconds = 15.0
    n = samples(seconds)
    t = time_axis(n)
    air = shaped_noise(n, rng, exponent=1.0, lo=120.0, hi=1800.0)
    low = 0.6 * shaped_noise(n, rng, exponent=2.0, lo=30.0, hi=120.0)
    flutter = 1.0 + 0.12 * np.sin(TAU * loop_frequency(6.4, seconds) * t) + 0.1 * smooth_random(n, 0.3, rng, True)
    wander = 0.8 * smooth_random(n, 0.2, rng, True)
    motor = loop_frequency(236.0, seconds)
    whine = 0.05 * np.sin(TAU * motor * t + wander) + 0.03 * np.sin(2 * (TAU * motor * t + wander))
    return (air + low) * flutter * 0.4 + whine


def bubble(n, start, radius_mm, amp, rng, rise=0.1):
    """
    Air bubble ringing in water (van den Doel's model): frequency about 3/r, damping set
    by the radius, and a small upward pitch drift. This is the "plink" of a drip.
    """
    r = radius_mm / 1000.0
    f0 = 3.0 / r
    damping = 0.13 / r + 0.0072 * r ** -1.5
    s0 = samples(start)
    out = np.zeros(n)
    if s0 >= n:
        return out
    tt = time_axis(n - s0)
    freq = f0 * (1.0 + rise * damping * tt)
    out[s0:] = amp * glide_tone(freq, phase=rng.uniform(0, TAU)) * (1.0 - np.exp(-tt / 0.0006)) * np.exp(-damping * tt)
    return out


def drop(n, rng, start, radius_mm, amp=1.0, rise=0.1, puddle=True):
    """One drop: a tiny impact splash, then (in a puddle) the main bubble and maybe small ones."""
    y = burst(n, rng, start, 0.004, lo=1500.0, hi=9000.0, tau=0.0012, amp=0.35 * amp)
    if puddle:
        delay = start + rng.uniform(0.001, 0.003)
        y += bubble(n, delay, radius_mm, amp, rng, rise)
        for _ in range(int(rng.integers(0, 3))):
            y += bubble(n, delay + rng.uniform(0.005, 0.04), rng.uniform(1.0, 2.0), amp * rng.uniform(0.1, 0.3), rng, rise)
    else:
        y += burst(n, rng, start, 0.012, hi=4000.0, tau=0.003, amp=0.6 * amp)
        for _ in range(int(rng.integers(3, 7))):
            y += bubble(n, start + rng.uniform(0.002, 0.06), rng.uniform(0.5, 0.9), amp * rng.uniform(0.05, 0.15), rng)
    return y


def drip(rng, variant):
    """0-1 puddle plinks, 2 plink with small bubbles, 3 drop on wet concrete, 4 two drops, 5 deep plonk."""
    n = samples(0.5)
    if variant == 0:
        y = drop(n, rng, 0.01, rng.uniform(2.6, 3.2))
    elif variant == 1:
        y = drop(n, rng, 0.01, rng.uniform(4.0, 4.8), rise=0.15)
    elif variant == 2:
        y = drop(n, rng, 0.01, rng.uniform(3.0, 3.6))
        for _ in range(3):
            y += bubble(n, rng.uniform(0.015, 0.05), rng.uniform(1.0, 1.6), rng.uniform(0.15, 0.3), rng)
    elif variant == 3:
        y = drop(n, rng, 0.01, 0.0, puddle=False)
    elif variant == 4:
        r = rng.uniform(2.8, 3.6)
        y = drop(n, rng, 0.01, r) + drop(n, rng, rng.uniform(0.15, 0.3), r * rng.uniform(0.85, 1.15), amp=0.6)
    else:
        y = drop(n, rng, 0.01, rng.uniform(5.2, 6.0), rise=0.25)
    return fade(y, 0.0, 0.05)


def rumble(rng, variant):
    seconds = rng.uniform(4.0, 8.0)
    n = samples(seconds)
    t = time_axis(n)
    noise = shaped_noise(n, rng, exponent=2.0, lo=18.0, hi=140.0)
    sub = 0.5 * np.sin(TAU * np.cumsum(rng.uniform(32, 45) * (1 + 0.05 * smooth_random(n, 0.3, rng))) / SR)
    attack = rng.uniform(1.0, 2.5)
    env = envelope(n, [(0.0, 0.0), (attack, 1.0), (seconds, 0.0)]) ** 1.5
    y = (noise + sub) * env * (1.0 + 0.3 * smooth_random(n, 1.5, rng))
    for _ in range(int(rng.integers(1, 4))):
        y += burst(n, rng, rng.uniform(0.3, seconds * 0.7), 0.4, lo=60.0, hi=150.0, tau=0.15, amp=0.8)
    return fade(y, 0.05, 0.3)


def sparks(rng, variant):
    seconds = rng.uniform(0.3, 1.2)
    n = samples(seconds + 0.1)
    t = time_axis(n)
    y = np.zeros(n)
    count = int(rng.uniform(25, 60) * seconds)
    for start in np.sort(rng.uniform(0, seconds, count)):
        y += burst(n, rng, start, rng.uniform(0.0005, 0.003), lo=1500.0, tau=0.0008,
                   amp=min(1.0, rng.pareto(2.5) * 0.3 + 0.1))
    for _ in range(int(rng.integers(1, 3))):
        start = rng.uniform(0, seconds * 0.6)
        length = rng.uniform(0.04, 0.2)
        gate = np.zeros(n)
        gate[samples(start):samples(start + length)] = 1.0
        gate *= (rng.random(n) > 0.3)
        gate = lowpass(gate, 400.0, order=2)
        y += 0.5 * ballast_buzz(t, rng) * gate
    sizzle = bandpass(rng.standard_normal(n), 3000.0, 9000.0, order=2)
    sizzle *= 0.15 * np.clip(smooth_random(n, 80.0, rng), 0, 1) * envelope(n, [(0, 1), (seconds, 0.3), (seconds + 0.1, 0)])
    return fade(y + sizzle, 0.001, 0.05)


def relay(rng, variant):
    n = samples(0.6)
    return fade(relay_clunk(n, rng, 0.01, heavy=variant == 2), 0.0, 0.05)


# ===========================================================================
# Flashlight and battery

def flashlight_on(rng, variant):
    n = samples(0.25)
    y = plastic_click(n, rng, 0.005, brightness=4000.0, amp=0.5)
    y += plastic_click(n, rng, 0.005 + rng.uniform(0.02, 0.035), brightness=7500.0, amp=1.0)
    return fade(y, 0.0, 0.03)


def flashlight_off(rng, variant):
    n = samples(0.25)
    y = plastic_click(n, rng, 0.005, brightness=5500.0, amp=0.9)
    y += plastic_click(n, rng, 0.005 + rng.uniform(0.025, 0.04), brightness=3500.0, amp=0.4)
    return fade(y, 0.0, 0.03)


def contact_fizz(n, rng, start, length, amp):
    """Bad electrical contact: a few irregular bursts of crackle over a faint buzz."""
    gate = np.zeros(n)
    pos = start
    while pos < start + length:
        on = rng.uniform(0.002, 0.015)
        gate[samples(pos):samples(pos + on)] = rng.uniform(0.4, 1.0)
        pos += on + rng.uniform(0.003, 0.025)
    gate = lowpass(gate, 600.0, order=2)
    t = time_axis(n)
    sparks_at = rng.random(n) < 0.004
    crackle = highpass(np.where(sparks_at, rng.uniform(-1.0, 1.0, n), 0.0), 2000.0, order=2)
    crackle /= np.max(np.abs(crackle)) + 1e-12
    buzz = bandpass(np.tanh(4.0 * np.sin(TAU * rng.uniform(170, 240) * t)), 300.0, 6000.0, order=2)
    buzz /= np.max(np.abs(buzz)) + 1e-12
    return amp * (0.8 * buzz + 0.5 * crackle) * gate


def flashlight_dead(rng, variant):
    """The same switch click as on/off (the switch works), then a weak fizz: the battery has nothing left."""
    n = samples(0.3)
    y = plastic_click(n, rng, 0.005, brightness=4000.0, amp=0.5)
    latch = 0.005 + rng.uniform(0.02, 0.035)
    y += plastic_click(n, rng, latch, brightness=6000.0, amp=0.85)
    y += contact_fizz(n, rng, latch + rng.uniform(0.01, 0.03), rng.uniform(0.03, 0.07), 0.25)
    if variant == 1:
        y += plastic_click(n, rng, latch + 0.12, brightness=3000.0, amp=0.15)  # loose battery rattle
    return fade(y, 0.0, 0.03)


def flashlight_flicker(rng, variant):
    """Short "bzzt" of a failing contact, played with the low-battery flicker."""
    length = rng.uniform(0.08, 0.25)
    n = samples(length + 0.05)
    return fade(contact_fizz(n, rng, 0.005, length, 1.0), 0.002, 0.02)


def thread_rasp(n, rng, start, length, amp):
    """Screwing or unscrewing a threaded cap: fast irregular ticks of the thread plus friction."""
    s0, m = samples(start), samples(length)
    rate = rng.uniform(35, 60) * (1.0 + 0.2 * smooth_random(m, 8.0, rng))
    phase = np.cumsum(rate / SR)
    ticks = np.zeros(m)
    idx = np.nonzero(np.diff(np.floor(phase)) > 0)[0]
    ticks[idx] = rng.uniform(0.5, 1.0, len(idx))
    clicky = resonator(ticks, rng.uniform(2500, 3500), 12.0) + 0.6 * resonator(ticks, rng.uniform(4500, 6000), 10.0)
    clicky /= np.max(np.abs(clicky)) + 1e-12
    friction = bandpass(rng.standard_normal(m), 2000.0, 7000.0, order=2)
    friction /= np.max(np.abs(friction)) + 1e-12
    swell = np.sin(np.pi * time_axis(m) / length) ** 0.7
    y = np.zeros(n)
    y[s0:s0 + m] = amp * (0.7 * clicky + 0.3 * friction) * swell
    return y


def battery_slide(n, rng, start, length, amp):
    """A battery sliding down the flashlight tube, then hitting the spring."""
    s0, m = samples(start), samples(length)
    noise = rng.standard_normal(m) * np.linspace(0.3, 1.0, m)
    tube = resonator(noise, rng.uniform(850, 1000), 8.0) + 0.7 * resonator(noise, rng.uniform(1650, 1900), 8.0)
    tube /= np.max(np.abs(tube)) + 1e-12
    y = np.zeros(n)
    y[s0:s0 + m] = 0.35 * amp * fade(tube, 0.01, 0.003)
    hit = start + length
    spring = rng.uniform(1400, 2000)
    k = samples(0.15)
    tt = time_axis(k)
    boing = glide_tone(spring * (1.0 - 0.05 * (1.0 - np.exp(-tt / 0.03)))) * np.exp(-tt / 0.04)
    place(y, amp * 0.35 * boing, hit)
    y += damped_partials(n, [180.0, 420.0], [0.6 * amp, 0.2 * amp], [0.03, 0.02], rng, hit)
    y += burst(n, rng, hit, 0.003, lo=800.0, hi=6000.0, tau=0.001, amp=0.5 * amp)
    return y


def battery_open(rng, variant):
    """Unscrewing the tail cap: two turns, then the cap comes loose."""
    n = samples(0.9)
    first = rng.uniform(0.18, 0.26)
    gap = rng.uniform(0.08, 0.12)
    second = rng.uniform(0.18, 0.26)
    y = thread_rasp(n, rng, 0.01, first, 0.8) + thread_rasp(n, rng, 0.01 + first + gap, second, 0.7)
    loose = 0.01 + first + gap + second + 0.02
    y += damped_partials(n, [rng.uniform(900, 1300), 2400.0], [0.5, 0.2], [0.02, 0.01], rng, loose)
    y += burst(n, rng, loose, 0.004, lo=600.0, hi=5000.0, tau=0.0015, amp=0.5)
    return fade(y, 0.0, 0.05)


def battery_close(rng, variant):
    """New battery slides in and hits the spring, then the cap is screwed on and tightened."""
    n = samples(1.2)
    slide = rng.uniform(0.08, 0.14)
    y = battery_slide(n, rng, 0.01, slide, 1.0)
    pos = 0.01 + slide + rng.uniform(0.12, 0.18)
    for _ in range(2):
        turn = rng.uniform(0.16, 0.24)
        y += thread_rasp(n, rng, pos, turn, 0.7)
        pos += turn + rng.uniform(0.07, 0.11)
    y += plastic_click(n, rng, pos, brightness=6000.0, amp=0.8)
    return fade(y, 0.0, 0.05)


# ===========================================================================
# Player feedback

def heartbeat(rng, variant):
    beat = heartbeat_beat(rng)
    return stereo(beat, beat)


def tinnitus(rng, variant):
    n = samples(5.0)
    return fade(ring(n, rng, 6200.0, 0.0, 1.8, 1.0, detune=15.0), 0.0, 0.5)


# ===========================================================================
# Level 2 / Pale man (non-organic layers)

def stinger_heard(rng, variant):
    n = samples(4.0)
    t = time_axis(n)
    y = np.zeros((n, 2))
    cluster = detuned_cluster(t, [98.0, 103.8, 146.8, 155.6, 207.7], rng)
    if variant == 0:  # swell, then cut
        peak = 1.2
        level = envelope(n, [(0.0, 0.0), (peak, 1.0), (peak + 0.05, 0.3), (peak + 0.6, 0.0), (4.0, 0.0)]) ** 1.5
        y += dark_to_bright(cluster, 300.0, 3000.0, np.clip(t / peak, 0, 1)) * level[:, None] * 2.0
        hit = 0.0
    else:
        hit = 0.05 if variant == 1 else 1.0
        if variant == 2:  # riser into the hit
            rise = np.clip(t / hit, 0, 1) * (t < hit)
            sweep = bandpass(rng.standard_normal(n), 200.0, 6000.0, order=2)
            y += stereo(sweep, sweep) * (rise ** 2 * 0.5)[:, None]
            f_rise = 300.0 + 900.0 * np.clip(t / hit, 0, 1) ** 2
            tones = glide_tone(f_rise) + glide_tone(f_rise * 1.059)
            y += stereo(tones, tones) * (rise ** 2 * 0.15)[:, None]
        s0 = samples(hit)
        tt = time_axis(n - s0)
        tail = np.zeros(n)
        tail[s0:] = np.exp(-tt / 1.2)
        bright = np.zeros(n)
        bright[s0:] = np.exp(-tt / 0.4)
        y += dark_to_bright(cluster, 250.0, 4000.0, bright) * tail[:, None] * 2.0
        sub = np.zeros(n)
        sub[s0:] = glide_tone(30.0 + 25.0 * np.exp(-tt / 0.15)) * np.exp(-tt / 0.4)
        thump = burst(n, rng, hit, 0.3, hi=3000.0, tau=0.08, amp=0.6)
        y += stereo(sub + thump, sub + thump)
    return fade(drive(y, 1.5), 0.002, 0.2)


def string_voice(t, freq, seconds, rng, vibrato_cents=12.0, detune_cents=6.0):
    """Synthetic string: three slightly detuned sawtooth voices with vibrato (loops exactly)."""
    n = len(t)
    v = loop_frequency(rng.uniform(4.5, 6.0), seconds)
    out = np.zeros(n)
    for cents in (-detune_cents, 0.0, detune_cents):
        f = loop_frequency(freq * 2 ** (cents / 1200.0), seconds)
        depth = f * (2 ** (vibrato_cents / 1200.0) - 1.0) / v
        phase = (TAU * f * t + depth * np.sin(TAU * v * t + rng.uniform(0, TAU))
                 + 0.3 * smooth_random(n, 0.5, rng, True) + rng.uniform(0, TAU))
        if f < 400.0:
            out += 2.0 * ((phase / TAU) % 1.0) - 1.0
        else:
            out += sum(np.sin(k * phase) / k for k in range(1, int(6000.0 / f) + 1))
    return out / 3.0


def tension_strings(rng, seconds=60.0):
    """Dissonant string clusters that slowly swell, a tremolo layer and a thin high squeal."""
    n = samples(seconds)
    t = time_axis(n)
    y = np.zeros((n, 2))
    low_swell = 0.35 + 0.65 * (0.5 - 0.5 * np.cos(TAU * t / 30.0)) ** 2
    for freq, position in ((82.41, -0.4), (87.31, 0.4), (116.54, 0.0)):
        y += pan(0.35 * string_voice(t, freq, seconds, rng) * low_swell, position)
    mid_swell = (0.5 - 0.5 * np.cos(TAU * t / 20.0 + 2.0)) ** 2
    tremolo = 1.0 - 0.6 * (0.5 + 0.5 * np.sin(TAU * loop_frequency(12.0, seconds) * t))
    for freq, position in ((329.63, -0.6), (349.23, 0.6), (493.88, 0.1)):
        y += pan(0.12 * string_voice(t, freq, seconds, rng) * mid_swell * tremolo, position)
    bend = 40.0 * np.sin(TAU * t / seconds)
    squeal_level = 0.03 * (0.5 - 0.5 * np.cos(TAU * t / seconds)) ** 3
    for freq, position in ((1318.5, -0.3), (1396.9, 0.3)):
        f = loop_frequency(freq, seconds)
        phase = TAU * f * t + TAU * f * np.cumsum(2 ** (bend / 1200.0) - 1.0) / SR
        y += pan(squeal_level * (np.sin(phase) + 0.3 * np.sin(2 * phase)), position)
    y = fft_filter(y, 60.0, 3500.0, order=2)
    bow = stereo(shaped_noise(n, rng, 0.0, 1000.0, 5000.0), shaped_noise(n, rng, 0.0, 1000.0, 5000.0))
    y += 0.02 * bow * low_swell[:, None]
    sub = shaped_noise(n, rng, 2.0, 25.0, 80.0)
    return y + 0.15 * stereo(sub, sub)


def tension_rising(rng, seconds=60.0, octave_seconds=15.0, fade_seconds=4.0):
    """Endless rising tone (Shepard-Risset glissando), two stacks a semitone apart, over a low drone."""
    n = samples(seconds + fade_seconds)
    t = time_axis(n)
    octaves = 7
    y = np.zeros((n, 2))
    for ratio, level, position in ((1.0, 1.0, -0.3), (2 ** (1 / 12), 0.5, 0.3)):
        stack = np.zeros(n)
        for i in range(octaves):
            pos = (i + t / octave_seconds) % octaves
            freq = 27.5 * ratio * 2 ** pos
            window = 0.5 - 0.5 * np.cos(TAU * pos / octaves)
            phase = TAU * np.cumsum(freq) / SR + rng.uniform(0, TAU)
            stack += window * (np.sin(phase) + 0.3 * np.sin(2 * phase))
        y += pan(level * stack, position)
    y *= (1.0 - 0.15 * (0.5 + 0.5 * np.sin(TAU * 6.0 * t)))[:, None]
    drone = 0.6 * np.sin(TAU * 41.2 * t) + 0.3 * shaped_noise(n, rng, 2.0, 25.0, 90.0)
    y += stereo(drone, drone)
    return crossfade_loop(y, fade_seconds)


def tension_pulse(rng, seconds=60.0):
    """A slow low throb, breathing air, a dissonant swell and a faint high whine."""
    n = samples(seconds)
    t = time_axis(n)
    period = 1.0 / loop_frequency(0.75, seconds)
    ph = (t % period) / period
    pulse = ph ** 0.5 * (1.0 - ph) ** 3
    pulse /= pulse.max()
    throb = drive(np.sin(TAU * loop_frequency(46.0, seconds) * t), 2.0) * pulse
    y = stereo(throb, throb)
    breath = 0.5 + 0.5 * np.sin(TAU * loop_frequency(1 / 8.5, seconds) * t)
    air = stereo(shaped_noise(n, rng, 1.0, 150.0, 1200.0), shaped_noise(n, rng, 1.0, 150.0, 1200.0))
    y += 0.12 * air * breath[:, None]
    swell = (0.5 - 0.5 * np.cos(TAU * t / 20.0)) ** 2
    cluster = sum(signal_saw(t, loop_frequency(f, seconds), rng) for f in (82.41, 87.31))
    cluster = fft_filter(cluster, None, 500.0, order=2) * swell * 0.15
    y += stereo(cluster, cluster)
    level = np.clip(smooth_random(n, 0.05, rng, True), 0, 1) * 0.02
    for freq, position in ((3100.0, -0.5), (3270.0, 0.5)):
        f = loop_frequency(freq, seconds)
        phase = TAU * f * t + 15.0 / 0.3 * np.sin(TAU * loop_frequency(0.3, seconds) * t)
        y += pan(level * np.sin(phase), position)
    sub = shaped_noise(n, rng, 2.0, 20.0, 70.0)
    return y + 0.2 * stereo(sub, sub)


def pale_man_step(rng, variant, run):
    """
    A long bare foot on concrete: the heel's dull thud, then the flat sole slapping down,
    then the skin peeling off the dusty floor. Running he lands harder and faster, a
    heavy body on long legs.
    """
    n = samples(0.45)
    heel = rng.uniform(0.008, 0.015)
    slap = heel + (rng.uniform(0.035, 0.05) if run else rng.uniform(0.07, 0.1))
    weight = 1.0 if run else 0.55
    y = burst(n, rng, heel, 0.04, hi=rng.uniform(260, 340), tau=0.014, amp=1.4 * weight)            # heel thud
    y += damped_partials(n, [rng.uniform(70, 95), rng.uniform(150, 190)], [0.6 * weight, 0.25 * weight],
                         [0.05, 0.025], rng, heel)                                                       # body weight
    y += burst(n, rng, slap, 0.025, lo=900.0, hi=rng.uniform(5000, 7000), tau=0.005,
               amp=(1.1 if run else 0.6) * rng.uniform(0.85, 1.15))                                     # skin slap
    y += burst(n, rng, slap, 0.03, lo=250.0, hi=900.0, tau=0.012, amp=0.5 * weight)                    # sole body
    peel = slap + rng.uniform(0.06, 0.1)
    for i in range(int(rng.integers(5, 10))):                                                           # sticky lift-off
        y += burst(n, rng, peel + i * rng.uniform(0.006, 0.014), 0.003, lo=2500.0, hi=9000.0, tau=0.0012,
                   amp=rng.uniform(0.03, 0.08) * (1.0 - i / 10))
    if run:
        y += burst(n, rng, heel, 0.12, lo=300.0, hi=2500.0, tau=0.04, amp=0.12)                         # grit scraped along
    return fade(y, 0.0, 0.06)


def chase(rng, variant):
    bpm = 120.0
    beat = 60.0 / bpm
    seconds = 32 * beat  # 8 bars, 16 s
    n = samples(seconds)
    t = time_axis(n)
    y = np.zeros((n, 2))

    kick_n = samples(0.6)
    kt = time_axis(kick_n)
    kick = glide_tone(38.0 + 14.0 * np.exp(-kt / 0.05), (1.0, 0.3)) * np.exp(-kt / 0.18)
    kick = drive(kick, 2.0) + burst(kick_n, rng, 0.0, 0.004, lo=1000.0, tau=0.001, amp=0.2)
    tom_n = samples(0.4)
    tt = time_axis(tom_n)
    tom = glide_tone(70.0 + 25.0 * np.exp(-tt / 0.04)) * np.exp(-tt / 0.12)
    tom += burst(tom_n, rng, 0.0, 0.05, hi=800.0, tau=0.03, amp=0.4)
    offset = 0.01  # the loop point falls just before the downbeat, not on the hit
    for b in range(32):
        place(y, stereo(kick, kick), b * beat + offset, circular=True)
        if b % 4 in (1, 3):
            place(y, pan(0.6 * tom, -0.3 if b % 8 < 4 else 0.3), (b + 0.75) * beat + offset, circular=True)
        tick = burst(samples(0.02), rng, 0.0, 0.004, lo=5000.0, tau=0.001, amp=0.08)
        place(y, pan(tick, 0.5), (b + 0.5) * beat + offset, circular=True)

    # Ducks on every kick (fast but smooth drop, slower recovery) without clicks.
    since_kick = (t - offset) % beat
    pump = 1.0 - 0.6 * np.exp(-since_kick / 0.12) * (1.0 - np.exp(-since_kick / 0.004))
    drone = (signal_saw(t, loop_frequency(55.0, seconds), rng) + signal_saw(t, loop_frequency(58.27, seconds), rng)) * 0.3
    drone = fft_filter(drone, None, 400.0, order=2) * pump
    y += stereo(drone, drone)

    trem = 1.0 - 0.7 * (0.5 + 0.5 * np.sin(TAU * loop_frequency(8.0, seconds) * t))
    swell = 0.6 + 0.4 * np.sin(TAU * t / seconds)
    for f, position in ((415.3, -0.6), (440.0, 0.0), (466.16, 0.6)):
        voice = fft_filter(signal_saw(t, loop_frequency(f, seconds), rng), 300.0, 3000.0, order=2)
        y += pan(0.05 * voice * trem * swell, position)
    return y


def signal_saw(t, freq, rng):
    """Sawtooth with an exact frequency (for loops; aliasing is filtered afterwards)."""
    return 2.0 * ((freq * t + rng.uniform(0, 1)) % 1.0) - 1.0


# ===========================================================================
# Level progress

def level_transition(rng, variant):
    n = samples(6.0)
    t = time_axis(n)
    whoomp = drive(glide_tone(22.0 + 48.0 * np.exp(-t / 0.5), (1.0, 0.3)) * (1 - np.exp(-t / 0.05)) * np.exp(-t / 1.2), 2.0)
    y = stereo(whoomp, whoomp)
    rumble_env = envelope(n, [(0.0, 0.0), (0.8, 1.0), (6.0, 0.0)])
    y += 0.5 * stereo(shaped_noise(n, rng, 2.0, 20.0, 150.0), shaped_noise(n, rng, 2.0, 20.0, 150.0)) * rumble_env[:, None]
    metal = damped_partials(n, [62.0, 171.0, 338.0, 590.0], [0.3, 0.2, 0.12, 0.06], [2.5, 1.8, 1.2, 0.8], rng, 0.1)
    y += pan(metal, -0.2) + pan(metal * 0.8, 0.3)
    whoosh = shaped_noise(n, rng, 1.0, 100.0, 6000.0) * envelope(n, [(0.0, 0.0), (0.2, 0.4), (1.5, 0.0), (6.0, 0.0)])
    y += stereo(*(2 * [dark_to_bright(whoosh, 300.0, 4000.0, np.clip(1.0 - t / 1.5, 0, 1))]))
    return fade(y, 0.002, 0.5)


def death(rng, variant):
    n = samples(5.0)
    t = time_axis(n)
    drop = glide_tone(20.0 + 70.0 * np.exp(-t / (0.3 if variant == 0 else 1.2)), (1.0, 0.4)) * np.exp(-t / (0.6 if variant == 0 else 2.0))
    hit = burst(n, rng, 0.0, 0.8, hi=6000.0, tau=0.15, amp=1.0)
    cluster = detuned_cluster(t, [69.3, 73.4, 103.8, 110.0], rng) * np.exp(-t / 0.5)[:, None]
    y = drive(stereo(drop + hit, drop + hit) + cluster * 1.5, 4.0)
    if variant == 1:
        collapse = shaped_noise(n, rng, 2.0, 20.0, 300.0) * envelope(n, [(0, 0), (0.3, 0.8), (4.5, 0.0), (5.0, 0.0)])
        y += 0.5 * stereo(collapse, collapse)
    y += ring(n, rng, 4000.0, 0.3, 2.0, 0.08)
    return fade(y, 0.0, 0.4)


# ===========================================================================

# ===========================================================================
# Materials: footsteps and impacts on the props (block sound groups in the mod)
#
# Minecraft plays footsteps quietly by itself (the block's volume x 0.15) and impacts
# louder, so both are normalized like the other sounds and balanced by game_volume.

def knock(n, rng, start, base, ratios, amps, taus, click=0.6, click_hi=7000.0):
    """A struck object: a short noise click for the contact plus its ringing modes."""
    y = burst(n, rng, start, 0.003, lo=600.0, hi=click_hi, tau=0.001, amp=click)
    y += damped_partials(n, [base * r for r in ratios], amps, taus, rng, start)
    return y


def cardboard(rng, variant, impact):
    n = samples(0.4)
    hit = 1.0 if impact else 0.5
    y = burst(n, rng, 0.01, 0.06, hi=420.0, tau=0.018, amp=1.2 * hit)                     # the box's dull thump
    y += damped_partials(n, [rng.uniform(120, 190), rng.uniform(260, 330)], [0.5 * hit, 0.2 * hit], [0.035, 0.02], rng, 0.01)
    for _ in range(int(rng.integers(2, 5 if impact else 3))):                                # paper crinkle
        y += burst(n, rng, rng.uniform(0.01, 0.08), rng.uniform(0.004, 0.012), lo=2500.0, hi=9000.0, tau=0.004,
                   amp=rng.uniform(0.08, 0.2))
    return fade(y, 0.0, 0.05)


def plastic(rng, variant, impact):
    n = samples(0.4)
    base = rng.uniform(300, 380) if impact else rng.uniform(420, 560)
    y = knock(n, rng, 0.01, base, [1.0, 2.31, 3.86, 5.1], [1.0, 0.5, 0.3, 0.15], [0.05, 0.03, 0.02, 0.012], click=0.5)
    y += burst(n, rng, 0.01, 0.02, hi=900.0, tau=0.008, amp=0.6)                            # hollow body
    if impact and rng.random() < 0.7:                                                        # a small bounce
        t2 = rng.uniform(0.07, 0.12)
        y += 0.35 * knock(n, rng, t2, base * rng.uniform(0.97, 1.03), [1.0, 2.31, 3.86], [1.0, 0.5, 0.3], [0.04, 0.025, 0.015])
    return fade(y, 0.0, 0.05)


def hollow_metal(rng, variant, impact):
    """A big empty steel body (oil drum, extinguisher): a low bong that rings on."""
    n = samples(1.2 if impact else 0.6)
    base = rng.uniform(150, 210)
    taus = [0.45, 0.25, 0.15, 0.09] if impact else [0.12, 0.08, 0.05, 0.03]
    y = knock(n, rng, 0.01, base, [1.0, 1.59, 2.71, 4.13], [1.0, 0.6, 0.4, 0.25], taus, click=0.8)
    y += burst(n, rng, 0.01, 0.03, hi=600.0, tau=0.01, amp=0.9 if impact else 0.5)
    return fade(y, 0.0, 0.1)


def metal_pipe(rng, variant, impact):
    """A steel pipe: free-bar modes (1, 2.76, 5.40, 8.93) ring a long time when struck."""
    n = samples(1.5 if impact else 0.5)
    base = rng.uniform(280, 420)
    taus = [0.8, 0.45, 0.25, 0.14] if impact else [0.09, 0.05, 0.03, 0.02]
    y = knock(n, rng, 0.01, base, [1.0, 2.756, 5.404, 8.933], [1.0, 0.7, 0.45, 0.25], taus, click=1.0, click_hi=9000.0)
    return fade(y, 0.0, 0.1)


def sheet_metal(rng, variant, impact):
    """Thin galvanized sheet (ducts, trays, panels): a dense, wobbling, slightly buzzy boom."""
    n = samples(0.9 if impact else 0.45)
    base = rng.uniform(85, 130)
    ratios = sorted(rng.uniform(1.0, 9.0, 10))
    amps = [0.8 / (1 + i * 0.4) for i in range(10)]
    taus = [(0.35 if impact else 0.1) / (1 + i * 0.25) for i in range(10)]
    y = knock(n, rng, 0.01, base, ratios, amps, taus, click=0.7)
    t = time_axis(n)
    y *= 1.0 + 0.35 * np.sin(TAU * rng.uniform(5, 9) * t)                                   # the sheet flexing
    for _ in range(int(rng.integers(2, 6 if impact else 3))):                               # loose screws rattling
        y += burst(n, rng, rng.uniform(0.02, 0.15), 0.004, lo=2000.0, hi=7000.0, tau=0.0015, amp=rng.uniform(0.1, 0.25))
    return fade(drive(y, 1.5), 0.0, 0.08)


def wire_mesh(rng, variant, impact):
    """Shopping cart: the wire basket rattles."""
    n = samples(0.5)
    y = burst(n, rng, 0.01, 0.02, hi=700.0, tau=0.008, amp=0.5)
    for _ in range(int(rng.integers(6, 12) if impact else rng.integers(3, 6))):
        y += tink(n, rng, rng.uniform(0.01, 0.18), amp=rng.uniform(0.15, 0.5))
    return fade(y, 0.0, 0.05)


def wood(rng, variant, impact):
    """Pallet boards: a dry knock, with a short creak when stepped on."""
    n = samples(0.5)
    base = rng.uniform(190, 260)
    y = knock(n, rng, 0.01, base, [1.0, 2.43, 4.1, 6.3], [1.0, 0.45, 0.25, 0.12],
              [0.05, 0.03, 0.018, 0.01] if impact else [0.03, 0.02, 0.012, 0.008], click=0.7)
    if not impact and rng.random() < 0.6:
        start = rng.uniform(0.04, 0.09)
        for i in range(int(rng.integers(8, 16))):                                            # creak: fast stick-slip ticks
            y += burst(n, rng, start + i * rng.uniform(0.004, 0.007), 0.002, lo=800.0, hi=3000.0, tau=0.001,
                       amp=0.12 * (1 - i / 16))
    return fade(y, 0.0, 0.05)


def puddle(rng, variant, impact):
    """Splashing through standing water: a wet slap, then a few drops and bubbles."""
    n = samples(0.6)
    amp = 1.0 if impact else 0.7
    y = burst(n, rng, 0.01, 0.09, lo=700.0, hi=7000.0, tau=0.03, amp=amp)
    y += burst(n, rng, 0.01, 0.04, hi=600.0, tau=0.012, amp=0.5 * amp)
    for _ in range(int(rng.integers(3, 7))):
        y += drop(n, rng, rng.uniform(0.04, 0.25), rng.uniform(1.2, 2.6), amp=rng.uniform(0.1, 0.3))
    return fade(y, 0.0, 0.08)


def elevator_ding(rng, variant):
    """The elevator's arrival bell: one soft struck chime (a little vibraphone-like) and its hum."""
    n = samples(2.5)
    f0 = 932.3
    y = damped_partials(n, [f0, f0 * 2.0, f0 * 3.01, f0 * 4.2], [1.0, 0.22, 0.06, 0.1], [1.3, 0.55, 0.3, 0.18], rng, 0.005)
    y += burst(n, rng, 0.005, 0.004, lo=1500.0, hi=6000.0, tau=0.0015, amp=0.15)                    # the striker
    return fade(y, 0.0, 0.3)


def elevator_doors(rng, variant):
    """
    The cabin doors sliding open, timed to the game: a relay clicks, the motor winds up,
    0.5 s later the doors roll for 0.6 s and stop against their rubber with a soft thump.
    """
    n = samples(1.8)
    t = time_axis(n)
    y = relay_clunk(n, rng, 0.02)
    moving = np.clip((t - 0.5) / 0.08, 0, 1) * np.clip((1.1 - t) / 0.1, 0, 1)
    spin = np.clip((t - 0.05) / 0.45, 0, 1) * np.clip((1.25 - t) / 0.2, 0, 1)
    motor = glide_tone(110.0 + 50.0 * spin) * spin
    motor = fft_filter(motor + 0.5 * np.sign(motor) * np.abs(motor) ** 3, None, 900.0, order=2)
    y += 0.25 * motor
    y += 0.5 * shaped_noise(n, rng, 1.0, 60.0, 400.0) * moving                                   # rollers in the track
    y += 0.12 * shaped_noise(n, rng, 0.0, 1500.0, 5000.0) * moving                               # the panels' edges brushing
    y += burst(n, rng, 1.1, 0.06, hi=250.0, tau=0.03, amp=1.0)                                   # stopped on the rubber
    y += knock(n, rng, 1.1, rng.uniform(140, 170), [1.0, 1.6, 2.7], [0.4, 0.25, 0.1], [0.08, 0.05, 0.03], click=0.2)
    return fade(y, 0.0, 0.2)


def beep(n, start, freq, length, amp=1.0):
    """A small electronic beep: a soft square-ish tone with a 3 ms ramp each end."""
    y = np.zeros(n)
    s0, m = samples(start), samples(length)
    tt = time_axis(m)
    ramp = np.clip(np.minimum(tt, length - tt) / 0.003, 0, 1)
    tone = np.tanh(3.0 * np.sin(TAU * freq * tt)) * ramp * amp
    y[s0:s0 + m] += tone[:max(0, min(m, n - s0))]
    return y


def card_reader_accept(rng, variant):
    """The exit's card reader taking the card: two rising beeps, then the lock's solenoid
    pulls back with a clunk and the door's latch clicks free."""
    n = samples(1.0)
    y = beep(n, 0.0, 2093.0, 0.07, 0.5) + beep(n, 0.11, 2794.0, 0.11, 0.5)
    y = lowpass(y, 6000.0)
    y += relay_clunk(n, rng, 0.3, heavy=True)
    y += knock(n, rng, 0.42, rng.uniform(380, 450), [1.0, 2.4, 4.1], [0.5, 0.3, 0.15], [0.05, 0.03, 0.02], click=0.6)
    return fade(y, 0.0, 0.1)


def card_reader_deny(rng, variant):
    """The card reader refusing: two low, harsh buzzes."""
    n = samples(0.5)
    y = beep(n, 0.0, 330.0, 0.13, 0.6) + beep(n, 0.19, 330.0, 0.13, 0.6)
    y += 0.3 * (beep(n, 0.0, 660.0, 0.13) + beep(n, 0.19, 660.0, 0.13))
    return fade(lowpass(y, 4000.0), 0.0, 0.05)



def valve_turn(rng, variant):
    """One full turn of a valve's handwheel: the ratchet ticking round, the clunk of the turn
    coming home, then water shifting in the pipe (a few bubbles and a low gurgle). Every valve
    sounds the same, as the electrician's note says."""
    n = samples(1.1)
    y = np.zeros(n)
    at = 0.0
    for _ in range(int(rng.integers(4, 6))):                                                # the ratchet's pawl
        y += knock(n, rng, at, rng.uniform(2300, 2700), [1.0, 1.9], [0.25, 0.1], [0.006, 0.004], click=0.35, click_hi=9000.0)
        at += rng.uniform(0.045, 0.06)
    y += knock(n, rng, at + 0.02, rng.uniform(170, 200), [1.0, 2.756, 5.404], [1.0, 0.45, 0.2], [0.14, 0.07, 0.04], click=0.9)
    y += burst(n, rng, at + 0.02, 0.05, hi=500.0, tau=0.02, amp=0.8)                         # the body's thud
    flow = shaped_noise(n, rng, 1.2, 120.0, 900.0) * envelope(n, [(0.0, 0.0), (at + 0.05, 0.0), (at + 0.2, 0.28), (1.05, 0.0)])
    y += resonator(flow, rng.uniform(210, 240), 6.0) * 0.6 + flow * 0.4
    for _ in range(int(rng.integers(3, 6))):
        y += bubble(n, rng.uniform(at + 0.1, 0.9), rng.uniform(3.5, 6.5), rng.uniform(0.08, 0.16), rng, rise=0.05)
    return fade(lowpass(y, 7000.0), 0.0, 0.08)


def valve_release(rng, variant):
    """All five valves right: a water hammer bangs through the pipe, the pipe rings and knocks
    as the pressure drops, a long hiss fades out and the last bubbles settle."""
    n = samples(3.4)
    t = time_axis(n)
    y = knock(n, rng, 0.02, 92.0, [1.0, 2.1, 2.756, 5.404], [1.0, 0.6, 0.5, 0.25], [0.5, 0.3, 0.25, 0.12], click=1.2)
    y += burst(n, rng, 0.02, 0.08, hi=300.0, tau=0.04, amp=1.4)
    for k, at in enumerate((0.55, 0.95, 1.25)):                                              # the pipe knocking down
        y += (0.5 - 0.12 * k) * knock(n, rng, at, rng.uniform(260, 320), [1.0, 2.756, 5.404], [1.0, 0.5, 0.25], [0.3, 0.15, 0.08])
    hiss = shaped_noise(n, rng, 0.3, 1500.0, 7000.0) * np.exp(-np.maximum(t - 0.1, 0.0) / 0.9) * (t > 0.08)
    y += 0.35 * hiss
    for _ in range(8):
        y += bubble(n, rng.uniform(0.4, 2.6), rng.uniform(4.0, 9.0), rng.uniform(0.05, 0.12), rng, rise=0.05)
    return fade(y, 0.0, 0.3)


# ===========================================================================
# Level 3 ambience: a near-empty building's room tone, the library's slow clock, and a phone
# ringing somewhere far off.

def hall_reverb(x, rng, seconds, damp_hz, early=((0.031, 0.5), (0.057, 0.35), (0.089, 0.25))):
    """A big, hard room's reverb for one channel: a few early reflections and an exponentially
    decaying noise tail (`seconds` to fade 60 dB), darker as it decays (`damp_hz`). Wet only."""
    n = samples(seconds)
    t = time_axis(n)
    tail = rng.standard_normal(n) * np.exp(-t * 6.9 / seconds)
    tail = dark_to_bright(tail, damp_hz, damp_hz * 4.0, np.clip(1.0 - t / seconds, 0.0, 1.0) ** 2)
    ir = 0.08 * tail
    for delay, gain in early:
        ir[samples(delay)] += gain
    return sp_signal.fftconvolve(x, ir)[: x.shape[0] + n]


def room_tone_layers(n, rng, seconds):
    """The offices' room tone, one stereo pair: the building's deep, slow breath, a faint hiss of
    air in far-off ducts and a soft, far hum, all looping by themselves."""
    t = time_axis(n)
    deep = shaped_noise(n, rng, exponent=2.2, lo=16.0, hi=140.0)
    breath = 1.0 + 0.35 * smooth_random(n, 0.04, rng, True)
    channels = []
    for _ in range(2):
        air = shaped_noise(n, rng, exponent=1.3, lo=120.0, hi=2600.0) * (1.0 + 0.2 * smooth_random(n, 0.07, rng, True))
        low = 0.7 * deep + 0.3 * shaped_noise(n, rng, exponent=2.2, lo=16.0, hi=140.0)
        channels.append(low * 0.5 * breath + air * 0.06)
    far_hum = 0.035 * np.sin(TAU * loop_frequency(52.0, seconds) * t) * (1 + 0.5 * smooth_random(n, 0.05, rng, True))
    return channels[0] + far_hum, channels[1] + far_hum


def offices_room_tone(rng, variant):
    """Level 3's background everywhere but the library and the bunker: the near-silence of a
    big, empty office building. Barely there, so the silence weighs."""
    seconds = 48.0
    n = samples(seconds)
    left, right = room_tone_layers(n, rng, seconds)
    return stereo(left, right)


def library_clock(rng, variant):
    """The library's background: a slow clock, tick and tock a second apart, filling the whole
    room with no clock to be seen, its knocks ringing round the wood and the high ceiling; the
    room tone faint under it. Loops: the reverb wraps round to the start."""
    seconds = 32.0
    n = samples(seconds)
    left, right = room_tone_layers(n, rng, seconds)
    y = stereo(left * 0.6, right * 0.6)
    dry = np.zeros(n)
    for k in range(int(seconds)):
        tock = k % 2 == 1
        base = rng.uniform(1650, 1750) if tock else rng.uniform(2050, 2150)
        tick = knock(samples(0.25), rng, 0.0, base, [1.0, 1.52, 2.3], [0.5, 0.25, 0.12], [0.012, 0.008, 0.005],
                     click=0.45, click_hi=8000.0)
        tick += 0.5 * knock(samples(0.25), rng, 0.0, rng.uniform(380, 420) if tock else rng.uniform(460, 500),
                            [1.0, 2.1], [0.6, 0.2], [0.03, 0.015], click=0.0)              # the case's wooden body
        place(dry, tick * (0.85 if tock else 1.0), k * 1.0 + 0.5, circular=True)        # off the loop point
    for ch, side in ((0, 0.9), (1, 1.1)):
        wet = hall_reverb(dry, rng, 2.6, 1800.0)
        loop = np.zeros(n)
        place(loop, wet, 0.0, circular=True)
        y[:, ch] += dry * 0.55 * side + loop * 0.9
    return y


def office_phone(rng, variant):
    """A desk phone ringing somewhere far off in the offices, in an empty room down a corridor:
    the electronic trill (two tones warbling), muffled by the distance and the walls, the room
    answering with its long echo. It rings 2, 3 or 4 times (the variant) and stops mid-ring,
    as if picked up. Played now and then at a spot around the player."""
    rings = variant + 1
    period, on = 4.0, 1.2
    length = (rings - 1) * period + on * rng.uniform(0.45, 0.8)
    n_dry = samples(length + 0.05)
    t = time_axis(n_dry)
    warble = (np.floor(t * 16.0) % 2).astype(float)
    tone = glide_tone(1000.0 + 250.0 * warble, harmonics=(1.0, 0.45, 0.25, 0.12))
    gate = np.zeros(n_dry)
    for r in range(rings):
        start, stop = r * period, min(r * period + on, length)
        gate += envelope(n_dry, [(0.0, 0.0), (start, 0.0), (start + 0.01, 1.0), (stop - 0.01, 1.0), (stop, 0.0), (length + 0.05, 0.0)])
    dry = bandpass(tone * np.clip(gate, 0.0, 1.0), 300.0, 2600.0)
    dry = lowpass(dry, 1800.0) * 0.35                                                        # far off, through walls
    wet = hall_reverb(dry, rng, 2.4, 1200.0, early=((0.043, 0.6), (0.071, 0.45), (0.12, 0.3), (0.19, 0.2)))
    y = np.zeros(wet.shape[0])
    y[:n_dry] += dry * 0.45
    y += wet * 1.2
    return fade(lowpass(y, 2200.0), 0.003, 0.4)


# ===========================================================================
# Level 3 / the Observer

def clock_tick(n, rng, start, tock, amp=1.0):
    """One tick (or the lower tock) of a big clock, its wooden case ringing a little."""
    base = rng.uniform(1650, 1750) if tock else rng.uniform(2050, 2150)
    y = knock(n, rng, start, base, [1.0, 1.52, 2.3], [0.5, 0.25, 0.12], [0.012, 0.008, 0.005], click=0.45, click_hi=8000.0)
    y += 0.5 * knock(n, rng, start, rng.uniform(380, 420) if tock else rng.uniform(460, 500), [1.0, 2.1], [0.6, 0.2],
                     [0.03, 0.015], click=0.0)
    return y * amp


def observer_clock(tock):
    """The book phase's warning (ObserverClock): one tick (or tock) of the library's clock, played
    in the head faster and louder the nearer the Observer is. Dry and close, a short ring of the
    room behind it; 0.6 s, so it never overlaps the next even at the fastest pace."""
    def generate(rng, variant):
        seconds = 0.6
        dry = np.zeros(samples(seconds))
        place(dry, clock_tick(samples(0.3), rng, 0.0, tock=tock), 0.005)
        y = stereo(dry * 0.8, dry * 0.8)
        for ch, side in ((0, 0.9), (1, 1.1)):
            wet = hall_reverb(dry, rng, 0.5, 2200.0)[:len(dry)]
            y[:, ch] += wet * 0.35 * side
        fade = np.ones(len(dry))
        tail = samples(0.1)
        fade[-tail:] = np.linspace(1.0, 0.0, tail)
        return y * fade[:, None]
    return generate


def observer_approach(rng, variant):
    """The Observer has seen you and walks your way: the library's clock again, but faster and
    closer (100 a minute), a low drone swelling under it and a thin, high tremolo held very
    quietly over it. 9.6 s, looping."""
    beat = 0.6
    seconds = 16 * beat
    n = samples(seconds)
    t = time_axis(n)
    y = np.zeros((n, 2))
    for k in range(16):
        tick = clock_tick(samples(0.3), rng, 0.0, tock=k % 2 == 1, amp=0.55)
        place(y, pan(tick, -0.15 if k % 2 else 0.15), k * beat + 0.02, circular=True)
    swell = 0.55 + 0.45 * (0.5 - 0.5 * np.cos(TAU * t / seconds))
    drone = sum(string_voice(t, f, seconds, rng, vibrato_cents=6.0) for f in (36.71, 38.89)) * 0.25
    drone = fft_filter(drone, 25.0, 300.0, order=2) * swell
    y += stereo(drone, drone)
    trem = 1.0 - 0.6 * (0.5 + 0.5 * np.sin(TAU * loop_frequency(10.0, seconds) * t))
    for f, position in ((1174.7, -0.5), (1244.5, 0.5)):
        y += pan(0.025 * string_voice(t, f, seconds, rng) * trem * swell, position)
    return y


def observer_chase(rng, variant):
    """The Observer running at you: the clock gone mad. Ticks racing on every eighth at 160,
    louder and harder on the beat, a dissonant string cluster in fast tremolo climbing over
    every two bars, and low, dull hits on the first and third beats, the drone pumping with
    them. 12 s (8 bars), looping."""
    bpm = 160.0
    beat = 60.0 / bpm
    seconds = 32 * beat
    n = samples(seconds)
    t = time_axis(n)
    y = np.zeros((n, 2))
    offset = 0.01                                                             # hits just after the loop point
    for e in range(64):                                                       # eighths
        on_beat = e % 2 == 0
        tick = clock_tick(samples(0.2), rng, 0.0, tock=e % 4 == 2, amp=0.7 if on_beat else 0.4)
        place(y, pan(tick, 0.2 if e % 2 else -0.2), e * beat / 2 + offset, circular=True)
    hit_n = samples(0.7)
    ht = time_axis(hit_n)
    hit = glide_tone(44.0 + 18.0 * np.exp(-ht / 0.05), (1.0, 0.35)) * np.exp(-ht / 0.25)
    hit = drive(hit, 1.8) + burst(hit_n, rng, 0.0, 0.03, hi=400.0, tau=0.015, amp=0.5)
    for b in range(0, 32, 2):
        place(y, stereo(hit, hit), b * beat + offset, circular=True)
    since_hit = (t - offset) % (2 * beat)
    pump = 1.0 - 0.5 * np.exp(-since_hit / 0.15) * (1.0 - np.exp(-since_hit / 0.004))
    drone = sum(string_voice(t, f, seconds, rng, vibrato_cents=4.0) for f in (36.71, 38.89, 55.0)) * 0.22
    y += stereo(*(2 * [fft_filter(drone, 25.0, 350.0, order=2) * pump]))
    # The cluster climbs a semitone over every two bars, then drops back: the clock racing.
    climb = ((t / (8 * beat)) % 1.0)
    trem = 1.0 - 0.75 * (0.5 + 0.5 * np.sin(TAU * loop_frequency(14.0, seconds) * t))
    for f, position in ((587.3, -0.6), (622.3, 0.0), (659.3, 0.6)):
        curve = f * 2 ** (climb / 12.0)
        voice = glide_tone(curve, harmonics=(1.0, 0.5, 0.33, 0.25, 0.2))
        y += pan(0.07 * voice * trem * (0.6 + 0.4 * climb), position)
    return fft_filter(y, 30.0, 9000.0, order=2)


def hatch_open(rng, variant):
    """The bunker hatch opening by itself (the fifth book back): under the floor a heavy bolt
    grinds back and drops free with a deep clank, then the two iron trapdoors swing up and bang
    against their stops one after the other, the shaft below booming with them, and the
    library's silence takes the ringing."""
    n = samples(3.6)
    t = time_axis(n)
    y = np.zeros(n)
    # The bolt: steel sliding on steel, rough and slowing, then its clank home.
    slide0, slide1 = 0.05, 0.55
    grind = shaped_noise(n, rng, 0.5, 300.0, 3500.0) * envelope(n, [(0.0, 0.0), (slide0, 0.0), (slide0 + 0.05, 0.35), (slide1 - 0.1, 0.25), (slide1, 0.0)])
    grind *= 0.6 + 0.4 * np.abs(smooth_random(n, 35.0, rng))
    y += resonator(grind, rng.uniform(900, 1100), 8.0) * 0.6 + grind * 0.3
    y += knock(n, rng, slide1, rng.uniform(140, 160), [1.0, 2.756, 5.404], [1.0, 0.5, 0.25], [0.35, 0.18, 0.08], click=1.0)
    y += burst(n, rng, slide1, 0.05, hi=500.0, tau=0.02, amp=1.0)
    # The trapdoors: a creak each as they swing, then the bang on their stops.
    for k, at in enumerate((1.05, 1.32)):
        swing = glide_tone(120.0 + 60.0 * np.clip((t - at + 0.3) / 0.3, 0, 1) + 8 * smooth_random(n, 20.0, rng), harmonics=(1.0, 0.5, 0.3))
        y += 0.08 * bandpass(swing * envelope(n, [(0.0, 0.0), (at - 0.3, 0.0), (at - 0.2, 1.0), (at, 0.6), (at + 0.01, 0.0)]), 100.0, 1500.0)
        ratios = sorted(rng.uniform(1.0, 7.0, 10))
        y += (0.9 - 0.15 * k) * knock(n, rng, at, rng.uniform(70, 85), ratios, [1.0 / (1 + i * 0.35) for i in range(10)],
                                      [0.9 / (1 + i * 0.3) for i in range(10)], click=1.1)
        y += burst(n, rng, at, 0.07, hi=250.0, tau=0.04, amp=1.6 - 0.3 * k)
    # The shaft below and the library: a low, long boom under it all.
    boom = lowpass(shaped_noise(n, rng, 2.0, 25.0, 140.0), 140.0) * np.exp(-np.maximum(t - 1.05, 0.0) / 1.1) * (t > 1.05)
    y += 0.5 * boom
    return fade(drive(y, 1.2), 0.0, 0.5)


def observer_alert(rng, variant):
    """Somewhere far off in the offices, something noticed: a heavy door knocked against its
    frame a long way down the corridors (low, dull, with the building's long echo), then the
    faint creak of a floor or a chair taking weight. A placeholder for the observant guard
    hearing a box go on the shelf, until he exists. Stereo, in the player's head, from one side."""
    n = samples(4.0)
    t = time_axis(n)
    thud = knock(n, rng, 0.05, rng.uniform(55, 65), [1.0, 1.6, 2.4], [1.0, 0.5, 0.25], [0.35, 0.2, 0.12], click=0.3, click_hi=1200.0)
    thud += burst(n, rng, 0.05, 0.12, hi=180.0, tau=0.06, amp=1.4)
    # Far away: no highs, and the long corridors answering.
    thud = lowpass(thud, 600.0)
    echo = np.zeros(n)
    for delay, gain in ((0.21, 0.45), (0.47, 0.3), (0.83, 0.2), (1.3, 0.12)):
        d = samples(delay)
        echo[d:] += gain * thud[:n - d]
    tail = lowpass(shaped_noise(n, rng, 1.5, 30.0, 400.0), 300.0) * np.exp(-np.maximum(t - 0.05, 0.0) / 1.4) * (t > 0.05) * 0.25
    body = thud + lowpass(echo, 400.0) + tail
    # The creak: a slow, dry glide, quiet, a while after.
    c0 = 1.6 + rng.uniform(0.0, 0.3)
    glide = np.clip((t - c0) / 0.9, 0.0, 1.0)
    creak = glide_tone(180.0 + 90.0 * glide + 12.0 * smooth_random(n, 25.0, rng), harmonics=(1.0, 0.6, 0.4, 0.25, 0.15))
    creak *= envelope(n, [(0.0, 0.0), (c0, 0.0), (c0 + 0.15, 0.06), (c0 + 0.7, 0.04), (c0 + 0.95, 0.0)])
    creak *= 0.5 + 0.5 * np.abs(smooth_random(n, 40.0, rng))
    creak = bandpass(creak, 150.0, 1800.0)
    side = rng.uniform(0.35, 0.6) * (1 if variant % 2 else -1)
    return fade(pan(body, side) + pan(creak, side * 0.7), 0.002, 0.6)


def locker_door(rng, variant):
    """A steel locker's door opened and pulled shut again: the latch lifting, the thin door's
    hinge squeaking as it swings, then the sheet clanging home, its vents buzzing and the latch
    catching. Played on the way into a locker and on the way out; it makes no noise for the
    level's enemies (the game does not count it)."""
    n = samples(1.3)
    t = time_axis(n)
    y = knock(n, rng, 0.02, rng.uniform(2600, 3100), [1.0, 1.8, 3.1], [0.4, 0.2, 0.1], [0.01, 0.006, 0.004],
              click=0.5, click_hi=9000.0)                                                     # the latch lifting
    # The hinge: a dry squeak gliding up as the door swings, rough from stick-slip.
    swing0, swing1 = 0.08, rng.uniform(0.38, 0.5)
    glide = np.clip((t - swing0) / (swing1 - swing0), 0.0, 1.0)
    f0 = rng.uniform(700, 900)
    squeak = glide_tone(f0 + f0 * 0.5 * glide + 25.0 * smooth_random(n, 30.0, rng), harmonics=(1.0, 0.5, 0.3, 0.15))
    squeak *= envelope(n, [(0.0, 0.0), (swing0, 0.0), (swing0 + 0.04, 0.12), (swing1 - 0.05, 0.08), (swing1, 0.0)])
    squeak *= 0.6 + 0.4 * np.abs(smooth_random(n, 60.0, rng))
    y += bandpass(squeak, 500.0, 5000.0)
    # The clang: a thin sheet booming with dense modes, wobbling as it flexes.
    slam = swing1 + 0.02
    ratios = sorted(rng.uniform(1.0, 9.0, 12))
    clang = knock(n, rng, slam, rng.uniform(150, 190), ratios, [0.9 / (1 + i * 0.35) for i in range(12)],
                  [0.3 / (1 + i * 0.25) for i in range(12)], click=0.9)
    clang *= 1.0 + 0.3 * np.sin(TAU * rng.uniform(6, 9) * t)
    y += clang + burst(n, rng, slam, 0.05, hi=400.0, tau=0.02, amp=0.9)                       # the frame's thud
    for _ in range(int(rng.integers(4, 8))):                                                 # the vent slats buzzing
        y += burst(n, rng, slam + rng.uniform(0.01, 0.2), 0.004, lo=2000.0, hi=7000.0, tau=0.0015, amp=rng.uniform(0.08, 0.22))
    y += knock(n, rng, slam + rng.uniform(0.04, 0.07), rng.uniform(2200, 2600), [1.0, 1.7], [0.35, 0.15], [0.012, 0.006],
               click=0.4)                                                                     # the latch catching
    return fade(drive(lowpass(y, 8000.0), 1.3), 0.0, 0.2)


def shutter_close(rng, variant):
    """
    A rolling shutter dropping shut: the slats clatter down the guide rails faster and
    faster (0.4 s, the time the game takes to fill the opening), then the bottom bar
    slams on the concrete and the whole curtain booms and rattles out.
    """
    n = samples(2.6)
    t = time_axis(n)
    slam = 0.42
    y = np.zeros(n)
    # Rolling down: the curtain's body rumbles louder as it speeds up.
    rumble = shaped_noise(n, rng, 1.0, 70.0, 450.0) * np.clip(t / slam, 0.0, 1.0) ** 1.5 * (t < slam)
    y += 0.35 * fade(rumble, 0.0, 0.0)
    at = 0.0
    while at < slam - 0.01:                                                                  # slats knocking in the rails
        progress = at / slam
        y += knock(n, rng, at, rng.uniform(900, 1500), [1.0, 1.7, 2.9], [0.6, 0.35, 0.2], [0.012, 0.008, 0.005],
                   click=0.4 + 0.4 * progress, click_hi=9000.0) * (0.25 + 0.5 * progress)
        at += 1.0 / (25.0 + 45.0 * progress) * rng.uniform(0.6, 1.4)
    # The slam: the bottom bar on the floor, then the sheet booming.
    ratios = sorted(rng.uniform(1.0, 8.0, 12))
    y += knock(n, rng, slam, rng.uniform(62, 75), ratios, [1.0 / (1 + i * 0.35) for i in range(12)],
               [0.7 / (1 + i * 0.3) for i in range(12)], click=1.2)
    y += burst(n, rng, slam, 0.08, hi=220.0, tau=0.04, amp=2.2)                             # thud
    y *= 1.0 + 0.3 * np.sin(TAU * 6.5 * t) * (t > slam) * np.exp(-np.clip(t - slam, 0, None) / 0.5)
    for _ in range(int(rng.integers(10, 16))):                                              # slats settling
        y += burst(n, rng, slam + rng.uniform(0.02, 0.35) ** 1.5, 0.004, lo=2000.0, hi=8000.0, tau=0.0015,
                   amp=rng.uniform(0.1, 0.35))
    return fade(drive(y, 1.3), 0.0, 0.3)


# ===========================================================================
# Main menu theme

def menu_theme(rng):
    """
    The main menu: a slow, uneasy felt piano in 3/4, about 70 s (menu_piano.py, composed by
    the user). Not a loop: it ends on an unresolved chord and a fade, and the game waits in
    silence before playing it again. It keeps its own seed, not the catalog's.
    """
    return menu_piano.render()


def material(fn, impact):
    return lambda rng, variant: fn(rng, variant, impact)


MATERIALS = [
    ("cardboard", cardboard, "Cardboard boxes"),
    ("plastic", plastic, "Traffic cones, bins, cables"),
    ("hollow_metal", hollow_metal, "Oil drums, extinguishers"),
    ("metal_pipe", metal_pipe, "Pipes"),
    ("sheet_metal", sheet_metal, "Air ducts, cable trays, the elevator's metal panels"),
    ("wire_mesh", wire_mesh, "The shopping cart"),
    ("wood", wood, "Pallets"),
    ("puddle", puddle, "Puddles (the splash plays together with the floor's own step)"),
]


SOUNDS = [
    Sound("ambient.garage.drone", "Garage ambience", "Background tone of the whole garage", "stereo", True, 1, garage_drone, -3.0,
          game_volume=0.35),
    Sound("block.fluorescent.hum", "Garage ambience", "Every working light", "mono", True, 1, fluorescent_hum, -6.0,
          stream=False, game_volume=0.5, attenuation=8),
    Sound("block.fluorescent.flicker", "Garage ambience", "Failing lights", "mono", False, 6, fluorescent_flicker, -3.0),
    Sound("block.fluorescent.start", "Garage ambience", "A light turning on", "mono", False, 2, fluorescent_start, -3.0),
    Sound("block.fluorescent.off", "Garage ambience", "Power cut", "mono", False, 2, fluorescent_off, -3.0),
    Sound("ambient.vent", "Garage ambience", "Ventilation ducts", "mono", True, 1, vent, -6.0),
    Sound("ambient.drip", "Garage ambience", "Water drops (1-2 puddle, 3 with small bubbles, 4 wet concrete, 5 two drops, 6 deep puddle)", "mono", False, 6, drip, -3.0),
    Sound("ambient.rumble", "Garage ambience", "Distant rumble (structure, traffic above)", "mono", False, 3, rumble, -1.0),
    Sound("ambient.sparks", "Garage ambience", "Broken wires, electrical panels", "mono", False, 4, sparks, -3.0),
    Sound("ambient.relay", "Garage ambience", "Relay or breaker clunk inside a wall (3rd: heavy breaker)", "mono", False, 3, relay, -3.0),
    Sound("item.flashlight.on", "Flashlight", "Switching on", "mono", False, 3, flashlight_on, -3.0),
    Sound("item.flashlight.off", "Flashlight", "Switching off", "mono", False, 3, flashlight_off, -3.0),
    Sound("item.flashlight.dead", "Flashlight", "Pressing it with a dead battery", "mono", False, 2, flashlight_dead, -3.0),
    Sound("item.flashlight.flicker", "Flashlight", "Failing contact \"bzzt\" during the low-battery flicker", "mono", False, 4, flashlight_flicker, -9.0),
    Sound("item.battery.open", "Flashlight", "Unscrewing the tail cap (start of the battery change)", "mono", False, 2, battery_open, -3.0),
    Sound("item.battery.close", "Flashlight", "New battery in, cap screwed on (end of the battery change)", "mono", False, 2, battery_close, -3.0),
    Sound("player.heartbeat", "Player feedback", "One beat, 4 variations picked at random; the game repeats it at a rate (see the previews)", "stereo", False, 4, heartbeat, -1.0),
    Sound("player.tinnitus", "Player feedback", "Ear ringing after a loud event or near death", "stereo", False, 1, tinnitus, -16.0,
          "Played quietly on purpose: high pure tones are harsh."),
    Sound("music.stinger.heard", "Level 2 / Pale man", "\"He heard you\" (1: swell, 2: hit, 3: riser + hit)", "stereo", False, 3, stinger_heard, -1.0),
    # The tension layers were variations 1-3 of one "music.tension" event; their seeds keep that audio.
    Sound("music.tension.strings", "Level 2 / Pale man", "Tension: dissonant strings (he is close, listening or searching)", "stereo", True, 1,
          lambda rng, variant: tension_strings(rng), -3.0, stream=True, seed=("music.tension", 0)),
    Sound("music.tension.rising", "Level 2 / Pale man", "Tension: endless rising tone (he walks towards a noise)", "stereo", True, 1,
          lambda rng, variant: tension_rising(rng), -3.0, stream=True, seed=("music.tension", 1)),
    Sound("music.tension.pulse", "Level 2 / Pale man", "Tension: low pulse (he is around, wandering)", "stereo", True, 1,
          lambda rng, variant: tension_pulse(rng), -3.0, stream=True, seed=("music.tension", 2)),
    Sound("entity.pale_man.step", "Level 2 / Pale man", "His bare feet on concrete, walking (6 variations)", "mono", False, 6,
          lambda rng, variant: pale_man_step(rng, variant, run=False), -3.0, stream=False, attenuation=16),
    Sound("entity.pale_man.step_run", "Level 2 / Pale man", "His bare feet on concrete, running (6 variations)", "mono", False, 6,
          lambda rng, variant: pale_man_step(rng, variant, run=True), -1.0, stream=False, attenuation=24),
    Sound("music.chase", "Level 2 / Pale man", "During a chase (120 bpm, 8 bars)", "stereo", True, 1, chase, -1.0),
    Sound("block.elevator.ding", "Level progress", "The elevator's bell when it arrives", "mono", False, 1, elevator_ding, -3.0),
    Sound("block.elevator.doors", "Level progress", "The elevator doors sliding open (motor, rollers, stop)", "mono", False, 1,
          elevator_doors, -3.0),
    Sound("block.card_reader.accept", "Level progress", "The exit's card reader taking the keycard (beeps, lock opens)", "mono", False, 1,
          card_reader_accept, -3.0),
    Sound("block.card_reader.deny", "Level progress", "The card reader refusing (no keycard)", "mono", False, 1, card_reader_deny, -6.0),
    Sound("block.valve.turn", "Level progress", "One full turn of a Level 3 valve (ratchet, clunk, water in the pipe)", "mono", False, 3,
          valve_turn, -4.0),
    Sound("block.valve.release", "Level progress", "Level 3's valves all right: water hammer, the pipe settles, the door unlocks", "mono",
          False, 1, valve_release, -2.0, attenuation=24),
    Sound("ambient.offices.room_tone", "Level 3 ambience", "The near-silence of the empty offices (everywhere but the library and the bunker)",
          "stereo", True, 1, offices_room_tone, -9.0, stream=True, game_volume=0.5),
    Sound("ambient.library.clock", "Level 3 ambience", "The library: a slow clock ticking, from nowhere in particular, over the room tone",
          "stereo", True, 1, library_clock, -6.0, stream=True, game_volume=0.45),
    Sound("ambient.offices.phone", "Level 3 ambience", "A phone ringing far off (2, 3 or 4 rings, cut mid-ring), now and then",
          "mono", False, 3, office_phone, -3.0, attenuation=32),
    Sound("music.observer.approach", "Level 3 / the Observer", "He has seen you and walks your way (clock, drone, high tremolo)", "stereo", True, 1,
          observer_approach, -4.0, stream=True),
    Sound("music.observer.chase", "Level 3 / the Observer", "He runs at you: the clock gone mad (160 bpm, 8 bars)", "stereo", True, 1,
          observer_chase, -1.0, stream=True),
    Sound("block.hatch.open", "Level progress", "Level 3's bunker hatch opening by itself (bolt, trapdoors banging up, the shaft booming)",
          "mono", False, 1, hatch_open, -1.0, attenuation=48),
    Sound("event.observer.alert", "Level progress", "Level 3: something far off noticed (a box went on the shelf); a placeholder for the observant guard",
          "stereo", False, 2, observer_alert, -6.0),
    Sound("event.observer.tick", "Level 3 / the Observer", "Book phase: the library clock's tick in your head, faster and louder as he nears",
          "stereo", False, 2, observer_clock(False), -6.0, stream=False),
    Sound("event.observer.tock", "Level 3 / the Observer", "Book phase: the library clock's tock in your head, faster and louder as he nears",
          "stereo", False, 2, observer_clock(True), -6.0, stream=False),
    Sound("block.locker.door", "Level progress", "A Level 3 locker's door, getting in or out (latch, hinge, clang); no noise for enemies",
          "mono", False, 3, locker_door, -4.0),
    Sound("block.shutter.close", "Level progress", "The rolling shutter closing behind the player (rattle down, slam)", "mono", False, 1,
          shutter_close, -1.0, attenuation=32),
    Sound("event.level_transition", "Level progress", "Going from Level 1 to Level 2", "stereo", False, 1, level_transition, -1.0),
    Sound("event.death", "Level progress", "Death (1: short, 2: with collapse)", "stereo", False, 2, death, -1.0),
    # Peak -2.9 dB: about -16.8 LUFS, as loud as the earlier theme.
    Sound("music.menu", "Menu", "Main menu theme: slow, uneasy felt piano in 3/4 (plays once, 1:10)", "stereo", False, 1,
          lambda rng, variant: menu_theme(rng), -2.9, stream=True),
] + [
    sound
    for name, fn, use in MATERIALS
    for sound in (
        Sound(f"block.{name}.step", "Materials", f"Walking on it: {use}", "mono", False, 4, material(fn, False), -3.0),
        Sound(f"block.{name}.impact", "Materials", f"Hit, placed, broken, landed on: {use}", "mono", False, 3,
              material(fn, True), -1.0, game_volume=0.8),
    )
]
