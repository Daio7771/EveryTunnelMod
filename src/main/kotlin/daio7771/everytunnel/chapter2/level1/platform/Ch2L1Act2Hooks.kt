package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.platform.Ch2Achievements
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Angles
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Follow
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Glances
import daio7771.everytunnel.chapter2.level1.core.Ch2L1MeetingScene
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Voices
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.core.cutscene.Cutscene
import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.Look
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.Shot
import daio7771.everytunnel.core.cutscene.Timeline
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.platform.KeptEntities
import daio7771.everytunnel.platform.TickProfiler
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.Random
import java.util.UUID

/**
 * Act 2 of Chapter 2's Level 1 on the server (`cutscenes/meeting.json`, [Ch2L1DataLoader.act2]): the coworker and the meeting in the lockers.
 *
 * - **Waiting** ([Ch2L1State.ACT1_TASK3]): from the start of the third task the coworker stands at `coworker_wait`, by the broom, arms
 *   crossed, his eyes on the player while they sweep (he is put there while the player is still in the LAB, out of sight).
 * - **Following** ([Ch2L1State.ACT2_FOLLOW], when the last spot is swept): he says `follow_01` (from where he stands, like any voice of
 *   the level: its subtitles show) and walks `coworker_route` to the lockers ([Ch2L1Follow]: he waits for a player who lags and reminds
 *   them, `follow_02`/`follow_03`), opening the doors in his way; the meeting's group stands waiting in the lockers (spawned now, out of
 *   sight); worker_a's and worker_b's ambient doubles leave the LAB out of sight (`npcs.json`'s `until`).
 * - **The meeting** ([Ch2L1State.ACT2_MEETING], when the player is in the lockers with him there): the player is put at `meeting_player`,
 *   held there, and the scene ([Ch2L1MeetingScene]) plays on their client ([START_PACKET_ID]: the frame and the door they came in by);
 *   this side directs the people on the same ticks: who speaks and how (trembling, laughing), everyone's eyes on the speaker (the coworker
 *   turning to them, body and all) and now and
 *   then on the door ([Ch2L1Glances]), the member nearest the door walking to it and shutting it after the first line, the coworker
 *   holding the badge's copy out and the player getting it as he says "Take this", and, when the last line ends, everyone leaving
 *   along `meeting_leave`, the coworker first, the rest one by one, each gone as soon as the player cannot see them.
 * - **The end**, played or skipped ([SKIP_PACKET_ID]), is [finishMeeting]: the badge given, [Ch2L1State.ACT3_EXPLORE]. A skip also puts the
 *   player at `taquillas_exit`, takes the coworker and the group away at once and opens the door again.
 *
 * Reaching a state by command does what the state means (the coworker waiting, following from the start, the meeting from the start,
 * the badge given and everyone gone); going back before the third task takes them and the badge away. What is in memory here (the walk,
 * the meeting) is lost on leaving the world, and picked up again from the state ([ensure]). Server thread; the scene is also built on the
 * client ([clientBegin]).
 */
object Ch2L1Act2Hooks {

    val START_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_meeting_start")
    val END_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_meeting_end")
    val SKIP_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_meeting_skip")

    /** The coworker's group and worker id. */
    private const val COWORKER = "coworker"

    /** How often what the state means is checked against the world. */
    private const val ENSURE_TICKS = 20

    /** The coworker watches a player sweeping within this. */
    private const val WATCH_RANGE = 24.0

    /** He holds the badge out from this many ticks before he hands it over to this many after. */
    private const val OFFER_BEFORE = 15
    private const val OFFER_AFTER = 30

    private val voices: Ch2L1Voices by lazy { Ch2L1Voices.load() }
    private val spec get() = Ch2L1DataLoader.act2

    private class Following(val player: UUID, val brain: Ch2L1Follow) {
        var talkUntil = 0L
    }

    private class Meeting(
        val player: UUID, val startTick: Long, val at: Vec3d, val yaw: Float, val built: Ch2L1MeetingScene.Built, val door: Int,
        val glances: Ch2L1Glances, val closer: Int,
    ) {
        var doorClosed = false
        var cardGiven = false
        var leaving = false
    }

    private class Leaver(val entity: UUID, val startTick: Long) {
        var point = 0
    }

    private var following: Following? = null
    private var meeting: Meeting? = null
    private val leavers = mutableListOf<Leaver>()
    private var leaversFor: UUID? = null

    /** What the client is playing (its own copy, built from the same data and frame as the server's). */
    @Volatile
    private var clientScene: Ch2L1MeetingScene.Built? = null

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(SKIP_PACKET_ID) { server, player, _, _, _ ->
            server.execute { if (meeting?.player == player.uuid) finishMeeting(server, skipped = true) }
        }
        TickProfiler.endServerTick.register(::tick)
        Ch2L1.onStateChange(::onState)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (following?.player == handler.player.uuid) following = null
            if (meeting?.player == handler.player.uuid) meeting = null
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { forget() }
    }

    /** Everything in memory dropped (the world closed, the player left the level). */
    fun forget() {
        following = null
        meeting = null
        leavers.clear()
        leaversFor = null
    }

    fun isMeetingRunning(player: ServerPlayerEntity): Boolean = meeting?.player == player.uuid

    // --- the states ------------------------------------------------------------------------------------

    private fun onState(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State) {
        val server = player.server
        when {
            to < Ch2L1State.ACT1_TASK3 -> {
                if (meeting?.player == player.uuid) endClientScene(player, skipped = true)
                clear(server)
                removeBadge(player)
            }
            to == Ch2L1State.ACT1_TASK3 -> waitForPlayer(player)
            to == Ch2L1State.ACT2_FOLLOW -> startFollow(player, opening = from == Ch2L1State.ACT1_TASK3)
            to == Ch2L1State.ACT2_MEETING -> startMeeting(player)
            else -> {
                if (meeting?.player == player.uuid) {
                    finishMeeting(server, skipped = true, setState = false)
                } else if (leavers.isEmpty()) {
                    following = null
                    despawnAll(server)
                }
                if (from < Ch2L1State.ACT3_EXPLORE) giveBadge(player)
            }
        }
    }

    private fun tick(server: MinecraftServer) {
        tickFollow(server)
        tickMeeting(server)
        tickLeavers(server)
        if (server.ticks % ENSURE_TICKS == 0) {
            for (player in server.playerManager.playerList) if (Ch2L1.active(player)) ensure(player)
        }
    }

    /** Brings the world to what the player's state means, if something is missing (after leaving the world, a chunk unloaded...). */
    private fun ensure(player: ServerPlayerEntity) {
        if (!Ch2L1Build.isBuilt(player.server)) return
        when (Ch2L1.state(player)) {
            Ch2L1State.ACT1_TASK3 -> waitForPlayer(player)
            Ch2L1State.ACT2_FOLLOW -> if (following?.player != player.uuid) startFollow(player, opening = false)
            Ch2L1State.ACT2_MEETING -> if (meeting?.player != player.uuid) startMeeting(player)
            else -> if (Ch2L1Act2Hooks.leavers.isEmpty() && Ch2L1State.ACT3_EXPLORE <= Ch2L1.state(player)) {
                // Left over (the world was closed while they were leaving): each goes as soon as the player cannot see them.
                val world = Ch2L1Npcs.world(player.server) ?: return
                (listOfNotNull(coworker(world)) + members(world).values).filter { !Ch2L1Npcs.seen(player, it) }.forEach(::gone)
            }
        }
    }

    // --- the people ----------------------------------------------------------------------------------------

    private fun coworker(world: ServerWorld): FactoryNpcEntity? =
        world.getEntitiesByType(Ch2Entities.FACTORY_NPC, Ch2L1Npcs.levelBox()) { it.workerId == COWORKER && !it.isRemoved }.firstOrNull()

    /** The meeting group's members by their index in `npcs.json`'s group. */
    private fun members(world: ServerWorld): Map<Int, FactoryNpcEntity> {
        val prefix = spec.meeting.group + "_"
        return world.getEntitiesByType(Ch2Entities.FACTORY_NPC, Ch2L1Npcs.levelBox()) { it.workerId?.startsWith(prefix) == true && !it.isRemoved }
            .associateBy { it.workerId!!.removePrefix(prefix).toIntOrNull() ?: -1 }
    }

    private fun spawn(world: ServerWorld, at: String, skin: String, id: String, group: String): FactoryNpcEntity? {
        val anchor = Ch2L1DataLoader.data.anchors.point(at) ?: return null
        val p = anchor.position
        if (!world.isChunkLoaded(BlockPos.ofFloored(p.x, p.y, p.z))) return null
        return KeptEntities.spawn(world) { w ->
            Ch2Entities.FACTORY_NPC.create(w)?.apply {
                refreshPositionAndAngles(p.x, p.y, p.z, anchor.yaw, 0f)
                headYaw = anchor.yaw
                bodyYaw = anchor.yaw
                this.skin = skin
                workerId = id
                this.group = group
                seed = id.hashCode().toLong()
                life = Ch2L1DataLoader.npcs.life
            }
        }
    }

    private fun ensureCoworker(world: ServerWorld, at: String): FactoryNpcEntity? =
        coworker(world) ?: spawn(world, at, Ch2L1Skins.COWORKER, COWORKER, COWORKER)

    /** The group of the meeting, each at their place facing him; those missing are put in. */
    private fun ensureMembers(world: ServerWorld): Map<Int, FactoryNpcEntity> {
        val list = Ch2L1DataLoader.npcs.groups[spec.meeting.group] ?: return emptyMap()
        val present = members(world).toMutableMap()
        list.forEachIndexed { i, member ->
            val npc = present[i] ?: spawn(world, member.at, member.skin, "${spec.meeting.group}_$i", spec.meeting.group)?.also { present[i] = it } ?: return@forEachIndexed
            if (npc.direction == null) {
                npc.direction = FactoryNpcEntity.Direction().apply {
                    face = Ch2L1DataLoader.data.anchors.point(member.at)?.yaw
                    pose = Ch2L1Pose.NONE
                }
            }
        }
        return present
    }

    private fun despawnAll(server: MinecraftServer) {
        Ch2L1Npcs.despawnGroup(server, COWORKER)
        Ch2L1Npcs.despawnGroup(server, spec.meeting.group)
    }

    private fun clear(server: MinecraftServer) {
        following = null
        meeting = null
        leavers.clear()
        despawnAll(server)
    }

    private fun eyes(player: ServerPlayerEntity) = Vec3d(player.x, player.eyeY, player.z)

    private fun eyes(npc: FactoryNpcEntity) = Vec3d(npc.x, npc.eyeY, npc.z)

    private fun position(v: Vec3d) = Position(v.x, v.y, v.z)

    /** [line] said by [npc], heard by [player] from where [npc] stands (its subtitles show like every voice of the level). */
    private fun say(player: ServerPlayerEntity, npc: FactoryNpcEntity, line: String) {
        val event = SoundEvent.of(Identifier(EveryTunnel.MOD_ID, "voice.$line"))
        player.networkHandler.sendPacket(
            PlaySoundS2CPacket(RegistryEntry.of(event), SoundCategory.VOICE, npc.x, npc.eyeY, npc.z, 1.2f, 1f, player.world.random.nextLong()),
        )
    }

    // --- waiting by the broom --------------------------------------------------------------------------

    /** The coworker by the broom, standing still, watching the player when they are near. */
    private fun waitForPlayer(player: ServerPlayerEntity) {
        val world = Ch2L1Npcs.world(player.server) ?: return
        val anchor = Ch2L1DataLoader.data.anchors.point(spec.follow.waitAnchor) ?: return
        val coworker = ensureCoworker(world, spec.follow.waitAnchor) ?: return
        val d = coworker.direction ?: FactoryNpcEntity.Direction().also { coworker.direction = it }
        d.walkTo = Vec3d(anchor.position.x, anchor.position.y, anchor.position.z)
        d.face = anchor.yaw
        d.pose = Ch2L1Pose.NONE
        d.lookAt = if (coworker.squaredDistanceTo(player) < WATCH_RANGE * WATCH_RANGE) eyes(player) else null
    }

    // --- following him -----------------------------------------------------------------------------------

    private fun startFollow(player: ServerPlayerEntity, opening: Boolean) {
        val world = Ch2L1Npcs.world(player.server) ?: return
        val route = Ch2L1DataLoader.data.anchors.path(spec.follow.route)?.points
        val coworker = ensureCoworker(world, spec.follow.waitAnchor)
        if (route == null || coworker == null) {
            EveryTunnel.LOGGER.warn("Chapter 2 Level 1: the coworker cannot lead the way (no route {} or no coworker)", spec.follow.route)
            return
        }
        val line = voices.line(spec.follow.openingLine)
        val openingTicks = if (opening && line != null) line.ms / 50 + 10 else 0
        val f = Following(player.uuid, Ch2L1Follow(spec.follow, route, openingTicks))
        following = f
        coworker.pace = spec.follow.pace
        coworker.direction = FactoryNpcEntity.Direction()
        if (opening && line != null) {
            say(player, coworker, line.id)
            f.talkUntil = player.server.ticks.toLong() + line.ms / 50
        }
        ensureMembers(world)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: the coworker leads {} to the lockers", player.gameProfile.name)
    }

    private fun tickFollow(server: MinecraftServer) {
        val f = following ?: return
        val player = server.playerManager.getPlayer(f.player)
        if (player == null || Ch2L1.state(player) != Ch2L1State.ACT2_FOLLOW) {
            following = null
            return
        }
        val world = Ch2L1Npcs.world(server) ?: return
        val coworker = coworker(world) ?: return
        val step = f.brain.tick(position(coworker.pos), position(player.pos))
        val d = coworker.direction ?: FactoryNpcEntity.Direction().also { coworker.direction = it }
        val talking = server.ticks < f.talkUntil
        when (val command = step.command) {
            is Ch2L1Follow.Command.Walk -> {
                d.walkTo = Vec3d(command.x, command.y, command.z)
                d.stopThere = command.last
                d.face = null
                d.lookAt = null
            }
            Ch2L1Follow.Command.Wait -> {
                d.walkTo = null
                d.face = null
                d.lookAt = eyes(player)
            }
            Ch2L1Follow.Command.Arrived -> {
                d.walkTo = null
                d.face = Ch2L1DataLoader.data.anchors.point(spec.meeting.coworkerSpot)?.yaw
                d.lookAt = if (Ch2L1Npcs.seen(player, coworker) || coworker.squaredDistanceTo(player) < 64) eyes(player) else null
            }
        }
        d.pose = if (talking) Ch2L1Pose.TALKING else Ch2L1Pose.NONE
        step.say?.let { line ->
            say(player, coworker, line)
            if (line in spec.follow.reminders) Ch2Achievements.followReminder(player, line, spec.follow.reminders)
            f.talkUntil = server.ticks.toLong() + (voices.line(line)?.ms ?: 1000) / 50
        }
        val zone = Ch2L1DataLoader.data.anchors.zone(spec.meeting.zone)?.box ?: return
        val inside = zone.contains(player.x, player.y, player.z)
        if (inside && (f.brain.arrived || zone.contains(coworker.x, coworker.y, coworker.z))) {
            Ch2L1.setState(player, Ch2L1State.ACT2_MEETING, "the meeting begins")
        }
    }

    // --- the meeting ------------------------------------------------------------------------------------------

    /** The scene seen from [frame] with [door] the one the player came in by, or null (logged) if the data lacks something. */
    fun build(frame: SceneFrame, door: Int): Ch2L1MeetingScene.Built? {
        val members = Ch2L1DataLoader.npcs.groups[spec.meeting.group] ?: emptyList()
        val result = Ch2L1MeetingScene.build(spec.meeting, Ch2L1DataLoader.data.anchors, voices, members, frame, door)
        result.problem?.let { EveryTunnel.LOGGER.warn("Chapter 2 Level 1: the meeting cannot be made: {}", it) }
        return result.built
    }

    /** The client begins the scene seen from [frame]; false if it cannot be built. */
    fun clientBegin(frame: SceneFrame, door: Int): Boolean {
        clientScene = build(frame, door)
        return clientScene != null
    }

    /** What the client is playing (a bare placeholder when none: it is never drawn then). */
    fun clientScene(): Ch2L1MeetingScene.Built? = clientScene

    fun clientTimeline(): Timeline = clientScene?.timeline ?: PLACEHOLDER

    private val PLACEHOLDER = Timeline(Cutscene(listOf(Shot(1, Local(0.0, 0.0, 0.0), Local(0.0, 0.0, 0.0), Look.Player)), 1, 1), emptyList(), emptyList())

    /** (x, y, z, yaw) of where the player stands, and the door, as [startMeeting] wrote them. */
    fun readStart(buf: PacketByteBuf): Pair<SceneFrame, Int> =
        SceneFrame(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat().toDouble()) to buf.readInt()

    private fun startMeeting(player: ServerPlayerEntity) {
        val server = player.server
        val world = Ch2L1Npcs.world(server) ?: return
        val anchors = Ch2L1DataLoader.data.anchors
        val m = spec.meeting
        following = null
        val spot = anchors.point(m.playerSpot)
        val coworker = ensureCoworker(world, m.coworkerSpot)
        val members = ensureMembers(world)
        if (spot == null || coworker == null || !ServerPlayNetworking.canSend(player, START_PACKET_ID)) {
            EveryTunnel.LOGGER.warn("Chapter 2 Level 1: the meeting cannot be played: it is skipped")
            Ch2L1.setState(player, Ch2L1State.ACT3_EXPLORE, "no meeting")
            return
        }
        val door = m.doors.indices.minByOrNull { i ->
            anchors.point(m.doors[i])?.position?.let { (it.x - player.x) * (it.x - player.x) + (it.z - player.z) * (it.z - player.z) } ?: Double.MAX_VALUE
        } ?: 0
        val p = spot.position
        player.stopRiding()
        player.teleport(world, p.x, p.y, p.z, spot.yaw, 0f)
        anchors.point(m.coworkerSpot)?.let { c ->
            if (coworker.squaredDistanceTo(c.position.x, c.position.y, c.position.z) > 2.25) {
                coworker.refreshPositionAndAngles(c.position.x, c.position.y, c.position.z, c.yaw, 0f)
            }
        }
        val frame = SceneFrame(p.x, p.y, p.z, spot.yaw.toDouble())
        val built = build(frame, door)
        if (built == null) {
            Ch2L1.setState(player, Ch2L1State.ACT3_EXPLORE, "no meeting")
            return
        }
        val inside = anchors.point("${m.doors[door]}_inside")?.position
        val closer = members.keys.minByOrNull { i ->
            val npc = members.getValue(i)
            if (inside == null) 0.0 else npc.squaredDistanceTo(inside.x, inside.y, inside.z)
        } ?: -1
        meeting = Meeting(
            player.uuid, server.ticks.toLong(), Vec3d(p.x, p.y, p.z), spot.yaw, built, door,
            Ch2L1Glances(m, members.size.coerceAtLeast(Ch2L1DataLoader.npcs.groups[m.group]?.size ?: 0), built.timeline.cutscene.totalTicks, server.ticks.toLong()),
            closer,
        )
        val buf = PacketByteBufs.create()
        buf.writeDouble(frame.x)
        buf.writeDouble(frame.y)
        buf.writeDouble(frame.z)
        buf.writeFloat(spot.yaw)
        buf.writeInt(door)
        ServerPlayNetworking.send(player, START_PACKET_ID, buf)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: the meeting begins for {} ({} s, door {})", player.gameProfile.name, built.timeline.cutscene.totalTicks / 20, m.doors[door])
    }

    private fun tickMeeting(server: MinecraftServer) {
        val m = meeting ?: return
        val player = server.playerManager.getPlayer(m.player)
        if (player == null) {
            meeting = null
            return
        }
        val world = Ch2L1Npcs.world(server) ?: return
        val anchors = Ch2L1DataLoader.data.anchors
        val spec = spec.meeting
        val built = m.built
        val t = (server.ticks - m.startTick).toInt()
        if (t >= built.timeline.cutscene.totalTicks) {
            finishMeeting(server, skipped = false)
            return
        }
        // Held there: the client takes their keys, this keeps them there whatever it does.
        if (player.pos.squaredDistanceTo(m.at) > 0.0025) player.networkHandler.requestTeleport(m.at.x, m.at.y, m.at.z, m.yaw, 0f)
        player.velocity = Vec3d.ZERO
        if (!m.leaving && t >= built.linesEnd) {
            m.leaving = true
            startLeaving(server, player)
        }
        val coworker = coworker(world)
        val members = members(world)
        val beat = built.beatAt(t.toDouble())
        val doorAnchor = anchors.point(spec.doors[m.door])?.position
        val doorEyes = doorAnchor?.let { Vec3d(it.x, it.y + 1.3, it.z) }
        val speaker: FactoryNpcEntity? = when (beat?.speaker) {
            null -> null
            Ch2L1Skins.COWORKER -> coworker
            else -> members.values.firstOrNull { it.skin == beat.speaker }
        }
        val playerEyes = eyes(player)
        if (!m.leaving) {
            if (coworker != null) {
                val spot = anchors.point(spec.coworkerSpot)
                val d = coworker.direction ?: FactoryNpcEntity.Direction().also { coworker.direction = it }
                d.walkTo = spot?.position?.let { Vec3d(it.x, it.y, it.z) }
                // He turns to whoever else speaks, body and head, and back to the player for his own lines.
                val listening = speaker != null && speaker !== coworker
                d.face = if (listening) Ch2L1Angles.yawOf(speaker!!.x - coworker.x, speaker.z - coworker.z) else spot?.yaw
                d.lookAt = if (listening) eyes(speaker!!) else playerEyes
                d.pose = when {
                    t in built.cardTick - OFFER_BEFORE..built.cardTick + OFFER_AFTER -> Ch2L1Pose.OFFERING
                    speaker === coworker -> Ch2L1Pose.TALKING
                    else -> Ch2L1Pose.LISTENING
                }
            }
            val list = Ch2L1DataLoader.npcs.groups[spec.group] ?: emptyList()
            for ((i, npc) in members) {
                val slot = list.getOrNull(i)?.let { anchors.point(it.at) } ?: continue
                val d = npc.direction ?: FactoryNpcEntity.Direction().also { npc.direction = it }
                val inside = anchors.point("${spec.doors[m.door]}_inside")?.position
                val closing = i == m.closer && inside != null && built.beats.isNotEmpty() && t >= built.beats.first().start && !m.doorClosed
                d.walkTo = if (closing) Vec3d(inside!!.x, inside.y, inside.z) else Vec3d(slot.position.x, slot.position.y, slot.position.z)
                d.face = if (closing) null else slot.yaw
                d.lookAt = when {
                    closing -> doorEyes
                    npc === speaker -> playerEyes
                    doorEyes != null && m.glances.glancing(i, t) -> doorEyes
                    speaker != null -> eyes(speaker)
                    coworker != null -> eyes(coworker)
                    else -> null
                }
                d.pose = when {
                    npc === speaker && beat!!.line.id in spec.trembleLines -> Ch2L1Pose.TREMBLING
                    npc === speaker && beat!!.line.id in spec.laughLines -> Ch2L1Pose.LAUGHING
                    npc === speaker -> Ch2L1Pose.TALKING
                    else -> Ch2L1Pose.LISTENING
                }
                if (closing && !m.doorClosed && built.doorCloses >= 0 && t >= built.doorCloses && (d.arrived || t >= built.doorEnd - 5)) {
                    closeDoor(world, m, npc)
                }
            }
            if (!m.doorClosed && built.doorCloses >= 0 && t >= built.doorEnd) closeDoor(world, m, null)
        }
        if (!m.cardGiven && t >= built.cardTick) {
            m.cardGiven = true
            giveBadge(player)
        }
    }

    private fun closeDoor(world: ServerWorld, m: Meeting, by: FactoryNpcEntity?) {
        m.doorClosed = true
        val at = Ch2L1DataLoader.data.anchors.point(spec.meeting.doors[m.door])?.position ?: return
        Ch2L1Doors.set(world, Vec3d(at.x, at.y, at.z), DOOR_RADIUS, false, by)
    }

    /** The leaves of a doorway are found within this of its anchor. */
    private const val DOOR_RADIUS = 1.8

    /** The meeting's end, played through ([skipped] false) or skipped; [setState] false when a state change is what ends it. */
    private fun finishMeeting(server: MinecraftServer, skipped: Boolean, setState: Boolean = true) {
        val m = meeting ?: return
        meeting = null
        val player = server.playerManager.getPlayer(m.player) ?: return
        if (!m.cardGiven) giveBadge(player)
        if (skipped) {
            leavers.clear()
            despawnAll(server)
            Ch2L1Npcs.world(server)?.let { world ->
                Ch2L1DataLoader.data.anchors.point(spec.meeting.doors[m.door])?.position?.let { Ch2L1Doors.set(world, Vec3d(it.x, it.y, it.z), DOOR_RADIUS, true) }
                Ch2L1DataLoader.data.anchors.point(spec.meeting.exitAnchor)?.let { exit ->
                    player.teleport(world, exit.position.x, exit.position.y, exit.position.z, exit.yaw, 0f)
                }
            }
        } else if (!m.leaving) {
            startLeaving(server, player)
        }
        endClientScene(player, skipped)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: the meeting {}", if (skipped) "skipped" else "ends")
        if (setState) Ch2L1.setState(player, Ch2L1State.ACT3_EXPLORE, if (skipped) "meeting skipped" else "meeting over")
    }

    private fun endClientScene(player: ServerPlayerEntity, skipped: Boolean) {
        if (!ServerPlayNetworking.canSend(player, END_PACKET_ID)) return
        val buf = PacketByteBufs.create()
        buf.writeBoolean(skipped)
        ServerPlayNetworking.send(player, END_PACKET_ID, buf)
    }

    // --- leaving ---------------------------------------------------------------------------------------------

    /** Everyone leaves the lockers, the coworker first, the rest one by one in no set order. */
    private fun startLeaving(server: MinecraftServer, player: ServerPlayerEntity) {
        val world = Ch2L1Npcs.world(server) ?: return
        leavers.clear()
        leaversFor = player.uuid
        val random = Random(server.ticks.toLong())
        var at = server.ticks.toLong()
        coworker(world)?.let { leavers += Leaver(it.uuid, at) }
        for (npc in members(world).values.shuffled(random)) {
            at += spec.meeting.leaveGapTicks.pick(random)
            leavers += Leaver(npc.uuid, at)
        }
    }

    private fun tickLeavers(server: MinecraftServer) {
        if (leavers.isEmpty()) return
        val world = Ch2L1Npcs.world(server) ?: return
        val player = leaversFor?.let { server.playerManager.getPlayer(it) }
        val path = Ch2L1DataLoader.data.anchors.path(spec.meeting.leavePath)?.points ?: emptyList()
        leavers.removeAll { leaver ->
            val npc = world.getEntity(leaver.entity) as? FactoryNpcEntity ?: return@removeAll true
            if (player == null) {
                gone(npc)
                return@removeAll true
            }
            if (server.ticks < leaver.startTick) return@removeAll false
            npc.pace = spec.meeting.leavePace
            val d = npc.direction ?: FactoryNpcEntity.Direction().also { npc.direction = it }
            d.lookAt = null
            d.face = null
            d.pose = Ch2L1Pose.NONE
            while (leaver.point < path.size && Ch2L1Follow.flat(position(npc.pos), path[leaver.point]) < Ch2L1Follow.ARRIVE) leaver.point++
            if (leaver.point < path.size) {
                val p = path[leaver.point]
                d.walkTo = Vec3d(p.x, p.y, p.z)
                d.stopThere = leaver.point == path.size - 1
                // Past the door and out of the player's sight: gone.
                if (leaver.point >= 2 && !Ch2L1Npcs.seen(player, npc)) {
                    gone(npc)
                    return@removeAll true
                }
                return@removeAll false
            }
            d.walkTo = null
            if (!Ch2L1Npcs.seen(player, npc)) {
                gone(npc)
                return@removeAll true
            }
            false
        }
    }

    private fun gone(npc: FactoryNpcEntity) {
        KeptEntities.forget { it === npc }
        npc.discard()
    }

    // --- the badge's copy ------------------------------------------------------------------------------

    /** The badge's copy into the player's pockets (once: not if they have it), with a small sound. */
    private fun giveBadge(player: ServerPlayerEntity) {
        if (player.inventory.containsAny { it.isOf(Ch2Items.LAB_BADGE_COPY) }) return
        player.giveItemStack(ItemStack(Ch2Items.LAB_BADGE_COPY))
        player.world.playSound(null, player.blockPos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f)
    }

    private fun removeBadge(player: ServerPlayerEntity) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            if (inventory.getStack(slot).isOf(Ch2Items.LAB_BADGE_COPY)) inventory.setStack(slot, ItemStack.EMPTY)
        }
    }
}
