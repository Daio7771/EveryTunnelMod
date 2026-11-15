package daio7771.everytunnel.platform

import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d

/**
 * Putting the factory's people ([FactoryNpcEntity]) in and taking them out. Kept entities ([KeptEntities]): put in through
 * its `spawn`, so they come back where they were if their chunk unloads, and taken out with `forget` and `discard()`.
 * For now only the developer's `/et ch2 l1 npc` does it; the level will place its own (the lockers' group, the workers
 * at their terminals) when its life is built.
 */
object Ch2L1Npcs {

    /** How far in front of the player a person is put. */
    private const val AHEAD = 2.5

    /** A person with [skin] [AHEAD] blocks in front of [player], facing them; null if there is no room. */
    fun spawnInFront(player: ServerPlayerEntity, skin: String): FactoryNpcEntity? {
        val world = player.serverWorld
        val ahead = Vec3d.fromPolar(0f, player.yaw)
        val x = player.x + ahead.x * AHEAD
        val z = player.z + ahead.z * AHEAD
        val y = player.y
        if (!world.isSpaceEmpty(ModEntities.FACTORY_NPC.dimensions.getBoxAt(x, y, z))) return null
        val facing = player.yaw + 180f
        return KeptEntities.spawn(world) { w ->
            ModEntities.FACTORY_NPC.create(w)?.apply {
                refreshPositionAndAngles(x, y, z, facing, 0f)
                headYaw = facing
                bodyYaw = facing
                this.skin = skin
            }
        }
    }

    /** Every one of them, gone; how many there were. */
    fun clear(server: MinecraftServer): Int {
        KeptEntities.forget { it is FactoryNpcEntity }
        var count = 0
        for (world in server.worlds) {
            for (entity in world.iterateEntities().toList()) {
                if (entity is FactoryNpcEntity) {
                    entity.discard()
                    count++
                }
            }
        }
        return count
    }
}
