package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.platform.FactoryNpcEntity
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

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "factory_npc"), FACTORY_NPC)
        FabricDefaultAttributeRegistry.register(FACTORY_NPC, FactoryNpcEntity.createAttributes())
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "scientist"), SCIENTIST)
    }
}
