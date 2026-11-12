package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Blocks
import net.minecraft.entity.FallingBlockEntity
import net.minecraft.particle.BlockStateParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * Level 5's end, past the director: the corridor out of his office looks like Level 1's garage and ends at the
 * same EXIT door, but over its last stretch its floor is only a lid on a pit 40 deep (the zone tagged [PIT_TAG]).
 * Stepping onto it brings the floor down as Level 3's tunnel came down (its rumble, the camera shaking, dust, the
 * lights round it flickering), and a moment later the lid falls away under the player ([DROP]) and they fall with
 * it, unharmed. The fall's music plays in their head (`music.fall`, by the client: [FALL_PACKET_ID]); the screen
 * goes black on the way down ([BLACK]); after the music's last hit, a while of black and silence, and then the
 * credits come ([EndingHooks.theEnd]), which erase the saved game. Once each time Level 5 is built.
 */
object FalseExitHooks {

    /** To the client: the fall's music, in the player's head (no payload). */
    val FALL_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "false_exit_fall")

    private const val LEVEL_ID = "level_5"
    private const val PIT_TAG = "false_exit_pit"

    /** Ticks from the rumble to the floor falling away. */
    private const val DROP = 8

    /**
     * Ticks from the floor falling to the black (two thirds of the way down), and to the credits: the music's hit,
     * then [SILENCE] of black and nothing before the credits' song comes in.
     */
    private const val BLACK = 40
    private const val SILENCE = 60
    private const val END = 95 + SILENCE

    private const val LIGHT_RANGE = 16

    private class Falling(val player: UUID, val world: ServerWorld, val lid: List<BlockPos>, val lights: List<BlockPos>) {
        var tick = 0
    }

    // Server thread only.
    private var falling: Falling? = null
    private var fallen = false

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ -> falling?.player != entity.uuid }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> if (falling?.player == handler.player.uuid) falling = null }
        ServerLifecycleEvents.SERVER_STOPPED.register { levelBuilt() }
    }

    /** Level 5 was built: the lid is whole again. */
    fun levelBuilt() {
        falling = null
        fallen = false
    }

    private fun tick(server: MinecraftServer) {
        val run = falling
        if (run == null) {
            if (!fallen) for (player in server.playerManager.playerList) if (onLid(player)) start(player)
            return
        }
        val player = server.playerManager.getPlayer(run.player)
        if (player == null) {
            falling = null
            return
        }
        val t = run.tick++
        if (t < DROP + BLACK && t % 2 == 0) {
            val dust = BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.SMOOTH_STONE.defaultState)
            run.world.spawnParticles(dust, player.x, player.y + 2.6, player.z, 6, 2.0, 0.1, 2.0, 0.0)
        }
        if (t < DROP && t % 2 == 0) for (pos in run.lights) setLit(run.world, pos, run.world.random.nextFloat() < 0.4f)
        if (t == DROP) drop(run, player)
        if (t == DROP + BLACK) DeathHooks.sendFade(player, 1f, 0.35f)
        if (t >= DROP + END) {
            falling = null
            EndingHooks.theEnd(player)
        }
    }

    /** Standing on the pit's lid: inside its columns, on its top. */
    private fun onLid(player: ServerPlayerEntity): Boolean {
        if (!ResourceHooks.appliesTo(player) || DirectorSceneHooks.isRunning(player)) return false
        val box = LevelManager.get(LEVEL_ID)?.zones?.firstOrNull { PIT_TAG in it.tags }?.box ?: return false
        if (LevelManager.levelAt(player.serverWorld.registryKey.value.toString(), player.x, player.y, player.z)?.id != LEVEL_ID) return false
        return player.x >= box.minX && player.x < box.maxX + 1 && player.z >= box.minZ && player.z < box.maxZ + 1 &&
            player.y >= box.maxY + 0.5 && player.y < box.maxY + 3
    }

    private fun start(player: ServerPlayerEntity) {
        val box = LevelManager.get(LEVEL_ID)?.zones?.firstOrNull { PIT_TAG in it.tags }?.box ?: return
        val world = player.serverWorld
        fallen = true
        DirectorLoopHooks.stop()
        val lid = BlockPos.iterate(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ).map { it.toImmutable() }
            .filter { !world.getBlockState(it).isAir }
        val r = LIGHT_RANGE
        val lights = BlockPos.iterate(player.blockPos.add(-r, -2, -r), player.blockPos.add(r, 8, r)).map { it.toImmutable() }
            .filter { world.getBlockState(it).isOf(ModBlocks.FLUORESCENT_LIGHT) }
        falling = Falling(player.uuid, world, lid, lights)
        player.playSound(ModSounds.COLLAPSE, SoundCategory.BLOCKS, 1f, 1f)
        if (ServerPlayNetworking.canSend(player, CollapseHooks.SHAKE_PACKET_ID)) {
            ServerPlayNetworking.send(player, CollapseHooks.SHAKE_PACKET_ID, PacketByteBufs.empty())
        }
        EveryTunnel.LOGGER.info("Level 5: the false exit's floor gives way under {} ({} blocks)", player.name.string, lid.size)
    }

    /** The lid falls away, block by block falling with the player; the lights round it go out; the music starts. */
    private fun drop(run: Falling, player: ServerPlayerEntity) {
        for (pos in run.lid) {
            val state = run.world.getBlockState(pos)
            if (!state.isAir) FallingBlockEntity.spawnFromBlock(run.world, pos, state)
        }
        for (pos in run.lights) setLit(run.world, pos, false)
        if (ServerPlayNetworking.canSend(player, FALL_PACKET_ID)) ServerPlayNetworking.send(player, FALL_PACKET_ID, PacketByteBufs.empty())
    }

    private fun setLit(world: ServerWorld, pos: BlockPos, lit: Boolean) {
        val state = world.getBlockState(pos)
        if (state.isOf(ModBlocks.FLUORESCENT_LIGHT) && state.get(FluorescentLightBlock.LIT) != lit) {
            world.setBlockState(pos, state.with(FluorescentLightBlock.LIT, lit).with(FluorescentLightBlock.BURST, false))
        }
    }
}
