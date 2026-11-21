package daio7771.everytunnel.chapter1.platform

import daio7771.everytunnel.chapter1.level3.platform.BoxShelfBlock
import daio7771.everytunnel.chapter1.level3.platform.BoxShelfBlockEntity
import daio7771.everytunnel.chapter1.level3.platform.OfficeBookBlock
import daio7771.everytunnel.chapter1.level3.platform.PurpleBoxBlock
import daio7771.everytunnel.chapter1.level3.platform.ValveBlock
import daio7771.everytunnel.chapter1.level4.platform.BigDoorBlock
import daio7771.everytunnel.chapter1.level4.platform.BreakerButtonBlock
import daio7771.everytunnel.chapter1.level4.platform.BreakerCounterBlock
import daio7771.everytunnel.chapter1.level4.platform.BreakerLeverBlock
import daio7771.everytunnel.chapter1.level4.platform.CalmReaderBlock
import daio7771.everytunnel.chapter1.level4.platform.DartHatchBlock
import daio7771.everytunnel.chapter1.level4.platform.DartTrayBlock
import daio7771.everytunnel.chapter1.level4.platform.DartboardBlock
import daio7771.everytunnel.chapter1.level4.platform.EmergencyLampBlock
import daio7771.everytunnel.chapter1.level4.platform.PuzzleDeviceBlock
import daio7771.everytunnel.chapter1.level4.platform.PuzzleRackBlock
import daio7771.everytunnel.chapter1.level4.platform.SuiteBedBlock
import daio7771.everytunnel.chapter1.level4.platform.SuiteSafeBlock
import daio7771.everytunnel.chapter1.level4.platform.WarmLampBlock
import daio7771.everytunnel.chapter1.level5.platform.AirlockDoorBlock
import daio7771.everytunnel.chapter1.level5.platform.BriefingBoardBlock
import daio7771.everytunnel.chapter1.level5.platform.BriefingSignBlock
import daio7771.everytunnel.chapter1.level5.platform.CageLampBlock
import daio7771.everytunnel.chapter1.level5.platform.CatwalkRailBlock
import daio7771.everytunnel.chapter1.level5.platform.ChalkArrowBlock
import daio7771.everytunnel.chapter1.level5.platform.DrainLadderBlock
import daio7771.everytunnel.chapter1.level5.platform.FuseHolderBlock
import daio7771.everytunnel.chapter1.level5.platform.LabTerminalBlock
import daio7771.everytunnel.chapter1.level5.platform.PanelLightBlock
import daio7771.everytunnel.common.platform.LockerBlock
import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.platform.BlockShapes.box
import daio7771.everytunnel.common.platform.DevWorlds
import daio7771.everytunnel.common.platform.ModBlocks
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.block.type.BlockSetTypeBuilder
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockSetType
import net.minecraft.block.DoorBlock
import net.minecraft.block.MapColor
import net.minecraft.block.SlabBlock
import net.minecraft.block.TransparentBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.block.piston.PistonBehavior
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.Direction
import daio7771.everytunnel.common.platform.BlockShapes
import daio7771.everytunnel.common.platform.FluorescentLightBlock
import daio7771.everytunnel.common.platform.ModSoundGroups

/**
 * Chapter 1's blocks that have a behaviour of their own (the levels' puzzle devices, doors, lockers, valves...), registered by
 * [daio7771.everytunnel.chapter1.Chapter1]; the shared props and the shape library stay in [ModBlocks], and ids are the mod's
 * (`every_tunnel:`), so worlds and saves are not affected.
 */
object Ch1Blocks {

    private val all = mutableListOf<Pair<String, Block>>()

    private fun register(id: String, block: Block): Block = block.also { all += id to it }


    val SUITE_BED: Block = register("suite_bed", SuiteBedBlock(ModBlocks.props(MapColor.DULL_RED, BlockSoundGroup.WOOL)))
    // Level 4's puzzles (LoungePuzzleHooks).
    val RECORD_RACK: Block = register("record_rack", PuzzleRackBlock(ModBlocks.props(MapColor.BROWN, ModSoundGroups.WOOD).emissiveLighting { state, _, _ -> state.get(PuzzleRackBlock.HINT) }.luminance { if (it.get(PuzzleRackBlock.HINT)) 7 else 0 }))
    val REEL_RACK: Block = register("reel_rack", PuzzleRackBlock(ModBlocks.props(MapColor.GRAY, ModSoundGroups.SHEET_METAL).noCollision().emissiveLighting { state, _, _ -> state.get(PuzzleRackBlock.HINT) }.luminance { if (it.get(PuzzleRackBlock.HINT)) 7 else 0 }))
    val RECORD_PLAYER: Block = register("record_player", PuzzleDeviceBlock(ModBlocks.props(MapColor.BROWN, ModSoundGroups.WOOD), listOf(box(1, 0, 1, 15, 16, 15))))
    val PROJECTOR: Block = register("projector", PuzzleDeviceBlock(ModBlocks.props(MapColor.GRAY, ModSoundGroups.SHEET_METAL), listOf(box(2, 0, 2, 14, 16, 14))))
    // Level 4's Maintenance room (BreakerHooks) and safes (SafeHooks).
    val BREAKER_LEVER: Block = register("breaker_lever", BreakerLeverBlock(ModBlocks.props(MapColor.GRAY, ModSoundGroups.SHEET_METAL).noCollision()))
    val BREAKER_BUTTON: Block = register("breaker_button", BreakerButtonBlock(ModBlocks.props(MapColor.RED, ModSoundGroups.PLASTIC).noCollision()))
    val BREAKER_COUNTER: Block = register(
        "breaker_counter",
        BreakerCounterBlock(
            ModBlocks.props(MapColor.BLACK, ModSoundGroups.PLASTIC).noCollision().luminance { if (it.get(BreakerCounterBlock.LIT)) 4 else 0 }
                .emissiveLighting { state, _, _ -> state.get(BreakerCounterBlock.LIT) },
        ),
    )
    val SUITE_SAFE: Block = register("suite_safe", SuiteSafeBlock(ModBlocks.props(MapColor.BLACK, ModSoundGroups.SHEET_METAL)))

    // Level 4's closing (LoungeClosingHooks).
    val CALM_READER: Block = register("calm_reader", CalmReaderBlock(ModBlocks.props(MapColor.GOLD, ModSoundGroups.PLASTIC).noCollision().luminance { if (it.get(CalmReaderBlock.POWERED)) 3 else 0 }))
    val EMERGENCY_LAMP: Block = register(
        "emergency_lamp",
        EmergencyLampBlock(
            ModBlocks.props(MapColor.RED, ModSoundGroups.PLASTIC).noCollision().luminance { if (it.get(EmergencyLampBlock.LIT)) EmergencyLampBlock.LIGHT_LEVEL else 0 }
                .emissiveLighting { state, _, _ -> state.get(EmergencyLampBlock.LIT) },
        ),
    )
    val BIG_DOOR: Block = register("big_door", BigDoorBlock(ModBlocks.props(MapColor.IRON_GRAY, ModSoundGroups.SHEET_METAL)))

    // Level 5.
    /** The airlock's sliding doors, a column of a leaf each; LabHooks slides them. */
    val AIRLOCK_DOOR: Block = register("airlock_door", AirlockDoorBlock(ModBlocks.props(MapColor.IRON_GRAY, ModSoundGroups.SHEET_METAL)))

    // The darts (DartHooks).
    val DARTBOARD: Block = register("dartboard", DartboardBlock(ModBlocks.props(MapColor.BROWN, ModSoundGroups.WOOD).luminance { if (it.get(DartboardBlock.HINT) > 0) 7 else 0 }))
    val DART_TRAY: Block = register("dart_tray", DartTrayBlock(ModBlocks.props(MapColor.BROWN, ModSoundGroups.WOOD).noCollision()))
    val DART_HATCH: Block = register("dart_hatch", DartHatchBlock(ModBlocks.props(MapColor.GOLD, ModSoundGroups.SHEET_METAL)))

    val WARM_LAMP: Block = register(
        "warm_lamp",
        WarmLampBlock(
            ModBlocks.props(MapColor.TERRACOTTA_YELLOW, BlockSoundGroup.GLASS).luminance { WarmLampBlock.luminance(it) }
                .emissiveLighting { state, _, _ -> state.get(WarmLampBlock.LIT) },
        ),
    )
    /** A gate valve inline in a wall pipe, lettered A to E: Level 3's valve corridor. */
    val VALVE: Block = register("valve", ValveBlock(ModBlocks.props(MapColor.RED, ModSoundGroups.METAL_PIPE)))
    /** Level 5's safety briefing: a big cork board in twelve parts, its four sheets read with a right-click (NoteHooks). */
    val BRIEFING_BOARD: Block = register("briefing_board", BriefingBoardBlock(ModBlocks.props(MapColor.BROWN, ModSoundGroups.WOOD).noCollision()))
    /** The SAFETY BRIEFING plate over it, in four parts. */
    val BRIEFING_SIGN: Block = register("briefing_sign", BriefingSignBlock(ModBlocks.props(MapColor.WHITE, ModSoundGroups.PLASTIC).noCollision()))

    /** Level 3's box puzzle ([BoxHooks]): a purple archive box, drawn full bright; picked up with right-click. */
    val PURPLE_BOX: Block = register(
        "purple_box",
        PurpleBoxBlock(ModBlocks.props(MapColor.PURPLE, ModSoundGroups.CARDBOARD).emissiveLighting { _, _, _ -> true }, listOf(box(1.5, 0, 1.5, 14.5, 11, 14.5))),
    )

    /** Level 3's book puzzle ([BookHooks]): an office book lying flat, drawn full bright. */
    val OFFICE_BOOK: Block = register(
        "office_book",
        OfficeBookBlock(ModBlocks.props(MapColor.BLUE, ModSoundGroups.CARDBOARD).noCollision().emissiveLighting { _, _, _ -> true }, listOf(box(4, 0, 3, 12, 2.5, 13))),
    )

    /** The empty shelf in Storage the boxes go on: six parts, 2 wide and 3 high ([BoxShelfBlock]). */
    val BOX_SHELF: Block = register("box_shelf", BoxShelfBlock(ModBlocks.props(MapColor.IRON_GRAY, ModSoundGroups.SHEET_METAL)))

    /** The boxes on a [BoxShelfBlock], at its foot; registered in [register]. */
    lateinit var BOX_SHELF_ENTITY: BlockEntityType<BoxShelfBlockEntity>
        private set

    /** A lab computer on a desk ([LabTerminalBlock]): its screen glows a little. */
    val LAB_TERMINAL: Block = register("lab_terminal", LabTerminalBlock(ModBlocks.props(MapColor.WHITE, ModSoundGroups.PLASTIC).noCollision().luminance { 3 }, listOf(box(3, 0, 1, 13, 12, 11))))
    // Level 5's drain (layer 3): rusty steel, caged lamps; the director's office: black leather, steel, smoked glass.
    /** The ladder from the chemical room's hatch down to the drain ([DrainLadderBlock]). */
    val DRAIN_LADDER: Block = register("drain_ladder", DrainLadderBlock(ModBlocks.props(MapColor.IRON_GRAY, ModSoundGroups.SHEET_METAL)))
    /** The drain's dim caged lamps, on walls and hanging from ceilings ([CageLampBlock]). */
    val CAGE_LAMP: Block = register(
        "cage_lamp",
        CageLampBlock(
            ModBlocks.props(MapColor.YELLOW, BlockSoundGroup.GLASS).noCollision().luminance { CageLampBlock.luminance(it) }
                .emissiveLighting { state, _, _ -> FluorescentLightBlock.glows(state) },
        ),
    )
    /** The anteroom's security camera on the ceiling, following the player ([SecurityCameraBlock]). */
    /** A chalk arrow on a drain wall, in three parts ([ChalkArrowBlock]). */
    val CHALK_ARROW: Block = register("chalk_arrow", ChalkArrowBlock(ModBlocks.props(MapColor.WHITE, BlockSoundGroup.STONE).noCollision()))
    /** Steel railing on a catwalk's edges ([CatwalkRailBlock]). */
    val CATWALK_RAIL: Block = register("catwalk_rail", CatwalkRailBlock(ModBlocks.props(MapColor.IRON_GRAY, ModSoundGroups.SHEET_METAL)))
    /** The office's square ceiling lights ([PanelLightBlock]). */
    val PANEL_LIGHT: Block = register(
        "panel_light",
        PanelLightBlock(
            ModBlocks.props(MapColor.WHITE, BlockSoundGroup.GLASS).noCollision().luminance { FluorescentLightBlock.luminance(it) }
                .emissiveLighting { state, _, _ -> FluorescentLightBlock.glows(state) },
        ),
    )
    /** One slot of Level 5's fuse box ([FuseHolderBlock]). */
    val FUSE_HOLDER: Block = register("fuse_holder", FuseHolderBlock(ModBlocks.props(MapColor.BLACK, ModSoundGroups.PLASTIC).noCollision().luminance { if (it.get(FuseHolderBlock.LIT)) 4 else 0 }))

    fun register() {
        val items = all.map { (id, block) ->
            val identifier = Identifier(EveryTunnel.MOD_ID, id)
            Registry.register(Registries.BLOCK, identifier, block)
            Registry.register(Registries.ITEM, identifier, BlockItem(block, Item.Settings()))
        }
        BOX_SHELF_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "box_shelf"),
            FabricBlockEntityTypeBuilder.create(::BoxShelfBlockEntity, BOX_SHELF).build(),
        )
        ItemGroupEvents.modifyEntriesEvent(ModBlocks.TAB).register { entries ->
            items.forEach { entries.add(it) }
            // The enemies' spawn eggs only while developing: in a release they would put
            // a second Observer or more Pale men into the levels.
            if (DevWorlds.enabled) {
                entries.add(Ch1Entities.PALE_MAN_SPAWN_EGG)
                entries.add(Ch1Entities.OBSERVER_SPAWN_EGG)
            }
        }
    }
}
