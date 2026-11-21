package daio7771.everytunnel.chapter1.platform

import daio7771.everytunnel.chapter1.level2.platform.PaleManEntity
import daio7771.everytunnel.chapter1.level3.platform.ObserverEntity
import daio7771.everytunnel.chapter1.level4.platform.DartEntity
import daio7771.everytunnel.chapter1.level4.platform.FilmWalkerEntity
import daio7771.everytunnel.chapter1.level5.platform.DeadGuardEntity
import daio7771.everytunnel.chapter1.level5.platform.DirectorEntity
import daio7771.everytunnel.chapter1.level5.platform.LabFigureEntity
import daio7771.everytunnel.chapter1.level5.platform.SubjectEntity
import daio7771.everytunnel.common.platform.LockerSeatEntity
import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.platform.HunterEntity
import daio7771.everytunnel.common.platform.ModEntities
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
 * Chapter 1's entities (the Pale man, the Observer, the locker seat, the film walker, the dart, the lab figure, the dead guard, the
 * director, the test subjects and the two spawn eggs), registered by [daio7771.everytunnel.chapter1.Chapter1]. Ids are the mod's
 * (`every_tunnel:`). The Hunters and the Distortions, which a later chapter also uses ([DistortionHooks]), stay in [ModEntities].
 */
object Ch1Entities {

    /**
     * 0.6 wide, 1.9 tall, so he fits wherever the player fits (two free blocks). The model
     * stands about 2.75 blocks; under a lower ceiling it stoops ([PaleManEntity.stoopAt]).
     */
    val PALE_MAN: EntityType<PaleManEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ::PaleManEntity)
        .dimensions(EntityDimensions.fixed(0.6f, PaleManEntity.HITBOX_HEIGHT))
        .trackRangeBlocks(64)
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

    /** The figure seen far off from Level 5's Lobby ([LabFigureHooks]). Never saved; tracked from far off. */
    val LAB_FIGURE: EntityType<LabFigureEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::LabFigureEntity)
        .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
        .disableSaving()
        .disableSummon()
        .trackRangeBlocks(128)
        .build()

    /** The guard before you, dead in Level 5's drain ([DeadGuardHooks]). Never saved. */
    val DEAD_GUARD: EntityType<DeadGuardEntity> =
        FabricEntityTypeBuilder.create<DeadGuardEntity>(SpawnGroup.MISC) { type, world -> DeadGuardEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.8f, 1.2f))
            .disableSaving()
            .disableSummon()
            .trackRangeBlocks(64)
            .build()

    /** The director, in his chair at the top of Level 5 ([DirectorHooks]). Never saved. */
    val DIRECTOR: EntityType<DirectorEntity> =
        FabricEntityTypeBuilder.create<DirectorEntity>(SpawnGroup.MISC) { type, world -> DirectorEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.8f, 1.2f))
            .disableSaving()
            .disableSummon()
            .trackRangeBlocks(64)
            .build()

    /** A test subject in its sealed chamber (Level 5, [SubjectHooks]). Never saved. */
    val SUBJECT: EntityType<SubjectEntity> =
        FabricEntityTypeBuilder.create<SubjectEntity>(SpawnGroup.MISC) { type, world -> SubjectEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.6f, 1.9f))
            .disableSaving()
            .disableSummon()
            .trackRangeBlocks(64)
            .build()

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "pale_man"), PALE_MAN)
        FabricDefaultAttributeRegistry.register(PALE_MAN, PaleManEntity.createAttributes())
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "pale_man_spawn_egg"), PALE_MAN_SPAWN_EGG)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "dart"), DART)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "film_walker"), FILM_WALKER)
        FabricDefaultAttributeRegistry.register(FILM_WALKER, FilmWalkerEntity.createAttributes())
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "observer"), OBSERVER)
        FabricDefaultAttributeRegistry.register(OBSERVER, ObserverEntity.createAttributes())
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "observer_spawn_egg"), OBSERVER_SPAWN_EGG)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "lab_figure"), LAB_FIGURE)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "subject"), SUBJECT)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "dead_guard"), DEAD_GUARD)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "director"), DIRECTOR)
        FabricDefaultAttributeRegistry.register(LAB_FIGURE, LabFigureEntity.createAttributes())
    }
}
