package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.MapColor
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.Identifier

/**
 * The mod's blocks, for building the map in Creative (Functional Blocks tab, or
 * `/give @s every_tunnel:fluorescent_light`).
 */
object ModBlocks {

    val FLUORESCENT_LIGHT: Block = FluorescentLightBlock(
        AbstractBlock.Settings.create()
            .mapColor(MapColor.WHITE_GRAY)
            .strength(0.3f)
            .sounds(BlockSoundGroup.GLASS)
            .nonOpaque()
            .luminance { FluorescentLightBlock.luminance(it) },
    )

    fun register() {
        val id = Identifier(EveryTunnel.MOD_ID, "fluorescent_light")
        Registry.register(Registries.BLOCK, id, FLUORESCENT_LIGHT)
        val item = Registry.register(Registries.ITEM, id, BlockItem(FLUORESCENT_LIGHT, Item.Settings()))
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register { it.add(item) }
    }
}
