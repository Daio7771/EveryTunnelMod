package daio7771.everytunnel.core.spawn

/**
 * Which creature owns each spawn point ("level/point") right now (plain Kotlin, unit tested), for
 * `platform.ObserverHooks`. A creature killed with `/kill` is not gone at once: it plays its death
 * for a second and only then leaves the world, so for that second the old creature and the new one
 * put in its place share a post, and the old one's leaving must not wipe the new one's record. A
 * creature saved with its chunk can also load after the post was filled again.
 *
 * [I] is whatever tells the creatures apart (their UUID).
 */
class PostLedger<I> {

    private val owners = HashMap<String, I>()

    /** [id] was put at [key] and owns it from now on. */
    fun assign(key: String, id: I) {
        owners[key] = id
    }

    /**
     * [id] stands at [key]. True if it may: nobody owns the post, it owns it already, or the owner
     * is gone ([ownerAlive] says false). False if another living creature owns the post: [id] is a
     * leftover.
     */
    fun claim(key: String, id: I, ownerAlive: (I) -> Boolean): Boolean {
        val owner = owners[key]
        if (owner == null || owner == id || !ownerAlive(owner)) {
            owners[key] = id
            return true
        }
        return false
    }

    /**
     * [id] left [key]. True if the post's record goes with it (it was the owner, or nobody was);
     * false if it was a leftover and a newer creature owns the post: its record stays.
     */
    fun release(key: String, id: I): Boolean {
        val owner = owners[key]
        if (owner != null && owner != id) return false
        owners.remove(key)
        return true
    }

    fun clear() {
        owners.clear()
    }
}
