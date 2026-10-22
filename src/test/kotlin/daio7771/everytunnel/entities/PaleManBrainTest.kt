package daio7771.everytunnel.entities

import daio7771.everytunnel.config.PaleManConfig
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.PaleManBrain.Heard
import daio7771.everytunnel.entities.PaleManBrain.NearbyPlayer
import daio7771.everytunnel.entities.PaleManBrain.Senses
import daio7771.everytunnel.entities.PaleManBrain.State
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaleManBrainTest {

    private val config = PaleManConfig(listenSeconds = 1.0, searchSeconds = 1.0, investigateSeconds = 5.0, chaseLoudness = 0.5)
    private val brain = PaleManBrain()
    private val home = Position(0.0, 0.0, 0.0)
    private val player = UUID.randomUUID()
    private var now = 100L

    private fun at(x: Double) = Position(x, 0.0, 0.0)
    private fun noise(x: Double, loudness: Double) = Heard(at(x), loudness, now - 1, player)

    private fun tick(
        heard: List<Heard> = emptyList(), position: Position = home,
        nearest: NearbyPlayer? = null, idle: Boolean = false,
    ): PaleManBrain.Decision {
        val decision = brain.tick(Senses(now, position, heard, nearest, idle), config)
        now++
        return decision
    }

    private fun idleFor(ticks: Int, position: Position = home) = repeat(ticks) { tick(position = position) }

    @Test
    fun `a quiet noise makes him stop and listen, then walk there`() {
        val listen = tick(listOf(noise(10.0, 0.2)))
        assertEquals(State.LISTEN, listen.state)
        assertEquals(0.0, listen.speed)
        assertEquals(at(10.0), listen.target)

        idleFor(19)
        val walk = tick()
        assertEquals(State.INVESTIGATE, walk.state)
        assertEquals(config.investigateSpeed, walk.speed)
        assertEquals(at(10.0), walk.target)
    }

    @Test
    fun `a loud noise or a second one while listening starts a chase, with the stinger`() {
        val loud = tick(listOf(noise(3.0, 0.8)))
        assertEquals(State.CHASE, loud.state)
        assertEquals(player, loud.alerted)
        assertEquals(config.chaseSpeed, loud.speed)

        val other = PaleManBrain()
        other.tick(Senses(now, home, listOf(noise(10.0, 0.2))), config)
        now++
        val second = other.tick(Senses(now, home, listOf(noise(9.0, 0.2))), config)
        assertEquals(State.CHASE, second.state)
        assertEquals(at(9.0), second.target)
    }

    @Test
    fun `each noise is used once, and only the tick after it was made`() {
        val sameTick = Heard(at(10.0), 0.2, now, player)
        assertEquals(State.ROAM, tick(listOf(sameTick)).state)
        assertEquals(State.LISTEN, tick(listOf(sameTick)).state)
        // The same noise still in memory does not count as a second one.
        repeat(5) { assertEquals(State.LISTEN, tick(listOf(sameTick)).state) }
    }

    @Test
    fun `losing the trail he searches, then roams again`() {
        tick(listOf(noise(5.0, 0.9)))
        val there = at(5.0)
        // On his target he keeps chasing for a moment, in case another step follows.
        repeat(10) { assertEquals(State.CHASE, tick(position = there).state) }
        assertEquals(State.SEARCH, tick(position = there).state)
        idleFor(19, there)
        val roam = tick(position = there)
        assertEquals(State.ROAM, roam.state)
        assertNull(roam.target)
    }

    @Test
    fun `any noise while searching starts the chase again`() {
        tick(listOf(noise(5.0, 0.9)))
        repeat(11) { tick(position = at(5.0)) }
        assertEquals(State.SEARCH, brain.state)
        assertEquals(State.CHASE, tick(listOf(noise(8.0, 0.1)), position = at(5.0)).state)
    }

    @Test
    fun `an idle pathfinder counts as arrived only after a moment`() {
        tick(listOf(noise(30.0, 0.9)))
        assertEquals(State.CHASE, tick(idle = true).state)
        repeat(9) { tick() }
        assertEquals(State.SEARCH, tick(idle = true).state)
    }

    @Test
    fun `he hits players in reach while hunting, and anyone who bumps into him`() {
        assertNull(tick(nearest = NearbyPlayer(player, 1.5)).attack)
        assertEquals(player, tick(nearest = NearbyPlayer(player, 0.8)).attack)
        assertEquals(State.CHASE, brain.state)

        // Cooldown, then the next hit at chase reach.
        repeat(19) { assertNull(tick(nearest = NearbyPlayer(player, 1.5)).attack) }
        assertEquals(player, tick(nearest = NearbyPlayer(player, 1.5)).attack)
    }

    @Test
    fun `loudness falls with distance, walls and hearing`() {
        assertEquals(1.0, PaleManBrain.loudness(0.0, 10.0, 1.0, 0, 0.5))
        assertEquals(0.5, PaleManBrain.loudness(5.0, 10.0, 1.0, 0, 0.5))
        assertEquals(0.0, PaleManBrain.loudness(5.0, 10.0, 1.0, 1, 0.5))
        assertEquals(0.5, PaleManBrain.loudness(10.0, 10.0, 2.0, 0, 0.5))
        assertEquals(0.0, PaleManBrain.loudness(1.0, 10.0, 1.0, 3, 0.0))
    }

    @Test
    fun `reset sends him back to wandering`() {
        assertEquals(State.CHASE, tick(listOf(noise(5.0, 0.9))).state)
        brain.reset()
        assertEquals(State.ROAM, brain.state)
        assertNull(brain.target)
        assertEquals(State.ROAM, tick().state)
    }
}
