"""
The Python-generated sounds of the Levels 1-2 demo, one generator per sound.

Each generator takes (rng, variant) and returns a mono (1-D) or stereo ((n, 2)) array.
`variant` is 0..variants-1; the seed is derived from the sound id and the variant, so
every run produces the same files. Sound ids follow the planned Minecraft sound events
(`every_tunnel:<id>`). Files are dry (no reverb): Sound Physics Remastered adds it in game.
"""

from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

import numpy as np

import audio_io
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


def death_reveal(rng, variant):
    """The death screen's words coming up on the black: one low, dull blow, felt more than heard, its
    boom sinking away, a little wider in one ear than the other."""
    n = samples(2.4)
    t = time_axis(n)
    f = np.interp(t, [0.0, 0.08, 1.5], [70.0, 46.0, 34.0])
    boom = glide_tone(f, harmonics=(1.0, 0.35, 0.12)) * np.exp(-t / 0.55)
    blow = knock(n, rng, 0.0, rng.uniform(52, 60), [1.0, 1.55, 2.3], [1.0, 0.45, 0.2], [0.5, 0.25, 0.12], click=0.25, click_hi=900.0)
    air = lowpass(rng.normal(0.0, 1.0, n), 300.0) * 3.0 * envelope(n, [(0.0, 0.0), (0.01, 1.0), (0.5, 0.25), (2.0, 0.0)])
    mono = drive(boom * 0.8 + blow + air * 0.3, 1.6)
    return stereo(mono, lowpass(mono, 220.0) * 0.85 + mono * 0.15)


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


def projector_run(rng, variant):
    """Level 4's projector, an old one cranked by hand, in a seamless loop: no motor and no hum. The
    claw snatches the film through the gate about 16 times a second, a double metal clack, never quite
    even as the arm tires and catches up; the sprockets rattle, the film flutters over its loops, and
    the whole tin housing rings a little. Thin, dry and mechanical."""
    seconds = 6.0
    n = samples(seconds)
    y = np.zeros(n)
    # The cranking's speed wanders slowly, and comes back to where it began.
    grid = np.linspace(0.0, seconds, 2400, endpoint=False)
    rate = 16.0 * (1.0 + 0.07 * np.sin(TAU * grid / seconds + 0.6) + 0.035 * np.sin(TAU * 3 * grid / seconds + 2.1))
    beats = np.cumsum(rate) * (grid[1] - grid[0])
    clicks = int(round(beats[-1]))
    beats *= clicks / beats[-1]
    for k in range(clicks):
        at = float(np.interp(k, beats, grid))
        # A stronger pull of the claw every second frame of the crank's turn.
        strength = (1.0 if k % 4 == 0 else 0.72 if k % 2 == 0 else 0.55) * rng.uniform(0.85, 1.1)
        clack = knock(samples(0.045), rng, 0.0, rng.uniform(2300, 3100), [1.0, 1.73, 2.61], [0.4, 0.24, 0.12], [0.005, 0.003, 0.002],
                      click=0.6, click_hi=9000.0) * strength
        place(y, clack, at, circular=True)
        release = knock(samples(0.03), rng, 0.0, rng.uniform(1100, 1500), [1.0, 2.2], [0.2, 0.08], [0.004, 0.002],
                        click=0.25, click_hi=6000.0) * strength * 0.7
        place(y, release, at + 0.022, circular=True)
    # The film over its sprockets: a dry flutter following the same beat.
    gate = np.interp(np.arange(n) / n * seconds, grid, 0.55 + 0.45 * np.cos(TAU * beats))
    y += 0.09 * shaped_noise(n, rng, lo=1500.0, hi=7000.0) * gate
    # The crank's gears, faint and uneven, and the tin box ringing.
    y += 0.05 * shaped_noise(n, rng, 1.0, 180.0, 900.0) * (1.0 + 0.5 * smooth_random(n, 2.0, rng, True))
    y += 0.16 * resonator(y, 1450.0, 7.0)
    return fft_filter(y, 180.0, 8500.0, order=2)


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


def bunker_collapse(rng, variant):
    """The bunker tunnel's ceiling coming down behind the player (CollapseHooks): a deep groan
    in the concrete swelling for a second, sharp cracks running along it, then the fall: heavy
    slabs and chunks crashing down one after another over a low roar, rubble sliding and
    trickling after, and the tunnel's dull echo. Stereo, in the player's head, mostly behind:
    the left and right differ a little so it feels wide. 6 s."""
    seconds = 6.0
    n = samples(seconds)
    t = time_axis(n)
    chans = []
    for side in (0, 1):
        y = np.zeros(n)
        # The groan: very low, rising and wavering, before anything gives way.
        groan = lowpass(shaped_noise(n, rng, 2.0, 18.0, 90.0), 90.0)
        groan *= envelope(n, [(0.0, 0.0), (0.6, 0.5), (1.1, 1.0), (2.5, 0.8), (4.5, 0.2), (seconds, 0.0)])
        y += 0.45 * groan * (0.8 + 0.2 * smooth_random(n, 3.0, rng))
        # Cracks: dry, sharp, quickening, as the concrete splits.
        at = 0.55
        while at < 1.25:
            y += burst(n, rng, at, rng.uniform(0.01, 0.03), lo=900.0, hi=7000.0, tau=0.008, amp=rng.uniform(0.4, 0.9))
            at += rng.uniform(0.05, 0.16)
        # The fall: big slabs, then smaller chunks, crowding together, over a roar.
        roar = shaped_noise(n, rng, 1.5, 40.0, 900.0) * envelope(n, [(0.0, 0.0), (1.2, 0.0), (1.4, 1.0), (2.8, 0.7), (4.6, 0.15), (seconds, 0.0)])
        y += 0.14 * roar
        at = 1.2
        while at < 3.6:
            big = at < 2.2
            base = rng.uniform(45, 70) if big else rng.uniform(90, 160)
            ratios = sorted(rng.uniform(1.0, 5.0, 6))
            y += (1.0 if big else 0.45) * knock(n, rng, at, base, ratios, [1.0 / (1 + i * 0.5) for i in range(6)],
                                               [0.25 / (1 + i * 0.4) for i in range(6)], click=0.8, click_hi=2500.0)
            y += burst(n, rng, at, 0.06 if big else 0.03, hi=1500.0 if big else 3000.0, tau=0.03, amp=1.2 if big else 0.6)
            at += rng.uniform(0.12, 0.3) if big else rng.uniform(0.05, 0.15)
        # Rubble sliding, settling, a last trickle of grit.
        grit = shaped_noise(n, rng, 0.5, 1500.0, 9000.0) * np.abs(smooth_random(n, 25.0, rng))
        y += 0.1 * grit * envelope(n, [(0.0, 0.0), (1.6, 0.0), (2.4, 1.0), (4.2, 0.5), (5.6, 0.1), (seconds, 0.0)])
        for _ in range(8):
            y += burst(n, rng, rng.uniform(3.6, 5.4), 0.015, lo=1500.0, hi=6000.0, tau=0.006, amp=rng.uniform(0.1, 0.3))
        # The tunnel answering, dark.
        y += 0.45 * hall_reverb(lowpass(y, 2500.0), rng, 2.2, 900.0)[:n]
        chans.append(y)
    return stereo(*(fade(drive(0.25 * c, 1.3), 0.0, 0.6) for c in chans))


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


def lounge_far(rng, variant):
    """Level 4's jazz as heard in the Calm room: only its middle, as from behind several walls."""
    x, _ = audio_io.read(Path(__file__).resolve().parent / "own" / "everytunnel_nivel4_jazz.wav")
    return bandpass(x.mean(axis=1) if x.ndim == 2 else x, 110.0, 620.0)


def _noise(n, rng, lo, hi):
    return bandpass(rng.normal(0.0, 1.0, n), lo, hi)


def puzzle_solved(rng, variant):
    """A puzzle solved: two warm, slow vibraphone-like notes, a fifth apart, kindly. The lounge is pleased."""
    n = samples(2.0)
    y = damped_partials(n, [392.0, 392.0 * 4.0], [1.0, 0.25], [0.9, 0.2], rng, 0.0)
    return y + damped_partials(n, [587.3, 587.3 * 4.0], [1.0, 0.25], [1.0, 0.2], rng, 0.32)


def record_handle(rng, variant):
    """A record slid out of its sleeve, or back: card and vinyl."""
    n = samples(0.4)
    y = _noise(n, rng, 1500.0, 7000.0) * envelope(n, [(0.0, 0.0), (0.05, 0.25), (0.22, 0.18), (0.32, 0.0)])
    return y + knock(n, rng, 0.3, rng.uniform(900, 1100), [1.0, 2.2], [0.25, 0.1], [0.02, 0.01], click=0.15)


def record_right(rng, variant):
    """Set on the platter: a soft knock, the needle's tick, a moment of crackle and a low warm note."""
    n = samples(1.2)
    y = knock(n, rng, 0.02, rng.uniform(170, 200), [1.0, 2.4], [0.7, 0.2], [0.06, 0.03], click=0.3, click_hi=3000.0)
    y += knock(n, rng, 0.3, rng.uniform(3000, 3600), [1.0], [0.25], [0.004], click=0.3, click_hi=9000.0)
    crackle = (rng.random(n) > 0.9992) * rng.normal(0.0, 1.0, n)
    y += highpass(crackle, 1500.0) * 0.9 * envelope(n, [(0.0, 0.0), (0.3, 0.0), (0.35, 1.0), (1.0, 0.6), (1.2, 0.0)])
    return y + damped_partials(n, [196.0, 392.0], [0.4, 0.12], [0.5, 0.3], rng, 0.4)


def record_wrong(rng, variant):
    """The needle dragged across the record: the wrong one, and they all come off."""
    n = samples(0.8)
    t = time_axis(n)
    tear = _noise(n, rng, 900.0, 6000.0) * envelope(n, [(0.0, 0.0), (0.02, 0.9), (0.28, 0.5), (0.36, 0.0)])
    tear *= 0.6 + 0.4 * np.sin(TAU * (60.0 - 90.0 * t) * t)
    return tear + knock(n, rng, 0.42, rng.uniform(120, 150), [1.0, 2.1], [0.7, 0.3], [0.08, 0.05], click=0.5, click_hi=2500.0)


def reel_handle(rng, variant):
    """A film can lifted or put down: a thin metal lid rattling."""
    n = samples(0.45)
    y = knock(n, rng, 0.01, rng.uniform(1100, 1400), [1.0, 1.6, 2.7, 3.9], [0.6, 0.4, 0.25, 0.15], [0.08, 0.06, 0.04, 0.03], click=0.4)
    return y + knock(n, rng, 0.14, rng.uniform(1300, 1600), [1.0, 1.7, 2.9], [0.3, 0.2, 0.1], [0.05, 0.04, 0.03], click=0.2)


def reel_wrong(rng, variant):
    """The wrong reel: the projector's gate refuses it, a clack and the film flapping out."""
    n = samples(0.7)
    y = knock(n, rng, 0.01, rng.uniform(500, 600), [1.0, 2.3], [0.7, 0.3], [0.05, 0.03], click=0.6)
    flap = (np.sin(TAU * 24.0 * time_axis(n)) > 0.7) * _noise(n, rng, 800.0, 4000.0)
    return y + flap * envelope(n, [(0.0, 0.0), (0.1, 0.0), (0.14, 0.5), (0.5, 0.25), (0.65, 0.0)])


def dart_throw(rng, variant):
    """A dart leaving the hand: a short rising whisper of air."""
    n = samples(0.22)
    return _noise(n, rng, 1200.0, 5000.0) * envelope(n, [(0.0, 0.0), (0.08, 1.0), (0.2, 0.0)])


def dart_hit(rng, variant):
    """A dart in something: a dry, short thock with a little ring from the shaft."""
    n = samples(0.25)
    y = knock(n, rng, 0.001, rng.uniform(420, 520), [1.0, 2.3], [0.8, 0.3], [0.025, 0.015], click=0.7, click_hi=5000.0)
    return y + damped_partials(n, [rng.uniform(1900, 2300)], [0.14], [0.09], rng, 0.002)


def dart_right(rng, variant):
    """The right number: one soft bell-like note, as polite as a hotel's lift."""
    n = samples(1.1)
    return damped_partials(n, [523.3, 523.3 * 3.0], [1.0, 0.2], [0.6, 0.15], rng, 0.0)


def dart_wrong(rng, variant):
    """The wrong number: two low, flat notes, a small disappointed sound."""
    n = samples(0.8)
    y = damped_partials(n, [174.6, 349.2], [1.0, 0.25], [0.2, 0.1], rng, 0.0)
    return y + damped_partials(n, [164.8, 329.6], [1.0, 0.25], [0.3, 0.1], rng, 0.2)


def hatch_open(rng, variant):
    """The brass hatch: a catch let go, a small hinge."""
    n = samples(0.7)
    y = knock(n, rng, 0.01, rng.uniform(1500, 1800), [1.0, 1.8, 2.9], [0.6, 0.3, 0.15], [0.05, 0.03, 0.02], click=0.5, click_hi=8000.0)
    f0 = rng.uniform(1000, 1200)
    t = time_axis(n)
    squeak = glide_tone(f0 + 300.0 * np.clip((t - 0.1) / 0.4, 0.0, 1.0), harmonics=(1.0, 0.5))
    return y + bandpass(squeak, 700.0, 5000.0) * envelope(n, [(0.0, 0.0), (0.1, 0.0), (0.15, 0.1), (0.45, 0.06), (0.55, 0.0)])


def breaker_lever(rng, variant):
    """A heavy knife switch thrown: a spring's stretch and a big steel clack."""
    n = samples(0.35)
    y = _noise(n, rng, 2000.0, 7000.0) * envelope(n, [(0.0, 0.0), (0.02, 0.12), (0.07, 0.05), (0.08, 0.0)])
    return y + knock(n, rng, 0.08, rng.uniform(340, 420), [1.0, 2.4, 3.8], [1.0, 0.5, 0.25], [0.05, 0.03, 0.02], click=0.9, click_hi=6000.0)


def breaker_button(rng, variant):
    """A stiff industrial push button, down and up."""
    n = samples(0.25)
    y = knock(n, rng, 0.005, rng.uniform(900, 1100), [1.0, 2.1], [0.5, 0.2], [0.02, 0.012], click=0.6)
    return y + knock(n, rng, 0.12, rng.uniform(1100, 1300), [1.0, 2.1], [0.3, 0.1], [0.015, 0.01], click=0.4)


def breaker_count(rng, variant):
    """Relays counting, then the counter's dull buzz: not enough."""
    n = samples(0.65)
    t = time_axis(n)
    y = np.zeros(n)
    for k in range(3):
        y += knock(n, rng, 0.01 + k * 0.07, rng.uniform(1500, 1900), [1.0, 2.2], [0.25, 0.1], [0.012, 0.008], click=0.4, click_hi=8000.0)
    buzz = np.sign(np.sin(TAU * 100.0 * t))
    return y + lowpass(buzz, 1200.0) * 0.14 * envelope(n, [(0.0, 0.0), (0.26, 0.0), (0.3, 1.0), (0.55, 1.0), (0.62, 0.0)])


def breaker_power(rng, variant):
    """The power comes on: a contactor slammed in, and a mains hum swelling behind the wall."""
    n = samples(3.0)
    t = time_axis(n)
    y = knock(n, rng, 0.02, rng.uniform(150, 190), [1.0, 2.2, 3.6], [1.0, 0.5, 0.3], [0.15, 0.09, 0.05], click=0.9, click_hi=5000.0)
    hum = sum(a * np.sin(TAU * f * t) for f, a in ((50.0, 0.6), (100.0, 0.5), (150.0, 0.25), (200.0, 0.12)))
    return y + hum * 0.45 * envelope(n, [(0.0, 0.0), (0.1, 0.0), (1.0, 1.0), (2.2, 0.8), (3.0, 0.0)])


def lounge_blackout(rng, variant):
    """Every lamp at once: a heavy breaker dropping somewhere far below, and the building's hum dying."""
    n = samples(3.5)
    t = time_axis(n)
    y = knock(n, rng, 0.005, rng.uniform(60, 75), [1.0, 1.9, 3.1, 4.7], [1.0, 0.7, 0.4, 0.2], [0.5, 0.3, 0.15, 0.08], click=0.8, click_hi=2200.0)
    f = 100.0 - 70.0 * np.clip(t / 2.2, 0.0, 1.0)
    hum = glide_tone(f, harmonics=(1.0, 0.5, 0.3)) * envelope(n, [(0.0, 0.4), (0.1, 0.4), (2.4, 0.0)])
    return y + hum * 0.5


def big_door_open(rng, variant):
    """The big door: the lock's bolts, then a motor dragging tons of steel along a rail for 3 s, ending in a stop."""
    n = samples(3.6)
    t = time_axis(n)
    y = knock(n, rng, 0.01, rng.uniform(90, 110), [1.0, 2.1, 3.3], [1.0, 0.6, 0.3], [0.2, 0.12, 0.08], click=0.8, click_hi=2500.0)
    y += knock(n, rng, 0.18, rng.uniform(110, 130), [1.0, 2.3], [0.7, 0.3], [0.15, 0.08], click=0.5, click_hi=2500.0)
    f = 48.0 + 8.0 * smooth_random(n, 3.0, rng)
    motor = glide_tone(f, harmonics=(1.0, 0.7, 0.5, 0.3, 0.2)) * envelope(n, [(0.0, 0.0), (0.35, 0.0), (0.7, 0.5), (3.0, 0.5), (3.15, 0.0)])
    rail = _noise(n, rng, 300.0, 2200.0) * (0.5 + 0.5 * smooth_random(n, 14.0, rng)) * envelope(n, [(0.0, 0.0), (0.4, 0.0), (0.8, 0.35), (3.0, 0.3), (3.15, 0.0)])
    stop = knock(n, rng, 3.12, rng.uniform(70, 85), [1.0, 2.2, 3.5], [0.9, 0.5, 0.25], [0.3, 0.15, 0.08], click=0.6, click_hi=2000.0)
    return y + motor + rail + stop


def big_door_slam(rng, variant):
    """It shuts behind you, at once: an enormous steel boom, its ring dying along the corridor."""
    n = samples(4.0)
    ratios = sorted(rng.uniform(1.0, 7.0, 10))
    y = knock(n, rng, 0.005, rng.uniform(46, 56), ratios, [1.0 / (1 + i * 0.3) for i in range(10)], [1.3 / (1 + i * 0.4) for i in range(10)], click=1.0, click_hi=3000.0)
    return y + _noise(n, rng, 60.0, 800.0) * envelope(n, [(0.0, 0.0), (0.01, 0.9), (0.3, 0.2), (1.4, 0.0)])


def airlock_close(rng, variant):
    """Level 5's airlock, the door behind: a pneumatic hiss as its leaves slide shut over a second, then
    their seal thumping home."""
    n = samples(1.9)
    hiss = _noise(n, rng, 2500.0, 9000.0) * envelope(n, [(0.0, 0.0), (0.03, 0.8), (0.9, 0.5), (1.2, 0.0)])
    slide = _noise(n, rng, 120.0, 900.0) * (0.6 + 0.4 * smooth_random(n, 10.0, rng)) * envelope(n, [(0.0, 0.0), (0.1, 0.4), (1.1, 0.4), (1.2, 0.0)])
    thump = knock(n, rng, 1.2, rng.uniform(70, 90), [1.0, 2.3, 3.7], [1.0, 0.5, 0.25], [0.25, 0.12, 0.06], click=0.6, click_hi=2000.0)
    return hiss * 0.6 + slide * 0.5 + thump


def airlock_open(rng, variant):
    """Level 5's airlock, the way in: the seal letting go with a long sigh of air, then a motor sliding the
    leaves apart for three seconds, and a soft stop."""
    n = samples(4.0)
    release = knock(n, rng, 0.02, rng.uniform(110, 130), [1.0, 2.1], [0.8, 0.3], [0.15, 0.08], click=0.5, click_hi=3000.0)
    sigh = _noise(n, rng, 1800.0, 7000.0) * envelope(n, [(0.0, 0.0), (0.05, 0.9), (0.6, 0.6), (1.6, 0.15), (3.4, 0.0)])
    f = 62.0 + 6.0 * smooth_random(n, 2.0, rng)
    motor = glide_tone(f, harmonics=(1.0, 0.6, 0.4, 0.2)) * envelope(n, [(0.0, 0.0), (0.3, 0.0), (0.6, 0.4), (3.4, 0.4), (3.6, 0.0)])
    rail = _noise(n, rng, 250.0, 1600.0) * (0.5 + 0.5 * smooth_random(n, 12.0, rng)) * envelope(n, [(0.0, 0.0), (0.4, 0.0), (0.8, 0.25), (3.4, 0.2), (3.6, 0.0)])
    stop = knock(n, rng, 3.55, rng.uniform(80, 95), [1.0, 2.2], [0.6, 0.3], [0.2, 0.1], click=0.4, click_hi=2000.0)
    return release + sigh * 0.5 + motor + rail + stop


def wrench_hit(rng, variant):
    """Level 5's wrench landing: a heavy steel tool striking, a dull thud under a short metal ring."""
    n = samples(0.6)
    ratios = sorted(rng.uniform(1.0, 5.5, 6))
    ring = knock(n, rng, 0.003, rng.uniform(380, 520), ratios, [1.0 / (1 + i * 0.5) for i in range(6)],
                 [0.12 / (1 + i * 0.3) for i in range(6)], click=0.9, click_hi=6000.0)
    thud = knock(n, rng, 0.0, rng.uniform(70, 95), [1.0, 1.9], [1.0, 0.4], [0.06, 0.03], click=0.4, click_hi=1200.0)
    return ring * 0.6 + thud


def detector_beep(rng, variant):
    """Level 5's radiation detector: one short, slightly harsh electronic beep; the game repeats it faster
    as a Distortion Cloud comes near."""
    n = samples(0.09)
    return beep(n, 0.0, 1760.0, 0.06, 0.55) + beep(n, 0.0, 3520.0, 0.06, 0.12)


def lab_unlock(rng, variant):
    """Level 5: somewhere far off, a heavy lock lets go: a muffled bolt and the hiss of a door's seal."""
    n = samples(1.6)
    bolt = knock(n, rng, 0.02, rng.uniform(55, 70), [1.0, 2.1, 3.4], [1.0, 0.5, 0.2], [0.35, 0.18, 0.08], click=0.4, click_hi=900.0)
    hiss = _noise(n, rng, 600.0, 2400.0) * envelope(n, [(0.0, 0.0), (0.15, 0.0), (0.3, 0.35), (1.3, 0.0)])
    return bolt + hiss * 0.5


def safe_tick(rng, variant):
    """A safe's dial passing a number: a tiny dry tick, high and short."""
    n = samples(0.05)
    return knock(n, rng, 0.001, rng.uniform(3400, 4200), [1.0, 1.7], [0.35, 0.15], [0.006, 0.004], click=0.3, click_hi=9000.0)


def safe_click(rng, variant):
    """The right number: under the tick, a low clack of a tumbler dropping into place, felt more than heard."""
    n = samples(0.18)
    y = knock(n, rng, 0.001, rng.uniform(3000, 3400), [1.0, 1.7], [0.2, 0.1], [0.006, 0.004], click=0.25, click_hi=8000.0)
    return y + knock(n, rng, 0.004, rng.uniform(210, 250), [1.0, 2.3, 3.7], [1.0, 0.5, 0.25], [0.05, 0.03, 0.02], click=0.5, click_hi=2500.0)


def safe_open(rng, variant):
    """The safe giving way: bolts drawn with a heavy clunk, then the door's dry hinge."""
    n = samples(1.0)
    t = time_axis(n)
    y = knock(n, rng, 0.02, rng.uniform(160, 190), [1.0, 2.1, 3.4], [1.0, 0.5, 0.3], [0.12, 0.07, 0.04], click=0.7, click_hi=4000.0)
    y += knock(n, rng, 0.12, rng.uniform(220, 260), [1.0, 2.3], [0.7, 0.3], [0.08, 0.05], click=0.5, click_hi=3000.0)
    f0 = rng.uniform(520, 640)
    glide = np.clip((t - 0.3) / 0.6, 0.0, 1.0)
    squeak = glide_tone(f0 + f0 * 0.3 * glide + 18.0 * smooth_random(n, 30.0, rng), harmonics=(1.0, 0.5, 0.25))
    squeak *= envelope(n, [(0.0, 0.0), (0.3, 0.0), (0.36, 0.1), (0.85, 0.06), (0.95, 0.0)])
    return y + bandpass(squeak, 400.0, 4000.0)


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



# ---------------------------------------------------------------------------
# Level 5's Hunters and Distortions

VOWELS = [(300, 870, 2240), (360, 2000, 2550), (530, 1840, 2480), (640, 1190, 2390),
          (440, 1020, 2240), (730, 1090, 2440), (570, 840, 2410), (390, 1990, 2550)]


def whisper_voice(n, rng, rate=4.0, shift=1.0, density=0.75, hiss=0.4, start=0.0, end=None):
    """One whispering voice: breath through a vowel's formants, a syllable at a time (about `rate` a
    second, some left out, see `density`), now and then a sibilant hiss before one. `shift` scales the
    formants (a smaller or bigger throat). Nothing voiced: no pitch, only air."""
    y = np.zeros(n)
    t = start
    end = n / SR if end is None else end
    while t < end - 0.05:
        length = rng.uniform(0.6, 1.4) / rate
        if rng.random() < density:
            m = samples(length)
            vowel = VOWELS[rng.integers(len(VOWELS))]
            f1, f2, f3 = (f * shift * rng.uniform(0.93, 1.07) for f in vowel)
            air = rng.normal(0.0, 1.0, m)
            syllable = (bandpass(air, f1 * 0.8, f1 * 1.25, order=2) + 0.8 * bandpass(air, f2 * 0.88, f2 * 1.12, order=2)
                        + 0.45 * bandpass(air, f3 * 0.9, min(f3 * 1.1, 15000.0), order=2) + 0.15 * bandpass(air, 200.0, 600.0, order=2))
            syllable *= envelope(m, [(0.0, 0.0), (length * 0.18, 1.0), (length * 0.55, 0.7), (length, 0.0)])
            syllable *= rng.uniform(0.5, 1.0)
            place(y, syllable, t)
            if rng.random() < hiss:
                h = samples(rng.uniform(0.05, 0.12))
                sibilant = bandpass(rng.normal(0.0, 1.0, h), min(4200.0 * shift, 8000.0), 11000.0, order=2)
                sibilant *= envelope(h, [(0.0, 0.0), (h / SR * 0.3, 0.6), (h / SR, 0.0)])
                place(y, sibilant, max(0.0, t - h / SR * 0.8))
        t += length * rng.uniform(1.0, 1.35)
    return y


def stutter(y, rng, count, lo=0.03, hi=0.08):
    """Digital glitches: `count` short slices each repeated a few times over what follows them, and a
    stretch of it crushed to a few bits."""
    y = y.copy()
    for _ in range(count):
        m = samples(rng.uniform(lo, hi))
        at = int(rng.integers(0, max(1, len(y) - m * 6)))
        piece = y[at:at + m].copy()
        for k in range(1, int(rng.integers(3, 6))):
            if at + (k + 1) * m <= len(y):
                y[at + k * m:at + (k + 1) * m] = piece
    m = samples(rng.uniform(0.06, 0.14))
    at = int(rng.integers(0, max(1, len(y) - m)))
    held = np.repeat(y[at:at + m:12], 12)[:m]
    peak = max(1e-9, float(np.max(np.abs(held))))
    y[at:at + len(held)] = np.round(held / peak * 3.0) / 3.0 * peak
    return y


def static_crackle(n, rng, rate, lo=1500.0, hi=9000.0):
    """Static: noise gated on and off at random, about `rate` times a second."""
    gate = (smooth_random(n, rate, rng) > 0.35).astype(float)
    return bandpass(rng.normal(0.0, 1.0, n), lo, hi, order=2) * gate


def cloud_whisper(rng, variant):
    """The Distortion Cloud, all the while it is there: many whispering voices at once, none of them
    clear, over a low dark breath, broken up now and then by digital glitches."""
    loop, extra = 6.0, 0.5
    n = samples(loop + extra)
    voices = sum(whisper_voice(n, rng, rate=rng.uniform(3.5, 5.5), shift=s, density=0.7) * g
                 for s, g in ((0.82, 1.0), (0.95, 0.8), (1.08, 0.9), (1.22, 0.6), (0.9, 0.5)))
    voices *= 0.8 + 0.2 * smooth_random(n, 0.7, rng)
    breath = lowpass(rng.normal(0.0, 1.0, n), 140.0) * 6.0 * (0.7 + 0.3 * smooth_random(n, 0.4, rng))
    y = stutter(voices, rng, 4) + breath * 0.5 + static_crackle(n, rng, 3.0) * 0.08
    return crossfade_loop(y, extra)


def cloud_appear(rng, variant):
    """A Distortion Cloud there all at once: a burst of static, a breath drawn in backwards that stops
    short, and a low thud."""
    n = samples(1.3)
    crackle = static_crackle(n, rng, 40.0, 800.0, 10000.0) * envelope(n, [(0.0, 0.0), (0.02, 1.0), (0.35, 0.5), (0.7, 0.0)])
    inhale = whisper_voice(n, rng, rate=6.0, shift=0.9, density=1.0, hiss=0.8, end=0.9)[::-1].copy()
    inhale *= envelope(n, [(0.0, 0.0), (0.4, 0.2), (1.1, 1.0), (1.15, 0.0)])
    thud = knock(n, rng, 0.0, rng.uniform(45, 60), [1.0, 1.7], [1.0, 0.4], [0.3, 0.15], click=0.3, click_hi=800.0)
    return stutter(crackle * 0.6 + inhale * 1.4, rng, 2) + drive(thud, 2.0) * 0.8


def cloud_kill(rng, variant):
    """Looking at a Distortion Cloud too long: its whispers rush in from everywhere, higher and harsher,
    torn by glitches into a screech, then cut dead over a low blow."""
    n = samples(2.2)
    y = np.zeros(n)
    for k in range(6):
        start = rng.uniform(0.0, 0.3)
        voice = whisper_voice(n, rng, rate=rng.uniform(7.0, 10.0), shift=0.9 + 0.15 * k, density=1.0, hiss=0.6, start=start, end=1.8)
        y += voice * envelope(n, [(0.0, 0.2), (1.2, 1.0), (1.75, 1.0), (1.8, 0.0)])
    f = np.interp(time_axis(n), [0.0, 1.0, 1.8], [500.0, 1400.0, 3200.0])
    screech = fft_filter(rng.normal(0.0, 1.0, n), 300.0, 6000.0)
    screech = resonator(screech, 1800.0, 2.0) * 0.3 + glide_tone(f, harmonics=(0.4, 0.3, 0.25, 0.2, 0.1)) * 0.3
    screech *= envelope(n, [(0.0, 0.0), (0.6, 0.1), (1.6, 1.0), (1.8, 0.0)])
    blow = knock(n, rng, 1.8, rng.uniform(38, 48), [1.0, 1.6, 2.4], [1.0, 0.5, 0.25], [0.4, 0.2, 0.1], click=0.8, click_hi=1500.0)
    return drive(stutter(y + screech, rng, 6), 2.5) * 0.8 + drive(blow, 2.0)


def cloud_leave(rng, variant):
    """A Distortion Cloud going: its whispers sinking away and thinning into static that dies out."""
    n = samples(1.4)
    voices = whisper_voice(n, rng, rate=5.0, shift=0.8, density=0.9, end=1.0) + whisper_voice(n, rng, rate=4.0, shift=0.7, density=0.8, end=1.0)
    voices = lowpass(voices, 1800.0) * envelope(n, [(0.0, 1.0), (0.9, 0.0)])
    crackle = static_crackle(n, rng, 20.0) * envelope(n, [(0.0, 0.4), (0.3, 0.6), (1.3, 0.0)])
    return voices + crackle * 0.3


def junior_whisper(rng, variant):
    """A Distortion Junior: a small, quick whisper, higher than a Cloud's, and little ticks of glitch."""
    loop, extra = 4.0, 0.4
    n = samples(loop + extra)
    voices = whisper_voice(n, rng, rate=6.0, shift=1.35, density=0.6) + 0.6 * whisper_voice(n, rng, rate=5.0, shift=1.5, density=0.5)
    ticks = static_crackle(n, rng, 6.0, 3000.0, 12000.0) * 0.05
    return crossfade_loop(stutter(voices, rng, 2, 0.02, 0.04) + ticks, extra)


def junior_stare(rng, variant):
    """Staring a Distortion Junior down: a thin, glassy hum, like a wet finger round a glass, that the game
    raises in pitch as the stare goes on and its eye cracks."""
    loop = 2.0
    n = samples(loop)
    t = time_axis(n)
    f = loop_frequency(660.0, loop)
    beat = loop_frequency(1.5, loop)
    tone = np.sin(TAU * f * t) + 0.35 * np.sin(TAU * 2 * f * t) + 0.12 * np.sin(TAU * 3 * f * t)
    tone *= 0.75 + 0.25 * np.sin(TAU * beat * t)
    rub = shaped_noise(n, rng, lo=1800.0, hi=5000.0) * 0.08
    return tone * 0.6 + rub


def junior_die(rng, variant):
    """A Distortion Junior ending: its eye cracking like glass, a soft pop, and a last whisper sighing out."""
    n = samples(1.4)
    ratios = sorted(rng.uniform(1.0, 6.0, 7))
    crack = knock(n, rng, 0.0, rng.uniform(1900, 2400), ratios, [1.0 / (1 + i * 0.4) for i in range(7)],
                  [0.05 / (1 + i * 0.2) for i in range(7)], click=1.0, click_hi=12000.0)
    crack += knock(n, rng, 0.07, rng.uniform(2600, 3100), ratios[:4], [0.6, 0.4, 0.3, 0.2], [0.03, 0.02, 0.02, 0.01], click=0.8, click_hi=12000.0)
    pop = burst(n, rng, 0.12, 0.05, lo=80.0, hi=900.0, tau=0.02, amp=1.2)
    sigh = whisper_voice(n, rng, rate=3.0, shift=1.3, density=1.0, hiss=0.0, start=0.15, end=1.2)
    sigh = lowpass(sigh, 2500.0) * envelope(n, [(0.0, 0.0), (0.2, 1.0), (1.3, 0.0)])
    return crack * 0.7 + pop + sigh * 0.8


def hunter_shriek(rng, variant):
    """The Hunters spotting someone: a short, rasping, child-high shriek, rising then cracking."""
    n = samples(rng.uniform(0.7, 0.9))
    t = time_axis(n)
    f0 = rng.uniform(820, 980)
    f = f0 * np.interp(t, [0.0, 0.15, 0.6, t[-1]], [0.8, 1.15, 1.25, 0.9]) * (1 + 0.03 * np.sin(TAU * 13.0 * t)) * (1 + 0.01 * smooth_random(n, 40.0, rng))
    voice = glide_tone(f, harmonics=(1.0, 0.7, 0.5, 0.35, 0.25, 0.15))
    voice = bandpass(voice, 700.0, 4500.0, order=2) + 0.5 * resonator(voice, 2300.0, 4.0)
    rasp = bandpass(rng.normal(0.0, 1.0, n), 1500.0, 6000.0, order=2) * 0.35
    return drive((voice + rasp) * envelope(n, [(0.0, 0.0), (0.03, 1.0), (0.5, 0.8), (t[-1], 0.0)]), 3.0)


def hunter_attack(rng, variant):
    """A Hunter's swipe: a quick swish of a coat sleeve and a dry scratch."""
    n = samples(0.35)
    swish = bandpass(rng.normal(0.0, 1.0, n), 500.0, 3500.0, order=2) * envelope(n, [(0.0, 0.0), (0.08, 0.8), (0.16, 0.0)])
    scratch = bandpass(rng.normal(0.0, 1.0, n), 1800.0, 7000.0, order=2) * (smooth_random(n, 80.0, rng) > 0.1)
    scratch *= envelope(n, [(0.0, 0.0), (0.12, 0.0), (0.14, 1.0), (0.3, 0.0)])
    return swish * 0.7 + scratch * 0.6


def hunter_death(rng, variant):
    """A Hunter struck down: a short wet crunch and a choked squeal falling away."""
    n = samples(0.8)
    t = time_axis(n)
    crunch = burst(n, rng, 0.0, 0.08, lo=200.0, hi=3000.0, tau=0.03, amp=1.2)
    crunch += knock(n, rng, 0.0, rng.uniform(140, 180), [1.0, 2.2], [0.8, 0.3], [0.08, 0.04], click=0.6)
    f = rng.uniform(700, 850) * np.interp(t, [0.0, 0.5], [1.1, 0.45])
    squeal = glide_tone(f, harmonics=(1.0, 0.5, 0.3, 0.2)) * envelope(n, [(0.0, 0.0), (0.04, 0.7), (0.4, 0.3), (0.6, 0.0)])
    return crunch + drive(bandpass(squeal, 400.0, 4000.0, order=2), 2.0) * 0.6


def hunter_ambient(rng, variant):
    """A Hunter idling: quick wet clicks of the tongue and a thin wheeze, as if muttering to itself."""
    n = samples(1.6)
    y = np.zeros(n)
    t = rng.uniform(0.05, 0.2)
    while t < 1.2:
        y += knock(n, rng, t, rng.uniform(1800, 2800), [1.0, 1.6], [0.4, 0.2], [0.012, 0.008], click=0.3, click_hi=6000.0)
        t += rng.uniform(0.06, 0.2)
    wheeze = whisper_voice(n, rng, rate=3.0, shift=1.6, density=1.0, hiss=0.0, start=0.2, end=1.4) * 0.5
    return y + wheeze


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
    Sound("block.projector.run", "Level progress", "Level 4's projector while the film runs, a seamless loop", "mono", True, 1,
          projector_run, -6.0, attenuation=28),
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
    Sound("event.collapse", "Level progress", "Level 3: the bunker tunnel's ceiling coming down behind the player (groan, cracks, slabs, rubble)",
          "stereo", False, 1, bunker_collapse, -1.0),
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
    Sound("music.lounge.far", "Level progress", "Level 4's jazz in the Calm room, as from far away (music.lounge, filtered)", "mono", False, 1,
          lounge_far, -6.0, stream=True, attenuation=64),
    Sound("event.puzzle.solved", "Level progress", "A Level 4 puzzle solved", "mono", False, 1, puzzle_solved, -6.0, attenuation=32),
    Sound("block.record.handle", "Level progress", "A record taken from its rack, or put back", "mono", False, 3, record_handle, -8.0),
    Sound("block.record.right", "Level progress", "A record set on the player, the right one", "mono", False, 2, record_right, -6.0),
    Sound("block.record.wrong", "Level progress", "The wrong record: the needle dragged", "mono", False, 2, record_wrong, -5.0),
    Sound("block.reel.handle", "Level progress", "A film can taken or put back", "mono", False, 3, reel_handle, -8.0),
    Sound("block.reel.wrong", "Level progress", "The wrong reel, refused", "mono", False, 2, reel_wrong, -5.0),
    Sound("item.dart.throw", "Level progress", "A dart thrown", "mono", False, 3, dart_throw, -12.0),
    Sound("block.dart.hit", "Level progress", "A dart sticking in something", "mono", False, 3, dart_hit, -6.0),
    Sound("event.dart.right", "Level progress", "The right number", "mono", False, 1, dart_right, -8.0),
    Sound("event.dart.wrong", "Level progress", "The wrong number", "mono", False, 1, dart_wrong, -7.0),
    Sound("block.dart_hatch.open", "Level progress", "The brass hatch under the board", "mono", False, 1, hatch_open, -6.0),
    Sound("block.breaker.lever", "Level progress", "A Maintenance room lever", "mono", False, 3, breaker_lever, -5.0),
    Sound("block.breaker.button", "Level progress", "The breakers' button", "mono", False, 2, breaker_button, -8.0),
    Sound("event.breaker.count", "Level progress", "The counter shows a number: not five", "mono", False, 1, breaker_count, -7.0),
    Sound("event.breaker.power", "Level progress", "Five: the power comes on", "mono", False, 1, breaker_power, -3.0, attenuation=32),
    Sound("event.lounge.blackout", "Level progress", "Taking the Master Key: every lamp at once", "mono", False, 1, lounge_blackout, -1.0, attenuation=48),
    Sound("block.big_door.open", "Level progress", "The big door opening (lock, motor, rail, stop)", "mono", False, 1, big_door_open, -2.0, attenuation=40),
    Sound("block.big_door.slam", "Level progress", "The big door slamming behind the player", "mono", False, 1, big_door_slam, -1.0, attenuation=48),
    Sound("block.airlock.close", "Level progress", "Level 5's airlock: the door behind shuts with a hiss of air", "mono", False, 1, airlock_close, -3.0, attenuation=24),
    Sound("block.airlock.open", "Level progress", "Level 5's airlock: the way in opens slowly, air sighing out", "mono", False, 1, airlock_open, -3.0, attenuation=24),
    Sound("item.wrench.hit", "Level progress", "Level 5: the wrench landing a blow, metal on something", "mono", False, 3, wrench_hit, -3.0, attenuation=16),
    Sound("item.detector.beep", "Level progress", "Level 5: the radiation detector's beep, repeated faster as the Cloud nears", "mono", False, 1, detector_beep, -6.0),
    Sound("event.lab.unlock", "Level progress", "Level 5: a lock letting go far away (the south route opens)", "mono", False, 1, lab_unlock, -4.0),
    Sound("entity.hunter.shriek", "Level 5 / Hunters", "A group of Hunters spotting someone: a rasping child-high shriek", "mono", False, 3, hunter_shriek, -2.0, attenuation=32),
    Sound("entity.hunter.attack", "Level 5 / Hunters", "A Hunter's swipe: a sleeve's swish and a scratch", "mono", False, 3, hunter_attack, -4.0),
    Sound("entity.hunter.death", "Level 5 / Hunters", "A Hunter struck down: a wet crunch, a choked squeal", "mono", False, 3, hunter_death, -3.0),
    Sound("entity.hunter.ambient", "Level 5 / Hunters", "A Hunter idling: tongue clicks and a thin wheeze", "mono", False, 3, hunter_ambient, -8.0, attenuation=12),
    Sound("entity.distortion_cloud.whisper", "Level 5 / Distortions", "The Distortion Cloud while it is there: many whispers, glitching (loop)", "mono", True, 1, cloud_whisper, -4.0,
          stream=False, attenuation=24),
    Sound("entity.distortion_cloud.appear", "Level 5 / Distortions", "A Distortion Cloud there all at once: static, a breath drawn in backwards", "mono", False, 1, cloud_appear, -2.0, attenuation=32),
    Sound("entity.distortion_cloud.kill", "Level 5 / Distortions", "Looking at the Cloud too long: whispers rushing into a screech, cut dead", "mono", False, 1, cloud_kill, -1.0, attenuation=32),
    Sound("entity.distortion_cloud.leave", "Level 5 / Distortions", "A Distortion Cloud going: whispers sinking into dying static", "mono", False, 1, cloud_leave, -4.0, attenuation=24),
    Sound("entity.distortion_junior.whisper", "Level 5 / Distortions", "A Distortion Junior: a small quick whisper (loop)", "mono", True, 1, junior_whisper, -6.0,
          stream=False, attenuation=16),
    Sound("entity.distortion_junior.stare", "Level 5 / Distortions", "Staring a Junior down: a glassy hum, raised in pitch by the game (loop)", "mono", True, 1, junior_stare, -6.0,
          stream=False, attenuation=16),
    Sound("entity.distortion_junior.die", "Level 5 / Distortions", "A Junior ending: its eye cracking like glass, a pop, a last sigh", "mono", False, 1, junior_die, -3.0, attenuation=24),
    Sound("block.safe.tick", "Level progress", "A safe's dial passing a number: the soft tick", "mono", False, 3, safe_tick, -18.0),
    Sound("block.safe.click", "Level progress", "A safe's dial on the right number: a low clack under the tick", "mono", False, 2, safe_click, -6.0),
    Sound("block.safe.open", "Level progress", "A safe giving way (bolts, hinge)", "mono", False, 1, safe_open, -4.0),
    Sound("block.shutter.close", "Level progress", "The rolling shutter closing behind the player (rattle down, slam)", "mono", False, 1,
          shutter_close, -1.0, attenuation=32),
    Sound("event.level_transition", "Level progress", "Going from Level 1 to Level 2", "stereo", False, 1, level_transition, -1.0),
    Sound("event.death", "Level progress", "Death (1: short, 2: with collapse)", "stereo", False, 2, death, -1.0),
    Sound("event.death.reveal", "Level progress", "The death screen: one low dull blow as \"Killed by\" comes up on the black", "stereo", False, 1, death_reveal, -2.0),
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
