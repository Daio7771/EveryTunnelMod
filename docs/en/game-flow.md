# Game flow

*[Versión en español](../es/game-flow.md) · [All pages](README.md)*

The demo plays from the main menu to the end credits. This page follows one playthrough
and names the code behind each step.

```mermaid
flowchart TD
    A[Game starts] --> B[MapWorldInstaller: unpack Noname Complex]
    B --> C[Main menu]
    C -->|BEGIN| D[Loading screen]
    D --> E[Server: run garage:build]
    E --> F[NewGame.start]
    F --> G[Client draws the elevator, fades in]
    G --> H[Elevator intro, 25 s]
    H --> I[Level 1, B1]
    I -->|ramp down| J[Shutter closes, Level 2 title]
    J --> K[Level 2, B2: Pale men]
    K -->|keycard| L[Every light goes out]
    L -->|EXIT door| M[Spiral stairs down to B3]
    M --> P[Level 3, B3: offices, library, bunker]
    P -->|door to B4| N[End of demo: footsteps behind, credits]
    N --> C
    I -.->|death| O[Fade to black, restart level]
    K -.->|death| O
    P -.->|death| O
    O -.-> I
    O -.-> K
    O -.-> P
```

## 1. Start-up

- `client.MapWorldInstaller` runs once as the client starts. If the jar carries the map
  world (`every_tunnel/world/noname_complex.zip`) and `saves/noname_complex` is missing, it
  unpacks it, removes the stored player from `level.dat` and names it "Noname Complex". If
  the world was installed by an older version of the mod, only its datapacks and
  `resources.zip` are replaced.
- `client.ShaderSettings` writes the recommended settings into any Complementary pack in
  `shaderpacks/`, once per pack and settings version (see [Graphics](graphics.md)).
- The Mojang splash screen plays as usual.

## 2. Main menu

`client.screen.MainMenuScreen` replaces vanilla's title screen wherever the game would open
it (`MinecraftClientMixin`): at start-up, after leaving a world, after the credits, and when a
screen closes with no world open (Escape in the options).

- **BEGIN** opens Noname Complex. If the world is missing it says "The map is missing.
  Reinstall the mod."
- **OPTIONS** opens vanilla's options.
- **GUIDE** opens the [player guide](guide/01-how-to-play.md) (`GuideScreen`): one sheet of
  worn white paper, scrolled with the wheel, with paper tabs to jump between the contents and
  each page. It is in the game's language (Spanish for any `es_*`, English otherwise). The
  language itself is changed in OPTIONS.
  Its last tab, **Credits**, rolls the credits (`EndOfDemoScreen.credits`) and comes back.
- **ACHIEVEMENTS** lists the [achievements](#achievements) (`AchievementsScreen`) on the same
  worn paper.
- **EXIT** closes the game.
- Behind it all, a parking hall seen down its aisle from a fixed camera (`MenuScene`), its
  lamps flickering now and then. The logo and options sit on black, which fades out towards
  the right to show the hall. See [Graphics](graphics.md#the-menu-scene).
- **Ctrl+Shift+V** swaps to vanilla's title screen (other worlds, multiplayer, mods) and
  back.
- `MenuMusic` plays `music.menu`, then 15 s of silence, again and again; `MusicTrackerMixin` keeps vanilla music off everywhere.

## 3. Opening the world

Client side (`client.screen.LoadingScreen.kt`, object `LoadingCover`):

- `WorldListEntryMixin` (world list) or `QuickPlayMixin` (quick play) notices that the world
  has levels before vanilla starts reading it.
- From then on one black screen covers everything: vanilla's loading screens
  (`ScreenMixin`) and the resource pack reload splash (`SplashOverlayMixin`). It shows a
  rotating tip (`LoadingTips`, 7 s each) and "Loading...".
- The game is silent: `SoundSystemMixin` scales every volume by `LoadingCover.worldVolume`.

Server side (`platform.LoadingHooks`):

1. When the server starts, every level's `build` function runs once (`garage:build`). The
   function force-loads the map, waits for its chunks, builds both levels and ends with
   `/everytunnel rebuilt`, which puts the loot back (`NewGame.levelsRebuilt`).
2. A joining player is *loading*: frozen, invulnerable, silent, no resource drain, no
   intro. The client is sent state `0`.
3. Once the build has reported back, a Survival or Adventure player gets a **new game**
   (`NewGame.start`). Creative and Spectator players are left where they are.
4. The light is sent again. The build runs while the player joins, so the chunks they
   were sent can carry light from before it (the elevator once had sky light and no lamp).
   After at least 1 s, once the light engine has no updates left (at most 10 s), the light
   of every loaded chunk in the view distance is sent again (`resendLight`).
5. The client is sent state `1` (ready). It waits until the terrain around the player is
   drawn, fades picture and sound in together, and answers `loading_done`.
6. The player is free and the intro starts.

Safety nets: a build that never reports back is given up after 120 s; a client that
never answers is let go after 30 s.

## 4. A new game

`platform.NewGame.start` resets the world and the player:

- **World:** every shutter opens (`LevelEntryHooks.openAll`), all loot is back
  (`LootSpawner.restock`), the lights the keycard put out are back and the card readers
  locked (`KeycardHooks`), and every Pale man returns to his spawn point
  (`PaleManSpawner.startNewGame`; those in unloaded chunks when their chunk loads).
- **Player:** empty inventory, full health and resources, Survival, at the start of the
  first level with an intro, and the intro plays again (`IntroHooks.replay`).

`/everytunnel newgame` does the same at any time.

## 5. The elevator intro

`platform.IntroHooks`, driven by the level's `intro` (see [Levels](levels.md)):

- The player stands in the zone tagged `start` (the service elevator). The doors (zones
  tagged `elevator_door`) are closed with `ElevatorDoorBlock`s.
- The client plays the intro song (`music.elevator`, Kevin MacLeod's "Local Forecast –
  Elevator") through `IntroMusic`. The floor indicators near the doors count down
  3, 2, 1, G, B1 (`FloorIndicatorBlock`).
- After `intro.seconds` (25): the bell (`block.elevator.ding`), the song slows down, wavers
  and fades over 3 s like a tape running down, and the doors slide open in stages
  (`block.elevator.doors`).
- The level's `entry` title shows ("Level 1", `LevelEntryHooks.announce`).

The world remembers who has had the intro (`every_tunnel_intro.dat`), so dying later
brings the player back to the elevator with the doors open.

## 6. Level 1 (B1)

No enemies. Five parking halls that get worse as you go, side rooms with loot, ambience
(drone, drips, relays, rumbles), humming and flickering fluorescent lights. The player
learns the flashlight, stamina, thirst and how loud things are. A ramp at the end leads
down.

## 7. Into Level 2

`platform.LevelEntryHooks`: walking into a level with a higher `order` than the one you
came from:

- Shows its `entry` title ("Level 2") and plays its sound in the player's head.
- Closes every zone of the new level tagged `shutter` once the player is past it: the
  empty blocks fill with `ShutterBlock`s row by row from the top, with a rattle and a slam
  (`block.shutter.close`). There is no way back.

Reopening the world or respawning inside a level does not count as walking in.

## 8. Level 2 (B2)

- Five darker halls. `PaleManSpawner` puts a Pale man at every `pale_man` spawn point the
  first time the player enters: one per hall, two in hall 5, each kept in his hall by the
  spawn point's `zone`. See [Noise and the Pale man](noise-and-pale-man.md).
- **The keycard** lies on the counter of a glass booth in hall 5 (`LootBlock` at a spawn
  point tagged `keycard`). Picking it up (`KeycardHooks.pickedUp`) puts out every light
  still on in B2 in a wave spreading from the player, 0.75 blocks per tick, the nearest
  ones heard powering down; only the lamps of the stair shaft stay on (zone tagged
  `lights_stay`).
- **The exit:** an EXIT door by hall 3 with a `CardReaderBlock`. Without the card it
  buzzes "It needs a keycard"; with it, it beeps, turns green and opens the iron doors
  next to it (zone tagged `keycard_door`), and everyone there earns "Employee of the Month"
  (see [Achievements](#achievements)).
- **The stairs to B3:** behind the door, a round shaft of metal panels with grating stairs
  spiralling down 4 turns (32 blocks, half a block per step) around an open well, railed
  with iron bars and lit by a wall lamp every half turn. At the bottom, a small landing
  and an open door under the "B3" sign: the way into Level 3.

## 9. Level 3 (B3)

The offices of the complex around the central library, in perfect order and empty. Built
so far: the structure, the furniture, the valve puzzle, the lockers, the box and book
puzzles that open the bunker, the Observer, the company's documents, the loot, the room
nameplates and the bunker tunnel with its collapse (see [Levels](levels.md#level-3-hooks)).
There is no music of its own (one is ready for Level 4).

The **ambience** is the near-silence of an empty office building (`ambient.offices.room_tone`)
with, rarely (every 1.5 to 4 minutes), a phone ringing somewhere far off and stopping
mid-ring (`ambient.offices.phone`); the library has a slow clock ticking from nowhere in place
of the room tone (`ambient.library.clock`, the library zone's own ambience), and the bunker is
silent.

- Behind the open door, stairs climb down 8 blocks to the **antechamber**, the electrical
  room; over the way into it, big dark letters on the wall read "OFFICES & LIBRARY"
  (`ENTRANCE_SIGN`). Walking into it shows "Level 3" (the stairs belong to no zone); its spawn point
  tagged `player` is there. On its wall, a cork board with the electrician's note
  (`NoteBoardBlock`): right-clicked, it opens on paper (`NoteScreen`) and tells, in words,
  how many full turns each valve needs.
- North, the **valve corridor**: five valves (A to E) on its east wall, each a `ValveBlock`
  inline in a pipe, then a wall with a closed double emergency door, the **checkpoint**.
- **The valve puzzle** (`ValveHooks`): a new game draws a digit 0 to 9 for each valve and
  puts every valve on 0. A right-click turns a valve once more (from 9 back to 0), with a
  clunk and water in the pipe; the plate on each side shows its letter and its turns. When
  all five show their digits, a water hammer bangs through the pipe, the valves lock open
  and the checkpoint's doors (zone tagged `valve_door`) open, and "Under Pressure" is
  earned. Getting it wrong costs
  nothing. Dying keeps what is solved; only a new game sets it back.
  `/everytunnel valves` shows the digits.
- Past it, through the belt of 17 rooms, the **inner corridor**: a ring round the
  **central library** (64 × 64, 12 high, lit by lights hanging on chains). Every light is
  on; the surfaces are clean. Beside each room's door, a dark nameplate with its name in
  white (`WallTextBlock`, in the game's font and language: `sign.every_tunnel.room.<id>`).
- Every room is furnished for what it is, tidy and no more than it needs (desks with their
  chairs and phones, filing cabinets, server racks, lockers, the cafeteria's tables...); the
  library has rows of bookshelves four high, two blocks apart, round a clearing, and in it
  the librarian's desk in a U round the hatch, open to the south, a brass lamp
  with a green shade on it (`desk_lamp`). Nobody is there.
- **Loot:** no bottles in Level 3. The cafeteria's water dispenser gives water: right-clicked,
  the player drinks there, as often as they like, a bottle's worth each time (at most once
  every 1.5 s; a noise like drinking a bottle), `WaterDispenserHooks`, the dispenser at the
  spawn point tagged `water_source` (`WATER_SOURCES`; the other dispensers are only
  furniture). Three batteries lie on the floor of the Electrical room by the ring (`LOOT`,
  spawn points tagged `battery`, put there by `LootSpawner` like the garages' loot).
- **The company's documents** (the lore): five loose typed sheets (`NotePaperBlock`), each
  on the desk or counter nearest the doors of its room (`NOTE_PAPERS`): a memo on the
  reading room's supervision (HR), the archive's procedure for boxes and books (Archive), a
  notice on the lockers (Lockers), a staff complaint about the machine brought down
  (Management) and the invoice for a reactor (Finance). Read like the electrician's note
  (`NoteScreen`). Formal, no humour; the first three hint at the Observer and the puzzles.
- **The box puzzle** (`BoxHooks`): a new game puts ten purple archive boxes (`PurpleBoxBlock`,
  drawn full bright) at ten of the level's 25 spots tagged `box_spot`, drawn at random: one in
  every room but Storage, on a desk, counter or cabinet or on the floor against a wall, and
  nine round the ring. Right-click picks one up into the hand: one at a time; while carried it
  stays in the hand (changing slot moves it along), it cannot be thrown away (it comes
  straight back) and the player cannot sprint; hiding in a locker with it is fine. In Storage,
  its other shelves only along the walls (the middle open, the door in plain sight from the
  box shelf), an empty metal shelf, 2 wide and 3 high with five boards (`BoxShelfBlock`, spawn point tagged
  `box_shelf`; the boxes on it drawn by `BoxShelfRenderer`): right-clicking a place on it puts
  the box there, "3 / 10" shows above the hotbar, and the Observer is alerted (he comes running;
  far off, something is heard noticing, `event.observer.alert`). With all ten up, "Master of the Boxes"
  is earned and the books appear. Carrying follows `CarryHooks` (for the books too). Dying
  puts a carried box back where it was found; the shelf keeps its boxes. `/everytunnel boxes` tells where they are, `boxes fill`
  fills all but the last.
- **The Observer** (`ObserverEntity`, decisions in `entities.ObserverBrain`, spawned by
  `ObserverHooks` at the spawn point tagged `observant_guard`): a tall guard in black with a
  blank grey head (`tools/entity/observer.py`). He sees; he does not hear footsteps. He
  patrols the library (the zone tagged `observer_home`), walking stiffly between the shelves
  and stopping to look round, his head sweeping from side to side. He sees only into the library: a player out
  in the ring or a room is safe from him, unless a box sent him out (then he sees anywhere until
  he is back on patrol) or he is already after them (a chase or a search goes on outside). He
  sees a player in a 90° cone round where his head faces, up to 24 blocks (12 sneaking, 32 with the flashlight
  on), with nothing solid in between, and anyone within 2 blocks whatever way he faces;
  never a player hidden in a locker. Seeing one, he first walks towards them; once they have
  stayed in his sight for 1.5 s, or are within 6 blocks, he runs, a little faster than a
  sprint, and a caught player dies. Out of his sight for 1.5 s, he goes to where he saw them
  last (running if he was), looks round and walks home: a player just spotted has a moment to
  sneak out of his range, duck behind a shelf or get into a locker unseen. A box on Storage's shelf brings him running there, faster
  still, and "He's coming. Hide." shows in big dark red letters; he looks round and runs home
  (only after a box; after a search he walks).
  Storage's locker stands right beside the shelf (he comes in by the door). A player he sees get into a locker is pulled out and
  dies. He makes no sound of his own, not even footsteps; walking your way he
  brings a slow clock and a drone (`music.observer.approach`), running at you the clock gone
  mad (`music.observer.chase`). A new game or a death sends him back to his spawn point. Starting numbers, to tune by
  playing (`ObserverSettings`; the log says how fast each run really was, "[observer] ran ...").
- **Lockers to hide in:** every room has at least one (the Lockers and Changing rooms are
  full of them; the library has none). Right-clicking a locker from in front of its door
  gets in (`HidingHooks`): the player rides an invisible seat on the locker's floor
  (`LockerSeatEntity`), facing out, the head turning at most 30° to the sides and 15° up or
  down (`core.hiding.LookLimit`), the screen covered by the inside of the door with its
  vent's slits (`client.hud.LockerView`). Inside, the player makes no noise at all. Right-
  clicking again or sneaking gets out in front of the door; both ways the door's sound plays
  (`block.locker.door`, not a noise for enemies). A new game takes everyone out.
- **The book puzzle** (`BookHooks`): with the boxes done, five office books (`OfficeBookBlock`,
  blue-grey, drawn full bright) appear, silently, in the library, his ground: at five of its 13
  spots picked at random (spawn points tagged `book_spot`): on book carts (`book_cart`) and on
  the floor of the aisles between the shelves, and one on the card catalogue by the south door
  (`LIBRARY_LAYOUT["books"]`). They are carried like the boxes and alert no
  one. Right-clicking the librarian's desk round the hatch (or a book already on it) hands
  one in: it lies at the next of five places spread along the desk (tagged `book_return`), and
  "2 / 5" shows. The fifth opens the hatch by itself (the zone tagged `book_hatch`: its steel
  leaves swing up with `block.hatch.open`) and "Shhh!" is earned. Dying puts a carried book
  back where it was found; only a new game takes the books away and shuts the hatch.
  `/everytunnel books` tells where they are, `books fill` finishes the boxes and puts the
  books out with all but one already on the desk.
  While the books are out (`BookPhaseHooks`, rules in `systems.ObserverNearness`), the
  library's clock ticks in the player's head whenever the Observer is within 24 blocks, tick
  and tock, every 0.75 s at the edge down to every 0.25 s within 4 blocks, louder as he nears
  (`event.observer.tick` / `tock`); and when he first comes within 16 blocks, "He's near.
  Hide." shows like the box warning (again only once he has been farther than 24; not while
  he is already after the player).
- In the library's centre, the **bunker hatch**: two leaves of dark steel checker plate
  (`bunker_trapdoor`, a trapdoor nobody opens by hand) with a yellow and black band along their
  hinges, hinged on the west and east sides, in a frame of checker plate,
  shut until the books are back. Under it a ladder goes down a shaft to the **bunker tunnel**:
  about 90 blocks of lit concrete, 3 wide, in four legs at right angles (`BUNKER["path"]`),
  pipes along the ceiling on one side and a cable tray on the other, through two open blast
  doors (a hazard-striped steel frame, `hazard_steel`, and the leaf folded back against the
  wall, `blast_door`), to the door to B4, where the demo ends. The Observer never follows
  anyone down (the shaft and tunnel are tagged `observer_never`).
- **The collapse** (`CollapseHooks`, timing in `systems.CollapseSchedule`): about 13 blocks
  before the door, the tunnel's ceiling comes down behind the player: a groan, cracks and the
  crash of the fall (`event.collapse`), the camera shaking (`CameraShake`), the stretch behind
  filling with rubble over a second and a half (the tunnel's concrete and cobbled deepslate),
  dust falling and the lights round about flickering. No way back; once per game. A death in
  Level 3 (the player starts again at the antechamber), a new game and the map's rebuild clear
  the rubble.

## 10. The end

`platform.EndingHooks`: at the end of Level 3's bunker corridor, in front of the door to
B4, the player walks into a zone tagged `end`.

1. Five bare footsteps come down behind the player, each a little closer and lower (the
   Pale man's own step sound).
2. The screen fades to black over 2.5 s.
3. The server sends `ending`; the client opens `EndOfDemoScreen`: "End of demo" fades in,
   then the credits roll (the CC BY music credit among them) until "Thank you for playing."
4. Any key after a moment, Esc, or a timeout saves the world and returns to the main menu.

Opening the world again starts a new game, as always.

## Death

`platform.DeathHooks`: while the world rules apply, a player who would die stays alive
instead:

1. `event.death` plays and the screen fades to black (`screen_fade`, 1.5 s). The player
   cannot move and nothing hurts them.
2. In the dark they are moved to the start of the level they were last in (its first spawn
   point tagged `player`), with full health and resources. That level's Pale men go back
   to their spawn points and start over.
3. The screen fades back in.

The inventory is kept: what was picked up stays picked up. A player who never entered a
level dies the vanilla way. `RespawnHooks` also sets the vanilla respawn point to the
current level's start, as a fallback.

## Achievements

Eight achievements (`core.achievement.Achievement`, in list order). The server says when one
is earned (`platform.AchievementHooks`, only in worlds with levels), once per game: earning
it again in the same game (a second death to a Pale man) shows nothing, and a new game lets
them all come again. The client shows the "Achievement Unlocked!" toast, drawn like vanilla's
challenge toast with its fanfare
(`client.hud.AchievementToast`), and remembers it for good in
`config/every_tunnel/achievements.json` (`client.AchievementStore`), across games and worlds.
The vanilla advancements are not touched.

| Achievement | Earned when | Icon |
|---|---|---|
| First Day at Work | walking out of the elevator (the zone tagged `start` of a level with an intro) | the guard's badge |
| Stay Hydrated | drinking a water bottle (`DrinkHooks`) | the water bottle |
| Beware of Tall Men | walking into Level 2 (`AchievementHooks.LEVEL_ENTRIES`) | the Pale man's silhouette |
| Ouch! | killed by a Pale man (`DeathHooks`) | a skull |
| Employee of the Month | the keycard opens the exit's reader (everyone in the world) | the keycard |
| Under Pressure | Level 3's valves are right (everyone in the world) | the valve |
| Master of the Boxes | Level 3's ten boxes are on Storage's shelf (everyone in the world) | the purple box |
| Shhh! | Level 3's five books are back on the librarian's desk (everyone in the world) | the office book |

The ACHIEVEMENTS screen in the main menu (`client.screen.AchievementsScreen`) lists them on
the guide's paper (`client.screen.Paper`) with how many are earned; one not earned yet shows a
"?" and "Not yet unlocked". The texts are in the lang file (`achievement.every_tunnel.<id>`, its
`.description`, `toast.every_tunnel.achievement_unlocked`); the badge, skull, silhouette and
"?" icons come from `tools/gui/achievement_icons.py`, the others are the mod's items
(`client.AchievementIcons`). An own sound
in place of the fanfare is planned.
