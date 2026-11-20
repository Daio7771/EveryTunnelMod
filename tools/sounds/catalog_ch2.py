"""
Chapter 2's Python-generated sounds (its prologue and its Level 1), apart from the base game's (`catalog.py`), with the same
tools and the same rules: one generator per sound, taking (rng, variant) and returning a mono or stereo array, seeded from
the sound id and the variant. `catalog.py` adds [CHAPTER2_SOUNDS] to its list, so `generate.py` and `export.py` see one
catalog. A later chapter gets a file like this one.
"""

from catalog import *  # noqa: F401,F403  (the base catalog's Sound and its synthesis helpers)
from catalog import _noise, stinger_heard  # noqa: E402
import physical as ph  # noqa: E402
import menu_piano as mp  # noqa: E402  (the felt piano of the main menu, for the lament)


def prologue_crack(rng, variant):
    """Chapter 2's prologue: a stage of the scientist's change, the sound of a body giving way: a run of dry cracks a few
    hundredths of a second apart (joints, like knuckles or green wood, each a sharp click with a short hollow ring), a dull
    wet thump under the first, and the thin creak of something stretched too far, rising and breaking off."""
    n = samples(1.1)
    t = time_axis(n)
    y = np.zeros(n)
    at = 0.0
    for _ in range(int(rng.integers(3, 6))):
        at += rng.uniform(0.025, 0.12)
        base = rng.uniform(550.0, 1500.0)
        y += rng.uniform(0.6, 1.0) * knock(n, rng, at, base, [1.0, 1.9, 2.7], [1.0, 0.5, 0.25], [0.012, 0.007, 0.004], click=1.0, click_hi=9000.0)
    y += 0.55 * glide_tone(52.0 + 26.0 * np.exp(-t / 0.09)) * np.exp(-t / 0.13)
    creak = bandpass(rng.standard_normal(n), 1300.0, 3400.0, order=2)
    creak *= envelope(n, [(0.0, 0.0), (0.12, 0.0), (0.4, 0.18), (0.62, 0.0), (1.1, 0.0)]) * (0.6 + 0.4 * np.sin(TAU * rng.uniform(38.0, 60.0) * t))
    y += creak
    return fade(drive(y / np.max(np.abs(y)), 1.2), 0.001, 0.25)


def prologue_title_hit(rng, variant):
    """Chapter 2's title landing on the black: one low blow, a sub tone dropping from 62 to 31 Hz, a dark detuned cluster
    swelling out of it and dying over a couple of seconds, a dull thump at the front and a rumble under the tail.
    Nothing high in it: it should be felt more than heard."""
    n = samples(5.0)
    t = time_axis(n)
    sub = glide_tone(31.0 + 31.0 * np.exp(-t / 0.18)) * np.exp(-t / 1.1)
    swell = ((1.0 - np.exp(-t / 0.12)) * np.exp(-t / 1.7))[:, None]
    cluster = lowpass(detuned_cluster(t, [41.2, 43.7, 61.7, 65.4], rng), 420.0, order=2) * swell
    thump = burst(n, rng, 0.0, 0.3, hi=500.0, tau=0.07, amp=0.9)
    tail = np.exp(-t / 1.9)
    left = sub + thump + 0.4 * shaped_noise(n, rng, exponent=2.0, lo=20.0, hi=130.0) * tail
    right = sub + 0.9 * thump + 0.4 * shaped_noise(n, rng, exponent=2.0, lo=20.0, hi=130.0) * tail
    left, right = left[:, None], right[:, None]
    y = 0.5 * cluster + np.concatenate([left, right], axis=1)
    return fade(drive(y, 1.4), 0.002, 0.8)


def prologue_hum(rng, variant):
    """Chapter 2's prologue: a clean modern laboratory's hum, the same everywhere in the shot: the lights' mains hum at
    100 Hz with its harmonics and a faint high buzz, a fridge's low tone, and the ventilation's soft hiss. Quieter and
    cleaner than Level 5's air; nothing moves in it. Loops: its tones have whole cycles in the loop, the noise is
    built circularly."""
    seconds = 10.0
    n = samples(seconds)
    t = time_axis(n)
    sides = []
    for _ in range(2):
        mains = 0.5 * mains_hum(t, 100.0, rng, harmonics=14, tilt=1.6)
        buzz = np.sin(TAU * loop_frequency(rng.uniform(5200.0, 5600.0), seconds) * t + rng.uniform(0, TAU))
        buzz *= 0.012 * (0.6 + 0.4 * smooth_random(n, 0.3, rng, periodic=True))
        fridge = 0.12 * np.sin(TAU * loop_frequency(rng.uniform(48.0, 52.0), seconds) * t + rng.uniform(0, TAU))
        hiss = 0.05 * shaped_noise(n, rng, exponent=0.8, lo=700.0, hi=7000.0, order=2)
        sides.append(mains * (1.0 + 0.02 * smooth_random(n, 0.4, rng, periodic=True)) + buzz + fridge + hiss)
    return stereo(sides[0], sides[1])


def prologue_drone(rng, variant):
    """Chapter 2's prologue: the tension under the shot, a low drone that never resolves: two tones a semitone apart
    beating slowly (55 and 58.3 Hz) over a fifth and a tritone above them, a dark rumble, and a thin high whine that
    drifts in and out. Plays quietly and rises with the scene (the game sets its volume). Loops: whole cycles."""
    seconds = 40.0
    n = samples(seconds)
    t = time_axis(n)
    tones = ((55.0, 1.0, -0.2), (58.27, 0.8, 0.2), (82.41, 0.35, 0.3), (77.78, 0.3, -0.3), (110.0, 0.12, 0.0))
    y = np.zeros((n, 2))
    for freq, level, position in tones:
        f = loop_frequency(freq, seconds)
        wobble = 1.0 + 0.25 * smooth_random(n, 0.08, rng, periodic=True)
        tone = (np.sin(TAU * f * t + rng.uniform(0, TAU)) + 0.25 * np.sin(TAU * 2 * f * t + rng.uniform(0, TAU))) * wobble
        y += pan(level * tone, position)
    rumble = shaped_noise(n, rng, exponent=2.0, lo=20.0, hi=120.0)
    y += stereo(0.5 * rumble, 0.5 * shaped_noise(n, rng, exponent=2.0, lo=20.0, hi=120.0))
    whine = np.sin(TAU * loop_frequency(rng.uniform(3300.0, 3700.0), seconds) * t + rng.uniform(0, TAU))
    whine *= 0.012 * np.clip(smooth_random(n, 0.05, rng, periodic=True), 0.0, None)
    y += stereo(whine, whine[::-1])
    return y


def ch2l1_ding(rng, variant):
    """The loudspeaker's chime before an announcement (Chapter 2 Level 1): two soft bell notes, the second a third lower."""
    n = samples(1.7)
    ratios, amps = [1.0, 2.0, 3.01, 4.2], [1.0, 0.45, 0.2, 0.08]
    y = knock(n, rng, 0.0, 880.0, ratios, amps, [0.5, 0.3, 0.2, 0.1], click=0.12, click_hi=6000.0)
    y += 0.9 * knock(n, rng, 0.45, 698.5, ratios, amps, [0.7, 0.4, 0.25, 0.12], click=0.12, click_hi=6000.0)
    return lowpass(y, 7000.0)


def ch2l1_sweep(rng, variant):
    """A broom drawn over a floor: a soft grainy swish of bristles with a little grit under it."""
    n = samples(0.6)
    y = _noise(n, rng, 1500.0, 7000.0) * envelope(n, [(0.0, 0.0), (0.08, 0.7), (0.3, 1.0), (0.55, 0.0)])
    y += 0.4 * _noise(n, rng, 300.0, 1200.0) * envelope(n, [(0.0, 0.0), (0.12, 0.6), (0.35, 0.4), (0.58, 0.0)])
    return 0.5 * y


def ch2l1_blip(rng, variant):
    """A small electronic pip of a lab instrument (a sample taken, a calibration step); the game pitches it up as it goes."""
    n = samples(0.14)
    return damped_partials(n, [880.0, 1760.0], [1.0, 0.2], [0.05, 0.03], rng, 0.0)


def ch2l1_keypad_key(rng, variant):
    """A key of the office's keypad pressed: a short square-ish beep over the plastic's tick."""
    n = samples(0.09)
    y = damped_partials(n, [1320.0, 2640.0, 3960.0], [1.0, 0.3, 0.15], [0.04, 0.03, 0.02], rng, 0.0)
    return y + 0.3 * _noise(n, rng, 2500.0, 8000.0) * envelope(n, [(0.0, 1.0), (0.012, 0.0)])


def ch2l1_keypad_error(rng, variant):
    """A wrong code: a low double buzz."""
    n = samples(0.5)
    t = np.arange(n) / SR
    tone = np.sign(np.sin(2 * np.pi * 180.0 * t)) * 0.5 + 0.5 * np.sin(2 * np.pi * 360.0 * t)
    gate = envelope(n, [(0.0, 0.0), (0.01, 1.0), (0.18, 1.0), (0.2, 0.0), (0.26, 0.0), (0.27, 1.0), (0.46, 1.0), (0.5, 0.0)])
    return lowpass(0.4 * tone * gate, 3000.0)


def ch2l1_keypad_accept(rng, variant):
    """The right code: two rising clean beeps and the lock's soft clack."""
    n = samples(0.6)
    y = damped_partials(n, [1046.5, 2093.0], [1.0, 0.25], [0.12, 0.08], rng, 0.0)
    y += damped_partials(n, [1568.0, 3136.0], [1.0, 0.25], [0.18, 0.1], rng, 0.13)
    y += 0.6 * _noise(n, rng, 200.0, 1800.0) * envelope(n, [(0.0, 0.0), (0.34, 0.0), (0.345, 1.0), (0.4, 0.0)])
    return 0.6 * y


def ch2l1_guard_suspect(rng, variant):
    """A guard has noticed something (Act 3's patrols): a soft questioning two-note rise, as a "?" over his head."""
    n = samples(0.5)
    y = damped_partials(n, [660.0, 1320.0], [1.0, 0.2], [0.12, 0.06], rng, 0.0)
    y += damped_partials(n, [880.0, 1760.0], [1.0, 0.2], [0.16, 0.08], rng, 0.16)
    return 0.5 * lowpass(y, 5000.0)


def ch2l1_guard_alert(rng, variant):
    """A guard breaks into a run at the player: a sharp low stab with a short rising sweep."""
    n = samples(0.7)
    t = np.arange(n) / SR
    sweep = np.sin(2 * np.pi * (220.0 * t + 400.0 * t * t)) * envelope(n, [(0.0, 0.0), (0.02, 1.0), (0.4, 0.5), (0.7, 0.0)])
    hit = _noise(n, rng, 60.0, 900.0) * envelope(n, [(0.0, 0.0), (0.005, 1.0), (0.12, 0.2), (0.4, 0.0)])
    y = 0.5 * lowpass(0.6 * sweep + 0.8 * hit, 4000.0)
    return stereo(y, y)


def ch2l1_guard_caught(rng, variant):
    """Caught: a dull low thud with a long dark tail, under the fade to black."""
    n = samples(1.6)
    y = damped_partials(n, [55.0, 110.0, 165.0], [1.0, 0.5, 0.2], [0.9, 0.5, 0.3], rng, 0.0)
    y += _noise(n, rng, 40.0, 400.0) * envelope(n, [(0.0, 0.0), (0.01, 1.0), (0.3, 0.1), (1.6, 0.0)])
    y = 0.7 * lowpass(y, 1200.0)
    return stereo(y, y)


def ch2l1_office_hum(rng, variant):
    """Rowan's office, as the scene turns cold: a very low hum, two tones a hair apart beating slowly, a faint airy hiss (loops)."""
    n = samples(8.0)
    t = np.arange(n) / SR
    y = 0.6 * np.sin(2 * np.pi * 46.0 * t) + 0.45 * np.sin(2 * np.pi * 46.5 * t) + 0.12 * np.sin(2 * np.pi * 92.0 * t)
    hiss = 0.05 * _noise(n, rng, 200.0, 900.0)
    left = lowpass(y + hiss, 600.0)
    right = lowpass(y + np.roll(hiss, 3000), 600.0)
    return stereo(0.5 * left, 0.5 * right)


def ch2l1_lock_shut(rng, variant):
    """The end of the overtime, up the stairs: the sealed door's bolt shot home from the other side, a hard dry clack and a short ring
    of steel, heard from below."""
    n = samples(1.2)
    y = knock(n, rng, 0.005, rng.uniform(150, 170), [1.0, 2.4, 3.9, 5.6], [1.0, 0.5, 0.3, 0.15], [0.12, 0.08, 0.05, 0.03],
              click=1.0, click_hi=4000.0)
    y += 0.6 * knock(n, rng, 0.06, rng.uniform(210, 230), [1.0, 2.7], [0.8, 0.3], [0.08, 0.05], click=0.7, click_hi=3000.0)
    return lowpass(y, 3500.0)


def ch2l1_manhole_scrape(rng, variant):
    """The emergency manhole's lid prised up and dragged a little over concrete: a heavy grinding of iron on stone (3 variations)."""
    n = samples(0.7)
    grain = 0.5 + 0.5 * smooth_random(n, 30.0, rng)
    y = _noise(n, rng, 150.0, 1600.0) * grain * envelope(n, [(0.0, 0.0), (0.06, 1.0), (0.5, 0.8), (0.68, 0.0)])
    y += 0.35 * _noise(n, rng, 1800.0, 5000.0) * grain * envelope(n, [(0.0, 0.0), (0.1, 0.5), (0.45, 0.4), (0.65, 0.0)])
    y += 0.3 * damped_partials(n, [rng.uniform(300, 360), rng.uniform(720, 800)], [1.0, 0.5], [0.15, 0.1], rng, 0.02)
    return 0.6 * y


def ch2l1_manhole_open(rng, variant):
    """The lid let fall beside the hole: a big iron clang on concrete, its ring humming down into the shaft."""
    n = samples(2.2)
    ratios = sorted(rng.uniform(1.0, 6.0, 8))
    y = knock(n, rng, 0.005, rng.uniform(170, 190), ratios, [1.0 / (1 + i * 0.4) for i in range(8)],
              [0.9 / (1 + i * 0.5) for i in range(8)], click=1.0, click_hi=5000.0)
    y += 0.5 * knock(n, rng, 0.12, rng.uniform(180, 200), ratios[:4], [0.6, 0.3, 0.2, 0.1], [0.3, 0.2, 0.1, 0.05], click=0.5, click_hi=3000.0)
    y += _noise(n, rng, 60.0, 600.0) * envelope(n, [(0.0, 0.0), (0.01, 0.8), (0.2, 0.15), (0.8, 0.0)])
    return 0.7 * y


def ch2l1_ladder(rng, variant):
    """Down the shaft: three hands and feet on iron rungs, each a dull metallic knock, going down and away."""
    n = samples(1.8)
    y = np.zeros(n)
    for k, start in enumerate((0.05, 0.55, 1.05)):
        fade_k = 1.0 - 0.25 * k
        y += fade_k * knock(n, rng, start, rng.uniform(380, 440), [1.0, 2.6, 4.1], [1.0, 0.4, 0.2], [0.08, 0.05, 0.03],
                            click=0.6, click_hi=3000.0)
        y += 0.4 * fade_k * _noise(n, rng, 200.0, 1500.0) * envelope(n, [(0.0, 0.0), (start, 0.0), (start + 0.01, 1.0), (start + 0.12, 0.0), (1.8, 0.0)])
    return lowpass(0.7 * y, 4000.0)


# --- Chapter 2 Level 2: the vibration creature, the pump and the pipe, physically modelled (tools/sounds/physical.py) ---

def _finish(y, rng, size, tail, wet, fade_out, amount=1.15):
    """Every Level 2 sound ends the same way: the room's answer, a gentle saturation, normalised, faded."""
    y = ph.room(y, rng, size=size, tail=tail, wet=wet)
    return fade(drive(y / np.max(np.abs(y)), amount), 0.001, fade_out)


def ch2l2_creature_step(rng, variant):
    """The vibration creature's heavy step (3 variations): the weight landing (a low blow felt more than heard), a lump of
    stone meeting stone (dense, quickly damped modes, soft contact), a crumble of grit under it and a few chips settling.
    Heavy and dry: no breath, no voice."""
    n = samples(0.9)
    y = thud(n, rng, 0.0, rng.uniform(40.0, 52.0), 0.13, amp=1.0, dull=260.0)
    f, a, d = ph.stone_modes(rng, 28, 140.0, 1800.0, decay=(0.012, 0.05))
    y += 0.7 * ph.modes(n, rng, 0.004, f, a, d, contact=0.0012)
    y += 0.25 * ph.grains(n, rng, [(0.0, 0.0), (0.006, 5000.0), (0.08, 1500.0), (0.3, 0.0)], 300.0, 3500.0)
    y += 0.12 * ph.grains(n, rng, [(0.0, 0.0), (0.15, 25.0), (0.6, 0.0)], 1200.0, 5000.0, length=(0.003, 0.008))
    return _finish(y, rng, 1.2, 0.4, 0.18, 0.15)


def ch2l2_creature_click(rng, variant):
    """The creature's stone teeth snapping shut when it orients (the one-second warning): two hard, tiny stone impacts a
    breath apart, each a cluster of bright, very short modes, and a few chips of enamel after."""
    n = samples(0.45)
    y = np.zeros(n)
    for start, scale in ((0.0, 1.0), (0.065, 0.75)):
        f, a, d = ph.stone_modes(rng, 18, 1500.0, 9000.0, decay=(0.004, 0.014))
        y += scale * ph.modes(n, rng, start, f, a, d, contact=0.00008)
        y += 0.3 * scale * thud(n, rng, start, 170.0, 0.02, amp=0.6, dull=900.0)
    y += 0.08 * ph.grains(n, rng, [(0.0, 0.0), (0.07, 400.0), (0.2, 0.0)], 2500.0, 8000.0, length=(0.001, 0.004))
    return _finish(y, rng, 1.0, 0.3, 0.2, 0.08, amount=1.05)


def ch2l2_creature_windup(rng, variant):
    """The creature raising its fists for a blow (0.9 s before it lands): stone grinding on stone, the grinding growing as the
    arms rise, and the creak of its joints: stick-slip, a train of catches that quickens, ringing a low stone body. Dry, no
    breath, no voice."""
    seconds = 0.9
    n = samples(seconds)
    y = 0.5 * ph.grains(n, rng, [(0.0, 300.0), (0.5, 2500.0), (0.85, 6000.0), (0.9, 0.0)], 180.0, 2600.0)
    catches = ph.impulses(n, rng, [(0.0, 18.0), (0.5, 35.0), (0.85, 70.0), (0.9, 0.0)], amp_sigma=0.4)
    f, a, d = ph.stone_modes(rng, 16, 90.0, 900.0, decay=(0.02, 0.07))
    y += 0.6 * ph.ring(catches, ph.body_ir(rng, f, a, d, 0.25))
    y *= envelope(n, [(0.0, 0.3), (0.85, 1.0), (0.9, 0.6)])
    return _finish(y, rng, 1.2, 0.35, 0.18, 0.04)


def ch2l2_creature_strike(rng, variant):
    """Both fists of stone slammed into the floor: the weight of it (a very low blow), two big lumps of stone ringing,
    the crunch of the floor's surface breaking (a dense burst of grains), debris scattering and settling for a second, and
    the room answering."""
    n = samples(1.8)
    y = thud(n, rng, 0.0, 36.0, 0.3, amp=1.3, dull=200.0)
    for start in (0.0, 0.012):
        f, a, d = ph.stone_modes(rng, 40, 70.0, 1500.0, decay=(0.04, 0.18))
        y += 0.6 * ph.modes(n, rng, start, f, a, d, contact=0.0015)
    y += 0.5 * ph.grains(n, rng, [(0.0, 0.0), (0.004, 18000.0), (0.06, 6000.0), (0.25, 0.0)], 250.0, 6000.0)
    y += 0.2 * ph.grains(n, rng, [(0.0, 0.0), (0.1, 60.0), (0.6, 30.0), (1.4, 0.0)], 600.0, 4500.0, length=(0.003, 0.01))
    y += 0.25 * ph.scrape(n, rng, 25.0, 140.0, flutter=4.0) * envelope(n, [(0.0, 0.0), (0.05, 1.0), (0.7, 0.35), (1.8, 0.0)])
    return _finish(y, rng, 1.6, 0.7, 0.25, 0.4, amount=1.3)


def ch2l2_pump_run(rng, variant):
    """The pump running for six seconds, then stopping: heavy pistons in a big concrete room. Each piston blow (every 0.67 s)
    is a deep thump and a heavy iron casing ringing (detuned modes that last), then the valve's clack; after it a hiss of air
    that flutters, and water forced through the pipes: a gush of bubbles over a rushing flow. A loose part rattles with each
    blow. From 4.8 s the blows come further apart and weaker; it ends in a long sigh of air by 6.0 s, so that silence follows.
    Mono, positioned at the pump."""
    seconds = 6.0
    n = samples(seconds)
    blows, at, gap = [], 0.1, 0.667
    while at < 4.8:
        blows.append(at)
        at += gap
    while at < 5.3:
        blows.append(at)
        gap *= 1.6
        at += gap
    casing = (np.array([118.0, 167.0, 243.0, 331.0, 452.0, 587.0, 761.0, 948.0]), np.array([1.0, 0.8, 0.6, 0.5, 0.35, 0.3, 0.2, 0.15]),
              np.array([0.5, 0.45, 0.35, 0.3, 0.25, 0.2, 0.15, 0.12]))
    y = np.zeros(n)
    rattle_rate, flow = [(0.0, 0.0)], np.zeros(n)
    for start in blows:
        k = 1.0 if start < 4.8 else max(0.45, 1.0 - (start - 4.8) / 1.2)
        y += k * thud(n, rng, start, rng.uniform(42.0, 50.0) * (0.85 + 0.15 * k), 0.25, amp=1.2, dull=240.0)
        y += 0.35 * k * ph.modes(n, rng, start + 0.005, *casing, detune=0.01, contact=0.002)
        f, a, d = ph.stone_modes(rng, 10, 900.0, 4000.0, decay=(0.01, 0.03))
        y += 0.2 * k * ph.modes(n, rng, start + 0.24, f, a, d, contact=0.0002)
        y += 0.2 * k * ph.scrape(n, rng, 1800.0, 7000.0) * envelope(
            n, [(0.0, 0.0), (start + 0.26, 0.0), (start + 0.29, 1.0), (start + 0.58, 0.0), (seconds + 1.0, 0.0)])
        flow += k * envelope(n, [(0.0, 0.0), (start + 0.05, 0.0), (start + 0.16, 1.0), (start + 0.5, 0.15), (seconds + 1.0, 0.0)])
        rattle_rate += [(start + 0.001, 0.0), (start + 0.01, 400.0 * k), (start + 0.12, 0.0)]
    flow = np.minimum(flow, 1.0)
    y += 0.35 * flow * ph.scrape(n, rng, 120.0, 1100.0, flutter=12.0)
    y += 0.25 * flow * ph.bubbles(n, rng, 900.0, radius=(1.0e-3, 6e-3))
    y += 0.12 * ph.grains(n, rng, sorted(rattle_rate), 1500.0, 4500.0, length=(0.002, 0.006))
    y += 0.35 * ph.scrape(n, rng, 20.0, 90.0, flutter=3.0) * envelope(n, [(0.0, 0.0), (0.3, 1.0), (4.8, 1.0), (6.0, 0.0)])
    sigh = ph.scrape(n, rng, 500.0, 3800.0, flutter=8.0) * envelope(n, [(0.0, 0.0), (5.2, 0.0), (5.35, 1.0), (6.0, 0.0)])
    y += 0.25 * lowpass(sigh, 2400.0, order=1)
    return _finish(y, rng, 2.0, 0.6, 0.25, 0.05)


# --- Chapter 2 Level 2: the Observant Guard, its wall and the four traps (phase 5), physically modelled ---

def ch2l2_wall_crack(rng, variant):
    """The bricked-up wall cracking before it bursts (3 variations): brick and mortar giving way, a few sharp stone cracks, a
    low groan of the whole wall under strain and a trickle of grit falling behind it."""
    n = samples(1.3)
    t = time_axis(n)
    y = 0.6 * ph.scrape(n, rng, 30.0, 160.0, flutter=3.0) * envelope(n, [(0.0, 0.0), (0.4, 0.6), (0.9, 1.0), (1.3, 0.0)])
    for start in sorted(rng.uniform(0.05, 1.0, int(rng.integers(2, 5)))):
        f, a, d = ph.stone_modes(rng, 18, 400.0, 5000.0, decay=(0.005, 0.03))
        y += rng.uniform(0.5, 1.0) * ph.modes(n, rng, float(start), f, a, d, contact=0.0001)
        y += 0.3 * thud(n, rng, float(start), rng.uniform(55.0, 90.0), 0.05, amp=0.7, dull=500.0)
    y += 0.3 * ph.grains(n, rng, [(0.0, 0.0), (0.3, 120.0), (1.0, 300.0), (1.3, 0.0)], 900.0, 5000.0, length=(0.002, 0.008))
    return _finish(y, rng, 1.1, 0.4, 0.2, 0.2, amount=1.1)


def ch2l2_wall_burst(rng, variant):
    """The patch of wall bursting outwards: a very low blow, big lumps of brick ringing, a dense crash of stone breaking and
    a long fall of rubble and dust settling, the room answering."""
    n = samples(3.0)
    y = thud(n, rng, 0.0, 32.0, 0.45, amp=1.5, dull=180.0)
    for start in (0.0, 0.02, 0.09):
        f, a, d = ph.stone_modes(rng, 40, 60.0, 2000.0, decay=(0.04, 0.2))
        y += 0.5 * ph.modes(n, rng, start, f, a, d, contact=0.002)
    y += 0.6 * ph.grains(n, rng, [(0.0, 0.0), (0.005, 25000.0), (0.1, 8000.0), (0.5, 800.0), (2.6, 0.0)], 200.0, 6000.0)
    y += 0.3 * ph.scrape(n, rng, 20.0, 180.0, flutter=3.0) * envelope(n, [(0.0, 0.0), (0.04, 1.0), (1.4, 0.3), (3.0, 0.0)])
    y += 0.2 * ph.grains(n, rng, [(0.3, 0.0), (0.6, 70.0), (1.5, 40.0), (2.8, 0.0)], 700.0, 5000.0, length=(0.003, 0.012))
    return _finish(y, rng, 1.8, 0.9, 0.28, 0.5, amount=1.3)


def ch2l2_guard_step(rng, variant):
    """The Guard's step (3 variations): a stiff, heavy shoe on concrete: a dull heel strike, the sole slapping flat a moment
    later, a tick of grit under it. Slower and drier than a person's: nothing alive in it."""
    n = samples(0.6)
    y = thud(n, rng, 0.0, rng.uniform(62.0, 78.0), 0.07, amp=1.0, dull=380.0)
    y += 0.5 * thud(n, rng, 0.09, rng.uniform(85.0, 105.0), 0.05, amp=0.8, dull=520.0)
    f, a, d = ph.stone_modes(rng, 14, 600.0, 3500.0, decay=(0.004, 0.02))
    y += 0.25 * ph.modes(n, rng, 0.0, f, a, d, contact=0.0004)
    y += 0.12 * ph.grains(n, rng, [(0.0, 0.0), (0.01, 800.0), (0.15, 0.0)], 1500.0, 6000.0, length=(0.001, 0.004))
    return _finish(y, rng, 1.0, 0.3, 0.16, 0.1, amount=1.05)


def ch2l2_guard_windup(rng, variant):
    """The Guard raising its arm (the 0.6 s before the blow): stiff cloth dragging, a joint creaking as it is forced
    (stick-slip catches quickening) and a held breath of air through the sleeve. No voice."""
    seconds = 0.65
    n = samples(seconds)
    cloth = bandpass(rng.standard_normal(n), 800.0, 3500.0, order=2) * envelope(n, [(0.0, 0.0), (0.3, 0.5), (0.58, 1.0), (0.65, 0.0)])
    catches = ph.impulses(n, rng, [(0.0, 12.0), (0.4, 40.0), (0.6, 90.0), (0.65, 0.0)], amp_sigma=0.4)
    f, a, d = ph.stone_modes(rng, 12, 250.0, 1600.0, decay=(0.015, 0.05))
    y = 0.5 * cloth + 0.5 * ph.ring(catches, ph.body_ir(rng, f, a, d, 0.2))
    return _finish(y, rng, 0.9, 0.25, 0.14, 0.04, amount=1.05)


def ch2l2_guard_hit(rng, variant):
    """The Guard's blow landing: the weight of an arm through a body (a low thud), the slap of cloth and flesh, a short
    crack, and the air pushed out of it."""
    n = samples(0.9)
    y = thud(n, rng, 0.0, 58.0, 0.14, amp=1.3, dull=300.0)
    y += 0.4 * ph.grains(n, rng, [(0.0, 0.0), (0.003, 9000.0), (0.05, 800.0), (0.2, 0.0)], 300.0, 4000.0)
    f, a, d = ph.stone_modes(rng, 16, 500.0, 3000.0, decay=(0.005, 0.02))
    y += 0.25 * ph.modes(n, rng, 0.0, f, a, d, contact=0.0003)
    y += 0.18 * ph.scrape(n, rng, 200.0, 1800.0, flutter=10.0) * envelope(n, [(0.0, 0.0), (0.02, 1.0), (0.35, 0.0), (0.9, 0.0)])
    return _finish(y, rng, 1.0, 0.35, 0.18, 0.15, amount=1.2)


def ch2l2_guard_stun(rng, variant):
    """The stunned Guard: an electric buzz (mains hum and its harmonics, hard-clipped), a crackle of small discharges and a
    shiver of the body's metal. One second, played over again while the stun lasts."""
    n = samples(1.0)
    t = time_axis(n)
    wobble = 1.0 + 0.04 * np.sin(TAU * 9.0 * t + rng.uniform(0, TAU))
    hum = sum(np.sin(TAU * 100.0 * k * wobble * t + rng.uniform(0, TAU)) / k ** 0.7 for k in range(1, 12))
    y = 0.6 * np.tanh(2.5 * hum / 3.0) * (0.75 + 0.25 * np.sin(TAU * 17.0 * t))
    y += 0.5 * ph.grains(n, rng, 90.0, 2500.0, 9000.0, length=(0.0008, 0.003))
    y += 0.2 * ph.impulses(n, rng, 20.0)
    y = lowpass(y, 7000.0, order=2)
    return _finish(y, rng, 0.8, 0.2, 0.12, 0.02, amount=1.2)


def ch2l2_trap_clack(rng, variant):
    """A breaker lever thrown: the iron handle meeting its stop (a hard clack, the plate ringing), a spring and a contact
    closing."""
    n = samples(0.45)
    y = thud(n, rng, 0.0, 150.0, 0.03, amp=0.9, dull=1200.0)
    iron = (np.array([410.0, 690.0, 1180.0, 1760.0, 2500.0]), np.array([1.0, 0.7, 0.5, 0.35, 0.2]), np.array([0.09, 0.07, 0.05, 0.04, 0.03]))
    y += 0.7 * ph.modes(n, rng, 0.0, *iron, detune=0.006, contact=0.0002)
    y += 0.35 * ph.modes(n, rng, 0.11, *iron, detune=0.006, contact=0.0002)
    y += 0.1 * ph.grains(n, rng, [(0.0, 0.0), (0.12, 80.0), (0.3, 0.0)], 2500.0, 8000.0, length=(0.001, 0.003))
    return _finish(y, rng, 0.8, 0.2, 0.14, 0.08, amount=1.05)


def ch2l2_trap_charge(rng, variant):
    """A trap charging for three seconds: a transformer hum rising from 60 to 190 Hz and a thin whine climbing from 500 Hz to
    2.5 kHz, swelling, with a faint crackle; it ends just short of full, the 'armed' tick follows."""
    seconds = 3.0
    n = samples(seconds)
    t = time_axis(n)
    rise = np.clip(t / seconds, 0.0, 1.0)
    hum = glide_tone(60.0 + 130.0 * rise ** 1.4, [1.0, 0.6, 0.45, 0.3, 0.2], rng.uniform(0, TAU))
    whine = glide_tone(500.0 + 2000.0 * rise ** 2, [1.0, 0.15], rng.uniform(0, TAU))
    y = 0.7 * hum * envelope(n, [(0.0, 0.0), (0.4, 0.5), (3.0, 1.0)]) + 0.18 * whine * envelope(n, [(0.0, 0.0), (1.0, 0.1), (2.8, 1.0), (3.0, 0.8)])
    y += 0.25 * ph.grains(n, rng, [(0.0, 0.0), (1.0, 8.0), (3.0, 60.0)], 2500.0, 9000.0, length=(0.001, 0.004))
    return _finish(y, rng, 0.9, 0.2, 0.12, 0.04, amount=1.1)


def ch2l2_trap_armed(rng, variant):
    """The trap armed: a relay's double tick and a soft steady beep, quick and clean."""
    n = samples(0.5)
    t = time_axis(n)
    y = thud(n, rng, 0.0, 190.0, 0.015, amp=0.8, dull=1800.0) + 0.7 * thud(n, rng, 0.07, 210.0, 0.015, amp=0.8, dull=1800.0)
    y += 0.35 * np.sin(TAU * 1450.0 * t) * envelope(n, [(0.0, 0.0), (0.13, 0.0), (0.14, 1.0), (0.34, 1.0), (0.38, 0.0), (0.5, 0.0)])
    return _finish(y, rng, 0.7, 0.15, 0.1, 0.05, amount=1.05)


def ch2l2_trap_zap(rng, variant):
    """The Guard on a live plate: a violent electric discharge, a hard crack and a flash of hiss, a thump under it as the
    body locks, mains hum shorting out and a shower of sparks dying away."""
    n = samples(1.6)
    t = time_axis(n)
    crack = highpass(rng.standard_normal(n), 1500.0, order=2) * envelope(n, [(0.0, 0.0), (0.001, 1.0), (0.05, 0.5), (0.3, 0.0), (1.6, 0.0)])
    y = 1.0 * crack + thud(n, rng, 0.0, 48.0, 0.25, amp=1.4, dull=260.0)
    y += 0.5 * np.tanh(3.0 * np.sin(TAU * 100.0 * t)) * envelope(n, [(0.0, 0.0), (0.005, 1.0), (0.6, 0.5), (1.6, 0.0)])
    y += 0.5 * ph.grains(n, rng, [(0.0, 0.0), (0.01, 1500.0), (0.3, 300.0), (1.4, 0.0)], 2000.0, 12000.0, length=(0.0008, 0.003))
    return _finish(y, rng, 1.2, 0.5, 0.2, 0.3, amount=1.3)


def ch2l2_trap_fizzle(rng, variant):
    """A trap failing: a short crackle of weak sparks and a buzz that sags and dies, one last tick."""
    n = samples(1.2)
    t = time_axis(n)
    buzz = np.tanh(2.0 * np.sin(TAU * np.cumsum(100.0 * np.exp(-t / 0.5)) / SR)) * envelope(n, [(0.0, 0.0), (0.01, 1.0), (0.7, 0.0), (1.2, 0.0)])
    y = 0.5 * buzz + 0.5 * ph.grains(n, rng, [(0.0, 0.0), (0.01, 220.0), (0.9, 10.0), (1.2, 0.0)], 1800.0, 8000.0, length=(0.0008, 0.003))
    y += 0.5 * thud(n, rng, 0.0, 90.0, 0.05, amp=0.8, dull=700.0)
    return _finish(y, rng, 0.8, 0.2, 0.12, 0.2, amount=1.1)


def ch2l2_guard_death(rng, variant):
    """The Guard falling and coming apart over fourteen seconds: a joint-by-joint collapse (creaks, the body hitting the floor at 2.1 s
    with a heavy thud and a slide of cloth), then a low detuned drone that thins into a distorted whine, glitching bursts of
    quantised noise (stutter, bit-crushed) coming faster as it goes, a dry hiss of dissolving and, at the end, silence. No voice."""
    seconds = 14.0
    k = seconds / 10.0
    n = samples(seconds)
    t = time_axis(n)
    drone = sum(np.sin(TAU * f * t + rng.uniform(0, TAU)) for f in (55.0, 55.7, 82.4, 110.9)) * envelope(
        n, [(0.0, 0.0), (2.0, 0.2), (4.2, 1.0), (11.2, 0.3), (14.0, 0.0)])
    whine = glide_tone(300.0 + 1500.0 * (t / seconds) ** 2, [1.0, 0.3], rng.uniform(0, TAU)) * envelope(
        n, [(0.0, 0.0), (5.6, 0.1), (11.9, 0.8), (14.0, 0.0)])
    noise = rng.standard_normal(n)
    hold = 220
    held = np.repeat(noise[::hold], hold)[:n]                         # sample-and-hold: a stuttering digital hiss
    crushed = np.round(held * 3.0) / 3.0
    gate = np.zeros(n)
    at = 2.6
    while at < 13.4:
        length = rng.uniform(0.03, 0.18)
        gate[int(at * SR):int(min(seconds, at + length) * SR)] = 1.0
        at += length + rng.uniform(0.05, 0.9) * (1.0 - 0.8 * at / seconds)
    y = 0.35 * drone / 4.0 + 0.25 * whine + 0.55 * crushed * gate * envelope(n, [(0.0, 0.0), (2.6, 0.2), (8.4, 0.7), (13.3, 1.0), (14.0, 0.0)])
    y += 0.2 * ph.scrape(n, rng, 2500.0, 9000.0, flutter=15.0) * envelope(n, [(0.0, 0.0), (7.0, 0.2), (12.6, 1.0), (14.0, 0.0)])
    # The collapse: joints creaking as the knees go, then the body down.
    catches = ph.impulses(n, rng, [(0.0, 0.0), (0.6, 14.0), (1.5, 30.0), (2.0, 0.0), (14.0, 0.0)], amp_sigma=0.4)
    f, a, d = ph.stone_modes(rng, 12, 250.0, 1600.0, decay=(0.015, 0.05))
    y += 0.5 * ph.ring(catches, ph.body_ir(rng, f, a, d, 0.2))
    y += 1.3 * thud(n, rng, 2.1, 46.0, 0.35, amp=1.4, dull=240.0)
    y += 0.3 * ph.scrape(n, rng, 200.0, 2500.0, flutter=8.0) * envelope(n, [(0.0, 0.0), (1.4, 0.0), (1.9, 0.8), (2.4, 0.0), (14.0, 0.0)])
    return _finish(y, rng, 1.4, 0.5, 0.2, 0.3, amount=1.2)


def ch2l2_guard_chase(rng, variant):
    """Chapter 2 Level 2's music of constant danger, for the Observant Guard's hunt and the game of the traps (12.8 s, 8 bars at 150 bpm,
    looping seamlessly: the game plays it again every 256 ticks). A kick on every beat that never lets go; a low, buzzing saw ostinato
    on sixteenths (E, with the minor second and the tritone coming and going: the electrical hum gone mad) with an octave jump now and
    then; ticks of rusty metal on the off-eighths; short bursts of electric static on the offbeats; a dissonant string cluster in tremolo
    climbing a semitone over every four bars and a thin siren rising across each two. Nothing resolves."""
    bpm = 150.0
    beat = 60.0 / bpm
    sixteenth = beat / 4
    seconds = 32 * beat
    n = samples(seconds)
    t = time_axis(n)
    y = np.zeros((n, 2))
    offset = 0.01                                                             # hits just after the loop point
    hit_n = samples(0.5)
    ht = time_axis(hit_n)
    kick = glide_tone(46.0 + 62.0 * np.exp(-ht / 0.04), (1.0, 0.3)) * np.exp(-ht / 0.17)
    kick = drive(kick, 1.9) + burst(hit_n, rng, 0.0, 0.02, hi=700.0, tau=0.01, amp=0.4)
    for b in range(32):
        place(y, stereo(kick, kick) * (1.0 if b % 4 == 0 else 0.78), b * beat + offset, circular=True)
    # The ostinato.
    roots = [41.2, 41.2, 43.65, 41.2, 41.2, 46.25, 43.65, 38.9]
    pattern = [1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 1, 0]
    note_n = samples(sixteenth * 0.95)
    nt = time_axis(note_n)
    env = np.exp(-nt / 0.1) * (1.0 - np.exp(-nt / 0.002))
    for bar in range(8):
        for s in range(16):
            if not pattern[s]:
                continue
            f = roots[bar] * (2.0 if s in (5, 14) else 1.0)
            note = glide_tone(np.full(note_n, f), (1.0, 0.7, 0.5, 0.4, 0.3, 0.25, 0.2)) * env
            place(y, stereo(note, note) * 0.34, (bar * 16 + s) * sixteenth + offset, circular=True)
    # Rusty ticks on the off-eighths, static on some offbeats.
    for e in range(64):
        if e % 2 == 1:
            tick = clock_tick(samples(0.2), rng, 0.0, tock=e % 4 == 3, amp=0.3)
            place(y, pan(tick, 0.3 if e % 4 == 1 else -0.3), e * beat / 2 + offset, circular=True)
    for s in range(0, 128, 1):
        if s % 8 == 6 or (s % 16 == 13 and s // 16 % 2 == 1):
            zap = burst(samples(0.12), rng, 0.0, 0.01, lo=1500.0, hi=9000.0, tau=0.03, amp=0.3)
            place(y, pan(zap, rng.uniform(-0.6, 0.6)), s * sixteenth + offset, circular=True)
    # Strings: a dissonant cluster in tremolo that slides between two clusters a semitone apart (whole cycles, so the loop is seamless);
    # and the siren, a wail swinging round 900 Hz every two bars.
    trem = 1.0 - 0.7 * (0.5 + 0.5 * np.sin(TAU * loop_frequency(12.0, seconds) * t))
    weight = 0.5 - 0.5 * np.cos(TAU * 2.0 * t / seconds)
    for (fa, fb), position in (((466.2, 493.9), -0.6), ((493.9, 523.3), 0.0), ((698.5, 740.0), 0.6)):
        for f, w in ((fa, 1.0 - weight), (fb, weight)):
            f = loop_frequency(f, seconds)
            voice = sum(np.sin(TAU * f * k * t + k) / k for k in (1, 2, 3, 4))
            y += pan(0.05 * voice * trem * w, position)
    base = loop_frequency(900.0, seconds)
    siren = np.sin(TAU * base * t + 250.0 * np.sin(TAU * t / (8 * beat)))
    y += stereo(*(2 * [0.03 * siren * (0.5 + 0.5 * np.sin(TAU * t / (16 * beat) - 1.0))]))
    return fft_filter(y, 30.0, 9000.0, order=2)


def ch2l2_guard_lament(rng, variant):
    """A short, sad song for the Observant Guard dying (14 s): the main menu's felt piano alone in A minor, slow. A low A and E held
    underneath, a melody that sinks in two phrases (E D C B, resting on A; then F E D C, and a last A that does not resolve), a
    little reverb, fading out as the body is gone."""
    seconds = 14.0
    n = int(seconds * mp.SR)
    piano = np.zeros((n, 2))
    for midi, start, vel in ((45, 0.0, 0.45), (52, 0.15, 0.35), (41, 6.6, 0.4), (48, 6.75, 0.32)):
        mp.mix_in(piano, mp.piano_note(mp.midi_to_hz(midi), vel, rng), start, pan=mp.pan_for(midi))
    melody = ((76, 0.5, 0.62), (74, 2.0, 0.55), (72, 3.2, 0.52), (71, 4.2, 0.5), (69, 5.2, 0.46),
              (77, 7.1, 0.55), (76, 8.5, 0.5), (74, 9.7, 0.46), (72, 10.7, 0.42), (69, 11.8, 0.4))
    for midi, start, vel in melody:
        mp.mix_in(piano, mp.piano_note(mp.midi_to_hz(midi), vel, rng), start, pan=mp.pan_for(midi))
    piano = mp.body_resonance(piano, rng)
    mix = piano * 0.9 + mp.reverb_tail(piano, rng, seconds=3.0) * 0.3
    return mp.finalize(mix, fade_in_s=0.02, fade_out_s=3.5, peak=0.95)


def ch2l2_guard_chest(rng, variant):
    """The Guard beating its chest with both fists (one beat, heard five times in its entrance): a hollow metal boom, as if on a
    drum of sheet iron, two fists a hair apart, a low body under it and a short ring of the cavity. A robot's with a human weight."""
    n = samples(0.8)
    y = thud(n, rng, 0.0, 78.0, 0.16, amp=1.2, dull=330.0) + 0.8 * thud(n, rng, 0.018, 70.0, 0.14, amp=1.0, dull=300.0)
    iron = (np.array([160.0, 290.0, 470.0, 760.0, 1180.0]), np.array([1.0, 0.8, 0.55, 0.35, 0.2]), np.array([0.28, 0.2, 0.15, 0.1, 0.07]))
    y += 0.55 * ph.modes(n, rng, 0.0, *iron, detune=0.01, contact=0.0009)
    y += 0.4 * ph.modes(n, rng, 0.018, *iron, detune=0.01, contact=0.0009)
    y += 0.15 * ph.grains(n, rng, [(0.0, 0.0), (0.004, 1200.0), (0.08, 0.0)], 600.0, 4000.0, length=(0.001, 0.004))
    return _finish(y, rng, 1.0, 0.35, 0.18, 0.12, amount=1.2)


def ch2l2_grate(rng, variant):
    """A heavy iron grate dropped or hauled up on its chains (about two seconds): chain links rattling and running over a
    sheave, the iron frame groaning, and at the end a big iron slam and its ring."""
    seconds = 2.2
    n = samples(seconds)
    links = ph.impulses(n, rng, [(0.0, 35.0), (1.6, 60.0), (1.9, 0.0)], amp_sigma=0.5)
    f, a, d = ph.stone_modes(rng, 20, 700.0, 6000.0, decay=(0.01, 0.05))
    y = 0.6 * ph.ring(links, ph.body_ir(rng, f, a, d, 0.15))
    y += 0.25 * ph.scrape(n, rng, 60.0, 600.0, flutter=6.0) * envelope(n, [(0.0, 0.0), (0.2, 1.0), (1.8, 0.8), (1.9, 0.0)])
    iron = (np.array([95.0, 180.0, 310.0, 520.0, 840.0, 1300.0]), np.array([1.0, 0.8, 0.6, 0.45, 0.3, 0.2]), np.array([0.7, 0.5, 0.4, 0.3, 0.2, 0.15]))
    y += 0.9 * ph.modes(n, rng, 1.85, *iron, detune=0.008, contact=0.0008)
    y += thud(n, rng, 1.85, 44.0, 0.2, amp=1.2, dull=240.0)
    return _finish(y, rng, 1.4, 0.6, 0.22, 0.3, amount=1.25)


# --- Chapter 2 Level 2: the flood and the exit (phase 6), physically modelled ---


def ch2l2_flood_gate_slam(rng, variant):
    """The vestibule's sluice gate dropping behind the player (about 1.6 s): a short rush of the plate sliding down its guides,
    a huge iron plate hitting its sill (a very low blow, a long ringing of heavy iron modes and a concrete thud under it) and
    the bolts shot home after it."""
    seconds = 2.6
    n = samples(seconds)
    iron = (np.array([62.0, 91.0, 137.0, 205.0, 310.0, 470.0, 720.0, 1100.0, 1700.0]), np.array([1.0, 0.9, 0.7, 0.55, 0.45, 0.35, 0.25, 0.18, 0.12]),
            np.array([1.1, 0.9, 0.7, 0.55, 0.4, 0.3, 0.2, 0.15, 0.1]))
    y = 0.35 * ph.scrape(n, rng, 120.0, 2500.0, flutter=9.0) * envelope(n, [(0.0, 0.0), (0.05, 0.7), (0.5, 1.0), (0.62, 0.0), (seconds, 0.0)])
    y += 0.5 * ph.impulses(n, rng, [(0.0, 0.0), (0.05, 45.0), (0.6, 30.0), (0.63, 0.0)], amp_sigma=0.6) * 0.05
    y += 1.2 * thud(n, rng, 0.62, 34.0, 0.4, amp=1.4, dull=200.0)
    y += 0.8 * ph.modes(n, rng, 0.62, *iron, detune=0.01, contact=0.0012)
    f, a, d = ph.stone_modes(rng, 18, 120.0, 1800.0, decay=(0.03, 0.12))
    y += 0.35 * ph.modes(n, rng, 0.625, f, a, d, contact=0.001)
    for at, base in ((1.12, 210.0), (1.28, 250.0)):
        y += 0.55 * knock(n, rng, at, base, [1.0, 2.4, 3.9, 5.6], [1.0, 0.5, 0.3, 0.15], [0.12, 0.08, 0.05, 0.03], click=1.0, click_hi=4000.0)
    return _finish(y, rng, 1.8, 0.8, 0.26, 0.4, amount=1.25)


def ch2l2_flood_creak(rng, variant):
    """Old pipes groaning under pressure (3 s), heard from the vestibule's far side: a long iron groan whose pitch bends up and
    down (a stuck joint giving a little at a time), shorter ticks of metal expanding, a low throb of water pushing hard and a
    faint hiss of weeping seams; it swells towards the end, when the pipe is about to go."""
    seconds = 3.0
    n = samples(seconds)
    t = time_axis(n)
    swell = envelope(n, [(0.0, 0.0), (0.4, 0.55), (1.6, 0.8), (2.6, 1.0), (3.0, 0.0)])
    pitch = 118.0 * (1.0 + 0.08 * np.sin(TAU * 0.7 * t + rng.uniform(0, TAU)) + 0.5 * (t / seconds) ** 2)
    groan = glide_tone(pitch, harmonics=(1.0, 0.6, 0.45, 0.3, 0.2, 0.12))
    groan *= 0.75 + 0.25 * smooth_random(n, 6.0, rng)
    y = 0.5 * lowpass(groan, 2600.0, order=2) * swell
    second = glide_tone(190.0 * (1.0 + 0.12 * np.sin(TAU * 0.45 * t + rng.uniform(0, TAU)) - 0.2 * (t / seconds)), harmonics=(1.0, 0.5, 0.3, 0.15))
    y += 0.3 * lowpass(second, 2200.0, order=2) * envelope(n, [(0.0, 0.0), (0.7, 0.0), (1.2, 0.8), (2.2, 0.6), (2.8, 0.0), (seconds, 0.0)])
    y += 0.25 * ph.scrape(n, rng, 400.0, 3200.0, flutter=7.0) * envelope(n, [(0.0, 0.0), (0.3, 0.3), (1.8, 0.4), (2.7, 0.8), (3.0, 0.0)])
    f, a, d = ph.stone_modes(rng, 10, 500.0, 4200.0, decay=(0.01, 0.04))
    ticks = ph.impulses(n, rng, [(0.0, 0.0), (0.2, 3.0), (2.0, 5.0), (3.0, 0.0)], amp_sigma=0.6)
    y += 0.35 * ph.ring(ticks, ph.body_ir(rng, f, a, d, 0.1))
    y += 0.3 * shaped_noise(n, rng, exponent=2.0, lo=20.0, hi=110.0) * envelope(n, [(0.0, 0.0), (1.0, 0.6), (2.7, 1.0), (3.0, 0.0)])
    y += 0.08 * ph.scrape(n, rng, 3000.0, 9000.0, flutter=15.0) * envelope(n, [(0.0, 0.0), (1.2, 0.5), (3.0, 1.0)])
    return _finish(y, rng, 1.4, 0.6, 0.2, 0.3, amount=1.1)


def ch2l2_flood_burst(rng, variant):
    """A pressurised pipe bursting in a concrete hall (2.4 s): a sharp metallic crack of the seam splitting, a very low blast as the
    water is let out, a roar of a thick jet that thins to a hiss and bubbles over it, pieces of the pipe ringing and falling."""
    seconds = 2.8
    n = samples(seconds)
    y = knock(n, rng, 0.0, rng.uniform(900.0, 1100.0), [1.0, 1.7, 2.6, 3.9], [1.0, 0.6, 0.4, 0.25], [0.03, 0.02, 0.012, 0.008], click=1.3, click_hi=11000.0)
    y += 1.1 * thud(n, rng, 0.015, 38.0, 0.3, amp=1.3, dull=300.0)
    iron = (np.array([84.0, 143.0, 236.0, 390.0, 620.0, 980.0, 1500.0]), np.array([1.0, 0.8, 0.6, 0.45, 0.3, 0.2, 0.12]), np.array([0.5, 0.4, 0.3, 0.22, 0.15, 0.1, 0.07]))
    y += 0.5 * ph.modes(n, rng, 0.01, *iron, detune=0.01, contact=0.001)
    roar = envelope(n, [(0.0, 0.0), (0.02, 1.0), (0.4, 0.9), (1.4, 0.55), (2.8, 0.3)])
    y += 0.7 * ph.scrape(n, rng, 150.0, 5000.0, flutter=18.0) * roar
    y += 0.3 * ph.scrape(n, rng, 4000.0, 11000.0, flutter=30.0) * envelope(n, [(0.0, 0.0), (0.03, 1.0), (0.8, 0.5), (2.8, 0.25)])
    y += 0.35 * ph.bubbles(n, rng, [(0.0, 0.0), (0.05, 1500.0), (1.0, 700.0), (2.8, 200.0)], radius=(0.8e-3, 8e-3))
    y += 0.4 * shaped_noise(n, rng, exponent=2.0, lo=20.0, hi=140.0) * envelope(n, [(0.0, 0.0), (0.03, 1.0), (1.2, 0.7), (2.8, 0.2)])
    y += 0.2 * ph.grains(n, rng, [(0.05, 0.0), (0.2, 60.0), (1.2, 20.0), (2.0, 0.0)], 700.0, 5000.0, length=(0.003, 0.012))
    return _finish(y, rng, 1.8, 0.8, 0.25, 0.5, amount=1.3)


def ch2l2_flood_jet(rng, variant):
    """The burst pipe's jet, heard from a few blocks (a loop of 8 s): a steady, thick hiss of water forced through a hole with a
    slow unevenness and a wet rumble under it; mono (it sits at the pipe), built in the frequency domain so it loops."""
    seconds = 8.0
    n = samples(seconds)
    for _ in range(1):
        body = shaped_noise(n, rng, exponent=0.6, lo=300.0, hi=7500.0, order=2)
        body *= 1.0 + 0.25 * smooth_random(n, 0.9, rng, periodic=True) + 0.1 * smooth_random(n, 5.0, rng, periodic=True)
        hiss = shaped_noise(n, rng, exponent=0.0, lo=4500.0, hi=12000.0, order=2) * 0.22
        low = shaped_noise(n, rng, exponent=2.0, lo=25.0, hi=240.0) * 0.5 * (1.0 + 0.3 * smooth_random(n, 0.5, rng, periodic=True))
    return 0.5 * body + hiss + low


def ch2l2_flood_rumble(rng, variant):
    """The flood in the pipes and under the floor: deep rushing water (a loop of 10 s): a wide, turbulent roar with a slow swell,
    a floor of sub-bass you feel, and the slap and gurgle of water against concrete far off; stereo and seamless."""
    seconds = 10.0
    n = samples(seconds)
    sides = []
    for _ in range(2):
        roar = shaped_noise(n, rng, exponent=1.2, lo=50.0, hi=2200.0, order=2)
        roar *= 1.0 + 0.3 * smooth_random(n, 0.35, rng, periodic=True) + 0.15 * smooth_random(n, 2.2, rng, periodic=True)
        sub = shaped_noise(n, rng, exponent=2.0, lo=18.0, hi=90.0) * 0.9 * (1.0 + 0.25 * smooth_random(n, 0.2, rng, periodic=True))
        wash = shaped_noise(n, rng, exponent=0.4, lo=800.0, hi=6000.0, order=2) * 0.12 * np.clip(0.5 + 0.5 * smooth_random(n, 0.8, rng, periodic=True), 0.0, 1.0)
        sides.append(0.4 * roar + 0.55 * sub + wash)
    return stereo(sides[0], sides[1])


def ch2l2_flood_rumble_muffled(rng, variant):
    """The same flood through the station's door: the roar low-passed to what a heavy plate lets through and quieter, mostly a
    deep rumble with a faint hiss on top (a loop of 10 s)."""
    seconds = 10.0
    n = samples(seconds)
    sides = []
    for _ in range(2):
        roar = shaped_noise(n, rng, exponent=1.4, lo=40.0, hi=420.0, order=3)
        roar *= 1.0 + 0.3 * smooth_random(n, 0.3, rng, periodic=True)
        sub = shaped_noise(n, rng, exponent=2.0, lo=18.0, hi=80.0) * 0.9 * (1.0 + 0.2 * smooth_random(n, 0.2, rng, periodic=True))
        sides.append(0.5 * roar + 0.5 * sub)
    return stereo(sides[0], sides[1])


def ch2l2_exit_door(rng, variant):
    """The exit's heavy door (about 4 s): a big iron wheel turned against its rust (a ratchet of clanks and a long groan of the
    spindle), then two heavy bolts drawn back, each a slide of iron and a hard clack, and the door's seal letting go in a sigh of
    stale air."""
    seconds = 4.4
    n = samples(seconds)
    t = time_axis(n)
    iron = (np.array([105.0, 190.0, 330.0, 560.0, 900.0, 1400.0]), np.array([1.0, 0.8, 0.6, 0.45, 0.3, 0.2]), np.array([0.25, 0.2, 0.15, 0.12, 0.08, 0.06]))
    y = np.zeros(n)
    at = 0.1
    while at < 2.1:
        y += rng.uniform(0.5, 0.8) * knock(n, rng, at, rng.uniform(170.0, 210.0), [1.0, 2.3, 3.7, 5.5], [1.0, 0.5, 0.3, 0.15], [0.07, 0.05, 0.03, 0.02], click=0.8, click_hi=5000.0)
        at += rng.uniform(0.17, 0.26)
    spindle = glide_tone(75.0 + 30.0 * np.sin(TAU * 0.5 * t) * envelope(n, [(0.0, 0.0), (0.2, 1.0), (2.0, 1.0), (2.2, 0.0), (seconds, 0.0)]), harmonics=(1.0, 0.6, 0.4, 0.25))
    y += 0.25 * lowpass(spindle, 1600.0, order=2) * envelope(n, [(0.0, 0.0), (0.15, 1.0), (2.1, 0.8), (2.3, 0.0), (seconds, 0.0)])
    y += 0.25 * ph.scrape(n, rng, 200.0, 1800.0, flutter=6.0) * envelope(n, [(0.0, 0.0), (0.15, 1.0), (2.1, 0.8), (2.3, 0.0), (seconds, 0.0)])
    for slide, clack in ((2.45, 2.85), (3.0, 3.38)):
        y += 0.4 * ph.scrape(n, rng, 300.0, 2800.0, flutter=5.0) * envelope(n, [(0.0, 0.0), (slide, 0.0), (slide + 0.05, 1.0), (clack - 0.02, 0.8), (clack, 0.0), (seconds, 0.0)])
        y += 0.9 * ph.modes(n, rng, clack, *iron, detune=0.01, contact=0.001)
        y += thud(n, rng, clack, 52.0, 0.12, amp=0.9, dull=300.0)
    y += 0.3 * ph.scrape(n, rng, 600.0, 4500.0, flutter=9.0) * envelope(n, [(0.0, 0.0), (3.55, 0.0), (3.65, 1.0), (4.4, 0.0)])
    return _finish(y, rng, 1.4, 0.6, 0.2, 0.4, amount=1.2)


# --- Chapter 2 Level 2: Stone Maw's last stand (phase 6.1), physically modelled ---

PIPE_MODES = (np.array([230.0, 610.0, 1130.0, 1790.0, 2650.0, 3700.0]), np.array([1.0, 0.8, 0.6, 0.45, 0.3, 0.2]), np.array([0.35, 0.25, 0.18, 0.12, 0.08, 0.05]))


def ch2l2_maw_pipe_fall(rng, variant):
    """A piece of pipe coming away from the ceiling and falling (about 0.9 s, heard at the ceiling point): a crumble of rust and
    mortar, a groan of old iron letting go and a snap, then the thin whistle of the fall, rising. It ends where the clang (the
    phase 4 pipe cut) takes over."""
    seconds = 0.95
    n = samples(seconds)
    t = time_axis(n)
    y = 0.5 * ph.grains(n, rng, [(0.0, 0.0), (0.03, 900.0), (0.2, 300.0), (0.5, 0.0)], 600.0, 5000.0, length=(0.002, 0.008))
    groan = lowpass(ph.scrape(n, rng, 180.0, 900.0, flutter=8.0), 1400.0, order=2)
    y += 0.3 * groan * envelope(n, [(0.0, 0.0), (0.05, 1.0), (0.3, 0.6), (0.38, 0.0), (seconds, 0.0)])
    y += 0.7 * knock(n, rng, 0.33, 520.0, [1.0, 2.3, 3.6], [1.0, 0.5, 0.25], [0.05, 0.03, 0.02], click=1.0, click_hi=5000.0)
    whistle = glide_tone(700.0 + 1500.0 * (t / seconds) ** 1.5, harmonics=(1.0, 0.35))
    y += 0.1 * bandpass(whistle, 600.0, 3500.0, order=2) * envelope(n, [(0.0, 0.0), (0.4, 0.0), (0.9, 1.0), (seconds, 0.8)])
    y += 0.08 * ph.scrape(n, rng, 1200.0, 6000.0, flutter=30.0) * envelope(n, [(0.0, 0.0), (0.4, 0.0), (0.9, 1.0), (seconds, 0.8)])
    return fade(drive(y / np.max(np.abs(y)), 1.1), 0.002, 0.04)


def ch2l2_maw_throw(rng, variant):
    """A heavy piece of pipe thrown (0.55 s): a low rush of air as a long iron weight swings and flies, the body of the throw
    (a dull blow of the arm) and a faint hollow tone of the tube cutting the air, dropping in pitch as it passes."""
    seconds = 0.55
    n = samples(seconds)
    t = time_axis(n)
    air = ph.scrape(n, rng, 120.0, 1100.0, flutter=14.0) * envelope(n, [(0.0, 0.0), (0.05, 0.6), (0.18, 1.0), (0.4, 0.5), (seconds, 0.0)])
    y = 0.8 * air
    y += 0.3 * lowpass(ph.scrape(n, rng, 800.0, 4000.0, flutter=40.0), 3500.0, order=2) * envelope(n, [(0.0, 0.0), (0.12, 0.2), (0.2, 0.7), (0.34, 0.0), (seconds, 0.0)])
    hollow = glide_tone(430.0 - 200.0 * t / seconds, harmonics=(1.0, 0.4))
    y += 0.12 * hollow * envelope(n, [(0.0, 0.0), (0.12, 0.0), (0.2, 1.0), (0.42, 0.0), (seconds, 0.0)])
    y += 0.5 * thud(n, rng, 0.0, 90.0, 0.05, amp=0.7, dull=500.0)
    return fade(drive(y / np.max(np.abs(y)), 1.05), 0.002, 0.15)


def ch2l2_maw_hit(rng, variant):
    """The pipe landing on Stone Maw (about 1 s): iron meeting cracked stone (a short, hard ring of the pipe's modes, cut by the
    contact), a dull lump of stone taking the blow, the dry crack of its skin splitting and a few chips falling. No voice."""
    n = samples(1.3)
    y = 0.9 * ph.modes(n, rng, 0.0, *PIPE_MODES, detune=0.01, contact=0.0003) * 0.6
    y += 0.8 * thud(n, rng, 0.0, 60.0, 0.1, amp=1.0, dull=300.0)
    f, a, d = ph.stone_modes(rng, 30, 160.0, 2600.0, decay=(0.02, 0.09))
    y += 0.6 * ph.modes(n, rng, 0.002, f, a, d, contact=0.0006)
    y += 0.7 * burst(n, rng, 0.0, 0.01, lo=1500.0, hi=9000.0, tau=0.003, amp=1.0)
    y += 0.4 * ph.grains(n, rng, [(0.0, 0.0), (0.004, 6000.0), (0.06, 2500.0), (0.3, 0.0)], 1500.0, 7000.0, length=(0.001, 0.005))
    y += 0.2 * ph.grains(n, rng, [(0.0, 0.0), (0.15, 40.0), (0.7, 20.0), (1.2, 0.0)], 800.0, 4500.0, length=(0.003, 0.01))
    return _finish(y, rng, 1.2, 0.4, 0.2, 0.25, amount=1.25)


def ch2l2_maw_pipe_break(rng, variant):
    """A thrown piece of pipe breaking on the floor (about 1.3 s): the first iron clang cut short as the tube splits, rusted metal
    tearing, bits of iron skittering and spinning on the stone, a last small ringing."""
    n = samples(1.5)
    y = 0.9 * ph.modes(n, rng, 0.0, *PIPE_MODES, detune=0.012, contact=0.0004)
    y += 0.5 * thud(n, rng, 0.0, 70.0, 0.1, amp=0.9, dull=350.0)
    y += 0.5 * ph.scrape(n, rng, 600.0, 5000.0, flutter=30.0) * envelope(n, [(0.0, 0.0), (0.02, 1.0), (0.18, 0.5), (0.3, 0.0), (1.5, 0.0)])
    shards = ph.impulses(n, rng, [(0.0, 0.0), (0.05, 90.0), (0.4, 40.0), (1.0, 8.0), (1.4, 0.0)], amp_sigma=0.6)
    f, a, d = ph.stone_modes(rng, 18, 900.0, 6500.0, decay=(0.006, 0.03))
    y += 0.5 * ph.ring(shards, ph.body_ir(rng, f, a, d, 0.12))
    y += 0.35 * ph.grains(n, rng, [(0.0, 0.0), (0.05, 120.0), (0.5, 40.0), (1.4, 0.0)], 1800.0, 8000.0, length=(0.002, 0.007))
    return _finish(y, rng, 1.0, 0.35, 0.18, 0.3, amount=1.2)


def ch2l2_maw_wall_ram(rng, variant):
    """Stone Maw running headlong into a wall (about 2.2 s): a huge, dull blow, almost all weight (a very low tone, a lump of
    stone ringing, a concrete crack), dust and chips raining off the wall and the room's answer rolling away. Phase 4's strike
    with more behind it and no fists."""
    n = samples(2.4)
    y = thud(n, rng, 0.0, 30.0, 0.45, amp=1.6, dull=180.0)
    f, a, d = ph.stone_modes(rng, 44, 60.0, 1400.0, decay=(0.05, 0.22))
    y += 0.7 * ph.modes(n, rng, 0.0, f, a, d, contact=0.002)
    f, a, d = ph.stone_modes(rng, 30, 220.0, 3000.0, decay=(0.02, 0.08))
    y += 0.4 * ph.modes(n, rng, 0.004, f, a, d, contact=0.001)
    y += 0.6 * ph.grains(n, rng, [(0.0, 0.0), (0.004, 22000.0), (0.08, 7000.0), (0.3, 0.0)], 250.0, 6000.0)
    y += 0.25 * ph.grains(n, rng, [(0.0, 0.0), (0.1, 80.0), (0.8, 40.0), (2.0, 0.0)], 600.0, 4500.0, length=(0.003, 0.012))
    y += 0.3 * ph.scrape(n, rng, 25.0, 160.0, flutter=4.0) * envelope(n, [(0.0, 0.0), (0.05, 1.0), (1.0, 0.4), (2.4, 0.0)])
    return _finish(y, rng, 1.8, 0.8, 0.26, 0.5, amount=1.35)


def ch2l2_maw_stunned(rng, variant):
    """Stone Maw stunned (a loop of 1 s, played again while the stun lasts): a low grinding of stone on stone from somewhere deep in
    it, the slow settling of a big body (a few dull ticks and a creak) and a trickle of grit; built so that it loops."""
    seconds = 1.0
    n = samples(seconds)
    pad = samples(0.4)
    grind = shaped_noise(n, rng, exponent=1.5, lo=40.0, hi=420.0, order=2) * (1.0 + 0.35 * smooth_random(n, 3.0, rng, periodic=True))
    creak = lowpass(shaped_noise(n, rng, exponent=0.5, lo=200.0, hi=1100.0, order=2), 1100.0, order=2) * (0.5 + 0.5 * smooth_random(n, 2.0, rng, periodic=True)) ** 2
    m = n + pad
    ticks = ph.impulses(m, rng, 7.0, amp_sigma=0.5)
    f, a, d = ph.stone_modes(rng, 16, 90.0, 1200.0, decay=(0.02, 0.08))
    ticks = ph.ring(ticks, ph.body_ir(rng, f, a, d, 0.25))
    grit = ph.grains(m, rng, 40.0, 1200.0, 5000.0, length=(0.002, 0.007))
    extra = 0.4 * ticks + 0.15 * grit
    extra[:pad] += extra[n:]                      # what rings past the end wraps round to the start: the loop has no seam
    return 0.7 * grind + 0.12 * creak + extra[:n]


def ch2l2_maw_burst(rng, variant):
    """The grate over the pipe's mouth torn off as Stone Maw comes out (about 2.4 s): iron screaming against its bolts, three
    bolts shearing with a crack each, the grate wrenched off and flung (a big iron clang and its bounce), and a crumble of
    wall round the mouth."""
    seconds = 2.6
    n = samples(seconds)
    iron = (np.array([75.0, 120.0, 190.0, 300.0, 480.0, 780.0, 1200.0, 1900.0]), np.array([1.0, 0.9, 0.7, 0.55, 0.45, 0.3, 0.2, 0.12]),
            np.array([0.9, 0.7, 0.55, 0.4, 0.3, 0.2, 0.15, 0.1]))
    y = 0.4 * ph.scrape(n, rng, 500.0, 3800.0, flutter=7.0) * envelope(n, [(0.0, 0.0), (0.1, 0.8), (0.75, 1.0), (0.85, 0.0), (seconds, 0.0)])
    squeal = glide_tone(950.0 + 700.0 * np.sin(TAU * 0.9 * time_axis(n)), harmonics=(1.0, 0.5, 0.3))
    y += 0.1 * bandpass(squeal, 500.0, 3500.0, order=2) * envelope(n, [(0.0, 0.0), (0.15, 1.0), (0.7, 0.8), (0.8, 0.0), (seconds, 0.0)])
    for at in (0.3, 0.55, 0.78):
        y += 0.7 * knock(n, rng, at, rng.uniform(900.0, 1400.0), [1.0, 2.4, 3.9], [1.0, 0.5, 0.25], [0.04, 0.025, 0.015], click=1.2, click_hi=8000.0)
    y += 1.1 * thud(n, rng, 0.85, 40.0, 0.3, amp=1.3, dull=220.0)
    y += 0.9 * ph.modes(n, rng, 0.85, *iron, detune=0.01, contact=0.001)
    y += 0.6 * ph.modes(n, rng, 1.32, *iron, detune=0.01, contact=0.0012) * 0.5
    y += 0.45 * ph.grains(n, rng, [(0.0, 0.0), (0.3, 80.0), (0.85, 260.0), (1.4, 50.0), (2.4, 0.0)], 300.0, 5500.0, length=(0.003, 0.012))
    f, a, d = ph.stone_modes(rng, 24, 100.0, 1600.0, decay=(0.03, 0.14))
    y += 0.4 * ph.modes(n, rng, 0.87, f, a, d, contact=0.001)
    return _finish(y, rng, 1.8, 0.8, 0.26, 0.4, amount=1.25)


def ch2l2_maw_collapse(rng, variant):
    """Part of the ceiling coming down (about 3.4 s): first the stone cracking (a quickening train of dry cracks and a growing low
    groan, grit sifting down), then everything giving way at once: a very low blow, tons of rubble hitting the floor in a dense
    roar of impacts that thins out into stones settling, and a rumble rolling off under it."""
    seconds = 3.6
    n = samples(seconds)
    t = time_axis(n)
    cracks = ph.impulses(n, rng, [(0.0, 3.0), (0.9, 12.0), (1.5, 40.0), (1.62, 0.0), (seconds, 0.0)], amp_sigma=0.7)
    f, a, d = ph.stone_modes(rng, 22, 200.0, 4200.0, decay=(0.008, 0.05))
    y = 0.8 * ph.ring(cracks, ph.body_ir(rng, f, a, d, 0.2))
    y += 0.35 * lowpass(shaped_noise(n, rng, exponent=1.5, lo=30.0, hi=300.0, order=2), 300.0, order=2) * envelope(n, [(0.0, 0.0), (0.3, 0.4), (1.6, 1.0), (1.7, 0.0), (seconds, 0.0)])
    y += 0.3 * ph.grains(n, rng, [(0.0, 0.0), (0.2, 60.0), (1.5, 160.0), (1.65, 0.0)], 1500.0, 6500.0, length=(0.002, 0.007))
    at = 1.62
    y += 1.4 * thud(n, rng, at, 28.0, 0.6, amp=1.7, dull=160.0)
    for k in range(3):
        f, a, d = ph.stone_modes(rng, 40, 50.0 + 20.0 * k, 1400.0, decay=(0.06, 0.25))
        y += 0.5 * ph.modes(n, rng, at + 0.03 * k, f, a, d, contact=0.002)
    rubble = ph.grains(n, rng, [(0.0, 0.0), (at, 0.0), (at + 0.02, 900.0), (at + 0.5, 600.0), (at + 1.4, 120.0), (seconds, 0.0)], 120.0, 3800.0, length=(0.004, 0.02))
    y += 0.9 * rubble
    heavy = ph.impulses(n, rng, [(0.0, 0.0), (at, 0.0), (at + 0.03, 35.0), (at + 0.9, 8.0), (at + 1.8, 0.0)], amp_sigma=0.8)
    f, a, d = ph.stone_modes(rng, 26, 80.0, 900.0, decay=(0.05, 0.2))
    y += 0.8 * ph.ring(heavy, ph.body_ir(rng, f, a, d, 0.4))
    y += 0.45 * lowpass(shaped_noise(n, rng, exponent=2.0, lo=20.0, hi=140.0), 140.0, order=2) * envelope(n, [(0.0, 0.0), (at, 0.0), (at + 0.05, 1.0), (at + 1.6, 0.4), (seconds, 0.0)])
    return _finish(y, rng, 2.2, 1.0, 0.28, 0.9, amount=1.35)


def ch2l2_maw_pillar_ram(rng, variant):
    """Stone Maw's charge ending against a pillar (about 2.8 s): the same weight as the wall's blow but deeper and tighter, a very
    low blow with a long sub tail, a block of masonry shaking (stone modes), one hard crack running up the shaft and dust and
    chips sifting after it. Heavier and more physical than the wall's, so that the two are never mistaken."""
    n = samples(2.8)
    y = 1.3 * thud(n, rng, 0.0, 26.0, 0.7, amp=1.9, dull=140.0)
    y += 0.6 * thud(n, rng, 0.012, 48.0, 0.25, amp=1.0, dull=300.0)
    f, a, d = ph.stone_modes(rng, 50, 45.0, 1100.0, decay=(0.08, 0.35))
    y += 0.8 * ph.modes(n, rng, 0.0, f, a, d, contact=0.003)
    f, a, d = ph.stone_modes(rng, 26, 700.0, 4200.0, decay=(0.01, 0.05))
    y += 0.55 * ph.modes(n, rng, 0.03, f, a, d, contact=0.0008)
    crack = ph.impulses(n, rng, [(0.0, 0.0), (0.03, 70.0), (0.2, 14.0), (0.7, 0.0)], amp_sigma=0.8)
    f, a, d = ph.stone_modes(rng, 20, 300.0, 5200.0, decay=(0.006, 0.03))
    y += 0.7 * ph.ring(crack, ph.body_ir(rng, f, a, d, 0.2))
    y += 0.5 * ph.grains(n, rng, [(0.0, 0.0), (0.03, 800.0), (0.4, 260.0), (1.4, 40.0), (2.8, 0.0)], 400.0, 6500.0, length=(0.003, 0.012))
    y += 0.4 * lowpass(shaped_noise(n, rng, exponent=2.0, lo=18.0, hi=120.0), 120.0, order=2) * envelope(n, [(0.0, 0.0), (0.03, 1.0), (1.2, 0.4), (2.8, 0.0)])
    return _finish(y, rng, 2.0, 0.9, 0.26, 0.6, amount=1.4)


def ch2l2_maw_pillar_crack(rng, variant):
    """A pillar cracking (about 1.8 s): masonry grinding as a seam opens in it, a quickening run of dry cracks, a short groan of
    stone under load and grit trickling out of the crack."""
    seconds = 1.8
    n = samples(seconds)
    cracks = ph.impulses(n, rng, [(0.0, 6.0), (0.5, 22.0), (0.9, 55.0), (1.0, 0.0), (seconds, 0.0)], amp_sigma=0.8)
    f, a, d = ph.stone_modes(rng, 24, 180.0, 4800.0, decay=(0.008, 0.045))
    y = 0.9 * ph.ring(cracks, ph.body_ir(rng, f, a, d, 0.25))
    y += 0.45 * lowpass(shaped_noise(n, rng, exponent=1.5, lo=45.0, hi=380.0, order=2), 380.0, order=2) * envelope(n, [(0.0, 0.0), (0.4, 0.5), (1.0, 1.0), (1.4, 0.3), (seconds, 0.0)])
    y += 0.3 * ph.scrape(n, rng, 60.0, 420.0, flutter=7.0) * envelope(n, [(0.0, 0.0), (0.3, 0.6), (1.0, 1.0), (seconds, 0.0)])
    y += 0.35 * ph.grains(n, rng, [(0.0, 0.0), (0.2, 80.0), (1.0, 200.0), (seconds, 0.0)], 1500.0, 6500.0, length=(0.002, 0.007))
    y += 0.6 * thud(n, rng, 0.98, 52.0, 0.12, amp=0.8, dull=260.0)
    return _finish(y, rng, 1.4, 0.5, 0.2, 0.3, amount=1.3)


def ch2l2_maw_pillar_break(rng, variant):
    """A pillar giving way (about 4 s): a long splitting groan, the column shearing with a huge crack, then its stone coming apart
    and falling in a dense roar of blocks and rubble on the floor that thins out into stones rolling and settling, a very low
    rumble under it all."""
    seconds = 4.0
    n = samples(seconds)
    cracks = ph.impulses(n, rng, [(0.0, 4.0), (0.8, 18.0), (1.3, 60.0), (1.4, 0.0), (seconds, 0.0)], amp_sigma=0.8)
    f, a, d = ph.stone_modes(rng, 26, 140.0, 4600.0, decay=(0.008, 0.05))
    y = 0.8 * ph.ring(cracks, ph.body_ir(rng, f, a, d, 0.25))
    y += 0.5 * lowpass(shaped_noise(n, rng, exponent=1.5, lo=35.0, hi=300.0, order=2), 300.0, order=2) * envelope(n, [(0.0, 0.0), (0.5, 0.5), (1.4, 1.0), (1.5, 0.0), (seconds, 0.0)])
    at = 1.4
    y += 1.5 * thud(n, rng, at, 30.0, 0.55, amp=1.7, dull=170.0)
    for k in range(4):
        f, a, d = ph.stone_modes(rng, 40, 50.0 + 25.0 * k, 1500.0, decay=(0.06, 0.25))
        y += 0.45 * ph.modes(n, rng, at + 0.05 * k, f, a, d, contact=0.002)
    rubble = ph.grains(n, rng, [(0.0, 0.0), (at, 0.0), (at + 0.02, 1100.0), (at + 0.7, 700.0), (at + 1.8, 140.0), (seconds, 0.0)], 100.0, 4000.0, length=(0.004, 0.022))
    y += 1.0 * rubble
    heavy = ph.impulses(n, rng, [(0.0, 0.0), (at, 0.0), (at + 0.03, 28.0), (at + 1.1, 7.0), (at + 2.3, 0.0)], amp_sigma=0.85)
    f, a, d = ph.stone_modes(rng, 26, 70.0, 900.0, decay=(0.05, 0.22))
    y += 0.8 * ph.ring(heavy, ph.body_ir(rng, f, a, d, 0.4))
    y += 0.45 * lowpass(shaped_noise(n, rng, exponent=2.0, lo=18.0, hi=130.0), 130.0, order=2) * envelope(n, [(0.0, 0.0), (at, 0.0), (at + 0.05, 1.0), (at + 2.0, 0.4), (seconds, 0.0)])
    return _finish(y, rng, 2.4, 1.1, 0.28, 0.9, amount=1.35)


def ch2l2_maw_debris_warn(rng, variant):
    """A piece of the ceiling about to come away (about 0.9 s): a small dry crumble, a couple of chips ticking, grit sifting. Quiet
    but near: the tell to step away from a spot on the floor."""
    seconds = 0.9
    n = samples(seconds)
    y = 0.5 * ph.grains(n, rng, [(0.0, 0.0), (0.05, 160.0), (0.5, 90.0), (seconds, 0.0)], 1500.0, 6800.0, length=(0.002, 0.007))
    ticks = ph.impulses(n, rng, [(0.0, 0.0), (0.05, 14.0), (0.5, 6.0), (seconds, 0.0)], amp_sigma=0.7)
    f, a, d = ph.stone_modes(rng, 14, 700.0, 4800.0, decay=(0.004, 0.02))
    y += 0.6 * ph.ring(ticks, ph.body_ir(rng, f, a, d, 0.12))
    y += 0.12 * lowpass(shaped_noise(n, rng, exponent=1.0, lo=120.0, hi=900.0, order=2), 900.0, order=2) * envelope(n, [(0.0, 0.0), (0.1, 1.0), (seconds, 0.0)])
    return _finish(y, rng, 0.8, 0.25, 0.12, 0.2, amount=1.1)


def ch2l2_maw_escape_rumble(rng, variant):
    """The arena caving in (8 s): a very low, wide rumble swelling and shuddering, deep stone groans, dull far impacts of heavy
    pieces landing and a constant sift of grit; it keeps rising to the end."""
    seconds = 8.0
    n = samples(seconds)
    wobble = 0.65 + 0.35 * smooth_random(n, 1.7, rng)
    y = 0.9 * lowpass(shaped_noise(n, rng, exponent=2.0, lo=16.0, hi=150.0, order=2), 150.0, order=2) * wobble * envelope(n, [(0.0, 0.2), (3.0, 0.7), (8.0, 1.0)])
    y += 0.35 * lowpass(shaped_noise(n, rng, exponent=1.5, lo=40.0, hi=420.0, order=2), 420.0, order=2) * (0.5 + 0.5 * smooth_random(n, 3.0, rng)) ** 2 * envelope(n, [(0.0, 0.0), (2.0, 0.5), (8.0, 1.0)])
    heavy = ph.impulses(n, rng, [(0.0, 0.6), (3.0, 1.3), (8.0, 2.6)], amp_sigma=0.9)
    f, a, d = ph.stone_modes(rng, 26, 45.0, 800.0, decay=(0.08, 0.3))
    y += 0.9 * ph.ring(heavy, ph.body_ir(rng, f, a, d, 0.7))
    y += 0.35 * ph.grains(n, rng, [(0.0, 20.0), (4.0, 70.0), (8.0, 160.0)], 800.0, 6000.0, length=(0.003, 0.012))
    return _finish(y, rng, 2.6, 0.9, 0.3, 0.6, amount=1.3)


def ch2l2_lift_gate(rng, variant):
    """The lift's iron gate shut by hand (about 1.7 s): the gate rattling along its track, the slam into the post (an iron clang
    with a short bar-ring), the latch dropping with a hard click and a last settle of the frame."""
    n = samples(1.7)
    y = 0.4 * ph.scrape(n, rng, 120.0, 900.0, flutter=15.0) * envelope(n, [(0.0, 0.0), (0.05, 1.0), (0.5, 0.8), (0.55, 0.0), (1.7, 0.0)])
    at = 0.55
    y += 1.0 * thud(n, rng, at, 85.0, 0.09, amp=1.0, dull=900.0)
    y += 0.9 * knock(n, rng, at, rng.uniform(330, 370), [1.0, 2.76, 5.4, 8.9], [1.0, 0.7, 0.4, 0.2], [0.35, 0.22, 0.12, 0.06], click=0.8, click_hi=5000.0)
    y += 0.5 * knock(n, rng, at + 0.012, rng.uniform(780, 860), [1.0, 2.4, 4.1], [1.0, 0.5, 0.25], [0.2, 0.12, 0.07], click=0.4, click_hi=6000.0)
    y += 0.6 * knock(n, rng, at + 0.34, rng.uniform(1500, 1700), [1.0, 1.9], [1.0, 0.4], [0.05, 0.03], click=1.0, click_hi=7000.0)
    y += 0.25 * ph.scrape(n, rng, 300.0, 1600.0, flutter=30.0) * envelope(n, [(0.0, 0.0), (at + 0.05, 0.0), (at + 0.08, 0.8), (at + 0.5, 0.0), (1.7, 0.0)])
    return _finish(y, rng, 1.0, 0.45, 0.16, 0.25, amount=1.2)


def ch2l2_lift_motor(rng, variant):
    """The service lift starting (about 4 s): a relay clunk, an electric motor winding up into a steady low whine with its
    gear hum, a steel cable creaking and whining under tension, the cab shuddering as it starts to climb."""
    seconds = 4.0
    n = samples(seconds)
    t = time_axis(n)
    ramp = 1.0 - np.exp(-t / 0.7)
    f0 = 52.0 * (0.4 + 0.6 * ramp)
    phase = np.cumsum(f0) / SR
    motor = sum(amp * np.sin(TAU * k * phase) for k, amp in ((1, 1.0), (2, 0.6), (3, 0.35), (5, 0.18)))
    y = 0.35 * motor * ramp * (0.85 + 0.15 * smooth_random(n, 5.0, rng))
    whine = np.sin(TAU * np.cumsum(780.0 * (0.5 + 0.5 * ramp) * (1.0 + 0.01 * smooth_random(n, 3.0, rng))) / SR)
    y += 0.07 * whine * ramp * envelope(n, [(0.0, 0.0), (0.6, 0.8), (seconds, 1.0)])
    y += 0.3 * bandpass(shaped_noise(n, rng, exponent=0.5, lo=200.0, hi=2400.0, order=2), 250.0, 2200.0, order=2) * (0.3 + 0.7 * smooth_random(n, 6.0, rng) ** 2) * ramp * 0.5
    y += 0.8 * thud(n, rng, 0.0, 70.0, 0.1, amp=0.8, dull=700.0)
    y += 0.6 * knock(n, rng, 0.01, rng.uniform(900, 1000), [1.0, 2.2], [1.0, 0.5], [0.04, 0.02], click=1.0, click_hi=6000.0)
    y += 0.25 * lowpass(shaped_noise(n, rng, exponent=2.0, lo=20.0, hi=130.0), 130.0, order=2) * smooth_random(n, 2.5, rng) ** 2 * ramp
    return _finish(y, rng, 1.0, 0.4, 0.12, 0.5, amount=1.15)


CHAPTER2_SOUNDS = [
    Sound("event.prologue.crack", "Chapter 2 prologue", "A stage of the scientist's change: dry cracks of joints, a dull thump, the creak of something stretched too far (3 variations)",
          "mono", False, 3, prologue_crack, -3.0, attenuation=24),
    Sound("music.prologue.reveal", "Chapter 2 prologue", "The scientist becomes a Pale man: the hit of \"he heard you\" (the Level 2 stinger's own), as he turns to the camera",
          "stereo", False, 1, lambda rng, variant: stinger_heard(rng, 1), -1.0),
    Sound("event.prologue.title", "Chapter 2 prologue", "\"CHAPTER 2\" landing on the black: one low blow, felt more than heard", "stereo", False, 1,
          prologue_title_hit, -1.0),
    Sound("ambient.prologue.hum", "Chapter 2 prologue", "The prologue's laboratory: the lights' hum, a fridge, the ventilation's hiss (plays flat, the same in every shot)",
          "stereo", True, 1, prologue_hum, -8.0, stream=True, game_volume=0.5),
    Sound("music.prologue.drone", "Chapter 2 prologue", "The prologue's tension: a low drone, two tones a semitone apart beating, a thin whine (its volume rises with the scene)",
          "stereo", True, 1, prologue_drone, -6.0, stream=True),
    Sound("event.ch2l1.ding", "Chapter 2 Level 1", "The loudspeakers' chime before each of Rowan's announcements: two soft bell notes (ding-dong)",
          "mono", False, 1, ch2l1_ding, -6.0, attenuation=48),
    Sound("block.ch2l1.sweep", "Chapter 2 Level 1", "Task 3: the broom over the floor, a soft swish (3 variations)", "mono", False, 3, ch2l1_sweep, -10.0),
    Sound("block.ch2l1.blip", "Chapter 2 Level 1", "Tasks 1 and 2: a small pip of a lab instrument, pitched up by the game as a task goes on", "mono", False, 1, ch2l1_blip, -8.0),
    Sound("block.ch2l1.keypad_key", "Chapter 2 Level 1", "Act 3: a key of the office's keypad pressed, a short beep", "mono", False, 1, ch2l1_keypad_key, -10.0),
    Sound("block.ch2l1.keypad_error", "Chapter 2 Level 1", "Act 3: a wrong code at the office's keypad, a low double buzz", "mono", False, 1, ch2l1_keypad_error, -9.0),
    Sound("event.ch2l1.guard_suspect", "Chapter 2 Level 1", "A patrolling guard notices something suspicious: a soft questioning rise", "mono", False, 1,
          ch2l1_guard_suspect, -8.0, attenuation=24),
    Sound("event.ch2l1.guard_alert", "Chapter 2 Level 1", "A guard runs at the player: a sharp low stab", "stereo", False, 1, ch2l1_guard_alert, -4.0),
    Sound("event.ch2l1.guard_caught", "Chapter 2 Level 1", "Caught by a guard: a dull thud under the fade to black", "stereo", False, 1, ch2l1_guard_caught, -3.0),
    Sound("ambient.ch2l1.office_hum", "Chapter 2 Level 1", "The office's cinematic: a very low beating hum under Rowan's colder lines (loops)", "stereo", True, 1,
          ch2l1_office_hum, -10.0, stream=True),
    Sound("block.ch2l1.keypad_accept", "Chapter 2 Level 1", "Act 3: Rowan's code accepted, two rising beeps and the lock's clack", "mono", False, 1, ch2l1_keypad_accept, -8.0),
    Sound("block.ch2l1.lock_shut", "Chapter 2 Level 1", "The overtime's end: the sealed door's bolt shot home from outside, a dry clack heard from the basement",
          "mono", False, 1, ch2l1_lock_shut, -4.0, attenuation=48),
    Sound("block.ch2l1.manhole_scrape", "Chapter 2 Level 1", "The emergency manhole's iron lid prised up, grinding on concrete (3 variations)", "mono", False, 3,
          ch2l1_manhole_scrape, -6.0),
    Sound("block.ch2l1.manhole_open", "Chapter 2 Level 1", "The manhole's lid let fall beside the hole: a big iron clang", "mono", False, 1, ch2l1_manhole_open, -3.0,
          attenuation=32),
    Sound("block.ch2l1.ladder", "Chapter 2 Level 1", "Climbing down the manhole's iron rungs, three knocks going away", "mono", False, 1, ch2l1_ladder, -6.0),
    Sound("event.ch2l2.creature_step", "Chapter 2 Level 2", "The vibration creature's knuckle gait: a heavy step of stone, dry and dull (3 variations)", "mono", False, 3,
          ch2l2_creature_step, -4.0, attenuation=40),
    Sound("event.ch2l2.creature_click", "Chapter 2 Level 2", "The vibration creature turning to a vibration: its stone teeth clicking shut twice (the one-second warning)", "mono", False, 1,
          ch2l2_creature_click, -3.0, attenuation=40),
    Sound("event.ch2l2.creature_strike", "Chapter 2 Level 2", "The vibration creature's blow: both fists slammed into the floor, a dull thud and a crunch of stone", "mono", False, 1,
          ch2l2_creature_strike, -2.0, attenuation=48),
    Sound("event.ch2l2.creature_windup", "Chapter 2 Level 2", "The vibration creature raising its fists for a blow: stone grinding and creaking, rising (the 0.9 s before it lands)", "mono", False, 1,
          ch2l2_creature_windup, -3.0, attenuation=40),
    Sound("event.ch2l2.pump_run", "Chapter 2 Level 2", "The pump room's pump: heavy pistons for six seconds, the blows spacing out at the end and a sigh of air (plays at the pump; then silence)", "mono", False, 1,
          ch2l2_pump_run, -3.0, attenuation=80),
    Sound("event.ch2l2.wall_crack", "Chapter 2 Level 2", "The Guard's bricked-up wall cracking: brick and mortar giving way, a groan and falling grit (3 variations)", "mono", False, 3,
          ch2l2_wall_crack, -4.0, attenuation=40),
    Sound("event.ch2l2.wall_burst", "Chapter 2 Level 2", "The wall bursting outwards: a very low blow, a crash of brick and a long fall of rubble", "mono", False, 1,
          ch2l2_wall_burst, -1.0, attenuation=64),
    Sound("event.ch2l2.guard_step", "Chapter 2 Level 2", "The Observant Guard's step: a stiff, heavy shoe on concrete, heel then sole (3 variations)", "mono", False, 3,
          ch2l2_guard_step, -5.0, attenuation=36),
    Sound("event.ch2l2.guard_windup", "Chapter 2 Level 2", "The Observant Guard raising its arm: dragging cloth and a joint creaking, quickening (0.6 s)", "mono", False, 1,
          ch2l2_guard_windup, -4.0, attenuation=32),
    Sound("event.ch2l2.guard_hit", "Chapter 2 Level 2", "The Observant Guard's blow: the weight of an arm through a body, a slap and a short crack", "mono", False, 1,
          ch2l2_guard_hit, -2.0, attenuation=40),
    Sound("event.ch2l2.guard_stun", "Chapter 2 Level 2", "The stunned Guard: an electric buzz and a crackle of small discharges, one second, played again while the stun lasts", "mono", False, 1,
          ch2l2_guard_stun, -5.0, attenuation=40),
    Sound("event.ch2l2.guard_death", "Chapter 2 Level 2", "The Guard falling and coming apart over fourteen seconds: a collapse, a thud on the floor, a thinning drone, a rising distorted whine and glitching bursts of digital noise", "mono", False, 1,
          ch2l2_guard_death, -3.0, attenuation=48),
    Sound("block.ch2l2.trap_clack", "Chapter 2 Level 2", "A trap's breaker lever thrown: iron meeting its stop and a contact closing", "mono", False, 1,
          ch2l2_trap_clack, -4.0, attenuation=32),
    Sound("block.ch2l2.trap_charge", "Chapter 2 Level 2", "A trap charging for three seconds: a transformer hum rising and a thin whine climbing", "mono", False, 1,
          ch2l2_trap_charge, -5.0, attenuation=36),
    Sound("block.ch2l2.trap_armed", "Chapter 2 Level 2", "A trap armed: a relay's double tick and a soft steady beep", "mono", False, 1,
          ch2l2_trap_armed, -5.0, attenuation=32),
    Sound("event.ch2l2.trap_zap", "Chapter 2 Level 2", "The Guard on a live plate: a violent discharge, a hard crack, the hum shorting out and sparks dying", "mono", False, 1,
          ch2l2_trap_zap, -1.0, attenuation=56),
    Sound("event.ch2l2.trap_fizzle", "Chapter 2 Level 2", "A trap failing under the Guard: a short crackle of weak sparks and a buzz that sags and dies", "mono", False, 1,
          ch2l2_trap_fizzle, -4.0, attenuation=40),
    Sound("block.ch2l2.grate", "Chapter 2 Level 2", "A heavy iron grate dropped or hauled up on its chains: links rattling and a big iron slam at the end", "mono", False, 1,
          ch2l2_grate, -2.0, attenuation=56),
    Sound("music.ch2l2.guard_chase", "Chapter 2 Level 2", "The Observant Guard's hunt and the game of the traps: a relentless kick, a buzzing saw ostinato, rusty ticks and static, dissonant string tremolo, a rising siren (150 bpm, 8 bars, loops)", "stereo", True, 1,
          ch2l2_guard_chase, -2.0, stream=True),
    Sound("music.ch2l2.guard_lament", "Chapter 2 Level 2", "The Observant Guard dying: a short, sad song for felt piano in A minor, sinking in two phrases and not resolving (14 s, plays once)", "stereo", False, 1,
          ch2l2_guard_lament, -1.0),
    Sound("event.ch2l2.guard_chest", "Chapter 2 Level 2", "The Guard beating its chest with both fists: a hollow boom of sheet iron and a low body under it (one beat)", "mono", False, 1,
          ch2l2_guard_chest, -2.0, attenuation=40),
    Sound("event.ch2l2.flood_gate_slam", "Chapter 2 Level 2", "The vestibule's sluice gate dropping behind the player: a rush down its guides, a huge iron plate on its sill and the bolts shot home (about 1.6 s)", "mono", False, 1,
          ch2l2_flood_gate_slam, -1.0, attenuation=64),
    Sound("event.ch2l2.flood_creak", "Chapter 2 Level 2", "Old pipes groaning under pressure before they burst: a long iron groan bending in pitch, ticks of metal and a low throb, swelling (3 s)", "mono", False, 1,
          ch2l2_flood_creak, -3.0, attenuation=56),
    Sound("event.ch2l2.flood_burst", "Chapter 2 Level 2", "A pipe bursting in the far wall of the collector: a metallic crack, a low blast, a roar of water thinning to a hiss over bubbles (about 2.4 s)", "mono", False, 1,
          ch2l2_flood_burst, -1.0, attenuation=72),
    Sound("ambient.ch2l2.flood_jet", "Chapter 2 Level 2", "The burst pipe's jet of water, steady and thick, heard from a few blocks (8 s loop)", "mono", True, 1,
          ch2l2_flood_jet, -6.0, attenuation=40, stream=False),
    Sound("ambient.ch2l2.flood_rumble", "Chapter 2 Level 2", "The flood: deep rushing water in the pipes and under the floor, a roar and sub-bass (10 s loop, its volume set by the stage)", "stereo", True, 1,
          ch2l2_flood_rumble, -6.0, stream=True),
    Sound("ambient.ch2l2.flood_rumble_muffled", "Chapter 2 Level 2", "The same flood heard through the station's shut door: a low rumble and little else (10 s loop)", "stereo", True, 1,
          ch2l2_flood_rumble_muffled, -8.0, stream=True),
    Sound("event.ch2l2.exit_door", "Chapter 2 Level 2", "The exit's heavy door: a big iron wheel turned against rust, two bolts drawn back and a sigh of air (about 4 s)", "mono", False, 1,
          ch2l2_exit_door, -2.0, attenuation=48),
    Sound("event.ch2l2.maw_pipe_fall", "Chapter 2 Level 2", "A piece of pipe coming away from the ceiling and falling (0.95 s, at the ceiling point): rust and mortar crumbling, iron groaning and snapping, the thin whistle of the fall; the landing is the pipe_fall cut",
          "mono", False, 1, ch2l2_maw_pipe_fall, -4.0, attenuation=40),
    Sound("event.ch2l2.maw_throw", "Chapter 2 Level 2", "A piece of pipe thrown at Stone Maw: a heavy rush of air and the hollow tone of the tube, dropping as it passes (0.55 s)",
          "mono", False, 1, ch2l2_maw_throw, -4.0, attenuation=24),
    Sound("event.ch2l2.maw_hit", "Chapter 2 Level 2", "The pipe landing on Stone Maw: iron on cracked stone, a dull lump taking the blow, the dry crack of its skin splitting and chips falling (no voice)",
          "mono", False, 1, ch2l2_maw_hit, -2.0, attenuation=56),
    Sound("event.ch2l2.maw_pipe_break", "Chapter 2 Level 2", "A thrown piece of pipe breaking on the floor: a clang cut short as the tube splits, rusted iron tearing, bits skittering on the stone (1.5 s)",
          "mono", False, 1, ch2l2_maw_pipe_break, -3.0, attenuation=56),
    Sound("event.ch2l2.maw_wall_ram", "Chapter 2 Level 2", "Stone Maw running headlong into a wall: a huge dull blow, stone ringing, a concrete crack and dust and chips raining down (2.4 s)",
          "mono", False, 1, ch2l2_maw_wall_ram, -1.0, attenuation=72),
    Sound("ambient.ch2l2.maw_stunned", "Chapter 2 Level 2", "Stone Maw stunned: a low grinding of stone on stone, a big body settling in dull ticks and a trickle of grit (1 s loop, played again while the stun lasts)",
          "mono", True, 1, ch2l2_maw_stunned, -6.0, attenuation=40, stream=False),
    Sound("event.ch2l2.maw_burst", "Chapter 2 Level 2", "The grate over the pipe's mouth torn off as Stone Maw comes out: iron screaming, three bolts shearing, the grate flung and clanging, wall crumbling (2.6 s)",
          "mono", False, 1, ch2l2_maw_burst, -1.0, attenuation=72),
    Sound("event.ch2l2.maw_collapse", "Chapter 2 Level 2", "Part of the arena's ceiling coming down on Stone Maw: stone cracking and groaning, then a very low blow and a roar of rubble thinning into settling stones (3.6 s)",
          "mono", False, 1, ch2l2_maw_collapse, -1.0, attenuation=80),
    Sound("event.ch2l2.maw_pillar_ram", "Chapter 2 Level 2", "Stone Maw's charge ending against a pillar: a deeper, tighter and heavier blow than the wall's, a block of masonry shaking, one hard crack and dust sifting (2.8 s)",
          "mono", False, 1, ch2l2_maw_pillar_ram, -1.0, attenuation=72),
    Sound("event.ch2l2.maw_pillar_crack", "Chapter 2 Level 2", "A pillar cracking after a ram: masonry grinding as a seam opens, a quickening run of dry cracks, a groan of stone under load and grit trickling (1.8 s)",
          "mono", False, 1, ch2l2_maw_pillar_crack, -2.0, attenuation=64),
    Sound("event.ch2l2.maw_pillar_break", "Chapter 2 Level 2", "A pillar giving way: a long splitting groan, the column shearing, then its stone falling in a dense roar of rubble that thins into stones settling (4 s)",
          "mono", False, 1, ch2l2_maw_pillar_break, -1.0, attenuation=80),
    Sound("event.ch2l2.maw_debris_warn", "Chapter 2 Level 2", "A piece of the ceiling about to fall on a spot of the floor: a small dry crumble, chips ticking, grit sifting (0.9 s)",
          "mono", False, 1, ch2l2_maw_debris_warn, -4.0, attenuation=32),
    Sound("event.ch2l2.maw_escape_rumble", "Chapter 2 Level 2", "The arena caving in during the escape: a very low wide rumble that swells and shudders, deep groans, far heavy impacts and grit, rising to the end (8 s)",
          "mono", False, 1, ch2l2_maw_escape_rumble, -2.0, attenuation=80),
    Sound("event.ch2l2.lift_gate", "Chapter 2 Level 2", "The service lift's iron gate shut: it rattles along its track, slams into the post with an iron clang and the latch drops (1.7 s)",
          "mono", False, 1, ch2l2_lift_gate, -2.0, attenuation=48),
    Sound("event.ch2l2.lift_motor", "Chapter 2 Level 2", "The service lift starting: a relay clunk, an electric motor winding up into a low whine, the cable creaking under tension (4 s)",
          "mono", False, 1, ch2l2_lift_motor, -3.0, attenuation=48),
]
