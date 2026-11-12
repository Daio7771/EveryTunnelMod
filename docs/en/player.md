# Player systems

*[Versión en español](../es/player.md) · [All pages](README.md)*

Everything on this page applies only to **Survival and Adventure players in a world with
levels** (`WorldRules.appliesTo`). Creative and Spectator are exempt, so the map can be
built by hand.

## World rules

`world.WorldRules` decides; `platform.WorldRulesHooks` enforces:

- **No breaking.** Block attacks are cancelled (no mining progress), and breaking is
  refused on the server as a safety net.
- **No placing or changing blocks with items** (blocks, buckets, flint and steel, bone
  meal, hoes...). Survival players get `allowModifyWorld = false`, the same flag Adventure
  uses. Doors, buttons and levers still work.
- **No vanilla mob spawning.** Spawn gamerules are set to false when the world starts and
  after `/reload`. They are not restored if the levels are removed.
- **No natural regeneration.** The same way: hunger is held full, so health would come back by
  itself. Only food and the beds heal.
- **Difficulty locked on Normal.** On Peaceful vanilla would delete the Pale man the tick
  he spawns, mob hits would deal no damage and health would regenerate.
- **The guard uniform.** Every player's skin is the uniform (`client.render.GuardUniform`,
  `AbstractClientPlayerEntityMixin`), on the wide-armed model, with no cape.

## Resources

`systems.ResourceSystem` simulates three resources per player. It is plain Kotlin: pure
functions of state, input and config (`step`, `toggleFlashlight`, `insertBattery`,
`switchOff`), unit tested. `platform.ResourceHooks` runs it every server tick, saves the
states with the world (`every_tunnel_resources.dat`) and syncs them to the client
(`ResourceSyncPacket`, see [Architecture](architecture.md#packets)).

### Safe levels

In a level marked `safe` (Level 4; `platform.SafeLevelHooks`) nothing goes wrong: nothing hurts
but `/kill` and the void, thirst and the battery stay as they are, and sprinting and jumping cost
no stamina, which keeps recovering. The HUD dims the thirst and battery rows. A bed in the Suites
(`platform.SuiteBedHooks`) fades to black in silence and fills health and stamina; it is not a
save, and it changes nothing else.

### Stamina

- Drains while sprinting (12/s) and per jump (8). Regenerates at 15/s after 1.5 s without
  use.
- At 0 the player is **exhausted**: no sprinting until stamina is back to 30%. The client
  enforces it (`ClientPlayerEntityMixin` makes `canSprint` false), because sprinting is
  decided on the client.
- Nobody sprints in a zone tagged `no_sprint` (Level 5's entry corridor): the server stops it and the
  client holds back the same way.
- While exhausted: heavy breathing in the player's head (`client.effects.BreathingEffect`),
  the screen edges darken and swell with each breath (`client.hud.ExhaustionVignette`),
  and panting is a noise for the Pale man once per second.

### Thirst

- Replaces hunger: vanilla food is held full (otherwise a low food level would block
  sprinting with no visible reason).
- Drains 5 per minute (full lasts 20 minutes), twice as fast while sprinting.
- Empty: 1 damage every 10 s ("dehydration", vanilla's `dryOut` damage source).
- Below a threshold the vignette closes in as thirst runs out.

### Drinking

`ModItems.WATER_BOTTLE`, `platform.DrinkHooks`: hold use for 1.5 s to drink. +40 thirst, a
gulp about every half second (`player.drink`, from a CC0 recording;
`LivingEntityMixin` silences vanilla's sipping), and a noise for the Pale man. A full
player cannot drink ("You are not thirsty"). Consumed outside Creative.

### Eating

`ModItems` (`food_plate`, `food_popcorn`, `food_snack`, the same effect in all three),
`platform.FoodHooks`: hold use for 1.5 s to eat, +8 health (4 hearts). A noise like drinking; a
player at full health cannot eat ("You are not hurt"). Consumed outside Creative.

### The flashlight

The flashlight is **built-in equipment, not an item**: every player has one, nothing is
shown in the hand.

- **Key F** (`client.FlashlightControls`, rebindable in Controls → Every Tunnel) sends
  `toggle_flashlight`; the server (`platform.FlashlightHooks`) checks the battery, switches
  it and syncs the result. Clicks play `item.flashlight.on/off` and are a small noise.
- **Battery:** 180 s of light. It drains one second per second while on; at 0 the light
  dies (`item.flashlight.dead`) and switches off.
- **Low battery** (below 20%): the HUD battery row blinks red and the beam flickers now and
  then, more often as it runs out, with a failing-contact "bzzt" (`item.flashlight.flicker`).
- **Changing the battery:** hold use with a battery item for 1.5 s
  (`item.battery.open/close`). Refills 100%. "The flashlight battery is already full" if it
  is.
- The flashlight works in Creative too, but only drains where the rules apply.
- **A dead battery** in a zone tagged `dead_battery` (Level 5's entry corridor, where the only light is the
  Cloud's): walking in, the battery drops to nothing and a flashlight on flickers out as if run down; no battery
  item helps there; walking out, the battery is full again.

The light itself is drawn on the client, see [Graphics](graphics.md#the-flashlight).

## Loot

`platform.LootSpawner` and `platform.LootBlock`:

- The first time a player enters a level, a `LootBlock` is placed at every spawn point
  tagged `battery`, `water`, `keycard`, `food_plate`, `food_popcorn`, `food_snack`, `wrench`, `detector` or a fuse (`fuse_5a`...). The client draws it as the item itself, lying
  flat (`client.render.LootRenderer`); it has no collision. Level 4, built in play, gets its loot
  once it stands.
- **Right-click** picks it up; the spot is empty.
- Each spot is filled once per world (`every_tunnel_loot.dat`): what was picked up stays
  picked up, after dying or reopening the world, until a new game restocks everything.
- The keycard's pickup also triggers the lights-out wave (`KeycardHooks.pickedUp`).

## Level 5's tools

- **The wrench** (`platform.WrenchItem`): a heavy weapon held like a sword. A full blow takes
  `wrench.damage` (7, three and a half hearts) and comes about once a second (`wrench.attacks_per_second`),
  pushes a little, and rings out in metal (`item.wrench.hit`). It never wears out.
- **The radiation detector:** it works from anywhere in the inventory and uses nothing. It notices the
  nearest Distortion Cloud within `detector.range` (16) blocks, never through a wall, and clicks in the
  player's head like a Geiger counter: its counts fall at random, about 1.5 a second at the edge and more with
  each step nearer, up to a crackle of about 120 a second right next to it. Each tick draws how many fall in it
  and plays `item.detector.click` (one), `.clicks` (two to four) or `.crackle` (more)
  (`client.effects.DetectorEffect`, numbers in `core.detector.Detector`). What it notices is any entity of
  the type tag `every_tunnel:radiation_sources` (the Cloud), until it starts to go.

## HUD

`client.hud.StatusHud` draws four vanilla-style rows (10 icons, full or half) where the
vanilla bars were; `InGameHudMixin` hides vanilla health, hunger, armour, air and the
experience bar.

```
 stamina (lungs)   top left            battery           top right
 health (hearts)   bottom left         thirst (drops)    bottom right
 [                          hotbar                          ]
```

- Hearts shake when health is low.
- Lungs turn grey while exhausted.
- The battery row blinks red below the low battery warning.

Icons: `textures/gui/hud_icons.png`, painted by `tools/gui/hud_icons.py`.

While the player carries Level 5's detector, `client.hud.DetectorHud` draws the radiation sign to the right
of the right-hand rows, between them: grey with nothing in range, filled in yellow and black from the bottom
up as a Cloud comes nearer. Within `detector.danger_range` (8) a red warning triangle blinks fast beside it
(`textures/gui/detector_icons.png`, from the same tool).

## Death

See [Game flow](game-flow.md#death): no death screen, a fade to black and a restart at the
level start (or at a restart point reached in it, like Level 5's Lab hall) with full resources, keeping
the inventory.

## The config file

`config/every_tunnel/gameplay.json` (in a dev run: `run/config/...`). Read once at
start-up by `config.ConfigManager`.

- Missing: created with the defaults.
- A bad value: a warning in the log, and the default for that value only.
- Not valid JSON: an error, every default, and the file is left untouched.
- Missing keys (a newer mod version): defaults, and the keys are written into the file,
  but only if it had no other problems, so a file with typos is never overwritten.

Every key and its default:

| Key | Default | Meaning |
|---|---|---|
| `config_version` | 1 | File format version |
| **`stamina`** | | |
| `max` | 100 | Maximum stamina |
| `sprint_drain_per_second` | 12 | Lost per second while sprinting |
| `jump_cost` | 8 | Lost per jump |
| `regen_per_second` | 15 | Regained per second while not sprinting |
| `regen_delay_seconds` | 1.5 | Wait after the last use before regenerating |
| `exhausted_recover_percent` | 30 | Exhausted until stamina is back to this % |
| **`thirst`** | | |
| `max` | 100 | Maximum thirst (full = not thirsty) |
| `drain_per_minute` | 5 | Lost per minute |
| `sprint_drain_multiplier` | 2 | Drain multiplier while sprinting |
| `damage_when_empty` | 1 | Damage when empty... |
| `damage_interval_seconds` | 10 | ...every this many seconds |
| `drink_restore` | 40 | Thirst one bottle gives back |
| `drink_seconds` | 1.5 | Time spent drinking |
| **`food`** | | |
| `heal` | 8 | Health one food gives back |
| `eat_seconds` | 1.5 | Time spent eating |
| **`flashlight`** | | |
| `battery_max_seconds` | 180 | Seconds of light from a full battery |
| `low_battery_warning_percent` | 20 | Below this the HUD blinks and the beam flickers |
| `battery_refill_percent` | 100 | How much one battery refills |
| `battery_change_seconds` | 1.5 | Time spent changing it (0 = instant) |
| `beam_range` | 20 | Beam reach in blocks (1..64) |
| `beam_width_degrees` | 40 | Cone width, edge to edge (1..120) |
| `beam_brightness` | 15 | Light level at the centre of the beam (0..15) |
| `glow_brightness` | 5 | Faint glow around the player (0..15) |
| **`wrench`** | | Level 5's weapon |
| `damage` | 7 | Health a full blow takes (a player has 20) |
| `attacks_per_second` | 1 | Full blows a second |
| **`detector`** | | Level 5's radiation detector |
| `range` | 16 | Blocks within which it notices the Cloud (never through a wall) |
| `danger_range` | 8 | Closer than this the warning triangle blinks |
| `slowest_counts_per_second` | 1.5 | Geiger counts a second at the edge of its range... |
| `fastest_counts_per_second` | 120 | ...and right next to the Cloud (a crackle) |
| **`hunter`** | | Level 5's Hunters |
| `health` | 6 | Their health against anything but the wrench (any blow of the wrench kills one) |
| `damage` | 1 | Health a hit takes (half a heart)... |
| `attack_seconds` | 1 | ...at most every this many seconds each... |
| `attack_range` | 1.4 | ...from at most this many blocks |
| `sight_range` | 16 | Blocks they see, never through a wall... |
| `field_of_view` | 140 | ...in a cone this many degrees wide... |
| `sense_range` | 3 | ...and closer than this whatever way they face |
| `lost_seconds` | 4 | Seconds without news of the one they chase before they search... |
| `search_seconds` | 3 | ...and how long they search before walking back |
| `wander_radius` | 5 | Blocks from their spawn point they wander |
| `wander_pause_seconds` | 3 | Seconds they stand between walks |
| `walk_speed`, `run_speed` | 0.7, 1.5 | Pathfinding speeds (times their base speed) |
| **`cloud`** | | Level 5's Distortion Cloud |
| `grace_seconds` | 1 | Seconds of looking its way before it kills |
| `leave_seconds` | 6 | Seconds with nobody looking its way before it goes |
| `min_seconds`, `max_seconds` | 120, 240 | One comes after a random time between these, spent in zones tagged `cloud` |
| `often_min_seconds`, `often_max_seconds` | 60, 120 | The same, in zones tagged `cloud_often` as well (Level 5's drain), counted apart |
| `nearest`, `farthest` | 8, 14 | Blocks ahead of the player where it appears |
| `touch_range` | 1.2 | Closer than this it kills whatever way one faces |
| `game_grace_seconds` | 1.5 | The entry corridor's game: seconds of looking its way there before it kills |
| `game_behind_blocks`, `game_ahead_blocks` | 8, 8 | Blocks behind and ahead of the player where it lands when it jumps |
| `game_forward_min_seconds`, `game_forward_max_seconds` | 7, 10 | A leg forward, with it behind: a random length between these... |
| `game_back_min_seconds`, `game_back_max_seconds` | 2.5, 3.5 | ...and a leg back, with it in front |
| `game_creep_start`, `game_creep_end` | 2.4, 3.6 | Blocks a second it creeps after the player, at the start and at the end (a walk is about 4.3) |
| `game_hurry` | 0.35 | Share by which the legs are shorter at the end |
| `game_dark_lift` | 0.3 | Without shaders, how far the Brightness is lifted from Moody (0) towards Bright (1) while the game runs; shader packs already give the dark a minimum |
| **`junior`** | | Level 5's Distortion Junior |
| `range` | 8 | Blocks from its eye within which it troubles the player (never through a wall) |
| `stare_seconds` | 4 | Seconds of staring at its eye, without looking away, that end it |
| `aim_degrees` | 5 | Degrees off its eye the crosshair may be (more very close up) |
| `tremor_degrees` | 0.6 | How far the camera trembles |
| `drift_degrees_per_second` | 14 | How fast it drifts away |
| **`noise`** | | Radius in blocks; 0 makes it silent |
| `sneak_radius` | 2 | Per step, sneaking |
| `walk_radius` | 6 | Per step, walking |
| `sprint_radius` | 14 | Per step, sprinting |
| `jump_radius` | 8 | Per jump |
| `land_radius` | 10 | Landing after a 2-block fall (half for small drops, up to twice for long falls) |
| `interact_radius` | 8 | Doors, trapdoors, gates, levers, buttons, chests |
| `equipment_radius` | 4 | Flashlight clicks, battery changes |
| `exhausted_breathing_radius` | 5 | Panting, once per second |
| `drink_radius` | 4 | Each drink |
| **`pale_man`** | | See [Noise and the Pale man](noise-and-pale-man.md) |
| `hearing` | 1 | Multiplies every noise radius he hears |
| `wall_muffle` | 0.6 | Each solid block between multiplies the radius by this |
| `chase_loudness` | 0.5 | Loudness (1 at source, 0 at the edge) that makes him charge |
| `listen_seconds` | 2 | Standing still listening after a first noise |
| `search_seconds` | 4 | Standing alert after losing you |
| `investigate_seconds` | 20 | Walking to a noise before giving up |
| `roam_speed` | 0.6 | Speed multipliers, 1 ≈ 3 blocks/s: wandering |
| `investigate_speed` | 0.8 | Walking to a noise |
| `chase_speed` | 1.6 | Chasing |
| `attack_damage` | 20 | Damage per hit (a player has 20 health) |
| `attack_cooldown_seconds` | 1 | Between hits |
| `attack_range` | 2 | Reach while chasing or searching |
| `touch_range` | 1 | Reach at any time (bumping into him) |

The parser (`config.GameplayConfigParser`) has the allowed range of each value.
