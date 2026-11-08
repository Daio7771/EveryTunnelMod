package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.puzzle.ChemicalMix
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Blocks
import net.minecraft.block.TrapdoorBlock
import net.minecraft.nbt.NbtCompound
import net.minecraft.particle.DustParticleEffect
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState
import org.joml.Vector3f

/**
 * Level 5's chemical puzzle, in the chemical room (the zone tagged [ROOM_TAG]): four reagent flasks (R-1 to
 * R-4) lie on a cabinet (spawn points tagged `reagent_<n>`, [LootSpawner]); one goes in each of the mixer's
 * two tanks ([MixerTankBlock]), and its red button runs it ([press], [MixerBlock]). One pair of them, drawn
 * for each game ([ChemicalMix], kept with the world in `data/every_tunnel_chemicals.dat` until a new game),
 * makes the solvent; the Office's computer says which ([TerminalHooks]).
 *
 * - **Right:** the mixer runs and pours (`block.mixer.run`) down the pipe to the drain's grate; a moment
 *   later its bars hiss and dissolve in green fumes (`block.grate.dissolve`; the zone tagged [GRATE_TAG]) and
 *   the hatch behind it opens (`block.hatch.open`; trapdoors in the zone tagged [DRAIN_TAG]).
 * - **Wrong:** the alarm: a siren (`event.lab.alarm`) and the room's red lights turning ([AlarmLightBlock])
 *   for [ALARM_TICKS]; three Hunters and a Distortion Junior appear in the room (spawn points tagged
 *   [ALARM_HUNTERS_TAG] and [ALARM_JUNIOR_TAG], [DistortionHooks.putIn]). When it stops, the two flasks used
 *   are back on their cabinet. Try again.
 * - With a tank empty, the button only clicks.
 *
 * The level built anew puts the grate, the hatch and the flasks back; the pair stays the game's.
 */
object ChemicalHooks {

    const val ROOM_TAG = "chemical_room"
    const val GRATE_TAG = "lab_grate"
    const val DRAIN_TAG = "lab_drain"
    const val ALARM_HUNTERS_TAG = "alarm_hunters"
    const val ALARM_JUNIOR_TAG = "alarm_junior"
    private const val LEVEL_ID = "level_5"

    /** The alarm's length, and how often its siren sounds again. */
    private const val ALARM_TICKS = 200L
    private const val SIREN_TICKS = 40L
    /** From the mixer running to the grate dissolving, and on to the hatch opening. */
    private const val POUR_TICKS = 50L
    private const val HATCH_TICKS = 30L

    // Server thread only.
    private var server: MinecraftServer? = null
    private var alarmUntil = Long.MIN_VALUE
    private var nextSiren = 0L
    private val used = mutableSetOf<Int>()
    private var dissolveAt = Long.MIN_VALUE
    private var hatchAt = Long.MIN_VALUE

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            forget()
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    /** This game's pair, drawn the first time it is wanted. */
    fun mix(server: MinecraftServer): ChemicalMix {
        val state = state(server)
        if (state.first == 0) {
            val drawn = ChemicalMix.draw()
            state.first = drawn.first
            state.second = drawn.second
            state.markDirty()
            EveryTunnel.LOGGER.info("Chemical puzzle: R-{} with R-{}", drawn.first, drawn.second)
        }
        return ChemicalMix(state.first, state.second)
    }

    /** A new game: a new pair, drawn when next wanted. */
    fun newGame() {
        val server = server ?: return
        val state = state(server)
        state.first = 0
        state.second = 0
        state.markDirty()
        forget()
    }

    /** The level stands anew: no alarm, nothing poured. */
    fun levelBuilt() = forget()

    private fun forget() {
        alarmUntil = Long.MIN_VALUE
        used.clear()
        dissolveAt = Long.MIN_VALUE
        hatchAt = Long.MIN_VALUE
    }

    /** The mixer at [pos] is run by [player]. */
    fun press(world: ServerWorld, pos: BlockPos, player: ServerPlayerEntity) {
        val now = world.server.ticks.toLong()
        val tanks = tanks(world, pos)
        val reagents = tanks.map { world.getBlockState(it).get(MixerTankBlock.REAGENT) }
        if (tanks.size < 2 || reagents.any { it == 0 } || now < alarmUntil || dissolveAt != Long.MIN_VALUE) {
            world.playSound(null, pos, ModSounds.LEVER_DEAD, SoundCategory.BLOCKS, 0.8f, 1.2f)
            return
        }
        for (tank in tanks) world.setBlockState(tank, world.getBlockState(tank).with(MixerTankBlock.REAGENT, 0))
        val (a, b) = reagents
        if (mix(world.server).right(a, b)) {
            EveryTunnel.LOGGER.info("{} mixes R-{} with R-{}: the solvent", player.name.string, a, b)
            world.playSound(null, pos, ModSounds.MIXER_RUN, SoundCategory.BLOCKS, 1.2f, 1f)
            dissolveAt = now + POUR_TICKS
            return
        }
        EveryTunnel.LOGGER.info("{} mixes R-{} with R-{}: the alarm", player.name.string, a, b)
        used += a
        used += b
        alarmUntil = now + ALARM_TICKS
        nextSiren = now
        setAlarmLights(world, true)
        val level = LevelManager.get(LEVEL_ID) ?: return
        level.spawnPoints.filter { ALARM_HUNTERS_TAG in it.tags }.forEach { DistortionHooks.putIn(world, DistortionHooks.HUNTERS_TAG, it.position, it.yaw, LEVEL_ID) }
        level.spawnPoints.filter { ALARM_JUNIOR_TAG in it.tags }.forEach { DistortionHooks.putIn(world, DistortionHooks.JUNIOR_TAG, it.position, it.yaw, LEVEL_ID) }
    }

    /** The mixer's tanks: beside it on either side, along its wall. */
    private fun tanks(world: ServerWorld, pos: BlockPos): List<BlockPos> {
        val facing = world.getBlockState(pos).get(PropBlock.FACING)
        return listOf(pos.offset(facing.rotateYClockwise()), pos.offset(facing.rotateYCounterclockwise()))
            .filter { world.getBlockState(it).isOf(ModBlocks.MIXER_TANK) }
    }

    private fun tick(server: MinecraftServer) {
        val now = server.ticks.toLong()
        val level = LevelManager.get(LEVEL_ID) ?: return
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        if (alarmUntil != Long.MIN_VALUE) {
            if (now < alarmUntil) {
                if (now >= nextSiren) {
                    nextSiren = now + SIREN_TICKS
                    zone(level, ROOM_TAG)?.let { world.playSound(null, it.centre(), ModSounds.LAB_ALARM, SoundCategory.BLOCKS, 2.5f, 1f) }
                }
            } else {
                alarmUntil = Long.MIN_VALUE
                setAlarmLights(world, false)
                returnFlasks(world, level)
            }
        }
        if (dissolveAt != Long.MIN_VALUE && now >= dissolveAt) {
            dissolveAt = Long.MAX_VALUE
            hatchAt = now + HATCH_TICKS
            dissolve(world, level)
        }
        if (hatchAt != Long.MIN_VALUE && now >= hatchAt) {
            hatchAt = Long.MIN_VALUE
            openHatch(world, level)
        }
    }

    /** The grate's bars gone in a hiss of green fumes. */
    private fun dissolve(world: ServerWorld, level: LevelDefinition) {
        val box = zone(level, GRATE_TAG) ?: return
        for (pos in positions(box)) {
            if (!world.getBlockState(pos).isOf(Blocks.IRON_BARS)) continue
            world.setBlockState(pos, Blocks.AIR.defaultState)
            world.spawnParticles(FUMES, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 12, 0.3, 0.4, 0.3, 0.02)
        }
        world.playSound(null, box.centre(), ModSounds.GRATE_DISSOLVE, SoundCategory.BLOCKS, 1.5f, 1f)
        EveryTunnel.LOGGER.info("Level 5: the drain's grate dissolves")
    }

    private fun openHatch(world: ServerWorld, level: LevelDefinition) {
        val box = zone(level, DRAIN_TAG) ?: return
        for (pos in positions(box)) {
            val state = world.getBlockState(pos)
            if (!state.isOf(ModBlocks.BUNKER_TRAPDOOR) || state.get(TrapdoorBlock.OPEN)) continue
            world.setBlockState(pos, state.with(TrapdoorBlock.OPEN, true))
            world.playSound(null, pos, ModSounds.HATCH_OPEN, SoundCategory.BLOCKS, 1.2f, 0.9f)
        }
    }

    private fun setAlarmLights(world: ServerWorld, on: Boolean) {
        val level = LevelManager.get(LEVEL_ID) ?: return
        val box = zone(level, ROOM_TAG) ?: return
        for (pos in positions(box.grow(1))) {
            val state = world.getBlockState(pos)
            if (state.isOf(ModBlocks.ALARM_LIGHT)) world.setBlockState(pos, state.with(AlarmLightBlock.ON, on))
        }
    }

    /** The flasks the failed mix used, back on their cabinet. */
    private fun returnFlasks(world: ServerWorld, level: LevelDefinition) {
        for (reagent in used) {
            val point = level.spawnPoints.firstOrNull { "reagent_$reagent" in it.tags } ?: continue
            val pos = BlockPos.ofFloored(point.position.x, point.position.y, point.position.z)
            if (world.getBlockState(pos).isAir) world.setBlockState(pos, ModBlocks.LOOT_REAGENTS[reagent - 1].defaultState)
        }
        used.clear()
    }

    private fun zone(level: LevelDefinition, tag: String): BlockBox? = level.zones.firstOrNull { tag in it.tags }?.box

    private fun positions(box: BlockBox): List<BlockPos> =
        BlockPos.iterate(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ).map { it.toImmutable() }

    private fun BlockBox.centre() = BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)

    private fun BlockBox.grow(by: Int) = BlockBox(minX - by, minY - by, minZ - by, maxX + by, maxY + by, maxZ + by)

    private val FUMES = DustParticleEffect(Vector3f(0.45f, 0.95f, 0.35f), 1.6f)

    private fun state(server: MinecraftServer): PairState =
        server.overworld.persistentStateManager.getOrCreate(PairState::read, ::PairState, "every_tunnel_chemicals")

    /** This game's pair (0: not drawn yet). */
    class PairState : PersistentState() {
        var first = 0
        var second = 0

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.putInt("first", first)
            nbt.putInt("second", second)
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): PairState = PairState().apply {
                first = nbt.getInt("first")
                second = nbt.getInt("second")
            }
        }
    }
}
