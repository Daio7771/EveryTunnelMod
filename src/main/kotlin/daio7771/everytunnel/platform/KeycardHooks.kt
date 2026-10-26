package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.PersistentState

/**
 * The keycard of Level 2 and what it sets off.
 *
 * Picking it up ([pickedUp]) puts out every light still on in the level it lay in, except in
 * zones tagged [KEEP_LIGHTS_TAG] (the stair shaft down to B3): they die in a wave spreading
 * from the player ([WAVE_BLOCKS_PER_TICK]), the near ones with their power-down sound. The world remembers what each light was (`data/every_tunnel_lights.dat`)
 * so a new game turns them back on ([restoreLights]); a new game also locks the card
 * readers in zones tagged [DOOR_TAG] and closes their doors ([resetReaders]).
 */
object KeycardHooks {

    const val DOOR_TAG = "keycard_door"

    /** Zones whose lights stay on when the keycard is taken. */
    const val KEEP_LIGHTS_TAG = "lights_stay"

    private const val WAVE_BLOCKS_PER_TICK = 0.75
    /** Lights within this distance of the player are heard going out (at most [MAX_SOUNDS]). */
    private const val SOUND_RANGE = 20.0
    private const val MAX_SOUNDS = 12

    private class Wave(val world: ServerWorld, val origin: Vec3d, val lights: MutableList<BlockPos>, var ticks: Int = 0, var sounds: Int = 0)

    // Server thread only.
    private val waves = mutableListOf<Wave>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { tick() }
        ServerLifecycleEvents.SERVER_STOPPED.register { waves.clear() }
    }

    fun pickedUp(player: ServerPlayerEntity, pos: BlockPos) {
        player.sendMessage(Text.translatable("message.every_tunnel.keycard_found"), true)
        val world = player.serverWorld
        val level = LevelManager.levelAt(world.registryKey.value.toString(), pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) ?: return
        lightsOut(world, level, player.pos)
    }

    private fun lightsOut(world: ServerWorld, level: LevelDefinition, origin: Vec3d) {
        val state = state(world.server)
        val found = LinkedHashSet<BlockPos>()
        val kept = level.zones.filter { KEEP_LIGHTS_TAG in it.tags }.map { it.box }
        for (zone in level.zones) {
            val box = zone.box
            for (pos in BlockPos.iterate(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                val block = world.getBlockState(pos)
                if (!block.isOf(ModBlocks.FLUORESCENT_LIGHT)) continue
                val mode = block.get(FluorescentLightBlock.MODE)
                if (mode == LightMode.OFF) continue
                if (kept.any { it.contains(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) }) continue
                val immutable = pos.toImmutable()
                if (found.add(immutable)) state.put(world, immutable, mode)
            }
        }
        val lights = found.toMutableList()
        lights.sortBy { Vec3d.ofCenter(it).squaredDistanceTo(origin) }
        waves += Wave(world, origin, lights)
        EveryTunnel.LOGGER.info("Keycard taken: {} lights in {} go out", lights.size, level.id)
    }

    private fun tick() {
        waves.removeIf { wave ->
            val reach = ++wave.ticks * WAVE_BLOCKS_PER_TICK
            val iterator = wave.lights.iterator()
            while (iterator.hasNext()) {
                val pos = iterator.next()
                val distance = Math.sqrt(Vec3d.ofCenter(pos).squaredDistanceTo(wave.origin))
                if (distance > reach) break
                iterator.remove()
                val block = wave.world.getBlockState(pos)
                if (!block.isOf(ModBlocks.FLUORESCENT_LIGHT)) continue
                wave.world.setBlockState(pos, FluorescentLightBlock.withMode(block, LightMode.OFF))
                if (distance <= SOUND_RANGE && wave.sounds < MAX_SOUNDS) {
                    wave.sounds++
                    wave.world.playSound(null, pos, ModSounds.FLUORESCENT_OFF, SoundCategory.BLOCKS, 1f, 0.9f + wave.world.random.nextFloat() * 0.2f)
                }
            }
            wave.lights.isEmpty()
        }
    }

    /** Every light the keycard put out is back as it was (a new game). */
    fun restoreLights(server: MinecraftServer) {
        waves.clear()
        val state = state(server)
        for ((key, mode) in state.lights) {
            val (dimension, pos) = LightsState.parse(key) ?: continue
            val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(dimension))) ?: continue
            val block = world.getBlockState(pos)
            if (!block.isOf(ModBlocks.FLUORESCENT_LIGHT)) continue
            world.setBlockState(pos, FluorescentLightBlock.withMode(block, mode))
            if (mode == LightMode.FLICKER) world.scheduleBlockTick(pos, block.block, 20)
        }
        state.lights.clear()
        state.markDirty()
    }

    /** Forgets the lights put out, without touching them (a rebuild of the level put them back). */
    fun forgetLights(server: MinecraftServer) {
        waves.clear()
        val state = state(server)
        state.lights.clear()
        state.markDirty()
    }

    /** Card readers in zones tagged [DOOR_TAG] locked again, their doors closed (a new game). */
    fun resetReaders(server: MinecraftServer) {
        for (level in LevelManager.all()) {
            val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: continue
            for (zone in level.zones.filter { DOOR_TAG in it.tags }) {
                val box = zone.box
                for (pos in BlockPos.iterate(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                    val block = world.getBlockState(pos)
                    if (!block.isOf(ModBlocks.CARD_READER)) continue
                    val immutable = pos.toImmutable()
                    world.setBlockState(immutable, block.with(CardReaderBlock.UNLOCKED, false))
                    CardReaderBlock.setDoors(world, immutable, open = false)
                }
            }
        }
    }

    private fun state(server: MinecraftServer): LightsState =
        server.overworld.persistentStateManager.getOrCreate(LightsState::read, ::LightsState, "every_tunnel_lights")

    /** Lights put out by the keycard: "dimension|x|y|z" -> their mode before. */
    class LightsState : PersistentState() {
        val lights: MutableMap<String, LightMode> = linkedMapOf()

        fun put(world: ServerWorld, pos: BlockPos, mode: LightMode) {
            lights.putIfAbsent("${world.registryKey.value}|${pos.x}|${pos.y}|${pos.z}", mode)
            markDirty()
        }

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.put("lights", NbtList().apply {
                lights.forEach { (key, mode) -> add(NbtCompound().apply { putString("at", key); putString("mode", mode.asString()) }) }
            })
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): LightsState = LightsState().apply {
                nbt.getList("lights", NbtElement.COMPOUND_TYPE.toInt()).forEach { element ->
                    val entry = element as NbtCompound
                    val mode = LightMode.entries.firstOrNull { it.asString() == entry.getString("mode") } ?: return@forEach
                    lights[entry.getString("at")] = mode
                }
            }

            fun parse(key: String): Pair<String, BlockPos>? {
                val parts = key.split('|')
                if (parts.size != 4) return null
                val (x, y, z) = parts.drop(1).map { it.toIntOrNull() ?: return null }
                return parts[0] to BlockPos(x, y, z)
            }
        }
    }
}
