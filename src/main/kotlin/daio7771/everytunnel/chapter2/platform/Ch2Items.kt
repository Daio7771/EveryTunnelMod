package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.common.EveryTunnel
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.Identifier

/** Chapter 2's items (the broom, the reagent bottles, the badge's copy, Rowan's pass), registered by [daio7771.everytunnel.chapter2.Chapter2]; ids are the mod's (`every_tunnel:`). */
object Ch2Items {

    /** Chapter 2 Level 1's third task: the broom for the dirt of the west corridor (`Ch2L1TaskHooks`); nothing else uses it. */
    val BROOM: Item = Item(Item.Settings().maxCount(1))

    /** Act 2: the copy of a lab badge the coworker hands over in the meeting; it opens only the auxiliary lab's door. */
    val LAB_BADGE_COPY: Item = Item(Item.Settings().maxCount(1))

    /** Act 3: Rowan's "Unpaid Overtime Pass", slid across his desk at the end of the office's scene; it opens the sealed zone. */
    val OVERTIME_PASS: Item = Item(Item.Settings().maxCount(1))

    /** The reagents' task: a bottle taken from the LAB's reagent cabinet, to pour into the mixing tank (`Ch2L1LabTaskHooks`). */
    val REAGENTS: Map<String, Item> = listOf("blue", "yellow", "red").associateWith { Item(Item.Settings().maxCount(1)) }

    /** Chapter 2 Level 2, Stone Maw's arena (phase 6.1): a piece of pipe, picked up and thrown with a right click ([daio7771.everytunnel.chapter2.level2.platform.PipePieceItem]). */
    val PIPE_PIECE: Item = daio7771.everytunnel.chapter2.level2.platform.PipePieceItem(Item.Settings().maxCount(1))

    fun register() {
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "pipe_piece"), PIPE_PIECE)
        for ((colour, item) in REAGENTS) Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "factory_reagent_$colour"), item)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "broom"), BROOM)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "lab_badge_copy"), LAB_BADGE_COPY)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "overtime_pass"), OVERTIME_PASS)
    }
}
