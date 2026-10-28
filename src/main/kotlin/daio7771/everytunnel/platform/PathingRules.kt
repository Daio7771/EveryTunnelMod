package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import net.fabricmc.fabric.api.registry.LandPathNodeTypesRegistry
import net.minecraft.block.SlabBlock
import net.minecraft.entity.ai.pathing.PathNodeType
import net.minecraft.registry.Registries

/**
 * How mobs (the Pale man, the Observer) path round the mod's blocks.
 *
 * Vanilla's land pathfinding treats any block that is not a full cube as open ground, and lets
 * a mob jump onto anything up to a block high and walk across the top. So they walked into
 * shelves and lockers and got stuck, and climbed onto desks and tables. Every block of the mod
 * that has collision (in its current state: an open door or shutter has none) is marked
 * [PathNodeType.DAMAGE_OTHER] instead, which a mob never paths through, nor onto: vanilla
 * gives the place above a block of that type the same type. As a neighbour it counts for
 * nothing, so they still walk right past it. Walk-through blocks (plants, chairs, stains,
 * notes, loot) keep vanilla's rules, and so do the blocks that are floors: full cubes (the
 * checker plate) and slabs (the spiral stairs' metal grating).
 */
object PathingRules {

    fun register() {
        var count = 0
        for (block in Registries.BLOCK) {
            if (Registries.BLOCK.getId(block).namespace != EveryTunnel.MOD_ID) continue
            // Floors stay floors: full cubes (checker plate) and slabs (the stairs' grating).
            if (block is SlabBlock) continue
            LandPathNodeTypesRegistry.registerDynamic(block) { state, world, pos, neighbor ->
                when {
                    neighbor || state.isFullCube(world, pos) -> null
                    state.getCollisionShape(world, pos).isEmpty -> null
                    else -> PathNodeType.DAMAGE_OTHER
                }
            }
            count++
        }
        EveryTunnel.LOGGER.debug("Pathing rules for {} blocks", count)
    }
}
