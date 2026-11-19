package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.platform.FactoryNpcEntity
import daio7771.everytunnel.chapter2.level2.platform.ObservantGuardEntity
import daio7771.everytunnel.chapter2.level2.platform.ThrownPipeEntity
import daio7771.everytunnel.chapter2.level2.platform.VibrationCreatureEntity
import daio7771.everytunnel.chapter2.prologue.platform.ScientistEntity
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** Chapter 2's entities, registered by [daio7771.everytunnel.chapter2.Chapter2]; ids are the mod's (`every_tunnel:`). */
object Ch2Entities {

    /** A person of Chapter 2's Level 1: Rowan, the coworker, the workers ([FactoryNpcEntity]); the skin tells them apart. Never saved. */
    val FACTORY_NPC: EntityType<FactoryNpcEntity> = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::FactoryNpcEntity)
        .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
        .disableSaving()
        .disableSummon()
        .trackRangeBlocks(64)
        .trackedUpdateRate(1)
        .build()

    /** The scientist at his bench in Chapter 2's prologue (`PrologueSceneHooks`). Never saved. */
    val SCIENTIST: EntityType<ScientistEntity> =
        FabricEntityTypeBuilder.create<ScientistEntity>(SpawnGroup.MISC) { type, world -> ScientistEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
            .disableSaving()
            .disableSummon()
            .trackRangeBlocks(64)
            .build()

    /** The vibration creature of Level 2's pump room (phase 4). Never saved: the level puts it back when it starts. */
    val VIBRATION_CREATURE: EntityType<VibrationCreatureEntity> =
        FabricEntityTypeBuilder.create<VibrationCreatureEntity>(SpawnGroup.MISC) { type, world -> VibrationCreatureEntity(type, world) }
            .dimensions(EntityDimensions.fixed(1.6f, 3.5f))
            .disableSaving()
            .disableSummon()
            .trackRangeBlocks(96)
            .build()

    /** The Observant Guard of Level 2's vestibule and maintenance room (phase 5). Never saved: `Ch2L2Guard` makes it when the wall bursts. */
    val OBSERVANT_GUARD: EntityType<ObservantGuardEntity> =
        FabricEntityTypeBuilder.create<ObservantGuardEntity>(SpawnGroup.MISC) { type, world -> ObservantGuardEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.9f, 2.5f))
            .disableSaving()
            .disableSummon()
            .trackRangeBlocks(80)
            .build()

    /** A piece of pipe in flight (phase 6.1): thrown at Stone Maw, it breaks on whatever it hits. Never saved. */
    val THROWN_PIPE: EntityType<ThrownPipeEntity> =
        FabricEntityTypeBuilder.create<ThrownPipeEntity>(SpawnGroup.MISC) { type, world -> ThrownPipeEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
            .disableSaving()
            .disableSummon()
            .trackRangeBlocks(64)
            .trackedUpdateRate(2)
            .build()

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "thrown_pipe"), THROWN_PIPE)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "factory_npc"), FACTORY_NPC)
        FabricDefaultAttributeRegistry.register(FACTORY_NPC, FactoryNpcEntity.createAttributes())
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "scientist"), SCIENTIST)
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "vibration_creature"), VIBRATION_CREATURE)
        FabricDefaultAttributeRegistry.register(VIBRATION_CREATURE, VibrationCreatureEntity.createAttributes())
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "observant_guard"), OBSERVANT_GUARD)
        FabricDefaultAttributeRegistry.register(OBSERVANT_GUARD, ObservantGuardEntity.createAttributes())
    }
}
