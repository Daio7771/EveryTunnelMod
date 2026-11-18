package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.Kind
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.Nav
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.NearbyPlayer
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.Senses
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.Source
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.State
import daio7771.everytunnel.chapter2.level2.core.VibrationCreatureBrain.Vibration
import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VibrationCreatureBrainTest {

    private var config = Ch2L2Creature.DEFAULT
    private val pump = PumpCycle(config.pump, config.graceAfterStopSeconds)
    private val brain = VibrationCreatureBrain(Random(7))
    private val home = Position(0.0, 0.0, 0.0)
    private val player = UUID.randomUUID()
    private var now = 1000L
    private var position = home

    private fun at(x: Double) = Position(x, 0.0, 0.0)

    /** A vibration made at tick [tick] (the previous tick by default), masked as the pump says when [pumpStart] is given. */
    private fun step(
        x: Double, kind: Kind = Kind.NORMAL, tick: Long = now - 1, pumpStart: Long? = null, source: Source = Source.PLAYER,
    ) = Vibration(
        at(x), tick, source, kind, masked = pumpStart != null && pump.masking(tick - pumpStart),
        playerId = if (source == Source.PLAYER) player else null,
    )

    private fun tick(
        vibrations: List<Vibration> = emptyList(), nearest: NearbyPlayer? = null, nav: Nav = Nav.MOVING, intro: Boolean = false,
    ): VibrationCreatureBrain.Decision {
        val decision = brain.tick(Senses(now, position, home, vibrations, nearest, nav, intro), config)
        now++
        return decision
    }

    private fun ticksFor(seconds: Double) = VibrationCreatureBrain.ticks(seconds)
    private fun wait(ticks: Int, nearest: NearbyPlayer? = null) = repeat(ticks) { tick(nearest = nearest) }

    /** Feels a step of [kind] at [x] and lets the orientation pass: the creature is now charging. */
    private fun charging(x: Double = 10.0, kind: Kind = Kind.NORMAL) {
        tick(listOf(step(x, kind)))
        wait(ticksFor(config.orientSeconds))
        assertEquals(State.CHARGE, brain.state)
    }

    /** Charges to [x] and arrives there: the windup has begun. */
    private fun windingUp(x: Double = 10.0) {
        charging(x)
        position = at(x)
        val d = tick()
        assertTrue(d.windup)
        assertEquals(State.WINDUP, brain.state)
    }

    @Test
    fun `it never reacts with nothing felt`() {
        repeat(400) { assertEquals(State.REST, tick().state) }
        assertNull(brain.target)
    }

    @Test
    fun `a soft step makes it stalk slowly, and a new one moves it on`() {
        tick(listOf(step(10.0, Kind.SOFT)))
        val d = tick()
        assertEquals(State.STALK, d.state)
        assertEquals(at(10.0), d.target)
        assertEquals(config.stalkSpeed, d.speed)
        tick(listOf(step(14.0, Kind.SOFT)))
        assertEquals(at(14.0), brain.target)
        assertEquals(State.STALK, brain.state)
    }

    @Test
    fun `at the end of a stalk it listens, then searches`() {
        tick(listOf(step(10.0, Kind.SOFT)))
        position = at(10.0)
        val listening = tick()
        assertEquals(State.STALK, listening.state)
        assertEquals(0.0, listening.speed)
        wait(ticksFor(config.stalkListenSeconds) - 1)
        assertEquals(State.STALK, brain.state)
        tick()
        assertEquals(State.SEARCH, brain.state)
    }

    @Test
    fun `a harder step while stalking makes it orient`() {
        tick(listOf(step(10.0, Kind.SOFT)))
        tick(listOf(step(12.0)))
        assertEquals(State.ORIENT, brain.state)
    }

    @Test
    fun `a step makes it orient for one second, the point moving, then charge to the locked point`() {
        val first = tick(listOf(step(10.0)))
        assertEquals(State.ORIENT, first.state)
        assertEquals(0.0, first.speed)
        tick(listOf(step(12.0)))
        assertEquals(at(12.0), brain.target)
        var oriented = false
        repeat(ticksFor(config.orientSeconds)) { if (tick().oriented) oriented = true }
        assertTrue(oriented)
        assertEquals(State.CHARGE, brain.state)
        val d = tick(listOf(step(20.0)))
        assertEquals(at(12.0), d.target, "locked once it charges")
        assertEquals(config.chargeSpeed, d.speed)
    }

    @Test
    fun `a loud step makes the charge faster`() {
        charging(kind = Kind.LOUD)
        assertEquals(config.chargeSpeedLoud, tick().speed)
    }

    @Test
    fun `touching a player starts the windup, it does not hit at once`() {
        charging()
        val near = NearbyPlayer(player, 1.0)
        val d = tick(nearest = near)
        assertTrue(d.windup)
        assertFalse(d.struck)
        assertNull(d.hit)
        repeat(ticksFor(config.windupSeconds) - 1) { assertFalse(tick(nearest = near).struck) }
        val blow = tick(nearest = near)
        assertTrue(blow.struck)
        assertEquals(player, blow.hit)
    }

    @Test
    fun `a player who got beyond contact by the impact is missed`() {
        windingUp()
        wait(ticksFor(config.windupSeconds) - 1, NearbyPlayer(player, 1.0))
        val blow = tick(nearest = NearbyPlayer(player, config.contactRange + 0.5))
        assertTrue(blow.struck)
        assertNull(blow.hit)
        assertEquals(State.RECOVER, brain.state)
    }

    @Test
    fun `blocked on the way it strikes where it stands, but not before the new path is trusted`() {
        charging()
        tick(nav = Nav.BLOCKED)
        assertEquals(State.CHARGE, brain.state, "a blocked path right after the change is the old one")
        wait(VibrationCreatureBrain.NAV_TRUST_TICKS)
        assertTrue(tick(nav = Nav.BLOCKED).windup)
    }

    @Test
    fun `an idle path is never taken for arrived`() {
        charging()
        repeat(100) { tick() }
        assertEquals(State.CHARGE, brain.state)
    }

    @Test
    fun `steps the pump masks are not felt, and the grace after it stops still masks`() {
        val start = now - 10
        repeat(40) { tick(listOf(step(10.0, pumpStart = start))) }
        assertEquals(State.REST, brain.state)
        val stop = start + ticksFor(config.pump.runSeconds)
        now = stop + 1
        tick(listOf(step(10.0, tick = stop, pumpStart = start)))
        assertEquals(State.REST, brain.state, "within the grace")
        now = stop + ticksFor(config.graceAfterStopSeconds) + 2
        tick(listOf(step(10.0, tick = now - 1, pumpStart = start)))
        assertEquals(State.ORIENT, brain.state)
    }

    @Test
    fun `after a blow it recovers, searches round the point, goes home and rests`() {
        windingUp()
        wait(ticksFor(config.windupSeconds))
        assertEquals(State.RECOVER, brain.state)
        wait(ticksFor(config.recoverSeconds))
        assertEquals(State.SEARCH, brain.state)
        val visited = mutableListOf<Position>()
        repeat(config.searchPoints) {
            val point = assertNotNull(brain.target)
            assertEquals(config.searchRadius, VibrationCreatureBrain.horizontalDistance(point, at(10.0)), 1e-6)
            visited += point
            position = point
            assertEquals(0.0, tick().speed, "listening at the point")
            wait(ticksFor(config.searchListenSeconds))
        }
        assertEquals(config.searchPoints, visited.toSet().size)
        assertEquals(State.RETURN, brain.state)
        assertEquals(home, tick().target)
        position = home
        tick()
        assertEquals(State.REST, brain.state)
    }

    @Test
    fun `blocked on the way home it rests where it is`() {
        windingUp()
        wait(ticksFor(config.windupSeconds) + ticksFor(config.recoverSeconds))
        repeat(config.searchPoints) {
            position = assertNotNull(brain.target)
            wait(1 + ticksFor(config.searchListenSeconds))
        }
        assertEquals(State.RETURN, brain.state)
        wait(VibrationCreatureBrain.NAV_TRUST_TICKS + 1)
        tick(nav = Nav.BLOCKED)
        assertEquals(State.REST, brain.state)
    }

    @Test
    fun `a step felt while recovering is followed when it ends`() {
        windingUp()
        wait(ticksFor(config.windupSeconds))
        tick(listOf(step(3.0)))
        wait(ticksFor(config.recoverSeconds))
        assertEquals(State.ORIENT, brain.state)
        assertEquals(at(3.0), brain.target)
    }

    @Test
    fun `during the intro it feels only the pipe, and reports the blow at it`() {
        tick(listOf(step(10.0)), intro = true)
        assertEquals(State.REST, brain.state)
        tick(listOf(step(6.0, Kind.LOUD, source = Source.PIPE)), intro = true)
        assertEquals(State.ORIENT, brain.state)
        wait(ticksFor(config.orientSeconds))
        position = at(6.0)
        tick()
        wait(ticksFor(config.windupSeconds) - 1)
        assertTrue(tick().struckPipe)
    }

    @Test
    fun `never two hits within the interval`() {
        config = config.copy(hitIntervalSeconds = 30.0)
        val near = NearbyPlayer(player, 1.0)
        windingUp()
        wait(ticksFor(config.windupSeconds) - 1, near)
        assertEquals(player, tick(nearest = near).hit)
        tick(listOf(step(3.0)))
        wait(ticksFor(config.recoverSeconds) + ticksFor(config.orientSeconds))
        assertEquals(State.CHARGE, brain.state)
        tick(nearest = near)
        wait(ticksFor(config.windupSeconds) - 1, near)
        val second = tick(nearest = near)
        assertTrue(second.struck)
        assertNull(second.hit)
    }

    @Test
    fun `reset puts it at rest and forgets`() {
        charging()
        brain.reset()
        assertEquals(State.REST, brain.state)
        assertNull(brain.target)
        assertEquals(State.REST, tick().state)
    }
}
