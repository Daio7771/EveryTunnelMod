package daio7771.everytunnel.common.platform

import daio7771.everytunnel.common.EveryTunnel
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.item.Item
import net.minecraft.item.SpawnEggItem
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/**
 * The mod's entities: the Pale man, plus his spawn egg (in the "Every Tunnel" creative
 * tab, for testing; `/summon every_tunnel:pale_man` works too), the Observer and his egg
 * (`/summon every_tunnel:observer`), the locker seat, and Level 5's Hunters and Distortions
 * (`/everytunnel spawn`, [DistortionHooks]). These last are never saved: Level 5 is built anew
 * each time it is needed, and they come with it.
 */
object ModEntities {

    /** A Hunter (Level 5), shorter than the player: the model stands about 1.4 blocks, as wide as its box (arms out of walls). */
    val HUNTER: EntityType<HunterEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::HunterEntity)
        .dimensions(EntityDimensions.fixed(0.6f, 1.35f))
        .disableSaving()
        .trackRangeBlocks(64)
        .build()

    /**
     * The invisible seat a player hiding in a locker rides ([LockerSeatEntity], [HidingHooks]).
     * Never saved and not summonable: it only exists while someone is inside.
     */
    val LOCKER_SEAT: EntityType<LockerSeatEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::LockerSeatEntity)
        .dimensions(EntityDimensions.fixed(0.2f, 0.2f))
        .disableSaving()
        .disableSummon()
        .trackRangeBlocks(16)
        .build()

    /** The Distortion Cloud (Level 5): it floats, nothing touches it; about 2.4 blocks tall. */
    val DISTORTION_CLOUD: EntityType<DistortionCloudEntity> =
        FabricEntityTypeBuilder.create<DistortionCloudEntity>(SpawnGroup.MISC) { type, world -> DistortionCloudEntity(type, world) }
            .dimensions(EntityDimensions.fixed(1.2f, 2.4f))
            .disableSaving()
            .trackRangeBlocks(64)
            .trackedUpdateRate(1)      // the entry corridor's follow the player: smooth
            .build()

    /** The Distortion Junior (Level 5): a small one, floating at face height. */
    val DISTORTION_JUNIOR: EntityType<DistortionJuniorEntity> =
        FabricEntityTypeBuilder.create<DistortionJuniorEntity>(SpawnGroup.MISC) { type, world -> DistortionJuniorEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.8f, 2.0f))
            .disableSaving()
            .trackRangeBlocks(64)
            .build()

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "locker_seat"), LOCKER_SEAT)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "hunter"), HUNTER)
        FabricDefaultAttributeRegistry.register(HUNTER, HunterEntity.createAttributes())
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "distortion_cloud"), DISTORTION_CLOUD)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "distortion_junior"), DISTORTION_JUNIOR)
    }
}
