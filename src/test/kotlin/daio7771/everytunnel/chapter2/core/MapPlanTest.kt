package daio7771.everytunnel.chapter2.core

import daio7771.everytunnel.chapter2.prologue.core.Cell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The plan format shared by Chapter 2's levels: its mixes, picked per cell as the Python generators pick them, and its gates. */
class MapPlanTest {

    @Test
    fun `the cell hash is the generator's, bit for bit`() {
        // From tools/map/ch2l2/level.py: cell_hash(x, y, z, seed).
        assertEquals(4080543237L, MapPlan.cellHash(0, 0, 0, 7))
        assertEquals(639632296L, MapPlan.cellHash(1, 2, 3, 7))
        assertEquals(1428284698L, MapPlan.cellHash(-5, -9, 13, 226))
        assertEquals(3326487719L, MapPlan.cellHash(1279, 95, 1450, 12345))
        assertEquals(569895420L, MapPlan.cellHash(-100, -100, -100, 0))
    }

    @Test
    fun `a mix picks the generator's block and keeps to its weights`() {
        val mix = listOf(MapPlan.Weighted("a", 10), MapPlan.Weighted("b", 3), MapPlan.Weighted("c", 3))
        assertEquals("c", MapPlan.pick(mix, 4, -2, 9, 220))      // level.pick(...) gives "c"
        val counts = (0 until 4000).groupingBy { MapPlan.pick(mix, it, it / 7, -it, 3) }.eachCount()
        assertTrue(counts.getValue("a") in 2200..2800, "$counts")
        assertTrue(counts.getValue("b") in 550..950, "$counts")
    }

    @Test
    fun `a plan reads fills of one block, mixes and gates`() {
        val plan = MapPlan.parse(
            """
            {"origin":[10,64,20],"clear":{"from":[0,-1,0],"to":[4,3,4]},
             "fills":[{"from":[0,-1,0],"to":[4,-1,4],"block":"minecraft:stone_bricks"},
                      {"from":[0,0,0],"to":[0,2,4],"mix":[["minecraft:stone_bricks",3],["minecraft:mossy_stone_bricks",1]],"seed":9}],
             "lights":[],"texts":[],"messages":[],
             "gates":[{"id":"g","cells":[[2,0,0],[2,1,0]],"states":{"open":"minecraft:air","grate":"minecraft:iron_bars"},"built":"open"}]}
            """.trimIndent(),
        )
        assertEquals(Cell(10, 64, 20), plan.origin)
        assertEquals("minecraft:stone_bricks", plan.fills[0].block)
        assertEquals("minecraft:stone_bricks", plan.fills[0].blockAt(3, -1, 3))
        val mixed = plan.fills[1]
        assertEquals(9, mixed.seed)
        assertEquals(MapPlan.pick(mixed.mix, 0, 1, 2, 9), mixed.blockAt(0, 1, 2))
        assertEquals(setOf("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:air", "minecraft:iron_bars"), plan.allBlocks())
        assertEquals("open", plan.gate("g")?.built)
        assertEquals(2, plan.gate("g")?.cells?.size)
        assertEquals(25L + 15L, plan.filledCells())
    }

    @Test
    fun `a plan without mixes or gates is Level 1's format, and its fingerprint follows its text`() {
        val text = """{"origin":[0,0,0],"clear":{"from":[0,0,0],"to":[1,1,1]},"fills":[{"from":[0,0,0],"to":[0,0,0],"block":"minecraft:bricks"}],"lights":[]}"""
        val plan = MapPlan.parse(text)
        assertTrue(plan.gates.isEmpty())
        assertEquals(1, plan.fills.single().mix.size)
        assertNotEquals(plan.fingerprint, MapPlan.parse(text.replace("bricks", "stone_bricks")).fingerprint)
    }
}
