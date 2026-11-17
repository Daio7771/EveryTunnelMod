package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.chapter2.core.MapPlan
import daio7771.everytunnel.chapter2.prologue.core.BlockSpec
import daio7771.everytunnel.chapter2.prologue.core.Cell
import java.io.File
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chapter 2's Level 2 as the jar has it: the plan `tools/map/ch2l2/generate.py` wrote and the anchors beside it. The generator's own
 * checks (`checks.py`: the route walked, no shortcuts, the pump room's length, the water held) run before it writes anything; this
 * holds the written files to what the mod needs.
 */
class Ch2L2MapPlanTest {

    private val plan = MapPlan.load(Ch2L2Files.PLAN)
    private val assets = File("src/main/resources/assets/every_tunnel")
    private val data = Ch2L2Data.parse(emptyMap(), Ch2L2Files.readAllBundled())

    /** The block of every cell the fills set, mixes resolved as the build resolves them. */
    private val cells: Map<Cell, String> by lazy {
        val out = HashMap<Cell, String>()
        for (f in plan.fills) for (x in f.from.x..f.to.x) for (y in f.from.y..f.to.y) for (z in f.from.z..f.to.z) {
            val previous = out.put(Cell(x, y, z), f.blockAt(x, y, z))
            assertEquals(null, previous, "cell ${Cell(x, y, z)} is set twice")
        }
        out
    }

    @Test
    fun `the plan stands south of Level 1's and has a body`() {
        assertEquals(Cell(1024, 64, 1280), plan.origin)
        assertTrue(plan.fills.size > 500, "${plan.fills.size} fills")
        assertEquals(plan.filledCells(), cells.size.toLong())
        val (a, b) = plan.clear
        for (f in plan.fills) {
            assertTrue(f.from.x >= a.x && f.to.x <= b.x && f.from.y >= a.y && f.to.y <= b.y && f.from.z >= a.z && f.to.z <= b.z, "fill $f is outside ${plan.clear}")
        }
    }

    /**
     * The world's resource pack re-skins some vanilla blocks as Chapter 1's (`level_resources.zip`). The catacombs use none of them but
     * Level 5's drain's stand-ins, which are meant: the factory's drainage looks like that drain.
     */
    @Test
    fun `every block exists and no vanilla one is re-skinned by the world's pack but the drain's`() {
        val drain = setOf("packed_mud", "mud_bricks", "mud_brick_stairs", "dripstone_block", "dead_horn_coral_block")
        val reskinned = setOf(
            "bamboo_planks", "black_glazed_terracotta", "brown_terracotta", "calcite", "cherry_planks", "chiseled_bookshelf",
            "cobbled_deepslate", "cobbled_deepslate_slab", "cut_red_sandstone", "cyan_concrete", "cyan_terracotta", "dark_oak_planks",
            "deepslate_tiles", "dried_kelp_block", "glow_lichen", "gray_concrete", "gray_terracotta", "green_wool", "hopper", "iron_block",
            "light_blue_terracotta", "light_blue_wool", "light_gray_concrete", "light_gray_terracotta", "lime_concrete", "lime_terracotta",
            "mangrove_planks", "orange_terracotta", "pink_terracotta", "pointed_dripstone", "polished_andesite", "polished_andesite_stairs",
            "polished_blackstone", "purple_terracotta", "quartz_block", "quartz_bricks", "red_candle", "red_concrete", "red_nether_brick_stairs",
            "red_wool", "sculk_vein", "sea_lantern", "smooth_basalt", "smooth_quartz", "smooth_red_sandstone", "smooth_sandstone", "smooth_stone",
            "stone", "tuff", "waxed_cut_copper", "waxed_cut_copper_stairs", "white_concrete", "white_glazed_terracotta", "white_terracotta",
            "yellow_concrete", "yellow_glazed_terracotta", "yellow_terracotta",
        )
        for (block in plan.allBlocks()) {
            val (namespace, name) = BlockSpec.parse(block).id.split(":")
            when (namespace) {
                "minecraft" -> assertTrue(name in drain || name !in reskinned, "$block: the world's pack re-skins it as Chapter 1's")
                "every_tunnel" -> assertTrue(File(assets, "blockstates/$name.json").isFile, "$block: no blockstate (run tools/blocks/generate.py)")
                else -> error("$block: unknown namespace")
            }
        }
    }

    @Test
    fun `the anchors parse and name every place of the route and its zones`() {
        assertEquals(emptyList(), data.problems)
        val route = listOf(
            "sealed_entry", "collector_junction", "hunter_galleries", "pump_overlook", "vibration_chamber", "lower_catacombs",
            "observer_wall", "observer_chamber", "return_gallery", "flood_collector", "rising_walkways", "upper_gallery", "pumping_station",
            "level_exit",
        )
        for (id in route) assertTrue(data.anchors.point(id) != null, "no anchor $id")
        for (id in route - "collector_junction") assertTrue(data.anchors.zone("zone_$id") != null, "no zone zone_$id")      // the balcony is the galleries'
        assertTrue(data.anchors.zone("level_box") != null)
    }

    @Test
    fun `every place to stand is air with a floor under it, and the wall where the Guard comes out is a wall`() {
        for (point in data.anchors.points) {
            val x = floor(point.position.x).toInt() - plan.origin.x
            val y = floor(point.position.y).toInt() - plan.origin.y
            val z = floor(point.position.z).toInt() - plan.origin.z
            if (point.id.endsWith("_face")) {
                assertTrue(Cell(x, y, z - 1) in cells, "${point.id}: no wall behind it")
                continue
            }
            assertTrue(Cell(x, y, z) !in cells && Cell(x, y + 1, z) !in cells, "${point.id}: no room to stand at ${point.position}")
            assertTrue(Cell(x, y - 1, z) in cells, "${point.id}: no floor under ${point.position}")
        }
    }

    @Test
    fun `the vestibule's grate is open in the build, with a grate and a seal for later`() {
        val gate = plan.gate("vestibule_gate") ?: error("no vestibule_gate")
        assertEquals("open", gate.built)
        assertEquals("minecraft:air", gate.states["open"])
        assertTrue(gate.states.getValue("grate").startsWith("minecraft:iron_bars"))
        assertTrue(gate.states.getValue("sealed").startsWith("every_tunnel:sluice_gate"))
        for (c in gate.cells) assertTrue(c !in cells, "the open gate's cell $c is filled")
    }

    @Test
    fun `the lamps are lamps`() {
        assertTrue(plan.lights.isNotEmpty())
        for (c in plan.lights) assertTrue(cells[c]?.startsWith("every_tunnel:cage_lamp") == true, "no lamp at $c")
    }
}
