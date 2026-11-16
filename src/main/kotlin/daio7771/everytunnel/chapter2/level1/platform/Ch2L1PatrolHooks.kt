package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Angles
import daio7771.everytunnel.chapter2.level1.core.Ch2L1PatrolRunner
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1PatrolSpec
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Stare
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Watch
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.platform.KeptEntities
import daio7771.everytunnel.platform.TickProfiler
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import java.util.UUID

/**
 * Act 3's patrols of Chapter 2's Level 1 (`patrols.json`, [daio7771.everytunnel.chapter2.level1.core.Ch2L1Patrols]): two guards in Chapter
 * 1's uniform walking their rounds whenever someone plays the level, from the start until the office's cinematic (patrol A round the
 * ring, clockwise, with a stop by the door down into the authorized corridor; patrol B out of Security, along the authorized corridor and
 * back). They are factory people ([FactoryNpcEntity], kept: [KeptEntities]) whose every step this hook directs ([FactoryNpcEntity.direction]).
 *
 * They do nothing to a player who behaves. Each tick each guard looks ([Ch2L1Watch.inView] and a clear line); a player he sees is
 * **suspicious** inside a forbidden zone, crouching close to him, or after staring at the auxiliary lab (the crosshair on it, [Ch2L1Stare]).
 * Then he stops and turns to them while his "?" fills ([FactoryNpcEntity.alert], drawn by the client) with a questioning sound; full, he
 * tells them off ([WARN_PACKET]: the line's subtitle, no voice yet) and waits; if they keep at it he runs at them ([Ch2Sounds.CH2L1_GUARD_ALERT])
 * and catches them: the screen fades to black with "You've been caught." ([CAUGHT_PACKET]), they are put back at the entrance of the zone
 * ([daio7771.everytunnel.chapter2.level1.core.Ch2L1Forbidden.back], or `back_elsewhere`) with what they opened still open, and he is back on his
 * round. Nobody watches during a cinematic, in Act 2 (the coworker's walk and the meeting), in Creative or Spectator, or for a few seconds
 * after a catch. Server thread only.
 */
object Ch2L1PatrolHooks {

    /** To the client: a guard's line as a subtitle (the speaker's lang key, the text's, how long in ticks). */
    val WARN_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_guard_line")

    /** To the client: caught (the fade to black, its text). */
    val CAUGHT_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_caught")

    private const val KEY = "every_tunnel.ch2.l1."
    private const val ENSURE_TICKS = 100

    /** The fade covers the screen after this many ticks (the player is moved then) and the guard's line comes after it. */
    const val FADE_IN_TICKS = 12
    private const val LINE_TICKS = 70

    /** After a catch nobody watches this player for this long. */
    private const val SPARE_TICKS = 100

    private class Guard(val spec: Ch2L1PatrolSpec, val entity: FactoryNpcEntity, val runner: Ch2L1PatrolRunner) {
        val watch = Ch2L1Watch(Ch2L1DataLoader.patrols.watch)
        var chaseFrom: Vec3d? = null
    }

    private val guards = LinkedHashMap<String, Guard>()
    private val stares = HashMap<UUID, Ch2L1Stare>()
    private val spared = HashMap<UUID, Int>()
    private val moving = HashMap<UUID, Pair<Int, Vec3d>>()
    private val backYaw = HashMap<UUID, Float>()

    private val watchSpec get() = Ch2L1DataLoader.patrols.watch

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        ServerLifecycleEvents.SERVER_STOPPED.register { forgetAll() }
        Ch2L1.onStateChange { player, _, to -> if (to >= Ch2L1State.ACT3_CUTSCENE) clear(player.server) }
    }

    private fun tick(server: MinecraftServer) {
        if (server.ticks % ENSURE_TICKS == 0) ensure(server)
        for (id in moving.keys.toList()) {
            val (left, to) = moving.getValue(id)
            if (left > 1) {
                moving[id] = (left - 1) to to
            } else {
                moving.remove(id)
                server.playerManager.getPlayer(id)?.let { putBack(it, to) }
            }
        }
        for (id in spared.keys.toList()) {
            val left = spared.getValue(id)
            if (left > 1) spared[id] = left - 1 else spared.remove(id)
        }
        if (guards.isEmpty()) return
        val world = Ch2L1Npcs.world(server) ?: return
        val players = server.playerManager.playerList.filter { it.serverWorld == world && Ch2L1.active(it) }
        for (player in players) stare(player)
        val iterator = guards.values.iterator()
        while (iterator.hasNext()) {
            val guard = iterator.next()
            if (guard.entity.isRemoved) {
                iterator.remove()
                continue
            }
            step(guard, players.minByOrNull { it.squaredDistanceTo(guard.entity) })
        }
    }

    // --- the guards in the level -----------------------------------------------------------------------

    /** Puts in each guard that is missing while someone plays the level before the office's cinematic; takes them out otherwise. */
    private fun ensure(server: MinecraftServer) {
        val world = Ch2L1Npcs.world(server) ?: return
        val playing = Ch2L1Build.isBuilt(server) && server.playerManager.playerList.any {
            it.serverWorld == world && Ch2L1.active(it) && Ch2L1.state(it) < Ch2L1State.ACT3_CUTSCENE
        }
        if (!playing) {
            clear(server)
            return
        }
        val anchors = Ch2L1DataLoader.data.anchors
        for (spec in Ch2L1DataLoader.patrols.patrols) {
            if (guards[spec.id]?.entity?.isRemoved == false) continue
            val path = anchors.path(spec.path) ?: continue
            if (path.points.size < 2) continue
            val start = spec.spawn?.let { anchors.point(it)?.position } ?: path.points.first()
            if (!world.isChunkLoaded(BlockPos.ofFloored(start.x, start.y, start.z))) continue
            val pauses = spec.pauses.mapNotNull { pause ->
                anchors.point(pause.at)?.position?.let { Ch2L1PatrolRunner.nearest(path.points, it) to pause.ticks }
            }.toMap()
            val npc = KeptEntities.spawn(world) { w ->
                Ch2Entities.FACTORY_NPC.create(w)?.also {
                    it.skin = spec.skin
                    it.pace = spec.pace
                    it.workerId = spec.id
                    it.group = GROUP
                    it.seed = spec.id.hashCode().toLong()
                    it.life = Ch2L1DataLoader.npcs.life
                    it.refreshPositionAndAngles(start.x, start.y, start.z, 0f, 0f)
                    it.direction = FactoryNpcEntity.Direction()
                }
            } ?: continue
            guards[spec.id] = Guard(spec, npc, Ch2L1PatrolRunner(path.points, path.closed, pauses))
        }
    }

    /** Every guard out of the level. */
    fun clear(server: MinecraftServer) {
        for (guard in guards.values) {
            KeptEntities.forget { it === guard.entity }
            guard.entity.discard()
        }
        guards.clear()
        Ch2L1Npcs.world(server)?.let { world ->
            world.getEntitiesByType(Ch2Entities.FACTORY_NPC, Ch2L1Npcs.levelBox()) { it.group == GROUP }.forEach {
                KeptEntities.forget { e -> e === it }
                it.discard()
            }
        }
    }

    private fun forgetAll() {
        guards.clear()
        stares.clear()
        spared.clear()
        moving.clear()
        backYaw.clear()
    }

    // --- one guard, one tick -----------------------------------------------------------------------------

    private fun step(guard: Guard, player: ServerPlayerEntity?) {
        val npc = guard.entity
        val d = npc.direction ?: FactoryNpcEntity.Direction().also { npc.direction = it }
        val watching = player != null && watches(player)
        val spec = watchSpec
        var seen = false
        var suspicious = false
        var distance = Double.MAX_VALUE
        if (watching) {
            player!!
            distance = Math.sqrt((player.x - npc.x) * (player.x - npc.x) + (player.z - npc.z) * (player.z - npc.z))
            val range = if (player.isSneaking) spec.crouchRange else spec.range
            seen = Math.abs(player.y - npc.y) < 3 &&
                Ch2L1Watch.inView(npc.x, npc.z, npc.headYaw, player.x, player.z, range, spec.fovDegrees, spec.proximity) &&
                (distance <= spec.proximity || npc.canSee(player))
            suspicious = seen && (forbiddenZone(player) != null || (player.isSneaking && distance <= spec.crouchNear) ||
                stares[player.uuid]?.over == true)
        } else if (guard.watch.phase != Ch2L1Watch.Phase.CALM) {
            backToRound(guard)
        }
        when (guard.watch.tick(suspicious, seen, distance)) {
            Ch2L1Watch.Event.NOTICED -> npc.world.playSound(null, npc.blockPos, Ch2Sounds.CH2L1_GUARD_SUSPECT, SoundCategory.HOSTILE, 0.9f, 1f)
            Ch2L1Watch.Event.WARNED -> player?.let { say(it, spec.warnLine) }
            Ch2L1Watch.Event.CHASED -> {
                guard.chaseFrom = npc.pos
                npc.pace = spec.runPace
                player?.let { it.playSound(Ch2Sounds.CH2L1_GUARD_ALERT, SoundCategory.HOSTILE, 1f, 1f) }
            }
            Ch2L1Watch.Event.CAUGHT -> player?.let { caught(guard, it) }
            Ch2L1Watch.Event.GAVE_UP, Ch2L1Watch.Event.CALMED -> backToRound(guard)
            null -> {}
        }
        npc.alert = when (guard.watch.phase) {
            Ch2L1Watch.Phase.CALM -> FactoryNpcEntity.ALERT_NONE
            Ch2L1Watch.Phase.NOTICING -> (guard.watch.fill * 100).toInt()
            Ch2L1Watch.Phase.WARNED -> FactoryNpcEntity.ALERT_WARNED
            Ch2L1Watch.Phase.CHASING -> FactoryNpcEntity.ALERT_CHASING
        }
        val eyes = player?.let { Vec3d(it.x, it.eyeY, it.z) }
        when (guard.watch.phase) {
            Ch2L1Watch.Phase.CALM -> round(guard, d)
            Ch2L1Watch.Phase.NOTICING, Ch2L1Watch.Phase.WARNED -> {
                // He stops and turns to them.
                d.walkTo = null
                d.face = eyes?.let { Ch2L1Angles.yawOf(it.x - npc.x, it.z - npc.z) }
                d.lookAt = eyes
                d.pose = Ch2L1Pose.NONE
            }
            Ch2L1Watch.Phase.CHASING -> {
                d.walkTo = player?.pos
                d.stopThere = false
                d.face = null
                d.lookAt = eyes
                d.pose = Ch2L1Pose.NONE
            }
        }
    }

    /** His round: where the runner says, at his own pace, looking about. */
    private fun round(guard: Guard, d: FactoryNpcEntity.Direction) {
        val npc = guard.entity
        d.lookAt = null
        d.pose = Ch2L1Pose.NONE
        when (val command = guard.runner.tick(npc.x, npc.z)) {
            is Ch2L1PatrolRunner.Command.Walk -> {
                d.walkTo = Vec3d(command.x, command.y, command.z)
                d.stopThere = command.brake
                d.face = null
            }
            is Ch2L1PatrolRunner.Command.Stand -> {
                d.walkTo = null
                d.face = command.yaw
            }
        }
    }

    /** Back on his round at his own pace (from where he stopped, or from where he left it to run). */
    private fun backToRound(guard: Guard) {
        guard.watch.calm()
        guard.entity.pace = guard.spec.pace
        guard.chaseFrom = null
    }

    /** Whether the guards watch [player] now: playing, in Act 1 or Act 3's exploring, not in a cinematic, not in Creative, not just caught. */
    private fun watches(player: ServerPlayerEntity): Boolean {
        val state = Ch2L1.state(player)
        if (state == Ch2L1State.ACT2_FOLLOW || state == Ch2L1State.ACT2_MEETING || state >= Ch2L1State.ACT3_CUTSCENE) return false
        if (player.interactionManager.gameMode == GameMode.CREATIVE || player.isSpectator) return false
        if (Ch2L1IntroHooks.isRunning(player) || player.uuid in spared || player.uuid in moving) return false
        return true
    }

    // --- what makes a player suspicious --------------------------------------------------------------------

    /** The forbidden zone [player] stands in, or null. */
    private fun forbiddenZone(player: ServerPlayerEntity) = watchSpec.forbidden.firstOrNull { f ->
        Ch2L1DataLoader.data.anchors.zone(f.zone)?.box?.contains(player.x, player.y + 0.1, player.z) == true
    }

    /** Counts how long [player] has kept the crosshair on the auxiliary lab: a ray from their eyes that reaches into its zone. */
    private fun stare(player: ServerPlayerEntity) {
        val spec = watchSpec
        val tracker = stares.getOrPut(player.uuid) { Ch2L1Stare(spec.stareTicks) }
        val zone = Ch2L1DataLoader.data.anchors.zone(spec.stareZone)
        if (zone == null || zone.box.contains(player.x, player.y + 0.1, player.z)) {
            tracker.tick(false)
            return
        }
        val eye = player.eyePos
        val look = player.rotationVector
        var looking = false
        var t = 0.5
        while (t <= spec.stareReach) {
            val p = eye.add(look.multiply(t))
            if (zone.box.contains(p.x, p.y, p.z)) {
                looking = true
                break
            }
            val state = player.world.getBlockState(BlockPos.ofFloored(p))
            if (!state.isOf(Ch2Blocks.FACTORY_GLASS) && state.isOpaqueFullCube(player.world, BlockPos.ofFloored(p))) break
            t += 0.25
        }
        tracker.tick(looking)
    }

    // --- told off, caught ------------------------------------------------------------------------------

    private fun say(player: ServerPlayerEntity, line: String) {
        if (!ServerPlayNetworking.canSend(player, WARN_PACKET)) return
        val buf = PacketByteBufs.create()
        buf.writeString(KEY + "speaker.guard")
        buf.writeString(KEY + line)
        buf.writeVarInt(LINE_TICKS)
        ServerPlayNetworking.send(player, WARN_PACKET, buf)
    }

    /** [guard] has caught [player]: the fade, the sound, and in a moment they are put back; he goes back to his round. */
    private fun caught(guard: Guard, player: ServerPlayerEntity) {
        val anchors = Ch2L1DataLoader.data.anchors
        val back = anchors.point(forbiddenZone(player)?.back ?: watchSpec.backElsewhere)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} caught by {}", player.gameProfile.name, guard.spec.id)
        if (ServerPlayNetworking.canSend(player, CAUGHT_PACKET)) ServerPlayNetworking.send(player, CAUGHT_PACKET, PacketByteBufs.empty())
        player.playSound(Ch2Sounds.CH2L1_GUARD_CAUGHT, SoundCategory.HOSTILE, 1f, 1f)
        guard.chaseFrom?.let { guard.entity.refreshPositionAndAngles(it.x, it.y, it.z, guard.entity.yaw, 0f) }
        backToRound(guard)
        for (other in guards.values) if (other !== guard && other.watch.phase != Ch2L1Watch.Phase.CALM) backToRound(other)
        stares[player.uuid] = Ch2L1Stare(watchSpec.stareTicks)
        val to = back?.position?.let { Vec3d(it.x, it.y, it.z) } ?: player.pos
        moving[player.uuid] = FADE_IN_TICKS to to
        backYaw[player.uuid] = back?.yaw ?: player.yaw
    }

    /** Under the black: the player at the zone's entrance, the guard's line, and a moment when nobody watches them. */
    private fun putBack(player: ServerPlayerEntity, to: Vec3d) {
        val yaw = backYaw.remove(player.uuid) ?: player.yaw
        player.stopRiding()
        player.teleport(player.serverWorld, to.x, to.y, to.z, yaw, 0f)
        player.velocity = Vec3d.ZERO
        say(player, watchSpec.caughtLine)
        spared[player.uuid] = SPARE_TICKS
    }

    /** For `/et ch2 l1 patrols`: one line for each guard. */
    fun status(): List<String> = if (guards.isEmpty()) listOf("No guards in the level (nobody plays it, or it is past the office).") else
        guards.values.map {
            val p: Position = it.runner.heading
            "${it.spec.id} (${it.spec.skin}) at ${"%.1f".format(it.entity.x)}, ${"%.1f".format(it.entity.z)}: ${it.watch.phase}" +
                (if (it.watch.phase == Ch2L1Watch.Phase.NOTICING) " ${(it.watch.fill * 100).toInt()} %" else "") +
                ", heading for ${"%.1f".format(p.x)}, ${"%.1f".format(p.z)}"
        }

    /** The group the guards are put in, apart from the workers'. */
    const val GROUP = "patrols"
}
