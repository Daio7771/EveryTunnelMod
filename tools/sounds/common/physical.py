"""
Physically modelled pieces for sounds that should not sound synthetic (Chapter 2 Level 2's creature and pump first).

Instead of filtered noise and plain tones, each sound is built the way it happens:
- modes(): a struck body rings in many inharmonic modes, each slightly detuned and damped on its own; a soft contact (a fist,
  a rubber sole) rolls off the high modes.
- body_ir() + ring(): a body's impulse response, and any excitation (a stream of impacts, a scrape) rung through it, so
  rolling, rattling and creaking excite the same body as the main blow.
- grains(): friction and crumbling as a Poisson stream of tiny resonant impacts (stone grinding, grit, debris, rattle),
  their sizes spread lognormally like real fragments.
- bubbles(): water as bubbles, each a sine at its Minnaert frequency rising as it nears the surface (van den Doel's model).
- room(): a hard room's answer: a few early reflections and a short dense tail. Short on purpose: in game the sound is also
  placed by Minecraft (and, for the user, Sound Physics Remastered).

All functions take n (samples) and a numpy Generator; times are seconds. Used by catalog_ch2.py.
"""

import numpy as np
from scipy.signal import fftconvolve

import sys as _sys
from pathlib import Path as _Path
_sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))
import toolpaths  # noqa: E402  (where each area keeps its common/chapter folders)
toolpaths.use('sounds')
from dsp import SR, bandpass, lowpass, samples, time_axis


def _rate_curve(n, rate):
    """A rate (events a second) as one value per sample: a number, or (seconds, rate) points."""
    if isinstance(rate, (int, float)):
        return np.full(n, float(rate))
    times, values = zip(*rate)
    return np.interp(time_axis(n), times, values)


def modes(n, rng, start, freqs, amps, decays, detune=0.004, contact=0.0006):
    """A struck body: one damped sine per mode (frequency in Hz, decay time in seconds), detuned by about `detune`, random
    phases; a contact lasting `contact` seconds softens the modes above about 1/contact Hz."""
    out = np.zeros(n)
    s0 = samples(start)
    if s0 >= n:
        return out
    t = time_axis(n - s0)
    y = np.zeros(n - s0)
    for f, a, d in zip(freqs, amps, decays):
        f = f * (1.0 + rng.normal(0.0, detune))
        if f >= SR / 2:
            continue
        soft = 1.0 / (1.0 + (f * contact) ** 2)
        y += a * soft * np.sin(2 * np.pi * f * t + rng.uniform(0, 2 * np.pi)) * np.exp(-t / d)
    y *= 1.0 - np.exp(-t / 0.0004)
    out[s0:] = y
    return out


def stone_modes(rng, count, lo, hi, decay=(0.008, 0.04)):
    """Modes of a rough lump of stone: dense, inharmonic, quickly damped; amplitudes falling with frequency."""
    freqs = np.sort(np.exp(rng.uniform(np.log(lo), np.log(hi), count)))
    amps = rng.lognormal(0.0, 0.4, count) * (lo / freqs) ** 0.5
    decays = rng.uniform(*decay, count) * (lo / freqs) ** 0.3
    return freqs, amps, decays


def body_ir(rng, freqs, amps, decays, seconds, detune=0.004):
    """The impulse response of a body with these modes (for ring())."""
    return modes(samples(seconds), rng, 0.0, freqs, amps, decays, detune=detune, contact=0.0)


def ring(excitation, ir):
    """The excitation rung through a body (convolution), cut to the excitation's length."""
    return fftconvolve(excitation, ir)[: len(excitation)]


def impulses(n, rng, rate, amp_sigma=0.7):
    """A Poisson stream of single-sample impacts at `rate` a second (a number or (seconds, rate) points), sizes lognormal."""
    p = np.clip(_rate_curve(n, rate) / SR, 0.0, 1.0)
    hits = rng.random(n) < p
    out = np.zeros(n)
    out[hits] = rng.lognormal(0.0, amp_sigma, hits.sum()) * rng.choice([-1.0, 1.0], hits.sum())
    return out


def grains(n, rng, rate, lo, hi, length=(0.002, 0.012), amp_sigma=0.7):
    """Friction or crumbling: tiny impacts at `rate` a second, each a short decaying ring at a random frequency between lo and
    hi Hz (log-uniform), sizes lognormal."""
    p = np.clip(_rate_curve(n, rate) / SR, 0.0, 1.0)
    where = np.flatnonzero(rng.random(n) < p)
    out = np.zeros(n)
    for i in where:
        length_i = samples(rng.uniform(*length))
        end = min(n, i + length_i)
        t = np.arange(end - i) / SR
        f = np.exp(rng.uniform(np.log(lo), np.log(hi)))
        out[i:end] += rng.lognormal(0.0, amp_sigma) * np.sin(2 * np.pi * f * t) * np.exp(-t / (length_i / SR / 5.0))
    return out


def bubbles(n, rng, rate, radius=(0.6e-3, 5e-3), rise=0.1):
    """Water: bubbles at `rate` a second, radius log-uniform in metres; each sings at its Minnaert frequency (about 3.26 / r Hz),
    rising by `rise` per decay as it nears the surface, and dies at van den Doel's rate (bigger bubbles ring longer and louder)."""
    p = np.clip(_rate_curve(n, rate) / SR, 0.0, 1.0)
    where = np.flatnonzero(rng.random(n) < p)
    out = np.zeros(n)
    for i in where:
        r = np.exp(rng.uniform(np.log(radius[0]), np.log(radius[1])))
        f0 = 3.26 / r
        if f0 >= SR / 2:
            continue
        damping = 0.13 * f0 + 0.0072 * f0 ** 1.5
        length = min(n - i, samples(6.0 / damping))
        t = np.arange(length) / SR
        phase = 2 * np.pi * f0 * (t + 0.5 * rise * damping * t * t)
        out[i:i + length] += (r / radius[1]) ** 0.5 * np.sin(phase) * np.exp(-damping * t)
    return out


def scrape(n, rng, lo, hi, flutter=25.0):
    """A turbulent, rough noise (air, water, grinding) whose band and level flutter like a real flow."""
    y = bandpass(rng.standard_normal(n), lo, hi, order=2)
    wobble = lowpass(rng.standard_normal(n), flutter, order=2)
    wobble /= np.max(np.abs(wobble)) + 1e-9
    return y * (0.6 + 0.4 * wobble)


def room(y, rng, size=1.0, tail=0.35, wet=0.22, dark=3500.0):
    """A hard room's answer: early reflections at 7-45 ms (times `size`) and a dense tail of `tail` seconds, darkened above
    `dark` Hz, mixed in at `wet`. The output keeps y's length."""
    n_ir = samples(tail + 0.05 * size)
    ir = np.zeros(n_ir)
    for _ in range(7):
        k = samples(rng.uniform(0.007, 0.045) * size)
        if k < n_ir:
            ir[k] += rng.uniform(0.25, 0.6) * rng.choice([-1.0, 1.0])
    t = time_axis(n_ir)
    late = rng.standard_normal(n_ir) * np.exp(-t / (tail / 6.9)) * (1.0 - np.exp(-t / (0.02 * size)))
    ir += 0.5 * lowpass(late, dark, order=2)
    wet_y = fftconvolve(y, ir)[: len(y)]
    wet_y *= np.max(np.abs(y)) / (np.max(np.abs(wet_y)) + 1e-9)
    return (1.0 - wet) * y + wet * wet_y
