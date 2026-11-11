package daio7771.everytunnel.platform

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.Entity
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Entities that are never saved (their types disable saving: they come back with their level) but are put in far
 * from the player: Level 5 is built, and its Hunters, Juniors, subjects and dead guard put in, while the player is
 * still in Level 4. Their chunks unload once the build lets them go, and an entity that is never saved is lost
 * with its chunk. Put in through [spawn], each comes back as it was made, where it was made, once its chunk ticks
 * again. One killed or discarded on purpose is let go; [forget] lets go of those of a level being put back.
 */
object KeptEntities {

    private class Kept(val world: RegistryKey<World>, val pos: BlockPos, val make: (ServerWorld) -> Entity?, var entity: Entity)

    /** How often lost ones are looked for. */
    private const val CHECK_TICKS = 20

    // Server thread only.
    private val kept = mutableListOf<Kept>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerLifecycleEvents.SERVER_STOPPED.register { kept.clear() }
    }

    /** [make]'s entity, put in [world] now and kept; null if it made none. */
    fun <T : Entity> spawn(world: ServerWorld, make: (ServerWorld) -> T?): T? {
        val entity = make(world) ?: return null
        world.spawnEntity(entity)
        kept += Kept(world.registryKey, entity.blockPos, make, entity)
        return entity
    }

    /** No longer kept: those whose entity passes [test] (their level is put back, or gone). */
    fun forget(test: (Entity) -> Boolean) {
        kept.removeAll { test(it.entity) }
    }

    private fun tick(server: MinecraftServer) {
        if (server.ticks % CHECK_TICKS != 0 || kept.isEmpty()) return
        val iterator = kept.iterator()
        while (iterator.hasNext()) {
            val one = iterator.next()
            if (!one.entity.isRemoved) continue
            // Killed or discarded: gone for good. Unloaded with its chunk: back once the chunk ticks again.
            if (one.entity.removalReason?.shouldDestroy() != false) {
                iterator.remove()
                continue
            }
            val world = server.getWorld(one.world) ?: continue
            if (!world.shouldTickEntity(one.pos)) continue
            val fresh = one.make(world) ?: continue
            world.spawnEntity(fresh)
            one.entity = fresh
        }
    }
}
