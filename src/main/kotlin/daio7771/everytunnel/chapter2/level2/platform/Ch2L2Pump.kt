package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.level2.core.PumpCycle
import daio7771.everytunnel.chapter2.level2.core.PumpPhase
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.platform.ModBlocks
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.ConnectingBlock
import net.minecraft.entity.FallingBlockEntity
import net.minecraft.particle.BlockStateParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * The pump room's pump and the encounter around it (Chapter 2 Level 2, phase 4), over the data of `level.json`'s `creature`:
 *
 * - **The pump** ([PumpCycle]) runs only while a player who has not got past the encounter (state up to [Ch2L2State.PUMP_ROOM]) is in the
 *   room (`zone_vibration_chamber`, which holds the overlook): it starts running when one comes in. Each run plays its sound at
 *   `pump.anchor`, lights every beacon of `pump.beacons` ([PumpBeaconBlock.LIT]) while it runs, blinks them slowly in the last second,
 *   and lets dust fall from the ceiling round each player in the room. With nobody in the room it stops, dark, and begins again
 *   (running) when someone comes back. While it runs and just after, it masks the player's vibrations ([masks]).
 * - **The introduction**, once per attempt: at the first silence (`pipe.delay_seconds` in) a pipe falls from `pipe.from` (a falling block
 *   that breaks on landing); where it lands it makes a loud [VibrationCreatureBrain.Source.PIPE] vibration ([pipeVibration]). From the
 *   start of that silence until the creature has struck the pipe's point ([pipeStruck]; at most [INTRO_LIMIT_TICKS] after the drop) it
 *   feels nothing else ([introPending]), so the player sees it. A death or CONTINUE to the overlook's checkpoint starts it again.
 * - **The creature** ([VibrationCreatureEntity]) is put at `creature.home` when a player is in the room and none is there (the level's
 *   start takes it away, so a death restores it at rest); getting to [Ch2L2State.CATACOMBS] takes it away and the pump stops: the
 *   encounter is past.
 *
 * Server thread only.
 */
object Ch2L2Pump {

    private val data get() = Ch2L2DataLoader.data

    /** The pump's clock: the tick it began running at, null while it is stopped. */
    private var startTick: Long? = null
    private var cycle: PumpCycle? = null

    /** The first silence of this attempt has not come yet. */
    private var introArmed = true
    private var pipeAt: Long? = null
    private var pipe: FallingBlockEntity? = null
    private var pipeLast: Position? = null
    private var pipeVibration: VibrationCreatureBrain.Vibration? = null
    /** Until when the creature feels only the pipe (server tick), null when it may feel the players. */
    private var introUntil: Long? = null

    private val watchCreature = mutableSetOf<UUID>()
    private val watchPump = mutableSetOf<UUID>()

    private const val ENSURE_EVERY = 20L
    private const val DUST_PER_TICK = 4
    private const val DUST_RADIUS = 8.0
    private const val CEILING_SCAN = 24
    private const val INTRO_LIMIT_TICKS = 120L

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        Ch2L2.onStart { player, state -> started(player, state) }
        Ch2L2.onStateChange { player, from, to ->
            if (to >= Ch2L2State.CATACOMBS) stop(player.server, removeCreature = true)
            else if (from >= Ch2L2State.CATACOMBS) started(player, to)
        }
    }

    // --- what the creature asks -------------------------------------------------------------------

    /** The pump masks a vibration made at server tick [tick]: it ran, or stopped less than the grace ago. */
    fun masks(tick: Long): Boolean {
        val start = startTick ?: return false
        if (tick < start) return false
        return cycle?.masking(tick - start) == true
    }

    /** The pipe's landing, while it is recent; null if the pipe has not landed. */
    fun pipeVibration(): VibrationCreatureBrain.Vibration? = pipeVibration

    /** The introduction is on: the creature feels only the pipe. */
    fun introPending(): Boolean = introUntil != null

    /** The creature has struck the pipe's point: from now on it feels the players. */
    fun pipeStruck() {
        if (introUntil == null) return
        introUntil = null
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the creature struck the pipe: the introduction is over")
    }

    // --- the level starts or the encounter is passed -----------------------------------------------

    /** The encounter as the level starts at [state]: pump stopped, intro armed, pipe and creature gone (the creature comes back when a player is in the room). */
    private fun started(player: ServerPlayerEntity, @Suppress("UNUSED_PARAMETER") state: Ch2L2State) {
        stop(player.server, removeCreature = true)
        introArmed = true
    }

    /**
     * The creature at its home, if none is there: it is never saved, so it is (re)made when a player is in the room, where its chunks
     * are loaded, not when the level starts far away.
     */
    private fun ensureCreature(world: ServerWorld) {
        if (creatures(world).isNotEmpty()) return
        val home = data.anchors.point(data.level.creature.home)
        if (home == null) {
            EveryTunnel.LOGGER.warn("Chapter 2 Level 2: no anchor {}: the creature is not placed", data.level.creature.home)
            return
        }
        val creature = Ch2Entities.VIBRATION_CREATURE.create(world) ?: return
        creature.refreshPositionAndAngles(home.position.x, home.position.y, home.position.z, home.yaw, 0f)
        creature.headYaw = home.yaw
        creature.bodyYaw = home.yaw
        world.spawnEntity(creature)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the creature rests at {} ({})", data.level.creature.home, home.position)
    }

    /** The pump stopped and dark, the pipe gone, the vibration forgotten; the creature too if [removeCreature]. */
    private fun stop(server: MinecraftServer, removeCreature: Boolean) {
        startTick = null
        cycle = null
        pipeAt = null
        pipe?.discard()
        pipe = null
        pipeLast = null
        pipeVibration = null
        introUntil = null
        val world = levelWorld(server) ?: return
        setBeacon(world, false)
        if (removeCreature) for (c in creatures(world)) c.discard()
    }

    /** The creature, for the debug command. */
    fun creatureIn(server: MinecraftServer): VibrationCreatureEntity? = levelWorld(server)?.let { creatures(it).firstOrNull() }

    private fun creatures(world: ServerWorld): List<VibrationCreatureEntity> =
        world.iterateEntities().filterIsInstance<VibrationCreatureEntity>().toList()

    // --- the clock ------------------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        val world = levelWorld(server) ?: return
        val room = data.anchors.zone(ROOM_ZONE)?.box
        val inRoom = if (room == null) emptyList() else world.players.filter {
            Ch2L2.active(it) && it.isAlive && !it.isSpectator && Ch2L2.state(it) <= Ch2L2State.PUMP_ROOM && room.contains(it.x, it.y, it.z)
        }
        val now = server.ticks.toLong()

        if (inRoom.isEmpty()) {
            if (startTick != null) stop(server, removeCreature = false)
        } else {
            if (startTick == null) {
                startTick = now
                cycle = PumpCycle(data.level.creature.pump, data.level.creature.graceAfterStopSeconds)
            }
            if (now % ENSURE_EVERY == 0L || startTick == now) ensureCreature(world)
            runPump(world, inRoom, now - startTick!!, now)
        }
        trackPipe(world, now)
        introUntil?.let { if (now >= it) {
            introUntil = null
            EveryTunnel.LOGGER.warn("Chapter 2 Level 2: the creature never struck the pipe: the introduction ends anyway")
        } }
        watchers(server, now)
    }

    private fun runPump(world: ServerWorld, players: List<ServerPlayerEntity>, t: Long, now: Long) {
        val cycle = cycle ?: return
        val config = data.level.creature
        if (cycle.runBegins(t)) playAtBeacon(world, Ch2Sounds.CH2L2_PUMP_RUN, 1.0f)
        val phase = cycle.phaseAt(t)
        setBeacon(world, when (phase) {
            PumpPhase.RUNNING -> true
            PumpPhase.SLOWING -> (t / BLINK_TICKS) % 2 == 0L
            PumpPhase.SILENT -> false
        })
        if (phase != PumpPhase.SILENT) dust(world, players)
        if (introArmed && cycle.silenceBegins(t)) {
            introArmed = false
            val delay = PumpCycle.ticks(config.pipe.delaySeconds)
            pipeAt = now + delay
            introUntil = now + delay + INTRO_LIMIT_TICKS
            EveryTunnel.LOGGER.info("Chapter 2 Level 2: the first silence: the pipe falls in {} s", config.pipe.delaySeconds)
        }
    }

    /** Lets the pipe go when its time comes, and when it has landed: its clang and its vibration. */
    private fun trackPipe(world: ServerWorld, now: Long) {
        pipeAt?.let { at ->
            if (now >= at) {
                pipeAt = null
                dropPipe(world)
            }
        }
        val falling = pipe ?: return
        if (!falling.isRemoved) {
            pipeLast = Position(falling.x, falling.y, falling.z)
            return
        }
        pipe = null
        val landed = pipeLast ?: return
        pipeLast = null
        world.playSound(null, landed.x, landed.y, landed.z, Ch2Sounds.CH2L2_PIPE_FALL, SoundCategory.BLOCKS, 1.5f, 1.0f)
        pipeVibration = VibrationCreatureBrain.Vibration(landed, now, VibrationCreatureBrain.Source.PIPE, masked = masks(now))
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the pipe lands at ({}, {}, {})", landed.x, landed.y, landed.z)
    }

    private fun dropPipe(world: ServerWorld) {
        val from = data.anchors.point(data.level.creature.pipe.from)?.position
        if (from == null) {
            EveryTunnel.LOGGER.warn("Chapter 2 Level 2: no anchor {}: the pipe does not fall", data.level.creature.pipe.from)
            return
        }
        var state = ModBlocks.PIPE.defaultState
        for (side in listOf(net.minecraft.util.math.Direction.UP, net.minecraft.util.math.Direction.DOWN)) {
            state = state.with(ConnectingBlock.FACING_PROPERTIES[side], true)
        }
        val pos = BlockPos.ofFloored(from.x, from.y, from.z)
        val block = FallingBlockEntity.spawnFromBlock(world, pos, state)
        block.setDestroyedOnLanding()
        block.dropItem = false
        block.setHurtEntities(0f, 0)
        pipe = block
        pipeLast = Position(block.x, block.y, block.z)
    }

    // --- the pump's signs ---------------------------------------------------------------------------

    private fun cell(anchor: String): BlockPos? = data.anchors.point(anchor)?.position?.let { BlockPos.ofFloored(it.x, it.y, it.z) }

    private fun beaconCell(): BlockPos? = cell(data.level.creature.pump.anchor)

    private fun setBeacon(world: ServerWorld, lit: Boolean) {
        val pump = data.level.creature.pump
        for (anchor in pump.beacons.ifEmpty { listOf(pump.anchor) }) {
            val pos = cell(anchor) ?: continue
            val state = world.getBlockState(pos)
            if (!state.isOf(daio7771.everytunnel.chapter2.platform.Ch2Blocks.PUMP_BEACON) || state.get(PumpBeaconBlock.LIT) == lit) continue
            world.setBlockState(pos, state.with(PumpBeaconBlock.LIT, lit), Block.NOTIFY_ALL)
        }
    }

    private fun playAtBeacon(world: ServerWorld, sound: net.minecraft.sound.SoundEvent, volume: Float) {
        val pos = beaconCell() ?: return
        world.playSound(null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, sound, SoundCategory.BLOCKS, volume, 1.0f)
    }

    /**
     * Dust falling from the ceiling round each player in the room while the pump runs: single grains at random spots within
     * [DUST_RADIUS], each just under the first solid cell above it (or [CEILING_SCAN] up), coloured like that cell (grey concrete
     * powder if none is found).
     */
    private fun dust(world: ServerWorld, players: List<ServerPlayerEntity>) {
        val random = world.random
        for (player in players) {
            repeat(DUST_PER_TICK) {
                val x = player.x + (random.nextDouble() * 2 - 1) * DUST_RADIUS
                val z = player.z + (random.nextDouble() * 2 - 1) * DUST_RADIUS
                val cursor = BlockPos.Mutable(x, player.eyeY, z)
                var ceiling: net.minecraft.block.BlockState? = null
                for (i in 0 until CEILING_SCAN) {
                    cursor.move(net.minecraft.util.math.Direction.UP)
                    val state = world.getBlockState(cursor)
                    if (state.isOpaqueFullCube(world, cursor)) {
                        ceiling = state
                        break
                    }
                }
                val effect = BlockStateParticleEffect(ParticleTypes.FALLING_DUST, ceiling ?: Blocks.GRAY_CONCRETE_POWDER.defaultState)
                world.spawnParticles(effect, x, cursor.y - 0.05, z, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    // --- debug --------------------------------------------------------------------------------------

    /** Toggles the action bar's readout of the creature's brain; returns whether it is on now. */
    fun toggleWatchCreature(player: ServerPlayerEntity): Boolean = toggle(watchCreature, player)

    /** Toggles the action bar's readout of the pump's cycle; returns whether it is on now. */
    fun toggleWatchPump(player: ServerPlayerEntity): Boolean = toggle(watchPump, player)

    private fun toggle(set: MutableSet<UUID>, player: ServerPlayerEntity): Boolean = if (set.remove(player.uuid)) false else set.add(player.uuid)

    private fun watchers(server: MinecraftServer, now: Long) {
        if (watchCreature.isEmpty() && watchPump.isEmpty()) return
        val parts = mutableListOf<String>()
        for (player in server.playerManager.playerList) {
            parts.clear()
            if (player.uuid in watchCreature) {
                val c = creatureIn(server)
                parts += if (c == null) "creature: none" else
                    "creature: ${c.mind.state} target ${c.mind.target?.let { "%.0f %.0f".format(it.x, it.z) } ?: "-"} " +
                        "%.1f b/s".format(c.measuredSpeed) + (if (introPending()) " intro" else "")
            }
            if (player.uuid in watchPump) {
                val start = startTick
                val phase = if (start == null) "stopped" else "${cycle?.phaseAt(now - start)} t=${(now - start) % (cycle?.cycleLengthTicks ?: 1L)} masking=${masks(now)}"
                parts += "pump: $phase"
            }
            if (parts.isNotEmpty()) player.sendMessage(Text.literal(parts.joinToString("  |  ")), true)
        }
    }

    // --- helpers ------------------------------------------------------------------------------------

    private fun levelWorld(server: MinecraftServer): ServerWorld? =
        Identifier.tryParse(data.anchors.dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }

    /** The room: the overlook is inside it. */
    private const val ROOM_ZONE = "zone_vibration_chamber"
    private const val BLINK_TICKS = 5L
}
