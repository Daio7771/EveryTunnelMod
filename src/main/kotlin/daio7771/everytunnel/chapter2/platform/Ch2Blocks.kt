package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.platform.AnalyzerBlock
import daio7771.everytunnel.chapter2.level1.platform.BadgeReaderBlock
import daio7771.everytunnel.chapter2.level1.platform.KeypadBlock
import daio7771.everytunnel.chapter2.level1.platform.PortraitBlock
import daio7771.everytunnel.chapter2.level1.platform.DirtSpotBlock
import daio7771.everytunnel.chapter2.level1.platform.LeaningBroomBlock
import daio7771.everytunnel.chapter2.level1.platform.NonameTerminalBlock
import daio7771.everytunnel.chapter2.level1.platform.SampleTubeBlock
import daio7771.everytunnel.chapter2.level1.platform.TimeClockBlock
import daio7771.everytunnel.platform.BlockShapes.box
import daio7771.everytunnel.platform.LinkedDoorBlock
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.ModSoundGroups
import daio7771.everytunnel.platform.PropBlock
import daio7771.everytunnel.platform.RowFurnitureBlock
import daio7771.everytunnel.platform.SecurityCameraBlock
import daio7771.everytunnel.platform.SecurityCameraBlockEntity
import daio7771.everytunnel.platform.StackFurnitureBlock
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.block.Block
import net.minecraft.block.DoorBlock
import net.minecraft.block.MapColor
import net.minecraft.block.TransparentBlock
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.Identifier

/**
 * Chapter 2's blocks, registered by [daio7771.everytunnel.chapter2.Chapter2] after Chapter 1's ([ModBlocks], whose settings and
 * shapes they share), and added to the end of the same "Every Tunnel" creative tab. Their ids are the mod's (`every_tunnel:`), as
 * before the chapters were split, so worlds keep them. Models and textures come from `tools/blocks/generate.py`, which reads
 * this file too.
 */
object Ch2Blocks {

    private val all = mutableListOf<Pair<String, Block>>()

    /** The time clock of Chapter 2's Level 1: a card reader that clocks the new hire in ([TimeClockBlock]); the same models. */
    val TIME_CLOCK: Block = register(
        "time_clock",
        TimeClockBlock(ModBlocks.props(MapColor.BLACK, ModSoundGroups.PLASTIC).luminance { 2 }.noCollision()),
    )

    /** The predecessor's locker in the lockers' room: Level 3's locker, its door ajar and the name on its plate scratched out. */
    val PREDECESSOR_LOCKER: Block = register("predecessor_locker", StackFurnitureBlock(ModBlocks.props(MapColor.IRON_GRAY, ModSoundGroups.SHEET_METAL), listOf(box(1, 0, 1, 15, 16, 16))))

    /** The factory's decorative cameras: Level 5's camera, sweeping instead of following the player (its own block entity type). */
    val FACTORY_CAMERA: Block = register("factory_camera", SecurityCameraBlock(ModBlocks.props(MapColor.WHITE, ModSoundGroups.PLASTIC).noCollision()) { FACTORY_CAMERA_ENTITY })

    /**
     * Chapter 2's prologue, on the scientist's bench (`tools/prologue/room.py`): a radiation counter whose amber screen
     * keeps climbing, a voice recorder with its red light blinking, a rack of glowing test tubes. Only to look at.
     */
    val RADIATION_COUNTER: Block = register("radiation_counter", PropBlock(ModBlocks.props(MapColor.BLACK, ModSoundGroups.PLASTIC).noCollision().luminance { 6 }, listOf(box(2, 0, 5, 14, 9, 11))))
    val VOICE_RECORDER: Block = register("voice_recorder", PropBlock(ModBlocks.props(MapColor.BLACK, ModSoundGroups.PLASTIC).noCollision().luminance { 2 }, listOf(box(6, 0, 4, 10, 2, 11))))
    val TUBE_RACK: Block = register("tube_rack", PropBlock(ModBlocks.props(MapColor.WHITE, ModSoundGroups.PLASTIC).noCollision().luminance { 4 }, listOf(box(2, 0, 5, 14, 10, 9))))

    /** Chapter 2 Level 1's tasks (`Ch2L1TaskHooks`): a tube on the bench, the terminal that registers them, the analyzer, the broom, the dirt. */
    val SAMPLE_TUBE: Block = register("sample_tube", SampleTubeBlock(ModBlocks.props(MapColor.WHITE, ModSoundGroups.PLASTIC).noCollision(), listOf(box(6.5, 0, 6.5, 9.5, 9.5, 9.5))))
    val NONAME_TERMINAL: Block = register("noname_terminal", NonameTerminalBlock(ModBlocks.props(MapColor.WHITE, ModSoundGroups.PLASTIC).noCollision().luminance { 3 }, listOf(box(3, 0, 1, 13, 12, 11))))
    val ANALYZER: Block = register("analyzer", AnalyzerBlock(ModBlocks.props(MapColor.WHITE, ModSoundGroups.PLASTIC).noCollision(), listOf(box(2, 0, 3, 14, 10, 13))))
    val LEANING_BROOM: Block = register("leaning_broom", LeaningBroomBlock(ModBlocks.props(MapColor.BROWN, ModSoundGroups.WOOD).noCollision(), listOf(box(6, 0, 7, 10, 16, 16))))
    val DIRT_SPOT: Block = register("dirt_spot", DirtSpotBlock(ModBlocks.props(MapColor.BROWN, BlockSoundGroup.GRAVEL).noCollision(), listOf(box(0, 0, 0, 16, 1, 16))))

    /**
     * Chapter 2 Level 1's Act 3 (`Ch2L1Act3Hooks`): the badge reader by the auxiliary lab's door (the card reader's models), the keypad by
     * the office's, the film set's studio light and cine camera in the auxiliary lab (only to look at), and Rowan's portrait in his office.
     */
    val BADGE_READER: Block = register("badge_reader", BadgeReaderBlock(ModBlocks.props(MapColor.BLACK, ModSoundGroups.PLASTIC).luminance { 2 }.noCollision()))
    val KEYPAD: Block = register("keypad", KeypadBlock(ModBlocks.props(MapColor.IRON_GRAY, ModSoundGroups.SHEET_METAL).luminance { 2 }.noCollision()))
    val FILM_LIGHT: Block = register("film_light", PropBlock(ModBlocks.props(MapColor.BLACK, ModSoundGroups.METAL_PIPE).luminance { 12 }, listOf(box(5, 0, 5, 11, 16, 11))))
    val FILM_CAMERA: Block = register("film_camera", PropBlock(ModBlocks.props(MapColor.BLACK, ModSoundGroups.METAL_PIPE), listOf(box(5, 0, 5, 11, 16, 11))))
    val ROWAN_PORTRAIT: Block = register("rowan_portrait", PortraitBlock(ModBlocks.props(MapColor.BROWN, ModSoundGroups.WOOD).noCollision(), listOf(box(0, 0, 15, 16, 16, 16))))

    /** The LAB's ceiling lights after Rowan's office (`Ch2L1OfficeHooks`): the lamp switched off, and a faint red emergency one here and there. */
    val FACTORY_LIGHT_OFF: Block = register("factory_light_off", Block(ModBlocks.props(MapColor.GRAY, ModSoundGroups.PLASTIC)))
    val FACTORY_LIGHT_EMERGENCY: Block = register("factory_light_emergency", Block(ModBlocks.props(MapColor.RED, ModSoundGroups.PLASTIC).luminance { 4 }))

    /** The lab door's look (it borrows its models), for the factory: a pair of leaves opens and shuts at once by hand. */
    val FACTORY_DOOR: Block = register("factory_door", LinkedDoorBlock(ModBlocks.door(), ModBlocks.SERVICE_DOOR))

    /** Glass with no frame drawn on each block, a faint tint and a sheen that runs on from one block to the next: the factory's windows. */
    val FACTORY_GLASS: Block = register("factory_glass", TransparentBlock(ModBlocks.glass()))

    /** Chapter 2 Level 1's furnishing (phase 10): the WCs' toilet, sink, mirror, stall partitions and doors; the dining room's warm light. */
    val TOILET: Block = register("toilet", PropBlock(ModBlocks.props(MapColor.WHITE, BlockSoundGroup.STONE), listOf(box(3, 0, 2, 13, 15, 16))))
    val WC_SINK: Block = register("wc_sink", PropBlock(ModBlocks.props(MapColor.WHITE, BlockSoundGroup.STONE), listOf(box(0, 0, 2, 16, 15, 16))))
    val WC_MIRROR: Block = register("wc_mirror", RowFurnitureBlock(ModBlocks.props(MapColor.LIGHT_GRAY, BlockSoundGroup.GLASS).noCollision(), listOf(box(0, 4, 15, 16, 16, 16))))
    val STALL_WALL: Block = register("stall_wall", StackFurnitureBlock(ModBlocks.props(MapColor.LIGHT_GRAY, ModSoundGroups.PLASTIC), listOf(box(7, 0, 0, 9, 16, 16))))
    val STALL_DOOR: Block = register("stall_door", DoorBlock(ModBlocks.door(), ModBlocks.SERVICE_DOOR))
    val WARM_CEILING_LIGHT: Block = register("warm_ceiling_light", Block(ModBlocks.props(MapColor.WHITE, ModSoundGroups.PLASTIC).luminance { 15 }))

    /** The factory cameras' block entity: the same as Level 5's camera's, a type of its own so the client sweeps them. */
    lateinit var FACTORY_CAMERA_ENTITY: BlockEntityType<SecurityCameraBlockEntity>

    private fun register(id: String, block: Block): Block = block.also { all += id to it }

    fun register() {
        val items = all.map { (id, block) ->
            val identifier = Identifier(EveryTunnel.MOD_ID, id)
            Registry.register(Registries.BLOCK, identifier, block)
            Registry.register(Registries.ITEM, identifier, BlockItem(block, Item.Settings()))
        }
        FACTORY_CAMERA_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "factory_camera"),
            FabricBlockEntityTypeBuilder.create({ pos, state -> SecurityCameraBlockEntity(FACTORY_CAMERA_ENTITY, pos, state) }, FACTORY_CAMERA).build(),
        )
        ItemGroupEvents.modifyEntriesEvent(ModBlocks.TAB).register { entries -> items.forEach { entries.add(it) } }
    }
}
