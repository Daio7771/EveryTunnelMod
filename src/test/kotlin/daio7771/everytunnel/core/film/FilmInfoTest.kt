package daio7771.everytunnel.core.film

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FilmInfoTest {

    @Test
    fun `the length comes from frames and speed`() {
        assertEquals(600, FilmInfo(12, 360).ticks)
    }

    @Test
    fun `frames follow the time and stop at the end`() {
        val film = FilmInfo(12, 24)
        assertEquals(1, film.frameAt(0f))
        assertEquals(2, film.frameAt(20f / 12f))
        assertEquals(24, film.frameAt(39.9f))
        assertNull(film.frameAt(40f))
        assertNull(film.frameAt(-1f))
    }

    @Test
    fun `a bad file gives no film`() {
        assertEquals(FilmInfo(12, 60), FilmInfo.parse("""{ "fps": 12, "frames": 60, "width": 288, "height": 128 }"""))
        assertNull(FilmInfo.parse("""{ "fps": 12 }"""))
        assertNull(FilmInfo.parse("not json"))
    }
}
