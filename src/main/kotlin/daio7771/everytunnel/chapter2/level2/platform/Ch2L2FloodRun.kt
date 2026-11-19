package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.level2.core.FloodCell
import daio7771.everytunnel.chapter2.level2.core.FloodStage
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.platform.CollapseHooks
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.ArrayDeque
import java.util.UUID

/**
 * The flood of Chapter 2's Level 2 (phase 6), over `flood.json`'s stages ([daio7771.everytunnel.chapter2.level2.core.Ch2L2Flood]) and
 * `level.json`'s `flood` tunables ([daio7771.everytunnel.chapter2.level2.core.Ch2L2FloodRules]):
 *
 * - **Stage 1** begins as the player enters the collector ([Ch2L2State.FLOOD_1]): the vestibule's gate slams down behind them (sealed:
 *   the way back is closed for good), the pipes creak for the stage's `delay_seconds`, then the pipe at `pipe_anchor` bursts (a sound, a
 *   shake, a jet of water for the stage's seconds, a thinner trickle after) and the water rises: layer by layer, bottom up, evenly over
 *   the stage's seconds, at most `cells_per_tick` cells a tick. **Stages 2 and 3** begin as the player reaches the walkways and the
 *   upper gallery and only fill. The water never touches the player but to slow their wading; the route always stays over it (the
 *   generator's checks), and a ladder takes anyone who falls back up.
 * - A **rumble** loop plays in the player's head at the stage's volume while they are in the flood; it goes muffled once the
 *   **station's door** shuts behind them ([Ch2L2State.STATION]: it shuts when they are `clear_blocks` inside it).
 * - **Water** is placed as a source block in the air, or as `waterlogged` in the blocks that have it (rails, ladders, slabs), and
 *   never over a block that has neither; it is taken away the same way, so a drained cell is what the map has there. The still water
 *   of the map is not in `flood.json` and stays.
 * - **A start** ([Ch2L2.onStart]: a death, CONTINUE, `reset`): the water as the state wants it, at once, before control returns: all
 *   drained up to the flood (a death inside it comes back to the collector, dry, and walking in starts it again), all of it from the
 *   station on, with the gates as they go (the vestibule's sealed from the flood on, the station's shut from the station on).
 *
 * Nothing here is saved: the world's blocks are what remains. Server thread only.
 */
object Ch2L2FloodRun {

    private val data get() = Ch2L2DataLoader.data
    private val flood get() = data.flood
    private val rules get() = data.level.flood

    /** One stage being laid: ticks since it began, the layers asked for so far and the cells waiting to be placed. */
    private class Fill(val stage: FloodStage) {
        var ticks = 0
        var layersQueued = 0
        var burst = false
        val queue = ArrayDeque<FloodCell>()
        val done: Boolean get() = layersQueued >= stage.layers.size && queue.isEmpty()
    }

    private var owner: UUID? = null
    private var fill: Fill? = null

    /** The stages whose water is all placed. */
    private var water = 0

    /** The jet runs: from the burst, at full for [jetFullTicks] ticks, then a trickle. */
    private var jetOn = false
    private var jetTicks = 0L
    private var jetFullTicks = 0L
    private var jetNext = 0L

    /** The rumble now: 0 none, 1 to 3 a stage's, [MUFFLED] through the door; and when it is played again. */
    private var rumble = 0
    private var rumbleNext = 0L
    private var rumbleVolume = 0f

    private const val MUFFLED = 4
    private const val CHECK_EVERY = 5

    /** The loops are played again a little before their ends (10 s and 8 s) so that their seams overlap. */
    private const val RUMBLE_PERIOD = 196L
    private const val JET_PERIOD = 156L

    private val RUMBLE_ID = Identifier(EveryTunnel.MOD_ID, "ambient.ch2l2.flood_rumble")
    private val MUFFLED_ID = Identifier(EveryTunnel.MOD_ID, "ambient.ch2l2.flood_rumble_muffled")
    private val JET_ID = Identifier(EveryTunnel.MOD_ID, "ambient.ch2l2.flood_jet")

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        Ch2L2.onStateChange { player, from, to -> changed(player, from, to) }
        Ch2L2.onStart { player, state -> settle(player, state) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> if (owner == handler.player.uuid) forgetRun(handler.player.server) }
        ServerLifecycleEvents.SERVER_STOPPED.register { clearRun() }
    }

    // --- the states ------------------------------------------------------------------------------------

    private fun changed(player: ServerPlayerEntity, from: Ch2L2State, to: Ch2L2State) {
        val world = levelWorld(player.server) ?: return
        owner = player.uuid
        when {
            to >= Ch2L2State.FLOOD_1 && to <= Ch2L2State.FLOOD_3 -> {
                val stage = flood.stage(to.floodStage) ?: return
                if (from > to) drainAll(world, player.server)
                completeUpTo(world, stage.id - 1)
                begin(world, player, stage)
            }
            to >= Ch2L2State.STATION && from < Ch2L2State.STATION -> {
                if (from < Ch2L2State.FLOOD_3) completeUpTo(world, flood.stages.size)
                jetOn = false
                stopJet(player.server)
            }
            to < Ch2L2State.FLOOD_1 && from >= Ch2L2State.FLOOD_1 -> {
                drainAll(world, player.server)
                if (to >= Ch2L2State.GUARD_DEAD) Ch2L2Build.setGate(world, VESTIBULE_GATE, "open")
            }
        }
        if (to < Ch2L2State.STATION && from >= Ch2L2State.STATION) Ch2L2Build.setGate(world, rules.stationDoor, "open")
    }

    /** The water and the gates as [state] wants them, at once (see the class); the flood to be lived again is brought back to the collector's state. */
    private fun settle(player: ServerPlayerEntity, state: Ch2L2State) {
        val world = levelWorld(player.server) ?: return
        owner = player.uuid
        fill = null
        jetOn = false
        stopSounds(player.server)
        val resume = flood.resume(state)
        if (resume.water > 0) {
            fillTo(world, resume.water)
            for ((gate, gateState) in flood.stages.last().gates) Ch2L2Build.setGate(world, gate, gateState)
            Ch2L2Build.setGate(world, rules.stationDoor, "shut")
        } else {
            if (water > 0 || isWet(world)) drainAll(world, player.server)
            Ch2L2Build.setGate(world, rules.stationDoor, "open")
        }
        if (resume.state != state) Ch2L2.setState(player, resume.state, "the flood is lived again from the collector")
    }

    // --- debug -------------------------------------------------------------------------------------------

    /** The water at once to stage [stages] (0 to the stages there are), whatever the state: `/et ch2 l2 flood`. */
    fun setWater(server: MinecraftServer, stages: Int): Int {
        val world = levelWorld(server) ?: return 0
        fill = null
        jetOn = false
        stopJet(server)
        val n = stages.coerceIn(0, flood.stages.size)
        if (n == 0) drainAll(world, server) else {
            drainAll(world, server)
            fillTo(world, n)
        }
        return n
    }

    // --- stages ----------------------------------------------------------------------------------------

    /** Stage [stage] begins: the gates of its state, and for the first the slam and the creak (the burst follows after its delay). */
    private fun begin(world: ServerWorld, player: ServerPlayerEntity, stage: FloodStage) {
        if (fill != null) completeFill(world)
        fill = Fill(stage)
        var slammed = false
        for ((gate, state) in stage.gates) {
            if (Ch2L2Build.gateIs(world, gate, state)) continue
            Ch2L2Build.setGate(world, gate, state)
            slammed = true
            Ch2L2Build.gateCentre(gate)?.let { world.playSound(null, it.x, it.y, it.z, Ch2Sounds.CH2L2_FLOOD_GATE_SLAM, SoundCategory.BLOCKS, 1.5f, 1.0f) }
        }
        if (slammed) shake(player.server, rules.shakeGate)
        if (stage.delayTicks > 0) {
            pipeAt(world)?.let { world.playSound(null, it.first, it.second, it.third, Ch2Sounds.CH2L2_FLOOD_CREAK, SoundCategory.BLOCKS, 1.2f, 1.0f) }
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the flood's stage {} begins ({} cells, {} s)", stage.id, stage.cellCount, stage.seconds)
    }

    /** What is left of the stage being laid, placed now. */
    private fun completeFill(world: ServerWorld) {
        val f = fill ?: return
        while (f.layersQueued < f.stage.layers.size) f.queue += f.stage.layers[f.layersQueued++].cells
        while (f.queue.isNotEmpty()) place(world, f.queue.poll())
        water = maxOf(water, f.stage.id)
        fill = null
    }

    /** The stages up to [stages] all water, in order, at once. */
    private fun completeUpTo(world: ServerWorld, stages: Int) {
        fill?.let { if (it.stage.id <= stages) completeFill(world) }
        for (stage in flood.stages) if (stage.id in (water + 1)..stages) {
            for (layer in stage.layers) for (cell in layer.cells) place(world, cell)
            water = stage.id
        }
    }

    /** The flood's lowest cell holds water: some was laid (a stage fills bottom up), perhaps in an earlier session of this world. */
    private fun isWet(world: ServerWorld): Boolean {
        val cell = flood.stages.firstOrNull()?.layers?.firstOrNull()?.cells?.firstOrNull() ?: return false
        val state = world.getBlockState(pos(world, cell))
        return state.isOf(Blocks.WATER) || (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED))
    }

    private fun fillTo(world: ServerWorld, stages: Int) {
        for (stage in flood.stages) if (stage.id <= stages) for (layer in stage.layers) for (cell in layer.cells) place(world, cell)
        water = stages
    }

    private fun drainAll(world: ServerWorld, server: MinecraftServer) {
        fill = null
        jetOn = false
        water = 0
        for (cell in flood.allCells) drain(world, cell)
        stopSounds(server)
    }

    private const val FLAGS = Block.NOTIFY_LISTENERS or Block.FORCE_STATE

    private fun pos(world: ServerWorld, cell: FloodCell): BlockPos {
        val plan = Ch2L2Build.plan
        return BlockPos(plan.worldX(cell.x), plan.worldY(cell.y), plan.worldZ(cell.z))
    }

    /** A cell of water: a source in the air, or the water of a block that holds it; nothing over any other block. */
    private fun place(world: ServerWorld, cell: FloodCell) {
        val at = pos(world, cell)
        val state = world.getBlockState(at)
        if (state.isAir) {
            world.setBlockState(at, Blocks.WATER.defaultState, FLAGS)
        } else if (state.contains(Properties.WATERLOGGED) && !state.get(Properties.WATERLOGGED)) {
            world.setBlockState(at, state.with(Properties.WATERLOGGED, true), FLAGS)
        }
    }

    /** A cell of water taken away: back to air, or to the block without its water. */
    private fun drain(world: ServerWorld, cell: FloodCell) {
        val at = pos(world, cell)
        val state = world.getBlockState(at)
        if (state.isOf(Blocks.WATER)) {
            world.setBlockState(at, Blocks.AIR.defaultState, FLAGS)
        } else if (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) {
            world.setBlockState(at, state.with(Properties.WATERLOGGED, false), FLAGS)
        }
    }

    // --- the clock -----------------------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        val id = owner ?: return
        val player = server.playerManager.getPlayer(id)
        if (player == null || !Ch2L2.active(player)) {
            // The player is gone or out of the level: its sounds stop, once, and the run waits for the next change.
            if (player != null) stopSounds(server)
            owner = null
            return
        }
        val world = levelWorld(server) ?: return
        val now = server.ticks.toLong()
        fill?.let { stepFill(world, player, it) }
        stepJet(world, server, now)
        if (now % CHECK_EVERY == 0L) {
            stepStationDoor(world, player)
            stepRumble(player, now)
        } else if (rumble != 0 && now >= rumbleNext) {
            playRumble(player, now)
        }
    }

    private fun stepFill(world: ServerWorld, player: ServerPlayerEntity, f: Fill) {
        f.ticks++
        val wait = f.stage.delayTicks
        if (f.ticks < wait) return
        if (!f.burst) {
            f.burst = true
            if (f.stage.id == 1 && wait > 0) burst(world, player.server, f.stage)
        }
        val due = f.stage.layersDue(f.ticks - wait)
        while (f.layersQueued < due) f.queue += f.stage.layers[f.layersQueued++].cells
        var budget = rules.cellsPerTick
        while (budget-- > 0 && f.queue.isNotEmpty()) place(world, f.queue.poll())
        if (f.done) {
            water = maxOf(water, f.stage.id)
            fill = null
            EveryTunnel.LOGGER.info("Chapter 2 Level 2: the flood's stage {} is up", f.stage.id)
        }
    }

    // --- the pipe --------------------------------------------------------------------------------------------

    /** The pipe's open end: a little north of its cell's middle, so that the jet leaves it. */
    private fun pipeAt(world: ServerWorld): Triple<Double, Double, Double>? =
        data.anchors.point(rules.pipeAnchor)?.position?.let { Triple(it.x, it.y + 0.5, it.z - 0.2) }

    private fun burst(world: ServerWorld, server: MinecraftServer, stage: FloodStage) {
        val at = pipeAt(world) ?: return
        world.playSound(null, at.first, at.second, at.third, Ch2Sounds.CH2L2_FLOOD_BURST, SoundCategory.BLOCKS, 2.0f, 1.0f)
        shake(server, rules.shakeBurst)
        jetOn = true
        jetTicks = 0
        jetFullTicks = stage.totalTicks.toLong()
        jetNext = 0
    }

    private fun stepJet(world: ServerWorld, server: MinecraftServer, now: Long) {
        if (!jetOn) return
        val at = pipeAt(world) ?: return
        val full = jetTicks < jetFullTicks
        jetTicks++
        val count = if (full) rules.jetParticles else rules.trickleParticles
        repeat(count) {
            val spread = if (full) 0.06 else 0.03
            val speed = if (full) rules.jetSpeed else rules.trickleSpeed
            val dx = (world.random.nextDouble() - 0.5) * spread
            val dy = 0.1 + world.random.nextDouble() * 0.12
            world.spawnParticles(ParticleTypes.FALLING_WATER, at.first, at.second, at.third, 0, dx, dy, -1.0, speed * (0.7 + world.random.nextDouble() * 0.6))
        }
        if (now >= jetNext) {
            jetNext = now + JET_PERIOD
            val volume = (if (full) rules.jetVolume else rules.trickleVolume).toFloat()
            world.playSound(null, at.first, at.second, at.third, Ch2Sounds.CH2L2_FLOOD_JET, SoundCategory.BLOCKS, volume, 1.0f)
        }
    }

    private fun stopJet(server: MinecraftServer) {
        for (p in server.playerManager.playerList) p.networkHandler.sendPacket(StopSoundS2CPacket(JET_ID, SoundCategory.BLOCKS))
    }

    // --- the station's door and the rumble -----------------------------------------------------------------------

    /** From the station on, the door shuts once the player is `station_door.clear_blocks` or more inside it. */
    private fun stepStationDoor(world: ServerWorld, player: ServerPlayerEntity) {
        if (Ch2L2.state(player) < Ch2L2State.STATION) return
        if (Ch2L2Build.gateIs(world, rules.stationDoor, "shut")) return
        val box = Ch2L2Build.gateBox(rules.stationDoor) ?: return
        val inside = data.anchors.point(rules.stationInside)?.position ?: return
        val clear = rules.stationClearBlocks
        val centre = box.center
        val side = if (inside.z < centre.z) -1 else 1
        val onTheStationSide = (player.z - centre.z) * side > 0
        val away = box.expand(clear, clear, clear).contains(player.pos)
        if (!onTheStationSide || away) return
        Ch2L2Build.setGate(world, rules.stationDoor, "shut")
        world.playSound(null, centre.x, centre.y, centre.z, Ch2Sounds.CH2L2_FLOOD_GATE_SLAM, SoundCategory.BLOCKS, 1.0f, 1.1f)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the station's door shuts behind {}", player.gameProfile.name)
    }

    /** Which rumble the player hears now: their stage's while in the flood, muffled once the station's door is shut, none otherwise. */
    private fun wantedRumble(world: ServerWorld, player: ServerPlayerEntity): Int {
        val state = Ch2L2.state(player)
        return when {
            state < Ch2L2State.FLOOD_1 -> 0
            state >= Ch2L2State.STATION -> if (Ch2L2Build.gateIs(world, rules.stationDoor, "shut")) MUFFLED else flood.stages.size
            else -> state.floodStage
        }
    }

    private fun stepRumble(player: ServerPlayerEntity, now: Long) {
        val world = levelWorld(player.server) ?: return
        val wanted = wantedRumble(world, player)
        if (wanted == rumble) {
            if (rumble != 0 && now >= rumbleNext) playRumble(player, now)
            return
        }
        stopRumble(player)
        rumble = wanted
        if (rumble != 0) playRumble(player, now)
    }

    private fun playRumble(player: ServerPlayerEntity, now: Long) {
        rumbleNext = now + RUMBLE_PERIOD
        val muffled = rumble == MUFFLED
        val volume = (if (muffled) rules.mutedVolume else rules.rumbleAt(rumble)).toFloat()
        rumbleVolume = volume
        val event = if (muffled) Ch2Sounds.CH2L2_FLOOD_RUMBLE_MUFFLED else Ch2Sounds.CH2L2_FLOOD_RUMBLE
        val seed = player.world.random.nextLong()
        player.networkHandler.sendPacket(PlaySoundFromEntityS2CPacket(RegistryEntry.of(event), SoundCategory.AMBIENT, player, volume, 1.0f, seed))
    }

    private fun stopRumble(player: ServerPlayerEntity) {
        player.networkHandler.sendPacket(StopSoundS2CPacket(RUMBLE_ID, SoundCategory.AMBIENT))
        player.networkHandler.sendPacket(StopSoundS2CPacket(MUFFLED_ID, SoundCategory.AMBIENT))
        rumble = 0
    }

    private fun stopSounds(server: MinecraftServer) {
        for (p in server.playerManager.playerList) {
            p.networkHandler.sendPacket(StopSoundS2CPacket(RUMBLE_ID, SoundCategory.AMBIENT))
            p.networkHandler.sendPacket(StopSoundS2CPacket(MUFFLED_ID, SoundCategory.AMBIENT))
            p.networkHandler.sendPacket(StopSoundS2CPacket(JET_ID, SoundCategory.BLOCKS))
        }
        rumble = 0
    }

    // --- shake, helpers ----------------------------------------------------------------------------------------

    /** A short shake of the camera ([strength] 1 is the collapse's peak), for every player in the level. */
    private fun shake(server: MinecraftServer, strength: Double) {
        if (strength <= 0.0) return
        val ticks = (rules.shakeSeconds * 20).toInt()
        for (p in server.playerManager.playerList) {
            if (!ServerPlayNetworking.canSend(p, CollapseHooks.SHAKE_PACKET_ID)) continue
            val buf = PacketByteBufs.create()
            buf.writeFloat(strength.toFloat())
            buf.writeVarInt(ticks)
            ServerPlayNetworking.send(p, CollapseHooks.SHAKE_PACKET_ID, buf)
        }
    }

    private fun forgetRun(server: MinecraftServer) {
        stopSounds(server)
        clearRun()
    }

    private fun clearRun() {
        owner = null
        fill = null
        jetOn = false
        water = 0
        rumble = 0
    }

    private fun levelWorld(server: MinecraftServer): ServerWorld? =
        Identifier.tryParse(data.anchors.dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }

    private const val VESTIBULE_GATE = "vestibule_gate"

    /** For the debug command: the water in the world as the run knows it. */
    fun describe(): String = "water ${water}/${flood.stages.size} stages${fill?.let { ", stage ${it.stage.id} rising (${it.ticks} ticks, ${it.layersQueued}/${it.stage.layers.size} layers)" } ?: ""}${if (jetOn) ", jet on" else ""}"
}
