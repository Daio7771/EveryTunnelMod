package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
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

    /**
     * 0.6 wide, 1.9 tall, so he fits wherever the player fits (two free blocks). The model
     * stands about 2.75 blocks; under a lower ceiling it stoops ([PaleManEntity.stoopAt]).
     */
    val PALE_MAN: EntityType<PaleManEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::PaleManEntity)
        .dimensions(EntityDimensions.fixed(0.6f, PaleManEntity.HITBOX_HEIGHT))
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

    /** The figure in the Cinema's film, on its set only ([FilmSetHooks]). Never saved. */
    val FILM_WALKER: EntityType<FilmWalkerEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::FilmWalkerEntity)
        .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
        .disableSaving()
        .trackRangeBlocks(96)
        .build()

    /** A thrown dart ([DartHooks]). Never saved: a new game starts with them all on the tray. */
    val DART: EntityType<DartEntity> = FabricEntityTypeBuilder.create<DartEntity>(SpawnGroup.MISC) { type, world -> DartEntity(type, world) }
        .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
        .disableSaving()
        .disableSummon()
        .trackRangeBlocks(64)
        .trackedUpdateRate(20)
        .build()

    val PALE_MAN_SPAWN_EGG: Item = SpawnEggItem(PALE_MAN, 0xCECCC4, 0x282E3E, Item.Settings())

    /** The Observer (Level 3): a 1.9-block hitbox like the Pale man's; the model stands 2.5. */
    val OBSERVER: EntityType<ObserverEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::ObserverEntity)
        .dimensions(EntityDimensions.fixed(0.6f, ObserverEntity.HITBOX_HEIGHT))
        .trackRangeBlocks(96)
        .build()

    val OBSERVER_SPAWN_EGG: Item = SpawnEggItem(OBSERVER, 0x1E1E23, 0xACB2BA, Item.Settings())

    /** A Hunter (Level 5), shorter than the player: the model stands about 1.4 blocks. */
    val HUNTER: EntityType<HunterEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::HunterEntity)
        .dimensions(EntityDimensions.fixed(0.5f, 1.35f))
        .disableSaving()
        .trackRangeBlocks(64)
        .build()

    /** The Distortion Cloud (Level 5): it floats, nothing touches it; about 2.4 blocks tall. */
    val DISTORTION_CLOUD: EntityType<DistortionCloudEntity> =
        FabricEntityTypeBuilder.create<DistortionCloudEntity>(SpawnGroup.MISC) { type, world -> DistortionCloudEntity(type, world) }
            .dimensions(EntityDimensions.fixed(1.2f, 2.4f))
            .disableSaving()
            .trackRangeBlocks(64)
            .build()

    /** The figure seen far off from Level 5's Lobby ([LabFigureHooks]). Never saved; tracked from far off. */
    val LAB_FIGURE: EntityType<LabFigureEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::LabFigureEntity)
        .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
        .disableSaving()
        .disableSummon()
        .trackRangeBlocks(128)
        .build()

    /** A test subject in its sealed chamber (Level 5, [SubjectHooks]). Never saved. */
    val SUBJECT: EntityType<SubjectEntity> =
        FabricEntityTypeBuilder.create<SubjectEntity>(SpawnGroup.MISC) { type, world -> SubjectEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.6f, 1.9f))
            .disableSaving()
            .disableSummon()
            .trackRangeBlocks(64)
            .build()

    /** The Distortion Junior (Level 5): a small one, floating at face height. */
    val DISTORTION_JUNIOR: EntityType<DistortionJuniorEntity> =
        FabricEntityTypeBuilder.create<DistortionJuniorEntity>(SpawnGroup.MISC) { type, world -> DistortionJuniorEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.8f, 2.0f))
            .disableSaving()
            .trackRangeBlocks(64)
            .build()

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "pale_man"), PALE_MAN)
        FabricDefaultAttributeRegistry.register(PALE_MAN, PaleManEntity.createAttributes())
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "pale_man_spawn_egg"), PALE_MAN_SPAWN_EGG)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "locker_seat"), LOCKER_SEAT)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "dart"), DART)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "film_walker"), FILM_WALKER)
        FabricDefaultAttributeRegistry.register(FILM_WALKER, FilmWalkerEntity.createAttributes())
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "observer"), OBSERVER)
        FabricDefaultAttributeRegistry.register(OBSERVER, ObserverEntity.createAttributes())
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "observer_spawn_egg"), OBSERVER_SPAWN_EGG)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "hunter"), HUNTER)
        FabricDefaultAttributeRegistry.register(HUNTER, HunterEntity.createAttributes())
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "distortion_cloud"), DISTORTION_CLOUD)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "distortion_junior"), DISTORTION_JUNIOR)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "lab_figure"), LAB_FIGURE)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "subject"), SUBJECT)
        FabricDefaultAttributeRegistry.register(LAB_FIGURE, LabFigureEntity.createAttributes())
    }
}
