"""
Chapter 2's Python-generated sounds (its prologue and its Level 1), apart from the base game's (`catalog.py`), with the same
tools and the same rules: one generator per sound, taking (rng, variant) and returning a mono or stereo array, seeded from
the sound id and the variant. `catalog.py` adds [CHAPTER2_SOUNDS] to its list, so `generate.py` and `export.py` see one
catalog. A later chapter gets a file like this one.
"""

from catalog import *  # noqa: F401,F403  (the base catalog's Sound and its synthesis helpers)
from catalog import _noise, stinger_heard  # noqa: E402


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
]
