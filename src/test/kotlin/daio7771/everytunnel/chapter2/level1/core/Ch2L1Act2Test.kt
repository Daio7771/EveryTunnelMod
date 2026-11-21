package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonParser
import daio7771.everytunnel.common.core.cutscene.SceneFrame
import daio7771.everytunnel.common.core.level.Position
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1Act2Test {

    private val data = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data
    private val act2Result = Ch2L1Act2Parser.parse(data.document("cutscenes/meeting"))
    private val act2 = act2Result.act2
    private val npcs = Ch2L1NpcsParser.parse(data.document("npcs")).data
    private val voices = Ch2L1Voices.load()

    // --- the file ----------------------------------------------------------------------------------------

    @Test
    fun `the jar's meeting file reads without a problem, with its fifteen lines and two doors`() {
        assertEquals(emptyList(), act2Result.problems)
        assertEquals(15, act2.meeting.lines.size)
        assertEquals(listOf("lockers_door_1", "lockers_door_2"), act2.meeting.doors)
        assertEquals(240, act2.follow.remindAfterTicks)
        assertEquals(70, act2.meeting.doorTicks)
        for (line in act2.meeting.lines + act2.follow.openingLine + act2.follow.reminders) assertNotNull(voices.line(line), "no voice $line")
    }

    @Test
    fun `an empty file is the defaults, and bad values are told`() {
        assertEquals(Ch2L1Act2.DEFAULT, Ch2L1Act2Parser.parse(JsonParser.parseString("{}").asJsonObject).act2)
        val bad = Ch2L1Act2Parser.parse(
            JsonParser.parseString("""{ "follow": { "lag_blocks": 0, "typo": 1 }, "meeting": { "lines": [], "glance_seconds": [3, 1] } }""").asJsonObject,
        )
        assertTrue(bad.problems.any { "lag_blocks" in it })
        assertTrue(bad.problems.any { "typo" in it })
        assertTrue(bad.problems.any { "no lines" in it })
        assertTrue(bad.problems.any { "glance_seconds" in it })
    }

    @Test
    fun `everything the meeting file names is in the anchors and the people's file`() {
        val tasks = Ch2L1TasksParser.parse(data.document("tasks")).tasks
        val speakers = Ch2L1SpeakersParser.parse(data.document("speakers")).speakers
        assertEquals(emptyList(), Ch2L1Wiring.problems(data.anchors, tasks, speakers, npcs, act2))
        val bare = Ch2L1Anchors("minecraft:overworld", emptyList(), emptyList())
        val problems = Ch2L1Wiring.problems(bare, tasks, speakers, Ch2L1NpcData.EMPTY, act2)
        assertTrue(problems.any { "coworker_route" in it })
        assertTrue(problems.any { "lockers_door_2_inside" in it })
        assertTrue(problems.any { "the group \"taquillas\"" in it })
    }

    @Test
    fun `worker_a's and worker_b's ambient doubles leave when Act 2 begins`() {
        val doubles = npcs.workers.filter { it.skin == Ch2L1Skins.WORKER_A || it.skin == Ch2L1Skins.WORKER_B }
        assertTrue(doubles.isNotEmpty())
        assertTrue(doubles.all { it.until == Ch2L1State.ACT2_FOLLOW })
        assertTrue(npcs.workers.filter { it !in doubles }.all { it.until == null })
        val bad = Ch2L1NpcsParser.parse(JsonParser.parseString("""{ "workers": [ { "id": "a", "spawn": "s", "until": "LUNCH" } ] }""").asJsonObject)
        assertNull(bad.data.workers.single().until)
        assertTrue(bad.problems.any { "LUNCH" in it })
    }

    // --- following him ---------------------------------------------------------------------------------------

    private val route = listOf(Position(0.0, 0.0, 0.0), Position(10.0, 0.0, 0.0), Position(10.0, 0.0, 10.0))

    /** Runs [ticks] of the walk: he moves 0.2 a tick toward where he is sent; the player is where [player] says at each tick. */
    private fun walk(follow: Ch2L1Follow, ticks: Int, start: Position, player: (Position) -> Position): Pair<Position, List<Ch2L1Follow.Step>> {
        var at = start
        val steps = mutableListOf<Ch2L1Follow.Step>()
        repeat(ticks) {
            val step = follow.tick(at, player(at))
            steps += step
            val command = step.command
            if (command is Ch2L1Follow.Command.Walk) {
                val dx = command.x - at.x
                val dz = command.z - at.z
                val d = kotlin.math.sqrt(dx * dx + dz * dz)
                val s = minOf(0.2, d)
                if (d > 0) at = Position(at.x + dx / d * s, 0.0, at.z + dz / d * s)
            }
        }
        return at to steps
    }

    @Test
    fun `he waits while he says his first line, then leads the way to the end with the player close behind`() {
        val follow = Ch2L1Follow(Ch2L1FollowSpec(), route, 30)
        val (end, steps) = walk(follow, 300, route.first()) { it }
        assertTrue(steps.take(30).all { it.command == Ch2L1Follow.Command.Wait })
        assertTrue(steps[30].command is Ch2L1Follow.Command.Walk)
        assertTrue(follow.arrived)
        assertTrue(Ch2L1Follow.flat(end, route.last()) < 0.5)
        assertEquals(Ch2L1Follow.Command.Arrived, steps.last().command)
        assertTrue(steps.none { it.say != null }, "no reminders for a player who keeps up")
    }

    @Test
    fun `a player who lags is waited for and reminded, in turns, not too often`() {
        val spec = Ch2L1FollowSpec(remindAfterTicks = 20, remindCooldownTicks = 50)
        val follow = Ch2L1Follow(spec, route, 0)
        val (_, steps) = walk(follow, 200, Position(5.0, 0.0, 0.0)) { Position(-20.0, 0.0, 0.0) }
        assertTrue(steps.all { it.command == Ch2L1Follow.Command.Wait }, "he does not walk off")
        val said = steps.mapIndexedNotNull { i, s -> s.say?.let { i to it } }
        assertEquals(listOf("follow_02", "follow_03", "follow_02"), said.map { it.second }.take(3))
        assertTrue(said.zipWithNext().all { (a, b) -> b.first - a.first >= 50 }, "the cooldown: $said")
        assertTrue(said.zipWithNext().all { (a, b) -> a.second != b.second }, "never the same twice running")
    }

    @Test
    fun `a player ahead of him is not lagging`() {
        val follow = Ch2L1Follow(Ch2L1FollowSpec(), route, 0)
        val step = follow.tick(Position(1.0, 0.0, 0.0), Position(10.0, 0.0, 9.0))
        assertTrue(step.command is Ch2L1Follow.Command.Walk)
    }

    // --- the meeting -----------------------------------------------------------------------------------------

    private val spot = data.anchors.point(act2.meeting.playerSpot)!!
    private val frame = SceneFrame(spot.position.x, spot.position.y, spot.position.z, spot.yaw.toDouble())
    private val members = npcs.groups.getValue(act2.meeting.group)
    private val built = Ch2L1MeetingScene.build(act2.meeting, data.anchors, voices, members, frame, 1).also { assertNull(it.problem, it.problem) }.built!!

    @Test
    fun `the meeting's lines come in order, each by its speaker, one after another`() {
        assertEquals(act2.meeting.lines, built.beats.map { it.line.id })
        assertEquals(act2.meeting.lines, built.timeline.lines.map { it.line.id })
        val speakers = built.beats.associate { it.line.id to it.speaker }
        assertEquals(Ch2L1Skins.COWORKER, speakers["meeting_01"])
        assertEquals(Ch2L1Skins.WORKER_A, speakers["meeting_03_a"])
        assertEquals(Ch2L1Skins.WORKER_B, speakers["meeting_04_b"])
        for ((a, b) in built.beats.zipWithNext()) assertTrue(b.start >= a.end + act2.meeting.gapTicks, "${a.line.id} -> ${b.line.id}")
        for (beat in built.beats) assertEquals(ceil(beat.line.ms / 50.0).toInt(), beat.end - beat.start)
        assertEquals(built.linesEnd + act2.meeting.flyOutTicks + 20, built.timeline.cutscene.totalTicks)
    }

    @Test
    fun `after the first line the eyes go to the door while it is shut, and the badge comes with Take this`() {
        val first = built.beats.first()
        assertTrue(built.doorStart > first.end)
        assertEquals(act2.meeting.doorTicks, built.doorEnd - built.doorStart)
        assertTrue(built.doorCloses in built.doorStart until built.doorEnd)
        assertTrue(built.beats[1].start >= built.doorEnd)
        val card = built.beats.single { it.line.id == act2.meeting.cardLine }
        val cue = card.line.cues.first { it.firstSentence == act2.meeting.cardSentence }
        assertEquals(card.start + cue.atMs / 50, built.cardTick)
        assertTrue(built.cardTick in card.start until card.end)
    }

    @Test
    fun `who speaks and what is on screen at a tick`() {
        val b = built.beats[4]
        assertEquals(b, built.beatAt(b.start + 1.0))
        assertNull(built.beatAt(b.end + 1.0))
        assertEquals(b, built.subtitleBeatAt(b.end + 1.0), "the subtitle stays a moment after the voice")
    }

    @Test
    fun `a meeting with a missing place or line is told`() {
        val bare = Ch2L1Anchors("minecraft:overworld", emptyList(), emptyList())
        assertNotNull(Ch2L1MeetingScene.build(act2.meeting, bare, voices, members, frame, 0).problem)
        assertNotNull(Ch2L1MeetingScene.build(act2.meeting.copy(lines = listOf("nobody_says_this")), data.anchors, voices, members, frame, 0).problem)
    }

    @Test
    fun `each listener glances at the door now and then, briefly, on their own times`() {
        val glances = Ch2L1Glances(act2.meeting, 6, 1200, 7)
        for (i in 0 until 6) {
            val list = glances.of(i)
            assertTrue(list.size >= 3, "member $i: $list")
            assertTrue(list.all { (it.last - it.first + 1) in act2.meeting.glanceTicks.min..act2.meeting.glanceTicks.max })
        }
        assertTrue(glances.of(0) != glances.of(1))
        assertEquals(glances.of(3), Ch2L1Glances(act2.meeting, 6, 1200, 7).of(3), "the same every time")
    }

    // --- the guide arrow ----------------------------------------------------------------------------------------

    @Test
    fun `the arrow shows over him only when the player has lost him`() {
        val him = Position(0.0, 64.0, 0.0)
        assertNull(Ch2L1Guide.follow(him, Position(3.0, 65.6, 0.0), true, act2.follow))
        assertNotNull(Ch2L1Guide.follow(him, Position(3.0, 65.6, 0.0), false, act2.follow))
        assertEquals(Position(0.0, 64.0 + Ch2L1Guide.OVER_HEAD, 0.0), Ch2L1Guide.follow(him, Position(30.0, 65.6, 0.0), true, act2.follow))
        assertNull(Ch2L1Guide.follow(null, Position(30.0, 65.6, 0.0), false, act2.follow))
    }
}
