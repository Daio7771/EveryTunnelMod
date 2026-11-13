package daio7771.everytunnel.core.spawn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostLedgerTest {

    private val alive = setOf("old", "new")

    @Test
    fun `the first to claim a post owns it`() {
        val ledger = PostLedger<String>()
        assertTrue(ledger.claim("level_3/guard", "old") { it in alive })
        assertTrue(ledger.claim("level_3/guard", "old") { it in alive })
    }

    @Test
    fun `a leftover cannot claim a post a living creature owns`() {
        val ledger = PostLedger<String>()
        ledger.assign("level_3/guard", "new")
        assertFalse(ledger.claim("level_3/guard", "old") { it in alive })
        assertTrue(ledger.claim("level_3/guard", "new") { it in alive })
    }

    @Test
    fun `a creature takes over the post of one that is gone`() {
        val ledger = PostLedger<String>()
        ledger.assign("level_3/guard", "old")
        assertTrue(ledger.claim("level_3/guard", "new") { false })
        assertFalse(ledger.claim("level_3/guard", "old") { it == "new" })
    }

    @Test
    fun `an owner leaving takes the record with it`() {
        val ledger = PostLedger<String>()
        ledger.assign("level_3/guard", "old")
        assertTrue(ledger.release("level_3/guard", "old"))
        assertTrue(ledger.claim("level_3/guard", "new") { it in alive })
    }

    @Test
    fun `a post nobody owns lets a creature leave with its record`() {
        val ledger = PostLedger<String>()
        assertTrue(ledger.release("level_3/guard", "old"))
    }

    @Test
    fun `the old creature's late death leaves the new one's record alone`() {
        val ledger = PostLedger<String>()
        ledger.assign("level_3/guard", "old")
        // The build killed "old" and it is still playing its death when "new" is put in its place.
        ledger.assign("level_3/guard", "new")
        assertFalse(ledger.release("level_3/guard", "old"))
        assertFalse(ledger.claim("level_3/guard", "old") { it == "new" })
        assertTrue(ledger.release("level_3/guard", "new"))
    }

    @Test
    fun `posts are independent and clear forgets them all`() {
        val ledger = PostLedger<String>()
        ledger.assign("level_3/a", "old")
        ledger.assign("level_3/b", "new")
        assertFalse(ledger.claim("level_3/a", "new") { it in alive })
        ledger.clear()
        assertEquals(true, ledger.claim("level_3/a", "new") { it in alive })
    }
}
