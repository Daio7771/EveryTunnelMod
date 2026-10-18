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
        tone = glide_tone(pitch * (f_lo + (f_hi - f_lo) * np.exp(-tt / 0.035)), (1.0, 0.35, 0.1))
        y[s0:] += amp * tone * (1.0 - np.exp(-tt / 0.007)) * np.exp(-tt / tau)
    y += burst(n, rng, 0.0, 0.14, hi=180.0, tau=0.045, amp=0.4)
    y += burst(n, rng, s2, 0.1, hi=260.0, tau=0.03, amp=0.25)
    y += burst(n, rng, s2, 0.015, lo=120.0, hi=500.0, tau=0.006, amp=0.12)
    flow = lowpass(rng.standard_normal(n), 220.0, order=2)
    y += 0.06 * flow * envelope(n, [(0.0, 0.0), (0.06, 0.0), (0.12, 1.0), (0.24, 0.0), (length, 0.0)])
    y = lowpass(y, 900.0, order=2)
    return fade(drive(y * 1.4, 2.2), 0.002, 0.04)


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


def tension(rng, variant):
    return (tension_strings, tension_rising, tension_pulse)[variant](rng)


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
    Sound("music.tension", "Level 2 / Pale man", "Tension layers for different moments: 1 dissonant strings, 2 endless rising tone, 3 low pulse", "stereo", True, 3, tension, -3.0,
          "All three are kept; the game picks one per situation (e.g. pulse when far, rising tone while he approaches, strings when close)."),
    Sound("music.chase", "Level 2 / Pale man", "During a chase (120 bpm, 8 bars)", "stereo", True, 1, chase, -1.0),
    Sound("event.level_transition", "Level progress", "Going from Level 1 to Level 2", "stereo", False, 1, level_transition, -1.0),
    Sound("event.death", "Level progress", "Death (1: short, 2: with collapse)", "stereo", False, 2, death, -1.0),
]
