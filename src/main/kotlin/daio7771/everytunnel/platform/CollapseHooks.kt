package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.systems.CollapseSchedule
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.particle.BlockStateParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box

/**
 * Level 3's tunnel collapse: the first time a player walks into a zone tagged [TRIGGER_TAG] (on
 * the last stretch before the door to Level 4), the ceiling comes down behind them and there is
 * no way back. Timed by [CollapseSchedule]:
 *
 * - the rumble and crash, `event.collapse`, in the head of every player in the level;
 * - the camera shakes (a packet, [SHAKE_PACKET_ID], read by `client.effects.CameraShake`);
 * - the zone tagged [BLOCKS_TAG] fills with rubble (the tunnel's concrete and cobbled
 *   deepslate), cell by cell in a random order, never where a player stands, a puff of dust
 *   at each;
 * - dust falls from the ceiling round the players, and the fluorescents within [LIGHT_RANGE]
 *   blocks flicker, then come back on.
 *
 * Once per level until [reset]: a death in the level (the player restarts at its start, and
 * the rubble would cut the way), a new game and the map's rebuild clear the rubble and arm it
 * again.
 */
object CollapseHooks {

    const val TRIGGER_TAG = "collapse"
    const val BLOCKS_TAG = "collapse_blocks"

    /** To the client: the collapse went off (no payload). */
    val SHAKE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "collapse_shake")

    private val RUBBLE: List<BlockState> get() = listOf(Blocks.SMOOTH_BASALT.defaultState, Blocks.COBBLED_DEEPSLATE.defaultState)
    private const val LIGHT_RANGE = 20

    private class Running(val world: ServerWorld, val levelId: String, val cells: List<BlockPos>, val lights: List<BlockPos>) {
        var tick = 0
        var filled = 0
    }

    // Server thread only.
    private val collapsed = HashSet<String>()
    private val running = ArrayList<Running>()
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            collapsed.clear()
            running.clear()
        }
        LevelTriggers.register { change ->
            val key = change.enteredZones.firstOrNull { key ->
                LevelManager.get(key.levelId)?.zone(key.zoneId)?.tags?.contains(TRIGGER_TAG) == true
            } ?: return@register
            val player = server?.playerManager?.getPlayer(change.playerId) ?: return@register
            if (!ResourceHooks.appliesTo(player)) return@register
            LevelManager.get(key.levelId)?.let { start(player, it) }
        }
        ServerTickEvents.END_SERVER_TICK.register { tick() }
    }

    private fun start(player: ServerPlayerEntity, level: LevelDefinition) {
        if (!collapsed.add(level.id)) return
        val world = player.serverWorld
        val zone = level.zones.firstOrNull { BLOCKS_TAG in it.tags } ?: return
        val b = zone.box
        val cells = BlockPos.iterate(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ).map { it.toImmutable() }
            .filter { world.getBlockState(it).isAir }.shuffled()
        val r = LIGHT_RANGE
        val lights = BlockPos.iterate(player.blockPos.add(-r, -6, -r), player.blockPos.add(r, 6, r)).map { it.toImmutable() }.filter {
            val state = world.getBlockState(it)
            state.isOf(ModBlocks.FLUORESCENT_LIGHT) && state.get(FluorescentLightBlock.MODE) == LightMode.ON
        }
        running += Running(world, level.id, cells, lights)
        for (other in playersIn(world, level)) {
            other.playSound(ModSounds.COLLAPSE, SoundCategory.BLOCKS, 1f, 1f)
            if (ServerPlayNetworking.canSend(other, SHAKE_PACKET_ID)) ServerPlayNetworking.send(other, SHAKE_PACKET_ID, PacketByteBufs.empty())
        }
        EveryTunnel.LOGGER.info("The tunnel of {} collapses behind {}: {} cells of rubble, {} lights", level.id, player.name.string, cells.size, lights.size)
    }

    private fun tick() {
        val iterator = running.iterator()
        while (iterator.hasNext()) {
            val run = iterator.next()
            val world = run.world
            val t = ++run.tick
            val due = CollapseSchedule.rubbleBy(t, run.cells.size)
            while (run.filled < due) {
                val pos = run.cells[run.filled++]
                if (world.players.any { it.boundingBox.intersects(Box(pos)) }) continue
                world.setBlockState(pos, RUBBLE[world.random.nextInt(RUBBLE.size)])
                world.spawnParticles(ParticleTypes.CLOUD, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 3, 0.4, 0.4, 0.4, 0.02)
            }
            if (t < CollapseSchedule.DUST_END && t % 2 == 0) {
                val dust = BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.SMOOTH_BASALT.defaultState)
                for (player in world.players) {
                    if (LevelManager.levelAt(world.registryKey.value.toString(), player.x, player.y, player.z)?.id != run.levelId) continue
                    world.spawnParticles(dust, player.x, player.y + 2.6, player.z, 6, 2.5, 0.1, 2.5, 0.0)
                }
            }
            if (t in CollapseSchedule.FLICKER_START until CollapseSchedule.FLICKER_END && t % 3 == 0) {
                for (pos in run.lights) setLit(world, pos, world.random.nextFloat() < 0.45f)
            }
            if (t == CollapseSchedule.FLICKER_END) for (pos in run.lights) setLit(world, pos, true)
            if (t >= CollapseSchedule.LENGTH) iterator.remove()
        }
    }

    private fun setLit(world: ServerWorld, pos: BlockPos, lit: Boolean) {
        val state = world.getBlockState(pos)
        if (state.isOf(ModBlocks.FLUORESCENT_LIGHT) && state.get(FluorescentLightBlock.LIT) != lit) {
            world.setBlockState(pos, state.with(FluorescentLightBlock.LIT, lit).with(FluorescentLightBlock.BURST, false))
        }
    }

    private fun playersIn(world: ServerWorld, level: LevelDefinition): List<ServerPlayerEntity> =
        world.players.filter { LevelManager.levelAt(world.registryKey.value.toString(), it.x, it.y, it.z)?.id == level.id }

    /** The collapse of [levelId] undone: its rubble cleared, its lights on, ready to go off again. */
    fun reset(server: MinecraftServer, levelId: String) {
        val level = LevelManager.get(levelId) ?: return
        val zone = level.zones.firstOrNull { BLOCKS_TAG in it.tags } ?: return
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        running.filter { it.levelId == levelId }.forEach { run -> run.lights.forEach { setLit(world, it, true) } }
        running.removeAll { it.levelId == levelId }
        val b = zone.box
        val rubble = RUBBLE.map { it.block }
        for (pos in BlockPos.iterate(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)) {
            if (world.getBlockState(pos).block in rubble) world.setBlockState(pos, Blocks.AIR.defaultState)
        }
        if (collapsed.remove(levelId)) EveryTunnel.LOGGER.info("The tunnel of {} is open again", levelId)
    }

    /** Every level's collapse undone (a new game, the map rebuilt). */
    fun resetAll(server: MinecraftServer) {
        for (level in LevelManager.all()) if (level.zones.any { BLOCKS_TAG in it.tags }) reset(server, level.id)
    }
}
