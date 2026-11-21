package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Speakers
import daio7771.everytunnel.chapter2.level1.core.Ch2L1SpeakerSchedule
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Voices
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.Random
import java.util.UUID
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.common.platform.TickProfiler
/**
 * Rowan's loudspeakers in Act 1 of Chapter 2's Level 1: the announcements `rowan_loyal_a*`. What plays and when is
 * [Ch2L1SpeakerSchedule] (the rules of `speakers.json`: a1 three seconds after clocking in, a4 five seconds after task 2, a
 * random one of the pool now and then, never two at once, only in a speaker zone, none from Act 2 on); this object keeps one for each
 * player playing the level, ticks it, and plays what it says from the ceiling speaker of the zone the player is in: the chime
 * ([Ch2Sounds.CH2L1_DING]) now and the voice (`voice.<id>`, its subtitles drawn by the client) when the chime is over, both
 * straight to that player (`PlaySoundS2CPacket`, heard all through the zone: [Ch2L1Speakers.volume]). Nothing speaks while the player has
 * NonameOS open ([Ch2L1TaskHooks.isTerminalOpen]), and when they leave it there is a beat ([Ch2L1Speakers.afterTerminalTicks]) before the
 * opening line. The first announcement is the cinematic ([Ch2L1IntroHooks]) instead, from the speaker it turns to, which plays its own
 * chime and voice. What a player has heard is in their progress
 * (`heard_<line>`), so leaving the world does not repeat it; going back to the start of the level forgets it.
 * Server thread only.
 */
object Ch2L1SpeakerHooks {

    private const val HEARD = "heard_"

    private class Queued(val line: String, val at: Long, val from: Vec3d)

    private class Entry(val data: Ch2L1Speakers, val schedule: Ch2L1SpeakerSchedule) {
        var queued: Queued? = null
    }

    private val entries = HashMap<UUID, Entry>()
    private val events = HashMap<String, SoundEvent>()
    private val voices: Ch2L1Voices by lazy { Ch2L1Voices.load() }

    private val speakers get() = Ch2L1DataLoader.speakers

    fun register() {
        Ch2L1.onStateChange(::onState)
        TickProfiler.endServerTick.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> entries.remove(handler.player.uuid) }
    }

    /** A voice line's sound event (`voice.<id>`). */
    private fun voice(line: String): SoundEvent = events.getOrPut(line) { SoundEvent.of(Identifier(EveryTunnel.MOD_ID, "voice.$line")) }

    /** How long a line takes from its chime: the chime, then the voice, in ticks. */
    private fun lineTicks(line: String): Int = speakers.dingTicks + (voices.line(line)?.ms?.let { (it + 49) / 50 } ?: 100)

    private fun entryOf(player: ServerPlayerEntity, server: MinecraftServer): Entry {
        val data = speakers
        entries[player.uuid]?.takeIf { it.data === data }?.let { return it }
        val schedule = Ch2L1SpeakerSchedule(data, ::lineTicks, Random())
        val heard = (data.fixed.map { it.line } + data.pool).filter { Ch2Progress.ch2L1Counter(player, HEARD + it) != 0 }
        schedule.restore(heard)
        schedule.onState(Ch2L1.state(player), server.ticks.toLong())
        return Entry(data, schedule).also { entries[player.uuid] = it }
    }

    /**
     * The player's entry, new if their progress went back since it was made (a new game, a reset): what an entry remembers of what was said
     * outlives the progress it was made from, and a stale one says nothing for ever.
     */
    private fun freshEntry(player: ServerPlayerEntity, server: MinecraftServer): Entry {
        val entry = entryOf(player, server)
        val state = Ch2L1.state(player)
        if (!entry.schedule.regressed(state)) return entry
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {}'s progress went back to {}: the loudspeakers start over", player.gameProfile.name, state)
        forget(player)
        return entryOf(player, server)
    }

    /** Forgets what the loudspeakers said to [player] (they begin the level again). */
    fun forget(player: ServerPlayerEntity) {
        Ch2Progress.clearCh2L1Counters(player, HEARD)
        entries.remove(player.uuid)
    }

    private fun onState(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State) {
        if (to.ordinal <= Ch2L1State.ACT1_CLOCKIN.ordinal) forget(player)
        val entry = entryOf(player, player.server)
        entry.schedule.onState(to, player.server.ticks.toLong())
        if (to.ordinal >= Ch2L1Speakers.CANCELLED_FROM.ordinal) {
            entry.queued = null
            stop(player)
        }
    }

    /** The player left NonameOS: a beat before anything is said. */
    fun terminalClosed(player: ServerPlayerEntity) {
        if (!Ch2L1.active(player)) return
        entryOf(player, player.server).schedule.holdUntil(player.server.ticks + speakers.afterTerminalTicks.toLong())
    }

    /** What is playing from the loudspeakers goes quiet. */
    private fun stop(player: ServerPlayerEntity) {
        val ids = speakers.fixed.map { it.line } + speakers.pool
        for (line in ids) player.networkHandler.sendPacket(StopSoundS2CPacket(Identifier(EveryTunnel.MOD_ID, "voice.$line"), SoundCategory.VOICE))
        player.networkHandler.sendPacket(StopSoundS2CPacket(Ch2Sounds.CH2L1_DING.id, SoundCategory.VOICE))
    }

    private fun tick(server: MinecraftServer) {
        val now = server.ticks.toLong()
        val live = server.playerManager.playerList.toSet()
        entries.keys.removeAll { id -> live.none { it.uuid == id } }
        for (player in live) {
            if (!Ch2L1.active(player) || player.isSpectator) continue
            if (Ch2L1IntroHooks.isRunning(player)) continue
            val entry = freshEntry(player, server)
            entry.queued?.let { queued ->
                if (now >= queued.at) {
                    entry.queued = null
                    send(player, voice(queued.line), queued.from)
                }
            }
            val zone = zoneOf(player)
            val line = entry.schedule.tick(now, zone != null && !Ch2L1TaskHooks.isTerminalOpen(player)) ?: continue
            play(player, entry, line, zone?.let(::emitter), now)
        }
    }

    /** The speaker zone the player stands in, or null. */
    fun zoneOf(player: ServerPlayerEntity): String? {
        val anchors = Ch2L1DataLoader.data.anchors
        val there = player.pos
        return speakers.zones.firstOrNull { anchors.zone(it)?.box?.contains(there.x, there.y, there.z) == true }
    }

    /** Where the ceiling speaker of [zone] is. */
    private fun emitter(zone: String): Vec3d? =
        speakers.emitters[zone]?.let { Ch2L1DataLoader.data.anchors.point(it) }?.position?.let { Vec3d(it.x, it.y, it.z) }

    private fun play(player: ServerPlayerEntity, entry: Entry, line: String, from: Vec3d?, now: Long) {
        Ch2Progress.setCh2L1Counter(player, HEARD + line, 1)
        val source = from ?: player.pos.add(0.0, 2.0, 0.0)
        if (line == speakers.intro?.line && Ch2L1IntroHooks.start(player, Ch2L1IntroHooks.speakerOf(source))) return
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: the loudspeakers say {} to {}", line, player.gameProfile.name)
        send(player, Ch2Sounds.CH2L1_DING, source)
        entry.queued = Queued(line, now + speakers.dingTicks, source)
    }

    /** [event] from [at], to this player alone, loud enough to be heard all through the zone. */
    fun send(player: ServerPlayerEntity, event: SoundEvent, at: Vec3d) {
        player.networkHandler.sendPacket(
            PlaySoundS2CPacket(RegistryEntry.of(event), SoundCategory.VOICE, at.x, at.y, at.z, speakers.volume, 1f, player.world.random.nextLong()),
        )
    }

    /** [line] once, from the speaker of the zone the player is in (the office's after-scene `rowan_loyal_a6`): heard from now on. */
    fun announceOnce(player: ServerPlayerEntity, line: String): Boolean {
        if (Ch2Progress.ch2L1Counter(player, HEARD + line) > 0) return false
        Ch2Progress.setCh2L1Counter(player, HEARD + line, 1)
        return say(player, line)
    }

    /** True once [player] has heard [line]. */
    fun heard(player: ServerPlayerEntity, line: String): Boolean = Ch2Progress.ch2L1Counter(player, HEARD + line) > 0

    /** `/et ch2 l1 say <line>`: the announcement now, from the speaker of the zone the player is in (or above them). */
    fun say(player: ServerPlayerEntity, line: String): Boolean {
        if (voices.line(line) == null) return false
        val entry = entryOf(player, player.server)
        val from = zoneOf(player)?.let(::emitter) ?: player.pos.add(0.0, 2.0, 0.0)
        send(player, Ch2Sounds.CH2L1_DING, from)
        entry.queued = Queued(line, player.server.ticks + speakers.dingTicks.toLong(), from)
        return true
    }
}
