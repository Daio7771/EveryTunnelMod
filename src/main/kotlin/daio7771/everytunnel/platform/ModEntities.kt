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
 * tab, for testing; `/summon every_tunnel:pale_man` works too).
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

    val PALE_MAN_SPAWN_EGG: Item = SpawnEggItem(PALE_MAN, 0xCECCC4, 0x282E3E, Item.Settings())

    fun register() {
        Registry.register(Registries.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "pale_man"), PALE_MAN)
        FabricDefaultAttributeRegistry.register(PALE_MAN, PaleManEntity.createAttributes())
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "pale_man_spawn_egg"), PALE_MAN_SPAWN_EGG)
    }
}
