package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.LevelManager
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * Water dispensers that give water (Level 3's kitchen): a water dispenser at a level's spawn
 * point tagged [TAG]. Right-clicked, the player drinks there, as often as they like: a bottle's
 * worth of thirst each time ([DrinkHooks.quench], a noise like drinking a bottle), at most once
 * per `thirst.drink_seconds`, the water gurgling in the dispenser and a gulp. A full player is
 * told they are not thirsty. Other dispensers are only furniture.
 */
object WaterDispenserHooks {

    const val TAG = "water_source"

    /** Player -> the server tick of their last drink here. */
    private val lastDrink = HashMap<UUID, Int>()

    fun register() {
        UseBlockCallback.EVENT.register { player, world, _, hit ->
            if (world.isClient || player !is ServerPlayerEntity) return@register ActionResult.PASS
            if (!world.getBlockState(hit.blockPos).isOf(ModBlocks.WATER_DISPENSER)) return@register ActionResult.PASS
            if (drink(player, world as ServerWorld, hit.blockPos)) ActionResult.SUCCESS else ActionResult.PASS
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> lastDrink.remove(handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register { lastDrink.clear() }
    }

    private fun drink(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos): Boolean {
        if (!ResourceHooks.appliesTo(player) || !isSource(world, pos)) return false
        if (!DrinkHooks.canDrink(player)) {
            DrinkHooks.explainRefused(player)
            return true
        }
        val now = world.server.ticks
        val last = lastDrink[player.uuid]
        if (last != null && now - last < ConfigManager.gameplay.thirst.drinkSeconds * 20) return true
        lastDrink[player.uuid] = now
        world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 0.8f, 0.8f)
        DrinkHooks.gulp(player)
        DrinkHooks.quench(player)
        return true
    }

    private fun isSource(world: ServerWorld, pos: BlockPos): Boolean {
        val level = LevelManager.levelAt(world.registryKey.value.toString(), pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) ?: return false
        return level.spawnPoints.any { TAG in it.tags && BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) == pos }
    }
}
