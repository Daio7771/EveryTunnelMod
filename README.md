# Every Tunnel

Singleplayer psychological horror mod for Minecraft (Fabric, Kotlin), set in the
abandoned underground "Noname Complex". You are the new night-shift security guard.

Current state: **Phase 0** (project skeleton only, no gameplay).

## Versions

| Component              | Version                    |
|------------------------|----------------------------|
| Minecraft              | 1.20.1                     |
| Fabric Loader          | 0.19.5                     |
| Fabric API             | 0.92.12+1.20.1             |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21       |
| Kotlin                 | 2.4.21                     |
| Mappings               | Yarn 1.20.1+build.10       |
| Fabric Loom            | 1.17.21                    |
| Gradle                 | 9.8.1 (wrapper)            |
| Java (mod bytecode)    | 17                         |
| Java (runs Gradle)     | 21                         |

Versions live in `gradle.properties` (plus Loom/Kotlin plugin versions in
`build.gradle.kts`).

### Required JDKs

- **JDK 17**: Minecraft 1.20.1 and the compiled mod target Java 17.
- **JDK 21**: Loom 1.11+ requires Gradle itself to run on Java 21. This is
  declared in `gradle/gradle-daemon-jvm.properties`, so `./gradlew` finds an
  installed JDK 21 automatically even when your default `java` is 17.

## Project structure

```
src/main/kotlin/daio7771/everytunnel/     common code (server + client)
    EveryTunnel.kt                        main entrypoint (ModInitializer)
    core/        LevelManager, PlayerState                    (later phases)
    systems/     ResourceSystem, NoiseSystem, LookDetection   (later phases)
    entities/    Pale man, ...                                (later phases)
    audio/       SoundManager                                 (later phases)
    world/       LevelTriggers, LootTables                    (later phases)
src/main/resources/
    fabric.mod.json, every_tunnel.mixins.json
src/client/kotlin/daio7771/everytunnel/client/   client-only code
    EveryTunnelClient.kt                  client entrypoint (ClientModInitializer)
    EveryTunnelDataGenerator.kt           datagen entrypoint (empty)
    hud/         custom HUD                                   (later phases)
    effects/     client visual/audio effects                  (later phases)
src/client/resources/
    every_tunnel.client.mixins.json
```

Each planned package contains a `Package.kt` file that only documents what will
go there. Main and client code are in separate source sets (Loom
`splitEnvironmentSourceSets`), so client-only classes cannot be referenced from
common code by accident.

## Running

```bash
./gradlew build                # compile + produce build/libs/every_tunnel-0.1.0.jar
./gradlew runClient            # launch Minecraft with the mod (title screen)
./gradlew runClientDevWorld    # launch straight into the "dev_flat" world (see below)
./gradlew runServer            # dedicated server (not a target, sanity check only)
```

The log should contain:

```
[Render thread/INFO] (Every Tunnel) Every Tunnel loaded
[Render thread/INFO] (Every Tunnel) Every Tunnel loaded (client)
```

The game directory for dev runs is `run/` (git-ignored).

## Superflat development world

Development uses a plain vanilla superflat world (no custom world generation).
One-time setup:

1. `./gradlew runClient`
2. Singleplayer -> Create New World.
3. World Name: **`dev_flat`** (exactly, the run config loads this folder name).
4. Game Mode: Creative (convenient while building tests).
5. More World Options -> World Type: **Superflat**. Allow Commands: ON.
6. Create the world, then optionally run once:
   ```
   /gamerule doMobSpawning false
   /gamerule doDaylightCycle false
   /gamerule doWeatherCycle false
   /time set midnight
   ```
7. Quit.

The world is saved as `run/saves/dev_flat/`. From then on:

- `./gradlew runClientDevWorld`, or
- IntelliJ run configuration **"Minecraft Client (dev_flat world)"** (generated
  by Loom on Gradle sync).

Both pass `--quickPlaySingleplayer dev_flat` and skip the menus. To reset the
world, delete `run/saves/dev_flat/` and repeat the setup.

The real hand-built map (Noname Complex) will later be another save in
`run/saves/`; the same quick-play mechanism works for it.
