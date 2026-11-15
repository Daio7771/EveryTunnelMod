package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonParser
import daio7771.everytunnel.core.level.Position
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Ch2L1NpcLifeTest {

    private val cruise = 0.065

    // --- the file -------------------------------------------------------------------------------------

    @Test
    fun `life and a worker's own pace are read from the file`() {
        val result = Ch2L1NpcsParser.parse(
            JsonParser.parseString(
                """{ "life": { "pace_spread": 0.2, "accel_seconds": 1, "turn_degrees": 6, "linger_chance": 0.5, "linger_seconds": [1, 2],
                               "chat_range": 3, "chat_seconds": [2, 3] },
                     "workers": [ { "id": "a", "spawn": "s", "pace": 1.2 }, { "id": "b", "spawn": "s" } ] }""",
            ).asJsonObject,
        )
        assertEquals(emptyList(), result.problems)
        val life = result.data.life
        assertEquals(0.2, life.paceSpread)
        assertEquals(20, life.accelTicks)
        assertEquals(6f, life.turnDegrees)
        assertEquals(Ch2L1Span(20, 40), life.lingerTicks)
        assertEquals(Ch2L1Span(40, 60), life.chatTicks)
        assertEquals(Ch2L1Life.DEFAULT.glanceTicks, life.glanceTicks, "what the file leaves out keeps its default")
        assertEquals(listOf(1.2, null), result.data.workers.map { it.pace })
    }

    @Test
    fun `bad life values are reported and fall back to the defaults`() {
        val result = Ch2L1NpcsParser.parse(
            JsonParser.parseString("""{ "life": { "chat_seconds": [5, 1], "chat_chance": 3, "wobble": 1 } }""").asJsonObject,
        )
        assertEquals(Ch2L1Life.DEFAULT, result.data.life)
        assertTrue(result.problems.any { "chat_seconds" in it })
        assertTrue(result.problems.any { "chat_chance" in it })
        assertTrue(result.problems.any { "wobble" in it })
    }

    @Test
    fun `each worker's drawn pace is their own, within the spread, and the same every time`() {
        val life = Ch2L1Life(paceSpread = 0.15)
        val paces = (1..40).map { life.pace(null, it.toLong()) }
        assertTrue(paces.all { it in 0.85..1.15 })
        assertTrue(paces.toSet().size > 30, "they differ")
        assertEquals(life.pace(null, 7), life.pace(null, 7))
        assertEquals(1.3, life.pace(1.3, 7), "the file's own pace wins")
    }

    // --- walking ----------------------------------------------------------------------------------------

    @Test
    fun `they ease into their pace instead of starting at full speed`() {
        val gait = Ch2L1Gait(cruise, 12, 9f)
        val speeds = (1..20).map { gait.walk(0f, 0.0, 20.0, null); gait.speed }
        assertTrue(speeds.first() < cruise / 5)
        assertTrue(speeds.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(cruise, speeds.last(), 1e-9)
    }

    @Test
    fun `they brake before they stop`() {
        val gait = Ch2L1Gait(cruise, 1, 9f)
        repeat(5) { gait.walk(0f, 0.0, 20.0, 20.0) }
        assertEquals(cruise, gait.speed, 1e-9)
        repeat(5) { gait.walk(0f, 0.0, 0.4, 0.4) }
        assertTrue(gait.speed < cruise * 0.5, "slow near the stop: ${gait.speed}")
    }

    @Test
    fun `a sharp turn slows them down and the body turns a little each tick`() {
        val gait = Ch2L1Gait(cruise, 1, 9f)
        repeat(5) { gait.walk(0f, 0.0, 20.0, null) }
        // The next point is to their side (-x is a yaw of 90).
        val step = gait.walk(0f, -20.0, 0.0, null)
        assertTrue(gait.speed < cruise * 0.5)
        assertTrue(step.yaw > 0f && step.yaw <= 9f, "turned ${step.yaw}")
        var yaw = step.yaw
        repeat(60) { yaw = gait.walk(yaw, -20.0, 0.0, null).yaw }
        assertEquals(90f, yaw, 0.6f)
    }

    @Test
    fun `they always move straight at the point, whatever way they face`() {
        val gait = Ch2L1Gait(cruise, 1, 9f)
        repeat(3) { gait.walk(0f, 3.0, 4.0, null) }
        val step = gait.walk(0f, 3.0, 4.0, null)
        assertEquals(step.vx / step.vz, 3.0 / 4.0, 1e-9)
        assertTrue(sqrt(step.vx * step.vx + step.vz * step.vz) <= cruise + 1e-9)
    }

    @Test
    fun `standing they turn to face the way given, a little each tick`() {
        val gait = Ch2L1Gait(cruise, 1, 9f)
        val yaw = gait.stand(0f, 180f)
        assertEquals(0.0, gait.speed)
        assertTrue(abs(yaw) in 1f..9f)
        assertEquals(30f, gait.stand(30f, null))
    }

    // --- the routine's lingering ---------------------------------------------------------------------------

    @Test
    fun `on a path they may stop a moment at a point, but never at the last`() {
        val path = Ch2L1Step.Walk(listOf(Position(0.0, 0.0, 0.0), Position(0.0, 0.0, 2.0), Position(0.0, 0.0, 4.0)))
        val runner = Ch2L1RoutineRunner(listOf(path), Random(1), linger = 1.0, lingerTicks = Ch2L1Span(10, 10))
        var z = 0.0
        val commands = mutableListOf<Ch2L1RoutineRunner.Command>()
        repeat(200) {
            val c = runner.tick(0.0, 0.0, z)
            commands += c
            if (c is Ch2L1RoutineRunner.Command.MoveTo) z += (c.z - z).coerceIn(-0.1, 0.1)
        }
        val firstStop = commands.indexOfFirst { it is Ch2L1RoutineRunner.Command.Stand }
        assertTrue(firstStop > 0)
        assertEquals(10, commands.drop(firstStop).takeWhile { it is Ch2L1RoutineRunner.Command.Stand }.size, "a pause at the middle point")
        val moves = commands.filterIsInstance<Ch2L1RoutineRunner.Command.MoveTo>()
        assertTrue(moves.any { it.last && it.z == 4.0 } && moves.none { it.last && it.z == 2.0 }, "only the end is where they stop")
    }

    @Test
    fun `without lingering they walk a path in one go`() {
        val path = Ch2L1Step.Walk(listOf(Position(0.0, 0.0, 0.0), Position(0.0, 0.0, 2.0), Position(0.0, 0.0, 4.0)))
        val runner = Ch2L1RoutineRunner(listOf(path), Random(1))
        var z = 0.0
        val commands = (1..50).map {
            runner.tick(0.0, 0.0, z).also { c -> if (c is Ch2L1RoutineRunner.Command.MoveTo) z += (c.z - z).coerceIn(-0.1, 0.1) }
        }
        val walk = commands.drop(1).takeWhile { it is Ch2L1RoutineRunner.Command.MoveTo }
        assertTrue(walk.size >= 35, "no stop on the way: ${walk.size}")
    }

    // --- looking ----------------------------------------------------------------------------------------------

    private val eyes = Ch2L1Point(0.0, 1.6, 0.0)

    private fun gaze(life: Ch2L1Life = Ch2L1Life.DEFAULT) = Ch2L1Gaze(life, Random(5))

    @Test
    fun `a player close in front is followed with the eyes for a moment, then not again until the cooldown is over`() {
        val life = Ch2L1Life(playerLookTicks = Ch2L1Span(20, 20), playerCooldownTicks = Ch2L1Span(100, 100))
        val gaze = gaze(life)
        // Body faces +z (yaw 0); the player is ahead and to the left, at x 2 (a yaw of about -34).
        val player = Ch2L1Point(2.0, 1.6, 3.0)
        repeat(19) { gaze.tick(0f, eyes, Ch2L1Gaze.Mode.STANDING, player, null) { emptyList() } }
        assertEquals(Ch2L1Angles.yawOf(2.0, 3.0), gaze.headYaw, 1.5f)
        var away = 0
        repeat(60) {
            gaze.tick(0f, eyes, Ch2L1Gaze.Mode.WALKING, player, null) { emptyList() }
            if (abs(Ch2L1Angles.wrap(gaze.headYaw - Ch2L1Angles.yawOf(2.0, 3.0))) > 10f) away++
        }
        assertTrue(away > 30, "looked away, the player still there: $away of 60")
    }

    @Test
    fun `a player behind them or far away is not seen`() {
        val gaze = gaze()
        repeat(40) { gaze.tick(0f, eyes, Ch2L1Gaze.Mode.WALKING, Ch2L1Point(0.0, 1.6, -3.0), null) { emptyList() } }
        assertTrue(abs(gaze.headYaw) <= 35f, "${gaze.headYaw}")
        val far = gaze()
        repeat(40) { far.tick(0f, eyes, Ch2L1Gaze.Mode.WALKING, Ch2L1Point(6.0, 1.6, 6.0), null) { emptyList() } }
        assertTrue(abs(far.headYaw) <= 35f, "${far.headYaw}")
    }

    @Test
    fun `the head never turns more than its most away from the body`() {
        val gaze = gaze()
        // The partner is right behind them.
        repeat(100) { gaze.tick(0f, eyes, Ch2L1Gaze.Mode.TALKING, null, Ch2L1Point(0.0, 1.6, -2.0)) { emptyList() } }
        assertEquals(Ch2L1Gaze.MOST, abs(gaze.headYaw), 0.6f)
    }

    @Test
    fun `talking they look at whoever they talk to, eyes to eyes`() {
        val gaze = gaze()
        repeat(100) { gaze.tick(0f, eyes, Ch2L1Gaze.Mode.TALKING, null, Ch2L1Point(-1.0, 1.6, 2.0)) { emptyList() } }
        assertEquals(Ch2L1Angles.yawOf(-1.0, 2.0), gaze.headYaw, 0.6f)
        assertEquals(0f, gaze.pitch, 0.5f)
    }

    @Test
    fun `typing they look down at the keyboard most of the time`() {
        val gaze = gaze()
        var down = 0
        repeat(2000) {
            gaze.tick(0f, eyes, Ch2L1Gaze.Mode.TYPING, null, null) { emptyList() }
            if (gaze.pitch > 15f) down++
        }
        assertTrue(down > 1000, "down $down of 2000")
    }

    @Test
    fun `standing they look about, sometimes at a colleague, never stock still`() {
        val gaze = gaze(Ch2L1Life(glanceTicks = Ch2L1Span(20, 20)))
        val colleague = Ch2L1Point(1.0, 1.6, 3.0)
        val yaws = mutableSetOf<Int>()
        var atColleague = 0
        repeat(2000) {
            gaze.tick(0f, eyes, Ch2L1Gaze.Mode.STANDING, null, null) { listOf(colleague) }
            yaws += (gaze.headYaw / 5).toInt()
            if (abs(gaze.headYaw - Ch2L1Angles.yawOf(1.0, 3.0)) < 1f) atColleague++
        }
        assertTrue(yaws.size > 10, "looked many ways: $yaws")
        assertTrue(atColleague > 0, "and at the colleague")
    }

    // --- talking ---------------------------------------------------------------------------------------------

    @Test
    fun `people close to each other are paired, the nearest first, no one twice`() {
        val people = listOf(
            Ch2L1Chat.Person(1, 0.0, 0.0), Ch2L1Chat.Person(2, 1.0, 0.0), Ch2L1Chat.Person(3, 2.5, 0.0),
            Ch2L1Chat.Person(4, 10.0, 0.0), Ch2L1Chat.Person(5, 11.5, 0.0), Ch2L1Chat.Person(6, 30.0, 0.0),
        )
        assertEquals(listOf(1 to 2, 4 to 5), Ch2L1Chat.pairs(people, 2.4))
        assertEquals(emptyList(), Ch2L1Chat.pairs(people.take(1), 2.4))
    }

    @Test
    fun `a talk lasts its ticks and who talks swaps now and then`() {
        val chat = Ch2L1Chat(200, Random(2))
        val talkers = mutableListOf<Boolean>()
        var ticks = 0
        while (chat.tick()) {
            talkers += chat.firstTalks
            ticks++
        }
        assertEquals(199, ticks)
        assertTrue(talkers.zipWithNext().count { (a, b) -> a != b } >= 2, "they took turns")
    }

    @Test
    fun `angles wrap and ease`() {
        assertEquals(-170f, Ch2L1Angles.wrap(190f))
        assertEquals(0f, Ch2L1Angles.yawOf(0.0, 1.0))
        assertEquals(90f, Ch2L1Angles.yawOf(-1.0, 0.0))
        assertEquals(10f, Ch2L1Angles.ease(0f, 100f, 0.5f, 10f))
        assertEquals(-180f, Ch2L1Angles.ease(-175f, 175f, 0.5f, 10f), "the short way round")
    }
}
