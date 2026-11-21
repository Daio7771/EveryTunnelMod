package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import daio7771.everytunnel.common.core.level.Position
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1NpcsTest {

    private fun parse(json: String) = Ch2L1NpcsParser.parse(JsonParser.parseString(json).asJsonObject)

    private val bundled = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data

    @Test
    fun `an empty file has no people`() {
        val result = Ch2L1NpcsParser.parse(JsonObject())
        assertEquals(emptyList(), result.problems)
        assertEquals(Ch2L1NpcData.EMPTY, result.data)
    }

    @Test
    fun `workers, routines and groups are read`() {
        val result = parse(
            """{ "walk_speed": 0.8,
                 "workers": [ { "id": "a", "skin": "worker_b", "group": "lab", "spawn": "s",
                                "routine": [ { "stand": [1, 2] }, { "walk": "r", "reverse": true }, { "type": "t", "seconds": 5 } ] } ],
                 "groups": { "g": [ { "skin": "generic_2", "at": "p" } ] } }""",
        )
        assertEquals(emptyList(), result.problems)
        assertEquals(0.8, result.data.walkSpeed)
        val worker = result.data.workers.single()
        assertEquals(Ch2L1WorkerSpec("a", "worker_b", "lab", "s", listOf(
            Ch2L1StepSpec.Stand(1.0, 2.0), Ch2L1StepSpec.Walk("r", true), Ch2L1StepSpec.Type("t", 5.0, 5.0),
        )), worker)
        assertEquals(mapOf("g" to listOf(Ch2L1GroupMember("generic_2", "p"))), result.data.groups)
    }

    @Test
    fun `bad entries are reported and skipped`() {
        val result = parse(
            """{ "workers": [ { "id": "ok", "spawn": "s", "skin": "nobody" }, { "id": "ok", "spawn": "s" }, { "spawn": "s" }, 7,
                              { "id": "x", "spawn": "s", "routine": [ { "stand": 1, "walk": "r" }, { "stand": "long" }, 3 ] } ] }""",
        )
        assertEquals(listOf("ok", "x"), result.data.workers.map { it.id })
        assertEquals(Ch2L1Skins.DEFAULT, result.data.workers[0].skin)
        assertTrue(result.data.workers[1].steps.isEmpty())
        assertTrue(result.problems.any { "nobody" in it })
        assertTrue(result.problems.any { "repeated" in it })
        assertTrue(result.problems.any { "needs an id" in it })
        assertTrue(result.problems.any { "exactly one" in it })
        assertTrue(result.problems.size >= 6, result.problems.toString())
    }

    @Test
    fun `the jar's people parse without a problem, twenty-two of them, with the lab's thirteen`() {
        val result = Ch2L1NpcsParser.parse(bundled.document("npcs"))
        assertEquals(emptyList(), result.problems)
        val workers = result.data.workers
        assertEquals(22, workers.size)
        assertEquals(13, workers.count { it.group == "lab" })
        assertEquals(3, workers.count { it.group == "main" })
        assertEquals(5, workers.count { it.group == "dining" })
        assertEquals(1, workers.count { it.group == "entrance" })            // the receptionist (phase 10b)
        assertTrue(workers.none { it.skin == Ch2L1Skins.ROWAN || it.skin == Ch2L1Skins.COWORKER }, "the ambient people are not Rowan or the coworker")
        assertEquals(6, result.data.groups.getValue("taquillas").size)
    }

    @Test
    fun `every anchor and path the jar's people name exists, and each resolves`() {
        val data = Ch2L1NpcsParser.parse(bundled.document("npcs")).data
        val problems = mutableListOf<String>()
        for (worker in data.workers) assertNotNull(Ch2L1Routines.resolve(worker, bundled.anchors, problems), "$worker $problems")
        assertEquals(emptyList(), problems)
        for ((name, members) in data.groups) for (m in members) assertNotNull(bundled.anchors.point(m.at), "$name: ${m.at}")
    }

    private fun anchors() = Ch2L1Anchors(
        "minecraft:overworld",
        listOf(Anchor("spot", Position(5.5, 64.0, 5.5), 180f, 0f, false), Anchor("start", Position(0.5, 64.0, 0.5), 0f, 0f, false)),
        emptyList(),
        listOf(
            AnchorPath("line", listOf(Position(0.5, 64.0, 0.5), Position(4.5, 64.0, 0.5)), closed = false, provisional = false),
            AnchorPath("loop", listOf(Position(0.5, 64.0, 0.5), Position(2.5, 64.0, 0.5), Position(2.5, 64.0, 2.5)), closed = true, provisional = false),
        ),
    )

    private fun resolve(vararg steps: Ch2L1StepSpec) =
        Ch2L1Routines.resolve(Ch2L1WorkerSpec("w", "generic_1", "g", "start", steps.toList()), anchors())!!

    @Test
    fun `a closed path comes back to its first point, and reverse goes the other way`() {
        val loop = resolve(Ch2L1StepSpec.Walk("loop", false)).steps.single() as Ch2L1Step.Walk
        assertEquals(4, loop.points.size)
        assertEquals(loop.points.first(), loop.points.last())
        val back = resolve(Ch2L1StepSpec.Walk("line", true)).steps.single() as Ch2L1Step.Walk
        assertEquals(listOf(4.5, 0.5), back.points.map { it.x })
    }

    @Test
    fun `a missing anchor or path is a problem and no routine`() {
        val problems = mutableListOf<String>()
        assertNull(Ch2L1Routines.resolve(Ch2L1WorkerSpec("w", "generic_1", "g", "nowhere", emptyList()), anchors(), problems))
        assertNull(Ch2L1Routines.resolve(Ch2L1WorkerSpec("w", "generic_1", "g", "start", listOf(Ch2L1StepSpec.Walk("nope", false))), anchors(), problems))
        assertNull(Ch2L1Routines.resolve(Ch2L1WorkerSpec("w", "generic_1", "g", "start", listOf(Ch2L1StepSpec.Type("nope", 1.0, 1.0))), anchors(), problems))
        assertEquals(3, problems.size)
    }

    /** Moves a person 0.25 a tick toward whatever the runner says, as the entity would, and records what they did. */
    private fun simulate(steps: List<Ch2L1Step>, ticks: Int, start: Position): List<Ch2L1RoutineRunner.Command> {
        val runner = Ch2L1RoutineRunner(steps, Random(3))
        var x = start.x
        var z = start.z
        val out = mutableListOf<Ch2L1RoutineRunner.Command>()
        repeat(ticks) {
            val command = runner.tick(x, start.y, z)
            out += command
            if (command is Ch2L1RoutineRunner.Command.MoveTo) {
                val dx = command.x - x
                val dz = command.z - z
                val d = Math.sqrt(dx * dx + dz * dz).coerceAtLeast(1e-9)
                val step = minOf(0.25, d)
                x += dx / d * step
                z += dz / d * step
            }
        }
        return out
    }

    @Test
    fun `a walker goes along its path, waits, and starts again`() {
        val steps = resolve(Ch2L1StepSpec.Walk("line", false), Ch2L1StepSpec.Stand(1.0, 1.0)).steps
        val commands = simulate(steps, 200, Position(0.5, 64.0, 0.5))
        val moves = commands.filterIsInstance<Ch2L1RoutineRunner.Command.MoveTo>()
        assertTrue(moves.isNotEmpty() && moves.all { !it.snap })
        assertTrue(moves.any { it.x == 4.5 }, "it went to the end of the line")
        assertTrue(commands.takeLast(60).any { it is Ch2L1RoutineRunner.Command.MoveTo }, "and walked it again")
    }

    @Test
    fun `a typist goes to the spot, faces the way it faces and types for the time asked`() {
        val steps = resolve(Ch2L1StepSpec.Type("spot", 2.0, 2.0), Ch2L1StepSpec.Stand(1.0, 1.0)).steps
        val commands = simulate(steps, 200, Position(0.5, 64.0, 0.5))
        val first = commands.indexOfFirst { it is Ch2L1RoutineRunner.Command.Stand && it.typing }
        assertTrue(first > 0, "it walked there first")
        val run = commands.drop(first).takeWhile { it is Ch2L1RoutineRunner.Command.Stand && it.typing }
        assertTrue(run.all { (it as Ch2L1RoutineRunner.Command.Stand).yaw == 180f })
        assertTrue(run.size in 38..41, "typed ${run.size} ticks for 2 s")
        assertTrue(commands.drop(first + run.size).first() == Ch2L1RoutineRunner.Command.Stand(null, false), "then it stands")
    }

    @Test
    fun `someone stuck is told to snap to the point`() {
        val runner = Ch2L1RoutineRunner(resolve(Ch2L1StepSpec.Walk("line", false)).steps, Random(1))
        var snapped = false
        repeat(Ch2L1RoutineRunner.STUCK_TICKS + 5) {
            val c = runner.tick(0.5, 64.0, 5.0)                // never gets anywhere near the line
            if (c is Ch2L1RoutineRunner.Command.MoveTo && c.snap) snapped = true
        }
        assertTrue(snapped)
    }

    @Test
    fun `a long walk is not being stuck, someone making headway is never snapped`() {
        val far = Ch2L1Step.Walk(listOf(Position(60.0, 64.0, 0.0)))
        val runner = Ch2L1RoutineRunner(listOf(far), Random(1))
        var x = 0.0
        var snapped = false
        repeat(1500) {                                    // 75 seconds at 0.04 a tick: far more than the stuck time
            val c = runner.tick(x, 64.0, 0.0)
            if (c is Ch2L1RoutineRunner.Command.MoveTo) {
                if (c.snap) snapped = true
                x += 0.04
            }
        }
        assertTrue(!snapped, "a person who is getting there is not put there")
        assertTrue(x > 55.0)
    }

    @Test
    fun `no steps is standing, and a single point or a single step does not loop for ever`() {
        assertEquals(Ch2L1RoutineRunner.Command.Stand(null, false), Ch2L1RoutineRunner(emptyList(), Random(1)).tick(0.0, 0.0, 0.0))
        val here = Ch2L1Step.Walk(listOf(Position(0.0, 0.0, 0.0)))
        val runner = Ch2L1RoutineRunner(listOf(here), Random(1))
        repeat(10) { assertEquals(Ch2L1RoutineRunner.Command.Stand(null, false), runner.tick(0.0, 0.0, 0.0)) }
    }
}
