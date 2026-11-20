package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.level.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MawScenesTest {

    private val data = Ch2L2Data.parse(emptyMap(), Ch2L2Files.readAllBundled())

    @Test
    fun `the jar's maw and reunion read without a problem and name anchors and zones that exist`() {
        assertEquals(emptyList(), data.problems)
        val maw = data.level.maw
        assertEquals(3, maw.pillars.size)
        for (id in maw.pillars) assertNotNull(data.anchors.point(id), id)
        assertEquals(8, maw.pipePoints.size)
        for (id in maw.pipePoints) assertNotNull(data.anchors.point(id), id)
        assertEquals(listOf("coworker", "coworker", "worker_b", "coworker"), data.level.reunion.lines.map { it.who })
        assertTrue(data.level.reunion.lines.all { it.sound != null && it.seconds > 3.0 })
    }

    private val frame = SceneFrame(0.0, 0.0, 0.0, 90.0)
    private val places = mapOf("coworker" to Position(-4.0, 0.0, -1.0), "worker_b" to Position(-4.0, 0.0, 2.0))
    private val leave = Position(-5.0, 0.0, 0.0)

    @Test
    fun `with no lines the eyes rest on the coworker, then turn to the door while they leave`() {
        val r = Ch2L2Reunion.DEFAULT
        val built = ReunionScene.build(r, frame, places, leave)
        assertTrue(built.beats.isEmpty())
        assertTrue(built.leaveStart > built.linesStart)
        assertTrue(built.shotsEnd < built.total)
        assertEquals(built.timeline.cutscene.totalTicks, built.total)
    }

    @Test
    fun `lines play in order, each at its speaker, with a subtitle that outlives it a little`() {
        val r = Ch2L2Reunion.DEFAULT.copy(
            lines = listOf(
                Ch2L2ReunionLine("coworker", "a", null, 2.0),
                Ch2L2ReunionLine("worker_b", "b", "event.x", 3.0),
                Ch2L2ReunionLine("coworker", "c", null, 1.0),
            ),
        )
        val built = ReunionScene.build(r, frame, places, leave)
        assertEquals(listOf("a", "b", "c"), built.beats.map { it.line.key })
        for ((i, beat) in built.beats.withIndex()) {
            assertEquals(Math.round(r.lines[i].seconds * 20).toInt(), beat.end - beat.start)
            if (i > 0) assertTrue(beat.start >= built.beats[i - 1].end)
        }
        val b = built.beats[1]
        assertEquals("b", built.beatAt(b.start.toDouble())?.line?.key)
        assertNull(built.beatAt(b.end.toDouble() + 0.5).takeIf { it?.line?.key == "b" })
        assertEquals(3, built.timeline.subtitles.size)
        assertEquals("every_tunnel.ch2.l2.reunion.a", built.timeline.subtitles[0].key)
    }

    @Test
    fun `a reunion line from a stranger is reported`() {
        val bad = Ch2L2Level.parse(
            com.google.gson.JsonParser.parseString("""{"reunion": {"lines": [{"who": "rowan", "key": "x", "seconds": 2}]}}""").asJsonObject,
        )
        assertTrue(bad.problems.any { it.contains("rowan") }, "${bad.problems}")
    }
}
