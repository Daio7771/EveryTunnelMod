package daio7771.everytunnel.entities

import daio7771.everytunnel.config.CloudConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudGameTest {

    private val config = CloudConfig()
    private val tick = 0.05

    private fun game(random: Double = 0.5) = CloudGame(config, length = 90.0, nearest = -8.0) { random }

    @Test
    fun `it starts behind the player and creeps after them`() {
        val game = game()
        assertEquals(2.0, game.start(10.0))
        val step = game.tick(10.0, tick)
        assertFalse(step.jumped)
        assertTrue(step.cloud > 2.0 && step.cloud < 10.0)
        assertEquals(CloudGame.Side.BEHIND, game.side)
    }

    @Test
    fun `it never stands further back than the nearest it may`() {
        assertEquals(-8.0, game().start(0.0))
    }

    @Test
    fun `after a leg forward it jumps in front, after a leg back behind again`() {
        val game = game(random = 0.0)
        game.start(10.0)
        var player = 10.0
        var ticks = 0
        var jump: CloudGame.Step
        do { player += 0.2; ticks++; jump = game.tick(player, tick) } while (!jump.jumped)
        val leg = config.gameForwardMinSeconds * (1 - config.gameHurry * 10.0 / 90.0)
        assertEquals(Math.ceil(leg / tick).toInt(), ticks)
        assertEquals(player + config.gameAheadBlocks, jump.cloud)
        assertEquals(CloudGame.Side.AHEAD, game.side)
        var back: CloudGame.Step
        do { player -= 0.2; back = game.tick(player, tick) } while (!back.jumped)
        assertEquals(CloudGame.Side.BEHIND, game.side)
        assertEquals(player - config.gameBehindBlocks, back.cloud)
        assertEquals(2, game.jumps)
    }

    @Test
    fun `standing still, it reaches the player`() {
        val game = game()
        game.start(20.0)
        var step = game.tick(20.0, tick)
        repeat(60) { step = game.tick(20.0, tick) }
        assertEquals(20.0, step.cloud)
    }

    @Test
    fun `near the end it no longer jumps in front`() {
        val game = game(random = 0.0)
        game.start(85.0)
        repeat(400) { assertFalse(game.tick(85.0 + it * 0.01, tick).jumped) }
    }

    @Test
    fun `everything quickens towards the end`() {
        val early = game()
        early.start(0.0)
        val late = game()
        late.start(80.0)
        val a = early.tick(0.0, tick).cloud - (-8.0)
        val b = late.tick(80.0, tick).cloud - 72.0
        assertTrue(b > a, "creeping faster at the end")
        assertTrue(late.tick(80.0, tick).progress > 0.8)
    }
}
