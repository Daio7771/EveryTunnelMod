package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.door.SlidingDoor
import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Level 5, the laboratory, built in play. It has a space of its own, away from the other levels, but it
 * is built anew each time it is needed, so that every game finds it as it was made:
 *
 * - Walking into Level 4's dark stretch (a zone tagged [BUILD_TAG]) starts the build: `garage:level5_begin`
 *   force-loads its area, and once the chunks are there (`/everytunnel labready`, [chunksReady]) one
 *   function `garage:level5_step_<n>` runs every [STEP_TICKS], in order, until there is no next. The steps
 *   put back the level's air as well, so a build over an older one leaves nothing of it. The player walks
 *   the stretch meanwhile, then the way in, built with Level 4: a steel frame, a dark corridor, the airlock.
 * - **The airlock** (a zone tagged [AIRLOCK_TAG]): walking in slides the door behind shut (the zone tagged
 *   [REAR_TAG]) with a hiss, never on someone standing in it; stepping back out before it is shut opens it
 *   again. Once the level stands, after a moment in the dark, the door ahead (tagged [FRONT_TAG], in the
 *   Lobby's wall) slides open slowly onto the lit Lobby, and the level's title shows. Each door is two leaves of [AirlockDoorBlock]
 *   meeting in the middle, moved a quarter of a block a step ([SlidingDoor]).
 *   Then the level's loot goes out ([LootSpawner]).
 *
 * What stands is not undone when the world opens: nobody can reach it until its next build. A game
 * continued in Level 5 builds it behind the loading screen ([beginAgain], [ContinueGame]); the airlock is
 * Level 4's then, and is not there.
 */
object LabHooks {

    const val BUILD_TAG = "lab_build"
    const val AIRLOCK_TAG = "lab_airlock"
    const val REAR_TAG = "lab_airlock_rear"
    const val FRONT_TAG = "lab_welcome"

    /** The level built in play. */
    private const val LEVEL_ID = "level_5"
    private const val BEGIN = "garage:level5_begin"
    private const val STEP_PREFIX = "garage:level5_step_"

    /** Ticks between steps: a step is at most what a tick can do unnoticed (see the generator). */
    private const val STEP_TICKS = 2

    /** Ticks a quarter-block step takes: the door behind fast, the one ahead slowly. */
    private const val REAR_STEP_TICKS = 3
    private const val FRONT_STEP_TICKS = 8

    /** In the dark between the two doors. */
    private const val PAUSE_TICKS = 30

    private enum class Phase { NOT_STARTED, WAITING_FOR_CHUNKS, BUILDING, BUILT }

    private enum class Airlock { WAITING, CLOSING, SHUT, OPENING, OPEN }

    // Server thread only.
    private var phase = Phase.NOT_STARTED
    private var nextStep = 0
    private var startTick = 0L
    private var server: MinecraftServer? = null

    /** A new game came while the build ran: the level it leaves belongs to the old one, build it again next time. */
    private var stale = false

    /** Players sent to the level's start once it stands (`/everytunnel setlevel 5`). */
    private val sendToStart = mutableSetOf<UUID>()

    private var airlock = Airlock.WAITING

    /** How far open the door being moved is, in quarters of a block per leaf ([SlidingDoor]): 0 is shut. */
    private var slide = 0
    private var airlockTick = 0

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            phase = Phase.NOT_STARTED
            stale = false
            sendToStart.clear()
            airlock = Airlock.WAITING
        }
        LevelTriggers.register { change ->
            val tags = change.enteredZones.flatMap { key -> LevelManager.get(key.levelId)?.zone(key.zoneId)?.tags ?: emptyList() }
            val server = server ?: return@register
            val player = server.playerManager.getPlayer(change.playerId) ?: return@register
            if (player.isSpectator || LoadingHooks.isLoading(player)) return@register
            if (BUILD_TAG in tags && phase == Phase.NOT_STARTED) begin(server)
            if (AIRLOCK_TAG in tags && airlock == Airlock.WAITING) enterAirlock(server)
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    private fun begin(server: MinecraftServer) {
        phase = Phase.WAITING_FOR_CHUNKS
        stale = false
        startTick = server.ticks.toLong()
        if (!run(server, BEGIN)) {
            EveryTunnel.LOGGER.warn("Function {} not found: Level 5 is not built", BEGIN)
            phase = Phase.NOT_STARTED
        }
    }

    /** A game continued in Level 5: up it goes, for [ContinueGame.labBuilt]. False if it cannot. */
    fun beginAgain(server: MinecraftServer): Boolean {
        if (phase != Phase.NOT_STARTED) return false
        begin(server)
        return phase != Phase.NOT_STARTED
    }

    /** `/everytunnel setlevel 5`: builds the level if it does not stand, then puts [player] at its start. */
    fun buildFor(player: ServerPlayerEntity): Boolean {
        sendToStart += player.uuid
        if (phase == Phase.NOT_STARTED) begin(player.server)
        return phase != Phase.NOT_STARTED
    }

    /** `/everytunnel labready`: the chunks are loaded, the steps can start. */
    fun chunksReady() {
        if (phase != Phase.WAITING_FOR_CHUNKS) return
        phase = Phase.BUILDING
        nextStep = 0
    }

    private fun tick(server: MinecraftServer) {
        tickAirlock(server)
        if (phase != Phase.BUILDING || server.ticks % STEP_TICKS != 0) return
        if (run(server, STEP_PREFIX + nextStep)) {
            nextStep++
            return
        }
        EveryTunnel.LOGGER.info(
            "Level 5 built in {} steps, {} s", nextStep, "%.1f".format(java.util.Locale.ROOT, (server.ticks - startTick) / 20.0),
        )
        if (stale) {
            phase = Phase.NOT_STARTED
            stale = false
            return
        }
        phase = Phase.BUILT
        val level = LevelManager.get(LEVEL_ID) ?: return
        LootSpawner.place(server, level)
        ContinueGame.labBuilt(server)
        for (id in sendToStart) server.playerManager.getPlayer(id)?.let { toStart(it, level) }
        sendToStart.clear()
    }

    /** The map was rebuilt when the world opened: the way in is gone with Level 4, and the level waits. */
    fun levelsRebuilt() {
        phase = Phase.NOT_STARTED
        airlock = Airlock.WAITING
        LabDoorHooks.reset()
    }

    /** A new game in the same world: the next walk down Level 4's dark stretch builds it again. */
    fun newGame() {
        if (phase == Phase.WAITING_FOR_CHUNKS || phase == Phase.BUILDING) stale = true else phase = Phase.NOT_STARTED
        sendToStart.clear()
        airlock = Airlock.WAITING
        LabDoorHooks.reset()
    }

    /** [level] is the one built in play, and it does not stand yet. */
    fun notBuiltYet(level: LevelDefinition): Boolean = level.id == LEVEL_ID && phase != Phase.BUILT

    // --- the airlock ---------------------------------------------------------------------

    private fun enterAirlock(server: MinecraftServer) {
        // Someone who came another way (a teleport) still gets a level to open onto.
        if (phase == Phase.NOT_STARTED) begin(server)
        airlock = Airlock.CLOSING
        slide = doorBox(REAR_TAG)?.let { (_, box) -> SlidingDoors.steps(box) } ?: return
        airlockTick = 0
        doorBox(REAR_TAG)?.let { (world, box) -> world.playSound(null, SlidingDoors.centre(box), ModSounds.AIRLOCK_CLOSE, SoundCategory.BLOCKS, 1.5f, 1f) }
    }

    private fun tickAirlock(server: MinecraftServer) {
        airlockTick++
        when (airlock) {
            Airlock.CLOSING -> {
                if (airlockTick % REAR_STEP_TICKS != 0) return
                val (world, box) = doorBox(REAR_TAG) ?: return
                val inside = doorBox(AIRLOCK_TAG)?.let { (_, room) -> SlidingDoors.playersIn(world, room) } ?: false
                if (!inside) {
                    // Back out before it shut: it opens again, and walking in starts over.
                    slide++
                    SlidingDoors.set(world, box, slide)
                    if (slide >= SlidingDoors.steps(box)) airlock = Airlock.WAITING
                    return
                }
                // Never shut on someone: the leaves wait until the doorway is clear.
                if (SlidingDoors.playersIn(world, box)) return
                slide--
                SlidingDoors.set(world, box, slide)
                if (slide == 0) {
                    airlock = Airlock.SHUT
                    airlockTick = 0
                }
            }
            Airlock.SHUT -> {
                if (phase != Phase.BUILT || airlockTick < PAUSE_TICKS) return
                val (world, box) = doorBox(FRONT_TAG) ?: return
                airlock = Airlock.OPENING
                slide = 0
                airlockTick = 0
                world.playSound(null, SlidingDoors.centre(box), ModSounds.AIRLOCK_OPEN, SoundCategory.BLOCKS, 1.5f, 1f)
                // The level's title comes with the light (its entry is `manual`).
                val level = LevelManager.get(LEVEL_ID) ?: return
                val room = doorBox(AIRLOCK_TAG)?.second ?: return
                for (player in world.getEntitiesByClass(ServerPlayerEntity::class.java, SlidingDoors.toBox(room)) { !it.isSpectator }) {
                    LevelEntryHooks.announce(player, level)
                }
            }
            Airlock.OPENING -> {
                if (airlockTick % FRONT_STEP_TICKS != 0) return
                val (world, box) = doorBox(FRONT_TAG) ?: return
                slide++
                SlidingDoors.set(world, box, slide)
                if (slide >= SlidingDoors.steps(box)) airlock = Airlock.OPEN
            }
            Airlock.WAITING, Airlock.OPEN -> Unit
        }
    }

    private fun doorBox(tag: String): Pair<ServerWorld, BlockBox>? {
        val server = server ?: return null
        val level = LevelManager.get(LEVEL_ID) ?: return null
        val zone = level.zones.firstOrNull { tag in it.tags } ?: return null
        val world = worldOf(server, level) ?: return null
        return world to zone.box
    }

    // --- helpers -----------------------------------------------------------------------

    private fun toStart(player: ServerPlayerEntity, level: LevelDefinition) {
        val start = level.spawnPoints.firstOrNull { RespawnHooks.TAG in it.tags } ?: return
        val world = worldOf(player.server, level) ?: return
        player.stopRiding()
        player.teleport(world, start.position.x, start.position.y, start.position.z, start.yaw, start.pitch)
    }

    private fun worldOf(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    /** Runs a function silently. False if there is no such function. */
    private fun run(server: MinecraftServer, id: String): Boolean {
        val functions = server.commandFunctionManager
        val function = functions.getFunction(Identifier(id)).orElse(null) ?: return false
        functions.execute(function, server.commandSource.withSilent())
        return true
    }
}
