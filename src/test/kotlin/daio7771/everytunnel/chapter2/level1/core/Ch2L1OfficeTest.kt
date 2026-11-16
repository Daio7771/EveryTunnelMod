package daio7771.everytunnel.chapter2.level1.core

import daio7771.everytunnel.core.cutscene.SceneFrame
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Ch2L1OfficeTest {

    private val data = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data
    private val result = Ch2L1OfficeParser.parse(data.document("cutscenes/office"))
    private val spec = result.spec
    private val voices = Ch2L1Voices.load()
    private val mark = data.anchors.point(spec.playerMark)!!
    private val built = Ch2L1OfficeScene.build(spec, data.anchors, voices, SceneFrame(mark.position.x, mark.position.y, mark.position.z, mark.yaw.toDouble())).let {
        assertNotNull(it.built, it.problem)
    }

    @Test
    fun `the jar's office file reads without a problem and names what anchors has`() {
        assertEquals(emptyList(), result.problems)
        assertEquals((1..8).map { "rowan_office_c$it" }, spec.lines)
        assertEquals(50, spec.pauses["rowan_office_c5"])
        val tasks = Ch2L1TasksParser.parse(data.document("tasks")).tasks
        val speakers = Ch2L1SpeakersParser.parse(data.document("speakers")).speakers
        assertEquals(emptyList(), Ch2L1Wiring.problems(data.anchors, tasks, speakers, office = spec))
    }

    @Test
    fun `he comes in and shuts the door before he speaks, and opens it again after`() {
        val first = built.beats.first()
        assertTrue(built.doorOpens < built.doorCloses)
        assertTrue(built.doorCloses < first.start)
        assertTrue(built.doorReopens > built.beats.last().end)
        assertEquals(data.anchors.point(spec.spawn)!!.position, built.rowanAt(0.0))
        assertEquals(data.anchors.point(spec.threshold)!!.position, built.rowanAt(first.start.toDouble()))
    }

    @Test
    fun `the eight lines in order, the pauses between them, the timing of the pass, the hum and the shake`() {
        assertEquals(spec.lines, built.beats.map { it.line.id })
        for ((a, b) in built.beats.zipWithNext()) {
            val pause = spec.gapTicks + (spec.pauses[a.line.id] ?: 0)
            assertTrue(b.start - a.end >= pause, "${a.line.id} to ${b.line.id}")
        }
        val c8 = built.beats.last()
        val cue = c8.line.cues.first { it.firstSentence == spec.cardSentence }
        assertEquals(c8.start + cue.atMs / 50, built.slideStart)
        assertEquals(built.beats.first { it.line.id == spec.humFrom }.start, built.humStart)
        assertEquals(c8.start, built.humEnd)
        val c2 = built.beats.first { it.line.id == spec.walkLine }
        assertEquals(c2.start, built.shake.first)
        assertEquals(c2.start + ceil(c2.line.ms / 50.0).toInt() - 1, built.shake.last)
    }

    @Test
    fun `he walks round in his cuffs, leans, opens his palms, bends, points, claps, and leaves`() {
        val c2 = built.beats.first { it.line.id == spec.walkLine }
        assertEquals(Ch2L1Pose.CUFFS, built.poseAt(c2.start + 5.0))
        assertEquals(data.anchors.point(spec.lean)!!.position, built.rowanAt(c2.end + 1.0))
        val c3 = built.beats.first { it.line.id == spec.palmsLine }
        assertEquals(Ch2L1Pose.LEANING, built.poseAt(c3.start + 2.0))
        assertEquals(Ch2L1Pose.PALMS, built.poseAt(c3.end - 2.0))
        assertEquals(Ch2L1Pose.LEAN_FORWARD, built.poseAt(built.beats.first { it.line.id == spec.leanLine }.start + 5.0))
        assertTrue(built.segmentAt(built.beats.first { it.line.id == spec.glanceLine }.start + 2.0).lookDoor)
        assertEquals(Ch2L1Pose.POINTING, built.poseAt(built.beats.first { it.line.id == spec.pointLine }.start + 5.0))
        assertEquals(Ch2L1Pose.OFFERING, built.poseAt(built.slideStart + 1.0))
        assertEquals(Ch2L1Pose.CLAPPING, built.poseAt(built.beats.last().end - 2.0))
        assertEquals(data.anchors.point(spec.spawn)!!.position, built.rowanAt(built.linesEnd.toDouble()))
        assertTrue(built.linesEnd < built.totalTicks)
    }
}
