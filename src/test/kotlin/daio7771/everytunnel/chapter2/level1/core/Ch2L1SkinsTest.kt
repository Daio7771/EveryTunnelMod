package daio7771.everytunnel.chapter2.level1.core

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Ch2L1SkinsTest {

    private val assets = File("src/main/resources/assets/every_tunnel")

    @Test
    fun `the skins are the six workers, the two guards and Rowan, each once`() {
        assertEquals(9, Ch2L1Skins.ALL.size)
        assertEquals(Ch2L1Skins.ALL.size, Ch2L1Skins.ALL.toSet().size)
        assertTrue(Ch2L1Skins.ROWAN in Ch2L1Skins.ALL)
        assertTrue(listOf(Ch2L1Skins.COWORKER, Ch2L1Skins.WORKER_A, Ch2L1Skins.WORKER_B).all { it in Ch2L1Skins.ALL })
    }

    @Test
    fun `an unknown skin draws as the default`() {
        assertEquals("worker_a", Ch2L1Skins.orDefault("worker_a"))
        assertEquals(Ch2L1Skins.DEFAULT, Ch2L1Skins.orDefault("no_such_skin"))
        assertEquals("textures/entity/npc/rowan.png", Ch2L1Skins.texturePath("rowan"))
        assertEquals("textures/entity/npc/generic_1.png", Ch2L1Skins.texturePath("no_such_skin"))
    }

    @Test
    fun `every skin is a 64 by 64 player skin with classic arms and nothing in its second layer`() {
        for (id in Ch2L1Skins.ALL) {
            val file = File(assets, Ch2L1Skins.texturePath(id))
            assertTrue(file.isFile, "no texture for $id (run tools/entity/workers.py)")
            val image = ImageIO.read(file)
            assertEquals(64, image.width, id)
            assertEquals(64, image.height, id)
            fun alpha(x: Int, y: Int) = image.getRGB(x, y) ushr 24
            // Classic arms are 4 px wide: the last column of each arm's back face is painted (a slim arm leaves it empty).
            for (y in 20 until 32) assertEquals(255, alpha(55, y), "$id: the right arm is not 4 px wide")
            for (y in 52 until 64) assertEquals(255, alpha(47, y), "$id: the left arm is not 4 px wide")
            // The second layer (hat, jacket, sleeves, trouser legs): the renderer ignores it, so it must be empty.
            val overlay = listOf(
                intArrayOf(32, 0, 64, 16), intArrayOf(16, 32, 40, 48), intArrayOf(40, 32, 56, 48),
                intArrayOf(0, 32, 16, 48), intArrayOf(0, 48, 16, 64), intArrayOf(48, 48, 64, 64),
            )
            for ((x0, y0, x1, y1) in overlay) {
                for (y in y0 until y1) for (x in x0 until x1) assertEquals(0, alpha(x, y), "$id: ($x, $y) of the second layer is painted")
            }
        }
    }
}
