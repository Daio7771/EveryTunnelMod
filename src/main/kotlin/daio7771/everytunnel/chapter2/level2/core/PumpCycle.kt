package daio7771.everytunnel.chapter2.level2.core

import kotlin.math.roundToLong

/** What the pump is doing: running, in its last second of running (slowing down, audibly and visibly) or silent. */
enum class PumpPhase { RUNNING, SLOWING, SILENT }

/**
 * Chapter 2 Level 2's pump (phase 4): a cycle of `run_seconds` of running (the last `slow_seconds` of it slowing down) and
 * `silent_seconds` of silence, counted in ticks from the moment it starts running. While it runs it masks what the player does on the
 * floor from the vibration creature (a vibration made in [RUNNING] or [SLOWING] is not felt), and it keeps doing so for
 * `grace_after_stop_seconds` after the silence begins, so a step begun while it ran is not felt. Plain Kotlin; `platform.Ch2L2Pump`
 * runs it.
 */
class PumpCycle(private val config: Ch2L2PumpConfig, graceAfterStopSeconds: Double) {

    private val runTicks = ticks(config.runSeconds)
    private val slowTicks = ticks(config.slowSeconds)
    private val cycleTicks = runTicks + ticks(config.silentSeconds)
    private val graceTicks = ticks(graceAfterStopSeconds)

    /** Where [ticks] since the pump started running falls in the cycle (0 up to the cycle's length). */
    private fun into(ticks: Long): Long = Math.floorMod(ticks, cycleTicks)

    fun phaseAt(ticks: Long): PumpPhase {
        val t = into(ticks)
        return when {
            t < runTicks - slowTicks -> PumpPhase.RUNNING
            t < runTicks -> PumpPhase.SLOWING
            else -> PumpPhase.SILENT
        }
    }

    /** A vibration made at [ticks] is masked: the pump runs, or has stopped for less than the grace. */
    fun masking(ticks: Long): Boolean {
        return into(ticks) < runTicks + graceTicks
    }

    /** The silence begins at [ticks] (the first tick of it, whichever cycle). */
    fun silenceBegins(ticks: Long): Boolean = into(ticks) == runTicks

    /** The pump starts running again at [ticks]. */
    fun runBegins(ticks: Long): Boolean = into(ticks) == 0L

    /** Ticks from the start of a cycle to the start of its silence. */
    val silenceStartsAt: Long get() = runTicks

    val cycleLengthTicks: Long get() = cycleTicks

    companion object {
        fun ticks(seconds: Double): Long = (seconds * 20).roundToLong()
    }
}
