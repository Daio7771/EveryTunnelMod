package daio7771.everytunnel.chapter2.platform

import java.util.concurrent.CopyOnWriteArrayList

/**
 * What `/et reload` reads again besides Level 1's data (which its command reads itself): each later level of the chapter registers its
 * loader once, and the command answers with every line they report. Server thread.
 */
object Ch2DataReload {

    private val reloaders = CopyOnWriteArrayList<() -> List<String>>()

    fun register(reloader: () -> List<String>) {
        reloaders += reloader
    }

    /** Runs every loader; their report lines, in the order they registered. */
    fun reloadAll(): List<String> = reloaders.flatMap { it() }
}
