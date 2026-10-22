package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.util.UUID

/**
 * Walking from one level into the next (a higher `order`), as set by the level's `entry`:
 *
 * - The level's title and subtitle on screen, and its sound in the player's head.
 * - Every zone of the new level tagged [SHUTTER_TAG] closes behind the player: once the
 *   player is in the new level and out of the zone's opening, the empty blocks of the
 *   zone fill with [ShutterBlock] row by row from the top, with a rattle and a slam
 *   (`block.shutter.close`). There is no way back.
 *
 * Reopening the world or respawning inside a level does not count as walking in.
 */
object LevelEntryHooks {

    const val SHUTTER_TAG = "shutter"

    /** Title timing in ticks: fade in, stay, fade out. */
    private const val TITLE_FADE_IN = 20
    private const val TITLE_STAY = 60
    private const val TITLE_FADE_OUT = 30
    /** A shutter comes down one row every this many ticks. */
    private const val ROW_TICKS = 2

    /** A shutter waiting for [playerId] to step clear of it. */
    private data class Pending(val playerId: UUID, val levelId: String, val dimension: String, val box: BlockBox)

    /** A shutter coming down: the next row to fill is [nextY]. */
    private class Closing(val world: ServerWorld, val box: BlockBox, var nextY: Int, var ticks: Int = 0)

    // Server thread only.
    private val pending = mutableListOf<Pending>()
    private val closing = mutableListOf<Closing>()
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            pending.clear()
            closing.clear()
        }
        LevelTriggers.register { change ->
            val from = change.previous.levelId?.let(LevelManager::get) ?: return@register
            val to = change.current.levelId?.let(LevelManager::get) ?: return@register
            if (to.order <= from.order) return@register
            val player = server?.playerManager?.getPlayer(change.playerId) ?: return@register
            enter(player, to)
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    private fun enter(player: ServerPlayerEntity, level: LevelDefinition) {
        if (level.entry == null) return
        EveryTunnel.LOGGER.info("{} walked into {}", player.name.string, level.id)
        announce(player, level)
        for (zone in level.zones) {
            if (SHUTTER_TAG in zone.tags) pending += Pending(player.uuid, level.id, level.dimension, zone.box)
        }
    }

    /** The level's entry title and sound for [player] (also used when a level's intro ends). */
    fun announce(player: ServerPlayerEntity, level: LevelDefinition) {
        val entry = level.entry ?: return
        if (entry.title.isNotEmpty() || entry.subtitle.isNotEmpty()) {
            player.networkHandler.sendPacket(TitleFadeS2CPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT))
            player.networkHandler.sendPacket(SubtitleS2CPacket(Text.literal(entry.subtitle).formatted(Formatting.GRAY)))
            player.networkHandler.sendPacket(TitleS2CPacket(Text.literal(entry.title).formatted(Formatting.GRAY)))
        }
        entry.sound?.let { id ->
            val sound = Registries.SOUND_EVENT.get(Identifier(id))
            if (sound == null) {
                EveryTunnel.LOGGER.warn("Level {}: entry sound {} is not a registered sound event", level.id, id)
            } else {
                player.playSound(sound, SoundCategory.PLAYERS, 1f, 1f)
            }
        }
    }

    private fun tick(server: MinecraftServer) {
        pending.removeIf { shutter ->
            val player = server.playerManager.getPlayer(shutter.playerId) ?: return@removeIf true
            if (PlayerLocationTracker.locationOf(player.uuid).levelId != shutter.levelId) return@removeIf false
            val opening = Box(
                shutter.box.minX.toDouble(), shutter.box.minY.toDouble(), shutter.box.minZ.toDouble(),
                shutter.box.maxX + 1.0, shutter.box.maxY + 1.0, shutter.box.maxZ + 1.0,
            ).expand(0.5)
            if (player.boundingBox.intersects(opening)) return@removeIf false
            val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(shutter.dimension))) ?: return@removeIf true
            startClosing(world, shutter.box)
            true
        }
        closing.removeIf { shutter ->
            if (shutter.ticks++ % ROW_TICKS != 0) return@removeIf false
            val state = ShutterBlock.across(shutter.box.maxX - shutter.box.minX + 1, shutter.box.maxZ - shutter.box.minZ + 1)
            for (x in shutter.box.minX..shutter.box.maxX) {
                for (z in shutter.box.minZ..shutter.box.maxZ) {
                    val pos = BlockPos(x, shutter.nextY, z)
                    if (shutter.world.getBlockState(pos).isAir) shutter.world.setBlockState(pos, state)
                }
            }
            --shutter.nextY < shutter.box.minY
        }
    }

    private fun startClosing(world: ServerWorld, box: BlockBox) {
        val anyOpen = (box.minX..box.maxX).any { x ->
            (box.minY..box.maxY).any { y -> (box.minZ..box.maxZ).any { z -> world.getBlockState(BlockPos(x, y, z)).isAir } }
        }
        if (!anyOpen) return
        closing += Closing(world, box, box.maxY)
        world.playSound(
            null, (box.minX + box.maxX + 1) / 2.0, (box.minY + box.maxY + 1) / 2.0, (box.minZ + box.maxZ + 1) / 2.0,
            ModSounds.SHUTTER_CLOSE, SoundCategory.BLOCKS, 1f, 1f,
        )
        EveryTunnel.LOGGER.info("Shutter closing at {}", box)
    }
}
