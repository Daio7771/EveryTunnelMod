package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.EveryTunnel
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** Chapter 2's items, registered by [daio7771.everytunnel.chapter2.Chapter2]; ids are the mod's (`every_tunnel:`). */
object Ch2Items {

    /** Chapter 2 Level 1's third task: the broom for the dirt of the west corridor (`Ch2L1TaskHooks`); nothing else uses it. */
    val BROOM: Item = Item(Item.Settings().maxCount(1))

    fun register() {
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "broom"), BROOM)
    }
}
